package com.danitube.app.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danitube.app.data.VideoRepository
import com.danitube.app.data.download.VideoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StorageInfo(val videoCount: Int, val usedBytes: Long, val freeBytes: Long)

@HiltViewModel
class StorageViewModel @Inject constructor(
    repository: VideoRepository,
    private val storage: VideoStorage,
) : ViewModel() {

    /** Recomputed whenever videos are added or deleted. */
    val info: StateFlow<StorageInfo?> = repository.videos
        .map { videos ->
            withContext(Dispatchers.IO) { StorageInfo(videos.size, storage.usedBytes(), storage.freeBytes()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
