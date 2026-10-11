package com.meridian.compass.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

data class LatLon(val lat: Double, val lon: Double)

data class MapMarker(
    val lat: Double,
    val lon: Double,
    val label: String,
    val color: Color = Color(0xFFE53935)
)

data class MapLine(
    val points: List<LatLon>,
    val color: Color = Color(0xFFFF7A00)
)

enum class MapStyle(val attribution: String, val dark: Boolean) {
    STREET("© OpenStreetMap contributors", false),
    TERRAIN("© OpenStreetMap contributors, SRTM | © OpenTopoMap", false),
    DARK("© OpenStreetMap contributors © CARTO", true);

    fun url(z: Int, x: Int, y: Int): String = when (this) {
        STREET -> "https://tile.openstreetmap.org/$z/$x/$y.png"
        TERRAIN -> {
            val s = listOf("a", "b", "c")[(x + y) % 3]
            "https://$s.tile.opentopomap.org/$z/$x/$y.png"
        }
        DARK -> {
            val s = listOf("a", "b", "c", "d")[(x + y) % 4]
            "https://$s.basemaps.cartocdn.com/dark_all/$z/$x/$y.png"
        }
    }

    fun next(): MapStyle = when (this) {
        STREET -> TERRAIN
        TERRAIN -> DARK
        DARK -> STREET
    }
}

// ---- Web Mercator helpers (world coordinates 0..1) ----

fun lonToX(lon: Double): Double = (lon + 180.0) / 360.0

fun latToY(lat: Double): Double {
    val s = sin(Math.toRadians(lat)).coerceIn(-0.9999, 0.9999)
    return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
}

fun xToLon(x: Double): Double = x * 360.0 - 180.0

fun yToLat(y: Double): Double {
    val n = PI - 2.0 * PI * y
    return Math.toDegrees(atan(sinh(n)))
}

class MapState(lat: Double, lon: Double, zoom: Float) {
    var cx by mutableStateOf(lonToX(lon))
    var cy by mutableStateOf(latToY(lat))
    var zoom by mutableStateOf(zoom)
    var following by mutableStateOf(false)

    val centerLat: Double get() = yToLat(cy)
    val centerLon: Double get() = xToLon(cx)

    fun moveTo(lat: Double, lon: Double, z: Float? = null) {
        cx = lonToX(lon)
        cy = latToY(lat)
        if (z != null) zoom = z
    }

    fun fit(points: List<LatLon>, w: Int, h: Int) {
        if (points.isEmpty() || w == 0 || h == 0) return
        val xs = points.map { lonToX(it.lon) }
        val ys = points.map { latToY(it.lat) }
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        cx = (minX + maxX) / 2
        cy = (minY + maxY) / 2
        val span = max(maxX - minX, maxY - minY).coerceAtLeast(1e-7)
        val z = ln(min(w, h) * 0.75 / (256.0 * span)) / ln(2.0)
        zoom = z.toFloat().coerceIn(3f, 18f)
    }
}

@Composable
fun rememberMapState(lat: Double, lon: Double, zoom: Float): MapState =
    remember { MapState(lat, lon, zoom) }

// ---- Tile loading with memory + disk cache ----

internal object TileStore {
    private val cache = LruCache<String, ImageBitmap>(300)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val sem = Semaphore(6)
    @Volatile private var dir: File? = null

    fun init(cacheDir: File) {
        dir = File(cacheDir, "map_tiles")
    }

    private fun key(s: MapStyle, z: Int, x: Int, y: Int) = "${s.name}/$z/$x/$y"

    fun get(s: MapStyle, z: Int, x: Int, y: Int): ImageBitmap? = cache.get(key(s, z, x, y))

    fun shouldTry(s: MapStyle, z: Int, x: Int, y: Int): Boolean {
        val k = key(s, z, x, y)
        if (inFlight.contains(k)) return false
        val f = failedAt[k]
        return f == null || System.currentTimeMillis() - f > 15_000
    }

