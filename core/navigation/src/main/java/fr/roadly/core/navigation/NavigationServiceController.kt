package fr.roadly.core.navigation

/** Démarre le service de premier plan du guidage ; il s'arrête seul quand la session quitte `Guiding`. */
interface NavigationServiceController {
    fun start()
}
