package com.danitube.app.ui.parent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danitube.app.data.update.UpdateManager
import com.danitube.app.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updates: UpdateManager) : ViewModel() {

    val state: StateFlow<UpdateState> = updates.state
    val installedName: String = updates.installedName

    fun check() {
        viewModelScope.launch { updates.checkAndDownload() }
    }

    fun canInstall() = updates.canInstall()

    fun permissionIntent() = updates.permissionIntent()

    fun install() {
        viewModelScope.launch { updates.install() }
    }
}
