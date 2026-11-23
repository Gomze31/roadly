package fr.roadly.core.data.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

/**
 * Lecture des réglages qui n'échoue jamais sur une erreur d'entrée-sortie : le dernier contenu lu est rendu, ou des réglages
 * vides (donc les valeurs par défaut) si rien n'a encore été lu. Sans cela, une seule erreur de lecture du fichier arrêtait
 * le guidage (NAV-6). Les écritures ne sont pas modifiées : leur échec reste signalé à l'appelant.
 */
class ResilientPreferencesDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
    private val lastRead = AtomicReference<Preferences>(emptyPreferences())

    override val data: Flow<Preferences> = delegate.data
        .onEach { lastRead.set(it) }
        .catch { e -> if (e is IOException) emit(lastRead.get()) else throw e }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        delegate.updateData(transform).also { lastRead.set(it) }
}
