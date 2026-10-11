package com.meridian.compass.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private object MapAssets {
    private fun read(name: String): String? = runCatching {
        MapAssets::class.java.classLoader
            ?.getResourceAsStream("META-INF/resources/webjars/leaflet/1.9.4/dist/$name")
            ?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    val js: String? by lazy { read("leaflet.js") }
    val css: String? by lazy { read("leaflet.css") }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkOnMapScreen(
    currentLat: Double?,
    currentLon: Double?,
    onBack: () -> Unit,
    onLocationPicked: (lat: Double, lon: Double) -> Unit
) {
    val hasFix = currentLat != null && currentLon != null
    val startLat = currentLat ?: 20.0
    val startLon = currentLon ?: 0.0

    var centerLat by remember { mutableStateOf(startLat) }
    var centerLon by remember { mutableStateOf(startLon) }
    var isDark by remember { mutableStateOf(false) }
    var isTopo by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var recentered by remember { mutableStateOf(hasFix) }

    LaunchedEffect(currentLat, currentLon, pageLoaded) {
        if (!recentered && pageLoaded && currentLat != null && currentLon != null) {
            recentered = true
            webViewRef?.evaluateJavascript("if (window.map) { map.setView([$currentLat, $currentLon], 17); }", null)
        }
    }

    val distanceMeters = if (currentLat != null && currentLon != null) {
        Bearing.distanceMeters(currentLat, currentLon, centerLat, centerLon)
    } else null

    fun reload() {
        mapError = null
        webViewRef?.loadDataWithBaseURL(
            "https://appassets.local/",
            buildMapHtml(centerLat, centerLon, if (hasFix) 17 else 2, isDark, isTopo),
            "text/html", "UTF-8", null
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // Top controls - their own row, not overlapping the map
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
            IconButton(onClick = {
                if (currentLat != null && currentLon != null) {
                    webViewRef?.evaluateJavascript("if (window.map) { map.setView([$currentLat, $currentLon], 17); }", null)
                }
            }) { Icon(Icons.Filled.MyLocation, contentDescription = "My location", tint = MaterialTheme.colorScheme.onBackground) }
            IconButton(onClick = { isDark = !isDark; reload() }) {
                Icon(Icons.Filled.Contrast, contentDescription = "Toggle dark map", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = { isTopo = !isTopo; reload() }) {
                Icon(Icons.Filled.Layers, contentDescription = "Toggle terrain map", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (mapError != null) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    mapError ?: "",
                    modifier = Modifier.padding(10.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                            "https://appassets.local/",
                            buildMapHtml(startLat, startLon, if (hasFix) 17 else 2, isDark, isTopo),
                            "text/html",
                            "UTF-8",
                            null
                        )
                        webViewRef = this
                    }
                },
                update = { }
            )

            // Centre pin (not interactive)
            Icon(
                Icons.Filled.Place,
                contentDescription = "Selected point",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .padding(bottom = 20.dp)
            )
        }

        // Bottom controls - their own panel, not overlapping the map
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (distanceMeters != null) {
                Text(
                    "${formatDistance(distanceMeters, DistanceUnit.IMPERIAL)} from your position",
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

private fun formatDistance(meters: Double, unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METRIC -> if (meters >= 1000) String.format(Locale.US, "%.2f km", meters / 1000) else "${meters.toInt()} m"
    DistanceUnit.IMPERIAL -> if (meters >= 1609) String.format(Locale.US, "%.2f mi", meters / 1609.344) else "${Bearing.metersToFeet(meters).toInt()} ft"
}

private fun buildMapHtml(lat: Double, lon: Double, zoom: Int, dark: Boolean, topo: Boolean): String {
    val tileUrl = when {
        topo -> "https://tile.opentopomap.org/{z}/{x}/{y}.png"
        dark -> "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        else -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    }
    val css = MapAssets.css
    val js = MapAssets.js
    val cssBlock = if (css != null) "<style>$css</style>"
        else "<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\"/>"
    val jsBlock = if (js != null) "<script>$js</script>"
        else "<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>"

    val page = """
        <!DOCTYPE html>
        <html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
        __CSS__
        <style>html,body,#map{height:100%;margin:0;padding:0;background:#dfe3e8;}</style>
        </head><body>
        <div id="map"></div>
        __JS__
        <script>
          if (typeof L === 'undefined') {
            if (window.Android) { Android.onMapError('Map library failed to load.'); }
          } else {
            var map = L.map('map', {zoomControl:false, attributionControl:false}).setView([$lat, $lon], $zoom);
            var tiles = L.tileLayer('$tileUrl', { maxZoom: 19 }).addTo(map);
            var tileErrors = 0;
            var tileOk = 0;
            tiles.on('tileload', function() { tileOk++; });
            tiles.on('tileerror', function() {
              tileErrors++;
              if (tileErrors === 6 && tileOk === 0 && window.Android) {
                Android.onMapError('Map tiles are not loading. Check your internet connection.');
              }
            });
            map.on('moveend', function() {
              var c = map.getCenter();
              if (window.Android) { Android.onCenterChanged(c.lat, c.lng); }
            });
          }
        </script>
        </body></html>
    """.trimIndent()
    return page.replace("__CSS__", cssBlock).replace("__JS__", jsBlock)
}
