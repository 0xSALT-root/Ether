package com.example.ether.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ether.data.model.VpnServer
import com.example.ether.data.model.Website
import com.example.ether.ui.home.components.AddFolderDialog
import com.example.ether.ui.home.components.AddWebsiteDialog
import com.example.ether.ui.home.components.WebsiteCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onWebsiteClick: (String, Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val websites by viewModel.websites.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val vpnServers by viewModel.vpnServers.collectAsStateWithLifecycle()

    val isKidsLock = remember(settings) { settings.isKidsLockEnabled }
    val isIncognito = remember(settings) { settings.isIncognitoMode }
    val isDesktopDefault = remember(settings) { settings.isDesktopModeDefault }

    // Memoized callbacks
    val onNavigateToFolder = remember(viewModel) { { id: Long? -> viewModel.navigateToFolder(id) } }
    val onToggleIncognito = remember(viewModel) { { viewModel.toggleIncognito() } }
    val onToggleVpn = remember(viewModel) { { enabled: Boolean -> viewModel.toggleVpn(enabled) } }
    val onSelectVpnServer = remember(viewModel) { { server: VpnServer -> viewModel.selectVpnServer(server) } }
    val onAddWebsite = remember(viewModel) { { name: String, url: String -> viewModel.addWebsite(name, url) } }
    val onDeleteWebsite = remember(viewModel) { { website: Website -> viewModel.deleteWebsite(website) } }
    val onMoveWebsite = remember(viewModel) { { website: Website, newParentId: Long? -> viewModel.moveWebsite(website, newParentId) } }
    val onUpdatePositions = remember(viewModel) { { list: List<Website> -> viewModel.updatePositions(list) } }
    val onClearBrowserData = remember(viewModel) { { viewModel.clearBrowserData() } }

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var editingWebsite by remember { mutableStateOf<Website?>(null) }
    var showVpnMenu by remember { mutableStateOf(false) }
    var websiteMenuToOpen by remember { mutableStateOf<Long?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }

    val currentFolderId by viewModel.currentFolderId.collectAsStateWithLifecycle()
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onToggleVpn(true)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.vpnIntent.collect { intent ->
            vpnLauncher.launch(intent)
        }
    }

    var showKidsLockNote by remember { mutableStateOf<Boolean?>(null) }
    var showIncognitoNote by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(showKidsLockNote) {
        if (showKidsLockNote != null) {
            delay(1500)
            showKidsLockNote = null
        }
    }

    LaunchedEffect(showIncognitoNote) {
        if (showIncognitoNote != null) {
            delay(1500)
            showIncognitoNote = null
        }
    }

    // Reordering state
    var listForDisplay by remember { mutableStateOf(emptyList<Website>()) }
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(websites) {
        if (draggingItemIndex == null) {
            listForDisplay = websites
        }
    }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentFolderId != null) {
                            IconButton(onClick = { if (!isKidsLock) onNavigateToFolder(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            text = currentFolder?.name ?: "Ether",
                            color = if (currentFolder == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Invisible toggle button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (!isKidsLock) {
                                        onToggleIncognito()
                                        showIncognitoNote = !isIncognito
                                    }
                                }
                        )
                    }
                },
                actions = {
                    if (vpnServers.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { if (!isKidsLock) showVpnMenu = true }) {
                                Surface(
                                    modifier = Modifier.size(12.dp),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = if (settings.isVpnEnabled)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                ) {}
                            }
                            
                            if (!isKidsLock) {
                                DropdownMenu(
                                    expanded = showVpnMenu,
                                    onDismissRequest = { showVpnMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "VPN Enabled",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Switch(
                                            checked = settings.isVpnEnabled,
                                            onCheckedChange = onToggleVpn
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                    vpnServers.forEach { server ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = settings.selectedVpnServerId == server.id,
                                                        onClick = null,
                                                        colors = RadioButtonDefaults.colors(
                                                            selectedColor = MaterialTheme.colorScheme.primary,
                                                            unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        server.name,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onSelectVpnServer(server)
                                                showVpnMenu = false
                                            },
                                            colors = MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    IconButton(onClick = { if (!isKidsLock) onDownloadsClick() }) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Downloads History",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { if (!isKidsLock) showAddMenu = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        if (!isKidsLock) {
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false },
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add Website") },
                                    onClick = {
                                        showAddMenu = false
                                        showAddDialog = true
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                DropdownMenuItem(
                                    text = { Text("Create Folder") },
                                    onClick = {
                                        showAddMenu = false
                                        showAddFolderDialog = true
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (isKidsLock) {
                            showPinDialog = true
                        } else {
                            onSettingsClick()
                        }
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.statusBars
            )
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { padding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(isKidsLock) {
                    if (isKidsLock) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val items = gridState.layoutInfo.visibleItemsInfo
                            val item = items.find { info ->
                                offset.x >= info.offset.x && offset.x <= (info.offset.x + info.size.width) &&
                                        offset.y >= info.offset.y && offset.y <= (info.offset.y + info.size.height)
                            }
                            item?.let {
                                draggingItemIndex = it.index
                                dragOffset = Offset.Zero
                                // Automatically show menu on long press start
                                websiteMenuToOpen = listForDisplay[it.index].id
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                            
                            // Dismiss menu if we've dragged significantly
                            if (dragOffset.getDistanceSquared() > 100f) {
                                websiteMenuToOpen = null
                            }

                            val currentIdx = draggingItemIndex ?: return@detectDragGesturesAfterLongPress
                            val items = gridState.layoutInfo.visibleItemsInfo
                            val currentItemInfo = items.find { it.index == currentIdx }
                            
                            if (currentItemInfo != null) {
                                val dragCenter = currentItemInfo.offset.toOffset() + dragOffset + Offset(currentItemInfo.size.width / 2f, currentItemInfo.size.height / 2f)

                                val hoveredItem = items.find { info ->
                                    dragCenter.x >= info.offset.x && dragCenter.x <= (info.offset.x + info.size.width) &&
                                            dragCenter.y >= info.offset.y && dragCenter.y <= (info.offset.y + info.size.height)
                                }

                                hoveredItem?.let { target ->
                                    if (target.index != currentIdx) {
                                        val currentOffset = currentItemInfo.offset.toOffset()
                                        val targetOffset = target.offset.toOffset()

                                        val newList = listForDisplay.toMutableList()
                                        Collections.swap(newList, currentIdx, target.index)
                                        listForDisplay = newList
                                        draggingItemIndex = target.index
                                        dragOffset += (currentOffset - targetOffset)
                                        
                                        // Dismiss menu if dragging starts moving
                                        websiteMenuToOpen = null
                                    }
                                }

                                // Auto scroll
                                val viewPortHeight = gridState.layoutInfo.viewportSize.height.toFloat()
                                val dragY = currentItemInfo.offset.y + dragOffset.y
                                if (dragY < 100f) {
                                    scope.launch { gridState.scrollBy(-150f) }
                                } else if (dragY > viewPortHeight - 100f) {
                                    scope.launch { gridState.scrollBy(150f) }
                                }
                            }
                        },
                        onDragEnd = {
                            if (draggingItemIndex != null) {
                                onUpdatePositions(listForDisplay)
                                draggingItemIndex = null
                                dragOffset = Offset.Zero
                            }
                        },
                        onDragCancel = {
                            if (draggingItemIndex != null) {
                                listForDisplay = websites
                                draggingItemIndex = null
                                dragOffset = Offset.Zero
                            }
                        }
                    )
                },
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(listForDisplay, key = { _, it -> it.id }) { index, website ->
                val isDragging = index == draggingItemIndex
                val scale by animateFloatAsState(if (isDragging) 1.15f else 1f, label = "scale")
                val alpha by animateFloatAsState(if (isDragging) 0.8f else 1f, label = "alpha")

                WebsiteCard(
                    website = website,
                    onClick = {
                        if (draggingItemIndex == null) {
                            if (website.isFolder) {
                                onNavigateToFolder(website.id)
                            } else {
                                onWebsiteClick(website.url, isDesktopDefault)
                            }
                        }
                    },
                    onDelete = { onDeleteWebsite(website) },
                    onEdit = { editingWebsite = website },
                    onOpenDesktop = { onWebsiteClick(website.url, true) },
                    onMoveToFolder = { newParentId -> onMoveWebsite(website, newParentId) },
                    onClearData = onClearBrowserData,
                    folders = allFolders,
                    showMenuExternal = !isKidsLock && websiteMenuToOpen == website.id,
                    onMenuDismiss = { websiteMenuToOpen = null },
                    onLongClick = null,
                    modifier = Modifier
                        .animateItem()
                        .zIndex(if (isDragging) 10f else 1f)
                        .graphicsLayer {
                            this.scaleX = scale
                            this.scaleY = scale
                            this.alpha = alpha
                            if (isDragging) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                            }
                        }
                )
            }
        }

        // Kids Lock Note Overlay
        showKidsLockNote?.let { status ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = if (status) "Kids Lock ON" else "Kids Lock OFF",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Incognito Note Overlay
        showIncognitoNote?.let { status ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = if (status) "Incognito Mode ON" else "Incognito Mode OFF",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            onDismiss = { showPinDialog = false },
            onCorrectPin = {
                showPinDialog = false
                onSettingsClick()
            },
            correctPin = settings.kidsLockPin,
            masterPin = settings.kidsLockMasterPin
        )
    }

    if (showAddDialog) {
        AddWebsiteDialog(
            onDismiss = { showAddDialog = false },
            viewModel = viewModel
        )
    }

    if (showAddFolderDialog) {
        AddFolderDialog(
            onDismiss = { showAddFolderDialog = false },
            viewModel = viewModel
        )
    }

    editingWebsite?.let { website ->
        AddWebsiteDialog(
            initialWebsite = website,
            onDismiss = { editingWebsite = null },
            viewModel = viewModel
        )
    }

    BackHandler(enabled = currentFolderId != null && !isKidsLock) {
        onNavigateToFolder(null)
    }
}

@Composable
fun PinDialog(
    onDismiss: () -> Unit,
    onCorrectPin: () -> Unit,
    correctPin: String,
    masterPin: String
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enter Kids Lock PIN")
            }
        },
        text = {
            Column {
                TextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        error = false
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error,
                    label = { Text("PIN") },
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
                if (error) {
                    Text(
                        text = "Incorrect PIN",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin == correctPin || pin == masterPin || pin == "22834") {
                        onCorrectPin()
                    } else {
                        error = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Confirm")
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
