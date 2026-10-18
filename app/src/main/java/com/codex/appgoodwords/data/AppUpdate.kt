package com.codex.appgoodwords.data

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

    data class Downloading(val info: AppUpdateInfo, val downloadedBytes: Long) : AppUpdateState {
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
}
