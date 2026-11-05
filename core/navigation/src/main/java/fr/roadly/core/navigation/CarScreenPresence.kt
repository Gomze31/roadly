package fr.roadly.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * L'écran Android Auto de Roadly est affiché. Le téléphone est alors souvent verrouillé ou sur une autre application : sans
 * service de premier plan `location`, Android suspend la position (demande « inactive ») et la carte de la voiture reste figée.
 * [NavigationService] reste donc au premier plan tant que cet état est vrai.
 */
@Singleton
class CarScreenPresence @Inject constructor() {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun set(active: Boolean) { _active.value = active }
}
