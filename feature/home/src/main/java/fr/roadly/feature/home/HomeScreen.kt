package fr.roadly.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.OfflineBanner
import fr.roadly.core.designsystem.component.ReportSheet
import fr.roadly.core.designsystem.component.RoundButtonStyle
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.SpeedBadge
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.geocoding.PoiCategory
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
    onResumeGuidance: () -> Unit,
    onSearchCategory: (PoiCategory) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isGuiding by vm.isGuiding.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showPosition by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive, c.routeCasing) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex(), c.routeCasing.toHex()) }
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (state.styleUrl.isNotEmpty()) {
            RoadlyMap(
                styleUrl = state.styleUrl,
                controller = map,
                modifier = Modifier.fillMaxSize(),
                onUserGesture = vm::onUserGesture,
                onMapLongClick = vm::onMapLongClick,
                onCameraIdle = vm::onCameraIdle,
            )
        }

        // Résultat du dernier signalement, 4 s, sous la barre de recherche.
        val reportMessage by vm.reportMessage.collectAsStateWithLifecycle()
        ReportResultPill(reportMessage, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp))

        // Colonne d'actions à droite : signaler (feuille des types), boussole si la carte est tournée.
        Column(
            Modifier.align(if (landscape) Alignment.TopEnd else Alignment.CenterEnd).statusBarsPadding().padding(RoadlySpacing.screenEdge).padding(top = if (landscape) 60.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm),
            horizontalAlignment = Alignment.End,
        ) {
            RoundIconButton(Icons.Default.Warning, stringResource(R.string.home_report), onClick = { showReport = true }, style = RoundButtonStyle.Accent)
            AnimatedVisibility(state.isRotated, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                CompassButton(bearingDeg = state.mapBearingDeg, onClick = map::resetBearing)
            }
        }

        // Barre du haut : bannière hors-ligne, recherche (ouvre l'écran de recherche) et réglages.
        Column(Modifier.fillMaxWidth(if (landscape) 0.5f else 1f).statusBarsPadding().padding(RoadlySpacing.screenEdge)) {
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

        // Bas d'écran : compteur à gauche, recentrage à droite, puis panneau d'accueil.
        val bottomModifier = if (landscape) {
            Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.5f).navigationBarsPadding().padding(RoadlySpacing.screenEdge)
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.screenEdge)
        }
        Column(bottomModifier) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // Pas de source de limitation de vitesse hors guidage : le badge affiche « — ».
                SpeedBadge(speedKmh = state.speedKmh, limitKmh = null)
                Spacer(Modifier.weight(1f))
                val recenterIcon = when (state.cameraMode) {
                    CameraMode.FREE -> Icons.Default.MyLocation
                    CameraMode.CENTERED -> Icons.Default.Explore
                    CameraMode.FOLLOW -> Icons.Default.Navigation
                }
                RoundIconButton(
                    recenterIcon,
                    stringResource(R.string.home_recenter),
                    onClick = vm::onRecenter,
                    style = if (state.cameraMode == CameraMode.FREE) RoundButtonStyle.Glass else RoundButtonStyle.Brand,
                )
            }
            Spacer(Modifier.height(RoadlySpacing.sm))
            when (state.permission) {
                LocationPermissionState.GRANTED -> BottomPanel(state, isGuiding, onNavigateToRoute, onDefine = onSearch, onResumeGuidance = onResumeGuidance, onPosition = { showPosition = true }, onCategory = onSearchCategory)
                else -> PermissionPanel(state.permission, onRequestLocationPermission, onOpenAppSettings)
            }
        }
    }

    val communityEnabled by vm.communityEnabled.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    LaunchedEffect(reports) { map.setReports(reports) }
    if (showReport) ReportSheet(onDismiss = { showReport = false }, enabled = communityEnabled, onSelect = vm::report)
    val fix = state.fix
    if (showPosition && fix != null) PositionSheet(point = fix.point, address = state.address, onDismiss = { showPosition = false })

    // Point choisi par appui long : fiche avec l'action d'itinéraire.
    state.pickedPlace?.let { p ->
        GlassBottomSheet(onDismiss = vm::onDismissPicked) {
            Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Default.Place, style = IconTileStyle.BrandFilled, size = 44.dp)
                    Spacer(Modifier.width(RoadlySpacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        p.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
                Spacer(Modifier.height(RoadlySpacing.m))
                GlassButton(
                    stringResource(R.string.home_picked_route),
                    onClick = { vm.onDismissPicked(); onNavigateToRoute(p) },
                    style = GlassButtonStyle.Brand,
                    icon = Icons.Default.Navigation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BottomPanel(
    state: HomeUiState,
    isGuiding: Boolean,
    onNavigateToRoute: (Place) -> Unit,
    onDefine: () -> Unit,
    onResumeGuidance: () -> Unit,
    onPosition: () -> Unit,
    onCategory: (PoiCategory) -> Unit,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column {
            if (isGuiding) {
                GlassButton(stringResource(R.string.home_resume_guidance), onClick = onResumeGuidance, icon = Icons.Default.Navigation, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(RoadlySpacing.sm))
            }
            // Toucher sa position : adresse copiable et partageable (feuille).
            Row(
                Modifier.fillMaxWidth().clip(RoadlyShapes.Tile).clickable(enabled = state.fix != null, role = Role.Button, onClick = onPosition).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Default.MyLocation, style = IconTileStyle.Neutral, size = 28.dp)
                Spacer(Modifier.width(RoadlySpacing.s))
                Text(
                    state.address ?: stringResource(R.string.home_locating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.fix != null) Icon(Icons.Default.IosShare, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(RoadlySpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                ShortcutTile(stringResource(R.string.home_home), Icons.Default.Home, state.home, onNavigateToRoute, onDefine, Modifier.weight(1f))
                ShortcutTile(stringResource(R.string.home_work), Icons.Default.Work, state.work, onNavigateToRoute, onDefine, Modifier.weight(1f))
            }
            Spacer(Modifier.height(RoadlySpacing.sm))
            // Catégories « à proximité » (lieux OSM via Photon) ; « Incidents » reste sans source (LOT 4/8) : désactivé et marqué « Bientôt ».
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                listOf(
                    Triple(R.string.home_stations, Icons.Default.LocalGasStation, PoiCategory.FUEL),
                    Triple(R.string.home_chargers, Icons.Default.EvStation, PoiCategory.CHARGING),
                    Triple(R.string.home_parkings, Icons.Default.LocalParking, PoiCategory.PARKING),
                    Triple(R.string.home_rest_areas, Icons.Default.Park, PoiCategory.REST_AREA),
                ).forEach { (label, icon, cat) ->
                    GlassChip(stringResource(label), onClick = { onCategory(cat) }, icon = icon, compact = true)
                }
                GlassChip(stringResource(R.string.home_incidents), onClick = {}, icon = Icons.Default.Warning, enabled = false, compact = true, trailing = { ComingSoonTag() })
            }
        }
    }
}

/** Raccourci Domicile/Travail : tuile avec l'adresse si défini, sinon « Définir » (ouvre la recherche). */
@Composable
private fun ShortcutTile(
    label: String,
    icon: ImageVector,
    place: Place?,
    onNavigateToRoute: (Place) -> Unit,
    onDefine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    Row(
        modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoadlyShapes.Tile)
            .background(c.glassBorder)
            .clickable(role = Role.Button, onClick = { if (place != null) onNavigateToRoute(place) else onDefine() })
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, style = if (place != null) IconTileStyle.Brand else IconTileStyle.Neutral, size = 36.dp)
        Spacer(Modifier.width(RoadlySpacing.s))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1)
            Text(
                place?.address ?: place?.name ?: stringResource(R.string.home_define),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PermissionPanel(state: LocationPermissionState, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.MyLocation, style = IconTileStyle.Accent, size = 44.dp)
                Spacer(Modifier.width(RoadlySpacing.sm))
                Text(stringResource(R.string.home_permission_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            Spacer(Modifier.height(RoadlySpacing.s))
            Text(stringResource(R.string.home_permission_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
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

/** Boussole : l'aiguille tourne avec la carte ; un appui remet le nord en haut. */
@Composable
private fun CompassButton(bearingDeg: Double, onClick: () -> Unit) {
    val c = LocalRoadlyColors.current
    val angle by animateFloatAsState((-bearingDeg).toFloat(), label = "compass")
    Box(
        Modifier.size(RoadlySpacing.touchTarget).clip(RoadlyShapes.Pill).background(c.glass).border(1.dp, c.glassBorder, RoadlyShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Navigation, contentDescription = stringResource(R.string.home_compass), tint = c.danger, modifier = Modifier.size(22.dp).rotate(angle))
    }
}

/** Position courante : adresse (si géocodée) et coordonnées, à copier ou partager. */
@Composable
private fun PositionSheet(point: GeoPoint, address: String?, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    val context = LocalContext.current
    val coords = String.format(Locale.ROOT, "%.5f, %.5f", point.lat, point.lon)
    val shareText = listOfNotNull(address, coords, "https://www.openstreetmap.org/?mlat=${point.lat}&mlon=${point.lon}#map=17/${point.lat}/${point.lon}").joinToString("\n")
    val shareTitle = stringResource(R.string.home_position_share_title)
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Default.MyLocation, style = IconTileStyle.BrandFilled, size = 44.dp)
                Spacer(Modifier.width(RoadlySpacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_position_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Text(address ?: stringResource(R.string.home_position_no_address), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(coords, style = MaterialTheme.typography.labelMedium, color = c.textTertiary)
                }
            }
            Spacer(Modifier.height(RoadlySpacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(
                    stringResource(R.string.home_position_copy), icon = Icons.Default.ContentCopy, style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f),
                    onClick = { context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("position", shareText)) },
                )
                GlassButton(
                    stringResource(R.string.home_position_share), icon = Icons.Default.IosShare, style = GlassButtonStyle.Brand, modifier = Modifier.weight(1f),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText)
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    },
                )
            }
        }
    }
}

/** Couleur Compose → `#RRGGBB` (CSS, sans alpha) pour les couches de style MapLibre. */
private fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())

/** « Signalement envoyé » / « Signalement impossible : … » pendant 4 s ; `null` = rien. */
@Composable
internal fun ReportResultPill(message: String?, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    AnimatedVisibility(message != null, modifier = modifier, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
        val ok = message == "sent"
        GlassPanel(contentPadding = 10.dp, shape = RoadlyShapes.Chip) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, contentDescription = null, tint = if (ok) c.success else c.danger, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(when {
                        ok -> R.string.report_sent
                        message == "error:RateLimited" -> R.string.report_rate_limited
                        else -> R.string.report_failed
                    }),
                    style = MaterialTheme.typography.labelLarge, color = c.textPrimary,
                )
            }
        }
    }
}
