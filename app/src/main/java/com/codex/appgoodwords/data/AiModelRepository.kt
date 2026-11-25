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
        val aiSettings = settingsFor(provider)
        return runCatching { client.listModels(aiSettings, settingsStore.getServerSyncSettings(), now()) }
            .onSuccess { settingsStore.saveAiModelList(provider, it) }
    }

    /**
     * "모델 정보 받아오기"(설정 → AI의 단추, 사용자가 정함 2026-11-25).
     *
     * 목록을 새로 받고, 서버가 모델을 고르는 칸([AiModels.serverPickedChoices] — CLI·Ollama 기본, Claude CLI의 계열 칸)은
     * 서버에 이름을 물어 적어 둡니다. 서버는 CLI에 칸마다 짧게 한 번씩 물어보므로 몇 초 걸리고 구독 사용량이 조금 듭니다 —
     * 그래서 설정 화면을 열 때가 아니라 단추를 누를 때만 합니다.
     *
     * 칸 하나를 알아내지 못하면 그 칸에 사유가 남고([AiModelCheck.errors]) 나머지는 적힙니다. 새로 안 것이 하나도 없으면
     * 누른 때를 남기지 않습니다 — 단추 아래 "…에 받음"이 지난 이름을 새것처럼 보이게 합니다.
     *
     * @return 목록이나 서버를 받지 못했으면 그 사유(화면에 그대로 보입니다). 그래도 받은 것은 적혀 있습니다.
     */
    suspend fun check(provider: AiProvider): Result<Unit> {
        val failures = mutableListOf<String>()
        var listFetched = false
        if (!provider.familyAliases) {
            refresh(provider, force = true)
                .onSuccess { listFetched = true }
                .onFailure { failures += "목록: " + (it.message ?: "받지 못했습니다.") }
        }
        val aiSettings = settingsFor(provider)
        // 칸 → 실제로 보내는 이름(`latest:sonnet` → `sonnet`). 서버는 보낸 이름으로 답합니다.
        val requests = AiModels.serverPickedChoices(provider, aiSettings.modelChoice)
            .associateWith { aiSettings.withModel(it).requestModel(emptyList()) }
        var resolved: Map<String, AiResolvedModel>? = null
        if (requests.isNotEmpty()) {
            runCatching { client.resolveModels(provider, requests.values.distinct(), settingsStore.getServerSyncSettings()) }
                .onSuccess { byRequest ->
                    resolved = requests.mapValues { (_, request) ->
                        byRequest[request] ?: AiResolvedModel(model = "", error = "서버가 이 칸을 알려 주지 않았습니다.")
                    }
                }
                .onFailure { failures += it.message ?: "모델 이름을 알아 오지 못했습니다." }
        }
        val learned = resolved
        val learnedSomething = if (requests.isEmpty()) listFetched else learned != null
        if (learnedSomething) {
            settingsStore.updateAiModelCheck(provider) { current ->
                val found = learned.orEmpty()
                AiModelCheck(
                    checkedAt = now(),
                    names = current.names + found.filterValues { it.model.isNotBlank() }.mapValues { it.value.model },
                    errors = found.filterValues { it.model.isBlank() }.mapValues { it.value.error.ifBlank { "알아내지 못했습니다." } }
                )
            }
        }
        return if (failures.isEmpty()) Result.success(Unit) else Result.failure(IllegalStateException(failures.joinToString("\n")))
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

    /**
     * 답한 모델을 그 칸에 적어 둡니다([AiModelCheck.withAnswer]). 설정의 "지금 답하는 모델" 줄과 "모델 정보 받아오기" 아래
     * 목록이 CLI처럼 미리 알 수 없는 칸을 보여 줄 때 씁니다.
     *
     * 서버가 답한 모델을 알려 주지 않으면(옛 서버, 모델을 알리지 않는 CLI) 보낸 이름이 그대로 옵니다. 서버가 고르는 칸에서
     * 그것은 이름이 아니라 별칭(`sonnet`)이라 적지 않습니다.
     */
    suspend fun recordAnswer(aiSettings: AiFeedbackSettings, answer: AiAnswer) {
        val model = answer.model.trim()
        if (model.isBlank()) return
        val provider = aiSettings.activeProvider
        val choice = aiSettings.modelChoice
        if (AiModels.isServerPicked(provider, choice) && model == aiSettings.requestModel(emptyList())) return
        settingsStore.recordAiAnswer(provider, choice, model)
    }

    /** 열쇠는 공급자마다 따로 있습니다. 지금 고른 곳이 아니어도 그 공급자의 설정(열쇠·고른 모델)으로 묻습니다. */
    private suspend fun settingsFor(provider: AiProvider): AiFeedbackSettings =
        settingsStore.getAiFeedbackSettings().let { if (it.activeProvider == provider) it else it.withProvider(provider) }

    companion object {
        /** 설정 화면을 열 때 이보다 묵은 목록은 새로 받습니다. */
        const val SCREEN_MAX_AGE_MILLIS = 6L * 60 * 60 * 1000

        /** 부르기 전에 이보다 묵은 목록은 새로 받아 봅니다. 새 모델은 며칠에 한 번도 안 나오니 하루면 넉넉합니다. */
        const val CALL_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
