package com.codex.appgoodwords.data

/**
 * 걷기 한 번. "걷기 기록 시작"부터 "끝내기"까지 GPS로 지나온 길입니다(사용자가 정함, 2026-11-07).
 *
 * **위치는 이 기록에만 있습니다.** 지나온 길([path])은 이 기기와 사용자의 서버에만 남고, AI에는 거리·시간·걸음 수만
 * 갑니다(사용자가 정함). 하루 점수·AI가 보는 하루([DayDigest])에는 길을 뺀 [WalkSummary]만 실립니다 — 길을 들고 있지
 * 않으면 실수로 실어 보낼 수도 없습니다.
 *
 * **걷는 동안에도 이 표에 있습니다**([endedAt]이 0). 기록하는 서비스가 죽어도 그때까지의 길이 남게 자주 적어 둡니다.
 * 끝나지 않은 걷기는 동기화에 싣지 않고, 다음에 앱이 켜질 때 마지막으로 적은 때에 맞춰 마무리합니다
 * ([WalkRepository.finishInterrupted]).
 */
@androidx.room.Entity(
    tableName = "walks",
    indices = [
        androidx.room.Index(value = ["syncId"], unique = true),
        androidx.room.Index(value = ["startedAt"])
    ]
)
data class WalkEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    /** 걷는 동안에는 마지막으로 길을 적은 때입니다. 끊긴 걷기를 그때에 맞춰 마무리합니다. */
    val updatedAt: Long = System.currentTimeMillis(),
    val startedAt: Long = 0L,
    /** 0이면 아직 걷는 중입니다. */
    val endedAt: Long = 0L,
    /** 지나온 거리(미터). 받아들인 점 사이를 이은 길이입니다([WalkPath.accept]). */
    val distanceMeters: Int = 0,
    /** 걷는 동안의 걸음 수(Health Connect). 모르면 0입니다. */
    val steps: Int = 0,
    /** 지나온 길. 구글 polyline 꼴(소수 다섯째 자리, 약 1m)로 줄여 둡니다([WalkPath.encode]). */
    val path: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isFinished: Boolean
        get() = endedAt > 0L

    val durationMillis: Long
        get() = if (isFinished) (endedAt - startedAt).coerceAtLeast(0L) else 0L

    /** 길을 뺀 숫자만. 하루 점수와 AI가 봅니다. */
    fun summary(): WalkSummary = WalkSummary(
        syncId = syncId,
        startedAt = startedAt,
        endedAt = endedAt,
        distanceMeters = distanceMeters,
        steps = steps
    )
}

/** 걷기 한 번의 숫자. 길([WalkEntity.path])은 들고 있지 않습니다. */
data class WalkSummary(
    val syncId: String,
    val startedAt: Long,
    val endedAt: Long,
    val distanceMeters: Int,
    val steps: Int
) {
    val durationMillis: Long
        get() = (endedAt - startedAt).coerceAtLeast(0L)
}
