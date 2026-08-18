package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기분 추이 그래프에 무엇이 들어가고 무엇이 빠지는지 봅니다.
 *
 * 그래프는 없는 것을 채워 넣기 쉬운 자리입니다. 안 쓴 날을 "보통"으로 메우면 선이 실제보다
 * 평평해지고, 그러면 사용자는 자기 기분을 잘못 읽게 됩니다. 그래서 빠지는 쪽을 특히 봅니다.
 */
class MoodTrendTest {
    private val today = LocalDate.of(2026, 8, 18)

    @Test
    fun theAxisAlwaysEndsToday() {
        val trend = trendOf(diary("2026-08-18", DiaryMood.GOOD), diary("2026-08-17", DiaryMood.TIRED))

        assertEquals(today, trend?.to)
        // 14일 치입니다. 오늘을 포함해서 세므로 첫날은 13일 전입니다.
        assertEquals(today.minusDays(13), trend?.from)
        assertEquals(StatsCalculator.MOOD_TREND_DAY_COUNT, trend?.dayCount)
    }

    @Test
    fun pointsComeInDateOrder() {
        // 목록은 최신 순으로 들어옵니다. 그대로 그리면 선이 오른쪽에서 왼쪽으로 갑니다.
        val trend = trendOf(
            diary("2026-08-18", DiaryMood.BAD),
            diary("2026-08-12", DiaryMood.GREAT),
            diary("2026-08-15", DiaryMood.NEUTRAL)
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 18)),
            trend?.points?.map { it.date }
        )
    }

    @Test
    fun aDiaryWithoutAMoodIsNotAPoint() {
        // 안 고른 것은 "보통이었던 날"이 아니라 "모르는 날"입니다.
        val trend = trendOf(
            diary("2026-08-18", DiaryMood.GOOD),
            diary("2026-08-17", mood = null),
            diary("2026-08-16", DiaryMood.SAD)
        )

        assertEquals(listOf(DiaryMood.GOOD, DiaryMood.SAD), trend?.points?.map { it.mood }?.sortedBy { it.rank })
        assertEquals(2, trend?.points?.size)
    }

    @Test
    fun olderThanTheWindowIsLeftOut() {
        val trend = trendOf(
            diary("2026-08-18", DiaryMood.GOOD),
            // 14일 창의 첫날. 들어와야 합니다.
            diary("2026-08-05", DiaryMood.TIRED),
            // 하루 더 앞. 빠져야 합니다.
            diary("2026-08-04", DiaryMood.BAD)
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 18)),
            trend?.points?.map { it.date }
        )
    }

    @Test
    fun aDateInTheFutureIsLeftOut() {
        // 날짜를 앞으로 적어 둔 일기. 가로축이 오늘에서 끝나야 마지막 점이 지금 기분으로 읽힙니다.
        val trend = trendOf(
            diary("2026-08-18", DiaryMood.GOOD),
            diary("2026-08-17", DiaryMood.NEUTRAL),
            diary("2026-08-20", DiaryMood.GREAT)
        )

        assertEquals(listOf(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18)), trend?.points?.map { it.date })
    }

    @Test
    fun aDayWithSeveralDiariesKeepsTheLastMood() {
        // 하루에 점이 둘이면 가로축이 날짜로 읽히지 않습니다. 그날을 닫으며 남긴 기분을 씁니다.
        val trend = trendOf(
            diary("2026-08-18", DiaryMood.ANGRY, createdAt = 1_000L),
            diary("2026-08-18", DiaryMood.GOOD, createdAt = 5_000L),
            diary("2026-08-17", DiaryMood.NEUTRAL)
        )

        assertEquals(2, trend?.points?.size)
        assertEquals(DiaryMood.GOOD, trend?.points?.last()?.mood)
    }

    @Test
    fun aBrokenDateDoesNotBreakTheChart() {
        // 다른 기기에서 이상한 값이 넘어와도 돌아보기 화면이 죽으면 안 됩니다.
        val trend = trendOf(
            diary("언젠가", DiaryMood.GOOD),
            diary("2026-08-18", DiaryMood.SAD),
            diary("2026-08-17", DiaryMood.SAD)
        )

        assertEquals(2, trend?.points?.size)
    }

    @Test
    fun onePointIsNotAShape() {
        val trend = trendOf(diary("2026-08-18", DiaryMood.GOOD))

        assertFalse("점 하나짜리 그래프는 자리만 차지합니다.", trend!!.hasShape)
    }

    @Test
    fun twoPointsAreAShape() {
        val trend = trendOf(diary("2026-08-18", DiaryMood.GOOD), diary("2026-08-11", DiaryMood.BAD))

        assertTrue(trend!!.hasShape)
    }

    @Test
    fun withoutAnyDiaryThereIsNoChartAtAll() {
        assertNull(trendOf())
    }

    @Test
    fun columnsAreSpacedByRealDays() {
        // 쉰 날이 칸을 차지해야 "사흘 쉬었다 썼다"가 그림에 남습니다.
        val trend = trendOf(diary("2026-08-05", DiaryMood.GOOD), diary("2026-08-18", DiaryMood.BAD))!!

        assertEquals(0, trend.columnOf(trend.points.first()))
        assertEquals(13, trend.columnOf(trend.points.last()))
    }

    private fun trendOf(vararg diaries: DiaryEntity) = StatsCalculator.build(
        events = emptyList(),
        items = emptyList(),
        routineChecks = emptyList(),
        today = today,
        diaries = diaries.toList()
    ).diary.moodTrend

    private fun diary(entryDate: String, mood: DiaryMood?, createdAt: Long = 0L) = DiaryEntity(
        syncId = "diary-$entryDate-${mood?.name}-$createdAt",
        entryDate = entryDate,
        mood = mood?.name.orEmpty(),
        createdAt = createdAt
    )
}
