package com.danitube.app.data.download

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import android.content.Context
import android.util.Log
import com.danitube.app.data.db.VideoEntity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LinkPreview(val title: String, val thumbnailUrl: String?, val durationSec: Int)

/** One YouTube search hit. */
data class SearchResult(
    val id: String,
    val title: String,
    val channel: String?,
    val durationSec: Int,
    val thumbnailUrl: String,
) {
    val link: YoutubeLink get() = YoutubeLink("https://www.youtube.com/watch?v=$id", isPlaylist = false)
}

@Singleton
class YtDlpDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: VideoStorage,
) {
    private val initLock = Any()
    private var initialised = false
    @Volatile private var lastUpdateMs = 0L

    /** One download at a time, in the order queued; also keeps yt-dlp's output files from mixing. */
    private val downloadLock = Mutex()

    private fun initLocked() {
        if (!initialised) {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            initialised = true
        }
    }

    private fun updateLocked(): Boolean = runCatching {
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
    }.onSuccess { lastUpdateMs = System.currentTimeMillis() }
        .onFailure { Log.w(TAG, "yt-dlp update failed", it) }
        .isSuccess

    /** Unpacks the bundled yt-dlp/ffmpeg on first use and updates it once; cheap afterwards. */
    private fun ensureInit() = synchronized(initLock) {
        if (!initialised) {
            initLocked()
            // The bundled yt-dlp goes stale within weeks as YouTube changes. A failed update
            // (offline) keeps the current copy.
            updateLocked()
        }
    }

    /** Pulls the latest yt-dlp now. Returns false when it could not (e.g. no network). */
    fun update(): Boolean = synchronized(initLock) {
        initLocked()
        updateLocked()
    }

    /**
     * Runs [block]; if it fails and yt-dlp has not been updated in the last few minutes, updates
     * it and tries once more. YouTube breaks old yt-dlp copies without warning, and this heals it.
     */
    private suspend fun <T> withUpdateRetry(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        val stale = System.currentTimeMillis() - lastUpdateMs > UPDATE_COOLDOWN_MS
        if (!stale || !withContext(Dispatchers.IO) { update() }) throw e
        block()
    }

    /** Title/thumbnail of the video (or first video of a playlist) for the confirm screen. */
    suspend fun preview(link: YoutubeLink): LinkPreview = withContext(Dispatchers.IO) {
        ensureInit()
        withUpdateRetry {
            val request = YoutubeDLRequest(link.url).apply { addOption("--playlist-items", "1") }
            val info = YoutubeDL.getInstance().getInfo(request)
            LinkPreview(
                title = info.title ?: link.url,
                thumbnailUrl = info.thumbnail,
                durationSec = info.duration,
            )
        }
    }

    /** The first [count] YouTube results for [query]; titles only, nothing is downloaded. */
    suspend fun search(query: String, count: Int = SEARCH_RESULTS): List<SearchResult> =
        withContext(Dispatchers.IO) {
            ensureInit()
            withUpdateRetry {
                val request = YoutubeDLRequest("ytsearch$count:$query").apply {
                    addOption("--flat-playlist")
                    addOption("--dump-json") // one json line per result
                }
                YoutubeDL.getInstance().execute(request).out.lineSequence()
                    .filter { it.startsWith("{") }
                    .mapNotNull { line -> runCatching { parseResult(JSONObject(line)) }.getOrNull() }
                    .distinctBy { it.id }
                    .toList()
            }
        }

    private fun parseResult(json: JSONObject): SearchResult {
        val id = json.getString("id")
        return SearchResult(
            id = id,
            title = json.optString("title", id),
            channel = (json.optString("channel").ifEmpty { json.optString("uploader") }).ifEmpty { null },
            durationSec = json.optInt("duration", 0),
            thumbnailUrl = "https://i.ytimg.com/vi/$id/mqdefault.jpg",
        )
    }

    /**
     * Downloads the video, or every video of a playlist, as capped-quality mp4 plus a jpg thumbnail.
     * [onProgress] receives 0..100. Cancelling the calling coroutine kills the yt-dlp process.
     */
    suspend fun download(link: YoutubeLink, onProgress: (Float) -> Unit): List<VideoEntity> =
        withContext(Dispatchers.IO) {
            ensureInit()
            downloadLock.withLock { withUpdateRetry { runDownload(link, onProgress) } }
        }

    private suspend fun runDownload(link: YoutubeLink, onProgress: (Float) -> Unit): List<VideoEntity> {
        val processId = UUID.randomUUID().toString()
        val handle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) YoutubeDL.getInstance().destroyProcessById(processId)
        }
        return try {
            val request = YoutubeDLRequest(link.url).apply {
                addOption("-f", FORMAT)
                addOption("--merge-output-format", "mp4")
                addOption("--write-info-json")
                addOption("--write-thumbnail")
                addOption("--convert-thumbnails", "jpg")
                addOption("--no-mtime")
                addOption(if (link.isPlaylist) "--yes-playlist" else "--no-playlist")
                addOption("-o", "${storage.dir.absolutePath}/%(id)s.%(ext)s")
            }
            YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                if (progress >= 0f) onProgress(progress)
            }
            collectFinished()
        } finally {
            handle.dispose()
        }
    }

    /** Turns each finished `<id>.info.json` into an entity and removes the json. */
    private fun collectFinished(): List<VideoEntity> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<VideoEntity>()
        storage.dir.listFiles { f -> f.name.endsWith(".info.json") }.orEmpty().forEach { json ->
            val info = JSONObject(json.readText())
            val id = info.getString("id")
            val mp4 = "$id.mp4"
            if (storage.file(mp4).exists()) {
                val thumb = "$id.jpg".takeIf { storage.file(it).exists() }
                result += VideoEntity(
                    id = id,
                    title = info.optString("title", id),
                    durationSec = info.optInt("duration", 0),
                    videoFile = mp4,
                    thumbFile = thumb,
                    addedAt = now,
                )
            }
            json.delete()
        }
        return result
    }

    private companion object {
        const val TAG = "YtDlpDownloader"
        const val SEARCH_RESULTS = 20
        // Prefer a single mp4 up to 720p (no merge needed); fall back to merging; last resort anything.
        const val UPDATE_COOLDOWN_MS = 10 * 60 * 1000L
        const val FORMAT =
            "best[height<=720][ext=mp4]/bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720]/b"
    }
}
