package com.example.ether.data.local

import androidx.room.*
import com.example.ether.data.model.Website
import kotlinx.coroutines.flow.Flow

@Dao
interface WebsiteDao {
    @Query("SELECT * FROM websites ORDER BY gridPosition ASC")
    fun getAllWebsites(): Flow<List<Website>>

    @Query("SELECT * FROM websites WHERE parentId IS :parentId ORDER BY gridPosition ASC")
    fun getWebsitesByParent(parentId: Long?): Flow<List<Website>>

    @Query("SELECT * FROM websites WHERE isFolder = 1 AND id != :excludeId")
    fun getAllFolders(excludeId: Long): Flow<List<Website>>

    @Query("SELECT * FROM websites WHERE isFolder = 1")
    fun getAllFolders(): Flow<List<Website>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebsite(website: Website)

    @Update
    suspend fun updateWebsite(website: Website)

    @Delete
    suspend fun deleteWebsite(website: Website)

    @Query("DELETE FROM websites WHERE parentId = :parentId")
    suspend fun deleteWebsitesByParent(parentId: Long)

    @Query("UPDATE websites SET parentId = NULL WHERE parentId = :parentId")
    suspend fun moveWebsitesToRoot(parentId: Long)

    @Query("SELECT * FROM websites WHERE id = :id")
    suspend fun getWebsiteById(id: Long): Website?

    @Query("SELECT MAX(gridPosition) FROM websites")
    suspend fun getMaxPosition(): Int?

    @Query("SELECT * FROM websites WHERE isBookmark = 1 ORDER BY name ASC")
    fun getBookmarks(): Flow<List<Website>>

    @Query("SELECT DISTINCT category FROM websites")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT * FROM websites WHERE category = :category ORDER BY name ASC")
    fun getWebsitesByCategory(category: String): Flow<List<Website>>

    @Query("UPDATE websites SET visitCount = visitCount + 1, lastOpenedTimestamp = :timestamp WHERE id = :id")
    suspend fun incrementVisitCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM websites WHERE name LIKE :query OR url LIKE :query")
    fun searchWebsites(query: String): Flow<List<Website>>

    @Query("UPDATE websites SET isProtected = :isProtected WHERE id = :id")
    suspend fun updateIsProtected(id: Long, isProtected: Boolean)

    @Query("SELECT * FROM websites WHERE isProtected = 1")
    fun getProtectedWebsites(): Flow<List<Website>>
}
