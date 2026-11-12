package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 두 기록을 잇는 줄 하나(사용자가 정함, 2026-11-12 — "모든 기록들간에 상호 연결성을 만들어 파악").
 *
 * **방향이 없습니다.** 어느 쪽에서 보아도 이어진 기록이고, from·to는 적은 차례일 뿐입니다. 그래서 같은 두 기록을
 * 거꾸로 이은 줄도 같은 줄입니다([RecordLinks.fingerprint]).
 *
 * 기기마다 같은 기록을 가리키도록 syncId로만 가리킵니다. **지운 기록을 가리키는 줄은 화면에서만 빼고 그대로 둡니다**
 * (목표에 이어 둔 루틴과 같은 규칙) — 줄은 작고, 다른 기기에서 그 기록이 아직 살아 있을 수 있습니다.
 * 합치기로 기록이 줄면 남은 쪽을 가리키게 옮깁니다(앱 [SyncDeduplicator]·서버 `deduplicate`).
 */
@Entity(
    tableName = "record_links",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["fromSyncId"]),
        Index(value = ["toSyncId"])
    ]
)
data class RecordLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** [RecordKind]의 이름. */
    val fromKind: String,
    val fromSyncId: String,
    val toKind: String,
    val toSyncId: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    val from: RecordRef
        get() = RecordRef(fromKind, fromSyncId)

    val to: RecordRef
        get() = RecordRef(toKind, toSyncId)

    /** 이 줄이 [ref]에 닿아 있으면 반대쪽 끝, 아니면 null. */
    fun otherEnd(ref: RecordRef): RecordRef? = when (ref) {
        from -> to
        to -> from
        else -> null
    }
}

/** 기록 하나를 기기와 상관없이 가리킵니다. [kind]는 [RecordKind]의 이름입니다. */
data class RecordRef(val kind: String, val syncId: String) {
    companion object {
        fun of(item: ContentItemEntity) = RecordRef(RecordKind.CONTENT_ITEM.name, item.syncId)
        fun of(diary: DiaryEntity) = RecordRef(RecordKind.DIARY.name, diary.syncId)
        fun of(book: BookEntity) = RecordRef(RecordKind.BOOK.name, book.syncId)
        fun of(routine: RoutineEntity) = RecordRef(RecordKind.ROUTINE.name, routine.syncId)
        fun of(todo: TodoEntity) = RecordRef(RecordKind.TODO.name, todo.syncId)
        fun of(walk: WalkEntity) = RecordRef(RecordKind.WALK.name, walk.syncId)
    }
}

/**
 * 이을 수 있는 기록의 종류. 이름은 삭제 표식([SyncEntityType])의 이름과 같습니다.
 *
 * 한 번 생기면 바뀌지 않는 것(체크·기분·독서 기록·걸음 수)은 잇지 않습니다 — 그것들은 "같은 날"로 이어져 보입니다.
 * 서버 `recordKinds`와 같아야 합니다. 모르는 종류의 줄은 앱도 서버도 버립니다.
 */
enum class RecordKind {
    CONTENT_ITEM,
    DIARY,
    BOOK,
    ROUTINE,
    TODO,
    WALK;

    companion object {
        fun fromNameOrNull(value: String?): RecordKind? = entries.firstOrNull { it.name == value?.trim() }
    }
}
