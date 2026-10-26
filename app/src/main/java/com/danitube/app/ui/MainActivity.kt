package com.danitube.app.ui

import com.danitube.app.ui.parent.BackupScreen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danitube.app.R
import com.danitube.app.ui.add.AddVideoScreen
import com.danitube.app.ui.library.LibraryScreen
import com.danitube.app.ui.parent.ManageVideosScreen
import com.danitube.app.ui.parent.ParentGate
import com.danitube.app.ui.parent.ParentMenuScreen
import com.danitube.app.ui.player.PlayerScreen
import com.danitube.app.ui.theme.DaniTubeTheme
import dagger.hilt.android.AndroidEntryPoint

/** Screens behind the parent gate. While none is open the app is in kid mode. */
private enum class ParentScreen { Menu, Add, Manage, Backup }

private enum class Tab { Home, Favorites }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparentDark = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparentDark, navigationBarStyle = transparentDark)
        setContent {
            DaniTubeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    var parentScreen by rememberSaveable { mutableStateOf<ParentScreen?>(null) }
                    var showGate by rememberSaveable { mutableStateOf(false) }
                    var changingPin by rememberSaveable { mutableStateOf(false) }
                    var playing by rememberSaveable { mutableStateOf(false) }
                    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
                    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
                    var startId by rememberSaveable { mutableStateOf<String?>(null) }
                    var searching by rememberSaveable { mutableStateOf(false) }
                    var query by rememberSaveable { mutableStateOf("") }

                    when (parentScreen) {
                        ParentScreen.Menu -> {
                            BackHandler { parentScreen = null }
                            ParentMenuScreen(
                                onAddVideo = { parentScreen = ParentScreen.Add },
                                onManageVideos = { parentScreen = ParentScreen.Manage },
                                onBackup = { parentScreen = ParentScreen.Backup },
                                onBackToKidMode = { parentScreen = null },
                                onChangePin = { changingPin = true },
                                onExitApp = { finishAffinity() },
                            )
                        }
                        ParentScreen.Add -> {
                            BackHandler { parentScreen = ParentScreen.Menu }
                            AddVideoScreen(onClose = { parentScreen = ParentScreen.Menu })
                        }
                        ParentScreen.Backup -> {
                            BackHandler { parentScreen = ParentScreen.Menu }
                            BackupScreen(onClose = { parentScreen = ParentScreen.Menu })
                        }
                        ParentScreen.Manage -> {
                            BackHandler { parentScreen = ParentScreen.Menu }
                            ManageVideosScreen(onClose = { parentScreen = ParentScreen.Menu })
                        }
                        null -> if (playing) {
                            // Back returns to the feed; it never leaves the app.
                            PlayerScreen(startId = startId, favoritesOnly = favoritesOnly, onExit = { playing = false })
                        } else {
                            // Back closes the search box; otherwise it is swallowed in kid mode.
                            BackHandler { if (searching) { searching = false; query = "" } }
                            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                                TopBar(
                                    searching = searching,
                                    query = query,
                                    onQueryChange = { query = it },
                                    onSearchToggle = { searching = !searching; if (!searching) query = "" },
                                    onSecretLongPress = { showGate = true },
                                )
                                Box(Modifier.weight(1f)) {
                                    LibraryScreen(
                                        query = query,
                                        favoritesOnly = tab == Tab.Favorites,
                                        onVideoClick = { startId = it; favoritesOnly = tab == Tab.Favorites; playing = true },
                                    )
                                }
                                BottomBar(
                                    selected = tab,
                                    onHome = { tab = Tab.Home },
                                    onShorts = { startId = null; favoritesOnly = false; playing = true },
                                    onFavorites = { tab = Tab.Favorites },
                                )
                            }
                        }
                    }

                    if (changingPin) {
                        ParentGate(onPass = { changingPin = false }, onCancel = { changingPin = false }, forceCreate = true)
                    }
                    if (showGate) {
                        ParentGate(
                            onPass = { showGate = false; parentScreen = ParentScreen.Menu },
                            onCancel = { showGate = false },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Logo on the left (press and hold = hidden parent entry), icons on the right. The search icon
 * swaps the bar for a search box that filters the downloaded videos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBar(
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchToggle: () -> Unit,
    onSecretLongPress: () -> Unit,
) {
    if (searching) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSearchToggle) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onSecretLongPress,
            ),
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(22.dp), tint = Color.White)
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        }
        Row {
            IconButton(onClick = onSearchToggle) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onHome: () -> Unit, onShorts: () -> Unit, onFavorites: () -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        indicatorColor = Color.Transparent,
        selectedIconColor = Color.White,
        selectedTextColor = Color.White,
        unselectedIconColor = Color.White,
        unselectedTextColor = Color.White,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        NavigationBarItem(
            selected = selected == Tab.Home,
            onClick = onHome,
            icon = { Icon(Icons.Rounded.Home, null) },
            label = { Text(stringResource(R.string.nav_home), maxLines = 1) },
            colors = colors,
        )
        NavigationBarItem(
            selected = false,
            onClick = onShorts,
            icon = { Icon(Icons.Rounded.PlayArrow, null) },
            label = { Text(stringResource(R.string.nav_shorts), maxLines = 1) },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == Tab.Favorites,
            onClick = onFavorites,
            icon = { Icon(Icons.Rounded.Favorite, null) },
            label = { Text(stringResource(R.string.nav_favorites), maxLines = 1) },
            colors = colors,
        )
    }
}
