package com.danitube.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.danitube.app.data.db.VideoDao
import com.danitube.app.data.db.VideoEntity
import com.danitube.app.data.download.VideoStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the library folder: a folder the parent picks (storage access framework, so it can be
 * a USB drive, an SD card or a cloud-synced folder) that holds the videos. The app's own storage
 * disappears on uninstall; the folder does not. Next to the videos it keeps `videos.json`, the list
 * the app reads to get the library back after a reinstall.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VideoDao,
    private val storage: VideoStorage,
    private val folder: BackupFolderStore,
) {
    /**
     * Makes [tree] the library folder. Videos already listed there are added to the app, and videos
     * still in the app's private storage are moved in. Returns how many videos the library has.
     * [onProgress] gets (done, total) while the private ones move.
     */
    suspend fun useFolder(tree: Uri, onProgress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        context.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        folder.tree = tree
        storage.invalidate()
        importListed()

        val videos = dao.getAll()
        videos.forEachIndexed { index, video ->
            onProgress(index, videos.size)
            moveToLibrary(video)
        }
        writeManifest()
        onProgress(videos.size, videos.size)
        storage.invalidate()
        videos.size
    }

    /** Moves a freshly downloaded video's files into the library. A no-op when no folder is picked. */
    suspend fun store(videos: List<VideoEntity>) = withContext(Dispatchers.IO) {
        if (!storage.hasLibrary) return@withContext
        videos.forEach(::moveToLibrary)
    }

    /** Rewrites the list in the library folder. Does nothing when no folder is picked. */
    suspend fun sync(removedId: String? = null) = withContext(Dispatchers.IO) {
        if (storage.hasLibrary) writeManifest(removedId)
    }

    private fun moveToLibrary(video: VideoEntity) {
        storage.moveToLibrary(video.videoFile, "video/mp4")
        video.thumbFile?.let { storage.moveToLibrary(it, "image/jpeg") }
    }

    /** Adds the videos listed in the folder's `videos.json` that the app does not know yet. */
    private suspend fun importListed() {
        val dir = storage.libraryDir() ?: return
        val file = dir.findFile(VideoStorage.MANIFEST) ?: return
        val entries = readManifest(file.uri) ?: return
        val known = dao.getAll().map { it.id }.toSet()
        val added = mutableListOf<VideoEntity>()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            val id = e.getString("id")
            val videoFile = e.getString("videoFile")
            if (id in known || dir.findFile(videoFile) == null) continue
            val thumbFile = e.optString("thumbFile").ifEmpty { null }
            added += VideoEntity(
                id = id,
                title = e.getString("title"),
                durationSec = e.optInt("durationSec"),
                videoFile = videoFile,
                thumbFile = thumbFile?.takeIf { dir.findFile(it) != null },
                addedAt = e.optLong("addedAt", System.currentTimeMillis()),
                playCount = e.optInt("playCount"),
                lastPlayedAt = e.optLong("lastPlayedAt"),
                channel = e.optString("channel").ifEmpty { null },
                tags = if (e.has("tags")) e.getString("tags") else null,
            )
        }
        dao.upsert(added)
    }

    private fun readManifest(uri: Uri): JSONArray? = runCatching {
        context.contentResolver.openInputStream(uri)!!.use { JSONObject(it.readBytes().decodeToString()) }
            .getJSONArray("videos")
    }.getOrNull()

    /**
     * Rewrites the list of videos in the library. Entries already there whose files are still in
     * the folder are kept, so a fresh install that has not imported yet cannot wipe the list.
     */
    private suspend fun writeManifest(removedId: String? = null) {
        val dir = storage.libraryDir(create = true) ?: error("Could not open the video folder")
        val videos = dao.getAll()
        val known = videos.map { it.id }.toSet()
        val array = JSONArray()
        dir.findFile(VideoStorage.MANIFEST)?.let { file ->
            readManifest(file.uri)?.let { old ->
                for (i in 0 until old.length()) {
                    val e = old.getJSONObject(i)
                    val id = e.getString("id")
                    if (id !in known && id != removedId && dir.findFile(e.getString("videoFile")) != null) array.put(e)
                }
            }
        }
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
                    .put("lastPlayedAt", v.lastPlayedAt)
                    .put("channel", v.channel ?: "")
                    .apply { v.tags?.let { put("tags", it) } },
            )
        }
        dir.findFile(VideoStorage.MANIFEST)?.delete()
        val target = dir.createFile("application/json", VideoStorage.MANIFEST) ?: error("Could not write the video list")
        context.contentResolver.openOutputStream(target.uri, "w")!!.use {
            it.write(JSONObject().put("version", 1).put("videos", array).toString().toByteArray())
        }
    }
}
