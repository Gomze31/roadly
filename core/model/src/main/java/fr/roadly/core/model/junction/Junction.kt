package fr.roadly.core.model.junction

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Lane

/**
 * Vue 3D de carrefour (docs/JUNCTION-3D.md). Modèles partagés par le moteur de guidage (core:navigation), qui décide quand et
 * quoi montrer, et le rendu (core:map), qui construit la scène. Toutes les données viennent de l'itinéraire calculé, du profil
 * Valhalla `trace_attributes` du carrefour et des tuiles vectorielles chargées : rien n'est inventé.
 */
enum class JunctionKind {
    /** Sortie d'autoroute ou de voie rapide (bretelle qui quitte la chaussée principale). */
    MOTORWAY_EXIT,
    /** Bifurcation autoroutière : la chaussée se sépare en deux branches de rang voisin. */
    MOTORWAY_FORK,
    /** Échangeur : bretelle vers une autre autoroute, ou plusieurs niveaux (pont / tunnel) dans la fenêtre du carrefour. */
    INTERCHANGE,
    /** Insertion depuis une bretelle. */
    MERGE,
    ROUNDABOUT,
    /** Intersection à plusieurs voies ou à branches nombreuses, hors autoroute. */
    COMPLEX_TURN,
}

/** Étapes d'anticipation, dans l'ordre (la distance de chaque étape dépend de la vitesse, du type de route et de la complexité). */
enum class JunctionPhase {
    /** Préparation : scène construite en arrière-plan, rien n'est encore affiché. */
    PREPARE,
    /** Affichage progressif de la vue 3D. */
    REVEAL,
    /** Guidage renforcé : vue complète, caméra qui descend vers le véhicule. */
    REINFORCE,
    /** Mise en évidence précise des voies et de la trajectoire. */
    PRECISE,
    /** Au niveau de la manœuvre : caméra de poursuite. */
    MANEUVER,
    /** Après la manœuvre : retour progressif à la navigation normale. */
    RECOVER;

    val visible: Boolean get() = this != PREPARE
}

/** Classe de route commune aux deux sources (Valhalla `road_class`, OpenMapTiles `transportation.class`). */
enum class RoadClass(val rank: Int) {
    MOTORWAY(0), TRUNK(1), PRIMARY(2), SECONDARY(3), TERTIARY(4), UNCLASSIFIED(5), RESIDENTIAL(6), SERVICE(7), OTHER(8);

    val isHighway: Boolean get() = this == MOTORWAY || this == TRUNK

    companion object {
        fun fromValhalla(v: String?): RoadClass = when (v) {
            "motorway" -> MOTORWAY
            "trunk" -> TRUNK
            "primary" -> PRIMARY
            "secondary" -> SECONDARY
            "tertiary" -> TERTIARY
            "unclassified" -> UNCLASSIFIED
            "residential" -> RESIDENTIAL
            "service_other", "service" -> SERVICE
            else -> OTHER
        }

        /** OpenMapTiles `transportation.class` (sans le suffixe `_construction`). */
        fun fromOpenMapTiles(v: String?): RoadClass? = when (v) {
            "motorway" -> MOTORWAY
            "trunk" -> TRUNK
            "primary" -> PRIMARY
            "secondary" -> SECONDARY
            "tertiary" -> TERTIARY
            "minor" -> RESIDENTIAL
            "service" -> SERVICE
            "track", "path", "rail", "transit", "ferry", "aerialway", "busway", "bus_guideway", "raceway", "pier", "bridge" -> null
            else -> null
        }
    }
}

/**
 * Branche rencontrée à un nœud de l'itinéraire (`node.intersecting_edges` de `trace_attributes`).
 * [outbound] = on peut quitter le nœud par cette branche (driveability `forward` ou `both`) ; [inbound] = on peut y arriver.
 */
data class NodeBranch(
    val headingDeg: Int,
    val roadClass: RoadClass,
    val isRamp: Boolean,
    val outbound: Boolean,
    val inbound: Boolean,
)

/**
 * Arête Valhalla de la fenêtre du carrefour. [beginIndex] / [endIndex] sont des indices de `Route.geometry` (la forme renvoyée
 * par `edge_walk` est celle envoyée : vérifié sur l'instance publique). [branches] = autres branches au nœud de fin.
 */
data class TopologyEdge(
    val beginIndex: Int,
    val endIndex: Int,
    val lengthM: Double,
    val roadClass: RoadClass,
    val isRamp: Boolean,
    /** `lane_count` Valhalla ; null = inconnu. */
    val laneCount: Int?,
    val roundabout: Boolean,
    val bridge: Boolean,
    val tunnel: Boolean,
    val branches: List<NodeBranch>,
    /** Cap (°) au début de l'arête (`begin_heading`) ; null = inconnu. */
    val beginHeadingDeg: Int? = null,
)

