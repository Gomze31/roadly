package fr.roadly.core.navigation

import javax.inject.Inject

/** Horloge injectable : temps virtuel en test, `System.currentTimeMillis()` en production. */
fun interface NavigationClock {
    fun nowMs(): Long
}

/** Horloge de production. */
class SystemNavigationClock @Inject constructor() : NavigationClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
