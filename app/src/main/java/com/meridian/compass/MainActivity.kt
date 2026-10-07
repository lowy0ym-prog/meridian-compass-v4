package com.meridian.compass

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meridian.compass.settings.ThemePreference
import com.meridian.compass.ui.screens.AddEditWaypointScreen
import com.meridian.compass.ui.screens.CompassScreen
import com.meridian.compass.ui.screens.MarkOnMapScreen
import com.meridian.compass.ui.screens.RecordTrailScreen
import com.meridian.compass.ui.screens.SettingsScreen
import com.meridian.compass.ui.screens.WaypointListScreen
import com.meridian.compass.ui.theme.AppThemeMode
import com.meridian.compass.ui.theme.MeridianTheme
import com.meridian.compass.viewmodel.CompassViewModel
import com.meridian.compass.viewmodel.TrailViewModel
import com.meridian.compass.viewmodel.WaypointViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val compassViewModel: CompassViewModel by viewModels()
    private val waypointViewModel: WaypointViewModel by viewModels()
    private val trailViewModel: TrailViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.any { it }
        if (granted) {
            compassViewModel.beginLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val app = application as MeridianApplication
            val settings by app.settingsRepository.settingsFlow.collectAsState(
                initial = com.meridian.compass.settings.AppSettings()
            )

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            LaunchedEffect(Unit) {
                requestLocationPermissionIfNeeded()
            }

            val themeMode = when (settings.theme) {
                ThemePreference.LIGHT -> AppThemeMode.LIGHT
                ThemePreference.DARK -> AppThemeMode.DARK
                ThemePreference.SYSTEM -> AppThemeMode.SYSTEM
            }

            MeridianTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavHost(compassViewModel, waypointViewModel, trailViewModel)
                }
            }
        }
    }

    private fun requestLocationPermissionIfNeeded() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            compassViewModel.beginLocationUpdates()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun AppNavHost(compassViewModel: CompassViewModel, waypointViewModel: WaypointViewModel, trailViewModel: TrailViewModel) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        compassViewModel.startSensors()
        onDispose { compassViewModel.stopSensors() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            compassViewModel.refreshCelestialClock()
            kotlinx.coroutines.delay(30_000L)
        }
    }

    val uiState by compassViewModel.uiState.collectAsState()
    val waypoints by waypointViewModel.waypoints.collectAsState()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as MeridianApplication
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(uiState.hasArrivedAtWaypoint) {
        if (uiState.hasArrivedAtWaypoint) {
            compassViewModel.hapticManager.fireArrivalCelebration()
        }
    }

    var editingWaypointId by remember { mutableStateOf<Long?>(null) }
    var pendingPickedLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabRoutes = setOf("compass", "waypoints", "settings")

    androidx.compose.material3.Scaffold(
        bottomBar = {
            if (currentRoute in tabRoutes) {
                PillBottomNav(
                    currentRoute = currentRoute ?: "compass",
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = "compass",
            modifier = Modifier.padding(scaffoldPadding)
        ) {
            composable("compass") {
                CompassScreen(
                    uiState = uiState,
                    waypoints = waypoints,
                    onToggleNorthReference = {
                        val next = if (uiState.settings.northReference == com.meridian.compass.settings.NorthReference.TRUE)
                            com.meridian.compass.settings.NorthReference.MAGNETIC
                        else com.meridian.compass.settings.NorthReference.TRUE
                        coroutineScope.launch { app.settingsRepository.setNorthReference(next) }
                    },
                    onOpenWaypoints = { navController.navigate("waypoints") { launchSingleTop = true } },
                    onOpenSettings = { navController.navigate("settings") { launchSingleTop = true } },
                    onClearTarget = { compassViewModel.selectWaypoint(null) },
                    onSelectWaypointId = { id -> waypoints.find { it.id == id }?.let { compassViewModel.selectWaypoint(it) } },
                    onLockBearing = { compassViewModel.lockCurrentBearing() },
                    onReleaseBearing = { compassViewModel.releaseLockedBearing() }
                )
            }
            composable("waypoints") {
                WaypointListScreen(
                    waypoints = waypoints,
                    currentLat = uiState.location.latitude,
                    currentLon = uiState.location.longitude,
                    distanceUnit = uiState.settings.distanceUnit,
                    onBack = { navController.navigate("compass") { launchSingleTop = true } },
                    onAddNew = {
                        editingWaypointId = null
                        pendingPickedLocation = null
                        navController.navigate("waypointEdit")
                    },
                    onMarkOnMap = { navController.navigate("markOnMap") },
                    onRecordTrail = { navController.navigate("recordTrail") },
                    onEdit = { wp ->
                        editingWaypointId = wp.id
                        pendingPickedLocation = null
                        navController.navigate("waypointEdit")
                    },
                    onDelete = { wp -> waypointViewModel.delete(wp) },
                    onSelect = { wp ->
                        compassViewModel.selectWaypoint(wp)
                        navController.navigate("compass") { launchSingleTop = true }
                    }
                )
            }
            composable("markOnMap") {
                MarkOnMapScreen(
                    currentLat = uiState.location.latitude,
                    currentLon = uiState.location.longitude,
                    onBack = { navController.popBackStack() },
                    onLocationPicked = { lat, lon ->
                        pendingPickedLocation = lat to lon
                        editingWaypointId = null
                        navController.navigate("waypointEdit") { popUpTo("waypoints") }
                    }
                )
            }
            composable("recordTrail") {
                val activeTrail by trailViewModel.activeTrail.collectAsState()
                val points by trailViewModel.recordedPoints.collectAsState()
                val distance by trailViewModel.distanceMeters.collectAsState()
                RecordTrailScreen(
                    activeTrail = activeTrail,
                    points = points,
                    distanceMeters = distance,
                    distanceUnit = uiState.settings.distanceUnit,
                    onBack = { navController.popBackStack() },
                    onStart = { name -> trailViewModel.startRecording(name) },
                    onStop = { trailViewModel.stopAndSave(); navController.popBackStack() },
                    onDiscard = { trailViewModel.discard(); navController.popBackStack() }
                )
            }
            composable("waypointEdit") {
                val existing = waypoints.find { it.id == editingWaypointId }
                AddEditWaypointScreen(
                    existing = existing,
                    currentLat = pendingPickedLocation?.first ?: uiState.location.latitude,
                    currentLon = pendingPickedLocation?.second ?: uiState.location.longitude,
                    currentAltitude = uiState.location.bestAltitudeMeters,
                    onBack = { navController.popBackStack() },
                    onSave = { name, lat, lon, altitude, category, photoPath ->
                        if (existing == null) {
                            waypointViewModel.saveNew(name, lat, lon, altitude, category, photoPath)
                        } else {
                            waypointViewModel.update(
                                existing.copy(
                                    name = name, latitude = lat, longitude = lon, altitudeMeters = altitude,
                                    category = category.name, photoPath = photoPath ?: existing.photoPath
                                )
                            )
                        }
                        pendingPickedLocation = null
                        navController.popBackStack()
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    settings = uiState.settings,
                    onBack = { navController.navigate("compass") { launchSingleTop = true } },
                    onNorthReferenceChange = { coroutineScope.launch { app.settingsRepository.setNorthReference(it) } },
                    onDistanceUnitChange = { coroutineScope.launch { app.settingsRepository.setDistanceUnit(it) } },
                    onAltitudeUnitChange = { coroutineScope.launch { app.settingsRepository.setAltitudeUnit(it) } },
                    onHapticEnabledChange = { coroutineScope.launch { app.settingsRepository.setHapticEnabled(it) } },
                    onHapticStepChange = { coroutineScope.launch { app.settingsRepository.setHapticStepDegrees(it) } },
                    onThemeChange = { coroutineScope.launch { app.settingsRepository.setTheme(it) } },
                    onKeepScreenOnChange = { coroutineScope.launch { app.settingsRepository.setKeepScreenOn(it) } },
                    onPreciseDistanceChange = { coroutineScope.launch { app.settingsRepository.setPreciseDistance(it) } }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PillBottomNav(currentRoute: String, onNavigate: (String) -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
            shadowElevation = 8.dp,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
            ) {
                PillNavItem(
                    selected = currentRoute == "waypoints",
                    icon = androidx.compose.material.icons.Icons.Filled.Place,
                    contentDescription = "Markers",
                    onClick = { onNavigate("waypoints") }
                )
                PillNavItem(
                    selected = currentRoute == "compass",
                    icon = androidx.compose.material.icons.Icons.Filled.Explore,
                    contentDescription = "Compass",
                    onClick = { onNavigate("compass") }
                )
                PillNavItem(
                    selected = currentRoute == "settings",
                    icon = androidx.compose.material.icons.Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = { onNavigate("settings") }
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PillNavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Transparent
    val tint = if (selected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    androidx.compose.material3.Surface(
        color = bg,
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = Modifier
            .size(52.dp)
            .clickable(onClick = onClick)
    ) {
        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            androidx.compose.material3.Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}
