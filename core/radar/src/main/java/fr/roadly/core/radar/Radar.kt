package fr.roadly.core.radar

import fr.roadly.core.model.GeoPoint

/**
 * Catégorie d'équipement (codes du jeu « Liste des radars fixes en France », Ministère de l'Intérieur / DSR).
 * [code] = code officiel ; null pour les catégories hors jeu officiel.
 */
enum class RadarKind(val code: String?, val controlsSpeed: Boolean) {
    /** ETF : radar vitesse fixe classique. */
    FIXED("ETF", true),
    /** ETD : discriminant (seuils VL / PL). */
    DISCRIMINATING("ETD", true),
    /** ETT : radar « tourelle » de nouvelle génération (vitesse ou franchissement, souvent double sens). */
    NEW_GENERATION("ETT", true),
    /** ETU : nouvelle génération en milieu urbain. */
    URBAN("ETU", true),
    /** ETVM : vitesse moyenne (tronçon). */
    SECTION("ETVM", true),
    /** ETFR : franchissement de feu rouge. */
    RED_LIGHT("ETFR", false),
    /** ETPN : passage à niveau. */
    LEVEL_CROSSING("ETPN", false),
    UNKNOWN(null, false);

    companion object {
        fun fromCode(code: String?): RadarKind = entries.firstOrNull { it.code != null && it.code.equals(code?.trim(), ignoreCase = true) } ?: UNKNOWN

        /** Libellés du jeu 2018 (« Radar fixe », « Radar feu rouge »…). */
        fun fromLabel2018(label: String?): RadarKind? = when (label?.trim()?.lowercase()) {
            "radar fixe" -> FIXED
            "radar discriminant" -> DISCRIMINATING
            "radar feu rouge" -> RED_LIGHT
            "radar vitesse moyenne" -> SECTION
            "radar passage à niveau", "radar passage a niveau" -> LEVEL_CROSSING
            else -> null
        }
    }
}

/**
 * Sens contrôlé. [bearingDeg] = cap de circulation contrôlé (0 = vers le nord) quand il est déductible ; [bothWays] = double sens.
 * [label] = texte source (« Nord vers Sud », « PARIS vers LYON ») conservé tel quel.
 * [hint] = sens indicatif d'une source communautaire (MapAtlas, juste dans ≈ 88 % des cas mesurés) : il peut avancer une alerte
 * quand le cap concorde, jamais écarter un équipement (pas de radar manqué si l'indication est fausse).
 */
data class ControlledDirection(val bearingDeg: Double?, val bothWays: Boolean, val label: String?, val hint: Boolean = false) {
    val isKnownOneWay: Boolean get() = !bothWays && bearingDeg != null && !hint
}

enum class RadarStatus { ACTIVE, REMOVED }

/**
 * Un équipement, avec sa provenance. Aucun champ inconnu n'est rempli : null = la source ne le dit pas.
 * [precisionM] = incertitude de position déduite du nombre de décimales publiées (et dégradée pour les coordonnées
 * partagées par plusieurs radars) ; [confidence] 0–100 (voir [RadarQuality]).
 */
data class Radar(
    val id: String,
    val point: GeoPoint,
    val kind: RadarKind,
    val speedLimitKmh: Int? = null,
    val heavySpeedLimitKmh: Int? = null,
    val direction: ControlledDirection? = null,
    val road: String? = null,
    val commune: String? = null,
    val department: String? = null,
    val country: String = "FR",
    val sectionLengthM: Double? = null,
    /** Date de mise en service telle que publiée (« 31/10/2011 »). */
    val commissionedAt: String? = null,
    val source: String,
    val sourceVersion: String? = null,
    /** Jeu ayant fourni sens, route, commune, longueur de tronçon ; null = aucun enrichissement. */
    val enrichedFrom: String? = null,
    val collectedAtMs: Long,
    val verifiedAtMs: Long? = null,
    val precisionM: Double,
    val confidence: Int,
    val status: RadarStatus = RadarStatus.ACTIVE,
)

/** Identifiants et licences des sources (affichés dans les réglages, voir `docs/RADAR-SOURCES.md`). */
object RadarSourceIds {
    const val FIXED_FR = "dsr-radars-fixes"
    const val AUTOMATIQUES_2018 = "radars-automatiques-2018"
    const val ATTRIBUTION_FR = "Ministère de l'Intérieur – Délégation à la sécurité routière, data.gouv.fr, Licence Ouverte 2.0"
    /** MapAtlas Camera Index (signalements de conducteurs, CC BY 4.0). */
    const val MAPATLAS = "mapatlas-camera-index"
    const val ATTRIBUTION_MAPATLAS = "MapAtlas, https://mapatlas.eu (CC BY 4.0)"
}
