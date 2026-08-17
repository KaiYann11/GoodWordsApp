package com.codex.appgoodwords.ui.screen

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 글귀 카드가 실제로 그려지고 다른 앱에 넘길 수 있는 모양인지 봅니다.
 *
 * 특히 긴 글귀입니다. 글자 크기를 고정하면 카드 밖으로 잘려 나가는데,
 * 하필 잘리는 곳이 문장 끝이라 무슨 말인지 알 수 없게 됩니다.
 */
class QuoteCardRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        File(context.cacheDir, "quote-cards").deleteRecursively()
    }

    @Test
    fun aCardIsDrawnAtTheExpectedSize() {
        val bitmap = QuoteCardRenderer.render(quote(body = "행동은 감정이 따라올 때까지 기다리면 늘 늦다."))

        assertEquals(QuoteCardRenderer.WIDTH, bitmap.width)
        assertEquals(QuoteCardRenderer.HEIGHT, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun theCardIsNotBlank() {
        val bitmap = QuoteCardRenderer.render(quote(body = "짧은 글귀"))

        // 배경만 칠하고 글자를 안 그리면 빈 카드가 나갑니다.
        // 흰 글자가 한 점이라도 있어야 합니다. 배경은 어두운 청록이라 흰색이 나올 수 없습니다.
        var whitePixels = 0
        for (y in 0 until QuoteCardRenderer.HEIGHT step 4) {
            for (x in 0 until QuoteCardRenderer.WIDTH step 4) {
                val pixel = bitmap.getPixel(x, y)
                val isWhite = android.graphics.Color.red(pixel) > 220 &&
                    android.graphics.Color.green(pixel) > 220 &&
                    android.graphics.Color.blue(pixel) > 220
                if (isWhite) whitePixels += 1
            }
        }
        assertTrue("글자가 그려지지 않았습니다.", whitePixels > 20)
        bitmap.recycle()
    }

    @Test
    fun aLongQuoteStillFits() {
        val long = "긴 문장입니다. ".repeat(40)
        val x = QuoteCardRenderer.WIDTH / 2
        val y = QuoteCardRenderer.HEIGHT - 8

        // 배경이 대각선 그라데이션이라, 글자 없는 같은 자리의 색과 견줘야 합니다.
        val plain = QuoteCardRenderer.render(quote(body = "짧은 글귀"))
        val backgroundAtBottom = plain.getPixel(x, y)
        plain.recycle()

        val bitmap = QuoteCardRenderer.render(quote(body = long))

        assertEquals("본문이 카드 밖까지 이어졌습니다.", backgroundAtBottom, bitmap.getPixel(x, y))
        bitmap.recycle()
    }

    @Test
    fun theSourceIsShownWhenThereIsOne() {
        val lines = QuoteCardRenderer.metaLines(
            quote(body = "본문").copy(author = "제임스 클리어", title = "아주 작은 습관의 힘", bookPage = 42)
        )

        assertEquals(listOf("— 제임스 클리어", "아주 작은 습관의 힘 · 42쪽"), lines)
    }

    @Test
    fun anEmptySourceLeavesNoEmptyLine() {
        val lines = QuoteCardRenderer.metaLines(
            ContentItemEntity(id = 1, syncId = "q1", type = ContentType.QUOTE, title = "본문", body = "본문")
        )

        // 빈 줄만 남으면 카드 아래가 어색하게 비어 보입니다.
        assertTrue("빈 줄이 들어갔습니다: $lines", lines.isEmpty())
    }

    @Test
    fun theCardBecomesAShareableAddress() {
        val uri = writeQuoteCard(context, quote(body = "공유할 글귀"))

        // 파일 경로를 그대로 넘기면 받는 앱이 열 수 없습니다.
        assertEquals("content", uri.scheme)
        assertTrue(uri.toString().contains("fileprovider"))

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        assertTrue("빈 파일이 넘어갑니다.", (bytes?.size ?: 0) > 1000)
    }

    @Test
    fun sharingTheSameQuoteTwiceDoesNotPileUpFiles() {
        val item = quote(body = "같은 글귀")

        writeQuoteCard(context, item)
        writeQuoteCard(context, item)

        val files = File(context.cacheDir, "quote-cards").listFiles().orEmpty()
        // 공유할 때마다 쌓이면 캐시가 계속 불어납니다.
        assertEquals(1, files.size)
    }

    private fun quote(body: String) = ContentItemEntity(
        id = 1,
        syncId = "q1",
        type = ContentType.QUOTE,
        title = "제목",
        body = body
    )
}
