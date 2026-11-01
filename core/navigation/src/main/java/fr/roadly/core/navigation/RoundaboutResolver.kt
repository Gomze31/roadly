package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.normalizeSignedDeg

/** Rond-point visé (B4), uniquement depuis les données Valhalla : rien n'est inventé, les autres sorties ne sont jamais dessinées. */
data class RoundaboutInfo(
    /** Nombre total de sorties de l'anneau : LIMITE, `/route` ne le fournit pas → toujours null aujourd'hui. */
    val exitCount: Int?,
    /** Rang de la sortie à prendre (`roundabout_exit_count` de l'entrée) ; null = inconnu, aucune valeur inventée. */
    val exitNumber: Int?,
    /** Cap d'arrivée sur l'anneau (`bearing_before` de l'entrée). */
    val entryBearingDeg: Double?,
    /** Cap de sortie (`bearing_after` de la manœuvre de sortie) ; null sans manœuvre de sortie. */
    val exitBearingDeg: Double?,
    /** Tracé de l'anneau parcouru, du début de l'entrée au début de la sortie (inclus). */
    val ringGeometry: List<GeoPoint>,
    /** Rue de sortie (premier `street_names` de la sortie) ; null si absente. */
    val exitStreet: String?,
) {
    /** Angle de sortie signé (−180..180, > 0 = à droite) = `bearing_after(EXIT) − bearing_before(ENTER)`. */
    val exitAngleDeg: Double?
        get() {
            val entry = entryBearingDeg ?: return null
            val exit = exitBearingDeg ?: return null
            return normalizeSignedDeg(exit - entry)
        }
}

/** Calcule les [RoundaboutInfo] d'un itinéraire (Kotlin pur) depuis ENTER (Valhalla 26) et EXIT (27). */
object RoundaboutResolver {
    /** Rond-point dont la manœuvre d'entrée est [enterIndex] ; la sortie est la manœuvre suivante si elle est de type EXIT. */
    fun resolve(route: Route, enterIndex: Int): RoundaboutInfo {
        val ms = route.maneuvers
        val enter = ms[enterIndex]
        val exit = ms.getOrNull(enterIndex + 1)?.takeIf { it.type == ManeuverType.ROUNDABOUT_EXIT }
        val last = route.geometry.lastIndex
        val from = enter.beginShapeIndex.coerceIn(0, last)
        val to = (exit?.beginShapeIndex ?: enter.endShapeIndex ?: ms.getOrNull(enterIndex + 1)?.beginShapeIndex ?: from).coerceIn(from, last)
        return RoundaboutInfo(
            exitCount = null,
            exitNumber = enter.roundaboutExit?.takeIf { it > 0 },
            entryBearingDeg = enter.bearingBeforeDeg?.toDouble(),
            exitBearingDeg = exit?.bearingAfterDeg?.toDouble(),
            ringGeometry = route.geometry.subList(from, to + 1).toList(),
            exitStreet = exit?.streetName,
        )
    }

    /** Tous les ronds-points de [route], indexés par leur manœuvre d'entrée (calculés une fois par tracé). */
    fun resolveAll(route: Route): Map<Int, RoundaboutInfo> =
        route.maneuvers.indices.filter { route.maneuvers[it].type == ManeuverType.ROUNDABOUT_ENTER }.associateWith { resolve(route, it) }

    /**
     * Rond-point à montrer quand la prochaine manœuvre est [nextIndex] : son entrée, ou l'entrée qui la précède (on est dans l'anneau).
     * La cible est figée à l'entrée : l'index de manœuvre est monotone (B1).
     */
    fun current(roundabouts: Map<Int, RoundaboutInfo>, nextIndex: Int): RoundaboutInfo? = roundabouts[nextIndex] ?: roundabouts[nextIndex - 1]
}
