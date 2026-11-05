package fr.roadly.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.data.community.CommunityConfig
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.RoadlyScreen
import fr.roadly.core.designsystem.component.RoadlyTopBar
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.SegmentedControl
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.StatRow
import fr.roadly.core.designsystem.component.StatusDot
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.theme.CockpitTint
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.CritAir
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.VehicleDimensions
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import java.util.Locale
import kotlin.math.roundToInt

/** Retrait du contenu placé sous une ligne avec tuile d'icône (aligné sur le texte de la ligne). */
private val RowContentInset = 64.dp

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenTrips: () -> Unit = {}, onOpenProfile: () -> Unit = {}, onOpenPremium: () -> Unit = {}, onOpenAssistance: () -> Unit = {}, onOpenRadar: () -> Unit = {}, vm: SettingsViewModel = hiltViewModel(), scooterVm: ScooterSettingsViewModel = hiltViewModel(), updatesVm: UpdatesViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val providerError by vm.providerError.collectAsStateWithLifecycle()
    val vigilanceRefresh by vm.vigilanceRefresh.collectAsStateWithLifecycle()
    val community by vm.community.collectAsStateWithLifecycle()
    val tint by vm.tint.collectAsStateWithLifecycle()

    RoadlyScreen {
        RoadlyTopBar(title = stringResource(R.string.settings_title), onBack = onBack, backDescription = stringResource(R.string.settings_back))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            DrivingGroup(state, vm)
            ScooterSettingsGroup(scooterVm)
            SettingsGroup(stringResource(R.string.settings_group_trips)) {
                ListRow(stringResource(R.string.settings_profile), subtitle = stringResource(R.string.settings_profile_subtitle), icon = Icons.Default.Person, onClick = onOpenProfile)
                ListRow(stringResource(R.string.settings_trips), subtitle = stringResource(R.string.settings_trips_subtitle), icon = Icons.Default.History, onClick = onOpenTrips, divider = false)
            }
            SettingsGroup(stringResource(R.string.settings_group_plus)) {
                ListRow(stringResource(R.string.settings_premium), subtitle = stringResource(R.string.settings_premium_subtitle), icon = Icons.Default.Star, onClick = onOpenPremium)
                ListRow(stringResource(R.string.settings_assistance), subtitle = stringResource(R.string.settings_assistance_subtitle), icon = Icons.Default.SupportAgent, onClick = onOpenAssistance, divider = false)
            }
            val navigation by vm.navigation.collectAsStateWithLifecycle()
            NavigationGroup(navigation, vm)
            AppearanceGroup(state.themeMode, onSelect = vm::setTheme)
            TintGroup(tint, onSelect = vm::setTint)
            VoiceGroup(state.voice, onEnabled = vm::setVoiceEnabled, onVolume = vm::setVoiceVolume, onOverSpeedSound = vm::setOverSpeedSound, onTest = vm::testVoice)
            AssistantGroup()
            SpeedAlertGroup(state.voice.toleranceKmh, onSelect = vm::setSpeedTolerance)
            val coach by vm.coach.collectAsStateWithLifecycle()
            AlertsGroup(
                state.alerts, vigilanceRefresh, onRain = vm::setRainLowersLimit, onOpenRadar = onOpenRadar,
                limitDropVoice = coach.first, onLimitDropVoice = vm::setLimitDropVoice, breakReminder = coach.second, onBreakReminder = vm::setBreakReminder,
            )
            RoutePreferencesGroup(state.routePreferences, onTolls = vm::setAvoidTolls, onHighways = vm::setAvoidHighways, onFerries = vm::setAvoidFerries)
            DataGroup(onClearHistory = vm::clearHistory, onClearFavorites = vm::clearFavorites)
            PrivacyGroup()
            CommunityGroup(community, onSave = vm::saveCommunity, onClear = vm::clearCommunity)
            ProvidersGroup(state.providers, providerError, onSave = vm::saveProviders, onReset = vm::resetProviders)
            AndroidAutoGroup()
            UpdatesGroup(updatesVm)
            AboutGroup()
            Spacer(Modifier.height(RoadlySpacing.s))
        }
    }
}

