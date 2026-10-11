package com.meridian.compass.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian.compass.data.Trail
import com.meridian.compass.data.TrailPoint
import com.meridian.compass.settings.DistanceUnit
import com.meridian.compass.ui.map.LatLon
import com.meridian.compass.ui.map.MapLine
import com.meridian.compass.ui.map.MapMarker
import com.meridian.compass.ui.map.SlippyMap
import com.meridian.compass.ui.map.rememberMapState
import com.meridian.compass.util.Bearing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatTrailDistance(meters: Double, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METRIC -> if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000) else "${meters.toInt()} m"
    DistanceUnit.IMPERIAL -> if (meters >= 1609) String.format(Locale.US, "%.2f mi", meters / 1609.344) else "${Bearing.metersToFeet(meters).toInt()} ft"
}

private fun formatTrailDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(millis))

private fun formatDuration(startMillis: Long, endMillis: Long?): String {
    val end = endMillis ?: return "recording"
    val minutes = ((end - startMillis) / 60_000L).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrailsScreen(
    trails: List<Trail>,
    distanceUnit: DistanceUnit,
    onBack: () -> Unit,
    onOpen: (Trail) -> Unit,
    onDelete: (Trail) -> Unit,
    onRecordNew: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Trail?>(null) }

    pendingDelete?.let { trail ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete trail?") },
            text = { Text("\"${trail.name}\" and its recorded path will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { onDelete(trail); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Trails", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (trails.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No trails yet. Record one and it will show up here.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
                    items(trails, key = { it.id }) { trail ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onOpen(trail) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trail.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        formatTrailDate(trail.startedAtMillis),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                    Text(
                                        "${formatTrailDistance(trail.distanceMeters, distanceUnit)}  ·  ${formatDuration(trail.startedAtMillis, trail.endedAtMillis)}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { pendingDelete = trail }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete trail", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onRecordNew,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) { Text("Record a new trail") }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrailDetailScreen(
    trail: Trail?,
    points: List<TrailPoint>,
    distanceUnit: DistanceUnit,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val line = remember(points) { points.map { LatLon(it.latitude, it.longitude) } }
    val state = rememberMapState(20.0, 0.0, 2.5f)

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete trail?") },
            text = { Text("This removes the recorded path from this phone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    val markers = remember(points) {
        buildList {
            if (points.isNotEmpty()) {
                add(MapMarker(points.first().latitude, points.first().longitude, "Start", androidx.compose.ui.graphics.Color(0xFF2E7D32)))
                if (points.size > 1) {
                    add(MapMarker(points.last().latitude, points.last().longitude, "End", androidx.compose.ui.graphics.Color(0xFFE53935)))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trail?.name ?: "Trail", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete trail")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SlippyMap(
                state = state,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                lines = listOf(MapLine(line)),
                markers = markers,
                fitTo = line,
                fitKey = trail?.id
            )
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (trail != null) {
                    Text(formatTrailDate(trail.startedAtMillis), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatTrailDistance(trail.distanceMeters, distanceUnit)}  ·  ${formatDuration(trail.startedAtMillis, trail.endedAtMillis)}  ·  ${points.size} points",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (points.size < 2) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Not enough points were recorded to draw a path.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
