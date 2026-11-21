package fr.roadly.core.data.repository

import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Options d'itinéraire « pour ce trajet seulement » choisies dans la recherche : elles priment sur les préférences enregistrées
 * jusqu'au démarrage du guidage (puis le guidage garde celles de son calcul). En mémoire seulement.
 */
@Singleton
class TripPreferences @Inject constructor() {
    private val state = MutableStateFlow<RoutePreferences?>(null)
    val override: StateFlow<RoutePreferences?> = state.asStateFlow()

    fun set(value: RoutePreferences?) { state.value = value }
    fun clear() { state.value = null }

    /** Options en vigueur : celles du trajet si choisies, sinon les préférences enregistrées. */
    fun effective(saved: Flow<RoutePreferences>): Flow<RoutePreferences> = combine(saved, state) { s, o -> o ?: s }
}
