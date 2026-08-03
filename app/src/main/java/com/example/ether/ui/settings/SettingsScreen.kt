package com.example.ether.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import kotlinx.coroutines.flow.collectLatest

import androidx.compose.ui.tooling.preview.Preview
import com.example.ether.data.model.AppSettings
import com.example.ether.ui.theme.EtherTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToThemes: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToVpnConfig: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri ->
            uri?.let { viewModel.createBackup(it) }
        }
    )

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.restoreBackup(it, context) }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.backupStatus.collectLatest { result ->
            if (result.isSuccess) {
                Toast.makeText(context, "Operation successful. Restart app to apply changes if restoring.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsContent(
        settings = settings,
        onBack = onBack,
        onNavigateToThemes = onNavigateToThemes,
        onNavigateToExtensions = onNavigateToExtensions,
        onNavigateToDownloads = onNavigateToDownloads,
        onNavigateToVpnConfig = onNavigateToVpnConfig,
        onToggleBiometricLock = viewModel::toggleBiometricLock,
        onToggleScreenshotProtection = viewModel::toggleScreenshotProtection,
        onToggleIncognito = viewModel::toggleIncognito,
        onToggleJavaScript = viewModel::toggleJavaScript,
        onToggleCookies = viewModel::toggleCookies,
        onToggleDesktopMode = viewModel::toggleDesktopMode,
        onToggleAutoplay = viewModel::toggleAutoplay,
        onTogglePopupBlocking = viewModel::togglePopupBlocking,
        onToggleDarkMode = viewModel::toggleDarkMode,
        onToggleAmoledMode = viewModel::toggleAmoledMode,
        onToggleLowDataMode = viewModel::toggleLowDataMode,
        onToggleUrlBarPosition = viewModel::toggleUrlBarPosition,
        onUpdateVpnAutoDisconnectTimeout = viewModel::updateVpnAutoDisconnectTimeout,
        onToggleKidsLock = viewModel::toggleKidsLock,
        onUpdateKidsLockPin = viewModel::updateKidsLockPin,
        onClearAllData = viewModel::clearAllData,
        onCreateBackup = { createBackupLauncher.launch("ether_backup_${System.currentTimeMillis()}.etherbackup") },
        onRestoreBackup = { restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: AppSettings,
    onBack: () -> Unit,
    onNavigateToThemes: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToVpnConfig: () -> Unit,
    onToggleBiometricLock: (Boolean) -> Unit,
    onToggleScreenshotProtection: (Boolean) -> Unit,
    onToggleIncognito: (Boolean) -> Unit,
    onToggleJavaScript: (Boolean) -> Unit,
    onToggleCookies: (Boolean) -> Unit,
    onToggleDesktopMode: (Boolean) -> Unit,
    onToggleAutoplay: (Boolean) -> Unit,
    onTogglePopupBlocking: (Boolean) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleAmoledMode: (Boolean) -> Unit,
    onToggleLowDataMode: (Boolean) -> Unit = {},
    onToggleUrlBarPosition: (Boolean) -> Unit = {},
    onUpdateVpnAutoDisconnectTimeout: (Int) -> Unit = {},
    onToggleKidsLock: (Boolean) -> Unit = {},
    onUpdateKidsLockPin: (String) -> Unit = {},
    onClearAllData: () -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSectionHeader(title = "Security & Privacy", icon = Icons.Default.Security)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column {
                        SettingsToggleItem(
                            title = "Biometric Lock",
                            subtitle = "Lock app on startup",
                            checked = settings.isBiometricLockEnabled,
                            onCheckedChange = onToggleBiometricLock
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Prevent Screenshots",
                            subtitle = "Hide previews in recents",
                            checked = settings.isScreenshotProtectionEnabled,
                            onCheckedChange = onToggleScreenshotProtection
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Incognito Mode",
                            subtitle = "Private browsing by default",
                            checked = settings.isIncognitoMode,
                            onCheckedChange = onToggleIncognito
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Kids Lock",
                            subtitle = "Restrict access to browser features",
                            checked = settings.isKidsLockEnabled,
                            onCheckedChange = onToggleKidsLock,
                            onTrailingIconClick = { showPinDialog = true },
                            trailingIcon = Icons.Default.Password
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(title = "Browser Engine", icon = Icons.Default.Language)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column {
                        SettingsToggleItem(
                            title = "JavaScript",
                            checked = settings.isJavaScriptEnabled,
                            onCheckedChange = onToggleJavaScript
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Cookies",
                            checked = settings.areCookiesEnabled,
                            onCheckedChange = onToggleCookies
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Block Popups",
                            checked = settings.isPopupBlockingEnabled,
                            onCheckedChange = onTogglePopupBlocking
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Autoplay",
                            checked = settings.isAutoplayEnabled,
                            onCheckedChange = onToggleAutoplay
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Default Desktop Mode",
                            checked = settings.isDesktopModeDefault,
                            onCheckedChange = onToggleDesktopMode
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Low-Data Mode",
                            subtitle = "Block fonts and scripts to save data",
                            checked = settings.isLowDataModeEnabled,
                            onCheckedChange = onToggleLowDataMode
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(title = "Navigation & Content", icon = Icons.Default.Explore)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column {
                        SettingsClickItem(
                            title = "VPN Configuration",
                            icon = Icons.Default.VpnLock,
                            onClick = onNavigateToVpnConfig
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        
                        // VPN Timeout Selection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("VPN Auto-disconnect", style = MaterialTheme.typography.bodyLarge)
                                Text("Wait time after closing site", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            var showTimeoutMenu by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { showTimeoutMenu = true }) {
                                    Text("${settings.vpnAutoDisconnectTimeout}m")
                                }
                                DropdownMenu(
                                    expanded = showTimeoutMenu, 
                                    onDismissRequest = { showTimeoutMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    listOf(0, 1, 5, 10, 30).forEach { mins ->
                                        DropdownMenuItem(
                                            text = { 
                                                Text(
                                                    if (mins == 0) "Immediate" else "${mins}m",
                                                    color = MaterialTheme.colorScheme.onSurface
                                                ) 
                                            },
                                            onClick = {
                                                onUpdateVpnAutoDisconnectTimeout(mins)
                                                showTimeoutMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsClickItem(
                            title = "Manage Extensions",
                            icon = Icons.Default.Extension,
                            onClick = onNavigateToExtensions
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsClickItem(
                            title = "View Downloads",
                            icon = Icons.Default.Download,
                            onClick = onNavigateToDownloads
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Bottom URL Bar",
                            subtitle = "Move search bar to the bottom",
                            checked = settings.isUrlBarBottom,
                            onCheckedChange = onToggleUrlBarPosition
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(title = "Personalization", icon = Icons.Default.Palette)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column {
                        SettingsClickItem(
                            title = "Select Theme",
                            subtitle = settings.selectedTheme,
                            icon = Icons.Default.ColorLens,
                            onClick = onNavigateToThemes
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "Dark Mode",
                            checked = settings.isDarkMode,
                            onCheckedChange = onToggleDarkMode
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsToggleItem(
                            title = "AMOLED Mode",
                            subtitle = "Pure black backgrounds",
                            checked = settings.isAmoledModeEnabled,
                            onCheckedChange = onToggleAmoledMode
                        )
                    }
                }
            }

            item {
                SettingsSectionHeader(title = "Data Management", icon = Icons.Default.SettingsBackupRestore)
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column {
                        SettingsClickItem(
                            title = "Create Backup",
                            icon = Icons.Default.CloudUpload,
                            onClick = onCreateBackup
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        SettingsClickItem(
                            title = "Restore Backup",
                            icon = Icons.Default.CloudDownload,
                            onClick = onRestoreBackup
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onClearAllData,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data")
                }
            }
        }

        if (showPinDialog) {
            SetPinDialog(
                currentPin = settings.kidsLockPin,
                onDismiss = { showPinDialog = false },
                onSave = {
                    onUpdateKidsLockPin(it)
                    showPinDialog = false
                }
            )
        }
    }
}

@Composable
fun SetPinDialog(
    currentPin: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pin by remember { mutableStateOf(currentPin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set Kids Lock PIN")
            }
        },
        text = {
            TextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text("New PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
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
        },
        confirmButton = {
            Button(
                onClick = { onSave(pin) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save")
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

@Composable
fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SettingsClickItem(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline) },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingIcon != null && onTrailingIconClick != null) {
                    IconButton(onClick = onTrailingIconClick) {
                        Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    thumbContent = if (checked) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else null
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    EtherTheme {
        SettingsContent(
            settings = AppSettings(),
            onBack = {},
            onNavigateToThemes = {},
            onNavigateToExtensions = {},
            onNavigateToDownloads = {},
            onNavigateToVpnConfig = {},
            onToggleBiometricLock = {},
            onToggleScreenshotProtection = {},
            onToggleIncognito = {},
            onToggleJavaScript = {},
            onToggleCookies = {},
            onToggleDesktopMode = {},
            onToggleAutoplay = {},
            onTogglePopupBlocking = {},
            onToggleDarkMode = {},
            onToggleAmoledMode = {},
            onToggleKidsLock = {},
            onUpdateKidsLockPin = {},
            onClearAllData = {},
            onCreateBackup = {},
            onRestoreBackup = {}
        )
    }
}
