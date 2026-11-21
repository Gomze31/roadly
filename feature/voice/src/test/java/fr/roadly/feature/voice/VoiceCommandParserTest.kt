package fr.roadly.feature.voice

import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.model.ReportKind
import fr.roadly.feature.voice.VoiceIntent.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    private fun p(s: String) = VoiceCommandParser.parse(s)

    @Test fun `going home and to work`() {
        listOf("Rentrer à la maison", "rentre à la maison", "Emmène-moi à la maison", "Ramène-moi chez moi", "maison", "On va chez moi").forEach {
            assertEquals(it, VoiceIntent.Navigate(Target.Home), p(it))
        }
        listOf("Emmène-moi au travail", "Va au boulot", "au bureau", "je vais au travail").forEach {
            assertEquals(it, VoiceIntent.Navigate(Target.Work), p(it))
        }
    }

    @Test fun `free destinations are kept for geocoding`() {
        assertEquals(VoiceIntent.Navigate(Target.Query("capitole de toulouse")), p("Emmène-moi au Capitole de Toulouse"))
        assertEquals(VoiceIntent.Navigate(Target.Query("gare matabiau")), p("Navigue vers la gare Matabiau"))
        assertEquals(VoiceIntent.Navigate(Target.Query("12 rue de la paix paris")), p("Je voudrais aller au 12 rue de la Paix, Paris"))
        assertEquals(VoiceIntent.Navigate(Target.Query("bordeaux")), p("Itinéraire pour Bordeaux"))
    }

    @Test fun `avoid options switch on and off`() {
        assertEquals(VoiceIntent.Avoid(AvoidOption.TOLLS, true), p("Évite les péages"))
        assertEquals(VoiceIntent.Avoid(AvoidOption.TOLLS, true), p("itinéraire sans péage"))
        assertEquals(VoiceIntent.Avoid(AvoidOption.HIGHWAYS, true), p("évite l'autoroute"))
        assertEquals(VoiceIntent.Avoid(AvoidOption.TUNNELS, true), p("pas de tunnels"))
        assertEquals(VoiceIntent.Avoid(AvoidOption.FERRIES, true), p("évite les ferries"))
        assertEquals(VoiceIntent.Avoid(AvoidOption.TOLLS, false), p("autorise les péages"))
        assertEquals(VoiceIntent.Avoid(AvoidOption.HIGHWAYS, false), p("n'évite plus les autoroutes"))
    }

    @Test fun `reports map to community report kinds`() {
        assertEquals(VoiceIntent.Report(ReportKind.ACCIDENT), p("Signale un accident"))
        assertEquals(VoiceIntent.Report(ReportKind.JAM), p("il y a un bouchon"))
        assertEquals(VoiceIntent.Report(ReportKind.ROADWORK), p("signaler des travaux"))
        assertEquals(VoiceIntent.Report(ReportKind.CLOSED), p("signale une route fermée"))
        assertEquals(VoiceIntent.Report(ReportKind.POTHOLE), p("attention nid de poule"))
        assertEquals(VoiceIntent.Report(ReportKind.OBSTACLE), p("il y a un objet sur la route"))
        assertEquals(VoiceIntent.Report(ReportKind.WEATHER), p("signale du verglas"))
    }

    @Test fun `trip questions`() {
        assertEquals(VoiceIntent.ArrivalTime, p("À quelle heure j'arrive ?"))
        assertEquals(VoiceIntent.ArrivalTime, p("Quand est-ce qu'on arrive"))
        assertEquals(VoiceIntent.Remaining, p("Combien de kilomètres il reste ?"))
        assertEquals(VoiceIntent.Remaining, p("c'est encore loin ?"))
        assertEquals(VoiceIntent.NextInstruction, p("C'est quoi la prochaine direction"))
        assertEquals(VoiceIntent.NextInstruction, p("je tourne où ?"))
        assertEquals(VoiceIntent.SpeedLimit, p("Quelle est la limitation ?"))
        assertEquals(VoiceIntent.Recalculate, p("Trouve un autre itinéraire"))
    }

    @Test fun `nearby places`() {
        assertEquals(VoiceIntent.Nearby(PoiCategory.FUEL), p("Trouve une station essence"))
        assertEquals(VoiceIntent.Nearby(PoiCategory.FUEL), p("Je dois faire le plein, trouve une station"))
        assertEquals(VoiceIntent.Nearby(PoiCategory.PARKING), p("Où est-ce que je peux me garer, trouve un parking"))
        assertEquals(VoiceIntent.Nearby(PoiCategory.CHARGING), p("cherche une borne de recharge"))
        assertEquals(VoiceIntent.Nearby(PoiCategory.REST_AREA), p("trouve une aire de repos"))
        assertEquals(VoiceIntent.Nearby(PoiCategory.RESTAURANT), p("je veux manger, trouve un restaurant"))
    }

    @Test fun `voice, stop and help`() {
        assertEquals(VoiceIntent.Mute(true), p("coupe la voix"))
        assertEquals(VoiceIntent.Mute(true), p("silence"))
        assertEquals(VoiceIntent.Mute(false), p("remets la voix"))
        assertEquals(VoiceIntent.StopGuidance, p("Arrête le guidage"))
        assertEquals(VoiceIntent.StopGuidance, p("stop"))
        assertEquals(VoiceIntent.Help, p("Qu'est-ce que je peux dire ?"))
    }

    @Test fun `anything else is reported as not understood`() {
        assertTrue(p("raconte-moi une blague") is VoiceIntent.Unknown)
        assertTrue(p("") is VoiceIntent.Unknown)
    }
}
