package com.example.ether.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ether.browser.GeckoManager
import com.example.ether.data.model.AppSettings
import com.example.ether.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ExtensionViewModel @Inject constructor(
    private val geckoManager: GeckoManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val extensions: StateFlow<List<WebExtension>> = geckoManager.extensions
    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun updateExtensionUuid(extensionId: String, uuid: String) {
        viewModelScope.launch {
            settingsRepository.updateExtensionUuidOverride(extensionId, uuid)
        }
    }

    fun togglePinnedExtension(extensionId: String, pinned: Boolean) {
        viewModelScope.launch {
            settingsRepository.updatePinnedExtension(extensionId, pinned)
        }
    }

    init {
        refreshExtensions()
    }

    fun refreshExtensions() {
        geckoManager.listExtensions()
    }

    fun uninstallExtension(extension: WebExtension) {
        geckoManager.uninstallExtension(extension).accept(
            { 
                Timber.i("Extension uninstalled: ${extension.id}")
                refreshExtensions()
            },
            { err -> Timber.e(err, "Failed to uninstall extension: ${extension.id}") }
        )
    }

    fun toggleExtension(extension: WebExtension, enabled: Boolean) {
        val result = if (enabled) {
            geckoManager.enableExtension(extension)
        } else {
            geckoManager.disableExtension(extension)
        }

        result.accept(
            {
                Timber.i("Extension toggle success: ${extension.id} to $enabled")
                refreshExtensions()
            },
            { err -> Timber.e(err, "Failed to toggle extension: ${extension.id}") }
        )
    }
}
