package fr.roadly.feature.route

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RouteHeadline
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.SegmentedControl
import fr.roadly.core.designsystem.component.WayCategoryBadge
import fr.roadly.core.designsystem.component.BatteryDialog
import fr.roadly.core.designsystem.component.WayLegend
import fr.roadly.core.designsystem.component.WayShareBar
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.map.OfflineDownloadState
import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.ScooterOptionInfo
import fr.roadly.core.scooter.analysis.WarningSeverity
import fr.roadly.core.scooter.range.RangeRisk
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

private fun goalLabel(g: ScooterRouteGoal) = when (g) {
    ScooterRouteGoal.FASTEST -> "Rapide"
    ScooterRouteGoal.SAFEST -> "Sécurisé"
    ScooterRouteGoal.COMFORT -> "Confort"
    ScooterRouteGoal.SHORTEST -> "Court"
}

private fun pct(v: Double) = "${(v * 100).roundToInt()} %"
private fun meters(v: Double?) = v?.let { "${it.roundToInt()} m" } ?: "—"

/** Panneau « Votre choix » en mode trottinette : préférences, batterie, comparaison des variantes, détail, démarrage. */
@Composable
internal fun ScooterChoicePanel(
    s: RouteUiState.Content,
    offline: OfflineDownloadState,
    onSelect: (String) -> Unit,
    onPreferences: (ScooterRoutePreferences) -> Unit,
    onBattery: (Int?) -> Unit,
    onDownload: () -> Unit,
    onAssistant: () -> Unit,
    onStart: () -> Unit,
) {
    val c = LocalRoadlyColors.current
    val sc = s.scooter ?: return
    val active = s.options.firstOrNull { it.route.id == s.activeId } ?: s.options.first()
    val info = active.scooter
    var batteryDialog by remember { mutableStateOf(false) }
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Column(Modifier.padding(RoadlySpacing.m), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            OverlineText("Trottinette · ${sc.profileName}")
            RouteHeadline(
                duration = Formatters.duration(active.route.durationSeconds),
                arrival = Formatters.clockTime(active.arrivalEpochMs, ZoneId.systemDefault()), arrivalLabel = "Arrivée",
                distance = Formatters.distance(active.route.distanceMeters), distanceLabel = "Distance",
                chips = {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                        val b = sc.batteryPct
                        GlassChip(
                            text = if (b == null) "Batterie : à saisir" else "Batterie $b %" + (sc.batteryAgeMin?.takeIf { it >= 30 }?.let { " (il y a ${it / 60} h ${it % 60} min)" } ?: ""),
                            onClick = { batteryDialog = true }, icon = Icons.Default.BatteryStd, compact = true, selected = b != null,
                        )
                        sc.temperatureC?.let { GlassChip(String.format(Locale.FRANCE, "%.0f °C", it), onClick = {}, icon = Icons.Default.Thermostat, compact = true, enabled = false) }
                        GlassChip("Roadly AI", onClick = onAssistant, icon = Icons.Default.Mic, compact = true)
                    }
                },
            )
            // Objectif + préférences : propres au mode trottinette, jamais partagées avec la voiture / moto.
            SegmentedControl(ScooterRouteGoal.entries, sc.preferences.goal, { onPreferences(sc.preferences.copy(goal = it)) }, ::goalLabel)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                val p = sc.preferences
                GlassChip("Pistes cyclables", onClick = { onPreferences(p.copy(preferCycleways = !p.preferCycleways)) }, icon = Icons.Default.DirectionsBike, compact = true, selected = p.preferCycleways)
                GlassChip("Éviter grands axes", onClick = { onPreferences(p.copy(avoidBusyRoads = !p.avoidBusyRoads)) }, icon = Icons.Default.DirectionsCar, compact = true, selected = p.avoidBusyRoads)
                GlassChip("Éviter non revêtu", onClick = { onPreferences(p.copy(avoidUnpaved = !p.avoidUnpaved)) }, compact = true, selected = p.avoidUnpaved)
                GlassChip("Éviter fortes pentes", onClick = { onPreferences(p.copy(avoidSteepHills = !p.avoidSteepHills)) }, icon = Icons.Default.Landscape, compact = true, selected = p.avoidSteepHills)
                GlassChip("Éviter pavés", onClick = { onPreferences(p.copy(avoidRoughSurfaces = !p.avoidRoughSurfaces)) }, compact = true, selected = p.avoidRoughSurfaces)
            }
            RowDivider(inset = 0.dp)
            s.options.forEach { o -> ScooterOptionRow(o, selected = o.route.id == s.activeId, onClick = { onSelect(o.route.id) }) }
            RowDivider(inset = 0.dp)
            info?.let { ScooterDetails(it) }
            WayLegend()
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
                GlassChip(
                    text = when (offline) {
                        OfflineDownloadState.Idle -> "Carte hors connexion"
                        is OfflineDownloadState.Downloading -> if (offline.required > 0) "Téléchargement ${(offline.completed * 100 / offline.required)} %" else "Téléchargement…"
                        is OfflineDownloadState.Done -> "Carte enregistrée (${offline.bytes / 1_000_000} Mo)"
                        is OfflineDownloadState.Failed -> "Échec : ${offline.reason}"
                    },
                    onClick = onDownload, icon = Icons.Default.Download, compact = true, enabled = offline !is OfflineDownloadState.Downloading,
                )
            }
            Text("Hors connexion : seule la carte est disponible ; le calcul et le recalcul d'itinéraire demandent une connexion.", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            GlassButton("Rouler", onClick = onStart, style = GlassButtonStyle.Brand, trailingIcon = Icons.AutoMirrored.Filled.ArrowForward, modifier = Modifier.fillMaxWidth())
        }
    }
    if (batteryDialog) BatteryDialog(sc.batteryPct, onDismiss = { batteryDialog = false }, onSave = { onBattery(it); batteryDialog = false })
}

