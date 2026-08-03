package com.example.ether.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ether.browser.GeckoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.WebExtension
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionScreen(
    onBack: () -> Unit,
    onVisitStore: () -> Unit,
    geckoManager: GeckoManager,
    viewModel: ExtensionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedExtensions by viewModel.extensions.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    
    val extensionPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            Toast.makeText(context, "Preparing extension...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val tempFile = try {
                    withContext(Dispatchers.IO) {
                        copyUriToTempFile(context, selectedUri)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to copy extension file")
                    null
                }

                if (tempFile != null && tempFile.exists()) {
                    Toast.makeText(context, "Installing extension...", Toast.LENGTH_SHORT).show()
                    geckoManager.runtime.webExtensionController.install("file://${tempFile.absolutePath}")
                        .accept(
                            { 
                                viewModel.refreshExtensions()
                                tempFile.delete()
                            },
                            { err -> 
                                Timber.e(err, "Installation failed")
                                tempFile.delete()
                                Toast.makeText(context, "Installation failed: ${err?.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                } else {
                    Toast.makeText(context, "Failed to read extension file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(text = "Add-ons", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Manage your Firefox extensions. Long-press to copy UUID or remove.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (installedExtensions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No extensions installed.")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(installedExtensions, key = { it.id }) { ext ->
                            val isPinned = appSettings.pinnedExtensions.contains(ext.id)
                            DynamicExtensionItem(
                                extension = ext,
                                onToggle = { viewModel.toggleExtension(ext, it) },
                                onDelete = { viewModel.uninstallExtension(ext) },
                                onDoubleClick = { 
                                    val override = appSettings.extensionUuidOverrides[ext.id]
                                    if (!override.isNullOrBlank()) {
                                        geckoManager.navigate(override)
                                    } else {
                                        geckoManager.openOptions(ext)
                                    }
                                },
                                onUpdateUuid = { newUuid -> viewModel.updateExtensionUuid(ext.id, newUuid) },
                                initialUuidUrl = appSettings.extensionUuidOverrides[ext.id] ?: ext.metaData.baseUrl,
                                isPinned = isPinned,
                                onTogglePin = { viewModel.togglePinnedExtension(ext.id, !isPinned) }
                            )
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onVisitStore,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Visit Store")
                }
                OutlinedButton(
                    onClick = { extensionPicker.launch("*/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Install .xpi")
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DynamicExtensionItem(
    extension: WebExtension,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDoubleClick: () -> Unit,
    onUpdateUuid: (String) -> Unit,
    initialUuidUrl: String,
    isPinned: Boolean,
    onTogglePin: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showUuidDialog by remember { mutableStateOf(false) }
    var uuidUrl by remember { mutableStateOf(initialUuidUrl) }
    var uuidError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(initialUuidUrl) {
        uuidUrl = initialUuidUrl
    }

    if (showUuidDialog) {
        AlertDialog(
            onDismissRequest = { 
                showUuidDialog = false
                uuidError = null
                uuidUrl = initialUuidUrl
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("Edit Extension UUID") },
            text = {
                Column {
                    Text(
                        text = "Modify the extension's base URL/UUID. Format: moz-extension://<UUID>/",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = uuidUrl,
                        onValueChange = { 
                            uuidUrl = it
                            uuidError = null
                        },
                        label = { Text("Extension URL") },
                        isError = uuidError != null,
                        supportingText = {
                            if (uuidError != null) {
                                Text(text = uuidError!!, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!uuidUrl.startsWith("moz-extension://")) {
                            uuidError = "Invalid format. Must start with moz-extension://"
                        } else {
                            onUpdateUuid(uuidUrl)
                            Toast.makeText(context, "URL updated successfully", Toast.LENGTH_SHORT).show()
                            showUuidDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showUuidDialog = false
                    uuidError = null
                    uuidUrl = initialUuidUrl
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    onClick = { },
                    onDoubleClick = onDoubleClick,
                    onLongClick = { showMenu = true }
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = extension.metaData.name ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                    extension.metaData.description?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                
                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin extension" else "Pin extension",
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(checked = extension.metaData.enabled, onCheckedChange = onToggle)
            }
        }

        DropdownMenu(
            expanded = showMenu, 
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
        ) {
            DropdownMenuItem(
                text = { Text("Copy UUID", color = MaterialTheme.colorScheme.onSurface) },
                onClick = { 
                    showMenu = false
                    val baseUrl = extension.metaData.baseUrl // moz-extension://uuid/
                    val uuid = baseUrl.removePrefix("moz-extension://").removeSuffix("/")
                    copyToClipboard(context, uuid)
                    Toast.makeText(context, "UUID copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface)
            )
            DropdownMenuItem(
                text = { Text("Edit Extension UUID", color = MaterialTheme.colorScheme.onSurface) },
                onClick = { 
                    showMenu = false
                    showUuidDialog = true
                },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface)
            )
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onDelete() },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Extension UUID", text)
    clipboard.setPrimaryClip(clip)
}

private fun copyUriToTempFile(context: android.content.Context, uri: Uri): File? {
    val contentResolver = context.contentResolver
    val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
    } ?: "temp_extension.xpi"

    val tempFile = File(context.cacheDir, fileName)
    contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
            input.copyTo(output)
        }
    }
    return tempFile
}
