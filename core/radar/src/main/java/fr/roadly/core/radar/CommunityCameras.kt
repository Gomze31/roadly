package fr.roadly.core.radar

import fr.roadly.core.model.GeoPoint

/**
 * Équipement signalé par des conducteurs (MapAtlas Camera Index, CC BY 4.0). [approachDeg] = sens de circulation indiqué
 * (N, NE… → cap), null si absent ou « both » ; [limitKmh] déjà filtré sur les vitesses réglementaires.
 */
data class CommunityCamera(val ref: String, val point: GeoPoint, val kind: RadarKind, val limitKmh: Int?, val approachDeg: Double?)

/** Bilan d'une fusion avec les signalements communautaires. */
data class CommunityReport(val read: Int, val corroborated: Int, val directionHints: Int, val added: Int, val ignored: Int)

/**
 * Fusion des signalements communautaires avec la liste officielle. Mesures du 2026-11-05 sur l'instantané MapAtlas du
 * 21/05/2024 (3 853 points en France) : 3 072 à moins de 100 m d'un équipement officiel (médiane 11 m), lettre de sens
 * conforme au jeu 2018 dans 37 cas sur 42, cap `facingDeg` conforme dans ≈ 55 % des cas seulement (non utilisé), « both »
 * contredit par le jeu 2018 dans 30 % des cas (non utilisé).
 *
 * - équipement officiel apparié (≤ [MATCH_M], catégorie compatible) : confiance + [CORROBORATION_BONUS] ; sens indicatif
 *   ([ControlledDirection.hint]) s'il n'en a pas ;
 * - point à plus de [UNMATCHED_MIN_M] de tout équipement officiel : équipement communautaire, confiance [COMMUNITY_CONFIDENCE]
 *   (alerte visuelle seulement), proposé seulement si l'utilisateur l'active ;
 * - entre les deux : ignoré (ni appariement sûr, ni équipement distinct).
 */
object CommunityMerge {
    const val MATCH_M = 50.0
    const val UNMATCHED_MIN_M = 100.0
    const val CORROBORATION_BONUS = 5
    const val COMMUNITY_CONFIDENCE = 45
    const val ID_PREFIX = "MA-"

    data class Result(val radars: List<Radar>, val report: CommunityReport)

    fun merge(official: List<Radar>, cameras: List<CommunityCamera>, collectedAtMs: Long, snapshot: String?): Result {
        val index = RadarIndex(official)
        val best = HashMap<String, Pair<CommunityCamera, Double>>()
        val added = ArrayList<Radar>()
        var ignored = 0
        for (cam in cameras) {
            val near = index.near(cam.point, UNMATCHED_MIN_M)
            val match = near.filter { compatible(it.kind, cam.kind) }
                .map { it to it.point.distanceTo(cam.point) }
                .filter { it.second <= MATCH_M }
                .minByOrNull { it.second }
            when {
                match != null -> {
                    val prev = best[match.first.id]
                    // Plusieurs signalements près du même équipement : le plus proche l'emporte.
                    if (prev == null || prev.second > match.second) best[match.first.id] = cam to match.second
                }
                near.isEmpty() -> added += communityRadar(cam, collectedAtMs, snapshot)
                else -> ignored++
            }
        }
        var hints = 0
        val merged = official.map { r ->
            val cam = best[r.id]?.first ?: return@map r
            val d = r.direction
            val unknownDirection = d == null || (!d.bothWays && d.bearingDeg == null)
            val dir = if (unknownDirection && cam.approachDeg != null) {
                hints++
                ControlledDirection(cam.approachDeg, false, d?.label ?: "MapAtlas", hint = true)
            } else d
            r.copy(
                direction = dir,
                confidence = (r.confidence + CORROBORATION_BONUS).coerceAtMost(100),
                enrichedFrom = listOfNotNull(r.enrichedFrom, RadarSourceIds.MAPATLAS).joinToString(","),
            )
        }
        return Result(merged + added, CommunityReport(cameras.size, best.size, hints, added.size, ignored))
    }

    private fun communityRadar(cam: CommunityCamera, collectedAtMs: Long, snapshot: String?) = Radar(
        id = ID_PREFIX + cam.ref,
        point = cam.point,
        kind = cam.kind,
        speedLimitKmh = cam.limitKmh,
        direction = cam.approachDeg?.let { ControlledDirection(it, false, "MapAtlas", hint = true) },
        source = RadarSourceIds.MAPATLAS,
        sourceVersion = snapshot,
        collectedAtMs = collectedAtMs,
        precisionM = 30.0,
        confidence = COMMUNITY_CONFIDENCE,
    )

    /** Classe MapAtlas → catégorie : « signal » = feu rouge, « corridor » = tronçon, « spot » / « adaptive » = vitesse. */
    fun kindOf(mapAtlasClass: String?): RadarKind = when (mapAtlasClass) {
        "signal" -> RadarKind.RED_LIGHT
        "corridor" -> RadarKind.SECTION
        "spot", "adaptive" -> RadarKind.FIXED
        else -> RadarKind.UNKNOWN
    }

    /** Lettre de sens MapAtlas (N, NE…) → cap ; « both » ou inconnue → null. */
    fun approachDeg(letter: String?): Double? = when (letter?.uppercase()) {
        "N" -> 0.0
        "NE" -> 45.0
        "E" -> 90.0
        "SE" -> 135.0
        "S" -> 180.0
        "SW" -> 225.0
        "W" -> 270.0
        "NW" -> 315.0
        else -> null
    }

    private fun compatible(official: RadarKind, community: RadarKind): Boolean = when (community) {
        RadarKind.RED_LIGHT -> official == RadarKind.RED_LIGHT
        RadarKind.SECTION -> official == RadarKind.SECTION
        RadarKind.FIXED -> official.controlsSpeed
        else -> false
    }
}
