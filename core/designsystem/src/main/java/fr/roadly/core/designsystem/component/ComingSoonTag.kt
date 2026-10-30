package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyTheme

@Composable
fun ComingSoonTag(modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Text(
        "Bientôt",
        style = MaterialTheme.typography.labelSmall,
        color = c.accent,
        modifier = modifier.background(c.accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun ComingSoonTagNightPreview() {
    RoadlyTheme(darkTheme = true) { ComingSoonTag() }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun ComingSoonTagDayPreview() {
    RoadlyTheme(darkTheme = false) { ComingSoonTag() }
}
