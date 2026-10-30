package com.danitube.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danitube.app.data.VideoRepository
import com.danitube.app.data.download.VideoStorage
import com.danitube.app.ui.library.VideoItem
import com.danitube.app.ui.library.toItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val storage: VideoStorage,
) : ViewModel() {

    private val _queue = MutableStateFlow<List<VideoItem>?>(null)

    /** The chosen video first, then the rest in random order. null until loaded. */
    val queue: StateFlow<List<VideoItem>?> = _queue.asStateFlow()

    /**
     * [startId] null means no preferred first video. With [favoritesOnly] the queue is just the
     * videos she has played before (falling back to everything if there are none yet).
     */
    fun start(startId: String?, favoritesOnly: Boolean) {
        viewModelScope.launch {
            var items = withContext(Dispatchers.IO) { repository.all().mapNotNull { it.toItem(storage) } }
            if (favoritesOnly) items = items.filter { it.playCount > 0 }.ifEmpty { items }
            val first = items.firstOrNull { it.id == startId }
            _queue.value = listOfNotNull(first) + items.filter { it.id != startId }.shuffled()
        }
    }

    fun recordPlay(id: String) {
        viewModelScope.launch { repository.recordPlay(id) }
    }
}
