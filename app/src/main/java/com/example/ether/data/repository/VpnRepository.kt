package com.example.ether.data.repository

import com.example.ether.data.local.VpnDao
import com.example.ether.data.model.VpnServer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnRepository @Inject constructor(
    private val vpnDao: VpnDao
) {
    val allServers: Flow<List<VpnServer>> = vpnDao.getAllServers()

    suspend fun addServer(name: String, config: String) {
        vpnDao.insertServer(VpnServer(name = name, config = config))
    }

    suspend fun updateServer(server: VpnServer) {
        vpnDao.updateServer(server)
    }

    suspend fun deleteServer(server: VpnServer) {
        vpnDao.deleteServer(server)
    }
}
