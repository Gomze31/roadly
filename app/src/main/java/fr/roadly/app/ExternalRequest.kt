package fr.roadly.app

import java.net.URLDecoder

/** Demande arrivée de l'extérieur : raccourci, widget, intent `geo:` / `google.navigation:` (Assistant, autres applications). */
sealed interface ExternalRequest {
    data object Home : ExternalRequest
    data object Work : ExternalRequest
    data class Search(val query: String?) : ExternalRequest
    data class Destination(val lat: Double, val lon: Double, val label: String?) : ExternalRequest
}

object ExternalIntents {
    const val ACTION_HOME = "fr.roadly.action.NAVIGATE_HOME"
    const val ACTION_WORK = "fr.roadly.action.NAVIGATE_WORK"
    const val ACTION_SEARCH = "fr.roadly.action.SEARCH"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    /**
     * Formes reconnues : `geo:lat,lon`, `geo:lat,lon?q=libellé`, `geo:0,0?q=lat,lon(libellé)`, `geo:0,0?q=adresse`,
     * `google.navigation:q=lat,lon`, `google.navigation:q=adresse`. Une requête sans coordonnée ouvre la recherche pré-remplie.
     */
    fun parse(action: String?, data: String?): ExternalRequest? = when (action) {
        ACTION_HOME -> ExternalRequest.Home
        ACTION_WORK -> ExternalRequest.Work
        ACTION_SEARCH -> ExternalRequest.Search(null)
        ACTION_VIEW -> data?.let(::parseUri)
        else -> null
    }

    private val coords = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.*)\))?\s*$""")

    private fun parseUri(uri: String): ExternalRequest? {
        val (scheme, rest) = uri.split(":", limit = 2).let { if (it.size == 2) it[0].lowercase() to it[1] else return null }
        if (scheme != "geo" && scheme != "google.navigation") return null
        // geo:PATH?PARAMS ; google.navigation:PARAMS (pas de chemin, « ? » facultatif).
        val path = if (scheme == "geo") rest.substringBefore('?') else ""
        val paramString = if (scheme == "geo") rest.substringAfter('?', "") else rest.removePrefix("?")
        val params = paramString.split('&').filter { it.isNotBlank() }
            .associate { p -> p.substringBefore('=') to decode(p.substringAfter('=', "")) }
        val q = params["q"]?.trim()?.takeIf { it.isNotEmpty() }
        val pathCoords = coords.find(path)?.let { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }?.takeIf { it.first != 0.0 || it.second != 0.0 }
        val qCoords = q?.let { coords.find(it) }
        return when {
            qCoords != null -> ExternalRequest.Destination(qCoords.groupValues[1].toDouble(), qCoords.groupValues[2].toDouble(), qCoords.groupValues[3].takeIf { it.isNotBlank() })
            pathCoords != null -> ExternalRequest.Destination(pathCoords.first, pathCoords.second, q)
            q != null -> ExternalRequest.Search(q)
            else -> null
        }
    }

    private fun decode(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (_: Exception) { s }
}
