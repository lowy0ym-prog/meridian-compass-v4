package com.meridian.compass

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF4DD0C4),
    onPrimary = Color(0xFF00201D),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF141B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1E2830),
    onSurfaceVariant = Color(0xFF9FB0BF),
    secondaryContainer = Color(0xFF0F3B37),
    onSecondaryContainer = Color(0xFFBFF3EE),
    error = Color(0xFFFF6B6B)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00796B),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF10181F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10181F),
    surfaceVariant = Color(0xFFE3EAF0),
    onSurfaceVariant = Color(0xFF52606D),
    secondaryContainer = Color(0xFFC9EFEA),
    onSecondaryContainer = Color(0xFF00201D),
    error = Color(0xFFC62828)
)

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MeridianApp(model) }
    }

    override fun onResume() {
        super.onResume()
        model.onResume()
    }

    override fun onPause() {
        super.onPause()
        model.onPause()
    }
}

@Composable
fun MeridianApp(model: AppModel) {
    val dark = when (model.settings.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    var screen by rememberSaveable { mutableIntStateOf(0) } // 0 compass, 1 waypoints, 2 settings

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.location.refreshPermission()
        model.location.start()
    }
    val requestPermission = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(Unit) {
        if (!model.location.permissionGranted) requestPermission()
    }
    BackHandler(enabled = screen != 0) { screen = 0 }

    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            when (screen) {
                1 -> WaypointsScreen(model, onBack = { screen = 0 }, onPicked = { screen = 0 })
                2 -> SettingsScreen(model, onBack = { screen = 0 })
                else -> CompassScreen(
                    model,
                    onWaypoints = { screen = 1 },
                    onSettings = { screen = 2 },
                    requestPermission = { requestPermission() }
                )
            }
        }
    }
}
