package fr.roadly.core.scooter.regulation

import fr.roadly.core.scooter.segment.WayCategory

/** Situation par rapport à l'agglomération, déduite uniquement des tags OSM (`maxspeed:type`, `source:maxspeed`, `zone:maxspeed`). */
enum class AreaType { URBAN, RURAL, UNKNOWN }

/**
 * Statut réglementaire d'une portion. [ALLOWED] signifie « autorisé d'après la règle nationale et les données
 * cartographiques disponibles » — jamais une garantie légale. [NOT_RIDABLE] : passage à pied (escaliers, pied à terre).
 */
enum class LegalStatus(val label: String) {
    ALLOWED("Autorisé d'après les données"),
    FORBIDDEN("Interdit"),
    NOT_RIDABLE("À pied"),
    UNVERIFIED("Non vérifiable"),
}

data class LegalAssessment(val status: LegalStatus, val reason: String)

/** Faits connus sur une portion, tels qu'établis par le classificateur ; null = inconnu. */
data class SegmentFacts(
    val category: WayCategory,
    val roadClass: String?,
    val speedLimitKmh: Int?,
    val area: AreaType,
    /** Valeur du tag OSM `small_electric_vehicle` (trottinettes électriques et gyropodes), ou null. */
    val smallElectricVehicleAccess: String?,
    val bicycleAccess: String?,
    val generalAccess: String?,
    val motorroad: Boolean,
)

/** Règles d'un pays pour les engins de déplacement personnel motorisés. */
interface ScooterRegulation {
    val countryCode: String
    val displayName: String
    /** Vitesse maximale par construction (km/h). */
    val maxSpeedKmh: Double
    val references: List<String>
    fun assess(f: SegmentFacts): LegalAssessment
}

/** Registre par code pays ISO 3166-1 alpha-2 ; la France est le pays par défaut. */
object ScooterRegulations {
    val DEFAULT: ScooterRegulation = FranceEdpmRegulation
    private val byCountry: Map<String, ScooterRegulation> = mapOf("FR" to FranceEdpmRegulation)

    /** Pays connu → ses règles ; pays sans règles intégrées → tout reste « non vérifiable » sauf les interdictions explicites. */
    fun forCountry(code: String?): ScooterRegulation {
        if (code.isNullOrBlank()) return DEFAULT
        return byCountry[code.uppercase()] ?: UnsupportedCountryRegulation(code.uppercase())
    }
}

/** Règles communes à tous les pays : tags OSM explicites, autoroutes, escaliers, travaux. Null = rien de décisif. */
internal fun explicitAssessment(f: SegmentFacts): LegalAssessment? {
    val sev = f.smallElectricVehicleAccess
    if (sev == "no") return LegalAssessment(LegalStatus.FORBIDDEN, "Interdit aux trottinettes électriques (OSM small_electric_vehicle=no)")
    if (f.category == WayCategory.STEPS) return LegalAssessment(LegalStatus.NOT_RIDABLE, "Escaliers : trottinette à porter")
    if (f.bicycleAccess == "dismount") return LegalAssessment(LegalStatus.NOT_RIDABLE, "Pied à terre obligatoire (OSM bicycle=dismount)")
    if (f.category == WayCategory.CONSTRUCTION) return LegalAssessment(LegalStatus.FORBIDDEN, "Voie en travaux, fermée à la circulation (OSM)")
    if (f.roadClass == "motorway") return LegalAssessment(LegalStatus.FORBIDDEN, "Autoroute : interdite")
    if (f.motorroad) return LegalAssessment(LegalStatus.FORBIDDEN, "Route express (OSM motorroad=yes) : interdite")
    if (sev == "yes" || sev == "designated" || sev == "permissive") {
        return LegalAssessment(LegalStatus.ALLOWED, "Accès des trottinettes électriques indiqué dans OSM (small_electric_vehicle=$sev)")
    }
    if ((f.generalAccess == "no" || f.generalAccess == "private") && f.bicycleAccess !in setOf("yes", "designated", "permissive")) {
        return LegalAssessment(LegalStatus.FORBIDDEN, "Accès interdit ou privé (OSM access=${f.generalAccess})")
    }
    return null
}

/** Pays sans règles intégrées : aucune autorisation n'est affirmée. */
class UnsupportedCountryRegulation(override val countryCode: String) : ScooterRegulation {
    override val displayName = "Pays $countryCode (règles non intégrées)"
    override val maxSpeedKmh = 25.0
    override val references = emptyList<String>()
    override fun assess(f: SegmentFacts): LegalAssessment = explicitAssessment(f)
        ?: LegalAssessment(LegalStatus.UNVERIFIED, "Réglementation $countryCode non intégrée à Roadly : à vérifier localement")
}

