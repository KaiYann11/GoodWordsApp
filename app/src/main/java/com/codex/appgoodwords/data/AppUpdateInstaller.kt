package com.codex.appgoodwords.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 받아 둔 설치 파일을 안드로이드 설치 화면으로 넘깁니다.
 *
 * **앱이 직접 설치하지 않습니다.** 설치를 결정하는 것은 사용자이고, 그 화면은 안드로이드가 띄웁니다.
 * 우리는 "이 파일을 설치하려 한다"고 알릴 뿐입니다. 처음에는 "이 출처의 앱 설치 허용"을 한 번
 * 물어봅니다 — 그 승인도 우리가 대신할 수 없습니다.
 *
 * 파일은 캐시 안에 둡니다([UPDATE_DIRECTORY]). 설치가 끝나면 시스템이 알아서 비워도 괜찮고,
 * 지워졌으면 다시 받으면 됩니다.
 */
object AppUpdateInstaller {
    const val UPDATE_DIRECTORY = "updates"

    /** 받아 둘 자리. 이름은 서버가 준 것을 그대로 쓰되, 경로는 우리가 정합니다. */
    fun targetFile(context: Context, info: AppUpdateInfo): File =
        File(File(context.cacheDir, UPDATE_DIRECTORY), info.fileName)

    /**
     * 설치 화면을 엽니다.
     *
     * `FileProvider`로 넘깁니다. `file://`로 넘기면 Android 7 이상에서 바로 죽고,
     * 읽기 권한을 함께 주지 않으면 설치 화면이 빈 파일을 봅니다.
     */
    fun install(context: Context, file: File) {
        require(file.exists() && file.length() > 0) { "받아 둔 설치 파일이 없습니다. 다시 받아 주세요." }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 설정 화면에서 띄우므로 새 작업으로 엽니다. 설치를 끝내면 앱으로 돌아옵니다.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 받아 둔 설치 파일을 지웁니다.
     *
     * 설치가 끝나면 25MB짜리가 캐시에 남습니다. 앱을 자주 올리는 사람에게는 그것이 쌓입니다.
     */
    fun clear(context: Context) {
        File(context.cacheDir, UPDATE_DIRECTORY).listFiles()?.forEach { it.delete() }
    }
}
