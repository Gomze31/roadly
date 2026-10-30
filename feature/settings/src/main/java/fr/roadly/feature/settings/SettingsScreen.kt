package fr.roadly.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.StatRow
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleType
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val providerError by vm.providerError.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.padding(RoadlySpacing.m), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back), onClick = onBack)
            Spacer(Modifier.width(RoadlySpacing.s))
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.m),
        ) {
            VehicleSection(state, vm)
            ThemeSection(state.themeMode, onSelect = vm::setTheme)
            VoiceSection(state.voice, onEnabled = vm::setVoiceEnabled, onVolume = vm::setVoiceVolume, onOverSpeedSound = vm::setOverSpeedSound, onTest = vm::testVoice)
            SpeedAlertSection(state.voice.toleranceKmh, onSelect = vm::setSpeedTolerance)
            RoutePreferencesSection(state.routePreferences, onTolls = vm::setAvoidTolls, onHighways = vm::setAvoidHighways, onFerries = vm::setAvoidFerries)
            DataSection(onClearHistory = vm::clearHistory, onClearFavorites = vm::clearFavorites)
            PrivacySection()
            ProvidersSection(state.providers, providerError, onSave = vm::saveProviders, onReset = vm::resetProviders)
            AboutSection()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = LocalRoadlyColors.current.textPrimary)
}

@Composable
private fun settingsFieldColors() = LocalRoadlyColors.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
        focusedBorderColor = c.accent, unfocusedBorderColor = c.glassBorder,
        focusedLabelColor = c.accent, unfocusedLabelColor = c.textSecondary,
        cursorColor = c.accent,
    )
}

@Composable
private fun SettingsField(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true, colors = settingsFieldColors(), modifier = Modifier.fillMaxWidth(),
    )
}

// ── Véhicule ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleSection(state: SettingsUiState, vm: SettingsViewModel) {
    val c = LocalRoadlyColors.current
    // L'état local est réinitialisé dès que le profil enregistré change ; aucun prix par défaut n'est inventé.
    var type by remember(state.vehicle) { mutableStateOf(state.vehicle?.type ?: VehicleType.SEDAN) }
    var fuel by remember(state.vehicle) { mutableStateOf(state.vehicle?.fuel ?: FuelType.SP95) }
    var consumption by remember(state.vehicle) { mutableStateOf(state.vehicle?.consumptionPer100?.toString().orEmpty()) }
    var price by remember(state.vehicle) { mutableStateOf(state.vehicle?.fuelPriceEur?.toString().orEmpty()) }
    val consumptionValid = consumption.trim().replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_vehicle))
            Text(stringResource(R.string.settings_vehicle_type), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                VehicleType.entries.forEach { GlassChip(it.label(), onClick = { type = it }, selected = type == it) }
            }
            Text(stringResource(R.string.settings_fuel), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                FuelType.entries.forEach { GlassChip(it.label(), onClick = { fuel = it }, selected = fuel == it) }
            }
            SettingsField(stringResource(R.string.settings_consumption), consumption, { consumption = it }, KeyboardType.Decimal)
            SettingsField(stringResource(R.string.settings_price), price, { price = it }, KeyboardType.Decimal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(
                    stringResource(R.string.settings_save),
                    onClick = { vm.saveVehicle(type, fuel, consumption, price) },
                    enabled = consumptionValid,
                )
                if (state.vehicle != null) GlassChip(stringResource(R.string.settings_clear_vehicle), onClick = vm::clearVehicle)
            }
        }
    }
}

// ── Thème ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeSection(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_theme))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                ThemeMode.entries.forEach { GlassChip(it.label(), onClick = { onSelect(it) }, selected = current == it) }
            }
        }
    }
}

// ── Données locales ─────────────────────────────────────────────────────────

private enum class PendingClear { HISTORY, FAVORITES }

// ── Voix et sons ────────────────────────────────────────────────────────────

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalRoadlyColors.current
    // Toute la ligne est cliquable (cible ≥ 48 dp) ; le Switch ne fait qu'afficher l'état.
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = RoadlySpacing.touchTarget).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.glass, uncheckedBorderColor = c.glassBorder),
        )
    }
}

@Composable
private fun VoiceSection(voice: VoiceSettings, onEnabled: (Boolean) -> Unit, onVolume: (Int) -> Unit, onOverSpeedSound: (Boolean) -> Unit, onTest: (String) -> Unit) {
    val c = LocalRoadlyColors.current
    // Valeur locale pendant le glissement ; enregistrée au relâchement.
    var volume by remember(voice.volume) { mutableFloatStateOf(voice.volume.toFloat()) }
    val phrase = stringResource(R.string.settings_voice_test_phrase)
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_voice))
            SettingsSwitch(stringResource(R.string.settings_voice_enabled), voice.enabled, onEnabled)
            Text(stringResource(R.string.settings_voice_volume, volume.roundToInt()), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Slider(
                value = volume, onValueChange = { volume = it }, onValueChangeFinished = { onVolume(volume.roundToInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.glassBorder),
            )
            SettingsSwitch(stringResource(R.string.settings_speed_sound), voice.overSpeedSound, onOverSpeedSound)
            GlassButton(stringResource(R.string.settings_voice_test), onClick = { onTest(phrase) }, icon = Icons.AutoMirrored.Filled.VolumeUp, style = GlassButtonStyle.Secondary)
        }
    }
}

