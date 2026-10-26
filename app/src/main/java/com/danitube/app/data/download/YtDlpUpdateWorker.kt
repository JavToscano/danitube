package com.danitube.app.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps yt-dlp fresh in the background so a download never starts with a broken copy. */
@HiltWorker
class YtDlpUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val downloader: YtDlpDownloader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (withContext(Dispatchers.IO) { downloader.update() }) Result.success() else Result.retry()

    companion object {
        const val NAME = "ytdlp-update"
    }
}
