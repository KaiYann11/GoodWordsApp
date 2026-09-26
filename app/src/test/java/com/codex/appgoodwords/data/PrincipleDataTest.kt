package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 원칙은 지키는 것이지 밟는 것이 아닙니다.
 *
 * 그래서 읽음·알림·지난해 오늘·AI 추천 원문 어디에도 끼지 않아야 합니다. 한 곳이라도 새면
 * 내가 적어 둔 기준이 "오늘의 글귀"라며 알림으로 돌아옵니다.
 */
class PrincipleDataTest {
    private val item = ContentItemEntity(
        id = 1, syncId = "keep-calm", type = ContentType.PRINCIPLE,
        title = "화가 난 채로 답하지 않는다", body = "하루 자고 나서 다시 읽기", updatedAt = 100
    )

    @Test fun aTitleIsEnoughAndLinksDoNotChangeTheType() {
        val draft = ContentNormalizer.normalize(ContentDraft(type = ContentType.PRINCIPLE, title = "  먼저 듣는다  "))
        ContentNormalizer.validate(draft)
        assertEquals("먼저 듣는다", draft.title)
        assertEquals("", draft.body)
        // 주소가 섞여 들어와도 종류를 글귀·링크로 바꾸지 않습니다. 담는 사람이 짚어 준 것입니다.
        assertEquals(
            ContentType.PRINCIPLE,
            ContentNormalizer.normalize(draft.copy(sourceUrl = "https://youtu.be/example")).type
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun anEmptyTitleIsRejected() {
        ContentNormalizer.validate(ContentDraft(type = ContentType.PRINCIPLE, title = "   "))
    }

    @Test fun backupsPreserveTheTypeAndRecordCount() {
        val restored = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot(listOf(item))).toString())
        assertEquals(listOf(item), restored.items)
        assertEquals(1, restored.recordCount)
    }

    @Test fun mergingKeepsTheLatestTextAndDoesNotCombineItWithARule() {
        val edited = item.copy(body = "하루 자고 나서 다시 읽기. 그래도 화가 나면 쓰지 않기", updatedAt = 200)
        // 같은 문장을 하지 않을 일로도 적어 둘 수 있습니다. 방향이 반대라 서로 다른 기록입니다.
        val avoid = edited.copy(id = 2, syncId = "avoid-reply", type = ContentType.NOT_TO_DO)
        val merged = SyncMerger.merge(snapshot(listOf(edited, avoid)), snapshot(listOf(item)))

        assertEquals(2, merged.items.size)
        assertEquals(edited, merged.items.single { it.type == ContentType.PRINCIPLE })
    }

    @Test fun aDeletedPrincipleDoesNotReturnFromAnOlderBackup() {
        val deleted = snapshot(emptyList()).copy(
            deletions = listOf(
                DeletionEntity(syncId = item.syncId, entityType = SyncEntityType.CONTENT_ITEM, deletedAt = 200)
            )
        )
        assertTrue(SyncMerger.merge(deleted, snapshot(listOf(item))).items.isEmpty())
    }

    @Test fun itIsNeverCountedAsSomethingToRead() {
        assertEquals(SearchKind.PRINCIPLE, AppSearch.search("화가", items = listOf(item)).hits.single().kind)
        assertFalse(item.type.isReadingContent)
        assertTrue(item.type.isPersonalRecord)

        val today = LocalDate.of(2026, 9, 26)
        val old = item.copy(
            createdAt = today.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        // 늘 거기 있는 기준이라, 담은 날을 되짚어도 "지난해 오늘"의 기억이 되지 않습니다.
        assertTrue(OnThisDay.find(today, emptyList(), listOf(old)).isEmpty())

        val digest = GrowthPrompt.digest(
            period = ReportPeriod.WEEKLY, today = today, routines = emptyList(), routineChecks = emptyList(),
            todos = emptyList(), diaries = emptyList(), items = listOf(item), events = emptyList(),
            books = emptyList(), includeDiaryBody = false
        )
        // AI에 "이미 가진 글귀"로 실어 보내면, 내가 정한 기준을 남의 말처럼 추천받습니다.
        assertTrue(digest.ownedQuoteLines.isEmpty())
    }

    private fun snapshot(items: List<ContentItemEntity>) = AppDataSnapshot(
        items = items, events = emptyList(), routines = emptyList(), routineChecks = emptyList(),
        routineMemos = emptyList(), settings = ReminderSettings()
    )
}
