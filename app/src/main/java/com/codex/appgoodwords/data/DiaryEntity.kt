package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 하루를 적어 두는 일기.
 *
 * 하루에 여러 번 쓸 수 있으므로 [entryDate]는 키가 아니라 묶는 기준입니다.
 * 첨부는 URI 문자열만 들고 있습니다. 파일 자체는 앱 밖에 있고 동기화되지 않습니다.
 */
@Entity(
    tableName = "diaries",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["entryDate"])
    ]
)
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** ISO `yyyy-MM-dd`. 기기 시간대가 달라도 같은 날로 읽히도록 문자열로 둡니다. */
    val entryDate: String,
    val title: String = "",
    val body: String = "",
    /** [DiaryWeather]의 이름. 고르지 않았으면 빈 문자열입니다. */
    val weather: String = "",
    /** [DiaryMood]의 이름. 고르지 않았으면 빈 문자열입니다. */
    val mood: String = "",
    val imageUris: List<String> = emptyList(),
    val videoUris: List<String> = emptyList(),
    val audioUris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasAttachments: Boolean
        get() = imageUris.isNotEmpty() || videoUris.isNotEmpty() || audioUris.isNotEmpty()

    // 모르는 값은 null이 됩니다. 저장된 문자열은 그대로 두므로 동기화로 사라지지는 않습니다.
    val weatherOption: DiaryWeather?
        get() = DiaryWeather.fromCode(weather)

    val moodOption: DiaryMood?
        get() = DiaryMood.fromCode(mood)

    /** 제목이 비어 있으면 목록에서 본문 앞부분을 대신 보여 줍니다. */
    val displayTitle: String
        get() = title.ifBlank { body.lineSequence().firstOrNull()?.take(30).orEmpty() }
}
