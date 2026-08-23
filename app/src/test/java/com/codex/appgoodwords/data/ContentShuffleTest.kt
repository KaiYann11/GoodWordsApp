package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈에서 글귀를 늘어놓는 차례입니다.
 *
 * 켤 때마다 달라져야 하지만, 보는 동안에는 발밑에서 움직이면 안 됩니다.
 * 그 두 가지를 함께 지키는지 봅니다.
 */
class ContentShuffleTest {
    private val items = (1..40).map { index ->
        item(id = index.toLong(), syncId = "item-$index")
    }

    @Test
    fun aSeedOfZeroKeepsTheOrderTheyCameIn() {
        assertEquals(items, ContentShuffle.ordered(items, seed = 0L))
    }

    @Test
    fun differentSeedsGiveDifferentOrders() {
        val first = ContentShuffle.ordered(items, seed = 1L).map { it.id }
        val second = ContentShuffle.ordered(items, seed = 2L).map { it.id }

        assertNotEquals(first, second)
        // 섞였을 뿐 사라지거나 겹치면 안 됩니다.
        assertEquals(items.map { it.id }.sorted(), first.sorted())
        assertEquals(items.size, first.toSet().size)
    }

    @Test
    fun theSameSeedGivesTheSameOrder() {
        assertEquals(
            ContentShuffle.ordered(items, seed = 7L).map { it.id },
            ContentShuffle.ordered(items, seed = 7L).map { it.id }
        )
    }

    @Test
    fun readingOneQuoteDoesNotMoveTheRest() {
        // 글귀 하나를 읽음으로 넘기면 목록에서 빠집니다. 그때 남은 글귀가 다시 섞이면
        // 방금 읽던 자리가 눈앞에서 사라집니다.
        val ordered = ContentShuffle.ordered(items, seed = 99L)
        val readItem = ordered[3]

        val afterReading = ContentShuffle.ordered(items - readItem, seed = 99L)

        assertEquals(ordered.filterNot { it.id == readItem.id }.map { it.id }, afterReading.map { it.id })
    }

    @Test
    fun aNewQuoteSlipsInWithoutMovingTheOthers() {
        val ordered = ContentShuffle.ordered(items, seed = 5L)
        val added = item(id = 999L, syncId = "item-new")

        val afterAdding = ContentShuffle.ordered(items + added, seed = 5L)

        assertEquals(ordered.map { it.id }, afterAdding.filterNot { it.id == added.id }.map { it.id })
    }

    @Test
    fun theOrderDoesNotFollowTheOrderTheyCameIn() {
        // 뒤쪽에 담아 둔 글귀도 앞자리에 설 수 있어야 섞는 뜻이 있습니다.
        val ordered = ContentShuffle.ordered(items, seed = 3L).map { it.id }

        assertNotEquals(items.map { it.id }, ordered)
        assertTrue("담은 차례가 그대로입니다.", ordered.take(10).any { it > 20L })
    }

    @Test
    fun quotesWithoutASyncIdStillGetAPlace() {
        // 옛 백업에서 들어온 글귀는 syncId가 비어 있을 수 있습니다. 그래도 빠지면 안 됩니다.
        val withBlank = listOf(
            item(id = 1L, syncId = ""),
            item(id = 2L, syncId = ""),
            item(id = 3L, syncId = "item-3")
        )

        val ordered = ContentShuffle.ordered(withBlank, seed = 11L)

        assertEquals(listOf(1L, 2L, 3L), ordered.map { it.id }.sorted())
    }

    @Test
    fun oneQuoteIsLeftAlone() {
        val single = items.take(1)

        assertEquals(single, ContentShuffle.ordered(single, seed = 42L))
    }

    private fun item(id: Long, syncId: String) = ContentItemEntity(
        id = id,
        syncId = syncId,
        updatedAt = 1_000L,
        type = ContentType.QUOTE,
        title = "글귀 $id",
        body = "본문 $id",
        createdAt = id * 100L
    )
}
