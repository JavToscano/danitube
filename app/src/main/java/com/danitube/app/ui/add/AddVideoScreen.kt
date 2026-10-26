package com.danitube.app.ui.add

import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import com.danitube.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import coil.compose.AsyncImage

/**
 * Parent-only screen: paste or receive a link, confirm the preview, watch the queue.
 * [initialText] is the shared text when opened via the Android share sheet.
 */
@Composable
fun AddVideoScreen(
    onClose: () -> Unit,
    initialText: String? = null,
    viewModel: AddVideoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    // Android 13+ needs permission to show the download notification; downloads work without it.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) viewModel.onInput(initialText)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.add_video), style = MaterialTheme.typography.headlineSmall)
        }

        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::onInput,
            label = { Text(stringResource(R.string.youtube_link)) },
            supportingText = { Text(stringResource(R.string.youtube_link_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = {
                TextButton(onClick = { clipboard.getText()?.text?.let(viewModel::onInput) }) { Text(stringResource(R.string.paste)) }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        when (val preview = state.preview) {
            PreviewState.None -> Unit
            PreviewState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(32.dp))
            }
            is PreviewState.Error -> Column {
                Text(stringResource(preview.messageRes), color = MaterialTheme.colorScheme.error)
                preview.detail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is PreviewState.Ready -> PreviewCard(preview, isPlaylist = state.link?.isPlaylist == true)
        }

        Button(
            onClick = { viewModel.confirm(); viewModel.reset() },
            enabled = state.preview is PreviewState.Ready,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(if (state.link?.isPlaylist == true) R.string.download_playlist else R.string.download)) }

        if (downloads.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.downloads), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = viewModel::clearFinished) { Text(stringResource(R.string.clear_finished)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(downloads, key = { it.id }) {
                    DownloadRow(it, onCancel = { item -> viewModel.cancel(item) }, onRetry = { item -> viewModel.retry(item) })
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(preview: PreviewState.Ready, isPlaylist: Boolean) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AsyncImage(
            model = preview.preview.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.padding(14.dp)) {
            Text(preview.preview.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (isPlaylist) {
                Text(
                    stringResource(R.string.playlist_first_shown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, onCancel: (DownloadItem) -> Unit, onRetry: (DownloadItem) -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.url.removePrefix("https://www.youtube.com/"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                when (item.state) {
                    // Progress stays at 0 while waiting for the previous download and while yt-dlp starts.
                    WorkInfo.State.RUNNING -> if (item.progress > 0f) {
                        LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    } else {
                        Status(stringResource(R.string.status_preparing), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        Status(stringResource(R.string.status_waiting), MaterialTheme.colorScheme.onSurfaceVariant)
                    WorkInfo.State.SUCCEEDED -> Status(stringResource(R.string.status_done), MaterialTheme.colorScheme.secondary)
                    WorkInfo.State.FAILED -> Status(stringResource(R.string.status_failed, item.error.orEmpty()), MaterialTheme.colorScheme.error)
                    WorkInfo.State.CANCELLED -> Status(stringResource(R.string.status_cancelled), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (item.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                    TextButton(onClick = { onCancel(item) }) { Text(stringResource(R.string.cancel)) }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED ->
                    TextButton(onClick = { onRetry(item) }) { Text(stringResource(R.string.retry)) }
                WorkInfo.State.SUCCEEDED -> Unit
            }
        }
    }
}

@Composable
private fun Status(text: String, color: androidx.compose.ui.graphics.Color) =
    Text(text, color = color, style = MaterialTheme.typography.labelLarge)
