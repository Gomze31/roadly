package fr.roadly.core.geocoding

import fr.roadly.core.common.Outcome
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import okhttp3.HttpUrl
import java.net.URLEncoder
import javax.inject.Qualifier

/** Fournisseur de géocodage : recherche directe (texte vers lieux) et inverse (point vers lieu). */
interface GeocodingProvider {
    /** Identifiant stable du fournisseur, repris dans `Place.source`. */
    val id: String

    /** Recherche des lieux ; `near` sert de biais de proximité quand le fournisseur le supporte. */
    suspend fun search(query: String, near: GeoPoint?, limit: Int = 10): Outcome<List<Place>>

    /** Géocodage inverse ; `null` dans le succès signifie qu'aucun lieu n'a été trouvé. */
    suspend fun reverse(point: GeoPoint): Outcome<Place?>
}

/** Qualifier Hilt : Base Adresse Nationale. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class BanGeocoder

/** Qualifier Hilt : Photon (komoot). */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PhotonGeocoder

/**
 * Ajoute un paramètre de requête encodé façon formulaire (espace = `+`), comme dans les URL de référence
 * des API BAN et Photon. `HttpUrl` encoderait sinon l'espace en `%20` ; les deux formes sont acceptées
 * par les serveurs, mais `+` garde des URL stables et lisibles.
 */
internal fun HttpUrl.Builder.addFormQueryParameter(name: String, value: String): HttpUrl.Builder =
    addEncodedQueryParameter(name, URLEncoder.encode(value, "UTF-8"))

/** Formate une coordonnée pour une requête : jamais de notation scientifique (ex. 5.0E-4 près du méridien de Greenwich). */
internal fun Double.toQueryString(): String = String.format(java.util.Locale.ROOT, "%.6f", this)
