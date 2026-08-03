package com.example.ether.ui.browser

import android.content.Intent
import android.view.View
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ether.browser.GeckoManager
import com.example.ether.data.repository.DownloadRepository
import com.example.ether.data.repository.HistoryRepository
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.ui.browser.components.*
import com.example.ether.ui.components.GeckoBrowser
import com.example.ether.ui.util.UrlUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebExtension
import timber.log.Timber

@Composable
fun BrowserScreen(
    url: String,
    isDesktopMode: Boolean,
    geckoManager: GeckoManager,
    settingsRepository: SettingsRepository,
    downloadRepository: DownloadRepository,
    historyRepository: HistoryRepository? = null,
    onFullScreenChanged: (Boolean) -> Unit = {},
    onNavigateToExtensions: () -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    var isFullScreen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val appSettings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(null)
    val isKidsLock = appSettings?.isKidsLockEnabled == true
    val pinnedExtensions = appSettings?.pinnedExtensions ?: emptySet()
    val uuidOverrides = appSettings?.extensionUuidOverrides ?: emptyMap()

    var activeUrl by remember { mutableStateOf(viewModel.currentUrl.value ?: url) }
    var showUrlTray by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(viewModel.currentUrl.value ?: url) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showUuidDialog by remember { mutableStateOf(false) }
    var selectedExtensionForUuid by remember { mutableStateOf<WebExtension?>(null) }

    val extensions by geckoManager.extensions.collectAsStateWithLifecycle()
    val activePopupSession by geckoManager.activePopupSession.collectAsStateWithLifecycle()

    val blockedOnPage by viewModel.blockedCountOnPage.collectAsStateWithLifecycle()
    val isWaitingForVpn by viewModel.isWaitingForVpn.collectAsStateWithLifecycle()
    val forcePrivateMode by viewModel.forcePrivateMode.collectAsStateWithLifecycle()
    
    LaunchedEffect(url) {
        if (url.isNotBlank() && url != "about:blank") {
            activeUrl = url
            urlInput = url
            viewModel.onUrlChanged(url)
        }
    }

    LaunchedEffect(geckoManager) {
        geckoManager.navigationEvents.collect { newUrl ->
            if (newUrl.isNotBlank() && newUrl != activeUrl) {
                activeUrl = newUrl
                urlInput = newUrl
                viewModel.onUrlChanged(newUrl)
                showUrlTray = false
            }
        }
    }
    
    val popupHandler = remember { 
        ExtensionPopupHandler(context) {
            geckoManager.dismissPopup()
        }
    }
    
    var extensionButtonAnchor by remember { mutableStateOf<View?>(null) }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val androidSurfaceColor = android.graphics.Color.argb(
        (surfaceColor.alpha * 255).toInt(),
        (surfaceColor.red * 255).toInt(),
        (surfaceColor.green * 255).toInt(),
        (surfaceColor.blue * 255).toInt()
    )

    DisposableEffect(Unit) {
        onDispose {
            popupHandler.dismissPopup()
            viewModel.onBrowserClosed()
        }
    }

    LaunchedEffect(activePopupSession, extensionButtonAnchor) {
        val session = activePopupSession
        val anchor = extensionButtonAnchor
        
        if (session != null && anchor != null) {
            if (anchor.isAttachedToWindow) {
                popupHandler.showPopup(anchor, session, androidSurfaceColor)
            }
        } else if (session == null) {
            popupHandler.dismissPopup()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size == 3) {
                            val dragAmount = event.changes.map { it.position.y - it.previousPosition.y }.average()
                            if (dragAmount > 5 && !showUrlTray) {
                                showUrlTray = true
                                event.changes.forEach { it.consume() }
                            } else if (dragAmount < -5 && showUrlTray) {
                                showUrlTray = false
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        GeckoBrowser(
            runtime = geckoManager.runtime,
            url = activeUrl,
            isDesktopMode = isDesktopMode,
            settingsRepository = settingsRepository,
            onDownloadStarted = { fileName, downloadUrl, filePath, mimeType, downloadId ->
                scope.launch {
                    downloadRepository.addDownload(fileName, downloadUrl, filePath, mimeType, downloadId)
                }
            },
            onUrlChanged = { newUrl ->
                urlInput = newUrl
                if (newUrl != "about:blank" && newUrl != activeUrl) {
                    activeUrl = newUrl
                }
                viewModel.onUrlChanged(newUrl)
            },
            onTitleReceived = { title, currentUrl ->
                val isIncognito = appSettings?.isIncognitoMode == true || isKidsLock
                if (!isIncognito) {
                    scope.launch {
                        historyRepository?.addHistoryItem(title, currentUrl)
                    }
                }
            },
            onContentBlocked = { viewModel.onContentBlocked() },
            onNavigationRequest = { navUrl, hasUserGesture, isRedirect -> 
                viewModel.handleNavigation(navUrl, hasUserGesture, isRedirect) 
            },
            isKidsLock = isKidsLock,
            isPrivateModeForced = forcePrivateMode,
            onFullScreenChanged = {
                isFullScreen = it
                onFullScreenChanged(it)
            },
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isFullScreen) {
                        Modifier.statusBarsPadding().navigationBarsPadding()
                    } else {
                        Modifier
                    }
                )
        )

        var showPrivacySheet by remember { mutableStateOf(false) }
        val isUrlBarBottom = appSettings?.isUrlBarBottom == true

        AnimatedVisibility(
            visible = showUrlTray,
            enter = if (isUrlBarBottom) slideInVertically(initialOffsetY = { it }) + fadeIn() else slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = if (isUrlBarBottom) slideOutVertically(targetOffsetY = { it }) + fadeOut() else slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(if (isUrlBarBottom) Alignment.BottomCenter else Alignment.TopCenter)
        ) {
            UrlTray(
                url = urlInput,
                onUrlChange = { urlInput = it },
                onUrlSubmit = {
                    var finalUrl = urlInput.trim()
                    if (finalUrl.isNotBlank()) {
                        if (!finalUrl.contains("://") && !finalUrl.startsWith("about:") && !finalUrl.startsWith("resource:")) {
                            finalUrl = if (finalUrl.contains(".") && !finalUrl.contains(" ")) "https://$finalUrl" 
                                      else "https://www.google.com/search?q=${UrlUtils.encode(finalUrl)}"
                        }
                        viewModel.onUrlChanged(finalUrl)
                        activeUrl = finalUrl
                    }
                    showUrlTray = false
                },
                blockedCount = blockedOnPage,
                onShieldClick = { if (!isKidsLock) showPrivacySheet = true },
                extensions = extensions,
                pinnedExtensionIds = pinnedExtensions,
                onExtensionClick = { ext ->
                    if (!isKidsLock) {
                        val override = uuidOverrides[ext.id] ?: ""
                        val resolved = geckoManager.resolveExtensionUrl(ext, override)
                        if (resolved != "about:blank" && resolved != ext.metaData.baseUrl) {
                            activeUrl = resolved
                            showUrlTray = false
                        } else {
                            geckoManager.triggerExtensionAction(ext)
                        }
                    }
                },
                onManageExtensionsClick = { if (!isKidsLock) onNavigateToExtensions() },
                onRefresh = { 
                    if (!isKidsLock) {
                        scope.launch {
                            val temp = activeUrl
                            activeUrl = ""
                            delay(10)
                            activeUrl = if (temp.isBlank()) (viewModel.currentUrl.value ?: url) else temp
                            showUrlTray = false
                        }
                    }
                },
                onShare = {
                    if (!isKidsLock) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_TEXT, urlInput)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                onExtensionButtonAnchor = { extensionButtonAnchor = it },
                isKidsLock = isKidsLock,
                onSettingsClick = { showPinDialog = true },
                onExtensionOptionsClick = { ext ->
                    selectedExtensionForUuid = ext
                    showUuidDialog = true
                },
                settingsRepository = settingsRepository
            )
        }

        if (showUuidDialog && selectedExtensionForUuid != null) {
            val ext = selectedExtensionForUuid!!
            ExtensionUuidDialog(
                extensionName = ext.metaData.name ?: ext.id,
                currentUuid = uuidOverrides[ext.id] ?: "",
                onDismiss = { 
                    showUuidDialog = false
                    selectedExtensionForUuid = null
                },
                onConfirm = { newUuid ->
                    scope.launch {
                        settingsRepository.updateExtensionUuidOverride(ext.id, newUuid)
                        if (activeUrl.startsWith("moz-extension://") && activeUrl.contains(ext.id)) {
                            val resolved = geckoManager.resolveExtensionUrl(ext, newUuid)
                            if (resolved != "about:blank") activeUrl = resolved
                        }
                    }
                    showUuidDialog = false
                    selectedExtensionForUuid = null
                },
                onNavigateToOptions = {
                    geckoManager.openOptions(ext)
                    showUuidDialog = false
                    selectedExtensionForUuid = null
                    showUrlTray = false
                }
            )
        }

        if (showPinDialog && appSettings != null) {
            PinDialog(
                onDismiss = { showPinDialog = false },
                onCorrectPin = { showPinDialog = false },
                correctPin = appSettings!!.kidsLockPin,
                masterPin = appSettings!!.kidsLockMasterPin
            )
        }

        if (isWaitingForVpn) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connecting to VPN...", color = Color.White)
                    }
                }
            }
        }

        if (showPrivacySheet) {
            PrivacyDashboardSheet(
                viewModel = viewModel,
                onDismiss = { showPrivacySheet = false }
            )
        }
    }
}
