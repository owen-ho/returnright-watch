package sg.returnright.watch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.launch
import sg.returnright.watch.FeedState
import sg.returnright.watch.data.*
import sg.returnright.watch.navigation.*
import java.util.Locale

val Accent = Color(0xFFB5F775)
val Muted = Color(0xFFA5B6AD)
private val Background = Color(0xFF07130E)
private val SurfaceColor = Color(0xFF18291F)

fun statusColor(status: StationStatus): Color = when (status) {
    StationStatus.RUNNING -> Accent
    StationStatus.FULL -> Color(0xFFFFC66D)
    StationStatus.OFFLINE, StationStatus.ERROR -> Color(0xFFFF998F)
    StationStatus.MAINTENANCE -> Color(0xFFAFB8FF)
    StationStatus.UNKNOWN -> Muted
}

private fun distanceText(meters: Double): String = if (meters < 1000) "${meters.toInt()} m"
    else String.format(Locale.ENGLISH, "%.1f km", meters / 1000)

private fun ageText(fetchedAt: Long?, now: Long): String {
    if (fetchedAt == null) return "No station data"
    val seconds = ((now - fetchedAt) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> "Fetched ${seconds}s ago"
        seconds < 3600 -> "Fetched ${seconds / 60}m ago"
        seconds < 86400 -> "Fetched ${seconds / 3600}h ago"
        else -> "Fetched ${seconds / 86400}d ago"
    }
}

