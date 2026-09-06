package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 글귀 옆에 덧붙여 쌓는 메모.
 *
 * 글귀 본문은 대개 남이 쓴 말이라 고칠 수 없습니다. 고치면 더는 그 사람의 말이 아닙니다.
 * 그런데 같은 글귀라도 읽을 때마다 드는 생각이 다릅니다. 그래서 루틴처럼 옆에 붙여 둡니다.
 * 본문에 덧붙여 적는 것과 달리 **언제 무슨 생각을 했는지가 날짜와 함께 남습니다.**
 *
 * 루틴 메모와 달리 **체크를 남기지 않습니다.** 루틴 메모는 "오늘 밟았다"는 기록이 함께 붙지만,
 * 글귀에 적는 것은 실천이 아니라 생각이라 하루의 실천 수를 늘리면 안 됩니다.
 */
@Entity(
    tableName = "content_memos",
    indices = [
        Index("createdAt"),
        Index("contentItemId", "createdAt"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class ContentMemoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    val contentItemId: Long,
    /** contentItemId는 기기마다 따로 증가하므로, 기기 간에는 이 값으로 글귀를 가리킨다. */
    val contentItemSyncId: String = "",
    val contentTitle: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis()
)
