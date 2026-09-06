package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 유튜브에서 공유한 것이 실제 DB에 어떻게 담기는지 봅니다.
 *
 * 담는 화면을 거치지 않으므로 사용자가 담기기 전에 볼 기회가 없습니다. 여기서 틀리면
 * 잘못 담긴 것이 그대로 보관함에 쌓입니다.
 *
 * **표가 비어 있다고 보지 않습니다.** 앱이 뜰 때 기본 글귀를 심어서(`seedDefaultsIfNeeded`),
 * 지우는 것과 심는 것이 겹치면 개수가 흔들립니다. 이 시험이 담은 것만 골라 봅니다.
 */
class SharedContentSaveTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val repository = container.repository

    @Before
    fun setUp() = clear()

    @After
    fun tearDown() = clear()

    /** 이 시험이 담은 것만 지웁니다. 기본 글귀는 건드리지 않습니다. */
    private fun clear() = runBlocking {
        container.database.contentItemDao().getAll()
            .filter { it.sourceUrl.contains(TEST_HOST) || it.body.startsWith(TEST_MARK) }
            .forEach { container.database.contentItemDao().deleteById(it.id) }
    }

    private suspend fun keptItems() = container.database.contentItemDao().getAll()
        .filter { it.sourceUrl.contains(TEST_HOST) || it.body.startsWith(TEST_MARK) }

    @Test
    fun aSharedYoutubeLinkIsKeptAsAVideo() = runBlocking {
        val draft = SharedText.toDraft("나를 바꾼 15분\nhttps://youtu.be/$TEST_HOST-1?si=abc")!!

        val result = repository.saveSharedContent(draft)

        assertFalse(result.alreadyKept)
        val saved = keptItems().single()
        assertEquals(ContentType.VIDEO, saved.type)
        assertEquals("나를 바꾼 15분", saved.title)
        assertEquals("https://youtu.be/$TEST_HOST-1?si=abc", saved.sourceUrl)
    }

    @Test
    fun theSameLinkIsNotKeptTwice() {
        // 공유는 손이 가벼워서 같은 영상을 여러 번 보내게 됩니다.
        runBlocking {
            val draft = SharedText.toDraft("제목\nhttps://youtu.be/$TEST_HOST-same")!!
            repository.saveSharedContent(draft)

            val second = repository.saveSharedContent(draft)

            assertTrue("같은 주소가 두 벌 담겼습니다.", second.alreadyKept)
            assertEquals(1, keptItems().size)
        }
    }

    @Test
    fun plainSharedTextIsKeptAsAQuote() = runBlocking {
        val draft = SharedText.toDraft("$TEST_MARK 행동은 감정이 따라올 때까지 기다리면 늘 늦다.")!!

        repository.saveSharedContent(draft)

        val saved = keptItems().single()
        assertEquals(ContentType.QUOTE, saved.type)
        assertEquals("", saved.sourceUrl)
        // 글 자체가 알맹이라 본문에 담겨야 합니다. 제목만 채우면 담기지 않습니다.
        assertEquals("$TEST_MARK 행동은 감정이 따라올 때까지 기다리면 늘 늦다.", saved.body)
    }

    @Test
    fun twoDifferentQuotesBothStay() {
        // 주소가 없는 것끼리는 겹침을 보지 않습니다. 빈 주소로 묶으면 둘째 글귀가 조용히 버려집니다.
        runBlocking {
            repository.saveSharedContent(SharedText.toDraft("$TEST_MARK 첫 번째 글귀")!!)
            repository.saveSharedContent(SharedText.toDraft("$TEST_MARK 두 번째 글귀")!!)

            assertEquals(2, keptItems().size)
        }
    }

    @Test
    fun anEmptyShareIsRefused() = runBlocking {
        val before = keptItems().size

        val result = runCatching {
            repository.saveSharedContent(ContentDraft(title = "", body = "", sourceUrl = ""))
        }

        assertTrue(result.isFailure)
        assertEquals(before, keptItems().size)
    }

    private companion object {
        /** 이 시험이 담은 것을 알아보는 표시. 기본 글귀와 섞이지 않게 합니다. */
        const val TEST_HOST = "sharedcontenttest"
        const val TEST_MARK = "[공유시험]"
    }
}
