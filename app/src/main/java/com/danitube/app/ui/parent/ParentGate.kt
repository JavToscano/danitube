package com.danitube.app.ui.parent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.danitube.app.R
import com.danitube.app.data.PinStore
import com.danitube.app.data.PinStore.Companion.PIN_LENGTH
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PinViewModel @Inject constructor(private val store: PinStore) : ViewModel() {
    fun hasPin() = store.hasPin()
    fun verify(pin: String) = store.verify(pin)
    fun save(pin: String) = store.setPin(pin)
}

/**
 * Keeps a small child out of the parent area. The first time (no PIN stored yet) it asks the
 * parent to create one; afterwards it asks for it. With [forceCreate] it always asks for a new PIN.
 */
@Composable
fun ParentGate(
    onPass: () -> Unit,
    onCancel: () -> Unit,
    forceCreate: Boolean = false,
    viewModel: PinViewModel = hiltViewModel(),
) {
    // Decided once so the dialog doesn't flip to "enter PIN" right after saving a new one.
    val creating = remember { forceCreate || !viewModel.hasPin() }
    if (creating) {
        CreatePinDialog(onSave = { viewModel.save(it); onPass() }, onCancel = onCancel)
    } else {
        EnterPinDialog(check = viewModel::verify, onPass = onPass, onCancel = onCancel)
    }
}

@Composable
private fun EnterPinDialog(check: (String) -> Boolean, onPass: () -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.pin_title)) },
        text = {
            Column {
                Text(stringResource(R.string.pin_enter), style = MaterialTheme.typography.titleMedium)
                PinField(
                    value = pin,
                    onValueChange = { pin = it; wrong = false },
                    label = stringResource(R.string.pin_label),
                    error = if (wrong) stringResource(R.string.pin_wrong) else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == PIN_LENGTH,
                onClick = { if (check(pin)) onPass() else { pin = ""; wrong = true } },
            ) { Text(stringResource(R.string.pin_enter_action)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CreatePinDialog(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.pin_create_title)) },
        text = {
            Column {
                Text(stringResource(R.string.pin_create_hint), style = MaterialTheme.typography.bodyMedium)
                PinField(pin, { pin = it; mismatch = false }, stringResource(R.string.pin_label), error = null)
                PinField(
                    confirm,
                    { confirm = it; mismatch = false },
                    stringResource(R.string.pin_confirm_label),
                    error = if (mismatch) stringResource(R.string.pin_mismatch) else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == PIN_LENGTH && confirm.length == PIN_LENGTH,
                onClick = { if (pin == confirm) onSave(pin) else { confirm = ""; mismatch = true } },
            ) { Text(stringResource(R.string.pin_save)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(PIN_LENGTH)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.padding(top = 12.dp),
    )
}
