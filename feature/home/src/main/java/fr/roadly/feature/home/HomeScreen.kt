package fr.roadly.feature.home

import androidx.compose.ui.unit.sp

import fr.roadly.core.designsystem.theme.softShadow

import fr.roadly.core.designsystem.theme.Display

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.repository.DeparturePlan
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.SegmentedControl
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.component.rememberVoiceInput
import fr.roadly.core.designsystem.theme.brandGradient
import fr.roadly.core.model.RoutePreferences
import java.time.Instant
import java.time.ZoneId
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
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
import fr.roadly.core.data.repository.ParkedVehicle
import fr.roadly.core.data.repository.TollReminder

/** Vue initiale (France entière) tant qu'aucune position n'est connue. */
private val FRANCE_CENTER = GeoPoint(46.6, 2.4)

/** Zoom de la carte quand on montre le véhicule garé. */
private const val PARKED_ZOOM = 16.5

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
    onOpenProfile: () -> Unit = {},
    assistantButton: @Composable () -> Unit = {},
    assistantPanel: @Composable () -> Unit = {},
    onFreeDrive: () -> Unit = {},
    onSearchText: (String) -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isGuiding by vm.isGuiding.collectAsStateWithLifecycle()
    val resumeCard by vm.resumeCard.collectAsStateWithLifecycle()
    val tollReminders by vm.tollReminders.collectAsStateWithLifecycle()
    val parked by vm.parkedVehicle.collectAsStateWithLifecycle()
    val freeDriveLimit by vm.freeDriveLimitKmh.collectAsStateWithLifecycle()
    val walk by vm.walkToCar.collectAsStateWithLifecycle()
    // E3 : après « Reprendre », l'écran de guidage s'ouvre dès que la session guide (même chemin que « Reprendre le guidage »).
    var resumeRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(resumeRequested, isGuiding) { if (resumeRequested && isGuiding) { resumeRequested = false; onResumeGuidance() } }
    LaunchedEffect(resumeCard?.phase) { if (resumeCard?.phase == ResumePhase.FAILED) resumeRequested = false }
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    val parkedName = stringResource(R.string.home_parked_title)
    val parkedNear = stringResource(R.string.home_parked_near)
    var showReport by rememberSaveable { mutableStateOf(false) }
    var showPosition by rememberSaveable { mutableStateOf(false) }
    var showRouteOptions by rememberSaveable { mutableStateOf(false) }
    var showDeparture by rememberSaveable { mutableStateOf(false) }
    /** Chevron : replie la carte « Départ » et les boutons latéraux pour dégager la carte. */
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val departurePlan by vm.departurePlan.collectAsStateWithLifecycle()
    val routePreferences by vm.routePreferences.collectAsStateWithLifecycle()
    val listen = rememberVoiceInput(stringResource(R.string.home_voice_prompt), onText = onSearchText)

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
    // S4 : trajet à pied vers la voiture garée, tracé et cadré au-dessus de la fiche (qui couvre le bas de l'écran).
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }
    LaunchedEffect(walk) {
        val route = (walk as? WalkToCar.Ready)?.route
        map.setRoutes(listOfNotNull(route), route?.id)
        if (route != null) {
            val side = with(density) { 48.dp.roundToPx() }
            map.fitBounds(route.geometry, side, with(density) { 120.dp.roundToPx() }, side, (screenHeightPx * 0.45f).toInt())
        }
    }

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

        // Haut : bannière hors-ligne, barre de recherche (micro, flèche), carte « Départ » (repliable).
        Column(
            Modifier.fillMaxWidth(if (landscape) 0.5f else 1f).statusBarsPadding().padding(RoadlySpacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            if (!state.isOnline) OfflineBanner()
            HomeSearchPill(onSearch = onSearch, onVoice = listen)
            AnimatedVisibility(!collapsed, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                DepartureCard(departurePlan, onClick = { showDeparture = true })
            }
            // Colonnes latérales sous la recherche : carburant et boussole à gauche, outils à droite.
            Row(Modifier.fillMaxWidth().padding(top = RoadlySpacing.xs), verticalAlignment = Alignment.Top) {
                Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                    AnimatedVisibility(!collapsed, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        RoundIconButton(Icons.Default.LocalGasStation, stringResource(R.string.home_stations), onClick = { onSearchCategory(PoiCategory.FUEL) }, iconTint = c.accent, size = 56.dp)
                    }
                    AnimatedVisibility(!collapsed, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        CompassButton(bearingDeg = state.mapBearingDeg, onClick = map::resetBearing)
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm), horizontalAlignment = Alignment.End) {
                    val chevron by animateFloatAsState(if (collapsed) 180f else 0f, label = "chevron")
                    RoundIconButton(
                        Icons.Default.KeyboardArrowUp, stringResource(if (collapsed) R.string.home_expand else R.string.home_collapse),
                        onClick = { collapsed = !collapsed }, iconModifier = Modifier.rotate(chevron), iconTint = c.brand, size = 56.dp,
                    )
                    AnimatedVisibility(!collapsed, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm), horizontalAlignment = Alignment.End) {
                            ParkingButton(onClick = { onSearchCategory(PoiCategory.PARKING) })
                            RoundIconButton(Icons.Default.EmojiEvents, stringResource(R.string.home_ranking), onClick = onOpenProfile, iconTint = c.warning, size = 56.dp)
                            RoundIconButton(Icons.Default.Settings, stringResource(R.string.home_settings), onClick = onSettings, iconTint = c.brand, size = 56.dp)
                            assistantButton()
                            // 1.0 : conduite libre (compteur, limitation, zones de vigilance sans itinéraire) ; pas pendant un guidage.
                            if (!isGuiding && state.permission == LocationPermissionState.GRANTED) {
                                RoundIconButton(Icons.Default.Speed, stringResource(R.string.home_free_drive), onClick = onFreeDrive)
                            }
                        }
                    }
                }
            }
        }

        // Résultat du dernier signalement, 4 s, sous la barre de recherche.
        val reportMessage by vm.reportMessage.collectAsStateWithLifecycle()
        ReportResultPill(reportMessage, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp))

        // Bas d'écran : compteur à gauche, recentrage à droite, puis panneau d'accueil.
        val bottomModifier = if (landscape) {
            Modifier.align(Alignment.BottomEnd).fillMaxWidth(0.5f).navigationBarsPadding().padding(RoadlySpacing.screenEdge)
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.screenEdge)
        }
        Column(bottomModifier) {
            // Fenêtre compacte l'assistant vocal, au-dessus du compteur ; invisible hors session.
            assistantPanel()
            Spacer(Modifier.height(RoadlySpacing.sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                // Limitation connue seulement pendant une conduite libre (lancée depuis la voiture) ; sinon le badge affiche « — ».
                SpeedBadge(speedKmh = state.speedKmh, limitKmh = freeDriveLimit)
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
                Spacer(Modifier.width(RoadlySpacing.sm))
                // Signaler : grand bouton rond, icône ambre (feuille des types).
                RoundIconButton(Icons.Default.Warning, stringResource(R.string.home_report), onClick = { showReport = true }, size = 64.dp, iconTint = c.accent)
            }
            Spacer(Modifier.height(RoadlySpacing.sm))
            when (state.permission) {
                LocationPermissionState.GRANTED -> BottomPanel(
                    state, isGuiding, onNavigateToRoute, onDefine = onSearch, onResumeGuidance = onResumeGuidance, onPosition = { showPosition = true },
                    resumeCard = resumeCard, onResumeLast = { resumeRequested = vm.resumeLastGuidance() }, onDismissResume = { resumeRequested = false; vm.dismissResume() },
                    tollReminders = tollReminders, onTollPaid = vm::dismissTollReminder,
                    parked = parked,
                    onRouteOptions = { showRouteOptions = true },
                    onShowParked = {
                        parked?.let { v ->
                            vm.showParkedVehicle(parkedName, v.label?.let { parkedNear.format(it) })
                            map.moveTo(v.point, PARKED_ZOOM)
                        }
                    },
                )
                else -> PermissionPanel(state.permission, onRequestLocationPermission, onOpenAppSettings)
            }
        }
    }

    val communityEnabled by vm.communityEnabled.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    LaunchedEffect(reports) { map.setReports(reports) }
    if (showReport) ReportSheet(onDismiss = { showReport = false }, enabled = communityEnabled, onSelect = vm::report)
    if (showRouteOptions) RouteOptionsSheet(routePreferences, onChange = vm::setRoutePreferences, onDismiss = { showRouteOptions = false })
    if (showDeparture) DepartureDialog(departurePlan, onPlan = { vm.setDeparturePlan(it); showDeparture = false }, onDismiss = { showDeparture = false })
    val fix = state.fix
    if (showPosition && fix != null) PositionSheet(point = fix.point, address = state.address, onDismiss = { showPosition = false }, onParkHere = { vm.parkHere(); showPosition = false })

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
                if (p.id == PARKED_PLACE_ID) {
                    // Véhicule mémorisé : trajet à pied pour le rejoindre (pas d'itinéraire voiture vers sa propre voiture), ou l'oublier.
                    WalkToCarBlock(walk, onRequest = vm::requestWalkToCar, onClear = vm::clearWalkToCar)
                    Spacer(Modifier.height(RoadlySpacing.s))
                    GlassButton(stringResource(R.string.home_parked_forget), onClick = vm::forgetParkedVehicle, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                } else GlassButton(
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
internal fun BottomPanel(
    state: HomeUiState,
    isGuiding: Boolean,
    onNavigateToRoute: (Place) -> Unit,
    onDefine: () -> Unit,
    onResumeGuidance: () -> Unit,
    onPosition: () -> Unit,
    resumeCard: ResumeCardUi? = null,
    onResumeLast: () -> Unit = {},
    onDismissResume: () -> Unit = {},
    tollReminders: List<TollReminder> = emptyList(),
    onTollPaid: (Long) -> Unit = {},
    parked: ParkedVehicle? = null,
    onRouteOptions: () -> Unit = {},
    onShowParked: () -> Unit = {},
) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column {
            if (isGuiding) {
                GlassButton(stringResource(R.string.home_resume_guidance), onClick = onResumeGuidance, icon = Icons.Default.Navigation, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(RoadlySpacing.sm))
            }
            // E3 : course interrompue (mort du processus, redémarrage) : reprise vers la même destination, depuis la position actuelle.
            if (!isGuiding && resumeCard != null) {
                ResumeGuidanceCard(resumeCard, canResume = state.fix != null, onResume = onResumeLast, onDismiss = onDismissResume)
                Spacer(Modifier.height(RoadlySpacing.sm))
            }
            // 1.0 : péage en flux libre franchi lors d'une course, à payer (le plus récent ; les autres sont comptés).
            tollReminders.firstOrNull()?.let { t ->
                val now = System.currentTimeMillis()
                TollReminderCard(t, others = tollReminders.size - 1, overdue = t.dueAtEpochMs?.let { it < now } == true, onPaid = { onTollPaid(t.id) })
                Spacer(Modifier.height(RoadlySpacing.sm))
            }
            // 1.0 : véhicule garé (position à l'arrivée du dernier guidage, ou « Je suis garé ici »).
            if (!isGuiding && parked != null) {
                ParkedVehicleRow(parked, state.fix?.point, System.currentTimeMillis(), onShow = onShowParked)
                Spacer(Modifier.height(RoadlySpacing.xs))
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
            Spacer(Modifier.height(RoadlySpacing.s))
            // Barre Domicile | options d'itinéraire | Travail.
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShortcutTile(stringResource(R.string.home_home), Icons.Default.Home, state.home, onNavigateToRoute, onDefine, Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(RoadlyShapes.Pill).border(1.dp, c.glassBorder, RoadlyShapes.Pill)
                        .clickable(role = Role.Button, onClick = onRouteOptions),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.home_route_options), tint = c.textSecondary, modifier = Modifier.size(20.dp)) }
                ShortcutTile(stringResource(R.string.home_work), Icons.Default.Work, state.work, onNavigateToRoute, onDefine, Modifier.weight(1f))
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
            .clickable(role = Role.Button, onClick = { if (place != null) onNavigateToRoute(place) else onDefine() })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.brandBright, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(RoadlySpacing.sm))
        Column(Modifier.weight(1f)) {
            OverlineText(label, color = c.textPrimary, dash = false)
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
internal fun CompassButton(bearingDeg: Double, onClick: () -> Unit) {
    val c = LocalRoadlyColors.current
    val angle by animateFloatAsState((-bearingDeg).toFloat(), label = "compass")
    val description = stringResource(R.string.home_compass)
    Box(
        Modifier.size(56.dp).softShadow(RoadlyShapes.Pill, Color.Black, 8.dp).clip(RoadlyShapes.Pill).background(c.surface).border(1.dp, c.glassBorder, RoadlyShapes.Pill)
            .semantics { contentDescription = description }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Cadran qui tourne avec la carte : N (couleur de marque), E, S, O.
        Box(Modifier.fillMaxSize().rotate(angle).padding(3.dp)) {
            val style = MaterialTheme.typography.labelLarge.copy(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text("N", style = style, color = c.brand, modifier = Modifier.align(Alignment.TopCenter).clearAndSetSemantics {})
            Text("E", style = style, color = c.textPrimary, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).clearAndSetSemantics {})
            Text("S", style = style, color = c.textPrimary, modifier = Modifier.align(Alignment.BottomCenter).clearAndSetSemantics {})
            Text("O", style = style, color = c.textPrimary, modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp).clearAndSetSemantics {})
        }
        Icon(Icons.Default.Navigation, contentDescription = null, tint = c.brand, modifier = Modifier.size(20.dp).rotate(angle))
    }
}

/** Position courante : adresse (si géocodée) et coordonnées, à copier ou partager. */
@Composable
private fun PositionSheet(point: GeoPoint, address: String?, onDismiss: () -> Unit, onParkHere: () -> Unit) {
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
            Spacer(Modifier.height(RoadlySpacing.s))
            GlassButton(
                stringResource(R.string.home_park_here), icon = Icons.Default.DirectionsCar, style = GlassButtonStyle.Ghost, modifier = Modifier.fillMaxWidth(),
                onClick = onParkHere,
            )
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

/** Barre de recherche de l'accueil : marque, champ (ouvre la recherche), dictée, flèche « aller ». */
@Composable
internal fun HomeSearchPill(onSearch: () -> Unit, onVoice: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth().height(64.dp), shape = RoadlyShapes.Panel, contentPadding = 0.dp, onClick = onSearch) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoadlyShapes.Pill).background(brandGradient()), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = c.onBrandAuto, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(RoadlySpacing.sm))
            Text(
                stringResource(R.string.home_search_placeholder), style = MaterialTheme.typography.titleMedium, color = c.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(44.dp).clip(RoadlyShapes.Pill).clickable(role = Role.Button, onClick = onVoice), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.home_voice_search), tint = c.textSecondary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(44.dp).clip(RoadlyShapes.Pill).background(c.brandSoft).clickable(role = Role.Button, onClick = onSearch), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.home_search_go), tint = c.brandBright, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Carte « Départ » : partir maintenant, partir à… ou arriver à… ; l'heure est reprise par l'écran d'itinéraires. */
