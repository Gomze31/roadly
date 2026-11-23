package fr.roadly.core.navigation

/**
 * Mise en forme d'une phrase avant la synthèse vocale (Kotlin pur, testé sur JVM).
 *
 * Constat en voiture : la voix se trompait sur les noms. Un moteur de synthèse lit ce qu'on lui écrit : « A61 » peut sortir
 * « a six un » ou « assoixante-et-un », « Av. du Gal Leclerc » « av du gal », « Bd Henri IV » « bd Henri i v ». Les textes de
 * guidage (Valhalla, OpenStreetMap) gardent ces écritures de panneau ; elles sont réécrites ici comme on les dit.
 * Rien d'autre n'est modifié : un mot inconnu passe tel quel.
 */
object SpeechText {
    /** Numéros de route : lettre(s) de réseau collées au nombre (« A61 », « D 820 », « RN20 », « D2A »). */
    private val roadRef = Regex("""(?<![\p{L}\d])(RN|RD|RM|A|N|D|M|E|C)\s?(\d{1,4})([A-Za-z])?(?![\p{L}\d])""")

    /** Abréviations de voirie et de titres, avec ou sans point. Clés en minuscules. */
    private val abbreviations = mapOf(
        "av" to "avenue", "ave" to "avenue", "bd" to "boulevard", "bld" to "boulevard", "bvd" to "boulevard", "boul" to "boulevard",
        "pl" to "place", "rte" to "route", "ch" to "chemin", "chem" to "chemin", "imp" to "impasse", "all" to "allée",
        "sq" to "square", "crs" to "cours", "fg" to "faubourg", "fbg" to "faubourg", "pte" to "porte", "qu" to "quai",
        "res" to "résidence", "rés" to "résidence", "lot" to "lotissement", "zi" to "zone industrielle", "za" to "zone d'activité",
        "zac" to "zac", "st" to "saint", "ste" to "sainte", "sts" to "saints",
        "dr" to "docteur", "pr" to "professeur", "prof" to "professeur", "gal" to "général", "gén" to "général", "gen" to "général",
        "mal" to "maréchal", "cdt" to "commandant", "cne" to "capitaine", "col" to "colonel", "lt" to "lieutenant",
        "pdt" to "président", "prés" to "président", "me" to "maître", "mgr" to "monseigneur",
    )

    /** Abréviations qui sont aussi des mots courants : développées seulement devant un nom propre (majuscule) ou avec un point. */
    private val ambiguous = setOf("ch", "pl", "all", "col", "me", "pr", "lot", "res", "st", "qu", "mal", "gal", "gen")

    private val word = Regex("""(?<![\p{L}\d])([\p{L}]{2,5})(\.)?(?=(\s+|-)([\p{L}]))""")
    private val roman = Regex("""(?<=\p{Lu}[\p{L}'-]{1,30} )(X{0,3}(?:IX|IV|V?I{0,3}))(er|ER)?(?![\p{L}\d])""")
    private val romanValues = mapOf('I' to 1, 'V' to 5, 'X' to 10)
    private val units = Regex("""\bkm\s?/\s?h\b""", RegexOption.IGNORE_CASE)
    private val spaces = Regex("""[ \t]{2,}""")

    fun normalize(text: String): String {
        var s = text
        s = units.replace(s, "kilomètres heure")
        s = s.replace("/", ", ")
        s = roadRef.replace(s) { m ->
            val net = when (val p = m.groupValues[1].uppercase()) { "RN" -> "N"; "RD" -> "D"; "RM" -> "M"; else -> p }
            // Lettre et nombre séparés : le moteur dit la lettre puis le nombre entier (« A soixante et un »).
            listOf(net, m.groupValues[2], m.groupValues[3].uppercase()).filter { it.isNotEmpty() }.joinToString(" ")
        }
        s = word.replace(s) { m ->
            val raw = m.groupValues[1]
            val key = raw.lowercase()
            val full = abbreviations[key] ?: return@replace m.value
            val dotted = m.groupValues[2].isNotEmpty()
            val next = m.groupValues[4]
            // « St » dans « St-Michel » ou « St Michel » ; « ch » dans « Ch. des Vignes ». Jamais un mot en minuscules dans une phrase
            // (« me », « col », « lot »…) : il faut une majuscule initiale et un nom propre derrière, ou le point d'abréviation.
            val properNext = next.first().isUpperCase()
            val capitalized = raw.first().isUpperCase()
            val ok = if (key in ambiguous) capitalized && (dotted || properNext) else capitalized || dotted
            if (!ok) return@replace m.value
            if (raw.first().isUpperCase()) full.replaceFirstChar { it.uppercase() } else full
        }
        s = roman.replace(s) { m ->
            val numeral = m.groupValues[1]
            if (numeral.isEmpty()) return@replace m.value
            val n = romanToInt(numeral) ?: return@replace m.value
            // « Ier » → « premier » ; un « I » seul sans « er » reste une lettre (initiale).
            when {
                m.groupValues[2].isNotEmpty() -> if (n == 1) "premier" else n.toString()
                numeral == "I" -> m.value
                else -> n.toString()
            }
        }
        return spaces.replace(s, " ").trim()
    }

    private fun romanToInt(s: String): Int? {
        var total = 0
        for (i in s.indices) {
            val v = romanValues[s[i]] ?: return null
            val next = s.getOrNull(i + 1)?.let { romanValues[it] } ?: 0
            total += if (v < next) -v else v
        }
        return total.takeIf { it in 1..39 }
    }
}

/** Voix proposée par le moteur de synthèse, réduite à ce qui sert au choix (découplé d'`android.speech.tts.Voice` pour les tests). */
data class VoiceCandidate(
    val name: String,
    /** Balise de langue, p. ex. « fr-FR », « fr-CA ». */
    val localeTag: String,
    /** Échelle d'Android : 100 (très basse) à 500 (très haute). */
    val quality: Int,
    /** Échelle d'Android : 100 (très faible) à 500 (très forte). */
    val latency: Int,
    val network: Boolean,
    /** Données de la voix présentes sur le téléphone (pas de marqueur `notInstalled`). */
    val installed: Boolean,
)

/**
 * Choix de la voix française la plus naturelle parmi celles du moteur.
 *
 * Avant : la voix par défaut du moteur, souvent la plus ancienne (qualité « normale »). Ici, la meilleure qualité disponible
 * l'emporte. À qualité égale, une voix embarquée passe devant une voix en ligne : une consigne « tournez maintenant » ne peut pas
 * attendre le réseau, ni se taire dans un tunnel. Une voix en ligne n'est retenue que si elle est nettement meilleure et que le
 * réseau est là.
 */
object VoiceRanking {
    /** Écart de qualité (échelle Android, pas de 100) à partir duquel une voix en ligne passe devant une voix embarquée. */
    const val NETWORK_QUALITY_MARGIN = 100

    fun best(voices: List<VoiceCandidate>, networkAvailable: Boolean): VoiceCandidate? {
        val french = voices.filter { it.installed && it.localeTag.startsWith("fr", ignoreCase = true) }
        val usable = french.filter { networkAvailable || !it.network }
        return usable.maxWithOrNull(
            compareBy<VoiceCandidate> { if (it.localeTag.equals("fr-FR", ignoreCase = true)) 1 else 0 }
                .thenBy { it.quality - if (it.network) NETWORK_QUALITY_MARGIN else 0 }
                .thenBy { if (it.network) 0 else 1 }
                .thenBy { -it.latency }
                .thenBy { it.name },
        )
    }
}
