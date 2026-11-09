package fr.roadly.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.SurfaceCard
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.model.CritAir
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.VehicleType
import java.util.Locale

/**
 * Accueil du premier lancement : feuille plein écran posée sur la carte, barre de progression segmentée,
 * « Ignorer » / « Continuer ». Chaque interrupteur modifie le réglage réel (aucune valeur fictive).
 */
@Composable
fun OnboardingScreen(
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    withNotificationPermission: (() -> Unit) -> Unit,
    onOpenProfile: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val done by vm.done.collectAsStateWithLifecycle()
    if (done != false) return
    val settings by vm.settings.collectAsStateWithLifecycle()
    val steps = remember { OnboardingFlow.steps(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[stepIndex.coerceIn(steps.indices)]

    fun advance() {
        val next = OnboardingFlow.next(steps, step)
        if (next == null) vm.finish() else stepIndex = steps.indexOf(next)
    }
    BackHandler(enabled = stepIndex > 0) { stepIndex -= 1 }

    // Localisation : quand la permission arrive pendant l'étape (dialogue système), on passe à la suite.
    var locationAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.permission, step) {
        if (step == OnboardingStep.LOCATION && locationAsked && settings.permission == LocationPermissionState.GRANTED) { locationAsked = false; advance() }
    }

    // Saisie du véhicule, gardée d'une étape à l'autre.
    var vehicleType by rememberSaveable { mutableStateOf<VehicleType?>(null) }
    var fuel by rememberSaveable { mutableStateOf<FuelType?>(null) }
    var consumption by rememberSaveable { mutableStateOf("") }
    var critAir by rememberSaveable { mutableStateOf<CritAir?>(null) }
    var consumptionError by remember { mutableStateOf(false) }
    LaunchedEffect(settings.vehicle) {
        settings.vehicle?.let { v ->
            if (vehicleType == null) vehicleType = v.type
            if (fuel == null) fuel = v.fuel
            if (consumption.isEmpty()) consumption = String.format(Locale.FRANCE, "%.1f", v.consumptionPer100)
            if (critAir == null) critAir = v.critAir
        }
    }

    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(notificationsGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsGranted = notificationsGranted(context) }

    val actions = remember(vm) {
        OnboardingActions(
            setRoute = vm::setRoute, setVoice = vm::setVoice, setVigilance = vm::setVigilance, setVigilanceSound = vm::setVigilanceSound,
            setSpeedAlertSound = vm::setSpeedAlertSound, setLimitDropVoice = vm::setLimitDropVoice, setRainLowersLimit = vm::setRainLowersLimit,
            setBreakReminder = vm::setBreakReminder,
        )
    }
    OnboardingSheet(
        steps = steps, step = step,
        primaryLabel = primaryLabel(step, settings.permission, notificationsGranted),
        onSkip = ::advance,
        onPrimary = {
            when (step) {
                OnboardingStep.LOCATION -> when (settings.permission) {
                    LocationPermissionState.GRANTED -> advance()
                    LocationPermissionState.DENIED_FOREVER -> onOpenAppSettings()
                    else -> { locationAsked = true; onRequestLocationPermission() }
                }
                OnboardingStep.NOTIFICATIONS ->
                    if (notificationsGranted) advance()
                    else withNotificationPermission { notificationsGranted = notificationsGranted(context); advance() }
                OnboardingStep.VEHICLE -> {
                    val t = vehicleType; val f = fuel
                    when {
                        consumption.isBlank() || t == null || f == null -> advance()
                        vm.saveVehicle(t, f, consumption, critAir) -> advance()
                        else -> consumptionError = true
                    }
                }
                else -> advance()
            }
        },
    ) { s ->
        OnboardingStepContent(
            s, settings, notificationsGranted, actions,
            vehicle = VehicleDraft(vehicleType, fuel, consumption, critAir, consumptionError),
            onVehicle = { d ->
                if (d.consumption != consumption) consumptionError = false
                vehicleType = d.type; fuel = d.fuel; consumption = d.consumption; critAir = d.critAir
            },
            onOpenProfile = { vm.finish(); onOpenProfile() },
        )
    }
}

/** Réglages modifiables depuis l'accueil (branchés sur le ViewModel ; factices dans les rendus de test). */
internal class OnboardingActions(
    val setRoute: (fr.roadly.core.model.RoutePreferences) -> Unit = {},
    val setVoice: (Boolean) -> Unit = {},
    val setVigilance: (Boolean) -> Unit = {},
    val setVigilanceSound: (Boolean) -> Unit = {},
    val setSpeedAlertSound: (Boolean) -> Unit = {},
    val setLimitDropVoice: (Boolean) -> Unit = {},
    val setRainLowersLimit: (Boolean) -> Unit = {},
    val setBreakReminder: (Boolean) -> Unit = {},
)

/** Saisie du véhicule en cours. */
internal data class VehicleDraft(
    val type: VehicleType? = null,
    val fuel: FuelType? = null,
    val consumption: String = "",
    val critAir: CritAir? = null,
    val consumptionError: Boolean = false,
)

/** Feuille posée sur la carte : progression, contenu de l'étape, « Ignorer » / bouton principal. Sans état. */
@Composable
internal fun OnboardingSheet(
    steps: List<OnboardingStep>,
    step: OnboardingStep,
    primaryLabel: Int,
    onSkip: () -> Unit,
    onPrimary: () -> Unit,
    content: @Composable (OnboardingStep) -> Unit,
) {
    val c = LocalRoadlyColors.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            // Absorbe les touchers : la carte en dessous ne réagit pas pendant l'accueil.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(Brush.verticalGradient(listOf(c.surfaceElevated, c.background)))
                .border(1.dp, c.glassBorder, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .navigationBarsPadding().imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            ProgressSegments(total = steps.size, filled = OnboardingFlow.filledSegments(steps, step))
            Spacer(Modifier.height(22.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = steps.indexOf(targetState) > steps.indexOf(initialState)
                    (slideInHorizontally { w -> if (forward) w / 4 else -w / 4 } + fadeIn()) togetherWith (slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboarding",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(stringResource(titleOf(s)), style = MaterialTheme.typography.headlineLarge, color = c.textPrimary, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(subtitleOf(s)), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                    Spacer(Modifier.height(22.dp))
                    content(s)
                    Spacer(Modifier.height(RoadlySpacing.l))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                GlassButton(stringResource(R.string.onboarding_skip), onClick = onSkip, style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f))
                GlassButton(stringResource(primaryLabel), style = GlassButtonStyle.Brand, modifier = Modifier.weight(1f), onClick = onPrimary)
            }
        }
    }
}

/** Contenu d'une étape. Sans état. */
@Composable
internal fun OnboardingStepContent(
    s: OnboardingStep,
    settings: OnboardingSettings,
    notificationsGranted: Boolean,
    actions: OnboardingActions,
    vehicle: VehicleDraft,
    onVehicle: (VehicleDraft) -> Unit,
    onOpenProfile: () -> Unit,
) {
    when (s) {
        OnboardingStep.WELCOME -> WelcomeContent()
        OnboardingStep.LOCATION -> LocationContent(settings.permission)
        OnboardingStep.NOTIFICATIONS -> NotificationsContent(notificationsGranted)
        OnboardingStep.VEHICLE -> VehicleContent(
            vehicle.type, { onVehicle(vehicle.copy(type = it)) }, vehicle.fuel, { onVehicle(vehicle.copy(fuel = it)) },
            vehicle.consumption, { onVehicle(vehicle.copy(consumption = it)) }, vehicle.consumptionError,
            vehicle.critAir, { onVehicle(vehicle.copy(critAir = if (vehicle.critAir == it) null else it)) },
        )
        OnboardingStep.ALERTS -> AlertsContent(settings, actions)
        OnboardingStep.ROUTE -> RouteContent(settings, actions)
        OnboardingStep.THANKS -> ThanksContent(onOpenProfile = onOpenProfile)
    }
}

private fun notificationsGranted(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun titleOf(s: OnboardingStep) = when (s) {
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_title
    OnboardingStep.LOCATION -> R.string.onboarding_location_title
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_title
    OnboardingStep.VEHICLE -> R.string.onboarding_vehicle_title
    OnboardingStep.ALERTS -> R.string.onboarding_alerts_title
    OnboardingStep.ROUTE -> R.string.onboarding_route_title
    OnboardingStep.THANKS -> R.string.onboarding_thanks_title
}

private fun subtitleOf(s: OnboardingStep) = when (s) {
    OnboardingStep.WELCOME -> R.string.onboarding_welcome_subtitle
    OnboardingStep.LOCATION -> R.string.onboarding_location_subtitle
    OnboardingStep.NOTIFICATIONS -> R.string.onboarding_notifications_subtitle
    OnboardingStep.VEHICLE -> R.string.onboarding_vehicle_subtitle
    OnboardingStep.ALERTS -> R.string.onboarding_alerts_subtitle
    OnboardingStep.ROUTE -> R.string.onboarding_route_subtitle
    OnboardingStep.THANKS -> R.string.onboarding_thanks_subtitle
}

private fun primaryLabel(s: OnboardingStep, permission: LocationPermissionState, notificationsGranted: Boolean) = when {
    s == OnboardingStep.LOCATION && permission == LocationPermissionState.DENIED_FOREVER -> R.string.onboarding_open_settings
    s == OnboardingStep.LOCATION && permission != LocationPermissionState.GRANTED -> R.string.onboarding_allow
    s == OnboardingStep.NOTIFICATIONS && !notificationsGranted -> R.string.onboarding_allow
    s == OnboardingStep.THANKS -> R.string.onboarding_done
    else -> R.string.onboarding_continue
}

/** Barre de progression : un segment par étape, remplis jusqu'à l'étape affichée. */
@Composable
private fun ProgressSegments(total: Int, filled: Int) {
    val c = LocalRoadlyColors.current
    val label = stringResource(R.string.onboarding_progress, filled, total)
    Row(Modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val color by animateColorAsState(if (i < filled) c.brandBright else c.glassBorder, label = "segment")
            Box(Modifier.weight(1f).height(5.dp).clip(RoadlyShapes.Pill).background(color))
        }
    }
}

/** Carte : grande tuile d'icône, titre, texte. */
@Composable
private fun FeatureCard(icon: ImageVector, title: String, body: String, style: IconTileStyle = IconTileStyle.Brand, footnote: String? = null) {
    val c = LocalRoadlyColors.current
    SurfaceCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(icon, style = style, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                if (footnote != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(footnote, style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun WelcomeContent() {
    Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
        FeatureCard(Icons.Default.Navigation, stringResource(R.string.onboarding_welcome_guidance_title), stringResource(R.string.onboarding_welcome_guidance_body))
        FeatureCard(Icons.Default.Insights, stringResource(R.string.onboarding_welcome_horizon_title), stringResource(R.string.onboarding_welcome_horizon_body))
        FeatureCard(Icons.Default.Shield, stringResource(R.string.onboarding_welcome_vigilance_title), stringResource(R.string.onboarding_welcome_vigilance_body), style = IconTileStyle.Accent)
        FeatureCard(Icons.Default.Groups, stringResource(R.string.onboarding_welcome_community_title), stringResource(R.string.onboarding_welcome_community_body))
    }
}

@Composable
private fun StatusLine(ok: Boolean, okText: String, pendingText: String) {
    val c = LocalRoadlyColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = RoadlySpacing.sm, start = 6.dp)) {
        Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.NotificationsActive, null, tint = if (ok) c.success else c.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (ok) okText else pendingText, style = MaterialTheme.typography.labelLarge, color = if (ok) c.success else c.textSecondary)
    }
}

@Composable
private fun LocationContent(permission: LocationPermissionState) {
    FeatureCard(
        Icons.Default.LocationOn, stringResource(R.string.onboarding_location_card_title), stringResource(R.string.onboarding_location_card_body),
        style = IconTileStyle.BrandFilled, footnote = stringResource(R.string.onboarding_location_card_note),
    )
    StatusLine(
        ok = permission == LocationPermissionState.GRANTED,
        okText = stringResource(R.string.onboarding_location_granted),
        pendingText = stringResource(if (permission == LocationPermissionState.DENIED_FOREVER) R.string.onboarding_location_denied_forever else R.string.onboarding_location_pending),
    )
}

@Composable
private fun NotificationsContent(granted: Boolean) {
    FeatureCard(
        Icons.Default.Notifications, stringResource(R.string.onboarding_notifications_card_title), stringResource(R.string.onboarding_notifications_card_body),
        style = IconTileStyle.BrandFilled,
    )
    StatusLine(ok = granted, okText = stringResource(R.string.onboarding_notifications_granted), pendingText = stringResource(R.string.onboarding_notifications_pending))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleContent(
    type: VehicleType?, onType: (VehicleType) -> Unit,
    fuel: FuelType?, onFuel: (FuelType) -> Unit,
    consumption: String, onConsumption: (String) -> Unit, consumptionError: Boolean,
    critAir: CritAir?, onCritAir: (CritAir) -> Unit,
) {
    val c = LocalRoadlyColors.current
    Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.m)) {
        Section(stringResource(R.string.onboarding_vehicle_type)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                OnboardingFlow.quickVehicleTypes.forEach { t -> GlassChip(stringResource(vehicleLabel(t)), onClick = { onType(t) }, selected = type == t, compact = true) }
            }
        }
        Section(stringResource(R.string.onboarding_vehicle_energy)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                OnboardingFlow.quickFuels.forEach { f -> GlassChip(stringResource(fuelLabel(f)), onClick = { onFuel(f) }, selected = fuel == f, compact = true) }
            }
        }
        Section(stringResource(R.string.onboarding_vehicle_consumption)) {
            OutlinedTextField(
                value = consumption, onValueChange = onConsumption, singleLine = true, isError = consumptionError,
                label = { Text(stringResource(if (fuel == FuelType.ELECTRIC) R.string.onboarding_vehicle_consumption_kwh else R.string.onboarding_vehicle_consumption_l)) },
                supportingText = if (consumptionError) ({ Text(stringResource(R.string.onboarding_vehicle_consumption_error)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoadlyShapes.Tile, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                    focusedBorderColor = c.brand, unfocusedBorderColor = c.glassBorder,
                    focusedLabelColor = c.brand, unfocusedLabelColor = c.textSecondary, cursorColor = c.brand,
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                ),
            )
        }
        Section(stringResource(R.string.onboarding_vehicle_critair)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                CritAir.entries.forEach { ca -> GlassChip(critAirLabel(ca), onClick = { onCritAir(ca) }, selected = critAir == ca, compact = true) }
            }
        }
        Text(stringResource(R.string.onboarding_vehicle_note), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val c = LocalRoadlyColors.current
    Column {
        Text(title.uppercase(Locale.FRANCE), style = MaterialTheme.typography.labelMedium, color = c.textTertiary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun AlertsContent(s: OnboardingSettings, vm: OnboardingActions) {
    Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.l)) {
        SettingsGroup(stringResource(R.string.onboarding_alerts_group_voice)) {
            ToggleRow(stringResource(R.string.onboarding_alerts_voice), checked = s.voice, onCheckedChange = vm.setVoice, icon = Icons.Default.RecordVoiceOver, subtitle = stringResource(R.string.onboarding_alerts_voice_sub))
            ToggleRow(stringResource(R.string.onboarding_alerts_limit_drop), checked = s.limitDropVoice, onCheckedChange = vm.setLimitDropVoice, icon = Icons.Default.TrendingDown, subtitle = stringResource(R.string.onboarding_alerts_limit_drop_sub))
            ToggleRow(stringResource(R.string.onboarding_alerts_break), checked = s.breakReminder, onCheckedChange = vm.setBreakReminder, icon = Icons.Default.Coffee, subtitle = stringResource(R.string.onboarding_alerts_break_sub), divider = false)
        }
        SettingsGroup(stringResource(R.string.onboarding_alerts_group_alerts)) {
            ToggleRow(stringResource(R.string.onboarding_alerts_vigilance), checked = s.vigilance, onCheckedChange = vm.setVigilance, icon = Icons.Default.Shield, iconStyle = IconTileStyle.Accent, subtitle = stringResource(R.string.onboarding_alerts_vigilance_sub))
            ToggleRow(stringResource(R.string.onboarding_alerts_vigilance_sound), checked = s.vigilanceSound, onCheckedChange = vm.setVigilanceSound, icon = Icons.Default.VolumeUp, enabled = s.vigilance)
            ToggleRow(stringResource(R.string.onboarding_alerts_speed_sound), checked = s.speedAlertSound, onCheckedChange = vm.setSpeedAlertSound, icon = Icons.Default.Speed, iconStyle = IconTileStyle.Danger, subtitle = stringResource(R.string.onboarding_alerts_speed_sound_sub), divider = false)
        }
    }
}

@Composable
private fun RouteContent(s: OnboardingSettings, vm: OnboardingActions) {
    val r = s.route
    Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.l)) {
        SettingsGroup(stringResource(R.string.onboarding_route_group_avoid), footer = stringResource(R.string.onboarding_route_footer)) {
            ToggleRow(stringResource(R.string.onboarding_route_tolls), checked = r.avoidTolls, onCheckedChange = { vm.setRoute(r.copy(avoidTolls = it)) }, icon = Icons.Default.Euro)
            ToggleRow(stringResource(R.string.onboarding_route_highways), checked = r.avoidHighways, onCheckedChange = { vm.setRoute(r.copy(avoidHighways = it)) }, icon = Icons.Default.AltRoute)
            ToggleRow(stringResource(R.string.onboarding_route_tunnels), checked = r.avoidTunnels, onCheckedChange = { vm.setRoute(r.copy(avoidTunnels = it)) }, icon = Icons.Default.Subway)
            ToggleRow(stringResource(R.string.onboarding_route_ferries), checked = r.avoidFerries, onCheckedChange = { vm.setRoute(r.copy(avoidFerries = it)) }, icon = Icons.Default.DirectionsBoat, divider = false)
        }
        SettingsGroup(stringResource(R.string.onboarding_route_group_local)) {
            ToggleRow(
                stringResource(R.string.onboarding_route_rain), checked = s.rainLowersLimit, onCheckedChange = vm.setRainLowersLimit,
                icon = Icons.Default.Umbrella, subtitle = stringResource(R.string.onboarding_route_rain_sub), divider = false,
            )
        }
    }
}

