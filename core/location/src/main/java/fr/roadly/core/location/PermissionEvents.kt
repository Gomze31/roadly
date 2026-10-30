package fr.roadly.core.location

import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Canal d'événements entre l'activité (qui possède le lanceur de permission système) et les
 * écrans : l'activité émet sur [changed] au retour du dialogue de permission, et les
 * ViewModels concernés relisent alors l'état de la permission.
 */
@Singleton
class PermissionEvents @Inject constructor() {
    val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