// ── Briques communes ────────────────────────────────────────────────────────

@Composable
internal fun settingsFieldColors() = LocalRoadlyColors.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
        focusedBorderColor = c.brand, unfocusedBorderColor = c.glassBorder,
        focusedLabelColor = c.brand, unfocusedLabelColor = c.textSecondary,
        cursorColor = c.brand,
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    )
}

@Composable
internal fun SettingsField(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType, masked: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true, colors = settingsFieldColors(), shape = RoadlyShapes.Tile, modifier = Modifier.fillMaxWidth(),
    )
}

/** Flèche de repli/dépli placée à droite d'une ligne qui ouvre un éditeur en ligne. */
@Composable
internal fun ExpandArrow(expanded: Boolean) {
    Icon(
        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null, tint = LocalRoadlyColors.current.textTertiary, modifier = Modifier.size(22.dp),
    )
}

/** Zone dépliable sous une ligne : même carte, retrait horizontal standard. */
@Composable
internal fun ExpandableContent(expanded: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(
            Modifier.fillMaxWidth().padding(start = RoadlySpacing.m, end = RoadlySpacing.m, top = RoadlySpacing.xs, bottom = RoadlySpacing.m),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm),
        ) { content() }
    }
}

@Composable
internal fun FieldCaption(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = LocalRoadlyColors.current.textSecondary)
}

// ── Conduite : véhicule ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrivingGroup(state: SettingsUiState, vm: SettingsViewModel) {
    // L'état local est réinitialisé dès que le profil enregistré change ; aucun prix par défaut n'est inventé.
    var type by remember(state.vehicle) { mutableStateOf(state.vehicle?.type ?: VehicleType.SEDAN) }
    var fuel by remember(state.vehicle) { mutableStateOf(state.vehicle?.fuel ?: FuelType.SP95) }
    var consumption by remember(state.vehicle) { mutableStateOf(state.vehicle?.consumptionPer100?.toString().orEmpty()) }
    var price by remember(state.vehicle) { mutableStateOf(state.vehicle?.fuelPriceEur?.toString().orEmpty()) }
    val dims = state.vehicle?.dimensions
    var height by remember(state.vehicle) { mutableStateOf(dims?.heightM?.toString().orEmpty()) }
    var width by remember(state.vehicle) { mutableStateOf(dims?.widthM?.toString().orEmpty()) }
    var length by remember(state.vehicle) { mutableStateOf(dims?.lengthM?.toString().orEmpty()) }
    var weight by remember(state.vehicle) { mutableStateOf(dims?.weightT?.toString().orEmpty()) }
    var axles by remember(state.vehicle) { mutableStateOf(dims?.axles?.toString().orEmpty()) }
    var critAir by remember(state.vehicle) { mutableStateOf(state.vehicle?.critAir) }
    val consumptionValid = consumption.trim().replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true
    // Éditeur replié par défaut ; la ligne résume le profil enregistré.
    var editing by remember { mutableStateOf(false) }

    SettingsGroup(stringResource(R.string.settings_group_driving)) {
        ListRow(
            stringResource(R.string.settings_vehicle),
            subtitle = state.vehicle?.summary() ?: stringResource(R.string.settings_vehicle_none),
            icon = Icons.Default.DirectionsCar,
            onClick = { editing = !editing },
            chevron = false,
            divider = false,
            trailing = { ExpandArrow(editing) },
        )
        ExpandableContent(editing) {
            FieldCaption(stringResource(R.string.settings_vehicle_type))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                VehicleType.entries.forEach { GlassChip(it.label(), onClick = { type = it }, selected = type == it, compact = true) }
            }
            FieldCaption(stringResource(R.string.settings_fuel))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                FuelType.entries.forEach { GlassChip(it.label(), onClick = { fuel = it }, selected = fuel == it, compact = true) }
            }
            SettingsField(stringResource(R.string.settings_consumption), consumption, { consumption = it }, KeyboardType.Decimal)
            SettingsField(stringResource(R.string.settings_price), price, { price = it }, KeyboardType.Decimal)
            // Gabarit : poids lourd et utilitaire seulement ; influence réelle sur le routage en costing poids lourd (Valhalla `truck`).
            if (type.isHeavy || type == VehicleType.VAN) {
                FieldCaption(stringResource(R.string.settings_dimensions))
                SettingsField(stringResource(R.string.settings_height), height, { height = it }, KeyboardType.Decimal)
                SettingsField(stringResource(R.string.settings_width), width, { width = it }, KeyboardType.Decimal)
                SettingsField(stringResource(R.string.settings_length), length, { length = it }, KeyboardType.Decimal)
                SettingsField(stringResource(R.string.settings_weight), weight, { weight = it }, KeyboardType.Decimal)
                if (type.isHeavy) SettingsField(stringResource(R.string.settings_axles), axles, { axles = it }, KeyboardType.Number)
                Text(stringResource(if (type.isHeavy) R.string.settings_dimensions_note_truck else R.string.settings_dimensions_note_van), style = MaterialTheme.typography.bodySmall, color = LocalRoadlyColors.current.textTertiary)
            }
            FieldCaption(stringResource(R.string.settings_critair))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                CritAir.entries.forEach { GlassChip(it.label(), onClick = { critAir = if (critAir == it) null else it }, selected = critAir == it, compact = true) }
            }
            Text(stringResource(R.string.settings_critair_note), style = MaterialTheme.typography.bodySmall, color = LocalRoadlyColors.current.textTertiary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(
                    stringResource(R.string.settings_save),
                    onClick = {
                        val d = VehicleDimensions(height.toFrDoubleOrNull(), width.toFrDoubleOrNull(), length.toFrDoubleOrNull(), weight.toFrDoubleOrNull(), axles.trim().toIntOrNull())
                        vm.saveVehicle(type, fuel, consumption, price, d, critAir)
                    },
                    enabled = consumptionValid,
                )
                if (state.vehicle != null) GlassButton(stringResource(R.string.settings_clear_vehicle), onClick = vm::clearVehicle, style = GlassButtonStyle.Danger)
            }
        }
    }
}

