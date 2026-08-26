package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI에게 보낼 글을 만드는 규칙입니다.
 *
 * 여기서 지키려는 것은 하나입니다. **일기 본문은 켜지 않으면 나가지 않습니다.**
 * 한번 나가면 되돌릴 수 없는 것이라, 기본값이 무심코 바뀌지 않도록 시험으로 못 박습니다.
 */
class GrowthPromptTest {
    private val today = LocalDate.of(2026, 8, 23)
    private val zone = ZoneId.of("Asia/Seoul")

    private val diary = DiaryEntity(
        id = 1,
        syncId = "diary-1",
        updatedAt = 0L,
        entryDate = "2026-08-22",
        title = "제목",
        body = "오늘은 마음이 무거웠다. 아무에게도 하지 못한 말이 있다.",
        weather = "RAIN",
        mood = "SAD",
        createdAt = 0L
    )

    @Test
    fun theDiaryBodyStaysOnThisDeviceUnlessAsked() {
        val digest = digest(diaries = listOf(diary), includeDiaryBody = false)
        val prompt = GrowthPrompt.userPrompt(digest)

        assertFalse("일기 본문이 나갔습니다.", prompt.contains("아무에게도 하지 못한 말"))
        // 날짜와 기분은 갑니다. 짧게 쓴 날과 길게 쓴 날의 차이도 신호라 글자 수만 보냅니다.
        assertTrue(prompt, prompt.contains("2026-08-22"))
        assertTrue(prompt, prompt.contains("SAD"))
        assertTrue(prompt, prompt.contains("본문 ${diary.body.length}자"))
    }

    @Test
    fun turningItOnSendsTheBody() {
        val prompt = GrowthPrompt.userPrompt(digest(diaries = listOf(diary), includeDiaryBody = true))

        assertTrue(prompt, prompt.contains("아무에게도 하지 못한 말"))
    }

    @Test
    fun onlyRecordsInsideThePeriodAreSent() {
        val old = diary.copy(id = 2, syncId = "diary-2", entryDate = "2026-01-01", body = "지난겨울")
        val prompt = GrowthPrompt.userPrompt(
            digest(diaries = listOf(diary, old), includeDiaryBody = true)
        )

        assertTrue(prompt.contains("2026-08-22"))
        assertFalse("기간 밖 일기가 나갔습니다.", prompt.contains("지난겨울"))
    }

    @Test
    fun routineLinesCountWhatWasSteppedInThePeriod() {
        val routine = RoutineEntity(id = 1, syncId = "r1", updatedAt = 0L, title = "물 한 컵", createdAt = 0L)
        val checks = listOf(
            check(1, LocalDate.of(2026, 8, 22)),
            check(1, LocalDate.of(2026, 8, 21)),
            // 기간 밖입니다.
            check(1, LocalDate.of(2026, 7, 1))
        )

        val prompt = GrowthPrompt.userPrompt(
            digest(routines = listOf(routine), checks = checks, includeDiaryBody = false)
        )

        assertTrue(prompt, prompt.contains("물 한 컵: 2회"))
    }

    @Test
    fun anEmptyPeriodIsRecognised() {
        assertTrue(digest(includeDiaryBody = false).isEmpty)
        assertFalse(digest(diaries = listOf(diary), includeDiaryBody = false).isEmpty)
    }

    @Test
    fun theSystemPromptAsksForJsonOnly() {
        val system = GrowthPrompt.systemPrompt()

        assertTrue(system, system.contains("JSON"))
        // 다그치지 않는 것이 이 앱의 규칙입니다. 물음에도 그렇게 적어 둡니다.
        assertTrue(system, system.contains("다그치지"))
    }

    @Test
    fun theBlockToPasteCarriesTheRulesAsWellAsTheRecords() {
        // 채팅창에는 붙여넣을 자리가 하나뿐입니다. 규칙을 빠뜨리면 읽어 낼 수 없는 답이 옵니다.
        val digest = digest(includeDiaryBody = false)
        val chat = GrowthPrompt.chatPrompt(digest)

        assertTrue(chat, chat.contains(GrowthPrompt.systemPrompt()))
        assertTrue(chat, chat.contains(GrowthPrompt.userPrompt(digest)))
    }

    @Test
    fun theCopiedPromptRemembersWhichStretchItCovered() {
        // 채팅 앱에 다녀오는 사이 앱이 꺼져도, 날짜와 기간만 있으면 구간을 그대로 다시 셉니다.
        val pending = PendingGrowthPrompt(ReportPeriod.WEEKLY, LocalDate.of(2026, 8, 23))

        assertEquals(LocalDate.of(2026, 8, 17), pending.from)
        assertEquals(LocalDate.of(2026, 8, 23), pending.to)
        assertEquals(
            digest(period = ReportPeriod.WEEKLY, includeDiaryBody = false).from,
            pending.from
        )
    }

    @Test
    fun aOneDayPromptCoversOneDay() {
        val pending = PendingGrowthPrompt(ReportPeriod.DAILY, LocalDate.of(2026, 8, 23))

        assertEquals(pending.to, pending.from)
    }

    @Test
    fun thePeriodDecidesHowFarBackToLook() {
        assertEquals(LocalDate.of(2026, 8, 23), digest(period = ReportPeriod.DAILY, includeDiaryBody = false).from)
        assertEquals(LocalDate.of(2026, 8, 17), digest(period = ReportPeriod.WEEKLY, includeDiaryBody = false).from)
        assertEquals(LocalDate.of(2026, 7, 25), digest(period = ReportPeriod.MONTHLY, includeDiaryBody = false).from)
    }

    private fun digest(
        period: ReportPeriod = ReportPeriod.WEEKLY,
        routines: List<RoutineEntity> = emptyList(),
        checks: List<RoutineCheckEntity> = emptyList(),
        diaries: List<DiaryEntity> = emptyList(),
        includeDiaryBody: Boolean
    ) = GrowthPrompt.digest(
        period = period,
        today = today,
        routines = routines,
        routineChecks = checks,
        todos = emptyList(),
        diaries = diaries,
        items = emptyList(),
        events = emptyList(),
        books = emptyList(),
        includeDiaryBody = includeDiaryBody,
        zoneId = zone
    )

    private fun check(routineId: Long, date: LocalDate) = RoutineCheckEntity(
        id = date.dayOfYear.toLong(),
        syncId = "check-${date.dayOfYear}",
        routineId = routineId,
        routineSyncId = "r$routineId",
        routineTitle = "루틴",
        checkedAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    )
}
