package fr.roadly.core.scooter.assistant

import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.scooter.guidance.CategoryAhead
import fr.roadly.core.scooter.guidance.ScooterPhrases
import fr.roadly.core.scooter.range.RangeEstimate
import fr.roadly.core.scooter.range.RangeRisk
import java.text.Normalizer
import kotlin.math.roundToInt

/** Intention reconnue dans une demande vocale ou écrite en mode trottinette. */
sealed interface ScooterCommand {
    data class SetGoal(val goal: ScooterRouteGoal) : ScooterCommand
    data object AvoidBusyRoads : ScooterCommand
    data object PreferCycleways : ScooterCommand
    data object AvoidHills : ScooterCommand
    data object AvoidRoughSurfaces : ScooterCommand
    /** « Est-ce que j'ai assez de batterie… » ; [toHome] = pour rentrer au domicile. */
    data class AskRange(val toHome: Boolean) : ScooterCommand
    data object NextCycleway : ScooterCommand
    data object CloserDestination : ScooterCommand
    data class SetBattery(val pct: Int) : ScooterCommand
    data object Unknown : ScooterCommand
}

/**
 * Compréhension par mots-clés (français), sans service distant. Prévue pour être remplacée ou complétée par le
 * copilote conversationnel (spec Roadly AI) : les mêmes intentions sont exposées comme outils ([ScooterAssistantTools]).
 */
object ScooterCommandParser {
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9% ]"), " ").replace(Regex("\\s+"), " ").trim()

    fun parse(text: String): ScooterCommand {
        val t = normalize(text)
        fun has(vararg words: String) = words.any { t.contains(it) }
        Regex("(?:batterie|charge)\\D{0,20}(\\d{1,3}) ?(?:%|pour ?cent)").find(t)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it in 0..100 }?.let { return ScooterCommand.SetBattery(it) }
        }
        if (has("plus proche", "plus pres") && has("destination", "endroit", "lieu", "batterie", "autonomie")) return ScooterCommand.CloserDestination
        if (has("manque de batterie", "plus assez de batterie")) return ScooterCommand.CloserDestination
        if (has("batterie", "autonomie", "charge")) {
            return ScooterCommand.AskRange(toHome = has("rentrer", "maison", "domicile", "chez moi", "retour"))
        }
        if (has("prochaine piste", "prochaine bande", "prochaine voie verte", "piste cyclable la plus proche")) return ScooterCommand.NextCycleway
        if (has("circulation", "frequente", "trafic", "grands axes", "voitures")) return ScooterCommand.AvoidBusyRoads
        if (has("piste") && has("privilegi", "prefer", "favoris", "maximum", "passe par", "utilise")) return ScooterCommand.PreferCycleways
        if (has("montee", "pente", "denivele", "cote", "grimpe")) return ScooterCommand.AvoidHills
        if (has("pave", "non revetu", "chemin de terre", "cailloux", "revetement")) return ScooterCommand.AvoidRoughSurfaces
        if (has("secur", "plus sur", "sans danger", "dangereux")) return ScooterCommand.SetGoal(ScooterRouteGoal.SAFEST)
        if (has("confort", "agreable", "tranquille")) return ScooterCommand.SetGoal(ScooterRouteGoal.COMFORT)
        if (has("plus court")) return ScooterCommand.SetGoal(ScooterRouteGoal.SHORTEST)
        if (has("plus rapide", "vite")) return ScooterCommand.SetGoal(ScooterRouteGoal.FASTEST)
        return ScooterCommand.Unknown
    }
}

/** Action demandée à l'application en réponse à une commande. */
sealed interface AssistantAction {
    data class ApplyPreferences(val preferences: ScooterRoutePreferences) : AssistantAction
    data object EstimateRangeToHome : AssistantAction
    data class SearchWithinRange(val maxDistanceM: Double) : AssistantAction
    data class UpdateBattery(val pct: Int) : AssistantAction
}

data class AssistantReply(val speech: String, val action: AssistantAction? = null)

/** Données réelles disponibles au moment de la question ; null = inconnu (la réponse le dit). */
data class ScooterAssistantContext(
    val preferences: ScooterRoutePreferences,
    val rangeToDestination: RangeEstimate? = null,
    val nextCycleInfrastructure: CategoryAhead? = null,
    val guiding: Boolean = false,
    val hasHome: Boolean = false,
    val analysisAvailable: Boolean = false,
)

/** Réponses du copilote en mode trottinette : uniquement à partir du moteur de navigation et du profil véhicule. */
object ScooterAssistant {
    fun respond(command: ScooterCommand, ctx: ScooterAssistantContext): AssistantReply = when (command) {
        is ScooterCommand.SetGoal -> AssistantReply(
            "D'accord, je recherche ${goalLabel(command.goal)}.",
            AssistantAction.ApplyPreferences(ctx.preferences.copy(goal = command.goal)),
        )
        ScooterCommand.AvoidBusyRoads -> AssistantReply(
            "D'accord, j'évite autant que possible les grands axes. Je n'ai pas de données de trafic en temps réel : j'utilise le type de route.",
            AssistantAction.ApplyPreferences(ctx.preferences.copy(avoidBusyRoads = true, goal = ScooterRouteGoal.SAFEST)),
        )
        ScooterCommand.PreferCycleways -> AssistantReply(
            "D'accord, je privilégie les pistes et bandes cyclables.",
            AssistantAction.ApplyPreferences(ctx.preferences.copy(preferCycleways = true)),
        )
        ScooterCommand.AvoidHills -> AssistantReply(
            "D'accord, je cherche un trajet avec moins de montées.",
            AssistantAction.ApplyPreferences(ctx.preferences.copy(avoidSteepHills = true)),
        )
        ScooterCommand.AvoidRoughSurfaces -> AssistantReply(
            "D'accord, j'évite les pavés et les chemins non revêtus quand les données les signalent.",
            AssistantAction.ApplyPreferences(ctx.preferences.copy(avoidRoughSurfaces = true, avoidUnpaved = true)),
        )
        is ScooterCommand.AskRange -> if (command.toHome) {
            if (ctx.hasHome) AssistantReply("Je calcule le trajet jusqu'au domicile.", AssistantAction.EstimateRangeToHome)
            else AssistantReply("Aucun domicile n'est enregistré. Définissez-le dans vos favoris.")
        } else rangeAnswer(ctx.rangeToDestination, "la destination")
        ScooterCommand.NextCycleway -> nextCyclewayAnswer(ctx)
        ScooterCommand.CloserDestination -> closerDestination(ctx.rangeToDestination)
        is ScooterCommand.SetBattery -> AssistantReply("C'est noté : batterie à ${command.pct} %.", AssistantAction.UpdateBattery(command.pct))
        ScooterCommand.Unknown -> AssistantReply("Je n'ai pas compris. Vous pouvez demander le trajet le plus sécurisé, éviter les montées, ou si la batterie suffit.")
    }

