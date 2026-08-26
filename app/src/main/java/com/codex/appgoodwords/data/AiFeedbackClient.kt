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
 * 부를 곳은 OpenAI와 Claude 둘입니다([AiProvider]). 둘 다 공식 SDK를 쓰지 않고 HTTP로
 * 직접 부릅니다. 이 앱이 부르는 것은 한 종류의 요청뿐이라, SDK를 더하면 앱만 무거워집니다.
 *
 * 어느 쪽이든 나가는 글은 [GrowthPrompt]가 만든 것 하나뿐입니다.
 */
class AiFeedbackClient {
    /**
     * 물어보고 답 글을 그대로 돌려줍니다. 해석은 [GrowthReportParser]가 맡습니다.
     *
     * @return 모델이 쓴 글(JSON 문자열이기를 기대합니다)
     */
    suspend fun ask(
        system: String,
        user: String,
        model: String,
        aiSettings: AiFeedbackSettings,
        syncSettings: ServerSyncSettings
    ): String = withContext(Dispatchers.IO) {
        val provider = aiSettings.activeProvider
        val viaServer = syncSettings.serverUrl.isNotBlank()
        if (viaServer) {
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

    private fun askDirectly(
        system: String,
        user: String,
        model: String,
        provider: AiProvider,
        apiKey: String
    ): String = when (provider) {
        AiProvider.OPENAI -> askOpenAi(system, user, model, apiKey)
        AiProvider.ANTHROPIC -> askAnthropic(system, user, model, apiKey)
    }

    private fun askServer(
        system: String,
        user: String,
        model: String,
        provider: AiProvider,
        syncSettings: ServerSyncSettings
    ): String {
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
                headers = buildMap {
                    if (syncSettings.apiKey.isNotBlank()) put("X-API-Key", syncSettings.apiKey.trim())
                }
            )
        )
        return response.optString("text")
    }

    private fun askOpenAi(
        system: String,
        user: String,
        model: String,
        apiKey: String
    ): String {
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
        return response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
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
    ): String {
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
        return anthropicText(response)
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

    private fun request(url: String, body: String, headers: Map<String, String>): String {
        val endpoint = URL(url)
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            // 글을 쓰는 데 시간이 걸립니다. 동기화보다 넉넉히 둡니다.
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
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

    private fun normalizeServerUrl(serverUrl: String): String = serverUrl.trim().trimEnd('/')

    private companion object {
        const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** 돌아보기 한 편은 짧지만, 생각하는 데 쓰는 몫까지 여기서 셉니다. 모자라면 답이 끊깁니다. */
        const val ANTHROPIC_MAX_TOKENS = 16_000
        const val SERVER_PATH = "/api/growth-feedback"
    }
}
