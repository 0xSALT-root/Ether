package com.example.ether.browser.vpn

import com.example.ether.data.repository.SettingsRepository
import com.wireguard.config.Config
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WireGuardConfigManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend fun getParsedConfig(): Config? {
        val rawConfig = settingsRepository.settingsFlow.first().vpnConfig
        if (rawConfig.isBlank()) return null
        
        return try {
            Config.parse(ByteArrayInputStream(rawConfig.toByteArray()))
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse WireGuard config")
            null
        }
    }

    suspend fun isVpnConfigured(): Boolean {
        val settings = settingsRepository.settingsFlow.first()
        return settings.vpnConfig.isNotBlank()
    }
    
    suspend fun getServerName(): String {
        return settingsRepository.settingsFlow.first().vpnServerName
    }
}
