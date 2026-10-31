package fr.roadly.feature.search

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
fun SearchScreen(onBack: () -> Unit, onNavigateToRoute: (Place) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    // Ouverture directe sur une catégorie (puce de l'accueil) : pas de clavier.
    LaunchedEffect(Unit) { if (vm.uiState.value.category == null) focus.requestFocus() }

    RoadlyScreen(Modifier.imePadding()) {
        if (!state.isOnline) OfflineBanner()
        Row(Modifier.padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.search_back), onClick = onBack)
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassSearchBar(
                value = state.query, onValueChange = vm::onQueryChange, placeholder = stringResource(R.string.search_placeholder),
                onClear = vm::onClear, focusRequester = focus, modifier = Modifier.weight(1f),
            )
        }
        CategoryChips(selected = state.category, onSelect = vm::onCategory)
        Spacer(Modifier.height(RoadlySpacing.xs))
        val lat = state.hereLat
        val lon = state.hereLon
        val here = if (lat != null && lon != null) GeoPoint(lat, lon) else null
        when (val r = state.results) {
            SearchResults.Idle -> SuggestionsList(state, onSelect = vm::onPlaceSelected)
            SearchResults.Loading -> Box(Modifier.fillMaxWidth().padding(RoadlySpacing.xl), contentAlignment = Alignment.Center) {
                LoadingRow(stringResource(if (state.category != null) R.string.search_loading_nearby else R.string.search_loading), Modifier.width(IntrinsicSize.Min))
            }
            is SearchResults.Error -> when {
                // Hors ligne : on garde favoris et récents sous le message d'erreur.
                r.error == AppError.Network -> SuggestionsList(state, onSelect = vm::onPlaceSelected, header = { ErrorState(r.error.userMessage(), onRetry = vm::retry) })
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
                    r.places, here, onSelect = vm::onPlaceSelected,
                    header = r.category?.let { stringResource(R.string.search_nearby_header, stringResource(it.labelRes()), it.radiusKm) },
                    footer = if (fuelMode) stringResource(R.string.search_fuel_footer) else null,
                    value = if (fuelMode) ({ p -> r.stations[p.id]?.let { s -> fuelPriceText(s, state.fuel) } }) else null,
                )
            }
        }
    }

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
private fun ResultsList(places: List<Place>, here: GeoPoint?, onSelect: (Place) -> Unit, header: String? = null, footer: String? = null, value: ((Place) -> String?)? = null) {
    val rightText: (Place) -> String? = { p -> value?.invoke(p) ?: here?.let { Formatters.distance(it.distanceTo(p.point)) } }
    val subtitle: (Place) -> String? = { p ->
        val d = if (value != null) here?.let { Formatters.distance(it.distanceTo(p.point)) } else null
        listOfNotNull(p.address, d).joinToString(" · ").ifBlank { null }
    }
    LazyColumn(contentPadding = listPadding) {
        item {
            if (header != null) {
                SettingsGroup(header, footer = footer ?: stringResource(R.string.search_nearby_footer)) {
                    places.forEachIndexed { i, p -> PlaceRow(p, onClick = { onSelect(p) }, divider = i < places.lastIndex, distance = rightText(p), subtitle = subtitle(p)) }
                }
            } else {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Column {
                        places.forEachIndexed { i, p -> PlaceRow(p, onClick = { onSelect(p) }, divider = i < places.lastIndex, distance = rightText(p)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsList(state: SearchUiState, onSelect: (Place) -> Unit, header: (@Composable () -> Unit)? = null) {
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
                        PlaceRow(p, onClick = { onSelect(p) }, icon = Icons.Default.Star, iconStyle = IconTileStyle.Accent, divider = i < state.favorites.lastIndex)
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
) {
    val leading = icon ?: when {
        place.source == "prix-carburants" -> Icons.Default.LocalGasStation
        place.kind == PlaceKind.CITY -> Icons.Default.LocationCity
        place.kind == PlaceKind.COORDINATES -> Icons.Default.MyLocation
        else -> Icons.Default.Place
    }
    ListRow(title = place.name, subtitle = subtitle, icon = leading, iconStyle = iconStyle, value = distance, onClick = onClick, chevron = false, divider = divider)
}
