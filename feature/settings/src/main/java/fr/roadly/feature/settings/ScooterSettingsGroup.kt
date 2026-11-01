package fr.roadly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.BatteryDialog
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.component.TravelModeSelector
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.ComfortLevel
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.SuspensionType
import fr.roadly.core.model.TireType
import fr.roadly.core.model.TravelMode
import fr.roadly.core.model.TripPeriodStats
import java.util.Locale
import kotlin.math.roundToInt

private fun TireType.label() = when (this) {
    TireType.PNEUMATIC_TUBELESS -> "Tubeless"; TireType.PNEUMATIC_TUBE -> "Chambre à air"; TireType.SOLID -> "Pleins"
    TireType.HONEYCOMB -> "Alvéolés"; TireType.UNKNOWN -> "Inconnu"
}
private fun SuspensionType.label() = when (this) {
    SuspensionType.NONE -> "Aucune"; SuspensionType.FRONT -> "Avant"; SuspensionType.REAR -> "Arrière"
    SuspensionType.DUAL -> "Avant + arrière"; SuspensionType.UNKNOWN -> "Inconnue"
}
private fun ComfortLevel.label() = when (this) {
    ComfortLevel.LOW -> "Faible"; ComfortLevel.MEDIUM -> "Moyen"; ComfortLevel.HIGH -> "Bon"; ComfortLevel.UNKNOWN -> "Non précisé"
}
private fun Double?.field(): String = this?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') }.orEmpty()
private fun String.optDouble(): Double? = toFrDoubleOrNull()

