package com.codex.appgoodwords.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** 위도·경도 한 점. */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 걸어온 길을 다룹니다 — 어느 GPS 점을 받아들일지, 거리, 줄여 담기.
 *
 * **떨림을 거리로 세지 않습니다.** 서 있어도 GPS 점은 몇 미터씩 흔들립니다. 그것을 다 이으면 신호등 앞에서 기다린
 * 동안에도 거리가 늘어납니다. 그래서 흐린 점, 너무 가까운 점, 사람이 걸어서 낼 수 없는 빠르기로 튄 점을 버립니다.
 *
 * 길은 구글 polyline 꼴로 줄여 담습니다(소수 다섯째 자리, 약 1m). 한 시간 걸어도 몇 KB라 동기화에 부담이 없습니다.
 * 웹(`server/web/app.js`)도 같은 꼴을 풉니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object WalkPath {
    /** 이보다 흐린 점(정확도 반경, 미터)은 버립니다. */
    const val MAX_ACCURACY_METERS = 25f

    /** 지난 점에서 이만큼 멀어져야 새 점으로 받습니다. 서 있는 동안의 떨림이 거리로 쌓이지 않게 합니다. */
    const val MIN_STEP_METERS = 5.0

    /** 이보다 빠르면(m/s) GPS가 튄 것으로 봅니다. 뛰어도 넘기 어려운 빠르기입니다. */
    const val MAX_SPEED_MPS = 12.0

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val PRECISION = 1e5

    /** GPS가 준 점 하나. */
    data class Fix(val lat: Double, val lng: Double, val accuracyMeters: Float, val time: Long)

    /**
     * 새 점을 길에 더할지. 더한다면 지난 점에서 몇 미터인지 돌려줍니다(첫 점은 0). 버리면 null입니다.
     *
     * 버린 점은 "지난 점"이 되지 않습니다. 천천히 걸어도 5m를 넘는 순간 그만큼이 한 번에 더해집니다.
     */
    fun accept(last: Fix?, next: Fix): Double? {
        if (!next.lat.isFinite() || !next.lng.isFinite()) return null
        if (next.lat !in -90.0..90.0 || next.lng !in -180.0..180.0) return null
        if (next.accuracyMeters > MAX_ACCURACY_METERS) return null
        if (last == null) return 0.0
        val meters = distanceMeters(GeoPoint(last.lat, last.lng), GeoPoint(next.lat, next.lng))
        if (meters < MIN_STEP_METERS) return null
        val seconds = (next.time - last.time) / 1000.0
        if (seconds > 0.0 && meters / seconds > MAX_SPEED_MPS) return null
        return meters
    }

    /** 두 점 사이의 땅 위 거리(미터, 하버사인). 걷는 거리에서는 미터 아래까지 맞습니다. */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(to.lng - from.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** 이어진 점들의 길이(미터). */
    fun length(points: List<GeoPoint>): Double =
        points.zipWithNext { a, b -> distanceMeters(a, b) }.sum()

    fun encode(points: List<GeoPoint>): String {
        val out = StringBuilder()
        var previousLat = 0L
        var previousLng = 0L
        for (point in points) {
            val lat = (point.lat * PRECISION).roundToLong()
            val lng = (point.lng * PRECISION).roundToLong()
            encodeValue(lat - previousLat, out)
            encodeValue(lng - previousLng, out)
            previousLat = lat
            previousLng = lng
        }
        return out.toString()
    }

    /**
     * 줄여 담은 길을 풉니다. 깨진 곳이 있으면 거기까지만 돌려줍니다 — 망가진 기록 하나 때문에 화면이 서면 안 됩니다.
     */
    fun decode(path: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0L
        var lng = 0L
        while (index < path.length) {
            val dLat = decodeValue(path, index) ?: break
            val dLng = decodeValue(path, dLat.second) ?: break
            index = dLng.second
            lat += dLat.first
            lng += dLng.first
            points += GeoPoint(lat / PRECISION, lng / PRECISION)
        }
        return points
    }

    /** 점들을 다 담는 사각형. 점이 없으면 null입니다. */
    fun bounds(points: List<GeoPoint>): Bounds? {
        if (points.isEmpty()) return null
        return Bounds(
            minLat = points.minOf { it.lat },
            minLng = points.minOf { it.lng },
            maxLat = points.maxOf { it.lat },
            maxLng = points.maxOf { it.lng }
        )
    }

    data class Bounds(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double)

    private fun encodeValue(value: Long, out: StringBuilder) {
        var remaining = if (value < 0) (value shl 1).inv() else value shl 1
        while (remaining >= 0x20) {
            out.append(((0x20L or (remaining and 0x1F)) + 63).toInt().toChar())
            remaining = remaining shr 5
        }
        out.append((remaining + 63).toInt().toChar())
    }

    private fun decodeValue(path: String, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var index = start
        while (true) {
            if (index >= path.length || shift > 60) return null
            val chunk = path[index].code - 63
            index += 1
            if (chunk !in 0..63) return null
            result = result or ((chunk and 0x1F).toLong() shl shift)
            shift += 5
            if (chunk < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }
}
