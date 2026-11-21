package fr.roadly.feature.car

import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le bandeau de l'accueil (recherche + plan / photos aériennes) doit être accepté par les deux gabarits d'accueil Android Auto. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarHomeStripTest {
    @Test fun `home strip has search and the aerial toggle, accepted by both home templates`() {
        var toggled = 0
        for (aerial in listOf(false, true)) {
            val strip = CarActions.homeStrip(aerial, onSearch = {}, onToggleAerial = { toggled++ })
            assertEquals(2, strip.actions.size)
            // Les constructeurs valident les contraintes de l'hôte (nombre et type d'actions) : une erreur lèverait ici.
            PlaceListNavigationTemplate.Builder().setTitle("Roadly").setItemList(ItemList.Builder().build()).setActionStrip(strip).build()
            MapWithContentTemplate.Builder()
                .setContentTemplate(ListTemplate.Builder().setHeader(Header.Builder().setTitle("Roadly").build()).setSingleList(ItemList.Builder().build()).build())
                .setMapController(MapController.Builder().build())
                .setActionStrip(strip).build()
            strip.actions[1].onClickDelegate!!.sendClick(object : androidx.car.app.OnDoneCallback {})
        }
        assertEquals(2, toggled)
    }
}
