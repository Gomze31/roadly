# Guidage 3D Roadly — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** En guidage, la carte est inclinée (58°), suit le cap, zoome selon la vitesse, affiche les bâtiments en 3D ; à l'approche d'une sortie ou d'une intersection : vue serrée, flèche de manœuvre sur le tracé, panneau « SORTIE n · direction », voies mises en avant.

**Architecture:** Une politique caméra pure (`GuidanceCameraPolicy`, `core/map`) calcule zoom / pitch / cap / padding à partir de la vitesse et de la prochaine manœuvre ; `MapController` l'applique (`easeCamera`) et gère deux nouveaux groupes de calques (bâtiments `fill-extrusion`, flèche de manœuvre). `buildUiState` (feature/navigation) expose `approach` ; `NavigationScreen` ne change que son `LaunchedEffect` caméra et deux conditions d'affichage. Le modèle `Maneuver` gagne `exitToward` / `exitBranch` depuis les panneaux Valhalla.

**Tech Stack:** Kotlin, Compose, Hilt, MapLibre Android 11.13.5 (`FillExtrusionLayer`, `CameraPosition.padding`, `easeCamera`), DataStore, JUnit 4 + Turbine (tests JVM), Gradle offline.

**Spec:** `docs/superpowers/specs/2026-10-31-roadly-3d-guidance-design.md`

## Global Constraints

- Worktree `GPS-roadly-3d`, branche `feat/roadly-3d`. Ne jamais toucher au checkout principal `GPS` ni à `GPS-roadly-ai`.
- Gradle **uniquement via l'outil PowerShell** depuis le worktree : `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q` et `.\gradlew.bat :app:assembleDebug --offline -q`. Les builds dépassent 2 min : lancer en arrière-plan.
- Textes UI en français ; aucune `Color(0x…)` dans un écran (tokens `LocalRoadlyColors`).
- Aucune fausse fonctionnalité : les bâtiments ne sont ajoutés que si le style a la source `openmaptiles`.
- Ne pas restructurer les signatures de `NavigationScreen` / `PortraitOverlay` / `LandscapeOverlay` (paramètres `assistantButton` / `assistantPanel` sur `feat/roadly-ai`) ni le `combine` de `NavigationViewModel.uiState`.
- Commits : message en français, conventionnel (`feat(map): …`), terminé par `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Guidage existant (voix, recalcul, alertes, notification) inchangé ; les 230+ tests existants restent verts.

---

### Task 1 : `exitToward` / `exitBranch` dans le modèle et le parseur Valhalla

**Files:**
- Modify: `core/model/src/main/java/fr/roadly/core/model/Route.kt:16-32`
- Modify: `core/routing/src/main/java/fr/roadly/core/routing/ValhallaDtos.kt:28`
- Modify: `core/routing/src/main/java/fr/roadly/core/routing/ValhallaRoutingProvider.kt:160`
- Test: `core/routing/src/test/java/fr/roadly/core/routing/ValhallaRoutingProviderTest.kt:192-210`

**Interfaces:**
- Produces: `Maneuver.exitToward: String?`, `Maneuver.exitBranch: String?` (paramètres nommés avec défaut `null`, donc aucun appelant existant ne casse).

- [ ] **Step 1 : étendre le test existant**

Dans `ValhallaRoutingProviderTest`, test `lanes and exit number are mapped when present`, remplacer la ligne `"sign" to …` par :

```kotlin
            "sign" to json.parseToJsonElement("""{"exit_number_elements":[{"text":"12"}],"exit_branch_elements":[{"text":"A62"}],"exit_toward_elements":[{"text":"Toulouse-Centre"},{"text":"Blagnac"},{"text":"Aéroport"}]}"""),
```

et après `assertEquals("12", m.exitNumber)` ajouter :

```kotlin
        assertEquals("Toulouse-Centre · Blagnac", m.exitToward)
        assertEquals("A62", m.exitBranch)
```

- [ ] **Step 2 : vérifier l'échec**

PowerShell, dans le worktree : `.\gradlew.bat :core:routing:testDebugUnitTest --offline -q --tests "*ValhallaRoutingProviderTest*"`
Attendu : échec de compilation « Unresolved reference: exitToward ».

- [ ] **Step 3 : modèle**

Dans `Route.kt`, après `val exitNumber: String? = null,` :

```kotlin
    /** Directions signalées sur le panneau de sortie (`sign.exit_toward_elements`, 2 premières jointes par « · »), p. ex. « Toulouse-Centre · Blagnac ». */
    val exitToward: String? = null,
    /** Route signalée sur le panneau de sortie (`sign.exit_branch_elements[0]`), p. ex. « A62 ». */
    val exitBranch: String? = null,
```

- [ ] **Step 4 : DTO et mapping**

`ValhallaDtos.kt`, remplacer la ligne `ValhallaSign` :

```kotlin
@Serializable internal data class ValhallaSign(
    @SerialName("exit_number_elements") val exitNumberElements: List<ValhallaSignElement> = emptyList(),
    @SerialName("exit_branch_elements") val exitBranchElements: List<ValhallaSignElement> = emptyList(),
    @SerialName("exit_toward_elements") val exitTowardElements: List<ValhallaSignElement> = emptyList(),
)
```

`ValhallaRoutingProvider.kt`, après la ligne `exitNumber = …,` :

```kotlin
                    exitToward = m.sign?.exitTowardElements?.map { it.text.trim() }?.filter { it.isNotEmpty() }?.take(2)?.joinToString(" · ")?.takeIf { it.isNotEmpty() },
                    exitBranch = m.sign?.exitBranchElements?.firstOrNull { it.text.isNotBlank() }?.text?.trim(),
```

- [ ] **Step 5 : tests verts**

`.\gradlew.bat :core:routing:testDebugUnitTest --offline -q --tests "*ValhallaRoutingProviderTest*"` → PASS.

- [ ] **Step 6 : commit**

```bash
git add core/model core/routing
git commit -m "feat(routing): direction et route du panneau de sortie (exit_toward / exit_branch)"
```

---

### Task 2 : réglage « Carte 3D en guidage »

**Files:**
- Modify: `core/data/src/main/java/fr/roadly/core/data/repository/UserPreferencesRepository.kt` (objet `GuidanceDefaults` ligne ~27, interface ligne ~50, implémentation ligne ~113)
- Modify: `core/data/src/main/java/fr/roadly/core/data/testing/FakeUserPreferencesRepository.kt`
- Modify: `feature/settings/src/main/java/fr/roadly/feature/settings/SettingsViewModel.kt:88,133`
- Modify: `feature/settings/src/main/java/fr/roadly/feature/settings/SettingsScreen.kt:143,289-305`
- Modify: `feature/settings/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `UserPreferencesRepository.map3dEnabled: Flow<Boolean>` (défaut `GuidanceDefaults.MAP_3D = true`), `suspend fun setMap3dEnabled(value: Boolean)` ; `FakeUserPreferencesRepository.map3d: MutableStateFlow<Boolean>`.

