package com.codex.appgoodwords.data

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SyncBackupKind(
    val fileNamePrefix: String,
    val label: String
) {
    BEFORE_UPLOAD("before-upload", "업로드 전 서버 데이터"),
    BEFORE_DOWNLOAD("before-download", "가져오기 전 기기 데이터"),
    BEFORE_MERGE("before-merge", "병합 전 기기 데이터"),
    BEFORE_AUTO_MERGE("before-auto-merge", "자동 동기화 전 기기 데이터"),
    BEFORE_RESTORE("before-restore", "복원 전 기기 데이터");

    companion object {
        fun fromFileName(fileName: String): SyncBackupKind? = values()
            .firstOrNull { kind -> fileName.startsWith("sync-${kind.fileNamePrefix}-") }
    }
}

data class SyncBackup(
    val fileName: String,
    val kind: SyncBackupKind,
    val createdAt: Long,
    val sizeBytes: Long
)

/**
 * 스냅샷 전체 교체(업로드/가져오기/복원) 직전 상태를 파일로 남겨 되돌릴 수 있게 합니다.
 * 앱 전용 외부 저장소에 저장해 사용자가 파일 관리자나 USB로도 꺼낼 수 있게 합니다.
 */
class SyncBackupStore(
    private val context: Context
) {
    suspend fun save(
        kind: SyncBackupKind,
        snapshot: AppDataSnapshot
    ): SyncBackup = withContext(Dispatchers.IO) {
        val directory = backupDirectory()
        val fileName = "sync-${kind.fileNamePrefix}-${fileTimestamp(System.currentTimeMillis())}.json"
        val file = File(directory, fileName)
        file.writeText(AppDataJson.toJson(snapshot).toString(2), Charsets.UTF_8)
        pruneOldBackups(directory)
        SyncBackup(
            fileName = fileName,
            kind = kind,
            createdAt = file.lastModified(),
            sizeBytes = file.length()
        )
    }

    suspend fun list(): List<SyncBackup> = withContext(Dispatchers.IO) {
        backupFiles(backupDirectory())
            .mapNotNull { file ->
                val kind = SyncBackupKind.fromFileName(file.name) ?: return@mapNotNull null
                SyncBackup(
                    fileName = file.name,
                    kind = kind,
                    createdAt = file.lastModified(),
                    sizeBytes = file.length()
                )
            }
            .sortedByDescending { it.createdAt }
    }

    suspend fun load(backup: SyncBackup): AppDataSnapshot = withContext(Dispatchers.IO) {
        val file = File(backupDirectory(), backup.fileName)
        require(file.isFile) { "백업 파일을 찾을 수 없습니다: ${backup.fileName}" }
        AppDataJson.fromJsonText(file.readText(Charsets.UTF_8))
    }

    suspend fun directoryPath(): String = withContext(Dispatchers.IO) {
        backupDirectory().absolutePath
    }

    private fun backupDirectory(): File {
        val directory = context.getExternalFilesDir(DIRECTORY_NAME)
            ?: File(context.filesDir, DIRECTORY_NAME)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    private fun backupFiles(directory: File): List<File> = directory
        .listFiles { file -> file.isFile && SyncBackupKind.fromFileName(file.name) != null }
        .orEmpty()
        .toList()

    /**
     * 종류별로 따로 셉니다.
     * 한 묶음으로 세면 자동 동기화가 자주 돌 때 사용자가 직접 만든 백업을 밀어냅니다.
     */
    private fun pruneOldBackups(directory: File) {
        backupFiles(directory)
            .groupBy { file -> SyncBackupKind.fromFileName(file.name) }
            .forEach { (_, files) ->
                files.sortedByDescending { it.lastModified() }
                    .drop(MAX_BACKUPS_PER_KIND)
                    .forEach { file -> file.delete() }
            }
    }

    private fun fileTimestamp(millis: Long): String = FILE_TIMESTAMP_FORMATTER
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    companion object {
        private const val DIRECTORY_NAME = "sync-backups"
        private const val MAX_BACKUPS_PER_KIND = 5
        private val FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
