package fr.roadly.feature.route

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.navigation.RouteIdentity

/**
 * S5 — carte d'identité de l'itinéraire choisi : part à limitation connue, temps sur routes à plus de 90, portiques de péage en flux
 * libre, zones de danger. Chaque valeur vient du profil réel du tracé ; « — » quand elle n'est pas connue.
 */
@Composable
internal fun RouteIdentityCard(identity: RouteIdentity, dangerZones: Int?, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val fast = identity.fastRoadMinutes
    val stats = listOf(
        stringResource(R.string.route_identity_percent, identity.knownLimitPercent) to stringResource(R.string.route_identity_known_limit),
        (fast?.let { stringResource(R.string.route_identity_minutes, it) } ?: "—") to stringResource(R.string.route_identity_fast_roads, RouteIdentity.FAST_ROAD_ABOVE_KMH),
        identity.tollGantries.toString() to pluralStringResource(R.plurals.route_identity_gantries, identity.tollGantries),
        (dangerZones?.toString() ?: "—") to pluralStringResource(R.plurals.route_identity_zones, dangerZones ?: 2),
    )
    val description = stats.joinToString(", ") { "${it.first} ${it.second}" }
    Column(
        modifier.fillMaxWidth().border(1.dp, c.glassBorder, RoadlyShapes.Tile)
            .padding(horizontal = RoadlySpacing.sm, vertical = RoadlySpacing.s)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(stringResource(R.string.route_identity_title), style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
        Row(Modifier.fillMaxWidth().padding(top = RoadlySpacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
            stats.forEach { (value, label) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
    }
}
