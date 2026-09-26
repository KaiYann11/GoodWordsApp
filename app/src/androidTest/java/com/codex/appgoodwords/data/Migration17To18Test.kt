package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration17To18Test {
    @Test
    fun openingVersion17PreservesExistingRecords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-17-18-test.db"
        context.deleteDatabase(name)
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .allowMainThreadQueries().build()
        val item = ContentItemEntity(
            id = 1, type = ContentType.QUOTE, title = "기존 글귀", body = "남아 있어야 할 원문",
            tags = listOf("기존 태그"), imageUris = listOf("content://test/photo"),
            bookSyncId = "existing-book", bookPage = 12
        )
        try {
            before.contentItemDao().insert(item)
            // 17과 18의 테이블 구조는 같습니다. 실제 파일을 17로 열었다가 Room의 업그레이드를 거칩니다.
            // Room이 만든 표에는 22의 칸이 이미 있습니다. 옛 모양으로 되돌려야 ALTER가 실제로 걸립니다.
            before.openHelper.writableDatabase.execSQL("ALTER TABLE books DROP COLUMN review")
            before.openHelper.writableDatabase.version = 17
        } finally {
            before.close()
        }
        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22
            ).allowMainThreadQueries().build()
        try {
            assertEquals(listOf(item), after.contentItemDao().getAll())
            assertEquals(22, after.openHelper.writableDatabase.version)
            val memo = item.copy(id = 2, syncId = "new-memo", type = ContentType.MEMO)
            after.contentItemDao().insert(memo)
            assertEquals(memo, after.contentItemDao().getById(2))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
