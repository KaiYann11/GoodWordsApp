package com.codex.appgoodwords.data

import java.time.LocalDate

/** 화면에서 편집 중인 일기. 저장 전까지는 DB에 없습니다. */
data class DiaryDraft(
    val id: Long = 0L,
    val entryDate: LocalDate = LocalDate.now(),
    val title: String = "",
    val body: String = "",
    val imageUris: List<String> = emptyList(),
    val videoUris: List<String> = emptyList(),
    val audioUris: List<String> = emptyList()
) {
    /** 사진만 넣고 글을 안 쓰는 날도 있으므로 본문만 보고 판단하지 않습니다. */
    val hasSomethingToSave: Boolean
        get() = title.isNotBlank() || body.isNotBlank() ||
            imageUris.isNotEmpty() || videoUris.isNotEmpty() || audioUris.isNotEmpty()

    companion object {
        fun from(diary: DiaryEntity): DiaryDraft = DiaryDraft(
            id = diary.id,
            entryDate = runCatching { LocalDate.parse(diary.entryDate) }.getOrElse { LocalDate.now() },
            title = diary.title,
            body = diary.body,
            imageUris = diary.imageUris,
            videoUris = diary.videoUris,
            audioUris = diary.audioUris
        )
    }
}

/** 화면에서 편집 중인 할 일. */
data class TodoDraft(
    val id: Long = 0L,
    val title: String = "",
    val note: String = "",
    val dueDate: LocalDate = LocalDate.now(),
    /** 알람 시각(epoch millis). null이면 알리지 않습니다. */
    val remindAt: Long? = null
) {
    companion object {
        fun from(todo: TodoEntity): TodoDraft = TodoDraft(
            id = todo.id,
            title = todo.title,
            note = todo.note,
            dueDate = runCatching { LocalDate.parse(todo.dueDate) }.getOrElse { LocalDate.now() },
            remindAt = todo.remindAt
        )
    }
}
