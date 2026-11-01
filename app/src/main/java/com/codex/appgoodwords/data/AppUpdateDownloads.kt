package com.codex.appgoodwords.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 설치 파일 받기를 안드로이드(DownloadManager)에 맡깁니다(사용자가 정함, 2026-11-01).
 *
 * 예전에는 앱이 화면 쪽에서 직접 받았습니다. 받는 중에 다른 앱으로 가면 Android 15가 몇 초 뒤 이 앱의 통신을 끊어,
 * 받던 것이 실패로 끝나고 처음부터 다시 받아야 했습니다. DownloadManager는 시스템이 받으므로 앱을 떠나도 이어지고,
 * 연결이 끊기면 기다렸다가 받은 데부터 이어 받습니다(서버가 ETag·Range를 내줍니다). 알림에도 진행이 보입니다.
 *
 * **믿을지는 여전히 앱이 정합니다.** 시스템의 "다 받음" 알림은 띄우지 않습니다 — 그 알림을 누르면 해시를 맞춰 보기
 * 전에 설치 화면이 열립니다. 다 받으면 [AppUpdateFiles.verifyInto]로 맞춰 본 것만 설치 화면으로 넘깁니다.
 *
 * 주소 규칙은 동기화와 같습니다([ServerUrlPolicy]) — 평문 주소면 맡기기 전에 막습니다. `/update/`는 서버가 인증 없이
 * 내주는 자리라 접속 키는 싣지 않습니다.
 */
class AppUpdateDownloads(private val context: Context) {
    private val manager: DownloadManager
        get() = checkNotNull(context.getSystemService(DownloadManager::class.java)) { "이 기기에서는 내려받기를 맡길 수 없습니다." }

    /** 받기를 맡기고 DownloadManager의 번호를 돌려줍니다. */
    fun enqueue(serverUrl: String, info: AppUpdateInfo): Long {
        val url = "${ServerUrlPolicy.normalize(serverUrl)}${info.path}"
        // 같은 이름이 남아 있으면 DownloadManager가 "-1"을 붙인 새 이름으로 받아, 다 받은 파일을 엉뚱한 데서 찾게 됩니다.
        fileFor(info).delete()
        val label = context.applicationInfo.loadLabel(context.packageManager)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("$label ${info.versionName}")
            .setDescription("앱 업데이트")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, DIRECTORY, info.fileName)
        return manager.enqueue(request)
    }

    /** 지금 어디까지 왔는지. 그사이 지워졌으면(알림이나 다운로드 앱에서 취소) null입니다. */
    fun query(downloadId: Long): Snapshot? =
        manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            Snapshot(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            )
        }

    /** DownloadManager가 받아 두는 자리. 앱 전용 바깥 저장소입니다 — 앱 안쪽 캐시에는 시스템이 쓰지 못합니다. */
    fun fileFor(info: AppUpdateInfo): File {
        val directory = context.getExternalFilesDir(DIRECTORY) ?: error("설치 파일을 받아 둘 자리를 찾지 못했습니다.")
        return File(directory, info.fileName)
    }

    /** 다 썼으면 지웁니다. 받은 파일도 함께 지워집니다. */
    fun remove(downloadId: Long) {
        runCatching { manager.remove(downloadId) }
    }

    data class Snapshot(val status: Int, val reason: Int, val downloadedBytes: Long)

    companion object {
        const val DIRECTORY = "updates"
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
