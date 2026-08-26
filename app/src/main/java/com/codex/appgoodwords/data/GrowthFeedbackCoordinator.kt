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
     *
     * 이 글은 그대로 복사해 채팅창에 붙여넣을 수도 있습니다. 그래서 일러 주는 말까지
     * 함께 보여 줍니다. 답의 형식을 정하는 것이 그 말이라, 빠뜨리면 읽어 낼 수 없는 답이 옵니다.
     */
    suspend fun preview(period: ReportPeriod, today: LocalDate = LocalDate.now()): String {
        val aiSettings = settingsStore.getAiFeedbackSettings()
        return GrowthPrompt.chatPrompt(buildDigest(period, today, aiSettings.includeDiaryBody))
    }

    /**
     * 물음을 복사해 갔다고 적어 둡니다.
     *
     * 돌아와 답을 붙여넣을 때 어느 구간을 보고 쓴 글인지 알아야 합니다. 채팅 앱에 다녀오는
     * 사이 앱이 꺼질 수 있어 화면이 아니라 설정에 남깁니다.
     */
    suspend fun markPromptCopied(period: ReportPeriod, today: LocalDate = LocalDate.now()) {
        settingsStore.setPendingGrowthPrompt(period, today)
    }

    suspend fun pendingPrompt(): PendingGrowthPrompt? = settingsStore.getPendingGrowthPrompt()

    /**
     * 채팅창에서 받아 온 답을 그대로 받아 한 편으로 적습니다.
     *
     * AI에 연결하지 못하는 자리를 위한 길입니다. 앱이 못 부를 뿐이지 사용자는 자기 계정으로
     * 얼마든지 물어볼 수 있는데, 그렇게 받은 답을 앱에 남길 방법이 없었습니다.
     *
     * **형식이 어긋나도 버리지 않습니다.** 사람이 직접 옮겨 온 글입니다. 갈래를 나눠 읽지
     * 못하면 통째로 가이드에 담아 둡니다. 못 읽었다며 되돌려 주면 사용자가 받아 온 답이
     * 그 자리에서 사라집니다.
     *
     * @throws IllegalStateException 붙여넣은 글이 비어 있을 때
     */
    suspend fun saveManualAnswer(
        rawText: String,
        today: LocalDate = LocalDate.now()
    ): GrowthReportEntity {
        val trimmed = rawText.trim()
        check(trimmed.isNotBlank()) { "붙여넣은 답이 비어 있습니다." }

        val pending = settingsStore.getPendingGrowthPrompt()
        val period = pending?.period ?: ReportPeriod.MANUAL
        val from = pending?.from ?: today.minusDays((period.days - 1).toLong())
        val to = pending?.to ?: today

        val parsed = GrowthReportParser.parse(
            rawText = trimmed,
            period = period,
            periodStart = from.toString(),
            periodEnd = to.toString(),
            model = MANUAL_MODEL
        )
        val report = parsed ?: GrowthReportEntity(
            period = period.name,
            periodStart = from.toString(),
            periodEnd = to.toString(),
            model = MANUAL_MODEL,
            guide = trimmed.take(MANUAL_ANSWER_LIMIT)
        )

        val id = database.growthReportDao().insert(report)
        settingsStore.clearPendingGrowthPrompt()
        settingsStore.recordAiFeedbackResult(runAt = System.currentTimeMillis(), error = "")
        return report.copy(id = id)
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
                model = aiSettings.effectiveModel,
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
            model = aiSettings.effectiveModel
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
        moodLogs = database.moodLogDao().getAll(),
        includeDiaryBody = includeDiaryBody,
        // 지난번에 권한 것을 함께 실어야 고리가 닫힙니다. 이것이 없으면 매번 처음 만난 사람처럼
        // 말하고, 사용자는 같은 조언을 몇 번이고 다시 받습니다.
        previousReport = database.growthReportDao().getLatest()
    )

    companion object {
        /** 손으로 옮겨 온 답. 어느 모델이 썼는지는 앱이 알 수 없어 이렇게 적어 둡니다. */
        const val MANUAL_MODEL = "직접 붙여넣음"

        /** 갈래를 나눠 읽지 못했을 때 통째로 담아 두는 글자 수. */
        const val MANUAL_ANSWER_LIMIT = 4000
    }
}
