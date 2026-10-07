package com.meridian.compass.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian.compass.data.Trail
import com.meridian.compass.data.TrailPoint
import com.meridian.compass.settings.DistanceUnit
import com.meridian.compass.util.Bearing

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RecordTrailScreen(
    activeTrail: Trail?,
    points: List<TrailPoint>,
    distanceMeters: Double,
    distanceUnit: DistanceUnit,
    onBack: () -> Unit,
    onStart: (name: String) -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record Trail", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text(
                "Fully offline: records your GPS breadcrumb path with no map or internet required.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )
            Spacer(Modifier.height(16.dp))

            if (activeTrail == null) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Trail name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onStart(name) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Recording")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatBlock("Points", points.size.toString())
                    StatBlock("Distance", formatDist(distanceMeters, distanceUnit))
                    StatBlock("Trail", activeTrail.name)
                }
                Spacer(Modifier.height(16.dp))
                TrailPreviewCanvas(points)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) { Text("Discard") }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Text("Stop & Save") }
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

@Composable
private fun TrailPreviewCanvas(points: List<TrailPoint>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(4.dp)
    ) {
        if (points.size < 2) return@Canvas
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }
        val maxLon = points.maxOf { it.longitude }
        val latSpan = (maxLat - minLat).coerceAtLeast(0.00005)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.00005)
        val margin = size.width * 0.1f

        fun toOffset(lat: Double, lon: Double): Offset {
            val x = margin + ((lon - minLon) / lonSpan * (size.width - 2 * margin)).toFloat()
            val y = size.height - margin - ((lat - minLat) / latSpan * (size.height - 2 * margin)).toFloat()
            return Offset(x, y)
        }

        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { index, p ->
            val offset = toOffset(p.latitude, p.longitude)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path, color = androidx.compose.ui.graphics.Color(0xFF2FE6C6), style = Stroke(width = 5f))
        drawCircle(androidx.compose.ui.graphics.Color(0xFF2FE6C6), radius = 8f, center = toOffset(points.first().latitude, points.first().longitude))
        drawCircle(androidx.compose.ui.graphics.Color(0xFFFF6B6B), radius = 8f, center = toOffset(points.last().latitude, points.last().longitude))
    }
}

private fun formatDist(meters: Double, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METRIC -> if (meters >= 1000) "%.2f km".format(meters / 1000) else "${meters.toInt()} m"
    DistanceUnit.IMPERIAL -> if (meters >= 1609) "%.2f mi".format(meters / 1609.344) else "${Bearing.metersToFeet(meters).toInt()} ft"
}
