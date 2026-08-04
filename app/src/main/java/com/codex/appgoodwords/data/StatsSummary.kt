package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyCount(
    val date: LocalDate,
    val confirmedCount: Int,
    val routineCheckCount: Int
) {
    val total: Int
        get() = confirmedCount + routineCheckCount
}

data class CategoryCount(
    val category: String,
    val count: Int
)

data class StatsSummary(
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val activeDays: Int,
    val confirmedTotal: Int,
    val routineCheckTotal: Int,
    val recentDays: List<DailyCount>,
    val topCategories: List<CategoryCount>
) {
    val hasActivity: Boolean
        get() = confirmedTotal > 0 || routineCheckTotal > 0
}

/**
 * 이력 화면에 보여줄 집계입니다.
 *
 * 확인한 글귀와 루틴 체크가 계속 쌓이는데 이력 화면은 원시 로그만 보여줬습니다.
 * 새 쿼리를 만들지 않고 이미 화면이 들고 있는 목록에서 계산하며,
 * 순수 함수로 두어 기기 없이 검증할 수 있게 했습니다.
 */
object StatsCalculator {
    const val RECENT_DAY_COUNT = 7

    fun build(
        events: List<ExposureEventEntity>,
        items: List<ContentItemEntity>,
        routineChecks: List<RoutineCheckEntity>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): StatsSummary {
        val confirmedEvents = events.filter { it.eventType == ExposureEventType.CONFIRMED }
        val confirmedByDate = confirmedEvents.groupingBy { toDate(it.occurredAt, zoneId) }.eachCount()
        val checksByDate = routineChecks.groupingBy { toDate(it.checkedAt, zoneId) }.eachCount()
        val activeDates = confirmedByDate.keys + checksByDate.keys

        return StatsSummary(
            currentStreakDays = currentStreak(activeDates, today),
            bestStreakDays = bestStreak(activeDates),
            activeDays = activeDates.size,
            confirmedTotal = confirmedEvents.size,
            routineCheckTotal = routineChecks.size,
            recentDays = recentDays(confirmedByDate, checksByDate, today),
            topCategories = topCategories(confirmedEvents, items)
        )
    }

    /**
     * 오늘 아직 활동이 없어도 어제까지 이어졌다면 연속으로 봅니다.
     * 하루가 통째로 비어야 끊긴 것으로 처리해, 아침에 앱을 열었을 때 streak이 0으로 보이지 않게 합니다.
     */
    private fun currentStreak(activeDates: Set<LocalDate>, today: LocalDate): Int {
        if (activeDates.isEmpty()) return 0

        var cursor = when {
            today in activeDates -> today
            today.minusDays(1) in activeDates -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        while (cursor in activeDates) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun bestStreak(activeDates: Set<LocalDate>): Int {
        if (activeDates.isEmpty()) return 0

        val sorted = activeDates.sorted()
        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index - 1].plusDays(1) == sorted[index]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    private fun recentDays(
        confirmedByDate: Map<LocalDate, Int>,
        checksByDate: Map<LocalDate, Int>,
        today: LocalDate
    ): List<DailyCount> = (RECENT_DAY_COUNT - 1 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        DailyCount(
            date = date,
            confirmedCount = confirmedByDate[date] ?: 0,
            routineCheckCount = checksByDate[date] ?: 0
        )
    }

    /**
     * 이벤트에는 카테고리가 없어 항목에서 가져옵니다.
     * 지워진 항목의 이벤트는 카테고리를 알 수 없으므로 집계에서 빠집니다.
     */
    private fun topCategories(
        confirmedEvents: List<ExposureEventEntity>,
        items: List<ContentItemEntity>
    ): List<CategoryCount> {
        val categoryById = items.associate { it.id to it.category.trim() }
        return confirmedEvents
            .mapNotNull { event -> categoryById[event.contentItemId]?.takeIf { it.isNotBlank() } }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { (category, count) -> CategoryCount(category = category, count = count) }
    }

    private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
