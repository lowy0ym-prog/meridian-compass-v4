# Meridian Compass

A full-featured Android compass app (Kotlin + Jetpack Compose), inspired by Waypoint Compass's layout and UX, built offline-first with no account and no Google Play Services dependency.

## Features
- Rotating compass dial: degree ticks, cardinal/intercardinal labels, sensor-fusion heading with smoothing, accuracy readout, magnetic-interference warning, calibration guidance
- True / Magnetic North toggle with offline declination (GeomagneticField)
- Configurable haptic step feedback as the heading crosses degree increments, with hysteresis so it doesn't fire from sensor jitter
- Waypoints ("markers"): save, edit, delete, categorize, attach a photo, see live bearing + distance, tap a marker's badge on the dial to select it as the target
- "Mark on map" screen (WebView + Leaflet, free OSM tiles) to drop a pin anywhere, not just your current GPS fix
- Trail recording: GPS breadcrumb path saved locally with a live offline preview canvas
- Sun & Moon shown on the dial (azimuth/elevation), sunrise/sunset, moonrise/moonset, moon phase & illumination, all computed offline
- Lock-bearing mode: lock the current heading as a target without a saved waypoint
- Settings: north reference, distance/altitude units, haptics + step interval, theme, keep-screen-on, precise vs rounded distance display
- Room database for waypoints/trails, DataStore for settings, both local-only

## Status
This is the corrected version of a larger "big update" build that originally failed to compile. It had 5 files with missing Kotlin imports (padding, size, clickable, width, height across MainActivity.kt, CompassScreen.kt, MarkOnMapScreen.kt, WaypointListScreen.kt, AddEditWaypointScreen.kt), all fixed here. The rest of the codebase (Room entities/DAOs, ViewModels, sensors, astronomy math, theme) was reviewed and is structurally sound, but the full project has not yet been run through an actual Gradle compiler, that's what the GitHub Actions workflow in this repo is for.

## Build the APK
GitHub: push to any branch, or go to Actions, "Build Meridian Compass debug APK", Run workflow. Download the artifact MeridianCompass-debug-apk, unzip, install app-debug.apk.

Local: open in Android Studio and Run, or gradle :app:assembleDebug (JDK 17). Output: app/build/outputs/apk/debug/app-debug.apk.