/** Une variante : durée, distance, part de pistes, dénivelé, portions partagées, alertes, batterie à l'arrivée, badges. */
@Composable
private fun ScooterOptionRow(o: RouteOption, selected: Boolean, onClick: () -> Unit) {
    val c = LocalRoadlyColors.current
    val info = o.scooter ?: return
    val a = info.analysis
    GlassPanel(Modifier.fillMaxWidth(), onClick = onClick, elevated = selected, contentPadding = RoadlySpacing.sm) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Formatters.duration(o.route.durationSeconds)} · ${Formatters.distance(o.route.distanceMeters)}", style = MaterialTheme.typography.titleSmall, color = if (selected) c.brandBright else c.textPrimary)
                Spacer(Modifier.width(RoadlySpacing.s))
                Text(o.label + (info.badges.takeIf { it.isNotEmpty() }?.joinToString(prefix = " · ") ?: ""), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            if (a != null) {
                WayShareBar(a.summary.lengthByCategory)
                Text(
                    "Pistes / bandes / voies vertes ${pct(a.summary.cycleInfrastructureShare)} · D+ ${meters(a.summary.ascentM)} · D− ${meters(a.summary.descentM)} · " +
                        "${a.summary.sharedRoadPortions} portion(s) partagée(s)",
                    style = MaterialTheme.typography.labelSmall, color = c.textSecondary,
                )
                val alerts = a.warnings.count { it.severity != WarningSeverity.INFO }
                if (alerts > 0 || a.hasBlockingPortion) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = if (a.hasBlockingPortion) c.danger else c.warning, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            buildString {
                                append("$alerts passage(s) délicat(s)")
                                if (a.summary.forbiddenLengthM > 0) append(" · ${meters(a.summary.forbiddenLengthM)} interdits d'après les données")
                                if (a.summary.notRidableLengthM > 0) append(" · ${meters(a.summary.notRidableLengthM)} à pied")
                            },
                            style = MaterialTheme.typography.labelSmall, color = if (a.hasBlockingPortion) c.danger else c.warning,
                        )
                    }
                }
                if (info.reroutedAroundForbidden) Text("Recalculé pour contourner des portions interdites", style = MaterialTheme.typography.labelSmall, color = c.success)
            } else {
                Text("Analyse des voies indisponible : types de voie, accès et revêtement non vérifiés", style = MaterialTheme.typography.labelSmall, color = c.warning)
            }
            RangeLine(info)
        }
    }
}

@Composable
private fun RangeLine(info: ScooterOptionInfo) {
    val c = LocalRoadlyColors.current
    val r = info.range
    val arrival = r.arrivalBatteryPct
    val (text, color) = when {
        r.risk == RangeRisk.UNKNOWN || arrival == null -> "Autonomie : saisissez le niveau de batterie" to c.textTertiary
        r.risk == RangeRisk.INSUFFICIENT -> "Batterie insuffisante pour ce trajet (estimation)" to c.danger
        r.risk == RangeRisk.TIGHT -> "≈ ${arrival.roundToInt().coerceAtLeast(0)} % à l'arrivée · juste" to c.warning
        else -> "≈ ${arrival.roundToInt()} % à l'arrivée" to c.success
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (r.risk == RangeRisk.INSUFFICIENT) Icons.Default.BatteryAlert else Icons.Default.BatteryStd, null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text + if (!r.calibration.calibrated && arrival != null) " · non calibrée" else "", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Détail de la variante active : alertes localisées, couverture des données, réglementation, hypothèses. */
@Composable
private fun ScooterDetails(info: ScooterOptionInfo) {
    val c = LocalRoadlyColors.current
    val a = info.analysis
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (a != null) {
            a.warnings.filter { it.severity != WarningSeverity.INFO }.take(5).forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(w.kind.icon(), null, tint = if (w.severity == WarningSeverity.DANGER) c.danger else c.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${Formatters.distance(w.startAlongM)} · ${w.kind.label} — ${w.detail}", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
            a.segments.firstOrNull()?.let { first ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WayCategoryBadge(first.category, size = 18)
                    Spacer(Modifier.width(6.dp))
                    Text("Départ sur : ${first.category.label}", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
            val cov = a.coverage
            Text(
                "Données connues : tags OSM ${pct(cov.osmTags)}, revêtement OSM ${pct(cov.surfaceFromOsm)}, pente ${pct(cov.grade)}, " +
                    "limitation ${pct(cov.speedLimit)}, agglomération ${pct(cov.area)}, éclairage ${pct(cov.lighting)}.",
                style = MaterialTheme.typography.labelSmall, color = c.textTertiary,
            )
            Text("${a.regulationName} : « autorisé » signifie conforme aux données disponibles, pas une garantie légale. Respectez la signalisation sur place.", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            if (!a.osmTagsAvailable) Text("Tags OSM non chargés : agglomération, éclairage et restrictions locales inconnus.", style = MaterialTheme.typography.labelSmall, color = c.warning)
        }
        if (info.range.assumptions.isNotEmpty()) Text("Autonomie : " + info.range.assumptions.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
    }
}

