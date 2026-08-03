package com.example.ether.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ether.browser.GeckoManager
import com.example.ether.data.model.AppSettings
import com.example.ether.data.repository.BackupRepository
import com.example.ether.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val backupRepository: BackupRepository,
    private val geckoManager: GeckoManager
) : ViewModel() {

    private val _backupStatus = MutableSharedFlow<Result<Unit>>()
    val backupStatus: SharedFlow<Result<Unit>> = _backupStatus.asSharedFlow()

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun toggleJavaScript(enabled: Boolean) {
        viewModelScope.launch { repository.updateJavaScriptEnabled(enabled) }
    }

    fun toggleCookies(enabled: Boolean) {
        viewModelScope.launch { repository.updateCookiesEnabled(enabled) }
    }

    fun toggleDesktopMode(enabled: Boolean) {
        viewModelScope.launch { repository.updateDesktopModeDefault(enabled) }
    }

    fun toggleAutoplay(enabled: Boolean) {
        viewModelScope.launch { repository.updateAutoplayEnabled(enabled) }
    }

    fun togglePopupBlocking(enabled: Boolean) {
        viewModelScope.launch { repository.updatePopupBlockingEnabled(enabled) }
    }

    fun toggleBiometricLock(enabled: Boolean) {
        viewModelScope.launch { repository.updateBiometricLockEnabled(enabled) }
    }

    fun toggleAmoledMode(enabled: Boolean) {
        viewModelScope.launch { repository.updateAmoledModeEnabled(enabled) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { repository.updateDarkMode(enabled) }
    }

    fun toggleScreenshotProtection(enabled: Boolean) {
        viewModelScope.launch { repository.updateScreenshotProtection(enabled) }
    }

    fun toggleIncognito(enabled: Boolean) {
        viewModelScope.launch { repository.updateIncognitoMode(enabled) }
    }

    fun toggleVpn(enabled: Boolean) {
        viewModelScope.launch { repository.updateVpnEnabled(enabled) }
    }

    fun toggleKidsLock(enabled: Boolean) {
        viewModelScope.launch { repository.updateKidsLockEnabled(enabled) }
    }

    fun updateKidsLockPin(pin: String) {
        viewModelScope.launch { repository.updateKidsLockPin(pin) }
    }

    fun toggleLowDataMode(enabled: Boolean) {
        viewModelScope.launch { repository.updateLowDataModeEnabled(enabled) }
    }

    fun updateVpnAutoDisconnectTimeout(timeout: Int) {
        viewModelScope.launch { repository.updateVpnAutoDisconnectTimeout(timeout) }
    }

    fun toggleUrlBarPosition(isBottom: Boolean) {
        viewModelScope.launch { repository.updateUrlBarBottom(isBottom) }
    }

    fun updateTheme(themeName: String) {
        viewModelScope.launch { repository.updateSelectedTheme(themeName) }
    }

    fun toggleExtension(extensionId: String, enabled: Boolean) {
        viewModelScope.launch { repository.updateExtensionEnabled(extensionId, enabled) }
    }

    fun clearAllData() {
        viewModelScope.launch {
            geckoManager.clearData()
            // Reset other repositories if needed
        }
    }

    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            val result = backupRepository.createBackup(uri)
            _backupStatus.emit(result)
        }
    }

    fun restoreBackup(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            val result = backupRepository.restoreBackup(uri)
            _backupStatus.emit(result)
            if (result.isSuccess) {
                // Force app restart to ensure all components pick up restored data
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
