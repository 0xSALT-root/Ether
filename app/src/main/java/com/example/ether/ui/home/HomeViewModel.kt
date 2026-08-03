package com.example.ether.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ether.browser.vpn.VpnManager
import com.example.ether.data.model.Website
import com.example.ether.data.model.VpnServer
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.data.repository.VpnRepository
import com.example.ether.data.repository.WebsiteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WebsiteRepository,
    private val settingsRepository: SettingsRepository,
    private val vpnRepository: VpnRepository,
    private val vpnManager: VpnManager,
    private val geckoManager: com.example.ether.browser.GeckoManager
) : ViewModel() {

    val settings = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = com.example.ether.data.model.AppSettings()
        )

    val vpnServers = vpnRepository.allServers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId = _currentFolderId.asStateFlow()

    val currentFolder = _currentFolderId
        .map { id -> if (id == null) null else repository.getWebsiteById(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val websites: StateFlow<List<Website>> = _currentFolderId
        .flatMapLatest { id -> repository.getWebsitesByParent(id) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val allFolders = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun navigateToFolder(id: Long?) {
        _currentFolderId.value = id
    }

    fun toggleKidsLock() {
        viewModelScope.launch {
            val current = settings.value.isKidsLockEnabled
            settingsRepository.updateKidsLockEnabled(!current)
        }
    }

    fun toggleIncognito() {
        viewModelScope.launch {
            val current = settings.value.isIncognitoMode
            settingsRepository.updateIncognitoMode(!current)
        }
    }

    fun setKidsLockPin(pin: String) {
        viewModelScope.launch {
            settingsRepository.updateKidsLockPin(pin)
        }
    }

    fun addWebsite(name: String, url: String) {
        viewModelScope.launch {
            repository.addWebsite(name, url, _currentFolderId.value)
        }
    }

    fun addFolder(name: String) {
        viewModelScope.launch {
            repository.addFolder(name, _currentFolderId.value)
        }
    }

    fun moveWebsite(website: Website, newParentId: Long?) {
        viewModelScope.launch {
            repository.updateWebsite(website.copy(parentId = newParentId))
        }
    }

    fun deleteWebsite(website: Website) {
        viewModelScope.launch {
            repository.deleteWebsite(website)
        }
    }

    fun updateWebsite(website: Website) {
        viewModelScope.launch {
            repository.updateWebsite(website)
        }
    }

    fun updatePositions(reorderedList: List<Website>) {
        viewModelScope.launch {
            reorderedList.forEachIndexed { index, website ->
                repository.updateWebsite(website.copy(gridPosition = index))
            }
        }
    }

    fun clearBrowserData() {
        viewModelScope.launch {
            geckoManager.clearData()
        }
    }

    fun selectVpnServer(server: VpnServer) {
        viewModelScope.launch {
            settingsRepository.updateSelectedVpnServerId(server.id)
            // Also update the legacy fields for compatibility if needed, 
            // but primarily we should use the ID now.
            settingsRepository.updateVpnServerName(server.name)
            settingsRepository.updateVpnConfig(server.config)
        }
    }

    fun toggleVpn(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val intent = vpnManager.prepareVpnIntent()
                if (intent != null) {
                    _vpnIntent.emit(intent)
                    return@launch
                }
                // If already prepared, just force start to be sure
                vpnManager.forceStartVpn()
            }
            settingsRepository.updateVpnEnabled(enabled)
        }
    }

    private val _vpnIntent = MutableSharedFlow<android.content.Intent>()
    val vpnIntent = _vpnIntent.asSharedFlow()
}
