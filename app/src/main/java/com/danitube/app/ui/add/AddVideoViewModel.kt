package com.danitube.app.ui.add

import java.util.UUID
import com.danitube.app.R
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.danitube.app.data.download.DownloadScheduler
import com.danitube.app.data.download.DownloadWorker
import com.danitube.app.data.VideoRepository
import com.danitube.app.data.download.LinkPreview
import com.danitube.app.data.download.SearchResult
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

sealed interface SearchState {
    data object None : SearchState
    data object Loading : SearchState
    data class Results(val items: List<SearchResult>) : SearchState
    data class Error(val detail: String?) : SearchState
}

data class AddUiState(
    val input: String = "",
    val link: YoutubeLink? = null,
    /** Every link found in the text; [link] is the first one, the one previewed. */
    val links: List<YoutubeLink> = emptyList(),
    val preview: PreviewState = PreviewState.None,
    val queued: Boolean = false,
    val search: SearchState = SearchState.None,
    /** Ids of the search results ticked for download. */
    val selected: Set<String> = emptySet(),
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
    repository: VideoRepository,
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

    /** Ids already in the library, so search results can show they are downloaded. */
    val downloadedIds: StateFlow<Set<String>> = repository.videos
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var previewJob: Job? = null
    private var searchJob: Job? = null

    /** Called when the text field changes, or with the shared text from the share sheet. */
    fun onInput(text: String) {
        previewJob?.cancel()
        val links = YoutubeLink.parseAll(text)
        val link = links.firstOrNull()
        if (link != null || text.isBlank()) searchJob?.cancel()
        _state.value = AddUiState(
            input = text,
            link = link,
            links = links,
            preview = if (link != null) PreviewState.Loading else PreviewState.None,
            // Text that is not a link is a search: keep the results while the words are edited.
            search = if (link == null && text.isNotBlank()) _state.value.search else SearchState.None,
            selected = if (link == null && text.isNotBlank()) _state.value.selected else emptySet(),
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

    /** Searches YouTube for the typed words (when the text is not a link). */
    fun search() {
        val query = _state.value.input.trim()
        if (query.isEmpty() || _state.value.links.isNotEmpty()) return
        searchJob?.cancel()
        _state.update { it.copy(search = SearchState.Loading, selected = emptySet()) }
        searchJob = viewModelScope.launch {
            val result = try {
                SearchState.Results(downloader.search(query))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SearchState.Error(e.message?.lineSequence()?.firstOrNull())
            }
            _state.update { it.copy(search = result) }
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun confirm() {
        val state = _state.value
        val links = state.links.ifEmpty {
            // Ticked search results, in the order they are listed.
            (state.search as? SearchState.Results)?.items.orEmpty()
                .filter { it.id in state.selected }.map(SearchResult::link)
        }.ifEmpty { return }
        links.forEach(scheduler::enqueue)
        _state.update { it.copy(queued = true) }
    }

    fun reset() {
        previewJob?.cancel()
        searchJob?.cancel()
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
