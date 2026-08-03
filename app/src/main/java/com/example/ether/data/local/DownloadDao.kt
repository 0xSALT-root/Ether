package com.example.ether.data.local

import androidx.room.*
import com.example.ether.data.model.Download
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<Download>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: Download): Long

    @Update
    suspend fun updateDownload(download: Download)

    @Delete
    suspend fun deleteDownload(download: Download)

    @Query("SELECT * FROM downloads WHERE downloadId = :downloadId")
    suspend fun getDownloadBySystemId(downloadId: Long): Download?

    @Query("UPDATE downloads SET progress = :progress, totalSizeBytes = :totalSize, isComplete = :isComplete WHERE downloadId = :downloadId")
    suspend fun updateProgress(downloadId: Long, progress: Int, totalSize: Long, isComplete: Boolean)

    @Query("SELECT * FROM downloads WHERE fileName LIKE :query ORDER BY timestamp DESC")
    fun searchDownloads(query: String): Flow<List<Download>>

    @Query("SELECT * FROM downloads ORDER BY totalSizeBytes DESC")
    fun getDownloadsByLargest(): Flow<List<Download>>

    @Query("DELETE FROM downloads WHERE isComplete = 1")
    suspend fun clearCompletedDownloads()
}
