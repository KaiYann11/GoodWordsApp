package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 걸음 수와 걷기가 동기화를 지나도 그대로인지. 서버(`server/app_good_words_server.mjs`)와 같은 규칙이어야 합니다 —
 * 한쪽만 바꾸면 병합할 때마다 결과가 달라집니다.
 */
class WalkSyncTest {
    private val path = WalkPath.encode(listOf(GeoPoint(37.5665, 126.978), GeoPoint(37.57, 126.98)))

    @Test
    fun theSameDayFromTwoDevicesKeepsTheLargerCount() {
        // 걸음을 덜 본 기기가 나중에 읽었어도, 그 수로 걸은 날을 지우면 안 됩니다.
        val phone = StepCountEntity(syncId = "phone", date = "2026-11-07", steps = 6_240, updatedAt = 100L)
        val tablet = StepCountEntity(syncId = "tablet", date = "2026-11-07", steps = 300, updatedAt = 200L)

        val merged = SyncMerger.merge(local = snapshot(stepCounts = listOf(phone)), remote = snapshot(stepCounts = listOf(tablet)))

        assertEquals(listOf(phone), merged.stepCounts)
        assertTrue("사라진 쪽은 표식이 없어도 다시 오지 않아야 합니다", merged.stepCounts.none { it.syncId == "tablet" })
    }

    @Test
    fun aLaterReadOfTheSameRecordWins() {
        // 같은 기록(같은 syncId)이면 나중에 읽은 쪽입니다. 그날 걸음은 저녁까지 늘어납니다.
        val morning = StepCountEntity(syncId = "today", date = "2026-11-07", steps = 1_000, updatedAt = 100L)
        val evening = morning.copy(steps = 7_500, updatedAt = 900L)

        val merged = SyncMerger.merge(local = snapshot(stepCounts = listOf(morning)), remote = snapshot(stepCounts = listOf(evening)))

        assertEquals(listOf(evening), merged.stepCounts)
    }

    @Test
    fun theSameWalkUnderTwoIdsBecomesOne() {
        val walk = walk("a")
        val copy = walk.copy(syncId = "b", updatedAt = walk.updatedAt + 1)

        val merged = SyncMerger.merge(local = snapshot(walks = listOf(walk)), remote = snapshot(walks = listOf(copy)))

        assertEquals(listOf("b"), merged.walks.map { it.syncId })
    }

    @Test
    fun aDeletedWalkStaysDeleted() {
        val walk = walk("gone")
        val tombstone = DeletionEntity(syncId = "gone", entityType = SyncEntityType.WALK, deletedAt = walk.updatedAt + 10)

        val merged = SyncMerger.merge(
            local = snapshot(walks = listOf(walk)),
            remote = snapshot().copy(deletions = listOf(tombstone))
        )

        assertTrue(merged.walks.isEmpty())
    }

    @Test
    fun stepCountsAndWalksSurviveTheJsonRoundTrip() {
        val counts = listOf(StepCountEntity(id = 3, syncId = "c1", date = "2026-11-07", steps = 6_240, updatedAt = 10L, createdAt = 5L))
        val walks = listOf(walk("w1"))

        val back = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot(stepCounts = counts, walks = walks)).toString())

        assertEquals(counts, back.stepCounts)
        assertEquals(walks, back.walks)
    }

    @Test
    fun aWalkStillInProgressIsNotAccepted() {
        // 다른 기기의 걷는 중인 길이 이 기기의 "걷는 중"으로 보이면 끝낼 길이 없습니다.
        val ongoing = walk("ongoing").copy(endedAt = 0L)

        val back = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot(walks = listOf(ongoing))).toString())

        assertTrue(back.walks.isEmpty())
    }

    @Test
    fun onlyChangedWalksAndCountsAreSent() {
        val old = walk("old").copy(updatedAt = 100L)
        val fresh = walk("fresh").copy(startedAt = 2_000_000L, endedAt = 2_600_000L, updatedAt = 900L)
        val count = StepCountEntity(syncId = "c", date = "2026-11-07", steps = 10, updatedAt = 100L)

        val delta = SyncDelta.changedSince(snapshot(stepCounts = listOf(count), walks = listOf(old, fresh)), since = 500L)

        assertEquals(listOf("fresh"), delta.walks.map { it.syncId })
        assertTrue(delta.stepCounts.isEmpty())
    }

    @Test
    fun reindexingNumbersWalksFromOne() {
        val reindexed = SnapshotReindexer.reindex(
            snapshot(
                stepCounts = listOf(StepCountEntity(id = 40, syncId = "c", date = "2026-11-07", steps = 1)),
                walks = listOf(walk("a").copy(id = 9), walk("b").copy(id = 9, startedAt = 5_000_000L, endedAt = 5_600_000L))
            )
        )

        assertEquals(listOf(1L), reindexed.stepCounts.map { it.id })
        assertEquals(listOf(1L, 2L), reindexed.walks.map { it.id })
    }

    private fun walk(syncId: String) = WalkEntity(
        id = 1,
        syncId = syncId,
        updatedAt = 1_000L,
        startedAt = 1_000_000L,
        endedAt = 1_600_000L,
        distanceMeters = 830,
        steps = 1_100,
        path = path,
        createdAt = 1_000L
    )

    private fun snapshot(
        stepCounts: List<StepCountEntity> = emptyList(),
        walks: List<WalkEntity> = emptyList()
    ) = AppDataSnapshot(
        items = emptyList(),
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        stepCounts = stepCounts,
        walks = walks
    )
}
