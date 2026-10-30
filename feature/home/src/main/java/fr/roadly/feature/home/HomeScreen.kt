package fr.roadly.feature.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.designsystem.component.ComingSoonTag
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.GlassSearchBar
import fr.roadly.core.designsystem.component.OfflineBanner
import fr.roadly.core.designsystem.component.SpeedBadge
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.map.CameraMode
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import java.util.Locale

/** Vue initiale (France entière) tant qu'aucune position n'est connue. */
private val FRANCE_CENTER = GeoPoint(46.6, 2.4)

@Composable
fun HomeScreen(
    isDark: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onNavigateToRoute: (Place) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()

    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex()) }
    // Mode libre : au retour sur l'accueil, on rétablit la caméra quittée (une seule fois, au premier passage).
    var restored by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { vm.rememberCamera(map.cameraPosition()) } }
    LaunchedEffect(state.fix, state.cameraMode) {
        map.setUserLocation(state.fix)
        val saved = vm.lastCamera
        if (!restored) {
            restored = true
            if (state.cameraMode == CameraMode.FREE && saved != null) map.restore(saved)
        }
        map.applyCameraMode(state.cameraMode, state.fix)
    }
    LaunchedEffect(state.fix == null) { if (state.fix == null && vm.lastCamera == null) map.moveTo(FRANCE_CENTER, 5.2, animate = false) }
    LaunchedEffect(state.pickedPlace) { map.setMarkers(listOfNotNull(state.pickedPlace)) }

    Box(Modifier.fillMaxSize()) {
        if (state.styleUrl.isNotEmpty()) {
            RoadlyMap(
                styleUrl = state.styleUrl,
                controller = map,
                modifier = Modifier.fillMaxSize(),
                onUserGesture = vm::onUserGesture,
                onMapLongClick = vm::onMapLongClick,
            )
        }

        // Barre du haut : bannière hors-ligne, recherche (ouvre l'écran de recherche) et réglages.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = RoadlySpacing.m)) {
            if (!state.isOnline) OfflineBanner(Modifier.padding(bottom = RoadlySpacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassSearchBar(
                    value = "",
                    onValueChange = {},
                    placeholder = stringResource(R.string.home_search_placeholder),
                    readOnly = true,
                    onClick = onSearch,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(RoadlySpacing.s))
                GlassIconButton(Icons.Default.Settings, stringResource(R.string.home_settings), onClick = onSettings)
            }
        }

        // Bas d'écran : vitesse, bouton de recentrage (3 modes) puis panneau verre.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.m)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // Pas de source de limitation de vitesse en v1 : le badge affiche « — ».
                SpeedBadge(speedKmh = state.speedKmh, limitKmh = null)
                Spacer(Modifier.weight(1f))
                val recenterIcon = when (state.cameraMode) {
                    CameraMode.FREE -> Icons.Default.MyLocation
                    CameraMode.CENTERED -> Icons.Default.Explore
                    CameraMode.FOLLOW -> Icons.Default.Navigation
                }
                GlassIconButton(
                    recenterIcon,
                    stringResource(R.string.home_recenter),
                    onClick = vm::onRecenter,
                    tint = if (state.cameraMode == CameraMode.FREE) c.textPrimary else c.accent,
                )
            }
            Spacer(Modifier.height(RoadlySpacing.m))
            when (state.permission) {
                LocationPermissionState.GRANTED -> BottomPanel(state, onNavigateToRoute, onDefine = onSearch)
                else -> PermissionPanel(state.permission, onRequestLocationPermission, onOpenAppSettings)
            }
        }
    }

    // Point choisi par appui long : fiche avec l'action d'itinéraire.
    state.pickedPlace?.let { p ->
        GlassBottomSheet(onDismiss = vm::onDismissPicked) {
            Column(Modifier.padding(RoadlySpacing.l).navigationBarsPadding()) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                p.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
                Spacer(Modifier.height(RoadlySpacing.m))
                GlassButton(
                    stringResource(R.string.home_picked_route),
                    onClick = { vm.onDismissPicked(); onNavigateToRoute(p) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BottomPanel(state: HomeUiState, onNavigateToRoute: (Place) -> Unit, onDefine: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column {
            Text(
                state.address ?: stringResource(R.string.home_locating),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.height(RoadlySpacing.s))
            Row {
                ShortcutChip(stringResource(R.string.home_home), Icons.Default.Home, state.home, onNavigateToRoute, onDefine)
                Spacer(Modifier.width(RoadlySpacing.s))
                ShortcutChip(stringResource(R.string.home_work), Icons.Default.Work, state.work, onNavigateToRoute, onDefine)
            }
            Spacer(Modifier.height(RoadlySpacing.s))
            // Catégories annoncées mais pas encore disponibles : seul cas « sans action », marqué « Bientôt ».
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(
                    R.string.home_stations to Icons.Default.LocalGasStation,
                    R.string.home_chargers to Icons.Default.EvStation,
                    R.string.home_parkings to Icons.Default.LocalParking,
                    R.string.home_incidents to Icons.Default.Warning,
                ).forEach { (label, icon) ->
                    GlassChip(stringResource(label), onClick = {}, icon = icon, enabled = false, trailing = { ComingSoonTag() })
                    Spacer(Modifier.width(RoadlySpacing.s))
                }
            }
        }
    }
}

/** Raccourci Domicile/Travail : lance l'itinéraire s'il est défini, sinon renvoie vers la recherche pour le définir. */
@Composable
private fun ShortcutChip(
    label: String,
    icon: ImageVector,
    place: Place?,
    onNavigateToRoute: (Place) -> Unit,
    onDefine: () -> Unit,
) {
    GlassChip(
        text = if (place != null) label else "$label · ${stringResource(R.string.home_define)}",
        icon = icon,
        onClick = { if (place != null) onNavigateToRoute(place) else onDefine() },
    )
}

@Composable
private fun PermissionPanel(state: LocationPermissionState, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column {
            Text(stringResource(R.string.home_permission_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.home_permission_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(RoadlySpacing.m))
            if (state == LocationPermissionState.DENIED_FOREVER) {
                GlassButton(
                    stringResource(R.string.home_permission_settings),
                    onClick = onOpenSettings,
                    style = GlassButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                GlassButton(stringResource(R.string.home_permission_allow), onClick = onRequest, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Couleur Compose → `#RRGGBB` (CSS, sans alpha) pour les couches de style MapLibre. */
private fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())
