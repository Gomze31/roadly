package fr.roadly.feature.voice

import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.model.ReportKind
import java.text.Normalizer
import java.util.Locale

/** Options d'itinéraire pilotables à la voix. */
enum class AvoidOption { TOLLS, HIGHWAYS, TUNNELS, FERRIES }

/** Ce que l'utilisateur demande, compris localement (aucun service d'IA). */
sealed interface VoiceIntent {
    sealed interface Target {
        data object Home : Target
        data object Work : Target
        data class Query(val text: String) : Target
    }
    data class Navigate(val target: Target) : VoiceIntent
    data object StopGuidance : VoiceIntent
    data class Avoid(val option: AvoidOption, val enabled: Boolean) : VoiceIntent
    data object ArrivalTime : VoiceIntent
    data object Remaining : VoiceIntent
    data object NextInstruction : VoiceIntent
    data object SpeedLimit : VoiceIntent
    data object Recalculate : VoiceIntent
    data class Report(val kind: ReportKind) : VoiceIntent
    data class Nearby(val category: PoiCategory) : VoiceIntent
    data class Mute(val muted: Boolean) : VoiceIntent
    data object Help : VoiceIntent
    data class Unknown(val text: String) : VoiceIntent
}

/**
 * Interprète une phrase en français (texte de la reconnaissance vocale) : règles simples, déterministes et testées.
 * L'ordre compte : les demandes les plus spécifiques (éviter, signaler) passent avant la navigation générique.
 */
object VoiceCommandParser {

    fun parse(raw: String): VoiceIntent {
        val t = normalize(raw)
        if (t.isBlank()) return VoiceIntent.Unknown(raw)

        if (Regex("""\b(aide|que (puis je|peux tu|est ce que je peux) dire|qu est ce que (je peux|tu sais) (dire|faire)|commandes)\b""").containsMatchIn(t)) return VoiceIntent.Help

        avoid(t)?.let { return it }
        report(t)?.let { return it }

        if (Regex("""\b(arrete|arreter|stop|stoppe|termine|terminer|annule|annuler|quitte|quitter|fin)\b.*\b(guidage|navigation|trajet|itineraire|gps)\b""").containsMatchIn(t) || t in setOf("stop", "arrete", "arrete tout")) {
            return VoiceIntent.StopGuidance
        }
        if (Regex("""\b(coupe|couper|desactive|eteins|mets en sourdine)\b.*\b(voix|son|annonces?)\b|\b(silence|tais toi|chut|mode silencieux)\b""").containsMatchIn(t)) return VoiceIntent.Mute(true)
        if (Regex("""\b(remets|remettre|reactive|active|rallume)\b.*\b(voix|son|annonces?)\b|\breparle\b""").containsMatchIn(t)) return VoiceIntent.Mute(false)

        if (Regex("""\b(a quelle heure|quand)\b.*\b(arrive|arrivons|arriverai|arrivera|arriver)\b|\bheure d arrivee\b""").containsMatchIn(t)) return VoiceIntent.ArrivalTime
        if (Regex("""\b(combien|reste|restant|restante|il reste)\b.*\b(temps|minutes?|kilometres?|km|distance|route|reste)\b|\bdistance restante\b|\bc est encore loin\b|\bencore loin\b""").containsMatchIn(t)) return VoiceIntent.Remaining
        if (Regex("""\b(prochaine?|suivante?)\b.*\b(direction|instruction|manoeuvre|etape|sortie)\b|\bou (est ce que )?(je|on) (tourne|va)\b|\bje tourne ou\b""").containsMatchIn(t)) return VoiceIntent.NextInstruction
        if (Regex("""\b(limitation|vitesse (maximale|maxi|max|autorisee)|limite a combien|limite de vitesse|limitee? a combien)\b""").containsMatchIn(t)) return VoiceIntent.SpeedLimit
        if (Regex("""\b(recalcule|recalculer|autre (itineraire|chemin|route)|plus rapide|change d itineraire)\b""").containsMatchIn(t)) return VoiceIntent.Recalculate

        nearby(t)?.let { return it }
        navigate(t)?.let { return it }
        return VoiceIntent.Unknown(raw)
    }

    private fun avoid(t: String): VoiceIntent? {
        val option = when {
            Regex("""\bpeages?\b""").containsMatchIn(t) -> AvoidOption.TOLLS
            Regex("""\bautoroutes?\b""").containsMatchIn(t) -> AvoidOption.HIGHWAYS
            Regex("""\btunnels?\b""").containsMatchIn(t) -> AvoidOption.TUNNELS
            Regex("""\b(ferry|ferries|bac|bacs)\b""").containsMatchIn(t) -> AvoidOption.FERRIES
            else -> return null
        }
        val off = Regex("""\b(n evite plus|autorise|autoriser|accepte|accepter|reprends?|reprendre|avec (les |l )?)\b""").containsMatchIn(t)
        val on = Regex("""\b(evite|eviter|sans|pas d|pas de|contourne|contourner)\b""").containsMatchIn(t)
        return when {
            off -> VoiceIntent.Avoid(option, enabled = false)
            on -> VoiceIntent.Avoid(option, enabled = true)
            else -> null
        }
    }

