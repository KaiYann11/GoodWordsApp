package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentItemDao {
    @Query("SELECT * FROM content_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ContentItemEntity>>

    @Query("SELECT * FROM content_items WHERE type = :type ORDER BY createdAt DESC")
    fun observeByType(type: ContentType): Flow<List<ContentItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ContentItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ContentItemEntity>)

    @Query("SELECT * FROM content_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ContentItemEntity?

    @Query("DELETE FROM content_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM content_items")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM content_items")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM content_items " +
            "WHERE (:category = '' OR category = :category) " +
            "ORDER BY RANDOM() LIMIT 1"
    )
    suspend fun getRandomByCategory(category: String): ContentItemEntity?

    @Query("UPDATE content_items SET lastShownAt = :readAt, showCount = showCount + 1 WHERE id = :id")
    suspend fun markRead(id: Long, readAt: Long)

    @Query("UPDATE content_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE content_items SET lastShownAt = NULL, showCount = 0")
    suspend fun resetReadCounts(): Int

    @Query("UPDATE content_items SET category = '' WHERE category = :category")
    suspend fun clearCategory(category: String): Int
}
