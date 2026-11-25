package com.codex.appgoodwords.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI에게 물어 글을 받아 옵니다.
 *
 * 길이 둘입니다. 서버 주소가 있으면 **내 서버를 거칩니다.** 열쇠가 서버 한 곳에만 있으면
 * 기기를 새로 붙일 때마다 다시 넣지 않아도 되고, 폰을 잃어버려도 열쇠가 함께 나가지 않습니다.
 * 서버가 없거나 서버에 열쇠가 없으면 이 기기에 넣어 둔 열쇠로 직접 부릅니다.
 *
 * 열쇠로 부르는 곳은 OpenAI와 Claude 둘입니다([AiProvider]). 둘 다 공식 SDK를 쓰지 않고 HTTP로
 * 직접 부릅니다. 이 앱이 부르는 것은 한 종류의 요청뿐이라, SDK를 더하면 앱만 무거워집니다.
 * 서버에 로그인해 둔 CLI(Claude Code·Codex)나 서버 PC의 Ollama가 답하는 공급자([AiProvider.serverOnly])는
 * 열쇠 없이 서버만 거칩니다. 이 기기에는 둘 다 없어 서버가 꺼져 있으면 되돌아갈 길이 없습니다.
 *
 * 어느 쪽이든 나가는 글은 [GrowthPrompt]가 만든 것 하나뿐입니다.
 */
class AiFeedbackClient {
    /**
     * 물어보고 답 글을 그대로 돌려줍니다. 해석은 [GrowthReportParser]가 맡습니다.
     *
     * @param model 보낼 모델 이름([AiFeedbackSettings.requestModel]). CLI·Ollama는 빈 값이면 서버 쪽 기본입니다.
     * @return 모델이 쓴 글(JSON 문자열이기를 기대합니다)과 실제로 답한 모델
     */
    suspend fun ask(
        system: String,
        user: String,
        model: String,
        aiSettings: AiFeedbackSettings,
        syncSettings: ServerSyncSettings
    ): AiAnswer = withContext(Dispatchers.IO) {
        val provider = aiSettings.activeProvider
        val viaServer = syncSettings.serverUrl.isNotBlank()
        if (aiSettings.requiresServer) {
            require(viaServer) {
                "${provider.label}는 서버가 대신 돌립니다. 설정에서 서버 주소를 먼저 지정하거나 다른 곳을 골라 주세요."
            }
            askServer(system, user, model, provider, syncSettings)
        } else if (viaServer) {
            runCatching { askServer(system, user, model, provider, syncSettings) }
                .getOrElse { failure ->
                    // 서버가 꺼져 있거나 서버에 열쇠가 없을 수 있습니다. 이 기기 열쇠가 있으면 그것으로 갑니다.
                    if (aiSettings.canCallDirectly) {
                        askDirectly(system, user, model, provider, aiSettings.activeKey)
                    } else {
                        throw failure
                    }
                }
        } else {
            require(aiSettings.canCallDirectly) {
                "AI 열쇠가 없습니다. 설정에서 ${provider.label} API 키를 넣거나 서버 주소를 지정해 주세요."
            }
            askDirectly(system, user, model, provider, aiSettings.activeKey)
        }
    }

