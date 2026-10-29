package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.work.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * 병합 결과를 실제로 저장했을 때 항목이 사라지지 않는지 확인합니다.
 *
 * 숫자 id는 기기마다 따로 증가하므로 두 기기의 스냅샷에는 같은 id가 함께 들어옵니다.
 * 저장은 REPLACE라, 다시 매기지 않으면 뒤에 넣은 쪽이 앞의 것을 조용히 덮어씁니다.
 */
class MergeImportTest {
    private lateinit var database: AppDatabase
    private lateinit var importer: AppDataImporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = AppDataImporter(
            context = context,
            database = database,
            settingsStore = SettingsStore(context),
            reminderScheduler = ReminderScheduler(context)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importingMergedSnapshot_keepsItemsFromBothDevices() = runBlocking {
        val merged = SyncMerger.merge(
            local = deviceSnapshot(prefix = "a", title = "A기기에서 쓴 글귀"),
            remote = deviceSnapshot(prefix = "b", title = "B기기에서 쓴 글귀")
        )

        importer.importSnapshot(merged)

        val stored = database.contentItemDao().getAll()
        assertEquals("두 기기의 항목이 모두 남아야 합니다.", 2, stored.size)
        assertEquals(
            setOf("A기기에서 쓴 글귀", "B기기에서 쓴 글귀"),
            stored.map { it.title }.toSet()
        )
        assertEquals("id가 겹치면 안 됩니다.", 2, stored.map { it.id }.distinct().size)
    }

    @Test
    fun importingMergedSnapshot_keepsEachEventOnItsOwnItem() = runBlocking {
        val merged = SyncMerger.merge(
            local = deviceSnapshot(prefix = "a", title = "A기기에서 쓴 글귀"),
            remote = deviceSnapshot(prefix = "b", title = "B기기에서 쓴 글귀")
        )

        importer.importSnapshot(merged)

        val itemsById = database.contentItemDao().getAll().associateBy { it.id }
        val events = database.exposureEventDao().getAll()
        assertEquals(2, events.size)
        events.forEach { event ->
            val item = itemsById[event.contentItemId]
            assertEquals(
                "이벤트가 다른 항목에 붙었습니다.",
                event.contentTitle,
                item?.title
            )
        }
        assertNotEquals(
            "두 이벤트가 같은 항목을 가리키면 안 됩니다.",
            events[0].contentItemId,
            events[1].contentItemId
        )
    }

    @Test
    fun importingMergedSnapshot_keepsRoutineChecksOnTheirOwnRoutine() = runBlocking {
        val merged = SyncMerger.merge(
            local = deviceSnapshot(prefix = "a", title = "A기기"),
            remote = deviceSnapshot(prefix = "b", title = "B기기")
        )

        importer.importSnapshot(merged)

        val routinesById = database.routineDao().getAll().associateBy { it.id }
        assertEquals(2, routinesById.size)
        val checks = database.routineCheckDao().getAll()
        assertEquals(2, checks.size)
        checks.forEach { check ->
            assertEquals(
                "체크가 다른 루틴에 붙었습니다.",
                check.routineTitle,
                routinesById[check.routineId]?.title
            )
        }
    }

    @Test
    fun importingMoodFlowKeepsEveryTapAndOnlyTheDeltaTombstoneRemovesOne() = runBlocking {
        val first = MoodLogEntity(id = 1L, syncId = "morning", entryDate = "2026-10-29", mood = "GOOD", createdAt = 1_000L, updatedAt = 1_000L)
        val last = first.copy(syncId = "evening", mood = "TIRED", createdAt = 2_000L, updatedAt = 2_000L)
        val empty = AppDataSnapshot(
            items = emptyList(), events = emptyList(), routines = emptyList(), routineChecks = emptyList(),
            routineMemos = emptyList(), settings = ReminderSettings()
        )
        // 파일 병합과 같은 JSON 왕복·같은 내용 합치기·기기 id 재배치를 모두 거칩니다.
        val merged = SyncMerger.merge(empty.copy(moodLogs = listOf(first)), empty.copy(moodLogs = listOf(last)))
        importer.importSnapshot(AppDataJson.fromJsonText(AppDataJson.toJson(merged).toString()))
        var stored = database.moodLogDao().getAll()
        assertEquals(setOf(first.syncId, last.syncId), stored.map { it.syncId }.toSet())
        assertEquals(2, stored.map { it.id }.distinct().size)

        val newest = last.copy(syncId = "night", createdAt = 3_000L, updatedAt = 3_000L)
        importer.applyDelta(empty.copy(moodLogs = listOf(newest), partial = true))
        assertEquals(3, database.moodLogDao().getAll().size)
        val deletion = DeletionEntity(syncId = first.syncId, entityType = SyncEntityType.MOOD_LOG, deletedAt = 4_000L)
        importer.applyDelta(empty.copy(deletions = listOf(deletion), partial = true))
        stored = database.moodLogDao().getAll()
        assertEquals(setOf(last.syncId, newest.syncId), stored.map { it.syncId }.toSet())
        // 다른 기기에 남아 있던 옛 사본을 다시 합쳐도 지운 기록은 되살아나지 않습니다.
        val current = empty.copy(moodLogs = stored, deletions = database.deletionDao().getAll())
        assertEquals(2, SyncMerger.merge(current, merged).moodLogs.size)
    }

    /** 한 기기의 DB 모습. 두 기기 모두 id는 1부터 시작한다. */
    private fun deviceSnapshot(prefix: String, title: String) = AppDataSnapshot(
        items = listOf(
            ContentItemEntity(
                id = 1L,
                syncId = "$prefix-item",
                updatedAt = 1_000L,
                type = ContentType.QUOTE,
                title = title,
                body = "본문",
                createdAt = 1_000L
            )
        ),
        events = listOf(
            ExposureEventEntity(
                id = 1L,
                syncId = "$prefix-event",
                contentItemId = 1L,
                contentItemSyncId = "$prefix-item",
                contentTitle = title,
                contentType = ContentType.QUOTE,
                eventType = ExposureEventType.SURFACED,
                trigger = ExposureTrigger.MANUAL_REFRESH,
                occurredAt = 1_000L
            )
        ),
        routines = listOf(
            RoutineEntity(
                id = 1L,
                syncId = "$prefix-routine",
                updatedAt = 1_000L,
                title = "$title 루틴",
                note = "",
                category = "",
                reminderEnabled = true,
                createdAt = 1_000L
            )
        ),
        routineChecks = listOf(
            RoutineCheckEntity(
                id = 1L,
                syncId = "$prefix-check",
                routineId = 1L,
                routineSyncId = "$prefix-routine",
                routineTitle = "$title 루틴",
                checkedAt = 1_000L
            )
        ),
        routineMemos = emptyList(),
        settings = ReminderSettings()
    )
}
