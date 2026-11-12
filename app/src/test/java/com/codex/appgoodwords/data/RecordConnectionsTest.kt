package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 이어진 기록(사용자가 정함, 2026-11-12 — 직접 이은 것 + 저절로 보이는 연결)을 셉니다.
 *
 * 직접 이은 것은 지운 기록을 화면에서만 빼고, 이름이 나온 곳은 좋아하는 것·롤모델·책·루틴의 이름만 보며, 같은 기록이
 * 두 번 보이지 않고, 잠긴 일기는 가립니다.
 */
class RecordConnectionsTest {
    private val sunset = item("like-1", ContentType.LIKE, "노을")
    private val moment = item("moment-1", ContentType.HAPPY_MOMENT, "사피엔스 다 읽고 노을 보며 산책")
    private val book = BookEntity(id = 7, syncId = "book-1", title = "사피엔스")
    private val diary = DiaryEntity(id = 3, syncId = "diary-1", entryDate = "2026-11-10", body = "회사 앞에서 노을을 봤다. 괜히 좋았다.")
    private val routine = RoutineEntity(id = 4, syncId = "routine-1", title = "저녁 산책")

    @Test
    fun aMomentShowsWhatItLinksAndTheNamesInItsText() {
        val index = RecordIndex(items = listOf(sunset, moment), books = listOf(book), diaries = listOf(diary), routines = listOf(routine))
        val links = listOf(link("a", RecordRef.of(moment), RecordRef.of(sunset)))

        val connections = index.connectionsOf(RecordRef.of(moment), links)

        assertEquals(listOf("노을"), connections.direct.map { it.title })
        // 책 이름이 글에 나왔습니다. 이어 둔 노을은 이름이 나와도 한 번만 보입니다. "저녁 산책"은 글에 그대로 나오지 않았습니다.
        assertEquals(listOf("book-1"), connections.mentions.map { it.ref.syncId })
        assertEquals("책", connections.mentions.single().label)
    }

    @Test
    fun aLikeShowsTheRecordsThatNameIt() {
        val index = RecordIndex(items = listOf(sunset, moment), diaries = listOf(diary))

        val connections = index.connectionsOf(RecordRef.of(sunset), links = emptyList())

        // 이어 두지 않았어도 이름이 나온 순간과 일기가 보입니다. 최근 기록이 먼저입니다(순간이 일기보다 나중).
        assertEquals(listOf("moment-1", "diary-1"), connections.mentions.map { it.ref.syncId })
        assertTrue(connections.mentions.last().snippet.contains("노을을 봤다"))
        assertEquals(listOf("moment-1"), connections.happyMoments.map { it.ref.syncId })
    }

    @Test
    fun aMomentIsQuotedOnceWhereItNamesALike() {
        // 순간의 이름은 글의 첫 줄입니다. 이름과 글을 이어 붙이면 조각에 그 줄이 두 번 보입니다(에뮬레이터에서 본 것, 2026-11-13).
        val index = RecordIndex(items = listOf(sunset, moment))

        val mention = index.connectionsOf(RecordRef.of(sunset), links = emptyList()).mentions.single()

        assertEquals("사피엔스 다 읽고 노을 보며 산책", mention.snippet)
    }

    @Test
    fun aLinkToARecordThatIsGoneIsLeftOutOfTheScreenOnly() {
        val index = RecordIndex(items = listOf(sunset))
        val gone = RecordRef(RecordKind.DIARY.name, "deleted-diary")

        val connections = index.connectionsOf(RecordRef.of(sunset), listOf(link("a", RecordRef.of(sunset), gone)))

        assertTrue(connections.isEmpty)
        assertNull(index.resolve(gone))
    }

    @Test
    fun aLockedDiaryIsMasked() {
        val index = RecordIndex(items = listOf(sunset), diaries = listOf(diary), diaryLocked = true)

        val mention = index.connectionsOf(RecordRef.of(sunset), emptyList()).mentions.single()

        assertEquals("잠긴 일기", mention.title)
        assertEquals("", mention.snippet)
    }

    @Test
    fun aSearchHitPointsAtTheRecordAndUnfinishedWalksAreNotRecordsYet() {
        val finished = WalkEntity(id = 1, syncId = "walk-1", startedAt = 1_000_000L, endedAt = 1_600_000L, distanceMeters = 830)
        val walking = WalkEntity(id = 2, syncId = "walk-2", startedAt = 2_000_000L, endedAt = 0L)
        val index = RecordIndex(items = listOf(sunset), books = listOf(book), walks = listOf(finished, walking))

        assertEquals(RecordRef.of(book), index.refOf(SearchHit(SearchKind.BOOK, 7, "사피엔스", "", "")))
        assertEquals(RecordRef.of(sunset), index.refOf(SearchHit(SearchKind.LIKE, sunset.id, "노을", "", "")))
        assertNull(index.refOf(SearchHit(SearchKind.GROWTH, 1, "돌아보기", "", "")))
        assertEquals("걷기", index.resolve(RecordRef.of(finished))?.label)
        assertNull(index.resolve(RecordRef.of(walking)))
    }

    private fun item(syncId: String, type: ContentType, text: String) = ContentItemEntity(
        id = syncId.hashCode().toLong(),
        syncId = syncId,
        type = type,
        title = Happy.titleOf(text),
        body = if (type == ContentType.LIKE) "" else text,
        // 2026-11-11 낮 — 일기(11월 10일)보다 나중에 적힌 것으로 둡니다.
        createdAt = 1_794_398_400_000L,
        updatedAt = 1_794_398_400_000L
    )

    private fun link(syncId: String, from: RecordRef, to: RecordRef) = RecordLinkEntity(
        syncId = syncId,
        fromKind = from.kind,
        fromSyncId = from.syncId,
        toKind = to.kind,
        toSyncId = to.syncId
    )
}
