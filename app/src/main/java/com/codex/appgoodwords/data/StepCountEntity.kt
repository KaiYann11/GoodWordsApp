package com.codex.appgoodwords.data

/**
 * 어느 날 몇 걸음을 걸었는지. Health Connect에서 읽어 온 하루치입니다.
 *
 * **이 앱이 세지 않고 받아 적습니다.** 걸음은 휴대폰(S24에서는 삼성 헬스)이 셉니다. 그래도 옮겨 적어 두는 까닭은
 * 둘입니다. Health Connect는 다른 앱이 30일 넘은 기록을 읽지 못하게 막아, 적어 두지 않으면 지난달의 "걸은 날"을
 * 다시 셀 수 없습니다. 그리고 서버·웹은 Health Connect를 볼 수 없습니다.
 *
 * **하루에 하나입니다.** 다시 읽으면 그날 것을 고칩니다(그날 걸음은 저녁까지 늘어납니다).
 * 걸음 하나하나를 쌓지 않습니다 — 언제 걸었는지가 아니라 그날 얼마나 걸었는지만 씁니다.
 */
@androidx.room.Entity(
    tableName = "step_counts",
    indices = [
        androidx.room.Index(value = ["syncId"], unique = true),
        androidx.room.Index(value = ["date"])
    ]
)
data class StepCountEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** ISO `yyyy-MM-dd`. 기기 시간대의 하루입니다. */
    val date: String = "",
    val steps: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
