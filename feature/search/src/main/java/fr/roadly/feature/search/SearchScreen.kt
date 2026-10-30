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
import fr.roadly.core.designsystem.component.ComingSoonTag
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
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind

@Composable
fun SearchScreen(onBack: () -> Unit, onNavigateToRoute: (Place) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

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
        CategoryChips()
        Spacer(Modifier.height(RoadlySpacing.xs))
        when (val r = state.results) {
            SearchResults.Idle -> SuggestionsList(state, onSelect = vm::onPlaceSelected)
            SearchResults.Loading -> Box(Modifier.fillMaxWidth().padding(RoadlySpacing.xl), contentAlignment = Alignment.Center) {
                LoadingRow(stringResource(R.string.search_loading), Modifier.width(IntrinsicSize.Min))
            }
            is SearchResults.Error ->
                if (r.error == AppError.Network) {
                    // Hors ligne : on garde favoris et récents sous le message d'erreur.
                    SuggestionsList(state, onSelect = vm::onPlaceSelected, header = { ErrorState(r.error.userMessage(), onRetry = vm::retry) })
                } else {
                    ErrorState(r.error.userMessage(), onRetry = vm::retry)
                }
            is SearchResults.Content -> if (r.places.isEmpty()) {
                EmptyState(Icons.Default.Search, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_subtitle))
            } else {
                ResultsList(r.places, onSelect = vm::onPlaceSelected)
            }
        }
    }

    state.selected?.let { place ->
        PlaceSheet(
            place = place,
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

/** Catégories de recherche à proximité : aucune source de données pour l'instant, affichées désactivées. */
@Composable
private fun CategoryChips() {
    val categories = listOf(
        R.string.search_cat_stations to Icons.Default.LocalGasStation,
        R.string.search_cat_charging to Icons.Default.EvStation,
        R.string.search_cat_parkings to Icons.Default.LocalParking,
        R.string.search_cat_rest_areas to Icons.Default.Park,
        R.string.search_cat_restaurants to Icons.Default.Restaurant,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = RoadlySpacing.m),
        horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
    ) {
        categories.forEach { (label, icon) ->
            GlassChip(stringResource(label), onClick = {}, icon = icon, enabled = false, compact = true, trailing = { ComingSoonTag() })
        }
    }
}

private val listPadding = PaddingValues(start = RoadlySpacing.m, end = RoadlySpacing.m, top = RoadlySpacing.sm, bottom = RoadlySpacing.l)

@Composable
private fun ResultsList(places: List<Place>, onSelect: (Place) -> Unit) {
    LazyColumn(contentPadding = listPadding) {
        item {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    places.forEachIndexed { i, p -> PlaceRow(p, onClick = { onSelect(p) }, divider = i < places.lastIndex) }
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
) {
    val leading = icon ?: when (place.kind) {
        PlaceKind.CITY -> Icons.Default.LocationCity
        PlaceKind.COORDINATES -> Icons.Default.MyLocation
        else -> Icons.Default.Place
    }
    ListRow(title = place.name, subtitle = place.address, icon = leading, iconStyle = iconStyle, onClick = onClick, chevron = false, divider = divider)
}