/** « Berline · SP95 · 6,5 L/100 » (kWh/100 pour l'électrique). */
@Composable
private fun VehicleProfile.summary(): String {
    val cons = String.format(Locale.FRANCE, "%.1f", consumptionPer100)
    val res = if (fuel == FuelType.ELECTRIC) R.string.settings_vehicle_summary_electric else R.string.settings_vehicle_summary_fuel
    return stringResource(res, type.label(), fuel.label(), cons)
}

// ── Apparence : thème ───────────────────────────────────────────────────────

@Composable
private fun AppearanceGroup(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_appearance)) {
        ListRow(
            stringResource(R.string.settings_theme),
            subtitle = stringResource(R.string.settings_theme_auto),
            icon = Icons.Default.Palette,
            divider = false,
        )
        // Les libellés sont résolus ici : le sélecteur reçoit une fonction non composable.
        val labels = ThemeMode.entries.associateWith { it.shortLabel() }
        SegmentedControl(
            options = ThemeMode.entries, selected = current, onSelect = onSelect, label = { labels.getValue(it) },
            modifier = Modifier.padding(start = RoadlySpacing.m, end = RoadlySpacing.m, bottom = RoadlySpacing.m),
        )
    }
}

// ── Voix et sons ────────────────────────────────────────────────────────────