/** Réglages → Trottinette : mode actif, fiches (plusieurs), profil actif, batterie, calibration, bilan, données OSM. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScooterSettingsGroup(vm: ScooterSettingsViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    var editingId by remember { mutableStateOf<Long?>(null) }
    var batteryDialog by remember { mutableStateOf(false) }

    SettingsGroup("Trottinette électrique") {
        Text("Mode de déplacement", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
        TravelModeSelector(ui.travelMode ?: TravelMode.CAR, vm::setTravelMode)
        ui.profiles.forEach { p ->
            val active = p.id == ui.activeId
            ListRow(
                p.name + if (active) " · actif" else "",
                subtitle = listOfNotNull(
                    "${p.maxSpeedKmh.roundToInt()} km/h",
                    p.batteryWh?.let { "${it.roundToInt()} Wh" },
                    p.claimedRangeKm?.let { "${it.roundToInt()} km annoncés" },
                    p.wheelSizeInch?.let { "${it.field()}\"" },
                ).joinToString(" · "),
                icon = Icons.Default.ElectricScooter,
                onClick = { if (!active) vm.setActive(p.id); editingId = if (editingId == p.id) null else p.id },
                chevron = false,
                trailing = { ExpandArrow(editingId == p.id) },
            )
            ExpandableContent(editingId == p.id) { ScooterEditor(p, onSave = { vm.save(it); editingId = null }, onDelete = { vm.delete(p.id); editingId = null }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            GlassButton("Ajouter une MAX G2", onClick = vm::addPreset, style = GlassButtonStyle.Secondary)
            GlassButton("Nouvelle fiche", onClick = vm::addBlank, style = GlassButtonStyle.Secondary)
        }
        ListRow(
            "Batterie",
            subtitle = ui.battery?.let { b ->
                "${b.pct} % saisis à ${Formatters.clockTime(b.atEpochMs, java.time.ZoneId.systemDefault())}" +
                    (ui.remainingRangeKm?.let { " · ≈ ${it.roundToInt()} km sur le plat (estimation)" } ?: "")
            } ?: "Non saisie : Roadly ne lit pas la batterie de la trottinette",
            icon = Icons.Default.BatteryStd, onClick = { batteryDialog = true },
        )
        ListRow(
            "Calibration de l'autonomie",
            subtitle = if (ui.calibration.calibrated) "${ui.calibration.trips} trajet(s) réel(s) · facteur ${String.format(Locale.FRANCE, "%.2f", ui.calibration.factor)}"
            else "Aucun trajet calibré : saisissez la batterie au départ et à l'arrivée",
            icon = Icons.Default.Insights,
        )
        ui.week?.let { w -> ListRow("Cette semaine", subtitle = w.summary(), icon = Icons.Default.Route) }
        ui.month?.let { m -> ListRow("Ce mois-ci", subtitle = m.summary(), icon = Icons.Default.Route) }
        ToggleRow(
            "Analyse détaillée des voies (OSM)",
            checked = ui.osmEnrichment, onCheckedChange = vm::setOsmEnrichment,
            subtitle = "Une requête Overpass par itinéraire : agglomération, éclairage, restrictions locales, revêtement",
            divider = false,
        )
    }
    if (batteryDialog) BatteryDialog(ui.battery?.pct, onDismiss = { batteryDialog = false }, onSave = { vm.setBattery(it); batteryDialog = false })
}

private fun TripPeriodStats.summary(): String = if (trips == 0) "Aucun trajet" else listOfNotNull(
    "$trips trajet(s)", Formatters.distance(distanceM), Formatters.duration(durationS.toDouble()), "${averageSpeedKmh.roundToInt()} km/h de moyenne",
    ascentM?.let { "D+ ${it.roundToInt()} m" }, energyModelWh?.let { "≈ ${it.roundToInt()} Wh estimés" },
).joinToString(" · ")

/** Éditeur d'une fiche : toutes les valeurs sont modifiables ; un champ vide = inconnu (jamais une valeur inventée). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScooterEditor(p: ElectricScooterProfile, onSave: (ElectricScooterProfile) -> Unit, onDelete: () -> Unit) {
    val c = LocalRoadlyColors.current
    var name by remember(p) { mutableStateOf(p.name) }
    var brand by remember(p) { mutableStateOf(p.brand.orEmpty()) }
    var model by remember(p) { mutableStateOf(p.model.orEmpty()) }
    var maxSpeed by remember(p) { mutableStateOf(p.maxSpeedKmh.field()) }
    var cruise by remember(p) { mutableStateOf(p.cruiseSpeedKmh.field()) }
    var range by remember(p) { mutableStateOf(p.claimedRangeKm.field()) }
    var battery by remember(p) { mutableStateOf(p.batteryWh.field()) }
    var voltage by remember(p) { mutableStateOf(p.batteryVoltage.field()) }
    var scooterKg by remember(p) { mutableStateOf(p.scooterWeightKg.field()) }
    var riderKg by remember(p) { mutableStateOf(p.riderWeightKg.field()) }
    var wheel by remember(p) { mutableStateOf(p.wheelSizeInch.field()) }
    var grade by remember(p) { mutableStateOf(p.maxGradePct.field()) }
    var tire by remember(p) { mutableStateOf(p.tireType) }
    var suspension by remember(p) { mutableStateOf(p.suspension) }
    var comfort by remember(p) { mutableStateOf(p.comfort) }
    var regen by remember(p) { mutableStateOf(p.hasRegenerativeBraking) }
    val maxSpeedValue = maxSpeed.optDouble()

    SettingsField("Nom", name, { name = it }, KeyboardType.Text)
    SettingsField("Marque", brand, { brand = it }, KeyboardType.Text)
    SettingsField("Modèle", model, { model = it }, KeyboardType.Text)
    SettingsField("Vitesse maximale autorisée (km/h)", maxSpeed, { maxSpeed = it }, KeyboardType.Decimal)
    if ((maxSpeedValue ?: 0.0) > 25.0) Text("En France, un EDPM est limité à 25 km/h par construction : le routage utilise 25 km/h au plus.", style = MaterialTheme.typography.bodySmall, color = c.warning)
    SettingsField("Vitesse de croisière (km/h, facultatif)", cruise, { cruise = it }, KeyboardType.Decimal)
    SettingsField("Autonomie annoncée (km)", range, { range = it }, KeyboardType.Decimal)
    p.claimedRangeConditions?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textTertiary) }
    SettingsField("Capacité de batterie (Wh)", battery, { battery = it }, KeyboardType.Decimal)
    SettingsField("Tension (V)", voltage, { voltage = it }, KeyboardType.Decimal)
    SettingsField("Poids de la trottinette (kg)", scooterKg, { scooterKg = it }, KeyboardType.Decimal)
    SettingsField("Poids du conducteur (kg, facultatif)", riderKg, { riderKg = it }, KeyboardType.Decimal)
    SettingsField("Taille des roues (pouces)", wheel, { wheel = it }, KeyboardType.Decimal)
    SettingsField("Pente maximale annoncée (%)", grade, { grade = it }, KeyboardType.Decimal)
    FieldCaption("Pneus")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        TireType.entries.forEach { GlassChip(it.label(), onClick = { tire = it }, selected = tire == it, compact = true) }
    }
    FieldCaption("Suspension")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        SuspensionType.entries.forEach { GlassChip(it.label(), onClick = { suspension = it }, selected = suspension == it, compact = true) }
    }
    FieldCaption("Confort général")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        ComfortLevel.entries.forEach { GlassChip(it.label(), onClick = { comfort = it }, selected = comfort == it, compact = true) }
    }
    FieldCaption("Freinage récupératif")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        listOf(true to "Oui", false to "Non", null to "Inconnu").forEach { (v, l) -> GlassChip(l, onClick = { regen = v }, selected = regen == v, compact = true) }
    }
    p.specSource?.let { Text("Source des caractéristiques : $it", style = MaterialTheme.typography.bodySmall, color = c.textTertiary) }
    Text("Les valeurs commerciales ne sont pas utilisées comme autonomie réelle : l'estimation repose sur un modèle physique calibré par vos trajets.", style = MaterialTheme.typography.bodySmall, color = c.textTertiary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        GlassButton(
            "Enregistrer",
            onClick = {
                onSave(p.copy(
                    name = name.trim().ifBlank { p.name }, brand = brand.trim().ifBlank { null }, model = model.trim().ifBlank { null },
                    maxSpeedKmh = maxSpeedValue ?: p.maxSpeedKmh, cruiseSpeedKmh = cruise.optDouble(), claimedRangeKm = range.optDouble(),
                    batteryWh = battery.optDouble(), batteryVoltage = voltage.optDouble(), scooterWeightKg = scooterKg.optDouble(),
                    riderWeightKg = riderKg.optDouble(), wheelSizeInch = wheel.optDouble(), maxGradePct = grade.optDouble(),
                    tireType = tire, suspension = suspension, comfort = comfort, hasRegenerativeBraking = regen,
                ))
            },
            enabled = maxSpeedValue != null && name.isNotBlank(),
        )
        GlassButton("Supprimer", onClick = onDelete, style = GlassButtonStyle.Danger)
    }
}
