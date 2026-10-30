package com.danitube.app.ui.parent

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danitube.app.data.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupState {
    data object Idle : BackupState
    data class Running(val done: Int, val total: Int) : BackupState
    data class Finished(val count: Int) : BackupState
    data class Failed(val message: String) : BackupState
}

@HiltViewModel
class BackupViewModel @Inject constructor(private val manager: BackupManager) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /** The folder becomes the library: its videos are added, the app's own videos move into it. */
    fun useFolder(tree: Uri) = run { manager.useFolder(tree, ::progress) }

    private fun progress(done: Int, total: Int) {
        _state.value = BackupState.Running(done, total)
    }

    private fun run(block: suspend () -> Int) {
        if (_state.value is BackupState.Running) return
        _state.value = BackupState.Running(0, 0)
        viewModelScope.launch {
            _state.value = try {
                BackupState.Finished(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupState.Failed(e.message.orEmpty())
            }
        }
    }
}
