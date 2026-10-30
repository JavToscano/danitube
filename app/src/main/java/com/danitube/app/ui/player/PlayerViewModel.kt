package com.danitube.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danitube.app.data.VideoRepository
import com.danitube.app.data.download.YtDlpDownloader
import com.danitube.app.data.suggest.UnmeteredNetwork
import com.danitube.app.ui.library.VideoCatalog
import com.danitube.app.ui.library.VideoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val catalog: VideoCatalog,
    private val downloader: YtDlpDownloader,
    network: UnmeteredNetwork,
) : ViewModel() {

    private val _queue = MutableStateFlow<List<VideoItem>?>(null)

    /** The chosen video first, then the rest in random order. null until loaded. */
    val queue: StateFlow<List<VideoItem>?> = _queue.asStateFlow()

    /** Streamed videos play only while this is true: Wi-Fi, never mobile data. */
    val unmetered: StateFlow<Boolean> = network.available

    private val streamUrls = ConcurrentHashMap<String, Pair<String, Long>>()
    private var streamFailures = 0

    /**
     * [startId] null means no preferred first video. With [favoritesOnly] the queue is just the
     * videos she has played before (falling back to everything if there are none yet).
     */
    fun start(startId: String?, favoritesOnly: Boolean) {
        viewModelScope.launch {
            var items = catalog.snapshot()
            if (favoritesOnly) items = items.filter { it.playCount > 0 }.ifEmpty { items }
            val first = items.firstOrNull { it.id == startId }
            _queue.value = listOfNotNull(first) + items.filter { it.id != startId }.shuffled()
        }
    }

    /** Direct link for a streamed video, or null when it cannot be had (offline, removed, ...). */
    suspend fun streamUrl(id: String): String? {
        streamUrls[id]?.let { (url, at) -> if (System.currentTimeMillis() - at < URL_TTL_MS) return url }
        return try {
            downloader.streamUrl(id).also { streamUrls[id] = it to System.currentTimeMillis() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** After a few streams fail in a row (no signal, yt-dlp broken) the rest of the session skips them. */
    fun streamFailed() {
        if (++streamFailures < MAX_STREAM_FAILURES) return
        _queue.value = _queue.value?.filter { !it.isStream }?.takeIf { it.isNotEmpty() } ?: _queue.value
    }

    fun recordPlay(id: String) {
        viewModelScope.launch { repository.recordPlay(id) }
    }

    private companion object {
        const val URL_TTL_MS = 60 * 60 * 1000L
        const val MAX_STREAM_FAILURES = 3
    }
}
