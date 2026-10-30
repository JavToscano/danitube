package com.danitube.app.data.suggest

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** The daily pass of [SuggestionEngine]; only scheduled for unmetered networks. */
@HiltWorker
class SuggestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SuggestionEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        engine.run()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val NAME = "suggestions"
        const val NAME_NOW = "suggestions-now"
    }
}
