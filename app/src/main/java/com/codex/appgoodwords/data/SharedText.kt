package com.codex.appgoodwords.data

/**
 * 다른 앱에서 공유해 온 글을 보관함에 담을 꼴로 나눕니다.
 *
 * 공유는 앱마다 보내는 모양이 다릅니다. 유튜브는 대개 **제목 한 줄과 주소**를 함께 보내고,
 * 브라우저는 주소만, 메모 앱은 글만 보냅니다. 그래서 "http로 시작하면 주소"라고만 보면
 * 유튜브에서 온 것은 제목이 앞에 붙어 있어 **주소가 아닌 것으로 읽혀 본문에 통째로 박힙니다.**
 * 그러면 종류를 알아내지 못해 영상이 아니라 글귀가 되고, 썸네일도 안 뜹니다.
 *
 * 여기서는 글 어디에 있든 주소를 찾아내고, 남은 글을 제목으로 씁니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object SharedText {
    /**
     * 공유해 온 글을 초안으로 바꿉니다. 담을 것이 없으면 null입니다.
     *
     * [subject]는 `Intent.EXTRA_SUBJECT`입니다. 본문에 제목이 없을 때만 씁니다.
     */
    fun toDraft(text: String?, subject: String? = null): ContentDraft? {
        val raw = text.orEmpty().trim()
        val subjectText = subject.orEmpty().trim()
        if (raw.isBlank() && subjectText.isBlank()) return null

        val url = firstUrl(raw) ?: firstUrl(subjectText).orEmpty()
        // 주소를 들어낸 나머지가 사람이 읽을 글입니다.
        // **주소가 없을 때 replace를 부르면 안 됩니다.** 빈 문자열을 바꾸라고 하면 글자 사이마다
        // 공백이 끼어 "행 동 은"이 됩니다.
        val withoutUrl = if (url.isBlank()) raw else raw.replace(url, " ")
        val rest = withoutUrl.lines()
            // 주소를 들어낸 자리에 공백이 겹쳐 남습니다.
            .map { it.trim().replace(REPEATED_SPACES, " ") }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .ifBlank { subjectText }

        // 주소도 글도 없으면 담아도 빈 카드만 남습니다.
        if (url.isBlank() && rest.isBlank()) return null

        if (url.isBlank()) {
            // 주소가 없으면 **글 자체가 알맹이**라 본문에 담아야 합니다. 제목만 채우면
            // "본문·링크·사진·영상 중 하나는 있어야 한다"는 규칙에 걸려 담기지 않습니다.
            // 제목은 담을 때 본문에서 끌어옵니다(ContentNormalizer) — 직접 적어 담는 것과 같은 모양입니다.
            return ContentDraft(body = rest)
        }

        val firstLine = rest.lineSequence().firstOrNull().orEmpty().trim()
        val title = firstLine.take(TITLE_LIMIT).trim()
        return ContentDraft(
            title = title,
            // 제목으로 다 담긴 글을 본문에 또 넣지 않습니다. 목록과 상세에 같은 말이 두 번 보입니다.
            body = if (rest == title) "" else rest,
            sourceUrl = url
        )
    }

    /**
     * 글 안의 첫 주소.
     *
     * 유튜브는 주소 뒤에 `?si=...`를 붙여 보내므로 공백까지를 한 덩어리로 봅니다.
     * 문장 끝의 마침표나 괄호는 주소가 아니라서 떼어 냅니다.
     */
    private fun firstUrl(text: String): String? {
        val found = URL_PATTERN.find(text)?.value ?: return null
        return found.trimEnd(*TRAILING.toCharArray()).takeIf { it.isNotBlank() }
    }

    private val URL_PATTERN = Regex("""https?://\S+""")

    private val REPEATED_SPACES = Regex("""[ \t]{2,}""")

    /** 주소 뒤에 붙어 오는 문장 부호. 남겨 두면 그 주소로는 열리지 않습니다. */
    private const val TRAILING = ".,)]}\"'>"

    /** 목록에서 잘리지 않을 만큼만. 나머지는 본문에 남습니다. */
    private const val TITLE_LIMIT = 60
}
