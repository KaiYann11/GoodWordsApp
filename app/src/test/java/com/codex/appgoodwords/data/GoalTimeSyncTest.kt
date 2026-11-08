package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 목표에 들인 시간이 동기화를 지나도 그대로인지. 서버(`server/app_good_words_server.mjs`)와 같은 규칙이어야 합니다 —
 * 한쪽만 바꾸면 병합할 때마다 결과가 달라집니다.
 */
class GoalTimeSyncTest {
    @Test
    fun timeLogsSurviveTheJsonRoundTrip() {
        val logs = listOf(log("t1").copy(id = 3, note = "5km 템포런\n페이스 5:40"))

        val back = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot(goalTimeLogs = logs)).toString())

        assertEquals(logs, back.goalTimeLogs)
        assertEquals(1, AppDataJson.toJson(snapshot(goalTimeLogs = logs)).getInt("goalTimeLogCount"))
    }

    @Test
    fun aLogWithoutAGoalOrTimeIsNotAccepted() {
        // 어느 목표의 것인지 모르거나 들인 시간이 없으면 쌓을 곳도 셀 것도 없습니다.
        val bad = listOf(log("no-goal").copy(goalSyncId = " "), log("zero").copy(minutes = 0), log("long").copy(minutes = 24 * 60 + 1))

        val back = AppDataJson.fromJsonText(AppDataJson.toJson(snapshot(goalTimeLogs = bad)).toString())

        assertTrue(back.goalTimeLogs.isEmpty())
    }

    @Test
    fun theSameLogUnderTwoIdsCountsOnce() {
        // 두 번 세면 들인 시간이 부풀어 보입니다(예전 설치의 백업을 다시 올린 때 등).
        val log = log("a")
        val copy = log.copy(syncId = "b", updatedAt = log.updatedAt + 1)

        val merged = SyncMerger.merge(
            local = snapshot(items = listOf(goal("goal-1")), goalTimeLogs = listOf(log)),
            remote = snapshot(goalTimeLogs = listOf(copy))
        )

        assertEquals(listOf("b"), merged.goalTimeLogs.map { it.syncId })
    }

    @Test
    fun whenTwoGoalsBecomeOneTheTimeMovesToTheSurvivor() {
        // 같은 목표가 두 벌이 되어 하나로 합쳐지면, 사라진 쪽에 쌓인 시간이 목표를 잃으면 안 됩니다.
        val older = goal("goal-old").copy(updatedAt = 1_000L)
        val newer = goal("goal-new").copy(id = 2, updatedAt = 2_000L)

        val merged = SyncMerger.merge(
            local = snapshot(items = listOf(older), goalTimeLogs = listOf(log("t").copy(goalSyncId = "goal-old"))),
            remote = snapshot(items = listOf(newer))
        )

        assertEquals(listOf("goal-new"), merged.items.map { it.syncId })
        assertEquals(listOf("goal-new"), merged.goalTimeLogs.map { it.goalSyncId })
    }

    @Test
    fun aDeletedLogStaysDeleted() {
        val log = log("gone")
        val tombstone = DeletionEntity(syncId = "gone", entityType = SyncEntityType.GOAL_TIME_LOG, deletedAt = log.updatedAt + 10)

        val merged = SyncMerger.merge(
            local = snapshot(goalTimeLogs = listOf(log)),
            remote = snapshot().copy(deletions = listOf(tombstone))
        )

        assertTrue(merged.goalTimeLogs.isEmpty())
    }

    @Test
    fun onlyChangedLogsAreSent() {
        val old = log("old").copy(updatedAt = 100L)
        val fresh = log("fresh").copy(updatedAt = 900L, loggedAt = 2_000_000L)

        val delta = SyncDelta.changedSince(snapshot(goalTimeLogs = listOf(old, fresh)), since = 500L)

        assertEquals(listOf("fresh"), delta.goalTimeLogs.map { it.syncId })
    }

    @Test
    fun reindexingNumbersLogsFromOne() {
        val reindexed = SnapshotReindexer.reindex(
            snapshot(goalTimeLogs = listOf(log("a").copy(id = 9), log("b").copy(id = 9, loggedAt = 5_000_000L)))
        )

        assertEquals(listOf(1L, 2L), reindexed.goalTimeLogs.map { it.id })
    }

    private fun goal(syncId: String) = ContentItemEntity(
        id = 1,
        syncId = syncId,
        type = ContentType.GOAL,
        title = "하프 마라톤 완주",
        body = "",
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private fun log(syncId: String) = GoalTimeLogEntity(
        id = 1,
        syncId = syncId,
        updatedAt = 1_000L,
        goalSyncId = "goal-1",
        minutes = 90,
        note = "",
        loggedAt = 1_762_508_400_000L,
        createdAt = 1_000L
    )

    private fun snapshot(
        items: List<ContentItemEntity> = emptyList(),
        goalTimeLogs: List<GoalTimeLogEntity> = emptyList()
    ) = AppDataSnapshot(
        items = items,
        events = emptyList(),
        routines = emptyList(),
        routineChecks = emptyList(),
        routineMemos = emptyList(),
        settings = ReminderSettings(),
        goalTimeLogs = goalTimeLogs
    )
}
