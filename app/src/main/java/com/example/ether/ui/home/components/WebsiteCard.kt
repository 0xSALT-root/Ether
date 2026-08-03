package com.example.ether.ui.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ether.data.model.Website

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebsiteCard(
    website: Website,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onOpenDesktop: () -> Unit,
    onMoveToFolder: (Long?) -> Unit,
    onClearData: () -> Unit = {},
    folders: List<Website> = emptyList(),
    showMenuExternal: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuMode by remember { mutableIntStateOf(0) } // 0: Main, 1: Move Selection

    LaunchedEffect(showMenuExternal) {
        if (showMenuExternal) menuMode = 0
    }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon/Favicon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (website.isFolder) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else if (website.faviconPath != null) {
                        AsyncImage(
                            model = website.faviconPath,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Text(
                    text = website.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        DropdownMenu(
            expanded = showMenuExternal,
            onDismissRequest = onMenuDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            if (menuMode == 0) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        onMenuDismiss()
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
                DropdownMenuItem(
                    text = { Text("Move to Folder") },
                    onClick = { menuMode = 1 },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
                if (!website.isFolder) {
                    DropdownMenuItem(
                        text = { Text("Desktop Mode") },
                        onClick = {
                            onMenuDismiss()
                            onOpenDesktop()
                        },
                        leadingIcon = { Icon(Icons.Default.DesktopWindows, contentDescription = null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    DropdownMenuItem(
                        text = { Text("Clear Data") },
                        onClick = {
                            onMenuDismiss()
                            onClearData()
                        },
                        leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        onMenuDismiss()
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error
                    )
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Back", fontWeight = FontWeight.Bold) },
                    onClick = { menuMode = 0 },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.primary,
                        leadingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DropdownMenuItem(
                    text = { Text("Home") },
                    onClick = {
                        onMenuDismiss()
                        onMoveToFolder(null)
                    },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = MaterialTheme.colorScheme.secondary
                    )
                )
                folders.filter { it.id != website.id }.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder.name) },
                        onClick = {
                            onMenuDismiss()
                            onMoveToFolder(folder.id)
                        },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}
