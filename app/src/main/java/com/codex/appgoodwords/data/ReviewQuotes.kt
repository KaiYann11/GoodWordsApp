package com.codex.appgoodwords.data

/** 독후감의 한 문장. [start]·[end]는 독후감 글 안의 자리이고, [page]는 바로 위 진도 머리 줄(`11월 9일 · 120쪽`)의 쪽입니다. */
data class ReviewSentence(
    val index: Int,
    val text: String,
    val start: Int,
    val end: Int,
    val page: Int?
)

/** 글귀 고르기 화면에 늘어놓는 조각 — 진도 머리 줄, 문장, 문단 사이 빈 줄. */
sealed interface ReviewPiece {
    data class Header(val text: String, val page: Int?) : ReviewPiece
    data class Sentence(val sentence: ReviewSentence) : ReviewPiece
    data object Gap : ReviewPiece
}

/** 따옴표 속 문장이나 `>`로 시작한 줄 — 누르면 바로 글귀 창으로 가는 후보입니다. */
data class QuoteCandidate(val text: String, val page: Int?)

/**
 * 독후감에서 글귀를 쉽게 뽑기(사용자가 고름, 2026-11-28).
 *
 * 전에는 글 칸에서 손잡이를 끌어 고른 뒤에야 글귀로 뽑을 수 있었습니다. 휴대폰에서는 손가락으로 한 글자씩 맞추기가 어렵습니다.
 * - **문장 탭으로 고르기**: 독후감을 문장으로 나눠([pieces]) 손가락으로 톡톡 고르면, 고른 문장을 쓴 그대로 이어([quoteText])
 *   글귀 창에 넣습니다. 쪽은 그 문장 위의 진도 머리 줄에서 가져옵니다(`BookNotes.entry`가 쓰는 꼴).
 * - **따옴표 문장 자동 후보**: “…”·"…"·「…」·『…』 안의 문장과 `>`로 시작한 줄을 후보로 늘어놓습니다([candidates]).
 *
 * 문장은 마침표·물음표·느낌표·말줄임에서 끊되, 뒤에 붙은 닫는 따옴표·괄호는 그 문장에 남깁니다. 띄어쓰기 없이 이어진
 * 마침표(3.5, "..."의 가운데)에서는 끊지 않습니다. 줄이 바뀌면 문장도 끝납니다 — 마침표 없이 적은 메모 한 줄이 한 문장입니다.
 */
object ReviewQuotes {
    /** `BookNotes.entry`의 머리 줄. 쪽을 모르면 쪽이 빠집니다. */
    private val header = Regex("^(\\d{1,2})월 (\\d{1,2})일(?: · (\\d+)쪽)?$")

    private val sentenceEnd = Regex("[.?!…。][\"”’'」』)\\]]*(?=\\s|$)")

    private val quoted = listOf(
        Regex("“([^”\\n]+)”"),
        Regex("\"([^\"\\n]+)\""),
        Regex("「([^」\\n]+)」"),
        Regex("『([^』\\n]+)』")
    )
    private val quotedLine = Regex("^\\s*>\\s?(.+)$")
    private val quoteMarker = Regex("^\\s*>\\s?")

    /** 너무 짧은 따옴표 속 말("좋다")은 글귀 후보로 두지 않습니다. */
    const val MIN_CANDIDATE_LENGTH = 6

