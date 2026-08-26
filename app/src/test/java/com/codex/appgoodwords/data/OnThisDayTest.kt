package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 지난 이맘때 남긴 것을 찾아 줍니다.
 *
 * 오래 쓴 사람에게만 생기는 되돌림이라, 아무 날에나 걸리면 안 됩니다.
 */
class OnThisDayTest {
    private val today = LocalDate.of(2026, 8, 26)
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun lastYearsDiaryOnThisDateIsFound() {
        val memories = find(diaries = listOf(diary(id = 1, date = "2025-08-26", body = "작년 오늘")))

        assertEquals(1, memories.size)
        assertEquals(1, memories.single().yearsAgo)
        assertEquals("작년 오늘", memories.single().whenText)
        assertEquals(SearchKind.DIARY, memories.single().kind)
    }

    @Test
    fun otherDaysAreNotPulledIn() {
        // 앞뒤로 며칠을 넓히면 매일 무언가 걸려서 특별하지 않게 됩니다.
        val memories = find(
            diaries = listOf(
                diary(id = 1, date = "2025-08-25", body = "하루 전"),
                diary(id = 2, date = "2025-08-27", body = "하루 뒤"),
                diary(id = 3, date = "2025-07-26", body = "한 달 전")
            )
        )

        assertTrue(memories.isEmpty())
    }

    @Test
    fun todayItselfIsNotAMemory() {
        val memories = find(diaries = listOf(diary(id = 1, date = "2026-08-26", body = "오늘 쓴 것")))

        assertTrue("오늘 쓴 것이 추억으로 나왔습니다.", memories.isEmpty())
    }

    @Test
    fun aQuoteKeptOnThisDateCountsToo() {
        val memories = find(items = listOf(item(id = 7, createdAt = "2024-08-26")))

        assertEquals(SearchKind.QUOTE, memories.single().kind)
        assertEquals(2, memories.single().yearsAgo)
        assertEquals("2년 전 오늘", memories.single().whenText)
    }

    @Test
    fun theNearestYearComesFirstAndDiariesLeadWithinAYear() {
        val memories = find(
            diaries = listOf(diary(id = 1, date = "2025-08-26", body = "작년 일기")),
            items = listOf(
                item(id = 2, createdAt = "2025-08-26"),
                item(id = 3, createdAt = "2020-08-26")
            )
        )

        // 가까운 해가 먼저, 같은 해라면 그날 직접 쓴 일기가 먼저입니다.
        assertEquals(listOf(SearchKind.DIARY, SearchKind.QUOTE, SearchKind.QUOTE), memories.map { it.kind })
        assertEquals(listOf(1, 1, 6), memories.map { it.yearsAgo })
    }

    @Test
    fun onlyAHandfulIsShown() {
        val many = (1..8).map { diary(id = it.toLong(), date = "2025-08-26", body = "$it") }

        assertTrue(find(diaries = many).size <= OnThisDay.MAX_MEMORIES)
    }

    @Test
    fun tooLongAgoIsLeftAlone() {
        // 십 년보다 오래된 글까지 뒤질 일은 드뭅니다.
        val memories = find(diaries = listOf(diary(id = 1, date = "2010-08-26", body = "아주 오래전")))

        assertTrue(memories.isEmpty())
    }

    @Test
    fun aLeapDayIsNotDraggedToAnotherDate() {
        // 2월 28일로 당겨 붙이면 그날 남긴 다른 기록과 섞여, 없는 날의 기억이 튀어나온 것이 됩니다.
        val memories = OnThisDay.find(
            today = LocalDate.of(2026, 2, 28),
            diaries = listOf(diary(id = 1, date = "2024-02-29", body = "윤달")),
            items = emptyList(),
            zoneId = zone
        )

        assertTrue(memories.isEmpty())
    }

    private fun find(
        diaries: List<DiaryEntity> = emptyList(),
        items: List<ContentItemEntity> = emptyList()
    ) = OnThisDay.find(today = today, diaries = diaries, items = items, zoneId = zone)

    private fun diary(id: Long, date: String, body: String) = DiaryEntity(
        id = id,
        syncId = "diary-$id",
        updatedAt = 0L,
        entryDate = date,
        body = body,
        createdAt = 0L
    )

    private fun item(id: Long, createdAt: String) = ContentItemEntity(
        id = id,
        syncId = "item-$id",
        type = ContentType.QUOTE,
        title = "글귀 $id",
        body = "본문",
        createdAt = LocalDate.parse(createdAt).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    )
}
