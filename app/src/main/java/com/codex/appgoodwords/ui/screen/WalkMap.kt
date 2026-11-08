package com.codex.appgoodwords.ui.screen

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.GeoPoint
import com.codex.appgoodwords.data.MapTiles
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal const val walkMapTag = "walk_map"

/**
 * 걸은 길을 OpenStreetMap 위에 그립니다(사용자가 정함, 2026-11-07 — 열쇠도 요금도 없는 지도).
 *
 * 처음에는 길 전체가 보이게 맞춥니다. [interactive]면 두 손가락으로 키우고 밀어 옮깁니다. [followRoute]면(걷는 중) 새 점이
 * 올 때마다 다시 맞추되, 손으로 옮긴 뒤로는 그대로 둡니다 — 보려던 곳에서 화면이 자꾸 달아나면 안 됩니다.
 *
 * 타일은 보이는 칸만 받습니다. 지도 아래 귀퉁이의 "© OpenStreetMap"은 그 지도를 쓰는 조건이라 지우지 않습니다.
 */
@Composable
fun WalkMap(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    followRoute: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileSizePx = with(density) { TILE_SIZE.toPx() }.toDouble()
    val loader = remember(context) {
        (context.applicationContext as AppGoodWordsApplication).container.mapTileImageLoader
    }
    val scope = rememberCoroutineScope()
    val bitmaps = remember { mutableStateMapOf<MapTiles.Tile, ImageBitmap>() }
    val loading = remember { mutableSetOf<MapTiles.Tile>() }
    val routeColor = MaterialTheme.colorScheme.primary
    val startColor = Color(0xFF2E7D32)

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(walkMapTag)
    ) {
        val widthPx = constraints.maxWidth.toDouble()
        val heightPx = constraints.maxHeight.toDouble()
        var center by remember { mutableStateOf<MapTiles.WorldPoint?>(null) }
        var zoom by remember { mutableStateOf(MapTiles.SINGLE_POINT_ZOOM) }
        var touched by remember { mutableStateOf(false) }

        LaunchedEffect(points, widthPx, heightPx) {
            if (points.isEmpty()) return@LaunchedEffect
            if (center == null || (followRoute && !touched)) {
                center = MapTiles.center(points)
                zoom = MapTiles.fitZoom(points, widthPx, heightPx, tileSizePx)
            }
        }

        val shownCenter = center
        if (shownCenter == null) {
            Text(
                text = if (points.isEmpty()) "위치를 찾는 중…" else "",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@BoxWithConstraints
        }

        val tiles = MapTiles.visibleTiles(shownCenter, zoom, widthPx, heightPx, tileSizePx)
        LaunchedEffect(tiles.map { it.first }) {
            tiles.forEach { (tile, _) ->
                if (tile in bitmaps || !loading.add(tile)) return@forEach
                scope.launch {
                    try {
                        val result = loader.execute(
                            ImageRequest.Builder(context).data(tile.url).allowHardware(false).build()
                        )
                        (result.drawable as? BitmapDrawable)?.bitmap?.let { bitmaps[tile] = it.asImageBitmap() }
                    } finally {
                        loading.remove(tile)
                    }
                }
            }
            // 오래 걸으며 여기저기 밀어 보면 쌓입니다. 지금 보이지 않는 것은 놓습니다(캐시에는 남습니다).
            if (bitmaps.size > MAX_KEPT_TILES) {
                val visible = tiles.mapTo(mutableSetOf()) { it.first }
                bitmaps.keys.filter { it !in visible }.forEach { bitmaps.remove(it) }
            }
        }

        val gestures = if (interactive) {
            Modifier.pointerInput(widthPx, heightPx) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    val current = center ?: return@detectTransformGestures
                    touched = true
                    val oldWorldPx = tileSizePx * 2.0.pow(zoom)
                    val panned = MapTiles.WorldPoint(current.x - pan.x / oldWorldPx, current.y - pan.y / oldWorldPx)
                    val newZoom = (zoom + log2(zoomChange.toDouble())).coerceIn(MapTiles.MIN_ZOOM, MapTiles.MAX_ZOOM)
                    val newWorldPx = tileSizePx * 2.0.pow(newZoom)
                    // 손가락 사이의 점이 그 자리에 머물게 키웁니다.
                    val dx = centroid.x - widthPx / 2.0
                    val dy = centroid.y - heightPx / 2.0
                    val underFingers = MapTiles.WorldPoint(panned.x + dx / oldWorldPx, panned.y + dy / oldWorldPx)
                    center = MapTiles.WorldPoint(underFingers.x - dx / newWorldPx, underFingers.y - dy / newWorldPx)
                    zoom = newZoom
                }
            }
        } else {
            Modifier
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestures)) {
            val drawnTile = (tileSizePx * 2.0.pow(zoom - kotlin.math.floor(zoom))).roundToInt()
            tiles.forEach { (tile, offset) ->
                val image = bitmaps[tile] ?: return@forEach
                drawImage(
                    image = image,
                    dstOffset = IntOffset(offset.first.roundToInt(), offset.second.roundToInt()),
                    dstSize = IntSize(drawnTile, drawnTile)
                )
            }
            val screen = points.map { point ->
                val (x, y) = MapTiles.toScreen(MapTiles.project(point), shownCenter, zoom, widthPx, heightPx, tileSizePx)
                Offset(x.toFloat(), y.toFloat())
            }
            if (screen.size >= 2) {
                val path = Path().apply {
                    moveTo(screen.first().x, screen.first().y)
                    screen.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = path,
                    color = routeColor,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            screen.firstOrNull()?.let { start ->
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = start)
                drawCircle(color = startColor, radius = 5.dp.toPx(), center = start)
            }
            if (screen.size >= 2) {
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = screen.last())
                drawCircle(color = routeColor, radius = 5.dp.toPx(), center = screen.last())
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.White.copy(alpha = 0.75f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text("© OpenStreetMap", style = MaterialTheme.typography.labelSmall, color = Color(0xFF333333))
        }
    }
}

/** 타일 한 장을 그리는 크기. 256픽셀 타일을 이보다 크게 그리면 흐려지고, 작게 그리면 글자가 깨알 같습니다. */
private val TILE_SIZE = 160.dp

private const val MAX_KEPT_TILES = 80
