package com.meridian.compass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.meridian.compass.settings.DistanceUnit
import com.meridian.compass.ui.map.LatLon
import com.meridian.compass.ui.map.MapMarker
import com.meridian.compass.ui.map.SlippyMap
import com.meridian.compass.ui.map.rememberMapState
import com.meridian.compass.util.Bearing
import java.util.Locale

@Composable
fun MarkOnMapScreen(
    currentLat: Double?,
    currentLon: Double?,
    markers: List<MapMarker> = emptyList(),
    onBack: () -> Unit,
    onLocationPicked: (lat: Double, lon: Double) -> Unit
) {
    val hasFix = currentLat != null && currentLon != null
    val state = rememberMapState(currentLat ?: 20.0, currentLon ?: 0.0, if (hasFix) 17f else 2.5f)
    var centered by remember { mutableStateOf(hasFix) }

    LaunchedEffect(currentLat, currentLon) {
        if (!centered && currentLat != null && currentLon != null) {
            centered = true
            state.moveTo(currentLat, currentLon, 17f)
        }
    }

    val centerLat = state.centerLat
    val centerLon = state.centerLon
    val distanceMeters = if (currentLat != null && currentLon != null) {
        Bearing.distanceMeters(currentLat, currentLon, centerLat, centerLon)
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "Close map", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                String.format(Locale.US, "%.5f, %.5f", centerLat, centerLon),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SlippyMap(
                state = state,
                modifier = Modifier.fillMaxSize(),
                markers = markers,
                userLocation = if (currentLat != null && currentLon != null) LatLon(currentLat, currentLon) else null
            )
            Icon(
                Icons.Filled.Place,
                contentDescription = "Selected point",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-20).dp)
                    .size(40.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (distanceMeters != null) {
                Text(
                    "${formatMapDistance(distanceMeters, DistanceUnit.IMPERIAL)} from your position",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = { onLocationPicked(centerLat, centerLon) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use this location")
            }
        }
    }
}

private fun formatMapDistance(meters: Double, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METRIC -> if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000) else "${meters.toInt()} m"
    DistanceUnit.IMPERIAL -> if (meters >= 1609) String.format(Locale.US, "%.2f mi", meters / 1609.344) else "${Bearing.metersToFeet(meters).toInt()} ft"
}