- [ ] **Step 1 : interface, défaut, implémentation**

Dans `GuidanceDefaults` (après `RAIN_LOWERS_LIMIT`) :

```kotlin
    /** Carte inclinée + bâtiments 3D pendant le guidage. */
    const val MAP_3D = true
```

Dans l'interface, après `setRainLowersLimit` :

```kotlin
    /** Perspective 3D et bâtiments extrudés pendant le guidage. */
    val map3dEnabled: Flow<Boolean>
    suspend fun setMap3dEnabled(value: Boolean)
```

Dans l'implémentation DataStore, à côté de `kRainLimit` :

```kotlin
    private val kMap3d = booleanPreferencesKey("map.3d")
```

et après `setRainLowersLimit` :

```kotlin
    override val map3dEnabled: Flow<Boolean> = store.data.map { it[kMap3d] ?: GuidanceDefaults.MAP_3D }
    override suspend fun setMap3dEnabled(value: Boolean) { store.edit { it[kMap3d] = value } }
```

- [ ] **Step 2 : double de test**

`FakeUserPreferencesRepository`, après `setRainLowersLimit` :

```kotlin
    val map3d = MutableStateFlow(GuidanceDefaults.MAP_3D)
    override val map3dEnabled: Flow<Boolean> = map3d
    override suspend fun setMap3dEnabled(value: Boolean) { map3d.value = value }
```

- [ ] **Step 3 : ViewModel réglages**

`SettingsViewModel`, après `val vigilanceRefresh` :

```kotlin
    val map3d: StateFlow<Boolean> = prefs.map3dEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuidanceDefaults.MAP_3D)
```

