package com.codex.appgoodwords.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 서버에서 설치 파일을 받아 옵니다.
 *
 * **동기화와 같은 HTTPS 규칙을 씁니다**([ServerUrlPolicy]). 설치 파일을 평문으로 받으면 중간에서
 * 바꿔치기할 수 있고, 그것을 그대로 설치하면 기기에 남의 앱이 깔립니다.
 *
 * **받은 파일은 해시를 맞춰 보고 나서야 설치 화면으로 넘깁니다.** 서버가 알려 준 `sha256`과
 * 다르면 지웁니다. 안드로이드가 서명이 다른 APK를 거절해 주기는 하지만, 그것은 마지막 울타리이고
 * 여기서 먼저 막아야 "왜 설치가 안 되지" 하고 사용자가 헤매지 않습니다.
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
     * 설치 파일을 받아 [target]에 둡니다. 해시가 다르면 지우고 실패합니다.
     *
     * 받는 동안 [onProgress]로 얼마나 왔는지 알립니다. 25MB쯤 되는 파일이라 아무 표시가 없으면
     * 멈춘 줄 알고 다시 누르게 됩니다.
     */
    suspend fun download(
        serverUrl: String,
        info: AppUpdateInfo,
        target: File,
        onProgress: (Long) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        // 지난번에 받다 만 파일이 남아 있을 수 있습니다. 이어 쓰면 해시가 맞을 리 없습니다.
        target.delete()

        val digest = MessageDigest.getInstance("SHA-256")
        var received = 0L

        try {
            request(
                serverUrl = serverUrl,
                path = info.path,
                accept = "application/vnd.android.package-archive"
            ) { connection ->
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            received += read
                            // 알려 준 크기를 넘으면 더 받지 않습니다. 저장 공간을 끝까지 채우게 둘 수 없습니다.
                            if (received > info.sizeBytes) {
                                error("설치 파일이 서버가 알려 준 크기보다 큽니다. 받기를 멈췄습니다.")
                            }
                            onProgress(received)
                        }
                    }
                }
            }

            if (received != info.sizeBytes) {
                error("설치 파일을 끝까지 받지 못했습니다. 다시 시도해 주세요.")
            }
            val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (actual != info.sha256) {
                error("받은 설치 파일이 서버가 알려 준 것과 다릅니다. 설치하지 않았습니다.")
            }
            target
        } catch (error: Throwable) {
            // 반쯤 받은 파일이나 못 믿을 파일을 남겨 두지 않습니다. 다음에 그것이 설치될 수 있습니다.
            target.delete()
            throw error
        }
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
