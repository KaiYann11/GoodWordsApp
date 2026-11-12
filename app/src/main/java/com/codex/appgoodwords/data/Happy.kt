package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 보관함 "행복" 칸 — 행복한 순간과 좋아하는 것(사용자가 정함, 2026-11-12, 목업으로 고름).
 *
 * 순간은 글·날짜·사진을 적고 그때 있던 좋아하는 것을 칩으로 이어 둡니다. 좋아하는 것은 이름·갈래·왜 좋은지·사진입니다.
 * 내가 쓴 것이라 알림·위젯·읽음·AI 추천 원문에 나오지 않습니다([ContentType.personalTypes]). 순수 함수라 기기 없이 시험합니다.
 */
object Happy {
    /** 좋아하는 것의 갈래 칩. 마지막 "직접"은 화면이 따로 둡니다(여기 없는 이름을 적는 칸). */
    val LIKE_CATEGORIES = listOf("음식", "음악", "장소", "사람", "활동", "물건", "작품")

    /** 목록·검색·이어진 기록에 보이는 순간의 이름. 글의 첫 줄을 이만큼 자릅니다. */
    const val TITLE_LENGTH = 40

    /** 글에서 순간의 이름을 뽑습니다 — 첫 줄을 [TITLE_LENGTH]자까지. 메모처럼 따로 적는 제목 칸이 없습니다. */
    fun titleOf(text: String): String =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(TITLE_LENGTH)

    /** 그 순간의 날짜. 순간은 [ContentItemEntity.createdAt]이 그때입니다. */
    fun dateOf(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()

    /**
     * 고른 날짜로 적을 때의 시각.
     *
     * 고치면서 날짜를 그대로 두었으면 처음 적은 시각을 지킵니다(같은 날 순간들의 차례가 바뀌지 않게). 오늘이면 지금이고,
     * 지난날이면 그날의 지금 시각입니다 — 몇 시였는지는 묻지 않습니다. 오지 않은 날은 지금으로 둡니다.
     */
    fun momentTime(
        date: LocalDate,
        previous: Long?,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        if (previous != null && dateOf(previous, zoneId) == date) return previous
        val current = Instant.ofEpochMilli(now).atZone(zoneId)
        if (!date.isBefore(current.toLocalDate())) return now
        return date.atTime(current.toLocalTime()).atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * 순간의 글에 이름이 나온 좋아하는 것. 적는 창이 이것을 미리 골라 둡니다(사용자가 정함 — "글에 나온 이름은 골라 둠").
     * 이름을 견주는 규칙은 이어진 기록의 "이름이 나온 곳"과 같습니다([Mentions]).
     */
    fun likesNamedIn(text: String, likes: List<ContentItemEntity>): List<ContentItemEntity> =
        likes.filter { it.type == ContentType.LIKE && Mentions.contains(text, it.title) }

    /** 좋아하는 것 이름이 같은지(띄어쓰기·대소문자는 상관없이). 같은 이름을 두 번 만들지 않게 씁니다. */
    fun sameName(a: String, b: String): Boolean = Mentions.key(a).isNotEmpty() && Mentions.key(a) == Mentions.key(b)

    /**
     * 갈래별로 묶은 좋아하는 것. 정해 둔 갈래는 칩 차례, 직접 적은 갈래는 가나다, 갈래 없음(빈 이름)은 맨 뒤입니다.
     * 갈래 안에서는 이름 차례입니다.
     */
    fun groupLikes(likes: List<ContentItemEntity>): List<Pair<String, List<ContentItemEntity>>> {
        val groups = likes.filter { it.type == ContentType.LIKE }.groupBy { it.category.trim() }
        val order = compareBy<String>(
            { it.isEmpty() },
            { LIKE_CATEGORIES.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } },
            { it }
        )
        return groups.keys.sortedWith(order).map { key -> key to groups.getValue(key).sortedBy { it.title } }
    }

    /** 순간마다 이어 둔 좋아하는 것 — 카드의 "♡ 노을 · 산책". 직접 이은 것만 봅니다(글에 나온 이름은 상세에서). */
    fun likesByMoment(items: List<ContentItemEntity>, links: List<RecordLinkEntity>): Map<String, List<ContentItemEntity>> {
        val likes = items.filter { it.type == ContentType.LIKE }.associateBy { RecordRef.of(it) }
        return items.filter { it.type == ContentType.HAPPY_MOMENT }.associate { moment ->
            moment.syncId to RecordLinks.linkedTo(links, RecordRef.of(moment)).mapNotNull(likes::get)
        }
    }

    /** 좋아하는 것마다 있었던 행복한 순간 수 — 직접 이었거나 순간의 글에 이름이 나온 것([Connections.happyMoments]). */
    fun momentCounts(items: List<ContentItemEntity>, index: RecordIndex, links: List<RecordLinkEntity>): Map<String, Int> =
        items.filter { it.type == ContentType.LIKE }.associate { like ->
            like.syncId to index.connectionsOf(RecordRef.of(like), links).happyMoments.size
        }
}

/**
 * 글에 어떤 이름이 나왔는지 봅니다. 띄어쓰기·대소문자는 상관없이 견주고, 두 글자보다 짧은 이름은 보지 않습니다 —
 * 한 글자 이름("책")은 아무 글에나 걸려 쓸모가 없습니다. 한국어는 조사가 붙으므로("노을이") 낱말이 아니라 글자로 찾습니다.
 */
object Mentions {
    const val MIN_NAME_LENGTH = 2

    /** 견주는 꼴 — 띄어쓰기를 지우고 소문자로. */
    fun key(value: String): String = value.filterNot(Char::isWhitespace).lowercase()

    fun contains(text: String, name: String): Boolean {
        val needle = key(name)
        if (needle.length < MIN_NAME_LENGTH) return false
        return key(text).contains(needle)
    }

    /**
     * 이름이 나온 자리 둘레의 글. 띄어쓰기를 한 칸으로 줄인 글에서 찾고, 띄어쓰기가 달라 못 찾으면 글의 앞머리를 씁니다.
     */
    fun snippet(text: String, name: String, radius: Int = SNIPPET_RADIUS): String {
        val flat = text.replace(WHITESPACE, " ").trim()
        val at = flat.lowercase().indexOf(name.replace(WHITESPACE, " ").trim().lowercase())
        if (at < 0) return flat.take(radius * 2).let { if (flat.length > it.length) "$it…" else it }
        val start = (at - radius).coerceAtLeast(0)
        val end = (at + name.trim().length + radius).coerceAtMost(flat.length)
        return buildString {
            if (start > 0) append("…")
            append(flat.substring(start, end))
            if (end < flat.length) append("…")
        }
    }

    private const val SNIPPET_RADIUS = 20
    private val WHITESPACE = Regex("\\s+")
}
