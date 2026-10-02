package com.meridian.compass.astronomy

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class CelestialPosition(
    val azimuthDegrees: Double,   // 0..360, compass bearing to the body
    val elevationDegrees: Double, // above horizon (+) / below (-)
    val isVisible: Boolean        // elevation > 0
)

data class SunInfo(
    val position: CelestialPosition,
    val sunriseMillis: Long?,
    val sunsetMillis: Long?
)

data class MoonInfo(
    val position: CelestialPosition,
    val moonriseMillis: Long?,
    val moonsetMillis: Long?,
    val illuminationFraction: Double, // 0..1
    val phaseName: String
)

/**
 * Low/medium-precision solar & lunar position calculator (good to a fraction of a degree for the
 * sun and roughly a degree for the moon -- more than sufficient for a hand compass overlay).
 * Based on standard NOAA / Jean Meeus approximate algorithms.
 */
object SunMoonCalculator {

    private fun toRad(deg: Double) = deg * PI / 180.0
    private fun toDeg(rad: Double) = rad * 180.0 / PI

    private fun julianDate(timeMillis: Long): Double {
        return timeMillis / 86400000.0 + 2440587.5
    }

    fun sunInfo(latitude: Double, longitude: Double, timeMillis: Long, timeZone: TimeZone): SunInfo {
        val pos = sunPosition(latitude, longitude, timeMillis)
        val (rise, set) = sunRiseSet(latitude, longitude, timeMillis, timeZone)
        return SunInfo(pos, rise, set)
    }

    fun moonInfo(latitude: Double, longitude: Double, timeMillis: Long, timeZone: TimeZone): MoonInfo {
        val pos = moonPosition(latitude, longitude, timeMillis)
        val illum = moonIllumination(timeMillis)
        val (rise, set) = moonRiseSet(latitude, longitude, timeMillis, timeZone)
        return MoonInfo(pos, rise, set, illum.first, illum.second)
    }

    // ---- Sun position (NOAA simplified algorithm) ----
    private fun sunPosition(lat: Double, lon: Double, timeMillis: Long): CelestialPosition {
        val jd = julianDate(timeMillis)
        val d = jd - 2451545.0 // days since J2000.0

        val meanLongitude = normalizeDeg(280.460 + 0.9856474 * d)
        val meanAnomaly = toRad(normalizeDeg(357.528 + 0.9856003 * d))
        val eclipticLongitude = toRad(
            meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly)
        )
        val obliquity = toRad(23.439 - 0.0000004 * d)

        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
        val declination = asin(sin(obliquity) * sin(eclipticLongitude))

