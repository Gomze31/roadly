package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import java.util.Locale

/** Portion de tracé couverte par une zone de danger, en abscisses curvilignes (m) ; [inside] = zone traversée. */
data class DangerZoneSpan(val startAlongM: Double, val endAlongM: Double, val inside: Boolean)

/**
 * Zones de danger peintes sur le tracé : une ligne par zone, découpée dans la géométrie de l'itinéraire. Seule l'étendue de la zone
 * est dessinée, jamais l'emplacement de l'équipement (sa position dans la zone varie d'un équipement à l'autre).
 */
object DangerZoneFeatures {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
    const val AHEAD_COLOR = "#FFB020"
    const val INSIDE_COLOR = "#FF4D4F"
    const val CASING = "#3A1A00"

    fun build(geometry: List<GeoPoint>, zones: List<DangerZoneSpan>): String {
        if (geometry.size < 2 || zones.isEmpty()) return EMPTY
        val cumulative = DoubleArray(geometry.size)
        for (i in 1 until geometry.size) cumulative[i] = cumulative[i - 1] + geometry[i - 1].distanceTo(geometry[i])
        val features = zones.mapNotNull { z ->
            val line = slice(geometry, cumulative, z.startAlongM, z.endAlongM)
            if (line.size < 2) return@mapNotNull null
            val coords = line.joinToString(",") { String.format(Locale.ROOT, "[%.6f,%.6f]", it.lon, it.lat) }
            """{"type":"Feature","properties":{"state":"${if (z.inside) "inside" else "ahead"}"},"geometry":{"type":"LineString","coordinates":[$coords]}}"""
        }
        if (features.isEmpty()) return EMPTY
        return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    }

    /** Points du tracé entre [from] et [to] (m), extrémités interpolées ; vide si l'intervalle sort du tracé ou est nul. */
    internal fun slice(geometry: List<GeoPoint>, cumulative: DoubleArray, from: Double, to: Double): List<GeoPoint> {
        val total = cumulative.last()
        val a = from.coerceIn(0.0, total)
        val b = to.coerceIn(0.0, total)
        if (b - a < MIN_LENGTH_M) return emptyList()
        val out = ArrayList<GeoPoint>()
        out += pointAt(geometry, cumulative, a)
        for (i in geometry.indices) if (cumulative[i] > a && cumulative[i] < b) out += geometry[i]
        out += pointAt(geometry, cumulative, b)
        return out
    }

    private fun pointAt(geometry: List<GeoPoint>, cumulative: DoubleArray, along: Double): GeoPoint {
        var i = cumulative.indexOfFirst { it >= along }
        if (i <= 0) return geometry[if (i == 0) 0 else geometry.lastIndex]
        val segment = cumulative[i] - cumulative[i - 1]
        val t = if (segment <= 0.0) 0.0 else (along - cumulative[i - 1]) / segment
        val p = geometry[i - 1]; val q = geometry[i]
        return GeoPoint(p.lat + (q.lat - p.lat) * t, p.lon + (q.lon - p.lon) * t)
    }

    private const val MIN_LENGTH_M = 1.0
}

object DangerZoneSpans {
    /** Au-delà, les zones ne sont pas peintes (lisibilité, coût du GeoJSON sur un long trajet). */
    const val HORIZON_M = 30_000.0

    /**
     * Zones de danger du tracé encore devant [alongM] (ou traversées), dans l'horizon, entières (la partie déjà parcourue est
     * recouverte par le calque du tracé parcouru : la liste ne change qu'à l'entrée et à la sortie, pas à chaque position) ; [activeKey] = zone ouverte par la machine
     * à états ; [hidden] = zones masquées par l'utilisateur (fausses alertes), retirées tout de suite sans attendre un recalcul.
     */
    fun from(hazards: List<fr.roadly.core.model.RouteHazard>, alongM: Double, activeKey: String?, hidden: Set<String> = emptySet()): List<DangerZoneSpan> =
        hazards.asSequence()
            .filter { it.kind == fr.roadly.core.model.HazardKind.VIGILANCE_ZONE && it.endAlongM > alongM && it.startAlongM < alongM + HORIZON_M }
            .filter { it.key == null || it.key !in hidden }
            .map { DangerZoneSpan(it.startAlongM, it.endAlongM, (activeKey != null && it.key == activeKey) || it.contains(alongM)) }
            .toList()
}
