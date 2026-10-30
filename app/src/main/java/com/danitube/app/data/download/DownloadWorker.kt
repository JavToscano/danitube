package com.danitube.app.data.download

import com.danitube.app.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.danitube.app.data.BackupManager
import com.danitube.app.data.VideoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloader: YtDlpDownloader,
    private val repository: VideoRepository,
    private val backup: BackupManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val isPlaylist = inputData.getBoolean(KEY_PLAYLIST, false)
        setForeground(getForegroundInfo())
        return try {
            val videos = downloader.download(YoutubeLink(url, isPlaylist)) { percent ->
                setProgressAsync(workDataOf(KEY_PROGRESS to percent))
            }
            if (videos.isEmpty()) {
                Result.failure(workDataOf(KEY_ERROR to applicationContext.getString(R.string.error_nothing_downloaded)))
            } else {
                // The download is done either way; a full or unplugged library folder must not fail
                // it. Videos that could not move stay playable from the app's private storage.
                runCatching { backup.store(videos) }
                repository.addDownloaded(videos)
                runCatching { backup.sync() }
                Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (e.message ?: applicationContext.getString(R.string.error_download_failed))))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.notification_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notification_downloading))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id.hashCode(), notification)
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_PLAYLIST = "playlist"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val TAG = "download"
        const val TAG_PLAYLIST = "download-playlist"
        private const val CHANNEL_ID = "downloads"
        private const val MAX_RETRIES = 2
    }
}
