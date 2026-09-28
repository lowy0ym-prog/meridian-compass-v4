# Meridian Compass

Original Android compass app (Kotlin + Jetpack Compose), offline, no accounts, no internet permission.

## Features
- Compass dial with 5-degree ticks, N/NE/E/SE/S/SW/W/NW, degree labels, smooth sensor-fusion heading (ROTATION_VECTOR + extra smoothing)
- Magnetic / True North toggle (declination from the platform `GeomagneticField`, offline)
- Accuracy readout, magnetic-interference warning, calibration instructions
- Haptic detents every N degrees (2/5/10/15/30, default 5) with hysteresis + debounce; stronger click at N/E/S/W
- Waypoints: save current location, rename/edit/delete, select, distance + bearing, dial target marker, turn hint; stored locally
- Location: lat/lon, accuracy, altitude (marked as estimate when accuracy is limited), barometer pressure if present
- Sun and Moon on the dial (azimuth + altitude), sunrise/sunset, moonrise/moonset, moon phase/illumination (offline math)
- Settings: north mode, units, haptics + interval, theme, keep screen on, calibration help, about

## Build the APK
GitHub: push to any branch (or Actions > Build Meridian Compass debug APK > Run workflow). Download the artifact `MeridianCompass-debug-apk`, unzip, install `app-debug.apk`.

Local: open in Android Studio and Run, or `gradle :app:assembleDebug` (Gradle 8.9, JDK 17). APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Status
Code was written without being compiled or run in an emulator. See the chat report for what has and has not been verified.
