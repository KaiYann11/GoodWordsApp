package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 문구의 성격. 화면이 색과 차례를 정하는 데 씁니다.
 */
enum class FeedbackKind {
    /** 아직 아무것도 없는 사람에게 건네는 첫마디. */
    INVITE,

    /** 며칠째 이어 오고 있는지. */
    STREAK,

    /** 잘하고 있는 것을 짚어 줍니다. */
    PRAISE,

    /** 뜸해진 것을 조심스럽게 짚습니다. 한 줄을 넘기지 않습니다. */
    NUDGE
}

data class FeedbackNote(
    val text: String,
    val kind: FeedbackKind
)

/**
 * 모아 둔 기록을 사람이 읽는 문장으로 바꿉니다.
 *
 * 숫자만 늘어놓으면 성적표가 됩니다. "이번 주 12건"보다 "지난 이레 중 화요일에 가장 많이
 * 했습니다"가 다음에 무엇을 할지 정하는 데 도움이 됩니다.
 *
 * **다그치지 않는 것이 규칙입니다.** 못 한 것을 세어 보여 주면 할 일 목록이 되고,
 * 하루 빠뜨린 날에는 앱을 열기가 싫어집니다. 그래서 짚는 말([FeedbackKind.NUDGE])은
 * 한 번에 한 줄까지만 담고, 늘 잘하고 있는 것을 먼저 적습니다.
 *
 * 화면 없이 검증할 수 있도록 순수 함수로 둡니다.
 */
object FeedbackWriter {
    /** 한 번에 보여 줄 문구 수. 넘치면 읽지 않고 넘깁니다. */
    const val MAX_NOTES = 3

    /** 이만큼 비어 있으면 "뜸하다"고 봅니다. */
    private const val QUIET_DAYS = 14

    private const val WEEK_DAYS = 7

