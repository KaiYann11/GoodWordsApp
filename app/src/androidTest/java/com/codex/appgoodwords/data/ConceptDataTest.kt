package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConceptDataTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = AppRepository(
            database = database,
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            contentMemoDao = database.contentMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher(),
            deletionDao = database.deletionDao()
        )
    }

    @After fun tearDown() = database.close()

    @Test fun explanationMovesTheSameRecordAndStoresTheAnswerTogether() = runBlocking {
        val original = ContentItemEntity(
            syncId = "concept-source",
            type = ContentType.INBOX,
            title = "인지 부하",
            body = "인지 부하를 줄이면 이해하기 쉽다.",
            sourceUrl = "https://example.com/context",
            tags = listOf("UX"),
            updatedAt = 100
        )
        val id = database.contentItemDao().insert(original)
        val before = database.contentItemDao().getById(id)!!

        val concept = repository.saveConceptExplanation(id, "한꺼번에 처리해야 하는 정보의 양입니다.")

        assertEquals(before.copy(type = ContentType.CONCEPT, updatedAt = concept.updatedAt), concept)
        assertTrue(concept.updatedAt > before.updatedAt)
        val memo = database.contentMemoDao().getByContentItemId(id).single()
        assertEquals(before.syncId, memo.contentItemSyncId)
        assertEquals("한꺼번에 처리해야 하는 정보의 양입니다.", ConceptExplanation.fromMemo(memo.body))
        assertFalse(repository.markContentConfirmed(id, ExposureTrigger.DETAIL_OPEN))
    }

    @Test fun anEmptyOrLateAnswerLeavesTheInboxRecordUntouched() = runBlocking {
        val id = database.contentItemDao().insert(
            ContentItemEntity(type = ContentType.INBOX, title = "남겨 둘 원문", body = "내용")
        )
        val before = database.contentItemDao().getById(id)

        assertTrue(runCatching { repository.saveConceptExplanation(id, "   ") }.isFailure)
        assertEquals(before, database.contentItemDao().getById(id))
        assertTrue(database.contentMemoDao().getByContentItemId(id).isEmpty())
    }
}
