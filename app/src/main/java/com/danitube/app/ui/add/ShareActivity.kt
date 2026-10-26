package com.danitube.app.ui.add

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.danitube.app.ui.theme.DaniTubeTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.danitube.app.ui.parent.ParentGate
import dagger.hilt.android.AndroidEntryPoint

/**
 * Target of "Share > DaniTube". Kept separate from [com.danitube.app.ui.MainActivity] so the
 * kid-mode task (screen pinning) is never affected by it.
 * The parent gate comes first, so only an adult can add videos.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparentDark = SystemBarStyle.dark(Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparentDark, navigationBarStyle = transparentDark)
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
        setContent {
            DaniTubeTheme {
                var unlocked by rememberSaveable { mutableStateOf(false) }
                Surface {
                    if (unlocked) {
                        AddVideoScreen(onClose = ::finish, initialText = shared)
                    } else {
                        ParentGate(onPass = { unlocked = true }, onCancel = ::finish)
                    }
                }
            }
        }
    }
}