    suspend fun fetch(s: MapStyle, z: Int, x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val k = key(s, z, x, y)
        if (cache.get(k) != null) return@withContext true
        if (!inFlight.add(k)) return@withContext false
        try {
            sem.withPermit {
                var bmp: Bitmap? = null
                val file = dir?.let { File(it, "$k.png") }
                if (file != null && file.exists()) {
                    bmp = BitmapFactory.decodeFile(file.path)
                }
                if (bmp == null) {
                    val conn = URL(s.url(z, x, y)).openConnection() as HttpURLConnection
                    try {
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.setRequestProperty("User-Agent", "MeridianCompass/1.0 (Android; offline compass app)")
                        if (conn.responseCode == 200) {
                            val bytes = conn.inputStream.use { it.readBytes() }
                            bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null && file != null) {
                                runCatching {
                                    file.parentFile?.mkdirs()
                                    file.writeBytes(bytes)
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
                val done = bmp
                if (done != null) {
                    cache.put(k, done.asImageBitmap())
                    failedAt.remove(k)
                    true
                } else {
                    failedAt[k] = System.currentTimeMillis()
                    false
                }
            }
        } catch (e: Exception) {
            failedAt[k] = System.currentTimeMillis()
            false
        } finally {
            inFlight.remove(k)
        }
    }
}

private fun visibleTiles(cx: Double, cy: Double, zoom: Float, tz: Int, size: IntSize): List<Triple<Int, Int, Int>> {
    if (size.width == 0 || size.height == 0) return emptyList()
    val n = 1 shl tz
    val tilePx = 256.0 * 2.0.pow((zoom - tz).toDouble())
    val txc = cx * n
    val tyc = cy * n
    val x0 = floor(txc - size.width / 2.0 / tilePx).toInt()
    val x1 = floor(txc + size.width / 2.0 / tilePx).toInt()
    val y0 = floor(tyc - size.height / 2.0 / tilePx).toInt()
    val y1 = floor(tyc + size.height / 2.0 / tilePx).toInt()
    val out = ArrayList<Triple<Int, Int, Int>>()
    for (tx in x0..x1) {
        for (ty in y0..y1) {
            if (ty < 0 || ty >= n) continue
            out.add(Triple(tz, ((tx % n) + n) % n, ty))
        }
    }
    return out.distinct()
}

/**
 * A native slippy map: pinch to zoom, drag to pan, tiles cached on the phone.
 * Draws trail lines, markers and the user's position on top.
 */
@Composable
fun SlippyMap(
    state: MapState,
    modifier: Modifier = Modifier,
    initialStyle: MapStyle = MapStyle.STREET,
    lines: List<MapLine> = emptyList(),
    markers: List<MapMarker> = emptyList(),
    userLocation: LatLon? = null,
    fitTo: List<LatLon>? = null,
    fitKey: Any? = null
) {
    val context = LocalContext.current
    remember { TileStore.init(context.cacheDir); 0 }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density

    var tick by remember { mutableStateOf(0) }
    var failures by remember { mutableStateOf(0) }
    var successes by remember { mutableStateOf(0) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var mapStyle by remember { mutableStateOf(initialStyle) }

    val labelFill = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.BLACK
        }
    }
    val labelStroke = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            this.style = Paint.Style.STROKE
        }
    }

    val tz = Math.round(state.zoom).coerceIn(2, 19)
    val tiles = visibleTiles(state.cx, state.cy, state.zoom, tz, viewSize)

    LaunchedEffect(mapStyle, tiles) {
        tiles.forEach { (z, x, y) ->
            if (TileStore.get(mapStyle, z, x, y) == null && TileStore.shouldTry(mapStyle, z, x, y)) {
                val s = mapStyle
                scope.launch {
                    if (TileStore.fetch(s, z, x, y)) {
                        successes++
                        tick++
                    } else {
                        failures++
                    }
                }
            }
        }
    }

    LaunchedEffect(fitKey, viewSize, fitTo?.size) {
        if (fitTo != null && viewSize != IntSize.Zero) {
            state.fit(fitTo, viewSize.width, viewSize.height)
        }
    }

    LaunchedEffect(userLocation) {
        if (state.following && userLocation != null) {
            state.moveTo(userLocation.lat, userLocation.lon)
        }
    }

    Box(modifier = modifier.clipToBounds()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        val s = 256.0 * 2.0.pow(state.zoom.toDouble())
                        if (pan != Offset.Zero) state.following = false
                        val nx = state.cx - pan.x / s
                        state.cx = nx - floor(nx)
                        state.cy = (state.cy - pan.y / s).coerceIn(0.0, 1.0)
                        if (zoomChange != 1f) {
                            state.zoom = (state.zoom + ln(zoomChange) / ln(2f)).coerceIn(2f, 19f)
                        }
                    }
                }
        ) {
            @Suppress("UNUSED_VARIABLE")
            val redraw = tick
            val w = size.width
            val h = size.height
            drawRect(if (mapStyle.dark) Color(0xFF1C1E22) else Color(0xFFDFE3E8))

            // Tiles
            val z = state.zoom
            val level = Math.round(z).coerceIn(2, 19)
            val n = 1 shl level
            val tilePx = (256.0 * 2.0.pow((z - level).toDouble())).toFloat()
            val txc = state.cx * n
            val tyc = state.cy * n
            val x0 = floor(txc - w / 2.0 / tilePx).toInt()
            val x1 = floor(txc + w / 2.0 / tilePx).toInt()
            val y0 = floor(tyc - h / 2.0 / tilePx).toInt()
            val y1 = floor(tyc + h / 2.0 / tilePx).toInt()
            for (tx in x0..x1) {
                for (ty in y0..y1) {
                    if (ty < 0 || ty >= n) continue
                    val wx = ((tx % n) + n) % n
                    val img = TileStore.get(mapStyle, level, wx, ty) ?: continue
                    val dx = ((tx - txc) * tilePx + w / 2.0).roundToInt()
                    val dy = ((ty - tyc) * tilePx + h / 2.0).roundToInt()
                    val dsz = ceil(tilePx).toInt() + 1
                    drawImage(
                        image = img,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(img.width, img.height),
                        dstOffset = IntOffset(dx, dy),
                        dstSize = IntSize(dsz, dsz)
                    )
                }
            }

            // Overlays
            val worldS = 256.0 * 2.0.pow(z.toDouble())
            fun pt(lat: Double, lon: Double): Offset = Offset(
                ((lonToX(lon) - state.cx) * worldS + w / 2.0).toFloat(),
                ((latToY(lat) - state.cy) * worldS + h / 2.0).toFloat()
            )

            for (line in lines) {
                if (line.points.size < 2) continue
                val path = Path()
                line.points.forEachIndexed { i, p ->
                    val o = pt(p.lat, p.lon)
                    if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                }
                drawPath(path, Color.White.copy(alpha = 0.9f), style = Stroke(width = 9f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, line.color, style = Stroke(width = 5.5f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            labelFill.textSize = 12f * density
            labelStroke.textSize = 12f * density
            labelStroke.strokeWidth = 3f * density
            for (m in markers) {
                val o = pt(m.lat, m.lon)
                if (o.x < -50 || o.y < -50 || o.x > w + 50 || o.y > h + 50) continue
                drawCircle(Color.White, radius = 10f * density, center = o)
                drawCircle(m.color, radius = 7.5f * density, center = o)
                if (m.label.isNotBlank()) {
                    drawContext.canvas.nativeCanvas.drawText(m.label, o.x, o.y - 14f * density, labelStroke)
                    drawContext.canvas.nativeCanvas.drawText(m.label, o.x, o.y - 14f * density, labelFill)
                }
            }

            if (userLocation != null) {
                val o = pt(userLocation.lat, userLocation.lon)
                drawCircle(Color.White, radius = 11f * density, center = o)
                drawCircle(Color(0xFF2979FF), radius = 8f * density, center = o)
            }
        }

        // Zoom / style / my-location controls
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MapButton(Icons.Filled.Add, "Zoom in") { state.zoom = (state.zoom + 1f).coerceAtMost(19f) }
            Spacer(Modifier.height(6.dp))
            MapButton(Icons.Filled.Remove, "Zoom out") { state.zoom = (state.zoom - 1f).coerceAtLeast(2f) }
            Spacer(Modifier.height(6.dp))
            MapButton(Icons.Filled.Layers, "Change map style") { mapStyle = mapStyle.next() }
            if (userLocation != null) {
                Spacer(Modifier.height(6.dp))
                MapButton(Icons.Filled.MyLocation, "My location") {
                    state.moveTo(userLocation.lat, userLocation.lon, max(state.zoom, 16f))
                    state.following = true
                }
            }
        }

        // Attribution (required by the map data providers)
        Surface(
            color = Color.White.copy(alpha = 0.75f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        ) {
            Text(
                mapStyle.attribution,
                fontSize = 9.sp,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }

        if (failures >= 3 && successes == 0) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp)
            ) {
                Text(
                    "Can't load map pictures. Check your internet connection. Your path is still recorded.",
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = CircleShape,
        shadowElevation = 3.dp,
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}
