package com.example.ether.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ether.data.model.Download
import com.example.ether.data.model.HistoryItem
import com.example.ether.data.model.VpnServer
import com.example.ether.data.model.Website

@Database(entities = [Website::class, Download::class, VpnServer::class, HistoryItem::class], version = 5, exportSchema = false)
abstract class EtherDatabase : RoomDatabase() {
    abstract fun websiteDao(): WebsiteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun vpnDao(): VpnDao
    abstract fun historyDao(): HistoryDao
}
