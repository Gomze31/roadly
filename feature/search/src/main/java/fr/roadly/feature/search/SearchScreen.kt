package fr.roadly.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.rememberVoiceInput
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.EmptyState
import fr.roadly.core.designsystem.component.ErrorState
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassSearchBar
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.OfflineBanner
import fr.roadly.core.designsystem.component.RoadlyScreen
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.SurfaceCard
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import java.util.Locale
import fr.roadly.core.model.PlaceKind

@Composable
fun SearchScreen(onBack: () -> Unit, onNavigateToRoute: (Place) -> Unit, onPick: ((Place) -> Unit)? = null, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val trip by vm.tripOptions.collectAsStateWithLifecycle()
    var showDeparture by remember { mutableStateOf(false) }
    val listen = rememberVoiceInput(stringResource(R.string.search_voice_prompt), onText = vm::onQueryChange)
    // « Y aller » : directement l'écran d'itinéraires (avec les options du trajet), sans passer par la fiche.
    val go: ((Place) -> Unit)? = if (onPick == null) ({ p -> vm.onGo(p); onNavigateToRoute(p) }) else null
    val focus = remember { FocusRequester() }
    // Mode « choisir un arrêt » : un lieu touché est rendu tout de suite (pas de fiche), après enregistrement dans les récents.
    val select: (Place) -> Unit = if (onPick != null) ({ p -> vm.onPlaceSelected(p); vm.onDismissPlace(); onPick(p) }) else vm::onPlaceSelected
    // Ouverture directe sur une catégorie (puce de l'accueil) : pas de clavier.
    LaunchedEffect(Unit) { if (vm.uiState.value.category == null) focus.requestFocus() }

    RoadlyScreen(Modifier.imePadding()) {
        if (!state.isOnline) OfflineBanner()
        Row(Modifier.padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.search_back), onClick = onBack)
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassSearchBar(
                value = state.query, onValueChange = vm::onQueryChange, placeholder = stringResource(if (onPick != null) R.string.search_placeholder_stop else R.string.search_placeholder),
                onClear = vm::onClear, focusRequester = focus, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassIconButton(Icons.Default.Mic, stringResource(R.string.search_voice), onClick = listen)
        }
        // Options du trajet (avant même de choisir la destination) : départ, péages, autoroutes, tunnels, ferries, chemins.
        if (onPick == null) {
            TripOptionsBar(trip, onDeparture = { showDeparture = true }, onToggle = vm::onToggleAvoid, onRemember = vm::onRememberOptions)
            Spacer(Modifier.height(RoadlySpacing.xs))
        }
        CategoryChips(selected = state.category, onSelect = vm::onCategory)
        Spacer(Modifier.height(RoadlySpacing.xs))
        val lat = state.hereLat
        val lon = state.hereLon
        val here = if (lat != null && lon != null) GeoPoint(lat, lon) else null
        when (val r = state.results) {
            SearchResults.Idle -> SuggestionsList(state, onSelect = select, onGo = go)
            SearchResults.Loading -> Box(Modifier.fillMaxWidth().padding(RoadlySpacing.xl), contentAlignment = Alignment.Center) {
                LoadingRow(stringResource(if (state.category != null) R.string.search_loading_nearby else R.string.search_loading), Modifier.width(IntrinsicSize.Min))
            }
            is SearchResults.Error -> when {
                // Hors ligne : on garde favoris et récents sous le message d'erreur.
                r.error == AppError.Network -> SuggestionsList(state, onSelect = select, header = { ErrorState(r.error.userMessage(), onRetry = vm::retry) })
                // Catégorie sans position connue : le GPS n'a pas encore donné de fix.
                r.error == AppError.NoResult && state.category != null -> EmptyState(Icons.Default.MyLocation, stringResource(R.string.search_no_position_title), stringResource(R.string.search_no_position_subtitle))
                else -> ErrorState(r.error.userMessage(), onRetry = vm::retry)
            }
            is SearchResults.Content -> if (r.places.isEmpty()) {
                if (r.category != null) EmptyState(r.category.icon(), stringResource(R.string.search_nearby_empty_title, stringResource(r.category.labelRes())), stringResource(R.string.search_nearby_empty_subtitle, r.category.radiusKm))
                else EmptyState(Icons.Default.Search, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_subtitle))
            } else {
                val fuelMode = r.category == PoiCategory.FUEL && r.stations.isNotEmpty()
                if (fuelMode) FuelControls(state.fuel, state.fuelSort, onFuel = vm::onFuel, onSort = vm::onFuelSort)
                ResultsList(
                    r.places, here, onSelect = select, onGo = go,
                    header = r.category?.let { stringResource(R.string.search_nearby_header, stringResource(it.labelRes()), it.radiusKm) },
                    footer = if (fuelMode) stringResource(R.string.search_fuel_footer) else null,
                    value = if (fuelMode) ({ p -> r.stations[p.id]?.let { s -> fuelPriceText(s, state.fuel) } }) else null,
                )
            }
        }
    }

    if (showDeparture) fr.roadly.core.designsystem.component.DepartureTimeDialog(
        initialEpochMs = trip.departure?.epochMs, initialArrive = trip.departure?.arrive == true,
        onConfirm = { ms, arrive -> vm.onDeparture(fr.roadly.core.data.repository.DeparturePlan(ms, arrive)); showDeparture = false },
        onNow = { vm.onDeparture(null); showDeparture = false }, onDismiss = { showDeparture = false },
    )

    state.selected?.let { place ->
        PlaceSheet(
            place = place,
            station = vm.stationFor(place),
            isFavorite = state.selectedIsFavorite,
            distanceMeters = state.selectedDistanceMeters,
            onDismiss = vm::onDismissPlace,
            onRoute = { vm.onDismissPlace(); onNavigateToRoute(place) },
            onToggleFavorite = vm::onToggleFavorite,
            onSetHome = vm::onSetHome,
            onSetWork = vm::onSetWork,
        )
    }
}

