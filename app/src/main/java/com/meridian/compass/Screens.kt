@file:OptIn(ExperimentalMaterial3Api::class)

package com.meridian.compass

import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Accent = Color(0xFFFF8A3D)
private val SunColor = Color(0xFFFFC107)
private val MoonColor = Color(0xFFCFD8DC)
private val Green = Color(0xFF3DDC84)

// ---------------------------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------------------------

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: Int, enabled: Boolean = true, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = i == selected, enabled = enabled, onClick = { onSelect(i) }, label = { Text(o) })
        }
    }
}

private fun accuracyLabel(a: Int): String = when (a) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
    else -> "Unreliable"
}

private fun timeText(t: Long?): String = if (t == null) "\u2014" else DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))

private fun bearingText(b: Float): String = "${b.roundToInt() % 360}\u00B0 ${Geo.cardinal(b)}"

/** Returns altitude text and whether it is only an estimate. */
private fun altitudeInfo(l: Location?, metric: Boolean): Triple<String, Boolean, String> {
    if (l == null || !l.hasAltitude()) return Triple("\u2014", true, "no altitude yet")
    val hasMsl = Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude()
    val a = if (hasMsl) l.mslAltitudeMeters else l.altitude
    val vAcc = if (l.hasVerticalAccuracy()) l.verticalAccuracyMeters else null
    val estimate = !hasMsl || vAcc == null || vAcc > 15f
    val note = buildString {
        append(if (hasMsl) "GPS, above sea level" else "GPS (may be relative to ellipsoid)")
        if (vAcc != null) append(", \u00B1${Geo.fmtAlt(vAcc.toDouble(), metric)}")
    }
    return Triple((if (estimate) "\u2248 " else "") + Geo.fmtAlt(a, metric), estimate, note)
}

// ---------------------------------------------------------------------------------------------
// Compass screen
// ---------------------------------------------------------------------------------------------

