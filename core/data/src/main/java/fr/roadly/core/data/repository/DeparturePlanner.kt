package fr.roadly.core.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Heure choisie sur la carte « Départ » de l'accueil : partir à [epochMs], ou arriver à [epochMs] si [arrive]. */
data class DeparturePlan(val epochMs: Long, val arrive: Boolean)

/**
 * Départ planifié partagé entre l'accueil (carte « Départ ») et l'écran d'itinéraires, en mémoire seulement.
 * Un plan dont l'heure est passée vaut « Partir maintenant » ; il est effacé au démarrage du guidage.
 */
@Singleton
class DeparturePlanner @Inject constructor() {
    private val state = MutableStateFlow<DeparturePlan?>(null)
    val plan: StateFlow<DeparturePlan?> = state.asStateFlow()

    fun set(plan: DeparturePlan?) { state.value = plan }
    fun clear() { state.value = null }

    /** Plan encore valable à [nowMs] (heure future), sinon null. */
    fun active(nowMs: Long): DeparturePlan? = state.value?.takeIf { it.epochMs > nowMs }
}
