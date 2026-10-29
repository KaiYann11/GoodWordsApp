package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 그날의 기분이 무엇이었는지.
 *
 * 기분이 두 곳에 남습니다. 톡 찍어 둔 것과 일기에 딸린 것입니다. 겹쳐 보는 자리마다 각자
 * 셈하면 화면끼리 다른 말을 하게 되므로, 여기 한곳에서 정합니다.
 */
class DayMoodTest {
    private val today = LocalDate.of(2026, 8, 26)

    @Test
    fun aDayWithoutADiaryStillHasAMood() {
        // 이것이 이 기록을 만든 이유입니다. 바쁘고 힘든 날일수록 일기를 못 쓰는데,
        // 그런 날이 통째로 비면 정작 알고 싶은 날을 잃습니다.
        val moods = DayMood.byDate(logs = listOf(log(today, DiaryMood.TIRED)), diaries = emptyList())

        assertEquals(DiaryMood.TIRED, moods[today])
    }

    @Test
    fun theDiaryStillCountsWhereNothingWasTapped() {
        // 예전에 쓴 기록이 그대로 살아 있어야 합니다.
        val moods = DayMood.byDate(logs = emptyList(), diaries = listOf(diary(1, today, DiaryMood.GOOD)))

        assertEquals(DiaryMood.GOOD, moods[today])
    }

    @Test
    fun whatWasTappedWinsOverTheDiary() {
        // 하루의 기분을 묻는 자리에서 직접 고른 것이고, 일기의 기분은 그 일기에 딸린 것입니다.
        val moods = DayMood.byDate(
            logs = listOf(log(today, DiaryMood.TIRED)),
            diaries = listOf(diary(1, today, DiaryMood.GREAT))
        )

        assertEquals(DiaryMood.TIRED, moods[today])
    }

    @Test
    fun theLastTapWins() {
        val moods = DayMood.byDate(
            logs = listOf(
                log(today, DiaryMood.GOOD, updatedAt = 100L),
                log(today, DiaryMood.SAD, updatedAt = 200L)
            ),
            diaries = emptyList()
        )

        assertEquals(DiaryMood.SAD, moods[today])
    }

    @Test
    fun differentTapsMakeTheDayUnsettledEvenWhenItReturnsToTheFirstMood() {
        val logs = listOf(
            log(today, DiaryMood.GOOD, 100L),
            log(today, DiaryMood.TIRED, 200L),
            log(today, DiaryMood.GOOD, 300L).copy(syncId = "last")
        )
        val entry = DayMood.entriesByDate(logs.reversed(), listOf(diary(1, today, DiaryMood.SAD)))[today]!!

        assertEquals(DiaryMood.GOOD, entry.mood)
        assertFalse(entry.settled)
        assertTrue(DayMood.settledByDate(logs, emptyList()).isEmpty())
    }

    @Test
    fun repeatedSameMoodStaysSettledAndDoesNotCountTheDiaryAgainstIt() {
        val logs = listOf(log(today, DiaryMood.GOOD, 100L), log(today, DiaryMood.GOOD, 300L).copy(syncId = "last"))

        assertTrue(DayMood.entriesByDate(logs, listOf(diary(1, today, DiaryMood.SAD)))[today]!!.settled)
    }

    @Test
    fun theFlowUsesTheOldRecordsLastEditAndStableTies() {
        val zone = ZoneId.of("Asia/Seoul")
        val morning = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val evening = today.atTime(18, 30).atZone(zone).toInstant().toEpochMilli()
        val old = log(today, DiaryMood.TIRED, evening).copy(createdAt = morning, syncId = "a")
        val tie = old.copy(syncId = "z", mood = "GOOD")
        val earlier = log(today, DiaryMood.NEUTRAL, morning)
        val logs = listOf(tie, old, earlier, log(today.minusDays(1), DiaryMood.SAD))

        assertEquals(listOf(earlier, old, tie), DayMood.logsOn(logs, today))
        assertEquals(DayMood.logsOn(logs, today), DayMood.logsOn(logs.reversed(), today))
        assertEquals("18:30", DayMood.timeLabel(old, zone))
        assertEquals(DiaryMood.GOOD, DayMood.byDate(logs, emptyList())[today])
    }

    @Test
    fun onlyAnImmediateRepeatOfTheLastMoodIsIgnored() {
        val last = log(today, DiaryMood.GOOD, 10_000L)

        assertTrue(DayMood.isImmediateRepeat(last, DiaryMood.GOOD, 10_000L))
        assertTrue(DayMood.isImmediateRepeat(last, DiaryMood.GOOD, 12_999L))
        assertFalse(DayMood.isImmediateRepeat(last, DiaryMood.GOOD, 13_000L))
        assertFalse(DayMood.isImmediateRepeat(last, DiaryMood.SAD, 10_001L))
        assertFalse(DayMood.isImmediateRepeat(last, DiaryMood.GOOD, 9_999L))
        assertFalse(DayMood.isImmediateRepeat(null, DiaryMood.GOOD, 10_000L))
    }

