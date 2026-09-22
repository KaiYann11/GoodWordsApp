package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import com.codex.appgoodwords.work.ReminderScheduler
import okhttp3.OkHttpClient

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
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21
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

    val aiFeedbackClient: AiFeedbackClient by lazy { AiFeedbackClient() }

    val syncBackupStore: SyncBackupStore by lazy {
        SyncBackupStore(appContext)
    }

    val repository: AppRepository by lazy {
        AppRepository(
            database = database,
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            contentMemoDao = database.contentMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher(),
            deletionDao = database.deletionDao(),
            diaryDao = database.diaryDao(),
            todoDao = database.todoDao(),
            bookDao = database.bookDao(),
            growthReportDao = database.growthReportDao(),
            moodLogDao = database.moodLogDao(),
            bookReadingLogDao = database.bookReadingLogDao()
        )
    }

    val todoAlarmScheduler: TodoAlarmScheduler by lazy {
        TodoAlarmScheduler(appContext)
    }

    val appDataExporter: AppDataExporter by lazy {
        AppDataExporter(appContext)
    }

    val appDataImporter: AppDataImporter by lazy {
        AppDataImporter(
            context = appContext,
            database = database,
            settingsStore = settingsStore,
            reminderScheduler = reminderScheduler,
            todoAlarmScheduler = todoAlarmScheduler
        )
    }

    val attachmentClient: AttachmentClient by lazy { AttachmentClient(appContext) }

    // 인증된 첨부 요청이 다른 주소로 넘어가 접속 키를 보내지 않도록 합니다.
    val attachmentImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .okHttpClient(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build())
            .build()
    }

    val attachmentUploader: AttachmentUploader by lazy {
        AttachmentUploader(client = attachmentClient, database = database)
    }

    val growthFeedbackCoordinator: GrowthFeedbackCoordinator by lazy {
        GrowthFeedbackCoordinator(
            database = database,
            settingsStore = settingsStore,
            client = aiFeedbackClient
        )
    }

    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(
            settingsStore = settingsStore,
            serverSyncClient = serverSyncClient,
            syncBackupStore = syncBackupStore,
            appDataImporter = appDataImporter,
            database = database,
            attachmentUploader = attachmentUploader
        )
    }
}
