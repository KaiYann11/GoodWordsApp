package com.codex.appgoodwords.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class LinkMetadata(
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String = ""
)

class LinkMetadataFetcher {
    suspend fun fetch(url: String): LinkMetadata = withContext(Dispatchers.IO) {
        val document = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 AppGoodWords/1.0")
            .timeout(10_000)
            .get()

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: document.title()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("meta[name=description]")?.attr("content")
            ?: ""
        val thumbnailUrl = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()

        LinkMetadata(
            title = title.orEmpty(),
            description = description,
            thumbnailUrl = thumbnailUrl
        )
    }
}

