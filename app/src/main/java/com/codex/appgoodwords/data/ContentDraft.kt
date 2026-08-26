package com.codex.appgoodwords.data

data class ContentDraft(
    val id: Long = 0,
    /**
     * 담는 쪽이 정해 준 종류.
     *
     * 보통은 비워 두고 주소를 보고 알아서 정합니다(`detectContentType`). 다만 번뜩인 것은
     * 겉모습으로 알 수 없어서 담는 사람이 짚어 줘야 합니다.
     */
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
