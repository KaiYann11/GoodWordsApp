package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DailyCount(
    val date: LocalDate,
    val confirmedCount: Int,
    val routineCheckCount: Int,
    val diaryCount: Int = 0,
    val todoDoneCount: Int = 0
) {
    val total: Int
        get() = confirmedCount + routineCheckCount + diaryCount + todoDoneCount
}

/** 기분을 몇 번 골랐는지. 화면에서 그날 기분 분포를 보여 주는 데 씁니다. */
data class MoodCount(
    val mood: DiaryMood,
    val count: Int
)

/**
 * 독서 요약.
 *
 * 읽은 쪽수는 전체 쪽수를 아는 책만 셉니다. 모르는 책까지 넣으면 진도를 짐작으로 채우게 됩니다.
 */
data class ReadingSummary(
    val readingCount: Int,
    val finishedCount: Int,
    val finishedThisYear: Int,
    val pagesRead: Int,
    val quotesFromBooks: Int
) {
    val hasBooks: Boolean
        get() = readingCount > 0 || finishedCount > 0
}

/**
 * 하루의 기분 하나.
 *
 * **일기를 안 썼거나 기분을 안 고른 날은 아예 목록에 없습니다.** 0으로 채우지 않습니다.
 * 기분 안 고른 날은 "보통이었던 날"이 아니라 "모르는 날"이라, 채워 넣으면 그래프가 거짓말을 합니다.
 */
data class MoodPoint(
    val date: LocalDate,
    val mood: DiaryMood
)

/**
 * 기분 추이 그래프에 필요한 것 전부.
 *
 * 가로축을 [from]~[to]로 함께 들고 있습니다. 점만 넘기면 사흘 쉬었다 쓴 날과 이어 쓴 날이
 * 나란히 붙어 그려져서, 쉰 자리가 사라집니다.
 */
data class MoodTrend(
    /** 가로축의 첫날. */
    val from: LocalDate,
    /** 가로축의 마지막 날. 늘 오늘입니다. 점이 없어도 축은 오늘에서 끝납니다. */
    val to: LocalDate,
    /** 기분을 남긴 날만, 날짜 순으로. 안 쓴 날은 없습니다. */
    val points: List<MoodPoint>
) {
    val dayCount: Int
        get() = ChronoUnit.DAYS.between(from, to).toInt() + 1

    /**
     * 점 하나로는 오르내림을 볼 수 없습니다. 그때는 그래프를 그리지 않습니다.
     * 한 점짜리 그래프는 자리만 차지하고 아무것도 알려 주지 않습니다.
     */
    val hasShape: Boolean
        get() = points.size >= 2

    /** 그 점이 가로축의 몇 번째 칸인지. */
    fun columnOf(point: MoodPoint): Int = ChronoUnit.DAYS.between(from, point.date).toInt()
}

/** 일기 요약. */
data class DiarySummary(
    val totalCount: Int,
    val daysThisMonth: Int,
    val topMoods: List<MoodCount>,
    /** 최근 [StatsCalculator.MOOD_TREND_DAY_COUNT]일의 기분. 일기가 없으면 null입니다. */
    val moodTrend: MoodTrend? = null
) {
    val hasDiaries: Boolean
        get() = totalCount > 0
}

