package com.danitube.app.ui.parent

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import android.text.format.Formatter
import com.danitube.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ParentMenuScreen(
    onAddVideo: () -> Unit,
    onManageVideos: () -> Unit,
    onBackToKidMode: () -> Unit,
    onBackup: () -> Unit,
    onChangePin: () -> Unit,
    onExitApp: () -> Unit,
    storageViewModel: StorageViewModel = hiltViewModel(),
    suggestionsViewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val storage by storageViewModel.info.collectAsStateWithLifecycle()
    val suggestionsOn by suggestionsViewModel.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val button = Modifier.fillMaxWidth().height(56.dp)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.parent_area), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 4.dp))
        storage?.let {
            Text(
                stringResource(
                    R.string.storage_summary,
                    it.videoCount,
                    Formatter.formatShortFileSize(context, it.usedBytes),
                    Formatter.formatShortFileSize(context, it.freeBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        FilledTonalButton(onClick = onAddVideo, shape = CircleShape, modifier = button) { Text(stringResource(R.string.add_video)) }
        FilledTonalButton(onClick = onManageVideos, shape = CircleShape, modifier = button) { Text(stringResource(R.string.manage_videos)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.suggestions_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.suggestions_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = suggestionsOn, onCheckedChange = suggestionsViewModel::setEnabled)
        }
        if (suggestionsOn) {
            TextButton(onClick = suggestionsViewModel::searchNow) { Text(stringResource(R.string.suggestions_search_now)) }
        }
        FilledTonalButton(onClick = onBackup, shape = CircleShape, modifier = button) { Text(stringResource(R.string.backup_title)) }
        Button(onClick = onBackToKidMode, shape = CircleShape, modifier = button) { Text(stringResource(R.string.back_to_app)) }
        FilledTonalButton(onClick = onChangePin, shape = CircleShape, modifier = button) { Text(stringResource(R.string.change_pin)) }
        OutlinedButton(
            onClick = onExitApp,
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = button,
        ) { Text(stringResource(R.string.exit_app)) }
    }
}
