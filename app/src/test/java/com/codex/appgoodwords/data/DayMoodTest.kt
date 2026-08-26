package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
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
    fun aTapWithNoMoodClearsTheDay() {
        // 병합으로 빈 값이 올 수 있습니다. 지운 것을 일기 기분으로 되살리면 안 됩니다.
        val moods = DayMood.byDate(
            logs = listOf(MoodLogEntity(syncId = "m-x", entryDate = today.toString(), mood = "")),
            diaries = listOf(diary(1, today, DiaryMood.GOOD))
        )

        assertTrue(moods.toString(), today !in moods)
    }

    @Test
    fun theTapOfTheDayIsFoundForEditing() {
        val logs = listOf(
            log(today.minusDays(1), DiaryMood.GOOD),
            log(today, DiaryMood.SAD, updatedAt = 100L),
            log(today, DiaryMood.TIRED, updatedAt = 200L)
        )

        assertEquals(DiaryMood.TIRED, DayMood.logOn(logs, today)?.moodOption)
        // 일기는 보지 않습니다. 고칠 자리를 찾는 것이라 찍어 둔 것만 봅니다.
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
