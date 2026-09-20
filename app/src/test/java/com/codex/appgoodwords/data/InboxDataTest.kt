package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxDataTest {
    @Test
    fun editingAMemoOrInboxLinkKeepsItsChosenType() {
        for (type in listOf(ContentType.MEMO, ContentType.INBOX)) {
            val draft = ContentDraft(type = type, body = "참고할 문장", sourceUrl = "https://youtu.be/example")
            assertEquals(type, ContentNormalizer.normalize(draft).type)
        }
    }

    @Test
    fun aBackupPreservesTextLinksAndCollectionTypes() {
        val items = listOf(ContentType.INBOX, ContentType.MEMO, ContentType.CONCEPT).mapIndexed { index, type ->
            ContentItemEntity(
                id = index + 1L, type = type, title = "용어 $index", body = "첫 줄\n둘째 줄",
                sourceUrl = "https://example.com/$index", tags = listOf("나중에"),
                imageUris = listOf("content://test/$index")
            )
        }
        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot(items)).toString())
        assertEquals(items, restored.items)
    }

    @Test
    fun classificationWinsOverAnOlderInboxCopyDuringSync() {
        val pending = ContentItemEntity(
            id = 1, syncId = "captured-on-phone", updatedAt = 100,
            type = ContentType.INBOX, title = "용어", body = "원문"
        )
        val classified = pending.copy(type = ContentType.MEMO, updatedAt = 200)
        val merged = SyncMerger.merge(snapshot(listOf(classified)), snapshot(listOf(pending)))
        assertEquals(listOf(classified), merged.items)
    }

    @Test
    fun searchFindsEachCollectionEvenWhenOneHasManyMatches() {
        val items = (1L..25L).map { id ->
            ContentItemEntity(id = id, type = ContentType.INBOX, title = "원문 $id", body = "검색할 내용")
        } + ContentItemEntity(id = 26, type = ContentType.MEMO, title = "원문 메모", body = "참고")
        val results = AppSearch.search("원문", items = items)
        assertTrue(results.hits.any { it.id == 26L && it.kind == SearchKind.MEMO })
        assertEquals(20, results.hits.count { it.kind == SearchKind.INBOX })
    }

    @Test
    fun unclassifiedTextAndMemosAreNotSentForQuoteRecommendations() {
        val items = listOf(ContentType.INBOX, ContentType.MEMO, ContentType.CONCEPT).map { type ->
            ContentItemEntity(type = type, title = "아직 정리하지 않은 원문", body = "내용")
        }
        val digest = GrowthPrompt.digest(
            period = ReportPeriod.WEEKLY, today = LocalDate.of(2026, 9, 12),
            routines = emptyList(), routineChecks = emptyList(), todos = emptyList(),
            diaries = emptyList(), items = items, events = emptyList(), books = emptyList(),
            includeDiaryBody = false
        )
        assertTrue(digest.ownedQuoteLines.isEmpty())
        assertFalse(GrowthPrompt.userPrompt(digest).contains("아직 정리하지 않은 원문"))
    }

    @Test
    fun conceptSearchFindsTheAiAnswerAndUsesItsOwnGroup() {
        val concept = ContentItemEntity(
            id = 30, syncId = "concept", type = ContentType.CONCEPT,
            title = "인지 부하", body = "원래 저장한 문장"
        )
        val answer = ContentMemoEntity(
            contentItemId = concept.id,
            contentItemSyncId = concept.syncId,
            contentTitle = concept.title,
            body = ConceptExplanation.memoBody("작업 기억이 한꺼번에 처리하는 정보의 양")
        )

        val hit = AppSearch.search("작업 기억", items = listOf(concept), contentMemos = listOf(answer)).hits.single()

        assertEquals(SearchKind.CONCEPT, hit.kind)
        assertTrue(hit.snippet.contains("작업 기억"))
        assertFalse(concept.type.isReadingContent)
    }

    private fun snapshot(items: List<ContentItemEntity>) = AppDataSnapshot(
        items = items, events = emptyList(), routines = emptyList(), routineChecks = emptyList(),
        routineMemos = emptyList(), settings = ReminderSettings()
    )
}