    /**
     * 고를 수 있는 모델 목록을 받아 옵니다.
     *
     * 예전에는 앱이 목록을 들고 있어 새 모델이 나올 때마다 앱을 다시 게시해야 했습니다(사용자가 바꿈, 2026-11-25). 받는 곳은
     * 묻는 길과 같습니다 — 서버가 있으면 서버(`GET /api/ai-models`, 서버가 그 PC의 Codex 목록·Ollama·열쇠로 API 목록을
     * 받아 줍니다), 서버에 열쇠가 없거나 서버가 없으면 이 기기의 열쇠로 공급자에게 직접 묻습니다. Claude CLI는 목록을 내줄
     * 길이 없어 빈 목록입니다(계열 이름을 그대로 넘깁니다 — [AiProvider.familyAliases]).
     *
     * @throws IllegalStateException·IOException 받을 길이 없거나 실패했을 때. 사유는 화면에 그대로 보입니다.
     */
    suspend fun listModels(
        aiSettings: AiFeedbackSettings,
        syncSettings: ServerSyncSettings,
        now: Long = System.currentTimeMillis()
    ): AiModelList = withContext(Dispatchers.IO) {
        val provider = aiSettings.activeProvider
        val viaServer = syncSettings.serverUrl.isNotBlank()
        val models: List<AiModelInfo> = when {
            provider.familyAliases -> emptyList()
            provider.serverOnly -> {
                require(viaServer) { "${provider.label} 목록은 서버가 알려 줍니다. 설정에서 서버 주소를 먼저 지정해 주세요." }
                listServerModels(provider, syncSettings)
            }
            viaServer -> runCatching { listServerModels(provider, syncSettings) }
                .getOrElse { failure ->
                    // 서버에 열쇠가 없는 것이 흔합니다(열쇠는 휴대폰에만 둔 사람). 이 기기 열쇠가 있으면 그것으로 묻습니다.
                    if (aiSettings.canCallDirectly) listDirectly(provider, aiSettings.activeKey) else throw failure
                }
            else -> {
                require(aiSettings.canCallDirectly) {
                    "AI 열쇠가 없습니다. 설정에서 ${provider.label} API 키를 넣거나 서버 주소를 지정해 주세요."
                }
                listDirectly(provider, aiSettings.activeKey)
            }
        }
        AiModelList(models = models, fetchedAt = now)
    }

    /**
     * 서버가 모델을 고르는 칸의 이름을 알아 옵니다(`POST /api/ai-models/resolve`, 설정 → AI의 "모델 정보 받아오기").
     *
     * Claude CLI·Codex CLI는 서버가 칸마다 아주 짧게 한 번씩 물어 실제로 답한 모델을 읽습니다. 그래서 몇 초 걸리고 구독
     * 사용량이 조금 듭니다 — 사용자가 단추를 누를 때만 부릅니다(2026-11-25). Ollama 기본은 서버가 고를 모델을 그대로 알려 줍니다.
     *
     * @param requests 보낼 이름(`""`·`sonnet`·`fable`). 칸이 아니라 실제로 보내는 이름입니다.
     * @return 보낸 이름 → 알아 온 것. 칸 하나가 실패하면 그 칸에 사유가 실립니다.
     * @throws IllegalArgumentException·IllegalStateException·IOException 서버가 없거나 닿지 못했을 때
     */
    suspend fun resolveModels(
        provider: AiProvider,
        requests: List<String>,
        syncSettings: ServerSyncSettings
    ): Map<String, AiResolvedModel> = withContext(Dispatchers.IO) {
        require(syncSettings.serverUrl.isNotBlank()) {
            "${provider.label} 모델은 서버가 알아 옵니다. 설정에서 서버 주소를 먼저 지정해 주세요."
        }
        val body = JSONObject()
            .put("provider", provider.name.lowercase())
            .put("models", JSONArray().apply { requests.forEach { put(it) } })
            .toString()
        AiModelListJson.parseResolved(
            request(
                url = "${normalizeServerUrl(syncSettings.serverUrl)}$RESOLVE_PATH",
                body = body,
                headers = serverHeaders(syncSettings)
            )
        )
    }

    private fun listServerModels(provider: AiProvider, syncSettings: ServerSyncSettings): List<AiModelInfo> {
        val url = "${normalizeServerUrl(syncSettings.serverUrl)}$MODELS_PATH?provider=${provider.name.lowercase()}"
        return AiModelListJson.parseServer(
            request(url = url, body = null, headers = serverHeaders(syncSettings), readTimeoutMillis = LIST_TIMEOUT)
        )
    }

