package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * 하루 마무리(사용자가 정함, 2026-11-19) — 저녁에 한 화면에서 오늘 한 것을 보고, 지금 기분 → 했는데 못 적은 루틴 → 오늘 감사한 일
 * 한 줄 → 내일 할 일 하나를 채우고 [마무리]로 끝냅니다.
 *
 * 새 기록 종류는 없습니다. 기분은 기분 기록, 루틴은 체크, 감사 한 줄은 오늘의 감사 일기, 내일 할 일은 내일 날짜의 할 일로 남아
 * 늘 하던 길로 동기화됩니다. 마무리를 마친 날만 이 기기에 적어 두어([SettingsStore.dayClosedDateFlow]) 홈 카드와 알림을 그날은
 * 거둡니다.
 *
 * 여는 길(사용자가 고름): 마무리 시각(설정의 하루 마무리 알림 — 예전 "일일 집계" 시각, 기본 21:00)의 알림, 그리고 그 시각 뒤
 * 홈 "오늘" 칸 맨 아래의 마무리 카드.
 */
object DayClose {
    /** "오늘 한 것"에 펼치는 수. 길어지면 알림 한 줄에 다 들어가지 않습니다. */
    private const val DONE_KINDS = 4

    /**
     * 홈에 마무리 카드를 둘 때 — 마무리 시각이 지났고 그날 아직 마무리하지 않았으면. 알림을 꺼 두었어도 카드는 시각을 따릅니다
     * (알림 스위치는 알림만 끕니다).
     */
    fun isDue(now: LocalDateTime, hour: Int, minute: Int, closedDate: String): Boolean {
        val time = now.hour * 60 + now.minute
        return time >= hour * 60 + minute && closedDate != now.toLocalDate().toString()
    }

    /** 오늘 한 것 한 줄 — "루틴 3번 · 글귀 2개 · 할 일 1개 끝냄 · 6,240걸음". 아직 한 것이 없으면 null입니다. */
    fun doneLine(digest: DayDigest): String? =
        DayComparisons.countsOf(digest)
            .take(DONE_KINDS)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ") { (kind, count) -> DayComparisons.amount(kind, count) }

    /** 알림의 글. 한 것이 없는 날에도 마무리할 수 있습니다 — 못 한 것을 세지 않고 정리하자고만 합니다. */
    fun notificationText(digest: DayDigest): String =
        doneLine(digest)?.let { "오늘 $it" } ?: "오늘을 정리해 볼까요?"

    /** 화면 맨 위의 날짜 — "11월 19일(목)". */
    fun dateLabel(date: LocalDate): String =
        "${date.monthValue}월 ${date.dayOfMonth}일(${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

    /**
     * 감사 한 줄을 오늘의 감사 일기로. 첫 물음("오늘 감사한 일")의 답입니다 — 감사 일기를 따로 쓰던 길과 같은 기록이라 일기 목록·
     * 검색·점수·동기화가 그대로입니다. 빈 줄이면 null(일기를 만들지 않습니다).
     */
    fun gratitudeDiary(line: String, date: LocalDate): DiaryDraft? {
        val text = line.trim()
        if (text.isEmpty()) return null
        return DiaryDraft(entryDate = date, kind = DiaryKind.GRATITUDE.name, answers = listOf(text))
    }

    /** 내일 할 일 하나. 빈 줄이면 null입니다. */
    fun tomorrowTodo(title: String, date: LocalDate): TodoDraft? {
        val text = title.trim()
        if (text.isEmpty()) return null
        return TodoDraft(title = text, dueDate = date.plusDays(1))
    }

    /** 마무리 뒤 스낵바 — "하루를 마무리했습니다 · 감사 일기와 내일 할 일 1개를 적었습니다". */
    fun finishedMessage(wroteGratitude: Boolean, wroteTodo: Boolean): String {
        val wrote = listOfNotNull("감사 일기".takeIf { wroteGratitude }, "내일 할 일 1개".takeIf { wroteTodo })
        return if (wrote.isEmpty()) "하루를 마무리했습니다" else "하루를 마무리했습니다 · ${wrote.joinToString("와 ")}를 적었습니다"
    }

    /**
     * 배경(마무리 알림)에서 오늘 한 것을 셉니다. 화면·코치와 같은 [DayScoreCalculator.digest]입니다 — 따로 세면 알림과 카드가
     * 다른 수를 말합니다.
     */
    suspend fun digestOf(
        database: AppDatabase,
        settingsStore: SettingsStore,
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DayDigest = DayScoreCalculator.digest(
        date = date,
        events = database.exposureEventDao().getAll(),
        routineChecks = database.routineCheckDao().getAll(),
        todos = database.todoDao().getAll(),
        diaries = database.diaryDao().getAll(),
        moodLogs = database.moodLogDao().getAll(),
        steps = settingsStore.getDailySteps(),
        readingLogs = database.bookReadingLogDao().getAll(),
        walkRecords = WalkRecords(
            stepCounts = database.stepCountDao().getAll(),
            walks = database.walkDao().getFinished(),
            goalSteps = settingsStore.getWalkGoalSteps()
        ),
        zoneId = zoneId
    )
}
