package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 하루에 매기는 점수와, 그날 무엇을 했는지.
 *
 * **기분은 점수에 넣지 않습니다.** 슬픈 날이 낮은 점수가 되면, 앱이 감정을 잘못한 일로 세는
 * 셈입니다. 힘든 날일수록 점수까지 나쁘게 나오면 다시 열고 싶지 않아집니다.
 * 기분은 점수 옆에 나란히 두어, 어떤 기분의 날에 무엇을 했는지 스스로 보게 합니다.
 *
 * **저장하지 않고 그때그때 기록에서 셉니다.** 연속 날수와 같은 이유입니다([DailyLoopCalculator]).
 * 하루의 축([DailyStep])을 바꾸면 지난날 점수도 곧바로 새 기준으로 다시 셈해집니다.
 * 저장해 두면 옛 기준으로 매긴 숫자와 새 기준이 섞여, 어느 기준의 몇 점인지 아무도 모릅니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
data class DayScore(
    val date: LocalDate,
    /** 그날 기준으로 삼은 걸음. 설정에서 고른 것입니다. */
    val steps: List<DailyStep>,
    val doneSteps: Set<DailyStep>,
    val routineChecks: Int,
    val todosDone: Int,
    val diaries: Int,
    val quotesRead: Int,
    /** 그날의 기분. 점수와는 상관없이 곁에 둡니다. */
    val mood: DiaryMood? = null
) {
    /** 그날 한 일을 모두 센 수. 덤 점수의 바탕입니다. */
    val deedCount: Int
        get() = routineChecks + todosDone + diaries + quotesRead

    /**
     * 고른 걸음을 얼마나 밟았는지. 다 밟으면 [STEP_POINTS]점입니다.
     *
     * 점수의 뼈대를 걸음에 두는 이유가 있습니다. 무엇이 중요한 하루인지는 사람마다 다르고,
     * 그것을 이미 설정에서 골라 두었기 때문입니다. 앱이 따로 정한 잣대로 매기면
     * 자기가 고른 축과 점수가 어긋납니다.
     */
    val stepScore: Int
        get() = if (steps.isEmpty()) 0 else {
            (doneSteps.size.toDouble() / steps.size * STEP_POINTS).toInt()
        }

    /**
     * 걸음 수를 넘겨 더 한 만큼의 덤.
     *
     * 걸음만으로 매기면 셋 중 셋을 밟은 날과, 그러고도 루틴을 다섯 개 더 밟은 날이 같은
     * 점수가 됩니다. 더 한 것이 보이지 않으면 더 할 마음이 들지 않습니다.
     * 다만 [BONUS_CAP]에서 멈춥니다. 많이 할수록 끝없이 오르면 개수 채우기가 됩니다.
     */
    val bonusScore: Int
        get() = ((deedCount - steps.size).coerceAtLeast(0) * BONUS_PER_DEED).coerceAtMost(BONUS_CAP)

    /** 0~100. */
    val score: Int
        get() = (stepScore + bonusScore).coerceIn(0, 100)

    /** 아무것도 안 한 날. 그래프에서 빈 칸으로 둘지 가릅니다. */
    val isEmpty: Boolean
        get() = deedCount == 0

    companion object {
        /** 고른 걸음을 다 밟았을 때의 점수. 나머지는 덤입니다. */
        const val STEP_POINTS = 80

        /** 걸음을 넘겨 하나 더 할 때마다. */
        const val BONUS_PER_DEED = 5

        /** 덤은 여기서 멈춥니다. */
        const val BONUS_CAP = 20
    }
}

/**
 * 그날 무엇을 했는지까지 담은 요약.
 *
 * 점수만 보면 "왜 60점이지?"를 알 수 없습니다. 숫자 옆에 그날 한 일의 이름이 있어야
 * 스스로 납득하고, 납득해야 다음 날이 달라집니다.
 */
data class DayDigest(
    val score: DayScore,
    val routineTitles: List<String>,
    val todoTitles: List<String>,
    val diaryTitles: List<String>,
    val quoteTitles: List<String>
) {
    val isEmpty: Boolean
        get() = score.isEmpty
}

data class ScorePoint(
    val date: LocalDate,
    val score: Int
)

/**
 * 며칠 치 점수의 흐름.
 *
 * "내가 어떻게 변해가는지"는 오늘 점수가 아니라 **지난주와의 차이**가 말해 줍니다.
 * 하루는 들쭉날쭉하지만 주 평균은 방향을 보여 줍니다.
 */
data class ScoreTrend(
    /** 오래된 날부터. 빈 날도 0점으로 담습니다 — 안 한 날도 흐름의 일부입니다. */
    val points: List<ScorePoint>,
    val thisWeekAverage: Int,
    val lastWeekAverage: Int
) {
    /** 지난주보다 얼마나 올랐는지. 음수면 내려간 것입니다. */
    val change: Int
        get() = thisWeekAverage - lastWeekAverage

    val best: Int
        get() = points.maxOfOrNull { it.score } ?: 0

    /** 견줄 지난주가 아직 없으면 변화를 말하지 않습니다. */
    val hasComparison: Boolean
        get() = lastWeekAverage > 0

    val hasShape: Boolean
        get() = points.any { it.score > 0 }
}

object DayScoreCalculator {
    /** 그래프에 보여 줄 날수. 4주면 주 단위 오르내림이 눈에 들어옵니다. */
    const val TREND_DAY_COUNT = 28

    /** 주 평균을 낼 때 묶는 날수. */
    const val WEEK_DAYS = 7

