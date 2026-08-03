package com.example.ether.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "websites")
data class Website(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String = "",
    val faviconPath: String? = null,
    val gridPosition: Int,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val isBookmark: Boolean = false,
    val category: String = "General",
    val visitCount: Int = 0,
    val isFolder: Boolean = false,
    val isProtected: Boolean = false,
    val parentId: Long? = null
)