(ajouter les imports `GuidanceDefaults`, `stateIn`, `SharingStarted` s'ils manquent) et après `setVigilanceSound` :

```kotlin
    fun setMap3d(value: Boolean) { viewModelScope.launch { prefs.setMap3dEnabled(value) } }
```

- [ ] **Step 4 : écran réglages**

Chaînes (`feature/settings/src/main/res/values/strings.xml`, après `settings_theme_auto`) :

```xml
    <string name="settings_map_3d">Carte 3D en guidage</string>
    <string name="settings_map_3d_subtitle">Perspective et bâtiments pendant la navigation. Désactivée automatiquement en économie d\'énergie.</string>
```

`SettingsScreen.kt` ligne 143 : collecter et passer la valeur.

```kotlin
            val map3d by vm.map3d.collectAsStateWithLifecycle()
            AppearanceGroup(state.themeMode, onSelect = vm::setTheme, map3d = map3d, onMap3d = vm::setMap3d)
```

`AppearanceGroup` : nouvelle signature et ligne de bascule **avant** la ligne « Thème » (pour que `SegmentedControl` reste en dernier).

```kotlin
@Composable
private fun AppearanceGroup(current: ThemeMode, onSelect: (ThemeMode) -> Unit, map3d: Boolean, onMap3d: (Boolean) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_appearance)) {
        ToggleRow(
            stringResource(R.string.settings_map_3d),
            checked = map3d, onCheckedChange = onMap3d,
            subtitle = stringResource(R.string.settings_map_3d_subtitle),
            icon = Icons.Default.ViewInAr,
        )
        ListRow(
```

(`Icons.Default.ViewInAr` vient de `material-icons-extended`, déjà dépendance du design system ; importer `androidx.compose.material.icons.filled.ViewInAr`.)

- [ ] **Step 5 : compiler les modules touchés**

`.\gradlew.bat :core:data:testDebugUnitTest :feature:settings:compileDebugKotlin --offline -q` → vert.

- [ ] **Step 6 : commit**

```bash
git add core/data feature/settings
git commit -m "feat(settings): réglage « Carte 3D en guidage »"
```

---

### Task 3 : token de couleur `buildings3d`

**Files:**
- Modify: `core/designsystem/src/main/java/fr/roadly/core/designsystem/theme/Color.kt` (data class ligne ~31, palettes sombre ligne ~78 et claire ligne ~102)
- Modify: `docs/DESIGN-SYSTEM.md` (tableau des tokens)

**Interfaces:**
- Produces: `RoadlyColorScheme.buildings3d: Color`.

- [ ] **Step 1 : token**

Dans `RoadlyColorScheme`, après `val routeCasing: Color,` :

```kotlin
    /** Façades des bâtiments extrudés (guidage 3D). */
    val buildings3d: Color,
```

Palette sombre (après `routeCasing = Color(0xFF0E0B14),`) : `buildings3d = Color(0xFF2A2236),`
Palette claire (après `routeCasing = Color(0xFFFFFFFF),`) : `buildings3d = Color(0xFFD9D4E3),`

- [ ] **Step 2 : doc**

Dans `docs/DESIGN-SYSTEM.md`, tableau des tokens, ajouter une ligne : `| `buildings3d` | façades des bâtiments en 3D pendant le guidage |`. Éditer avec l'outil Edit uniquement (jamais sed/perl : accents).

- [ ] **Step 3 : compiler**

`.\gradlew.bat :core:designsystem:compileDebugKotlin --offline -q` → vert (le compilateur signale toute construction manuelle de `RoadlyColorScheme` à compléter ; il n'y en a que deux dans `Color.kt`).

- [ ] **Step 4 : commit**

```bash
git add core/designsystem docs/DESIGN-SYSTEM.md
git commit -m "feat(design): token buildings3d pour les façades 3D"
```

---

### Task 4 : `GuidanceCameraPolicy` (pur, testé)

**Files:**
- Create: `core/map/src/main/java/fr/roadly/core/map/GuidanceCameraPolicy.kt`
- Test: `core/map/src/test/java/fr/roadly/core/map/GuidanceCameraPolicyTest.kt`

**Interfaces:**
- Consumes: `fr.roadly.core.model.ManeuverType`.
- Produces:

```kotlin
data class CameraPadding(val left: Float, val top: Float, val right: Float, val bottom: Float)   // fractions 0..1 de la vue
data class GuidanceCamera(val zoom: Double, val pitch: Double, val bearing: Double, val padding: CameraPadding)
data class GuidanceCameraInput(speedKmh: Double?, bearingDeg: Float?, distanceToManeuverM: Double, maneuverType: ManeuverType, threeD: Boolean, powerSave: Boolean, landscape: Boolean)
object GuidanceCameraPolicy { fun compute(input, previous: GuidanceCamera?): GuidanceCamera ; fun isApproaching(distanceM, type, speedKmh): Boolean }
```

- [ ] **Step 1 : tests**

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceCameraPolicyTest {
    private fun input(
        speed: Double? = 50.0, bearing: Float? = 90f, distance: Double = 2_000.0, type: ManeuverType = ManeuverType.TURN_RIGHT,
        threeD: Boolean = true, powerSave: Boolean = false, landscape: Boolean = false,
    ) = GuidanceCameraInput(speed, bearing, distance, type, threeD, powerSave, landscape)

    @Test fun `cruise zoom follows speed between the anchor points`() {
        assertEquals(17.5, GuidanceCameraPolicy.compute(input(speed = 10.0), null).zoom, 1e-9)
        assertEquals(17.5, GuidanceCameraPolicy.compute(input(speed = 30.0), null).zoom, 1e-9)
        assertEquals(17.0, GuidanceCameraPolicy.compute(input(speed = 50.0), null).zoom, 1e-9)
        assertEquals(16.65, GuidanceCameraPolicy.compute(input(speed = 70.0), null).zoom, 1e-9)
        assertEquals(16.3, GuidanceCameraPolicy.compute(input(speed = 90.0), null).zoom, 1e-9)
        assertEquals(15.8, GuidanceCameraPolicy.compute(input(speed = 130.0), null).zoom, 1e-9)
        assertEquals(17.0, GuidanceCameraPolicy.compute(input(speed = null), null).zoom, 1e-9)
    }

    @Test fun `3d cruise is pitched 58 and the car sits in the lower third in portrait`() {
        val cam = GuidanceCameraPolicy.compute(input(), null)
        assertEquals(58.0, cam.pitch, 1e-9)
        assertEquals(90.0, cam.bearing, 1e-9)
        assertEquals(CameraPadding(0f, 0.45f, 0f, 0f), cam.padding)
    }

    @Test fun `landscape pads the left side for the overlay column`() {
        assertEquals(CameraPadding(0.40f, 0.25f, 0f, 0f), GuidanceCameraPolicy.compute(input(landscape = true), null).padding)
    }

    @Test fun `2d keeps the legacy follow camera`() {
        val cam = GuidanceCameraPolicy.compute(input(threeD = false, speed = 120.0, distance = 50.0), null)
        assertEquals(17.0, cam.zoom, 1e-9)
        assertEquals(0.0, cam.pitch, 1e-9)
        assertEquals(CameraPadding(0f, 0f, 0f, 0f), cam.padding)
    }

    @Test fun `power save lowers the pitch to 45`() {
        assertEquals(45.0, GuidanceCameraPolicy.compute(input(powerSave = true), null).pitch, 1e-9)
    }

    @Test fun `motorway exits are approached from 400 m only at speed`() {
        assertTrue(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT, 100.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT, 50.0))
        assertTrue(GuidanceCameraPolicy.isApproaching(140.0, ManeuverType.EXIT, 50.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(410.0, ManeuverType.EXIT, 100.0))
    }

    @Test fun `turns and roundabouts are approached from 150 m, departure and arrival never`() {
        assertTrue(GuidanceCameraPolicy.isApproaching(140.0, ManeuverType.ROUNDABOUT_ENTER, 40.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(160.0, ManeuverType.TURN_LEFT, 40.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(50.0, ManeuverType.ARRIVE, 40.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(50.0, ManeuverType.DEPART, 40.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(50.0, ManeuverType.CONTINUE, 40.0))
    }

    @Test fun `approach zooms to 18 with a flatter pitch and skips smoothing`() {
        val previous = GuidanceCameraPolicy.compute(input(speed = 90.0), null)
        val cam = GuidanceCameraPolicy.compute(input(speed = 90.0, distance = 100.0), previous)
        assertEquals(18.0, cam.zoom, 1e-9)
        assertEquals(50.0, cam.pitch, 1e-9)
        assertEquals(45.0, GuidanceCameraPolicy.compute(input(distance = 100.0, powerSave = true), null).pitch, 1e-9)
    }

    @Test fun `zoom moves at most half a level per update while cruising`() {
        val previous = GuidanceCameraPolicy.compute(input(speed = 10.0), null) // 17.5
        val cam = GuidanceCameraPolicy.compute(input(speed = 130.0), previous)   // cible 15.8
        assertEquals(17.0, cam.zoom, 1e-9)
    }

    @Test fun `bearing is kept from the previous camera when stopped or unknown`() {
        val previous = GuidanceCameraPolicy.compute(input(bearing = 135f), null)
        assertEquals(135.0, GuidanceCameraPolicy.compute(input(speed = 2.0, bearing = 10f), previous).bearing, 1e-9)
        assertEquals(135.0, GuidanceCameraPolicy.compute(input(bearing = null), previous).bearing, 1e-9)
        assertEquals(0.0, GuidanceCameraPolicy.compute(input(bearing = null), null).bearing, 1e-9)
    }
}
```

- [ ] **Step 2 : vérifier l'échec**

`.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*GuidanceCameraPolicyTest*"` → échec de compilation (classes absentes).

- [ ] **Step 3 : implémentation**

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.ManeuverType

/** Marges de la caméra en fractions de la vue (0..1) : gauche, haut, droite, bas. */
data class CameraPadding(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object { val NONE = CameraPadding(0f, 0f, 0f, 0f) }
}

data class GuidanceCamera(val zoom: Double, val pitch: Double, val bearing: Double, val padding: CameraPadding)

data class GuidanceCameraInput(
    val speedKmh: Double?,
    val bearingDeg: Float?,
    val distanceToManeuverM: Double,
    val maneuverType: ManeuverType,
    /** Réglage « Carte 3D en guidage ». */
    val threeD: Boolean,
    /** Économiseur de batterie Android actif. */
    val powerSave: Boolean,
    val landscape: Boolean,
)

/**
 * Caméra de guidage (spec guidage 3D §1) : inclinaison 58° (45° en économie, 0° en 2D), zoom de croisière
 * selon la vitesse, vue serrée à l'approche d'une manœuvre, cap conservé à l'arrêt, voiture décalée vers le bas.
 */
object GuidanceCameraPolicy {
    const val PITCH_3D = 58.0
    const val PITCH_POWER_SAVE = 45.0
    const val PITCH_APPROACH = 50.0
    const val ZOOM_2D = 17.0
    const val ZOOM_APPROACH = 18.0
    const val MAX_ZOOM_STEP = 0.5
    const val STOPPED_KMH = 5.0
    const val EXIT_APPROACH_M = 400.0
    const val EXIT_APPROACH_MIN_KMH = 70.0
    const val TURN_APPROACH_M = 150.0

    val PADDING_PORTRAIT = CameraPadding(0f, 0.45f, 0f, 0f)
    val PADDING_LANDSCAPE = CameraPadding(0.40f, 0.25f, 0f, 0f)

    private val exitLike = setOf(ManeuverType.EXIT, ManeuverType.RAMP, ManeuverType.MERGE, ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT)
    private val neverApproach = setOf(ManeuverType.DEPART, ManeuverType.ARRIVE, ManeuverType.CONTINUE)

    /** (vitesse km/h, zoom) ; interpolation linéaire entre les points, bornée aux extrémités. */
    private val cruiseZoom = listOf(30.0 to 17.5, 50.0 to 17.0, 90.0 to 16.3, 110.0 to 15.8)

    fun isApproaching(distanceM: Double, type: ManeuverType, speedKmh: Double?): Boolean {
        if (type in neverApproach) return false
        if (type in exitLike && (speedKmh ?: 0.0) > EXIT_APPROACH_MIN_KMH) return distanceM < EXIT_APPROACH_M
        return distanceM < TURN_APPROACH_M
    }

    fun compute(input: GuidanceCameraInput, previous: GuidanceCamera?): GuidanceCamera {
        val bearing = when {
            input.bearingDeg == null -> previous?.bearing ?: 0.0
            (input.speedKmh ?: 0.0) < STOPPED_KMH && previous != null -> previous.bearing
            else -> input.bearingDeg.toDouble()
        }
        if (!input.threeD) return GuidanceCamera(ZOOM_2D, 0.0, bearing, CameraPadding.NONE)
        val approaching = isApproaching(input.distanceToManeuverM, input.maneuverType, input.speedKmh)
        val pitch = when {
            input.powerSave -> PITCH_POWER_SAVE
            approaching -> PITCH_APPROACH
            else -> PITCH_3D
        }
        val target = if (approaching) ZOOM_APPROACH else zoomForSpeed(input.speedKmh)
        val zoom = if (approaching || previous == null) target else previous.zoom + (target - previous.zoom).coerceIn(-MAX_ZOOM_STEP, MAX_ZOOM_STEP)
        return GuidanceCamera(zoom, pitch, bearing, if (input.landscape) PADDING_LANDSCAPE else PADDING_PORTRAIT)
    }

    internal fun zoomForSpeed(speedKmh: Double?): Double {
        val v = speedKmh ?: return ZOOM_2D
        if (v <= cruiseZoom.first().first) return cruiseZoom.first().second
        if (v >= cruiseZoom.last().first) return cruiseZoom.last().second
        val i = cruiseZoom.indexOfLast { it.first <= v }
        val (v0, z0) = cruiseZoom[i]
        val (v1, z1) = cruiseZoom[i + 1]
        return z0 + (z1 - z0) * (v - v0) / (v1 - v0)
    }
}
```

- [ ] **Step 4 : tests verts**

`.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*GuidanceCameraPolicyTest*"` → PASS.

- [ ] **Step 5 : commit**

```bash
git add core/map/src/main/java/fr/roadly/core/map/GuidanceCameraPolicy.kt core/map/src/test/java/fr/roadly/core/map/GuidanceCameraPolicyTest.kt
git commit -m "feat(map): politique de caméra de guidage 3D (zoom, inclinaison, cap, marges)"
```

---

### Task 5 : `ManeuverArrowBuilder` (GeoJSON de la flèche, testé)

**Files:**
- Create: `core/map/src/main/java/fr/roadly/core/map/ManeuverArrowBuilder.kt`
- Test: `core/map/src/test/java/fr/roadly/core/map/ManeuverArrowBuilderTest.kt`

**Interfaces:**
- Consumes: `GeoPoint.distanceTo`.
- Produces: `ManeuverArrowBuilder.build(geometry: List<GeoPoint>, maneuverShapeIndex: Int): String` — FeatureCollection avec un `LineString` (propriété `kind = "arrow"`) et un `Point` (propriétés `kind = "head"`, `bearing`) ; `ManeuverArrowBuilder.EMPTY`.

- [ ] **Step 1 : tests**

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ManeuverArrowBuilderTest {
    // Ligne plein est, pas de 50 m, 21 points (0..1000 m) ; manœuvre au point 10 (500 m).
    private val lonPerM = 1.0 / (111_320.0 * Math.cos(Math.toRadians(48.85)))
    private val line = (0..20).map { GeoPoint(48.85, 2.3 + it * 50.0 * lonPerM) }

    private fun features(json: String) = Json.parseToJsonElement(json).jsonObject["features"]!!.jsonArray

    @Test fun `arrow spans 80 m before and 40 m after the maneuver point`() {
        val f = features(ManeuverArrowBuilder.build(line, 10))
        val coords = f[0].jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        val pts = coords.map { GeoPoint(it.jsonArray[1].jsonPrimitive.double, it.jsonArray[0].jsonPrimitive.double) }
        assertEquals(80.0, pts.first().distanceTo(line[10]), 1.0)
        assertEquals(40.0, pts.last().distanceTo(line[10]), 1.0)
        val length = pts.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
        assertEquals(120.0, length, 1.0)
    }

    @Test fun `head sits at the arrow end and points along the last segment`() {
        val f = features(ManeuverArrowBuilder.build(line, 10))
        val head = f[1].jsonObject
        assertEquals("head", head["properties"]!!.jsonObject["kind"]!!.jsonPrimitive.content)
        assertEquals(90.0, head["properties"]!!.jsonObject["bearing"]!!.jsonPrimitive.double, 0.5)
        val c = head["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        assertEquals(40.0, GeoPoint(c[1].jsonPrimitive.double, c[0].jsonPrimitive.double).distanceTo(line[10]), 1.0)
    }

    @Test fun `arrow is clipped at the route ends`() {
        val f = features(ManeuverArrowBuilder.build(line, 1))   // 50 m disponibles avant
        val coords = f[0].jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
        val first = GeoPoint(coords[0].jsonArray[1].jsonPrimitive.double, coords[0].jsonArray[0].jsonPrimitive.double)
        assertEquals(0.0, first.distanceTo(line[0]), 0.1)
    }

    @Test fun `too short a geometry gives an empty collection`() {
        assertEquals(ManeuverArrowBuilder.EMPTY, ManeuverArrowBuilder.build(listOf(GeoPoint(48.85, 2.3)), 0))
        assertEquals(ManeuverArrowBuilder.EMPTY, ManeuverArrowBuilder.build(line, -1))
        assertEquals(ManeuverArrowBuilder.EMPTY, ManeuverArrowBuilder.build(line, 25))
    }
}
```

- [ ] **Step 2 : vérifier l'échec**

`.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*ManeuverArrowBuilderTest*"` → échec de compilation.

- [ ] **Step 3 : implémentation**

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flèche de manœuvre dessinée sur le tracé à l'approche d'une intersection (spec guidage 3D §3) :
 * [BEFORE_M] en amont du point de manœuvre, [AFTER_M] en aval, bornée aux extrémités du tracé.
 * GeoJSON : LineString `kind=arrow` puis Point `kind=head` avec le cap du dernier segment.
 */
object ManeuverArrowBuilder {
    const val BEFORE_M = 80.0
    const val AFTER_M = 40.0
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    fun build(geometry: List<GeoPoint>, maneuverShapeIndex: Int): String {
        if (geometry.size < 2 || maneuverShapeIndex !in geometry.indices) return EMPTY
        val before = walk(geometry, maneuverShapeIndex, -1, BEFORE_M).asReversed()
        val after = walk(geometry, maneuverShapeIndex, +1, AFTER_M)
        val pts = (before + geometry[maneuverShapeIndex] + after).distinct()
        if (pts.size < 2) return EMPTY
        val bearing = bearingDeg(pts[pts.size - 2], pts.last())
        return buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                add(buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("kind", "arrow") }
                    putJsonObject("geometry") {
                        put("type", "LineString")
                        put("coordinates", JsonArray(pts.map { p -> JsonArray(listOf(JsonPrimitive(p.lon), JsonPrimitive(p.lat))) }))
                    }
                })
                add(buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("kind", "head"); put("bearing", bearing) }
                    putJsonObject("geometry") {
                        put("type", "Point")
                        put("coordinates", JsonArray(listOf(JsonPrimitive(pts.last().lon), JsonPrimitive(pts.last().lat))))
                    }
                })
            }
        }.toString()
    }

    /** Points rencontrés en partant de [from] dans le sens [step] (±1) jusqu'à [budgetM] ; le dernier est interpolé. */
    private fun walk(geometry: List<GeoPoint>, from: Int, step: Int, budgetM: Double): List<GeoPoint> {
        val out = mutableListOf<GeoPoint>()
        var remaining = budgetM
        var i = from
        while (remaining > 0 && i + step in geometry.indices) {
            val a = geometry[i]; val b = geometry[i + step]
            val d = a.distanceTo(b)
            if (d <= remaining) { out += b; remaining -= d; i += step } else {
                val t = remaining / d
                out += GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
                remaining = 0.0
            }
        }
        return out
    }

    /** Cap initial de a vers b en degrés (0 = nord, 90 = est). */
    internal fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat); val lat2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}