/** Prix du carburant choisi, ou « — » si la station ne le publie pas (jamais un prix inventé). */
private fun fuelPriceText(s: FuelStation, fuel: FuelType): String = s.price(fuel)?.let { String.format(Locale.FRANCE, "%.3f €", it.eurPerLitre) } ?: "—"

internal fun FuelType.shortLabel(): String = when (this) {
    FuelType.SP95 -> "SP95"; FuelType.SP98 -> "SP98"; FuelType.E10 -> "E10"; FuelType.E85 -> "E85"
    FuelType.DIESEL -> "Gazole"; FuelType.LPG -> "GPL"; FuelType.ELECTRIC -> "Élec."
}

/** Carburant mis en avant et tri (distance / prix) des stations-service. */
@Composable
private fun FuelControls(fuel: FuelType, sort: FuelSort, onFuel: (FuelType) -> Unit, onSort: (FuelSort) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
    ) {
        FuelType.entries.filter { it != FuelType.ELECTRIC }.forEach { f ->
            GlassChip(f.shortLabel(), onClick = { onFuel(f) }, selected = fuel == f, compact = true)
        }
        GlassChip(
            stringResource(if (sort == FuelSort.PRICE) R.string.search_fuel_sort_price else R.string.search_fuel_sort_distance),
            onClick = { onSort(if (sort == FuelSort.PRICE) FuelSort.DISTANCE else FuelSort.PRICE) },
            icon = Icons.AutoMirrored.Filled.Sort, compact = true,
        )
    }
}

internal fun PoiCategory.labelRes(): Int = when (this) {
    PoiCategory.FUEL -> R.string.search_cat_stations
    PoiCategory.CHARGING -> R.string.search_cat_charging
    PoiCategory.PARKING -> R.string.search_cat_parkings
    PoiCategory.REST_AREA -> R.string.search_cat_rest_areas
    PoiCategory.RESTAURANT -> R.string.search_cat_restaurants
}

