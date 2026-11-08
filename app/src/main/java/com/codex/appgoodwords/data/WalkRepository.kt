package com.codex.appgoodwords.data

import android.util.Log
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * 걸음 수와 걷기를 적고 읽습니다(사용자가 정함, 2026-11-07).
 *
 * 글귀·루틴을 다루는 [AppRepository]와 나눠 둡니다. 걷기는 화면 밖의 기록 서비스
 * ([com.codex.appgoodwords.work.WalkRecordingService])와 Health Connect가 함께 쓰는 자리라, 한곳에 모아야 둘이
 * 같은 규칙으로 적습니다.
 */
class WalkRepository(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val healthSteps: HealthSteps
) {
    private val stepCountDao: StepCountDao
        get() = database.stepCountDao()
    private val walkDao: WalkDao
        get() = database.walkDao()

    fun observeStepCounts(): Flow<List<StepCountEntity>> = stepCountDao.observeAll()

    fun observeWalks(): Flow<List<WalkEntity>> = walkDao.observeAll()

    /**
     * Health Connect에서 지난 30일 걸음을 다시 읽어 적습니다. 허락받지 않았으면 아무것도 하지 않습니다.
     *
     * **그날 것보다 적게 읽혔으면 고치지 않습니다.** 같은 날을 다른 기기가 더 많이 본 기록이 병합으로 와 있을 수 있는데,
     * 그것을 이 기기가 본 적은 수로 덮으면 걸은 날이 사라집니다. 그날 걸음은 하루가 끝날 때까지 늘기만 합니다.
     *
     * @return 고친 날수. 읽지 못했으면 null입니다.
     */
    suspend fun refreshStepCounts(today: LocalDate = LocalDate.now(), now: Long = System.currentTimeMillis()): Int? {
        if (!healthSteps.hasPermission()) return null
        val read = try {
            healthSteps.dailySteps(today.minusDays(HealthSteps.REFRESH_DAYS - 1), today)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // 권한을 막 거둬 갔거나 Health Connect가 바쁠 때입니다. 다음에 앱을 열 때 다시 읽습니다.
            Log.w(TAG, "걸음 수를 읽지 못했습니다 — ${failure.javaClass.simpleName}: ${failure.message}")
            return null
        }

        val byDate = stepCountDao.getAll().groupBy { it.date }
        val changed = read.mapNotNull { (date, steps) ->
            val existing = byDate[date.toString()]?.maxByOrNull { it.steps }
            when {
                existing == null -> StepCountEntity(date = date.toString(), steps = steps, updatedAt = now, createdAt = now)
                steps > existing.steps -> existing.copy(steps = steps, updatedAt = now)
                else -> null
            }
        }
        if (changed.isNotEmpty()) stepCountDao.insertAll(changed)
        refreshRecentWalkSteps(now)
        return changed.size
    }

    /**
     * 사흘 안에 끝난 걷기의 걸음 수를 다시 읽습니다. 삼성 헬스는 걸음을 몇 분씩 모았다가 넘겨서, 끝내는 순간에는
     * 마지막 몇 분이 빠져 있기도 합니다.
     */
    private suspend fun refreshRecentWalkSteps(now: Long) {
        walkDao.getFinished()
            .filter { now - it.endedAt < RECENT_WALK_MILLIS }
            .forEach { walk ->
                val steps = runCatching {
                    healthSteps.stepsBetween(Instant.ofEpochMilli(walk.startedAt), Instant.ofEpochMilli(walk.endedAt))
                }.getOrNull() ?: return@forEach
                if (steps > walk.steps) walkDao.updateSteps(walk.syncId, steps, now)
            }
    }

    /** Health Connect를 처음 이었을 때. 걷기를 하루의 걸음에 넣고(한 번만) 바로 읽어 옵니다. */
    suspend fun onHealthConnected() {
        if (!healthSteps.hasPermission()) return
        settingsStore.addWalkStepOnce()
        refreshStepCounts()
    }

    /**
     * 걷기를 시작합니다. 끝나지 않은 걷기가 있으면 그것을 이어 갑니다 — 기록 서비스가 다시 떠도 걷기가 둘로 갈라지지 않게.
     */
    suspend fun startWalk(now: Long = System.currentTimeMillis()): WalkEntity {
        walkDao.getUnfinished().firstOrNull()?.let { return it }
        val walk = WalkEntity(startedAt = now, endedAt = 0L, updatedAt = now, createdAt = now)
        val id = walkDao.insert(walk)
        return walk.copy(id = id)
    }

    suspend fun saveProgress(syncId: String, points: List<GeoPoint>, distanceMeters: Double, now: Long = System.currentTimeMillis()) {
        walkDao.updateProgress(syncId, WalkPath.encode(points), distanceMeters.toInt(), now)
    }

    /**
     * 걷기를 끝냅니다. 걸음 수는 Health Connect에서 그 동안을 읽습니다(허락받지 않았으면 0).
     *
     * 점 하나 없이 1분도 안 된 것은 버립니다 — 잘못 누른 것입니다. 남긴 걷기가 처음이면 걷기를 하루의 걸음에 넣습니다.
     *
     * @return 남긴 걷기. 버렸으면 null입니다.
     */
    suspend fun finishWalk(
        syncId: String,
        points: List<GeoPoint>,
        distanceMeters: Double,
        endedAt: Long = System.currentTimeMillis()
    ): WalkEntity? {
        val walk = walkDao.getBySyncId(syncId) ?: return null
        val end = endedAt.coerceAtLeast(walk.startedAt)
        if (points.isEmpty() && end - walk.startedAt < ACCIDENTAL_MILLIS) {
            walkDao.deleteBySyncIds(listOf(syncId))
            return null
        }
        val steps = if (healthSteps.hasPermission()) {
            runCatching {
                healthSteps.stepsBetween(Instant.ofEpochMilli(walk.startedAt), Instant.ofEpochMilli(end))
            }.getOrDefault(0)
        } else {
            0
        }
        walkDao.finish(
            syncId = syncId,
            endedAt = end,
            distanceMeters = distanceMeters.toInt(),
            steps = steps,
            path = WalkPath.encode(points),
            updatedAt = System.currentTimeMillis()
        )
        settingsStore.addWalkStepOnce()
        return walkDao.getBySyncId(syncId)
    }

    /**
     * 기록 서비스가 죽어 끝나지 않은 채 남은 걷기를, 마지막으로 길을 적은 때에 맞춰 마무리합니다.
     * 지금 기록하는 중이면 부르지 않습니다.
     */
    suspend fun finishInterrupted() {
        walkDao.getUnfinished().forEach { walk ->
            val points = WalkPath.decode(walk.path)
            finishWalk(
                syncId = walk.syncId,
                points = points,
                distanceMeters = walk.distanceMeters.toDouble(),
                endedAt = walk.updatedAt
            )
        }
    }

    /** 걷기를 지웁니다. 삭제 표식을 남겨야 다른 기기와 서버에서도 지워집니다. */
    suspend fun deleteWalk(syncId: String) {
        val walk = walkDao.getBySyncId(syncId) ?: return
        walkDao.deleteBySyncIds(listOf(syncId))
        if (walk.isFinished) {
            database.deletionDao().insert(
                DeletionEntity(syncId = syncId, entityType = SyncEntityType.WALK, deletedAt = System.currentTimeMillis())
            )
        }
    }

    private companion object {
        const val TAG = "WalkRepository"
        const val RECENT_WALK_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val ACCIDENTAL_MILLIS = 60_000L
    }
}
