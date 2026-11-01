package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.TravelMode
import fr.roadly.core.scooter.analysis.ScooterWarningKind
import fr.roadly.core.scooter.map.RouteDisplayGroup
import fr.roadly.core.scooter.segment.WayCategory

/** Couleur Roadly d'un type de voie (identique au tracé sur la carte). */
fun WayCategory.color(): Color = RouteDisplayGroup.of(this).color()
fun RouteDisplayGroup.color(): Color = Color(android.graphics.Color.parseColor(colorHex))

/** Pictogramme d'un type de voie. */
fun WayCategory.icon(): ImageVector = when (this) {
    WayCategory.CYCLE_TRACK -> Icons.Default.DirectionsBike
    WayCategory.CYCLE_LANE -> Icons.Default.PedalBike
    WayCategory.GREENWAY -> Icons.Default.Park
    WayCategory.LIVING_STREET, WayCategory.PEDESTRIAN_ZONE, WayCategory.SIDEWALK -> Icons.Default.Groups
    WayCategory.SHARED_ROAD -> Icons.Default.DirectionsCar
    WayCategory.ALLOWED_PATH, WayCategory.PATH -> Icons.Default.Hiking
    WayCategory.CROSSING -> Icons.Default.Traffic
    WayCategory.STEPS -> Icons.Default.Stairs
    WayCategory.FERRY -> Icons.Default.DirectionsBoat
    WayCategory.CONSTRUCTION -> Icons.Default.Construction
    WayCategory.UNKNOWN -> Icons.Default.QuestionMark
}

fun ScooterWarningKind.icon(): ImageVector = when (this) {
    ScooterWarningKind.STEEP_CLIMB, ScooterWarningKind.STEEP_DESCENT, ScooterWarningKind.GRADE_ABOVE_CAPACITY -> Icons.Default.Landscape
    ScooterWarningKind.ROADWORKS -> Icons.Default.Construction
    ScooterWarningKind.SHARED_ROAD, ScooterWarningKind.FAST_TRAFFIC -> Icons.Default.DirectionsCar
    ScooterWarningKind.COMPLEX_INTERSECTION -> Icons.Default.Traffic
    ScooterWarningKind.NOT_RIDABLE -> Icons.Default.Stairs
    ScooterWarningKind.PEDESTRIAN_ZONE -> Icons.Default.Groups
    else -> Icons.Default.Warning
}

fun TravelMode.icon(): ImageVector = when (this) {
    TravelMode.CAR -> Icons.Default.DirectionsCar
    TravelMode.MOTORCYCLE -> Icons.Default.TwoWheeler
    TravelMode.ELECTRIC_SCOOTER -> Icons.Default.ElectricScooter
    TravelMode.BICYCLE -> Icons.Default.PedalBike
}

fun TravelMode.label(): String = when (this) {
    TravelMode.CAR -> "Voiture"
    TravelMode.MOTORCYCLE -> "Moto"
    TravelMode.ELECTRIC_SCOOTER -> "Trottinette"
    TravelMode.BICYCLE -> "Vélo"
}

/** Sélecteur de mode de déplacement ; le vélo est prévu par l'architecture mais pas encore proposé (« Bientôt »). */
@Composable
fun TravelModeSelector(selected: TravelMode, onSelect: (TravelMode) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        TravelMode.entries.forEach { m ->
            GlassChip(
                text = m.label(), onClick = { if (m.available) onSelect(m) }, icon = m.icon(), selected = m == selected,
                enabled = m.available, compact = true, trailing = if (!m.available) ({ ComingSoonTag() }) else null,
            )
        }
    }
}

/** Pastille ronde colorée + pictogramme d'un type de voie (bandeau, comparaison, légende). */
@Composable
fun WayCategoryBadge(category: WayCategory, modifier: Modifier = Modifier, size: Int = 28) {
    Box(modifier.size(size.dp).clip(CircleShape).background(category.color()), contentAlignment = Alignment.Center) {
        Icon(category.icon(), contentDescription = category.label, tint = Color(0xFF0E0B14), modifier = Modifier.size((size * 0.6f).dp))
    }
}

/** Barre proportionnelle des types de voie d'un itinéraire (part de chaque groupe de couleur). */
@Composable
fun WayShareBar(lengthByCategory: Map<WayCategory, Double>, modifier: Modifier = Modifier) {
    val byGroup = lengthByCategory.entries.groupBy({ RouteDisplayGroup.of(it.key) }, { it.value }).mapValues { it.value.sum() }
    val total = byGroup.values.sum().takeIf { it > 0 } ?: return
    Row(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        RouteDisplayGroup.entries.forEach { g ->
            val len = byGroup[g] ?: return@forEach
            Box(Modifier.weight((len / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(g.color()))
        }
    }
}

/** Légende des couleurs du tracé trottinette. */
@Composable
fun WayLegend(modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RouteDisplayGroup.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.m)) {
                row.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(g.color()))
                        Spacer(Modifier.width(6.dp))
                        Text(g.label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    }
                }
            }
        }
    }
}

/** Saisie manuelle du niveau de batterie (Roadly n'est pas connecté à la trottinette). */
@Composable
fun BatteryDialog(current: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    val c = LocalRoadlyColors.current
    var value by remember { mutableFloatStateOf((current ?: 80).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        title = { Text("Niveau de batterie", color = c.textPrimary) },
        text = {
            Column {
                Text("${value.roundToInt()} %", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..100f, steps = 19)
                Spacer(Modifier.height(4.dp))
                Text("Lisez le niveau sur l'écran de la trottinette : Roadly ne peut pas le mesurer.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        },
        confirmButton = { GlassButton("Enregistrer", onClick = { onSave(value.roundToInt()) }) },
        dismissButton = { GlassButton("Effacer", onClick = { onSave(null) }, style = GlassButtonStyle.Secondary) },
    )
}
