package fr.roadly.core.assistant.tools

import fr.roadly.core.model.Place
import fr.roadly.core.routing.RouteOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Lieux cités dans les réponses récentes ; le modèle les rappelle par `place_id` (ajout d'arrêt, destination). */
@Singleton
class ToolPlaces @Inject constructor() {
    private val places = LinkedHashMap<String, Place>()
    @Synchronized fun remember(place: Place): String {
        places.remove(place.id)
        places[place.id] = place
        while (places.size > MAX) places.remove(places.keys.first())
        return place.id
    }
    @Synchronized operator fun get(id: String): Place? = places[id]
    private companion object { const val MAX = 50 }
}

/** Dernières alternatives proposées, par identifiant court (`r1`, `r2`…). */
@Singleton
class ToolRoutes @Inject constructor() {
    private var options: Map<String, RouteOption> = emptyMap()
    @Synchronized fun replace(list: List<RouteOption>): List<Pair<String, RouteOption>> =
        list.mapIndexed { i, o -> "r${i + 1}" to o }.also { options = it.toMap() }
    @Synchronized operator fun get(id: String): RouteOption? = options[id]
}

data class CardRow(val title: String, val detail: String, val placeId: String? = null)
data class ResultCard(val title: String, val rows: List<CardRow>)

/** Carte de résultat affichée dans la fenêtre compacte (≤ 3 lignes). */
@Singleton
class AssistantCards @Inject constructor() {
    private val _card = MutableStateFlow<ResultCard?>(null)
    val card: StateFlow<ResultCard?> = _card.asStateFlow()
    fun show(card: ResultCard) { _card.value = card.copy(rows = card.rows.take(3)) }
    fun clear() { _card.value = null }
}
