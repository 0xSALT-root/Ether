package com.example.ether.data.repository

import com.example.ether.data.local.HistoryDao
import com.example.ether.data.model.HistoryItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {
    val allHistory: Flow<List<HistoryItem>> = historyDao.getAllHistory()

    suspend fun addHistoryItem(title: String, url: String) {
        historyDao.upsertHistoryItem(title, url)
    }

    suspend fun deleteHistoryItem(item: HistoryItem) {
        historyDao.deleteHistoryItem(item)
    }

    suspend fun clearHistory() {
        historyDao.clearHistory()
    }

    suspend fun deleteOldHistory(days: Int) {
        val threshold = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        historyDao.deleteOldHistory(threshold)
    }
}
