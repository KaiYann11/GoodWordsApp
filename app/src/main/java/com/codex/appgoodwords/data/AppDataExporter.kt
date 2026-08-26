package com.codex.appgoodwords.data

import android.content.Context
import android.net.Uri

class AppDataExporter(
    private val context: Context
) {
    /**
     * 기기 데이터를 파일 하나로 씁니다.
     *
     * **스냅샷을 통째로 받습니다.** 예전에는 종류별로 인자를 받았는데, 일기·할 일·책이 생겼을 때
     * 여기에 더하는 것을 빠뜨려서 내보낸 파일에 그 셋이 담기지 않았습니다. 그 파일을
     * `가져오기(교체)`로 되넣으면 [AppDataImporter]가 먼저 전부 지우므로 일기가 영영 사라졌습니다.
     *
     * 그래서 "내 데이터 전부"를 아는 곳은 [SyncCoordinator.currentSnapshot] 한 곳만 두고,
     * 내보내기도 동기화도 그것을 씁니다. 새 종류가 생겨도 고칠 곳이 하나입니다.
     */
    fun export(uri: Uri, snapshot: AppDataSnapshot): Int {
        val payload = AppDataJson.toJson(snapshot)

        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: error("내보내기 파일을 열 수 없습니다.")

        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(payload.toString(2))
        }

        return snapshot.recordCount
    }
}
