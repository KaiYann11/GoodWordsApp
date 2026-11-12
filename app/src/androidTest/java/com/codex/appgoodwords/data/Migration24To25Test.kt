package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 24→25는 목표에 들인 시간(`goal_time_logs`) 표를 더합니다. 기존 표는 건드리지 않습니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히 전부 지우거나, 표 모양이 엔티티와
 * 달라 Room이 열기를 거부합니다. 그래서 쓰던 기록이 남는지와 새 표에 실제로 쓰고 읽을 수 있는지를 봅니다.
 */
class Migration24To25Test {
    @Test fun theUpgradeKeepsRecordsAndAddsGoalTime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-24-25-test.db"
        context.deleteDatabase(name)

        val goal = ContentItemEntity(id = 1, syncId = "goal-1", type = ContentType.GOAL, title = "하프 마라톤 완주", body = "")
        val memo = ContentMemoEntity(id = 1, syncId = "memo-1", contentItemId = 1, contentItemSyncId = "goal-1", contentTitle = "하프", body = "무릎이 아팠다")
        val walk = WalkEntity(id = 1, syncId = "walk-1", startedAt = 1_000_000L, endedAt = 1_600_000L, distanceMeters = 830)
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.contentItemDao().insert(goal)
            before.contentMemoDao().insert(memo)
            before.walkDao().insert(walk)
            // Room이 만든 DB에는 이미 새 표가 있습니다. 24의 모양으로 되돌려 놓아야 CREATE가 실제로 걸리는 길을 시험합니다.
            before.openHelper.writableDatabase.execSQL("DROP TABLE goal_time_logs")
            before.openHelper.writableDatabase.execSQL("DROP TABLE record_links")
            before.openHelper.writableDatabase.version = 24
        } finally {
            before.close()
        }

        // 지금 판까지 잇습니다. 25→26은 표 하나만 더하므로 여기서 본 것을 건드리지 않습니다.
        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_24_25, AppDatabase.MIGRATION_25_26)
            .build()
        try {
            assertEquals(26, after.openHelper.writableDatabase.version)
            // 쓰던 기록이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다.
            assertEquals(goal, after.contentItemDao().getById(goal.id))
            assertEquals(1, after.contentMemoDao().getByContentItemId(goal.id).size)
            assertEquals(walk.syncId, after.walkDao().getBySyncId("walk-1")?.syncId)

            // 새 표는 비어 있고, 엔티티 그대로 쓰고 읽힙니다(표 모양이 다르면 Room이 여기서 거부합니다).
            assertEquals(0, after.goalTimeLogDao().getAll().size)
            val log = GoalTimeLogEntity(
                id = 1,
                syncId = "time-1",
                updatedAt = 9L,
                goalSyncId = "goal-1",
                minutes = 90,
                note = "5km 템포런",
                loggedAt = 1_762_508_400_000L,
                createdAt = 9L
            )
            after.goalTimeLogDao().insert(log)
            assertEquals(listOf(log), after.goalTimeLogDao().getByGoalSyncId("goal-1"))
            assertEquals(listOf(log), after.goalTimeLogDao().getAll())
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
