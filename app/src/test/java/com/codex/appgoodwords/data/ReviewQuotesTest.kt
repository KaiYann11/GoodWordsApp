package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 독후감에서 글귀를 쉽게 뽑기(사용자가 고름, 2026-11-28) — 문장으로 나누기, 고른 문장 잇기, 따옴표 속 문장 후보.
 * 진도 머리 줄(`BookNotes.entry`의 `11월 9일 · 120쪽`)은 문장이 아니고 그 아래 문장에 쪽을 줍니다.
 */
class ReviewQuotesTest {
    private val review = """
        |처음부터 끝까지 경험의 이야기다.
        |
        |11월 9일 · 120쪽
        |결국 사람들은 경험을 산다. 물건보다 이야기가 오래 남는다.
        |나도 요즘 그렇다
        |
        |
        |11월 12일
        |그는 “행복은 습관이다.”라고 말했다. 3.5배라는 숫자도 기억난다!
        |> 오늘을 사는 사람이 내일을 산다
    """.trimMargin()

    @Test
    fun progressHeadersAreNotSentencesAndGiveTheirPageToTheLinesBelow() {
        val pieces = ReviewQuotes.pieces(review)

        assertEquals(
            listOf("11월 9일 · 120쪽" to 120, "11월 12일" to null),
            pieces.filterIsInstance<ReviewPiece.Header>().map { it.text to it.page }
        )
        assertEquals(
            listOf(
                "처음부터 끝까지 경험의 이야기다." to null,
                "결국 사람들은 경험을 산다." to 120,
                "물건보다 이야기가 오래 남는다." to 120,
                // 마침표 없이 적은 한 줄도 한 문장입니다.
                "나도 요즘 그렇다" to 120,
                // 따옴표 안의 마침표, 숫자 속 마침표에서는 끊지 않습니다. 쪽 없는 머리 줄 아래는 쪽을 모릅니다.
                "그는 “행복은 습관이다.”라고 말했다." to null,
                "3.5배라는 숫자도 기억난다!" to null,
                // > 표시는 떼고 문장만.
                "오늘을 사는 사람이 내일을 산다" to null
            ),
            ReviewQuotes.sentences(review).map { it.text to it.page }
        )
        // 빈 줄이 여럿이어도 문단 사이는 한 칸입니다.
        assertEquals(2, pieces.count { it == ReviewPiece.Gap })
    }

    @Test
    fun pickedSentencesJoinAsWrittenAndSkippedOnesStartANewLine() {
        val sentences = ReviewQuotes.sentences(review)

        assertEquals("결국 사람들은 경험을 산다. 물건보다 이야기가 오래 남는다.", ReviewQuotes.quoteText(review, sentences, setOf(2, 1)))
        assertEquals("물건보다 이야기가 오래 남는다.\n나도 요즘 그렇다", ReviewQuotes.quoteText(review, sentences, setOf(2, 3)))
        // 머리 줄은 글귀에 들어가지 않습니다.
        assertEquals("처음부터 끝까지 경험의 이야기다.\n결국 사람들은 경험을 산다.", ReviewQuotes.quoteText(review, sentences, setOf(0, 1)))
        assertEquals("결국 사람들은 경험을 산다.\n그는 “행복은 습관이다.”라고 말했다.", ReviewQuotes.quoteText(review, sentences, setOf(1, 4)))
        assertEquals("", ReviewQuotes.quoteText(review, sentences, emptySet()))
    }

    @Test
    fun theQuotePageIsThatOfTheFirstPickedSentence() {
        val sentences = ReviewQuotes.sentences(review)

        assertEquals(120, ReviewQuotes.pageOf(sentences, setOf(3, 1)))
        assertNull(ReviewQuotes.pageOf(sentences, setOf(0, 1)))
        assertNull(ReviewQuotes.pageOf(sentences, emptySet()))
    }

    @Test
    fun quotedSentencesAndQuoteLinesBecomeCandidatesInOrderOnce() {
        val text = """
            |11월 9일 · 120쪽
            |그는 “행복은 매일의 습관이다.”라고 말했다. 짧게 "좋다" 했다.
            |「익숙한 것을 낯설게 보라」
            |> 오늘을 사는 사람이 내일을 산다
            |“행복은 매일의 습관이다.”
        """.trimMargin()

        assertEquals(
            listOf(
                QuoteCandidate("행복은 매일의 습관이다.", 120),
                QuoteCandidate("익숙한 것을 낯설게 보라", 120),
                QuoteCandidate("오늘을 사는 사람이 내일을 산다", 120)
            ),
            ReviewQuotes.candidates(text)
        )
    }

    @Test
    fun candidatesBeforeAnyHeaderHaveNoPage() {
        assertEquals(listOf(QuoteCandidate("처음 읽은 문장이 오래 남는다", null)), ReviewQuotes.candidates("『처음 읽은 문장이 오래 남는다』"))
    }

    @Test
    fun savedQuotesMatchWithoutQuoteMarksOrExtraSpaces() {
        assertEquals("행복은 습관이다.", ReviewQuotes.normalized(" “행복은   습관이다.” "))
        assertEquals(ReviewQuotes.normalized("행복은 습관이다."), ReviewQuotes.normalized("\"행복은 습관이다.\""))
    }
}
