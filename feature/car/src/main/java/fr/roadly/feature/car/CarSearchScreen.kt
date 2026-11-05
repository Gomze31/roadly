package fr.roadly.feature.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.model.Place
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Recherche d'adresse ou de lieu (BAN + Photon, comme sur le téléphone), autour de la position du véhicule. */
internal class CarSearchScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val renderer: CarMapRenderer,
    private val initialQuery: String?,
) : Screen(carContext) {
    private var query = initialQuery.orEmpty()
    private var results: List<Place> = emptyList()
    private var loading = false
    private var searched = false
    private var job: Job? = null

    init {
        if (!initialQuery.isNullOrBlank()) search(initialQuery, 0)
    }

    private fun search(text: String, debounceMs: Long) {
        query = text
        job?.cancel()
        val q = text.trim()
        if (q.length < MIN_QUERY) {
            results = emptyList(); loading = false; searched = false
            invalidate()
            return
        }
        job = lifecycleScope.launch {
            delay(debounceMs)
            loading = true
            invalidate()
            when (val r = deps.geocoder.search(q, near = deps.location.lastKnown?.point, limit = MAX_RESULTS)) {
                is Outcome.Success -> results = r.value
                is Outcome.Failure -> {
                    results = emptyList()
                    CarToast.makeText(carContext, r.error.userMessage(), CarToast.LENGTH_LONG).show()
                }
            }
            loading = false
            searched = true
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) = search(searchText, DEBOUNCE_MS)
            override fun onSearchSubmitted(searchText: String) = search(searchText, 0)
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.car_search_hint))
            .setInitialSearchText(query)
            .setShowKeyboardByDefault(initialQuery.isNullOrBlank() && results.isEmpty())
        if (loading) return builder.setLoading(true).build()
        val items = ItemList.Builder()
        val here = deps.location.lastKnown?.point
        for (place in results) {
            val detail = listOfNotNull(here?.let { Formatters.distance(it.distanceTo(place.point)) }, place.address?.takeIf { it != place.name })
                .joinToString(" · ")
            items.addItem(
                Row.Builder().setTitle(place.name).apply { if (detail.isNotEmpty()) addText(detail) }
                    .setOnClickListener { choose(place) }.build(),
            )
        }
        if (searched && results.isEmpty()) items.setNoItemsMessage(carContext.getString(R.string.car_no_result))
        return builder.setItemList(items.build()).build()
    }

    private fun choose(place: Place) {
        lifecycleScope.launch { deps.recents.record(place) }
        screenManager.push(CarRoutePreviewScreen(carContext, deps, renderer, place))
    }

    private companion object {
        const val MIN_QUERY = 3
        const val MAX_RESULTS = 6
        const val DEBOUNCE_MS = 400L
    }
}
