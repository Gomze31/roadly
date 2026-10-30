package fr.roadly.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

/** Barre de recherche verre. En [readOnly] (accueil) tout le composant est cliquable et déclenche [onClick]. */
@Composable
fun GlassSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onClick: () -> Unit = {},
    onClear: () -> Unit = { onValueChange("") },
    onSearch: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoadlyShapes.Panel,
        contentPadding = 0.dp,
        onClick = if (readOnly) onClick else null,
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(RoadlySpacing.m))
            Icon(Icons.Default.Search, contentDescription = null, tint = c.textSecondary)
            Spacer(Modifier.width(RoadlySpacing.s))
            if (readOnly) {
                Text(
                    if (value.isEmpty()) placeholder else value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isEmpty()) c.textSecondary else c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.weight(1f).testTag("search_input")
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                    decorationBox = { inner ->
                        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
                        inner()
                    },
                )
                if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(48.dp).clickable(onClick = onClear).testTag("search_clear"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer", tint = c.textSecondary)
                    }
                }
            }
            Spacer(Modifier.width(RoadlySpacing.m))
        }
    }
}

@Composable
private fun GlassSearchBarSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        GlassSearchBar(value = "", onValueChange = {}, placeholder = "Où allez-vous ?", readOnly = true)
        GlassSearchBar(value = "Paris", onValueChange = {}, placeholder = "Où allez-vous ?")
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun GlassSearchBarNightPreview() {
    RoadlyTheme(darkTheme = true) { GlassSearchBarSamples() }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF4F6F8)
@Composable
private fun GlassSearchBarDayPreview() {
    RoadlyTheme(darkTheme = false) { GlassSearchBarSamples() }
}