@Composable
private fun VoiceGroup(voice: VoiceSettings, onEnabled: (Boolean) -> Unit, onVolume: (Int) -> Unit, onOverSpeedSound: (Boolean) -> Unit, onTest: (String) -> Unit) {
    val c = LocalRoadlyColors.current
    // Valeur locale pendant le glissement ; enregistrée au relâchement.
    var volume by remember(voice.volume) { mutableFloatStateOf(voice.volume.toFloat()) }
    val phrase = stringResource(R.string.settings_voice_test_phrase)
    SettingsGroup(stringResource(R.string.settings_voice)) {
        ToggleRow(stringResource(R.string.settings_voice_enabled), checked = voice.enabled, onCheckedChange = onEnabled, icon = Icons.Default.RecordVoiceOver)
        ListRow(
            stringResource(R.string.settings_voice_volume),
            icon = Icons.Default.GraphicEq,
            value = stringResource(R.string.settings_voice_volume_value, volume.roundToInt()),
            divider = false,
        )
        Slider(
            value = volume, onValueChange = { volume = it }, onValueChangeFinished = { onVolume(volume.roundToInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = c.brand, activeTrackColor = c.brand, inactiveTrackColor = c.glassBorder),
            modifier = Modifier.padding(start = RowContentInset, end = RoadlySpacing.m, bottom = RoadlySpacing.xs),
        )
        RowDivider(inset = RowContentInset)
        ToggleRow(stringResource(R.string.settings_speed_sound), checked = voice.overSpeedSound, onCheckedChange = onOverSpeedSound, icon = Icons.Default.Speed)
        ListRow(
            stringResource(R.string.settings_voice_test),
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            onClick = { onTest(phrase) },
            chevron = false,
            divider = false,
            trailing = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = c.brandBright, modifier = Modifier.size(22.dp)) },
        )
    }
}

// ── Alertes de vitesse ──────────────────────────────────────────────────────

@Composable
private fun SpeedAlertGroup(toleranceKmh: Int, onSelect: (Int) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_speed_alerts), footer = stringResource(R.string.settings_speed_limit_note)) {
        ListRow(stringResource(R.string.settings_speed_tolerance), icon = Icons.Default.Speed, iconStyle = IconTileStyle.Warning, divider = false)
        val options = GuidanceDefaults.ALLOWED_TOLERANCES_KMH
        val labels = options.associateWith { stringResource(R.string.settings_speed_tolerance_value, it) }
        SegmentedControl(
            options = options, selected = toleranceKmh, onSelect = onSelect, label = { labels.getValue(it) },
            modifier = Modifier.padding(start = RoadlySpacing.m, end = RoadlySpacing.m, bottom = RoadlySpacing.m),
        )
    }
}

// ── Alertes : zones de vigilance, pluie ─────────────────────────────────────

@Composable
private fun AlertsGroup(
    alerts: AlertSettings, refresh: RefreshState, onRain: (Boolean) -> Unit, onOpenRadar: () -> Unit,
    limitDropVoice: Boolean = true, onLimitDropVoice: (Boolean) -> Unit = {}, breakReminder: Boolean = true, onBreakReminder: (Boolean) -> Unit = {},
) {
    val c = LocalRoadlyColors.current
    val updated = alerts.zonesUpdatedAtMs?.let { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, java.util.Locale.FRANCE).format(java.util.Date(it)) }
    val subtitle = when {
        refresh == RefreshState.Loading -> stringResource(R.string.settings_vigilance_loading)
        refresh is RefreshState.Failed -> stringResource(R.string.settings_vigilance_failed)
        alerts.zoneCount == 0 -> stringResource(R.string.settings_vigilance_empty)
        else -> stringResource(R.string.settings_vigilance_count, alerts.zoneCount, updated ?: "—")
    }
    SettingsGroup(stringResource(R.string.settings_group_alerts), footer = stringResource(R.string.settings_vigilance_footer)) {
        // Radar Intelligence : activation, voix, préavis, catégories, base locale et synchronisation dans un écran dédié.
        ListRow(
            stringResource(R.string.settings_vigilance),
            subtitle = if (alerts.vigilanceEnabled) subtitle else stringResource(R.string.settings_vigilance_off),
            icon = Icons.Default.Shield, iconStyle = IconTileStyle.Warning, onClick = onOpenRadar,
        )
        ToggleRow(stringResource(R.string.settings_rain_limit), checked = alerts.rainLowersLimit, onCheckedChange = onRain, icon = Icons.Default.WaterDrop, subtitle = stringResource(R.string.settings_rain_limit_subtitle))
        ToggleRow(stringResource(R.string.settings_limit_drop), checked = limitDropVoice, onCheckedChange = onLimitDropVoice, icon = Icons.Default.Speed, subtitle = stringResource(R.string.settings_limit_drop_subtitle))
        ToggleRow(stringResource(R.string.settings_break_reminder), checked = breakReminder, onCheckedChange = onBreakReminder, icon = Icons.Default.LocalCafe, subtitle = stringResource(R.string.settings_break_reminder_subtitle), divider = false)
    }
}

