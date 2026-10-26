package com.danitube.app.ui.parent

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danitube.app.data.BackupManager
import com.danitube.app.data.BackupNotFoundException
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
    data class Finished(val count: Int, val restored: Boolean) : BackupState
    data object NotFound : BackupState
    data class Failed(val message: String) : BackupState
}

@HiltViewModel
class BackupViewModel @Inject constructor(private val manager: BackupManager) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun backup(tree: Uri) = run(restored = false) { manager.backup(tree, ::progress) }

    fun restore(tree: Uri) = run(restored = true) { manager.restore(tree, ::progress) }

    private fun progress(done: Int, total: Int) {
        _state.value = BackupState.Running(done, total)
    }

    private fun run(restored: Boolean, block: suspend () -> Int) {
        if (_state.value is BackupState.Running) return
        _state.value = BackupState.Running(0, 0)
        viewModelScope.launch {
            _state.value = try {
                BackupState.Finished(block(), restored)
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupNotFoundException) {
                BackupState.NotFound
            } catch (e: Exception) {
                BackupState.Failed(e.message.orEmpty())
            }
        }
    }
}