internal fun PoiCategory.icon(): ImageVector = when (this) {
    PoiCategory.FUEL -> Icons.Default.LocalGasStation
    PoiCategory.CHARGING -> Icons.Default.EvStation
    PoiCategory.PARKING -> Icons.Default.LocalParking
    PoiCategory.REST_AREA -> Icons.Default.Park
    PoiCategory.RESTAURANT -> Icons.Default.Restaurant
}

/** Catégories « à proximité » (OSM via Photon) : lieux et adresses seulement, sans prix ni disponibilité (LOT 5). */
@Composable
private fun CategoryChips(selected: PoiCategory?, onSelect: (PoiCategory) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = RoadlySpacing.m),
        horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
    ) {
        PoiCategory.entries.forEach { cat ->
            GlassChip(stringResource(cat.labelRes()), onClick = { onSelect(cat) }, icon = cat.icon(), selected = selected == cat, compact = true)
        }
    }
}

private val listPadding = PaddingValues(start = RoadlySpacing.m, end = RoadlySpacing.m, top = RoadlySpacing.sm, bottom = RoadlySpacing.l)

/** [value] : texte de droite par lieu (prix) ; à défaut, la distance depuis [here]. */
@Composable
private fun ResultsList(places: List<Place>, here: GeoPoint?, onSelect: (Place) -> Unit, header: String? = null, footer: String? = null, value: ((Place) -> String?)? = null, onGo: ((Place) -> Unit)? = null) {
    val rightText: (Place) -> String? = { p -> value?.invoke(p) ?: here?.let { Formatters.distance(it.distanceTo(p.point)) } }
    val subtitle: (Place) -> String? = { p ->
        val d = if (value != null) here?.let { Formatters.distance(it.distanceTo(p.point)) } else null
        listOfNotNull(p.address, d).joinToString(" · ").ifBlank { null }
    }
    LazyColumn(contentPadding = listPadding) {
        item {
            if (header != null) {
                SettingsGroup(header, footer = footer ?: stringResource(R.string.search_nearby_footer)) {
                    places.forEachIndexed { i, p -> PlaceRow(p, onClick = { onSelect(p) }, divider = i < places.lastIndex, distance = rightText(p), subtitle = subtitle(p), onGo = onGo?.let { g -> { g(p) } }) }
                }
            } else {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        places.forEachIndexed { i, p -> PlaceRow(p, onClick = { onSelect(p) }, divider = i < places.lastIndex, distance = rightText(p), onGo = onGo?.let { g -> { g(p) } }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsList(state: SearchUiState, onSelect: (Place) -> Unit, header: (@Composable () -> Unit)? = null, onGo: ((Place) -> Unit)? = null) {
    if (state.recents.isEmpty() && state.favorites.isEmpty()) {
        if (header != null) header()
        else EmptyState(Icons.Default.Search, stringResource(R.string.search_idle_title), stringResource(R.string.search_empty_subtitle))
        return
    }
    LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(RoadlySpacing.l)) {
        if (header != null) item { header() }
        if (state.favorites.isNotEmpty()) {
            item {
                SettingsGroup(stringResource(R.string.search_favorites)) {
                    state.favorites.forEachIndexed { i, p ->
                        PlaceRow(p, onClick = { onSelect(p) }, icon = Icons.Default.Star, iconStyle = IconTileStyle.Accent, divider = i < state.favorites.lastIndex, onGo = onGo?.let { g -> { g(p) } })
                    }
                }
            }
        }
        if (state.recents.isNotEmpty()) {
            item {
                SettingsGroup(stringResource(R.string.search_recents)) {
                    state.recents.forEachIndexed { i, p ->
                        PlaceRow(p, onClick = { onSelect(p) }, icon = Icons.Default.History, iconStyle = IconTileStyle.Neutral, divider = i < state.recents.lastIndex)
                    }
                }
            }
        }
    }
}

/** Ligne de lieu : icône selon le type sauf si une icône de section (favori, récent) est fournie. */
@Composable
internal fun PlaceRow(
    place: Place,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconStyle: IconTileStyle = IconTileStyle.Brand,
    divider: Boolean = true,
    distance: String? = null,
    subtitle: String? = place.address,
    /** Bouton « Y aller » à droite (itinéraire direct) ; null = pas de bouton. */
    onGo: (() -> Unit)? = null,
) {
    val leading = icon ?: when {
        place.source == "prix-carburants" -> Icons.Default.LocalGasStation
        place.kind == PlaceKind.CITY -> Icons.Default.LocationCity
        place.kind == PlaceKind.COORDINATES -> Icons.Default.MyLocation
        else -> Icons.Default.Place
    }
    val c = LocalRoadlyColors.current
    ListRow(
        title = place.name, subtitle = subtitle, icon = leading, iconStyle = iconStyle, value = distance, onClick = onClick, chevron = false, divider = divider,
        trailing = onGo?.let { go ->
            {
                Box(
                    Modifier.size(40.dp).clip(RoadlyShapes.Pill).background(c.brandSoft).clickable(role = Role.Button, onClickLabel = stringResource(R.string.search_go), onClick = go),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Navigation, contentDescription = stringResource(R.string.search_go), tint = c.brand, modifier = Modifier.size(20.dp)) }
            }
        },
    )
}

/** Options du trajet : départ, éviter péages / autoroutes / tunnels / ferries / chemins, et « Mémoriser » (sinon : ce trajet seulement). */
@Composable
internal fun TripOptionsBar(
    trip: TripOptionsUi,
    onDeparture: () -> Unit,
    onToggle: (fr.roadly.core.model.RouteAvoid) -> Unit,
    onRemember: (Boolean) -> Unit,
) {
    val c = LocalRoadlyColors.current
    val zone = java.time.ZoneId.systemDefault()
    Column {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = RoadlySpacing.m),
            horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically,
        ) {
            val d = trip.departure
            GlassChip(
                when {
                    d == null -> stringResource(R.string.search_depart_now)
                    d.arrive -> stringResource(R.string.search_arrive_at, Formatters.clockTime(d.epochMs, zone))
                    else -> stringResource(R.string.search_depart_at, Formatters.clockTime(d.epochMs, zone))
                },
                onClick = onDeparture, icon = Icons.Default.Schedule, compact = true, selected = d != null,
            )
            fr.roadly.core.model.RouteAvoid.entries.forEach { a ->
                GlassChip(stringResource(a.labelRes()), onClick = { onToggle(a) }, icon = a.icon(), compact = true, selected = trip.preferences.avoids(a))
            }
            GlassChip(stringResource(R.string.search_remember), onClick = { onRemember(!trip.remember) }, icon = if (trip.remember) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, compact = true, selected = trip.remember)
        }
        if (trip.tripOnly && trip.preferences.any) {
            Text(
                stringResource(R.string.search_trip_only), style = MaterialTheme.typography.bodySmall, color = c.textTertiary,
                modifier = Modifier.padding(start = RoadlySpacing.m + 6.dp, top = 4.dp),
            )
        }
    }
}

internal fun fr.roadly.core.model.RouteAvoid.labelRes(): Int = when (this) {
    fr.roadly.core.model.RouteAvoid.TOLLS -> R.string.search_avoid_tolls
    fr.roadly.core.model.RouteAvoid.HIGHWAYS -> R.string.search_avoid_highways
    fr.roadly.core.model.RouteAvoid.TUNNELS -> R.string.search_avoid_tunnels
    fr.roadly.core.model.RouteAvoid.FERRIES -> R.string.search_avoid_ferries
    fr.roadly.core.model.RouteAvoid.UNPAVED -> R.string.search_avoid_unpaved
}

internal fun fr.roadly.core.model.RouteAvoid.icon(): ImageVector = when (this) {
    fr.roadly.core.model.RouteAvoid.TOLLS -> Icons.Default.Euro
    fr.roadly.core.model.RouteAvoid.HIGHWAYS -> Icons.Default.AltRoute
    fr.roadly.core.model.RouteAvoid.TUNNELS -> Icons.Default.Subway
    fr.roadly.core.model.RouteAvoid.FERRIES -> Icons.Default.DirectionsBoat
    fr.roadly.core.model.RouteAvoid.UNPAVED -> Icons.Default.Terrain
}
