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
 * 노출 순환 쿼리는 손으로 쓴 SQL이라 실제 SQLite에서만 검증됩니다.
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
            linkMetadataFetcher = LinkMetadataFetcher(),
            deletionDao = database.deletionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pick_prefersNeverSurfacedItem() = runBlocking {
        val shown = insertItem("이미 노출됨", lastSurfacedAt = 1_000L)
        val neverShown = insertItem("한 번도 노출 안 됨")

        val picked = pick(poolSize = 1)

        assertEquals(neverShown, picked?.id)
    }

    @Test
    fun pick_prefersOldestSurfacedWhenAllSeen() = runBlocking {
        val oldest = insertItem("가장 오래됨", lastSurfacedAt = 1_000L)
        insertItem("중간", lastSurfacedAt = 2_000L)
        insertItem("가장 최근", lastSurfacedAt = 3_000L)

        val picked = pick(poolSize = 1)

        assertEquals(oldest, picked?.id)
    }

    @Test
    fun recordContentSurfaced_updatesRotationMarker() = runBlocking {
        val id = insertItem("항목")
        val item = database.contentItemDao().getById(id)!!

        repository.recordContentSurfaced(item, ExposureTrigger.MANUAL_REFRESH)

        val updated = database.contentItemDao().getById(id)!!
        assertNotNull("노출 시각이 항목에 기록되어야 합니다.", updated.lastSurfacedAt)
    }

    @Test
    fun rotation_survivesHistoryDeletion() = runBlocking {
        // 이력 조인으로 순환을 판단하면 사용자가 이력을 지웠을 때 순환이 초기화된다.
        val first = insertItem("먼저 노출")
        val second = insertItem("나중 노출")
        // 어느 항목이 뽑히는지에 기대지 않도록 두 항목을 직접 노출시킨다.
        listOf(first, second).forEach { id ->
            repository.recordContentSurfaced(
                item = database.contentItemDao().getById(id)!!,
                trigger = ExposureTrigger.MANUAL_REFRESH
            )
        }

        val eventIds = database.exposureEventDao().getAll().map { it.id }
        repository.deleteExposureEvents(eventIds)

        assertTrue("이력이 지워져야 합니다.", database.exposureEventDao().getAll().isEmpty())
        val firstItem = database.contentItemDao().getById(first)!!
        val secondItem = database.contentItemDao().getById(second)!!
        assertNotNull("이력을 지워도 순환 기준은 남아야 합니다.", firstItem.lastSurfacedAt)
        assertNotNull("이력을 지워도 순환 기준은 남아야 합니다.", secondItem.lastSurfacedAt)
    }

    @Test
    fun pick_respectsCategoryFilter() = runBlocking {
        insertItem("다른 카테고리", category = "건강")
        val target = insertItem("맞는 카테고리", category = "동기부여")

        val picked = database.contentItemDao().pickLeastRecentlySurfaced(category = "동기부여", poolSize = 5)

        assertEquals(target, picked?.id)
    }

    @Test
    fun pick_returnsNullWhenNoItemMatches() = runBlocking {
        insertItem("항목", category = "건강")

        val picked = database.contentItemDao().pickLeastRecentlySurfaced(category = "없는카테고리", poolSize = 5)

        assertNull(picked)
    }

    @Test
    fun pick_staysWithinCandidatePool() = runBlocking {
        // 오래된 순으로 5개까지만 후보에 들어가므로 6번째로 오래된 항목은 뽑히면 안 된다.
        val ids = (1..6).map { index -> insertItem("항목 $index", lastSurfacedAt = index * 1_000L) }
        val excluded = ids.last()

        repeat(40) {
            val picked = pick(poolSize = AppRepository.SURFACE_POOL_SIZE)
            assertNotNull(picked)
            assertTrue("후보 밖 항목이 뽑혔습니다.", picked!!.id != excluded)
        }
    }

    @Test
    fun repeatedPicks_withPoolSizeOne_cycleThroughEveryItemExactlyOnce() = runBlocking {
        val ids = (1..8).map { index -> insertItem("항목 $index") }.toSet()

        // 후보를 1개로 좁히면 무작위 요소가 사라져 순환이 그대로 드러난다.
        val seen = mutableSetOf<Long>()
        var surfacedAt = 10_000L
        repeat(ids.size) {
            val picked = pick(poolSize = 1)
            assertNotNull(picked)
            val pickedId = picked!!.id
            seen += pickedId
            database.contentItemDao().markSurfaced(pickedId, surfacedAt)
            surfacedAt += 1_000L
        }

        assertEquals("한 바퀴에 모든 항목이 정확히 한 번씩 노출되어야 합니다.", ids, seen)
    }

    @Test
    fun repeatedPicks_eventuallySurfaceEveryItem() = runBlocking {
        val ids = (1..8).map { index -> insertItem("항목 $index") }.toSet()

        // 실제 후보 수에서는 무작위가 섞이지만, 오래 안 나온 항목은 계속 후보에 남으므로
        // 굶는 항목 없이 전부 노출되어야 한다.
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

    private suspend fun pick(poolSize: Int): ContentItemEntity? =
        database.contentItemDao().pickLeastRecentlySurfaced(category = "", poolSize = poolSize)

    private suspend fun insertItem(
        title: String,
        category: String = "",
        lastSurfacedAt: Long? = null
    ): Long = database.contentItemDao().insert(
        ContentItemEntity(
            type = ContentType.QUOTE,
            title = title,
            body = "본문 $title",
            category = category,
            createdAt = 1_600_000_000_000L,
            lastSurfacedAt = lastSurfacedAt
        )
    )
}