    fun pieces(review: String): List<ReviewPiece> {
        val out = mutableListOf<ReviewPiece>()
        var page: Int? = null
        var index = 0
        var offset = 0
        var afterGap = true
        for (rawLine in review.split("\n")) {
            val lineStart = offset
            offset += rawLine.length + 1
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                if (!afterGap) {
                    out += ReviewPiece.Gap
                    afterGap = true
                }
                continue
            }
            afterGap = false
            val headerMatch = header.matchEntire(line.trim())
            if (headerMatch != null) {
                page = headerMatch.groupValues[3].toIntOrNull()
                out += ReviewPiece.Header(line.trim(), page)
                continue
            }
            // `>`로 시작한 줄(옮겨 적은 글귀)은 표시를 떼고 문장만 고릅니다.
            val marker = quoteMarker.find(line)?.value?.length ?: 0
            val ends = sentenceEnd.findAll(line).map { it.range.last + 1 }.filter { it > marker }.toMutableList()
            if (ends.isEmpty() || ends.last() < line.length) ends += line.length
            var cursor = marker
            for (end in ends) {
                val chunk = line.substring(cursor, end)
                val text = chunk.trim()
                if (text.isNotEmpty()) {
                    val start = lineStart + cursor + chunk.indexOfFirst { !it.isWhitespace() }
                    out += ReviewPiece.Sentence(ReviewSentence(index++, text, start, start + text.length, page))
                }
                cursor = end
            }
        }
        // 끝의 빈 줄은 남기지 않습니다.
        while (out.lastOrNull() == ReviewPiece.Gap) out.removeAt(out.lastIndex)
        return out
    }

    fun sentences(review: String): List<ReviewSentence> =
        pieces(review).filterIsInstance<ReviewPiece.Sentence>().map { it.sentence }

    /**
     * 고른 문장을 글귀로 잇습니다. 바로 이어진 문장은 쓴 그대로(같은 줄이면 한 칸, 줄이 바뀌었으면 줄바꿈), 사이를 건너뛴
     * 문장끼리는 줄을 바꿉니다. 진도 머리 줄은 넣지 않습니다.
     */
    fun quoteText(review: String, sentences: List<ReviewSentence>, selected: Set<Int>): String {
        val chosen = sentences.filter { it.index in selected }.sortedBy { it.index }
        if (chosen.isEmpty()) return ""
        val text = StringBuilder(chosen.first().text)
        chosen.zipWithNext().forEach { (before, after) ->
            val between = review.substring(before.end, after.start.coerceAtLeast(before.end))
            val joiner = if (after.index == before.index + 1 && !between.contains('\n')) " " else "\n"
            text.append(joiner).append(after.text)
        }
        return text.toString()
    }

    /** 고른 문장 가운데 첫 문장의 쪽. 진도 머리 줄 아래가 아니면 null입니다(창이 지금 진도를 넣습니다). */
    fun pageOf(sentences: List<ReviewSentence>, selected: Set<Int>): Int? =
        sentences.filter { it.index in selected }.minByOrNull { it.index }?.page

    /** 따옴표 속 문장과 `>` 줄. 쓴 차례대로, 같은 글은 한 번만. */
    fun candidates(review: String): List<QuoteCandidate> {
        val found = mutableListOf<Pair<Int, String>>()
        quoted.forEach { pattern ->
            pattern.findAll(review).forEach { match -> found += match.range.first to match.groupValues[1].trim() }
        }
        var offset = 0
        for (rawLine in review.split("\n")) {
            quotedLine.matchEntire(rawLine.trimEnd())?.let { match -> found += offset to match.groupValues[1].trim() }
            offset += rawLine.length + 1
        }
        val seen = HashSet<String>()
        return found
            .filter { (_, text) -> text.length >= MIN_CANDIDATE_LENGTH }
            .sortedBy { it.first }
            .filter { (_, text) -> seen.add(normalized(text)) }
            .map { (position, text) -> QuoteCandidate(text, pageAt(review, position)) }
    }

    /** 이미 뽑은 글귀와 견줄 때의 글 — 따옴표를 떼고 띄어쓰기를 한 칸으로 줄입니다. */
    fun normalized(text: String): String =
        text.replace(Regex("[“”\"「」『』]"), "").replace(Regex("\\s+"), " ").trim()

    /** [position] 앞에서 가장 가까운 진도 머리 줄의 쪽. */
    private fun pageAt(review: String, position: Int): Int? {
        var page: Int? = null
        var offset = 0
        for (rawLine in review.split("\n")) {
            if (offset > position) break
            header.matchEntire(rawLine.trim())?.let { page = it.groupValues[3].toIntOrNull() }
            offset += rawLine.length + 1
        }
        return page
    }
}
