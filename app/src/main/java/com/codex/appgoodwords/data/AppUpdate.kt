package com.codex.appgoodwords.data

import android.app.DownloadManager
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/**
 * 서버에 올라와 있는 설치 파일.
 *
 * 게시 스크립트(`server/publish_update.mjs`)가 쓰는 `/update/latest.json`과 같은 모양입니다.
 * 한쪽만 바꾸면 앱이 업데이트를 영영 못 찾습니다.
 */
data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val fileName: String,
    val sizeBytes: Long,
    /** 내려받은 파일을 견줄 값. 소문자 16진수 64자입니다. */
    val sha256: String,
    val publishedAt: String
) {
    /** 서버에서 받아 올 자리. 이름을 검사해 두었으므로 경로를 벗어날 수 없습니다. */
    val path: String
        get() = "$UPDATE_DIRECTORY$fileName"

    val sizeText: String
        get() = String.format("%.1f MB", sizeBytes / 1024.0 / 1024.0)

    companion object {
        const val UPDATE_DIRECTORY = "/update/"
    }
}

/**
 * 업데이트를 확인하고 받아 설치하기까지의 상태.
 *
 * 한 줄로 보여 줄 수 있어야 사용자가 지금 무엇을 기다리는지 압니다.
 */
sealed interface AppUpdateState {
    /** 아직 묻지 않았습니다. */
    data object Idle : AppUpdateState

    data object Checking : AppUpdateState

    /** 서버에 있는 것이 지금 깔린 것과 같거나 더 낮습니다. */
    data class UpToDate(val versionName: String) : AppUpdateState

    data class Available(val info: AppUpdateInfo) : AppUpdateState

    /**
     * 안드로이드가 받는 중입니다. 다른 앱으로 가도 이어집니다([AppUpdateDownloads]).
     *
     * [waitingForNetwork]는 연결이 끊겨 기다리는 때입니다. 연결되면 받은 데부터 이어 받습니다.
     */
    data class Downloading(
        val info: AppUpdateInfo,
        val downloadedBytes: Long,
        val waitingForNetwork: Boolean = false
    ) : AppUpdateState {
        /** 0~1. 서버가 알려 준 크기로 셉니다. */
        val ratio: Float
            get() = if (info.sizeBytes <= 0) 0f else (downloadedBytes.toFloat() / info.sizeBytes).coerceIn(0f, 1f)
    }

    /** 받아서 해시까지 맞춰 본 파일입니다. 누르면 안드로이드 설치 화면이 뜹니다. */
    data class Ready(val info: AppUpdateInfo, val filePath: String) : AppUpdateState

    data class Failed(val message: String) : AppUpdateState
}

/**
 * `/update/latest.json`을 읽고, 받은 것을 믿어도 되는지 봅니다.
 *
 * **설치 파일은 아무 이름이나 받지 않습니다.** 서버가 알려 준 이름을 그대로 주소에 붙이므로,
 * `../`가 섞이면 엉뚱한 파일을 받아 설치하게 됩니다. 웹 업데이트 페이지도 같은 규칙으로 거릅니다.
 */
object AppUpdateJson {
    /** 게시 스크립트가 만드는 이름입니다. 이 꼴이 아니면 받지 않습니다. */
    private val FILE_NAME = Regex("^app-good-words-[A-Za-z0-9._-]+\\.apk$")

    private val SHA256 = Regex("^[a-f0-9]{64}$")

    /** 내려받기 전에 막는 크기. 설치 파일이 이보다 크면 뭔가 잘못된 것입니다. */
    const val MAX_SIZE_BYTES = 300L * 1024 * 1024

    fun parse(text: String): AppUpdateInfo {
        val json = runCatching { JSONObject(text) }.getOrNull()
            ?: error("서버의 업데이트 정보를 읽지 못했습니다.")

        val fileName = json.optString("fileName").trim()
        require(FILE_NAME.matches(fileName)) { "서버가 알려 준 설치 파일 이름을 믿을 수 없습니다." }

        val sha256 = json.optString("sha256").trim().lowercase()
        require(SHA256.matches(sha256)) { "서버의 업데이트 정보에 설치 파일 해시가 없습니다." }

        val sizeBytes = json.optLong("sizeBytes", 0L)
        require(sizeBytes in 1..MAX_SIZE_BYTES) { "서버가 알려 준 설치 파일 크기가 올바르지 않습니다." }

        val versionCode = json.optInt("versionCode", 0)
        require(versionCode > 0) { "서버의 업데이트 정보에 버전 번호가 없습니다." }

        return AppUpdateInfo(
            // 이름이 비어 있어도 막지 않습니다. 보여 주기만 하는 값이라 번호로 가립니다.
            versionName = json.optString("versionName").trim().ifBlank { versionCode.toString() },
            versionCode = versionCode,
            fileName = fileName,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            publishedAt = json.optString("publishedAt").trim()
        )
    }

    /**
     * 받아 둘 만한 것인지 가립니다.
     *
     * **번호로만 견줍니다.** 이름(`1.6.0`)은 사람이 읽는 값이라 서버가 잘못 적어도 앱이 알 수 없고,
     * 안드로이드도 번호가 낮으면 설치를 거절합니다. 같은 번호도 새 것이 아닙니다 —
     * 다시 깔아 봐야 달라지는 것이 없습니다.
     */
    fun decide(currentVersionCode: Int, latest: AppUpdateInfo): AppUpdateState =
        if (latest.versionCode > currentVersionCode) {
            AppUpdateState.Available(latest)
        } else {
            AppUpdateState.UpToDate(latest.versionName)
        }