// ── Itinéraires ─────────────────────────────────────────────────────────────

@Composable
private fun RoutePreferencesGroup(prefs: RoutePreferences, onTolls: (Boolean) -> Unit, onHighways: (Boolean) -> Unit, onFerries: (Boolean) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_routes)) {
        ToggleRow(stringResource(R.string.settings_avoid_tolls), checked = prefs.avoidTolls, onCheckedChange = onTolls, icon = Icons.Default.Toll)
        ToggleRow(stringResource(R.string.settings_avoid_highways), checked = prefs.avoidHighways, onCheckedChange = onHighways, icon = Icons.Default.Route)
        ToggleRow(stringResource(R.string.settings_avoid_ferries), checked = prefs.avoidFerries, onCheckedChange = onFerries, icon = Icons.Default.DirectionsBoat, divider = false)
    }
}

// ── Données locales ─────────────────────────────────────────────────────────

private enum class PendingClear { HISTORY, FAVORITES }

@Composable
private fun DataGroup(onClearHistory: () -> Unit, onClearFavorites: () -> Unit) {
    val c = LocalRoadlyColors.current
    var pending by remember { mutableStateOf<PendingClear?>(null) }

    SettingsGroup(stringResource(R.string.settings_data)) {
        ListRow(stringResource(R.string.settings_clear_history), icon = Icons.Default.History, iconStyle = IconTileStyle.Danger, onClick = { pending = PendingClear.HISTORY })
        ListRow(stringResource(R.string.settings_clear_favorites), icon = Icons.Default.Star, iconStyle = IconTileStyle.Danger, onClick = { pending = PendingClear.FAVORITES }, divider = false)
    }

    pending?.let { which ->
        val history = which == PendingClear.HISTORY
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = c.surfaceElevated,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(stringResource(if (history) R.string.settings_confirm_history_title else R.string.settings_confirm_favorites_title)) },
            text = { Text(stringResource(if (history) R.string.settings_confirm_history_body else R.string.settings_confirm_favorites_body)) },
            confirmButton = {
                TextButton(onClick = {
                    if (history) onClearHistory() else onClearFavorites()
                    pending = null
                }) { Text(stringResource(R.string.settings_confirm_action), color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.settings_cancel), color = c.textSecondary) } },
        )
    }
}

// ── Confidentialité ─────────────────────────────────────────────────────────

@Composable
private fun PrivacyGroup() {
    SettingsGroup(stringResource(R.string.settings_privacy)) {
        Row(Modifier.fillMaxWidth().padding(RoadlySpacing.m), verticalAlignment = Alignment.Top) {
            IconTile(Icons.Default.Shield, size = 36.dp)
            Spacer(Modifier.width(RoadlySpacing.sm))
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium, color = LocalRoadlyColors.current.textSecondary)
        }
    }
}

// ── Personnalisation : teinte du cockpit ────────────────────────────────────

