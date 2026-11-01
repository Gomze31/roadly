package fr.roadly.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {
    private val fr = Locale.FRANCE
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    fun distance(meters: Double): String = when {
        meters < 1000 -> "${(meters / 10).roundToInt() * 10} m"
        meters < 10_000 -> String.format(fr, "%.1f km", meters / 1000)
        else -> "${(meters / 1000).roundToInt()} km"
    }

    /** Table d'arrondi commune à la voix et à l'écran (B2) : < 100 m → 10 m près (min. 10) ; < 1 km → 50 m près ; au-delà → 100 m près. */
    fun roundManeuverMeters(meters: Double): Int = when {
        meters < 100.0 -> ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10)
        meters < 1000.0 -> (meters / 50.0).roundToInt() * 50
        else -> (meters / 100.0).roundToInt() * 100
    }

    /** Distance à une manœuvre (bandeau, Horizon, notification), même arrondi que la voix : « 450 m », « 1,2 km ». */
    fun maneuverDistance(meters: Double): String {
        val r = roundManeuverMeters(meters)
        return if (r < 1000) "$r m" else String.format(fr, "%.1f km", r / 1000.0)
    }

    fun duration(seconds: Double): String {
        val totalMin = (seconds / 60).roundToInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h == 0) "$m min" else "$h h ${m.toString().padStart(2, '0')}"
    }

    fun euros(amount: Double): String = String.format(fr, "%.2f €", amount)

    fun clockTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        clock.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
