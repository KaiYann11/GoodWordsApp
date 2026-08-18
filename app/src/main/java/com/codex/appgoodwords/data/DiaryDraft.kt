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
    /** [DiaryKind]의 이름. 물음 없이 쓰면 `FREE`입니다. 모르는 값도 그대로 들고 있습니다. */
    val kind: String = DiaryKind.FREE.name,
    /** 물음에 대한 답. [DiaryKind.prompts]와 같은 순서라 빈칸도 자리를 지킵니다. */
    val answers: List<String> = emptyList(),
    val imageUris: List<String> = emptyList(),
    val videoUris: List<String> = emptyList(),
    val audioUris: List<String> = emptyList()
) {
    val kindOption: DiaryKind
        get() = DiaryKind.fromCode(kind) ?: DiaryKind.FREE

    /**
     * 물음이 있는 일기에는 날씨·기분을 붙이지 않습니다.
     *
     * 감사·반성은 물음에 답하는 자리라 고를 것이 늘어날수록 손이 무거워집니다. 그날의 날씨와
     * 기분은 자유 일기에서 남기면 됩니다.
     *
     * 자유 일기로 쓰다 종류를 바꾸면 이미 고른 값이 [weather]·[mood]에 남아 있는데,
     * 저장할 때는 이 값을 씁니다. **화면에 없는 값을 저장하면 안 됩니다.**
     * 원래 값은 그대로 두어서 종류를 되돌리면 고른 것이 다시 보입니다.
     */
    val effectiveWeather: String
        get() = if (kindOption.isGuided) "" else weather.trim()

    val effectiveMood: String
        get() = if (kindOption.isGuided) "" else mood.trim()

    /**
     * 사진만 넣거나 날씨·기분만 남기는 날도 있으므로 본문만 보고 판단하지 않습니다.
     * 감사·반성 일기는 본문 없이 답만 적는 날이 흔해서 답도 함께 봅니다.
     *
     * 날씨·기분은 [effectiveWeather]로 봅니다. 화면에 안 보이는 값으로 저장 버튼이 켜지면
     * 아무것도 안 적은 일기가 저장됩니다.
     */
    val hasSomethingToSave: Boolean
        get() = title.isNotBlank() || body.isNotBlank() ||
            effectiveWeather.isNotBlank() || effectiveMood.isNotBlank() ||
            answers.any { it.isNotBlank() } ||
            imageUris.isNotEmpty() || videoUris.isNotEmpty() || audioUris.isNotEmpty()

    val weatherOption: DiaryWeather?
        get() = DiaryWeather.fromCode(weather)

    val moodOption: DiaryMood?
        get() = DiaryMood.fromCode(mood)

    /** 물음 하나의 답만 바꿉니다. 아직 짧은 목록이어도 물음 개수만큼은 자리를 채워 둡니다. */
    fun withAnswer(index: Int, answer: String): DiaryDraft {
        val size = maxOf(kindOption.prompts.size, answers.size)
        if (index !in 0 until size) return this
        val padded = DiaryAnswers.padded(answers, size)
        return copy(answers = padded.mapIndexed { at, value -> if (at == index) answer else value })
    }

    companion object {
        fun from(diary: DiaryEntity): DiaryDraft = DiaryDraft(
            id = diary.id,
            entryDate = runCatching { LocalDate.parse(diary.entryDate) }.getOrElse { LocalDate.now() },
            title = diary.title,
            body = diary.body,
            weather = diary.weather,
            mood = diary.mood,
            kind = diary.kind,
            answers = diary.answers,
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