@Composable
private fun TintGroup(current: String?, onSelect: (String?) -> Unit) {
    val c = LocalRoadlyColors.current
    SettingsGroup(stringResource(R.string.settings_group_personalization), footer = stringResource(R.string.settings_tint_footer)) {
        ListRow(stringResource(R.string.settings_tint), subtitle = stringResource(R.string.settings_tint_subtitle), icon = Icons.Default.ColorLens, chevron = false, divider = false)
        Row(Modifier.padding(start = RoadlySpacing.m, end = RoadlySpacing.m, bottom = RoadlySpacing.m), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
            CockpitTint.entries.forEach { t ->
                val color = if (c.isDark) t.night.second else t.day.first
                val selected = (current ?: CockpitTint.VIOLET.name) == t.name
                Box(
                    Modifier.size(44.dp).clip(RoadlyShapes.Pill).background(color)
                        .border(if (selected) 3.dp else 0.dp, if (selected) c.textPrimary else androidx.compose.ui.graphics.Color.Transparent, RoadlyShapes.Pill)
                        .clickable(role = Role.RadioButton) { onSelect(if (t == CockpitTint.VIOLET) null else t.name) },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Default.Check, contentDescription = t.name, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

// ── Communauté (signalements) ───────────────────────────────────────────────

@Composable
private fun CommunityGroup(cfg: CommunityConfig, onSave: (String, String) -> Unit, onClear: () -> Unit) {
    val c = LocalRoadlyColors.current
    var url by remember(cfg) { mutableStateOf(cfg.supabaseUrl) }
    var key by remember(cfg) { mutableStateOf(cfg.anonKey) }
    var editing by remember { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.settings_group_community), footer = stringResource(R.string.settings_community_footer)) {
        ListRow(
            stringResource(R.string.settings_community_service),
            subtitle = stringResource(if (cfg.isConfigured) R.string.settings_community_connected else R.string.settings_community_unconfigured),
            icon = Icons.Default.Groups, iconStyle = if (cfg.isConfigured) IconTileStyle.Brand else IconTileStyle.Neutral,
            onClick = { editing = !editing }, chevron = false, divider = false,
            trailing = { StatusDot(if (cfg.isConfigured) c.success else c.textTertiary); Spacer(Modifier.width(RoadlySpacing.s)); ExpandArrow(editing) },
        )
        ExpandableContent(editing) {
            SettingsField(stringResource(R.string.settings_community_url), url, { url = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_community_key), key, { key = it }, KeyboardType.Password)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(stringResource(R.string.settings_save), onClick = { onSave(url, key) }, enabled = CommunityConfig(url, key).isConfigured)
                if (cfg.isConfigured) GlassButton(stringResource(R.string.settings_community_disconnect), onClick = onClear, style = GlassButtonStyle.Danger)
            }
        }
    }
}

// ── Fournisseurs (avancé) ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProvidersGroup(providers: ProviderConfig, error: String?, onSave: (ProviderConfig) -> Unit, onReset: () -> Unit) {
    // Champs réinitialisés quand la configuration enregistrée change (enregistrement ou réinitialisation).
    var ban by remember(providers) { mutableStateOf(providers.banBaseUrl) }
    var photon by remember(providers) { mutableStateOf(providers.photonBaseUrl) }
    var valhalla by remember(providers) { mutableStateOf(providers.valhallaBaseUrl) }
    var styleLight by remember(providers) { mutableStateOf(providers.mapStyleLightUrl) }
    var styleDark by remember(providers) { mutableStateOf(providers.mapStyleDarkUrl) }
    var overpass by remember(providers) { mutableStateOf(providers.overpassBaseUrl) }
    // Réglage avancé : replié par défaut pour ne pas encombrer l'écran.
    var expanded by remember { mutableStateOf(false) }

    SettingsGroup(stringResource(R.string.settings_providers)) {
        ListRow(
            stringResource(R.string.settings_providers_servers),
            subtitle = stringResource(R.string.settings_providers_servers_subtitle),
            icon = Icons.Default.Dns,
            iconStyle = IconTileStyle.Neutral,
            onClick = { expanded = !expanded },
            chevron = false,
            divider = false,
            trailing = { ExpandArrow(expanded) },
        )
        ExpandableContent(expanded) {
            SettingsField(stringResource(R.string.settings_provider_ban), ban, { ban = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_photon), photon, { photon = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_valhalla), valhalla, { valhalla = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_style_light), styleLight, { styleLight = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_style_dark), styleDark, { styleDark = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_overpass), overpass, { overpass = it }, KeyboardType.Uri)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(
                    stringResource(R.string.settings_save),
                    onClick = {
                        onSave(ProviderConfig(ban.trim(), photon.trim(), valhalla.trim(), styleLight.trim(), styleDark.trim(), overpass.trim()))
                    },
                    // Une URL vide casserait la recherche ou la carte : on ne l'enregistre pas.
                    enabled = listOf(ban, photon, valhalla, styleLight, styleDark, overpass).all { it.isNotBlank() },
                )
                GlassButton(stringResource(R.string.settings_reset), onClick = onReset, style = GlassButtonStyle.Secondary)
            }
            if (error != null) Text(stringResource(R.string.settings_provider_invalid, error), style = MaterialTheme.typography.bodySmall, color = LocalRoadlyColors.current.danger)
        }
    }
}

// ── À propos ────────────────────────────────────────────────────────────────

@Composable
private fun AboutGroup() {
    val context = LocalContext.current
    val version = remember(context) { context.versionName() }
    SettingsGroup(stringResource(R.string.settings_about)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s)) {
            if (version != null) {
                StatRow(stringResource(R.string.settings_version), version)
                RowDivider(inset = 0.dp)
                Spacer(Modifier.height(RoadlySpacing.s))
            }
            Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.bodySmall, color = LocalRoadlyColors.current.textTertiary)
        }
    }
}

