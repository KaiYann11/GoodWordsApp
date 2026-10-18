package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 오늘의 한마디로 무엇이 나가는지.
 *
 * 돌아보기와 달리 하루에 여러 번 나가는 길이라, 두 가지를 못 박습니다.
 * **일기 본문은 켜지 않으면 나가지 않습니다**(돌아보기와 같은 설정, 같은 함수).
 * 그리고 **지문은 오늘 한 것으로만 만듭니다** — 시계가 섞이면 "같은 기록으로 두 번 묻지
 * 않는다"는 규칙이 통째로 무너져, 홈을 열 때마다 요금이 붙습니다.
 */
class DailyCoachPromptTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 17)
    private val at = LocalTime.of(15, 12)
    private val steps = listOf(DailyStep.QUOTE, DailyStep.ROUTINE, DailyStep.DIARY)

    private val diary = DiaryEntity(
        id = 1,
        syncId = "diary-1",
        entryDate = today.toString(),
        title = "아무에게도 못 한 말",
        body = "오늘은 마음이 무거웠다. 그래도 산책은 다녀왔다.",
        weather = "RAIN",
        mood = "SAD"
    )

    @Test
    fun theDiaryStaysOnThisDeviceUnlessAsked() {
        val prompt = DailyCoachPrompt.userPrompt(digest(diaries = listOf(diary), includeDiaryBody = false))

        assertFalse(prompt, prompt.contains("마음이 무거웠다"))
        // 제목도 본문입니다. 한 줄 제목에 그날의 전부가 담기기도 합니다.
        assertFalse(prompt, prompt.contains("아무에게도 못 한 말"))
        // 날짜·기분·날씨·글자 수는 갑니다. 짧게 쓴 날과 길게 쓴 날의 차이도 신호입니다.
        assertTrue(prompt, prompt.contains("SAD"))
        assertTrue(prompt, prompt.contains("본문 ${diary.body.length}자"))
    }

    @Test
    fun turningItOnSendsTheBody() {
        val prompt = DailyCoachPrompt.userPrompt(digest(diaries = listOf(diary), includeDiaryBody = true))

        assertTrue(prompt, prompt.contains("마음이 무거웠다"))
    }

    @Test
    fun onlyTodayGoesOut() {
        val yesterday = diary.copy(id = 2, syncId = "diary-2", entryDate = today.minusDays(1).toString(), body = "어제의 글")
        val digest = digest(
            checks = listOf(check("물 한 컵")),
            diaries = listOf(diary, yesterday),
            includeDiaryBody = true
        )
        val prompt = DailyCoachPrompt.userPrompt(digest)

        assertTrue(prompt, prompt.contains("물 한 컵"))
        assertFalse(prompt, prompt.contains("어제의 글"))
    }

    @Test
    fun whatWasDoneTodayComesFromTheDayCard() {
        // 그날 카드와 같은 셈을 봅니다. 여기서 따로 세면 화면과 코치가 다른 하루를 말합니다.
        val digest = digest(
            checks = listOf(check("물 한 컵"), check("산책")),
            events = listOf(confirmed("오늘의 글귀")),
            todos = listOf(todoDone("장보기"))
        )

        assertEquals(
            listOf("루틴: 물 한 컵, 산책", "할 일: 장보기", "읽은 글귀: 오늘의 글귀"),
            digest.doneLines
        )
        assertFalse(digest.isEmpty)
    }

    @Test
    fun routinesLeftAreForPickingOneNotForScolding() {
        val digest = digest(
            routines = listOf(routine(1, "r1", "물 한 컵"), routine(2, "r2", "산책"), routine(3, "r3", "스트레칭")),
            checks = listOf(check("물 한 컵", routineId = 1, routineSyncId = "r1"))
        )

        // 밟은 것은 빠집니다. 체크는 syncId로 루틴을 찾습니다.
        assertEquals(listOf("산책", "스트레칭"), digest.restRoutines)
        // 다그치지 말라는 말이 물음에 함께 나갑니다.
        assertTrue(DailyCoachPrompt.systemPrompt().contains("못 한 것을 세지 않습니다"))
    }

    @Test
    fun theClockDoesNotChangeTheFingerprint() {
        val morning = digest(checks = listOf(check("물 한 컵")), at = LocalTime.of(7, 30))
        val evening = digest(checks = listOf(check("물 한 컵")), at = LocalTime.of(22, 10))

        assertEquals(morning.fingerprint, evening.fingerprint)
        // 그래도 지금 몇 시인지는 나갑니다. 아침에 남은 것을 재촉하지 않으려면 알아야 합니다.
        assertTrue(DailyCoachPrompt.userPrompt(morning).contains("07:30"))
    }

    @Test
    fun doingOneMoreThingChangesTheFingerprint() {
        val before = digest(checks = listOf(check("물 한 컵")))
        val after = digest(checks = listOf(check("물 한 컵"), check("산책")))

        assertNotEquals(before.fingerprint, after.fingerprint)
    }

    @Test
    fun aDayWithNothingInItHasNoFingerprint() {
        // 빈 기록으로 물으면 누구에게나 같은 말이 돌아옵니다. 묻지 않는 쪽을 지문이 정합니다.
        val empty = digest()

        assertTrue(empty.isEmpty)
        assertEquals("", empty.fingerprint)
    }

    @Test
    fun theShortFingerprintPathMatchesTheRealOne() {
        // 화면은 짧은 길로 세고 실제로 물을 때는 긴 길로 셉니다. 둘이 다르면 다시 물을 때를
        // 놓치거나, 같은 기록을 두고 또 묻습니다.
        val checks = listOf(check("물 한 컵", routineId = 1, routineSyncId = "r1"))
        val diaries = listOf(diary)
        // 긴 길에만 있는 것들(루틴 목록·보관함)은 지문에 들어가지 않습니다.
        val full = digest(
            routines = listOf(routine(1, "r1", "물 한 컵"), routine(2, "r2", "산책")),
            checks = checks,
            items = listOf(ContentItemEntity(id = 1, syncId = "i1", type = ContentType.QUOTE, title = "담아 둔 글귀", body = "")),
            diaries = diaries
        )

        assertEquals(
            full.fingerprint,
            DailyCoachPrompt.fingerprint(
                today = today,
                dayDigest = dayDigest(checks = checks, diaries = diaries),
                progress = progress(checks = checks, diaries = diaries),
                diaries = diaries,
                includeDiaryBody = false
            )
        )
    }

    @Test
    fun whatIAlreadyKeepIsSentToBeLeftOut() {
        val digest = digest(
            checks = listOf(check("물 한 컵")),
            items = listOf(ContentItemEntity(id = 1, syncId = "i1", type = ContentType.QUOTE, title = "이미 가진 글귀", body = "", author = "나"))
        )
        val prompt = DailyCoachPrompt.userPrompt(digest)

        assertTrue(prompt, prompt.contains("이미 가진 글귀 (나)"))
        assertTrue(prompt, prompt.contains("추천에서 빼 주세요"))
    }

    @Test
    fun myOwnNotesAreNotSentAsQuotesToExclude() {
        // 임시저장 글과 메모는 추천할 글귀가 아니라 제외 목록에도 보내지 않습니다.
        val digest = digest(
            checks = listOf(check("물 한 컵")),
            items = listOf(
                ContentItemEntity(id = 1, syncId = "i1", type = ContentType.MEMO, title = "내가 적어 둔 것", body = ""),
                ContentItemEntity(id = 2, syncId = "i2", type = ContentType.INBOX, title = "임시로 담은 것", body = "")
            )
        )

        assertEquals(emptyList<String>(), digest.ownedQuoteLines)
    }

    @Test
    fun thePreviewIsExactlyWhatGoesOut() {
        val digest = digest(checks = listOf(check("물 한 컵")))

        assertEquals(
            DailyCoachPrompt.systemPrompt() + "\n\n" + DailyCoachPrompt.userPrompt(digest),
            DailyCoachPrompt.chatPrompt(digest)
        )
    }

    private fun digest(
        routines: List<RoutineEntity> = emptyList(),
        checks: List<RoutineCheckEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList(),
        diaries: List<DiaryEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList(),
        items: List<ContentItemEntity> = emptyList(),
        includeDiaryBody: Boolean = false,
        at: LocalTime = this.at
    ): DailyCoachDigest = DailyCoachPrompt.digest(
        today = today,
        at = at,
        dayDigest = dayDigest(checks, todos, diaries, events),
        progress = progress(checks, todos, diaries, events),
        routines = routines,
        todayChecks = checks,
        diaries = diaries,
        items = items,
        includeDiaryBody = includeDiaryBody
    )

    private fun dayDigest(
        checks: List<RoutineCheckEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList(),
        diaries: List<DiaryEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList()
    ): DayDigest = DayScoreCalculator.digest(
        date = today,
        events = events,
        routineChecks = checks,
        todos = todos,
        diaries = diaries,
        steps = steps,
        zoneId = zone
    )

    private fun progress(
        checks: List<RoutineCheckEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList(),
        diaries: List<DiaryEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList()
    ): DailyProgress = DailyLoopCalculator.build(
        events = events,
        routineChecks = checks,
        todos = todos,
        diaries = diaries,
        today = today,
        steps = steps,
        zoneId = zone
    )

    private fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun routine(id: Long, syncId: String, title: String) =
        RoutineEntity(id = id, syncId = syncId, title = title)

    private fun check(
        title: String,
        routineId: Long = 1,
        routineSyncId: String = "r1",
        checkedAt: Long = at(today)
    ) = RoutineCheckEntity(
        syncId = "c-$title",
        routineId = routineId,
        routineSyncId = routineSyncId,
        routineTitle = title,
        checkedAt = checkedAt
    )

    private fun todoDone(title: String) = TodoEntity(
        syncId = "t-$title",
        title = title,
        dueDate = today.toString(),
        doneAt = at(today)
    )

    private fun confirmed(title: String) = ExposureEventEntity(
        syncId = "e-$title",
        contentItemId = 1,
        contentItemSyncId = "i1",
        contentTitle = title,
        contentType = ContentType.QUOTE,
        eventType = ExposureEventType.CONFIRMED,
        trigger = ExposureTrigger.MANUAL_REFRESH,
        occurredAt = at(today)
    )
}
