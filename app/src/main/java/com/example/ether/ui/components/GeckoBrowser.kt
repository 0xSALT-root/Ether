package com.example.ether.ui.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.ether.browser.GeckoManager
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.ui.util.findActivity
import org.mozilla.geckoview.*
import timber.log.Timber

sealed class BrowserPrompt {
    data class Alert(val message: String, val result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>, val prompt: GeckoSession.PromptDelegate.AlertPrompt) : BrowserPrompt()
    data class Confirm(val message: String, val result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>, val prompt: GeckoSession.PromptDelegate.ButtonPrompt) : BrowserPrompt()
    data class Prompt(val message: String, val defaultValue: String?, val result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>, val prompt: GeckoSession.PromptDelegate.TextPrompt) : BrowserPrompt()
}

@Composable
fun GeckoBrowser(
    runtime: GeckoRuntime,
    url: String,
    isDesktopMode: Boolean,
    settingsRepository: SettingsRepository,
    onDownloadStarted: (String, String, String, String?, Long) -> Unit,
    onUrlChanged: (String) -> Unit = {},
    onTitleReceived: (String, String) -> Unit = { _, _ -> },
    onVideoPlaybackStateChanged: (Boolean) -> Unit = {},
    onContentBlocked: (Int) -> Unit = {},
    onNavigationRequest: (url: String, hasUserGesture: Boolean, isRedirect: Boolean) -> GeckoResult<AllowOrDeny>? = { _, _, _ -> null },
    isKidsLock: Boolean = false,
    isPrivateModeForced: Boolean = false,
    modifier: Modifier = Modifier,
    onFullScreenChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val appSettings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    
    if (appSettings == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val settings = appSettings!!
    val isIncognito = settings.isIncognitoMode || isKidsLock || isPrivateModeForced
    val isJsEnabled = settings.isJavaScriptEnabled
    val userAgentType = settings.userAgentType

    val activity = context.findActivity()
    DisposableEffect(isKidsLock) {
        if (isKidsLock) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (isKidsLock && !settings.isScreenshotProtectionEnabled) {
                activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val browserSession = remember(runtime, isDesktopMode, isIncognito, userAgentType, isPrivateModeForced) {
        val geckoSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(isIncognito || isPrivateModeForced)
            .useTrackingProtection(true)
            .userAgentMode(if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .allowJavascript(isJsEnabled)
            .apply {
                if (userAgentType.userAgent != null) {
                    userAgentOverride(userAgentType.userAgent)
                } else if (!isDesktopMode) {
                    userAgentOverride("Mozilla/5.0 (Android 14; Mobile; rv:134.0) Gecko/134.0 Firefox/134.0")
                }
            }
            .build()
        GeckoSession(geckoSettings)
    }

    DisposableEffect(browserSession) {
        if (!browserSession.isOpen) {
            browserSession.open(runtime)
        }
        onDispose { }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, browserSession) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try { browserSession.javaClass.getMethod("setActive", Boolean::class.java).invoke(browserSession, true) } catch (_: Exception) {}
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    try { browserSession.javaClass.getMethod("setActive", Boolean::class.java).invoke(browserSession, false) } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            browserSession.close()
        }
    }

    var canGoBack by remember(browserSession) { mutableStateOf(false) }
    var loadError by remember(browserSession) { mutableStateOf<String?>(null) }
    var isRefreshing by remember(browserSession) { mutableStateOf(false) }
    var isFullScreen by remember(browserSession) { mutableStateOf(false) }
    var activePrompt by remember(browserSession) { mutableStateOf<BrowserPrompt?>(null) }
    var currentUri by remember(browserSession) { mutableStateOf("") }
    var currentScrollY by remember(browserSession) { mutableIntStateOf(0) }

    BackHandler(enabled = canGoBack) {
        browserSession.goBack()
    }

    LaunchedEffect(url, browserSession) {
        if (url.isNotBlank() && url != "about:blank" && url != currentUri) {
            browserSession.loadUri(url)
        }
    }

    DisposableEffect(browserSession) {
        browserSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBackValue: Boolean) {
                canGoBack = canGoBackValue
            }

            override fun onLocationChange(session: GeckoSession, url: String?, permissions: List<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean) {
                if (url != null && url != currentUri) {
                    currentUri = url
                    onUrlChanged(url)
                }
            }

            override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                loadError = error.toString()
                isRefreshing = false
                return null
            }

            override fun onLoadRequest(session: GeckoSession, request: GeckoSession.NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                val uri = request.uri

                if (uri.startsWith("moz-extension://") || uri.startsWith("about:") || uri.startsWith("resource:")) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                val isInitialOrManual = try {
                    val requestedHost = Uri.parse(if (url.contains("://")) url else "https://$url").host?.lowercase()?.removePrefix("www.")
                    val targetHost = Uri.parse(if (uri.contains("://")) uri else "https://$uri").host?.lowercase()?.removePrefix("www.")

                    (requestedHost != null && targetHost != null && (requestedHost == targetHost || targetHost.endsWith(".$requestedHost"))) ||
                    (uri.trimEnd('/') == url.trimEnd('/')) || uri.startsWith("about:") || uri.startsWith("moz-extension:")
                } catch (e: Exception) {
                    false
                }
                
                if (uri.contains(".xpi", ignoreCase = true)) {
                    runtime.webExtensionController.install(uri).accept({ ext ->
                        if (ext != null) Toast.makeText(context, "Extension installed: ${ext.id}", Toast.LENGTH_SHORT).show()
                    }, { err ->
                        Toast.makeText(context, "Installation failed: ${err?.message}", Toast.LENGTH_LONG).show()
                    })
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (!uri.startsWith("http") && !uri.startsWith("about") && !uri.startsWith("file") && !uri.startsWith("resource") && !uri.startsWith("moz-extension")) {
                    if (!request.hasUserGesture && !isInitialOrManual) {
                        Timber.w("Blocking automatic external intent: $uri")
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    }
                    try {
                        val intent = android.content.Intent.parseUri(uri, android.content.Intent.URI_INTENT_SCHEME).apply {
                            addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                            component = null
                            selector = null
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                        return GeckoResult.fromValue(AllowOrDeny.DENY)
                    } catch (e: Exception) {
                        Timber.e(e, "External URI error")
                    }
                }
                
                return onNavigationRequest(uri, request.hasUserGesture || isInitialOrManual, request.isRedirect)
                    ?: GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                browserSession.loadUri(uri)
                return GeckoResult.fromValue(null)
            }
        }

        browserSession.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) { isRefreshing = false }
            override fun onPageStart(session: GeckoSession, url: String) { loadError = null }
        }

        browserSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val uri = response.uri
                if (uri.contains(".xpi", ignoreCase = true) || response.headers["Content-Type"] == "application/x-xpinstall") {
                    runtime.webExtensionController.install(uri).accept({ ext ->
                        if (ext != null) Toast.makeText(context, "Extension installed: ${ext.id}", Toast.LENGTH_SHORT).show()
                    }, { err ->
                        Toast.makeText(context, "Installation failed: ${err?.message}", Toast.LENGTH_LONG).show()
                    })
                    return
                }
                handleDownload(context, response, onDownloadStarted)
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                isFullScreen = fullScreen
                onFullScreenChanged(fullScreen)
            }

            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (title != null) onTitleReceived(title, currentUri)
            }
        }

        browserSession.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) { onVideoPlaybackStateChanged(true) }
            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) { onVideoPlaybackStateChanged(false) }
        }

        browserSession.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                onContentBlocked(1)
            }
        }

        browserSession.promptDelegate = object : GeckoSession.PromptDelegate {
            override fun onAlertPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                activePrompt = BrowserPrompt.Alert(prompt.message ?: "", result, prompt)
                return result
            }

            override fun onButtonPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                activePrompt = BrowserPrompt.Confirm(prompt.message ?: "", result, prompt)
                return result
            }

            override fun onTextPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.TextPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
                val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
                activePrompt = BrowserPrompt.Prompt(prompt.message ?: "", prompt.defaultValue, result, prompt)
                return result
            }

            override fun onPopupPrompt(session: GeckoSession, prompt: GeckoSession.PromptDelegate.PopupPrompt): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
                return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
            }
        }

        onDispose {
            browserSession.navigationDelegate = null
            browserSession.progressDelegate = null
            browserSession.contentDelegate = null
            browserSession.promptDelegate = null
            browserSession.scrollDelegate = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        key(browserSession) {
            AndroidView(
                factory = { context ->
                    val swipeLayout = object : SwipeRefreshLayout(context) {
                        override fun canChildScrollUp(): Boolean = currentScrollY > 1
                    }

                    swipeLayout.apply {
                        val geckoView = GeckoView(context).apply {
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            setSession(browserSession)
                            isFocusable = true
                            isFocusableInTouchMode = true
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                        addView(geckoView)
                        
                        geckoView.requestFocus()
                        setDistanceToTriggerSync(400)
                        setOnRefreshListener {
                            isRefreshing = true
                            browserSession.reload()
                        }

                        browserSession.scrollDelegate = object : GeckoSession.ScrollDelegate {
                            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) { currentScrollY = scrollY }
                        }
                    }
                },
                update = { swipeRefreshLayout ->
                    swipeRefreshLayout.isEnabled = !isFullScreen && currentScrollY <= 0
                    if (swipeRefreshLayout.isRefreshing != isRefreshing) swipeRefreshLayout.isRefreshing = isRefreshing
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (loadError != null) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "Connection Failed", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    loadError = null
                    browserSession.reload()
                }) { Text("Retry") }
            }
        }

        activePrompt?.let { prompt ->
            BrowserPromptDialog(prompt = prompt, onDismiss = { activePrompt = null })
        }
    }
}