@Composable
internal fun DepartureCard(plan: DeparturePlan?, onClick: () -> Unit) {
    val c = LocalRoadlyColors.current
    val now = System.currentTimeMillis()
    val active = plan?.takeIf { it.epochMs > now }
    val zone = ZoneId.systemDefault()
    GlassPanel(Modifier.fillMaxWidth(), shape = RoadlyShapes.Panel, contentPadding = 0.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoadlyShapes.Pill).background(c.brandSoft), contentAlignment = Alignment.Center) {
                Icon(if (active == null) Icons.Default.Bolt else Icons.Default.Schedule, contentDescription = null, tint = c.brandBright, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(RoadlySpacing.sm))
            Column(Modifier.weight(1f)) {
                OverlineText(stringResource(R.string.home_departure), color = c.brandBright, dash = false)
                Text(
                    when {
                        active == null -> stringResource(R.string.home_departure_now)
                        active.arrive -> stringResource(R.string.home_departure_arrive_at, Formatters.clockTime(active.epochMs, zone))
                        else -> stringResource(R.string.home_departure_at, Formatters.clockTime(active.epochMs, zone))
                    },
                    style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1,
                )
            }
            OverlineText(stringResource(R.string.home_departure_edit), color = c.textSecondary, dash = false)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.brandBright, modifier = Modifier.size(24.dp))
        }
    }
}

