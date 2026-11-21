package fr.roadly.core.navigation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.location.GpxTrack
import fr.roadly.core.location.LocationReplay
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Trajet simulé le long d'un itinéraire (Kotlin pur) : une position par [periodMs], à la vitesse que Valhalla prévoit pour chaque
 * manœuvre (longueur / durée), avec une accélération et un freinage bornés. Le véhicule ralentit donc avant un rond-point ou une
 * bretelle, comme sur la route : les seuils d'annonce liés à la vitesse sont exercés de façon réaliste.
 */
object RouteSimulation {
    private const val MIN_MPS = 3.0
    private const val MAX_MPS = 38.0
    private const val ACCEL = 2.0
    private const val BRAKE = 2.5
    private const val ACCURACY_M = 5f
    /** Positions immobiles émises à l'arrivée, pour que l'arrivée soit détectée et dite. */
    private const val ARRIVAL_FIXES = 4

    fun fixes(route: Route, periodMs: Long = 1_000L, fromAlongM: Double = 0.0): List<LocationFix> {
        val g = RouteGeometry(route.geometry)
        if (g.points.size < 2 || g.totalM <= 0.0) return emptyList()
        val starts = route.maneuvers.map { g.cumulative[it.beginShapeIndex.coerceIn(0, g.cumulative.lastIndex)] }
        val cruise = route.maneuvers.map { m -> if (m.durationSeconds > 0.0 && m.lengthMeters > 0.0) (m.lengthMeters / m.durationSeconds).coerceIn(MIN_MPS, MAX_MPS) else MIN_MPS }
        val dt = periodMs / 1000.0
        val out = ArrayList<LocationFix>()
        var along = fromAlongM.coerceIn(0.0, g.totalM)
        var v = 0.0
        var t = 0L
        var seg = 0
        while (along < g.totalM) {
            val k = starts.indexOfLast { it <= along }.coerceAtLeast(0)
            val nextStart = starts.getOrNull(k + 1)
            val nextCruise = cruise.getOrNull(k + 1) ?: 0.0
            // Vitesse permise pour pouvoir être à la vitesse du tronçon suivant (ou à l'arrêt) à son début.
            val toNext = (nextStart ?: g.totalM) - along
            val brakeLimit = sqrt(nextCruise * nextCruise + 2 * BRAKE * toNext.coerceAtLeast(0.0))
            val target = min(cruise[k], brakeLimit).coerceAtLeast(MIN_MPS)
            v = if (target > v) min(target, v + ACCEL * dt) else maxOf(target, v - BRAKE * dt)
            while (seg < g.cumulative.lastIndex - 1 && g.cumulative[seg + 1] <= along) seg++
            val a = g.points[seg]; val b = g.points[seg + 1]
            val len = g.cumulative[seg + 1] - g.cumulative[seg]
            val f = if (len > 0.0) ((along - g.cumulative[seg]) / len).coerceIn(0.0, 1.0) else 0.0
            val p = fr.roadly.core.model.GeoPoint(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
            out += LocationFix(p, bearing(a, b).toFloat(), v.toFloat(), ACCURACY_M, t)
            along += v * dt
            t += periodMs
        }
        val end = g.points.last()
        val heading = out.lastOrNull()?.bearingDeg
        repeat(ARRIVAL_FIXES) { out += LocationFix(end, heading, 0f, ACCURACY_M, t); t += periodMs }
        return out
    }
}

/** Source de positions de test pour une course : trajet simulé ou trace rejouée. Sans effet par défaut. */
interface DriveSimulation {
    /** Démarre le rejeu pour [route] si la simulation est activée ; rend vrai s'il a démarré. */
    suspend fun start(route: Route): Boolean
    fun stop()
}

object NoDriveSimulation : DriveSimulation {
    override suspend fun start(route: Route): Boolean = false
    override fun stop() = Unit
}

/**
 * Simulation réelle : réglage « Simulation de trajet » activé → au départ d'un guidage, les positions viennent de
 * `Android/data/<paquet>/files/replay.gpx` s'il existe (trace enregistrée, déposée par `adb push`), sinon d'un trajet simulé
 * le long de l'itinéraire ([RouteSimulation]). Le GPS réel reprend la main à l'arrêt du guidage.
 */
@Singleton
class ReplayDriveSimulation @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val replay: LocationReplay,
    private val prefs: UserPreferencesRepository,
) : DriveSimulation {
    override suspend fun start(route: Route): Boolean {
        if (!prefs.simulateDrive.first()) return false
        val track = gpxTrack() ?: RouteSimulation.fixes(route)
        if (track.isEmpty()) return false
        replay.play(track, prefs.simulationSpeed.first().toDouble())
        return true
    }

    override fun stop() = replay.stop()

    private fun gpxTrack(): List<LocationFix>? = try {
        File(ctx.getExternalFilesDir(null), GPX_FILE).takeIf { it.isFile && it.length() in 1..MAX_GPX_BYTES }?.readText()?.let(GpxTrack::parse)?.takeIf { it.size >= 2 }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val GPX_FILE = "replay.gpx"
        private const val MAX_GPX_BYTES = 20L * 1024 * 1024
    }
}
