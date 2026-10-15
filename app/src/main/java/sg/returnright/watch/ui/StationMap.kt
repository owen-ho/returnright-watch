package sg.returnright.watch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import sg.returnright.watch.data.Station
import sg.returnright.watch.navigation.*
import kotlin.math.*

@Composable
fun StationMap(stations: List<Station>, selected: Station, position: Coordinate?, stale: Boolean,
    onSelect: (Station) -> Unit, modifier: Modifier = Modifier) {
    var center by remember(selected.id) { mutableStateOf(Coordinate(selected.latitude, selected.longitude)) }
    var zoom by remember { mutableIntStateOf(16) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var retry by remember { mutableIntStateOf(0) }
    var images by remember { mutableStateOf<Map<Tile, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }
    var mapError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val repository = TileRepository.shared(LocalContext.current)
    val point = Geo.world(center, zoom)
    // Only request tiles intersecting the currently visible viewport.
    val visible = remember(center, zoom, size) {
        if (size.width == 0) emptyList() else buildList {
            val extent = 1 shl zoom
            val x0 = floor((point.x - size.width / 2.0) / 256).toInt()
            val x1 = floor((point.x + size.width / 2.0) / 256).toInt()
            val y0 = floor((point.y - size.height / 2.0) / 256).toInt()
            val y1 = floor((point.y + size.height / 2.0) / 256).toInt()
            for (x in x0..x1) for (y in y0..y1) if (x in 0 until extent && y in 0 until extent) add(Tile(zoom, x, y))
        }
    }
    LaunchedEffect(visible, retry) {
        delay(180)
        loading = true
        mapError = false
        images = images.filterKeys { it in visible }
        try {
            for (tile in visible) {
                ensureActive()
                if (tile in images) continue
                val bitmap = try { repository.load(tile) } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                ensureActive()
                if (bitmap == null) mapError = true else images = images + (tile to bitmap)
            }
        } finally { loading = false }
    }
    fun screen(coordinate: Coordinate): Offset {
        val p = Geo.world(coordinate, zoom)
        return Offset((p.x - point.x + size.width / 2.0).toFloat(), (p.y - point.y + size.height / 2.0).toFloat())
    }
    Box(modifier.background(Color(0xFF14201D))) {
        Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }
            .semantics { contentDescription = "Street map. Drag to pan; use plus and minus to zoom. Station markers show reported status." }
            .pointerInput(zoom) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val current = Geo.world(center, zoom)
                    center = Geo.coordinate(WorldPoint(current.x - drag.x, current.y - drag.y), zoom)
                }
            }
            .pointerInput(stations, center, zoom, size) {
                detectTapGestures { tap ->
                    stations.minByOrNull { (screen(Coordinate(it.latitude, it.longitude)) - tap).getDistance() }
                        ?.takeIf { (screen(Coordinate(it.latitude, it.longitude)) - tap).getDistance() < 22.dp.toPx() }
                        ?.let(onSelect)
                }
            }) {
            images.forEach { (tile, bitmap) ->
                drawImage(bitmap, dstOffset = IntOffset((tile.x * 256 - point.x + size.width / 2.0).roundToInt(),
                    (tile.y * 256 - point.y + size.height / 2.0).roundToInt()), dstSize = IntSize(256, 256))
            }
            stations.forEach { station ->
                val at = screen(Coordinate(station.latitude, station.longitude))
                if (at.x in -20f..(size.width + 20f) && at.y in -20f..(size.height + 20f)) {
                    val radius = if (station.id == selected.id) 8.dp.toPx() else 5.dp.toPx()
                    drawCircle(Color.White, radius + 2.dp.toPx(), at)
                    drawCircle(if (stale) Muted else statusColor(station.status), radius, at)
                    if (station.id == selected.id) drawCircle(Color(0xFF172D20), radius + 5.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                }
            }
            position?.let {
                val at = screen(it)
                drawCircle(Color(0x663E8CFF), 13.dp.toPx(), at)
                drawCircle(Color.White, 6.dp.toPx(), at)
                drawCircle(Color(0xFF2979FF), 4.dp.toPx(), at)
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MapButton("+", "Zoom in", enabled = zoom < 18) { zoom++ }
            MapButton("−", "Zoom out", enabled = zoom > 11) { zoom-- }
            MapButton("◎", "Center on selected station") { center = Coordinate(selected.latitude, selected.longitude) }
        }
        if (loading || mapError) Column(Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
            .background(Color(0xDF081A14)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (loading) "Map loading…" else "Map unavailable", fontSize = 10.sp)
            if (!loading) MapButton("↻", "Retry map") { retry++ }
        }
        // Kept inside the round display's safe region, visible in every map state.
        Text("© OpenStreetMap contributors", color = Color.White, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 45.dp)
                .background(Color(0xDC081A14)).padding(horizontal = 4.dp, vertical = 2.dp))
    }
}
