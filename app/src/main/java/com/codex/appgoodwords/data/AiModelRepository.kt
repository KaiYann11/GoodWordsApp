package com.codex.appgoodwords.data

/**
 * 모델 목록을 받아 두고, 부를 때 보낼 모델을 정합니다.
 *
 * "Sonnet 최신"처럼 계열을 고르면 그 계열의 가장 새 모델은 받아 둔 목록에서 셉니다([AiFeedbackSettings.requestModel]).
 * 그래서 목록이 낡으면 새 모델로 넘어가지 못합니다 — 설정 화면을 열 때([refresh]), 그리고 부르기 전에([requestModel]) 오래된
 * 목록을 새로 받습니다. 받지 못해도 막지 않습니다. 받아 둔 목록(없으면 앱에 든 목록)으로 그대로 묻습니다.
 *
 * 목록을 받는 것은 값이 들지 않습니다(모델 목록 API·서버의 파일 읽기).
 */
class AiModelRepository(
    private val settingsStore: SettingsStore,
    private val client: AiFeedbackClient,
    private val now: () -> Long = System::currentTimeMillis
) {
    /**
     * [provider]의 목록을 새로 받아 적어 둡니다.
     *
     * [force]가 아니면 받은 지 [SCREEN_MAX_AGE_MILLIS]가 안 지난 목록은 그대로 둡니다 — 설정 화면을 열 때마다 공급자에게
     * 묻지 않게. Claude CLI는 목록이 없어 묻지 않습니다(별칭으로 부릅니다).
     */
    suspend fun refresh(provider: AiProvider, force: Boolean = false): Result<AiModelList> {
        val stored = settingsStore.getAiModelList(provider)
        if (provider.familyAliases) return Result.success(stored)
        if (!force && !stored.isStale(now(), SCREEN_MAX_AGE_MILLIS)) return Result.success(stored)
        val aiSettings = settingsStore.getAiFeedbackSettings().let {
            // 열쇠는 공급자마다 따로 있습니다. 지금 고른 곳이 아니어도 그 공급자의 열쇠로 묻습니다.
            if (it.activeProvider == provider) it else it.withProvider(provider)
        }
        return runCatching { client.listModels(aiSettings, settingsStore.getServerSyncSettings(), now()) }
            .onSuccess { settingsStore.saveAiModelList(provider, it) }
    }

    /**
     * 지금 설정으로 보낼 모델 이름.
     *
     * 계열을 골랐고 목록이 하루 넘게 묵었으면 먼저 한 번 새로 받아 봅니다 — 앱을 열지 않고 배경에서만 돌아도 새 세대로
     * 넘어가게. 받지 못하면 받아 둔 것으로 갑니다. 이름을 그대로 고른 경우와 CLI·Ollama 기본은 목록이 필요 없습니다.
     */
    suspend fun requestModel(aiSettings: AiFeedbackSettings): String {
        val provider = aiSettings.activeProvider
        if (AiModels.familyOfChoice(aiSettings.modelChoice) == null || provider.familyAliases) {
            return aiSettings.requestModel(emptyList())
        }
        var list = settingsStore.getAiModelList(provider)
        if (list.isStale(now(), CALL_MAX_AGE_MILLIS)) {
            list = runCatching { client.listModels(aiSettings, settingsStore.getServerSyncSettings(), now()) }
                .onSuccess { settingsStore.saveAiModelList(provider, it) }
                .getOrDefault(list)
        }
        return aiSettings.requestModel(list.models)
    }

    /** 답한 모델을 적어 둡니다. 설정의 "지금 답하는 모델" 줄이 CLI처럼 미리 알 수 없는 쪽을 보여 줄 때 씁니다. */
    suspend fun recordAnswer(aiSettings: AiFeedbackSettings, answer: AiAnswer) {
        if (answer.model.isBlank()) return
        settingsStore.recordAiAnswer(
            AiLastAnswer(provider = aiSettings.activeProvider, choice = aiSettings.modelChoice, model = answer.model.trim())
        )
    }

    companion object {
        /** 설정 화면을 열 때 이보다 묵은 목록은 새로 받습니다. */
        const val SCREEN_MAX_AGE_MILLIS = 6L * 60 * 60 * 1000

        /** 부르기 전에 이보다 묵은 목록은 새로 받아 봅니다. 새 모델은 며칠에 한 번도 안 나오니 하루면 넉넉합니다. */
        const val CALL_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
