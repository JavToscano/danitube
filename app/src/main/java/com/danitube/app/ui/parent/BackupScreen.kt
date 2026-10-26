package com.danitube.app.ui.parent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danitube.app.R

/** Parent-only: save the videos to a folder you choose, or bring them back from one. */
@Composable
fun BackupScreen(onClose: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val backupFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::backup)
    }
    val restoreFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::restore)
    }
    val busy = state is BackupState.Running
    val button = Modifier.fillMaxWidth().height(56.dp)

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
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            stringResource(R.string.backup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { backupFolder.launch(null) }, enabled = !busy, shape = CircleShape, modifier = button) {
            Text(stringResource(R.string.backup_action))
        }
        FilledTonalButton(onClick = { restoreFolder.launch(null) }, enabled = !busy, shape = CircleShape, modifier = button) {
            Text(stringResource(R.string.restore_action))
        }
        when (val s = state) {
            BackupState.Idle -> Unit
            is BackupState.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_running, s.done, s.total))
                if (s.total > 0) {
                    LinearProgressIndicator(progress = { s.done / s.total.toFloat() }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            is BackupState.Finished -> Text(
                stringResource(if (s.restored) R.string.restore_done else R.string.backup_done, s.count),
                color = MaterialTheme.colorScheme.secondary,
            )
            BackupState.NotFound -> Text(stringResource(R.string.backup_not_found), color = MaterialTheme.colorScheme.error)
            is BackupState.Failed -> Text(stringResource(R.string.backup_error, s.message), color = MaterialTheme.colorScheme.error)
        }
    }
}
