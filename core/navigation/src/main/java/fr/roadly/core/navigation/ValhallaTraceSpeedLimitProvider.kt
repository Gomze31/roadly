package fr.roadly.core.navigation

import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Limitation via Valhalla `trace_attributes` (map matching des derniers fixes). Actif uniquement sur une
 * instance auto-hébergée (spec §2 et §8) ; au plus un appel toutes les 5 s.
 */
@Singleton
class ValhallaTraceSpeedLimitProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
    private val clock: NavigationClock,
) : SpeedLimitProvider {
    private val mutex = Mutex()
    private var lastCallAtMs: Long? = null
    private var lastValue: Int? = null

    override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> = mutex.withLock {
        val base = try {
            config.config.first().valhallaBaseUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock Outcome.Success(null)
        }
        val url = "${base.trimEnd('/')}/trace_attributes".toHttpUrlOrNull() ?: return@withLock Outcome.Success(null)
        if (isPublicInstance(url)) return@withLock Outcome.Success(null)
        if (recent.size < 2) return@withLock Outcome.Success(lastValue)
        val now = clock.nowMs()
        val last = lastCallAtMs
        if (last != null && now - last < GuidanceConstants.SPEED_LIMIT_MIN_INTERVAL_MS) return@withLock Outcome.Success(lastValue)
        lastCallAtMs = now
        val body = buildJsonObject {
            putJsonArray("shape") { recent.takeLast(GuidanceConstants.RECENT_FIXES).forEach { f -> addJsonObject { put("lat", f.point.lat); put("lon", f.point.lon) } } }
            put("costing", "auto")
            put("shape_match", "map_snap")
            put("units", "kilometers")
            putJsonObject("filters") {
                putJsonArray("attributes") { add("edge.speed_limit"); add("edge.names"); add("edge.road_class") }
                put("action", "include")
            }
        }
        when (val r = api.postJson(url, body.toString())) {
            is Outcome.Success -> {
                lastValue = parse(r.value)
                Outcome.Success(lastValue)
            }
            is Outcome.Failure -> {
                lastValue = null
                r
            }
        }
    }

    private fun parse(text: String): Int? = runCatching {
        val edges = json.parseToJsonElement(text).jsonObject["edges"]?.jsonArray ?: return null
        val last = edges.lastOrNull() as? JsonObject ?: return null
        (last["speed_limit"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..254 }
    }.getOrNull()

    companion object {
        /** Instance publique FOSSGIS (fair-use, développement) : jamais interrogée pour la limitation. */
        fun isPublicInstance(url: HttpUrl): Boolean = url.host == "openstreetmap.de" || url.host.endsWith(".openstreetmap.de")
    }
}
