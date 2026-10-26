package com.danitube.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.danitube.app.data.db.VideoEntity
import com.danitube.app.data.download.VideoStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class BackupNotFoundException : Exception("No backup in that folder")

/**
 * Copies the videos to a folder the parent picks (storage access framework, so it can be a
 * USB drive, an SD card or a cloud-synced folder) and restores them from there. The app's own
 * storage disappears on uninstall; the backup folder does not.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: VideoRepository,
    private val storage: VideoStorage,
) {
    /** Returns how many videos are in the backup afterwards. [onProgress] gets (done, total). */
    suspend fun backup(tree: Uri, onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, tree) ?: error("Folder not available")
        val dir = root.findFile(DIR_NAME)?.takeIf { it.isDirectory } ?: root.createDirectory(DIR_NAME)
            ?: error("Could not create the backup folder")
        val videos = repository.all()
        videos.forEachIndexed { index, video ->
            onProgress(index, videos.size)
            copyToBackup(storage.file(video.videoFile), dir, video.videoFile, "video/mp4")
            video.thumbFile?.let { copyToBackup(storage.file(it), dir, it, "image/jpeg") }
        }
        writeManifest(dir, videos)
        onProgress(videos.size, videos.size)
        videos.size
    }

    /** Returns how many videos were restored. Works with the picked folder or its parent. */
    suspend fun restore(tree: Uri, onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val picked = DocumentFile.fromTreeUri(context, tree) ?: error("Folder not available")
        val dir = listOfNotNull(picked, picked.findFile(DIR_NAME))
            .firstOrNull { it.findFile(MANIFEST) != null } ?: throw BackupNotFoundException()
        val manifest = context.contentResolver.openInputStream(dir.findFile(MANIFEST)!!.uri)!!
            .use { JSONObject(it.readBytes().decodeToString()) }
        val entries = manifest.getJSONArray("videos")

        val restored = mutableListOf<VideoEntity>()
        for (i in 0 until entries.length()) {
            onProgress(i, entries.length())
            val e = entries.getJSONObject(i)
            val videoFile = e.getString("videoFile")
            val thumbFile = e.optString("thumbFile").ifEmpty { null }
            val video = dir.findFile(videoFile) ?: continue
            copyFromBackup(video, storage.file(videoFile))
            thumbFile?.let { name -> dir.findFile(name)?.let { copyFromBackup(it, storage.file(name)) } }
            restored += VideoEntity(
                id = e.getString("id"),
                title = e.getString("title"),
                durationSec = e.optInt("durationSec"),
                videoFile = videoFile,
                thumbFile = thumbFile?.takeIf { storage.file(it).exists() },
                addedAt = e.optLong("addedAt", System.currentTimeMillis()),
                playCount = e.optInt("playCount"),
                lastPlayedAt = e.optLong("lastPlayedAt"),
            )
        }
        repository.add(restored)
        onProgress(entries.length(), entries.length())
        restored.size
    }

    private fun copyToBackup(source: File, dir: DocumentFile, name: String, mime: String) {
        if (!source.exists()) return
        val existing = dir.findFile(name)
        if (existing != null && existing.length() == source.length()) return // already backed up
        existing?.delete()
        val target = dir.createFile(mime, name) ?: error("Could not create $name")
        context.contentResolver.openOutputStream(target.uri, "w")!!.use { out ->
            source.inputStream().use { it.copyTo(out) }
        }
    }

    private fun copyFromBackup(source: DocumentFile, target: File) {
        if (target.exists() && target.length() == source.length()) return
        context.contentResolver.openInputStream(source.uri)!!.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
    }

    private fun writeManifest(dir: DocumentFile, videos: List<VideoEntity>) {
        val array = JSONArray()
        videos.forEach { v ->
            array.put(
                JSONObject()
                    .put("id", v.id)
                    .put("title", v.title)
                    .put("durationSec", v.durationSec)
                    .put("videoFile", v.videoFile)
                    .put("thumbFile", v.thumbFile ?: "")
                    .put("addedAt", v.addedAt)
                    .put("playCount", v.playCount)
                    .put("lastPlayedAt", v.lastPlayedAt),
            )
        }
        dir.findFile(MANIFEST)?.delete()
        val target = dir.createFile("application/json", MANIFEST) ?: error("Could not write the backup list")
        context.contentResolver.openOutputStream(target.uri, "w")!!.use {
            it.write(JSONObject().put("version", 1).put("videos", array).toString().toByteArray())
        }
    }

    private companion object {
        const val DIR_NAME = "DaniTube"
        const val MANIFEST = "videos.json"
    }
}
