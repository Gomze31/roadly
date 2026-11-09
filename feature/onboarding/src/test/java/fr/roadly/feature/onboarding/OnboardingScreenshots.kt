package fr.roadly.feature.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleType
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendus PNG de chaque étape (comparaison visuelle avec les captures de référence). Inactifs sans -PscreenshotsDir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w372dp-h832dp-xhdpi")
class OnboardingScreenshots {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val dir = System.getProperty("roadly.screenshots.dir")

    @Before fun enabled() = assumeTrue(dir != null)

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { RoadlyTheme(darkTheme = true) { Box(Modifier.fillMaxSize().background(Color(0xFFB9B4AE))) { content() } } }
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(android.graphics.Canvas(bmp)) }
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val steps = OnboardingFlow.steps(true)
    private val settings = OnboardingSettings(route = RoutePreferences(avoidFerries = true), permission = LocationPermissionState.GRANTED)

    private fun step(s: OnboardingStep, primary: Int) = shot("onboarding_${s.name.lowercase()}") {
        OnboardingSheet(steps, s, primary, {}, {}) {
            OnboardingStepContent(it, settings, false, OnboardingActions(), VehicleDraft(VehicleType.SEDAN, FuelType.E10, "6,2"), {}, {})
        }
    }

    @Test fun welcome() = step(OnboardingStep.WELCOME, R.string.onboarding_continue)
    @Test fun location() = step(OnboardingStep.LOCATION, R.string.onboarding_continue)
    @Test fun notifications() = step(OnboardingStep.NOTIFICATIONS, R.string.onboarding_allow)
    @Test fun vehicle() = step(OnboardingStep.VEHICLE, R.string.onboarding_continue)
    @Test fun alerts() = step(OnboardingStep.ALERTS, R.string.onboarding_continue)
    @Test fun route() = step(OnboardingStep.ROUTE, R.string.onboarding_continue)
    @Test fun thanks() = step(OnboardingStep.THANKS, R.string.onboarding_done)
}
