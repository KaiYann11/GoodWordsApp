package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 걷기를 적는 자리([WalkRepository])가 진짜 DB에서 어떻게 남는지.
 *
 * 기록 서비스가 죽거나, 걷는 사이에 병합이 기기 데이터를 통째로 바꿔도 지나온 길이 사라지면 안 됩니다. 걸음 수(Health
 * Connect)는 시험 기기에서 허락받지 않았으므로 0으로 남는 것까지 봅니다.
 */
class WalkRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val repository = container.walkRepository
    private val walkDao = container.database.walkDao()
    private val route = listOf(GeoPoint(37.5665, 126.978), GeoPoint(37.5670, 126.9785), GeoPoint(37.5680, 126.9790))

    // 걷기를 끝내면 하루의 걸음에 걷기가 한 번 들어갑니다(addWalkStepOnce). 다른 시험이 보는 축을 바꾸지 않게 되돌립니다.
    private var stepsBefore: List<DailyStep> = DailyStep.DEFAULTS

    @Before
    fun setUp() {
        stepsBefore = runBlocking { container.settingsStore.getDailySteps() }
        reset()
    }

    @After
    fun tearDown() {
        reset()
        runBlocking { container.settingsStore.setDailySteps(stepsBefore) }
    }

    private fun reset() = runBlocking {
        walkDao.clearAll()
        container.database.deletionDao().clearAll()
    }

    @Test
    fun aFinishedWalkKeepsItsPathAndDistance() = runBlocking {
        val walk = repository.startWalk(now = 1_000_000L)
        repository.saveProgress(walk.syncId, route.take(2), distanceMeters = 70.0, now = 1_060_000L)

        val finished = repository.finishWalk(walk.syncId, route, distanceMeters = 180.0, endedAt = 1_900_000L)

        assertNotNull(finished)
        assertEquals(1_900_000L, finished!!.endedAt)
        assertEquals(180, finished.distanceMeters)
        assertEquals(route, WalkPath.decode(finished.path))
        assertEquals("허락받지 않은 Health Connect에서 걸음을 지어내면 안 됩니다", 0, finished.steps)
        assertTrue(finished.isFinished)
    }

    @Test
    fun startingAgainContinuesTheSameWalk() = runBlocking {
        // 기록 서비스가 다시 떠도 걷기가 둘로 갈라지면 안 됩니다.
        val first = repository.startWalk(now = 1_000_000L)
        val again = repository.startWalk(now = 1_300_000L)

        assertEquals(first.syncId, again.syncId)
        assertEquals(1, walkDao.getAll().size)
    }

    @Test
    fun aMistakenTapLeavesNothing() = runBlocking {
        val walk = repository.startWalk(now = 1_000_000L)

        val finished = repository.finishWalk(walk.syncId, emptyList(), distanceMeters = 0.0, endedAt = 1_020_000L)

        assertNull(finished)
        assertTrue(walkDao.getAll().isEmpty())
    }

    @Test
    fun anInterruptedWalkEndsWhereItWasLastSaved() = runBlocking {
        // 서비스가 거둬졌습니다. 마지막으로 길을 적은 때가 끝난 때입니다.
        val walk = repository.startWalk(now = 1_000_000L)
        repository.saveProgress(walk.syncId, route, distanceMeters = 180.0, now = 1_600_000L)

        repository.finishInterrupted()

        val finished = walkDao.getBySyncId(walk.syncId)!!
        assertEquals(1_600_000L, finished.endedAt)
        assertEquals(route, WalkPath.decode(finished.path))
    }

    @Test
    fun deletingLeavesATombstone() = runBlocking {
        val walk = repository.startWalk(now = 1_000_000L)
        repository.finishWalk(walk.syncId, route, distanceMeters = 180.0, endedAt = 1_900_000L)

        repository.deleteWalk(walk.syncId)

        assertTrue(walkDao.getAll().isEmpty())
        val tombstone = container.database.deletionDao().getAll().single { it.syncId == walk.syncId }
        assertEquals(SyncEntityType.WALK, tombstone.entityType)
    }

    @Test
    fun replacingAllDataKeepsTheWalkInProgress() = runBlocking {
        // 걷는 사이에 "서버에서 가져오기"나 전체 병합으로 기기 데이터가 통째로 바뀌었습니다.
        val ongoing = repository.startWalk(now = 1_000_000L)
        repository.saveProgress(ongoing.syncId, route.take(2), distanceMeters = 70.0, now = 1_060_000L)
        val arrived = WalkEntity(
            id = 1,
            syncId = "from-server",
            startedAt = 500_000L,
            endedAt = 900_000L,
            distanceMeters = 400,
            path = WalkPath.encode(route),
            updatedAt = 900_000L
        )
        val snapshot = container.syncCoordinator.currentSnapshot().copy(walks = listOf(arrived))

        container.appDataImporter.importSnapshot(snapshot)

        val walks = walkDao.getAll().associateBy { it.syncId }
        assertNotNull("받은 걷기", walks["from-server"])
        val kept = walks[ongoing.syncId]
        assertNotNull("걷던 길이 지워졌습니다", kept)
        assertEquals(0L, kept!!.endedAt)
        // 기록하는 서비스는 번호가 아니라 syncId로 이어 적습니다. 번호가 바뀌어도 이어집니다.
        repository.saveProgress(ongoing.syncId, route, distanceMeters = 180.0, now = 1_200_000L)
        assertEquals(route, WalkPath.decode(walkDao.getBySyncId(ongoing.syncId)!!.path))
    }
}
