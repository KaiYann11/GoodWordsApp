package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 28→29는 할 일에 반복(`repeatRule`)과 다음 회차를 만든 때(`repeatedAt`)를 더합니다(사용자가 정함, 2026-11-19).
 * 다른 표는 건드리지 않습니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히 전부 지웁니다.
 * 그래서 쓰던 할 일이 그대로 남는지, 이미 있던 할 일이 한 번뿐인 할 일이 되는지, 새 칸에 실제로 쓰고 읽을 수 있는지를 봅니다.
 */
class Migration28To29Test {
    @Test fun theUpgradeKeepsTodosAsOneOff() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-28-29-test.db"
        context.deleteDatabase(name)

        // 기본값을 쓰면 칸이 빠져도 통과해 버립니다. 메모·알람·끝냄·사진도 채웁니다.
        val todo = TodoEntity(
            id = 1, syncId = "todo-1", updatedAt = 5_000L, title = "우체국 가기", note = "등기", dueDate = "2026-11-19",
            remindAt = 7_000L, doneAt = 8_000L, createdAt = 4_000L, imageUris = listOf("content://photo/1")
        )
        val routine = RoutineEntity(
            id = 1, syncId = "routine-1", updatedAt = 5_000L, title = "운동", weekdays = RoutineSchedule.maskOf(listOf(1, 3, 5)),
            timeOfDay = 19 * 60, createdAt = 4_000L
        )
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.todoDao().insert(todo)
            before.routineDao().insert(routine)
            // Room이 만든 표에는 이미 새 칸이 있습니다. 28의 모양으로 되돌려 놓아야
            // ALTER가 실제로 걸리는 길을 시험하게 됩니다(그러지 않으면 "칸이 이미 있다"로 끝납니다).
            before.openHelper.writableDatabase.execSQL("ALTER TABLE todos DROP COLUMN repeatRule")
            before.openHelper.writableDatabase.execSQL("ALTER TABLE todos DROP COLUMN repeatedAt")
            before.openHelper.writableDatabase.version = 28
        } finally {
            before.close()
        }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_28_29)
            .build()
        try {
            assertEquals(29, after.openHelper.writableDatabase.version)
            // 쓰던 할 일이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다. 이미 있던 할 일은 한 번뿐입니다.
            val todos = after.todoDao().getAll()
            assertEquals(listOf(todo), todos)
            assertEquals("", todos.single().repeatRule)
            assertNull(todos.single().repeatedAt)
            assertEquals(listOf(routine), after.routineDao().getAll())

            // 새 칸은 엔티티 그대로 쓰고 읽힙니다(칸 모양이 다르면 Room이 여기서 거부합니다).
            val monthly = todo.copy(repeatRule = "MONTHLY:31", repeatedAt = 9_000L)
            after.todoDao().insert(monthly)
            assertEquals(monthly, after.todoDao().getById(1))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
