package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 삭제 표식은 지운 항목이 되살아나지 않게 남기는 것이라 그냥 두면 끝없이 쌓입니다.
 * 반대로 너무 일찍 지우면 오래 꺼져 있던 기기가 지운 항목을 되살립니다.
 */
class DeletionPruningTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pruning_removesOldMarksAndKeepsRecentOnes() = runBlocking {
        val now = System.currentTimeMillis()
        val retention = SyncCoordinator.DELETION_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        val dao = database.deletionDao()
        dao.insert(mark("아주-오래됨", now - retention - DAY))
        dao.insert(mark("경계-바로-밖", now - retention - 1))
        dao.insert(mark("경계-안쪽", now - retention + DAY))
        dao.insert(mark("어제", now - DAY))

        val removed = dao.deleteOlderThan(now - retention)

        assertEquals(2, removed)
        assertEquals(
            setOf("경계-안쪽", "어제"),
            dao.getAll().map { it.syncId }.toSet()
        )
    }

    @Test
    fun pruning_onEmptyTableRemovesNothing() = runBlocking {
        assertEquals(0, database.deletionDao().deleteOlderThan(System.currentTimeMillis()))
    }

    private fun mark(syncId: String, deletedAt: Long) = DeletionEntity(
        syncId = syncId,
        entityType = SyncEntityType.CONTENT_ITEM,
        deletedAt = deletedAt
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
