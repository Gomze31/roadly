package fr.roadly.core.data.charging

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Prises d'une station (base nationale IRVE). */
enum class Connector { CCS, CHADEMO, TYPE2 }

/**
 * Station de recharge publique (base nationale IRVE consolidée, data.gouv.fr, Licence Ouverte) : points de charge regroupés
 * par emplacement. [maxKw] = puissance nominale la plus haute ; [price], [hours], [access] tels que publiés par l'opérateur.
 */
data class ChargingStation(
    val id: String,
    val name: String,
    val operator: String?,
    val address: String?,
    val point: GeoPoint,
    val maxKw: Int,
    val chargePoints: Int,
    val connectors: Set<Connector>,
    val free: Boolean,
    val price: String?,
    val hours: String?,
    val access: String?,
    /** Identifiants d'itinérance des points de charge (pour leur état publié). */
    val pdcIds: List<String> = emptyList(),
) {
    fun toPlace(): Place = Place(id = "irve:$id", name = name, address = address, point = point, kind = PlaceKind.POI, source = "irve")
}

/** Dernier état publié d'un point de charge (base dynamique) ; [atEpochMs] = horodatage du relevé. */
data class PdcStatus(val inService: Boolean?, val occupancy: Occupancy, val atEpochMs: Long?)

enum class Occupancy { FREE, OCCUPIED, RESERVED, UNKNOWN }

interface ChargingStationProvider {
    /** Stations d'au moins [minKw] dans [box], regroupées ; échec réseau = Failure. */
    suspend fun fastIn(box: BoundingBox, minKw: Int = 50): Outcome<List<ChargingStation>>
    /** Dernier état publié de chaque point de charge demandé (absent = jamais publié). */
    suspend fun status(pdcIds: List<String>): Outcome<Map<String, PdcStatus>> = Outcome.Success(emptyMap())
}

/**
 * Lecture par zone de la base IRVE via l'API tabulaire de data.gouv.fr (la base complète pèse 118 Mo) : colonnes utiles seulement,
 * pages de 200 points de charge, deux pages au plus par zone.
 */
@Singleton
class IrveChargingStationProvider @Inject constructor(private val api: ApiClient, private val json: Json) : ChargingStationProvider {
    override suspend fun fastIn(box: BoundingBox, minKw: Int): Outcome<List<ChargingStation>> {
        val rows = ArrayList<JsonObject>()
        for (page in 1..MAX_PAGES) {
            val url = url(box, minKw, page) ?: return Outcome.Failure(AppError.ProviderUnavailable(SOURCE))
            // L'API tabulaire laisse parfois tomber une requête : un second essai.
            var r = api.get(url)
            if (r is Outcome.Failure) r = api.get(url)
            val body = when (r) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> return if (rows.isEmpty()) r else Outcome.Success(Irve.group(rows))
            }
            val (data, more) = Irve.parsePage(json, body) ?: return Outcome.Failure(AppError.Parse(SOURCE))
            rows += data
            if (!more) break
        }
        return Outcome.Success(Irve.group(rows))
    }

    private fun url(box: BoundingBox, minKw: Int, page: Int) = Irve.BASE_URL.toHttpUrlOrNull()?.newBuilder()
        ?.addQueryParameter("consolidated_latitude__greater", fmt(box.south))
        ?.addQueryParameter("consolidated_latitude__less", fmt(box.north))
        ?.addQueryParameter("consolidated_longitude__greater", fmt(box.west))
        ?.addQueryParameter("consolidated_longitude__less", fmt(box.east))
        ?.addQueryParameter("puissance_nominale__greater", (minKw - 1).toString())
        ?.addQueryParameter("columns", Irve.COLUMNS)
        ?.addQueryParameter("page_size", PAGE_SIZE.toString())
        ?.addQueryParameter("page", page.toString())
        ?.build()

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.5f", v)

    override suspend fun status(pdcIds: List<String>): Outcome<Map<String, PdcStatus>> {
        val out = HashMap<String, PdcStatus>()
        for (chunk in pdcIds.distinct().filter { Irve.validId(it) }.chunked(STATUS_BATCH)) {
            val url = Irve.DYNAMIC_URL.toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("id_pdc_itinerance__in", chunk.joinToString(","))
                ?.addQueryParameter("columns", Irve.DYNAMIC_COLUMNS)
                ?.addQueryParameter("page_size", PAGE_SIZE.toString())
                ?.build() ?: return Outcome.Failure(AppError.ProviderUnavailable(SOURCE))
            var r = api.get(url)
            if (r is Outcome.Failure) r = api.get(url)
            when (r) {
                is Outcome.Success -> Irve.parseStatus(json, r.value)?.let(out::putAll)
                is Outcome.Failure -> if (out.isEmpty()) return r
            }
        }
        return Outcome.Success(out)
    }

    companion object {
        const val SOURCE = "IRVE"
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 2
        const val STATUS_BATCH = 50
    }
}

