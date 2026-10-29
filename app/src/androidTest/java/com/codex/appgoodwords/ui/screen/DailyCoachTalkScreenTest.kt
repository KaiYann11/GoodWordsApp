package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import com.codex.appgoodwords.data.DailyCoachLine
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.DailyCoachTip
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 오늘의 한마디와 나눈 말.
 *
 * 세 가지를 봅니다 — **보낸 말이 그대로 나가고 칸이 비는지**, **답을 기다리는 동안 한 번 더 보내지 못하는지**(답 하나에
 * 물음 하나, 값이 드는 길입니다), 그리고 **담을 것은 한 번만 담기는지**(카드와 같은 규칙).
 */
class DailyCoachTalkScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.now()

    /** 화면을 열기 전의 때. 이때 이미 있던 말은 써지듯 나타나지 않고 처음부터 다 보입니다. */
    private val before = System.currentTimeMillis() - 60_000L

    private val tip = DailyCoachTip(
        message = "물 한 컵으로 하루를 여셨네요.",
        quote = "작게 시작하라",
        routine = "저녁 스트레칭",
        action = "자기 전 물 한 컵",
        model = "claude-opus-5-5",
        createdAt = before
    )

    private fun withTip(): DailyCoachState = DailyCoachState(date = today).asked(today, before).withTip(tip, "abc").markSeen()

    private fun answer(text: String, action: String = "", at: Long) = DailyCoachLine(
        kind = DailyCoachLine.Kind.ANSWER,
        text = text,
        action = action,
        model = "claude-sonnet-5-5",
        at = at
    )

    @Test
    fun theWordAndWhatWeSaidAreThereInOrder() {
        val talked = withTip()
            .replied("I am tired today", before + 1_000)
            .answered(answer("그럼 5분만 쉬어 가세요.", at = before + 2_000), askedAt = before + 1_000)
        compose.setContent { DailyCoachTalkScreen(state = talked) }

        compose.onNodeWithText("물 한 컵으로 하루를 여셨네요.").assertIsDisplayed()
        compose.onNodeWithText("I am tired today").assertIsDisplayed()
        compose.onNodeWithText("그럼 5분만 쉬어 가세요.").assertIsDisplayed()
    }

    @Test
    fun whatIWriteIsSentAndTheFieldEmpties() {
        var sent: String? = null
        compose.setContent { DailyCoachTalkScreen(state = withTip(), onSend = { sent = it }) }

        // 빈 말은 보낼 수 없습니다.
        compose.onNodeWithTag(coachTalkSendTag).assertIsNotEnabled()
        compose.onNodeWithTag(coachTalkInputTag).performTextInput("  I am tired  ")
        compose.onNodeWithTag(coachTalkSendTag).assertIsEnabled().performClick()

        assertEquals("I am tired", sent)
        compose.onNodeWithTag(coachTalkInputTag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun whileTheCoachWritesNothingMoreIsSent() {
        // 답 하나에 물음 하나입니다. 기다리는 동안 다시 보내지 못하게 합니다 — 쓰는 것은 그대로 됩니다.
        compose.setContent {
            DailyCoachTalkScreen(state = withTip().replied("I am tired", before + 1_000), replying = true)
        }

        compose.onNodeWithTag(coachTalkTypingTag).assertIsDisplayed()
        compose.onNodeWithTag(coachTalkInputTag).performTextInput("and hungry")
        compose.onNodeWithTag(coachTalkSendTag).assertIsNotEnabled()
    }

    @Test
    fun anUnansweredWordSaysWhyAndCanBeAskedAgain() {
        var retried = false
        compose.setContent {
            DailyCoachTalkScreen(
                state = withTip().replied("I am tired", before + 1_000).withReplyError("AI 요청이 실패했습니다(502)."),
                onRetry = { retried = true }
            )
        }

        compose.onNodeWithText("AI 요청이 실패했습니다(502).").assertIsDisplayed()
        compose.onNodeWithTag(coachTalkRetryTag).performClick()

        assertTrue(retried)
    }

    @Test
    fun oneThingToTryInAnAnswerCanBeKeptOnce() {
        val answerAt = before + 2_000
        val talked = withTip()
            .replied("I am tired", before + 1_000)
            .answered(answer("그럼 창가에서 숨을 고르세요.", action = "창문 열기", at = answerAt), askedAt = before + 1_000)
        var current by mutableStateOf(talked)
        var keptAt: Long? = null
        compose.setContent { DailyCoachTalkScreen(state = current, onKeepAnswerAction = { keptAt = it }) }

        compose.onNodeWithText("창문 열기").assertIsDisplayed()
        compose.onNodeWithTag(coachTalkAnswerActionTag(answerAt)).performClick()
        assertEquals(answerAt, keptAt)

        // 담고 나면 버튼을 거둡니다. 두 번 눌리면 같은 할 일이 두 벌 생깁니다.
        current = talked.answerActionKept(answerAt)
        compose.onNodeWithTag(coachTalkAnswerActionTag(answerAt)).assertDoesNotExist()
        compose.onNodeWithText("할 일에 담았습니다").assertIsDisplayed()
    }

    @Test
    fun onlyTheCurrentWordHasKeepButtons() {
        // 앞선 한마디의 담기 표시는 남아 있지 않습니다. 버튼을 달면 이미 담은 것을 또 담게 됩니다.
        val later = before + 30_000
        val twoWords = withTip()
            .asked(today, later)
            .withTip(tip.copy(message = "점심 뒤에 물 한 컵 더.", quote = "다시 시작하라", createdAt = later), "abc")
            .markSeen()
        var keptQuote = false
        compose.setContent { DailyCoachTalkScreen(state = twoWords, onKeepQuote = { keptQuote = true }) }

        compose.onNodeWithText("물 한 컵으로 하루를 여셨네요.").assertIsDisplayed()
        compose.onAllNodesWithTag(coachTalkQuoteTag).assertCountEquals(1)
        compose.onNodeWithTag(coachTalkQuoteTag).performClick()
        assertTrue(keptQuote)
    }

    @Test
    fun withoutAWordThereIsNothingToAnswer() {
        compose.setContent { DailyCoachTalkScreen(state = DailyCoachState(date = today)) }

        compose.onNodeWithTag(coachTalkInputTag).assertIsNotEnabled()
        compose.onNodeWithText("오늘 받은 한마디가 아직 없습니다. 홈의 한마디 칸에서 받으면 여기서 답할 수 있습니다.")
            .assertIsDisplayed()
    }

    @Test
    fun anAnswerThatArrivesWhileOpenIsWrittenOutAndThenItsButtonShows() {
        // 화면을 연 뒤에 온 답은 글자가 써지듯 나타나고, 다 써진 뒤에 담기 버튼이 보입니다.
        val said = withTip().replied("I am tired", before + 1_000)
        var current by mutableStateOf(said)
        compose.setContent { DailyCoachTalkScreen(state = current) }

        val arrivedAt = System.currentTimeMillis() + 1_000
        current = said.answered(answer("그럼 5분만 쉬어 가세요.", action = "창문 열기", at = arrivedAt), askedAt = before + 1_000)
        compose.mainClock.advanceTimeBy(3_000)

        compose.onNodeWithText("그럼 5분만 쉬어 가세요.").assertIsDisplayed()
        compose.onNodeWithTag(coachTalkAnswerActionTag(arrivedAt)).assertIsDisplayed()
    }

    @Test
    fun aNewWordSeenHereIsMarkedSeen() {
        // 홈 카드를 거치지 않고 여기서 새 한마디를 보았으면 카드의 반짝임과 홈 탭의 점을 거둡니다.
        var seen = 0
        val fresh = DailyCoachState(date = today).asked(today, before).withTip(tip, "abc")
        compose.setContent { DailyCoachTalkScreen(state = fresh, onSeen = { seen += 1 }) }

        compose.mainClock.advanceTimeBy(6_000)
        compose.waitForIdle()

        assertEquals(1, seen)
    }
}
