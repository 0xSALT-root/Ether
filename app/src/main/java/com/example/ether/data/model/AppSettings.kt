package com.example.ether.data.model

data class AppSettings(
    val isJavaScriptEnabled: Boolean = true,
    val areCookiesEnabled: Boolean = true,
    val isDesktopModeDefault: Boolean = false,
    val isAutoplayEnabled: Boolean = false,
    val isPopupBlockingEnabled: Boolean = false,
    val isBiometricLockEnabled: Boolean = false,
    val isAmoledModeEnabled: Boolean = false,
    val isDarkMode: Boolean = true,
    val isScreenshotProtectionEnabled: Boolean = false,
    val selectedTheme: String = "DEFAULT",
    val isIncognitoMode: Boolean = false,
    val vpnConfig: String = "",
    val vpnServerName: String = "",
    val selectedVpnServerId: Int? = null,
    val isVpnEnabled: Boolean = false,
    val isKidsLockEnabled: Boolean = false,
    val kidsLockPin: String = "",
    val kidsLockMasterPin: String = "22834",

    val isLowDataModeEnabled: Boolean = false,
    val enabledExtensions: Set<String> = setOf(
        "uBlock0@raymondhill.net",
        "addon@darkreader.org"
    ),
    val vpnAutoDisconnectTimeout: Int = 5,
    val isUrlBarBottom: Boolean = false,
    val isDownloadsBiometricProtected: Boolean = false,
    val extensionUuidOverrides: Map<String, String> = emptyMap(),
    val pinnedExtensions: Set<String> = emptySet(),
    val autoIncognitoDomains: Set<String> = emptySet(),
    val userAgentType: UserAgentType = UserAgentType.DEFAULT
)
