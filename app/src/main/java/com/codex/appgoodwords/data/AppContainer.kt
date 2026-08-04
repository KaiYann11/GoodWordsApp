package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import com.codex.appgoodwords.work.ReminderScheduler

class AppContainer(
    context: Context
) {
    val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "app-good-words.db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(appContext)
    }

    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(appContext)
    }

    val serverSyncClient: ServerSyncClient by lazy {
        ServerSyncClient()
    }

    val syncBackupStore: SyncBackupStore by lazy {
        SyncBackupStore(appContext)
    }

    val repository: AppRepository by lazy {
        AppRepository(
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher()
        )
    }

    val appDataExporter: AppDataExporter by lazy {
        AppDataExporter(appContext)
    }

    val appDataImporter: AppDataImporter by lazy {
        AppDataImporter(
            context = appContext,
            database = database,
            settingsStore = settingsStore,
            reminderScheduler = reminderScheduler
        )
    }
}
