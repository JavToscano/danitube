package com.danitube.app.ui.parent

import androidx.lifecycle.ViewModel
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.danitube.app.data.suggest.SuggestionSettings
import com.danitube.app.data.suggest.SuggestionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val settings: SuggestionSettings,
    private val workManager: WorkManager,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settings.enabled

    fun setEnabled(value: Boolean) = settings.setEnabled(value)

    /** Looks for new suggestions now instead of waiting for the daily run (still Wi-Fi only). */
    fun searchNow() {
        val request = OneTimeWorkRequestBuilder<SuggestionWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        workManager.enqueueUniqueWork(SuggestionWorker.NAME_NOW, ExistingWorkPolicy.KEEP, request)
    }
}
