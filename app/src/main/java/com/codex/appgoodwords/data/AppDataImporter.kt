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

    suspend fun importSnapshot(snapshot: AppDataSnapshot): AppImportResult {
        val settings = snapshot.settings.copy(
            intervalMinutes = snapshot.settings.effectiveIntervalMinutes
        )

        database.withTransaction {
            database.routineMemoDao().clearAll()
            database.routineCheckDao().clearAll()
            database.routineDao().clearAll()
            database.exposureEventDao().clearAll()
            database.contentItemDao().clearAll()

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

        settingsStore.updateSettings(settings)
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
