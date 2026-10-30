package com.danitube.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.danitube.app.data.download.YtDlpUpdateWorker
import com.danitube.app.data.suggest.SuggestionWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class DaniTubeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        val request = PeriodicWorkRequestBuilder<YtDlpUpdateWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(YtDlpUpdateWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request)

        // Looks for videos like the ones she watches; Wi-Fi only, and not while the battery is low.
        val suggestions = PeriodicWorkRequestBuilder<SuggestionWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build(),
            )
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(SuggestionWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, suggestions)
    }
}
