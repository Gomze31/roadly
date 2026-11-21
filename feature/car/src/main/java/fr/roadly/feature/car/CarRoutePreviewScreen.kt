package fr.roadly.feature.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.model.Place
import fr.roadly.core.routing.RouteOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Itinéraires vers [place] (mêmes variantes que le téléphone : préférences et profil du véhicule).
 * Niveau 7 et plus : `MapWithContentTemplate` + liste, une ligne = démarrer. En dessous : `RoutePreviewNavigationTemplate`,
 * une ligne = choisir la variante (surlignée sur la carte), « Démarrer » = partir.
 */
internal class CarRoutePreviewScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val renderer: CarMapRenderer,
    private val place: Place,
) : Screen(carContext) {
    private var options: List<RouteOption>? = null
    private var error: AppError? = null
    /** Zones de danger par variante (même calcul que le guidage). */
    private var zones: Map<String, Int> = emptyMap()
    /** ZFE la plus contraignante traversée par chaque variante (réglage « Zones à faibles émissions » actif). */
    private var zfe: Map<String, fr.roadly.core.data.zfe.ZfeCrossing> = emptyMap()
    /** Incidents officiels notables par variante. */
    private var incidents: Map<String, Int> = emptyMap()
    /** Variante choisie dans l'aperçu des hôtes de niveau 1 à 6. */
    private var selected = 0

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = showOnMap()
            override fun onStop(owner: LifecycleOwner) = renderer.clearRoutes()
        })
        load()
    }

    private fun load() {
        options = null
        error = null
        selected = 0
        invalidate()
        lifecycleScope.launch {
            val origin = deps.location.lastKnown?.point
            if (origin == null) {
                error = AppError.NoResult
                invalidate()
                return@launch
            }
            val prefs = deps.prefs.routePreferences.first()
            val vehicle = deps.vehicles.profile.first()
            when (val r = deps.alternatives(origin, place.point, vehicle, deps.clock.nowMs(), prefs)) {
                is Outcome.Success -> options = r.value.takeIf { it.isNotEmpty() } ?: run { error = AppError.NoResult; null }
                is Outcome.Failure -> error = r.error
            }
            showOnMap()
            invalidate()
            val counted = HashMap<String, Int>()
            for (o in options.orEmpty()) {
                try {
                    counted[o.route.id] = deps.dangerZones.zonesAlong(o.route).size
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Base locale illisible : pas de décompte.
                }
            }
            if (counted.isNotEmpty()) {
                zones = counted
                invalidate()
            }
            val opts = options.orEmpty()
            if (opts.isNotEmpty()) {
                val events = try { deps.traffic.events() } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) { emptyList() }
                if (events.isNotEmpty()) {
                    val counts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        opts.associate { o -> o.route.id to fr.roadly.core.data.traffic.TrafficOnRoute.along(o.route.geometry, events).count { it.event.kind.notable } }
                    }
                    if (counts.values.any { it > 0 }) { incidents = counts; invalidate() }
                }
            }
            if (opts.isNotEmpty() && deps.prefs.zfeEnabled.first()) {
                val rules = try { deps.zfe.rules() } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) { emptyList() }
                if (rules.isNotEmpty()) {
                    val at = java.time.LocalDateTime.now()
                    val cls = fr.roadly.core.data.zfe.ZfeVehicleClass.of(vehicle?.type)
                    val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        opts.mapNotNull { o -> fr.roadly.core.data.zfe.ZfeRules.crossings(o.route.geometry, rules, cls, vehicle?.critAir, at).firstOrNull()?.let { o.route.id to it } }.toMap()
                    }
                    if (found.isNotEmpty()) { zfe = found; invalidate() }
                }
            }
        }
    }

    private fun showOnMap() {
        val routes = options?.map { it.route } ?: return
        renderer.setRoutes(routes, (routes.getOrNull(selected) ?: routes.firstOrNull())?.id)
        renderer.overview(routes.flatMap { it.geometry })
    }

    override fun onGetTemplate(): Template {
        error?.let { e ->
            val message = if (deps.location.lastKnown == null && e == AppError.NoResult) carContext.getString(R.string.car_location_unknown) else e.userMessage()
            return MessageTemplate.Builder(message)
                .withHeader(carContext, place.name, Action.BACK)
                .addAction(CarActions.text(carContext, R.string.car_retry) { load() })
                .build()
        }
        val caps = carContext.capabilities()
        if (caps.tier == TemplateTier.NAVIGATION) return routePreviewTemplate(caps)
        val header = Header.Builder().setTitle(carContext.getString(R.string.car_routes_title, place.name)).setStartHeaderAction(Action.BACK).build()
        val list = ListTemplate.Builder().setHeader(header)
        val opts = options
        if (opts == null) {
            list.setLoading(true)
        } else {
            val items = ItemList.Builder()
            for (o in opts) items.addItem(routeRow(o).setOnClickListener { start(o) }.build())
            list.setSingleList(items.build())
        }
        return MapWithContentTemplate.Builder()
            .setContentTemplate(list.build())
            .setMapController(MapController.Builder().setMapActionStrip(CarActions.mapStrip(renderer)).setPanModeListener { }.build())
            .build()
    }

    /** Ligne d'une variante : nom, durée et distance, puis arrivée, péage et zones de danger. */
    private fun routeRow(o: RouteOption): Row.Builder {
        val r = o.route
        val second = listOfNotNull(
            carContext.getString(R.string.car_route_arrival, Formatters.clockTime(o.arrivalEpochMs)),
            carContext.getString(R.string.car_route_toll).takeIf { r.hasToll },
            zones[r.id]?.let { n -> if (n == 0) carContext.getString(R.string.car_danger_zones_none) else carContext.resources.getQuantityString(R.plurals.car_danger_zones, n, n) },
            incidents[r.id]?.takeIf { it > 0 }?.let { n -> carContext.resources.getQuantityString(R.plurals.car_incidents, n, n) },
            zfe[r.id]?.let { z -> carContext.getString(carZfeText(z.verdict), z.rule.name) },
        ).joinToString(" · ")
        return Row.Builder()
            .setTitle(o.label)
            .addText(carContext.getString(R.string.car_route_line, Formatters.duration(r.durationSeconds), Formatters.distance(r.distanceMeters)))
            .addText(second)
    }

    /** Hôtes de niveau 1 à 6 : liste sélectionnable (pas de clic par ligne) et action « Démarrer » ; bandeau de carte dès le niveau 4. */
    private fun routePreviewTemplate(caps: CarCapabilities): Template {
        val builder = RoutePreviewNavigationTemplate.Builder()
        val title = carContext.getString(R.string.car_routes_title, place.name)
        if (caps.header) {
            builder.setHeader(Header.Builder().setTitle(title).setStartHeaderAction(Action.BACK).build())
        } else {
            @Suppress("DEPRECATION")
            builder.setTitle(title).setHeaderAction(Action.BACK)
        }
        val opts = options
        if (opts == null) {
            builder.setLoading(true)
        } else {
            selected = selected.coerceIn(0, opts.lastIndex)
            val items = ItemList.Builder()
            for (o in opts) items.addItem(routeRow(o).build())
            items.setSelectedIndex(selected).setOnSelectedListener { i ->
                selected = i
                showOnMap()
            }
            builder.setItemList(items.build())
            builder.setNavigateAction(CarActions.text(carContext, R.string.car_route_start) { opts.getOrNull(selected)?.let(::start) })
        }
        if (caps.listMapStrip) builder.setMapActionStrip(CarActions.mapStrip(renderer)).setPanModeListener { }
        return builder.build()
    }

    /** La session pousse l'écran de guidage dès que l'état passe en guidage. */
    private fun start(option: RouteOption) {
        lifecycleScope.launch {
            val prefs = deps.prefs.routePreferences.first()
            val vehicle = deps.vehicles.profile.first()
            deps.guidance.start(option, prefs, vehicle, emptyList(), place.name)
        }
    }
}

/** Texte de la ligne de variante pour une ZFE traversée (Android Auto : texte seul, sans couleur garantie par l'hôte). */
internal fun carZfeText(v: fr.roadly.core.data.zfe.ZfeVerdict): Int = when (v) {
    fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN -> R.string.car_zfe_forbidden
    fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN_OTHER_HOURS -> R.string.car_zfe_hours
    fr.roadly.core.data.zfe.ZfeVerdict.CRITAIR_UNKNOWN -> R.string.car_zfe_unknown
    else -> R.string.car_zfe_allowed
}
