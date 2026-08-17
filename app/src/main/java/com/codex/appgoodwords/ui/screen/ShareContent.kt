package com.codex.appgoodwords.ui.screen

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import java.io.File

/**
 * 글귀를 한 장의 카드 이미지로 만들어 공유합니다.
 *
 * 글로만 보내면 받는 쪽에서 그냥 문자로 흘러가는데, 카드로 보내면 남습니다.
 * 실패하면 null을 돌려주어 부르는 쪽이 안내할 수 있게 합니다.
 */
fun shareQuoteImage(context: Context, item: ContentItemEntity): Boolean {
    val uri = runCatching { writeQuoteCard(context, item) }.getOrNull() ?: return false

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        // 글도 함께 넣어 둡니다. 이미지를 못 받는 앱에서는 글이라도 가야 합니다.
        putExtra(Intent.EXTRA_TEXT, buildShareText(item))
        // 공유 시트가 보낼 그림을 미리 보여 주려면 ClipData가 있어야 합니다.
        // 없으면 무엇을 보내는지 모른 채 앱을 골라야 합니다.
        clipData = ClipData.newUri(context.contentResolver, "글귀 카드", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareIntent, "공유할 앱 선택").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * 카드를 캐시에 써 두고 다른 앱이 읽을 수 있는 주소를 돌려줍니다.
 *
 * 같은 글귀는 같은 파일에 덮어씁니다. 공유할 때마다 쌓이면 캐시가 계속 불어납니다.
 */
internal fun writeQuoteCard(context: Context, item: ContentItemEntity): Uri {
    val directory = File(context.cacheDir, "quote-cards").apply { mkdirs() }
    val file = File(directory, "quote-${item.id}.png")

    QuoteCardRenderer.render(item).use { bitmap ->
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }

    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** 다 쓴 비트맵은 바로 놓아 줍니다. 카드가 한 장에 5MB쯤 됩니다. */
private inline fun <T> Bitmap.use(block: (Bitmap) -> T): T {
    try {
        return block(this)
    } finally {
        recycle()
    }
}

fun shareContentItem(
    context: Context,
    item: ContentItemEntity
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, buildShareText(item))
    }

    val chooser = Intent.createChooser(shareIntent, "공유할 앱 선택").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
    }
}

private fun buildShareText(item: ContentItemEntity): String {
    return when (item.type) {
        ContentType.QUOTE -> buildQuoteShareText(item)
        ContentType.LINK -> buildLinkShareText(item)
        ContentType.VIDEO -> buildVideoShareText(item)
    }
}

private fun buildQuoteShareText(item: ContentItemEntity): String {
    val lines = buildList {
        add(item.title.ifBlank { "공유한 글귀" })
        if (item.body.isNotBlank()) {
            add("")
            add(item.body)
        }
        if (item.author.isNotBlank()) {
            add("")
            add("- ${item.author}")
        }
        if (item.sourceUrl.isNotBlank()) {
            add("")
            add(item.sourceUrl)
        }
    }
    return lines.joinToString("\n")
}

private fun buildLinkShareText(item: ContentItemEntity): String {
    val lines = buildList {
        add(item.title.ifBlank { "공유한 링크" })
        if (item.body.isNotBlank()) {
            add("")
            add(item.body)
        }
        if (item.sourceUrl.isNotBlank()) {
            add("")
            add(item.sourceUrl)
        }
    }
    return lines.joinToString("\n")
}

private fun buildVideoShareText(item: ContentItemEntity): String {
    val lines = buildList {
        add(item.title.ifBlank { "공유한 영상" })
        if (item.body.isNotBlank()) {
            add("")
            add(item.body)
        }
        if (item.sourceUrl.isNotBlank()) {
            add("")
            add(item.sourceUrl)
        }
    }
    return lines.joinToString("\n")
}
