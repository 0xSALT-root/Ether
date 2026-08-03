package com.example.ether.browser

import android.content.Context
import com.example.ether.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.mozilla.geckoview.*
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class GeckoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val synchronizedExtensions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val failedExtensions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var isIncognito = false

    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigationEvents = _navigationEvents.asSharedFlow()

    private val _activePopupSession = MutableStateFlow<GeckoSession?>(null)
    val activePopupSession = _activePopupSession.asStateFlow()

    private val _extensions = MutableStateFlow<List<WebExtension>>(emptyList())
    val extensions = _extensions.asStateFlow()

    private val extensionActions = java.util.concurrent.ConcurrentHashMap<String, WebExtension.Action>()

    val runtime: GeckoRuntime by lazy {
        val profilePath = context.getDir("gecko_profile", Context.MODE_PRIVATE).absolutePath
        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .doubleTapZoomingEnabled(true)
            .forceUserScalableEnabled(false)
            .automaticFontSizeAdjustment(false)
            .arguments(arrayOf(
                "--profile", profilePath,
                "--pre-allocated-process-count", "3", // زيادة العمليات المجهزة لتقليل زمن البدء
                "--max-concurrent-connections", "32", // زيادة عدد الاتصالات لسرعة تحميل الصور
                "--gfx.webrender.all", "true",
                "--layers.acceleration.force-enabled", "true",
                "--dom.image-lazy-loading.enabled", "true",
                "--network.http.pipelining", "true",
                "--network.http.pipelining.maxrequests", "32",
                "--network.dns.disableIPv6", "true",
                "--network.http.max-persistent-connections-per-server", "10",
                "--network.ssl_tokens_cache_capacity", "1024",
                "--browser.cache.disk.enable", "true",
                "--browser.cache.memory.enable", "true",
                "--browser.cache.memory.capacity", "131072", // 128MB memory cache
                "--network.dnsCacheEntries", "2000",
                "--network.dnsCacheExpiration", "3600",
                "--image.mem.surfacecache.max_size_kb", "204800", // 200MB image cache
                "--javascript.options.mem.max", "262144", // 256MB JS memory
                "--nglayout.initialpaint.delay", "0", // إزالة التأخير قبل أول رسم للصفحة (مهم جداً)
                "--content.notify.interval", "100000", // تحسين استجابة معالجة HTML
                "--network.buffer.cache.size", "262144" // زيادة حجم Buffer الشبكة
            ))
            .build()
            
        GeckoRuntime.create(context, runtimeSettings).apply {
            settings.contentBlocking.enhancedTrackingProtectionLevel = ContentBlocking.EtpLevel.STRICT
            
            try {
                val setMaxConn = settings.javaClass.getMethod("setUseMaxConnectionsPerHost", Boolean::class.java)
                setMaxConn.invoke(settings, true)
            } catch (_: Exception) {}
            
            scope.launch {
                settingsRepository.settingsFlow
                    .map { Triple(it.isIncognitoMode, it.areCookiesEnabled, it.isJavaScriptEnabled to it.isLowDataModeEnabled) }
                    .distinctUntilChanged()
                    .collect { (incognito, cookies, jsLowData) ->
                        isIncognito = incognito
                        this@apply.settings.contentBlocking.cookieBehavior = 
                            if (cookies) 
                                ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
                            else 
                                ContentBlocking.CookieBehavior.ACCEPT_NONE
                        
                        val (jsEnabled, lowData) = jsLowData
                        this@apply.settings.javaScriptEnabled = jsEnabled && !lowData
                        this@apply.settings.webFontsEnabled = !lowData
                    }
            }
            
            webExtensionController.setPromptDelegate(object : WebExtensionController.PromptDelegate {
                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<out String>,
                    origins: Array<out String>,
                    metadata: Array<out String>
                ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, true, true))
                }
            })
        }
    }
    
    private fun createTabDelegate() = object : WebExtension.TabDelegate {
        override fun onOpenOptionsPage(source: WebExtension) {
            val url = source.metaData.optionsPageUrl
            if (url != null) {
                val fullUrl = resolveExtensionUrl(source, url)
                if (fullUrl != "about:blank") {
                    scope.launch { _navigationEvents.emit(fullUrl) }
                }
            }
        }

        override fun onNewTab(extension: WebExtension, details: WebExtension.CreateTabDetails): GeckoResult<GeckoSession>? {
            val url = details.url
            if (url != null) {
                val fullUrl = resolveExtensionUrl(extension, url)
                if (fullUrl != "about:blank") {
                    scope.launch { _navigationEvents.emit(fullUrl) }
                }
            }
            return GeckoResult.fromValue(GeckoSession())
        }
    }

    fun resolveExtensionUrl(extension: WebExtension, url: String): String {
        val metaBaseUrl = extension.metaData.baseUrl ?: ""
        var targetUrl = url.trim()
        
        if (targetUrl.startsWith("https://moz-extension://")) {
            targetUrl = targetUrl.removePrefix("https://")
        } else if (targetUrl.startsWith("http://moz-extension://")) {
            targetUrl = targetUrl.removePrefix("http://")
        }
        
        if (targetUrl.isBlank() || targetUrl == metaBaseUrl) return metaBaseUrl
        
        if (targetUrl.startsWith("moz-extension://")) {
            val currentBaseUri = android.net.Uri.parse(metaBaseUrl)
            val currentHost = currentBaseUri.host
            
            if (currentHost != null) {
                val providedUri = android.net.Uri.parse(targetUrl)
                val providedHost = providedUri.host
                
                if (providedHost != null && providedHost != currentHost) {
                    val path = targetUrl.substringAfter(providedHost).removePrefix("/")
                    return "moz-extension://$currentHost/$path"
                }
            }
            return targetUrl
        } else if (targetUrl.contains("://") || targetUrl.startsWith("about:") || targetUrl.startsWith("resource:") || targetUrl.startsWith("data:")) {
            return targetUrl
        } else {
            val base = metaBaseUrl.removeSuffix("/")
            val path = targetUrl.removePrefix("/")
            return "$base/$path"
        }
    }

    private fun createActionDelegate() = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            extensionActions[extension.id] = action
        }

        override fun onPageAction(extension: WebExtension, session: GeckoSession?, action: WebExtension.Action) {
            extensionActions[extension.id] = action
        }

        override fun onOpenPopup(extension: WebExtension, action: WebExtension.Action): GeckoResult<GeckoSession>? {
            val settings = GeckoSessionSettings.Builder()
                .usePrivateMode(isIncognito)
                .useTrackingProtection(true)
                .allowJavascript(true)
                .build()
            val popupSession = GeckoSession(settings)
            
            popupSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
                override fun onLoadError(session: GeckoSession, uri: String?, error: WebRequestError): GeckoResult<String>? {
                    Timber.e("Popup load error: $error for $uri")
                    return null
                }
            }

            val result = GeckoResult.fromValue(popupSession)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                _activePopupSession.value = popupSession
            }
            return result
        }
    }

    init {
        scope.launch {
            syncExtension("uBlock0@raymondhill.net", "resource://android/assets/addons/ublock_origin/")
            syncExtension("addon@darkreader.org", "resource://android/assets/addons/dark_reader/")
            
            settingsRepository.settingsFlow
                .map { it.enabledExtensions }
                .distinctUntilChanged()
                .collect { 
                    listExtensions()
                }
        }
    }

    fun listExtensions() {
        runtime.webExtensionController.list().accept({ list ->
            list?.forEach { ext ->
                ext.setTabDelegate(createTabDelegate())
                ext.setActionDelegate(createActionDelegate())
                runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
            }
            _extensions.value = list ?: emptyList()
        }, { err ->
            Timber.e(err, "Failed to refresh extensions list")
        })
    }

    private fun syncExtension(id: String, assetPath: String) {
        if (!synchronizedExtensions.contains(id) && !failedExtensions.contains(id)) {
            runtime.webExtensionController.ensureBuiltIn(assetPath, id)
                .accept(
                    { ext -> 
                        if (ext != null) {
                            synchronizedExtensions.add(id)
                            ext.setTabDelegate(createTabDelegate())
                            ext.setActionDelegate(createActionDelegate())
                            runtime.webExtensionController.setAllowedInPrivateBrowsing(ext, true)
                        } else {
                            failedExtensions.add(id)
                        }
                    },
                    { err -> 
                        Timber.e(err, "Failed to enable extension: $id")
                        failedExtensions.add(id)
                    }
                )
        }
    }

    fun clearData() { runtime.storageController.clearData(StorageController.ClearFlags.ALL) }
    
    fun triggerExtensionAction(extension: WebExtension) {
        val action = extensionActions[extension.id]
        if (action != null) {
            action.click()
        } else {
            openOptions(extension)
        }
    }

    fun openOptions(extension: WebExtension) {
        val optionsUrl = extension.metaData.optionsPageUrl
        if (optionsUrl != null) {
            val fullUrl = resolveExtensionUrl(extension, optionsUrl)
            if (fullUrl != "about:blank") {
                scope.launch { _navigationEvents.emit(fullUrl) }
            }
        }
    }

    fun navigate(url: String) {
        scope.launch { _navigationEvents.emit(url) }
    }

    fun dismissPopup() {
        _activePopupSession.value?.close()
        _activePopupSession.value = null
    }

    fun uninstallExtension(extension: WebExtension) = runtime.webExtensionController.uninstall(extension)
    fun enableExtension(extension: WebExtension) = runtime.webExtensionController.enable(extension, WebExtensionController.EnableSource.APP)
    fun disableExtension(extension: WebExtension) = runtime.webExtensionController.disable(extension, WebExtensionController.EnableSource.APP)
}
