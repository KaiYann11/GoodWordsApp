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

    /**
     * 노출 순환용 선택.
     *
     * 마지막으로 노출(SURFACED)된 지 가장 오래된 후보 [poolSize]개를 추린 뒤 그 안에서 무작위로 하나를 고른다.
     * 순수 RANDOM()과 달리 항목이 늘어도 특정 항목이 영영 안 나오는 일이 없고,
     * 후보를 여러 개 두어 순서가 기계적으로 반복되지도 않는다.
     *
     * showCount는 "확인한 횟수"라 노출 이력을 대신할 수 없어 exposure_events를 기준으로 쓴다.
     * 한 번도 노출되지 않은 항목은 COALESCE로 0이 되어 가장 먼저 뽑힌다.
     */
    @Query(
        "SELECT * FROM (" +
            "SELECT items.* FROM content_items AS items " +
            "LEFT JOIN (" +
            "SELECT contentItemId, MAX(occurredAt) AS lastSurfacedAt FROM exposure_events " +
            "WHERE eventType = :surfacedType GROUP BY contentItemId" +
            ") AS surfaced ON surfaced.contentItemId = items.id " +
            "WHERE (:category = '' OR items.category = :category) " +
            "ORDER BY COALESCE(surfaced.lastSurfacedAt, 0) ASC, items.showCount ASC " +
            "LIMIT :poolSize" +
            ") ORDER BY RANDOM() LIMIT 1"
    )
    suspend fun pickLeastRecentlySurfaced(
        category: String,
        poolSize: Int,
        surfacedType: ExposureEventType
    ): ContentItemEntity?

    @Query("UPDATE content_items SET lastShownAt = :readAt, showCount = showCount + 1 WHERE id = :id")
    suspend fun markRead(id: Long, readAt: Long)

    @Query("UPDATE content_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE content_items SET lastShownAt = NULL, showCount = 0")
    suspend fun resetReadCounts(): Int

    @Query("UPDATE content_items SET category = '' WHERE category = :category")
    suspend fun clearCategory(category: String): Int
}
