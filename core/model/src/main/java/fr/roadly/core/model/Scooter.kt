package fr.roadly.core.model

/**
 * Mode de déplacement actif (sélecteur de véhicules). Chaque mode a son propre profil et ses propres préférences :
 * [CAR] et [MOTORCYCLE] utilisent le [VehicleProfile] existant, [ELECTRIC_SCOOTER] un [ElectricScooterProfile].
 * [BICYCLE] est prévu par l'architecture (même costing Valhalla que la trottinette) mais pas encore proposé.
 */
enum class TravelMode(val available: Boolean = true) {
    CAR, MOTORCYCLE, ELECTRIC_SCOOTER, BICYCLE(available = false);

    companion object {
        /** Mode par défaut quand aucun choix n'a été mémorisé : déduit du profil véhicule existant (aucune régression). */
        fun derivedFrom(profile: VehicleProfile?): TravelMode = if (profile?.type == VehicleType.MOTORCYCLE) MOTORCYCLE else CAR
    }
}

/**
 * Profil véhicule réellement transmis au routage motorisé pour [mode] : le profil saisi n'est jamais modifié,
 * seul son type est forcé pour la durée du calcul (moto ↔ voiture). Null pour un mode non motorisé.
 */
fun TravelMode.motorVehicle(profile: VehicleProfile?): VehicleProfile? = when (this) {
    TravelMode.CAR -> profile?.let { if (it.type == VehicleType.MOTORCYCLE) it.copy(type = VehicleType.SEDAN) else it }
    TravelMode.MOTORCYCLE -> profile?.copy(type = VehicleType.MOTORCYCLE)
        ?: VehicleProfile(VehicleType.MOTORCYCLE, FuelType.SP95, consumptionPer100 = 0.0, fuelPriceEur = null)
    TravelMode.ELECTRIC_SCOOTER, TravelMode.BICYCLE -> null
}

enum class TireType { PNEUMATIC_TUBELESS, PNEUMATIC_TUBE, SOLID, HONEYCOMB, UNKNOWN }
enum class SuspensionType { NONE, FRONT, REAR, DUAL, UNKNOWN }
/** Confort ressenti déclaré par l'utilisateur ; UNKNOWN = déduit des pneus et de la suspension. */
enum class ComfortLevel { LOW, MEDIUM, HIGH, UNKNOWN }

/**
 * Fiche d'une trottinette électrique. Toutes les caractéristiques sont modifiables ; null = inconnue (jamais inventée).
 * Les valeurs commerciales ([claimedRangeKm]) sont affichées comme telles et ne servent pas d'autonomie réelle.
 */
data class ElectricScooterProfile(
    val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val model: String? = null,
    /** Vitesse maximale autorisée / bridée (km/h). France : 25 km/h par construction (R311-1). */
    val maxSpeedKmh: Double = 25.0,
    /** Vitesse de croisière habituelle (km/h) ; null = 80 % de [maxSpeedKmh]. */
    val cruiseSpeedKmh: Double? = null,
    val claimedRangeKm: Double? = null,
    /** Conditions du constructeur pour [claimedRangeKm], p. ex. « 16 km/h, 75 kg, revêtement plat ». */
    val claimedRangeConditions: String? = null,
    val batteryWh: Double? = null,
    val batteryVoltage: Double? = null,
    val scooterWeightKg: Double? = null,
    val riderWeightKg: Double? = null,
    val maxLoadKg: Double? = null,
    val wheelSizeInch: Double? = null,
    val tireType: TireType = TireType.UNKNOWN,
    val suspension: SuspensionType = SuspensionType.UNKNOWN,
    val comfort: ComfortLevel = ComfortLevel.UNKNOWN,
    /** Pente maximale annoncée (%) ; null = inconnue. */
    val maxGradePct: Double? = null,
    val motorRatedW: Double? = null,
    val hasRegenerativeBraking: Boolean? = null,
    /** Provenance des caractéristiques (URL ou mention « saisie utilisateur »). */
    val specSource: String? = null,
) {
    val effectiveCruiseKmh: Double get() = (cruiseSpeedKmh ?: maxSpeedKmh * 0.8).coerceIn(5.0, maxSpeedKmh)
}

/** Objectif principal d'un itinéraire trottinette. */
enum class ScooterRouteGoal { FASTEST, SAFEST, COMFORT, SHORTEST }

/** Préférences propres au mode trottinette : stockées à part, elles ne touchent jamais à [RoutePreferences]. */
data class ScooterRoutePreferences(
    val goal: ScooterRouteGoal = ScooterRouteGoal.SAFEST,
    val preferCycleways: Boolean = true,
    val avoidBusyRoads: Boolean = true,
    val avoidUnpaved: Boolean = true,
    val avoidSteepHills: Boolean = false,
    val avoidRoughSurfaces: Boolean = true,
)

/** Demande d'itinéraire trottinette portée par [RouteRequest.scooter] ; [goal] = variante calculée. */
data class ScooterRouteRequest(
    val profile: ElectricScooterProfile,
    val preferences: ScooterRoutePreferences,
    val goal: ScooterRouteGoal = preferences.goal,
)
