package fr.roadly.core.geocoding

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/** BAN d'abord (adresses France), Photon ensuite (POI, villes, Europe). Fusion, déduplication, repli. Pas de troncature à `limit` après fusion : à l'appelant de couper. */
@Singleton
class CompositeGeocoder @Inject constructor(
    @param:BanGeocoder private val ban: GeocodingProvider,
    @param:PhotonGeocoder private val photon: GeocodingProvider,
) : GeocodingProvider {
    override val id = "composite"

    override suspend fun search(query: String, near: GeoPoint?, limit: Int): Outcome<List<Place>> {
        // Une saisie de coordonnées court-circuite les fournisseurs réseau.
        val coord = CoordinateParser.parse(query)
        if (coord != null) {
            return Outcome.Success(listOf(Place("coord:${coord.lat},${coord.lon}", "Coordonnées GPS", "%.5f, %.5f".format(java.util.Locale.ROOT, coord.lat, coord.lon), coord, PlaceKind.COORDINATES, "local")))
        }
        val (banR, photonR) = coroutineScope {
            val a = async { safely { ban.search(query, near, limit) } }
            val b = async { safely { photon.search(query, near, limit) } }
            a.await() to b.await()
        }
        val merged = mutableListOf<Place>()
        fun addAll(r: Outcome<List<Place>>) {
            if (r is Outcome.Success) r.value.forEach { p -> if (merged.none { it.isSameAs(p) }) merged += p }
        }
        addAll(banR); addAll(photonR)
        return when {
            merged.isNotEmpty() -> Outcome.Success(merged)
            banR is Outcome.Failure && photonR is Outcome.Failure -> Outcome.Failure(AppError.ProviderUnavailable("géocodage"))
            else -> Outcome.Success(emptyList())
        }
    }

    override suspend fun reverse(point: GeoPoint): Outcome<Place?> {
        val r = ban.reverse(point)
        if (r is Outcome.Success && r.value != null) return r
        return photon.reverse(point)
    }

    /** Doublon : à moins de 30 m et même nom une fois normalisé (casse, accents, ponctuation). */
    private fun Place.isSameAs(o: Place): Boolean = point.distanceTo(o.point) < 30.0 && name.normalized() == o.name.normalized()
    private fun String.normalized() = Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
}

/** Isole un fournisseur : toute exception (hors annulation) devient un échec, pour ne pas annuler le fournisseur frère. */
private suspend fun safely(block: suspend () -> Outcome<List<Place>>): Outcome<List<Place>> =
    runCatching { block() }.getOrElse { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        Outcome.Failure(AppError.Unknown(e.message))
    }
