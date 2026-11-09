package fr.roadly.feature.navigation

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.HorizonItem
import fr.roadly.core.designsystem.component.HorizonKind
import fr.roadly.core.designsystem.component.HorizonStrip
import fr.roadly.core.designsystem.component.SpeedBadge
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendu PNG des éléments du guidage (sans la carte native). Inactif sans -PscreenshotsDir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w430dp-h932dp-xhdpi")
class NavigationScreenshots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = System.getProperty("roadly.screenshots.dir")

    @Before fun enabled() = assumeTrue(dir != null)

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { RoadlyTheme(darkTheme = true, drivingMode = true) { Box(Modifier.fillMaxSize().background(Color(0xFFE9E5DF))) { content() } } }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(android.graphics.Canvas(bmp)) }
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun guidance() = shot("navigation_guidance") {
        val banner = BannerUi(ManeuverType.ROUNDABOUT_ENTER, "600 m", "Au rond-point, prenez la 2e sortie.", "Rd-pt du Pr Jean-Jacques Laffont", null, 2)
        val state = NavigationUiState(
            phase = NavigationPhase.GUIDING, banner = banner, etaClock = "14:03", remainingDuration = "18 min", remainingDistance = "5,3 km",
            remainingDurationSplit = SplitValue("18", "min"), remainingDistanceSplit = SplitValue("5,3", "km"), speedKmh = 0.0, speedLimitKmh = 50,
        )
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.TopCenter).padding(12.dp).padding(top = 36.dp)) {
                ManeuverBanner(banner, Modifier.fillMaxWidth(), roundabout = previewRoundabout(180.0, 2, "Rd-pt du Pr Jean-Jacques Laffont"))
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    SpeedBadge(0.0, 50)
                    Spacer(Modifier.width(10.dp))
                    HorizonStrip(
                        listOf(HorizonItem(HorizonKind.CLOSURE, 420.0, "dans 25 s", "fermeture"), HorizonItem(HorizonKind.MANEUVER, 600.0, "600 m", "rond-point")),
                        title = "Horizon", modifier = Modifier.weight(1f),
                    )
                }
                NavigationBottomPanel(state, expanded = false, destinationName = "6 Impasse Miguel Villabona", {}, {}, {}, {}, {}, {})
            }
        }
    }
}
