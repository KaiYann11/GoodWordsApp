package com.codex.appgoodwords.data

import android.net.Uri

/**
 * 담기 전에 초안을 다듬고, 담을 만한 것인지 봅니다.
 *
 * 담는 화면(`MainViewModel.saveContent`)과 다른 앱에서 공유해 온 것(`ShareTargetActivity`)이
 * **같은 규칙을 써야 합니다.** 한쪽만 고치면 같은 유튜브 링크가 담는 길에 따라 영상이 되기도
 * 하고 그냥 링크가 되기도 합니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다(주소 읽기만 Android에 기댑니다).
 */
object ContentNormalizer {
    fun normalize(draft: ContentDraft): ContentDraft {
        val generatedTitle = when {
            draft.title.isNotBlank() -> draft.title.trim()
            draft.body.isNotBlank() -> draft.body.trim().take(TITLE_LIMIT)
            draft.sourceUrl.isNotBlank() -> draft.sourceUrl.trim()
            draft.imageUris.isNotEmpty() -> displayNameFromUri(draft.imageUris.first())
            draft.videoUris.isNotEmpty() -> displayNameFromUri(draft.videoUris.first())
            else -> ""
        }

        return draft.copy(
            type = detectType(draft),
            title = generatedTitle,
            body = draft.body.trim(),
            author = draft.author.trim(),
            sourceUrl = draft.sourceUrl.trim(),
            thumbnailUrl = draft.thumbnailUrl.trim(),
            category = draft.category.trim(),
            tags = draft.tags.map(String::trim).filter(String::isNotBlank),
            imageUris = draft.imageUris.map(String::trim).filter(String::isNotBlank).distinct(),
            videoUris = draft.videoUris.map(String::trim).filter(String::isNotBlank).distinct()
        )
    }

    /** 담을 것이 하나도 없으면 알려 줍니다. */
    fun validate(draft: ContentDraft) {
        require(
            draft.body.isNotBlank() ||
                draft.sourceUrl.isNotBlank() ||
                draft.imageUris.isNotEmpty() ||
                draft.videoUris.isNotEmpty()
        ) { "본문, 링크, 사진, 영상 중 하나는 넣어야 합니다." }
    }

    /**
     * 주소를 보고 종류를 정합니다.
     *
     * **번뜩인 것은 겉모습으로 알 수 없습니다.** 담는 사람이 짚어 준 것을 덮어쓰지 않습니다.
     */
    fun detectType(draft: ContentDraft): ContentType {
        if (draft.type == ContentType.IDEA) return ContentType.IDEA
        val url = draft.sourceUrl.trim().lowercase()
        return when {
            url.contains("youtube.com") ||
                url.contains("youtu.be") ||
                url.contains("vimeo.com") ||
                url.contains("tiktok.com") -> ContentType.VIDEO
            url.isNotBlank() -> ContentType.LINK
            else -> ContentType.QUOTE
        }
    }

    private fun displayNameFromUri(uriString: String): String {
        val lastSegment = Uri.parse(uriString).lastPathSegment.orEmpty()
        return lastSegment.substringAfterLast('/').substringAfterLast(':').ifBlank { "새 게시글" }
    }

    /** 목록에서 잘리지 않을 만큼만. 본문에서 제목을 끌어올 때 씁니다. */
    private const val TITLE_LIMIT = 24
}
