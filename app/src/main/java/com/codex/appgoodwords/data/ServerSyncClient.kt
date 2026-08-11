package com.codex.appgoodwords.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ServerConnectionInfo(
    val serverSchemaVersion: Int,
    val appSchemaVersion: Int,
    val itemCount: Int,
    val eventCount: Int,
    val routineCount: Int
) {
    val schemaMatches: Boolean
        get() = serverSchemaVersion == appSchemaVersion
}

class ServerSyncClient {
    /**
     * 파괴적인 동기화를 실행하기 전에 주소와 API 키를 확인합니다.
     * `/api/health`는 API 키를 검사하지 않으므로 스냅샷까지 받아 인증과 서버 데이터 양을 함께 확인합니다.
     */
    suspend fun testConnection(settings: ServerSyncSettings): ServerConnectionInfo = withContext(Dispatchers.IO) {
        val health = JSONObject(
            request(
                method = "GET",
                serverUrl = settings.serverUrl,
                apiKey = settings.apiKey,
                path = HEALTH_PATH
            )
        )
        val snapshot = AppDataJson.fromJsonText(
            request(
                method = "GET",
                serverUrl = settings.serverUrl,
                apiKey = settings.apiKey,
                path = SNAPSHOT_PATH
            )
        )
        ServerConnectionInfo(
            serverSchemaVersion = health.optInt("schemaVersion", 0),
            appSchemaVersion = AppDataJson.schemaVersion,
            itemCount = snapshot.items.size,
            eventCount = snapshot.events.size,
            routineCount = snapshot.routines.size
        )
    }

    suspend fun downloadSnapshot(settings: ServerSyncSettings): AppDataSnapshot = withContext(Dispatchers.IO) {
        requireCompatibleSchema(settings)
        val response = request(
            method = "GET",
            serverUrl = settings.serverUrl,
            apiKey = settings.apiKey,
            path = SNAPSHOT_PATH
        )
        AppDataJson.fromJsonText(response)
    }

    /**
     * 스냅샷을 서버와 합치고 합쳐진 결과를 돌려받습니다.
     * 서버가 쓰기를 직렬화하므로 여러 기기가 동시에 보내도 서로를 덮지 않습니다.
     */
    suspend fun mergeSnapshot(
        settings: ServerSyncSettings,
        snapshot: AppDataSnapshot
    ): AppDataSnapshot = withContext(Dispatchers.IO) {
        requireCompatibleSchema(settings)
        val response = request(
            method = "POST",
            serverUrl = settings.serverUrl,
            apiKey = settings.apiKey,
            path = MERGE_PATH,
            body = AppDataJson.toJson(snapshot).toString()
        )
        AppDataJson.fromJsonText(response)
    }

    suspend fun uploadSnapshot(
        settings: ServerSyncSettings,
        snapshot: AppDataSnapshot
    ): AppDataSnapshot = withContext(Dispatchers.IO) {
        requireCompatibleSchema(settings)
        val response = request(
            method = "PUT",
            serverUrl = settings.serverUrl,
            apiKey = settings.apiKey,
            path = SNAPSHOT_PATH,
            body = AppDataJson.toJson(snapshot).toString()
        )
        AppDataJson.fromJsonText(response)
    }

    /**
     * 데이터를 주고받기 전에 형식이 같은지 먼저 묻습니다.
     *
     * 병합 응답을 받고 나서 확인해도 늦습니다. 그때는 서버 쪽 데이터가 이미 상한 뒤입니다.
     * 요청이 한 번 늘지만 자동 동기화는 몇 시간에 한 번이라 부담이 없습니다.
     */
    private fun requireCompatibleSchema(settings: ServerSyncSettings) {
        val health = JSONObject(
            request(
                method = "GET",
                serverUrl = settings.serverUrl,
                apiKey = settings.apiKey,
                path = HEALTH_PATH
            )
        )
        SyncSchema.mismatchMessage(health.optInt("schemaVersion", 0))?.let { message ->
            error(message)
        }
    }

    private fun request(
        method: String,
        serverUrl: String,
        apiKey: String,
        path: String,
        body: String? = null
    ): String {
        val endpoint = URL("${normalizeServerUrl(serverUrl)}$path")
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
                error(failureMessage(responseCode, responseText))
            }
            responseText
        } catch (e: IOException) {
            val port = if (endpoint.port > 0) endpoint.port else endpoint.defaultPort
            throw IOException(
                "서버에 연결하지 못했습니다(${endpoint.host}:$port). 주소와 서버 실행 상태, 같은 네트워크인지 확인해 주세요.",
                e
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun failureMessage(responseCode: Int, responseText: String): String = when (responseCode) {
        401, 403 -> "서버 인증에 실패했습니다($responseCode). API 키를 확인해 주세요."
        404 -> "서버 주소를 찾을 수 없습니다(404). 주소가 앱 서버를 가리키는지 확인해 주세요."
        else -> "서버 요청 실패($responseCode): ${responseText.ifBlank { "응답 없음" }}"
    }

    private fun normalizeServerUrl(serverUrl: String): String = ServerUrlPolicy.normalize(serverUrl)

    private companion object {
        const val HEALTH_PATH = "/api/health"
        const val SNAPSHOT_PATH = "/api/snapshot"
        const val MERGE_PATH = "/api/sync"
    }
}
