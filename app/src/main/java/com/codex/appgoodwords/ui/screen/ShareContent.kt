package com.codex.appgoodwords.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType

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
