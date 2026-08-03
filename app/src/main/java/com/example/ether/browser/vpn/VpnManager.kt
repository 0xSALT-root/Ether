package com.example.ether.browser.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.data.repository.VpnRepository
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val vpnRepository: VpnRepository
) {
    private val backend = GoBackend(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val tunnel = object : Tunnel {
        override fun getName(): String = "EtherVPN"
        override fun onStateChange(newState: Tunnel.State) {
            _vpnState.value = newState
        }
    }

    private val _vpnState = MutableStateFlow(Tunnel.State.DOWN)
    val vpnState = _vpnState.asStateFlow()

    private val _preparationNeeded = MutableSharedFlow<Intent>(replay = 1)
    val preparationNeeded = _preparationNeeded.asSharedFlow()

    private var autoDisconnectJob: Job? = null
    private val startMutex = Mutex()
    private var isAppInForeground = true

    init {
        scope.launch {
            _vpnState.value = backend.getState(tunnel)
        }

        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    isAppInForeground = true
                    // Cancel any pending disconnect when returning to foreground
                    cancelAutoDisconnect()
                }

                override fun onStop(owner: LifecycleOwner) {
                    isAppInForeground = false
                }
            })
        }

        scope.launch {
            settingsRepository.settingsFlow
                .map { Triple(it.isVpnEnabled, it.vpnConfig, it.selectedVpnServerId) }
                .distinctUntilChanged()
                .collect { (isEnabled, _, _) ->
                    if (isEnabled) {
                        startVpn()
                    } else {
                        stopVpn()
                    }
                }
        }
    }

    fun forceStartVpn() {
        scope.launch {
            settingsRepository.updateVpnEnabled(true)
            startVpn()
        }
    }

    fun prepareVpnIntent(): Intent? {
        return VpnService.prepare(context)
    }

    private suspend fun startVpn() = withContext(Dispatchers.IO) {
        startMutex.withLock {
            if (_vpnState.value == Tunnel.State.UP) return@withLock

            cancelAutoDisconnect()
            val settings = settingsRepository.settingsFlow.first()
            var configString = settings.vpnConfig
            
            if (configString.isBlank()) {
                val servers = vpnRepository.allServers.first()
                val serverToUse = settings.selectedVpnServerId?.let { id ->
                    servers.find { it.id == id }
                } ?: servers.firstOrNull()
                
                if (serverToUse != null) {
                    configString = serverToUse.config
                    settingsRepository.updateVpnConfig(serverToUse.config)
                    settingsRepository.updateVpnServerName(serverToUse.name)
                    if (settings.selectedVpnServerId == null) {
                        settingsRepository.updateSelectedVpnServerId(serverToUse.id)
                    }
                }
            }
            
            if (configString.isBlank()) {
                Timber.e("No VPN config found.")
                _vpnState.value = Tunnel.State.DOWN
                return@withLock
            }

            val intent = VpnService.prepare(context)
            if (intent != null) {
                _preparationNeeded.emit(intent)
                _vpnState.value = Tunnel.State.DOWN
                return@withLock
            }

            try {
                val config = com.wireguard.config.Config.parse(ByteArrayInputStream(configString.toByteArray()))
                backend.setState(tunnel, Tunnel.State.UP, config)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start VPN")
                _vpnState.value = Tunnel.State.DOWN
            }
        }
    }

    fun stopVpn() {
        cancelAutoDisconnect()
        scope.launch(Dispatchers.IO) {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop VPN")
            }
        }
    }

    fun startAutoDisconnectTimer() {
        cancelAutoDisconnect()
        autoDisconnectJob = scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val timeoutMinutes = settings.vpnAutoDisconnectTimeout
            
            if (timeoutMinutes >= 0) {
                if (timeoutMinutes > 0) {
                    delay(timeoutMinutes * 60 * 1000L)
                } else {
                    // Immediate disconnect: wait a bit to prevent accidental drops
                    delay(3_000L)
                    
                    // If the user backgrounded the app (accidental exit), give them 60s grace
                    if (!isAppInForeground) {
                        delay(60_000L)
                    }
                }
                
                // If the app is back in foreground, this job will have been cancelled by onStart.
                // Otherwise, proceed with disconnection.
                settingsRepository.updateVpnEnabled(false)
            }
        }
    }

    fun cancelAutoDisconnect() {
        autoDisconnectJob?.cancel()
        autoDisconnectJob = null
    }
}
