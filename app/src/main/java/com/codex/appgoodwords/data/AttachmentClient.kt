package com.codex.appgoodwords.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 첨부 파일 하나를 서버에 올립니다.
 *
 * 본문에 파일을 그대로 싣습니다. multipart를 쓰지 않는 이유는 한 번에 한 파일만 보내고,
 * 서버가 표준 라이브러리만으로 돌기 때문입니다.
 */
class AttachmentClient(private val context: Context) {
    /**
     * 기기 파일을 올리고 서버 주소를 돌려줍니다.
     *
     * 열 수 없는 파일(사용자가 지웠거나 권한이 끊긴 경우)은 null입니다.
     * 사진 한 장 때문에 동기화 전체가 멈추면 안 되므로, 부르는 쪽에서 건너뛰게 합니다.
     */
    suspend fun upload(settings: ServerSyncSettings, uri: String): String? = withContext(Dispatchers.IO) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
        val mime = mimeOf(parsed)
        if (mime.isBlank()) return@withContext null

        val bytes = runCatching {
            context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        if (bytes.isEmpty()) return@withContext null

        val response = post(settings, bytes, mime)
        JSONObject(response).optString("uri").takeIf { AttachmentUris.isServerAttachment(it) }
    }

    /**
     * 파일 형식을 알아냅니다.
     *
     * `getType()`은 `content://`가 아니면 대체로 null이고, content 제공자도 형식을 안 줄 때가 있습니다.
     * 서버는 형식을 보고 받을지 정하므로, 여기서 비어 있으면 멀쩡한 사진도 못 올립니다.
     */
    private fun mimeOf(uri: Uri): String {
        context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }?.let { return it }
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
        if (extension.isBlank()) return ""
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
    }

    private fun post(settings: ServerSyncSettings, bytes: ByteArray, mime: String): String {
        val endpoint = URL("${ServerUrlPolicy.normalize(settings.serverUrl)}$ATTACHMENTS_PATH")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            // 동영상은 오래 걸립니다. 스냅샷보다 넉넉히 잡습니다.
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", mime)
            // 큰 파일을 메모리에 다시 쌓지 않도록 흘려 보냅니다.
            setFixedLengthStreamingMode(bytes.size)
            if (settings.apiKey.isNotBlank()) {
                setRequestProperty("X-API-Key", settings.apiKey.trim())
            }
        }

        return try {
            connection.outputStream.use { it.write(bytes) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                error("첨부를 올리지 못했습니다($responseCode): ${responseText.ifBlank { "응답 없음" }}")
            }
            responseText
        } catch (e: IOException) {
            throw IOException("첨부를 올리는 중 서버에 연결하지 못했습니다.", e)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ATTACHMENTS_PATH = "/api/attachments"
    }
}
