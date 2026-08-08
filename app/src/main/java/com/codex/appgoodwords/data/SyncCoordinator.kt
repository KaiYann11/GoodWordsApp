package com.codex.appgoodwords.data

import kotlinx.coroutines.CancellationException

data class ServerSyncResult(
    val counts: AppImportResult,
    val backup: SyncBackup?
)

/**
 * 서버와 병합하는 한 번의 절차를 모아 둡니다.
 *
 * 화면에서 누르는 병합과 배경에서 도는 자동 동기화가 같은 길을 쓰도록 여기에 둡니다.
 * 배경 작업에는 화면이 없어 ViewModel의 StateFlow가 비어 있으므로, 데이터는 DAO에서 직접 읽습니다.
 */
class SyncCoordinator(
    private val settingsStore: SettingsStore,
    private val serverSyncClient: ServerSyncClient,
    private val syncBackupStore: SyncBackupStore,
    private val appDataImporter: AppDataImporter,
    private val database: AppDatabase
) {
    suspend fun currentSnapshot(): AppDataSnapshot = AppDataSnapshot(
        items = database.contentItemDao().getAll(),
        events = database.exposureEventDao().getAll(),
        routines = database.routineDao().getAll(),
        routineChecks = database.routineCheckDao().getAll(),
        routineMemos = database.routineMemoDao().getAll(),
        settings = settingsStore.getSettings(),
        settingsUpdatedAt = settingsStore.getSettingsUpdatedAt(),
        // 삭제 표식을 함께 보내야 다른 기기에서 지운 항목이 되살아나지 않는다.
        deletions = database.deletionDao().getAll()
    )

    /**
     * 서버와 레코드 단위로 합칩니다.
     *
     * 업로드/가져오기와 달리 어느 쪽도 통째로 지우지 않으므로 두 기기에서 각각 편집해도 살아남습니다.
     * 그래도 로컬 DB를 교체하는 동작이라 직전 상태는 백업해 둡니다.
     */
    suspend fun merge(backupKind: SyncBackupKind): ServerSyncResult {
        try {
            val syncSettings = settingsStore.getServerSyncSettings()
            val merged = serverSyncClient.mergeSnapshot(
                settings = syncSettings,
                snapshot = currentSnapshot()
            )
            val backup = syncBackupStore.save(backupKind, currentSnapshot())
            val counts = appDataImporter.importSnapshot(merged)
            pruneOldDeletions()
            settingsStore.recordSyncResult(System.currentTimeMillis(), error = "")
            return ServerSyncResult(counts = counts, backup = backup)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // 배경 동기화는 실패해도 화면에 뜨지 않으므로, 설정 화면에서 볼 수 있게 남긴다.
            settingsStore.recordSyncResult(
                System.currentTimeMillis(),
                error = failure.message.orEmpty().ifBlank { "동기화에 실패했습니다." }
            )
            throw failure
        }
    }

    /**
     * 삭제 표식은 지운 항목이 되살아나지 않게 하려고 남기는 것이라 계속 쌓입니다.
     * 양쪽이 같은 기간을 쓰지 않으면 한쪽이 지운 표식을 다른 쪽이 되돌려 주므로,
     * 서버도 같은 [DELETION_RETENTION_DAYS]를 씁니다.
     */
    private suspend fun pruneOldDeletions() {
        val before = System.currentTimeMillis() - DELETION_RETENTION_DAYS * MILLIS_PER_DAY
        database.deletionDao().deleteOlderThan(before)
    }

    companion object {
        /** 이 기간보다 오래 꺼져 있던 기기가 다시 붙으면, 그 사이 지운 항목이 되살아날 수 있다. */
        const val DELETION_RETENTION_DAYS = 90L
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
