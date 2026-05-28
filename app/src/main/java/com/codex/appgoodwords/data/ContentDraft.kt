package com.codex.appgoodwords.data

data class ContentDraft(
    val id: Long = 0,
    val type: ContentType = ContentType.QUOTE,
    val title: String = "",
    val body: String = "",
    val author: String = "",
    val sourceUrl: String = "",
    val thumbnailUrl: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val imageUris: List<String> = emptyList(),
    val videoUris: List<String> = emptyList(),
    val isFavorite: Boolean = false
) {
    companion object {
        fun fromItem(item: ContentItemEntity): ContentDraft = ContentDraft(
            id = item.id,
            type = item.type,
            title = item.title,
            body = item.body,
            author = item.author,
            sourceUrl = item.sourceUrl,
            thumbnailUrl = item.thumbnailUrl,
            category = item.category,
            tags = item.tags,
            imageUris = item.imageUris,
            videoUris = item.videoUris,
            isFavorite = item.isFavorite
        )
    }
}
