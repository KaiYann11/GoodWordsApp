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
    val todoCount: Int = 0,
    val bookCount: Int = 0
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

    /**
     * 서버가 보내온 "바뀐 것만"을 기존 데이터 위에 얹습니다.
     *
     * [importSnapshot]과 달리 **지우고 다시 넣지 않습니다.** 부분 응답에 없는 레코드는
     * 지워진 것이 아니라 안 바뀐 것이라, 통째로 갈아엎으면 담기지 않은 기록이 전부 사라집니다.
     *
     * 짝은 `syncId`로 찾고 **이 기기의 숫자 id는 그대로 둡니다.** 들어온 레코드에는 서버의 번호가
     * 붙어 있는데, 그걸 그대로 쓰면 번호가 겹쳐 엉뚱한 레코드를 덮어씁니다.
     */
    suspend fun applyDelta(incoming: AppDataSnapshot): AppImportResult {
        val itemDao = database.contentItemDao()
        val routineDao = database.routineDao()
        val memoDao = database.routineMemoDao()
        val checkDao = database.routineCheckDao()
        val eventDao = database.exposureEventDao()
        val diaryDao = database.diaryDao()
        val todoDao = database.todoDao()
        val bookDao = database.bookDao()

        val previousReminders = database.todoDao().getPendingReminders()
        val deletedSyncIds = incoming.deletions.map { it.syncId }

        database.withTransaction {
            if (incoming.deletions.isNotEmpty()) {
                database.deletionDao().insertAll(incoming.deletions)
                // 지운 표식이 온 레코드는 이 기기에서도 지웁니다. 부분 응답에는 남은 것만 오기 때문입니다.
                itemDao.deleteBySyncIds(deletedSyncIds)
                eventDao.deleteBySyncIds(deletedSyncIds)
                routineDao.deleteBySyncIds(deletedSyncIds)
                checkDao.deleteBySyncIds(deletedSyncIds)
                memoDao.deleteBySyncIds(deletedSyncIds)
                diaryDao.deleteBySyncIds(deletedSyncIds)
                todoDao.deleteBySyncIds(deletedSyncIds)
                bookDao.deleteBySyncIds(deletedSyncIds)
            }

            val localItemIds = itemDao.getAll().associate { it.syncId to it.id }
            itemDao.insertAll(incoming.items.map { it.copy(id = localItemIds[it.syncId] ?: 0L) })

            val localRoutineIds = routineDao.getAll().associate { it.syncId to it.id }
            routineDao.insertAll(incoming.routines.map { it.copy(id = localRoutineIds[it.syncId] ?: 0L) })

            // 자식은 부모의 이 기기 번호로 다시 이어야 합니다. 서버 번호를 그대로 두면 남을 가리킵니다.
            val itemIdBySyncId = itemDao.getAll().associate { it.syncId to it.id }
            val routineIdBySyncId = routineDao.getAll().associate { it.syncId to it.id }

            val localEventIds = eventDao.getAll().associate { it.syncId to it.id }
            eventDao.insertAll(
                incoming.events.map { event ->
                    event.copy(
                        id = localEventIds[event.syncId] ?: 0L,
                        contentItemId = itemIdBySyncId[event.contentItemSyncId] ?: 0L
                    )
                }
            )

            val localCheckIds = checkDao.getAll().associate { it.syncId to it.id }
            checkDao.insertAll(
                incoming.routineChecks.map { check ->
                    check.copy(
                        id = localCheckIds[check.syncId] ?: 0L,
                        routineId = routineIdBySyncId[check.routineSyncId] ?: 0L
                    )
                }
            )

            val localMemoIds = memoDao.getAll().associate { it.syncId to it.id }
            memoDao.insertAll(
                // 붙을 루틴이 없는 메모는 루틴 화면에서 볼 방법이 없습니다.
                incoming.routineMemos
                    .filter { routineIdBySyncId.containsKey(it.routineSyncId) }
                    .map { memo ->
                        memo.copy(
                            id = localMemoIds[memo.syncId] ?: 0L,
                            routineId = routineIdBySyncId.getValue(memo.routineSyncId)
                        )
                    }
            )

            val localDiaryIds = diaryDao.getAll().associate { it.syncId to it.id }
            diaryDao.insertAll(incoming.diaries.map { it.copy(id = localDiaryIds[it.syncId] ?: 0L) })

            val localTodoIds = todoDao.getAll().associate { it.syncId to it.id }
            todoDao.insertAll(incoming.todos.map { it.copy(id = localTodoIds[it.syncId] ?: 0L) })

            val localBookIds = bookDao.getAll().associate { it.syncId to it.id }
            bookDao.insertAll(incoming.books.map { it.copy(id = localBookIds[it.syncId] ?: 0L) })
        }

        // 예약은 DB 밖(AlarmManager)에 있어서 함께 바뀌지 않습니다.
        todoAlarmScheduler?.let { scheduler ->
            previousReminders.forEach(scheduler::cancel)
            scheduler.syncAll(todoDao.getPendingReminders())
        }

        // 설정은 레코드가 아니라 한 덩어리라, 서버가 더 최근일 때만 덮어씁니다.
        if (incoming.settingsUpdatedAt > settingsStore.getSettingsUpdatedAt()) {
            val settings = incoming.settings.copy(
                intervalMinutes = incoming.settings.effectiveIntervalMinutes
            )
            settingsStore.updateSettings(settings, incoming.settingsUpdatedAt)
            reminderScheduler.sync(settings)
        }

        return AppImportResult(
            itemCount = incoming.items.size,
            eventCount = incoming.events.size,
            routineCount = incoming.routines.size,
            routineCheckCount = incoming.routineChecks.size,
            routineMemoCount = incoming.routineMemos.size,
            diaryCount = incoming.diaries.size,
            todoCount = incoming.todos.size,
            bookCount = incoming.books.size
        )
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
            database.bookDao().clearAll()
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

            if (snapshot.books.isNotEmpty()) {
                database.bookDao().insertAll(snapshot.books)
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

        // 통째로 바꿨으니 서버와 어디까지 맞췄는지는 더 이상 믿을 수 없습니다.
        // 다음 병합이 전체를 주고받고 나서 새 기준을 적습니다.
        settingsStore.clearSyncCursor()

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
            todoCount = snapshot.todos.size,
            bookCount = snapshot.books.size
        )
    }
}
