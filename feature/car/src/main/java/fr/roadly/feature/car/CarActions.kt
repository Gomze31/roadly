package fr.roadly.feature.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.ui.graphics.vector.ImageVector

/** Capacités de l'hôte pour le niveau d'API voiture négocié (connu dès la création de la session). */
internal fun CarContext.capabilities(): CarCapabilities = CarTemplates.level(carAppApiLevel)

/** En-tête d'un `MessageTemplate` : `Header` à partir du niveau 7, titre + action d'en-tête en dessous. */
internal fun MessageTemplate.Builder.withHeader(carContext: CarContext, title: String, action: Action): MessageTemplate.Builder =
    if (carContext.capabilities().messageHeader) {
        setHeader(Header.Builder().setTitle(title).setStartHeaderAction(action).build())
    } else {
        @Suppress("DEPRECATION")
        setTitle(title).setHeaderAction(action)
    }

internal object CarActions {
    /** Action avec icône seule (bandeau de carte, bandeau d'actions). */
    fun icon(vector: ImageVector, onClick: () -> Unit): Action =
        Action.Builder().setIcon(CarIcons.of(vector, tint = CarColor.DEFAULT)).setOnClickListener(onClick).build()

    fun text(carContext: CarContext, titleRes: Int, onClick: () -> Unit): Action =
        Action.Builder().setTitle(carContext.getString(titleRes)).setOnClickListener(onClick).build()

    /** Bandeau de carte : défilement (mode panoramique de l'hôte), zoom avant / arrière, recentrage sur le véhicule. */
    fun mapStrip(renderer: CarMapRenderer): ActionStrip = ActionStrip.Builder()
        .addAction(Action.PAN)
        .addAction(icon(Icons.Default.Add) { renderer.zoom(1.0) })
        .addAction(icon(Icons.Default.Remove) { renderer.zoom(-1.0) })
        .addAction(icon(Icons.Default.MyLocation) { renderer.recenter() })
        .build()
}
