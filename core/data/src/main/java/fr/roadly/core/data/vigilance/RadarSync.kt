package fr.roadly.core.data.vigilance

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.db.VigilanceZoneEntity
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.radar.CommunityCamera
import fr.roadly.core.radar.CommunityMerge
import fr.roadly.core.radar.CommunityReport
import fr.roadly.core.radar.ControlledDirection
import fr.roadly.core.radar.RadarSourceIds
import fr.roadly.core.radar.MergeReport
import fr.roadly.core.radar.Radar
import fr.roadly.core.radar.RadarDiff
import fr.roadly.core.radar.RadarKind
import fr.roadly.core.radar.RadarMerger
import fr.roadly.core.radar.RadarQuality
import fr.roadly.core.radar.RadarSources
import fr.roadly.core.radar.RadarStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Sources officielles de la base radar (Licence Ouverte 2.0) ; voir `docs/RADAR-SOURCES.md`. */
object RadarSourceUrls {
    /** API data.gouv.fr du jeu « Liste des radars fixes en France » (DSR) : la ressource CSV la plus récente est choisie. */
    const val FIXED_DATASET_API = "https://www.data.gouv.fr/api/1/datasets/liste-des-radars-fixes-en-france/"
    /** Repli si l'API ne répond pas : fichier de décembre 2025. */
    const val FIXED_FALLBACK_CSV = "https://static.data.gouv.fr/resources/liste-des-radars-fixes-en-france/20251230-134204/jeu-de-donnees-liste-des-radars-fixes-en-france-12-2025.csv"
    const val FIXED_DATASET_PAGE = "https://www.data.gouv.fr/datasets/liste-des-radars-fixes-en-france/"
    /** « Radars automatiques » (2018) : sens, route, commune, longueur des tronçons, utilisés seulement en enrichissement. */
    const val ENRICH_2018_CSV = "https://static.data.gouv.fr/resources/radars-automatiques/20181025-141231/radars.csv"
    /**
     * MapAtlas Camera Index, France (signalements de conducteurs, CC BY 4.0, « no signup, no API key and no request limit ») :
     * corroboration, sens indicatif, équipements absents de la liste officielle (option). Lien de téléchargement public de la page.
     */
    const val MAPATLAS_FR_GEOJSON = "https://mapatlas.eu/data/camera-index/FR.geojson"
    const val MAPATLAS_PAGE = "https://mapatlas.eu/tools/speed-camera-map"
}

/**
 * Version de la chaîne d'enrichissement : une base appliquée avec une autre version est refaite à la prochaine vérification,
 * même si la liste officielle n'a pas changé.
 */
const val RADAR_ENRICHMENT_VERSION = "2018+mapatlas"

enum class RadarSyncStatus { APPLIED, UNCHANGED, REJECTED, FAILED }

/** Une synchronisation (journal `radar_versions`). */
data class RadarVersion(
    val fetchedAtMs: Long,
    val status: RadarSyncStatus,
    val sourceUrl: String?,
    val sourceModified: String?,
    val total: Int = 0,
    val diff: RadarDiff = RadarDiff(0, 0, 0, 0),
    val enriched: Int = 0,
    val withDirection: Int = 0,
    val highPrecision: Int = 0,
    val rejected: Int = 0,
    val message: String? = null,
    /** Sources d'enrichissement appliquées (« 2018+mapatlas:2024-05-21 ») ; null = antérieur. */
    val enrichment: String? = null,
    /** Signalements communautaires : appariés à un équipement officiel, sens indicatifs, équipements en plus (option). */
    val community: fr.roadly.core.radar.CommunityReport? = null,
)

/** Stockage local de la base radar (Room en production, mémoire en test). */
interface RadarStore {
    /** Équipements issus d'une synchronisation, retirés compris. */
    suspend fun synced(): List<Radar>
    suspend fun lastSuccess(): RadarVersion?
    suspend fun apply(radars: List<Radar>, removedIds: List<String>, version: RadarVersion)
    suspend fun log(version: RadarVersion)
}

/**
 * Synchronisation incrémentale : la ressource la plus récente est repérée par l'API data.gouv.fr (date de modification) ;
 * inchangée depuis la dernière synchronisation réussie, rien n'est téléchargé. Sinon : téléchargement, fusion avec le jeu
 * 2018 (enrichissement), contrôle qualité (une baisse de plus de 30 % ou moins de 1 000 équipements rejette la version et
 * garde la précédente), écart avec la base locale, écriture atomique. Les équipements disparus restent en base, marqués retirés.
 */
