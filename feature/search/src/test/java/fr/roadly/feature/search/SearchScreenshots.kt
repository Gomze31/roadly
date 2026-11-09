package fr.roadly.feature.search

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassSearchBar
import fr.roadly.core.designsystem.component.SurfaceCard
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.RoutePreferences
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendu PNG du haut de la recherche : barre, micro, options du trajet, résultats avec « Y aller ». Inactif sans -PscreenshotsDir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w372dp-h832dp-xhdpi")
class SearchScreenshots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val dir = System.getProperty("roadly.screenshots.dir")

    @Before fun enabled() = assumeTrue(dir != null)

    @Test fun search() {
        rule.setContent {
            RoadlyTheme(darkTheme = true) {
                val c = LocalRoadlyColors.current
                Column(Modifier.fillMaxSize().background(c.background).padding(top = 36.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "", {})
                        Spacer(Modifier.width(8.dp))
                        GlassSearchBar("Capitole", {}, "Où allez-vous ?", modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(Icons.Default.Mic, "", {})
                    }
                    TripOptionsBar(TripOptionsUi(RoutePreferences(avoidTolls = true, avoidTunnels = true), remember = false, departure = null, tripOnly = true), {}, {}, {})
                    Spacer(Modifier.padding(6.dp))
                    SurfaceCard(Modifier.padding(horizontal = 16.dp)) {
                        Column(verticalArrangement = Arrangement.Top) {
                            PlaceRow(Place("1", "Capitole de Toulouse", "Place du Capitole, 31000 Toulouse", GeoPoint(43.6045, 1.444), PlaceKind.POI, "x"), {}, distance = "3,2 km", onGo = {})
                            PlaceRow(Place("2", "Capitole", "Rue du Capitole, Bordeaux", GeoPoint(44.84, -0.57), PlaceKind.ADDRESS, "x"), {}, distance = "212 km", onGo = {}, divider = false)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height / 2, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(android.graphics.Canvas(bmp)) }
        File(dir!!).mkdirs()
        File(dir, "search.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
