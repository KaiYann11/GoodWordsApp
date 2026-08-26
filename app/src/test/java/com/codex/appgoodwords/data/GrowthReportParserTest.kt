package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 모델이 돌려준 글을 레코드로 바꾸는 규칙입니다.
 *
 * JSON만 달라고 일러 두어도 그대로 지켜 주지 않는 경우가 있습니다. 그때마다 오류로 돌리면
 * 사용자는 값만 치르고 이유를 알 수 없는 실패를 봅니다.
 */
class GrowthReportParserTest {
    @Test
    fun aCleanJsonAnswerBecomesAReport() {
        val report = parse(
            """
            {
              "strengths": ["이레 내내 물을 마셨습니다."],
              "improvements": ["스트레칭이 뜸합니다."],
              "suggestedQuote": {"text": "작게 시작하라.", "author": "제임스 클리어"},
              "suggestedRoutines": ["자기 전 스트레칭 5분"],
              "guide": "다음 주에는 저녁 시간을 한 번 비워 보세요."
            }
            """.trimIndent()
        )

        assertEquals(listOf("이레 내내 물을 마셨습니다."), report?.strengths)
        assertEquals("작게 시작하라.", report?.suggestedQuote)
        assertEquals("제임스 클리어", report?.suggestedQuoteAuthor)
        assertEquals(listOf("자기 전 스트레칭 5분"), report?.suggestedRoutines)
        assertEquals("2026-08-17", report?.periodStart)
    }

    @Test
    fun aCodeFencedAnswerIsStillRead() {
        // ```json 으로 감싸 오는 모델이 있습니다. 그것만으로 실패시키지 않습니다.
        val report = parse("```json\n{\"guide\": \"천천히 가도 됩니다.\"}\n```")

        assertEquals("천천히 가도 됩니다.", report?.guide)
    }

    @Test
    fun aGreetingBeforeTheJsonIsIgnored() {
        val report = parse("좋습니다! 아래와 같이 정리했어요.\n{\"strengths\": [\"꾸준했습니다.\"]}\n도움이 되었길 바랍니다.")

        assertEquals(listOf("꾸준했습니다."), report?.strengths)
    }

    @Test
    fun aQuoteSentAsPlainTextStillLands() {
        val report = parse("""{"suggestedQuote": "오늘 한 걸음."}""")

        assertEquals("오늘 한 걸음.", report?.suggestedQuote)
        assertEquals("", report?.suggestedQuoteAuthor)
    }

    @Test
    fun anAnswerWithoutJsonIsRefused() {
        assertNull(parse("죄송하지만 도와드릴 수 없습니다."))
        assertNull(parse(""))
    }

    @Test
    fun anEmptyShellIsRefused() {
        // 껍데기만 오면 화면에 놓아도 빈 카드만 보입니다.
        assertNull(parse("""{"strengths": [], "improvements": [], "guide": "  "}"""))
    }

    @Test
    fun blankLinesAreDroppedAndLongListsAreTrimmed() {
        val report = parse(
            """{"strengths": ["가", "  ", "나", "다", "라", "마", "바", "사"]}"""
        )

        assertTrue(report!!.strengths.none { it.isBlank() })
        assertTrue("${report.strengths.size}줄입니다.", report.strengths.size <= 5)
    }

    private fun parse(rawText: String) = GrowthReportParser.parse(
        rawText = rawText,
        period = ReportPeriod.WEEKLY,
        periodStart = "2026-08-17",
        periodEnd = "2026-08-23",
        model = "gpt-4o-mini",
        now = 1_700_000_000_000L
    )
}
