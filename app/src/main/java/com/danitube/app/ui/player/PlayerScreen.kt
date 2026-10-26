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
import androidx.compose.foundation.layout.size
import android.content.pm.ActivityInfo
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
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
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

    ImmersiveMode()
    // Back goes to the library (harmless); the library itself swallows back, so she can't leave the app.
    BackHandler(onBack = onExit)

    val items = queue
    if (items.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else {
        key(items) { ShortsPager(items, onPlayed = viewModel::recordPlay) }
    }
}

@Composable
private fun ShortsPager(items: List<VideoItem>, onPlayed: (String) -> Unit) {
    val size = items.size
    val scope = rememberCoroutineScope()
    // Start on a page that maps to items[0].
    val pagerState = rememberPagerState(initialPage = LOOP_PAGES / 2 / size * size) { LOOP_PAGES }

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1, // next/previous page is prepared before the swipe lands
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) { page ->
        VideoPage(
            item = items[page % size],
            isCurrent = page == pagerState.currentPage,
            onEnded = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
            onPlayed = onPlayed,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPage(item: VideoItem, isCurrent: Boolean, onEnded: () -> Unit, onPlayed: (String) -> Unit) {
    val context = LocalContext.current
    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(item.video)))
            prepare()
        }
    }
    var videoSize by remember(player) { mutableStateOf(VideoSize.UNKNOWN) }
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentIsCurrent by rememberUpdatedState(isCurrent)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }

            // Only the visible video advances the feed.
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && currentIsCurrent) currentOnEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // A video counts as played once it has been on screen for a few seconds (feeds Favoritos).
    LaunchedEffect(isCurrent, item.id) {
        if (isCurrent) {
            delay(PLAY_COUNT_AFTER_MS)
            onPlayed(item.id)
        }
    }

    // Rotate the screen to match the video that is on screen (neighbouring pages don't count).
    val activity = LocalView.current.context as Activity
    LaunchedEffect(isCurrent, videoSize) {
        if (isCurrent && videoSize.width > 0 && videoSize.height > 0) {
            val swapped = videoSize.unappliedRotationDegrees % 180 != 0
            val landscape = if (swapped) videoSize.height > videoSize.width else videoSize.width > videoSize.height
            activity.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
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

/** Hides status and navigation bars while the player is on screen. */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            (view.context as Activity).requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