```

- [ ] **Step 4 : tests verts**

`.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*ManeuverArrowBuilderTest*"` → PASS.

- [ ] **Step 5 : commit**

```bash
git add core/map/src/main/java/fr/roadly/core/map/ManeuverArrowBuilder.kt core/map/src/test/java/fr/roadly/core/map/ManeuverArrowBuilderTest.kt
git commit -m "feat(map): géométrie de la flèche de manœuvre"
```

---

### Task 6 : `MapController` — caméra 3D, bâtiments, calques de flèche

**Files:**
- Modify: `core/map/src/main/java/fr/roadly/core/map/MapController.kt`
- Modify: `core/map/src/main/java/fr/roadly/core/map/MapLayers.kt:16-34` (`MapIds`)
- Modify: `core/map/src/main/java/fr/roadly/core/map/CameraSnapshot.kt`
- Create: `core/map/src/main/res/drawable/ic_arrow_head.xml`

**Interfaces:**
- Consumes: `GuidanceCamera`, `CameraPadding`, `ManeuverArrowBuilder.EMPTY`.
- Produces: `MapController.applyGuidanceCamera(point: GeoPoint, camera: GuidanceCamera, durationMs: Int = 1000)`, `setBuildings3d(enabled: Boolean, colorHex: String)`, `setManeuverArrow(geoJson: String)`, `clearManeuverArrow()`, `setArrowColors(body: String, casing: String)` ; `CameraSnapshot.tilt`.