/** Bouton « P » (parkings à proximité), pastille bleue des panneaux de stationnement. */
@Composable
internal fun ParkingButton(onClick: () -> Unit) {
    val blue = Color(0xFF2F6FDB)
    val label = stringResource(R.string.home_parkings)
    Box(
        Modifier.semantics(mergeDescendants = true) { contentDescription = label }.size(56.dp).clip(RoadlyShapes.Pill)
            .background(Brush.verticalGradient(listOf(Color(0xFF6FA2F5), blue)))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoadlyShapes.Pill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("P", style = MaterialTheme.typography.titleLarge, color = Color(0xFF0B1B3A), fontWeight = FontWeight.Black, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** Options d'itinéraire depuis l'accueil (mêmes préférences que l'écran d'itinéraires et les réglages). */
@Composable
private fun RouteOptionsSheet(prefs: RoutePreferences, onChange: (RoutePreferences) -> Unit, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            Text(stringResource(R.string.home_route_options_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(RoadlySpacing.m))
            SettingsGroup(stringResource(R.string.home_route_options_avoid), footer = stringResource(R.string.home_route_options_footer)) {
                ToggleRow(stringResource(R.string.home_avoid_tolls), checked = prefs.avoidTolls, onCheckedChange = { onChange(prefs.copy(avoidTolls = it)) }, icon = Icons.Default.Euro)
                ToggleRow(stringResource(R.string.home_avoid_highways), checked = prefs.avoidHighways, onCheckedChange = { onChange(prefs.copy(avoidHighways = it)) }, icon = Icons.Default.AltRoute)
                ToggleRow(stringResource(R.string.home_avoid_tunnels), checked = prefs.avoidTunnels, onCheckedChange = { onChange(prefs.copy(avoidTunnels = it)) }, icon = Icons.Default.Subway)
                ToggleRow(stringResource(R.string.home_avoid_ferries), checked = prefs.avoidFerries, onCheckedChange = { onChange(prefs.copy(avoidFerries = it)) }, icon = Icons.Default.DirectionsBoat)
                ToggleRow(stringResource(R.string.home_avoid_unpaved), checked = prefs.avoidUnpaved, onCheckedChange = { onChange(prefs.copy(avoidUnpaved = it)) }, icon = Icons.Default.Terrain, divider = false)
            }
        }
    }
}

/** Choix de l'heure du départ planifié (boîte commune du design system). */
@Composable
private fun DepartureDialog(plan: DeparturePlan?, onPlan: (DeparturePlan?) -> Unit, onDismiss: () -> Unit) {
    fr.roadly.core.designsystem.component.DepartureTimeDialog(
        initialEpochMs = plan?.epochMs, initialArrive = plan?.arrive == true,
        onConfirm = { ms, arrive -> onPlan(DeparturePlan(ms, arrive)) }, onNow = { onPlan(null) }, onDismiss = onDismiss,
    )
}
