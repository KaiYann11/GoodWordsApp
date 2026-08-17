package com.codex.appgoodwords.data

/**
 * 동기화 직전에 기기 안 첨부를 서버로 올리고 주소를 서버 것으로 바꿉니다.
 *
 * 이 단계가 없으면 스냅샷에는 `content://` 주소만 담겨, 다른 기기와 웹에서는 첨부를 열 수 없습니다.
 * 주소를 바꾼 레코드는 [DiaryEntity.updatedAt]을 올려야 병합에서 이 변경이 살아남습니다.
 *
 * **한 파일이 실패해도 멈추지 않습니다.** 사용자가 원본을 지웠거나 권한이 끊긴 첨부가 있을 수 있고,
 * 사진 한 장 때문에 글까지 동기화되지 않으면 잃는 것이 더 큽니다. 실패한 주소는 그대로 둡니다.
 */
class AttachmentUploader(
    private val client: AttachmentClient,
    private val database: AppDatabase
) {
    data class Result(val uploaded: Int, val failed: Int)

    suspend fun uploadPending(settings: ServerSyncSettings): Result {
        if (settings.serverUrl.isBlank()) return Result(uploaded = 0, failed = 0)

        var uploaded = 0
        var failed = 0
        val cache = mutableMapOf<String, String>()

        suspend fun convert(uris: List<String>): List<String> = uris.map { uri ->
            if (!AttachmentUris.isLocal(uri)) return@map uri
            // 같은 파일이 여러 기록에 붙어 있으면 한 번만 올립니다.
            cache[uri]?.let { return@map it }
            val serverUri = runCatching { client.upload(settings, uri) }.getOrNull()
            if (serverUri == null) {
                failed += 1
                uri
            } else {
                cache[uri] = serverUri
                uploaded += 1
                serverUri
            }
        }

        val diaryDao = database.diaryDao()
        for (diary in diaryDao.getAll()) {
            val images = convert(diary.imageUris)
            val videos = convert(diary.videoUris)
            val audios = convert(diary.audioUris)
            if (images == diary.imageUris && videos == diary.videoUris && audios == diary.audioUris) continue
            diaryDao.insert(
                diary.copy(
                    imageUris = images,
                    videoUris = videos,
                    audioUris = audios,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        val itemDao = database.contentItemDao()
        for (item in itemDao.getAll()) {
            val images = convert(item.imageUris)
            val videos = convert(item.videoUris)
            if (images == item.imageUris && videos == item.videoUris) continue
            itemDao.insert(
                item.copy(
                    imageUris = images,
                    videoUris = videos,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        return Result(uploaded = uploaded, failed = failed)
    }
}