- [ ] **Step 1 : identifiants**

Dans `MapIds` ajouter :

```kotlin
    const val SRC_ARROW = "roadly-arrow"
    const val LYR_ARROW_CASING = "roadly-arrow-casing"
    const val LYR_ARROW = "roadly-arrow-line"
    const val LYR_ARROW_HEAD = "roadly-arrow-head"
    const val LYR_BUILDINGS_3D = "roadly-buildings-3d"
    const val IMG_ARROW_HEAD = "roadly-img-arrow-head"
    /** Source vecteur OpenMapTiles des styles OpenFreeMap ; absente = pas de bâtiments 3D. */
    const val STYLE_SRC_OPENMAPTILES = "openmaptiles"
    const val STYLE_LYR_BUILDING = "building"
```

- [ ] **Step 2 : tête de flèche**

`core/map/src/main/res/drawable/ic_arrow_head.xml` (triangle blanc 28 dp, pointe vers le haut, liseré sombre) :

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="28dp" android:height="28dp" android:viewportWidth="28" android:viewportHeight="28">
    <path android:fillColor="#0E0B14" android:pathData="M14,2 L26,24 L14,18 L2,24 Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M14,5 L23,22 L14,17.5 L5,22 Z" />
</vector>
```

- [ ] **Step 3 : `CameraSnapshot.tilt`**

```kotlin
package fr.roadly.core.map

data class CameraSnapshot(val lat: Double, val lon: Double, val zoom: Double, val bearing: Double, val tilt: Double = 0.0)
```

- [ ] **Step 4 : champs et calques dans `MapController`**

Imports à ajouter :

```kotlin
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionBase
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionHeight
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity
import org.maplibre.android.style.layers.PropertyFactory.visibility
```

Champs (après `routeColorCasing`) :

```kotlin
    private var arrowColorBody = "#FFFFFF"
    private var lastArrow: String = ManeuverArrowBuilder.EMPTY
    private var buildings3d = false
    private var buildingsColor = "#2A2236"
```

Dans `installLayers`, après `style.addImage(MapIds.IMG_REPORT, …)` :

```kotlin
        style.addImage(MapIds.IMG_ARROW_HEAD, drawableToBitmap(R.drawable.ic_arrow_head))
        style.addSource(GeoJsonSource(MapIds.SRC_ARROW))
```

Après le calque `LYR_ROUTE_TRAVERSED` et avant `LYR_MARKERS` :

```kotlin
        // Flèche de manœuvre (approche d'une intersection) : liseré, trait clair, tête orientée selon le dernier segment.
        style.addLayer(LineLayer(MapIds.LYR_ARROW_CASING, MapIds.SRC_ARROW).withProperties(
            lineColor(routeColorCasing), lineWidth(14f), lineOpacity(0.9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("kind"), literal("arrow"))))
        style.addLayer(LineLayer(MapIds.LYR_ARROW, MapIds.SRC_ARROW).withProperties(
            lineColor(arrowColorBody), lineWidth(8f), lineCap(Property.LINE_CAP_BUTT), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("kind"), literal("arrow"))))
        style.addLayer(SymbolLayer(MapIds.LYR_ARROW_HEAD, MapIds.SRC_ARROW).withProperties(
            iconImage(MapIds.IMG_ARROW_HEAD), iconRotate(get("bearing")), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAnchor(Property.ICON_ANCHOR_CENTER), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(1f),
        ).withFilter(eq(get("kind"), literal("head"))))
