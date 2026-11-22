package fr.roadly.core.model

data class LocationFix(
    val point: GeoPoint,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val accuracyM: Float,
    /** Heure du fix (epoch ms, horloge GNSS ou de l'appareil) : heure calendaire, jamais comparée à une durée. */
    val timestampMs: Long,
    /**
     * Instant du fix sur l'horloge monotone de l'appareil (`Location.elapsedRealtimeNanos`, en ms) : seule base valable pour l'âge
     * d'un fix et l'écart entre deux fixes. Null quand la source ne le fournit pas (rejeu, doublures de test).
     */
    val elapsedRealtimeMs: Long? = null,
) {
    /** Instant du fix pour un calcul de durée : monotone s'il est connu, sinon [timestampMs]. */
    val durationBaseMs: Long get() = elapsedRealtimeMs ?: timestampMs
}
