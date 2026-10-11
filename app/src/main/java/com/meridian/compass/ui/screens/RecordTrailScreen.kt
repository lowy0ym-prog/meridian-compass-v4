package com.meridian.compass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian.compass.data.Trail
import com.meridian.compass.data.TrailPoint
import com.meridian.compass.settings.DistanceUnit
import com.meridian.compass.ui.map.LatLon
import com.meridian.compass.ui.map.MapLine
import com.meridian.compass.ui.map.SlippyMap
import com.meridian.compass.ui.map.rememberMapState

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RecordTrailScreen(
    activeTrail: Trail?,
    points: List<TrailPoint>,
    distanceMeters: Double,
    distanceUnit: DistanceUnit,
    currentLat: Double?,
    currentLon: Double?,
    gpsAccuracyMeters: Float?,
    onBack: () -> Unit,
    onOpenTrails: () -> Unit,
    onStart: (name: String) -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val hasFix = currentLat != null && currentLon != null
    val state = com.meridian.compass.ui.map.rememberMapState(currentLat ?: 20.0, currentLon ?: 0.0, if (hasFix) 17f else 2.5f)

    LaunchedEffect(hasFix) {
        if (currentLat != null && currentLon != null) {
            state.moveTo(currentLat, currentLon, 17f)
            state.following = true
        }
    }

    val line = remember(points) { points.map { LatLon(it.latitude, it.longitude) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Trail", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = onOpenTrails) { Text("Saved trails") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SlippyMap(
                state = state,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                lines = listOf(MapLine(line)),
                userLocation = if (currentLat != null && currentLon != null) LatLon(currentLat, currentLon) else null
            )

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    when {
                        !hasFix -> "Waiting for GPS… go outside for best accuracy."
                        gpsAccuracyMeters != null -> "GPS accuracy ±${gpsAccuracyMeters.toInt()} m"
                        else -> "GPS ready"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
                Spacer(Modifier.height(8.dp))

                if (activeTrail == null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Trail name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { onStart(name) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start Recording")
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatBlock("Points", points.size.toString())
                        StatBlock("Distance", formatTrailDistance(distanceMeters, distanceUnit))
                        StatBlock("Trail", activeTrail.name)
                    }
                    if (points.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Recording… waiting for a good GPS fix. Start walking.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Discard") }
                        Button(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop & Save") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text(value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}