/** Décodage et regroupement des lignes IRVE (une ligne = un point de charge). */
object Irve {
    /**
     * Base nationale consolidée des points de recharge (Point d'Accès National, transport.data.gouv.fr), données statiques
     * dédoublonnées, actualisées chaque jour ; elle remplace l'ancienne base data.gouv.fr (plus mise à jour après 2026).
     */
    const val BASE_URL = "https://tabular-api.data.gouv.fr/api/resources/4ca78c71-4ea4-475d-bd3a-d4aef88f7bf8/data/"
    /** Même base, données dynamiques : dernier état publié de chaque point de charge (pas du temps réel, voir [PdcStatus]). */
    const val DYNAMIC_URL = "https://tabular-api.data.gouv.fr/api/resources/89185b1f-f958-4c5b-9282-399a66ecee97/data/"
    const val DYNAMIC_COLUMNS = "id_pdc_itinerance,etat_pdc,occupation_pdc,horodatage"
    const val NAME = "Base nationale IRVE · data.gouv.fr"
    const val COLUMNS = "id_station_itinerance,nom_station,nom_operateur,nom_enseigne,adresse_station,consolidated_latitude," +
        "consolidated_longitude,puissance_nominale,prise_type_combo_ccs,prise_type_chademo,prise_type_2,gratuit,tarification," +
        "horaires,condition_acces,id_pdc_itinerance"

    /** Identifiant d'itinérance (lettres, chiffres, « * », « _ », « - ») : rien d'autre n'est placé dans le filtre « in ». */
    fun validId(id: String) = id.length in 3..64 && id.all { it.isLetterOrDigit() || it == '*' || it == '_' || it == '-' }

    fun parseStatus(json: Json, body: String): Map<String, PdcStatus>? = parsePage(json, body)?.first?.mapNotNull { r ->
        val id = r.str("id_pdc_itinerance") ?: return@mapNotNull null
        val etat = r.str("etat_pdc")
        val occ = when (r.str("occupation_pdc")) { "libre" -> Occupancy.FREE; "occupe" -> Occupancy.OCCUPIED; "reserve" -> Occupancy.RESERVED; else -> Occupancy.UNKNOWN }
        val at = r.str("horodatage")?.let { s ->
            runCatching { java.time.OffsetDateTime.parse(s.replace(' ', 'T').replace(Regex("""(\+\d\d)$"""), "$1:00")).toInstant().toEpochMilli() }.getOrNull()
        }
        id to PdcStatus(when (etat) { "en_service" -> true; "hors_service" -> false; else -> null }, occ, at)
    }?.toMap()

    /** Nom affichable : certains opérateurs publient un identifiant technique (« Tohmo/ee057d6d-… ») comme nom de station. */
    private val technicalName = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}|/[A-Z0-9]{8,}$""")

    internal fun readableName(r: JsonObject): String {
        val name = r.str("nom_station")
        if (name != null && !technicalName.containsMatchIn(name)) return name
        val who = r.str("nom_enseigne") ?: r.str("nom_operateur")
        val where = r.str("adresse_station")
        return listOfNotNull(who, where).joinToString(" · ").ifEmpty { name ?: "Borne de recharge" }
    }

    /** Lignes de la page et « il y a une page suivante ». */
    fun parsePage(json: Json, body: String): Pair<List<JsonObject>, Boolean>? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val data = (root["data"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        val next = (root["links"] as? JsonObject)?.get("next")?.let { it as? JsonPrimitive }?.contentOrNull
        data to (next != null)
    }.getOrNull()

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.replace(',', '.')?.toDoubleOrNull() }
    // Booléen JSON, ou texte « true » / « 1 » / « oui » selon les opérateurs (parenthèses : `?:` passe après `in`).
    private fun JsonObject.flag(k: String) = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.contentOrNull?.lowercase() in setOf("true", "1", "oui")) } == true

    /**
     * Regroupe les points de charge d'un même emplacement (coordonnées arrondies à ~10 m et même nom). Les coordonnées consolidées
     * sont utilisées telles quelles (`consolidated_is_lon_lat_correct` = faux signale une inversion d'origine, déjà corrigée).
     * Puissances aberrantes (> 1 000 kW, souvent saisies en W) ramenées en kW.
     */
    fun group(rows: List<JsonObject>): List<ChargingStation> = rows.mapNotNull { r ->
        val lat = r.num("consolidated_latitude") ?: return@mapNotNull null
        val lon = r.num("consolidated_longitude") ?: return@mapNotNull null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
        val kwRaw = r.num("puissance_nominale") ?: return@mapNotNull null
        val kw = if (kwRaw > 1_000) kwRaw / 1_000 else kwRaw
        Triple(r, GeoPoint(lat, lon), kw)
    }.groupBy { (r, p, _) -> Triple((p.lat * 10_000).roundToInt(), (p.lon * 10_000).roundToInt(), r.str("nom_station")?.lowercase()) }
        .map { (_, list) ->
            val first = list.first().first
            ChargingStation(
                id = first.str("id_station_itinerance") ?: "${list.first().second.lat},${list.first().second.lon}",
                name = readableName(first),
                operator = first.str("nom_operateur") ?: first.str("nom_enseigne"),
                address = first.str("adresse_station"),
                point = list.first().second,
                maxKw = list.maxOf { it.third }.roundToInt(),
                chargePoints = list.size,
                connectors = buildSet {
                    if (list.any { it.first.flag("prise_type_combo_ccs") }) add(Connector.CCS)
                    if (list.any { it.first.flag("prise_type_chademo") }) add(Connector.CHADEMO)
                    if (list.any { it.first.flag("prise_type_2") }) add(Connector.TYPE2)
                },
                free = list.all { it.first.flag("gratuit") },
                price = list.firstNotNullOfOrNull { it.first.str("tarification") }?.replace(Regex("\\s+"), " ")?.take(120),
                hours = list.firstNotNullOfOrNull { it.first.str("horaires") }?.take(80),
                access = list.firstNotNullOfOrNull { it.first.str("condition_acces") },
                pdcIds = list.mapNotNull { it.first.str("id_pdc_itinerance") }.distinct(),
            )
        }
}
