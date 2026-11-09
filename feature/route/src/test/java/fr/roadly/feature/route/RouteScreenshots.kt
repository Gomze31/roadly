package fr.roadly.feature.route

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.CostEstimate
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.routing.RouteOption
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendu PNG de l'écran d'itinéraires (sans la carte native, remplacée par un aplat clair). Inactif sans -PscreenshotsDir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w430dp-h932dp-xhdpi")
class RouteScreenshots {
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

    private fun option(id: String, min: Int, km: Double, toll: Boolean = false, cost: Double? = null) = RouteOption(
        Route(id, RouteCosting.FASTEST, km * 1000, min * 60.0, listOf(GeoPoint(43.6, 1.44), GeoPoint(43.61, 1.45)), emptyList(), toll, "fake"),
        id, System.currentTimeMillis() + min * 60_000L, cost?.let { CostEstimate(it, null, it, it / km) }, false,
    )

    private fun zfe(v: fr.roadly.core.data.zfe.ZfeVerdict) = fr.roadly.core.data.zfe.ZfeCrossing(
        fr.roadly.core.data.zfe.ZfeRule(
            "243100518-ZFE-003", "Toulouse Métropole", null, null, emptyMap(), emptyMap(), null, null,
            listOf(fr.roadly.core.data.zfe.ZfePolygon(listOf(GeoPoint(43.59, 1.43), GeoPoint(43.59, 1.46), GeoPoint(43.61, 1.46)))),
        ),
        v, "V4", "24/7",
    )

    @Test fun choice() = shot("route_choice") {
        val s = RouteUiState.Content(
            GeoPoint(43.6, 1.44), "6 Impasse Miguel Villabona", listOf(option("a", 17, 5.3, cost = 0.62), option("b", 19, 16.0, toll = true), option("c", 21, 6.8), option("d", 19, 6.6)),
            activeId = "a", hasVehicleProfile = true, departAtEpochMs = System.currentTimeMillis(), dangerZones = mapOf("c" to 1),
            zfe = mapOf("a" to listOf(zfe(fr.roadly.core.data.zfe.ZfeVerdict.FORBIDDEN)), "b" to listOf(zfe(fr.roadly.core.data.zfe.ZfeVerdict.ALLOWED))),
        )
        val c = LocalRoadlyColors.current
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.TopCenter).padding(12.dp).padding(top = 36.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RouteTopBar("6 Impasse Miguel Villabona", favorite = false, {}, {}, {})
                    RouteFromBar("Ma position", onSwap = {})
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    SideRoundButton(Icons.Default.LocalGasStation, "", c.accent, {})
                    Spacer(Modifier.weight(1f))
                    SideRoundButton(Icons.Default.KeyboardArrowDown, "", c.brand, {})
                }
            }
            ChooseLabel({}, Modifier.align(Alignment.Center).padding(bottom = 240.dp))
            Column(Modifier.align(Alignment.BottomCenter).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RouteChoicePanel(s, false, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {})
                RollButton({})
            }
        }
    }
}
