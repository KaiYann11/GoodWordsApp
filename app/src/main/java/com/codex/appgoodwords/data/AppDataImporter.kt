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
    val routineMemoCount: Int,
    val diaryCount: Int = 0,
    val todoCount: Int = 0
)

class AppDataImporter(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
    private val todoAlarmScheduler: TodoAlarmScheduler? = null
) {
    /** 파일 내용으로 기기 데이터를 통째로 바꿉니다. */
    suspend fun import(uri: Uri): AppImportResult = importSnapshot(readSnapshot(uri))

    /**
     * 파일과 기기 데이터를 레코드 단위로 합칩니다. 서버 없이 두 기기를 맞출 때 씁니다.
     *
     * 서버의 `/api/sync`와 같은 규칙입니다. 다만 서버는 쓰기를 직렬화해 주는데
     * 파일에는 그런 게 없으므로, 두 기기가 서로의 파일을 동시에 넣으면
     * 각자 자기 쪽이 최신인 결과를 갖게 됩니다.
     */
    suspend fun mergeFromFile(uri: Uri, local: AppDataSnapshot): AppImportResult =
        importSnapshot(SyncMerger.merge(local = local, remote = readSnapshot(uri)))

    private fun readSnapshot(uri: Uri): AppDataSnapshot {
        val jsonText = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("가져올 파일을 열 수 없습니다.")

        return AppDataJson.fromJsonText(jsonText)
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

        // 들어온 할 일에는 예약이 없다. 예전 할 일의 예약은 id가 다시 매겨지면 가리킬 곳을 잃는다.
        val previousReminders = database.todoDao().getPendingReminders()

        database.withTransaction {
            database.todoDao().clearAll()
            database.diaryDao().clearAll()
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

            if (snapshot.diaries.isNotEmpty()) {
                database.diaryDao().insertAll(snapshot.diaries)
            }

            if (snapshot.todos.isNotEmpty()) {
                database.todoDao().insertAll(snapshot.todos)
            }
        }

        // 예약은 DB 밖(AlarmManager)에 있어서 함께 지워지지 않는다.
        // 옛 예약을 지우고 새 목록으로 다시 걸지 않으면, 지운 할 일의 알람이 그대로 울린다.
        todoAlarmScheduler?.let { scheduler ->
            previousReminders.forEach(scheduler::cancel)
            scheduler.syncAll(database.todoDao().getPendingReminders())
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
            routineMemoCount = snapshot.routineMemos.size,
            diaryCount = snapshot.diaries.size,
            todoCount = snapshot.todos.size
        )
    }
}
