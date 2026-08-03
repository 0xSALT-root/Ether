package com.example.ether.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.ether.data.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object PreferencesKeys {
        val JS_ENABLED = booleanPreferencesKey("js_enabled")
        val COOKIES_ENABLED = booleanPreferencesKey("cookies_enabled")
        val DESKTOP_MODE_DEFAULT = booleanPreferencesKey("desktop_mode_default")
        val AUTOPLAY_ENABLED = booleanPreferencesKey("autoplay_enabled")
        val POPUP_BLOCKING_ENABLED = booleanPreferencesKey("popup_blocking_enabled")
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val AMOLED_MODE_ENABLED = booleanPreferencesKey("amoled_mode_enabled")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
        val SELECTED_THEME = stringPreferencesKey("selected_theme")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val RAW_VPN_CONFIG = stringPreferencesKey("raw_vpn_config")
        val VPN_SERVER_NAME = stringPreferencesKey("vpn_server_name")
        val SELECTED_VPN_SERVER_ID = intPreferencesKey("selected_vpn_server_id")
        val VPN_ENABLED = booleanPreferencesKey("vpn_enabled")
        val KIDS_LOCK_ENABLED = booleanPreferencesKey("kids_lock_enabled")
        val KIDS_LOCK_PIN = stringPreferencesKey("kids_lock_pin")
        val LOW_DATA_MODE_ENABLED = booleanPreferencesKey("low_data_mode_enabled")
        val ENABLED_EXTENSIONS = stringSetPreferencesKey("enabled_extensions")
        val VPN_AUTO_DISCONNECT_TIMEOUT = intPreferencesKey("vpn_auto_disconnect_timeout")
        val URL_BAR_POSITION_BOTTOM = booleanPreferencesKey("url_bar_position_bottom")
        val DOWNLOADS_BIOMETRIC_PROTECTED = booleanPreferencesKey("downloads_biometric_protected")
        val EXTENSION_UUID_OVERRIDES = stringPreferencesKey("extension_uuid_overrides")
        val PINNED_EXTENSIONS = stringSetPreferencesKey("pinned_extensions")
        val AUTO_INCOGNITO_DOMAINS = stringSetPreferencesKey("auto_incognito_domains")
        val USER_AGENT_TYPE = stringPreferencesKey("user_agent_type")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            isJavaScriptEnabled = preferences[PreferencesKeys.JS_ENABLED] ?: true,
            areCookiesEnabled = preferences[PreferencesKeys.COOKIES_ENABLED] ?: true,
            isDesktopModeDefault = preferences[PreferencesKeys.DESKTOP_MODE_DEFAULT] ?: false,
            isAutoplayEnabled = preferences[PreferencesKeys.AUTOPLAY_ENABLED] ?: false,
            isPopupBlockingEnabled = preferences[PreferencesKeys.POPUP_BLOCKING_ENABLED] ?: true,
            isBiometricLockEnabled = preferences[PreferencesKeys.BIOMETRIC_LOCK_ENABLED] ?: false,
            isAmoledModeEnabled = preferences[PreferencesKeys.AMOLED_MODE_ENABLED] ?: false,
            isDarkMode = preferences[PreferencesKeys.DARK_MODE] ?: true,
            isScreenshotProtectionEnabled = preferences[PreferencesKeys.SCREENSHOT_PROTECTION] ?: false,
            selectedTheme = preferences[PreferencesKeys.SELECTED_THEME] ?: "DEFAULT",
            isIncognitoMode = preferences[PreferencesKeys.INCOGNITO_MODE] ?: false,
            vpnConfig = preferences[PreferencesKeys.RAW_VPN_CONFIG] ?: "",
            vpnServerName = preferences[PreferencesKeys.VPN_SERVER_NAME] ?: "",
            selectedVpnServerId = preferences[PreferencesKeys.SELECTED_VPN_SERVER_ID],
            isVpnEnabled = preferences[PreferencesKeys.VPN_ENABLED] ?: false,
            isKidsLockEnabled = preferences[PreferencesKeys.KIDS_LOCK_ENABLED] ?: false,
            kidsLockPin = preferences[PreferencesKeys.KIDS_LOCK_PIN] ?: "",
            isLowDataModeEnabled = preferences[PreferencesKeys.LOW_DATA_MODE_ENABLED] ?: false,
            enabledExtensions = preferences[PreferencesKeys.ENABLED_EXTENSIONS] ?: emptySet(),
            vpnAutoDisconnectTimeout = preferences[PreferencesKeys.VPN_AUTO_DISCONNECT_TIMEOUT] ?: 5,
            isUrlBarBottom = preferences[PreferencesKeys.URL_BAR_POSITION_BOTTOM] ?: false,
            isDownloadsBiometricProtected = preferences[PreferencesKeys.DOWNLOADS_BIOMETRIC_PROTECTED] ?: false,
            extensionUuidOverrides = parseUuidOverrides(preferences[PreferencesKeys.EXTENSION_UUID_OVERRIDES]),
            pinnedExtensions = preferences[PreferencesKeys.PINNED_EXTENSIONS] ?: emptySet(),
            autoIncognitoDomains = preferences[PreferencesKeys.AUTO_INCOGNITO_DOMAINS] ?: emptySet(),
            userAgentType = try {
                com.example.ether.data.model.UserAgentType.valueOf(preferences[PreferencesKeys.USER_AGENT_TYPE] ?: "DEFAULT")
            } catch (e: Exception) {
                com.example.ether.data.model.UserAgentType.DEFAULT
            }
        )
    }.distinctUntilChanged()

    suspend fun updatePinnedExtension(extensionId: String, pinned: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.PINNED_EXTENSIONS] ?: emptySet()
            val next = if (pinned) current + extensionId else current - extensionId
            prefs[PreferencesKeys.PINNED_EXTENSIONS] = next
        }
    }

    private fun parseUuidOverrides(encoded: String?): Map<String, String> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return encoded.split(";").mapNotNull {
            val parts = it.split("|")
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    private fun encodeUuidOverrides(overrides: Map<String, String>): String {
        return overrides.map { "${it.key}|${it.value}" }.joinToString(";")
    }

    suspend fun updateExtensionUuidOverride(extensionId: String, uuid: String) {
        dataStore.edit { prefs ->
            val current = parseUuidOverrides(prefs[PreferencesKeys.EXTENSION_UUID_OVERRIDES]).toMutableMap()
            current[extensionId] = uuid
            prefs[PreferencesKeys.EXTENSION_UUID_OVERRIDES] = encodeUuidOverrides(current)
        }
    }

    suspend fun updateLowDataModeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.LOW_DATA_MODE_ENABLED] = enabled }
    }

    suspend fun updateKidsLockEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.KIDS_LOCK_ENABLED] = enabled }
    }

    suspend fun updateKidsLockPin(pin: String) {
        dataStore.edit { it[PreferencesKeys.KIDS_LOCK_PIN] = pin }
    }

    suspend fun updateJavaScriptEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.JS_ENABLED] = enabled }
    }

    suspend fun updateCookiesEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.COOKIES_ENABLED] = enabled }
    }

    suspend fun updateDesktopModeDefault(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DESKTOP_MODE_DEFAULT] = enabled }
    }

    suspend fun updateAutoplayEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTOPLAY_ENABLED] = enabled }
    }

    suspend fun updatePopupBlockingEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.POPUP_BLOCKING_ENABLED] = enabled }
    }

    suspend fun updateBiometricLockEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun updateAmoledModeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AMOLED_MODE_ENABLED] = enabled }
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DARK_MODE] = enabled }
    }

    suspend fun updateScreenshotProtection(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SCREENSHOT_PROTECTION] = enabled }
    }

    suspend fun updateSelectedTheme(theme: String) {
        dataStore.edit { it[PreferencesKeys.SELECTED_THEME] = theme }
    }

    suspend fun updateIncognitoMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.INCOGNITO_MODE] = enabled }
    }

    suspend fun updateVpnConfig(config: String) {
        dataStore.edit { it[PreferencesKeys.RAW_VPN_CONFIG] = config }
    }

    suspend fun updateVpnServerName(name: String) {
        dataStore.edit { it[PreferencesKeys.VPN_SERVER_NAME] = name }
    }

    suspend fun updateSelectedVpnServerId(id: Int?) {
        dataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(PreferencesKeys.SELECTED_VPN_SERVER_ID)
            } else {
                prefs[PreferencesKeys.SELECTED_VPN_SERVER_ID] = id
            }
        }
    }

    suspend fun updateVpnEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.VPN_ENABLED] = enabled }
    }

    suspend fun updateExtensionEnabled(extensionId: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.ENABLED_EXTENSIONS] ?: emptySet()
            val next = if (enabled) current + extensionId else current - extensionId
            prefs[PreferencesKeys.ENABLED_EXTENSIONS] = next
        }
    }

    suspend fun updateVpnAutoDisconnectTimeout(timeout: Int) {
        dataStore.edit { it[PreferencesKeys.VPN_AUTO_DISCONNECT_TIMEOUT] = timeout }
    }

    suspend fun updateUrlBarBottom(isBottom: Boolean) {
        dataStore.edit { it[PreferencesKeys.URL_BAR_POSITION_BOTTOM] = isBottom }
    }

    suspend fun updateDownloadsBiometricProtected(protected: Boolean) {
        dataStore.edit { it[PreferencesKeys.DOWNLOADS_BIOMETRIC_PROTECTED] = protected }
    }

    suspend fun updateAutoIncognitoDomain(domain: String, add: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[PreferencesKeys.AUTO_INCOGNITO_DOMAINS] ?: emptySet()
            val next = if (add) current + domain else current - domain
            prefs[PreferencesKeys.AUTO_INCOGNITO_DOMAINS] = next
        }
    }

    suspend fun updateUserAgentType(type: com.example.ether.data.model.UserAgentType) {
        dataStore.edit { it[PreferencesKeys.USER_AGENT_TYPE] = type.name }
    }
}
