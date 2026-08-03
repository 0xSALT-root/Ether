package com.example.ether.ui.settings

import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ether.data.model.Download
import com.example.ether.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import timber.log.Timber

data class MediaPromptInfo(
    val fileName: String,
    val url: String,
    val mimeType: String?,
    val size: Long
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _detectedMedia = MutableStateFlow<MediaPromptInfo?>(null)
    val detectedMedia = _detectedMedia.asStateFlow()

    val downloads: StateFlow<Map<String, List<Download>>> = repository.allDownloads
        .map { list ->
            list.groupBy { download ->
                categorizeMimeType(download.mimeType)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    init {
        viewModelScope.launch {
            repository.allDownloads.collectLatest { list ->
                val hasActiveDownloads = list.any { !it.isComplete && it.downloadId != -1L }
                if (hasActiveDownloads) {
                    startProgressPolling()
                } else {
                    stopProgressPolling()
                }
            }
        }
    }

    private var pollingJob: kotlinx.coroutines.Job? = null

    private fun startProgressPolling() {
        if (pollingJob?.isActive == true) return
        
        pollingJob = viewModelScope.launch {
            while (true) {
                val currentDownloads = repository.allDownloads.first()
                val active = currentDownloads.filter { !it.isComplete && it.downloadId != -1L }
                
                if (active.isEmpty()) break

                active.forEach { download ->
                    val query = DownloadManager.Query().setFilterById(download.downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        
                        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        val isFinished = status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED
                        
                        repository.updateDownloadProgress(download.downloadId, progress, total, isFinished)
                    }
                    cursor?.close()
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun deleteDownload(download: Download) {
        viewModelScope.launch {
            repository.deleteDownload(download)
        }
    }

    private fun categorizeMimeType(mimeType: String?): String {
        if (mimeType == null) return "Others"
        val type = mimeType.lowercase()
        return when {
            type.startsWith("image/") -> "Images"
            type.startsWith("video/") || type.contains("mpegurl") || type.contains("dash+xml") || type.contains("video/mp2t") -> "Videos"
            type.startsWith("audio/") -> "Audio"
            type.contains("pdf") || type.contains("msword") || type.contains("officedocument") -> "Documents"
            type.contains("zip") || type.contains("rar") || type.contains("7z") || type.contains("tar") -> "Archives"
            type.contains("apk") -> "Apps"
            else -> "Others"
        }
    }
}