@Composable
fun CompassScreen(m: AppModel, onWaypoints: () -> Unit, onSettings: () -> Unit, requestPermission: () -> Unit) {
    val s = m.settings
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val view = LocalView.current
    DisposableEffect(s.keepOn) {
        view.keepScreenOn = s.keepOn
        onDispose { view.keepScreenOn = false }
    }
    var showCal by remember { mutableStateOf(false) }

    val loc = m.location.location
    val heading = m.heading
    val trueActive = m.trueActive

    val sun = remember(now, loc) { loc?.let { Astro.sun(now, it.latitude, it.longitude) } }
    val moon = remember(now, loc) { loc?.let { Astro.moon(now, it.latitude, it.longitude) } }
    val illum = remember(now) { Astro.moonIllum(now) }
    val latKey = loc?.let { (it.latitude * 20).roundToInt() }
    val lonKey = loc?.let { (it.longitude * 20).roundToInt() }
    val timeKey = now / 1_800_000L
    val sunTimes = remember(timeKey, latKey, lonKey) { loc?.let { Astro.sunTimes(now, it.latitude, it.longitude) } }
    val moonTimes = remember(timeKey, latKey, lonKey) { loc?.let { Astro.moonTimes(now, it.latitude, it.longitude) } }

    val tgt = m.selected
    val trueBearing = if (tgt != null && loc != null) Geo.bearing(loc.latitude, loc.longitude, tgt.lat, tgt.lon) else null
    val tgtDist = if (tgt != null && loc != null) Geo.distance(loc.latitude, loc.longitude, tgt.lat, tgt.lon) else null
    val tgtDisplay = trueBearing?.let { m.toDisplay(it) }
    val delta = tgtDisplay?.let { Geo.angleDiff(it, heading) }
    val aligned = delta != null && abs(delta) < 3f

    val acc = m.compass.accuracy
    val field = m.compass.fieldStrength
    val interference = field > 0f && (field < 20f || field > 70f)
    val poor = acc <= SensorManager.SENSOR_STATUS_ACCURACY_LOW || interference

    val modeLabel = when {
        trueActive -> "TRUE NORTH  (declination ${String.format(java.util.Locale.US, "%+.1f", m.declination)}\u00B0)"
        s.trueNorth -> "MAGNETIC NORTH  (waiting for location to apply True North)"
        else -> "MAGNETIC NORTH"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MERIDIAN",
                style = MaterialTheme.typography.titleMedium,
                color = cs.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onWaypoints) { Icon(Icons.Default.Place, contentDescription = "Waypoints") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val big = if (m.compass.hasReading) "${heading.roundToInt() % 360}\u00B0  ${Geo.cardinal(heading)}" else "--"
            Text(big, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(
                modeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = if (s.trueNorth && !trueActive) Accent else cs.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilterChip(selected = !s.trueNorth, onClick = { s.setTrueNorth(false) }, label = { Text("Magnetic") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = s.trueNorth, onClick = { s.setTrueNorth(true) }, label = { Text("True") })
        }

        if (!m.compass.hasCompass) {
            Panel {
                Text("No compass sensor", fontWeight = FontWeight.Bold, color = cs.error)
                Text("This device does not report a rotation-vector/magnetometer sensor, so a compass reading is not possible.")
            }
        } else if (poor) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (interference) "Magnetic interference detected" else "Compass accuracy is ${accuracyLabel(acc).lowercase()}",
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Do not rely on the heading until this clears. Field ${field.roundToInt()} \u00B5T (normal is about 25\u201365 \u00B5T).",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { showCal = true }) { Text("How to calibrate") }
            }
        } else {
            Text(
                "Sensor accuracy: ${accuracyLabel(acc)}  \u00B7  Field ${field.roundToInt()} \u00B5T",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        CompassDial(
            heading = heading,
            sunAz = sun?.let { m.toDisplay(it.azimuth) },
            sunUp = (sun?.altitude ?: -1.0) >= 0.0,
            moonAz = moon?.let { m.toDisplay(it.azimuth) },
            moonUp = (moon?.altitude ?: -1.0) >= 0.0,
            moonFraction = illum.fraction.toFloat(),
            moonWaxing = illum.phase < 0.5,
            target = tgtDisplay,
            aligned = aligned,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(4.dp)
        )

        if (tgt != null) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tgt.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.weight(1f))
                    TextButton(onClick = { m.waypoints.select(null) }) { Text("Clear") }
                }
                if (tgtDisplay != null && tgtDist != null) {
                    Row(Modifier.fillMaxWidth()) {
                        Cell("DISTANCE", Geo.fmtDist(tgtDist, s.metricDistance), Modifier.weight(1f))
                        Cell(if (trueActive) "BEARING (TRUE)" else "BEARING (MAG)", bearingText(tgtDisplay), Modifier.weight(1f))
                    }
                    val d = delta ?: 0f
                    Text(
                        when {
                            aligned -> "On target"
                            d > 0 -> "Turn right ${abs(d).roundToInt()}\u00B0"
                            else -> "Turn left ${abs(d).roundToInt()}\u00B0"
                        },
                        color = if (aligned) Green else cs.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                } else {
                    Text("Waiting for a location fix to compute distance and bearing.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Panel {
            if (!m.location.permissionGranted) {
                Text("Location permission needed", fontWeight = FontWeight.Bold)
                Text("Used on-device for coordinates, True North, waypoints and sun/moon positions. Nothing is uploaded.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = requestPermission, modifier = Modifier.padding(top = 8.dp)) { Text("Allow location") }
            } else if (loc == null) {
                Text(
                    if (!m.location.gpsEnabled) "Turn on device location (GPS) to get a position." else "Waiting for a GPS fix\u2026 (go outdoors for best results)",
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                val (altText, _, altNote) = altitudeInfo(loc, s.metricAltitude)
                Row(Modifier.fillMaxWidth()) {
                    Cell("LATITUDE", Geo.fmtCoord(loc.latitude, "N", "S"), Modifier.weight(1f))
                    Cell("LONGITUDE", Geo.fmtCoord(loc.longitude, "E", "W"), Modifier.weight(1f))
                }
                Spacer(Modifier.padding(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Cell("ALTITUDE", altText, Modifier.weight(1f))
                    Cell(
                        "ACCURACY",
                        if (loc.hasAccuracy()) "\u00B1${Geo.fmtAlt(loc.accuracy.toDouble(), s.metricAltitude)}" else "\u2014",
                        Modifier.weight(1f)
                    )
                }
                Text(altNote, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (m.compass.pressure > 0f) {
                    val baro = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, m.compass.pressure)
                    Text(
                        "Barometer ${m.compass.pressure.roundToInt()} hPa (uncalibrated pressure altitude \u2248 ${Geo.fmtAlt(baro.toDouble(), s.metricAltitude)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
                val ageSec = (now - loc.time) / 1000
                if (ageSec > 60) {
                    Text("Last fix ${ageSec / 60} min ago", style = MaterialTheme.typography.labelSmall, color = Accent)
                }
            }
        }

        Panel {
            if (loc == null || sun == null || moon == null) {
                Text("Sun & Moon appear once a location is known.", style = MaterialTheme.typography.bodySmall)
            } else {
                val sunDir = m.toDisplay(sun.azimuth)
                val moonDir = m.toDisplay(moon.azimuth)
                Text("\u2600  Sun", fontWeight = FontWeight.Bold, color = SunColor)
                Text(
                    "${bearingText(sunDir)}  \u00B7  altitude ${sun.altitude.roundToInt()}\u00B0 ${if (sun.altitude >= 0) "(above horizon)" else "(below horizon)"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Sunrise ${timeText(sunTimes?.first)}  \u00B7  Sunset ${timeText(sunTimes?.second)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Spacer(Modifier.padding(4.dp))
                Text("\u263E  Moon", fontWeight = FontWeight.Bold, color = MoonColor)
                Text(
                    "${bearingText(moonDir)}  \u00B7  altitude ${moon.altitude.roundToInt()}\u00B0 ${if (moon.altitude >= 0) "(above horizon)" else "(below horizon)"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("${Astro.phaseName(illum.phase)}  \u00B7  ${(illum.fraction * 100).roundToInt()}% lit", style = MaterialTheme.typography.bodyMedium)
                Text("Moonrise ${timeText(moonTimes?.first)}  \u00B7  Moonset ${timeText(moonTimes?.second)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        Spacer(Modifier.padding(8.dp))
    }

    if (showCal) CalibrationDialog(accuracyLabel(acc), onDismiss = { showCal = false })
}

@Composable
fun CalibrationDialog(accuracy: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Calibrate compass") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Current sensor accuracy: $accuracy", fontWeight = FontWeight.SemiBold)
                Text("1. Move away from metal objects, magnets, speakers, car dashboards and magnetic phone cases.")
                Text("2. Hold the phone and draw a large figure-8 in the air, rotating your wrist so the phone tilts in every direction. Repeat 5\u201310 times.")
                Text("3. Watch the accuracy readout. It should reach Medium or High. If it stays Low/Unreliable, move to a different spot and try again.")
                Text("Android decides when calibration is good; this app never hides a poor reading.", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Compass dial
// ---------------------------------------------------------------------------------------------

private fun DrawScope.placeBody(az: Float, radius: Float, c: Offset, upright: Float, block: DrawScope.(Offset) -> Unit) {
    val a = Math.toRadians(az.toDouble())
    val p = Offset(c.x + radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
    rotate(upright, p) { block(this, p) }
}

@Composable
private fun CompassDial(
    heading: Float,
    sunAz: Float?,
    sunUp: Boolean,
    moonAz: Float?,
    moonUp: Boolean,
    moonFraction: Float,
    moonWaxing: Boolean,
    target: Float?,
    aligned: Boolean,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val dialBg = cs.surface
    val ink = cs.onSurface
    val pointer = if (aligned) Green else cs.primary
    val north = Color(0xFFFF5252)
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension / 2f * 0.96f
        drawCircle(dialBg, radius = outer, center = c)
        drawCircle(ink.copy(alpha = 0.25f), radius = outer, center = c, style = Stroke(2.dp.toPx()))

        rotate(-heading, c) {
            // Ticks every 5 degrees
            for (deg in 0 until 360 step 5) {
                val major = deg % 30 == 0
                val len = (if (major) 0.10f else if (deg % 10 == 0) 0.065f else 0.04f) * outer
                val col = if (deg == 0) north else ink.copy(alpha = if (major) 0.9f else 0.45f)
                rotate(deg.toFloat(), c) {
                    drawLine(
                        col,
                        Offset(c.x, c.y - outer),
                        Offset(c.x, c.y - outer + len),
                        strokeWidth = (if (major) 2.dp else 1.dp).toPx()
                    )
                }
            }
            // Labels
            drawIntoCanvas { cv ->
                val nc = cv.nativeCanvas
                for (deg in 0 until 360 step 15) {
                    val isCard = deg % 90 == 0
                    val isInter = deg % 45 == 0 && !isCard
                    val isNum = deg % 30 == 0 && !isCard
                    if (!isCard && !isInter && !isNum) continue
                    val text = when {
                        isCard -> arrayOf("N", "E", "S", "W")[deg / 90]
                        isInter -> arrayOf("NE", "SE", "SW", "NW")[(deg - 45) / 90]
                        else -> deg.toString()
                    }
                    paint.textSize = (if (isCard) 26.sp else if (isInter) 15.sp else 11.sp).toPx()
                    paint.color = (if (deg == 0) north else ink.copy(alpha = if (isNum) 0.6f else 0.95f)).toArgb()
                    val rad = if (isNum) 0.80f else 0.74f
                    nc.save()
                    nc.rotate(deg.toFloat(), c.x, c.y)
                    nc.drawText(text, c.x, c.y - outer * rad + paint.textSize / 3f, paint)
                    nc.restore()
                }
            }
            // Target waypoint direction
            target?.let { t ->
                rotate(t, c) {
                    drawLine(
                        Accent.copy(alpha = 0.85f),
                        Offset(c.x, c.y - outer * 0.12f),
                        Offset(c.x, c.y - outer * 0.84f),
                        strokeWidth = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                    val tri = Path().apply {
                        moveTo(c.x, c.y - outer * 0.86f)
                        lineTo(c.x - 9.dp.toPx(), c.y - outer * 0.99f)
                        lineTo(c.x + 9.dp.toPx(), c.y - outer * 0.99f)
                        close()
                    }
                    drawPath(tri, Accent)
                }
            }
            // Sun
            sunAz?.let { az ->
                placeBody(az, outer * 0.50f, c, heading) { p ->
                    val col = SunColor.copy(alpha = if (sunUp) 1f else 0.35f)
                    val sr = 9.dp.toPx()
                    drawCircle(col, sr, p)
                    for (k in 0 until 8) {
                        rotate(k * 45f, p) {
                            drawLine(col, Offset(p.x, p.y - sr * 1.4f), Offset(p.x, p.y - sr * 1.9f), 2.dp.toPx())
                        }
                    }
                }
            }
            // Moon
            moonAz?.let { az ->
                placeBody(az, outer * 0.36f, c, heading) { p ->
                    val mr = 9.dp.toPx()
                    drawCircle(MoonColor.copy(alpha = if (moonUp) 1f else 0.35f), mr, p)
                    val clip = Path().apply { addOval(Rect(center = p, radius = mr)) }
                    clipPath(clip) {
                        drawCircle(dialBg, mr, Offset(p.x + (if (moonWaxing) -1f else 1f) * 2f * mr * moonFraction, p.y))
                    }
                }
            }
        }

        // Fixed heading pointer (top) and centre hub
        val ptr = Path().apply {
            moveTo(c.x, c.y - outer + 16.dp.toPx())
            lineTo(c.x - 9.dp.toPx(), c.y - outer - 6.dp.toPx())
            lineTo(c.x + 9.dp.toPx(), c.y - outer - 6.dp.toPx())
            close()
        }
        drawPath(ptr, pointer)
        drawCircle(pointer, radius = 4.dp.toPx(), center = c)
    }
}

// ---------------------------------------------------------------------------------------------
// Waypoints screen
// ---------------------------------------------------------------------------------------------

@Composable
fun WaypointsScreen(m: AppModel, onBack: () -> Unit, onPicked: () -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Waypoint?>(null) }
    var deleting by remember { mutableStateOf<Waypoint?>(null) }
    val loc = m.location.location
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Waypoints") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Save here") }
            )
        }
    ) { pad ->
        if (m.waypoints.items.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No waypoints yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Tap \"Save here\" to store your current position, then select it to get a bearing and distance on the compass.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(m.waypoints.items, key = { it.id }) { w ->
                    val active = w.id == m.waypoints.selectedId
                    Card(
                        onClick = { m.waypoints.select(w.id); onPicked() },
                        colors = CardDefaults.cardColors(containerColor = if (active) cs.secondaryContainer else cs.surface)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.name + if (active) "  \u25CF active" else "", fontWeight = FontWeight.Bold)
                                if (loc != null) {
                                    val d = Geo.distance(loc.latitude, loc.longitude, w.lat, w.lon)
                                    val b = m.toDisplay(Geo.bearing(loc.latitude, loc.longitude, w.lat, w.lon))
                                    Text("${Geo.fmtDist(d, m.settings.metricDistance)}  \u00B7  ${bearingText(b)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    "${Geo.fmtCoord(w.lat, "N", "S")}, ${Geo.fmtCoord(w.lon, "E", "W")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editing = w }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                            IconButton(onClick = { deleting = w }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Save current location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loc == null) {
                        Text("No location fix yet. Wait for GPS, then try again.", color = cs.error)
                    } else {
                        Text("${Geo.fmtCoord(loc.latitude, "N", "S")}, ${Geo.fmtCoord(loc.longitude, "E", "W")}", style = MaterialTheme.typography.bodySmall)
                        val age = (System.currentTimeMillis() - loc.time) / 60000
                        if (age >= 2) Text("Warning: this fix is $age min old.", color = Accent, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = loc != null,
                    onClick = {
                        if (loc != null) {
                            val n = name.trim().ifBlank { "Waypoint ${m.waypoints.items.size + 1}" }
                            m.waypoints.add(n, loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else null)
                        }
                        showAdd = false
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    editing?.let { w ->
        var name by remember(w.id) { mutableStateOf(w.name) }
        var lat by remember(w.id) { mutableStateOf(w.lat.toString()) }
        var lon by remember(w.id) { mutableStateOf(w.lon.toString()) }
        val latV = lat.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
        val lonV = lon.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit waypoint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude (-90..90)") }, singleLine = true, isError = latV == null)
                    OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text("Longitude (-180..180)") }, singleLine = true, isError = lonV == null)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = latV != null && lonV != null && name.isNotBlank(),
                    onClick = {
                        m.waypoints.update(w.id, name.trim(), latV!!, lonV!!)
                        editing = null
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }

    deleting?.let { w ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete waypoint?") },
            text = { Text("\"${w.name}\" will be removed from this device.") },
            confirmButton = { TextButton(onClick = { m.waypoints.delete(w.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Settings screen
// ---------------------------------------------------------------------------------------------

@Composable
private fun SettingBlock(title: String, content: @Composable ColumnScope.() -> Unit) {
    Panel {
        Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SettingsScreen(m: AppModel, onBack: () -> Unit) {
    val s = m.settings
    var showCal by remember { mutableStateOf(false) }
    val steps = listOf(2, 5, 10, 15, 30)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingBlock("North reference") {
                ChipRow(listOf("Magnetic North", "True North"), if (s.trueNorth) 1 else 0) { s.setTrueNorth(it == 1) }
                Text("True North adds the magnetic declination for your location (computed offline).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            SettingBlock("Distance units") {
                ChipRow(listOf("Metric (m, km)", "Imperial (ft, mi)"), if (s.metricDistance) 0 else 1) { s.setMetricDistance(it == 0) }
            }
            SettingBlock("Altitude units") {
                ChipRow(listOf("Meters", "Feet"), if (s.metricAltitude) 0 else 1) { s.setMetricAltitude(it == 0) }
            }
            SettingBlock("Haptic compass steps") {
                SwitchRow("Haptic feedback", s.haptics) { s.setHaptics(it) }
                Text("Step interval", modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                ChipRow(steps.map { "$it\u00B0" }, steps.indexOf(s.stepDeg).coerceAtLeast(0), enabled = s.haptics) { m.setStep(steps[it]) }
                SwitchRow("Stronger click at N / E / S / W", s.majorStrong) { s.setMajorStrong(it) }
            }
            SettingBlock("Theme") {
                ChipRow(listOf("System", "Light", "Dark"), s.theme) { s.setTheme(it) }
            }
            SettingBlock("Display") {
                SwitchRow("Keep screen on while compass is open", s.keepOn) { s.setKeepOn(it) }
            }
            SettingBlock("Calibration & help") {
                Text("If the compass reports Low or Unreliable accuracy, calibrate it with a figure-8 motion away from metal.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showCal = true }) { Text("Show calibration steps") }
            }
            SettingBlock("About") {
                Text("Meridian Compass 1.0", fontWeight = FontWeight.SemiBold)
                Text(
                    "An original, offline compass. No account, no analytics, no internet permission. Location, waypoints and settings never leave this device.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    if (showCal) CalibrationDialog(accuracyLabel(m.compass.accuracy), onDismiss = { showCal = false })
}