    fun write(
        summary: StatsSummary,
        progress: DailyProgress,
        routines: List<RoutineEntity>,
        routineChecks: List<RoutineCheckEntity>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<FeedbackNote> {
        if (!summary.hasActivity) {
            return listOf(
                FeedbackNote(
                    text = "여기는 돌아보는 자리입니다. 글귀를 하나 읽으면 오늘이 시작됩니다.",
                    kind = FeedbackKind.INVITE
                )
            )
        }

        val praises = buildList {
            bestDayNote(summary)?.let(::add)
            keptAllWeekNote(routines, routineChecks, today, zoneId)?.let(::add)
            readingNote(summary)?.let(::add)
            diaryNote(summary)?.let(::add)
        }
        // 짚는 말은 하나까지만. 여럿이 겹치면 화면이 잔소리가 됩니다.
        val nudge = quietRoutineNote(routines, routineChecks, today, zoneId)
            ?: overdueTodoNote(summary)

        return buildList {
            add(streakNote(summary, progress))
            addAll(praises)
            nudge?.let(::add)
        }.take(MAX_NOTES)
    }

    /** 며칠째인지. 하루도 못 이은 날에도 지난 기록을 등에 대 줍니다. */
    private fun streakNote(summary: StatsSummary, progress: DailyProgress): FeedbackNote {
        val streak = summary.currentStreakDays
        val text = when {
            streak >= 2 -> "${streak}일째 이어 가고 있습니다."
            streak == 1 && progress.isComplete -> "오늘 ${progress.stepCount}걸음을 다 밟았습니다."
            streak == 1 -> "오늘 첫 걸음을 뗐습니다."
            summary.bestStreakDays >= 2 -> "가장 길게는 ${summary.bestStreakDays}일을 이어 갔습니다. 오늘 한 걸음이면 다시 시작입니다."
            else -> "오늘 한 걸음이면 이어 가기가 시작됩니다."
        }
        return FeedbackNote(text, FeedbackKind.STREAK)
    }

    /** 지난 이레 중 가장 많이 한 날. 다음 주에 언제 힘이 나는지 알려 줍니다. */
    private fun bestDayNote(summary: StatsSummary): FeedbackNote? {
        val best = summary.recentDays.filter { it.total > 0 }.maxByOrNull { it.total } ?: return null
        // 하루밖에 없으면 "가장 많은 날"이라고 부를 것이 못 됩니다.
        if (summary.recentDays.count { it.total > 0 } < 2) return null
        return FeedbackNote(
            text = "지난 이레 중 ${weekdayName(best.date)}에 가장 많이 했습니다. ${best.total}건입니다.",
            kind = FeedbackKind.PRAISE
        )
    }

    /** 이레 내내 하루도 빠뜨리지 않은 루틴. */
    private fun keptAllWeekNote(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        today: LocalDate,
        zoneId: ZoneId
    ): FeedbackNote? {
        if (routines.isEmpty()) return null
        val week = (0 until WEEK_DAYS).map { today.minusDays(it.toLong()) }.toSet()
        val datesByRoutine = checks
            .groupBy { it.routineId }
            .mapValues { (_, list) -> list.map { toDate(it.checkedAt, zoneId) }.toSet() }

        val kept = RoutineOrder.sorted(routines).firstOrNull { routine ->
            datesByRoutine[routine.id]?.containsAll(week) == true
        } ?: return null

        return FeedbackNote(
            text = "'${kept.title}'은 이레 내내 지켰습니다.",
            kind = FeedbackKind.PRAISE
        )
    }

    private fun readingNote(summary: StatsSummary): FeedbackNote? {
        val reading = summary.reading
        return when {
            reading.finishedThisYear > 0 ->
                FeedbackNote("올해 책 ${reading.finishedThisYear}권을 다 읽었습니다.", FeedbackKind.PRAISE)

            reading.quotesFromBooks > 0 ->
                FeedbackNote("책에서 뽑아 둔 글귀가 ${reading.quotesFromBooks}개입니다.", FeedbackKind.PRAISE)

            else -> null
        }
    }

    private fun diaryNote(summary: StatsSummary): FeedbackNote? {
        val days = summary.diary.daysThisMonth
        if (days < 2) return null
        return FeedbackNote("이달에 일기를 ${days}일 썼습니다.", FeedbackKind.PRAISE)
    }

    /**
     * 두 주째 비어 있는 루틴 하나.
     *
     * 여럿이면 가장 앞 차례의 것만 짚습니다. 목록으로 늘어놓으면 그 자체로 밀린 일감이 됩니다.
     */
    private fun quietRoutineNote(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        today: LocalDate,
        zoneId: ZoneId
    ): FeedbackNote? {
        if (routines.size < 2) return null
        val since = today.minusDays((QUIET_DAYS - 1).toLong())
        val recent = checks
            .filter { !toDate(it.checkedAt, zoneId).isBefore(since) }
            .map { it.routineId }
            .toSet()

        val quiet = RoutineOrder.sorted(routines).firstOrNull { it.id !in recent } ?: return null
        // 만든 지 얼마 안 된 루틴을 두고 뜸하다고 하면 억울합니다.
        if (!toDate(quiet.createdAt, zoneId).isBefore(since)) return null

        return FeedbackNote(
            text = "'${quiet.title}'은 두 주째 뜸합니다. 오늘 한 번이면 다시 이어집니다.",
            kind = FeedbackKind.NUDGE
        )
    }

    private fun overdueTodoNote(summary: StatsSummary): FeedbackNote? {
        val overdue = summary.todo.overdueCount
        if (overdue <= 0) return null
        return FeedbackNote("기한이 지난 할 일이 ${overdue}개 있습니다.", FeedbackKind.NUDGE)
    }

    /**
     * 요일 이름.
     *
     * 기기 언어에 기대지 않습니다. 영어로 맞춰 둔 기기에서 "Tuesday에 가장 많이 했습니다"가
     * 섞여 나오면 문장이 깨집니다.
     */
    private fun weekdayName(date: LocalDate): String = when (date.dayOfWeek.value) {
        1 -> "월요일"
        2 -> "화요일"
        3 -> "수요일"
        4 -> "목요일"
        5 -> "금요일"
        6 -> "토요일"
        else -> "일요일"
    }

    private fun toDate(millis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
}
