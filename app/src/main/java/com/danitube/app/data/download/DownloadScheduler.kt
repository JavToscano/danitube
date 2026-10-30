package com.danitube.app.data.download

import java.util.UUID
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadScheduler @Inject constructor(private val workManager: WorkManager) {

    /**
     * Queues a download. Each link is its own job (so one can be cancelled without touching the
     * others) and the downloader runs them one at a time. A link that is already queued or
     * running is ignored; one that failed or was cancelled can be queued again. With [unmeteredOnly]
     * it waits for Wi-Fi (used by auto-downloads); manual downloads go over any connection.
     */
    fun enqueue(link: YoutubeLink, unmeteredOnly: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_URL to link.url, DownloadWorker.KEY_PLAYLIST to link.isPlaylist))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(DownloadWorker.TAG)
            .addTag(link.url)
            .apply { if (link.isPlaylist) addTag(DownloadWorker.TAG_PLAYLIST) }
            .build()
        workManager.enqueueUniqueWork(link.url, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(id: UUID) {
        workManager.cancelWorkById(id)
    }

    /** Queues the link again and drops the old failed/cancelled entries from the list. */
    fun retry(link: YoutubeLink) {
        enqueue(link)
        workManager.pruneWork()
    }

    fun observe(): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(DownloadWorker.TAG)

    fun clearFinished() = workManager.pruneWork()
}
