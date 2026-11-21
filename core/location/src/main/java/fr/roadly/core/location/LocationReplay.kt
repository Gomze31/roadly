package fr.roadly.core.location

import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rejeu de positions à la place du GPS (docs/VALIDATION-TELEPHONE.md). Tant qu'un rejeu est actif, `LocationRepository.fixes` émet
 * les positions rejouées : tout le guidage (suivi, voix, écran, Android Auto) se comporte comme sur la route, sans rouler.
 * Sert à reproduire un problème à l'identique après chaque modification. Jamais actif sans le réglage « Simulation de trajet ».
 */
@Singleton
class LocationReplay @Inject constructor(@param:ApplicationScope private val scope: CoroutineScope) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    private val _fixes = MutableSharedFlow<LocationFix>(replay = 1, extraBufferCapacity = 16)
    val fixes: Flow<LocationFix> = _fixes
    private var job: Job? = null

    /**
     * Rejoue [track] (horodatages relatifs, croissants) en temps réel accéléré de [speedFactor]. Chaque position est émise avec
     * l'heure courante. La dernière position reste la position courante jusqu'à [stop].
     */
    fun play(track: List<LocationFix>, speedFactor: Double = 1.0, nowMs: () -> Long = System::currentTimeMillis) {
        job?.cancel()
        if (track.isEmpty()) { _active.value = false; return }
        _active.value = true
        val factor = speedFactor.coerceIn(0.25, 8.0)
        job = scope.launch {
            var previous = track.first().timestampMs
            for (fix in track) {
                delay(((fix.timestampMs - previous) / factor).toLong().coerceIn(0L, MAX_GAP_MS))
                previous = fix.timestampMs
                _fixes.emit(fix.copy(timestampMs = nowMs(), speedMps = fix.speedMps?.let { (it * factor).toFloat() }))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _active.value = false
        _fixes.resetReplayCache()
    }

    private companion object {
        /** Un trou de la trace (tunnel, pause) n'est jamais rejoué plus longtemps que ceci. */
        const val MAX_GAP_MS = 30_000L
    }
}

/** Lecture d'une trace GPX (points `trkpt`, sinon `rtept`) en positions rejouables (Kotlin pur). */
object GpxTrack {
    private val point = Regex("""<(trkpt|rtept)\b([^>]*?)(/>|>(.*?)</\1>)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val lat = Regex("""\blat\s*=\s*["']([-0-9.]+)["']""")
    private val lon = Regex("""\blon\s*=\s*["']([-0-9.]+)["']""")
    private val time = Regex("""<time>\s*([^<\s]+)\s*</time>""", RegexOption.IGNORE_CASE)
    private val hdop = Regex("""<hdop>\s*([0-9.]+)\s*</hdop>""", RegexOption.IGNORE_CASE)
    private val speed = Regex("""<(?:\w+:)?speed>\s*([0-9.]+)\s*</(?:\w+:)?speed>""", RegexOption.IGNORE_CASE)

    /** Précision annoncée par défaut (m) et par unité de HDOP. */
    private const val DEFAULT_ACCURACY_M = 5f
    private const val HDOP_TO_M = 5f

    /**
     * Positions de la trace, horodatées en millisecondes depuis le premier point. Sans balise `<time>`, un point par seconde.
     * Vitesse : balise `<speed>` (m/s) si présente, sinon distance / durée entre deux points. Cap : direction du segment.
     * Les points invalides ou qui reculent dans le temps sont ignorés.
     */
    fun parse(gpx: String): List<LocationFix> {
        data class Raw(val p: GeoPoint, val t: Long?, val accuracy: Float, val speed: Float?)
        val raws = point.findAll(gpx).mapNotNull { m ->
            val attrs = m.groupValues[2]
            val body = m.groupValues[4]
            val la = lat.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val lo = lon.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
            if (la !in -90.0..90.0 || lo !in -180.0..180.0) return@mapNotNull null
            val t = time.find(body)?.groupValues?.get(1)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            val acc = hdop.find(body)?.groupValues?.get(1)?.toFloatOrNull()?.let { it * HDOP_TO_M } ?: DEFAULT_ACCURACY_M
            Raw(GeoPoint(la, lo), t, acc, speed.find(body)?.groupValues?.get(1)?.toFloatOrNull())
        }.toList()
        if (raws.isEmpty()) return emptyList()
        val timed = raws.all { it.t != null }
        val out = ArrayList<LocationFix>(raws.size)
        var lastT = -1L
        val t0 = raws.first().t ?: 0L
        for ((i, r) in raws.withIndex()) {
            val t = if (timed) r.t!! - t0 else i * 1_000L
            if (t <= lastT) continue
            val previous = out.lastOrNull()
            val dtS = if (previous == null) 0.0 else (t - previous.timestampMs) / 1000.0
            val dist = previous?.point?.distanceTo(r.p) ?: 0.0
            val v = r.speed ?: if (dtS > 0.0) (dist / dtS).toFloat() else null
            val bearing = if (previous != null && dist >= 1.0) bearing(previous.point, r.p) else previous?.bearingDeg
            out += LocationFix(r.p, bearing, v, r.accuracy, t)
            lastT = t
        }
        // Le premier point n'a ni cap ni vitesse mesurés : il prend ceux du deuxième.
        if (out.size >= 2) out[0] = out[0].copy(bearingDeg = out[1].bearingDeg, speedMps = out[1].speedMps)
        return out
    }

    /**
     * Écrit [fixes] en GPX 1.1 (horodatages relatifs ajoutés à [startEpochMs], vitesse et précision conservées). [description] est
     * écrite dans les métadonnées : une trace fabriquée doit s'y déclarer comme telle, pour ne jamais passer pour un enregistrement.
     */
    fun write(fixes: List<LocationFix>, name: String, description: String, startEpochMs: Long = 1_767_225_600_000L): String = buildString {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Roadly\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <metadata><name>").append(esc(name)).append("</name><desc>").append(esc(description)).append("</desc></metadata>\n")
        append("  <trk><name>").append(esc(name)).append("</name><trkseg>\n")
        for (f in fixes) {
            append("    <trkpt lat=\"").append(String.format(java.util.Locale.ROOT, "%.7f", f.point.lat))
            append("\" lon=\"").append(String.format(java.util.Locale.ROOT, "%.7f", f.point.lon)).append("\">")
            append("<time>").append(Instant.ofEpochMilli(startEpochMs + f.timestampMs).toString()).append("</time>")
            append("<hdop>").append(String.format(java.util.Locale.ROOT, "%.1f", f.accuracyM / HDOP_TO_M)).append("</hdop>")
            f.speedMps?.let { append("<speed>").append(String.format(java.util.Locale.ROOT, "%.2f", it)).append("</speed>") }
            append("</trkpt>\n")
        }
        append("  </trkseg></trk>\n</gpx>\n")
    }

    private fun bearing(a: GeoPoint, b: GeoPoint): Float {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
