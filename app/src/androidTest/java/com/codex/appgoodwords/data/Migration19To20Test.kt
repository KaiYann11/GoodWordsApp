package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class Migration19To20Test {
    @Test fun theUpgradePreservesAnInboxItemAndCanStoreAConcept() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-19-20-test.db"
        context.deleteDatabase(name)
        val inbox = ContentItemEntity(id = 1, syncId = "pending-concept", type = ContentType.INBOX, title = "인지 부하", body = "원문")
        val before = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try {
            before.contentItemDao().insert(inbox)
            // Room이 만든 표에는 22의 칸이 이미 있습니다. 옛 모양으로 되돌려야 ALTER가 실제로 걸립니다.
            before.openHelper.writableDatabase.execSQL("ALTER TABLE books DROP COLUMN review")
            before.openHelper.writableDatabase.version = 19
        } finally { before.close() }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22
            ).build()
        try {
            assertEquals(22, after.openHelper.writableDatabase.version)
            assertEquals(inbox, after.contentItemDao().getById(inbox.id))
            val concept = inbox.copy(type = ContentType.CONCEPT, updatedAt = inbox.updatedAt + 1)
            after.contentItemDao().insert(concept)
            assertEquals(concept, after.contentItemDao().getById(inbox.id))
        } finally {
            after.close()
            context.deleteDatabase(name)
        }
    }
}
