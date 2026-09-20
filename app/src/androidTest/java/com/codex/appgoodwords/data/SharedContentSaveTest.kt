package com.codex.appgoodwords.data

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.ShareTargetActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
    fun twoSelectionsDeliveredDuringStartupBothStay() = runBlocking {
        val texts = listOf("$TEST_MARK 연속 공유 첫 문장", "$TEST_MARK 연속 공유 둘째 문장")
        withContext(Dispatchers.Main) {
            texts.forEach { text ->
                context.startActivity(Intent(context, ShareTargetActivity::class.java).apply {
                    action = Intent.ACTION_PROCESS_TEXT
                    type = "text/plain"
                    putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                })
            }
        }
        withTimeout(5_000) {
            while (!keptItems().map { it.body }.containsAll(texts)) delay(50)
        }
        assertEquals(2, keptItems().count { it.body in texts })
    }

    @Test
    fun theTextSelectionActionAcceptsStyledText() = runBlocking {
        val text = "$TEST_MARK 선택한 용어"
        withContext(Dispatchers.Main) {
            context.startActivity(Intent(context, ShareTargetActivity::class.java).apply {
                action = Intent.ACTION_PROCESS_TEXT
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, SpannableString(text))
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        withTimeout(5_000) {
            while (keptItems().none { it.body == text }) delay(50)
        }
        assertEquals(ContentType.INBOX, keptItems().single { it.body == text }.type)
    }

    @Test
    fun aSharedYoutubeLinkWaitsInTheInbox() = runBlocking {
        val draft = SharedText.toDraft("나를 바꾼 15분\nhttps://youtu.be/$TEST_HOST-1?si=abc")!!

        val result = repository.saveSharedContent(draft)

        assertFalse(result.alreadyKept)
        val saved = keptItems().single()
        assertEquals(ContentType.INBOX, saved.type)
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
    fun plainSharedTextWaitsInTheInbox() = runBlocking {
        val draft = SharedText.toDraft("$TEST_MARK 행동은 감정이 따라올 때까지 기다리면 늘 늦다.")!!

        repository.saveSharedContent(draft)

        val saved = keptItems().single()
        assertEquals(ContentType.INBOX, saved.type)
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

    @Test
    fun repeatingPlainTextDoesNotCreateAnotherInboxItem() = runBlocking {
        val draft = SharedText.toDraft("$TEST_MARK 같은 문장")!!
        repository.saveSharedContent(draft)
        assertTrue(repository.saveSharedContent(draft).alreadyKept)
        assertEquals(1, keptItems().size)
    }

    @Test
    fun differentPassagesFromTheSamePageBothStay() = runBlocking {
        val url = "https://example.com/$TEST_HOST"
        repository.saveSharedContent(SharedText.toDraft("$TEST_MARK 첫 문장\n$url")!!)
        repository.saveSharedContent(SharedText.toDraft("$TEST_MARK 다른 문장\n$url")!!)
        assertEquals(2, keptItems().size)
    }

    @Test
    fun classificationPreservesTheOriginalRecordAndItsAttachments() = runBlocking {
        repository.saveSharedContent(SharedText.toDraft("$TEST_MARK 첫 줄\n둘째 줄")!!)
        val before = keptItems().single().copy(
            tags = listOf("공부"), imageUris = listOf("content://test/attachment"),
            bookSyncId = "source-book", bookPage = 10, updatedAt = System.currentTimeMillis() + 60_000
        )
        container.database.contentItemDao().insert(before)

        repository.classifyInboxItem(before.id, ContentType.MEMO)

        val after = keptItems().single()
        assertEquals(before.copy(type = ContentType.MEMO, updatedAt = after.updatedAt), after)
        assertTrue(after.updatedAt > before.updatedAt)
        assertTrue(runCatching { repository.classifyInboxItem(before.id, ContentType.QUOTE) }.isFailure)
    }

    @Test
    fun classifyingAVideoIntoTheQuoteLibraryDetectsItsType() = runBlocking {
        repository.saveSharedContent(SharedText.toDraft("영상\nhttps://youtu.be/$TEST_HOST-classify")!!)
        val before = keptItems().single()
        repository.classifyInboxItem(before.id, ContentType.QUOTE)
        val after = keptItems().single()
        assertEquals(ContentType.VIDEO, after.type)
        assertEquals(before.syncId, after.syncId)
    }

    @Test
    fun aMemoCannotBeCountedAsAReadQuote() = runBlocking {
        repository.saveSharedContent(SharedText.toDraft("$TEST_MARK 메모로 남길 글")!!)
        val item = keptItems().single()
        repository.classifyInboxItem(item.id, ContentType.MEMO)
        assertFalse(repository.markContentConfirmed(item.id, ExposureTrigger.DETAIL_OPEN))
        assertEquals(0, keptItems().single().showCount)
    }

    private companion object {
        /** 이 시험이 담은 것을 알아보는 표시. 기본 글귀와 섞이지 않게 합니다. */
        const val TEST_HOST = "sharedcontenttest"
        const val TEST_MARK = "[공유시험]"
    }
}
