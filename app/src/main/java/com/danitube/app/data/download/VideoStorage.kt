package com.danitube.app.data.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.danitube.app.data.BackupFolderStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the videos and thumbnails live. The parent-picked library folder (outside the app, so it
 * survives updates and uninstalls) holds the only copy. The app-private folder is where yt-dlp
 * writes first, and where videos stay when no folder is picked or the folder is unavailable.
 * Callers ask for a name and get a Uri from whichever place has the file.
 */
@Singleton
class VideoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folder: BackupFolderStore,
) {
    val dir: File = File(context.filesDir, "videos").apply { mkdirs() }

    private var cachedDir: Pair<Uri, DocumentFile>? = null
    private var index: Map<String, DocumentFile>? = null

    private val _version = MutableStateFlow(0)

    /** Bumps whenever files move between the app and the folder, so lists can re-resolve their Uris. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun file(name: String): File = File(dir, name)

    val hasLibrary: Boolean get() = folder.tree != null

    /** The `DaniTube` folder inside the picked one (or the picked one itself when it is already it). */
    @Synchronized
    fun libraryDir(create: Boolean = false): DocumentFile? {
        val tree = folder.tree ?: return null
        cachedDir?.let { (cached, cachedDoc) -> if (cached == tree) return cachedDoc }
        return runCatching {
            val root = DocumentFile.fromTreeUri(context, tree) ?: return null
            root.takeIf { it.findFile(MANIFEST) != null }
                ?: root.findFile(DIR_NAME)?.takeIf { it.isDirectory }
                ?: if (create) root.createDirectory(DIR_NAME) else null
        }.getOrNull()?.also { cachedDir = tree to it }
    }

    /** Uri to play or draw [name]; null when the file is in neither place. Blocking: call off the main thread. */
    @Synchronized
    fun uri(name: String): Uri? {
        val local = file(name)
        if (local.exists()) return Uri.fromFile(local)
        val files = index ?: loadIndex().also { index = it }
        return files[name]?.uri
    }

    private fun loadIndex(): Map<String, DocumentFile> = runCatching {
        libraryDir()?.listFiles().orEmpty().mapNotNull { f -> f.name?.let { it to f } }.toMap()
    }.getOrDefault(emptyMap())

    @Synchronized
    fun invalidate() {
        index = null
        _version.value++
    }

    /**
     * Moves [name] from the private folder into the library, then deletes the private copy.
     * Returns false (and keeps the private file) when there is no folder or the copy failed.
     */
    @Synchronized
    fun moveToLibrary(name: String, mime: String): Boolean {
        val source = file(name)
        if (!source.exists()) return true
        val target = runCatching {
            val dir = libraryDir(create = true) ?: return false
            dir.findFile(name)?.delete()
            val created = dir.createFile(mime, name) ?: error("Could not create $name")
            context.contentResolver.openOutputStream(created.uri, "w")!!.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
            check(created.length() == source.length()) { "Incomplete copy of $name" }
            created
        }.getOrNull() ?: return false
        source.delete()
        index = null
        return target.exists()
    }

    /** Deletes [name] from wherever it is. */
    @Synchronized
    fun delete(name: String) {
        file(name).delete()
        runCatching { libraryDir()?.findFile(name)?.delete() }
        index = null
    }

    /** Bytes used by videos and thumbnails, in the app and in the library folder. */
    fun usedBytes(): Long {
        val local = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val library = runCatching { libraryDir()?.listFiles().orEmpty().sumOf { it.length() } }.getOrDefault(0L)
        return local + library
    }

    fun freeBytes(): Long = dir.usableSpace

    companion object {
        const val DIR_NAME = "DaniTube"
        const val MANIFEST = "videos.json"
    }
}
