package com.danitube.app.ui.add

import java.util.UUID
import com.danitube.app.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.danitube.app.data.download.DownloadScheduler
import com.danitube.app.data.download.DownloadWorker
import com.danitube.app.data.download.LinkPreview
import com.danitube.app.data.download.YoutubeLink
import com.danitube.app.data.download.YtDlpDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PreviewState {
    data object None : PreviewState
    data object Loading : PreviewState
    data class Ready(val preview: LinkPreview) : PreviewState
    data class Error(@StringRes val messageRes: Int, val detail: String? = null) : PreviewState
}

data class AddUiState(
    val input: String = "",
    val link: YoutubeLink? = null,
    val preview: PreviewState = PreviewState.None,
    val queued: Boolean = false,
)

data class DownloadItem(
    val id: UUID,
    val url: String,
    val isPlaylist: Boolean,
    val state: WorkInfo.State,
    val progress: Float,
    val error: String?,
)

@HiltViewModel
class AddVideoViewModel @Inject constructor(
    private val downloader: YtDlpDownloader,
    private val scheduler: DownloadScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    val downloads: StateFlow<List<DownloadItem>> = scheduler.observe()
        .map { infos ->
            infos.sortedBy { it.state.sortOrder() }.map { info ->
                DownloadItem(
                    id = info.id,
                    isPlaylist = DownloadWorker.TAG_PLAYLIST in info.tags,
                    url = info.tags.firstOrNull { it.startsWith("http") }.orEmpty(),
                    state = info.state,
                    progress = info.progress.getFloat(DownloadWorker.KEY_PROGRESS, 0f),
                    error = info.outputData.getString(DownloadWorker.KEY_ERROR),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var previewJob: Job? = null

    /** Called when the text field changes, or with the shared text from the share sheet. */
    fun onInput(text: String) {
        previewJob?.cancel()
        val link = YoutubeLink.parse(text)
        _state.value = AddUiState(
            input = text,
            link = link,
            preview = when {
                link != null -> PreviewState.Loading
                text.isBlank() -> PreviewState.None
                else -> PreviewState.Error(R.string.error_not_youtube_link)
            },
        )
        if (link == null) return
        previewJob = viewModelScope.launch {
            val result = try {
                PreviewState.Ready(downloader.preview(link))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PreviewState.Error(R.string.error_preview_failed, e.message?.lineSequence()?.firstOrNull())
            }
            _state.update { it.copy(preview = result) }
        }
    }

    fun confirm() {
        val link = _state.value.link ?: return
        scheduler.enqueue(link)
        _state.update { it.copy(queued = true) }
    }

    fun reset() {
        previewJob?.cancel()
        _state.value = AddUiState()
    }

    fun clearFinished() = scheduler.clearFinished()

    fun cancel(item: DownloadItem) = scheduler.cancel(item.id)

    fun retry(item: DownloadItem) = scheduler.retry(YoutubeLink(item.url, item.isPlaylist))

    /** Active downloads first, then failures, then the finished ones. */
    private fun WorkInfo.State.sortOrder() = when (this) {
        WorkInfo.State.RUNNING -> 0
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> 1
        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> 2
        WorkInfo.State.SUCCEEDED -> 3
    }
}
