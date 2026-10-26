package com.danitube.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danitube.app.data.VideoRepository
import com.danitube.app.data.db.VideoEntity
import com.danitube.app.data.download.VideoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** A video ready to display, with absolute file paths resolved. */
data class VideoItem(
    val id: String,
    val title: String,
    val durationSec: Int,
    val thumb: File?,
    val video: File,
    val playCount: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val storage: VideoStorage,
) : ViewModel() {

    /** null while the first database read is pending, so the UI doesn't flash the empty state. */
    val videos: StateFlow<List<VideoItem>?> = repository.videos
        .map { list -> list.map { it.toItem(storage) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Most-played first; only videos she has actually played. */
    val favorites: StateFlow<List<VideoItem>?> = videos
        .map { list -> list?.filter { it.playCount > 0 }?.sortedByDescending { it.playCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

fun VideoEntity.toItem(storage: VideoStorage) = VideoItem(
    id = id,
    title = title,
    durationSec = durationSec,
    thumb = thumbFile?.let(storage::file),
    video = storage.file(videoFile),
    playCount = playCount,
)
