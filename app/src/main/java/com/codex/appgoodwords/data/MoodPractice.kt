package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 어떤 기분의 날에 얼마나 움직였는지. */
data class MoodPracticeRow(
    val mood: DiaryMood,
    val dayCount: Int,
    /** 그 기분의 날 하루 평균 실천 수(루틴 체크 + 읽은 글귀 + 끝낸 할 일). */
    val averagePerDay: Float
)

/**
 * 기분과 실천을 나란히 놓아 봅니다.
 *
 * 기분 흐름 그래프와 실천 통계가 각각 있었지만 서로 이어지지 않았습니다.
 * "좋았던 날엔 얼마나 움직였나"는 두 기록을 겹쳐야만 나옵니다.
 *
 * **인과로 말하지 않습니다.** 많이 움직여서 기분이 좋았는지, 기분이 좋아서 많이 움직였는지는
 * 이 숫자로 알 수 없습니다. 화면도 "이런 날엔 이만큼 움직였습니다"까지만 말합니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object MoodPractice {
    /** 이보다 적은 날수의 기분은 내놓지 않습니다. 하루치로 "이런 날엔"이라고 할 수 없습니다. */
    const val MIN_DAYS = 2

    fun build(
        diaries: List<DiaryEntity>,
        routineChecks: List<RoutineCheckEntity>,
        events: List<ExposureEventEntity>,
        todos: List<TodoEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<MoodPracticeRow> {
        // 같은 날 일기를 두 편 썼는데 기분이 다르면 어느 쪽이라 할 수 없어 그날은 셈에서 뺍니다.
        val moodByDate = diaries
            .mapNotNull { diary ->
                val date = parseDate(diary.entryDate) ?: return@mapNotNull null
                val mood = DiaryMood.fromCode(diary.mood) ?: return@mapNotNull null
                date to mood
            }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (date, moods) -> moods.distinct().singleOrNull()?.let { date to it } }
            .toMap()

        if (moodByDate.isEmpty()) return emptyList()

        val actsByDate = mutableMapOf<LocalDate, Int>()
        fun count(date: LocalDate) {
            if (date in moodByDate) actsByDate[date] = (actsByDate[date] ?: 0) + 1
        }
        routineChecks.forEach { count(toDate(it.checkedAt, zoneId)) }
        events.filter { it.eventType == ExposureEventType.CONFIRMED }
            .forEach { count(toDate(it.occurredAt, zoneId)) }
        todos.mapNotNull { it.doneAt }.forEach { count(toDate(it, zoneId)) }

        return moodByDate.entries
            .groupBy({ it.value }, { it.key })
            .mapNotNull { (mood, dates) ->
                if (dates.size < MIN_DAYS) return@mapNotNull null
                val total = dates.sumOf { actsByDate[it] ?: 0 }
                MoodPracticeRow(
                    mood = mood,
                    dayCount = dates.size,
                    averagePerDay = total.toFloat() / dates.size
                )
            }
            // 많이 움직인 기분이 위로. 같으면 날이 많은 쪽이 먼저입니다.
            .sortedWith(
                compareByDescending<MoodPracticeRow> { it.averagePerDay }
                    .thenByDescending { it.dayCount }
                    .thenBy { it.mood.ordinal }
            )
    }

    private fun parseDate(value: String): LocalDate? =
        value.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun toDate(millis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
}
