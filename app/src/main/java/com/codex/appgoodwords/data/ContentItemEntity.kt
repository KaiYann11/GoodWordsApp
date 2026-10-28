package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "content_items",
    indices = [Index(value = ["syncId"], unique = true)]
)
data class ContentItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
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
    /**
     * 이 글귀를 뽑아낸 책. 책에서 뽑지 않았으면 빈 문자열입니다.
     *
     * 숫자 id가 아니라 syncId로 가리킵니다. 숫자 id는 기기마다 따로 증가해서
     * 다른 기기로 넘어가면 엉뚱한 책을 가리킵니다.
     */
    val bookSyncId: String = "",
    /** 뽑아낸 쪽수. 0이면 적지 않은 것입니다. */
    val bookPage: Int = 0,
    /**
     * 이 이상향·목표·롤모델을 위해 하는 루틴([ContentType.isGoal]일 때만 씁니다).
     *
     * 루틴의 syncId로 가리킵니다(숫자 id는 기기마다 다릅니다). 같은 내용 합치기로 루틴이 줄면 남은 쪽으로 옮기고
     * ([SyncDeduplicator]·서버 `deduplicate`), 지운 루틴을 가리키는 것은 화면에서만 빼고 그대로 둡니다.
     * 몇 번 밟았는지는 저장하지 않고 체크 기록에서 그때그때 셉니다.
     */
    val linkedRoutineSyncIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastShownAt: Long? = null,
    /** 마지막으로 노출된 시각. 이력을 지워도 노출 순환이 초기화되지 않도록 항목에 직접 둔다. */
    val lastSurfacedAt: Long? = null,
    val showCount: Int = 0,
    val isFavorite: Boolean = false
)
