package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 25→26은 기록끼리 이은 줄(`record_links`) 표를 더합니다(사용자가 정함, 2026-11-12). 기존 표는 건드리지 않습니다.
 * 행복한 순간·좋아하는 것은 보관함 종류라 `content_items`에 이름으로 담깁니다 — 표를 바꿀 것이 없습니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히 전부 지우거나, 표 모양이 엔티티와
 * 달라 Room이 열기를 거부합니다. 그래서 쓰던 기록이 남는지와 새 표에 실제로 쓰고 읽을 수 있는지를 봅니다.
 */
class Migration25To26Test {
    @Test fun theUpgradeKeepsRecordsAndAddsRecordLinks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-25-26-test.db"
        context.deleteDatabase(name)

        val goal = ContentItemEntity(id = 1, syncId = "goal-1", type = ContentType.GOAL, title = "하프 마라톤 완주", body = "")
        val time = GoalTimeLogEntity(id = 1, syncId = "time-1", goalSyncId = "goal-1", minutes = 30, note = "")
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.contentItemDao().insert(goal)
            before.goalTimeLogDao().insert(time)
            // Room이 만든 DB에는 이미 새 표가 있습니다. 25의 모양으로 되돌려 놓아야 CREATE가 실제로 걸리는 길을 시험합니다.
            before.openHelper.writableDatabase.execSQL("DROP TABLE record_links")
            before.openHelper.writableDatabase.version = 25
        } finally {
            before.close()
        }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_25_26)
            .build()
        try {
            assertEquals(26, after.openHelper.writableDatabase.version)
            // 쓰던 기록이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다.
            assertEquals(goal, after.contentItemDao().getById(goal.id))
            assertEquals(listOf(time), after.goalTimeLogDao().getAll())

            // 새 표는 비어 있고, 엔티티 그대로 쓰고 읽힙니다(표 모양이 다르면 Room이 여기서 거부합니다).
            assertEquals(0, after.recordLinkDao().getAll().size)
            val moment = ContentItemEntity(id = 2, syncId = "moment-1", type = ContentType.HAPPY_MOMENT, title = "노을", body = "노을")
            after.contentItemDao().insert(moment)
            assertEquals(ContentType.HAPPY_MOMENT, after.contentItemDao().getById(2)?.type)
            val link = RecordLinkEntity(
                id = 1,
                syncId = "link-1",
                updatedAt = 9L,
                fromKind = RecordKind.CONTENT_ITEM.name,
                fromSyncId = "moment-1",
                toKind = RecordKind.CONTENT_ITEM.name,
                toSyncId = "goal-1",
                createdAt = 9L
            )
            after.recordLinkDao().insert(link)
            assertEquals(listOf(link), after.recordLinkDao().getTouching("goal-1"))
            assertEquals(listOf(link), after.recordLinkDao().getTouching("moment-1"))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
