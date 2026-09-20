package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotToDoDataTest {
    private val item = ContentItemEntity(
        id = 1, syncId = "avoid-scroll", type = ContentType.NOT_TO_DO,
        title = "잠들기 전 숏폼 오래 보기", body = "휴대폰은 책상에 두기\n잠을 먼저 챙기기", updatedAt = 100
    )

    @Test fun aTitleIsEnoughAndLinksDoNotChangeTheType() {
        val draft = ContentNormalizer.normalize(ContentDraft(type = ContentType.NOT_TO_DO, title = "  충동 구매  "))
        ContentNormalizer.validate(draft)
        assertEquals("충동 구매", draft.title)
        assertEquals("", draft.body)
        assertEquals(ContentType.NOT_TO_DO, ContentNormalizer.normalize(draft.copy(sourceUrl = "https://youtu.be/example")).type)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEmptyTitleIsRejected() {
        ContentNormalizer.validate(ContentDraft(type = ContentType.NOT_TO_DO, title = "   "))
    }

    @Test fun backupsPreserveTheTypeMemoAndRecordCount() {
        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot(listOf(item))).toString())
        assertEquals(listOf(item), restored.items)
        assertEquals(1, restored.recordCount)
    }

    @Test fun mergingKeepsTheLatestMemoAndDoesNotCombineItWithAQuote() {
        val edited = item.copy(body = "최근에 바꾼 기준", updatedAt = 200)
        val quote = edited.copy(id = 2, syncId = "separate-quote", type = ContentType.QUOTE)
        val merged = SyncMerger.merge(snapshot(listOf(edited, quote)), snapshot(listOf(item)))
        assertEquals(2, merged.items.size)
        assertEquals(edited, merged.items.single { it.type == ContentType.NOT_TO_DO })
    }

    @Test fun aDeletedRuleDoesNotReturnFromAnOlderBackup() {
        val deleted = snapshot(emptyList()).copy(deletions = listOf(
            DeletionEntity(syncId = item.syncId, entityType = SyncEntityType.CONTENT_ITEM, deletedAt = 200)
        ))
        assertTrue(SyncMerger.merge(deleted, snapshot(listOf(item))).items.isEmpty())
    }

    @Test fun searchUsesItsOwnGroupAndDoesNotSurfaceRulesAsQuotes() {
        assertEquals(SearchKind.NOT_TO_DO, AppSearch.search("숏폼", items = listOf(item)).hits.single().kind)
        assertFalse(item.type.isReadingContent)
        val today = LocalDate.of(2026, 9, 14)
        val old = item.copy(createdAt = today.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertTrue(OnThisDay.find(today, emptyList(), listOf(old)).isEmpty())
        val digest = GrowthPrompt.digest(
            period = ReportPeriod.WEEKLY, today = today, routines = emptyList(), routineChecks = emptyList(),
            todos = emptyList(), diaries = emptyList(), items = listOf(item), events = emptyList(),
            books = emptyList(), includeDiaryBody = false
        )
        assertTrue(digest.ownedQuoteLines.isEmpty())
    }

    private fun snapshot(items: List<ContentItemEntity>) = AppDataSnapshot(
        items = items, events = emptyList(), routines = emptyList(), routineChecks = emptyList(),
        routineMemos = emptyList(), settings = ReminderSettings()
    )
}