    /** 받는 중인 것을 기억해 둘 글. 앱이 꺼졌다 켜져도 이어서 보이고, 다 받은 뒤 맞춰 볼 해시를 잃지 않습니다. */
    fun encodePending(pending: AppUpdatePending): String = JSONObject()
        .put("downloadId", pending.downloadId)
        .put("versionName", pending.info.versionName)
        .put("versionCode", pending.info.versionCode)
        .put("fileName", pending.info.fileName)
        .put("sizeBytes", pending.info.sizeBytes)
        .put("sha256", pending.info.sha256)
        .put("publishedAt", pending.info.publishedAt)
        .toString()

    /**
     * 읽을 수 없으면 null입니다. 받던 것을 잊으면 다시 받으면 그만이지만, 못 믿을 값으로 맞춰 보고 설치하면 안 됩니다 —
     * 그래서 서버에서 받은 것과 같은 검사([parse])를 다시 거칩니다.
     */
    fun decodePending(text: String): AppUpdatePending? {
        if (text.isBlank()) return null
        val downloadId = runCatching { JSONObject(text).optLong("downloadId", 0L) }.getOrDefault(0L)
        if (downloadId <= 0L) return null
        val info = runCatching { parse(text) }.getOrNull() ?: return null
        return AppUpdatePending(downloadId, info)
    }
}

/** 안드로이드에 맡겨 받는 중인 설치 파일. [downloadId]는 DownloadManager의 번호입니다. */
data class AppUpdatePending(val downloadId: Long, val info: AppUpdateInfo)

/**
 * DownloadManager가 알려 준 한 줄을 화면의 상태로 옮깁니다.
 *
 * 숫자만 받아 안드로이드 없이 시험할 수 있게 둡니다. 값은 [DownloadManager]의 상수 그대로입니다.
 */
object AppUpdateDownloadStatus {
    sealed interface Step {
        /** 아직 받는 중입니다. [waiting]이면 연결을 기다리고 있습니다. */
        data class Receiving(val downloadedBytes: Long, val waiting: Boolean) : Step

        /** 다 받았습니다. 믿을지는 아직 모릅니다 — [AppUpdateFiles.verifyInto]가 정합니다. */
        data object Received : Step

        data class Stopped(val message: String) : Step
    }

    fun step(status: Int, reason: Int, downloadedBytes: Long): Step = when (status) {
        DownloadManager.STATUS_SUCCESSFUL -> Step.Received
        DownloadManager.STATUS_FAILED -> Step.Stopped(failureMessage(reason))
        // 멈춘 까닭은 연결이 끊겼거나, 곧 다시 해 보거나, 와이파이를 기다리는 것입니다. 모두 기다리면 이어집니다.
        DownloadManager.STATUS_PAUSED -> Step.Receiving(downloadedBytes, waiting = true)
        else -> Step.Receiving(downloadedBytes, waiting = false)
    }

    /** 실패하면 까닭이 HTTP 번호이거나 DownloadManager의 오류 번호입니다. */
    fun failureMessage(reason: Int): String = when (reason) {
        404 -> "서버에 게시된 설치 파일이 없습니다. 서버에서 먼저 새 버전을 올려 주세요."
        in 400..599 -> "서버가 설치 파일을 주지 않았습니다(HTTP $reason)."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "저장 공간이 모자라 설치 파일을 받지 못했습니다. 공간을 비운 뒤 다시 받아 주세요."
        DownloadManager.ERROR_CANNOT_RESUME, DownloadManager.ERROR_HTTP_DATA_ERROR ->
            "연결이 끊겨 이어 받지 못했습니다. 다시 받아 주세요."
        else -> "설치 파일을 받지 못했습니다. 다시 받아 주세요."
    }
}

/** 다 받은 설치 파일을 믿을지 가립니다. */
object AppUpdateFiles {
    /**
     * [source]를 [target]으로 옮겨 담으며 크기와 해시를 맞춰 봅니다. 맞지 않으면 [target]을 지우고 실패합니다.
     *
     * **옮겨 담는 까닭:** DownloadManager는 앱 안쪽 캐시에 쓰지 못해 앱 전용 바깥 저장소에 받습니다. 맞춰 본 것을 앱 안쪽에
     * 두고 그것만 설치 화면으로 넘기면, 맞춰 본 뒤에 파일이 바뀔 틈이 없습니다(옛 안드로이드는 바깥 저장소를 다른 앱도
     * 읽고 씁니다). 해시가 서버가 알려 준 것과 다르면 설치하지 않습니다 — 바꿔치기와 같은 모양이기 때문입니다.
     */
    fun verifyInto(source: File, target: File, info: AppUpdateInfo): File {
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        try {
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        copied += read
                        if (copied > info.sizeBytes) {
                            error("설치 파일이 서버가 알려 준 크기보다 큽니다. 설치하지 않았습니다.")
                        }
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            if (copied != info.sizeBytes) error("설치 파일을 끝까지 받지 못했습니다. 다시 받아 주세요.")
            val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (actual != info.sha256) error("받은 설치 파일이 서버가 알려 준 것과 다릅니다. 설치하지 않았습니다.")
            return target
        } catch (failure: Throwable) {
            // 반쯤 담은 파일이나 못 믿을 파일을 남겨 두지 않습니다. 다음에 그것이 설치될 수 있습니다.
            target.delete()
            throw failure
        }
    }
}