@Composable
private fun ThanksContent(onOpenProfile: () -> Unit) {
    val c = LocalRoadlyColors.current
    Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.m)) {
        FeatureCard(
            Icons.Default.EmojiEvents, stringResource(R.string.onboarding_thanks_ranking_title), stringResource(R.string.onboarding_thanks_ranking_body),
            style = IconTileStyle.Accent, footnote = stringResource(R.string.onboarding_thanks_ranking_note),
        )
        SurfaceCard(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
            Column {
                Text(stringResource(R.string.onboarding_thanks_trust), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                Spacer(Modifier.height(RoadlySpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = c.success, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.onboarding_thanks_privacy), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        GlassButton(stringResource(R.string.onboarding_thanks_profile), onClick = onOpenProfile, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.onboarding_thanks_profile_note), style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    }
}

private fun vehicleLabel(t: VehicleType) = when (t) {
    VehicleType.CITY -> R.string.onboarding_vehicle_city
    VehicleType.SEDAN -> R.string.onboarding_vehicle_sedan
    VehicleType.SUV -> R.string.onboarding_vehicle_suv
    VehicleType.VAN -> R.string.onboarding_vehicle_van
    VehicleType.MOTORCYCLE -> R.string.onboarding_vehicle_moto
    VehicleType.TAXI -> R.string.onboarding_vehicle_taxi
    VehicleType.TRUCK -> R.string.onboarding_vehicle_truck
}

private fun fuelLabel(f: FuelType) = when (f) {
    FuelType.SP95 -> R.string.onboarding_fuel_sp95
    FuelType.SP98 -> R.string.onboarding_fuel_sp98
    FuelType.E10 -> R.string.onboarding_fuel_e10
    FuelType.E85 -> R.string.onboarding_fuel_e85
    FuelType.DIESEL -> R.string.onboarding_fuel_diesel
    FuelType.LPG -> R.string.onboarding_fuel_lpg
    FuelType.ELECTRIC -> R.string.onboarding_fuel_electric
}

private fun critAirLabel(ca: CritAir) = when (ca) {
    CritAir.ZERO -> "Crit'Air 0"
    CritAir.C1 -> "Crit'Air 1"
    CritAir.C2 -> "Crit'Air 2"
    CritAir.C3 -> "Crit'Air 3"
    CritAir.C4 -> "Crit'Air 4"
    CritAir.C5 -> "Crit'Air 5"
    CritAir.NONE -> "Non classé"
}
