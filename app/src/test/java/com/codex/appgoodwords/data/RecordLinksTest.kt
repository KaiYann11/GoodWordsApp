package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기록끼리 이은 줄(사용자가 정함, 2026-11-12)이 파일·동기화·합치기를 거쳐도 하나로 남는지 봅니다.
 *
 * 서버 `recordLinkFingerprint`·`normalizeRecordLink`·`deduplicate`와 같은 규칙이어야 합니다 — 서버 쪽은
 * server.test의 "행복한 순간·좋아하는 것과 이은 줄"이 같은 경우를 봅니다. 어긋나면 두 기기가 끝없이 서로를 고칩니다.
 */
class RecordLinksTest {
    @Test
    fun aLinkHasNoDirection() {
        assertEquals(
            RecordLinks.fingerprint(link("a")),
            RecordLinks.fingerprint(link("b", fromSyncId = "like", toSyncId = "moment"))
        )
        // 끝을 모르면 무엇과도 묶이지 않습니다.
        assertEquals("", RecordLinks.fingerprint(link("c", toSyncId = " ")))
    }

    @Test
    fun linksAreFoundFromEitherEnd() {
        val links = listOf(link("a"))
        val moment = RecordRef(RecordKind.CONTENT_ITEM.name, "moment")
        val like = RecordRef(RecordKind.CONTENT_ITEM.name, "like")

        assertEquals(listOf(like), RecordLinks.linkedTo(links, moment))
        assertEquals(listOf(moment), RecordLinks.linkedTo(links, like))
        assertEquals("a", RecordLinks.between(links, like, moment)?.syncId)
        // 종류가 다르면 syncId가 같아도 다른 기록입니다.
        assertNull(RecordLinks.between(links, like, RecordRef(RecordKind.DIARY.name, "moment")))
    }

    @Test
    fun theFileKeepsLinksAndDropsBrokenOnes() {
        val good = link("good", toKind = RecordKind.DIARY.name, toSyncId = "diary-1")
        val snapshot = snapshot(
            recordLinks = listOf(
                good,
                link("unknown-kind", toKind = "MOOD_LOG"),
                link("no-end", toSyncId = ""),
                link("self", toSyncId = "moment")
            )
        )

        val back = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot).toString())

        assertEquals(listOf(good), back.recordLinks)
        assertEquals(4, AppDataJson.toJson(snapshot).getInt("recordLinkCount"))
    }

    @Test
    fun aReversedCopyOfTheSameLinkCollapsesToTheNewerOne() {
        val merged = SyncDeduplicator.deduplicate(
            snapshot(recordLinks = listOf(link("a", updatedAt = 2_000L), link("b", fromSyncId = "like", toSyncId = "moment", updatedAt = 3_000L)))
        )

        assertEquals(listOf("b"), merged.recordLinks.map { it.syncId })
    }

    @Test
    fun whenTwoRecordsBecomeOneTheLinksFollowAndSelfLinksGo() {
        val older = like("like-old", updatedAt = 1_000L)
        val newer = like("like-new", updatedAt = 2_000L).copy(id = 3)

        val merged = SyncDeduplicator.deduplicate(
            snapshot(
                items = listOf(moment(), older, newer),
                recordLinks = listOf(
                    link("to-old", toSyncId = "like-old"),
                    // 하나로 합쳐질 두 좋아하는 것끼리 이은 줄 — 합친 뒤에는 제 자신을 잇는 줄이라 보여 줄 것이 없습니다.
                    link("old-new", fromSyncId = "like-old", toSyncId = "like-new")
                )
            )
        )

        assertEquals(listOf("like-new"), merged.items.filter { it.type == ContentType.LIKE }.map { it.syncId })
        assertEquals(listOf("to-old" to "like-new"), merged.recordLinks.map { it.syncId to it.toSyncId })
    }

    @Test
    fun aLinkToARecordThatIsGoneStays() {
        // 화면이 뺍니다(목표에 이어 둔 루틴과 같은 규칙). 다른 기기에는 그 기록이 아직 있을 수 있습니다.
        val merged = SyncDeduplicator.deduplicate(
            snapshot(items = listOf(moment()), recordLinks = listOf(link("a", toKind = RecordKind.DIARY.name, toSyncId = "elsewhere")))
        )

        assertEquals(listOf("a"), merged.recordLinks.map { it.syncId })
    }

    @Test
    fun anUnlinkedLinkStaysUnlinked() {
        val tombstone = DeletionEntity(syncId = "a", entityType = SyncEntityType.RECORD_LINK, deletedAt = 5_000L)

        val merged = SyncMerger.merge(
            local = snapshot(recordLinks = listOf(link("a"))),
            remote = snapshot().copy(deletions = listOf(tombstone))
        )

        assertTrue(merged.recordLinks.isEmpty())
    }

    @Test
    fun onlyChangedLinksAreSentAndReindexingNumbersFromOne() {
        val delta = SyncDelta.changedSince(
            snapshot(recordLinks = listOf(link("old", updatedAt = 100L), link("fresh", toSyncId = "x", updatedAt = 900L))),
            since = 500L
        )
        assertEquals(listOf("fresh"), delta.recordLinks.map { it.syncId })

        val reindexed = SnapshotReindexer.reindex(
            snapshot(recordLinks = listOf(link("a").copy(id = 9), link("b", toSyncId = "x").copy(id = 9)))
        )
        assertEquals(listOf(1L, 2L), reindexed.recordLinks.map { it.id })
    }

    private fun link(
        syncId: String,
        fromSyncId: String = "moment",
        toSyncId: String = "like",
        updatedAt: Long = 2_000L,
        fromKind: String = RecordKind.CONTENT_ITEM.name,
        toKind: String = RecordKind.CONTENT_ITEM.name
    ) = RecordLinkEntity(
        id = 0,
        syncId = syncId,
        updatedAt = updatedAt,
        fromKind = fromKind,
        fromSyncId = fromSyncId,
        toKind = toKind,
        toSyncId = toSyncId,
        createdAt = 1_000L
    )

    private fun moment() = ContentItemEntity(
        id = 1,
        syncId = "moment",
        type = ContentType.HAPPY_MOMENT,
        title = "퇴근길 노을이 너무 예뻤다",
        body = "퇴근길 노을이 너무 예뻤다",
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private fun like(syncId: String, updatedAt: Long) = ContentItemEntity(
        id = 2,
        syncId = syncId,
        type = ContentType.LIKE,
        title = "노을",
        body = "",
        category = "장소",
        createdAt = 1_000L,
        updatedAt = updatedAt
    )

    private fun snapshot(
        items: List<ContentItemEntity> = emptyList(),
        recordLinks: List<RecordLinkEntity> = emptyList()
    ) = AppDataSnapshot(
        items = items,
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        recordLinks = recordLinks
    )
}
