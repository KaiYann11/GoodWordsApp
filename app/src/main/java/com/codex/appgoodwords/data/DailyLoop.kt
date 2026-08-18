package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 하루에 밟는 세 걸음.
 *
 * 이 앱이 하려는 일은 "좋은 글귀를 읽고, 하기로 한 것을 하고, 하루를 돌아보기"입니다.
 * 기능은 다 있었지만 서로 떨어져 있어서, 사용자는 무엇부터 해야 할지 매번 새로 정해야 했습니다.
 * 순서를 정해 주면 고민할 것이 없어집니다.
 */
enum class DailyStep(val label: String, val hint: String) {
    QUOTE("글귀 읽기", "오늘의 글귀를 하나 확인해 보세요"),
    ROUTINE("루틴", "하기로 한 것을 하나 체크해 보세요"),
    DIARY("일기", "오늘을 한 줄이라도 남겨 보세요")
}

/**
 * 오늘 세 걸음 중 어디까지 왔는지.
 *
 * 사람을 다그치지 않는 것이 이 화면의 전부입니다. 못 한 것을 붉게 세는 대신
 * 다음 한 걸음만 알려 주고, 이어 온 날을 지켜 주려는 마음이 들게 합니다.
 */
data class DailyProgress(
    val doneSteps: Set<DailyStep>,
    /** 세 걸음을 다 밟은 날이 며칠 이어졌는지. 오늘이 아직이면 어제까지로 셉니다. */
    val streakDays: Int,
    /** 지금까지 가장 길게 이어 간 날수. */
    val bestStreakDays: Int
) {
    val doneCount: Int
        get() = doneSteps.size

    val isComplete: Boolean
        get() = doneSteps.size == DailyStep.entries.size

    /** 0~1. 진행 막대에 씁니다. */
    val ratio: Float
        get() = doneCount.toFloat() / DailyStep.entries.size

    /** 아직 안 한 첫 걸음. 다 했으면 null입니다. */
    val nextStep: DailyStep?
        get() = DailyStep.entries.firstOrNull { it !in doneSteps }

    /**
     * 한 줄 격려.
     *
     * 남은 개수를 세어 주지 않고 **다음 하나만** 말합니다. "2개 남았습니다"는 할 일 목록처럼
     * 들리고, 세 걸음을 한꺼번에 떠올리게 해서 오히려 손이 무거워집니다.
     *
     * [DailyStep.hint]를 그대로 옮겨 쓰지 않습니다. 그 안내문은 다음 걸음 줄에 이미 붙어 있어서,
     * 같은 문장이 카드에 두 번 나옵니다.
     */
    val message: String
        get() = when {
            isComplete && streakDays > 1 -> "오늘도 세 걸음을 다 밟았습니다. ${streakDays}일째 이어 가는 중입니다."
            isComplete -> "오늘 세 걸음을 다 밟았습니다."
            // 이어 온 날이 있으면 그것을 지키는 쪽이 새로 시작하는 것보다 훨씬 큰 힘이 됩니다.
            streakDays > 0 -> "${streakDays}일째 이어 오고 있습니다. ${nextStep?.label}만 하면 오늘도 이어집니다."
            doneCount > 0 -> "${nextStep?.label}까지 하면 오늘이 채워집니다."
            else -> "여기서 오늘이 시작됩니다."
        }
}

/**
 * 세 걸음의 진행을 셉니다.
 *
 * [StatsCalculator]와 같은 자리에서 같은 목록을 보고 계산합니다. 새 표를 만들지 않는 이유는,
 * "오늘 했는지"는 이미 남아 있는 기록에서 알 수 있기 때문입니다. 따로 저장하면 두 값이 어긋납니다.
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object DailyLoopCalculator {
    fun build(
        events: List<ExposureEventEntity>,
        routineChecks: List<RoutineCheckEntity>,
        diaries: List<DiaryEntity>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DailyProgress {
        val quoteDays = events
            .filter { it.eventType == ExposureEventType.CONFIRMED }
            .mapTo(mutableSetOf()) { toDate(it.occurredAt, zoneId) }
        val routineDays = routineChecks.mapTo(mutableSetOf()) { toDate(it.checkedAt, zoneId) }
        val diaryDays = diaries.mapNotNullTo(mutableSetOf()) { parseDate(it.entryDate) }

        val doneSteps = buildSet {
            if (today in quoteDays) add(DailyStep.QUOTE)
            if (today in routineDays) add(DailyStep.ROUTINE)
            if (today in diaryDays) add(DailyStep.DIARY)
        }

        val fullDays = quoteDays.intersect(routineDays).intersect(diaryDays)

        return DailyProgress(
            doneSteps = doneSteps,
            streakDays = currentStreak(fullDays, today),
            bestStreakDays = bestStreak(fullDays)
        )
    }

    /**
     * 오늘이 아직이어도 어제까지 이어졌으면 그 날수를 보여 줍니다.
     *
     * 아침에 앱을 열었을 때 0일로 보이면, 어제까지 쌓은 것이 사라진 것처럼 느껴집니다.
     * 지킬 것이 있어야 오늘도 하게 됩니다. [StatsCalculator]의 연속 날수와 같은 규칙입니다.
     */
    private fun currentStreak(fullDays: Set<LocalDate>, today: LocalDate): Int {
        if (fullDays.isEmpty()) return 0

        var cursor = when {
            today in fullDays -> today
            today.minusDays(1) in fullDays -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        while (cursor in fullDays) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun bestStreak(fullDays: Set<LocalDate>): Int {
        if (fullDays.isEmpty()) return 0

        val sorted = fullDays.sorted()
        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index - 1].plusDays(1) == sorted[index]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
