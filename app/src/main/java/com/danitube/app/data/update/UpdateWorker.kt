package com.danitube.app.data.update

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Daily check for a newer release; downloads it so the parent only has to tap Actualizar. */
@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updates: UpdateManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        updates.checkAndDownload()
        return Result.success()
    }

    companion object {
        const val NAME = "app-update"
    }
}