/** Topologie réelle autour d'une manœuvre, issue d'une requête `trace_attributes` sur la fenêtre du carrefour. */
data class JunctionTopology(val routeId: String, val maneuverIndex: Int, val edges: List<TopologyEdge>) {
    /** Arête qui contient l'indice de géométrie [index], ou null. */
    fun edgeAt(index: Int): TopologyEdge? = edges.firstOrNull { index >= it.beginIndex && index < it.endIndex }
}

/** Panneau de direction réel de la manœuvre (`sign` Valhalla) ; absent = aucun panneau n'est dessiné. */
data class JunctionSign(val exitNumber: String?, val toward: List<String>, val branch: String?) {
    val isEmpty: Boolean get() = exitNumber == null && toward.isEmpty() && branch == null
}

/** Sortie d'un rond-point connue par la topologie : [rank] (1 = première), cap de la branche et indice du nœud sur la géométrie. */
data class RoundaboutExit(val rank: Int, val headingDeg: Int, val nodeIndex: Int, val isTarget: Boolean)

/**
 * Rond-point de la vue 3D. [targetExit] = rang à prendre selon Valhalla (`roundabout_exit_count`). [exits] = sorties comptées sur
 * la topologie réelle, de l'entrée à la sortie prise ; [consistent] = le comptage topologique donne le même rang que Valhalla.
 * Si les deux divergent, seule la sortie cible est numérotée (aucun numéro intermédiaire inventé).
 */
data class JunctionRoundabout(
    val targetExit: Int?,
    val exits: List<RoundaboutExit>,
    val consistent: Boolean,
    /** Indices de géométrie de l'entrée sur l'anneau et du nœud de sortie. */
    val entryIndex: Int,
    val exitIndex: Int,
    val exitStreet: String?,
)

/**
 * Carrefour à montrer, publié par le guidage à chaque position. Les distances sont sur la même base que le bandeau
 * (`RouteProgress.distanceToNextManeuverM`).
 */
data class JunctionGuidance(
    val routeId: String,
    val maneuverIndex: Int,
    val kind: JunctionKind,
    val phase: JunctionPhase,
    /** Distance restante jusqu'au point de manœuvre (m) ; négative en [JunctionPhase.RECOVER] (distance parcourue depuis). */
    val distanceM: Double,
    /** Abscisse curviligne du point de manœuvre sur le tracé. */
    val maneuverAlongM: Double,
    /** Abscisse courante du véhicule sur le tracé. */
    val vehicleAlongM: Double,
    /** Indice de géométrie du point de manœuvre. */
    val maneuverIndexInShape: Int,
    /** 0..1 : progression de l'apparition (0 au seuil REVEAL, 1 au seuil REINFORCE) ; 1 ensuite, décroît en RECOVER. */
    val reveal: Float,
    /** Complexité 0..3 (branches, niveaux, voies), qui allonge les distances d'anticipation. */
    val complexity: Int,
    val sign: JunctionSign?,
    val lanes: List<Lane>,
    val roundabout: JunctionRoundabout?,
    /** Topologie Valhalla ; null tant qu'elle n'est pas chargée (ou hors ligne) : la scène se contente du tracé et des tuiles. */
    val topology: JunctionTopology?,
    /** Position imprécise ou extrapolée : la scène ne prétend pas à la précision voie par voie. */
    val degraded: Boolean,
    /** Manœuvre suivante rapprochée enchaînée dans la même vue (sorties successives). */
    val chainedNext: Boolean,
    /** Vitesse lissée (km/h), pour la caméra de la vue. */
    val speedKmh: Double?,
)

/** Route lue dans les tuiles vectorielles chargées (OpenMapTiles `transportation`), dans le rayon du carrefour. */
data class MapRoad(
    val points: List<GeoPoint>,
    val roadClass: RoadClass,
    val isRamp: Boolean,
    val oneway: Boolean,
    val bridge: Boolean,
    val tunnel: Boolean,
    /** OSM `layer` (niveau relatif) ; 0 = sol. */
    val layer: Int,
)

/** Bâtiment des tuiles (OpenMapTiles `building`) : anneau extérieur et hauteurs (m). */
data class MapBuilding(val ring: List<GeoPoint>, val heightM: Double, val minHeightM: Double)

/** Contexte cartographique du carrefour, lu dans les tuiles déjà chargées (fonctionne hors ligne avec les cartes téléchargées). */
data class JunctionMapContext(val center: GeoPoint, val radiusM: Double, val roads: List<MapRoad>, val buildings: List<MapBuilding>) {
    companion object { fun empty(center: GeoPoint) = JunctionMapContext(center, 0.0, emptyList(), emptyList()) }
}