/**
 * France — engins de déplacement personnel motorisés (Code de la route R311-1, R412-43-1 en vigueur depuis le
 * 1er septembre 2023, décret 2023-848) :
 * - en agglomération : pistes et bandes cyclables ; à défaut, routes limitées à 50 km/h au plus, aires piétonnes
 *   à l'allure du pas, accotements revêtus ;
 * - hors agglomération : interdit, sauf voies vertes et pistes cyclables (dérogation possible de l'autorité de police
 *   sur des routes ≤ 80 km/h : non vérifiable par Roadly) ;
 * - trottoirs interdits sauf autorisation locale ; 25 km/h maximum par construction.
 */
object FranceEdpmRegulation : ScooterRegulation {
    override val countryCode = "FR"
    override val displayName = "France (EDPM motorisés)"
    override val maxSpeedKmh = 25.0
    override val references = listOf(
        "Code de la route, art. R412-43-1 : https://www.legifrance.gouv.fr/codes/article_lc/LEGIARTI000048035783",
        "Code de la route, art. R311-1 (définition EDPM, 25 km/h)",
        "Décret n° 2023-848 du 31 août 2023 (âge minimal 14 ans)",
        "Sécurité routière : https://www.securite-routiere.gouv.fr/reglementation-liee-aux-modes-de-deplacements/reglementation-des-edpm",
    )

    override fun assess(f: SegmentFacts): LegalAssessment {
        explicitAssessment(f)?.let { return it }
        return when (f.category) {
            WayCategory.CYCLE_TRACK -> LegalAssessment(LegalStatus.ALLOWED, "Piste cyclable : autorisée (R412-43-1), aucune restriction dans les données")
            WayCategory.GREENWAY -> LegalAssessment(LegalStatus.ALLOWED, "Voie verte : autorisée (R412-43-1), aucune restriction dans les données")
            WayCategory.CYCLE_LANE -> when (f.area) {
                AreaType.URBAN -> LegalAssessment(LegalStatus.ALLOWED, "Bande cyclable en agglomération : autorisée (R412-43-1)")
                AreaType.RURAL -> LegalAssessment(LegalStatus.FORBIDDEN, "Hors agglomération, seules les voies vertes et pistes cyclables sont autorisées")
                AreaType.UNKNOWN -> LegalAssessment(LegalStatus.UNVERIFIED, "Bande cyclable : agglomération non indiquée dans les données")
            }
            WayCategory.LIVING_STREET ->
                if (f.area == AreaType.RURAL) LegalAssessment(LegalStatus.FORBIDDEN, "Hors agglomération : interdit sur la chaussée")
                else LegalAssessment(LegalStatus.ALLOWED, "Zone de rencontre (20 km/h) : autorisée en l'absence de piste ou bande")
            WayCategory.SHARED_ROAD -> roadAssessment(f)
            WayCategory.PEDESTRIAN_ZONE ->
                if (f.area == AreaType.RURAL) LegalAssessment(LegalStatus.FORBIDDEN, "Hors agglomération : interdit")
                else LegalAssessment(LegalStatus.ALLOWED, "Aire piétonne : autorisée à l'allure du pas, sans gêner les piétons (R431-9)")
            WayCategory.SIDEWALK -> LegalAssessment(LegalStatus.FORBIDDEN, "Trottoir / voie piétonne : interdit en roulant, sauf autorisation locale")
            WayCategory.CROSSING -> LegalAssessment(LegalStatus.UNVERIFIED, "Traversée piétonne : sans passage cyclable, traversez à pied")
            WayCategory.ALLOWED_PATH -> LegalAssessment(LegalStatus.UNVERIFIED, "Chemin ouvert aux cycles : statut EDPM non défini par les données")
            WayCategory.PATH -> LegalAssessment(LegalStatus.UNVERIFIED, "Chemin : ni piste cyclable ni voie verte d'après les données")
            WayCategory.FERRY -> LegalAssessment(LegalStatus.UNVERIFIED, "Bac : conditions d'embarquement à vérifier")
            WayCategory.UNKNOWN -> LegalAssessment(LegalStatus.UNVERIFIED, "Type de voie inconnu")
            WayCategory.STEPS, WayCategory.CONSTRUCTION -> error("traité par explicitAssessment")
        }
    }

    private fun roadAssessment(f: SegmentFacts): LegalAssessment {
        val limit = f.speedLimitKmh
        if (f.area == AreaType.RURAL) {
            return LegalAssessment(LegalStatus.FORBIDDEN, "Route hors agglomération : interdite (sauf arrêté local, non vérifiable)")
        }
        if (limit != null && limit > 50) {
            return LegalAssessment(LegalStatus.FORBIDDEN, "Route limitée à $limit km/h : interdite (maximum 50 km/h en agglomération)")
        }
        if (limit == null) return LegalAssessment(LegalStatus.UNVERIFIED, "Limitation de vitesse inconnue")
        return when (f.area) {
            AreaType.URBAN -> LegalAssessment(LegalStatus.ALLOWED, "Route en agglomération limitée à $limit km/h : autorisée en l'absence de piste ou bande")
            else -> LegalAssessment(LegalStatus.UNVERIFIED, "Route limitée à $limit km/h, mais l'agglomération n'est pas indiquée dans les données")
        }
    }
}
