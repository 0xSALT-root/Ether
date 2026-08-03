package com.example.ether.data.local

import androidx.room.*
import com.example.ether.data.model.HistoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryItem(item: HistoryItem)

    @Delete
    suspend fun deleteHistoryItem(item: HistoryItem)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun getHistoryItemByUrl(url: String): HistoryItem?

    @Query("DELETE FROM history WHERE timestamp < :threshold")
    suspend fun deleteOldHistory(threshold: Long)

    @Transaction
    suspend fun upsertHistoryItem(title: String, url: String) {
        val existing = getHistoryItemByUrl(url)
        if (existing != null) {
            insertHistoryItem(existing.copy(title = title, timestamp = System.currentTimeMillis()))
        } else {
            insertHistoryItem(HistoryItem(title = title, url = url))
        }
    }
}
