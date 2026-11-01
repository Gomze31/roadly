package fr.roadly.core.scooter.guidance

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.scooter.analysis.ScooterWarningKind
import fr.roadly.core.scooter.segment.WayCategory
import java.util.Locale
import kotlin.math.roundToInt

/** Palier d'annonce d'une manœuvre (miroir des paliers du guidage commun). */
enum class AnnouncementStage { FAR, NEAR, NOW }

/**
 * Phrases du guidage trottinette : courtes, anticipées, et jamais d'incitation à traverser sans signalisation.
 * Elles ne sont produites que lorsque la donnée qui les justifie existe ; sinon le texte Valhalla est conservé.
 */
object ScooterPhrases {
    /** Seuils (m) des paliers à vitesse de trottinette (≤ 25 km/h) : loin, proche, maintenant. */
    const val FAR_M = 200.0
    const val NEAR_M = 80.0
    const val NOW_M = 20.0

    const val INSUFFICIENT_RANGE = "Votre autonomie estimée devient insuffisante pour rejoindre la destination."
    const val TIGHT_RANGE = "Votre autonomie estimée devient juste pour rejoindre la destination."
    const val CROSSING = "Au prochain carrefour, traversez en suivant l'itinéraire et en respectant la signalisation."

    fun distance(meters: Double): String {
        val rounded = if (meters < 100.0) ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10) else (meters / 50.0).roundToInt() * 50
        if (rounded < 1000) return "$rounded mètres"
        val km = (meters / 100.0).roundToInt() / 10.0
        val text = if (km % 1.0 == 0.0) km.toInt().toString() else String.format(Locale.FRANCE, "%.1f", km)
        return "$text ${if (km < 2.0) "kilomètre" else "kilomètres"}"
    }

    private fun side(type: ManeuverType): String? = when (type) {
        ManeuverType.TURN_RIGHT, ManeuverType.SLIGHT_RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.KEEP_RIGHT -> "à droite"
        ManeuverType.TURN_LEFT, ManeuverType.SLIGHT_LEFT, ManeuverType.SHARP_LEFT, ManeuverType.KEEP_LEFT -> "à gauche"
        else -> null
    }

    private fun article(c: WayCategory): String? = when (c) {
        WayCategory.CYCLE_TRACK -> "la piste cyclable"
        WayCategory.CYCLE_LANE -> "la bande cyclable"
        WayCategory.GREENWAY -> "la voie verte"
        WayCategory.LIVING_STREET -> "la zone de rencontre"
        else -> null
    }

    /**
     * Action de la manœuvre quand la voie qui suit est connue, p. ex. « prenez la piste cyclable à droite ».
     * [into] = catégorie de la portion après la manœuvre ; [from] = avant. Null = pas de formulation spécifique.
     */
    fun maneuverAction(m: Maneuver, from: WayCategory?, into: WayCategory?): String? {
        if (into == WayCategory.CROSSING) return null // phrase dédiée [CROSSING]
        val target = into?.let(::article)
        val s = side(m.type)
        return when {
            target != null && s != null && into != from -> "prenez $target $s"
            target != null && m.type == ManeuverType.CONTINUE && into != from -> "continuez tout droit sur $target"
            s != null && into == WayCategory.SHARED_ROAD && from?.isCycleInfrastructure == true -> "tournez $s et rejoignez la route partagée"
            else -> null
        }
    }

    fun forStage(stage: AnnouncementStage, distanceM: Double, action: String): String = when (stage) {
        AnnouncementStage.NOW -> action.replaceFirstChar { it.titlecase(Locale.FRANCE) } + "."
        else -> "Dans ${distance(distanceM)}, $action."
    }

    fun continueAlong(category: WayCategory, lengthM: Double): String? =
        article(category)?.let { "Continuez sur $it pendant ${distance(lengthM)}." }

    fun sharedRoadAhead(limitKmh: Int?): String =
        if (limitKmh != null) "Attention, vous allez rejoindre une route partagée limitée à $limitKmh kilomètres heure."
        else "Attention, vous allez rejoindre une route partagée."

    /** Avertissement anticipé ; null pour les types non annoncés à la voix. */
    fun warning(kind: ScooterWarningKind, distanceM: Double): String? {
        val d = distance(distanceM)
        return when (kind) {
            ScooterWarningKind.STEEP_CLIMB -> "Forte montée dans $d."
            ScooterWarningKind.STEEP_DESCENT -> "Descente prononcée dans $d, modérez votre vitesse."
            ScooterWarningKind.GRADE_ABOVE_CAPACITY -> "Pente très forte dans $d, au-delà de la capacité annoncée de votre trottinette."
            ScooterWarningKind.ROUGH_SURFACE -> "Revêtement dégradé dans $d."
            ScooterWarningKind.UNPAVED -> "Chemin non revêtu dans $d."
            ScooterWarningKind.ROADWORKS -> "Zone de travaux signalée dans $d."
            ScooterWarningKind.COMPLEX_INTERSECTION -> "Intersection complexe dans $d, soyez vigilant."
            ScooterWarningKind.FORBIDDEN -> "Attention, passage interdit aux trottinettes dans $d d'après les données cartographiques."
            ScooterWarningKind.NOT_RIDABLE -> "Dans $d, descendez de la trottinette pour ce passage."
            ScooterWarningKind.PEDESTRIAN_ZONE -> "Aire piétonne dans $d, roulez au pas."
            ScooterWarningKind.CONDITIONAL_RESTRICTION -> "Restriction temporaire possible dans $d."
            ScooterWarningKind.FAST_TRAFFIC, ScooterWarningKind.SHARED_ROAD, ScooterWarningKind.UNVERIFIED_ACCESS, ScooterWarningKind.UNLIT -> null
        }
    }
}
