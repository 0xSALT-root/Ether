package com.example.ether.ui.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ether.data.model.UserAgentType
import com.example.ether.data.repository.SettingsRepository
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebExtension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlTray(
    url: String,
    onUrlChange: (String) -> Unit,
    onUrlSubmit: () -> Unit,
    blockedCount: Int,
    onShieldClick: () -> Unit,
    extensions: List<WebExtension>,
    pinnedExtensionIds: Set<String>,
    onExtensionClick: (WebExtension) -> Unit,
    onManageExtensionsClick: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onExtensionButtonAnchor: (android.view.View) -> Unit,
    isKidsLock: Boolean = false,
    onSettingsClick: () -> Unit = {},
    onExtensionOptionsClick: (WebExtension) -> Unit = {},
    settingsRepository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    var showExtensionMenu by remember { mutableStateOf(false) }
    val displayExtensions = if (pinnedExtensionIds.isEmpty()) extensions else extensions.filter { pinnedExtensionIds.contains(it.id) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShieldClick) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Privacy Shield",
                        tint = if (blockedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    if (blockedCount > 0) {
                        Text(
                            text = blockedCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                                .padding(horizontal = 4.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                        )
                    }
                }
            }

            TextField(
                value = url,
                onValueChange = onUrlChange,
                enabled = !isKidsLock,
                modifier = Modifier.weight(1f).heightIn(max = 56.dp),
                placeholder = { Text("Search or enter URL", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onUrlSubmit() })
            )

            if (isKidsLock) {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Box(contentAlignment = Alignment.Center) {
                    AndroidView(
                        modifier = Modifier.size(48.dp),
                        factory = { ctx ->
                            android.view.View(ctx).apply {
                                isClickable = false
                                isFocusable = false
                                onExtensionButtonAnchor(this)
                            }
                        }
                    )

                    IconButton(onClick = { showExtensionMenu = true }) {
                        Icon(Icons.Default.Extension, contentDescription = "Extensions", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(
                        expanded = showExtensionMenu,
                        onDismissRequest = { showExtensionMenu = false },
                        modifier = Modifier.widthIn(min = 220.dp)
                    ) {
                        if (displayExtensions.isNotEmpty()) {
                            Text(
                                "EXTENSIONS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                            displayExtensions.forEach { ext ->
                                val optionsUrl = ext.metaData.optionsPageUrl
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(ext.metaData.name ?: ext.id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (!ext.metaData.version.isNullOrBlank()) {
                                                Text("v${ext.metaData.version}", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    },
                                    onClick = {
                                        onExtensionClick(ext)
                                        showExtensionMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (pinnedExtensionIds.contains(ext.id)) Icons.Default.PushPin else Icons.Default.Extension,
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (optionsUrl != null) {
                                            IconButton(
                                                onClick = {
                                                    onExtensionOptionsClick(ext)
                                                    showExtensionMenu = false
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Settings, contentDescription = "Options", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        Text(
                            "TOOLS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        var showUAMenu by remember { mutableStateOf(false) }
                        Box {
                            DropdownMenuItem(
                                text = { Text("Browse As...") },
                                onClick = { showUAMenu = true },
                                leadingIcon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                                trailingIcon = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = showUAMenu,
                                onDismissRequest = { showUAMenu = false }
                            ) {
                                UserAgentType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.title) },
                                        onClick = {
                                            scope.launch {
                                                settingsRepository.updateUserAgentType(type)
                                                showUAMenu = false
                                                showExtensionMenu = false
                                                onRefresh() 
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        DropdownMenuItem(
                            text = { Text("Manage Extensions") },
                            onClick = {
                                onManageExtensionsClick()
                                showExtensionMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
