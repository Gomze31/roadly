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