// ── Alertes de vitesse ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedAlertSection(toleranceKmh: Int, onSelect: (Int) -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_speed_alerts))
            Text(stringResource(R.string.settings_speed_tolerance), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GuidanceDefaults.ALLOWED_TOLERANCES_KMH.forEach { t ->
                    GlassChip(stringResource(R.string.settings_speed_tolerance_value, t), onClick = { onSelect(t) }, selected = toleranceKmh == t)
                }
            }
            Text(stringResource(R.string.settings_speed_limit_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

// ── Préférences d'itinéraire ────────────────────────────────────────────────

@Composable
private fun RoutePreferencesSection(prefs: RoutePreferences, onTolls: (Boolean) -> Unit, onHighways: (Boolean) -> Unit, onFerries: (Boolean) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs)) {
            SectionTitle(stringResource(R.string.settings_route_prefs))
            SettingsSwitch(stringResource(R.string.settings_avoid_tolls), prefs.avoidTolls, onTolls)
            SettingsSwitch(stringResource(R.string.settings_avoid_highways), prefs.avoidHighways, onHighways)
            SettingsSwitch(stringResource(R.string.settings_avoid_ferries), prefs.avoidFerries, onFerries)
        }
    }
}

@Composable
private fun DataSection(onClearHistory: () -> Unit, onClearFavorites: () -> Unit) {
    var pending by remember { mutableStateOf<PendingClear?>(null) }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_data))
            GlassButton(stringResource(R.string.settings_clear_history), onClick = { pending = PendingClear.HISTORY }, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            GlassButton(stringResource(R.string.settings_clear_favorites), onClick = { pending = PendingClear.FAVORITES }, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }

    pending?.let { which ->
        val history = which == PendingClear.HISTORY
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(if (history) R.string.settings_confirm_history_title else R.string.settings_confirm_favorites_title)) },
            text = { Text(stringResource(if (history) R.string.settings_confirm_history_body else R.string.settings_confirm_favorites_body)) },
            confirmButton = {
                TextButton(onClick = {
                    if (history) onClearHistory() else onClearFavorites()
                    pending = null
                }) { Text(stringResource(R.string.settings_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

// ── Confidentialité ─────────────────────────────────────────────────────────

@Composable
private fun PrivacySection() {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_privacy))
            Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium, color = LocalRoadlyColors.current.textSecondary)
        }
    }
}

// ── Fournisseurs (avancé) ───────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProvidersSection(providers: ProviderConfig, error: String?, onSave: (ProviderConfig) -> Unit, onReset: () -> Unit) {
    // Champs réinitialisés quand la configuration enregistrée change (enregistrement ou réinitialisation).
    var ban by remember(providers) { mutableStateOf(providers.banBaseUrl) }
    var photon by remember(providers) { mutableStateOf(providers.photonBaseUrl) }
    var valhalla by remember(providers) { mutableStateOf(providers.valhallaBaseUrl) }
    var styleLight by remember(providers) { mutableStateOf(providers.mapStyleLightUrl) }
    var styleDark by remember(providers) { mutableStateOf(providers.mapStyleDarkUrl) }

    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_providers))
            SettingsField(stringResource(R.string.settings_provider_ban), ban, { ban = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_photon), photon, { photon = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_valhalla), valhalla, { valhalla = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_style_light), styleLight, { styleLight = it }, KeyboardType.Uri)
            SettingsField(stringResource(R.string.settings_provider_style_dark), styleDark, { styleDark = it }, KeyboardType.Uri)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(
                    stringResource(R.string.settings_save),
                    onClick = {
                        onSave(ProviderConfig(ban.trim(), photon.trim(), valhalla.trim(), styleLight.trim(), styleDark.trim()))
                    },
                    // Une URL vide casserait la recherche ou la carte : on ne l'enregistre pas.
                    enabled = listOf(ban, photon, valhalla, styleLight, styleDark).all { it.isNotBlank() },
                )
                GlassButton(stringResource(R.string.settings_reset), onClick = onReset, style = GlassButtonStyle.Secondary)
            }
            if (error != null) Text(stringResource(R.string.settings_provider_invalid, error), style = MaterialTheme.typography.bodySmall, color = LocalRoadlyColors.current.danger)
        }
    }
}

// ── À propos ────────────────────────────────────────────────────────────────

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember(context) { context.versionName() }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_about))
            if (version != null) StatRow(stringResource(R.string.settings_version), version)
            Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.bodySmall, color = LocalRoadlyColors.current.textSecondary)
        }
    }
}

/** `BuildConfig` du module app est inaccessible ici : on lit la version via le PackageManager. */
private fun Context.versionName(): String? = try {
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

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.AUTO_SOLAR -> R.string.settings_theme_auto
    },
)
