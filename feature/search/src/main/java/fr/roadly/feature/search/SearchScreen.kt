package fr.roadly.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.AppError
import fr.roadly.core.designsystem.component.EmptyState
import fr.roadly.core.designsystem.component.ErrorState
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassSearchBar
import fr.roadly.core.designsystem.component.OfflineBanner
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind

@Composable
fun SearchScreen(onBack: () -> Unit, onNavigateToRoute: (Place) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding()) {
        if (!state.isOnline) OfflineBanner()
        Row(Modifier.padding(RoadlySpacing.m), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.search_back), onClick = onBack)
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassSearchBar(
                value = state.query, onValueChange = vm::onQueryChange, placeholder = stringResource(R.string.search_placeholder),
                onClear = vm::onClear, focusRequester = focus, modifier = Modifier.weight(1f),
            )
        }
        when (val r = state.results) {
            SearchResults.Idle -> SuggestionsList(state, onSelect = vm::onPlaceSelected)
            SearchResults.Loading -> Column(Modifier.fillMaxWidth().padding(RoadlySpacing.xl), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(color = c.accent) }
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
                LazyColumn { items(r.places, key = { it.id }) { p -> PlaceRow(p, onClick = { vm.onPlaceSelected(p) }) } }
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

@Composable
private fun SuggestionsList(state: SearchUiState, onSelect: (Place) -> Unit, header: (@Composable () -> Unit)? = null) {
    if (state.recents.isEmpty() && state.favorites.isEmpty()) {
        if (header != null) header()
        else EmptyState(Icons.Default.Search, stringResource(R.string.search_idle_title), stringResource(R.string.search_empty_subtitle))
        return
    }
    LazyColumn {
        if (header != null) item { header() }
        if (state.favorites.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.search_favorites)) }
            items(state.favorites, key = { "f" + it.id }) { p -> PlaceRow(p, icon = Icons.Default.Star, onClick = { onSelect(p) }) }
        }
        if (state.recents.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.search_recents)) }
            items(state.recents, key = { "r" + it.id }) { p -> PlaceRow(p, icon = Icons.Default.History, onClick = { onSelect(p) }) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = LocalRoadlyColors.current.textSecondary, modifier = Modifier.padding(horizontal = RoadlySpacing.l, vertical = RoadlySpacing.s))
}

@Composable
internal fun PlaceRow(place: Place, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val c = LocalRoadlyColors.current
    val leading = icon ?: when (place.kind) {
        PlaceKind.CITY -> Icons.Default.LocationCity
        PlaceKind.COORDINATES -> Icons.Default.MyLocation
        else -> Icons.Default.Place
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = RoadlySpacing.l, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(leading, null, tint = c.accent)
        Spacer(Modifier.width(RoadlySpacing.m))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1)
            if (place.address != null) Text(place.address!!, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1)
        }
    }
    Spacer(Modifier.height(1.dp).fillMaxWidth().background(c.glassBorder))
}
