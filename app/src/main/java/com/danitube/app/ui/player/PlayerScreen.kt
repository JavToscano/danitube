package com.danitube.app.ui.player

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import com.danitube.app.ui.library.ChannelAvatar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.danitube.app.R
import androidx.compose.foundation.layout.size
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.danitube.app.ui.library.VideoItem

/** Total pages in the pager; videos repeat (page % size) so swiping never runs out. */
private const val LOOP_PAGES = 1_000_000

/** Seconds a video must stay on screen before it counts as a play. */
private const val PLAY_COUNT_AFTER_MS = 3_000L

/** Full-screen, swipe-up-for-next player. Videos are shuffled and the next one starts when one ends. */
@Composable
fun PlayerScreen(
    startId: String?,
    favoritesOnly: Boolean,
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    LaunchedEffect(startId, favoritesOnly) { viewModel.start(startId, favoritesOnly) }
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val unmetered by viewModel.unmetered.collectAsStateWithLifecycle()

    ImmersiveMode()
    // Back goes to the library (harmless); the library itself swallows back, so she can't leave the app.
    BackHandler(onBack = onExit)

    val items = queue
    if (items.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else {
        key(items) {
            ShortsPager(
                items,
                unmetered = unmetered,
                onPlayed = viewModel::recordPlay,
                resolveStream = viewModel::streamUrl,
                onStreamFailed = viewModel::streamFailed,
            )
        }
    }
}

@Composable
private fun ShortsPager(
    items: List<VideoItem>,
    unmetered: Boolean,
    onPlayed: (String) -> Unit,
    resolveStream: suspend (String) -> String?,
    onStreamFailed: () -> Unit,
) {
    val size = items.size
    val scope = rememberCoroutineScope()
    val activity = LocalView.current.context as Activity
    // Portrait until she taps the rotate button, like the one in the YouTube player.
    var landscape by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(landscape) {
        activity.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
    // Start on a page that maps to items[0].
    val pagerState = rememberPagerState(initialPage = LOOP_PAGES / 2 / size * size) { LOOP_PAGES }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1, // next/previous page is prepared before the swipe lands
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            VideoPage(
                item = items[page % size],
                isCurrent = page == pagerState.currentPage,
                onEnded = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
                onPlayed = onPlayed,
                unmetered = unmetered,
                resolveStream = resolveStream,
                onStreamFailed = onStreamFailed,
            )
        }
        // Above the pager, so tapping it does not pause the video.
        IconButton(
            onClick = { landscape = !landscape },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 8.dp, bottom = 20.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_screen_rotation),
                contentDescription = stringResource(R.string.rotate_screen),
                tint = Color.White,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPage(
    item: VideoItem,
    isCurrent: Boolean,
    onEnded: () -> Unit,
    onPlayed: (String) -> Unit,
    unmetered: Boolean,
    resolveStream: suspend (String) -> String?,
    onStreamFailed: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            // A streamed video gets its media below, once its link is known and Wi-Fi is confirmed.
            item.video?.let {
                setMediaItem(MediaItem.fromUri(it))
                prepare()
            }
        }
    }
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentIsCurrent by rememberUpdatedState(isCurrent)
    val currentOnStreamFailed by rememberUpdatedState(onStreamFailed)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            // Only the visible video advances the feed.
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && currentIsCurrent) currentOnEnded()
            }

            // A stream that cannot play (link expired, signal lost) is skipped, not shown broken.
            override fun onPlayerError(error: PlaybackException) {
                if (!item.isStream) return
                currentOnStreamFailed()
                if (currentIsCurrent) currentOnEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Streamed videos: only on Wi-Fi. Off Wi-Fi they never start, and the visible one is skipped.
    if (item.isStream) {
        LaunchedEffect(item.id, unmetered) {
            if (!unmetered) {
                // Stop loading too: nothing may keep downloading over mobile data.
                player.stop()
                player.clearMediaItems()
            } else if (player.mediaItemCount == 0) {
                val url = resolveStream(item.id)
                if (url != null) {
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                } else {
                    currentOnStreamFailed()
                    if (currentIsCurrent) currentOnEnded()
                }
            }
        }
        LaunchedEffect(isCurrent, unmetered) { if (isCurrent && !unmetered) currentOnEnded() }
    }

    // A video counts as played once it has been on screen for a few seconds (feeds Favoritos).
    LaunchedEffect(isCurrent, item.id) {
        if (isCurrent) {
            delay(PLAY_COUNT_AFTER_MS)
            onPlayed(item.id)
        }
    }

    var userPaused by remember { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    LaunchedEffect(isCurrent, userPaused, resumed) {
        player.playWhenReady = isCurrent && !userPaused && resumed
        if (!isCurrent) {
            player.seekTo(0)
            userPaused = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT // black bars, whole video visible
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    keepScreenOn = true
                    this.player = player
                }
            },
        )
        ShortsOverlay(item)
        // Tap anywhere toggles pause; sits above the PlayerView and overlay so it always receives the tap.
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { userPaused = !userPaused },
        )
        if (userPaused) {
            Text("▶", color = Color.White.copy(alpha = 0.8f), fontSize = 72.sp, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/** Hides the status bar while the player is on screen; the navigation bar/gesture area stays usable. */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Only the status bar: hiding the navigation bar too can swallow the first back swipe.
        controller.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

/** Purely visual: channel and title over the video, like a shorts feed. Not tappable. */
@Composable
private fun ShortsOverlay(item: VideoItem) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChannelAvatar(size = 32.dp)
                Text("@DaniTube", color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
            Text(item.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
