package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 글귀에 메모를 달고, 글귀에서 루틴을 뽑는 길을 실제 DB에서 봅니다.
 *
 * 특히 삭제 표식을 봅니다. 메모를 지우고 표식을 안 남기면 다음 병합에서 되살아나고,
 * 글귀를 지울 때 딸린 메모의 표식을 빠뜨리면 주인 없는 메모가 서버에서 돌아옵니다.
 */
class ContentMemoAndRoutineExtractionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val repository = container.repository

    @Before
    fun setUp() = clearAll()

    @After
    fun tearDown() = clearAll()

    private fun clearAll() = runBlocking {
        container.database.contentMemoDao().clearAll()
        container.database.routineDao().clearAll()
        container.database.contentItemDao().clearAll()
        container.database.deletionDao().clearAll()
    }

    @Test
    fun aMemoPointsAtItsQuoteBySyncId() = runBlocking {
        val quote = saveQuote(title = "오늘의 기준", body = "행동이 먼저다.")

        repository.saveContentMemo(quote.id, "  세 번째 읽으니 다르게 들린다.  ")

        val memo = container.database.contentMemoDao().getAll().single()
        assertEquals(quote.id, memo.contentItemId)
        // 기기 간 식별자는 syncId뿐입니다. 숫자 id로만 이으면 다른 기기에서 엉뚱한 글귀에 붙습니다.
        assertEquals(quote.syncId, memo.contentItemSyncId)
        assertEquals("세 번째 읽으니 다르게 들린다.", memo.body)
    }

    @Test
    fun anEmptyMemoIsRefused() = runBlocking {
        val quote = saveQuote(title = "빈 메모", body = "본문")

        val result = runCatching { repository.saveContentMemo(quote.id, "   ") }

        assertTrue(result.isFailure)
        assertTrue(container.database.contentMemoDao().getAll().isEmpty())
    }

    @Test
    fun aMemoDoesNotCountAsPractice() = runBlocking {
        // 루틴 메모와 달리 체크를 남기지 않습니다. 여기 적는 것은 실천이 아니라 생각입니다.
        val quote = saveQuote(title = "오늘의 기준", body = "행동이 먼저다.")

        repository.saveContentMemo(quote.id, "메모")

        assertTrue(container.database.routineCheckDao().getAll().isEmpty())
    }

    @Test
    fun deletingAMemoLeavesATombstone() = runBlocking {
        val quote = saveQuote(title = "오늘의 기준", body = "행동이 먼저다.")
        val memoId = repository.saveContentMemo(quote.id, "지울 메모")

        repository.deleteContentMemo(memoId)

        assertTrue(container.database.contentMemoDao().getAll().isEmpty())
        val tombstones = container.database.deletionDao().getAll()
            .filter { it.entityType == SyncEntityType.CONTENT_MEMO }
        assertEquals("표식이 없으면 지운 메모가 다음 병합에 되살아납니다.", 1, tombstones.size)
    }

    @Test
    fun deletingAQuoteTakesItsMemosAndLeavesTombstonesForThem() = runBlocking {
        val quote = saveQuote(title = "지울 글귀", body = "본문")
        repository.saveContentMemo(quote.id, "첫 메모")
        repository.saveContentMemo(quote.id, "둘째 메모")

        repository.deleteContent(quote.id)

        assertTrue("주인 없는 메모가 남았습니다.", container.database.contentMemoDao().getAll().isEmpty())
        val tombstones = container.database.deletionDao().getAll()
            .filter { it.entityType == SyncEntityType.CONTENT_MEMO }
        assertEquals(2, tombstones.size)
    }

    @Test
    fun aRoutineExtractedFromAQuoteRemembersWhereItCameFrom() = runBlocking {
        val quote = saveQuote(title = "오늘의 기준", body = "행동은 감정을 기다리지 않는다.")

        val routine = repository.extractRoutineFromContent(quote.id, "아침에 한 가지 먼저 하기")

        assertEquals("아침에 한 가지 먼저 하기", routine.title)
        assertEquals(quote.syncId, routine.sourceContentSyncId)
        // 이름만 남기면 며칠 뒤에 왜 이걸 하기로 했는지 알 수 없습니다.
        assertEquals("행동은 감정을 기다리지 않는다.", routine.note)
        assertEquals(AppRepository.PRACTICE_CATEGORY, routine.category)
    }

    @Test
    fun anExtractedRoutineGoesToTheEndOfTheDay() = runBlocking {
        repository.saveRoutine(RoutineDraft(title = "이미 밟고 있는 것"))
        val quote = saveQuote(title = "오늘의 기준", body = "본문")

        val extracted = repository.extractRoutineFromContent(quote.id, "새로 뽑은 것")

        val routines = RoutineOrder.sorted(container.database.routineDao().getAll())
        assertEquals(listOf("이미 밟고 있는 것", "새로 뽑은 것"), routines.map { it.title })
        assertTrue(extracted.orderIndex > 0)
    }

    @Test
    fun renamingAnExtractedRoutineKeepsItsSource() = runBlocking {
        // 편집 화면이 출처를 실어 보내지 않아도 조용히 지워지면 안 됩니다.
        val quote = saveQuote(title = "오늘의 기준", body = "본문")
        val routine = repository.extractRoutineFromContent(quote.id, "처음 이름")

        repository.saveRoutine(RoutineDraft(id = routine.id, title = "고친 이름"))

        val saved = container.database.routineDao().getById(routine.id)
        assertEquals("고친 이름", saved?.title)
        assertEquals(quote.syncId, saved?.sourceContentSyncId)
    }

    @Test
    fun deletingTheQuoteKeepsTheRoutineItWasExtractedInto() = runBlocking {
        // 밟기로 한 것은 그 글귀와 별개로 이미 내 것입니다.
        val quote = saveQuote(title = "지울 글귀", body = "본문")
        val routine = repository.extractRoutineFromContent(quote.id, "남아야 하는 루틴")

        repository.deleteContent(quote.id)

        assertNull(repository.getContentById(quote.id))
        assertEquals("남아야 하는 루틴", container.database.routineDao().getById(routine.id)?.title)
    }

    @Test
    fun extractingFromAQuoteThatIsGoneFails() = runBlocking {
        val result = runCatching { repository.extractRoutineFromContent(9_999L, "이름") }

        assertTrue(result.isFailure)
    }

    private suspend fun saveQuote(title: String, body: String): ContentItemEntity {
        repository.saveContent(ContentDraft(type = ContentType.QUOTE, title = title, body = body))
        return container.database.contentItemDao().getAll().first { it.title == title }
    }
}
