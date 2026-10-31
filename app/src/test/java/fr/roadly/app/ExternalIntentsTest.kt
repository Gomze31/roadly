package fr.roadly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalIntentsTest {
    private fun view(uri: String) = ExternalIntents.parse(ExternalIntents.ACTION_VIEW, uri)

    @Test fun `shortcut actions map to home, work and search`() {
        assertEquals(ExternalRequest.Home, ExternalIntents.parse(ExternalIntents.ACTION_HOME, null))
        assertEquals(ExternalRequest.Work, ExternalIntents.parse(ExternalIntents.ACTION_WORK, null))
        assertEquals(ExternalRequest.Search(null), ExternalIntents.parse(ExternalIntents.ACTION_SEARCH, null))
        assertNull(ExternalIntents.parse("android.intent.action.MAIN", null))
    }

    @Test fun `geo with coordinates becomes a destination, label from q`() {
        assertEquals(ExternalRequest.Destination(43.6045, 1.444, null), view("geo:43.6045,1.444"))
        assertEquals(ExternalRequest.Destination(43.6045, 1.444, "Capitole"), view("geo:43.6045,1.444?q=Capitole"))
        assertEquals(ExternalRequest.Destination(48.85, 2.35, "Paris"), view("geo:0,0?q=48.85,2.35(Paris)"))
    }

    @Test fun `geo and navigation queries without coordinates open a prefilled search`() {
        assertEquals(ExternalRequest.Search("12 rue de la Paix, Paris"), view("geo:0,0?q=12%20rue%20de%20la%20Paix%2C%20Paris"))
        assertEquals(ExternalRequest.Search("Gare Matabiau"), view("google.navigation:q=Gare+Matabiau".replace("+", "%20")))
        assertEquals(ExternalRequest.Destination(43.61, 1.45, null), view("google.navigation:q=43.61,1.45"))
    }

    @Test fun `unknown schemes and empty data are ignored`() {
        assertNull(view("https://example.com"))
        assertNull(view("geo:0,0"))
        assertNull(ExternalIntents.parse(ExternalIntents.ACTION_VIEW, null))
    }
}
