package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "content_items")
data class ContentItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ContentType,
    val title: String,
    val body: String,
    val author: String = "",
    val sourceUrl: String = "",
    val thumbnailUrl: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val imageUris: List<String> = emptyList(),
    val videoUris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastShownAt: Long? = null,
    val showCount: Int = 0,
    val isFavorite: Boolean = false
)
