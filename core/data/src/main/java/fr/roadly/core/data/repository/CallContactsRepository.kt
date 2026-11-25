package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Un proche à appeler pendant le trajet : nom affiché et numéro tel que le composeur du téléphone le recevra. */
@Serializable
data class CallContact(val name: String, val number: String)

/**
 * Proches à appeler depuis le guidage. Ils ne sont enregistrés que sur ce téléphone : Roadly ne lit pas le carnet d'adresses
 * (la fiche est choisie par l'utilisateur dans le sélecteur d'Android) et ne passe pas l'appel lui-même.
 */
interface CallContactsRepository {
    val contacts: Flow<List<CallContact>>
    /** Ajoute [contact] en tête (ou le remonte s'il y est déjà) ; au-delà de [CallContacts.MAX], le plus ancien sort. */
    suspend fun add(contact: CallContact)
    suspend fun remove(number: String)
}

@Singleton
class DataStoreCallContactsRepository @Inject constructor(private val store: DataStore<Preferences>) : CallContactsRepository {
    private val key = stringPreferencesKey("call.contacts")

    override val contacts: Flow<List<CallContact>> = store.data.map { CallContacts.decode(it[key]) }

    override suspend fun add(contact: CallContact) {
        store.edit { p -> p[key] = CallContacts.encode(CallContacts.add(CallContacts.decode(p[key]), contact)) }
    }

    override suspend fun remove(number: String) {
        store.edit { p -> p[key] = CallContacts.encode(CallContacts.decode(p[key]).filterNot { CallContacts.sameNumber(it.number, number) }) }
    }
}

/** Liste des proches : validation des numéros, dédoublonnage, plafond et (dé)codage JSON tolérant. */
object CallContacts {
    /** Quatre proches au plus : la liste doit se lire d'un coup d'œil, à l'arrêt. */
    const val MAX = 4
    private const val MAX_NAME = 40
    private val json = Json { ignoreUnknownKeys = true }

    /** Numéro réduit à ce qu'un composeur accepte (chiffres, +, *, #) ; null s'il ne reste pas au moins trois chiffres. */
    fun cleanNumber(raw: String): String? {
        val kept = raw.trim().filterIndexed { i, ch -> ch.isDigit() || ch == '*' || ch == '#' || (ch == '+' && raw.trim().indexOf('+') == i && i == 0) }
        return kept.takeIf { n -> n.count { it.isDigit() } >= 3 }
    }

    /** Même numéro, écrit avec ou sans espaces, points ou tirets. */
    fun sameNumber(a: String, b: String): Boolean = cleanNumber(a) != null && cleanNumber(a) == cleanNumber(b)

    /** [contact] nettoyé (nom borné, numéro composable), ou null si le numéro est inutilisable. */
    fun sanitize(contact: CallContact): CallContact? {
        val number = cleanNumber(contact.number) ?: return null
        val name = contact.name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).ifEmpty { number }
        return CallContact(name, number)
    }

    fun add(current: List<CallContact>, contact: CallContact): List<CallContact> {
        val clean = sanitize(contact) ?: return current
        return (listOf(clean) + current.filterNot { sameNumber(it.number, clean.number) }).take(MAX)
    }

    fun encode(list: List<CallContact>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(CallContact.serializer()), list)

    /** Valeur absente ou illisible : liste vide, jamais d'exception (une préférence abîmée ne doit pas bloquer le guidage). */
    fun decode(raw: String?): List<CallContact> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(CallContact.serializer()), raw) }
            .getOrDefault(emptyList()).mapNotNull(::sanitize).distinctBy { it.number }.take(MAX)
    }
}
