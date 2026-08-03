package com.example.ether.data.repository

import com.example.ether.data.local.DownloadDao
import com.example.ether.data.model.Download
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {
    val allDownloads: Flow<List<Download>> = downloadDao.getAllDownloads()

    suspend fun addDownload(fileName: String, url: String, filePath: String, mimeType: String?, downloadId: Long) {
        val download = Download(
            fileName = fileName,
            url = url,
            filePath = filePath,
            mimeType = mimeType,
            isComplete = false,
            downloadId = downloadId
        )
        downloadDao.insertDownload(download)
        
        // If not using system DownloadManager (downloadId == -1), use our parallel worker
        if (downloadId == -1L) {
            val file = java.io.File(filePath)
            ParallelDownloadWorker(url, file).startDownload(
                onProgress = { progress ->
                    CoroutineScope(Dispatchers.IO).launch {
                        updateDownloadProgress(downloadId, progress, -1, false)
                    }
                },
                onComplete = {
                    CoroutineScope(Dispatchers.IO).launch {
                        updateDownloadProgress(downloadId, 100, -1, true)
                    }
                },
                onError = { /* Log error */ }
            )
        }
    }

    suspend fun updateDownloadProgress(downloadId: Long, progress: Int, totalSize: Long, isComplete: Boolean) {
        downloadDao.updateProgress(downloadId, progress, totalSize, isComplete)
    }

    suspend fun deleteDownload(download: Download) {
        downloadDao.deleteDownload(download)
    }

    fun searchDownloads(query: String): Flow<List<Download>> =
        downloadDao.searchDownloads("%$query%")

    fun getDownloadsByLargest(): Flow<List<Download>> =
        downloadDao.getDownloadsByLargest()

    suspend fun clearCompletedDownloads() {
        downloadDao.clearCompletedDownloads()
    }
}
