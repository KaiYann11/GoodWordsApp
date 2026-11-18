package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 27→28은 루틴에 요일(`weekdays`)과 시각(`timeOfDay`)을 더합니다(사용자가 정함, 2026-11-17). 다른 표는 건드리지 않습니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히 전부 지웁니다.
 * 그래서 쓰던 루틴과 체크가 그대로 남는지, 이미 있던 루틴이 매일·상시가 되는지(사용자가 정한 기본값), 새 칸에 실제로
 * 쓰고 읽을 수 있는지를 봅니다.
 */
class Migration27To28Test {
    @Test fun theUpgradeKeepsRoutinesAsEveryDayAndAnytime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-27-28-test.db"
        context.deleteDatabase(name)

        // 기본값을 쓰면 칸이 빠져도 통과해 버립니다. 차례·출처·주기 알림도 채웁니다.
        val stretching = RoutineEntity(
            id = 1, syncId = "routine-1", updatedAt = 5_000L, title = "아침 스트레칭", note = "10분", category = "건강",
            orderIndex = 2, sourceContentSyncId = "item-1", reminderEnabled = false, createdAt = 4_000L
        )
        val water = RoutineEntity(id = 2, syncId = "routine-2", updatedAt = 5_000L, title = "물 한 컵", createdAt = 4_100L)
        val check = RoutineCheckEntity(
            id = 1, syncId = "check-1", routineId = 1, routineSyncId = "routine-1", routineTitle = "아침 스트레칭", checkedAt = 6_000L
        )
        val todo = TodoEntity(id = 1, syncId = "todo-1", title = "우체국 가기", dueDate = "2026-11-17", imageUris = listOf("content://photo/1"))
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.routineDao().insertAll(listOf(stretching, water))
            before.routineCheckDao().insert(check)
            before.todoDao().insert(todo)
            // Room이 만든 표에는 이미 새 칸이 있습니다. 27의 모양으로 되돌려 놓아야
            // ALTER가 실제로 걸리는 길을 시험하게 됩니다(그러지 않으면 "칸이 이미 있다"로 끝납니다).
            before.openHelper.writableDatabase.execSQL("ALTER TABLE routines DROP COLUMN weekdays")
            before.openHelper.writableDatabase.execSQL("ALTER TABLE routines DROP COLUMN timeOfDay")
            before.openHelper.writableDatabase.execSQL("ALTER TABLE todos DROP COLUMN repeatRule")
            before.openHelper.writableDatabase.execSQL("ALTER TABLE todos DROP COLUMN repeatedAt")
            before.openHelper.writableDatabase.version = 27
        } finally {
            before.close()
        }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_27_28, AppDatabase.MIGRATION_28_29)
            .build()
        try {
            assertEquals(29, after.openHelper.writableDatabase.version)
            // 쓰던 루틴이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다. 이미 있던 루틴은 매일·상시입니다.
            val routines = after.routineDao().getAll().sortedBy { it.id }
            assertEquals(listOf(stretching, water), routines)
            routines.forEach { routine ->
                assertEquals(routine.title, RoutineSchedule.EVERY_DAY, routine.weekdays)
                assertNull(routine.title, routine.timeOfDay)
            }
            assertEquals(listOf(check), after.routineCheckDao().getAll())
            assertEquals(listOf(todo), after.todoDao().getAll())

            // 새 칸은 엔티티 그대로 쓰고 읽힙니다(칸 모양이 다르면 Room이 여기서 거부합니다).
            val gym = stretching.copy(weekdays = RoutineSchedule.maskOf(listOf(1, 3, 5)), timeOfDay = 19 * 60)
            after.routineDao().insert(gym)
            assertEquals(gym, after.routineDao().getById(1))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
