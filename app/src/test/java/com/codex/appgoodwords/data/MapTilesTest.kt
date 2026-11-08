package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 걸은 길을 OpenStreetMap 타일 위에 놓는 셈.
 *
 * 틀리면 길이 엉뚱한 동네 위에 그려지거나, 화면 가장자리에 빈 칸이 생깁니다.
 */
class MapTilesTest {
    private val cityHall = GeoPoint(37.5665, 126.978)

    @Test
    fun seoulCityHallFallsOnItsOsmTile() {
        // OpenStreetMap에서 z16의 서울시청 타일은 55883/25378입니다.
        val world = MapTiles.project(cityHall)
        val tiles = 1 shl 16

        assertEquals(55_883, (world.x * tiles).toInt())
        assertEquals(25_378, (world.y * tiles).toInt())
        assertEquals("https://tile.openstreetmap.org/16/55883/25378.png", MapTiles.Tile(16, 55_883, 25_378).url)
    }

    @Test
    fun projectingAndBackLandsOnTheSamePlace() {
        val back = MapTiles.unproject(MapTiles.project(cityHall))

        assertEquals(cityHall.lat, back.lat, 1e-9)
        assertEquals(cityHall.lng, back.lng, 1e-9)
    }

    @Test
    fun theWholeRouteFitsInsideThePaddedView() {
        // 시청에서 광화문까지 약 1km.
        val route = listOf(cityHall, GeoPoint(37.5759, 126.9768))
        val width = 1_080.0
        val height = 840.0
        val tile = 480.0

        val zoom = MapTiles.fitZoom(route, width, height, tile)
        val center = MapTiles.center(route)!!

        route.forEach { point ->
            val (x, y) = MapTiles.toScreen(MapTiles.project(point), center, zoom, width, height, tile)
            assertTrue("x=$x", x in 0.0..width)
            assertTrue("y=$y", y in 0.0..height)
        }
        assertTrue("너무 멀리서 봅니다: $zoom", zoom > 13.0)
    }

    @Test
    fun aSinglePointShowsTheStreets() {
        assertEquals(MapTiles.SINGLE_POINT_ZOOM, MapTiles.fitZoom(listOf(cityHall), 1_080.0, 840.0, 480.0), 0.0)
    }

    @Test
    fun theTilesCoverTheWholeView() {
        val center = MapTiles.project(cityHall)
        val width = 1_080.0
        val height = 840.0
        val tile = 480.0

        val tiles = MapTiles.visibleTiles(center, 15.5, width, height, tile)
        val drawn = tile * Math.sqrt(2.0)

        assertTrue(tiles.minOf { it.second.first } <= 0.0)
        assertTrue(tiles.minOf { it.second.second } <= 0.0)
        assertTrue(tiles.maxOf { it.second.first } + drawn >= width)
        assertTrue(tiles.maxOf { it.second.second } + drawn >= height)
        assertTrue(tiles.all { it.first.zoom == 15 })
    }

    @Test
    fun tilesBeyondTheDateLineWrapAround() {
        val nearDateLine = MapTiles.WorldPoint(x = 0.0005, y = 0.4)

        val tiles = MapTiles.visibleTiles(nearDateLine, 4.0, 1_080.0, 840.0, 480.0)

        assertTrue(tiles.all { it.first.x in 0 until 16 })
    }
}