class RadarSync(
    private val fetch: suspend (String) -> Outcome<String>,
    private val store: RadarStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Resource(val url: String, val modified: String?)

    suspend fun sync(force: Boolean): Outcome<RadarVersion> {
        val now = clock()
        return try {
            run(force, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val v = RadarVersion(now, RadarSyncStatus.FAILED, null, null, message = e.message)
            runCatching { store.log(v) }
            Outcome.Failure(AppError.Unknown(e.message))
        }
    }

    private suspend fun run(force: Boolean, now: Long): Outcome<RadarVersion> {
        val resource = latestResource()
        val last = store.lastSuccess()
        val sameEnrichment = last?.enrichment?.substringBefore(':') == RADAR_ENRICHMENT_VERSION
        if (!force && last != null && sameEnrichment && resource.modified != null && last.sourceModified == resource.modified && last.sourceUrl == resource.url) {
            val v = RadarVersion(now, RadarSyncStatus.UNCHANGED, resource.url, resource.modified, total = last.total, message = "Source inchangée")
            store.log(v)
            return Outcome.Success(v)
        }
        val primaryText = when (val r = fetch(resource.url)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> { store.log(RadarVersion(now, RadarSyncStatus.FAILED, resource.url, resource.modified, message = "Téléchargement impossible")); return r }
        }
        val primary = RadarSources.parseFixedList(primaryText)
        // Enrichissement facultatif : sans lui, la base reste valable (sans sens ni route).
        val enrichment = (fetch(RadarSourceUrls.ENRICH_2018_CSV) as? Outcome.Success)?.value?.let(RadarSources::parse2018).orEmpty()
        val official = RadarMerger.merge(primary, enrichment, now, resource.modified?.take(10))
        // Signalements communautaires facultatifs : corroboration et sens indicatif ; leur absence ne bloque rien.
        val community = (fetch(RadarSourceUrls.MAPATLAS_FR_GEOJSON) as? Outcome.Success)?.value?.let(::parseMapAtlas)
        val withCommunity = community?.let { (snapshot, cams) -> CommunityMerge.merge(official.radars, cams, now, snapshot) }
        val merged = official.copy(radars = withCommunity?.radars ?: official.radars)
        val enrichmentTag = RADAR_ENRICHMENT_VERSION + (community?.first?.let { ":$it" } ?: "")
        val previous = store.synced()
        val previousActive = previous.count { it.status == RadarStatus.ACTIVE }
        val report = merged.report
        // Contrôle qualité sur la seule liste officielle (les équipements communautaires ne la remplacent jamais).
        val officialCount = official.radars.size
        val previousOfficial = previous.count { it.status == RadarStatus.ACTIVE && it.source != RadarSourceIds.MAPATLAS }
        val gateMessage = when {
            officialCount < MIN_RADARS -> "Seulement $officialCount équipements lisibles"
            previousOfficial > 0 && officialCount < previousOfficial * MIN_KEEP_RATIO -> "Baisse de $previousOfficial à $officialCount équipements"
            else -> null
        }
        if (gateMessage != null) {
            val v = version(now, RadarSyncStatus.REJECTED, resource, report, RadarDiff(0, 0, 0, 0), gateMessage, enrichmentTag, withCommunity?.report)
            store.log(v)
            return Outcome.Failure(AppError.Parse("radars: $gateMessage"))
        }
        val diff = RadarDiff.of(previous, merged.radars)
        val nextIds = merged.radars.mapTo(HashSet()) { it.id }
        val removed = previous.filter { it.status == RadarStatus.ACTIVE && it.id !in nextIds }.map { it.id }
        val v = version(now, RadarSyncStatus.APPLIED, resource, report, diff, null, enrichmentTag, withCommunity?.report)
        store.apply(merged.radars, removed, v)
        return Outcome.Success(v)
    }

    private fun version(
        now: Long, status: RadarSyncStatus, res: Resource, rep: MergeReport, diff: RadarDiff, message: String?,
        enrichment: String?, community: CommunityReport?,
    ) = RadarVersion(
        now, status, res.url, res.modified, total = rep.kept, diff = diff, enriched = rep.enriched,
        withDirection = rep.withDirection + (community?.directionHints ?: 0),
        highPrecision = rep.highPrecision, rejected = rep.rejectedInvalid + rep.rejectedDuplicateId,
        message = message ?: community?.let { "MapAtlas : ${it.corroborated} confirmés, ${it.directionHints} sens indicatifs, ${it.added} en plus" },
        enrichment = enrichment, community = community,
    )

    /**
     * Instantané MapAtlas (GeoJSON) → (date de l'instantané, équipements) ; null si illisible. Seules les vitesses réglementaires
     * sont gardées (l'export contient 140 km/h, inexistant en France) ; `facingDeg` n'est pas utilisé (conforme dans ≈ 55 % des cas).
     */
    fun parseMapAtlas(body: String): Pair<String?, List<CommunityCamera>>? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val snapshot = (root["snapshot"] as? JsonPrimitive)?.content
        val features = root["features"] as? JsonArray ?: return null
        snapshot to features.mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val props = o["properties"] as? JsonObject ?: return@mapNotNull null
            val coords = ((o["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray) ?: return@mapNotNull null
            val lon = (coords.getOrNull(0) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = (coords.getOrNull(1) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val point = GeoPoint(lat, lon)
            if (!RadarQuality.inFrance(lat, lon)) return@mapNotNull null
            val ref = (props["ref"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kind = CommunityMerge.kindOf((props["class"] as? JsonPrimitive)?.content)
            if (kind == RadarKind.UNKNOWN) return@mapNotNull null
            val limit = (props["limitKph"] as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it in RadarQuality.ALLOWED_LIMITS }
            CommunityCamera(ref, point, kind, limit, CommunityMerge.approachDeg((props["approach"] as? JsonPrimitive)?.content))
        }
    }.getOrNull()

    /** Ressource CSV la plus récente du jeu ; repli sur l'URL connue si l'API échoue. */
    suspend fun latestResource(): Resource {
        val body = (fetch(RadarSourceUrls.FIXED_DATASET_API) as? Outcome.Success)?.value ?: return Resource(RadarSourceUrls.FIXED_FALLBACK_CSV, null)
        return parseResources(body) ?: Resource(RadarSourceUrls.FIXED_FALLBACK_CSV, null)
    }

    fun parseResources(body: String): Resource? = runCatching {
        val resources = json.parseToJsonElement(body).jsonObject["resources"] as? JsonArray ?: return null
        resources.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val format = (o["format"] as? JsonPrimitive)?.content?.lowercase()
            val url = (o["url"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            if (format != "csv" || !url.startsWith("https://")) return@mapNotNull null
            Resource(url, (o["last_modified"] as? JsonPrimitive)?.content)
        }.maxByOrNull { it.modified.orEmpty() }
    }.getOrNull()

    companion object {
        const val MIN_RADARS = 1_000
        const val MIN_KEEP_RATIO = 0.7
    }
}

/** Correspondance entité Room ↔ modèle radar ; les lignes héritées (sans source) gardent une confiance moyenne. */
object RadarMapping {
    /** Lignes du jeu 2018 encore en base avant la première synchronisation : alerte visuelle seulement. */
    const val LEGACY_CONFIDENCE = 50

    fun toRadar(e: VigilanceZoneEntity): Radar = Radar(
        id = e.id, point = GeoPoint(e.lat, e.lon),
        kind = e.kind?.let { k -> runCatching { RadarKind.valueOf(k) }.getOrNull() } ?: RadarKind.fromLabel2018(e.type) ?: RadarKind.UNKNOWN,
        speedLimitKmh = e.limitKmh?.takeIf { it in RadarQuality.ALLOWED_LIMITS },
        heavySpeedLimitKmh = e.heavyLimitKmh,
        direction = when {
            e.bothWays == true -> ControlledDirection(null, true, e.direction)
            e.bearingDeg != null -> ControlledDirection(e.bearingDeg, false, e.direction, hint = e.directionHint == true)
            e.source == null -> fr.roadly.core.radar.DirectionParser.parse(e.direction)
            e.direction != null -> ControlledDirection(null, false, e.direction)
            else -> null
        },
        road = e.road, commune = e.commune, department = e.department, country = e.country ?: "FR",
        sectionLengthM = e.sectionLengthM, commissionedAt = e.commissionedAt,
        source = e.source ?: "radars-automatiques-2018", sourceVersion = e.sourceVersion, enrichedFrom = e.enrichedFrom,
        collectedAtMs = e.updatedAt, verifiedAtMs = e.verifiedAt,
        precisionM = e.precisionM ?: 30.0,
        confidence = e.confidence ?: LEGACY_CONFIDENCE,
        status = if (e.status == RadarStatus.REMOVED.name) RadarStatus.REMOVED else RadarStatus.ACTIVE,
    )

    fun toEntity(r: Radar): VigilanceZoneEntity = VigilanceZoneEntity(
        id = r.id, lat = r.point.lat, lon = r.point.lon, direction = r.direction?.label, type = r.kind.code, road = r.road,
        limitKmh = r.speedLimitKmh, updatedAt = r.collectedAtMs, kind = r.kind.name, bearingDeg = r.direction?.bearingDeg,
        bothWays = r.direction?.bothWays, sectionLengthM = r.sectionLengthM, heavyLimitKmh = r.heavySpeedLimitKmh,
        commune = r.commune, department = r.department, country = r.country, source = r.source, sourceVersion = r.sourceVersion,
        enrichedFrom = r.enrichedFrom, commissionedAt = r.commissionedAt, verifiedAt = r.verifiedAtMs, precisionM = r.precisionM,
        confidence = r.confidence, status = r.status.name, directionHint = r.direction?.hint?.takeIf { it },
    )
}
