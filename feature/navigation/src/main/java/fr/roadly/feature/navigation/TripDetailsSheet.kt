package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType

/** D5 : feuille « Trajet complet » : toutes les manœuvres du tracé, les passées estompées, la prochaine en marque, ouverte sur la prochaine. */
@Composable
fun TripDetailsSheet(state: NavigationUiState, destinationName: String?, onDismiss: () -> Unit) {
    val route = state.route ?: return
    val next = state.progress?.nextManeuverIndex ?: 0
    val steps = remember(route.id, next) { tripSteps(route, next) }
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            OverlineText(stringResource(R.string.nav_trip_details))
            Spacer(Modifier.height(4.dp))
            Text(destinationName ?: stringResource(R.string.nav_trip_title_fallback), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.nav_trip_summary, state.remainingDistance, state.remainingDuration, state.etaClock), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(RoadlySpacing.m))
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = next.coerceIn(0, (steps.size - 1).coerceAtLeast(0)))
            // Paysage : la feuille borne la colonne ; la liste prend la place restante (`weight`, sans remplir) et « Fermer » reste visible dessous.
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp), state = listState) {
                items(steps, key = { it.index }) { step -> TripStepRow(step) }
            }
            Spacer(Modifier.height(RoadlySpacing.sm))
            GlassButton(stringResource(R.string.nav_close), onClick = onDismiss, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Une étape : icône (prochaine = marque pleine, passée = neutre), instruction, rue et longueur Valhalla de l'étape. */
@Composable
internal fun TripStepRow(step: TripStepUi) {
    val c = LocalRoadlyColors.current
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(
            step.type.icon(), size = 36.dp,
            style = when { step.isNext -> IconTileStyle.BrandFilled; step.passed -> IconTileStyle.Neutral; else -> IconTileStyle.Brand },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.instruction, style = MaterialTheme.typography.bodyLarge, color = if (step.passed) c.textTertiary else c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val length = step.lengthText?.let { stringResource(R.string.nav_trip_step_length, it) }
            val detail = listOfNotNull(step.street, length).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val sampleSteps = listOf(
    TripStepUi(0, ManeuverType.DEPART, "Conduisez vers l'est.", null, "80 m", passed = true, isNext = false),
    TripStepUi(1, ManeuverType.TURN_LEFT, "Tournez à gauche dans Rue Louis Plana.", "Rue Louis Plana", "1,2 km", passed = false, isNext = true),
    TripStepUi(2, ManeuverType.ARRIVE, "Vous êtes arrivé.", null, null, passed = false, isNext = false),
)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF1F1829, widthDp = 420)
@Composable
private fun TripStepsNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { Column { sampleSteps.forEach { TripStepRow(it) } } } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 420)
@Composable
private fun TripStepsDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { Column { sampleSteps.forEach { TripStepRow(it) } } } }
