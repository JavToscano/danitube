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
import android.net.Uri
import javax.inject.Inject

/** A video ready to display, with where its files are resolved. */
data class VideoItem(
    val id: String,
    val title: String,
    val durationSec: Int,
    val thumb: Uri?,
    /** null for a streamed YouTube video, which is resolved when it plays. */
    val video: Uri?,
    val isStream: Boolean = false,
    val playCount: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: VideoRepository,
    catalog: VideoCatalog,
) : ViewModel() {

    /** null while the first database read is pending, so the UI doesn't flash the empty state. */
    val videos: StateFlow<List<VideoItem>?> = catalog.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Everything, for the parent's list (suggested YouTube videos included). */
    val managed: StateFlow<List<VideoItem>?> = catalog.managed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Most-played first; only videos she has actually played. */
    val favorites: StateFlow<List<VideoItem>?> = videos
        .map { list -> list?.filter { it.playCount > 0 }?.sortedByDescending { it.playCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

/** null when the video's file is gone (e.g. the library folder was emptied or is unplugged). */
fun VideoEntity.toItem(storage: VideoStorage): VideoItem? {
    val video = storage.uri(videoFile) ?: return null
    return VideoItem(
        id = id,
        title = title,
        durationSec = durationSec,
        thumb = thumbFile?.let(storage::uri),
        video = video,
        playCount = playCount,
    )
}
