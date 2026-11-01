package fr.roadly.feature.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.Overline
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.designsystem.theme.softShadow
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.ManeuverType

/**
 * D3 : panneau de direction façon signalisation. Il comprend :
 * - le surtitre selon la manœuvre [type] ([signOverline]) : « Prochaine sortie » / « Sortie suivante » ([isNext]) pour une sortie,
 *   « … direction » pour une fourche ou une fusion, « … bretelle » pour une bretelle ;
 * - la distance ;
 * - la pastille de numéro ;
 * - la route (`exitBranch`, p. ex. « A62 ») ;
 * - jusqu'à 2 directions ;
 * - les voies conseillées dessous (D4).
 * Couleurs : fond `brand` plein (`brandSoft`, translucide, n'assure pas le contraste sur la carte claire) et texte `onBrandAuto`
 * (blanc, ou sombre sur les teintes claires du cockpit : 3:1 au moins) ; pastille `onBrandAuto` / texte `brand` ; route bordée `onBrandAuto`.
 */
@Composable
fun ExitSignPanel(
    type: ManeuverType,
    exitNumber: String?,
    toward: String?,
    branch: String?,
    lanes: List<Lane>,
    distanceText: String,
    isNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    val ink = c.onBrandAuto
    val towardLines = remember(toward) { signTowardLines(toward) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        Column(
            Modifier.fillMaxWidth()
                .softShadow(RoadlyShapes.Panel, c.brand, 10.dp)
                .clip(RoadlyShapes.Panel)
                .background(c.brand)
                .border(1.dp, c.glassBorder, RoadlyShapes.Panel)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(signOverline(type, isNext)).uppercase(), style = Overline, color = ink.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
                Text(distanceText, style = NavDigits.copy(fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.End), color = ink, maxLines = 1)
            }
            if (exitNumber != null || branch != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    exitNumber?.let {
                        Box(Modifier.background(ink, RoadlyShapes.Tile).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.nav_exit_number, it), style = MaterialTheme.typography.labelLarge, color = c.brand)
                        }
                    }
                    branch?.let {
                        Box(Modifier.border(1.5.dp, ink, RoadlyShapes.Tile).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = ink)
                        }
                    }
                }
            }
            towardLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LaneGuidanceView(lanes, emphasized = true)
    }
}

/**
 * D3 : directions du panneau (« A · B », séparateur posé par `ValhallaRoutingProvider`), une par ligne, 2 au plus, sans entrée vide.
 * Le séparateur garde ses espaces : un point médian dans un nom (« Paral·lel ») ne coupe pas la direction.
 */
internal fun signTowardLines(toward: String?): List<String> =
    toward?.split(" · ")?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(2).orEmpty()

/**
 * D3 : surtitre du panneau. « Sortie » seulement pour une sortie (`EXIT_*`), « Direction » pour une fourche ou une fusion
 * (`KEEP_*`, `MERGE*`), « Bretelle » pour une bretelle (`RAMP*`) ; [isNext] = prochaine manœuvre (« Prochaine … ») ou suivante (« … suivante »).
 */
@StringRes
internal fun signOverline(type: ManeuverType, isNext: Boolean): Int = when (type) {
    ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT, ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT ->
        if (isNext) R.string.nav_sign_next_direction else R.string.nav_sign_following_direction
    ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT ->
        if (isNext) R.string.nav_sign_next_ramp else R.string.nav_sign_following_ramp
    else -> if (isNext) R.string.nav_sign_next else R.string.nav_sign_following
}

private val signLanes = listOf(Lane(setOf(LaneDirection.STRAIGHT), valid = false), Lane(setOf(LaneDirection.SLIGHT_RIGHT), valid = true, active = true))

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 420)
@Composable
private fun ExitSignNightPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { ExitSignPanel(ManeuverType.EXIT_RIGHT, "31b", "Toulouse-Centre · Blagnac", "A62", signLanes, "450 m", isNext = true) }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 420)
@Composable
private fun ExitSignDayPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) { ExitSignPanel(ManeuverType.EXIT_RIGHT, "19", "Balma", null, emptyList(), "1,2 km", isNext = false) }
}
