package com.meridian.compass.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian.compass.astronomy.MoonInfo
import com.meridian.compass.astronomy.SunInfo
import com.meridian.compass.data.MarkerCategory
import com.meridian.compass.data.Waypoint
import com.meridian.compass.sensors.CompassAccuracy
import com.meridian.compass.settings.AltitudeUnit
import com.meridian.compass.settings.DistanceUnit
import com.meridian.compass.settings.NorthReference
import com.meridian.compass.ui.components.CompassDial
import com.meridian.compass.ui.components.DialMarker
import com.meridian.compass.ui.components.PinBadge
import com.meridian.compass.util.Bearing
import com.meridian.compass.viewmodel.CompassUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CompassScreen(
    uiState: CompassUiState,
    waypoints: List<Waypoint>,
    onToggleNorthReference: () -> Unit,
    onOpenWaypoints: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearTarget: () -> Unit,
    onSelectWaypointId: (Long) -> Unit,
    onLockBearing: () -> Unit,
    onReleaseBearing: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary

    Surface(color = bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 20.dp, vertical = 16.dp))
        ) {
            TopBar(uiState.settings.northReference, onToggleNorthReference, onOpenWaypoints, onOpenSettings)

            Spacer(Modifier.height(4.dp))

            AccuracyBanner(uiState.orientation.accuracy, uiState.orientation.magneticInterferenceSuspected)

            Spacer(Modifier.height(8.dp))

            if (uiState.selectedWaypoint != null) {
                if (uiState.hasArrivedAtWaypoint) {
                    ArrivalCelebrationCard(
                        name = uiState.selectedWaypoint.name,
                        photoPath = uiState.selectedWaypoint.photoPath,
                        onClear = onClearTarget
                    )
                } else {
                    NavigationCard(
                        name = uiState.selectedWaypoint.name,
                        category = MarkerCategory.fromName(uiState.selectedWaypoint.category),
                        relativeBearingDegrees = uiState.relativeBearingDegrees,
                        directionLabel = uiState.relativeDirectionLabel,
                        distanceMeters = uiState.distanceToWaypointMeters,
                        gpsAccuracyMeters = uiState.location.horizontalAccuracyMeters,
                        unit = uiState.settings.distanceUnit,
                        precise = uiState.settings.preciseDistance,
                        onClear = onClearTarget
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            HeadingReadout(
                headingDegrees = uiState.displayHeadingDegrees,
                northReference = uiState.settings.northReference,
                onToggleNorthReference = onToggleNorthReference
            )

            Spacer(Modifier.height(4.dp))

            val markers = buildList {
                uiState.sunInfo?.let {
                    add(DialMarker(it.position.azimuthDegrees, Color(0xFFFFC94A), "S", it.position.elevationDegrees))
                }
                uiState.moonInfo?.let {
                    add(DialMarker(it.position.azimuthDegrees, Color(0xFFB9C4D9), "M", it.position.elevationDegrees))
                }
            }

            val pinBadges = remember(waypoints, uiState.location.latitude, uiState.location.longitude) {
                val lat = uiState.location.latitude
                val lon = uiState.location.longitude
                if (lat == null || lon == null) emptyList()
                else waypoints.map { wp ->
                    PinBadge(
                        id = wp.id,
                        azimuthDegrees = Bearing.bearingDegrees(lat, lon, wp.latitude, wp.longitude),
                        glyph = categoryEmoji(MarkerCategory.fromName(wp.category)),
                        color = accent
                    )
                }
            }

            val effectiveTarget = uiState.bearingToWaypointDegrees ?: uiState.lockedBearingDegrees

            CompassDial(
                headingDegrees = uiState.displayHeadingDegrees,
                targetBearingDegrees = effectiveTarget,
                markers = markers,
                pinBadges = pinBadges,
                onPinTapped = { id -> onSelectWaypointId(id) },
                ringColor = onBg.copy(alpha = 0.6f),
                dialBackground = MaterialTheme.colorScheme.surface,
                textColor = onBg,
                accentColor = accent,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(10.dp))

            if (uiState.selectedWaypoint == null) {
                LockBearingRow(
                    lockedBearing = uiState.lockedBearingDegrees,
                    turnDegrees = uiState.lockedBearingTurnDegrees,
                    label = uiState.lockedBearingLabel,
                    onLock = onLockBearing,
                    onRelease = onReleaseBearing
                )
                Spacer(Modifier.height(10.dp))
            }

            InfoGrid(uiState)

            Spacer(Modifier.height(10.dp))

            SunMoonRow(uiState.sunInfo, uiState.moonInfo)

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TopBar(
    northReference: NorthReference,
    onToggleNorthReference: () -> Unit,
    onOpenWaypoints: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Meridian", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onToggleNorthReference() }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        "N",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (northReference == NorthReference.TRUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onOpenWaypoints) {
                Icon(Icons.Filled.List, contentDescription = "Markers", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
private fun AccuracyBanner(accuracy: CompassAccuracy, interference: Boolean) {
    val show = accuracy == CompassAccuracy.UNRELIABLE || accuracy == CompassAccuracy.LOW || interference
    if (!show) return
    val message = when {
        interference && accuracy != CompassAccuracy.HIGH -> "Magnetic interference detected — move away from metal or electronics"
        accuracy == CompassAccuracy.UNRELIABLE -> "Compass needs calibration — move phone in a figure-8"
        else -> "Low compass accuracy — calibration recommended"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    }
}

@Composable
private fun NavigationCard(
    name: String,
    category: MarkerCategory,
    relativeBearingDegrees: Double?,
    directionLabel: String?,
    distanceMeters: Double?,
    gpsAccuracyMeters: Float?,
    unit: DistanceUnit,
    precise: Boolean,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(MarkerCategory.icon(category), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    "Clear",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onClear() }
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = "Direction to marker",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(40.dp)
                            .rotate((relativeBearingDegrees ?: 0.0).toFloat())
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        distanceMeters?.let { formatDistance(it, unit, precise) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        directionLabel ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    gpsAccuracyMeters?.let { "GPS accuracy ±${it.toInt()} m" } ?: "GPS accuracy unknown",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun ArrivalCelebrationCard(name: String, photoPath: String?, onClear: () -> Unit) {
    val bitmap = remember(photoPath) {
        photoPath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("You've arrived!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(name, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            if (bitmap != null) {
                Spacer(Modifier.height(12.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Marker photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Done navigating",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onClear() }
            )
        }
    }
}

@Composable
private fun HeadingReadout(headingDegrees: Float, northReference: NorthReference, onToggleNorthReference: () -> Unit) {
    val pointLabel = Bearing.compassPointLabel(headingDegrees.toDouble())
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${headingDegrees.toInt()}°",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = pointLabel,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.clickable { onToggleNorthReference() }
        ) {
            Text(
                text = if (northReference == NorthReference.TRUE) "TRUE NORTH" else "MAGNETIC NORTH",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InfoGrid(uiState: CompassUiState) {
    val loc = uiState.location
    val altUnit = uiState.settings.altitudeUnit

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            InfoRow("Latitude", loc.latitude?.let { formatCoordinate(it, isLat = true) } ?: "—")
            InfoRow("Longitude", loc.longitude?.let { formatCoordinate(it, isLat = false) } ?: "—")
            InfoRow(
                "Altitude",
                loc.bestAltitudeMeters?.let { formatAltitude(it, altUnit) + if (loc.altitudeIsRoughEstimate) " (est.)" else "" } ?: "—"
            )
            InfoRow("GPS accuracy", loc.horizontalAccuracyMeters?.let { "±${it.toInt()} m" } ?: "—")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SunMoonRow(sun: SunInfo?, moon: MoonInfo?) {
    val fmt = rememberTimeFormatter()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("☀ Sun", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Text(
                text = "${sun?.sunriseMillis?.let { fmt.format(Date(it)) } ?: "—"} / ${sun?.sunsetMillis?.let { fmt.format(Date(it)) } ?: "—"}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = sun?.let { "elev ${it.position.elevationDegrees.toInt()}°" } ?: "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("☾ Moon", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Text(
                text = "${moon?.moonriseMillis?.let { fmt.format(Date(it)) } ?: "—"} / ${moon?.moonsetMillis?.let { fmt.format(Date(it)) } ?: "—"}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = moon?.let { "${it.phaseName} · ${(it.illuminationFraction * 100).toInt()}%" } ?: "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun rememberTimeFormatter(): SimpleDateFormat =
    remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

private fun formatCoordinate(value: Double, isLat: Boolean): String {
    val hemi = if (isLat) (if (value >= 0) "N" else "S") else (if (value >= 0) "E" else "W")
    return String.format(Locale.US, "%.5f° %s", kotlin.math.abs(value), hemi)
}

private fun formatAltitude(meters: Double, unit: AltitudeUnit): String = when (unit) {
    AltitudeUnit.METERS -> "${meters.toInt()} m"
    AltitudeUnit.FEET -> "${Bearing.metersToFeet(meters).toInt()} ft"
}

private fun formatDistance(meters: Double, unit: DistanceUnit, precise: Boolean): String {
    if (!precise) {
        val roundedMeters = when {
            meters < 100 -> (Math.round(meters / 5.0) * 5).toDouble()
            else -> (Math.round(meters / 10.0) * 10).toDouble()
        }
        return "~" + rawDistanceString(roundedMeters, unit)
    }
    return rawDistanceString(meters, unit)
}

@Composable
private fun LockBearingRow(
    lockedBearing: Double?,
    turnDegrees: Double?,
    label: String?,
    onLock: () -> Unit,
    onRelease: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (lockedBearing != null && turnDegrees != null && label != null) {
            Text(
                "△ ${kotlin.math.abs(turnDegrees).toInt()}°",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
        }
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.clickable { if (lockedBearing == null) onLock() else onRelease() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (lockedBearing == null) "🔓 Lock Bearing" else "🔒 ${lockedBearing.toInt()}° · Release",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun categoryEmoji(category: MarkerCategory): String = when (category) {
    MarkerCategory.OUTDOOR -> "⛰"
    MarkerCategory.VEHICLE -> "🚗"
    MarkerCategory.FOOD -> "🍽"
    MarkerCategory.WATER -> "💧"
    MarkerCategory.EVENT -> "🎉"
    MarkerCategory.PERSON -> "🧍"
    MarkerCategory.BOAT -> "⛵"
    MarkerCategory.OTHER -> "📍"
}

private fun rawDistanceString(meters: Double, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METRIC -> if (meters >= 1000) String.format(Locale.US, "%.2f km", Bearing.metersToKilometers(meters)) else "${meters.toInt()} m"
    DistanceUnit.IMPERIAL -> if (meters >= 1609) String.format(Locale.US, "%.2f mi", Bearing.metersToMiles(meters)) else "${Bearing.metersToFeet(meters).toInt()} ft"
}