@Composable
fun BrowserPromptDialog(prompt: BrowserPrompt, onDismiss: () -> Unit) {
    when (prompt) {
        is BrowserPrompt.Alert -> {
            AlertDialog(
                onDismissRequest = { prompt.result.complete(prompt.prompt.dismiss()); onDismiss() },
                title = { Text("Alert") },
                text = { Text(prompt.message) },
                confirmButton = { TextButton(onClick = { prompt.result.complete(prompt.prompt.dismiss()); onDismiss() }) { Text("OK") } }
            )
        }
        is BrowserPrompt.Confirm -> {
            AlertDialog(
                onDismissRequest = { prompt.result.complete(prompt.prompt.dismiss()); onDismiss() },
                title = { Text("Confirm") },
                text = { Text(prompt.message) },
                confirmButton = { TextButton(onClick = { prompt.result.complete(prompt.prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE)); onDismiss() }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { prompt.result.complete(prompt.prompt.dismiss()); onDismiss() }) { Text("Cancel") } }
            )
        }
        is BrowserPrompt.Prompt -> {
            var textValue by remember { mutableStateOf(prompt.defaultValue ?: "") }
            AlertDialog(
                onDismissRequest = { prompt.result.complete(prompt.prompt.dismiss()); onDismiss() },
                title = { Text("Prompt") },
                text = {
                    Column {
                        Text(prompt.message)
                        OutlinedTextField(value = textValue, onValueChange = { textValue = it }, modifier = Modifier.padding(top = 8.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { prompt.result.complete(prompt.prompt.confirm(textValue)); onDismiss() }) { Text("OK") } },
                dismissButton = { TextButton(onClick = { prompt.result.complete(prompt.prompt.dismiss()); onDismiss() }) { Text("Cancel") } }
            )
        }
    }
}

private fun handleDownload(
    context: Context,
    response: WebResponse,
    onDownloadStarted: (String, String, String, String?, Long) -> Unit
) {
    val url = response.uri
    val contentDisposition = response.headers["Content-Disposition"]
    val mimeType = response.headers["Content-Type"]
    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setDescription("Downloading via Ether")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    if (mimeType != null) request.setMimeType(mimeType)

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = downloadManager.enqueue(request)

    val filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath + "/" + fileName
    onDownloadStarted(fileName, url, filePath, mimeType, downloadId)
}
