package fr.roadly.core.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import fr.roadly.core.designsystem.component.GlassSearchBar
import fr.roadly.core.designsystem.theme.RoadlyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GlassSearchBarTest {
    @get:Rule val rule = createComposeRule()

    @Test fun typing_updates_value() {
        var value = ""
        rule.setContent {
            RoadlyTheme(darkTheme = true) {
                GlassSearchBar(value = value, onValueChange = { value = it }, placeholder = "Où allez-vous ?")
            }
        }
        rule.onNodeWithTag("search_input").performTextInput("Paris")
        assertEquals("Paris", value)
    }
}