    private fun report(t: String): VoiceIntent? {
        if (!Regex("""\b(signale|signaler|signalement|il y a|attention|alerte)\b""").containsMatchIn(t)) return null
        val kind = when {
            Regex("""\baccidents?\b""").containsMatchIn(t) -> ReportKind.ACCIDENT
            Regex("""\b(bouchons?|embouteillages?|ralentissements?|trafic|circulation)\b""").containsMatchIn(t) -> ReportKind.JAM
            Regex("""\b(route|rue|voie) (fermee|barree|coupee)\b|\bfermeture\b""").containsMatchIn(t) -> ReportKind.CLOSED
            Regex("""\btravaux\b""").containsMatchIn(t) -> ReportKind.ROADWORK
            Regex("""\bnids? de poule\b""").containsMatchIn(t) -> ReportKind.POTHOLE
            Regex("""\b(obstacles?|objets?|debris|animal|animaux)\b""").containsMatchIn(t) -> ReportKind.OBSTACLE
            Regex("""\b(verglas|brouillard|neige|inondation|grele|meteo)\b""").containsMatchIn(t) -> ReportKind.WEATHER
            Regex("""\bdangers?\b""").containsMatchIn(t) -> ReportKind.HAZARD
            else -> return null
        }
        return VoiceIntent.Report(kind)
    }

    private fun nearby(t: String): VoiceIntent? {
        if (!Regex("""\b(trouve|trouver|cherche|chercher|ou (est|sont|y a t il|trouver)|montre|affiche|besoin d|il me faut|y a t il|je veux|une?|la|le|les|des)\b""").containsMatchIn(t)) return null
        val cat = when {
            Regex("""\b(station|stations|essence|carburant|gazole|diesel|faire le plein|pompe)\b""").containsMatchIn(t) -> PoiCategory.FUEL
            Regex("""\b(borne|bornes|recharge|recharger)\b""").containsMatchIn(t) -> PoiCategory.CHARGING
            Regex("""\b(parking|parkings|me garer|stationner|stationnement)\b""").containsMatchIn(t) -> PoiCategory.PARKING
            Regex("""\b(aire|aires|pause|repos)\b""").containsMatchIn(t) -> PoiCategory.REST_AREA
            Regex("""\b(restaurant|restaurants|manger|dejeuner|diner)\b""").containsMatchIn(t) -> PoiCategory.RESTAURANT
            else -> return null
        }
        return VoiceIntent.Nearby(cat)
    }

    private val home = Regex("""^(a |au |a la |chez |vers |jusqu a )?(la maison|maison|chez moi|domicile|mon domicile|a la maison)$""")
    private val work = Regex("""^(a |au |vers |jusqu a )?(au |mon |le )?(travail|boulot|bureau|taf)$""")

    private fun navigate(t: String): VoiceIntent? {
        if (Regex("""\b(rentre|rentrer|rentrons|retour)\b.*\b(maison|chez moi|domicile)\b|^(maison|chez moi)$""").containsMatchIn(t)) return VoiceIntent.Navigate(VoiceIntent.Target.Home)
        if (Regex("""^(au )?(travail|boulot|bureau)$""").containsMatchIn(t)) return VoiceIntent.Navigate(VoiceIntent.Target.Work)
        val m = Regex(
            """^(?:(?:emmene|amene|conduis|guide|ramene|emmenez|conduisez|guidez)[ -]moi|(?:je (?:veux|voudrais|vais|dois) (?:aller|me rendre))|(?:je vais)|(?:on va|allons|aller|va|vas|rends toi|navigue|naviguer|navigation|itineraire|route|direction|trajet|go))\b\s*(.+)$""",
        ).find(t) ?: return null
        val rest = m.groupValues[1].trim()
        if (home.matches(rest)) return VoiceIntent.Navigate(VoiceIntent.Target.Home)
        val dest = rest.replace(Regex("""^(?:a|au|aux|chez|vers|jusqu a|pour|en direction d[eu]?|a la|a l)\s+"""), "").removePrefix("la ").trim()
        if (dest.isBlank()) return null
        if (home.matches(dest) || dest in setOf("la maison", "maison", "chez moi", "domicile")) return VoiceIntent.Navigate(VoiceIntent.Target.Home)
        if (work.matches(dest)) return VoiceIntent.Navigate(VoiceIntent.Target.Work)
        // Texte normalisé (sans accents) : la Base Adresse Nationale et Photon le comprennent tel quel.
        return VoiceIntent.Navigate(VoiceIntent.Target.Query(dest))
    }

    /** Minuscule, sans accents ni ponctuation, apostrophes et tirets en espaces, espaces simples. */
    fun normalize(s: String): String {
        val noAccents = Normalizer.normalize(s.lowercase(Locale.FRENCH), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return noAccents.replace(Regex("[’'\\-]"), " ").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
