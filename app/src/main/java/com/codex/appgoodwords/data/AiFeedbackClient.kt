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
        val viaServer = syncSettings.serverUrl.isNotBlank()
        if (viaServer) {
            runCatching { askServer(system, user, model, syncSettings) }
                .getOrElse { failure ->
                    // 서버가 꺼져 있거나 서버에 열쇠가 없을 수 있습니다. 이 기기 열쇠가 있으면 그것으로 갑니다.
                    if (aiSettings.canCallDirectly) {
                        askOpenAi(system, user, model, aiSettings.apiKey)
                    } else {
                        throw failure
                    }
                }
        } else {
            require(aiSettings.canCallDirectly) {
                "AI 열쇠가 없습니다. 설정에서 OpenAI API 키를 넣거나 서버 주소를 지정해 주세요."
            }
            askOpenAi(system, user, model, aiSettings.apiKey)
        }
    }

    private fun askServer(
        system: String,
        user: String,
        model: String,
        syncSettings: ServerSyncSettings
    ): String {
        val body = JSONObject()
            .put("system", system)
            .put("user", user)
            .put("model", model)
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

    private fun request(url: String, body: String, headers: Map<String, String>): String {
        val endpoint = URL(url)
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            // 글을 쓰는 데 시간이 걸립니다. 동기화보다 넉넉히 둡니다.
            readTimeout = 90_000
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
     */
    private fun failureMessage(code: Int, responseText: String): String {
        val detail = runCatching {
            JSONObject(responseText).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
            .ifBlank { runCatching { JSONObject(responseText).optString("error") }.getOrDefault("") }

        return when {
            code == 401 -> "AI 열쇠가 올바르지 않습니다(401)."
            code == 429 -> "AI 사용량 한도에 걸렸습니다(429). 잠시 뒤 다시 시도해 주세요."
            detail.isNotBlank() -> "AI 요청이 실패했습니다($code): $detail"
            else -> "AI 요청이 실패했습니다($code)."
        }
    }

    private fun normalizeServerUrl(serverUrl: String): String = serverUrl.trim().trimEnd('/')

    private companion object {
        const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        const val SERVER_PATH = "/api/growth-feedback"
    }
}
