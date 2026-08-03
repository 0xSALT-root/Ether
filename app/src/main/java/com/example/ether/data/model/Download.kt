package com.example.ether.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val filePath: String,
    val mimeType: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isComplete: Boolean = false,
    val progress: Int = 0,
    val totalSizeBytes: Long = -1,
    val downloadId: Long = -1 // System DownloadManager ID
)
