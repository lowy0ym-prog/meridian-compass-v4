package com.meridian.compass

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Offline sun/moon calculations (low-precision astronomical formulas, good to well under a degree
 * for the sun and roughly a degree for the moon; rise/set to about a minute or two).
 * Azimuths are returned in degrees clockwise from TRUE north.
 */
object Astro {
    data class Body(val azimuth: Double, val altitude: Double)
    data class MoonIllum(val fraction: Double, val phase: Double)

    private const val RAD = PI / 180.0
    private const val DAY_MS = 86400000.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private const val J0 = 0.0009
    private const val OBLIQ = RAD * 23.4397

    private fun toDays(t: Long): Double = t / DAY_MS - 0.5 + J1970 - J2000
    private fun ra(l: Double, b: Double) = atan2(sin(l) * cos(OBLIQ) - tan(b) * sin(OBLIQ), cos(l))
    private fun dec(l: Double, b: Double) = asin(sin(b) * cos(OBLIQ) + cos(b) * sin(OBLIQ) * sin(l))
    private fun azRad(h: Double, phi: Double, d: Double) = atan2(sin(h), cos(h) * sin(phi) - tan(d) * cos(phi))
    private fun altRad(h: Double, phi: Double, d: Double) = asin(sin(phi) * sin(d) + cos(phi) * cos(d) * cos(h))
    private fun sidereal(d: Double, lw: Double) = RAD * (280.16 + 360.9856235 * d) - lw
    private fun solarM(d: Double) = RAD * (357.5291 + 0.98560028 * d)

    private fun eclLong(m: Double): Double {
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        val p = RAD * 102.9372
        return m + c + p + PI
    }

    private fun refraction(h0: Double): Double {
        val h = if (h0 < 0) 0.0 else h0
        return 0.0002967 / tan(h + 0.00312536 / (h + 0.08901179))
    }

    private fun fromJulian(j: Double): Long = ((j + 0.5 - J1970) * DAY_MS).toLong()

    private fun toBearing(azFromSouth: Double): Double = ((azFromSouth / RAD + 180.0) % 360.0 + 360.0) % 360.0

    fun sun(t: Long, lat: Double, lon: Double): Body {
        val lw = RAD * -lon
        val phi = RAD * lat
        val d = toDays(t)
        val l = eclLong(solarM(d))
        val dc = dec(l, 0.0)
        val h = sidereal(d, lw) - ra(l, 0.0)
        return Body(toBearing(azRad(h, phi, dc)), altRad(h, phi, dc) / RAD)
    }

    private fun moonCoords(d: Double): Triple<Double, Double, Double> {
        val l = RAD * (218.316 + 13.176396 * d)
        val m = RAD * (134.963 + 13.064993 * d)
        val f = RAD * (93.272 + 13.229350 * d)
        val lon = l + RAD * 6.289 * sin(m)
        val b = RAD * 5.128 * sin(f)
        val dist = 385001.0 - 20905.0 * cos(m)
        return Triple(ra(lon, b), dec(lon, b), dist)
    }

    fun moon(t: Long, lat: Double, lon: Double): Body {
        val lw = RAD * -lon
        val phi = RAD * lat
        val d = toDays(t)
        val (r, dc, _) = moonCoords(d)
        val h = sidereal(d, lw) - r
        var alt = altRad(h, phi, dc)
        alt += refraction(alt)
        return Body(toBearing(azRad(h, phi, dc)), alt / RAD)
    }

    fun moonIllum(t: Long): MoonIllum {
        val d = toDays(t)
        val sl = eclLong(solarM(d))
        val sDec = dec(sl, 0.0)
        val sRa = ra(sl, 0.0)
        val (mRa, mDec, mDist) = moonCoords(d)
        val sDist = 149598000.0
        val phi = acos(sin(sDec) * sin(mDec) + cos(sDec) * cos(mDec) * cos(sRa - mRa))
        val inc = atan2(sDist * sin(phi), mDist - sDist * cos(phi))
        val angle = atan2(
            cos(sDec) * sin(sRa - mRa),
            sin(sDec) * cos(mDec) - cos(sDec) * sin(mDec) * cos(sRa - mRa)
        )
        val fraction = (1 + cos(inc)) / 2
        val phase = 0.5 + 0.5 * inc * (if (angle < 0) -1 else 1) / PI
        return MoonIllum(fraction, phase)
    }

    fun phaseName(p: Double): String = when {
        p < 0.03 || p >= 0.97 -> "New Moon"
        p < 0.22 -> "Waxing Crescent"
        p < 0.28 -> "First Quarter"
        p < 0.47 -> "Waxing Gibbous"
        p < 0.53 -> "Full Moon"
        p < 0.72 -> "Waning Gibbous"
        p < 0.78 -> "Last Quarter"
        else -> "Waning Crescent"
    }

    /** Sunrise and sunset (epoch ms) for the solar day nearest [t]; null values at polar day/night. */
    fun sunTimes(t: Long, lat: Double, lon: Double): Pair<Long?, Long?> {
        val lw = RAD * -lon
        val phi = RAD * lat
        val d = toDays(t)
        val n = Math.round(d - J0 - lw / (2 * PI)).toDouble()
        val ds = J0 + (0 + lw) / (2 * PI) + n
        val m = solarM(ds)
        val l = eclLong(m)
        val dc = dec(l, 0.0)
        val jNoon = J2000 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)
        val h0 = -0.833 * RAD
        val w = acos((sin(h0) - sin(phi) * sin(dc)) / (cos(phi) * cos(dc)))
        if (w.isNaN()) return Pair(null, null)
        val a = J0 + (w + lw) / (2 * PI) + n
        val jSet = J2000 + a + 0.0053 * sin(m) - 0.0069 * sin(2 * l)
        val jRise = jNoon - (jSet - jNoon)
        return Pair(fromJulian(jRise), fromJulian(jSet))
    }

    /** Moonrise and moonset (epoch ms) within the local calendar day of [t]; either may be null. */
    fun moonTimes(t: Long, lat: Double, lon: Double): Pair<Long?, Long?> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = t
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val hc = 0.133 * RAD
        fun hAt(hours: Double): Double =
            moon(start + (hours * 3600000.0).toLong(), lat, lon).altitude * RAD - hc

        var h0 = hAt(0.0)
        var rise: Double? = null
        var set: Double? = null
        var i = 1
        while (i <= 24) {
            val h1 = hAt(i.toDouble())
            val h2 = hAt(i + 1.0)
            val a = (h0 + h2) / 2 - h1
            val b = (h2 - h0) / 2
            val xe = -b / (2 * a)
            val ye = (a * xe + b) * xe + h1
            val disc = b * b - 4 * a * h1
            var roots = 0
            var x1 = 0.0
            var x2 = 0.0
            if (disc >= 0) {
                val dx = sqrt(disc) / (abs(a) * 2)
                x1 = xe - dx
                x2 = xe + dx
                if (abs(x1) <= 1) roots++
                if (abs(x2) <= 1) roots++
                if (x1 < -1) x1 = x2
            }
            if (roots == 1) {
                if (h0 < 0) rise = i + x1 else set = i + x1
            } else if (roots == 2) {
                rise = i + (if (ye < 0) x2 else x1)
                set = i + (if (ye < 0) x1 else x2)
            }
            if (rise != null && set != null) break
            h0 = h2
            i += 2
        }
        return Pair(
            rise?.let { start + (it * 3600000.0).toLong() },
            set?.let { start + (it * 3600000.0).toLong() }
        )
    }
}