@Composable
fun ReturnRightApp(feed: FeedState, location: LocationState, heading: Heading,
    hasLocation: Boolean, now: Long, onRefresh: () -> Unit, onDemo: () -> Unit,
    onLocation: () -> Unit, onCompassActive: (Boolean) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf("details") }
    var onlyRunning by rememberSaveable { mutableStateOf(false) }
    val selected = feed.stations.firstOrNull { it.id == selectedId }
    val demoOrigin = Coordinate(1.2915, 103.8495)
    val position = if (feed.demo) demoOrigin else location.position?.coordinate
    val freshPosition = feed.demo || (location.position?.isFresh() == true)
    val stale = !feed.demo && (feed.error != null || feed.fetchedAt == null || now - feed.fetchedAt > 120_000)
    LaunchedEffect(selected, mode) { onCompassActive(selected != null && mode == "compass") }
    DisposableEffect(Unit) { onDispose { onCompassActive(false) } }
    BackHandler(selectedId != null) {
        if (mode == "details") selectedId = null else mode = "details"
    }
    MaterialTheme(colors = Colors(primary = Accent, onPrimary = Background,
        background = Background, surface = SurfaceColor, onSurface = Color.White)) {
        Box(Modifier.fillMaxSize().background(Background)) {
            if (selected == null) {
                val stations = remember(feed.stations, onlyRunning, position) {
                    feed.stations.filter { !onlyRunning || it.status == StationStatus.RUNNING }
                        .let { list -> if (position == null) list.sortedBy { it.name }
                            else list.sortedBy { Geo.distance(position, Coordinate(it.latitude, it.longitude)) } }
                }
                val listState = rememberScalingLazyListState()
                ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(rotaryModifier(listState)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    item { Title("ReturnRight", "SINGAPORE") }
                    item { Note(if (feed.demo) "DEMO • synthetic stations & origin" else ageText(feed.fetchedAt, now),
                        if (feed.demo || stale) Color(0xFFFFC66D) else Muted) }
                    if (feed.error != null) item { Note(feed.error, Color(0xFFFF998F)) }
                    if (!feed.demo && stale && feed.stations.isNotEmpty()) item { Note("Cached status • verify before travelling", Color(0xFFFFC66D)) }
                    if (!hasLocation && !feed.demo) item { Action("Enable location", onLocation) }
                    if (!feed.demo && (location.message != null || !freshPosition)) item {
                        Note(location.message ?: "GPS fix is old • waiting for update")
                    }
                    if (feed.demo) item { Note("Distances use a simulated City Hall origin") }
                    item { Action(if (feed.loading) "Updating…" else if (feed.demo) "Load live stations" else "Refresh stations",
                        onRefresh, !feed.loading) }
                    if (!feed.loading && (feed.stations.isEmpty() || feed.error != null) && !feed.demo)
                        item { Action("Explore demo", onDemo) }
                    if (feed.stations.isNotEmpty()) item {
                        Action(if (onlyRunning) "Running only ✓" else "All statuses", { onlyRunning = !onlyRunning })
                    }
                    if (!feed.loading && stations.isEmpty()) item {
                        Note(if (feed.stations.isEmpty()) "Connect watch to Wi-Fi or phone, then refresh." else "No reported running stations. Try all statuses.")
                    }
                    items(stations, key = { it.id }) { station ->
                        Chip(onClick = { selectedId = station.id; mode = "details" },
                            label = { Text(station.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = {
                                val distance = position?.let { distanceText(Geo.distance(it, Coordinate(station.latitude, station.longitude))) }
                                Text("${if (stale) "Last: " else ""}${station.status.label}${distance?.let { " • $it" }.orEmpty()}",
                                    color = if (stale) Muted else statusColor(station.status), maxLines = 1)
                            }, colors = ChipDefaults.chipColors(backgroundColor = SurfaceColor),
                            modifier = Modifier.fillMaxWidth())
                    }
                    item { Note("Independent app • data from ReturnRight\nRunning status may differ from opening hours.") }
                }
                PositionIndicator(scalingLazyListState = listState)
            } else when (mode) {
                "map" -> {
                    StationMap(feed.stations, selected, if (freshPosition) position else null, stale,
                        onSelect = { selectedId = it.id }, modifier = Modifier.fillMaxSize())
                    MapHeader(selected, feed, stale)
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallAction("Back", { mode = "details" })
                        SmallAction("Arrow", { mode = "compass" })
                    }
                }
                "compass" -> {
                    CompassScreen(selected, feed, position, freshPosition, location, heading, stale,
                        onMap = { mode = "map" }, onBack = { mode = "details" })
                }
                else -> {
                    val listState = rememberScalingLazyListState()
                    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize().then(rotaryModifier(listState)),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 30.dp)) {
                        item { Title(selected.name, if (feed.demo) "DEMO STATION" else "RETURN POINT") }
                        item { Note("${if (stale) "Last reported: " else ""}${selected.status.label}",
                            if (stale) Muted else statusColor(selected.status)) }
                        item { Note(if (feed.demo) "Synthetic data" else ageText(feed.fetchedAt, now)) }
                        if (feed.error != null) item { Note(feed.error, Color(0xFFFF998F)) }
                        if (position != null) item { Note("${distanceText(Geo.distance(position, Coordinate(selected.latitude, selected.longitude)))} straight-line${if (!freshPosition) " • old GPS fix" else ""}") }
                        item { Action("View map", { mode = "map" }) }
                        item { Action("Compass arrow", { mode = "compass" }) }
                        if (!hasLocation && !feed.demo) item { Action("Enable location", onLocation) }
                        item { Note(selected.address) }
                        if (selected.postalCode.isNotBlank()) item { Note("Singapore ${selected.postalCode}") }
                        item { Note(selected.hours.ifBlank { "Opening hours not supplied" }) }
                        if (selected.remarks.isNotBlank()) item { Note(selected.remarks) }
                        item { Action(if (feed.loading) "Updating…" else "Refresh status", onRefresh, !feed.loading) }
                        item { Action("All stations", { selectedId = null }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun rotaryModifier(state: ScalingLazyListState): Modifier {
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state) { focus.requestFocus() }
    return Modifier.onRotaryScrollEvent { event ->
        scope.launch { state.scrollBy(event.verticalScrollPixels) }
        true
    }.focusRequester(focus).focusable()
}

@Composable
private fun BoxScope.MapHeader(station: Station, feed: FeedState, stale: Boolean) {
    Column(Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
        .widthIn(max = 156.dp).background(Color(0xDF07130E)).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(station.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${if (feed.demo) "DEMO • " else if (stale) "Cached • " else ""}${station.status.label}",
            fontSize = 10.sp, color = if (stale) Muted else statusColor(station.status))
    }
}

@Composable
private fun CompassScreen(station: Station, feed: FeedState, position: Coordinate?, freshPosition: Boolean,
    location: LocationState, heading: Heading, stale: Boolean, onMap: () -> Unit, onBack: () -> Unit) {
    val target = Coordinate(station.latitude, station.longitude)
    val distance = position?.let { Geo.distance(it, target) }
    val headingValue = if (feed.demo) 0.0 else heading.magneticDegrees?.let { value ->
        position?.let { WatchSensors.trueHeading(value, it) }
    }
    val reliable = feed.demo || heading.reliable
    val uncertainty = if (feed.demo) 10f else location.position?.accuracy ?: Float.MAX_VALUE
    val near = distance != null && freshPosition && distance <= maxOf(20.0, uncertainty.toDouble())
    val showArrow = position != null && freshPosition && reliable && headingValue != null && !near
    val rotation = if (showArrow) Geo.turn(Geo.bearing(position!!, target), headingValue!!) else 0.0
    val message = when {
        feed.demo -> "DEMO • simulated GPS & north"
        position == null -> location.message ?: "Waiting for GPS"
        !freshPosition -> "GPS fix is old • waiting for update"
        headingValue == null -> heading.message ?: "Starting compass"
        !reliable -> heading.message ?: "Calibrate compass"
        near -> "Within GPS accuracy • look nearby"
        else -> "Hold watch flat • ±${uncertainty.toInt()} m GPS"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(station.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${if (stale) "Last: " else ""}${station.status.label}", fontSize = 10.sp,
                color = if (stale) Muted else statusColor(station.status))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = if (showArrow) "Station direction: ${rotation.toInt()} degrees relative to top of watch" else message
            }) {
                val radius = minOf(size.width, size.height) * 0.40f
                drawCircle(Color(0xFF2A4032), radius, style = Stroke(1.5.dp.toPx()))
                if (showArrow) rotate(rotation.toFloat()) {
                    val path = Path().apply {
                        moveTo(center.x, center.y - radius * 0.82f)
                        lineTo(center.x + radius * 0.46f, center.y + radius * 0.50f)
                        lineTo(center.x, center.y + radius * 0.23f)
                        lineTo(center.x - radius * 0.46f, center.y + radius * 0.50f)
                        close()
                    }
                    drawPath(path, Accent)
                } else {
                    drawCircle(if (near) Accent else Muted, radius * 0.24f, style = Stroke(3.dp.toPx()))
                    drawLine(Muted, Offset(center.x, center.y - radius * .75f), Offset(center.x, center.y - radius * .55f), 2.dp.toPx())
                }
            }
        }
        Text(distance?.let { distanceText(it) } ?: "—", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(message, fontSize = 10.sp, color = Muted, textAlign = TextAlign.Center, maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction("Back", onBack)
            SmallAction("Map", onMap)
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(subtitle, color = Accent, fontSize = 10.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Note(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 12.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
}

@Composable
private fun Action(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Chip(onClick = onClick, enabled = enabled, label = { Text(text, maxLines = 2) },
        colors = ChipDefaults.chipColors(backgroundColor = SurfaceColor), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    CompactChip(onClick = onClick, label = { Text(text, fontSize = 11.sp) },
        colors = ChipDefaults.chipColors(backgroundColor = SurfaceColor), modifier = Modifier.width(65.dp))
}

@Composable
fun MapButton(text: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(backgroundColor = Background),
        modifier = Modifier.size(40.dp).semantics { contentDescription = description }) {
        Text(text, fontSize = 20.sp)
    }
}
