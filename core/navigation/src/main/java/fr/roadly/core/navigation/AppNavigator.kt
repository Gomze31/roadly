package fr.roadly.core.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Écran demandé hors UI (assistant) ; [OpenSearch.category] = `PoiCategory.name` ou null. */
sealed interface NavRequest {
    data class OpenSearch(val category: String?) : NavRequest
    data object OpenGuidance : NavRequest
    data object OpenSettings : NavRequest
}

/** Canal à consommateur unique (le `RoadlyNavHost`) : une demande émise sans écouteur attend, puis est livrée une seule fois. */
@Singleton
class AppNavigator @Inject constructor() {
    private val channel = Channel<NavRequest>(Channel.BUFFERED)
    val requests: Flow<NavRequest> = channel.receiveAsFlow()
    fun navigate(request: NavRequest) { channel.trySend(request) }
}