    private fun listDirectly(provider: AiProvider, apiKey: String): List<AiModelInfo> = when (provider) {
        AiProvider.ANTHROPIC -> buildList {
            // 쪽마다 천 개까지 옵니다. 다음 쪽이 있으면 이어 받되 끝없이 넘기지는 않습니다(서버 `listAnthropicModels`와 같게).
            var afterId: String? = null
            for (page in 0 until MAX_MODEL_PAGES) {
                val query = "limit=1000" + afterId?.let { "&after_id=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
                val (models, next) = AiModelListJson.parseAnthropicPage(
                    request(
                        url = "$ANTHROPIC_MODELS_URL?$query",
                        body = null,
                        headers = mapOf("x-api-key" to apiKey.trim(), "anthropic-version" to ANTHROPIC_VERSION),
                        readTimeoutMillis = LIST_TIMEOUT
                    )
                )
                addAll(models)
                afterId = next ?: break
            }
        }
        AiProvider.OPENAI -> AiModelListJson.parseOpenAi(
            request(
                url = OPENAI_MODELS_URL,
                body = null,
                headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}"),
                readTimeoutMillis = LIST_TIMEOUT
            )
        )
        AiProvider.CLAUDE_CLI, AiProvider.CODEX_CLI, AiProvider.OLLAMA ->
            error("${provider.label} 목록은 서버가 알려 줍니다. 서버 주소를 지정해 주세요.")
    }

    private fun serverHeaders(syncSettings: ServerSyncSettings): Map<String, String> = buildMap {
        if (syncSettings.apiKey.isNotBlank()) put("X-API-Key", syncSettings.apiKey.trim())
    }

    private fun askDirectly(
        system: String,
        user: String,
        model: String,
        provider: AiProvider,
        apiKey: String
    ): AiAnswer = when (provider) {
        AiProvider.OPENAI -> askOpenAi(system, user, model, apiKey)
        AiProvider.ANTHROPIC -> askAnthropic(system, user, model, apiKey)
        AiProvider.CLAUDE_CLI, AiProvider.CODEX_CLI, AiProvider.OLLAMA ->
            error("${provider.label}는 이 기기에서 직접 부를 수 없습니다. 서버 주소를 지정해 주세요.")
    }

    private fun askServer(
        system: String,
        user: String,
        model: String,
        provider: AiProvider,
        syncSettings: ServerSyncSettings
    ): AiAnswer {
        val body = JSONObject()
            .put("system", system)
            .put("user", user)
            .put("model", model)
            // 어느 곳에 물어볼지는 기기가 정합니다. 서버는 그에 맞는 열쇠만 골라 씁니다.
            .put("provider", provider.name.lowercase())
            .toString()
        val response = JSONObject(
            request(
                url = "${normalizeServerUrl(syncSettings.serverUrl)}$SERVER_PATH",
                body = body,
                headers = serverHeaders(syncSettings)
            )
        )
        // 1.26.0부터 서버가 답한 모델을 함께 보냅니다(`model`). 옛 서버면 없어서 보낸 이름으로 적습니다.
        return AiAnswer(text = response.optString("text"), model = response.optString("model").ifBlank { model })
    }

