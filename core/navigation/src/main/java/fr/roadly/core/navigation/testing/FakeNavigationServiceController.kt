package fr.roadly.core.navigation.testing

import fr.roadly.core.navigation.NavigationServiceController

/** Double de test de [NavigationServiceController] : compte les démarrages demandés. */
class FakeNavigationServiceController : NavigationServiceController {
    var starts = 0
        private set
    override fun start() { starts++ }
}
