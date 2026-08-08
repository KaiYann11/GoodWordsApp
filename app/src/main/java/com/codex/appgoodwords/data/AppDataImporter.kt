package com.codex.appgoodwords.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.codex.appgoodwords.work.ReminderScheduler

data class AppImportResult(
    val itemCount: Int,
    val eventCount: Int,
    val routineCount: Int,
    val routineCheckCount: Int,
    val routineMemoCount: Int
)

class AppDataImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler
) {
    suspend fun import(uri: Uri): AppImportResult {
        val jsonText = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("가져올 파일을 열 수 없습니다.")

        return importSnapshot(AppDataJson.fromJsonText(jsonText))
    }

    suspend fun importSnapshot(incoming: AppDataSnapshot): AppImportResult {
        // 병합 결과에는 서로 다른 기기의 같은 숫자 id가 섞여 있어, 그대로 넣으면 서로를 덮어쓴다.
        val snapshot = SnapshotReindexer.reindex(incoming)
        val settings = snapshot.settings.copy(
            intervalMinutes = snapshot.settings.effectiveIntervalMinutes
        )
        // id를 다시 매기므로, 위젯이 보던 글귀는 번호가 아니라 syncId로 따라가야 그대로 남는다.
        val widgetItemSyncId = settingsStore.getWidgetContentId()
            .takeIf { it != 0L }
            ?.let { database.contentItemDao().getById(it)?.syncId }

        database.withTransaction {
            database.routineMemoDao().clearAll()
            database.routineCheckDao().clearAll()
            database.routineDao().clearAll()
            database.exposureEventDao().clearAll()
            database.contentItemDao().clearAll()
            database.deletionDao().clearAll()

            if (snapshot.deletions.isNotEmpty()) {
                database.deletionDao().insertAll(snapshot.deletions)
            }

            if (snapshot.items.isNotEmpty()) {
                database.contentItemDao().insertAll(snapshot.items)
            }

            if (snapshot.events.isNotEmpty()) {
                database.exposureEventDao().insertAll(snapshot.events)
            }

            if (snapshot.routines.isNotEmpty()) {
                database.routineDao().insertAll(snapshot.routines)
            }

            if (snapshot.routineChecks.isNotEmpty()) {
                database.routineCheckDao().insertAll(snapshot.routineChecks)
            }

            if (snapshot.routineMemos.isNotEmpty()) {
                database.routineMemoDao().insertAll(snapshot.routineMemos)
            }
        }

        settingsStore.setWidgetContentId(
            snapshot.items.firstOrNull { it.syncId == widgetItemSyncId }?.id ?: 0L
        )

        // 병합 결과를 되쓸 때 설정 시각을 그대로 유지해야 다음 병합에서 뒤집히지 않는다.
        if (snapshot.settingsUpdatedAt > 0L) {
            settingsStore.updateSettings(settings, snapshot.settingsUpdatedAt)
        } else {
            settingsStore.updateSettings(settings)
        }
        reminderScheduler.sync(settings)

        return AppImportResult(
            itemCount = snapshot.items.size,
            eventCount = snapshot.events.size,
            routineCount = snapshot.routines.size,
            routineCheckCount = snapshot.routineChecks.size,
            routineMemoCount = snapshot.routineMemos.size
        )
    }
}
