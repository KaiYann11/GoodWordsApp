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
    fun lastTimesAdviceComesBackWithTheRecords() {
        // 이것이 없으면 매번 처음 만난 사람처럼 말합니다. 같은 조언을 몇 번이고 다시 받습니다.
        val prompt = GrowthPrompt.userPrompt(
            digest(previous = report(on = LocalDate.of(2026, 8, 16)), includeDiaryBody = false)
        )

        assertTrue(prompt, prompt.contains("지난번에 권한 것"))
        assertTrue(prompt, prompt.contains("자기 전 30분 폰 내려놓기"))
        assertTrue(prompt, prompt.contains("저녁 산책"))
        // 언제 한 말인지 알아야 "그 뒤로 어땠는지"를 셀 수 있습니다.
        assertTrue(prompt, prompt.contains("2026-08-16"))
    }

    @Test
    fun whatTheAiWroteFreelyDoesNotGoOutAgain() {
        // 가이드는 자유롭게 쓴 글이라 그때 읽은 일기가 묻어날 수 있습니다. 그 뒤로 일기 본문
        // 보내기를 껐다면, 껐다는 뜻이 지난 글을 통해 조용히 뒤집힙니다.
        val prompt = GrowthPrompt.userPrompt(
            digest(previous = report(on = LocalDate.of(2026, 8, 16)), includeDiaryBody = false)
        )

        assertFalse("지난 가이드가 다시 나갔습니다.", prompt.contains("마음이 무거웠다고"))
        // 잘한 점은 같은 칭찬을 되풀이하게 할 뿐입니다.
        assertFalse(prompt, prompt.contains("일기를 다섯 번 썼습니다"))
    }

    @Test
    fun adviceFromLongAgoIsNotLastTime() {
        // 석 달 전에 권한 것을 두고 "그 뒤로 어떠셨나요"라고 물으면 이번 기간의 답이 끌려갑니다.
        assertTrue(digest(previous = report(on = today.minusDays(90)), includeDiaryBody = false).previousAdviceLines.isNotEmpty())
        assertTrue(digest(previous = report(on = today.minusDays(91)), includeDiaryBody = false).previousAdviceLines.isEmpty())
    }

    @Test
    fun lastTimesAdviceAloneIsNothingToLookBackOn() {
        // 이 기간에 한 일이 없는데 지난 조언만 들고 다시 물으면 같은 말이 돌아옵니다.
        assertTrue(digest(previous = report(on = LocalDate.of(2026, 8, 16)), includeDiaryBody = false).isEmpty)
    }

    @Test
    fun theRulesTellItToPickUpWhereItLeftOff() {
        val system = GrowthPrompt.systemPrompt()

        assertTrue(system, system.contains("지난번에 권한 것"))
        // 되풀이를 막지 않으면 매주 같은 글이 옵니다.
        assertTrue(system, system.contains("되풀이"))
    }

    @Test
    fun whatIsAlreadyKeptGoesOutSoItIsNotRecommendedBack() {
        // 기록만 보여 주면 모델은 거기 있던 글귀를 그대로 돌려줍니다. 내가 쓴 것을 내가
        // 추천받는 셈이 됩니다. 무엇에 쓰는 목록인지 제목에 적어 보냅니다.
        val prompt = GrowthPrompt.userPrompt(
            digest(
                items = listOf(
                    ContentItemEntity(
                        id = 1,
                        syncId = "i1",
                        type = ContentType.QUOTE,
                        title = "오늘 할 수 있는 것부터",
                        body = "본문",
                        author = "내가"
                    )
                ),
                includeDiaryBody = false
            )
        )

        assertTrue(prompt, prompt.contains("이미 담아 둔 것 (추천에서 빼 주세요)"))
        assertTrue(prompt, prompt.contains("오늘 할 수 있는 것부터 (내가)"))
    }

    @Test
    fun theRulesSayNotToHandBackWhatIsAlreadyThere() {
        val system = GrowthPrompt.systemPrompt()

        assertTrue(system, system.contains("권하지 않습니다"))
        // 검색해 오라고 시키지 않습니다. 알고 있는 것 중에서 고르면 됩니다.
        assertTrue(system, system.contains("인터넷을 찾아볼 필요는 없습니다"))
    }

    @Test
    fun whatIsKeptAloneIsNotSomethingToLookBackOn() {
        // 담아 둔 것만 있고 이 기간에 한 일이 없으면 값만 나가고 빈 글이 돌아옵니다.
        val digest = digest(
            items = listOf(ContentItemEntity(id = 1, syncId = "i1", type = ContentType.QUOTE, title = "글귀", body = "본문")),
            includeDiaryBody = false
        )

        assertTrue(digest.ownedQuoteLines.isNotEmpty())
        assertTrue(digest.isEmpty)
    }

    @Test
    fun aWeekWithoutDiariesIsNoLongerBlank() {
        // 기분을 일기에서 뗀 이유입니다. 바빠서 일기를 못 쓴 주는 AI에게 "아무것도 안 한 주"로
        // 보였습니다. 정작 그런 주가 가장 알고 싶은 주입니다.
        val prompt = GrowthPrompt.userPrompt(
            digest(
                moodLogs = listOf(moodLog("2026-08-21", DiaryMood.TIRED)),
                includeDiaryBody = false
            )
        )

        assertTrue(prompt, prompt.contains("일기 없이 남긴 기분"))
        assertTrue(prompt, prompt.contains("2026-08-21 지침"))
    }

    @Test
    fun aMoodIsNotSentTwiceForTheSameDay() {
        // 일기가 있는 날은 그 줄에 기분이 이미 붙어 있습니다.
        val prompt = GrowthPrompt.userPrompt(
            digest(
                diaries = listOf(diary.copy(entryDate = "2026-08-21", mood = "SAD")),
                moodLogs = listOf(moodLog("2026-08-21", DiaryMood.TIRED)),
                includeDiaryBody = false
            )
        )

        assertFalse(prompt, prompt.contains("일기 없이 남긴 기분"))
    }

    @Test
    fun aMoodAloneIsSomethingToLookBackOn() {
        // 기분만 찍어 둔 주도 돌아볼 것이 있습니다. 값만 나가고 빈 글이 오는 것과 다릅니다.
        assertFalse(digest(moodLogs = listOf(moodLog("2026-08-21", DiaryMood.GOOD)), includeDiaryBody = false).isEmpty)
    }

    private fun moodLog(date: String, mood: DiaryMood) = MoodLogEntity(
        syncId = "mood-$date",
        entryDate = date,
        mood = mood.name
    )

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
        moodLogs: List<MoodLogEntity> = emptyList(),
        items: List<ContentItemEntity> = emptyList(),
        previous: GrowthReportEntity? = null,
        includeDiaryBody: Boolean
    ) = GrowthPrompt.digest(
        period = period,
        today = today,
        routines = routines,
        routineChecks = checks,
        todos = emptyList(),
        diaries = diaries,
        items = items,
        events = emptyList(),
        books = emptyList(),
        moodLogs = moodLogs,
        includeDiaryBody = includeDiaryBody,
        previousReport = previous,
        zoneId = zone
    )

    private fun millis(date: LocalDate): Long =
        date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun report(
        on: LocalDate,
        strengths: List<String> = listOf("일기를 다섯 번 썼습니다"),
        improvements: List<String> = listOf("자기 전 30분 폰 내려놓기"),
        routines: List<String> = listOf("저녁 산책"),
        guide: String = "비가 온 날 마음이 무거웠다고 쓰셨습니다. 그런 날은 짧게 걸어 보세요."
    ) = GrowthReportEntity(
        period = ReportPeriod.WEEKLY.name,
        periodStart = on.minusDays(6).toString(),
        periodEnd = on.toString(),
        strengths = strengths,
        improvements = improvements,
        suggestedRoutines = routines,
        guide = guide,
        createdAt = millis(on)
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
