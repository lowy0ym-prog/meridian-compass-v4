package com.meridian.compass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meridian.compass.settings.AltitudeUnit
import com.meridian.compass.settings.AppSettings
import com.meridian.compass.settings.DistanceUnit
import com.meridian.compass.settings.NorthReference
import com.meridian.compass.settings.ThemePreference

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onNorthReferenceChange: (NorthReference) -> Unit,
    onDistanceUnitChange: (DistanceUnit) -> Unit,
    onAltitudeUnitChange: (AltitudeUnit) -> Unit,
    onHapticEnabledChange: (Boolean) -> Unit,
    onHapticStepChange: (Int) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onPreciseDistanceChange: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {

            SectionTitle("North Reference")
            RadioRow("True North", settings.northReference == NorthReference.TRUE) { onNorthReferenceChange(NorthReference.TRUE) }
            RadioRow("Magnetic North", settings.northReference == NorthReference.MAGNETIC) { onNorthReferenceChange(NorthReference.MAGNETIC) }

            Divider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Distance Unit")
            RadioRow("Metric (m / km)", settings.distanceUnit == DistanceUnit.METRIC) { onDistanceUnitChange(DistanceUnit.METRIC) }
            RadioRow("Imperial (ft / mi)", settings.distanceUnit == DistanceUnit.IMPERIAL) { onDistanceUnitChange(DistanceUnit.IMPERIAL) }

            Divider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Altitude Unit")
            RadioRow("Meters", settings.altitudeUnit == AltitudeUnit.METERS) { onAltitudeUnitChange(AltitudeUnit.METERS) }
            RadioRow("Feet", settings.altitudeUnit == AltitudeUnit.FEET) { onAltitudeUnitChange(AltitudeUnit.FEET) }

            Divider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Distance Display")
            SwitchRow("Show exact distance", settings.preciseDistance, onPreciseDistanceChange)
            Text(
                if (settings.preciseDistance) "e.g. 42 m, 128 ft" else "Rounded, e.g. ~40 m, ~130 ft",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Divider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Haptic Step Feedback")
            SwitchRow("Enabled", settings.hapticEnabled, onHapticEnabledChange)
            Text(
                "Step interval: every ${settings.hapticStepDegrees} degrees",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Slider(
                value = settings.hapticStepDegrees.toFloat(),
                onValueChange = { onHapticStepChange(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28
            )

            Divider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Theme")
            RadioRow("System default", settings.theme == ThemePreference.SYSTEM) { onThemeChange(ThemePreference.SYSTEM) }
            RadioRow("Light", settings.theme == ThemePreference.LIGHT) { onThemeChange(ThemePreference.LIGHT) }
            RadioRow("Dark", settings.theme == ThemePreference.DARK) { onThemeChange(ThemePreference.DARK) }

            Divider(Modifier.padding(vertical = 12.dp))

            SwitchRow("Keep screen on while compass is open", settings.keepScreenOn, onKeepScreenOnChange)

            Divider(Modifier.padding(vertical = 12.dp))

            SectionTitle("Calibration")
            Text(
                "If the compass reports low accuracy, wave your phone in a slow figure-8 motion " +
                    "several times, away from metal objects, magnets, speakers, and other electronics.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Divider(Modifier.padding(vertical = 12.dp))

            SectionTitle("About")
            Text(
                "Meridian Compass uses your device's motion sensors and GPS to provide heading, " +
                    "waypoint navigation, and sun/moon position. All location and waypoint data stays " +
                    "on your device, nothing is uploaded, and no account is required.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onBackground)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
