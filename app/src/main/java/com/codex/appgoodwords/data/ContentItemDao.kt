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

    @Query("DELETE FROM content_items WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM content_items")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM content_items")
    suspend fun count(): Int

    /**
     * 노출 순환용 선택.
     *
     * 마지막으로 노출된 지 가장 오래된 후보 [poolSize]개를 추린 뒤 그 안에서 무작위로 하나를 고른다.
     * 순수 RANDOM()과 달리 항목이 늘어도 특정 항목이 영영 안 나오는 일이 없고,
     * 후보를 여러 개 두어 순서가 기계적으로 반복되지도 않는다.
     *
     * 기준은 항목의 lastSurfacedAt이다. 노출 이력 테이블을 조인하면 사용자가 이력을 지웠을 때
     * 순환이 초기화되므로 항목에 직접 기록한다. showCount는 "확인한 횟수"라 쓸 수 없다.
     * 한 번도 노출되지 않은 항목은 COALESCE로 0이 되어 가장 먼저 뽑힌다.
     */
    @Query(
        "SELECT * FROM (" +
            "SELECT * FROM content_items " +
            "WHERE (:category = '' OR category = :category) " +
            "ORDER BY COALESCE(lastSurfacedAt, 0) ASC, showCount ASC " +
            "LIMIT :poolSize" +
            ") ORDER BY RANDOM() LIMIT 1"
    )
    suspend fun pickLeastRecentlySurfaced(
        category: String,
        poolSize: Int
    ): ContentItemEntity?

    @Query("UPDATE content_items SET lastSurfacedAt = :surfacedAt WHERE id = :id")
    suspend fun markSurfaced(id: Long, surfacedAt: Long)

    // 아래 갱신들은 병합에서 최신 변경으로 인식되도록 updatedAt을 함께 올린다.
    // markSurfaced만 예외로 두는데, 노출 순환은 기기별 상태여서 매번 동기화를 흔들 이유가 없다.
    @Query(
        "UPDATE content_items SET lastShownAt = :readAt, showCount = showCount + 1, " +
            "updatedAt = :readAt WHERE id = :id"
    )
    suspend fun markRead(id: Long, readAt: Long)

    @Query("UPDATE content_items SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean, updatedAt: Long)

    @Query("UPDATE content_items SET lastShownAt = NULL, showCount = 0, updatedAt = :updatedAt")
    suspend fun resetReadCounts(updatedAt: Long): Int

    @Query("UPDATE content_items SET category = '', updatedAt = :updatedAt WHERE category = :category")
    suspend fun clearCategory(category: String, updatedAt: Long): Int

    @Query("SELECT * FROM content_items")
    suspend fun getAll(): List<ContentItemEntity>
}
