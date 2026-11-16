package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 26→27은 할 일에 사진 칸(`imageUris`)을 더합니다(사용자가 정함, 2026-11-15). 다른 표는 건드리지 않습니다.
 *
 * 마이그레이션이 없거나 틀리면 `fallbackToDestructiveMigration()`이 사용자 DB를 조용히 전부 지웁니다.
 * 그래서 쓰던 할 일이 그대로 남는지(완료·알람·날짜 없는 것까지)와, 새 칸에 실제로 쓰고 읽을 수 있는지를 봅니다.
 */
class Migration26To27Test {
    @Test fun theUpgradeKeepsTodosAndAddsRoomForPhotos() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-26-27-test.db"
        context.deleteDatabase(name)

        val dated = TodoEntity(
            id = 1, syncId = "todo-1", updatedAt = 5_000L, title = "우체국 가기", note = "등기",
            dueDate = "2026-11-14", remindAt = 9_000L, doneAt = 9_500L, createdAt = 4_000L
        )
        val someday = TodoEntity(id = 2, syncId = "todo-2", updatedAt = 5_000L, title = "선반 고치기", dueDate = "", createdAt = 4_000L)
        val diary = DiaryEntity(id = 1, syncId = "diary-1", entryDate = "2026-11-14", body = "사진 붙인 일기", imageUris = listOf("content://photo/1"))
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.todoDao().insertAll(listOf(dated, someday))
            before.diaryDao().insert(diary)
            // Room이 만든 표에는 이미 새 칸이 있습니다. 26의 모양으로 되돌려 놓아야
            // ALTER가 실제로 걸리는 길을 시험하게 됩니다(그러지 않으면 "칸이 이미 있다"로 끝납니다).
            before.openHelper.writableDatabase.execSQL("ALTER TABLE todos DROP COLUMN imageUris")
            before.openHelper.writableDatabase.execSQL("ALTER TABLE routines DROP COLUMN weekdays")
            before.openHelper.writableDatabase.execSQL("ALTER TABLE routines DROP COLUMN timeOfDay")
            before.openHelper.writableDatabase.version = 26
        } finally {
            before.close()
        }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_26_27, AppDatabase.MIGRATION_27_28)
            .build()
        try {
            assertEquals(28, after.openHelper.writableDatabase.version)
            // 쓰던 할 일이 그대로 있어야 합니다. 비었으면 통째로 지워진 것입니다. 옛 할 일의 사진은 빈 목록으로 읽힙니다 —
            // NULL이면 읽는 쪽마다 "없음"과 "빈 목록"을 따로 다뤄야 합니다.
            assertEquals(listOf(dated, someday), after.todoDao().getAll().sortedBy { it.id })
            assertEquals(emptyList<String>(), after.todoDao().getById(1)?.imageUris)
            assertEquals(listOf(diary), after.diaryDao().getAll())

            // 새 칸은 엔티티 그대로 쓰고 읽힙니다(칸 모양이 다르면 Room이 여기서 거부합니다).
            val withPhotos = dated.copy(imageUris = listOf("content://photo/receipt", "appgoodwords://attachment/${"a".repeat(64)}.jpg"))
            after.todoDao().insert(withPhotos)
            assertEquals(withPhotos, after.todoDao().getById(1))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
