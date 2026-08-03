package com.example.ether.ui.browser

import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ether.browser.vpn.VpnManager
import com.example.ether.data.repository.DownloadRepository
import com.example.ether.data.repository.PrivacyRepository
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.data.repository.WebsiteRepository
import com.example.ether.ui.notification.NotificationHelper
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnManager: VpnManager,
    private val websiteRepository: WebsiteRepository,
    private val settingsRepository: SettingsRepository,
    private val privacyRepository: PrivacyRepository,
    private val notificationHelper: NotificationHelper,
    private val downloadRepository: DownloadRepository,
    private val geckoManager: com.example.ether.browser.GeckoManager
) : ViewModel() {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private var lastUrlWasProtected = false
    private var protectedSitesCache = emptyList<com.example.ether.data.model.Website>()
    private val isCacheInitialized = MutableStateFlow(false)
    private val navMutex = Mutex()
    
    private val _blockedCountOnPage = MutableStateFlow(0)
    val blockedCountOnPage = _blockedCountOnPage.asStateFlow()

    private val _isWaitingForVpn = MutableStateFlow(false)
    val isWaitingForVpn = _isWaitingForVpn.asStateFlow()

    private val _forcePrivateMode = MutableStateFlow(false)
    val forcePrivateMode = _forcePrivateMode.asStateFlow()

    val totalBlockedTrackers = privacyRepository.totalBlockedTrackers
    val totalBlockedAds = privacyRepository.totalBlockedAds

    val settingsFlow = settingsRepository.settingsFlow

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl = _currentUrl.asStateFlow()

    private var currentActiveUrl: String? = null

    init {
        viewModelScope.launch {
            websiteRepository.protectedWebsites.collect {
                protectedSitesCache = it
                isCacheInitialized.value = true
            }
        }
    }

    fun onUrlChanged(url: String) {
        if (url == currentActiveUrl || url == "about:blank") return
        currentActiveUrl = url
        _currentUrl.value = url
        _blockedCountOnPage.value = 0

        viewModelScope.launch {
            if (!isCacheInitialized.value) {
                isCacheInitialized.filter { it }.first()
            }
            
            val domain = getDomain(url)
            val settings = settingsRepository.settingsFlow.first()
            
            if (domain != null && settings.autoIncognitoDomains.contains(domain)) {
                if (!settings.isIncognitoMode && !_forcePrivateMode.value) {
                    _forcePrivateMode.value = true
                }
            } else if (_forcePrivateMode.value) {
                _forcePrivateMode.value = false
            }

            // يتم التعامل مع الـ VPN الآن في handleNavigation بشكل تلقائي
            // لضمان عدم توقف الواجهة عن تحميل المحرك
            val isProtected = isUrlProtected(url, protectedSitesCache)
            if (isProtected) {
                vpnManager.cancelAutoDisconnect()
                if (vpnManager.vpnState.value != Tunnel.State.UP) {
                    vpnManager.forceStartVpn()
                }
                lastUrlWasProtected = true
            } else if (lastUrlWasProtected) {
                vpnManager.startAutoDisconnectTimer()
                lastUrlWasProtected = false
            }
        }
    }

    fun handleNavigation(url: String, hasUserGesture: Boolean, isRedirect: Boolean): GeckoResult<AllowOrDeny>? {
        if (!url.startsWith("http") || url == "about:blank") return GeckoResult.fromValue(AllowOrDeny.ALLOW)

        val result = GeckoResult<AllowOrDeny>()
        viewModelScope.launch {
            navMutex.withLock {
                try {
                    if (!isCacheInitialized.value) {
                        isCacheInitialized.filter { it }.first()
                    }

                    val activeUrl = currentActiveUrl
                    if (activeUrl != null && activeUrl != "about:blank" && url != activeUrl) {
                        val currentDomain = getDomain(activeUrl)
                        val targetDomain = getDomain(url)
                        
                        if (currentDomain != null && targetDomain != null && 
                            currentDomain != targetDomain && !targetDomain.endsWith(".$currentDomain")) {
                            if (!hasUserGesture && !isRedirect) {
                                Timber.w("Blocking automatic redirect: $currentDomain -> $targetDomain")
                                result.complete(AllowOrDeny.DENY)
                                return@launch
                            }
                        }
                    }

                    val isProtected = isUrlProtected(url, protectedSitesCache)

                    if (!isProtected) {
                        if (lastUrlWasProtected) {
                            vpnManager.startAutoDisconnectTimer()
                            lastUrlWasProtected = false
                        }
                        result.complete(AllowOrDeny.ALLOW)
                        return@launch
                    }

                    // Website is protected, ensure VPN is connected
                    vpnManager.cancelAutoDisconnect()
                    if (vpnManager.vpnState.value != Tunnel.State.UP) {
                        _isWaitingForVpn.value = true
                        vpnManager.forceStartVpn()
                        
                        // Wait for VPN to be fully connected (UP state)
                        val state = withTimeoutOrNull(15000) { // 15 seconds timeout
                            vpnManager.vpnState.filter { it == Tunnel.State.UP }.first()
                        }
                        
                        if (state != Tunnel.State.UP) {
                            Timber.w("VPN connection timed out or failed for protected site: $url")
                            // Do not load the page if VPN is not connected
                            result.complete(AllowOrDeny.DENY)
                            return@launch
                        }
                    }
                    
                    lastUrlWasProtected = true
                    result.complete(AllowOrDeny.ALLOW)
                } catch (e: Exception) {
                    Timber.e(e, "Error waiting for VPN connection")
                    // Safety first: if something goes wrong, deny access to protected site
                    result.complete(AllowOrDeny.DENY)
                } finally {
                    _isWaitingForVpn.value = false
                }
            }
        }
        return result
    }

    private fun isUrlProtected(url: String, protectedSites: List<com.example.ether.data.model.Website>): Boolean {
        val currentDomain = getDomain(url) ?: return false
        return protectedSites.any { 
            val siteDomain = getDomain(it.url)
            siteDomain != null && (currentDomain == siteDomain || currentDomain.endsWith(".$siteDomain"))
        }
    }

    fun getDomain(url: String): String? {
        return try {
            val normalizedUrl = if (url.contains("://")) url else "https://$url"
            java.net.URI(normalizedUrl).host?.lowercase()?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

    fun onContentBlocked() {
        _blockedCountOnPage.value += 1
        privacyRepository.incrementBlockedAds()
    }

    fun onBrowserClosed() {
        if (lastUrlWasProtected) {
            vpnManager.startAutoDisconnectTimer()
        }
    }

    suspend fun toggleAutoIncognitoForCurrentSite() {
        currentActiveUrl?.let { url ->
            getDomain(url)?.let { domain ->
                val settings = settingsRepository.settingsFlow.first()
                val isCurrentlyAuto = settings.autoIncognitoDomains.contains(domain)
                settingsRepository.updateAutoIncognitoDomain(domain, !isCurrentlyAuto)
                if (!isCurrentlyAuto && !settings.isIncognitoMode) {
                    _forcePrivateMode.value = true
                }
            }
        }
    }
}
