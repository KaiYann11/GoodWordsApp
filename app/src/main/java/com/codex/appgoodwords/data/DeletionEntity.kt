package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 삭제 표식(tombstone).
 *
 * 병합에서 삭제를 표현하려면 "없음"만으로는 부족합니다. 상대 기기에는 아직 그 레코드가 있어서
 * 지운 항목이 되살아나기 때문입니다. 지울 때 표식을 남기고, 표식이 레코드의 updatedAt보다
 * 최신이면 삭제를 유지합니다.
 */
@Entity(
    tableName = "deletions",
    indices = [Index("deletedAt")]
)
data class DeletionEntity(
    @PrimaryKey val syncId: String,
    val entityType: SyncEntityType,
    val deletedAt: Long = System.currentTimeMillis()
)
