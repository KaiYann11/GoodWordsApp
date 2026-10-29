package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 언제 다시 물을지.
 *
 * 값이 드는 요청이라 이 규칙이 곧 요금입니다. 주기가 돌아오면 오늘 기록이 그대로여도 다시 묻고
 * (사용자가 정함, 2026-10-19), 하루 횟수가 그 몫을 묶습니다.
 */
class DailyCoachStateTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val noon = 1_760_000_000_000L
    private val minute = 60_000L

    @Test
    fun theFirstAskOfTheDayGoesRightAway() {
        assertTrue(DailyCoachState().needsAsk("abc", noon))
    }

    @Test
    fun theSameRecordIsAskedAgainOnceTheGapHasPassed() {
        // 예전에는 기록이 그대로면 다시 묻지 않았습니다. 그러면 앱을 열지 않는 날 한마디가 오지 않아,
        // 주기마다 묻게 바꿨습니다. 다만 주기 안에서는 여전히 기다립니다.
        val state = asked().withTip(tip(), "abc")

        assertFalse(state.needsAsk("abc", noon + 10 * minute))
        assertTrue(state.needsAsk("abc", noon + AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute))
    }

    @Test
    fun aNewWordIsUnseenUntilItIsShown() {
        // 새 한마디는 카드가 눈에 띄게 알리고, 다 보여 준 뒤 거둡니다.
        val state = asked().withTip(tip(), "abc")

        assertTrue(state.hasNewTip)
        assertFalse(state.markSeen().hasNewTip)
        // 다음 한마디가 오면 다시 새 것입니다.
        assertTrue(state.markSeen().withTip(tip(message = "다음 한마디"), "abc").hasNewTip)
    }

    @Test
    fun aWordSavedBeforeSeenExistedIsReadAsSeen() {
        // 업데이트하자마자 옛 한마디가 새 것처럼 반짝이면 무엇이 새로 왔는지 헷갈립니다.
        val legacy = """{"date":"2026-10-17","askCount":1,"tip":{"message":"예전 한마디","createdAt":1}}"""

        val state = DailyCoachJson.decode(legacy)
        assertEquals("예전 한마디", state.tip?.message)
        assertFalse(state.hasNewTip)
    }

    @Test
    fun aNewRecordWaitsForTheGap() {
        val state = asked().withTip(tip(), "abc")

        assertFalse(state.needsAsk("def", noon + 10 * minute))
        assertTrue(state.needsAsk("def", noon + AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute))
    }

    @Test
    fun aDayWithNothingInItIsNotAsked() {
        // 빈 지문은 "오늘 한 것이 없다"는 뜻입니다.
        assertFalse(DailyCoachState().needsAsk("", noon))
    }

    @Test
    fun thereIsACeilingForTheDay() {
        var state = DailyCoachState()
        repeat(AiFeedbackSettings.DEFAULT_ASK_LIMIT) { index ->
            state = state.asked(today, noon + index * AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute)
        }

        assertEquals(AiFeedbackSettings.DEFAULT_ASK_LIMIT, state.askCount)
        assertFalse(state.needsAsk("새 기록", noon + 10 * 60 * minute))
    }

    @Test
    fun failingCountsToo() {
        // 안 되는 설정으로 하루 종일 두드리지 않습니다. 사유는 그대로 남습니다.
        val state = DailyCoachState().asked(today, noon).withError("열쇠가 없습니다.")

        assertEquals(1, state.askCount)
        assertEquals("열쇠가 없습니다.", state.lastError)
        // 지문은 적지 않습니다. 같은 기록으로 틈을 두고 한 번 더 가 볼 수 있어야 합니다.
        assertEquals("", state.fingerprint)
        assertTrue(state.needsAsk("abc", noon + AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute))
    }

    @Test
    fun anAskCutOffByLeavingTheScreenIsNotCounted() {
        // 홈에서 묻던 중에 앱을 나가 Android 15에 통신이 끊기면, 연결 탓이 아니라 세지도 사유를 남기지도
        // 않습니다. 세면 하루 횟수만 줄고 "인터넷 연결과 열쇠를 확인해 주세요"가 남습니다.
        val before = asked().withTip(tip(), "abc")
        val later = noon + AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute

        val cut = before.asked(today, later).interrupted(before)

        assertEquals(before.askCount, cut.askCount)
        assertEquals(before.lastAskedAt, cut.lastAskedAt)
        assertEquals("", cut.lastError)
        assertEquals(before.tip, cut.tip)
        assertTrue(cut.interruptedAsk)
    }

    @Test
    fun aCutOffAskIsAskedAgainWithoutWaitingForTheGap() {
        // 이미 묻기로 했던 것입니다. 주기를 다시 기다리면 앱을 나갔다는 이유로 한마디가 한 주기 밀립니다.
        // 눌러서 받던 것(주기 안)이 끊겼어도, 하루 횟수를 다 쓴 뒤였어도 같습니다.
        var used = DailyCoachState()
        repeat(AiFeedbackSettings.DEFAULT_ASK_LIMIT) { index -> used = used.asked(today, noon + index * minute) }
        val cut = used.asked(today, noon + 10 * minute).interrupted(used)

        assertTrue(cut.needsAsk("abc", noon + 11 * minute))
        // 오늘 한 것이 없으면 그래도 묻지 않습니다. 보낼 기록이 없습니다.
        assertFalse(cut.needsAsk("", noon + 11 * minute))

        // 이어 물으면 표시를 거둡니다. 그다음부터는 다시 주기와 횟수를 봅니다.
        val resumed = cut.asked(today, noon + 12 * minute)
        assertFalse(resumed.interruptedAsk)
        assertEquals(AiFeedbackSettings.DEFAULT_ASK_LIMIT + 1, resumed.askCount)
        assertFalse(resumed.needsAsk("abc", noon + 12 * 60 * minute))
    }

    @Test
    fun theLimitAndTheGapAreWhateverWasChosen() {
        // 값이 드는 정도는 사람마다 다르게 느낍니다. 앱이 정해 주지 않습니다.
        var state = DailyCoachState()
        repeat(2) { index -> state = state.asked(today, noon + index * 30 * minute) }

        // 하루 2번으로 줄여 두었으면 거기서 멈춥니다.
        assertFalse(state.needsAsk("새 기록", noon + 10 * 60 * minute, limit = 2, gapMinutes = 30))
        assertTrue(state.needsAsk("새 기록", noon + 10 * 60 * minute, limit = 5, gapMinutes = 30))

        // 틈을 30분으로 줄여 두었으면 30분 뒤에 갑니다.
        val once = DailyCoachState().asked(today, noon)
        assertFalse(once.needsAsk("새 기록", noon + 20 * minute, limit = 5, gapMinutes = 30))
        assertTrue(once.needsAsk("새 기록", noon + 30 * minute, limit = 5, gapMinutes = 30))
    }

    @Test
    fun turningTheLimitOffStopsTheAutomaticPath() {
        // "안 함"으로 두면 저절로는 묻지 않습니다. 눌러서 받는 길은 그대로 있습니다
        // (`force`는 needsAsk를 거치지 않습니다).
        assertFalse(DailyCoachState().needsAsk("abc", noon, limit = 0, gapMinutes = 30))
    }

    @Test
    fun aReasonWrittenBeforeTheFirstAskSurvivesToTheCard() {
        // 아직 한 번도 묻지 않은 날에 "닿을 길이 없다"를 적었습니다. 날짜가 비어 있으면
        // 화면이 다시 읽으며 오늘 것이 아니라고 버려서, 눌러도 아무 일도 없어 보입니다.
        val written = DailyCoachState().forDate(today).withError("AI에 닿을 길이 없습니다.")

        assertEquals("AI에 닿을 길이 없습니다.", written.forDate(today).lastError)
    }

    @Test
    fun yesterdaysWordIsNotShownToday() {
        val yesterday = DailyCoachState().asked(today.minusDays(1), noon).withTip(tip(), "abc")

        val fresh = yesterday.forDate(today)
        assertNull(fresh.tip)
        assertEquals(0, fresh.askCount)
        // 어제 다섯 번 물었어도 오늘은 처음부터입니다.
        assertTrue(fresh.needsAsk("abc", noon))
    }

    @Test
    fun aNewWordClearsWhatWasKept() {
        // 담은 표시가 남아 있으면 새 글귀를 담을 버튼이 사라진 채로 뜹니다.
        val state = asked().withTip(tip(), "abc").copy(quoteKept = true, routineKept = true, actionKept = true)

        val next = state.withTip(tip(message = "다음 한마디"), "def")
        assertFalse(next.quoteKept)
        assertFalse(next.routineKept)
        assertFalse(next.actionKept)
        assertEquals("", next.lastError)
    }

    @Test
    fun whatWasSavedComesBackTheSame() {
        val state = asked()
            .withTip(tip(), "abc")
            .copy(quoteKept = true, lastError = "")

        assertEquals(state, DailyCoachJson.decode(DailyCoachJson.encode(state)))
        // 끊긴 물음도 앱을 껐다 켜도 남아야 이어 묻습니다.
        val cut = asked().asked(today, noon + minute).interrupted(asked())
        assertEquals(cut, DailyCoachJson.decode(DailyCoachJson.encode(cut)))
    }

    @Test
    fun unreadableStorageIsAnEmptyDayNotACrash() {
        assertEquals(DailyCoachState(), DailyCoachJson.decode(""))
        assertEquals(DailyCoachState(), DailyCoachJson.decode("{망가진 글"))
        assertNull(DailyCoachJson.decode("""{"date":"어제"}""").date)
    }

    @Test
    fun aWordJoinsTheTalkInTheOrderItCame() {
        // 대화 화면에서 하루의 흐름이 보이고, 다음 물음에 무엇을 건넸는지가 갑니다.
        val first = asked().withTip(tip(), "abc")
        val second = first.asked(today, noon + 90 * minute).withTip(tip(message = "다음 한마디", at = noon + 90 * minute), "abc")

        assertEquals(listOf("물 한 컵으로 하루를 여셨네요.", "다음 한마디"), second.talk.map { it.text })
        assertTrue(second.talk.all { it.kind == DailyCoachLine.Kind.TIP })
        assertEquals("작은 일을 소홀히 하지 않는다", second.talk.first().quote)
        assertTrue(second.isCurrentTip(second.talk.last()))
        assertFalse(second.isCurrentTip(second.talk.first()))
    }

    @Test
    fun whatISayIsWrittenBeforeTheAnswerComes() {
        // 답이 오기 전에 먼저 적어야 화면에 곧바로 보이고, 기다리다 앱을 나가도 남습니다.
        val said = asked().withTip(tip(), "abc").replied("오늘은 좀 피곤해요", noon + 5 * minute)

        assertEquals(DailyCoachLine.Kind.REPLY, said.unanswered?.kind)
        assertEquals("오늘은 좀 피곤해요", said.unanswered?.text)
        assertEquals(noon + 5 * minute, said.lastRepliedAt)
        // 내가 말을 거는 것은 하루 횟수에 세지 않습니다. 저절로 묻는 길의 몫입니다.
        assertEquals(1, said.askCount)
        assertEquals(1, said.exchangeCount)

        val answered = said.answered(answer("그럼 5분만 창가에서 숨을 고르세요.", at = noon + 6 * minute), askedAt = noon + 5 * minute)
        assertNull(answered.unanswered)
        assertEquals(2, answered.exchangeCount)
        assertEquals(listOf("오늘은 좀 피곤해요", "그럼 5분만 창가에서 숨을 고르세요."), answered.talkSinceTip.map { it.text })
    }

    @Test
    fun talkingPushesTheNextWordOneGapAway() {
        // 대화하는 중에 새 한마디가 끼어들면 이어 가던 말이 위로 밀려 끊깁니다. 내가 말한 때부터 한 주기를 셉니다.
        val gap = AiFeedbackSettings.DEFAULT_GAP_MINUTES * minute
        val said = asked().withTip(tip(), "abc").replied("오늘은 좀 피곤해요", noon + 60 * minute)

        assertEquals(noon + 60 * minute, said.lastActiveAt)
        assertFalse(said.needsAsk("abc", noon + gap))
        assertTrue(said.needsAsk("abc", noon + 60 * minute + gap))
    }

    @Test
    fun aWordAskedBeforeISpokeGoesBeforeMyWords() {
        // 한마디를 묻는 사이에 보낸 내 말은 그 한마디가 읽지 못한 말입니다. 끝에 붙이면 답을 받은 것처럼 보이고,
        // 마지막 줄이 내 말이 아니게 되어 다음 차례에도 답을 받지 못합니다.
        val askedAt = noon + 90 * minute
        val waiting = asked().withTip(tip(), "abc")
            .asked(today, askedAt)
            .replied("방금 산책 다녀왔어요", askedAt + minute)

        val next = waiting.withTip(tip(message = "다음 한마디", at = askedAt), "abc")

        assertEquals(listOf("물 한 컵으로 하루를 여셨네요.", "다음 한마디", "방금 산책 다녀왔어요"), next.talk.map { it.text })
        assertEquals("방금 산책 다녀왔어요", next.unanswered?.text)
    }

    @Test
    fun anAnswerAskedBeforeISpokeAgainGoesBeforeMyNewWords() {
        val first = asked().withTip(tip(), "abc").replied("피곤해요", noon + minute)
        val more = first.replied("그래도 걷고 싶긴 해요", noon + 3 * minute)

        // 첫 말에 대한 답은 둘째 말을 보기 전에 물은 것입니다.
        val answered = more.answered(answer("쉬는 것도 하루의 일입니다.", at = noon + 4 * minute), askedAt = noon + 2 * minute)

        assertEquals(
            listOf("물 한 컵으로 하루를 여셨네요.", "피곤해요", "쉬는 것도 하루의 일입니다.", "그래도 걷고 싶긴 해요"),
            answered.talk.map { it.text }
        )
        assertEquals("그래도 걷고 싶긴 해요", answered.unanswered?.text)
    }

    @Test
    fun aNewWordReadsWhatWasLeftUnanswered() {
        // 답을 받지 못한 말이 있어도 그 뒤에 물은 한마디는 그 말까지 읽고 쓴 것입니다. 실패 표시는 거둡니다.
        val failed = asked().withTip(tip(), "abc")
            .replied("피곤해요", noon + minute)
            .withReplyError("AI에 연결하지 못했습니다.")

        val next = failed.asked(today, noon + 2 * 60 * minute).withTip(tip(message = "다음 한마디", at = noon + 2 * 60 * minute), "abc")

        assertNull(next.unanswered)
        assertEquals("", next.replyError)
        assertEquals(listOf("피곤해요", "다음 한마디"), next.talk.drop(1).map { it.text })
    }

    @Test
    fun aReplyCutOffByLeavingIsNotAnError() {
        // 화면을 떠나 끊긴 것은 연결 탓이 아닙니다. 사유를 남기지 않고 이어 받을 것으로 적습니다.
        val cut = asked().withTip(tip(), "abc").replied("피곤해요", noon + minute).replyCutOff()

        assertTrue(cut.replyInterrupted)
        assertEquals("", cut.replyError)
        assertEquals("피곤해요", cut.unanswered?.text)
        // 이어 받으면 표시를 거둡니다.
        assertFalse(cut.answered(answer("쉬어 가세요.", at = noon + 2 * minute), askedAt = noon + minute).replyInterrupted)
    }

    @Test
    fun keepingAnAnswersActionMarksOnlyThatAnswer() {
        val talked = asked().withTip(tip(), "abc")
            .replied("피곤해요", noon + minute)
            .answered(answer("창가에서 숨을 고르세요.", action = "창문 열기", at = noon + 2 * minute), askedAt = noon + minute)
            .replied("좋아요", noon + 3 * minute)
            .answered(answer("그럼 물도 한 컵.", action = "물 한 컵", at = noon + 4 * minute), askedAt = noon + 3 * minute)

        val kept = talked.answerActionKept(noon + 2 * minute)

        val answers = kept.talk.filter { it.kind == DailyCoachLine.Kind.ANSWER }
        assertEquals(listOf(true, false), answers.map { it.actionKept })
        // 한마디에 딸린 해 볼 일의 표시와는 따로입니다.
        assertFalse(kept.actionKept)
    }

    @Test
    fun theWordIsShownEvenWhenTheTalkHasNoTrailOfIt() {
        // 대화가 생기기 전에 받은 한마디(업데이트 직후)에도 답할 수 있어야 합니다. 무엇에 답하는지가 빠지면 대화가 허공에 뜹니다.
        val legacy = DailyCoachJson.decode("""{"date":"2026-10-17","askCount":1,"tip":{"message":"예전 한마디","createdAt":1}}""")

        assertEquals(emptyList<DailyCoachLine>(), legacy.talk)
        assertEquals(listOf("예전 한마디"), legacy.conversation.map { it.text })
        assertTrue(legacy.isCurrentTip(legacy.conversation.single()))
    }

    @Test
    fun theTalkComesBackTheSameAfterSaving() {
        val talked = asked().withTip(tip(), "abc")
            .replied("피곤해요\n오늘은 쉬고 싶어요", noon + minute)
            .answered(answer("쉬어 가세요.", action = "일찍 눕기", at = noon + 2 * minute), askedAt = noon + minute)
            .answerActionKept(noon + 2 * minute)
            .replied("고마워요", noon + 3 * minute)
            .replyCutOff()

        assertEquals(talked, DailyCoachJson.decode(DailyCoachJson.encode(talked)))
        val failed = talked.withReplyError("AI 요청이 실패했습니다(502).")
        assertEquals(failed, DailyCoachJson.decode(DailyCoachJson.encode(failed)))
    }

    @Test
    fun aBrokenLineDoesNotLoseTheWholeDay() {
        val stored = """{"date":"2026-10-17","talk":[{"kind":"ODD","text":"모르는 종류"},{"kind":"REPLY","text":"  "},""" +
            """{"kind":"REPLY","text":"피곤해요","at":5}]}"""

        assertEquals(listOf("피곤해요"), DailyCoachJson.decode(stored).talk.map { it.text })
    }

    @Test
    fun yesterdaysTalkIsGoneToday() {
        val talked = DailyCoachState().asked(today.minusDays(1), noon).withTip(tip(), "abc").replied("피곤해요", noon + minute)

        val fresh = talked.forDate(today)
        assertEquals(emptyList<DailyCoachLine>(), fresh.talk)
        assertEquals(0L, fresh.lastRepliedAt)
    }

    @Test
    fun theTalkDoesNotGrowForever() {
        var state = asked().withTip(tip(), "abc")
        repeat(DailyCoachState.MAX_TALK_LINES) { index -> state = state.replied("말 $index", noon + (index + 1) * minute) }

        assertEquals(DailyCoachState.MAX_TALK_LINES, state.talk.size)
        assertEquals("말 ${DailyCoachState.MAX_TALK_LINES - 1}", state.talk.last().text)
        // 앞이 잘려 지금 한마디가 대화에서 빠져도, 대화 화면과 물음에는 맨 앞에 놓입니다.
        assertTrue(state.isCurrentTip(state.conversation.first()))
    }

    @Test
    fun onlyAFreshAnswerIsAnnouncedWithWhatItAnswered() {
        val talked = asked().withTip(tip(), "abc")
            .replied("피곤해요", noon + minute)
            .answered(answer("쉬어 가세요.", at = noon + 2 * minute), askedAt = noon + minute)
        val answer = talked.talk.last()

        assertEquals(answer, talked.answerSince(noon + 2 * minute))
        assertNull(talked.answerSince(noon + 3 * minute))
        assertEquals("피곤해요", talked.replyBefore(answer))
        // 마지막 줄이 내 말이면 새로 온 답이 없습니다.
        assertNull(talked.replied("고마워요", noon + 4 * minute).answerSince(0L))
    }

    private fun asked() = DailyCoachState().asked(today, noon)

    private fun answer(text: String, action: String = "", at: Long) = DailyCoachLine(
        kind = DailyCoachLine.Kind.ANSWER,
        text = text,
        action = action,
        model = "claude-opus-5-5",
        at = at
    )

    private fun tip(message: String = "물 한 컵으로 하루를 여셨네요.", at: Long = noon) = DailyCoachTip(
        message = message,
        quote = "작은 일을 소홀히 하지 않는다",
        quoteAuthor = "",
        routine = "저녁 스트레칭",
        action = "자기 전 물 한 컵",
        model = "claude-opus-5",
        createdAt = at
    )
}
