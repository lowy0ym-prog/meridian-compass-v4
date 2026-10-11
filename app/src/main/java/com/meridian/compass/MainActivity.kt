package com.meridian.compass

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.meridian.compass.settings.ThemePreference
import com.meridian.compass.ui.map.MapMarker
import com.meridian.compass.ui.screens.AddEditWaypointScreen
import com.meridian.compass.ui.screens.CompassScreen
import com.meridian.compass.ui.screens.MarkOnMapScreen
import com.meridian.compass.ui.screens.RecordTrailScreen
import com.meridian.compass.ui.screens.SettingsScreen
import com.meridian.compass.ui.screens.TrailDetailScreen
import com.meridian.compass.ui.screens.TrailsScreen
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
    val trails by trailViewModel.trails.collectAsState()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as MeridianApplication
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(uiState.hasArrivedAtWaypoint) {
        if (uiState.hasArrivedAtWaypoint) {
            compassViewModel.hapticManager.fireArrivalCelebration()
        }
    }

    var editingWaypointId by remember { mutableStateOf<Long?>(null) }
    var pendingPickedLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedTrailId by remember { mutableStateOf<Long?>(null) }

    val waypointMarkers = remember(waypoints) {
        waypoints.map { MapMarker(it.latitude, it.longitude, it.name) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "compass",
            modifier = Modifier.fillMaxSize()
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
                    onBack = { navController.popBackStack() },
                    onAddNew = {
                        editingWaypointId = null
                        pendingPickedLocation = null
                        navController.navigate("waypointEdit")
                    },
                    onMarkOnMap = { navController.navigate("markOnMap") },
                    onRecordTrail = { navController.navigate("recordTrail") },
                    onOpenTrails = { navController.navigate("trails") },
                    onEdit = { wp ->
                        editingWaypointId = wp.id
                        pendingPickedLocation = null
                        navController.navigate("waypointEdit")
                    },
                    onDelete = { wp -> waypointViewModel.delete(wp) },
                    onSelect = { wp ->
                        compassViewModel.selectWaypoint(wp)
                        navController.popBackStack("compass", false)
                    }
                )
            }
            composable("markOnMap") {
                MarkOnMapScreen(
                    currentLat = uiState.location.latitude,
                    currentLon = uiState.location.longitude,
                    markers = waypointMarkers,
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
                    currentLat = uiState.location.latitude,
                    currentLon = uiState.location.longitude,
                    gpsAccuracyMeters = uiState.location.horizontalAccuracyMeters,
                    onBack = { navController.popBackStack() },
                    onOpenTrails = { navController.navigate("trails") },
                    onStart = { name -> trailViewModel.startRecording(name) },
                    onStop = {
                        trailViewModel.stopAndSave()
                        navController.navigate("trails") { popUpTo("recordTrail") { inclusive = true } }
                    },
                    onDiscard = { trailViewModel.discard(); navController.popBackStack() }
                )
            }
            composable("trails") {
                TrailsScreen(
                    trails = trails,
                    distanceUnit = uiState.settings.distanceUnit,
                    onBack = { navController.popBackStack() },
                    onOpen = { trail ->
                        selectedTrailId = trail.id
                        navController.navigate("trailDetail")
                    },
                    onDelete = { trail -> trailViewModel.deleteTrail(trail) },
                    onRecordNew = { navController.navigate("recordTrail") }
                )
            }
            composable("trailDetail") {
                val id = selectedTrailId
                val trail = trails.find { it.id == id }
                val pointsFlow = remember(id) { trailViewModel.pointsFor(id ?: -1L) }
                val pts by pointsFlow.collectAsState(initial = emptyList())
                TrailDetailScreen(
                    trail = trail,
                    points = pts,
                    distanceUnit = uiState.settings.distanceUnit,
                    onBack = { navController.popBackStack() },
                    onDelete = {
                        trail?.let { trailViewModel.deleteTrail(it) }
                        navController.popBackStack()
                    }
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
                    onBack = { navController.popBackStack() },
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
