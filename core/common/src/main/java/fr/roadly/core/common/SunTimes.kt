package fr.roadly.core.common

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Élévation solaire approchée (algorithme NOAA simplifié, précision < 1°), suffisant pour le thème automatique. */
object SunTimes {
    fun isNight(lat: Double, lon: Double, epochMs: Long): Boolean = solarElevationDegrees(lat, lon, epochMs) < -0.833

    fun solarElevationDegrees(lat: Double, lon: Double, epochMs: Long): Double {
        val jd = epochMs / 86_400_000.0 + 2_440_587.5
        val n = jd - 2_451_545.0
        val meanLong = (280.460 + 0.9856474 * n).mod(360.0)
        val meanAnom = Math.toRadians((357.528 + 0.9856003 * n).mod(360.0))
        val eclLong = Math.toRadians(meanLong + 1.915 * sin(meanAnom) + 0.020 * sin(2 * meanAnom))
        val obliq = Math.toRadians(23.439 - 0.0000004 * n)
        val ra = atan2(cos(obliq) * sin(eclLong), cos(eclLong))
        val dec = asin(sin(obliq) * sin(eclLong))
        val gmstHours = (18.697374558 + 24.06570982441908 * n).mod(24.0)
        val lst = Math.toRadians((gmstHours * 15 + lon).mod(360.0))
        val hourAngle = lst - ra
        val latR = Math.toRadians(lat)
        val sinElev = sin(latR) * sin(dec) + cos(latR) * cos(dec) * cos(hourAngle)
        return Math.toDegrees(asin(sinElev.coerceIn(-1.0, 1.0)))
    }
}
