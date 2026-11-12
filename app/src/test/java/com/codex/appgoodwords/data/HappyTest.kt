package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 보관함 "행복" 칸 — 행복한 순간과 좋아하는 것(사용자가 정함, 2026-11-12, 목업으로 고름).
 *
 * 날짜를 고른 순간이 그날로 적히는지, 글에 나온 좋아하는 것을 미리 골라 두는 규칙, 갈래 차례, 좋아하는 것마다 순간 수를
 * 셉니다. 그리고 **AI에 새로 나가는 것이 없는지**(내가 쓴 것이라 추천 원문에도 안 감)를 못 박습니다.
 */
class HappyTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDate.of(2026, 11, 12).atTime(21, 30).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun theTitleIsTheFirstLineCut() {
        assertEquals("퇴근길 노을", Happy.titleOf("\n  퇴근길 노을  \n하늘이 분홍색이었다"))
        assertEquals(Happy.TITLE_LENGTH, Happy.titleOf("가".repeat(100)).length)
        assertEquals("", Happy.titleOf("   "))
    }

    @Test
    fun aMomentIsWrittenOnTheDayPicked() {
        // 오늘이면 지금입니다.
        assertEquals(now, Happy.momentTime(LocalDate.of(2026, 11, 12), previous = null, now = now, zoneId = zone))
        // 지난날이면 그날의 지금 시각입니다 — 몇 시였는지는 묻지 않습니다.
        val yesterday = Happy.momentTime(LocalDate.of(2026, 11, 11), previous = null, now = now, zoneId = zone)
        assertEquals(LocalDate.of(2026, 11, 11).atTime(21, 30).atZone(zone).toInstant().toEpochMilli(), yesterday)
        // 오지 않은 날은 지금으로 둡니다.
        assertEquals(now, Happy.momentTime(LocalDate.of(2026, 11, 20), previous = null, now = now, zoneId = zone))
    }

    @Test
    fun editingWithoutChangingTheDayKeepsTheTime() {
        val firstWritten = LocalDate.of(2026, 11, 9).atTime(8, 15).atZone(zone).toInstant().toEpochMilli()

        assertEquals(firstWritten, Happy.momentTime(LocalDate.of(2026, 11, 9), previous = firstWritten, now = now, zoneId = zone))
        assertEquals(LocalDate.of(2026, 11, 9), Happy.dateOf(firstWritten, zone))
    }

    @Test
    fun likesNamedInTheTextArePickedIgnoringSpacesAndCase() {
        val sunset = like("l-1", "노을")
        val coffee = like("l-2", "Coffee")
        val book = like("l-3", "책")

        val named = Happy.likesNamedIn("퇴근길 노 을이 예뻤고 coffee 한 잔, 책도 읽었다", listOf(sunset, coffee, book))

        // 한 글자 이름("책")은 아무 글에나 걸려 보지 않습니다.
        assertEquals(listOf("l-1", "l-2"), named.map { it.syncId })
    }

    @Test
    fun theSnippetShowsWhereTheNameAppeared() {
        val text = "오늘은 아침부터 비가 왔는데 저녁에 개어서 퇴근길에 노을이 너무 예뻤다. 사진도 찍었다."
        val snippet = Mentions.snippet(text, "노을")

        assertTrue(snippet, snippet.contains("노을이 너무"))
        assertTrue(snippet, snippet.startsWith("…"))
        assertFalse(Mentions.contains(text, "무지개"))
    }

    @Test
    fun likesAreGroupedInChipOrderWithNoCategoryLast() {
        val groups = Happy.groupLikes(
            listOf(
                like("a", "떡볶이", "음식"),
                like("b", "노을", "장소"),
                like("c", "가을", "계절"),
                like("d", "잠"),
                like("e", "김밥", "음식")
            )
        )

        assertEquals(listOf("음식", "장소", "계절", ""), groups.map { it.first })
        assertEquals(listOf("김밥", "떡볶이"), groups.first().second.map { it.title })
    }

    @Test
    fun aLikeCountsMomentsLinkedOrNamingIt() {
        val linked = moment("m-1", "친구랑 늦게까지 수다")
        val naming = moment("m-2", "한강에서 노을 보며 산책")
        val other = moment("m-3", "따뜻한 커피")
        val sunset = like("l-1", "노을")
        val links = listOf(link("x", from = "l-1", to = "m-1"), link("y", from = "m-2", to = "l-1"))
        val items = listOf(linked, naming, other, sunset)

        val counts = Happy.momentCounts(items, RecordIndex(items = items), links)

        // 이어 둔 m-1, 이어 두었고 글에도 이름이 나온 m-2(한 번만), 둘 다 아닌 m-3은 세지 않습니다.
        assertEquals(mapOf("l-1" to 2), counts)
        assertEquals(listOf("l-1"), Happy.likesByMoment(items, links).getValue("m-1").map { it.syncId })
        assertTrue(Happy.likesByMoment(items, links).getValue("m-3").isEmpty())
    }

    @Test
    fun theSameTextOnAnotherDayIsAnotherMoment() {
        // 동기화의 같은 내용 합치기는 순간의 때까지 봅니다(서버 deduplicate와 같게). 안 보면 다른 날의 순간이 하나로 사라집니다.
        val monday = moment("monday", "가족과 저녁")
        val friday = moment("friday", "가족과 저녁").copy(createdAt = now + 4 * 86_400_000L)
        val fridayCopy = friday.copy(id = 77, syncId = "friday-copy", updatedAt = now + 1)

        val merged = SyncDeduplicator.deduplicate(
            AppDataSnapshot(
                items = listOf(monday, friday, fridayCopy),
                events = emptyList(),
                routines = emptyList(),
                routineChecks = emptyList(),
                routineMemos = emptyList(),
                settings = ReminderSettings()
            )
        )

        // 때까지 같은 사본만 하나가 됩니다(나중에 고친 쪽이 남습니다).
        assertEquals(listOf("friday-copy", "monday"), merged.items.map { it.syncId }.sorted())
    }

    @Test
    fun happyRecordsAreMineAndNeverGoToTheAi() {
        assertTrue(ContentType.HAPPY_MOMENT.isPersonalRecord)
        assertTrue(ContentType.LIKE.isPersonalRecord)
        assertFalse(ContentType.HAPPY_MOMENT.isReadingContent)

        val quote = ContentItemEntity(id = 9, syncId = "q", type = ContentType.QUOTE, title = "가장 어려운 문제로 달려가라", body = "")
        val prompt = GrowthPrompt.userPrompt(
            GrowthPrompt.digest(
                period = ReportPeriod.WEEKLY,
                today = LocalDate.of(2026, 11, 12),
                routines = emptyList(),
                routineChecks = emptyList(),
                todos = emptyList(),
                diaries = emptyList(),
                items = listOf(quote, moment("m-1", "퇴근길 노을이 너무 예뻤다"), like("l-1", "떡볶이")),
                events = emptyList(),
                books = emptyList(),
                moodLogs = emptyList(),
                // 일기·목표를 다 켜도 행복 칸은 나가지 않습니다.
                includeDiaryBody = true,
                previousReport = null,
                includeGoals = true,
                zoneId = zone
            )
        )

        assertTrue("이미 담은 글귀는 추천 제외 목록으로 나갑니다.", prompt.contains("가장 어려운 문제로 달려가라"))
        assertFalse(prompt.contains("노을"))
        assertFalse(prompt.contains("떡볶이"))
    }

    private fun moment(syncId: String, text: String) = ContentItemEntity(
        id = syncId.hashCode().toLong(),
        syncId = syncId,
        type = ContentType.HAPPY_MOMENT,
        title = Happy.titleOf(text),
        body = text,
        createdAt = now,
        updatedAt = now
    )

    private fun like(syncId: String, name: String, category: String = "") = ContentItemEntity(
        id = syncId.hashCode().toLong(),
        syncId = syncId,
        type = ContentType.LIKE,
        title = name,
        body = "",
        category = category,
        createdAt = now,
        updatedAt = now
    )

    private fun link(syncId: String, from: String, to: String) = RecordLinkEntity(
        syncId = syncId,
        fromKind = RecordKind.CONTENT_ITEM.name,
        fromSyncId = from,
        toKind = RecordKind.CONTENT_ITEM.name,
        toSyncId = to
    )
}
