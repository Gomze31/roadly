package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

/** Barre de titre des écrans pleine page : retour, surtitre en capitales, titre, actions à droite. */
@Composable
fun RoadlyTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    overline: String? = null,
    backDescription: String = "Retour",
    actions: (@Composable () -> Unit)? = null,
) {
    val c = LocalRoadlyColors.current
    Row(modifier.fillMaxWidth().padding(horizontal = RoadlySpacing.m, vertical = RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, backDescription, onClick = onBack)
            Spacer(Modifier.width(RoadlySpacing.sm))
        }
        Column(Modifier.weight(1f)) {
            if (overline != null) OverlineText(overline, color = c.accent)
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (actions != null) { Spacer(Modifier.width(RoadlySpacing.s)); actions() }
    }
}

/** Fond d'écran pleine page (hors carte) : couleur de fond + marge de barre d'état. */
@Composable
fun RoadlyScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding()) { content() }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 380)
@Composable
private fun TopBarNightPreview() {
    RoadlyTheme(darkTheme = true) { RoadlyTopBar("Place du Châtelet", onBack = {}, overline = "Roadly Replay") }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 380)
@Composable
private fun TopBarDayPreview() {
    RoadlyTheme(darkTheme = false) { RoadlyTopBar("Réglages", onBack = {}) }
}