    @Test
    fun aDayWithTwoDifferentDiaryMoodsIsNotSettled() {
        // 감사 일기는 좋음, 반성 일기는 지침이면 그날을 어느 쪽이라 딱 잘라 말할 수 없습니다.
        val diaries = listOf(
            diary(1, today, DiaryMood.GOOD, createdAt = 1_000L),
            diary(2, today, DiaryMood.TIRED, createdAt = 5_000L)
        )

        // 그래프는 그날을 비우지 않습니다. 실제로는 쓴 날인데 안 쓴 날처럼 보입니다.
        // 그날을 닫으며 남긴 것이 그날에 가깝습니다.
        assertEquals(DiaryMood.TIRED, DayMood.byDate(emptyList(), diaries)[today])
        // 기분별로 실천을 평균 내는 자리는 뺍니다. 그 칸의 평균이 흔들립니다.
        assertTrue(today !in DayMood.settledByDate(emptyList(), diaries))
    }

    @Test
    fun aTapSettlesADayTheDiariesDisagreedOn() {
        // 어느 쪽이라 할 수 없던 날도, 직접 하나를 고르면 그날의 기분이 정해집니다.
        val logs = listOf(log(today, DiaryMood.NEUTRAL))
        val diaries = listOf(diary(1, today, DiaryMood.GOOD), diary(2, today, DiaryMood.TIRED))

        assertEquals(DiaryMood.NEUTRAL, DayMood.byDate(logs, diaries)[today])
        assertEquals(DiaryMood.NEUTRAL, DayMood.settledByDate(logs, diaries)[today])
    }

    @Test
    fun theSameMoodTwiceInADayIsNotADisagreement() {
        val moods = DayMood.byDate(
            logs = emptyList(),
            diaries = listOf(diary(1, today, DiaryMood.GOOD), diary(2, today, DiaryMood.GOOD))
        )

        assertEquals(DiaryMood.GOOD, moods[today])
    }

    @Test
    fun aDiaryWithoutAMoodIsNotAMood() {
        // 안 고른 것을 "보통"으로 치면 그래프가 실제보다 평평해집니다.
        val moods = DayMood.byDate(logs = emptyList(), diaries = listOf(diary(1, today, null)))

        assertTrue(moods.isEmpty())
    }

    @Test
    fun brokenDatesAndUnknownMoodsAreDropped() {
        val moods = DayMood.byDate(
            logs = listOf(MoodLogEntity(syncId = "m-x", entryDate = "언젠가", mood = "GOOD")),
            diaries = listOf(DiaryEntity(syncId = "d-x", entryDate = today.toString(), mood = "행복"))
        )

        assertTrue(moods.toString(), moods.isEmpty())
    }

    @Test
    fun unreadableTapsDoNotEraseTheDiaryWhenNoReadableTapExists() {
        val moods = DayMood.byDate(
            logs = listOf(
                MoodLogEntity(syncId = "m-x", entryDate = today.toString(), mood = ""),
                MoodLogEntity(syncId = "m-y", entryDate = today.toString(), mood = "UNKNOWN")
            ),
            diaries = listOf(diary(1, today, DiaryMood.GOOD))
        )

        assertEquals(DiaryMood.GOOD, moods[today])
    }

    @Test
    fun anUnreadableLastTapDoesNotHideTheDaysReadableFlow() {
        val first = log(today, DiaryMood.GOOD, 100L)
        val last = log(today, DiaryMood.TIRED, 200L)
        val broken = last.copy(syncId = "broken", mood = "", updatedAt = 300L)
        val unknown = last.copy(syncId = "unknown", mood = "UNKNOWN", updatedAt = 400L)
        val diaries = listOf(diary(1, today, DiaryMood.SAD))

        val changing = DayMood.entriesByDate(listOf(first, last, broken, unknown), diaries)[today]!!
        assertEquals(DiaryMood.TIRED, changing.mood)
        assertFalse(changing.settled)
        val settled = DayMood.entriesByDate(listOf(last, broken, unknown), diaries)[today]!!
        assertEquals(DiaryMood.TIRED, settled.mood)
        assertTrue(settled.settled)
        assertTrue(DayMood.byDate(listOf(broken, unknown), emptyList()).isEmpty())
    }

    @Test
    fun theLastTapOfTheDayIsFoundForRepeatDetection() {
        val logs = listOf(
            log(today.minusDays(1), DiaryMood.GOOD),
            log(today, DiaryMood.SAD, updatedAt = 100L),
            log(today, DiaryMood.TIRED, updatedAt = 200L)
        )

        assertEquals(DiaryMood.TIRED, DayMood.logOn(logs, today)?.moodOption)
        // 일기는 보지 않습니다. 연속 누름을 보는 것이라 찍어 둔 것만 봅니다.
        assertNull(DayMood.logOn(emptyList(), today))
    }

    private fun log(
        date: LocalDate,
        mood: DiaryMood,
        updatedAt: Long = 100L
    ) = MoodLogEntity(
        syncId = "mood-$date-$mood",
        updatedAt = updatedAt,
        entryDate = date.toString(),
        mood = mood.name,
        createdAt = updatedAt
    )

    private fun diary(
        id: Long,
        date: LocalDate,
        mood: DiaryMood?,
        createdAt: Long = 0L
    ) = DiaryEntity(
        id = id,
        syncId = "diary-$id",
        updatedAt = 0L,
        entryDate = date.toString(),
        body = "본문",
        mood = mood?.name.orEmpty(),
        createdAt = createdAt
    )
}
