package com.codex.appgoodwords.data

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * Health Connect에서 걸음 수를 읽습니다(사용자가 정함, 2026-11-07 — S24는 삼성 헬스가 여기에 걸음을 넣습니다).
 *
 * **읽기만 합니다.** 걸음을 세는 것은 휴대폰(삼성 헬스, Android 14부터는 Health Connect 자신)이고 이 앱은 하루치를
 * 받아 적기만 합니다([StepCountEntity]). 같은 날을 여러 앱이 적어도 Health Connect가 합칠 때 겹치지 않게 골라 줍니다.
 *
 * 라이브러리는 `1.1.0-alpha12`입니다. 1.1.0 정식판은 AGP 8.9.1·compileSdk 36·Kotlin 2를 요구해, 지금 빌드 도구로는
 * 쓸 수 없습니다(AGENTS.md).
 */
class HealthSteps(private val context: Context) {
    enum class Availability {
        AVAILABLE,
        /** 휴대폰에 있지만 Health Connect를 업데이트해야 합니다(Android 13 아래). */
        NEEDS_UPDATE,
        /** 이 휴대폰에서는 쓸 수 없습니다. */
        UNAVAILABLE
    }

    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    fun availability(): Availability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.NEEDS_UPDATE
        else -> Availability.UNAVAILABLE
    }

    /** 화면이 보는 연결 상태. */
    suspend fun connection(): HealthConnection = when (availability()) {
        Availability.AVAILABLE -> if (hasPermission()) HealthConnection.CONNECTED else HealthConnection.NOT_CONNECTED
        Availability.NEEDS_UPDATE -> HealthConnection.NEEDS_UPDATE
        Availability.UNAVAILABLE -> HealthConnection.UNAVAILABLE
    }

    /** 걸음 읽기를 허락받았는지. Health Connect가 없으면 false입니다. */
    suspend fun hasPermission(): Boolean {
        if (availability() != Availability.AVAILABLE) return false
        return runCatching { client().permissionController.getGrantedPermissions().containsAll(permissions) }
            .getOrDefault(false)
    }

    /**
     * 날짜별 걸음 수. [from]부터 [to]까지(둘 다 넣음) 걸음이 있었던 날만 담습니다.
     *
     * Health Connect는 다른 앱이 허락받기 30일 전보다 오래된 기록을 읽지 못하게 합니다. 그보다 앞을 물으면 비어 옵니다.
     */
    suspend fun dailySteps(from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
        val response = client().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                // 기기 시간대의 하루로 자릅니다. 한국에서 걸은 하루가 UTC 자정에 갈리면 아침 걸음이 어제가 됩니다.
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1)
            )
        )
        return response
            .mapNotNull { slice ->
                val steps = slice.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                slice.startTime.toLocalDate() to steps.toInt()
            }
            .filter { (_, steps) -> steps > 0 }
            .toMap()
    }

    /** 걷기 한 번 동안의 걸음 수. 모르면 0입니다. */
    suspend fun stepsBetween(start: Instant, end: Instant): Int {
        if (!end.isAfter(start)) return 0
        val result = client().aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return (result[StepsRecord.COUNT_TOTAL] ?: 0L).toInt()
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    companion object {
        /** Android 13 아래에서 Health Connect를 깔거나 올리는 곳(Play 스토어). */
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

        /** 화면이 권한을 물을 때 씁니다. 결과는 허락받은 권한 묶음입니다. */
        fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()

        /** 한 번에 다시 읽는 날수. Health Connect가 허락하는 만큼입니다. */
        const val REFRESH_DAYS = 30L
    }
}

/** 홈 걷기 카드·설정이 보는 Health Connect 상태. */
enum class HealthConnection {
    /** 걸음 읽기를 허락받았습니다. */
    CONNECTED,
    /** 있지만 아직 허락받지 않았습니다. "걸음 수 가져오기"로 묻습니다. */
    NOT_CONNECTED,
    /** Android 13 아래에서 Health Connect 앱을 깔거나 올려야 합니다. */
    NEEDS_UPDATE,
    /** 이 휴대폰에서는 쓸 수 없습니다. 걷기 기록은 그대로 됩니다. */
    UNAVAILABLE,
    /** 아직 확인하지 않았습니다(앱을 막 열었을 때). */
    UNKNOWN
}
