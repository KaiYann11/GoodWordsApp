package com.codex.appgoodwords.ui.screen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.codex.appgoodwords.data.ContentItemEntity

/**
 * 글귀를 한 장의 카드 이미지로 그립니다.
 *
 * 글자 크기는 본문 길이에 맞춰 줄입니다. 고정해 두면 긴 글귀가 카드 밖으로 잘려 나가는데,
 * 하필 잘리는 곳이 문장 끝이라 무슨 말인지 알 수 없게 됩니다.
 *
 * Compose 대신 Canvas로 그리는 이유는 화면에 없는 것을 그려야 하고,
 * 결과 크기를 화면과 무관하게 정해야 하기 때문입니다.
 */
object QuoteCardRenderer {
    /** 세로로 조금 긴 4:5. 대부분의 공유 화면에서 잘리지 않고 들어갑니다. */
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val MARGIN = 96f
    private const val MAX_BODY_SIZE = 68f
    private const val MIN_BODY_SIZE = 30f
    private const val META_SIZE = 34f

    fun render(item: ContentItemEntity): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)

        val body = item.body.ifBlank { item.title }.trim()
        val contentWidth = (WIDTH - MARGIN * 2).toInt()
        // 아래쪽 저자·출처가 들어갈 자리를 빼고 남는 높이 안에 본문을 맞춥니다.
        val bodyMaxHeight = HEIGHT - MARGIN * 2 - META_SIZE * 4

        val bodyLayout = fitBody(body, contentWidth, bodyMaxHeight)
        val metaLines = metaLines(item)
        val metaPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.argb(200, 255, 255, 255)
            textSize = META_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val metaHeight = metaLines.size * META_SIZE * 1.5f
        val blockHeight = bodyLayout.height + metaHeight
        val top = ((HEIGHT - blockHeight) / 2f).coerceAtLeast(MARGIN)

        canvas.save()
        canvas.translate(MARGIN, top)
        bodyLayout.draw(canvas)
        canvas.restore()

        var metaY = top + bodyLayout.height + META_SIZE * 1.6f
        metaLines.forEach { line ->
            canvas.drawText(line, MARGIN, metaY, metaPaint)
            metaY += META_SIZE * 1.5f
        }

        return bitmap
    }

    /**
     * 카드 안에 들어갈 때까지 글자 크기를 줄입니다.
     *
     * 가장 작은 크기로도 넘치면 거기서 멈춥니다. 더 줄이면 읽을 수 없게 되어,
     * 잘리더라도 읽히는 크기를 지키는 편이 낫습니다.
     */
    private fun fitBody(text: String, width: Int, maxHeight: Float): StaticLayout {
        var size = MAX_BODY_SIZE
        var layout = bodyLayout(text, width, size)
        while (layout.height > maxHeight && size > MIN_BODY_SIZE) {
            size -= 2f
            layout = bodyLayout(text, width, size)
        }
        return layout
    }

    private fun bodyLayout(text: String, width: Int, size: Float): StaticLayout {
        val paint = TextPaint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(size * 0.35f, 1f)
            .setIncludePad(false)
            .build()
    }

    /** 어디서 온 말인지 밝힙니다. 없는 줄은 넣지 않습니다. */
    internal fun metaLines(item: ContentItemEntity): List<String> = buildList {
        val author = item.author.trim()
        if (author.isNotBlank()) add("— $author")

        val source = item.title.trim().takeIf { it.isNotBlank() && it != item.body.trim() }
        val page = item.bookPage.takeIf { it > 0 }
        when {
            // 책에서 뽑은 글귀는 몇 쪽인지까지 밝혀 두면 다시 찾기 쉽습니다.
            source != null && page != null -> add("$source · ${page}쪽")
            source != null -> add(source)
            else -> Unit
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val paint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                0f,
                0f,
                WIDTH.toFloat(),
                HEIGHT.toFloat(),
                intArrayOf(Color.rgb(31, 111, 120), Color.rgb(20, 82, 90), Color.rgb(15, 52, 60)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
    }
}
