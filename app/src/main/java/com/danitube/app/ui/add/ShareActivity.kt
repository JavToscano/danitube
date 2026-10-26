package com.danitube.app.ui.add

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.danitube.app.R
import com.danitube.app.data.download.DownloadScheduler
import com.danitube.app.data.download.YoutubeLink
import com.danitube.app.ui.theme.DaniTubeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Target of "Share > DaniTube". A valid YouTube link is queued for download right away, with no
 * PIN and no preview (fetching the metadata can be slow), and the screen closes so you land back
 * in the YouTube app. Anything else opens the regular add screen so the problem is visible.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {

    @Inject lateinit var scheduler: DownloadScheduler

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparentDark = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparentDark, navigationBarStyle = transparentDark)

        val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val link = YoutubeLink.parse(shared)
        if (link == null) {
            setContent {
                DaniTubeTheme { Surface { AddVideoScreen(onClose = ::finish, initialText = shared) } }
            }
            return
        }

        scheduler.enqueue(link)
        Toast.makeText(this, R.string.share_queued, Toast.LENGTH_LONG).show()
        if (needsNotificationPermission()) {
            // Downloads work without it; it only shows the progress notification.
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish()
        }
    }

    private fun needsNotificationPermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
}
