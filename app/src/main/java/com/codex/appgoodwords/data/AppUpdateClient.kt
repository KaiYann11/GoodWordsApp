package com.codex.appgoodwords.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 서버에 어떤 설치 파일이 올라와 있는지 읽어 옵니다(`/update/latest.json`).
 *
 * **동기화와 같은 HTTPS 규칙을 씁니다**([ServerUrlPolicy]). 설치 파일 정보를 평문으로 받으면 중간에서
 * 바꿔치기할 수 있고, 여기 적힌 해시로 받은 파일을 믿기 때문에 그러면 남의 앱이 깔립니다.
 *
 * 설치 파일 자체는 안드로이드에 맡겨 받고([AppUpdateDownloads]), 받은 것은 여기서 읽은 `sha256`과 맞춰 본 뒤에야
 * 설치 화면으로 넘깁니다([AppUpdateFiles.verifyInto]). 안드로이드가 서명이 다른 APK를 거절해 주기는 하지만, 그것은
 * 마지막 울타리이고 먼저 막아야 "왜 설치가 안 되지" 하고 사용자가 헤매지 않습니다.
 *
 * 접속 키는 보내지 않습니다. `/update/`는 서버가 인증 없이 내주는 자리라 키를 실어 보낼 까닭이
 * 없고, 안 보내면 로그나 중계에 키가 남을 일도 없습니다.
 */
class AppUpdateClient {

    suspend fun fetchLatest(serverUrl: String): AppUpdateInfo = withContext(Dispatchers.IO) {
        val text = request(
            serverUrl = serverUrl,
            path = "${AppUpdateInfo.UPDATE_DIRECTORY}latest.json",
            accept = "application/json"
        ) { connection ->
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
        AppUpdateJson.parse(text)
    }

    /**
     * 헤더는 **연결 전에** 답니다. 응답을 읽기 시작한 뒤에 달면
     * `Cannot set request property after connection is made`로 터집니다.
     */
    private fun <T> request(
        serverUrl: String,
        path: String,
        accept: String,
        read: (HttpURLConnection) -> T
    ): T {
        val endpoint = URL("${ServerUrlPolicy.normalize(serverUrl)}$path")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // 따라가지 않습니다. HTTPS에서 평문으로 끌고 가는 길을 열어 주지 않기 위해서입니다.
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("Accept", accept)
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                if (code == 404) {
                    error("서버에 게시된 설치 파일이 없습니다. 서버에서 먼저 새 버전을 올려 주세요.")
                }
                error("서버가 설치 파일을 주지 않았습니다(HTTP $code).")
            }
            read(connection)
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
}
