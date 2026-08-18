package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

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
    /** [DiaryKind]의 이름. 자유롭게 쓰는 일기면 `FREE`입니다. */
    val kind: String = DiaryKind.FREE.name,
    /**
     * 감사·반성 일기의 물음에 대한 답. [DiaryKind.prompts]와 같은 순서입니다.
     *
     * 물음마다 열을 따로 두지 않는 이유는, 물음이 바뀌거나 늘 때마다 스키마를 고쳐야 하기 때문입니다.
     *
     * 빈칸도 자리를 지켜야 해서 [DiaryAnswerConverters]를 이 열에만 따로 붙입니다.
     * 기본 [Converters]는 빈 문자열을 버려서, 마지막 물음에만 답한 날 그 답이 첫 물음 자리로 밀립니다.
     */
    @field:TypeConverters(DiaryAnswerConverters::class)
    val answers: List<String> = emptyList(),
    val imageUris: List<String> = emptyList(),
    val videoUris: List<String> = emptyList(),
    val audioUris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasAttachments: Boolean
        get() = imageUris.isNotEmpty() || videoUris.isNotEmpty() || audioUris.isNotEmpty()

    val kindOption: DiaryKind
        get() = DiaryKind.fromCode(kind) ?: DiaryKind.FREE

    /** 적어 둔 답만. 빈칸은 뺍니다. */
    val filledAnswers: List<Pair<String, String>>
        get() = kindOption.prompts.mapIndexedNotNull { index, prompt ->
            val answer = answers.getOrNull(index)?.trim().orEmpty()
            if (answer.isBlank()) null else prompt to answer
        }

    // 모르는 값은 null이 됩니다. 저장된 문자열은 그대로 두므로 동기화로 사라지지는 않습니다.
    val weatherOption: DiaryWeather?
        get() = DiaryWeather.fromCode(weather)

    val moodOption: DiaryMood?
        get() = DiaryMood.fromCode(mood)

    /**
     * 제목이 비어 있으면 목록에서 본문 앞부분을 대신 보여 줍니다.
     *
     * 감사·반성 일기는 본문이 비고 답만 있는 경우가 흔해서, 그때는 첫 답을 씁니다.
     */
    val displayTitle: String
        get() = title.ifBlank {
            body.lineSequence().firstOrNull()?.take(30)?.takeIf { it.isNotBlank() }
                ?: filledAnswers.firstOrNull()?.second?.take(30).orEmpty()
        }
}
