package fr.roadly.core.data.zfe

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Source des réglementations ZFE ; interface pour les doubles de test. */
interface ZfeSource {
    /** Réglementations connues (cache local, rafraîchi au plus une fois par [ZfeRepository.MAX_AGE_MS]) ; vide si jamais téléchargées. */
    suspend fun rules(): List<ZfeRule>
    /** Date (ms) du fichier en cache, null si aucun. */
    suspend fun updatedAtMs(): Long? = null
}

/**
 * Base nationale consolidée des ZFE (transport.data.gouv.fr, ressource `aires.geojson`, Licence Ouverte Etalab 2.0).
 * Le fichier (~1,8 Mo) est gardé dans `files/zfe/` : sans réseau, la dernière version téléchargée sert.
 */
@Singleton
class ZfeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiClient,
) : ZfeSource {
    private val mutex = Mutex()
    private var cached: List<ZfeRule>? = null
    private var cachedAt: Long = 0
    private val file get() = File(context.filesDir, "zfe/aires.geojson")

    override suspend fun updatedAtMs(): Long? = withContext(Dispatchers.IO) { file.takeIf { it.exists() }?.lastModified() }

    override suspend fun rules(): List<ZfeRule> = mutex.withLock {
        val now = System.currentTimeMillis()
        val f = file
        val stale = !f.exists() || now - f.lastModified() > MAX_AGE_MS
        if (stale) {
            when (val r = api.get(URL.toHttpUrl())) {
                is Outcome.Success -> if (ZfeParser.parse(r.value).isNotEmpty()) withContext(Dispatchers.IO) {
                    f.parentFile?.mkdirs()
                    val tmp = File(f.parentFile, "aires.tmp")
                    tmp.writeText(r.value)
                    tmp.renameTo(f)
                }
                is Outcome.Failure -> Unit // hors ligne : on garde le cache
            }
        }
        val stamp = if (f.exists()) f.lastModified() else 0
        cached?.takeIf { stamp == cachedAt }?.let { return it }
        val body = withContext(Dispatchers.IO) { if (f.exists()) f.readText() else null } ?: return emptyList()
        val names = withContext(Dispatchers.IO) { runCatching { context.assets.open("zfe_ids.csv").bufferedReader().readText() }.getOrDefault("") }
        return ZfeParser.parse(body, ZfeParser.parseNames(names)).also { cached = it; cachedAt = stamp }
    }

    companion object {
        /** Lien stable de la ressource `aires.geojson` (redirige vers static.data.gouv.fr). */
        const val URL = "https://transport.data.gouv.fr/resources/79567/download"
        const val MAX_AGE_MS = 7L * 24 * 3600 * 1000
    }
}
