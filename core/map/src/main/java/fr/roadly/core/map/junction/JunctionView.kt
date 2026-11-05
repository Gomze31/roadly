package fr.roadly.core.map.junction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import fr.roadly.core.map.MapController
import fr.roadly.core.model.Route
import fr.roadly.core.model.junction.JunctionGuidance
import fr.roadly.core.model.junction.JunctionPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scène prête à afficher (maillages construits hors du thread principal) et la clé du carrefour qu'elle représente. */
class JunctionSceneState(val key: String, val routeId: String, val tileRoads: Int, val meshes: JunctionMeshes, val palette: JunctionPalette)

/**
 * Prépare la scène du carrefour [guidance] : contexte des tuiles lu sur le thread principal (une fois par carrefour, relu à
 * l'apparition si les tuiles n'étaient pas encore chargées, et quand la topologie Valhalla arrive), scène et maillages construits
 * sur `Dispatchers.Default`. Null tant qu'aucune scène n'est prête.
 */
@Composable
fun rememberJunctionScene(route: Route?, guidance: JunctionGuidance?, map: MapController, palette: JunctionPalette, detail: JunctionDetail): JunctionSceneState? {
    var state by remember { mutableStateOf<JunctionSceneState?>(null) }
    val g = rememberUpdatedState(guidance)
    val revealed = guidance != null && guidance.phase >= JunctionPhase.REVEAL
    val key = if (route != null && guidance != null) "${guidance.routeId}#${guidance.maneuverIndex}#${guidance.topology != null}" else null
    LaunchedEffect(key, revealed, palette, detail) {
        val current = g.value
        if (route == null || current == null || key == null) { state = null; return@LaunchedEffect }
        if (route.id != current.routeId) return@LaunchedEffect
        val existing = state
        // Déjà construite pour ce carrefour avec des routes de tuiles : inutile de relire au passage en REVEAL.
        if (existing != null && existing.key == key && existing.palette == palette && existing.tileRoads > 0) return@LaunchedEffect
        val center = route.geometry[current.maneuverIndexInShape.coerceIn(0, route.geometry.lastIndex)]
        val context = map.junctionContext(center, JunctionSceneBuilder.RADIUS_M + 150.0)
        val built = withContext(Dispatchers.Default) {
            JunctionSceneBuilder.build(route, current, context, detail)?.let { scene -> JunctionMeshBuilder.build(scene, palette) }
        }
        state = built?.let { JunctionSceneState(key, route.id, context.roads.size, it, palette) }
    }
    // Sorties enchaînées : l'ancienne scène (même tracé, mêmes abscisses) reste affichée le temps de construire la suivante.
    return state?.takeIf { guidance != null && it.routeId == guidance.routeId }
}

/** Vue OpenGL de la scène [scene], animée par [guidance] (position, étape, apparition). */
@Composable
fun JunctionView(scene: JunctionSceneState, guidance: JunctionGuidance, maxFps: Int, modifier: Modifier = Modifier, onUnsupported: () -> Unit = {}) {
    val unsupported = rememberUpdatedState(onUnsupported)
    val shown = remember { arrayOfNulls<JunctionSceneState>(1) }
    AndroidView(
        factory = { ctx -> JunctionGlView(ctx).also { v -> v.onUnsupported = { v.post { unsupported.value() } } } },
        modifier = modifier,
        update = { v ->
            v.maxFps = maxFps
            if (shown[0] !== scene) { shown[0] = scene; v.setScene(scene.meshes, scene.palette) }
            v.setDynamics(
                JunctionDynamics(guidance.phase, guidance.kind, guidance.complexity, guidance.vehicleAlongM, guidance.speedKmh, guidance.reveal, guidance.degraded),
            )
        },
    )
}
