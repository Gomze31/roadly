package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.Overline
import fr.roadly.core.designsystem.theme.RoadlyTheme

/** Étiquette « Bientôt » : fonction annoncée mais sans source réelle (jamais un bouton actif). */
@Composable
fun ComingSoonTag(modifier: Modifier = Modifier) = Tag("Bientôt", LocalRoadlyColors.current.accent, modifier)

/** Étiquette « + » : fonction réservée au palier premium. */
@Composable
fun PremiumTag(modifier: Modifier = Modifier) = Tag("Roadly+", LocalRoadlyColors.current.brandBright, modifier)

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Petit titre en capitales espacées avec un tiret de couleur (« — VOTRE CHOIX »). */
@Composable
fun OverlineText(text: String, modifier: Modifier = Modifier, color: Color = LocalRoadlyColors.current.accent, dash: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (dash) {
            Box(Modifier.width(14.dp).height(2.dp).background(color, RoundedCornerShape(1.dp)))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = Overline, color = color, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Point d'état coloré (service en ligne, zone active, utilisateur connecté). */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
}

@Composable
private fun TagsSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ComingSoonTag(); PremiumTag(); OverlineText("Votre choix"); StatusDot(LocalRoadlyColors.current.success)
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun TagsNightPreview() { RoadlyTheme(darkTheme = true) { TagsSamples() } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun TagsDayPreview() { RoadlyTheme(darkTheme = false) { TagsSamples() } }
