package fr.roadly.core.data.zfe

import fr.roadly.core.model.CritAir
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.VehicleType
import java.time.LocalDate

/** Catégories de véhicules de la Base nationale des ZFE (schéma etalab/schema-zfe). */
enum class ZfeVehicleClass(val key: String) {
    CAR("vp"), VAN("vul"), TRUCK("pl"), MOTO("deux_rm"), BUS("autobus_autocars");

    companion object {
        fun of(type: VehicleType?): ZfeVehicleClass = when (type) {
            VehicleType.VAN -> VAN
            VehicleType.TRUCK -> TRUCK
            VehicleType.MOTORCYCLE -> MOTO
            else -> CAR
        }
    }
}

/** Polygone : contour extérieur et trous éventuels. */
data class ZfePolygon(val outer: List<GeoPoint>, val holes: List<List<GeoPoint>> = emptyList())

/**
 * Une réglementation ZFE (une ligne de `aires.geojson`) : périmètre, période de validité, vignette interdite par catégorie
 * (« V4 » = Crit'Air 4, 5 et non classés interdits) et horaires au format `opening_hours` d'OpenStreetMap.
 */
data class ZfeRule(
    val id: String,
    val name: String,
    val start: LocalDate?,
    val end: LocalDate?,
    val restriction: Map<ZfeVehicleClass, String?>,
    val hours: Map<ZfeVehicleClass, String?>,
    val decreeUrl: String?,
    val infoUrl: String?,
    val polygons: List<ZfePolygon>,
) {
    val minLat = polygons.minOf { p -> p.outer.minOf { it.lat } }
    val maxLat = polygons.maxOf { p -> p.outer.maxOf { it.lat } }
    val minLon = polygons.minOf { p -> p.outer.minOf { it.lon } }
    val maxLon = polygons.maxOf { p -> p.outer.maxOf { it.lon } }

    fun activeOn(date: LocalDate): Boolean = (start == null || !date.isBefore(start)) && (end == null || !date.isAfter(end))
}

/** Verdict pour un véhicule dans une ZFE. */
enum class ZfeVerdict {
    /** Aucune restriction pour cette catégorie. */
    NO_RESTRICTION,
    /** Vignette autorisée. */
    ALLOWED,
    /** Vignette interdite, et la restriction s'applique à l'heure du passage. */
    FORBIDDEN,
    /** Vignette interdite, mais pas à l'heure du passage (restriction horaire). */
    FORBIDDEN_OTHER_HOURS,
    /** Restriction en vigueur, vignette du véhicule non renseignée. */
    CRITAIR_UNKNOWN,
}

/** Zone traversée par un itinéraire, avec le verdict le plus strict des réglementations actives qui la couvrent. */
data class ZfeCrossing(val rule: ZfeRule, val verdict: ZfeVerdict, val restriction: String?, val hours: String?)

/** Rang des vignettes dans l'ordre du schéma : EL, V1, V2, V3, V4, V5, NC. */
internal fun restrictionRank(code: String?): Int? = when (code?.trim()?.uppercase()) {
    "EL" -> 0; "V1" -> 1; "V2" -> 2; "V3" -> 3; "V4" -> 4; "V5" -> 5; "NC" -> 6
    else -> null
}

internal fun CritAir.rank(): Int = when (this) {
    CritAir.ZERO -> 0; CritAir.C1 -> 1; CritAir.C2 -> 2; CritAir.C3 -> 3; CritAir.C4 -> 4; CritAir.C5 -> 5; CritAir.NONE -> 6
}

/** Libellé lisible d'un code de restriction (« V4 » → « Crit'Air 4 »). */
fun zfeRestrictionLabel(code: String?): String? = when (val r = restrictionRank(code)) {
    null -> null
    0 -> "électrique"
    6 -> "non classé"
    else -> "Crit'Air $r"
}
