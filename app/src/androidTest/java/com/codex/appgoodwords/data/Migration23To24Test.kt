package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 23→24는 걸음 수(`step_counts`)와 걷기(`walks`) 표를 더합니다. 기존 표는 건드리지 않습니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히 전부 지우거나, 표 모양이 엔티티와
 * 달라 Room이 열기를 거부합니다. 그래서 쓰던 기록이 남는지와 새 표에 실제로 쓰고 읽을 수 있는지를 봅니다.
 */
class Migration23To24Test {
    @Test fun theUpgradeKeepsRecordsAndAddsWalkingTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-23-24-test.db"
        context.deleteDatabase(name)

        val quote = ContentItemEntity(id = 1, syncId = "quote-1", type = ContentType.QUOTE, title = "글귀", body = "본문")
        val routine = RoutineEntity(id = 1, syncId = "routine-1", title = "아침 산책")
        val mood = MoodLogEntity(id = 1, syncId = "mood-1", entryDate = "2026-11-07", mood = DiaryMood.GOOD.name)
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.contentItemDao().insert(quote)
            before.routineDao().insert(routine)
            before.moodLogDao().insert(mood)
            // Room이 만든 DB에는 이미 새 표가 있습니다. 23의 모양으로 되돌려 놓아야 CREATE가 실제로 걸리는 길을 시험합니다.
            before.openHelper.writableDatabase.execSQL("DROP TABLE step_counts")
            before.openHelper.writableDatabase.execSQL("DROP TABLE walks")
            before.openHelper.writableDatabase.version = 23
        } finally {
            before.close()
        }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_23_24)
            .build()
        try {
            assertEquals(24, after.openHelper.writableDatabase.version)
            // 쓰던 기록이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다.
            assertEquals(quote, after.contentItemDao().getById(quote.id))
            assertEquals(1, after.routineDao().getAll().size)
            assertEquals(1, after.moodLogDao().getAll().size)

            // 새 표는 비어 있고, 엔티티 그대로 쓰고 읽힙니다(표 모양이 다르면 Room이 여기서 거부합니다).
            assertEquals(0, after.stepCountDao().getAll().size)
            val count = StepCountEntity(id = 1, syncId = "steps-1", date = "2026-11-07", steps = 6_240, updatedAt = 5L, createdAt = 5L)
            after.stepCountDao().insert(count)
            assertEquals(listOf(count), after.stepCountDao().getAll())

            val walk = WalkEntity(
                id = 1,
                syncId = "walk-1",
                updatedAt = 9L,
                startedAt = 1_000_000L,
                endedAt = 1_600_000L,
                distanceMeters = 830,
                steps = 1_100,
                path = WalkPath.encode(listOf(GeoPoint(37.5665, 126.978), GeoPoint(37.57, 126.98))),
                createdAt = 9L
            )
            after.walkDao().insert(walk)
            assertEquals(walk, after.walkDao().getBySyncId("walk-1"))
            assertEquals(listOf(walk), after.walkDao().getFinished())
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
