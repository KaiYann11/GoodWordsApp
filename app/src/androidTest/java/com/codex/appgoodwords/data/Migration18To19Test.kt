package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration18To19Test {
    @Test fun theUpgradePreservesExistingContentAndRoutines() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-18-19-test.db"
        context.deleteDatabase(name)
        val items = listOf(ContentType.QUOTE, ContentType.MEMO, ContentType.INBOX).mapIndexed { index, type ->
            ContentItemEntity(id = index + 1L, type = type, title = "기존 기록 $index", body = "보존할 본문")
        }
        val routine = RoutineEntity(id = 1, title = "기존 루틴", note = "보존할 메모", orderIndex = 3)
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            items.forEach { before.contentItemDao().insert(it) }
            before.routineDao().insert(routine)
            // 테이블 구조는 같습니다. 18인 파일을 실제 Room 업그레이드 경로로 엽니다.
            before.openHelper.writableDatabase.version = 18
        } finally { before.close() }
        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_18_19, AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21).build()
        try {
            assertEquals(21, after.openHelper.writableDatabase.version)
            assertEquals(items.sortedBy { it.id }, after.contentItemDao().getAll().sortedBy { it.id })
            assertEquals(listOf(routine), after.routineDao().getAll())
            val rule = items.first().copy(id = 4, syncId = "new-rule", type = ContentType.NOT_TO_DO, body = "")
            after.contentItemDao().insert(rule)
            assertEquals(rule, after.contentItemDao().getById(4))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
