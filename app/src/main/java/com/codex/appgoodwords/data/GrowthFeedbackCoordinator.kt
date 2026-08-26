package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 기록을 추려 AI에게 묻고, 받은 글을 저장하기까지.
 *
 * 화면과 배경 작업이 같은 길을 쓰게 하려고 한곳에 모았습니다. 수동으로 누르든 주기가 돌아와
 * 저절로 돌든, 무엇이 나가고 무엇이 저장되는지가 같아야 합니다.
 */
class GrowthFeedbackCoordinator(
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val client: AiFeedbackClient = AiFeedbackClient()
) {
    /**
     * 보내기 전에 무엇이 나가는지 보여 주기 위한 미리 보기.
     *
     * 실제로 보내는 글과 **같은 함수**로 만듭니다. 미리 보기만 따로 만들면
     * 화면에 보이는 것과 나가는 것이 언젠가 어긋납니다.
     */
    suspend fun preview(period: ReportPeriod, today: LocalDate = LocalDate.now()): String {
        val aiSettings = settingsStore.getAiFeedbackSettings()
        return GrowthPrompt.userPrompt(buildDigest(period, today, aiSettings.includeDiaryBody))
    }

    /**
     * 피드백 한 편을 만들어 저장합니다.
     *
     * @throws IllegalStateException 돌아볼 기록이 없거나, 답을 읽어 내지 못했을 때
     */
    suspend fun generate(
        period: ReportPeriod,
        today: LocalDate = LocalDate.now()
    ): GrowthReportEntity {
        val aiSettings = settingsStore.getAiFeedbackSettings()
        val syncSettings = settingsStore.getServerSyncSettings()
        val digest = buildDigest(period, today, aiSettings.includeDiaryBody)
        check(!digest.isEmpty) {
            "돌아볼 기록이 아직 없습니다. 루틴을 밟거나 일기를 남긴 뒤에 다시 눌러 주세요."
        }

        val rawText = try {
            client.ask(
                system = GrowthPrompt.systemPrompt(),
                user = GrowthPrompt.userPrompt(digest),
                model = aiSettings.model,
                aiSettings = aiSettings,
                syncSettings = syncSettings
            )
        } catch (failure: Exception) {
            settingsStore.recordAiFeedbackResult(
                runAt = settingsStore.getAiFeedbackSettings().lastRunAt,
                error = failure.message ?: "AI 피드백을 받지 못했습니다."
            )
            throw failure
        }

        val report = GrowthReportParser.parse(
            rawText = rawText,
            period = period,
            periodStart = digest.from.toString(),
            periodEnd = digest.to.toString(),
            model = aiSettings.model
        )
        if (report == null) {
            val message = "AI가 보낸 답을 읽지 못했습니다. 잠시 뒤 다시 시도해 주세요."
            settingsStore.recordAiFeedbackResult(
                runAt = settingsStore.getAiFeedbackSettings().lastRunAt,
                error = message
            )
            error(message)
        }

        val id = database.growthReportDao().insert(report)
        settingsStore.recordAiFeedbackResult(runAt = System.currentTimeMillis(), error = "")
        return report.copy(id = id)
    }

    /** 주기가 돌아왔는지. 배경 작업이 하루에 여러 번 깨어나도 한 번만 만듭니다. */
    suspend fun isDue(today: LocalDate = LocalDate.now()): Boolean =
        settingsStore.getAiFeedbackSettings().isDue(System.currentTimeMillis(), today)

    private suspend fun buildDigest(
        period: ReportPeriod,
        today: LocalDate,
        includeDiaryBody: Boolean
    ): GrowthDigest = GrowthPrompt.digest(
        period = period,
        today = today,
        routines = database.routineDao().getAll(),
        routineChecks = database.routineCheckDao().getAll(),
        todos = database.todoDao().getAll(),
        diaries = database.diaryDao().getAll(),
        items = database.contentItemDao().getAll(),
        events = database.exposureEventDao().getAll(),
        books = database.bookDao().getAll(),
        includeDiaryBody = includeDiaryBody
    )
}
