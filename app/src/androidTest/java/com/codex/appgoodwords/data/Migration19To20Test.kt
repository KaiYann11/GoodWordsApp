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
            before.openHelper.writableDatabase.version = 19
        } finally { before.close() }

        val after = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_19_20, AppDatabase.MIGRATION_20_21).build()
        try {
            assertEquals(21, after.openHelper.writableDatabase.version)
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
