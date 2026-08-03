package com.example.ether.data.repository

import androidx.room.withTransaction
import com.example.ether.data.local.EtherDatabase
import com.example.ether.data.local.WebsiteDao
import com.example.ether.data.model.Website
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebsiteRepository @Inject constructor(
    private val websiteDao: WebsiteDao,
    private val database: EtherDatabase
) {
    val allWebsites: Flow<List<Website>> = websiteDao.getAllWebsites()

    fun getWebsitesByParent(parentId: Long?): Flow<List<Website>> = 
        websiteDao.getWebsitesByParent(parentId)

    fun getAllFolders(excludeId: Long? = null): Flow<List<Website>> =
        if (excludeId == null) websiteDao.getAllFolders() else websiteDao.getAllFolders(excludeId)

    suspend fun getWebsiteById(id: Long): Website? = websiteDao.getWebsiteById(id)

    suspend fun addWebsite(name: String, url: String, parentId: Long? = null) {
        val nextPosition = (websiteDao.getMaxPosition() ?: -1) + 1
        val trimmedUrl = url.trim()
        val formattedUrl = if (trimmedUrl.isNotEmpty() && !trimmedUrl.contains("://") && !trimmedUrl.startsWith("about:") && !trimmedUrl.startsWith("resource:")) {
            "https://$trimmedUrl"
        } else {
            trimmedUrl
        }
        
        // Basic validation: allow common browser schemes
        if (formattedUrl.isNotEmpty()) {
            val isAllowedScheme = formattedUrl.startsWith("http://") || 
                                 formattedUrl.startsWith("https://") || 
                                 formattedUrl.startsWith("about:") || 
                                 formattedUrl.startsWith("resource:") || 
                                 formattedUrl.startsWith("moz-extension://") ||
                                 formattedUrl.startsWith("file://")
            
            if (!isAllowedScheme) return
        }

        val faviconUrl = if (formattedUrl.isNotEmpty()) {
            try {
                val domain = java.net.URI(formattedUrl).host
                if (domain != null) {
                    "https://www.google.com/s2/favicons?domain=$domain&sz=128"
                } else null
            } catch (e: Exception) {
                null
            }
        } else null
        
        val website = Website(
            name = name.ifBlank { "Untitled" },
            url = formattedUrl,
            faviconPath = faviconUrl,
            gridPosition = nextPosition,
            parentId = parentId
        )
        websiteDao.insertWebsite(website)
    }

    suspend fun addFolder(name: String, parentId: Long? = null) {
        val nextPosition = (websiteDao.getMaxPosition() ?: -1) + 1
        val website = Website(
            name = name.ifBlank { "New Folder" },
            url = "",
            isFolder = true,
            gridPosition = nextPosition,
            parentId = parentId
        )
        websiteDao.insertWebsite(website)
    }

    suspend fun deleteWebsite(website: Website) {
        database.withTransaction {
            if (website.isFolder) {
                websiteDao.deleteWebsitesByParent(website.id)
            }
            websiteDao.deleteWebsite(website)
        }
    }

    suspend fun updateWebsite(website: Website) {
        websiteDao.updateWebsite(website)
    }

    val bookmarks: Flow<List<Website>> = websiteDao.getBookmarks()
    val allCategories: Flow<List<String>> = websiteDao.getAllCategories()

    fun getWebsitesByCategory(category: String): Flow<List<Website>> =
        websiteDao.getWebsitesByCategory(category)

    suspend fun incrementVisitCount(id: Long) {
        websiteDao.incrementVisitCount(id)
    }

    fun searchWebsites(query: String): Flow<List<Website>> =
        websiteDao.searchWebsites("%$query%")

    suspend fun toggleBookmark(website: Website) {
        websiteDao.updateWebsite(website.copy(isBookmark = !website.isBookmark))
    }

    suspend fun updateIsProtected(id: Long, isProtected: Boolean) {
        websiteDao.updateIsProtected(id, isProtected)
    }

    val protectedWebsites: Flow<List<Website>> = websiteDao.getProtectedWebsites()
}
