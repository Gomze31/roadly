package fr.roadly.core.scooter.routing

import fr.roadly.core.model.ComfortLevel
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.ScooterRouteRequest
import fr.roadly.core.model.SuspensionType
import fr.roadly.core.model.TireType
import fr.roadly.core.scooter.regulation.ScooterRegulation
import fr.roadly.core.scooter.regulation.ScooterRegulations

/** Sensibilité aux revêtements dégradés, déduite des pneus, de la suspension, du diamètre des roues et du confort déclaré. */
enum class SurfaceSensitivity { LOW, MEDIUM, HIGH }

/**
 * Profil de routage trottinette. Valhalla n'a pas de costing « EDPM » : on utilise le costing `bicycle`
 * (accès cycles, pistes et bandes connues de Valhalla) paramétré par le profil et les préférences, puis chaque
 * itinéraire est vérifié segment par segment (`ScooterSegmentClassifier`) : routes > 50 km/h, escaliers,
 * trottoirs, restrictions OSM. Options et bornes : https://valhalla.github.io/valhalla/api/route/api-reference/
 */
object ScooterRoutingProfile {
    const val COSTING = "bicycle"

    /** Fortement dissuasif (défaut Valhalla 8) : un escalier n'est franchissable qu'en portant la trottinette. */
    const val STEPS_FACTOR = 1000.0

    /** Ordre par défaut des variantes proposées : l'objectif choisi d'abord, puis les autres, 3 au total. */
    fun variantGoals(selected: ScooterRouteGoal, count: Int = 3): List<ScooterRouteGoal> =
        (listOf(selected) + listOf(ScooterRouteGoal.SAFEST, ScooterRouteGoal.FASTEST, ScooterRouteGoal.COMFORT, ScooterRouteGoal.SHORTEST))
            .distinct().take(count)

    fun sensitivity(p: ElectricScooterProfile): SurfaceSensitivity {
        var score = 0
        score += when (p.tireType) {
            TireType.SOLID, TireType.HONEYCOMB -> 2
            TireType.UNKNOWN -> 1
            else -> 0
        }
        score += when (p.suspension) {
            SuspensionType.NONE -> 1
            SuspensionType.UNKNOWN -> 0
            else -> 0
        }
        val wheel = p.wheelSizeInch
        if (wheel != null && wheel <= 8.5) score += 1
        score += when (p.comfort) {
            ComfortLevel.LOW -> 1
            ComfortLevel.HIGH -> -1
            else -> 0
        }
        return when {
            score >= 3 -> SurfaceSensitivity.HIGH
            score >= 1 -> SurfaceSensitivity.MEDIUM
            else -> SurfaceSensitivity.LOW
        }
    }

    /** Vitesse de croisière transmise à Valhalla (`cycling_speed`), bornée par la réglementation et la plage Valhalla 5–60. */
    fun cyclingSpeedKmh(p: ElectricScooterProfile, regulation: ScooterRegulation = ScooterRegulations.DEFAULT): Double =
        minOf(p.effectiveCruiseKmh, p.maxSpeedKmh, regulation.maxSpeedKmh).coerceIn(5.0, 60.0)

    /**
     * `costing_options.bicycle` pour [req]. Les poids restent dans [0, 1] ; `avoid_bad_surfaces` n'atteint jamais 1
     * (Valhalla interdirait alors totalement ces voies et pourrait ne plus trouver d'itinéraire).
     */
    fun costingOptions(req: ScooterRouteRequest, regulation: ScooterRegulation = ScooterRegulations.DEFAULT): Map<String, Any> {
        val prefs = req.preferences
        val sens = sensitivity(req.profile)
        var useRoads = when (req.goal) {
            ScooterRouteGoal.FASTEST -> 0.4
            ScooterRouteGoal.SAFEST -> 0.05
            ScooterRouteGoal.COMFORT -> 0.15
            ScooterRouteGoal.SHORTEST -> 0.25
        }
        if (prefs.preferCycleways) useRoads = minOf(useRoads, 0.15)
        if (prefs.avoidBusyRoads) useRoads = minOf(useRoads, 0.1)
        var useHills = when (req.goal) {
            ScooterRouteGoal.FASTEST, ScooterRouteGoal.SHORTEST -> 0.5
            ScooterRouteGoal.SAFEST -> 0.25
            ScooterRouteGoal.COMFORT -> 0.1
        }
        if (prefs.avoidSteepHills) useHills = minOf(useHills, 0.05)
        var badSurfaces = when (sens) {
            SurfaceSensitivity.LOW -> 0.25
            SurfaceSensitivity.MEDIUM -> 0.5
            SurfaceSensitivity.HIGH -> 0.75
        }
        if (req.goal == ScooterRouteGoal.COMFORT) badSurfaces += 0.2
        if (prefs.avoidUnpaved) badSurfaces = maxOf(badSurfaces, 0.6)
        if (prefs.avoidRoughSurfaces) badSurfaces = maxOf(badSurfaces, 0.75)
        badSurfaces = badSurfaces.coerceIn(0.0, 0.95)
        // « road » écarte les revêtements les plus mauvais : réservé aux petites roues / pneus pleins quand on évite le non revêtu.
        val bicycleType = if (sens == SurfaceSensitivity.HIGH && prefs.avoidUnpaved) "road" else "hybrid"
        return buildMap {
            put("bicycle_type", bicycleType)
            put("cycling_speed", cyclingSpeedKmh(req.profile, regulation))
            put("use_roads", useRoads)
            put("use_hills", useHills)
            put("avoid_bad_surfaces", badSurfaces)
            put("use_ferry", 0.2)
            put("use_living_streets", 0.5)
            put("steps_factor", STEPS_FACTOR)
            if (req.goal == ScooterRouteGoal.SHORTEST) put("shortest", true)
        }
    }
}