    /**
     * 하루치 요약. 그날 한 일의 이름까지 담습니다.
     */
    fun digest(
        date: LocalDate,
        events: List<ExposureEventEntity>,
        routineChecks: List<RoutineCheckEntity>,
        todos: List<TodoEntity>,
        diaries: List<DiaryEntity>,
        moodLogs: List<MoodLogEntity> = emptyList(),
        steps: List<DailyStep> = DailyStep.DEFAULTS,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): DayDigest {
        val chosen = steps.distinct().ifEmpty { DailyStep.DEFAULTS }

        val checksToday = routineChecks.filter { toDate(it.checkedAt, zoneId) == date }
        // 마감일이 아니라 **끝낸 날**로 셉니다. 지난주 할 일을 오늘 끝냈으면 오늘 한 것입니다.
        val todosToday = todos.filter { todo ->
            todo.doneAt?.takeIf { it > 0L }?.let { toDate(it, zoneId) } == date
        }
        val diariesToday = diaries.filter { parseDate(it.entryDate) == date }
        val quotesToday = events.filter {
            it.eventType == ExposureEventType.CONFIRMED && toDate(it.occurredAt, zoneId) == date
        }

        val done = buildSet {
            if (quotesToday.isNotEmpty()) add(DailyStep.QUOTE)
            if (checksToday.isNotEmpty()) add(DailyStep.ROUTINE)
            if (todosToday.isNotEmpty()) add(DailyStep.TODO)
            if (diariesToday.isNotEmpty()) add(DailyStep.DIARY)
        }

        val score = DayScore(
            date = date,
            steps = chosen,
            // 고르지 않은 걸음은 밟았어도 걸음 점수에 세지 않습니다. 덤으로는 셉니다.
            doneSteps = done.intersect(chosen.toSet()),
            routineChecks = checksToday.size,
            todosDone = todosToday.size,
            diaries = diariesToday.size,
            quotesRead = quotesToday.size,
            // 그날의 기분은 DayMood가 정합니다. 여기서 따로 셈하면 화면마다 다른 말을 합니다.
            mood = DayMood.byDate(logs = moodLogs, diaries = diaries)[date]
        )

        return DayDigest(
            score = score,
            // 같은 루틴을 여러 번 밟았으면 한 줄로 보여 줍니다. 목록이 같은 이름으로 채워집니다.
            routineTitles = checksToday.map { it.routineTitle }.filter { it.isNotBlank() }.distinct(),
            todoTitles = todosToday.map { it.title }.filter { it.isNotBlank() },
            diaryTitles = diariesToday.map { it.displayTitle.ifBlank { it.entryDate } },
            quoteTitles = quotesToday.map { it.contentTitle }.filter { it.isNotBlank() }.distinct()
        )
    }

    /**
     * 최근 [dayCount]일의 점수 흐름.
     *
     * 하루씩 [digest]를 부르지 않고 날짜별로 미리 묶습니다. 이력이 수천 건 쌓인 뒤에
     * 스물여덟 번을 훑으면 홈 화면을 열 때마다 그만큼 느려집니다.
     */
    fun trend(
        today: LocalDate,
        events: List<ExposureEventEntity>,
        routineChecks: List<RoutineCheckEntity>,
        todos: List<TodoEntity>,
        diaries: List<DiaryEntity>,
        steps: List<DailyStep> = DailyStep.DEFAULTS,
        dayCount: Int = TREND_DAY_COUNT,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ScoreTrend {
        val chosen = steps.distinct().ifEmpty { DailyStep.DEFAULTS }

        val quotesByDate = events
            .filter { it.eventType == ExposureEventType.CONFIRMED }
            .groupingBy { toDate(it.occurredAt, zoneId) }
            .eachCount()
        val checksByDate = routineChecks.groupingBy { toDate(it.checkedAt, zoneId) }.eachCount()
        val todosByDate = todos
            .mapNotNull { todo -> todo.doneAt?.takeIf { it > 0L }?.let { toDate(it, zoneId) } }
            .groupingBy { it }
            .eachCount()
        val diariesByDate = diaries.mapNotNull { parseDate(it.entryDate) }.groupingBy { it }.eachCount()

        val points = (dayCount - 1 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val quotes = quotesByDate[date] ?: 0
            val checks = checksByDate[date] ?: 0
            val todosDone = todosByDate[date] ?: 0
            val diaryCount = diariesByDate[date] ?: 0

            val done = buildSet {
                if (quotes > 0) add(DailyStep.QUOTE)
                if (checks > 0) add(DailyStep.ROUTINE)
                if (todosDone > 0) add(DailyStep.TODO)
                if (diaryCount > 0) add(DailyStep.DIARY)
            }

            ScorePoint(
                date = date,
                score = DayScore(
                    date = date,
                    steps = chosen,
                    doneSteps = done.intersect(chosen.toSet()),
                    routineChecks = checks,
                    todosDone = todosDone,
                    diaries = diaryCount,
                    quotesRead = quotes
                ).score
            )
        }

        return ScoreTrend(
            points = points,
            thisWeekAverage = averageOf(points.takeLast(WEEK_DAYS)),
            // 오늘이 낀 이레의 바로 앞 이레입니다.
            lastWeekAverage = averageOf(points.dropLast(WEEK_DAYS).takeLast(WEEK_DAYS))
        )
    }

    private fun averageOf(points: List<ScorePoint>): Int =
        if (points.isEmpty()) 0 else points.sumOf { it.score } / points.size

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
