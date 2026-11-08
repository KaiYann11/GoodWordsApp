package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 걸어온 길을 받아들이고 줄여 담는 규칙.
 *
 * 떨림을 거리로 세면 신호등 앞에 서 있던 동안에도 거리가 늘고, 길을 잘못 풀면 웹과 앱이 다른 길을 그립니다.
 */
class WalkPathTest {
    @Test
    fun itEncodesTheSameWayAsGoogleAndTheWeb() {
        // 구글 문서의 예시입니다. 웹(server/web/app.js decodePolyline)도 같은 글자를 풉니다.
        val points = listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453))

        val encoded = WalkPath.encode(points)

        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", encoded)
        assertEquals(points, WalkPath.decode(encoded))
    }

    @Test
    fun aBrokenPathDecodesAsFarAsItCan() {
        val whole = WalkPath.encode(listOf(GeoPoint(37.5665, 126.978), GeoPoint(37.5670, 126.9790)))

        assertEquals(1, WalkPath.decode(whole.dropLast(2)).size)
        assertEquals(emptyList<GeoPoint>(), WalkPath.decode(" !!"))
        assertEquals(emptyList<GeoPoint>(), WalkPath.decode(""))
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111Km() {
        val meters = WalkPath.distanceMeters(GeoPoint(37.0, 127.0), GeoPoint(38.0, 127.0))

        assertEquals(111_195.0, meters, 1.0)
    }

    @Test
    fun theFirstClearFixStartsThePath() {
        assertEquals(0.0, WalkPath.accept(null, fix(37.5665, 126.978, accuracy = 8f, seconds = 0))!!, 0.0)
        assertNull("흐린 첫 점", WalkPath.accept(null, fix(37.5665, 126.978, accuracy = 60f, seconds = 0)))
    }

    @Test
    fun standingStillDoesNotAddDistance() {
        // 신호등 앞에서 GPS가 2~3m씩 흔들립니다. 거리가 쌓이면 안 됩니다.
        val start = fix(37.5665, 126.978, seconds = 0)
        val jitter = fix(37.56652, 126.97802, seconds = 5)

        assertNull(WalkPath.accept(start, jitter))
    }

    @Test
    fun walkingAwayAddsTheStep() {
        val start = fix(37.5665, 126.978, seconds = 0)
        // 북쪽으로 약 11m, 10초 — 사람 걸음입니다.
        val step = WalkPath.accept(start, fix(37.5666, 126.978, seconds = 10))

        assertNotNull(step)
        assertEquals(11.1, step!!, 0.2)
    }

    @Test
    fun aGpsJumpIsIgnored() {
        // 1초에 200m는 걸어서 낼 수 없습니다.
        val start = fix(37.5665, 126.978, seconds = 0)
        val jump = fix(37.5683, 126.978, seconds = 1)

        assertNull(WalkPath.accept(start, jump))
    }

    @Test
    fun boundsHoldEveryPoint() {
        val points = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.6, 126.9), GeoPoint(37.55, 127.1))

        val bounds = WalkPath.bounds(points)!!

        assertEquals(37.5, bounds.minLat, 0.0)
        assertEquals(37.6, bounds.maxLat, 0.0)
        assertEquals(126.9, bounds.minLng, 0.0)
        assertEquals(127.1, bounds.maxLng, 0.0)
        assertNull(WalkPath.bounds(emptyList()))
        assertTrue(WalkPath.length(points) > 0.0)
    }

    private fun fix(lat: Double, lng: Double, accuracy: Float = 5f, seconds: Long) =
        WalkPath.Fix(lat = lat, lng = lng, accuracyMeters = accuracy, time = 1_000_000L + seconds * 1_000L)
}
