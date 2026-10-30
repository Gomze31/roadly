package fr.roadly.feature.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alternatives: RouteAlternativesUseCase,
    private val vehicleRepo: VehicleProfileRepository,
    private val location: LocationRepository,
    private val config: ProviderConfigRepository,
) : ViewModel() {

    // Arguments lus par nom de champ de RouteRoute : c'est ce que fait toRoute(), mais sans
    // passer par Bundle (indisponible en test unitaire JVM sans Robolectric).
    private val destName: String = savedStateHandle.get<String>("destName").orEmpty()
    private val destination = GeoPoint(
        requireNotNull(savedStateHandle.get<Double>("destLat")) { "destLat manquant" },
        requireNotNull(savedStateHandle.get<Double>("destLon")) { "destLon manquant" },
    )
    private val origin = MutableStateFlow(location.lastKnown?.point)
    private val departAt = MutableStateFlow(System.currentTimeMillis())
    private val activeId = MutableStateFlow<String?>(null)
    private val isDark = MutableStateFlow(true)
    private val computed = MutableStateFlow<RouteUiState>(RouteUiState.Loading)
    private val reload = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            combine(origin, departAt, vehicleRepo.profile, reload) { o, d, v, _ -> Triple(o, d, v) }.collect { (o, d, v) ->
                if (o == null) { computed.value = RouteUiState.NoOrigin(destName); return@collect }
                computed.value = RouteUiState.Loading
                when (val r = alternatives(o, destination, v, d)) {
                    is Outcome.Success -> {
                        val options = r.value
                        // L'option active doit toujours exister dans la liste affichée.
                        if (activeId.value == null || options.none { it.route.id == activeId.value }) activeId.value = options.first().route.id
                        computed.value = RouteUiState.Content(o, destName, options, activeId.value!!, v != null, d)
                    }
                    is Outcome.Failure -> computed.value = RouteUiState.Error(r.error)
                }
            }
        }
    }

    val uiState: StateFlow<RouteScreenState> = combine(computed, activeId, isDark, config.config) { s, id, dark, cfg ->
        val phase = if (s is RouteUiState.Content) s.copy(activeId = id ?: s.activeId) else s
        RouteScreenState(styleUrl = if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl, phase = phase)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteScreenState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun selectRoute(id: String) { activeId.value = id }
    fun setDepartAt(epochMs: Long) { departAt.value = epochMs }
    fun retry() { reload.value++ }
    /** Origine choisie par appui long sur la carte quand la position est inconnue. */
    fun setOrigin(point: GeoPoint) { origin.value = point }
    val destinationPoint: GeoPoint get() = destination
}
