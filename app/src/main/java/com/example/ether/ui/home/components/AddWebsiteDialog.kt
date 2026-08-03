package com.example.ether.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ether.data.model.Website
import com.example.ether.ui.home.HomeViewModel

@Composable
fun AddWebsiteDialog(
    onDismiss: () -> Unit,
    viewModel: HomeViewModel,
    initialWebsite: Website? = null
) {
    val websites by viewModel.websites.collectAsState()
    var name by remember { mutableStateOf(initialWebsite?.name ?: "") }
    var url by remember { mutableStateOf(initialWebsite?.url ?: "") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val isFolder = initialWebsite?.isFolder == true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isFolder) Icons.Default.Folder else Icons.Default.Public, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (initialWebsite == null) "Add Website" else if (isFolder) "Edit Folder" else "Edit Website")
            }
        },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        errorText = null
                    },
                    label = { Text("Name") },
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                if (!isFolder) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = url,
                        onValueChange = { 
                            url = it
                            errorText = null
                        },
                        label = { Text("URL") },
                        placeholder = { Text("https://example.com") },
                        isError = errorText != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                if (initialWebsite != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = {
                            viewModel.deleteWebsite(initialWebsite)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete shortcut")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && (isFolder || url.isNotBlank())) {
                        val isDuplicate = !isFolder && websites.any { it.url == url && it.id != initialWebsite?.id }
                        if (isDuplicate) {
                            errorText = "This URL is already in your grid."
                        } else {
                            if (initialWebsite == null) {
                                viewModel.addWebsite(name, url)
                            } else {
                                viewModel.updateWebsite(initialWebsite.copy(name = name, url = url))
                            }
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (initialWebsite == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Cancel")
            }
        }
    )
}
