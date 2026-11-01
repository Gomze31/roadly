package fr.roadly.core.scooter.segment

import fr.roadly.core.scooter.regulation.AreaType
import fr.roadly.core.scooter.regulation.LegalAssessment

/** Catégorie de voie empruntée, du point de vue d'un conducteur de trottinette. */
enum class WayCategory(val label: String, val isCycleInfrastructure: Boolean = false, val isDedicated: Boolean = false) {
    CYCLE_TRACK("Piste cyclable", isCycleInfrastructure = true, isDedicated = true),
    CYCLE_LANE("Bande cyclable", isCycleInfrastructure = true),
    GREENWAY("Voie verte", isCycleInfrastructure = true, isDedicated = true),
    LIVING_STREET("Zone de rencontre"),
    SHARED_ROAD("Route partagée"),
    ALLOWED_PATH("Chemin ouvert aux cycles"),
    PATH("Chemin"),
    PEDESTRIAN_ZONE("Aire piétonne"),
    SIDEWALK("Trottoir / voie piétonne"),
    CROSSING("Traversée"),
    STEPS("Escaliers"),
    FERRY("Bac"),
    CONSTRUCTION("Voie en travaux"),
    UNKNOWN("Type de voie inconnu"),
}

/** Qualité du revêtement ; [UNKNOWN] quand aucune donnée n'existe (jamais devinée). */
enum class SurfaceQuality(val label: String) {
    SMOOTH("Revêtement lisse"), GOOD("Revêtement correct"), ROUGH("Pavés / revêtement dégradé"),
    UNPAVED("Non revêtu"), IMPASSABLE("Impraticable"), UNKNOWN("Revêtement inconnu"),
}

/** Provenance d'une valeur : tag OSM de la voie, attribut calculé par Valhalla (parfois déduit du type de voie), ou aucune. */
enum class DataSource { OSM_TAG, VALHALLA, LAW, NONE }

/**
 * Portion homogène d'un itinéraire trottinette, entre [startAlongM] et [endAlongM] (mètres depuis le départ du tracé).
 * Chaque attribut nul ou UNKNOWN signifie « donnée absente ».
 */
data class ScooterRouteSegment(
    val startAlongM: Double,
    val endAlongM: Double,
    val category: WayCategory,
    val roadClass: String?,
    val surface: SurfaceQuality,
    val surfaceSource: DataSource,
    /** Pente moyenne pondérée (%), positive en montée ; null = pas de données d'altitude. */
    val gradePct: Double?,
    val maxUpGradePct: Double?,
    val maxDownGradePct: Double?,
    val speedLimitKmh: Int?,
    val speedLimitSource: DataSource,
    /** Éclairage public : uniquement le tag OSM `lit` (Valhalla ne l'expose pas). */
    val lit: Boolean?,
    val area: AreaType,
    val legal: LegalAssessment,
    val roadworks: Boolean,
    /** Restriction conditionnelle / temporaire présente dans OSM (`*:conditional`). */
    val conditionalRestriction: String?,
    val names: List<String>,
    val wayIds: List<Long>,
    val hasOsmTags: Boolean,
) {
    val lengthM: Double get() = (endAlongM - startAlongM).coerceAtLeast(0.0)
    fun contains(alongM: Double): Boolean = alongM >= startAlongM && alongM < endAlongM
}
