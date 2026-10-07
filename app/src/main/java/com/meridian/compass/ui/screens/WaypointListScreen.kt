package com.meridian.compass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian.compass.data.MarkerCategory
import com.meridian.compass.data.Waypoint
import com.meridian.compass.settings.DistanceUnit
import com.meridian.compass.util.Bearing

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WaypointListScreen(
    waypoints: List<Waypoint>,
    currentLat: Double?,
    currentLon: Double?,
    distanceUnit: DistanceUnit,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onMarkOnMap: () -> Unit,
    onRecordTrail: () -> Unit,
    onEdit: (Waypoint) -> Unit,
    onDelete: (Waypoint) -> Unit,
    onSelect: (Waypoint) -> Unit
) {
    var fabExpanded by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showRallyInfo by remember { mutableStateOf(false) }

    val visibleWaypoints = if (searchQuery.isBlank()) waypoints
        else waypoints.filter { it.name.contains(searchQuery, ignoreCase = true) }

    if (showRallyInfo) {
        AlertDialog(
            onDismissRequest = { showRallyInfo = false },
            title = { Text("Rally isn't available") },
            text = {
                Text(
                    "Rally shares live location between devices, which needs an internet " +
                        "connection and an active session on a server. This app is built to work " +
                        "fully offline with no account and no data leaving your phone, so live " +
                        "location sharing isn't included."
                )
            },
            confirmButton = { TextButton(onClick = { showRallyInfo = false }) { Text("Got it") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Markers", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            SpeedDialFab(
                expanded = fabExpanded,
                onToggle = { fabExpanded = !fabExpanded },
                onSearch = { searchOpen = !searchOpen; fabExpanded = false },
                onRally = { showRallyInfo = true; fabExpanded = false },
                onRecordTrail = { onRecordTrail(); fabExpanded = false },
                onMarkOnMap = { onMarkOnMap(); fabExpanded = false },
                onMarkMyLocation = { onAddNew(); fabExpanded = false }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(visible = searchOpen, enter = fadeIn(), exit = fadeOut()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search markers") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (visibleWaypoints.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (waypoints.isEmpty())
                            "No markers yet. Tap the button below to mark your first spot."
                        else "No markers match \"$searchQuery\".",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    items(visibleWaypoints, key = { it.id }) { wp ->
                        WaypointRow(
                            waypoint = wp,
                            currentLat = currentLat,
                            currentLon = currentLon,
                            distanceUnit = distanceUnit,
                            onEdit = { onEdit(wp) },
                            onDelete = { onDelete(wp) },
                            onSelect = { onSelect(wp) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedDialFab(
    expanded: Boolean,
    onToggle: () -> Unit,
    onSearch: () -> Unit,
    onRally: () -> Unit,
    onRecordTrail: () -> Unit,
    onMarkOnMap: () -> Unit,
    onMarkMyLocation: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Column(horizontalAlignment = Alignment.End) {
                SpeedDialAction("Search", Icons.Filled.Search, onSearch)
                Spacer(Modifier.height(10.dp))
                SpeedDialAction("Start or join a Rally", Icons.Filled.Groups, onRally, badge = "BETA")
                Spacer(Modifier.height(10.dp))
                SpeedDialAction("Record trail", Icons.Filled.Route, onRecordTrail)
                Spacer(Modifier.height(10.dp))
                SpeedDialAction("Mark on map", Icons.Filled.Map, onMarkOnMap)
                Spacer(Modifier.height(10.dp))
                SpeedDialAction("Mark my location", Icons.Filled.MyLocation, onMarkMyLocation)
                Spacer(Modifier.height(14.dp))
            }
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.error
        ) {
            Icon(Icons.Filled.Navigation, contentDescription = "Marker actions", tint = MaterialTheme.colorScheme.onError)
        }
    }
}

@Composable
private fun SpeedDialAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(6.dp)) {
                        Text(badge, fontSize = 9.sp, color = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun WaypointRow(
    waypoint: Waypoint,
    currentLat: Double?,
    currentLon: Double?,
    distanceUnit: DistanceUnit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    val distanceText = if (currentLat != null && currentLon != null) {
        val meters = Bearing.distanceMeters(currentLat, currentLon, waypoint.latitude, waypoint.longitude)
        val bearing = Bearing.bearingDegrees(currentLat, currentLon, waypoint.latitude, waypoint.longitude)
        val distStr = when (distanceUnit) {
            DistanceUnit.METRIC -> if (meters >= 1000) "%.2f km".format(meters / 1000) else "${meters.toInt()} m"
            DistanceUnit.IMPERIAL -> if (meters >= 1609) "%.2f mi".format(meters / 1609.344) else "${(meters * 3.28084).toInt()} ft"
        }
        "${bearing.toInt()} deg  -  $distStr"
    } else "--"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(MarkerCategory.icon(MarkerCategory.fromName(waypoint.category)), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(waypoint.name, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(distanceText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                }
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                "Point compass here",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clickable { onSelect() },
            )
        }
    }
}