```

(importer `PropertyFactory.iconIgnorePlacement`.)

À la fin de `installLayers` :

```kotlin
        installBuildings(style)
```

Nouvelle méthode :

```kotlin
    /** Bâtiments extrudés (guidage 3D) sous le premier calque d'étiquettes ; seulement si le style a la source OpenMapTiles. */
    private fun installBuildings(style: Style) {
        if (style.getSource(MapIds.STYLE_SRC_OPENMAPTILES) == null) return
        val layer = FillExtrusionLayer(MapIds.LYR_BUILDINGS_3D, MapIds.STYLE_SRC_OPENMAPTILES).withSourceLayer(MapIds.STYLE_LYR_BUILDING).withProperties(
            fillExtrusionHeight(coalesce(get("render_height"), literal(0f))),
            fillExtrusionBase(coalesce(get("render_min_height"), literal(0f))),
            fillExtrusionOpacity(0.85f),
            fillExtrusionColor(buildingsColor),
            visibility(if (buildings3d) Property.VISIBLE else Property.NONE),
        )
        layer.minZoom = 15f
        val firstSymbol = style.layers.firstOrNull { it is SymbolLayer }?.id
        if (firstSymbol != null) style.addLayerBelow(layer, firstSymbol) else style.addLayer(layer)
    }
```

Dans `replayState`, avant la ligne `map?.let { m -> pendingCamera … }` :

```kotlin
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_ARROW)?.setGeoJson(lastArrow)
        (style.getLayer(MapIds.LYR_ARROW_CASING) as? LineLayer)?.setProperties(lineColor(routeColorCasing))
        (style.getLayer(MapIds.LYR_ARROW) as? LineLayer)?.setProperties(lineColor(arrowColorBody))
        (style.getLayer(MapIds.LYR_BUILDINGS_3D) as? FillExtrusionLayer)?.setProperties(
            fillExtrusionColor(buildingsColor), visibility(if (buildings3d) Property.VISIBLE else Property.NONE),
        )
```

- [ ] **Step 5 : API publique**

Après `clearTraversed()` :

```kotlin
    /** Flèche de manœuvre (GeoJSON de `ManeuverArrowBuilder`) ; vide = aucune flèche. */
    fun setManeuverArrow(geoJson: String) {
        lastArrow = geoJson
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_ARROW)?.setGeoJson(geoJson)
    }

    fun clearManeuverArrow() = setManeuverArrow(ManeuverArrowBuilder.EMPTY)

    /** Couleur CSS du trait de la flèche (le liseré reprend `casing` de [setRouteColors]). */
    fun setArrowColor(body: String) {
        arrowColorBody = body
        if (styleReady) replayState()
    }

    /** Bâtiments extrudés : visibles ou non, couleur CSS des façades. Sans effet si le style n'a pas la source OpenMapTiles. */
    fun setBuildings3d(enabled: Boolean, colorHex: String) {
        buildings3d = enabled; buildingsColor = colorHex
        if (styleReady) replayState()
    }

    /**
     * Caméra de guidage : cible, zoom, cap, inclinaison et marges (fractions de la vue → pixels) ;
     * transition linéaire de [durationMs] pour suivre le rythme des positions (~1 Hz).
     */
    fun applyGuidanceCamera(point: GeoPoint, camera: GuidanceCamera, durationMs: Int = 1000) {
        val action: (MapLibreMap) -> Unit = { m ->
            val w = m.width.toDouble(); val h = m.height.toDouble()
            val p = camera.padding
            val pos = CameraPosition.Builder()
                .target(LatLng(point.lat, point.lon)).zoom(camera.zoom).bearing(camera.bearing).tilt(camera.pitch)
                .padding(p.left * w, p.top * h, p.right * w, p.bottom * h)
                .build()
            m.easeCamera(CameraUpdateFactory.newCameraPosition(pos), durationMs, false)
        }
        val m = map
        if (m == null || !styleReady) { pendingCamera = action; return }
        action(m)
    }