        return horizontalCoordinates(rightAscension, declination, lat, lon, jd)
    }

    // ---- Moon position (simplified low-precision lunar theory) ----
    private fun moonPosition(lat: Double, lon: Double, timeMillis: Long): CelestialPosition {
        val jd = julianDate(timeMillis)
        val d = jd - 2451545.0

        val L = normalizeDeg(218.316 + 13.176396 * d)         // mean longitude
        val M = normalizeDeg(134.963 + 13.064993 * d)         // mean anomaly
        val F = normalizeDeg(93.272 + 13.229350 * d)          // mean distance from ascending node

        val lonEcliptic = toRad(L + 6.289 * sin(toRad(M)))
        val latEcliptic = toRad(5.128 * sin(toRad(F)))
        val obliquity = toRad(23.439 - 0.0000004 * d)

        val rightAscension = atan2(
            sin(lonEcliptic) * cos(obliquity) - kotlin.math.tan(latEcliptic) * sin(obliquity),
            cos(lonEcliptic)
        )
        val declination = asin(
            sin(latEcliptic) * cos(obliquity) + cos(latEcliptic) * sin(obliquity) * sin(lonEcliptic)
        )

        return horizontalCoordinates(rightAscension, declination, lat, lon, jd)
    }

    private fun horizontalCoordinates(ra: Double, dec: Double, lat: Double, lon: Double, jd: Double): CelestialPosition {
        val t = (jd - 2451545.0) / 36525.0
        var gmstHours = 6.697374558 + 0.06570982441908 * (jd - 2451545.0) +
            1.00273790935 * ((jd - floor(jd) - 0.5) * 24.0)
        gmstHours = ((gmstHours % 24.0) + 24.0) % 24.0
        val lstHours = gmstHours + lon / 15.0
        val lstDeg = normalizeDeg(lstHours * 15.0)

        val hourAngle = toRad(lstDeg - toDeg(ra))
        val latRad = toRad(lat)

        val elevation = asin(sin(dec) * sin(latRad) + cos(dec) * cos(latRad) * cos(hourAngle))
        var azimuth = atan2(-sin(hourAngle), cos(hourAngle) * sin(latRad) - kotlin.math.tan(dec) * cos(latRad))
        var azDeg = normalizeDeg(toDeg(azimuth))

        return CelestialPosition(
            azimuthDegrees = azDeg,
            elevationDegrees = toDeg(elevation),
            isVisible = toDeg(elevation) > -0.5
        )
    }

    private fun normalizeDeg(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    // ---- Sunrise / sunset (hour-angle method, standard -0.833 deg altitude for atmospheric refraction) ----
    private fun sunRiseSet(lat: Double, lon: Double, timeMillis: Long, tz: TimeZone): Pair<Long?, Long?> {
        return riseSetGeneric(lat, lon, timeMillis, tz, altitudeDeg = -0.833) { jd -> sunDeclinationAndEqTime(jd) }
    }

    private fun moonRiseSet(lat: Double, lon: Double, timeMillis: Long, tz: TimeZone): Pair<Long?, Long?> {
        // Approximate using the moon's declination at local noon; adequate for a compass-app overlay.
        val cal = Calendar.getInstance(tz).apply { timeInMillis = timeMillis }
        cal.set(Calendar.HOUR_OF_DAY, 12); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val jdNoon = julianDate(cal.timeInMillis)
        val moonPos = moonPosition(lat, lon, cal.timeInMillis)
        val decRad = toRad(90 - (90 - moonPos.elevationDegrees)) // not exact; fallback approach below is more robust

        // Simple scan across the day at 5-minute resolution to find horizon crossings.
        var prevElev: Double? = null
        var riseMillis: Long? = null
        var setMillis: Long? = null
        val dayStart = Calendar.getInstance(tz).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        var t = dayStart
        val stepMs = 5 * 60 * 1000L
        repeat(288) {
            val elev = moonPosition(lat, lon, t).elevationDegrees
            if (prevElev != null) {
                if (prevElev!! < 0 && elev >= 0 && riseMillis == null) riseMillis = t
                if (prevElev!! >= 0 && elev < 0 && setMillis == null) setMillis = t
            }
            prevElev = elev
            t += stepMs
        }
        return riseMillis to setMillis
    }

    private fun sunDeclinationAndEqTime(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val meanLongitude = normalizeDeg(280.460 + 0.9856474 * d)
        val meanAnomaly = toRad(normalizeDeg(357.528 + 0.9856003 * d))
        val eclipticLongitude = toRad(meanLongitude + 1.915 * sin(meanAnomaly) + 0.020 * sin(2 * meanAnomaly))
        val obliquity = toRad(23.439 - 0.0000004 * d)
        val declination = asin(sin(obliquity) * sin(eclipticLongitude))
        // Equation of time in minutes (approx)
        val y = kotlin.math.tan(obliquity / 2).let { it * it }
        val eqTime = 4 * toDeg(
            y * sin(2 * toRad(meanLongitude)) - 2 * 0.0167 * sin(meanAnomaly) +
                4 * 0.0167 * y * sin(meanAnomaly) * cos(2 * toRad(meanLongitude)) -
                0.5 * y * y * sin(4 * toRad(meanLongitude)) - 1.25 * 0.0167 * 0.0167 * sin(2 * meanAnomaly)
        )
        return declination to eqTime
    }

    private fun riseSetGeneric(
        lat: Double, lon: Double, timeMillis: Long, tz: TimeZone, altitudeDeg: Double,
        decAndEqTime: (Double) -> Pair<Double, Double>
    ): Pair<Long?, Long?> {
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val jdNoon = julianDate(cal.timeInMillis)
        val (declination, eqTimeMinutes) = decAndEqTime(jdNoon)
        val latRad = toRad(lat)

        val cosHourAngle = (sin(toRad(altitudeDeg)) - sin(latRad) * sin(declination)) /
            (cos(latRad) * cos(declination))

        if (cosHourAngle > 1.0 || cosHourAngle < -1.0) {
            // Sun/moon never rises or never sets on this day at this latitude (polar day/night).
            return null to null
        }
        val hourAngleDeg = toDeg(acos(cosHourAngle))

        val solarNoonMinutesUtc = 720 - 4 * lon - eqTimeMinutes
        val sunriseMinutesUtc = solarNoonMinutesUtc - 4 * hourAngleDeg
        val sunsetMinutesUtc = solarNoonMinutesUtc + 4 * hourAngleDeg

        val dayStartUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val rise = dayStartUtc + (sunriseMinutesUtc * 60000).toLong()
        val set = dayStartUtc + (sunsetMinutesUtc * 60000).toLong()
        return rise to set
    }

    /** Returns (illuminated fraction 0..1, human phase name). */
    private fun moonIllumination(timeMillis: Long): Pair<Double, String> {
        val jd = julianDate(timeMillis)
        val d = jd - 2451545.0

        val sunMeanAnomaly = toRad(normalizeDeg(357.529 + 0.98560028 * d))
        val moonMeanLongitude = normalizeDeg(218.316 + 13.176396 * d)
        val moonMeanAnomaly = toRad(normalizeDeg(134.963 + 13.064993 * d))
        val moonArgLatitude = toRad(normalizeDeg(93.272 + 13.229350 * d))

        val sunLongitude = normalizeDeg(280.460 + 0.9856474 * d + 1.915 * sin(sunMeanAnomaly))
        val moonLongitude = normalizeDeg(moonMeanLongitude + 6.289 * sin(moonMeanAnomaly))

        val phaseAngle = normalizeDeg(moonLongitude - sunLongitude)
        val illuminatedFraction = (1 - cos(toRad(phaseAngle))) / 2.0

        val phaseName = when {
            phaseAngle < 8 || phaseAngle >= 352 -> "New Moon"
            phaseAngle < 90 -> "Waxing Crescent"
            phaseAngle < 98 -> "First Quarter"
            phaseAngle < 172 -> "Waxing Gibbous"
            phaseAngle < 188 -> "Full Moon"
            phaseAngle < 262 -> "Waning Gibbous"
            phaseAngle < 278 -> "Last Quarter"
            else -> "Waning Crescent"
        }
        return illuminatedFraction to phaseName
    }
}
