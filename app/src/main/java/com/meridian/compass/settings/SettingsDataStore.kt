package com.meridian.compass.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "meridian_settings")

enum class NorthReference { MAGNETIC, TRUE }
enum class DistanceUnit { METRIC, IMPERIAL }
enum class AltitudeUnit { METERS, FEET }
enum class ThemePreference { LIGHT, DARK, SYSTEM }

data class AppSettings(
    val northReference: NorthReference = NorthReference.TRUE,
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.METERS,
    val hapticEnabled: Boolean = true,
    val hapticStepDegrees: Int = 5,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val keepScreenOn: Boolean = true,
    val preciseDistance: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val NORTH_REF = stringPreferencesKey("north_reference")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val ALTITUDE_UNIT = stringPreferencesKey("altitude_unit")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val HAPTIC_STEP = intPreferencesKey("haptic_step_degrees")
        val THEME = stringPreferencesKey("theme")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PRECISE_DISTANCE = booleanPreferencesKey("precise_distance")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            northReference = prefs[Keys.NORTH_REF]?.let { runCatching { NorthReference.valueOf(it) }.getOrNull() } ?: NorthReference.TRUE,
            distanceUnit = prefs[Keys.DISTANCE_UNIT]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: DistanceUnit.METRIC,
            altitudeUnit = prefs[Keys.ALTITUDE_UNIT]?.let { runCatching { AltitudeUnit.valueOf(it) }.getOrNull() } ?: AltitudeUnit.METERS,
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            hapticStepDegrees = prefs[Keys.HAPTIC_STEP] ?: 5,
            theme = prefs[Keys.THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() } ?: ThemePreference.SYSTEM,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            preciseDistance = prefs[Keys.PRECISE_DISTANCE] ?: true
        )
    }

    suspend fun setNorthReference(value: NorthReference) {
        context.dataStore.edit { it[Keys.NORTH_REF] = value.name }
    }
    suspend fun setDistanceUnit(value: DistanceUnit) {
        context.dataStore.edit { it[Keys.DISTANCE_UNIT] = value.name }
    }
    suspend fun setAltitudeUnit(value: AltitudeUnit) {
        context.dataStore.edit { it[Keys.ALTITUDE_UNIT] = value.name }
    }
    suspend fun setHapticEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ENABLED] = value }
    }
    suspend fun setHapticStepDegrees(value: Int) {
        context.dataStore.edit { it[Keys.HAPTIC_STEP] = value }
    }
    suspend fun setTheme(value: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = value.name }
    }
    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }
    }
    suspend fun setPreciseDistance(value: Boolean) {
        context.dataStore.edit { it[Keys.PRECISE_DISTANCE] = value }
    }
}
