package com.example.ether.ui.browser.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExtensionUuidDialog(
    extensionName: String,
    currentUuid: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onNavigateToOptions: () -> Unit
) {
    var uuid by remember { mutableStateOf(currentUuid) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit UUID Override") },
        text = {
            Column {
                Text(
                    text = "Extension: $extensionName",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                TextField(
                    value = uuid,
                    onValueChange = { uuid = it },
                    label = { Text("Custom URL or UUID") },
                    placeholder = { Text("moz-extension://uuid/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "If set, clicking the extension will navigate to this URL instead of triggering its default action.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onNavigateToOptions) {
                    Text("Open Options")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onConfirm(uuid) }) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