```

`moveTo` : ajouter `.padding(0.0, 0.0, 0.0, 0.0)` après `.tilt(0.0)` (un retour à l'accueil efface les marges du guidage).
`liveCamera()` : passer `it.tilt` en cinquième argument. `restore(snapshot)` reste en `moveTo` (tilt 0 : l'écran de guidage réapplique sa caméra au prochain `progress`).

- [ ] **Step 6 : compiler et tests du module**

`.\gradlew.bat :core:map:testDebugUnitTest --offline -q` → vert. Si `CameraPosition.Builder.padding(Double, Double, Double, Double)` n'existe pas dans 11.13.5, utiliser `.padding(doubleArrayOf(left, top, right, bottom))`.

- [ ] **Step 7 : commit**

```bash
git add core/map
git commit -m "feat(map): caméra de guidage 3D, bâtiments extrudés et flèche de manœuvre"
```

---

### Task 7 : `rememberPowerSaveMode()`

**Files:**
- Create: `core/map/src/main/java/fr/roadly/core/map/PowerSaveMode.kt`

**Interfaces:**
- Produces: `@Composable fun rememberPowerSaveMode(): State<Boolean>`.

- [ ] **Step 1 : implémentation**

```kotlin
package fr.roadly.core.map

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Économiseur de batterie Android (réglage système), suivi en direct : la 3D est allégée quand il est actif. */
@Composable
fun rememberPowerSaveMode(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val pm = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    val state = remember { mutableStateOf(pm.isPowerSaveMode) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { state.value = pm.isPowerSaveMode }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        state.value = pm.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
```

- [ ] **Step 2 : compiler**

`.\gradlew.bat :core:map:compileDebugKotlin --offline -q` → vert.

- [ ] **Step 3 : commit**

```bash
git add core/map/src/main/java/fr/roadly/core/map/PowerSaveMode.kt
git commit -m "feat(map): suivi de l'économiseur de batterie pour la carte 3D"
```

---

### Task 8 : `ApproachUi`, `exitToward` dans le bandeau, `map3d` dans le ViewModel

**Files:**
- Modify: `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationUiState.kt:20-27,38-66,100-131`
- Modify: `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt` (après `alternativesUi`, ligne ~92)
- Test: `feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt`

**Interfaces:**
- Consumes: `GuidanceCameraPolicy.isApproaching`, `Maneuver.exitToward`, `UserPreferencesRepository.map3dEnabled`.
- Produces: `ApproachUi(type, exitNumber, exitToward, maneuverShapeIndex)`, `NavigationUiState.approach: ApproachUi?`, `BannerUi.exitToward: String?`, `NavigationViewModel.map3d: StateFlow<Boolean>`.

- [ ] **Step 1 : test**

Dans `NavigationViewModelTest`, après le test `lanes and exit number of the next maneuver reach the screen` :

```kotlin
    @Test fun `approach is flagged within 150 m of a turn and the exit direction reaches the banner`() = runTest {
        val withSign = route.copy(maneuvers = route.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(exitNumber = "12", exitToward = "Lyon · Mâcon") else m })
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option.copy(route = withSign), RoutePreferences()); runCurrent()
            location.emit(fixAt(300.0)); runCurrent()
            assertNull(vm.uiState.value.approach)
            assertEquals("Lyon · Mâcon", vm.uiState.value.banner!!.exitToward)
            location.emit(fixAt(650.0)); runCurrent()
            val approach = vm.uiState.value.approach!!
            assertEquals(ManeuverType.TURN_RIGHT, approach.type)
            assertEquals("12", approach.exitNumber)
            assertEquals("Lyon · Mâcon", approach.exitToward)
            assertEquals(10, approach.maneuverShapeIndex)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

(importer `org.junit.Assert.assertNull` si absent.) Route `TestRoutes.straight()` : manœuvre TURN_RIGHT au point 10 ≈ 732 m ; à 300 m → 432 m restants (pas d'approche), à 650 m → 82 m (approche).

- [ ] **Step 2 : vérifier l'échec**

`.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q --tests "*NavigationViewModelTest*"` → échec de compilation.

- [ ] **Step 3 : état UI**

`NavigationUiState.kt` — `BannerUi` :

```kotlin
data class BannerUi(
    val type: ManeuverType,
    val distanceText: String,
    val instruction: String,
    val street: String?,
    val exitNumber: String?,
    val roundaboutExit: Int?,
    /** Direction du panneau de sortie (« Lyon · Mâcon »), si Valhalla la fournit. */
    val exitToward: String? = null,
)
```

Après `UpcomingUi` :

```kotlin
/** Approche d'une intersection (vue serrée, flèche, panneau) ; [maneuverShapeIndex] = point du tracé où dessiner la flèche. */
data class ApproachUi(val type: ManeuverType, val exitNumber: String?, val exitToward: String?, val maneuverShapeIndex: Int)
```

Dans `NavigationUiState`, après `val upcoming` :

```kotlin
    val approach: ApproachUi? = null,
```

Dans `buildUiState` (branche `Guiding`) : import `fr.roadly.core.map.GuidanceCameraPolicy` ; remplacer la construction de `banner` par

```kotlin
                banner = BannerUi(m.type, Formatters.distance(p.distanceToNextManeuverM), m.instruction, m.streetName, m.exitNumber, m.roundaboutExit, m.exitToward),
                approach = if (GuidanceCameraPolicy.isApproaching(p.distanceToNextManeuverM, m.type, s.speedKmh)) ApproachUi(m.type, m.exitNumber, m.exitToward, m.beginShapeIndex) else null,
```

Vérifier que `feature/navigation/build.gradle.kts` dépend déjà de `:core:map` (il importe `fr.roadly.core.map.CameraMode` : oui).

- [ ] **Step 4 : ViewModel**

`NavigationViewModel`, après `val alternativesUi` :

```kotlin
    /** Réglage « Carte 3D en guidage » (l'écran y ajoute l'économiseur de batterie). */
    val map3d: StateFlow<Boolean> = prefs.map3dEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
```

- [ ] **Step 5 : tests verts**

`.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q` → PASS (tous les tests du module).

- [ ] **Step 6 : commit**

```bash
git add feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationUiState.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt feature/navigation/src/test
git commit -m "feat(navigation): état d'approche d'intersection et direction de sortie"
```

---

### Task 9 : panneau de sortie dans `ManeuverBanner`, voies mises en avant

**Files:**
- Modify: `feature/navigation/src/main/java/fr/roadly/feature/navigation/ManeuverBanner.kt:35-78`
- Modify: `feature/navigation/src/main/java/fr/roadly/feature/navigation/LaneStrip.kt:30-45`
- Modify: `feature/navigation/src/main/res/values/strings.xml:28`

**Interfaces:**
- Consumes: `BannerUi.exitToward`.
- Produces: `LaneStrip(lanes, modifier, emphasized: Boolean = false)`.

- [ ] **Step 1 : chaîne**

Remplacer `<string name="nav_exit_number">Sortie %1$s</string>` par `<string name="nav_exit_number">SORTIE %1$s</string>` et ajouter :

```xml
    <string name="nav_exit_toward">Direction %1$s</string>
```

- [ ] **Step 2 : bandeau**

Dans `ManeuverBanner`, à l'intérieur de `AnimatedVisibility(expanded) { Column { … } }`, après la ligne `banner.street?.let { … }` :

```kotlin
                        banner.exitToward?.let {
                            Text(stringResource(R.string.nav_exit_toward, it), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
```

`ExitPill` : utiliser `MaterialTheme.typography.labelLarge` et `padding(horizontal = 10.dp, vertical = 4.dp)` (panneau plus lisible).

- [ ] **Step 3 : voies**

`LaneStrip` : signature `fun LaneStrip(lanes: List<Lane>, modifier: Modifier = Modifier, emphasized: Boolean = false)` ; remplacer les tailles :

```kotlin
    val cell = if (emphasized) 40.dp else 32.dp
    val icon = if (emphasized) 34.dp else 28.dp
```

et dans la boucle `Box(Modifier.size(width = cell, height = cell + 8.dp), …)` / `Modifier.size(icon)`. Les voies non valides en `emphasized` passent à `c.textTertiary.copy(alpha = 0.6f)` (contraste accru pour les voies conseillées).

- [ ] **Step 4 : compiler**

`.\gradlew.bat :feature:navigation:compileDebugKotlin --offline -q` → vert.

- [ ] **Step 5 : commit**

```bash
git add feature/navigation
git commit -m "feat(navigation): panneau de sortie avec direction et voies mises en avant"
```

---

### Task 10 : câblage dans `NavigationScreen`

**Files:**
- Modify: `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt:98-146,174-175,236-237`

**Interfaces:**
- Consumes: `GuidanceCameraPolicy.compute`, `GuidanceCameraInput`, `GuidanceCamera`, `MapController.applyGuidanceCamera / setManeuverArrow / clearManeuverArrow / setBuildings3d / setArrowColor`, `ManeuverArrowBuilder.build`, `rememberPowerSaveMode`, `vm.map3d`, `state.approach`, `LaneStrip(emphasized)`.

- [ ] **Step 1 : imports**

```kotlin
import fr.roadly.core.map.GuidanceCamera
import fr.roadly.core.map.GuidanceCameraInput
import fr.roadly.core.map.GuidanceCameraPolicy
import fr.roadly.core.map.ManeuverArrowBuilder
import fr.roadly.core.map.rememberPowerSaveMode
```

Retirer `import fr.roadly.core.map.CameraMode` s'il n'est plus utilisé.

- [ ] **Step 2 : effet caméra**

Dans `NavigationContent`, remplacer le bloc

```kotlin
    val progress = state.progress
    val following = state.following
    LaunchedEffect(progress, following) {
        if (route != null && progress != null) {
            val fix = LocationFix(progress.snapped, progress.bearingDeg, null, 5f, 0L)
            map.setUserLocation(fix)
            map.setTraversed(route.geometry, progress.segmentIndex, progress.snapped)
            // Suivi : zoom 17, cap, inclinaison 0 (CameraMode.FOLLOW de la v1).
            if (following) map.applyCameraMode(CameraMode.FOLLOW, fix)
        }
    }
```

par

```kotlin
    val progress = state.progress
    val following = state.following
    val threeD by vm.map3d.collectAsStateWithLifecycle()
    val powerSave by rememberPowerSaveMode()
    LaunchedEffect(threeD, powerSave, c.buildings3d) { map.setBuildings3d(threeD && !powerSave, c.buildings3d.toHex()) }
    LaunchedEffect(c.onBrand) { map.setArrowColor(c.onBrand.toHex()) }
    LaunchedEffect(progress) {
        if (route != null && progress != null) {
            map.setUserLocation(LocationFix(progress.snapped, progress.bearingDeg, null, 5f, 0L))
            map.setTraversed(route.geometry, progress.segmentIndex, progress.snapped)
        }
    }
    val approach = state.approach
    LaunchedEffect(route?.id, approach?.maneuverShapeIndex) {
        if (route != null && approach != null) map.setManeuverArrow(ManeuverArrowBuilder.build(route.geometry, approach.maneuverShapeIndex)) else map.clearManeuverArrow()
    }
    // Dernière caméra calculée : le cap y est conservé à l'arrêt et le zoom lissé d'une position à l'autre.
    val lastCamera = remember { arrayOfNulls<GuidanceCamera>(1) }
```

puis, **à l'intérieur** de `BoxWithConstraints`, juste après `val landscape = maxWidth > maxHeight` :

```kotlin
        LaunchedEffect(progress, following, approach != null, threeD, powerSave, landscape) {
            if (route == null || progress == null || !following) return@LaunchedEffect
            val next = route.maneuvers.getOrNull(progress.nextManeuverIndex) ?: return@LaunchedEffect
            val cam = GuidanceCameraPolicy.compute(
                GuidanceCameraInput(state.speedKmh, progress.bearingDeg, progress.distanceToNextManeuverM, next.type, threeD, powerSave, landscape),
                lastCamera[0],
            )
            lastCamera[0] = cam
            map.applyGuidanceCamera(progress.snapped, cam)
        }
```

- [ ] **Step 3 : voies visibles en approche**

Portrait (ligne ~175) et paysage (ligne ~237), remplacer
`if (state.lanes.isNotEmpty() && bannerExpanded) LaneStrip(state.lanes)` par
`if (state.lanes.isNotEmpty() && (bannerExpanded || state.approach != null)) LaneStrip(state.lanes, emphasized = state.approach != null)`.

- [ ] **Step 4 : compiler et tests**

`.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q` → vert.

- [ ] **Step 5 : commit**

```bash
git add feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt
git commit -m "feat(navigation): caméra 3D, flèche et bâtiments branchés sur l'écran de guidage"
```

---

### Task 11 : vérification complète, appareil, suivi de parité

**Files:**
- Modify: `SILLAXE_PARITY.md` (matrice + journal)

- [ ] **Step 1 : tests complets**

PowerShell (arrière-plan) : `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q` → vert, aucun test existant modifié hormis ceux du plan.

- [ ] **Step 2 : build**

`.\gradlew.bat :app:assembleDebug --offline -q` → vert.

- [ ] **Step 3 : installation et essai sur le HONOR**

```bash
adb -s (numéro de série retiré) install -r app/build/outputs/apk/debug/app-debug.apk
```

Lancer un guidage (destination à > 2 km avec au moins un virage ; si le téléphone est immobile, utiliser une position simulée : Options développeur → application de position fictive, ou l'écran de recherche puis « Rouler » et observer la caméra au premier `progress`). Vérifier : carte inclinée orientée selon le cap, voiture dans le tiers bas (portrait) / bas-droite (paysage), bâtiments visibles en ville (zoom ≥ 15), flèche + panneau « SORTIE n » à moins de 150 m d'un virage, `LaneStrip` visible bandeau replié en approche, bouton Recentrer → retour en 3D, réglage « Carte 3D » désactivé → carte plate zoom 17, économiseur de batterie activé → bâtiments masqués.

Captures :

```bash
adb -s (numéro de série retiré) exec-out screencap -p > "C:/Users/FORZAP~1/AppData/Local/Temp/claude/C--Users-Forza-PC-Documents-Yannis-Projet-Yannis-GPS/2cf31ddc-48c6-40e0-a417-479338457359/scratchpad/nav3d-portrait.png"
adb -s (numéro de série retiré) logcat -d | grep -i FATAL
```

Aucune ligne FATAL attendue.

- [ ] **Step 4 : suivi de parité**

Dans `SILLAXE_PARITY.md` (outil Edit uniquement), ajouter dans la matrice des fonctions une ligne `| Guidage 3D (caméra inclinée, bâtiments, approche de sortie) | 3D | ✅ | Pitch 58°, zoom selon vitesse, flèche de manœuvre, panneau « SORTIE n · direction », réglage « Carte 3D », allégé en économie d'énergie |` et une entrée de journal datée 2026-10-31 décrivant la branche `feat/roadly-3d`.

- [ ] **Step 5 : commit**

```bash
git add SILLAXE_PARITY.md
git commit -m "docs(parity): guidage 3D"
```

---

## Auto-revue

- Spec §1 caméra → Tasks 4, 6, 10. §2 bâtiments → Tasks 3, 6, 10. §3 approche / flèche / panneau / voies → Tasks 5, 6, 8, 9, 10. §4 modèle → Task 1. §5 réglage + économie → Tasks 2, 7, 10. §6 flux de données → Task 10. Tests → Tasks 1, 4, 5, 8, 11. Suivi → Task 11.
- Noms croisés : `applyGuidanceCamera`, `setBuildings3d`, `setManeuverArrow`, `clearManeuverArrow`, `setArrowColor` (Task 6 ↔ 10) ; `ApproachUi.maneuverShapeIndex` (Task 8 ↔ 10) ; `LaneStrip(emphasized)` (Task 9 ↔ 10) ; `map3dEnabled` / `map3d` (Task 2 ↔ 8) ; `buildings3d` (Task 3 ↔ 10).
