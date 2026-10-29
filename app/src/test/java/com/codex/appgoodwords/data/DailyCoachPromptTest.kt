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
    fun theDirectionGoesOutOnlyWhenTurnedOn() {
        // 되고 싶은 모습은 사적인 글이라 켜지 않으면 나가지 않습니다(기본 꺼짐).
        val vision = ContentItemEntity(
            id = 50, syncId = "v-1", type = ContentType.VISION, title = "아침을 지배하는 사람",
            body = "일어나자마자 물 한 컵", linkedRoutineSyncIds = listOf("r1")
        )
        val routine = RoutineEntity(id = 1, syncId = "r1", title = "물 한 컵")
        val off = DailyCoachPrompt.userPrompt(digest(routines = listOf(routine), checks = listOf(check("물 한 컵")), items = listOf(vision)))
        val on = DailyCoachPrompt.userPrompt(
            digest(routines = listOf(routine), checks = listOf(check("물 한 컵")), items = listOf(vision), includeGoals = true)
        )

        assertFalse(off.contains("내가 가려는 방향"))
        assertFalse(off.contains("아침을 지배하는 사람"))
        assertTrue(on.contains("[내가 가려는 방향]"))
        assertTrue(on.contains("되고 싶은 나: 아침을 지배하는 사람 — 일어나자마자 물 한 컵 (이어 둔 루틴: 물 한 컵)"))
    }

    @Test
    fun aChangedGoalIsNotANewRecordForToday() {
        // 목표를 고친 것은 오늘 한 일이 아닙니다. 지문이 바뀌면 같은 하루로 또 묻습니다.
        val goal = ContentItemEntity(id = 51, syncId = "g-1", type = ContentType.GOAL, title = "마라톤", body = "")
        val before = digest(checks = listOf(check("물 한 컵")), items = listOf(goal), includeGoals = true)
        val after = digest(checks = listOf(check("물 한 컵")), items = listOf(goal.copy(title = "하프 마라톤")), includeGoals = true)

        assertEquals(before.fingerprint, after.fingerprint)
    }

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

    @Test
    fun whatWeSaidGoesOutOnlyOnceISpoke() {
        // 코치 혼자 건넨 한마디들은 대화가 아닙니다. 답하지 않는 사람의 물음은 예전 그대로입니다.
        val silent = digest(checks = listOf(check("물 한 컵")), talk = listOf(tipLine("물 한 컵으로 여셨네요.", "09:12")))
        assertEquals(emptyList<String>(), silent.talkLines)
        assertFalse(DailyCoachPrompt.userPrompt(silent).contains("오늘 나눈 말"))

        // 내가 한 말이 있으면 다음 한마디도 압니다(사용자가 정함, 2026-10-29).
        val talked = digest(
            checks = listOf(check("물 한 컵")),
            talk = listOf(
                tipLine("물 한 컵으로 여셨네요. 저녁엔 걸어 보세요.", "09:12", action = "저녁 산책 10분"),
                line(DailyCoachLine.Kind.REPLY, "다리가 아파서 걷기는 어려워요", "09:20"),
                line(DailyCoachLine.Kind.ANSWER, "그럼 앉아서 발목을 돌려 보세요.", "09:21")
            )
        )
        val prompt = DailyCoachPrompt.userPrompt(talked)
        assertTrue(prompt, prompt.contains("[오늘 나눈 말]"))
        assertTrue(prompt, prompt.contains("- 09:12 코치(한마디): 물 한 컵으로 여셨네요. 저녁엔 걸어 보세요. / 해 볼 일: 저녁 산책 10분"))
        assertTrue(prompt, prompt.contains("- 09:20 나: 다리가 아파서 걷기는 어려워요"))
        assertTrue(prompt, prompt.contains("- 09:21 코치: 그럼 앉아서 발목을 돌려 보세요."))
        // 마다한 것을 다시 권하지 말라는 말이 함께 나갑니다.
        assertTrue(DailyCoachPrompt.systemPrompt().contains("[오늘 나눈 말]이 있으면 그 흐름을 잇습니다"))
    }

    @Test
    fun talkingIsNotANewRecordForToday() {
        // 말을 주고받은 것은 오늘 한 일이 아닙니다. 지문이 바뀌면 답할 때마다 "기록이 바뀌었다"가 됩니다.
        val quiet = digest(checks = listOf(check("물 한 컵")))
        val talked = digest(
            checks = listOf(check("물 한 컵")),
            talk = listOf(tipLine("한마디", "09:12"), line(DailyCoachLine.Kind.REPLY, "피곤해요", "09:20"))
        )

        assertEquals(quiet.fingerprint, talked.fingerprint)
    }

    @Test
    fun theReplyPromptEndsWithWhatIJustSaid() {
        val digest = digest(
            checks = listOf(check("물 한 컵")),
            items = listOf(ContentItemEntity(id = 1, syncId = "i1", type = ContentType.QUOTE, title = "이미 가진 글귀", body = "")),
            talk = listOf(tipLine("한마디", "09:12"), line(DailyCoachLine.Kind.REPLY, "오늘은 좀 피곤해요", "09:20"))
        )
        val prompt = DailyCoachPrompt.replyUserPrompt(digest)

        assertTrue(prompt, prompt.contains("[오늘 한 것]"))
        assertEquals("- 09:20 나: 오늘은 좀 피곤해요", digest.talkLines.last().let { "- $it" })
        assertTrue(prompt, prompt.trimEnd().endsWith("방금 들은 말에 이어 형식에 맞는 JSON으로만 답해 주세요."))
        // 답할 때는 글귀를 권하지 않습니다. 빼 달라는 목록을 보낼 일이 없습니다.
        assertFalse(prompt, prompt.contains("이미 가진 글귀"))
    }

    @Test
    fun theReplyKeepsTheSameDiaryRule() {
        // 무엇이 나가는지는 한마디와 같은 설정이 정합니다. 답할 때라고 일기 본문이 따라가지 않습니다.
        val digest = digest(
            diaries = listOf(diary),
            includeDiaryBody = false,
            talk = listOf(tipLine("한마디", "09:12"), line(DailyCoachLine.Kind.REPLY, "피곤해요", "09:20"))
        )
        val prompt = DailyCoachPrompt.replyUserPrompt(digest)

        assertFalse(prompt, prompt.contains("마음이 무거웠다"))
        assertFalse(prompt, prompt.contains("아무에게도 못 한 말"))
    }

    @Test
    fun theReplyAsksForAShortAnswerAndOneThingToTry() {
        val system = DailyCoachPrompt.replySystemPrompt()

        assertTrue(system, system.contains("""{"message":"...","action":"..."}"""))
        assertTrue(system, system.contains("방금 들은 말에 먼저 답합니다"))
        assertTrue(system, system.contains("세 문장을 넘기지 않습니다"))
        // 힘든 말을 들었을 때 할 일보다 마음을 먼저 받고, 위험하면 기댈 곳을 알려 줍니다.
        assertTrue(system, system.contains("109"))
    }

    @Test
    fun aLongDayOfTalkSendsOnlyTheEnd() {
        // 하루 내내 이어진 대화를 다 보내면 물을 때마다 값이 커집니다. 지금 무슨 이야기인지는 끝쪽에 있습니다.
        val talk = (0 until 30).map { index ->
            line(if (index % 2 == 0) DailyCoachLine.Kind.REPLY else DailyCoachLine.Kind.ANSWER, "말 $index", "10:%02d".format(index))
        }
        val lines = DailyCoachPrompt.talkLines(talk, zone)

        assertEquals(16, lines.size)
        assertEquals("10:29 코치: 말 29", lines.last())
        assertEquals("10:14 나: 말 14", lines.first())
    }

    @Test
    fun myLineBreaksDoNotSplitALine() {
        // 줄이 갈리면 어디까지가 한 사람의 말인지 흐려집니다.
        val lines = DailyCoachPrompt.talkLines(
            listOf(line(DailyCoachLine.Kind.REPLY, "피곤해요\n\n그래도 걷고 싶어요  \n", "09:20")),
            zone
        )

        assertEquals(listOf("09:20 나: 피곤해요 그래도 걷고 싶어요"), lines)
    }

    private fun digest(
        routines: List<RoutineEntity> = emptyList(),
        checks: List<RoutineCheckEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList(),
        diaries: List<DiaryEntity> = emptyList(),
        events: List<ExposureEventEntity> = emptyList(),
        items: List<ContentItemEntity> = emptyList(),
        includeDiaryBody: Boolean = false,
        at: LocalTime = this.at,
        includeGoals: Boolean = false,
        talk: List<DailyCoachLine> = emptyList()
    ): DailyCoachDigest = DailyCoachPrompt.digest(
        today = today,
        at = at,
        dayDigest = dayDigest(checks, todos, diaries, events),
        progress = progress(checks, todos, diaries, events),
        routines = routines,
        todayChecks = checks,
        diaries = diaries,
        items = items,
        includeDiaryBody = includeDiaryBody,
        includeGoals = includeGoals,
        talk = talk,
        zoneId = zone
    )

    private fun line(kind: DailyCoachLine.Kind, text: String, time: String, action: String = "") = DailyCoachLine(
        kind = kind,
        text = text,
        action = action,
        at = at(today, LocalTime.parse(time))
    )

    private fun tipLine(text: String, time: String, action: String = "") = line(DailyCoachLine.Kind.TIP, text, time, action)

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
