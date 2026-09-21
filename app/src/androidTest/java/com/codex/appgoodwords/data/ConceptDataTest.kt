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

    @Test fun askingAgainReplacesTheAnswerAndLeavesMyOwnNotesAlone() = runBlocking {
        // 직접 적어 둔 개념에 처음 묻는 경우와, 받아 둔 답이 아쉬워 다시 묻는 경우입니다.
        val id = database.contentItemDao().insert(
            ContentItemEntity(type = ContentType.CONCEPT, title = "직접 적은 개념", body = "설명을 받고 싶은 원문")
        )
        repository.saveContentMemo(id, "내가 적어 둔 생각")

        val first = repository.saveConceptExplanation(id, "처음 받은 설명")
        val second = repository.saveConceptExplanation(id, "다시 받은 설명")

        assertEquals(ContentType.CONCEPT, second.type)
        assertTrue(second.updatedAt > first.updatedAt)
        val memos = database.contentMemoDao().getByContentItemId(id)
        // AI 설명은 한 줄뿐입니다. 쌓이면 카드가 옛 답을 집습니다.
        val answers = memos.mapNotNull { ConceptExplanation.fromMemo(it.body) }
        assertEquals(listOf("다시 받은 설명"), answers)
        // 같은 줄을 고친 것이라 식별자가 그대로여야 다른 기기도 고쳐진 것으로 읽습니다.
        assertEquals(1, memos.count { ConceptExplanation.fromMemo(it.body) != null })
        // 직접 적은 메모는 건드리지 않습니다.
        assertTrue(memos.any { it.body == "내가 적어 둔 생각" })
    }

    @Test fun onlyAnInboxOrConceptCanBeExplained() = runBlocking {
        // 글귀나 메모에까지 열어 두면 사용자가 정리해 둔 종류가 AI 한 번에 개념으로 바뀝니다.
        val quoteId = database.contentItemDao().insert(
            ContentItemEntity(type = ContentType.QUOTE, title = "글귀", body = "이미 정리한 글")
        )

        assertTrue(runCatching { repository.saveConceptExplanation(quoteId, "설명") }.isFailure)
        assertEquals(ContentType.QUOTE, database.contentItemDao().getById(quoteId)!!.type)
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