/** 할 일 요약. */
data class TodoSummary(
    val doneCount: Int,
    val openCount: Int,
    val overdueCount: Int
) {
    val hasTodos: Boolean
        get() = doneCount > 0 || openCount > 0

    /** 끝낸 비율(0~1). 아직 아무것도 없으면 null입니다. 0%라고 단정하지 않습니다. */
    val doneRatio: Float?
        get() {
            val total = doneCount + openCount
            if (total <= 0) return null
            return doneCount.toFloat() / total
        }
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
    val topCategories: List<CategoryCount>,
    val reading: ReadingSummary = ReadingSummary(0, 0, 0, 0, 0),
    val diary: DiarySummary = DiarySummary(0, 0, emptyList()),
    val todo: TodoSummary = TodoSummary(0, 0, 0)
) {
    val hasActivity: Boolean
        get() = confirmedTotal > 0 ||
            routineCheckTotal > 0 ||
            diary.hasDiaries ||
            todo.hasTodos ||
            reading.hasBooks
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

    /**
     * 기분 추이를 며칠 치 보여 줄지.
     *
     * 막대 그래프의 7일보다 깁니다. 일기는 매일 쓰는 것이 아니라서, 7일로 자르면 점이 두어 개만
     * 남아 오르내림이 보이지 않습니다. 더 늘리면 좁은 카드에서 하루 칸이 이모지보다 좁아집니다.
     */
    const val MOOD_TREND_DAY_COUNT = 14

    fun build(
        events: List<ExposureEventEntity>,
        items: List<ContentItemEntity>,
        routineChecks: List<RoutineCheckEntity>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        diaries: List<DiaryEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList(),
        books: List<BookEntity> = emptyList()
    ): StatsSummary {
        val confirmedEvents = events.filter { it.eventType == ExposureEventType.CONFIRMED }
        val confirmedByDate = confirmedEvents.groupingBy { toDate(it.occurredAt, zoneId) }.eachCount()
        val checksByDate = routineChecks.groupingBy { toDate(it.checkedAt, zoneId) }.eachCount()
        // 일기는 쓴 날짜가 레코드에 있고, 할 일은 끝낸 시각으로 셉니다.
        val diaryByDate = diaries.mapNotNull { parseDate(it.entryDate) }.groupingBy { it }.eachCount()
        val todoDoneByDate = todos.mapNotNull { it.doneAt }.groupingBy { toDate(it, zoneId) }.eachCount()

        // 일기를 쓴 날과 할 일을 끝낸 날도 "실천한 날"입니다. 글귀만 세면 절반만 돌아보는 셈입니다.
        val activeDates = confirmedByDate.keys + checksByDate.keys + diaryByDate.keys + todoDoneByDate.keys

        return StatsSummary(
            currentStreakDays = currentStreak(activeDates, today),
            bestStreakDays = bestStreak(activeDates),
            activeDays = activeDates.size,
            confirmedTotal = confirmedEvents.size,
            routineCheckTotal = routineChecks.size,
            recentDays = recentDays(confirmedByDate, checksByDate, diaryByDate, todoDoneByDate, today),
            topCategories = topCategories(confirmedEvents, items),
            reading = readingSummary(books, items, today, zoneId),
            diary = diarySummary(diaries, today),
            todo = todoSummary(todos, today)
        )
    }

    private fun readingSummary(
        books: List<BookEntity>,
        items: List<ContentItemEntity>,
        today: LocalDate,
        zoneId: ZoneId
    ): ReadingSummary {
        val finished = books.filter { it.isFinished }
        return ReadingSummary(
            readingCount = books.count { !it.isFinished },
            finishedCount = finished.size,
            finishedThisYear = finished.count { book ->
                book.finishedAt?.let { toDate(it, zoneId).year == today.year } ?: false
            },
            // 전체 쪽수를 모르는 책은 세지 않습니다. 짐작으로 채우면 숫자가 거짓말이 됩니다.
            pagesRead = books.filter { it.totalPages > 0 }.sumOf { it.currentPage },
            quotesFromBooks = items.count { it.bookSyncId.isNotBlank() }
        )
    }

    private fun diarySummary(diaries: List<DiaryEntity>, today: LocalDate): DiarySummary {
        val thisMonth = diaries.mapNotNull { parseDate(it.entryDate) }
            .filter { it.year == today.year && it.month == today.month }
            .distinct()

        return DiarySummary(
            totalCount = diaries.size,
            // 하루에 여러 번 써도 하루로 셉니다. "이 달에 며칠 썼나"가 궁금한 것입니다.
            daysThisMonth = thisMonth.size,
            topMoods = diaries
                .mapNotNull { it.moodOption }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<DiaryMood, Int>> { it.value }.thenBy { it.key.ordinal })
                .map { (mood, count) -> MoodCount(mood = mood, count = count) },
            moodTrend = moodTrend(diaries, today)
        )
    }

    /**
     * 최근 며칠의 기분을 날짜 순으로 늘어놓습니다.
     *
     * 기분을 안 고른 일기는 점이 되지 않습니다. 안 고른 것을 "보통"으로 치면 그래프가 실제보다
     * 평평해집니다. 앞으로 적어 둔 날짜(오늘보다 뒤)도 뺍니다. 가로축이 오늘에서 끝나야
     * 마지막 점이 지금의 기분으로 읽힙니다.
     */
    private fun moodTrend(diaries: List<DiaryEntity>, today: LocalDate): MoodTrend? {
        if (diaries.isEmpty()) return null
        val from = today.minusDays((MOOD_TREND_DAY_COUNT - 1).toLong())

        val points = diaries
            .mapNotNull { diary ->
                val date = parseDate(diary.entryDate) ?: return@mapNotNull null
                if (date < from || date > today) return@mapNotNull null
                val mood = diary.moodOption ?: return@mapNotNull null
                Triple(date, mood, diary.createdAt)
            }
            .groupBy { (date, _, _) -> date }
            // 하루에 여러 번 썼으면 그날 마지막으로 남긴 기분을 봅니다. 하루에 점 하나여야
            // 가로축이 날짜로 읽힙니다. 아침의 기분보다 그날을 닫으며 남긴 기분이 그날에 가깝습니다.
            .map { (date, sameDay) ->
                MoodPoint(date = date, mood = sameDay.maxBy { (_, _, createdAt) -> createdAt }.second)
            }
            .sortedBy { it.date }

        return MoodTrend(from = from, to = today, points = points)
    }

    private fun todoSummary(todos: List<TodoEntity>, today: LocalDate): TodoSummary = TodoSummary(
        doneCount = todos.count { it.doneAt != null },
        openCount = todos.count { it.doneAt == null },
        overdueCount = todos.count { it.doneAt == null && it.isOverdueOn(today) }
    )

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

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
        diaryByDate: Map<LocalDate, Int>,
        todoDoneByDate: Map<LocalDate, Int>,
        today: LocalDate
    ): List<DailyCount> = (RECENT_DAY_COUNT - 1 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong())
        DailyCount(
            date = date,
            confirmedCount = confirmedByDate[date] ?: 0,
            routineCheckCount = checksByDate[date] ?: 0,
            diaryCount = diaryByDate[date] ?: 0,
            todoDoneCount = todoDoneByDate[date] ?: 0
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
