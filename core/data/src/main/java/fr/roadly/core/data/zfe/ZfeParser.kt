package fr.roadly.core.data.zfe

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/**
 * Lecture de `aires.geojson` (Base nationale consolidée des ZFE, transport.data.gouv.fr, Licence Ouverte).
 * Une ligne illisible est ignorée, jamais d'exception pour le fichier entier si au moins une ligne est valide.
 */
object ZfeParser {
    /** [names] : siren de l'EPCI → nom (« 243100518 » → « Toulouse Métropole »), lu dans `zfe_ids.csv`. */
    fun parse(body: String, names: Map<String, String> = emptyMap(), json: Json = Json { ignoreUnknownKeys = true }): List<ZfeRule> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val features = (root["features"] as? JsonArray) ?: return emptyList()
        return features.mapNotNull { f -> runCatching { feature(f.jsonObject, names) }.getOrNull() }
    }

    private fun feature(f: JsonObject, names: Map<String, String>): ZfeRule? {
        val p = f["properties"]?.jsonObject ?: return null
        val polygons = geometry(f["geometry"]?.jsonObject ?: return null).ifEmpty { return null }
        val id = p.str("id") ?: return null
        val restriction = ZfeVehicleClass.entries.associateWith { p.str("${it.key}_critair") }
        val hours = ZfeVehicleClass.entries.associateWith { p.str("${it.key}_horaires") }
        val info = p.str("url_site_information") ?: p.str("url_site") ?: p.str("url_site_informations")
        return ZfeRule(
            id = id, name = name(id, p.str("nom"), info, names),
            start = p.date("date_debut"), end = p.date("date_fin"),
            restriction = restriction, hours = hours, decreeUrl = p.str("url_arrete"), infoUrl = info, polygons = polygons,
        )
    }

    /** Nom de l'EPCI (siren en tête de l'identifiant), sinon le nom fourni s'il n'est pas générique, sinon le site d'information. */
    internal fun name(id: String, nom: String?, infoUrl: String?, names: Map<String, String>): String {
        names[id.substringBefore("-")]?.let { return it }
        nom?.takeIf { it.isNotBlank() && !it.equals("ZFEM", true) && !it.equals("ZFE", true) }?.let { return it }
        val host = infoUrl?.substringAfter("://")?.substringBefore("/")?.removePrefix("www.")?.removePrefix("zfe.")
        return host?.substringBeforeLast(".")?.replaceFirstChar { it.uppercase() } ?: "ZFE"
    }

    private fun geometry(g: JsonObject): List<ZfePolygon> {
        val coords = g["coordinates"] as? JsonArray ?: return emptyList()
        return when (g.str("type")) {
            "Polygon" -> listOfNotNull(polygon(coords))
            "MultiPolygon" -> coords.mapNotNull { polygon(it.jsonArray) }
            else -> emptyList()
        }
    }

    private fun polygon(rings: JsonArray): ZfePolygon? {
        val parsed = rings.map { ring -> ring.jsonArray.mapNotNull { pt -> point(pt) } }.filter { it.size >= 3 }
        val outer = parsed.firstOrNull() ?: return null
        return ZfePolygon(outer, parsed.drop(1))
    }

    private fun point(e: JsonElement): GeoPoint? {
        val a = e as? JsonArray ?: return null
        val lon = a.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return null
        val lat = a.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return null
        return GeoPoint(lat, lon)
    }

    private fun JsonObject.str(key: String): String? = this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null }
    private fun JsonObject.date(key: String): LocalDate? = str(key)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    /** `zfe_ids.csv` (séparateur « ; ») : siren → EPCI principal ; « autres_siren » renvoie au même nom. */
    fun parseNames(csv: String): Map<String, String> {
        val out = HashMap<String, String>()
        csv.lineSequence().drop(1).forEach { line ->
            val cols = line.split(';')
            val name = cols.getOrNull(2)?.trim()?.ifEmpty { null } ?: return@forEach
            cols.getOrNull(0)?.trim()?.ifEmpty { null }?.let { out[it] = name }
            cols.getOrNull(3)?.split(',', ' ')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { out[it] = name }
        }
        return out
    }
}
