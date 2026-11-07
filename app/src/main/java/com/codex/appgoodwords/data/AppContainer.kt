package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.disk.DiskCache
import com.codex.appgoodwords.BuildConfig
import com.codex.appgoodwords.work.ReminderScheduler
import okhttp3.Dispatcher
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
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24
            )
            .fallbackToDestructiveMigration()
            .build()
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(appContext)
    }

    val healthSteps: HealthSteps by lazy {
        HealthSteps(appContext)
    }

    /** 걸음 수와 걷기. 화면, 기록 서비스, 동기화가 같은 것을 씁니다. */
    val walkRepository: WalkRepository by lazy {
        WalkRepository(database = database, settingsStore = settingsStore, healthSteps = healthSteps)
    }

    /** 설정의 "앱 업데이트"가 씁니다. 동기화와 같은 HTTPS 규칙을 따릅니다. */
    val appUpdateClient: AppUpdateClient by lazy {
        AppUpdateClient()
    }

    /** 설치 파일 받기는 안드로이드에 맡깁니다. 다른 앱으로 가도 이어집니다. */
    val appUpdateDownloads: AppUpdateDownloads by lazy {
        AppUpdateDownloads(appContext)
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

    /**
     * 지도 타일(OpenStreetMap). 그 타일 서버의 쓰는 규칙을 지킵니다 — 앱을 밝히는 User-Agent, 한 서버에 동시에 둘까지만,
     * 받은 것은 캐시 기한까지 다시 받지 않음, 미리 받아 두지 않음(보이는 칸만). 첨부와 캐시 자리를 나눕니다.
     */
    val mapTileImageLoader: ImageLoader by lazy {
        val userAgent = "AppGoodWords/${BuildConfig.VERSION_NAME} (Android; personal walking log)"
        ImageLoader.Builder(appContext)
            .okHttpClient(
                OkHttpClient.Builder()
                    .dispatcher(Dispatcher().apply { maxRequestsPerHost = 2 })
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
                    }
                    .build()
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve("osm_tiles"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(true)
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

    /** 오늘 하루를 보고 건네는 한마디. 돌아보기와 같은 AI 설정을 그대로 씁니다. */
    val dailyCoachCoordinator: DailyCoachCoordinator by lazy {
        DailyCoachCoordinator(
            database = database,
            settingsStore = settingsStore,
            repository = repository,
            client = aiFeedbackClient,
            handOver = { reminderScheduler.handOverDailyCoach() }
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
