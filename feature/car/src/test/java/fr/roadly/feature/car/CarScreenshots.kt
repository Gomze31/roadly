package fr.roadly.feature.car

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import fr.roadly.core.map.SurfaceArea
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendu PNG du HUD dessiné sur la carte d'Android Auto (écran 800 × 480, aplat clair à la place de la carte). Inactif sans -PscreenshotsDir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h480dp-mdpi")
class CarScreenshots {
    private val dir = System.getProperty("roadly.screenshots.dir")

    @Before fun enabled() = assumeTrue(dir != null)

    private fun shot(name: String, model: CarHudModel) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = CarHudView(activity)
        val w = 800; val h = 480
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        // Zone libre laissée par les gabarits de l'hôte : à droite du panneau de manœuvre (≈ 300 px).
        view.setArea(SurfaceArea(300, 0, w, h))
        view.update(model)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFFE9E5DF.toInt())
        view.draw(canvas)
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun hudWithLimitAndZone() = shot("car_hud", CarHudModel(48, 50, false, CarZoneModel(inside = false, distanceM = 650.0, limitKmh = 50, confidence = 90)))
    @Test fun hudOverLimit() = shot("car_hud_over", CarHudModel(63, 50, true, null))
    @Test fun hudLimitOnly() = shot("car_hud_limit_only", CarHudModel(null, 90, false, null))
}
