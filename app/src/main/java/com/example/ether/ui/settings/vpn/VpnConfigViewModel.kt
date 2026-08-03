package com.example.ether.ui.settings.vpn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ether.data.model.VpnServer
import com.example.ether.data.repository.SettingsRepository
import com.example.ether.data.repository.VpnRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VpnConfigViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val vpnRepository: VpnRepository,
    private val websiteRepository: com.example.ether.data.repository.WebsiteRepository
) : ViewModel() {

    val servers = vpnRepository.allServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWebsites = websiteRepository.allWebsites
        .map { list -> list.filter { it.url.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _serverName = MutableStateFlow("")
    val serverName = _serverName.asStateFlow()

    private val _vpnConfig = MutableStateFlow("")
    val vpnConfig = _vpnConfig.asStateFlow()

    private var editingServer: VpnServer? = null

    fun onServerNameChange(name: String) {
        _serverName.value = name
    }

    fun onVpnConfigChange(config: String) {
        _vpnConfig.value = config
    }

    fun toggleWebsiteProtection(website: com.example.ether.data.model.Website) {
        viewModelScope.launch {
            websiteRepository.updateIsProtected(website.id, !website.isProtected)
        }
    }

    fun importConfig(config: String, fileName: String? = null) {
        _vpnConfig.value = config
        // If a file name is provided (e.g. "US-Free.conf"), strip extension for server name
        fileName?.let {
            if (_serverName.value.isBlank()) {
                _serverName.value = it.substringBeforeLast(".")
            }
        }
        // Auto-save on import if we have a name
        if (_serverName.value.isNotBlank()) {
            saveConfig()
        }
    }

    fun saveConfig() {
        viewModelScope.launch {
            if (_serverName.value.isNotBlank() && _vpnConfig.value.isNotBlank()) {
                val server = editingServer
                if (server != null) {
                    vpnRepository.updateServer(server.copy(name = _serverName.value, config = _vpnConfig.value))
                    editingServer = null
                } else {
                    vpnRepository.addServer(_serverName.value, _vpnConfig.value)
                }
                _serverName.value = ""
                _vpnConfig.value = ""
            }
        }
    }

    fun editServer(server: VpnServer) {
        editingServer = server
        _serverName.value = server.name
        _vpnConfig.value = server.config
    }

    fun deleteServer(server: VpnServer) {
        viewModelScope.launch {
            vpnRepository.deleteServer(server)
        }
    }
}