    fun rangeAnswer(estimate: RangeEstimate?, destinationLabel: String): AssistantReply {
        if (estimate == null) return AssistantReply("Je n'ai pas d'itinéraire pour estimer l'autonomie.")
        if (estimate.risk == RangeRisk.UNKNOWN || estimate.arrivalBatteryPct == null) {
            return AssistantReply("Je ne connais pas le niveau de batterie de la trottinette. Dites par exemple « batterie à 80 % », ou saisissez-le à l'écran.")
        }
        val arrival = estimate.arrivalBatteryPct.roundToInt()
        val text = when (estimate.risk) {
            RangeRisk.OK -> "Oui, d'après mon estimation il devrait vous rester environ $arrival % en arrivant à $destinationLabel."
            RangeRisk.TIGHT -> "C'est juste : environ ${arrival.coerceAtLeast(0)} % estimés à l'arrivée à $destinationLabel, avec une marge d'incertitude. Roulez plus doucement pour économiser la batterie."
            RangeRisk.INSUFFICIENT -> "Probablement pas : l'énergie estimée pour rejoindre $destinationLabel dépasse la batterie restante. Je peux chercher un lieu plus proche."
            RangeRisk.UNKNOWN -> error("traité plus haut")
        }
        val note = if (!estimate.calibration.calibrated) " Estimation non encore calibrée sur vos trajets." else ""
        return AssistantReply(text + note)
    }

    private fun nextCyclewayAnswer(ctx: ScooterAssistantContext): AssistantReply {
        if (!ctx.analysisAvailable) return AssistantReply("Je n'ai pas l'analyse détaillée des voies pour cet itinéraire.")
        val next = ctx.nextCycleInfrastructure ?: return AssistantReply("Il n'y a plus de piste ni de bande cyclable connue sur la suite de l'itinéraire.")
        val name = next.category.label.lowercase()
        return if (next.distanceM <= 5.0) AssistantReply("Vous êtes sur une $name, sur encore ${ScooterPhrases.distance(next.lengthM)}.")
        else AssistantReply("Prochaine $name dans ${ScooterPhrases.distance(next.distanceM)}, sur ${ScooterPhrases.distance(next.lengthM)}.")
    }

    private fun closerDestination(estimate: RangeEstimate?): AssistantReply {
        val km = estimate?.remainingRangeKm
        if (km == null) return AssistantReply("Sans niveau de batterie, je ne peux pas estimer la distance atteignable. Indiquez d'abord la batterie.")
        // Rayon prudent : moitié de l'autonomie à plat restante (trajet non rectiligne, marge d'incertitude).
        val radius = (km * 1000.0 * 0.5).coerceAtLeast(300.0)
        return AssistantReply("Je cherche des lieux dans un rayon d'environ ${ScooterPhrases.distance(radius)}.", AssistantAction.SearchWithinRange(radius))
    }

    private fun goalLabel(g: ScooterRouteGoal) = when (g) {
        ScooterRouteGoal.FASTEST -> "le trajet le plus rapide"
        ScooterRouteGoal.SAFEST -> "le trajet le plus sécurisé d'après les données"
        ScooterRouteGoal.COMFORT -> "le trajet le plus confortable"
        ScooterRouteGoal.SHORTEST -> "le trajet le plus court"
    }
}

/** Description des intentions comme outils (nom, description, paramètres) pour le futur copilote conversationnel. */
object ScooterAssistantTools {
    data class ToolSpec(val name: String, val description: String, val parameters: Map<String, String> = emptyMap())

    val specs = listOf(
        ToolSpec("scooterSetRouteGoal", "Recalcule l'itinéraire trottinette selon un objectif", mapOf("goal" to "FASTEST|SAFEST|COMFORT|SHORTEST")),
        ToolSpec("scooterSetPreference", "Active une préférence trottinette", mapOf("preference" to "preferCycleways|avoidBusyRoads|avoidUnpaved|avoidSteepHills|avoidRoughSurfaces")),
        ToolSpec("scooterGetRange", "Autonomie estimée jusqu'à la destination ou au domicile (jamais inventée)", mapOf("target" to "destination|home")),
        ToolSpec("scooterNextCycleway", "Distance et longueur de la prochaine piste, bande cyclable ou voie verte"),
        ToolSpec("scooterSetBattery", "Enregistre le niveau de batterie annoncé par l'utilisateur", mapOf("pct" to "0..100")),
        ToolSpec("scooterFindWithinRange", "Recherche des lieux dans le rayon atteignable"),
    )
}
