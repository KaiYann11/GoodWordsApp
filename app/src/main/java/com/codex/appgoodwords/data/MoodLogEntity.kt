package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 오늘 기분 한 번.
 *
 * 예전에는 기분이 일기에만 붙어 있었습니다. 그래서 **일기를 써야만 기분이 남았습니다.**
 * 그런데 바쁘고 힘든 날일수록 일기를 못 씁니다. 가장 알고 싶은 날이 가장 확실하게 비어 있었고,
 * 기분 그래프는 점이 성글어 모양이 안 나오고, 기분과 실천을 겹쳐 보는 자리는 그런 날을
 * 통째로 버렸습니다.
 *
 * 여기에는 기분만 담습니다. 한 줄이라도 적고 싶어지면 그것은 일기입니다. 적을 것이 늘어나면
 * 3초에 끝나지 않고, 3초에 안 끝나면 바쁜 날 또 비어 버립니다.
 *
 * **누를 때마다 남깁니다**(사용자가 정함, 2026-10-29). 하루 안에서도 바뀌는 마음을 보려는
 * 기록이라 앞선 기분을 덮지 않습니다. 그날의 기분과 흐름은 [DayMood] 한 곳에서 읽습니다.
 * 칸과 인덱스는 이미 여러 기록을 담을 수 있어 Room 스키마는 바꾸지 않습니다.
 */
@Entity(
    tableName = "mood_logs",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["entryDate"])
    ]
)
data class MoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** ISO `yyyy-MM-dd`. */
    val entryDate: String = "",
    /** [DiaryMood]의 코드. 일기와 같은 선택지를 씁니다. */
    val mood: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val moodOption: DiaryMood?
        get() = DiaryMood.fromCode(mood)
}
