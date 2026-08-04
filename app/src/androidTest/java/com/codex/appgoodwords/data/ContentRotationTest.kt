package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 노출 순환 쿼리는 손으로 쓴 조인 SQL이라 실제 SQLite에서만 검증됩니다.
 * 여기가 깨지면 어떤 항목은 영영 노출되지 않으므로 순환 보장을 직접 확인합니다.
 */
class ContentRotationTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pick_prefersNeverSurfacedItem() = runBlocking {
        val shown = insertItem("이미 노출됨")
        val neverShown = insertItem("한 번도 노출 안 됨")
        surface(shown, occurredAt = 1_000L)

        val picked = database.contentItemDao().pickLeastRecentlySurfaced(
            category = "",
            poolSize = 1,
            surfacedType = ExposureEventType.SURFACED
        )

        assertEquals(neverShown, picked?.id)
    }

    @Test
    fun pick_prefersOldestSurfacedWhenAllSeen() = runBlocking {
        val oldest = insertItem("가장 오래됨")
        val middle = insertItem("중간")
        val newest = insertItem("가장 최근")
        surface(oldest, occurredAt = 1_000L)
        surface(middle, occurredAt = 2_000L)
        surface(newest, occurredAt = 3_000L)

        val picked = database.contentItemDao().pickLeastRecentlySurfaced(
            category = "",
            poolSize = 1,
            surfacedType = ExposureEventType.SURFACED
        )

        assertEquals(oldest, picked?.id)
    }

    @Test
    fun pick_usesLatestSurfaceEventPerItem() = runBlocking {
        val repeatedlyShown = insertItem("여러 번 노출됨")
        val shownOnce = insertItem("한 번만 노출됨")
        // 예전 이력이 남아 있어도 마지막 노출 시각으로 판단해야 한다.
        surface(repeatedlyShown, occurredAt = 1_000L)
        surface(repeatedlyShown, occurredAt = 5_000L)
        surface(shownOnce, occurredAt = 2_000L)

        val picked = database.contentItemDao().pickLeastRecentlySurfaced(
            category = "",
            poolSize = 1,
            surfacedType = ExposureEventType.SURFACED
        )

        assertEquals(shownOnce, picked?.id)
    }

    @Test
    fun pick_ignoresNonSurfaceEvents() = runBlocking {
        val onlyConfirmed = insertItem("확인만 함")
        val surfacedItem = insertItem("노출됨")
        // CONFIRMED/SHOWN은 노출 순환 기준이 아니다.
        insertEvent(onlyConfirmed, ExposureEventType.CONFIRMED, occurredAt = 9_000L)
        insertEvent(onlyConfirmed, ExposureEventType.SHOWN, occurredAt = 9_000L)
        surface(surfacedItem, occurredAt = 1_000L)

        val picked = database.contentItemDao().pickLeastRecentlySurfaced(
            category = "",
            poolSize = 1,
            surfacedType = ExposureEventType.SURFACED
        )

        assertEquals(onlyConfirmed, picked?.id)
    }

    @Test
    fun pick_respectsCategoryFilter() = runBlocking {
        insertItem("다른 카테고리", category = "건강")
        val target = insertItem("맞는 카테고리", category = "동기부여")

        val picked = database.contentItemDao().pickLeastRecentlySurfaced(
            category = "동기부여",
            poolSize = 5,
            surfacedType = ExposureEventType.SURFACED
        )

        assertEquals(target, picked?.id)
    }

    @Test
    fun pick_returnsNullWhenNoItemMatches() = runBlocking {
        insertItem("항목", category = "건강")

        val picked = database.contentItemDao().pickLeastRecentlySurfaced(
            category = "없는카테고리",
            poolSize = 5,
            surfacedType = ExposureEventType.SURFACED
        )

        assertNull(picked)
    }

    @Test
    fun pick_staysWithinCandidatePool() = runBlocking {
        // 오래된 순으로 5개까지만 후보에 들어가므로 6번째로 오래된 항목은 뽑히면 안 된다.
        val ids = (1..6).map { index ->
            val id = insertItem("항목 $index")
            surface(id, occurredAt = index * 1_000L)
            id
        }
        val excluded = ids.last()

        repeat(40) {
            val picked = database.contentItemDao().pickLeastRecentlySurfaced(
                category = "",
                poolSize = AppRepository.SURFACE_POOL_SIZE,
                surfacedType = ExposureEventType.SURFACED
            )
            assertNotNull(picked)
            assertTrue("후보 밖 항목이 뽑혔습니다.", picked!!.id != excluded)
        }
    }

    @Test
    fun repeatedPicks_withPoolSizeOne_cycleThroughEveryItemExactlyOnce() = runBlocking {
        val ids = (1..8).map { index -> insertItem("항목 $index") }.toSet()

        // 후보를 1개로 좁히면 무작위 요소가 사라져 순환이 그대로 드러난다.
        val seen = mutableSetOf<Long>()
        var occurredAt = 10_000L
        repeat(ids.size) {
            val picked = database.contentItemDao().pickLeastRecentlySurfaced(
                category = "",
                poolSize = 1,
                surfacedType = ExposureEventType.SURFACED
            )
            assertNotNull(picked)
            val pickedId = picked!!.id
            seen += pickedId
            surface(pickedId, occurredAt)
            occurredAt += 1_000L
        }

        assertEquals("한 바퀴에 모든 항목이 정확히 한 번씩 노출되어야 합니다.", ids, seen)
    }

    @Test
    fun repeatedPicks_eventuallySurfaceEveryItem() = runBlocking {
        val ids = (1..8).map { index -> insertItem("항목 $index") }.toSet()

        // 실제 후보 수에서는 무작위가 섞이지만, 오래 안 나온 항목은 계속 후보에 남으므로
        // 굶는 항목 없이 전부 노출되어야 한다. pickFeaturedContent가 노출을 기록한다.
        val seen = mutableSetOf<Long>()
        repeat(80) {
            val item = repository.pickFeaturedContent(
                category = "",
                trigger = ExposureTrigger.MANUAL_REFRESH
            )
            assertNotNull(item)
            seen += item!!.id
        }

        assertEquals("반복 노출에서 빠지는 항목이 없어야 합니다.", ids, seen)
    }

    private suspend fun insertItem(
        title: String,
        category: String = ""
    ): Long = database.contentItemDao().insert(
        ContentItemEntity(
            type = ContentType.QUOTE,
            title = title,
            body = "본문 $title",
            category = category,
            createdAt = 1_600_000_000_000L
        )
    )

    private suspend fun surface(itemId: Long, occurredAt: Long) {
        insertEvent(itemId, ExposureEventType.SURFACED, occurredAt)
    }

    private suspend fun insertEvent(
        itemId: Long,
        eventType: ExposureEventType,
        occurredAt: Long
    ) {
        database.exposureEventDao().insert(
            ExposureEventEntity(
                contentItemId = itemId,
                contentTitle = "제목 $itemId",
                contentType = ContentType.QUOTE,
                eventType = eventType,
                trigger = ExposureTrigger.MANUAL_REFRESH,
                occurredAt = occurredAt
            )
        )
    }
}
