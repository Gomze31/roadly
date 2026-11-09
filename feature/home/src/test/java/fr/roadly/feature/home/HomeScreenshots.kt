package fr.roadly.feature.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.SpeedBadge
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.location.LocationPermissionState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendu PNG des éléments de l'accueil, disposés comme l'écran réel (sans la carte native). Inactif sans -PscreenshotsDir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w430dp-h932dp-xhdpi")
class HomeScreenshots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = System.getProperty("roadly.screenshots.dir")

    @Before fun enabled() = assumeTrue(dir != null)

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { RoadlyTheme(darkTheme = true) { Box(Modifier.fillMaxSize().background(Color(0xFFE9E5DF))) { content() } } }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(android.graphics.Canvas(bmp)) }
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shot("home") {
        val c = LocalRoadlyColors.current
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.padding(12.dp).padding(top = 36.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeSearchPill({}, {})
                DepartureCard(null, {})
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoundIconButton(Icons.Default.LocalGasStation, "", {}, iconTint = c.accent)
                        CompassButton(35.0, {})
                    }
                    Spacer(Modifier.weight(1f))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                        RoundIconButton(Icons.Default.KeyboardArrowUp, "", {})
                        ParkingButton {}
                        RoundIconButton(Icons.Default.EmojiEvents, "", {}, iconTint = c.warning)
                        RoundIconButton(Icons.Default.Settings, "", {}, iconTint = c.brandBright)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    SpeedBadge(0.0, null)
                    Spacer(Modifier.weight(1f))
                    RoundIconButton(Icons.Default.Warning, "", {}, size = 64.dp, iconTint = c.accent)
                }
                Spacer(Modifier.padding(6.dp))
                BottomPanel(HomeUiState(permission = LocationPermissionState.GRANTED, address = "Allées Jean Jaurès, Toulouse"), isGuiding = false, onNavigateToRoute = {}, onDefine = {}, onResumeGuidance = {}, onPosition = {})
            }
        }
    }
}
