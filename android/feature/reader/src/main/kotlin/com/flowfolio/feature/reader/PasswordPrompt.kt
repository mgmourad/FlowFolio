package com.flowfolio.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** Asks for a PDF's password. The password is passed to PDFium only; it is never stored. */
@Composable
internal fun PasswordPrompt(wrongPassword: Boolean, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.reader_password_title)) },
        text = {
            Column {
                if (wrongPassword) {
                    Text(
                        stringResource(R.string.reader_password_wrong),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.reader_password_label)) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(password) }) { Text(stringResource(R.string.reader_password_open)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.reader_back)) } }
    )
}
