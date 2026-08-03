package com.example.ether.data.local

import androidx.room.*
import com.example.ether.data.model.VpnServer
import kotlinx.coroutines.flow.Flow

@Dao
interface VpnDao {
    @Query("SELECT * FROM vpn_servers")
    fun getAllServers(): Flow<List<VpnServer>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: VpnServer)

    @Delete
    suspend fun deleteServer(server: VpnServer)

    @Update
    suspend fun updateServer(server: VpnServer)
}
