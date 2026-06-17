package com.codex.appgoodwords.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ServerSyncClient {
    suspend fun downloadSnapshot(settings: ServerSyncSettings): AppDataSnapshot = withContext(Dispatchers.IO) {
        val response = request(
            method = "GET",
            serverUrl = settings.serverUrl,
            apiKey = settings.apiKey
        )
        AppDataJson.fromJsonText(response)
    }

    suspend fun uploadSnapshot(
        settings: ServerSyncSettings,
        snapshot: AppDataSnapshot
    ): AppDataSnapshot = withContext(Dispatchers.IO) {
        val response = request(
            method = "PUT",
            serverUrl = settings.serverUrl,
            apiKey = settings.apiKey,
            body = AppDataJson.toJson(snapshot).toString()
        )
        AppDataJson.fromJsonText(response)
    }

    private fun request(
        method: String,
        serverUrl: String,
        apiKey: String,
        body: String? = null
    ): String {
        val endpoint = URL("${normalizeServerUrl(serverUrl)}/api/snapshot")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("X-API-Key", apiKey.trim())
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val responseText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (responseCode !in 200..299) {
                error("서버 요청 실패($responseCode): ${responseText.ifBlank { "응답 없음" }}")
            }
            responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeServerUrl(serverUrl: String): String {
        val normalized = serverUrl.trim().trimEnd('/')
        require(normalized.isNotBlank()) { "서버 주소를 입력해 주세요." }
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "서버 주소는 http:// 또는 https://로 시작해야 합니다."
        }
        return normalized
    }
}
