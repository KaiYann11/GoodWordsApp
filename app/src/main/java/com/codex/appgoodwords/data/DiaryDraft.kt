package com.codex.appgoodwords.data

import java.time.LocalDate

/** 화면에서 편집 중인 일기. 저장 전까지는 DB에 없습니다. */
data class DiaryDraft(
    val id: Long = 0L,
    val entryDate: LocalDate = LocalDate.now(),
    val title: String = "",
    val body: String = "",
    /**
     * 날씨와 기분은 고른 [DiaryWeather]·[DiaryMood]의 이름입니다. 안 골랐으면 빈 문자열입니다.
     *
     * 열거형이 아니라 문자열로 들고 있는 이유는, 이 앱이 모르는 값이 동기화로 들어왔을 때
     * 그 일기를 고쳐도 값이 지워지지 않게 하려는 것입니다.
     */
    val weather: String = "",
    val mood: String = "",
    val imageUris: List<String> = emptyList(),
    val videoUris: List<String> = emptyList(),
    val audioUris: List<String> = emptyList()
) {
    /** 사진만 넣거나 날씨·기분만 남기는 날도 있으므로 본문만 보고 판단하지 않습니다. */
    val hasSomethingToSave: Boolean
        get() = title.isNotBlank() || body.isNotBlank() ||
            weather.isNotBlank() || mood.isNotBlank() ||
            imageUris.isNotEmpty() || videoUris.isNotEmpty() || audioUris.isNotEmpty()

    val weatherOption: DiaryWeather?
        get() = DiaryWeather.fromCode(weather)

    val moodOption: DiaryMood?
        get() = DiaryMood.fromCode(mood)

    companion object {
        fun from(diary: DiaryEntity): DiaryDraft = DiaryDraft(
            id = diary.id,
            entryDate = runCatching { LocalDate.parse(diary.entryDate) }.getOrElse { LocalDate.now() },
            title = diary.title,
            body = diary.body,
            weather = diary.weather,
            mood = diary.mood,
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
