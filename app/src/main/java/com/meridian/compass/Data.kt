package com.meridian.compass

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Waypoint(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val alt: Double?
)

/** User settings, persisted in SharedPreferences (stays on device). */
class AppSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var trueNorth by mutableStateOf(sp.getBoolean("trueNorth", false)); private set
    var metricDistance by mutableStateOf(sp.getBoolean("metricDistance", true)); private set
    var metricAltitude by mutableStateOf(sp.getBoolean("metricAltitude", true)); private set
    var haptics by mutableStateOf(sp.getBoolean("haptics", true)); private set
    var majorStrong by mutableStateOf(sp.getBoolean("majorStrong", true)); private set
    var stepDeg by mutableStateOf(sp.getInt("stepDeg", 5)); private set
    var theme by mutableStateOf(sp.getInt("theme", 0)); private set // 0 system, 1 light, 2 dark
    var keepOn by mutableStateOf(sp.getBoolean("keepOn", true)); private set

    fun setTrueNorth(v: Boolean) { trueNorth = v; sp.edit().putBoolean("trueNorth", v).apply() }
    fun setMetricDistance(v: Boolean) { metricDistance = v; sp.edit().putBoolean("metricDistance", v).apply() }
    fun setMetricAltitude(v: Boolean) { metricAltitude = v; sp.edit().putBoolean("metricAltitude", v).apply() }
    fun setHaptics(v: Boolean) { haptics = v; sp.edit().putBoolean("haptics", v).apply() }
    fun setMajorStrong(v: Boolean) { majorStrong = v; sp.edit().putBoolean("majorStrong", v).apply() }
    fun setStepDeg(v: Int) { stepDeg = v; sp.edit().putInt("stepDeg", v).apply() }
    fun setTheme(v: Int) { theme = v; sp.edit().putInt("theme", v).apply() }
    fun setKeepOn(v: Boolean) { keepOn = v; sp.edit().putBoolean("keepOn", v).apply() }
}

/** Waypoints persisted locally as JSON in SharedPreferences. */
class WaypointStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("waypoints", Context.MODE_PRIVATE)
    val items = mutableStateListOf<Waypoint>()
    var selectedId by mutableStateOf<String?>(sp.getString("selected", null)); private set

    init {
        try {
            val arr = JSONArray(sp.getString("data", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    Waypoint(
                        o.getString("id"),
                        o.getString("name"),
                        o.getDouble("lat"),
                        o.getDouble("lon"),
                        if (o.has("alt") && !o.isNull("alt")) o.getDouble("alt") else null
                    )
                )
            }
        } catch (e: Exception) {
            // corrupted data: start empty
        }
    }

    private fun save() {
        val arr = JSONArray()
        for (w in items) {
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("name", w.name)
                    .put("lat", w.lat)
                    .put("lon", w.lon)
                    .put("alt", w.alt ?: JSONObject.NULL)
            )
        }
        sp.edit().putString("data", arr.toString()).putString("selected", selectedId).apply()
    }

    fun add(name: String, lat: Double, lon: Double, alt: Double?) {
        items.add(Waypoint(UUID.randomUUID().toString(), name, lat, lon, alt))
        save()
    }

    fun update(id: String, name: String, lat: Double, lon: Double) {
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) {
            items[i] = items[i].copy(name = name, lat = lat, lon = lon)
            save()
        }
    }

    fun delete(id: String) {
        items.removeAll { it.id == id }
        if (selectedId == id) selectedId = null
        save()
    }

    fun select(id: String?) {
        selectedId = id
        save()
    }
}

object Geo {
    fun norm360(d: Double): Double = ((d % 360.0) + 360.0) % 360.0
    fun norm360(d: Float): Float = ((d % 360f) + 360f) % 360f

    /** Signed shortest difference a-b in (-180, 180]. */
    fun angleDiff(a: Float, b: Float): Float = (((a - b) % 360f + 540f) % 360f) - 180f

    fun cardinal(deg: Float): String {
        val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return names[(((norm360(deg) + 22.5f) / 45f).toInt()) % 8]
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return norm360(Math.toDegrees(atan2(y, x)))
    }

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371008.8
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(a))
    }

    fun fmtCoord(v: Double, pos: String, neg: String): String =
        String.format(Locale.US, "%.5f\u00B0 %s", abs(v), if (v >= 0) pos else neg)

    fun fmtDist(m: Double, metric: Boolean): String {
        return if (metric) {
            if (m < 1000) "${m.roundToInt()} m" else String.format(Locale.US, "%.2f km", m / 1000.0)
        } else {
            val ft = m * 3.28084
            if (ft < 1000) "${ft.roundToInt()} ft" else String.format(Locale.US, "%.2f mi", m / 1609.344)
        }
    }

    fun fmtAlt(m: Double, metric: Boolean): String =
        if (metric) "${m.roundToInt()} m" else "${(m * 3.28084).roundToInt()} ft"
}
