package com.codex.appgoodwords.data

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

/**
 * 지도 타일의 셈(웹 메르카토르). 걸은 길을 OpenStreetMap 타일 위에 그릴 때 씁니다.
 *
 * 세계를 가로세로 0~1인 한 장으로 놓고 셉니다. 확대 단계 z에서 그 한 장은 2^z×2^z 타일입니다. 화면 크기와 타일의
 * 화면 크기는 부르는 쪽이 넘깁니다 — 여기에는 화면이 없습니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object MapTiles {
    /** OpenStreetMap 기본 타일이 내주는 가장 깊은 단계. */
    const val MAX_ZOOM = 19.0
    const val MIN_ZOOM = 2.0

    /** 메르카토르가 그리는 위도의 끝. 넘으면 무한대가 됩니다. */
    private const val MAX_LATITUDE = 85.05112878

    /** 세계 한 장 위의 자리(0~1). x는 동쪽, y는 남쪽으로 커집니다. */
    data class WorldPoint(val x: Double, val y: Double)

    data class Tile(val zoom: Int, val x: Int, val y: Int) {
        /** OpenStreetMap 기본 타일 주소. */
        val url: String
            get() = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
    }

    fun project(point: GeoPoint): WorldPoint {
        val lat = Math.toRadians(point.lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        val x = (point.lng + 180.0) / 360.0
        val y = (1.0 - ln(tan(lat) + 1.0 / kotlin.math.cos(lat)) / PI) / 2.0
        return WorldPoint(x, y)
    }

    fun unproject(world: WorldPoint): GeoPoint {
        val lng = world.x * 360.0 - 180.0
        val lat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * world.y))))
        return GeoPoint(lat, lng)
    }

    /**
     * 길 전체가 화면에 들어오는 확대 단계. 둘레에 [paddingFraction]만큼 여유를 둡니다 — 끝점이 화면 가장자리에 붙으면
     * 어디서 시작했는지 보기 어렵습니다. 점 하나뿐이면 골목이 보이는 [SINGLE_POINT_ZOOM]입니다.
     *
     * @param tileSizePx 타일 한 장을 화면에 그리는 크기(픽셀).
     */
    fun fitZoom(
        points: List<GeoPoint>,
        viewportWidthPx: Double,
        viewportHeightPx: Double,
        tileSizePx: Double,
        paddingFraction: Double = 0.15
    ): Double {
        if (points.size < 2 || viewportWidthPx <= 0.0 || viewportHeightPx <= 0.0) return SINGLE_POINT_ZOOM
        val world = points.map(::project)
        val spanX = world.maxOf { it.x } - world.minOf { it.x }
        val spanY = world.maxOf { it.y } - world.minOf { it.y }
        val usableWidth = viewportWidthPx * (1.0 - 2 * paddingFraction)
        val usableHeight = viewportHeightPx * (1.0 - 2 * paddingFraction)
        // 세계 한 장의 폭은 tileSize×2^z 픽셀입니다. span×그 폭이 화면 안에 들어오는 가장 큰 z를 찾습니다.
        val zoomX = if (spanX > 0.0) log2(usableWidth / (spanX * tileSizePx)) else MAX_ZOOM
        val zoomY = if (spanY > 0.0) log2(usableHeight / (spanY * tileSizePx)) else MAX_ZOOM
        return minOf(zoomX, zoomY, SINGLE_POINT_ZOOM + 1).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /** 길의 가운데(세계 한 장 위). 점이 없으면 null입니다. */
    fun center(points: List<GeoPoint>): WorldPoint? {
        if (points.isEmpty()) return null
        val world = points.map(::project)
        return WorldPoint(
            x = (world.minOf { it.x } + world.maxOf { it.x }) / 2.0,
            y = (world.minOf { it.y } + world.maxOf { it.y }) / 2.0
        )
    }

    /**
     * 화면을 덮는 타일과 각 타일의 왼쪽 위 자리(화면 픽셀).
     *
     * 확대 단계가 소수면 아래 정수 단계의 타일을 키워 그립니다(예: 15.5면 15단계 타일을 √2배). 위아래 끝을 넘는 타일은
     * 없으니 빼고, 날짜변경선을 넘는 x는 둘러 잇습니다.
     */
    fun visibleTiles(
        center: WorldPoint,
        zoom: Double,
        viewportWidthPx: Double,
        viewportHeightPx: Double,
        tileSizePx: Double
    ): List<Pair<Tile, Pair<Double, Double>>> {
        val level = floor(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)).toInt()
        val scale = 2.0.pow(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) - level)
        val drawnTile = tileSizePx * scale
        val tilesPerSide = 1 shl level
        // 세계 한 장을 이 단계에서 그린 크기(픽셀) 위에서 화면 왼쪽 위가 어디인지.
        val worldPx = drawnTile * tilesPerSide
        val left = center.x * worldPx - viewportWidthPx / 2.0
        val top = center.y * worldPx - viewportHeightPx / 2.0
        val firstX = floor(left / drawnTile).toInt()
        val lastX = floor((left + viewportWidthPx) / drawnTile).toInt()
        val firstY = floor(top / drawnTile).toInt().coerceAtLeast(0)
        val lastY = floor((top + viewportHeightPx) / drawnTile).toInt().coerceAtMost(tilesPerSide - 1)
        return buildList {
            for (ty in firstY..lastY) {
                for (tx in firstX..lastX) {
                    val wrapped = Math.floorMod(tx, tilesPerSide)
                    add(Tile(level, wrapped, ty) to Pair(tx * drawnTile - left, ty * drawnTile - top))
                }
            }
        }
    }

    /** 세계 한 장 위의 점이 화면 어디에 오는지(픽셀). [visibleTiles]와 같은 셈입니다. */
    fun toScreen(
        world: WorldPoint,
        center: WorldPoint,
        zoom: Double,
        viewportWidthPx: Double,
        viewportHeightPx: Double,
        tileSizePx: Double
    ): Pair<Double, Double> {
        val worldPx = tileSizePx * 2.0.pow(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        return Pair(
            (world.x - center.x) * worldPx + viewportWidthPx / 2.0,
            (world.y - center.y) * worldPx + viewportHeightPx / 2.0
        )
    }

    /** 점 하나만 있을 때의 단계. 골목과 건물이 보이는 정도입니다. */
    const val SINGLE_POINT_ZOOM = 16.0
}