/** `BuildConfig` du module app est inaccessible ici : on lit la version via le PackageManager. */
internal fun Context.versionName(): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName
    }
} catch (_: PackageManager.NameNotFoundException) {
    null
}

// ── Libellés ────────────────────────────────────────────────────────────────

@Composable
internal fun VehicleType.label(): String = stringResource(
    when (this) {
        VehicleType.CITY -> R.string.settings_vehicle_city
        VehicleType.SEDAN -> R.string.settings_vehicle_sedan
        VehicleType.SUV -> R.string.settings_vehicle_suv
        VehicleType.VAN -> R.string.settings_vehicle_van
        VehicleType.MOTORCYCLE -> R.string.settings_vehicle_motorcycle
        VehicleType.TAXI -> R.string.settings_vehicle_taxi
        VehicleType.TRUCK -> R.string.settings_vehicle_truck
    },
)

@Composable
internal fun FuelType.label(): String = stringResource(
    when (this) {
        FuelType.SP95 -> R.string.settings_fuel_sp95
        FuelType.SP98 -> R.string.settings_fuel_sp98
        FuelType.E10 -> R.string.settings_fuel_e10
        FuelType.E85 -> R.string.settings_fuel_e85
        FuelType.DIESEL -> R.string.settings_fuel_diesel
        FuelType.LPG -> R.string.settings_fuel_lpg
        FuelType.ELECTRIC -> R.string.settings_fuel_electric
    },
)

/** Libellé court pour le sélecteur segmenté ; le détail du mode solaire est donné en sous-titre. */
@Composable
private fun ThemeMode.shortLabel(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.AUTO_SOLAR -> R.string.settings_theme_auto_short
    },
)

@Composable
internal fun CritAir.label(): String = when (this) {
    CritAir.ZERO -> "Crit'Air 0"; CritAir.C1 -> "1"; CritAir.C2 -> "2"; CritAir.C3 -> "3"; CritAir.C4 -> "4"; CritAir.C5 -> "5"
    CritAir.NONE -> stringResource(R.string.settings_critair_none)
}

internal fun String.toFrDoubleOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
