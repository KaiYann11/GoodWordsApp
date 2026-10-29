package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 코치가 보낸 글을 한마디로 바꾸는 자리.
 *
 * 홈 카드는 스치듯 보는 자리라, 길게 오면 여기서 자릅니다. 그리고 **한마디가 없으면 받은
 * 것으로 치지 않습니다** — 글귀만 덜렁 오면 오늘 하루를 보고 건넨 말이 아닙니다.
 */
class DailyCoachParserTest {
    private val now = 1_760_000_000_000L

    @Test
    fun readsTheFourPieces() {
        val tip = parse(
            """
            {"message":"물 한 컵으로 하루를 여셨네요.",
             "quote":{"text":"작게 시작하라","author":"작자 미상"},
             "routine":"저녁 스트레칭","action":"자기 전 물 한 컵"}
            """
        )!!

        assertEquals("물 한 컵으로 하루를 여셨네요.", tip.message)
        assertEquals("작게 시작하라", tip.quote)
        assertEquals("작자 미상", tip.quoteAuthor)
        assertEquals("저녁 스트레칭", tip.routine)
        assertEquals("자기 전 물 한 컵", tip.action)
        assertEquals("claude-opus-5", tip.model)
        assertEquals(now, tip.createdAt)
    }

    @Test
    fun aWordWithNothingToRecommendIsStillAWord() {
        // 셋을 매번 채우라고 하지 않습니다. 비어 있는 것이 맞는 날이 있습니다.
        val tip = parse("""{"message":"오늘은 이대로 충분합니다.","quote":{"text":"","author":""},"routine":"","action":""}""")!!

        assertEquals("오늘은 이대로 충분합니다.", tip.message)
        assertEquals("", tip.quote)
        assertEquals("", tip.routine)
        assertEquals("", tip.action)
        assertTrue(tip.hasContent)
    }

    @Test
    fun codeFencesAndGreetingsDoNotThrowTheAnswerAway() {
        val tip = parse(
            """
            알겠습니다. 아래와 같습니다.
            ```json
            {"message":"산책까지 다녀오셨네요."}
            ```
            """
        )

        assertEquals("산책까지 다녀오셨네요.", tip?.message)
    }

    @Test
    fun aQuoteSentAsPlainTextIsStillRead() {
        val tip = parse("""{"message":"좋습니다.","quote":"작게 시작하라"}""")

        assertEquals("작게 시작하라", tip?.quote)
        assertEquals("", tip?.quoteAuthor)
    }

    @Test
    fun aLongAnswerIsCutSoTheCardStaysACard() {
        val long = "가".repeat(400)
        val tip = parse("""{"message":"$long","routine":"$long"}""")!!

        assertTrue(tip.message.length < 200)
        assertTrue(tip.message.endsWith("…"))
        assertTrue(tip.routine.length < 60)
    }

    @Test
    fun aWordlessAnswerIsNotAWord() {
        assertNull(parse("""{"quote":{"text":"작게 시작하라"}}"""))
        assertNull(parse("""{"message":"   "}"""))
    }

    @Test
    fun garbageIsNotMistakenForAWord() {
        assertNull(parse(""))
        assertNull(parse("오늘은 쉬어 가세요"))
        assertNull(parse("{망가진 글"))
    }

    @Test
    fun anAnswerReadsTheWordAndOneThingToTry() {
        val answer = answer("""{"message":"그럼 5분만 창가에서 숨을 고르세요.","action":"창문 열기"}""")!!

        assertEquals(DailyCoachLine.Kind.ANSWER, answer.kind)
        assertEquals("그럼 5분만 창가에서 숨을 고르세요.", answer.text)
        assertEquals("창문 열기", answer.action)
        assertEquals("claude-opus-5", answer.model)
        assertEquals(now, answer.at)
    }

    @Test
    fun anAnswerThatForgotTheFormatIsStillAnAnswer() {
        // 대화의 답은 말 자체가 전부입니다. JSON을 잊었다고 버리면 내 말이 답 없이 남습니다.
        val answer = answer("  쉬는 것도 하루의 일입니다. 오늘은 일찍 누워 보세요.  ")

        assertEquals("쉬는 것도 하루의 일입니다. 오늘은 일찍 누워 보세요.", answer?.text)
        assertEquals("", answer?.action)
    }

    @Test
    fun aCutOffAnswerIsNotShownAsIs() {
        // 잘린 JSON을 글자 그대로 띄우면 말풍선에 `{"message":"그럼` 같은 조각이 뜹니다.
        assertNull(answer("""{"message":"그럼 5분만"""))
        assertNull(answer(""))
        assertNull(answer("""{"message":"","action":"창문 열기"}"""))
    }

    @Test
    fun aNullActionIsNoAction() {
        // 비워 두라는 자리에 null을 적어 보내는 모델이 있습니다. "null"이 할 일로 뜨면 안 됩니다.
        assertEquals("", answer("""{"message":"좋습니다.","action":null}""")?.action)
    }

    @Test
    fun aLongAnswerIsCutToThreeSentencesWorth() {
        val answer = answer("""{"message":"${"가".repeat(600)}","action":"${"나".repeat(100)}"}""")!!

        assertTrue(answer.text.length <= 241)
        assertTrue(answer.text.endsWith("…"))
        assertTrue(answer.action.length <= 41)
    }

    private fun parse(rawText: String) = DailyCoachParser.parse(rawText, model = "claude-opus-5", now = now)

    private fun answer(rawText: String) = DailyCoachParser.parseAnswer(rawText, model = "claude-opus-5", now = now)
}
