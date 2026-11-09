package fr.roadly.feature.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.roadly.core.common.Formatters
import fr.roadly.core.designsystem.component.BatteryDialog
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.LoadingRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.StatTile
import fr.roadly.core.designsystem.component.WayCategoryBadge
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.Trip
import fr.roadly.core.scooter.analysis.WarningSeverity
import fr.roadly.core.scooter.osm.PoiCompatibility
import fr.roadly.core.scooter.osm.ScooterPoiKind
import fr.roadly.core.scooter.range.RangeRisk
import fr.roadly.core.scooter.regulation.LegalStatus
import fr.roadly.core.scooter.guidance.ScooterNavigationUiState
import kotlin.math.roundToInt

/**
 * Bandeau trottinette sous la manœuvre : voie empruntée (pictogramme + couleur du tracé), voie après la prochaine
 * intersection, prochaine piste, alertes devant soi et autonomie estimée (touchez pour saisir la batterie).
 */
@Composable
internal fun ScooterGuidancePanel(s: ScooterNavigationUiState, onBatteryClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.fillMaxWidth(), contentPadding = RoadlySpacing.sm) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val cat = s.currentCategory
                if (cat != null) {
                    WayCategoryBadge(cat, size = 30)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        val legal = s.currentLegal
                        val details = listOfNotNull(
                            s.currentSpeedLimitKmh?.let { "$it km/h" },
                            s.currentGradePct?.takeIf { kotlin.math.abs(it) >= 3 }?.let { "pente ${it.roundToInt()} %" },
                            legal?.takeIf { it.status != LegalStatus.ALLOWED }?.status?.label,
                        )
                        if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = if (legal?.status == LegalStatus.FORBIDDEN) c.danger else c.textSecondary)
                    }
                } else {
                    Text(if (s.analysisAvailable) "Type de voie inconnu" else "Analyse des voies indisponible", style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                }
                s.nextManeuverInto?.takeIf { it != s.currentCategory }?.let { into ->
                    // Pictogramme de changement de voie : la voie prise à la prochaine intersection.
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    WayCategoryBadge(into, size = 26)
                }
                Spacer(Modifier.width(8.dp))
                BatteryPill(s, onBatteryClick)
            }
            s.nextCycleInfrastructure?.takeIf { it.distanceM > 5 }?.let {
                Text("${it.category.label} dans ${Formatters.distance(it.distanceM)} (${Formatters.distance(it.lengthM)})", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            s.upcomingWarnings.take(2).forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(w.kind.icon(), null, tint = if (w.severity == WarningSeverity.DANGER) c.danger else c.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${w.kind.label} · ${if (w.distanceM < 5) "maintenant" else Formatters.distance(w.distanceM)}", style = MaterialTheme.typography.labelSmall, color = c.textPrimary)
                }
            }
        }
    }
}

@Composable
private fun BatteryPill(s: ScooterNavigationUiState, onClick: () -> Unit) {
    val c = LocalRoadlyColors.current
    val r = s.range
    GlassPanel(contentPadding = 8.dp, shape = RoadlyShapes.Chip, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = when (r?.risk) {
                RangeRisk.INSUFFICIENT -> c.danger
                RangeRisk.TIGHT -> c.warning
                RangeRisk.OK -> c.success
                else -> c.textTertiary
            }
            Icon(if (r?.risk == RangeRisk.INSUFFICIENT) Icons.Default.BatteryAlert else Icons.Default.BatteryStd, "Batterie", tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (r == null) "—" else "≈${r.batteryNowPct.roundToInt()} % → ${r.arrivalPct.roundToInt().coerceAtLeast(0)} %",
                style = MaterialTheme.typography.labelMedium, color = color,
            )
        }
    }
}

/** Récapitulatif après un trajet trottinette ; la batterie lue à l'arrivée sert à calibrer l'autonomie. */
@Composable
internal fun ScooterRecapPanel(trip: Trip?, onArrivalBattery: (Int?) -> Unit, onFinish: () -> Unit) {
    val c = LocalRoadlyColors.current
    var dialog by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Int?>(null) }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
            OverlineText("Trajet terminé")
            if (trip == null) {
                Text("Trajet trop court pour être enregistré (moins de 200 m).", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.m)) {
                    StatTile(Formatters.distance(trip.distanceM), "Distance")
                    StatTile(Formatters.duration(trip.durationS.toDouble()), "Durée")
                    StatTile("${trip.averageSpeedKmh.roundToInt()} km/h", "Moyenne")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.m)) {
                    StatTile(trip.ascentM?.let { "${it.roundToInt()} m" } ?: "—", "Dénivelé +")
                    StatTile(trip.energyModelWh?.let { "≈${it.roundToInt()} Wh" } ?: "—", "Énergie estimée")
                    StatTile("${trip.maxSpeedKmh.roundToInt()} km/h", "Max (GPS)")
                }
                Text(
                    "Énergie estimée par le modèle (non mesurée). Indiquez la batterie affichée par la trottinette pour calibrer les prochaines estimations.",
                    style = MaterialTheme.typography.labelSmall, color = c.textTertiary,
                )
                GlassButton(saved?.let { "Batterie à l'arrivée : $it %" } ?: "Saisir la batterie à l'arrivée", onClick = { dialog = true }, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
            }
            GlassButton("Terminer", onClick = onFinish, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
        }
    }
    if (dialog) BatteryDialog(saved ?: trip?.batteryStartPct, onDismiss = { dialog = false }, onSave = { saved = it; onArrivalBattery(it); dialog = false })
}

/** Parkings près de l'arrivée ou bornes autour de soi, tels que renseignés dans OpenStreetMap. */
@Composable
internal fun ScooterPoiSheet(state: PoisUi, onSelect: (PoiUi) -> Unit, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        val kind = when (state) { is PoisUi.Loading -> state.kind; is PoisUi.Content -> state.kind; is PoisUi.Error -> state.kind; PoisUi.Hidden -> return@GlassBottomSheet }
        Text(if (kind == ScooterPoiKind.PARKING) "Stationnement trottinettes près de l'arrivée" else "Recharge à proximité", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Spacer(Modifier.size(RoadlySpacing.s))
        when (state) {
            is PoisUi.Loading -> LoadingRow("Recherche dans OpenStreetMap…")
            is PoisUi.Error -> Text(state.error.userMessage(), style = MaterialTheme.typography.bodySmall, color = c.danger)
            is PoisUi.Content -> if (state.items.isEmpty()) {
                Text("Aucun lieu renseigné dans OpenStreetMap à proximité. Les données peuvent être incomplètes.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            } else state.items.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { onSelect(p) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (kind == ScooterPoiKind.PARKING) Icons.Default.LocalParking else Icons.Default.EvStation, null, tint = c.brandBright)
                    Spacer(Modifier.width(RoadlySpacing.s))
                    Column(Modifier.weight(1f)) {
                        Text(p.poi.name ?: if (kind == ScooterPoiKind.PARKING) "Stationnement" else "Borne de recharge", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        Text(p.poi.compatibility.label, style = MaterialTheme.typography.labelSmall, color = if (p.poi.compatibility == PoiCompatibility.EXPLICIT) c.success else c.warning)
                    }
                    Text(Formatters.distance(p.distanceM), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
            PoisUi.Hidden -> Unit
        }
        Text("Source : OpenStreetMap (Overpass API). Disponibilité et compatibilité non garanties.", style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.padding(vertical = RoadlySpacing.s))
    }
}

/** Réponse de l'assistant vocal. */
@Composable
internal fun AssistantReplyPill(text: String, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.fillMaxWidth(), contentPadding = RoadlySpacing.sm) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
    }
}
