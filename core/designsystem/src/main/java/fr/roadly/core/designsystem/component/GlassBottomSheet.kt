package fr.roadly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalRoadlyColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceElevated,
        contentColor = c.textPrimary,
        shape = RoadlyShapes.Sheet,
        scrimColor = Color.Black.copy(alpha = if (c.isDark) 0.5f else 0.3f),
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).width(36.dp).height(4.dp).background(c.textTertiary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))) },
        content = content,
    )
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun GlassBottomSheetNightPreview() {
    RoadlyTheme(darkTheme = true) {
        GlassBottomSheet(onDismiss = {}) { Text("Contenu du panneau", color = LocalRoadlyColors.current.textPrimary) }
    }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun GlassBottomSheetDayPreview() {
    RoadlyTheme(darkTheme = false) {
        GlassBottomSheet(onDismiss = {}) { Text("Contenu du panneau", color = LocalRoadlyColors.current.textPrimary) }
    }
}
