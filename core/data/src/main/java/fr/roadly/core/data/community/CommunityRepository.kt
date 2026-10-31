package fr.roadly.core.data.community

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ReportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Service communautaire : signalements entre conducteurs. Sans configuration, tout renvoie `ProviderUnavailable`. */
interface CommunityRepository {
    val isConfigured: Flow<Boolean>
    suspend fun report(kind: ReportKind, at: GeoPoint, headingDeg: Float?): Outcome<CommunityReport>
    suspend fun nearby(box: BoundingBox): Outcome<List<CommunityReport>>
    suspend fun vote(reportId: String, confirm: Boolean): Outcome<CommunityReport>
}

/** PostgREST `rest/v1/rpc/<fonction>` du schéma `backend/supabase/schema.sql` ; en-têtes `apikey` + `Authorization: Bearer <anon>`. */
@Singleton
class SupabaseCommunityRepository @Inject constructor(
    private val api: ApiClient,
    private val configRepo: CommunityConfigRepository,
    private val json: Json,
) : CommunityRepository {
    override val isConfigured: Flow<Boolean> = configRepo.config.map { it.isConfigured }

    private suspend fun rpc(name: String, body: JsonObject): Outcome<String> {
        val cfg = configRepo.config.first()
        if (!cfg.isConfigured) return Outcome.Failure(AppError.ProviderUnavailable(SERVICE))
        val url = "${cfg.supabaseUrl}/rest/v1/rpc/$name".toHttpUrlOrNull() ?: return Outcome.Failure(AppError.ProviderUnavailable(SERVICE))
        return api.postJson(url, body.toString(), mapOf("apikey" to cfg.anonKey, "Authorization" to "Bearer ${cfg.anonKey}"))
    }

    override suspend fun report(kind: ReportKind, at: GeoPoint, headingDeg: Float?): Outcome<CommunityReport> {
        val device = configRepo.deviceId()
        val body = buildJsonObject {
            put("p_kind", kind.name); put("p_lat", at.lat); put("p_lon", at.lon); put("p_device", device)
            if (headingDeg != null) put("p_heading", Math.floorMod(headingDeg.toInt(), 360)) else put("p_heading", null as Int?)
        }
        return when (val r = rpc("create_report", body)) {
            is Outcome.Failure -> Outcome.Failure(mapError(r.error))
            is Outcome.Success -> parseOne(r.value, device)
        }
    }

    override suspend fun nearby(box: BoundingBox): Outcome<List<CommunityReport>> {
        val device = configRepo.deviceId()
        val body = buildJsonObject { put("south", box.south); put("west", box.west); put("north", box.north); put("east", box.east) }
        return when (val r = rpc("reports_in_box", body)) {
            is Outcome.Failure -> Outcome.Failure(mapError(r.error))
            is Outcome.Success -> try {
                Outcome.Success(json.parseToJsonElement(r.value).jsonArray.mapNotNull { it.jsonObject.toReport(device) })
            } catch (e: Exception) { Outcome.Failure(AppError.Parse(e.message ?: SERVICE)) }
        }
    }

    override suspend fun vote(reportId: String, confirm: Boolean): Outcome<CommunityReport> {
        val device = configRepo.deviceId()
        val body = buildJsonObject { put("p_report", reportId); put("p_confirm", confirm); put("p_device", device) }
        return when (val r = rpc("vote_report", body)) {
            is Outcome.Failure -> Outcome.Failure(mapError(r.error))
            is Outcome.Success -> parseOne(r.value, device)
        }
    }

    private fun parseOne(body: String, device: String): Outcome<CommunityReport> = try {
        val el = json.parseToJsonElement(body)
        val obj = if (el is JsonArray) el.firstOrNull()?.jsonObject else el.jsonObject
        obj?.toReport(device)?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Parse(SERVICE))
    } catch (e: Exception) { Outcome.Failure(AppError.Parse(e.message ?: SERVICE)) }

    /** Erreurs métier PostgreSQL (`P0001` anti-spam, `P0003` propre signalement) → `RateLimited` / `NoResult` plutôt qu'un code HTTP brut. */
    private fun mapError(e: AppError): AppError = if (e is AppError.Http && e.code == 400) {
        when {
            e.body.contains("rate_limited") -> AppError.RateLimited
            e.body.contains("own_report") || e.body.contains("not_found") -> AppError.NoResult
            else -> e
        }
    } else e

    companion object {
        const val SERVICE = "communauté"

        internal fun JsonObject.toReport(device: String): CommunityReport? {
            val id = this["id"]?.jsonPrimitive?.contentOrNull ?: return null
            val kind = this["kind"]?.jsonPrimitive?.contentOrNull?.let { k -> ReportKind.entries.firstOrNull { it.name == k } } ?: return null
            val lat = this["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
            val lon = this["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
            return CommunityReport(
                id = id, kind = kind, point = GeoPoint(lat, lon),
                createdAtEpochMs = parseDate(this["created_at"]?.jsonPrimitive?.contentOrNull) ?: 0L,
                expiresAtEpochMs = parseDate(this["expires_at"]?.jsonPrimitive?.contentOrNull) ?: 0L,
                confirmations = this["confirmations"]?.jsonPrimitive?.intOrNull ?: 0,
                denials = this["denials"]?.jsonPrimitive?.intOrNull ?: 0,
                mine = this["device_id"]?.jsonPrimitive?.contentOrNull == device,
            ).takeIf { this["active"]?.jsonPrimitive?.booleanOrNull != false }
        }

        private fun parseDate(iso: String?): Long? = iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    }
}
