package fr.roadly.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arborescence des réglages : chaque page est atteignable une seule fois, liens directs avec retour vers le sous-menu. */
class SettingsTreeTest {
    private fun pagesOf(entries: List<SettingsEntry>): List<SettingsPage> = entries.flatMap { e ->
        when (e) {
            is SettingsEntry.Page -> listOf(e.page)
            is SettingsEntry.Menu -> pagesOf(e.menu.entries)
            is SettingsEntry.Link -> emptyList()
        }
    }

    @Test fun `every page is reachable exactly once from the home`() {
        val reachable = pagesOf(SettingsHomeGroups.flatMap { it.second })
        assertEquals(SettingsPage.entries.sorted(), reachable.sorted())
    }

    @Test fun `every dedicated screen is linked`() {
        fun links(entries: List<SettingsEntry>): List<SettingsLink> = entries.flatMap { e ->
            when (e) { is SettingsEntry.Link -> listOf(e.link); is SettingsEntry.Menu -> links(e.menu.entries); else -> emptyList() }
        }
        assertEquals(SettingsLink.entries.toSet(), links(SettingsHomeGroups.flatMap { it.second }).toSet())
    }

    @Test fun `deep links open the page above its sub-menu, unknown keys open the home`() {
        assertEquals(listOf("menu:VEHICLE", "page:VEHICLE"), SettingsStack.initial("VEHICLE"))
        assertEquals(listOf("page:UPDATES"), SettingsStack.initial("UPDATES"))
        assertEquals(listOf("menu:NAVIGATION"), SettingsStack.initial("NAVIGATION"))
        assertEquals(listOf("menu:ALERTS"), SettingsStack.initial("ALERTS"))
        assertTrue(SettingsStack.initial("NOPE").isEmpty())
        assertTrue(SettingsStack.initial(null).isEmpty())
    }

    @Test fun `stack survives its text encoding`() {
        val s = listOf("menu:ALERTS", "page:SPEED")
        assertEquals(s, SettingsStack.decode(SettingsStack.encode(s)))
        assertTrue(SettingsStack.decode("").isEmpty())
        assertEquals(SettingsPage.SPEED, SettingsStack.pageOf("page:SPEED"))
        assertEquals(SettingsMenu.ALERTS, SettingsStack.menuOf("menu:ALERTS"))
        assertEquals(null, SettingsStack.pageOf("menu:ALERTS"))
    }
}
