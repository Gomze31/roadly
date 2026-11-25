package fr.roadly.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallContactsTest {
    @Test fun `a number keeps its digits, a leading plus, star and hash`() {
        assertEquals("+33612345678", CallContacts.cleanNumber(" +33 6 12-34.56 78 "))
        assertEquals("0612345678", CallContacts.cleanNumber("06 12 34 56 78"))
        assertEquals("*123#", CallContacts.cleanNumber("*123#"))
        assertEquals("33612", CallContacts.cleanNumber("33+612"))
    }

    @Test fun `a number without three digits is rejected`() {
        assertNull(CallContacts.cleanNumber("abc"))
        assertNull(CallContacts.cleanNumber("+1"))
        assertNull(CallContacts.sanitize(CallContact("Camille", "appelle-moi")))
    }

    @Test fun `adding moves an existing number to the top and caps the list`() {
        var list = emptyList<CallContact>()
        for (i in 1..5) list = CallContacts.add(list, CallContact("Proche $i", "06000000$i$i"))
        assertEquals(CallContacts.MAX, list.size)
        assertEquals("Proche 5", list.first().name)
        assertTrue("le plus ancien est sorti", list.none { it.name == "Proche 1" })
        val again = CallContacts.add(list, CallContact("Proche 3 bis", "06 00 00 00 33"))
        assertEquals(CallContacts.MAX, again.size)
        assertEquals("Proche 3 bis", again.first().name)
        assertEquals(1, again.count { it.number == "0600000033" })
    }

    @Test fun `an unusable contact leaves the list unchanged, an empty name falls back to the number`() {
        val list = listOf(CallContact("Camille", "0612345678"))
        assertEquals(list, CallContacts.add(list, CallContact("Sans numéro", "")))
        assertEquals("0611111111", CallContacts.add(emptyList(), CallContact("  ", "06 11 11 11 11")).single().name)
    }

    @Test fun `the list survives encoding, and a damaged value decodes to nothing`() {
        val list = listOf(CallContact("Camille « la route »", "+33612345678"), CallContact("Maison", "0561000000"))
        assertEquals(list, CallContacts.decode(CallContacts.encode(list)))
        assertEquals(emptyList<CallContact>(), CallContacts.decode(null))
        assertEquals(emptyList<CallContact>(), CallContacts.decode("{pas du json"))
    }
}