    private fun askOpenAi(
        system: String,
        user: String,
        model: String,
        apiKey: String
    ): AiAnswer {
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user))
            )
            // 형식을 지키게 못 박습니다. 코드펜스로 감싸 오는 일이 줄어듭니다.
            .put("response_format", JSONObject().put("type", "json_object"))
            .toString()

        val response = JSONObject(
            request(
                url = OPENAI_URL,
                body = body,
                headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}")
            )
        )
        val text = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        // 별칭으로 물어도 답에는 실제 판(`gpt-6-luna-2026-…`)이 실립니다.
        return AiAnswer(text = text, model = response.optString("model").ifBlank { model })
    }

    /**
     * Claude에게 묻습니다.
     *
     * OpenAI와 모양이 다릅니다. 일러 주는 말은 `messages`가 아니라 `system` 자리에 따로 싣고,
     * 답도 `content` 배열에서 글 조각만 골라 이어야 합니다. `max_tokens`는 없으면 안 됩니다.
     *
     * 생각하는 시간은 따로 켜지 않습니다. 요즘 모델은 기본으로 켜져 있고, 예전 방식대로
     * `budget_tokens`를 보내면 오히려 400으로 되돌아옵니다.
     *
     * [FALLBACK_BETA]는 안전 분류기가 요청을 거절했을 때(HTTP 200에 `stop_reason: refusal`)
     * 같은 요청을 다른 모델로 한 번 더 돌려 주게 합니다. 일기를 곁들여 보내면 드물게 거절이
     * 나올 수 있는데, 사용자에게는 이유 없이 빈 답으로만 보입니다.
     */
    private fun askAnthropic(
        system: String,
        user: String,
        model: String,
        apiKey: String
    ): AiAnswer {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", ANTHROPIC_MAX_TOKENS)
            .put("system", system)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", user))
            )
            .put("fallbacks", "default")
            .toString()

        val response = JSONObject(
            request(
                url = ANTHROPIC_URL,
                body = body,
                headers = mapOf(
                    "x-api-key" to apiKey.trim(),
                    "anthropic-version" to ANTHROPIC_VERSION,
                    "anthropic-beta" to FALLBACK_BETA
                )
            )
        )
        if (response.optString("stop_reason") == "refusal") {
            error("AI가 이 요청에 답하지 않았습니다. 설정에서 일기 본문 보내기를 끄고 다시 시도해 보세요.")
        }
        return AiAnswer(text = anthropicText(response), model = response.optString("model").ifBlank { model })
    }

    /** 글 조각만 골라 잇습니다. 생각한 내용 같은 다른 조각이 섞여 있어도 버립니다. */
    private fun anthropicText(response: JSONObject): String {
        val blocks = response.optJSONArray("content") ?: return ""
        return buildString {
            for (index in 0 until blocks.length()) {
                val block = blocks.optJSONObject(index) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
    }

    /**
     * 한 번 주고받습니다. [body]가 있으면 POST, 없으면 GET입니다.
     *
     * 글을 쓰는 데 시간이 걸려 기본은 동기화보다 넉넉히 기다립니다. 서버는 180초에 포기하고 사유를 돌려주므로, 그 사유가
     * 닿도록 서버보다 조금 더 기다립니다. 목록 받기처럼 금방 끝날 일은 [readTimeoutMillis]를 줄여 부릅니다.
     */
    private fun request(
        url: String,
        body: String?,
        headers: Map<String, String>,
        readTimeoutMillis: Int = 200_000
    ): String {
        val endpoint = URL(url)
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = readTimeoutMillis
            doOutput = body != null
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }

        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) error(failureMessage(code, text))
            text
        } catch (e: IOException) {
            throw IOException("AI에 연결하지 못했습니다(${endpoint.host}). 인터넷 연결과 열쇠를 확인해 주세요.", e)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 사용자가 읽고 무엇을 고쳐야 할지 알 수 있는 사유로 바꿉니다.
     *
     * 응답 본문을 그대로 띄우면 열쇠 앞자리가 섞여 나오는 경우가 있어 message만 뽑습니다.
     * OpenAI와 Claude가 오류를 담는 자리가 같아서 한 함수로 둡니다.
     */
    private fun failureMessage(code: Int, responseText: String): String {
        val detail = runCatching {
            JSONObject(responseText).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
            .ifBlank { runCatching { JSONObject(responseText).optString("error") }.getOrDefault("") }

        return when {
            code == 401 || code == 403 -> "AI 열쇠가 올바르지 않습니다($code)."
            code == 429 -> "AI 사용량 한도에 걸렸습니다(429). 잠시 뒤 다시 시도해 주세요."
            detail.isNotBlank() -> "AI 요청이 실패했습니다($code): $detail"
            else -> "AI 요청이 실패했습니다($code)."
        }
    }

    private fun normalizeServerUrl(serverUrl: String): String = ServerUrlPolicy.normalize(serverUrl)

    private companion object {
        const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        const val OPENAI_MODELS_URL = "https://api.openai.com/v1/models"
        const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_MODELS_URL = "https://api.anthropic.com/v1/models"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** 돌아보기 한 편은 짧지만, 생각하는 데 쓰는 몫까지 여기서 셉니다. 모자라면 답이 끊깁니다. */
        const val ANTHROPIC_MAX_TOKENS = 16_000
        const val SERVER_PATH = "/api/growth-feedback"
        const val MODELS_PATH = "/api/ai-models"
        const val RESOLVE_PATH = "/api/ai-models/resolve"

        /** 목록은 금방 옵니다. 글을 쓸 때만큼 붙들고 있지 않습니다. */
        const val LIST_TIMEOUT = 20_000
        const val MAX_MODEL_PAGES = 5
    }
}

/** AI가 쓴 글과, 실제로 답한 모델(서버·API가 알려 준 이름. 모르면 보낸 이름). */
data class AiAnswer(val text: String, val model: String)
