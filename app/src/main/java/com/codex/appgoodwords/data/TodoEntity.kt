package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 오늘 해야 할 일.
 *
 * 루틴과 다릅니다. 루틴은 매일 반복하며 몇 번 했는지를 세고, 할 일은 한 번 끝내면 없어집니다.
 * 못 끝낸 할 일은 날짜에 남되 오늘 목록에 "지난 일"로 함께 보입니다([isOverdueOn]).
 */
@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["dueDate"])
    ]
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    val title: String,
    val note: String = "",
    /** ISO `yyyy-MM-dd`. 이 할 일이 속한 날. */
    val dueDate: String,
    /** 알람 시각(epoch millis). 없으면 알리지 않습니다. */
    val remindAt: Long? = null,
    /** 완료 시각. null이면 아직 안 한 것입니다. */
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * 붙여 둔 사진(사용자가 정함, 2026-11-15) — 반품할 영수증, 살 물건처럼 할 일을 할 때 볼 것.
     * 일기·보관함 사진과 같은 주소이고, 동기화 직전에 서버 주소로 바뀝니다([AttachmentUploader]).
     */
    val imageUris: List<String> = emptyList(),
    /**
     * 반복(사용자가 정함, 2026-11-19) — 매주·2주마다·매달·매년. 빈 글이면 한 번뿐인 할 일입니다.
     * 꼴과 셈은 [TodoRepeats]에 있고, 끝내면 다음 회차가 새 할 일로 생깁니다.
     */
    val repeatRule: String = "",
    /**
     * 이 할 일을 끝내며 다음 회차를 만든 때. 만들었으면 끝냄을 거뒀다가 다시 끝내도 또 만들지 않습니다 — 다음 회차를
     * 지워 반복을 멈춘 사람에게 되살아나지 않습니다. 끝냄을 바로 "되돌리기"하면 다음 회차와 함께 지워집니다.
     */
    val repeatedAt: Long? = null
) {
    val isDone: Boolean
        get() = doneAt != null

    /** 기준일보다 앞선 날짜인데 아직 안 끝낸 일. 오늘 목록 위쪽에 따로 모읍니다. */
    fun isOverdueOn(today: LocalDate): Boolean {
        if (isDone) return false
        val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return false
        return due.isBefore(today)
    }
}
