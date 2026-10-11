package com.meridian.compass.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.viewinterop.AndroidView
import com.meridian.compass.settings.DistanceUnit
import com.meridian.compass.util.Bearing
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkOnMapScreen(
    currentLat: Double?,
    currentLon: Double?,
    onBack: () -> Unit,
    onLocationPicked: (lat: Double, lon: Double) -> Unit
) {
    val startLat = currentLat ?: 0.0
    val startLon = currentLon ?: 0.0

    var centerLat by remember { mutableStateOf(startLat) }
    var centerLon by remember { mutableStateOf(startLon) }
    var isDark by remember { mutableStateOf(true) }
    var isTopo by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var recentered by remember { mutableStateOf(currentLat != null) }

    LaunchedEffect(currentLat, currentLon, pageLoaded) {
        if (!recentered && pageLoaded && currentLat != null && currentLon != null) {
            recentered = true
            webViewRef?.evaluateJavascript("if (window.map) { map.setView([$currentLat, $currentLon], 17); }", null)
        }
    }

    val distanceMeters = if (currentLat != null && currentLon != null) {
        Bearing.distanceMeters(currentLat, currentLon, centerLat, centerLon)
    } else null

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            pageLoaded = false
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            pageLoaded = true
                        }
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onCenterChanged(lat: Double, lon: Double) {
                                Handler(Looper.getMainLooper()).post {
                                    centerLat = lat
                                    centerLon = lon
                                }
                            }

                            @JavascriptInterface
                            fun onMapError(message: String) {
                                Handler(Looper.getMainLooper()).post { mapError = message }
                            }
                        },
                        "Android"
                    )
                    loadDataWithBaseURL(
                        "https://leaflet-map.local/",
                        buildMapHtml(startLat, startLon, isDark, isTopo),
                        "text/html",
                        "UTF-8",
                        null
                    )
                    webViewRef = this
                }
            },
            update = { }
        )

        Icon(
            Icons.Filled.Place,
            contentDescription = "Selected point",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .padding(bottom = 20.dp)
        )

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        String.format(Locale.US, "%.5f, %.5f", centerLat, centerLon),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column {
                    FloatingActionButton(
                        onClick = {
                            isDark = !isDark
                            webViewRef?.loadDataWithBaseURL(
                                "https://leaflet-map.local/", buildMapHtml(centerLat, centerLon, isDark, isTopo),
                                "text/html", "UTF-8", null
                            )
                        },
                        modifier = Modifier.size(44.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) { Icon(Icons.Filled.Contrast, contentDescription = "Toggle contrast", modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.height(8.dp))
                    FloatingActionButton(
                        onClick = {
                            isTopo = !isTopo
                            webViewRef?.loadDataWithBaseURL(
                                "https://leaflet-map.local/", buildMapHtml(centerLat, centerLon, isDark, isTopo),
                                "text/html", "UTF-8", null
                            )
                        },
                        modifier = Modifier.size(44.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) { Icon(Icons.Filled.Layers, contentDescription = "Toggle map layer", modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.height(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (currentLat != null && currentLon != null) {
                                webViewRef?.evaluateJavascript("if (window.map) { map.setView([$currentLat, $currentLon], 17); }", null)
                            }
                        },
                        modifier = Modifier.size(44.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) { Icon(Icons.Filled.MyLocation, contentDescription = "My location", modifier = Modifier.size(18.dp)) }
                }
            }

            if (mapError != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        mapError ?: "",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (distanceMeters != null) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "${formatDistance(distanceMeters, DistanceUnit.IMPERIAL)} from your position",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = { onLocationPicked(centerLat, centerLon) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use this location")
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(44.dp)
                    .clickable { onBack() }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatDistance(meters: Double, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METRIC -> if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000) else "${meters.toInt()} m"
    DistanceUnit.IMPERIAL -> if (meters >= 1609) String.format(Locale.US, "%.2f mi", meters / 1609.344) else "${Bearing.metersToFeet(meters).toInt()} ft"
}

private fun buildMapHtml(lat: Double, lon: Double, dark: Boolean, topo: Boolean): String {
    val tileUrl = when {
        topo -> "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png"
        dark -> "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        else -> "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"
    }
    return """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <style>html,body,#map{height:100%;margin:0;padding:0;background:#111;}</style>
        </head><body>
        <div id="map"></div>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <script>
          if (typeof L === 'undefined') {
            if (window.Android) { Android.onMapError('Map could not load. It needs an internet connection the first time.'); }
          } else {
            var map = L.map('map', {zoomControl:false, attributionControl:false}).setView([$lat, $lon], 17);
            var tiles = L.tileLayer('$tileUrl', { maxZoom: 19 }).addTo(map);
            var tileErrors = 0;
            tiles.on('tileerror', function() {
              tileErrors++;
              if (tileErrors === 6 && window.Android) { Android.onMapError('Map tiles are not loading. Check your internet connection.'); }
            });
            map.on('moveend', function() {
              var c = map.getCenter();
              if (window.Android) { Android.onCenterChanged(c.lat, c.lng); }
            });
          }
        </script>
        </body></html>
    """.trimIndent()
}
