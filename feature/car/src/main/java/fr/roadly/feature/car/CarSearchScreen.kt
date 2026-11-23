package fr.roadly.feature.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.lifecycle.lifecycleScope
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.model.Place
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ce que la recherche enregistre avec le lieu choisi (accueil : « Domicile » ou « Travail » pas encore défini). */
internal enum class CarSearchAssign { HOME, WORK }

/**
 * État affiché par la recherche de la voiture, sans dépendance Android (testé sur JVM).
 *
 * Constat en voiture : pendant une dictée, le champ se vidait dès que la phrase s'allongeait. Cause : le gabarit était renvoyé à
 * l'hôte à chaque texte partiel avec un texte initial, un état du clavier et un état de chargement différents ; l'hôte
 * réinitialisait alors le champ — et la dictée en cours — avec un texte déjà périmé. Règles appliquées ici :
 * - [initialText] et [showKeyboard] ne changent jamais pendant la vie de l'écran ;
 * - l'indicateur de chargement ne remplace la liste que s'il n'y a rien d'autre à montrer ;
 * - un texte partiel ne redessine rien ([onText] renvoie faux) tant que la liste affichée reste la même.
 */
internal class CarSearchModel(initialQuery: String?, private val minQuery: Int = MIN_QUERY) {
    val initialText: String = initialQuery.orEmpty()
    val showKeyboard: Boolean = initialQuery.isNullOrBlank()

    var query: String = initialText
        private set
    var results: List<Place> = emptyList()
        private set
    var recents: List<Place> = emptyList()
        private set
    /** Requête dont [results] est la réponse ; null tant qu'aucune recherche n'a abouti. */
    var answered: String? = null
        private set
    var searching = false
        private set

    val short: Boolean get() = query.trim().length < minQuery
    /** Lieux affichés : récents tant que la saisie est trop courte, sinon les derniers résultats reçus (même d'une requête précédente). */
    val shown: List<Place> get() = if (short) recents else results
    /** Indicateur de chargement plein écran : seulement sans rien à montrer. */
    val loading: Boolean get() = searching && !short && results.isEmpty()
    val noResult: Boolean get() = !short && !searching && results.isEmpty() && answered == query.trim()

    /** Nouveau texte du champ ; vrai si l'affichage change (sinon l'hôte n'est pas sollicité : la dictée continue). */
    fun onText(text: String): Boolean {
        val before = snapshot()
        query = text
        if (short) { searching = false; results = emptyList(); answered = null }
        return snapshot() != before
    }

    fun onSearchStarted(): Boolean {
        val before = snapshot()
        searching = true
        return snapshot() != before
    }

    fun onResults(forQuery: String, places: List<Place>): Boolean {
        val before = snapshot()
        searching = false
        results = places
        answered = forQuery
        return snapshot() != before
    }

    fun onRecents(places: List<Place>): Boolean {
        val before = snapshot()
        recents = places
        return snapshot() != before
    }

    private fun snapshot(): List<Any?> = listOf(shown, loading, noResult, short)

    companion object { const val MIN_QUERY = 3 }
}

/**
 * Recherche d'adresse ou de lieu (BAN + Photon, comme sur le téléphone), autour de la position du véhicule.
 * [assign] : le lieu choisi est aussi enregistré comme domicile ou travail (ligne « à définir » de l'accueil).
 */
internal class CarSearchScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val renderer: CarMapRenderer,
    initialQuery: String?,
    private val assign: CarSearchAssign? = null,
) : Screen(carContext) {
    private val model = CarSearchModel(initialQuery)
    private var job: Job? = null

    init {
        if (!initialQuery.isNullOrBlank()) search(initialQuery, 0)
        lifecycleScope.launch { deps.recents.observe().collect { if (model.onRecents(it.take(MAX_RESULTS))) invalidate() } }
    }

    private fun search(text: String, debounceMs: Long) {
        job?.cancel()
        if (model.onText(text)) invalidate()
        val q = text.trim()
        if (model.short) return
        job = lifecycleScope.launch {
            delay(debounceMs)
            if (model.onSearchStarted()) invalidate()
            val places = when (val r = deps.geocoder.search(q, near = deps.location.lastKnown?.point, limit = MAX_RESULTS)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> {
                    CarToast.makeText(carContext, r.error.userMessage(), CarToast.LENGTH_LONG).show()
                    emptyList()
                }
            }
            if (model.onResults(q, places)) invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val hint = when (assign) {
            CarSearchAssign.HOME -> R.string.car_search_hint_home
            CarSearchAssign.WORK -> R.string.car_search_hint_work
            null -> R.string.car_search_hint
        }
        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) = search(searchText, DEBOUNCE_MS)
            override fun onSearchSubmitted(searchText: String) = search(searchText, 0)
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(hint))
            // Constants pendant toute la vie de l'écran : voir [CarSearchModel].
            .setInitialSearchText(model.initialText)
            .setShowKeyboardByDefault(model.showKeyboard)
        if (model.loading) return builder.setLoading(true).build()
        val items = ItemList.Builder()
        val here = deps.location.lastKnown?.point
        val icon = CarIcons.of(if (model.short) Icons.Default.History else Icons.Default.Place, tint = CarColor.DEFAULT)
        for (place in model.shown) {
            val detail = listOfNotNull(here?.let { Formatters.distance(it.distanceTo(place.point)) }, place.address?.takeIf { it != place.name })
                .joinToString(" · ")
            items.addItem(
                Row.Builder().setTitle(place.name).setImage(icon).apply { if (detail.isNotEmpty()) addText(detail) }
                    .setOnClickListener { choose(place) }.build(),
            )
        }
        when {
            model.short && model.shown.isEmpty() -> items.setNoItemsMessage(carContext.getString(R.string.car_search_empty))
            model.noResult -> items.setNoItemsMessage(carContext.getString(R.string.car_no_result))
        }
        return builder.setItemList(items.build()).build()
    }

    private fun choose(place: Place) {
        lifecycleScope.launch {
            deps.recents.record(place)
            when (assign) {
                CarSearchAssign.HOME -> { deps.favorites.setHome(place); CarToast.makeText(carContext, R.string.car_home_saved, CarToast.LENGTH_SHORT).show() }
                CarSearchAssign.WORK -> { deps.favorites.setWork(place); CarToast.makeText(carContext, R.string.car_work_saved, CarToast.LENGTH_SHORT).show() }
                null -> Unit
            }
        }
        screenManager.push(CarRoutePreviewScreen(carContext, deps, renderer, place))
    }

    private companion object {
        const val MAX_RESULTS = 6
        const val DEBOUNCE_MS = 400L
    }
}
