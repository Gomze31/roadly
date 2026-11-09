# Étape C — Carte 3D — plan d'implémentation

> **Pour les agents d'exécution :** SOUS-COMPÉTENCE OBLIGATOIRE : utiliser superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour exécuter ce plan tâche par tâche. Les étapes utilisent des cases à cocher (`- [ ]`) pour le suivi.

**Objectif :** donner au guidage Roadly une carte 3D fluide et lisible. La caméra est lissée, avec un cap filtré, un marqueur interpolé, des transitions 2D ↔ 3D, un mode « nord en haut » et un recentrage annulable (C1). Un style Roadly sombre et clair est embarqué, avec un niveau de détail réglable (C2). Le travail fait à chaque position est réduit, et des mesures réelles sont prises avant et après sur le HONOR (C3).

**Architecture :**
- Les règles de caméra restent en Kotlin pur dans `core/map`, testées sur JVM. `NavigationCameraController` enveloppe `GuidanceCameraPolicy` et y ajoute :
  - le filtre circulaire du cap ;
  - le mode de caméra ;
  - les durées de transition.
- Les petites fonctions pures `Bearings`, `MarkerInterpolation`, `MapDetailPolicy`, `TraversedLine` et `FpsWindow` portent chacune une règle testée. `MapController` ne fait que les appliquer à MapLibre (animateur du marqueur, calques, filtres).
- Le style Roadly est un asset JSON généré une fois par un script PowerShell déterministe, à partir des styles OpenFreeMap enregistrés dans le dépôt. Le script et l'asset sont commités, et un test JVM vérifie l'asset.
- À l'écran, les mises à jour de la carte passent par un `snapshotFlow` unique, hors recomposition des overlays. Les nouveaux états du ViewModel sont des `StateFlow` séparées : `cameraMode`, `recenterInSeconds`, `mapDetail`. Le `combine` de `uiState` n'est pas touché.

**Pile technique :** Kotlin 2.2.10, Jetpack Compose (BOM 2026.05.00), Hilt 2.58, MapLibre Android 11.13.5, styles et tuiles OpenFreeMap (schéma OpenMapTiles), DataStore Preferences 1.1.7, kotlinx.serialization (lecture du style dans les tests), JUnit 4.13.2, kotlinx-coroutines-test, Turbine, Windows PowerShell 5.1 (génération du style), adb (HONOR Magic 8 Pro).

**Spécification :** `ROADLY_3D_NAVIGATION_PLAN.md`, § « Étape C — Carte 3D » (C1 à C3 et leurs critères d'acceptation). Lire aussi :
- `ROADLY_3D_NAVIGATION_TESTS.md` : scénarios 15 et 19, ligne « Performance » des vérifications supplémentaires, journal ;
- `ROADLY_3D_NAVIGATION_AUDIT.md` : §4 (carte, styles, LIMITES), §5 (interface), §10 (performance).

## Contraintes globales

**Environnement et commandes**
- Travailler uniquement dans le worktree `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d`, branche `feat/roadly-3d`, base `c4d05ef`.
- Lancer Gradle avec l'outil PowerShell, toujours avec `--offline`. Les tâches utilisées sont :
  - `.\gradlew.bat :core:map:testDebugUnitTest --offline -q` ;
  - `:feature:navigation:testDebugUnitTest` ;
  - `:core:data:testDebugUnitTest` ;
  - `:feature:settings:testDebugUnitTest` et `:feature:settings:compileDebugKotlin` ;
  - `:feature:trips:compileDebugKotlin` ;
  - `:app:assembleDebug`.
- Pour filtrer un test : `--tests "*NomDuTest*"`.
- Les numéros de ligne cités sont ceux de `c4d05ef`. Quand une tâche précédente a déjà modifié un fichier, se repérer au bloc `ancien` cité, qui sert d'ancre.

**Langue et constantes**
- Écrire en français les KDoc, commentaires, chaînes, messages de commit et noms de tests descriptifs. La KDoc tient en une ligne, dans le style du fichier.
- Chaque seuil numérique est une constante nommée avec sa KDoc, dans l'objet de la règle concernée : `GuidanceCameraPolicy`, `NavigationCameraController`, `MarkerInterpolation`, `MapDetailPolicy`, `MapRendering`, `ReportsAheadProjector`.

**Commits**
- Chaque commit est en français et se termine par la ligne `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

**Données et tests**
- Aucune fausse fonctionnalité. Toute donnée de carte vient des tuiles OpenMapTiles (OpenFreeMap), et rien n'est dessiné sans donnée. Chaque limite est écrite « LIMITE » dans la KDoc ou dans `ROADLY_3D_NAVIGATION_TESTS.md`.
- Les 374 tests existants restent verts. Aucune assertion existante ne change. Les signatures modifiées gagnent des paramètres avec une valeur par défaut, placés en fin de liste.

**Surface de fusion avec `feat/roadly-ai`** (worktree `GPS-roadly-ai`, lecture seule)
- Les signatures de `NavigationScreen`, `NavigationContent`, `PortraitOverlay` et `LandscapeOverlay` ne changent pas, ni leurs lignes d'appel (`NavigationScreen.kt` 98, 102-103, 162-166).
- Dans les overlays, on ne fait qu'ajouter des éléments en fin de bloc.
- La structure du `combine` de `NavigationViewModel.uiState` ne change pas. Les nouveaux états sont des propriétés `StateFlow` séparées.
- Le détail des fichiers communs est donné dans la section « Surface de fusion » en fin de plan.

**Interface et appareil**
- Pas de `Color(0x…)` dans les écrans. Les couleurs CSS `#RRGGBB` du style JSON et de `MapController` ne sont pas des couleurs Compose et sont admises.
- Les chaînes d'interface sont en français, dans `strings.xml`.
- Appareil : HONOR `adb -s (numéro de série retiré)`, identifiant d'application `fr.roadly` (debug, débogable). Le fichier DataStore est `files/datastore/roadly_prefs.preferences_pb`.
- Modifier `ROADLY_3D_NAVIGATION_TESTS.md` uniquement avec l'outil Edit, en gardant les accents.

---

## Écarts constatés (vérifiés le 2026-10-31) et arbitrages

Ces points ont été vérifiés dans le code, dans le bytecode MapLibre et sur les styles OpenFreeMap réels. Ils précisent les décisions prises sans les rouvrir.

1. **PowerShell 5.1 abîme le JSON imbriqué.**
   - Constat : `ConvertTo-Json` écrit chaque tableau imbriqué sous la forme `{"value":[…],"Count":n}`. C'est le cas de tous les filtres d'OpenFreeMap.
   - Correctif : le script commence par `Remove-TypeData System.Array`. L'aller-retour `ConvertFrom-Json` → `ConvertTo-Json -Depth 50` donne alors un JSON identique (comparaison structurelle des styles `dark` et `positron` réels).
   - Note : Node existe en fait sur la machine (`C:\Program Files\nodejs\node.exe`). Il n'est pas utilisé, conformément à la décision.
2. **Aucun calque de points d'intérêt dans les styles amont.**
   - Constat : ni `dark` (47 calques) ni `positron` (55 calques) n'a de calque `poi`. Le script ajoute donc deux calques `poi` lus dans les tuiles.
   - La classe `station` n'existe pas dans OpenMapTiles : les gares sont en `class = railway`. On utilise donc `hospital`, `fuel`, `parking`, `railway`.
   - Les quatre icônes existent dans le sprite `ofm` (vérifié dans `ofm.json`).
3. **Tunnels et ponts à peine distingués en amont.**
   - Constat : `dark` ne distingue ni tunnels ni ponts. `positron` ne le fait que pour les autoroutes.
   - Choix : le script remplace les calques routiers amont par une pile Roadly de 7 calques, identique pour les deux styles (seule la palette change), au lieu de les retoucher un à un.
3 bis. **Glyphes, sprite et attribution.** Ces trois éléments sont conservés tels quels dans les deux styles amont.
4. **Le 62° de la spécification est impossible avec MapLibre Android 11.13.5.**
   - Constat : `MapLibreConstants.MAXIMUM_PITCH = 60`, et `Transform.setMaxPitch` refuse toute valeur supérieure à 60.
   - Choix : `PITCH_DENSE_CITY = 60.0`, noté en LIMITE.
5. **`setMaximumFps` appartient à `MapView`, pas à `MapLibreMap`.** Le moteur de rendu est créé dans le constructeur de `MapView` : l'appel est donc possible dès le `remember`.
6. **`ProviderConfig.normalized()` refuserait `asset://`.**
   - Constat : la fonction exige une URL http(s). Le test existant `SettingsViewModelTest.valid provider urls are normalised and saved` part de `ProviderConfig.DEFAULT` : il échouerait.
   - Choix : `asset://…` est accepté pour les deux URL de style seulement.
7. **Conflit certain sur `reportsAhead` avec `feat/roadly-ai`.**
   - Constat : l'IA déplace la projection dans `core/navigation/ReportsAhead.kt`, une fonction `reportsAhead(g, list)` utilisée aussi par ses outils.
   - Choix : C3 met le cache dans un fichier propre à `feature/navigation` (`ReportsAheadProjector.kt`). Au moment de la fusion, on garde la ligne C dans le ViewModel, et la fonction IA reste pour l'assistant.
8. **Hauteur insuffisante en paysage.**
   - Constat : la colonne d'actions du paysage (repli, Signaler, Voix, Destination, Itinéraires, puis Roadly AI) remplit déjà la hauteur. Sur le HONOR, l'écran fait environ 380 dp de haut une fois les barres retirées.
   - Choix : les deux boutons caméra vont au début de la rangée du bas à droite (compteur), sous forme de `Row`. En portrait, ils sont ajoutés en fin de colonne d'actions, eux aussi en `Row` (56 dp de haut au lieu de 2 × 56). La réorganisation complète des contrôles relève de D5.
9. **La marge du panneau inférieur arrive à l'Étape D5.**
   - Constat : mesurer le panneau demande un rappel dans les overlays, dont la signature est figée.
   - Choix : `NavigationCameraInput.bottomPanelFraction` est livré et testé, mais l'écran passe `null`. Le branchement se fera en D5 (`onGloballyPositioned`).
10. **`dumpsys gfxinfo` ne voit pas le rendu de la carte.**
    - Constat : il ne mesure que les vues de l'application (overlays Compose), pas la `SurfaceView` GL de MapLibre.
    - Choix : un journal des images par seconde de la carte (`RoadlyFps`) est ajouté, en build débogable seulement.
11. **Des URL de style déjà enregistrées masqueraient le style Roadly.** Le HONOR peut avoir enregistré les anciennes URL https dans « Fournisseurs ». La passe appareil commence donc par « Réinitialiser ».
12. **Le marqueur animé ne convient pas à la relecture de trajet.** `ReplayScreen` reçoit des positions rapprochées : il passe `animateMs = 0`. L'accueil garde l'animation (1 s par défaut).

---

## Structure des fichiers

| Fichier | Rôle | Tâches |
|---|---|---|
| `core/map/.../Bearings.kt` (nouveau) | caps : normalisation, écart le plus court, interpolation | 1 |
| `core/map/.../NavigationCameraController.kt` (nouveau) | `CameraMode3D`, filtre de cap, modes, durées, marge du panneau | 1 |
| `core/map/.../GuidanceCameraPolicy.kt` | `buildingsVisible`, `PITCH_DENSE_CITY`, `MAX_PITCH` | 1 |
| `core/map/.../MarkerInterpolation.kt` (nouveau) | pose du marqueur, interpolation, saut > 200 m | 2 |
| `core/map/.../MapController.kt` | marqueur animé, transition, inclinaison restaurée (2) ; niveau de détail (6) ; parcouru incrémental, journal FPS (7) | 2, 6, 7 |
| `core/map/.../MapLayers.kt` | `PointFeatureBuilder.user(pose)` (2), identifiants (6, 7) | 2, 6, 7 |
| `core/map/.../CameraSnapshot.kt` | KDoc de l'inclinaison | 2 |
| `feature/trips/.../ReplayScreen.kt` | marqueur sans animation | 2 |
| `feature/navigation/.../NavigationViewModel.kt` | mode caméra, compte à rebours, caméra mémorisée (3) ; `mapDetail` (6) ; projection en cache (7) | 3, 6, 7 |
| `feature/navigation/.../NavigationScreen.kt`, `res/values/strings.xml` | effets de carte découplés, boutons, pastille (4) ; détail (6) ; FPS (7) | 4, 6, 7 |
| `core/map/tools/upstream/dark.json`, `positron.json` (nouveaux) | styles OpenFreeMap amont figés au 2026-10-31 | 5 |
| `core/map/tools/build-roadly-styles.ps1` (nouveau) | génération déterministe du style | 5 |
| `core/map/src/main/assets/styles/roadly-dark.json`, `roadly-light.json` (générés) | styles embarqués | 5 |
| `core/data/.../config/ProviderConfig.kt` | styles par défaut `asset://`, normalisation | 5 |
| `core/model/.../MapDetail.kt` (nouveau) | `NORMAL` / `REDUCED` | 6 |
| `core/map/.../MapDetailPolicy.kt` (nouveau) | réduction auto au-delà de 90 km/h (hystérésis 80) | 6 |
| `core/data/.../UserPreferencesRepository.kt`, `testing/FakeUserPreferencesRepository.kt` | `mapDetail` | 6 |
| `feature/settings/.../SettingsViewModel.kt`, `SettingsScreen.kt`, `res/values/strings.xml` | réglage « Niveau de détail de la carte » | 6 |
| `core/map/.../TraversedLine.kt`, `FpsWindow.kt`, `MapRendering.kt` (nouveaux), `RoadlyMap.kt` | C3 | 7 |
| `feature/navigation/.../ReportsAheadProjector.kt` (nouveau) | signalements projetés une fois par tracé | 7 |
| `ROADLY_3D_NAVIGATION_TESTS.md`, `docs/captures/etape-c/*.png` | mesures et essais réels | 8 |

Préfixes des chemins :
- `map/` = `core/map/src/main/java/fr/roadly/core/map/` ;
- `maptest/` = `core/map/src/test/java/fr/roadly/core/map/` ;
- `nav/` = `feature/navigation/src/main/java/fr/roadly/feature/navigation/` ;
- `navtest/` = `feature/navigation/src/test/java/fr/roadly/feature/navigation/`.

---

### Tâche 1 : C1 — filtre de cap et `NavigationCameraController` (pur)

**Fichiers :**
- Créer : `map/Bearings.kt`, `map/NavigationCameraController.kt`.
- Modifier : `map/GuidanceCameraPolicy.kt`, lignes 12-24 (`GuidanceCameraInput`), 26-41 (KDoc et constantes) et 68-72 (inclinaison).
- Test : `maptest/NavigationCameraControllerTest.kt` (nouveau) ; `maptest/GuidanceCameraPolicyTest.kt` (2 tests ajoutés avant l'accolade finale, ligne 91).

**Interfaces :**
- Consomme : `GuidanceCameraPolicy.compute(input, previous)`, `GuidanceCamera`, `CameraPadding`, `PADDING_PORTRAIT`, `PADDING_LANDSCAPE`, `STOPPED_KMH`, `ZOOM_2D`, `PITCH_3D`, `PITCH_APPROACH`, `PITCH_POWER_SAVE` (existants).
- Produit :
  - `internal object Bearings { fun normalize(deg: Double): Double; fun delta(from: Double, to: Double): Double; fun lerp(from: Double, to: Double, t: Double): Double }`
  - `enum class CameraMode3D { FOLLOW_3D, FOLLOW_2D, NORTH_UP; fun toggled3d(): CameraMode3D; companion object { fun default(map3dEnabled: Boolean): CameraMode3D } }`
  - `data class NavigationCameraInput(val point: GeoPoint, val bearingDeg: Float?, val speedKmh: Double?, val distanceToManeuverM: Double, val maneuverType: ManeuverType, val approaching: Boolean?, val mode: CameraMode3D, val powerSave: Boolean, val landscape: Boolean, val nowMs: Long, val bottomPanelFraction: Float? = null)`
  - `data class NavigationCameraFrame(val point: GeoPoint, val camera: GuidanceCamera, val durationMs: Int, val eased: Boolean)`
  - `class NavigationCameraController` avec :
    - `fun update(input: NavigationCameraInput): NavigationCameraFrame`, `fun requestTransition()`, `fun reset()`, `val bearing: Double?` ;
    - constantes `BEARING_TAU_S = 1.5`, `MAX_DT_S = 5.0`, `TICK_MS = 1_000`, `TRANSITION_MS = 600`, `MAX_VERTICAL_PADDING = 0.85f`.
  - `GuidanceCameraInput.buildingsVisible: Boolean = false`, ajouté en dernier paramètre. Constantes `GuidanceCameraPolicy.PITCH_DENSE_CITY = 60.0`, `DENSE_CITY_MAX_KMH = 30.0`, `MAX_PITCH = 60.0`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`maptest/NavigationCameraControllerTest.kt` :

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

class NavigationCameraControllerTest {
    private fun input(
        bearing: Float? = 90f, speed: Double? = 50.0, nowMs: Long = 0L, mode: CameraMode3D = CameraMode3D.FOLLOW_3D,
        approaching: Boolean? = false, powerSave: Boolean = false, landscape: Boolean = false, bottomPanel: Float? = null,
    ) = NavigationCameraInput(
        point = GeoPoint(48.85, 2.35), bearingDeg = bearing, speedKmh = speed, distanceToManeuverM = 2_000.0,
        maneuverType = ManeuverType.TURN_RIGHT, approaching = approaching, mode = mode, powerSave = powerSave,
        landscape = landscape, nowMs = nowMs, bottomPanelFraction = bottomPanel,
    )

    private fun assertPadding(expected: CameraPadding, actual: CameraPadding) {
        assertEquals(expected.left, actual.left, 1e-6f); assertEquals(expected.top, actual.top, 1e-6f)
        assertEquals(expected.right, actual.right, 1e-6f); assertEquals(expected.bottom, actual.bottom, 1e-6f)
    }

    @Test fun `crossing north turns the short way, without a full spin`() { // C1 : 359° → 1°
        assertEquals(20.0, Bearings.delta(350.0, 10.0), 1e-9)
        val c = NavigationCameraController()
        c.update(input(bearing = 359f, nowMs = 0))
        for (i in 1..10) {
            val f = c.update(input(bearing = 1f, nowMs = i * 1_000L))
            val b = c.bearing!!
            assertTrue("cap $b hors de [359 ; 1]", b >= 359.0 - 1e-9 || b <= 1.0 + 1e-9)
            assertEquals(0.0, Bearings.delta(b, f.camera.bearing), 1e-3)
        }
        assertEquals(0.0, Bearings.delta(c.bearing!!, 1.0), 0.01)
    }

    @Test fun `the heading is an exponential average with a 1,5 s time constant`() {
        val c = NavigationCameraController()
        c.update(input(bearing = 0f, nowMs = 0))
        c.update(input(bearing = 90f, nowMs = 1_500))
        assertEquals(90.0 * (1 - exp(-1.0)), c.bearing!!, 1e-6)
    }

    @Test fun `stopped, the heading does not drift`() { // C1 : arrêt sans dérive
        val c = NavigationCameraController()
        for (i in 0..2) c.update(input(bearing = 90f, nowMs = i * 1_000L))
        val settled = c.bearing!!
        listOf(200f, 10f, 300f, 45f).forEachIndexed { i, noisy ->
            val f = c.update(input(bearing = noisy, speed = 2.0, nowMs = 3_000L + i * 1_000L))
            assertEquals(settled, c.bearing!!, 0.0)
            assertEquals(settled, f.camera.bearing, 1e-3)
        }
    }

    @Test fun `a long GPS gap is capped so the heading catches up without overshoot`() {
        val c = NavigationCameraController()
        c.update(input(bearing = 0f, nowMs = 0))
        c.update(input(bearing = 90f, nowMs = 60_000))
        val alpha = 1 - exp(-NavigationCameraController.MAX_DT_S / NavigationCameraController.BEARING_TAU_S)
        assertEquals(90.0 * alpha, c.bearing!!, 1e-6)
    }

    @Test fun `an unknown heading keeps the filtered one`() {
        val c = NavigationCameraController()
        c.update(input(bearing = 45f, nowMs = 0))
        val f = c.update(input(bearing = null, nowMs = 1_000))
        assertEquals(45.0, c.bearing!!, 1e-9)
        assertEquals(45.0, f.camera.bearing, 1e-3)
    }

    @Test fun `2d stays flat, north up points north, back to 3d restores the heading`() {
        val c = NavigationCameraController()
        val flat = c.update(input(mode = CameraMode3D.FOLLOW_2D))
        assertEquals(0.0, flat.camera.pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.ZOOM_2D, flat.camera.zoom, 1e-9)
        assertEquals(90.0, flat.camera.bearing, 1e-3)
        val north = c.update(input(mode = CameraMode3D.NORTH_UP, nowMs = 1_000))
        assertEquals(0.0, north.camera.bearing, 1e-9)
        assertEquals(0.0, north.camera.pitch, 1e-9)
        val back = c.update(input(mode = CameraMode3D.FOLLOW_3D, nowMs = 2_000))
        assertEquals(90.0, back.camera.bearing, 1e-3)
        assertEquals(GuidanceCameraPolicy.PITCH_3D, back.camera.pitch, 1e-9)
    }

    @Test fun `dense city tilts to the maximum below 30 km per h, except in power save or on approach`() {
        assertEquals(GuidanceCameraPolicy.PITCH_DENSE_CITY, NavigationCameraController().update(input(speed = 20.0)).camera.pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_POWER_SAVE, NavigationCameraController().update(input(speed = 20.0, powerSave = true)).camera.pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_APPROACH, NavigationCameraController().update(input(speed = 20.0, approaching = true)).camera.pitch, 1e-9)
    }

    @Test fun `the zoom stays smoothed across a mode change`() {
        val c = NavigationCameraController()
        c.update(input(speed = 130.0, mode = CameraMode3D.FOLLOW_2D))   // zoom 2D : 17,0
        val f = c.update(input(speed = 130.0, nowMs = 1_000))           // cible 15,8, pas maximal 0,5
        assertEquals(16.5, f.camera.zoom, 1e-9)
    }

    @Test fun `first frame jumps, ticks glide 1 s linearly, mode changes and recentring ease 600 ms`() {
        val c = NavigationCameraController()
        assertEquals(0, c.update(input(nowMs = 0)).durationMs)
        val tick = c.update(input(nowMs = 1_000))
        assertEquals(NavigationCameraController.TICK_MS, tick.durationMs)
        assertFalse(tick.eased)
        val switch = c.update(input(nowMs = 1_200, mode = CameraMode3D.FOLLOW_2D))
        assertEquals(NavigationCameraController.TRANSITION_MS, switch.durationMs)
        assertTrue(switch.eased)
        val after = c.update(input(nowMs = 2_000, mode = CameraMode3D.FOLLOW_2D))
        assertEquals(NavigationCameraController.TICK_MS, after.durationMs)
        assertFalse(after.eased)
        c.requestTransition()
        val recentred = c.update(input(nowMs = 3_000, mode = CameraMode3D.FOLLOW_2D))
        assertEquals(NavigationCameraController.TRANSITION_MS, recentred.durationMs)
        assertTrue(recentred.eased)
        c.reset()
        assertEquals(0, c.update(input(nowMs = 4_000)).durationMs)
    }

    @Test fun `the bottom panel pushes the car up, in portrait only`() {
        assertPadding(CameraPadding(0f, 0.45f, 0f, 0.30f), NavigationCameraController().update(input(bottomPanel = 0.30f)).camera.padding)
        assertPadding(CameraPadding(0f, 0.35f, 0f, 0.50f), NavigationCameraController().update(input(bottomPanel = 0.50f)).camera.padding)
        assertPadding(GuidanceCameraPolicy.PADDING_PORTRAIT, NavigationCameraController().update(input(bottomPanel = 0.05f)).camera.padding)
        assertPadding(GuidanceCameraPolicy.PADDING_LANDSCAPE, NavigationCameraController().update(input(bottomPanel = 0.30f, landscape = true)).camera.padding)
    }

    @Test fun `the 2d button and the session default`() {
        assertEquals(CameraMode3D.FOLLOW_3D, CameraMode3D.default(map3dEnabled = true))
        assertEquals(CameraMode3D.FOLLOW_2D, CameraMode3D.default(map3dEnabled = false))
        assertEquals(CameraMode3D.FOLLOW_2D, CameraMode3D.FOLLOW_3D.toggled3d())
        assertEquals(CameraMode3D.FOLLOW_3D, CameraMode3D.FOLLOW_2D.toggled3d())
        assertEquals(CameraMode3D.FOLLOW_3D, CameraMode3D.NORTH_UP.toggled3d())
    }
}
```

Dans `maptest/GuidanceCameraPolicyTest.kt`, ajouter avant l'accolade finale (ligne 91) :

```kotlin
    @Test fun `dense city tilts to the MapLibre maximum below 30 km per h when buildings are visible`() {
        fun pitch(speed: Double?, buildings: Boolean, distance: Double = 2_000.0) =
            GuidanceCameraPolicy.compute(input(speed = speed, distance = distance).copy(buildingsVisible = buildings), null).pitch
        assertEquals(GuidanceCameraPolicy.PITCH_DENSE_CITY, pitch(20.0, true), 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_DENSE_CITY, pitch(2.0, true), 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_3D, pitch(20.0, false), 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_3D, pitch(30.0, true), 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_3D, pitch(null, true), 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_APPROACH, pitch(20.0, true, distance = 100.0), 1e-9)
    }

    @Test fun `no pitch goes beyond what MapLibre Android renders`() {
        for (speed in listOf(null, 0.0, 20.0, 50.0, 130.0)) for (buildings in listOf(true, false)) for (powerSave in listOf(true, false)) for (distance in listOf(100.0, 2_000.0)) {
            val pitch = GuidanceCameraPolicy.compute(input(speed = speed, distance = distance, powerSave = powerSave).copy(buildingsVisible = buildings), null).pitch
            assertTrue("$speed $buildings $powerSave $distance → $pitch", pitch <= GuidanceCameraPolicy.MAX_PITCH)
        }
    }
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*NavigationCameraControllerTest*" --tests "*GuidanceCameraPolicyTest*"`

Résultat attendu : ÉCHEC de compilation (`Unresolved reference: NavigationCameraInput`, `buildingsVisible`).

- [ ] **Étape 3 : écrire l'implémentation.**

`map/Bearings.kt` :

```kotlin
package fr.roadly.core.map

/** Calculs sur les caps (degrés, 0 = nord, sens horaire), toujours par le plus court chemin. */
internal object Bearings {
    /** Cap ramené dans [0, 360[. */
    fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** Écart signé le plus court de [from] vers [to], dans ]-180, 180]. */
    fun delta(from: Double, to: Double): Double {
        val d = normalize(to - from)
        return if (d > 180.0) d - 360.0 else d
    }

    /** Cap à la fraction [t] de [from] vers [to] (0 → from, 1 → to), sans tour complet. */
    fun lerp(from: Double, to: Double, t: Double): Double = normalize(from + delta(from, to) * t)
}
```

`map/NavigationCameraController.kt` :

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import kotlin.math.exp

/** C1 : mode de caméra du guidage, choisi pour la session (bouton 2D/3D, « nord en haut ») ; départ = réglage « Carte 3D ». */
enum class CameraMode3D {
    FOLLOW_3D, FOLLOW_2D, NORTH_UP;

    /** Bouton 2D/3D : 3D → 2D, sinon 3D (depuis « nord en haut », on repasse en 3D). */
    fun toggled3d(): CameraMode3D = if (this == FOLLOW_3D) FOLLOW_2D else FOLLOW_3D

    companion object {
        /** Mode de départ d'une session : réglage « Carte 3D en guidage » activé → 3D, sinon 2D. */
        fun default(map3dEnabled: Boolean): CameraMode3D = if (map3dEnabled) FOLLOW_3D else FOLLOW_2D
    }
}

/** Entrée de la caméra à chaque position (temps [nowMs] monotone, ex. `SystemClock.elapsedRealtime()`). */
data class NavigationCameraInput(
    val point: GeoPoint,
    val bearingDeg: Float?,
    val speedKmh: Double?,
    val distanceToManeuverM: Double,
    val maneuverType: ManeuverType,
    /** Approche décidée par l'écran (hystérésis B1) ; null = règle de distance. */
    val approaching: Boolean?,
    val mode: CameraMode3D,
    val powerSave: Boolean,
    val landscape: Boolean,
    val nowMs: Long,
    /** Hauteur du panneau inférieur / hauteur de la vue (portrait) ; null = marges par défaut. Mesure branchée à l'Étape D5. */
    val bottomPanelFraction: Float? = null,
)

/** Commande caméra : cible, caméra, durée (0 = déplacement immédiat) et courbe ([eased] = accélération / décélération MapLibre, sinon linéaire). */
data class NavigationCameraFrame(val point: GeoPoint, val camera: GuidanceCamera, val durationMs: Int, val eased: Boolean)

/**
 * Caméra du guidage (C1) : enveloppe [GuidanceCameraPolicy] (zoom, inclinaison, marges) et y ajoute le cap filtré
 * (moyenne exponentielle circulaire, constante de temps [BEARING_TAU_S], gelée à l'arrêt), le mode et les transitions.
 */
class NavigationCameraController {
    private var filteredBearing: Double? = null
    private var lastNowMs: Long? = null
    private var previous: GuidanceCamera? = null
    private var lastMode: CameraMode3D? = null
    private var transitionRequested = false

    /** Cap filtré courant (degrés), null avant le premier cap connu. */
    val bearing: Double? get() = filteredBearing

    fun update(input: NavigationCameraInput): NavigationCameraFrame {
        val bearing = filterBearing(input)
        val threeD = input.mode == CameraMode3D.FOLLOW_3D
        val headingUp = GuidanceCameraPolicy.compute(
            GuidanceCameraInput(
                speedKmh = input.speedKmh, bearingDeg = bearing?.toFloat(), distanceToManeuverM = input.distanceToManeuverM,
                maneuverType = input.maneuverType, threeD = threeD, powerSave = input.powerSave, landscape = input.landscape,
                approaching = input.approaching, buildingsVisible = threeD && !input.powerSave,
            ),
            previous,
        )
        // La caméra « cap en haut » est gardée comme référence : en sortant du nord en haut, cap et zoom repartent d'elle.
        previous = headingUp
        val padded = withBottomPanel(headingUp, input)
        val camera = if (input.mode == CameraMode3D.NORTH_UP) padded.copy(bearing = 0.0) else padded
        val first = lastMode == null
        val transition = !first && (transitionRequested || lastMode != input.mode)
        lastMode = input.mode
        transitionRequested = false
        val duration = when {
            first -> 0
            transition -> TRANSITION_MS
            else -> TICK_MS
        }
        return NavigationCameraFrame(input.point, camera, duration, eased = transition)
    }

    /** Prochaine image en transition douce (recentrage après un geste). */
    fun requestTransition() { transitionRequested = true }

    /** Nouveau tracé : cap, zoom et mode oubliés ; la prochaine image est immédiate. */
    fun reset() {
        filteredBearing = null; lastNowMs = null; previous = null; lastMode = null; transitionRequested = false
    }

    private fun filterBearing(input: NavigationCameraInput): Double? {
        val raw = input.bearingDeg?.toDouble()
        val last = lastNowMs
        lastNowMs = input.nowMs
        val current = filteredBearing
        val stopped = (input.speedKmh ?: 0.0) < GuidanceCameraPolicy.STOPPED_KMH
        if (raw == null || (stopped && current != null)) return current
        if (current == null || last == null) return Bearings.normalize(raw).also { filteredBearing = it }
        val dtS = ((input.nowMs - last) / 1000.0).coerceIn(0.0, MAX_DT_S)
        val alpha = 1.0 - exp(-dtS / BEARING_TAU_S)
        return Bearings.normalize(current + alpha * Bearings.delta(current, raw)).also { filteredBearing = it }
    }

    private fun withBottomPanel(cam: GuidanceCamera, input: NavigationCameraInput): GuidanceCamera {
        val fraction = input.bottomPanelFraction ?: return cam
        if (input.landscape) return cam
        val bottom = maxOf(cam.padding.bottom, fraction)
        val top = cam.padding.top.coerceAtMost(MAX_VERTICAL_PADDING - bottom).coerceAtLeast(0f)
        return cam.copy(padding = cam.padding.copy(top = top, bottom = bottom))
    }

    companion object {
        /** Constante de temps (s) du filtre de cap : environ 63 % d'un virage absorbé en 1,5 s. */
        const val BEARING_TAU_S = 1.5
        /** Intervalle maximal (s) pris en compte entre deux positions (après un trou GPS, le cap rattrape sans dépasser). */
        const val MAX_DT_S = 5.0
        /** Durée (ms) d'un glissement de caméra entre deux positions (~1 Hz), linéaire, comme l'animation du marqueur. */
        const val TICK_MS = 1_000
        /** Durée (ms) d'une transition 2D ↔ 3D, nord en haut ou recentrage. */
        const val TRANSITION_MS = 600
        /** Somme maximale des marges haute et basse (fraction de la hauteur de la vue). */
        const val MAX_VERTICAL_PADDING = 0.85f
    }
}
```

Dans `map/GuidanceCameraPolicy.kt` :

1. Lignes 22-24. Ancien :
```kotlin
    /** Approche décidée par l'écran (hystérésis B1) ; null = règle de distance [GuidanceCameraPolicy.isApproaching]. */
    val approaching: Boolean? = null,
)
```
Nouveau :
```kotlin
    /** Approche décidée par l'écran (hystérésis B1) ; null = règle de distance [GuidanceCameraPolicy.isApproaching]. */
    val approaching: Boolean? = null,
    /** Bâtiments 3D affichés (mode 3D hors économie d'énergie) : autorise l'inclinaison « ville dense ». */
    val buildingsVisible: Boolean = false,
)
```
2. Lignes 26-33. Ancien :
```kotlin
/**
 * Caméra de guidage (spec guidage 3D §1) : inclinaison 58° (45° en économie, 0° en 2D), zoom de croisière
 * selon la vitesse, vue serrée à l'approche d'une manœuvre, cap conservé à l'arrêt, voiture décalée vers le bas.
 */
object GuidanceCameraPolicy {
    const val PITCH_3D = 58.0
    const val PITCH_POWER_SAVE = 45.0
    const val PITCH_APPROACH = 50.0
```
Nouveau :
```kotlin
/**
 * Caméra de guidage (spec guidage 3D §1, C1) : inclinaison 58° (50° à l'approche, 60° en ville dense, 45° en économie, 0° en 2D),
 * zoom de croisière selon la vitesse, vue serrée à l'approche d'une manœuvre, cap conservé à l'arrêt, voiture décalée vers le bas.
 */
object GuidanceCameraPolicy {
    const val PITCH_3D = 58.0
    const val PITCH_POWER_SAVE = 45.0
    const val PITCH_APPROACH = 50.0
    /** LIMITE : MapLibre Android 11.13.5 ne rend pas au-delà de 60° (`MapLibreConstants.MAXIMUM_PITCH`, `Transform.setMaxPitch`). */
    const val MAX_PITCH = 60.0
    /** Ville dense (C1 demande 62°) : plafonnée au maximum rendu par MapLibre, [MAX_PITCH]. */
    const val PITCH_DENSE_CITY = MAX_PITCH
    /** Vitesse (km/h) sous laquelle, bâtiments visibles, la caméra passe en inclinaison « ville dense ». */
    const val DENSE_CITY_MAX_KMH = 30.0
```
3. Lignes 68-72. Ancien :
```kotlin
        val pitch = when {
            input.powerSave -> PITCH_POWER_SAVE
            approaching -> PITCH_APPROACH
            else -> PITCH_3D
        }
```
Nouveau :
```kotlin
        val pitch = when {
            input.powerSave -> PITCH_POWER_SAVE
            approaching -> PITCH_APPROACH
            input.buildingsVisible && input.speedKmh != null && input.speedKmh < DENSE_CITY_MAX_KMH -> PITCH_DENSE_CITY
            else -> PITCH_3D
        }
```

- [ ] **Étape 4 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS. Les 10 tests de `GuidanceCameraPolicyTest` passent sans changement, avec les 2 nouveaux tests et les 11 de `NavigationCameraControllerTest`.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/map/src/main/java/fr/roadly/core/map/Bearings.kt core/map/src/main/java/fr/roadly/core/map/NavigationCameraController.kt core/map/src/main/java/fr/roadly/core/map/GuidanceCameraPolicy.kt core/map/src/test/java/fr/roadly/core/map/NavigationCameraControllerTest.kt core/map/src/test/java/fr/roadly/core/map/GuidanceCameraPolicyTest.kt
git commit -m @'
feat(carte): contrôleur de caméra — cap filtré (τ 1,5 s), modes 3D / 2D / nord en haut, transitions 600 ms (C1)

Inclinaison « ville dense » sous 30 km/h bâtiments visibles, plafonnée à 60° : maximum rendu par MapLibre Android 11.13.5 (LIMITE).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 2 : C1 — marqueur interpolé, transition et inclinaison restaurée dans `MapController`

**Fichiers :**
- Créer : `map/MarkerInterpolation.kt`.
- Modifier : `map/MapController.kt` :
  - imports (lignes 3-6) et champs (après la ligne 80) ;
  - `detach` (lignes 85-89) ;
  - `replayState` (ligne 198) ;
  - `setUserLocation` (lignes 217-222) ;
  - `applyGuidanceCamera` (lignes 268-287) ;
  - `moveTo` (lignes 289-298) ;
  - `restore` (ligne 310) ;
  - ajout d'un `companion object` avant l'accolade finale.
- Modifier : `map/MapLayers.kt`, lignes 69-73 (`PointFeatureBuilder.user`).
- Modifier : `map/CameraSnapshot.kt`, ligne 3 (KDoc).
- Modifier : `feature/trips/src/main/java/fr/roadly/feature/trips/ReplayScreen.kt`, ligne 82.
- Test : `maptest/MarkerInterpolationTest.kt` (nouveau).

**Interfaces :**
- Consomme : `Bearings` (tâche 1), `GeoPoint.distanceTo`, `LocationFix`.
- Produit :
  - `internal data class MarkerPose(val lat: Double, val lon: Double, val bearingDeg: Double)` ;
  - `internal object MarkerInterpolation { const val SNAP_ABOVE_M = 200.0; fun pose(fix: LocationFix): MarkerPose; fun lerp(from: MarkerPose, to: MarkerPose, t: Double): MarkerPose; fun shouldAnimate(from: MarkerPose, to: MarkerPose, animateMs: Long): Boolean }` ;
  - `MapController.setUserLocation(fix: LocationFix?, animateMs: Long = MapController.USER_ANIMATION_MS)`, avec `USER_ANIMATION_MS = 1_000L` ;
  - `MapController.applyGuidanceCamera(point: GeoPoint, camera: GuidanceCamera, durationMs: Int = 1000, eased: Boolean = false)` ;
  - `MapController.moveTo(point: GeoPoint, zoom: Double, bearing: Double = 0.0, animate: Boolean = true, tilt: Double = 0.0)` ;
  - `MapController.restore(snapshot: CameraSnapshot, keepTilt: Boolean = false)` ;
  - `PointFeatureBuilder.user(pose: MarkerPose, accuracyM: Float): String`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`maptest/MarkerInterpolationTest.kt` :

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerInterpolationTest {
    private val a = MarkerPose(48.0, 2.0, 350.0)
    private val b = MarkerPose(48.001, 2.001, 10.0)   // ~134 m plus loin

    @Test fun `halfway is the midpoint and the arrow turns the short way`() {
        val m = MarkerInterpolation.lerp(a, b, 0.5)
        assertEquals(48.0005, m.lat, 1e-12)
        assertEquals(2.0005, m.lon, 1e-12)
        assertEquals(0.0, m.bearingDeg, 1e-9)
    }

    @Test fun `the fraction is clamped to the two fixes`() {
        assertEquals(a, MarkerInterpolation.lerp(a, b, -1.0))
        val end = MarkerInterpolation.lerp(a, b, 2.0)
        assertEquals(b.lat, end.lat, 1e-12); assertEquals(b.lon, end.lon, 1e-12); assertEquals(b.bearingDeg, end.bearingDeg, 1e-9)
    }

    @Test fun `a jump of more than 200 m snaps instead of gliding across the map`() {
        assertTrue(MarkerInterpolation.shouldAnimate(a, b, 1_000))
        assertFalse(MarkerInterpolation.shouldAnimate(a, MarkerPose(48.01, 2.0, 0.0), 1_000))  // ~1,1 km
        assertFalse(MarkerInterpolation.shouldAnimate(a, b, 0))
    }

    @Test fun `a fix without heading points north, headings are normalised`() {
        assertEquals(0.0, MarkerInterpolation.pose(LocationFix(GeoPoint(48.0, 2.0), null, null, 5f, 0L)).bearingDeg, 1e-9)
        assertEquals(270.0, MarkerInterpolation.pose(LocationFix(GeoPoint(48.0, 2.0), -90f, null, 5f, 0L)).bearingDeg, 1e-9)
    }
}
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*MarkerInterpolationTest*"`

Résultat attendu : ÉCHEC de compilation (`Unresolved reference: MarkerPose`).

- [ ] **Étape 3 : écrire l'implémentation.**

`map/MarkerInterpolation.kt` :

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix

/** Pose affichée du marqueur utilisateur (position et cap), interpolée entre deux positions GPS. */
internal data class MarkerPose(val lat: Double, val lon: Double, val bearingDeg: Double)

/** C1 : le marqueur glisse d'une position à la suivante au rythme de la caméra, au lieu de sauter à chaque position (1 Hz). */
internal object MarkerInterpolation {
    /** Au-delà (m), le marqueur est posé directement (recalcul, saut GPS) au lieu de glisser à travers la carte. */
    const val SNAP_ABOVE_M = 200.0

    /** Pose d'un fix ; cap inconnu = nord (comme le marqueur d'origine). */
    fun pose(fix: LocationFix): MarkerPose = MarkerPose(fix.point.lat, fix.point.lon, Bearings.normalize((fix.bearingDeg ?: 0f).toDouble()))

    /** Pose à la fraction [t] (bornée à 0..1) : position linéaire, cap par le plus court chemin. */
    fun lerp(from: MarkerPose, to: MarkerPose, t: Double): MarkerPose {
        val k = t.coerceIn(0.0, 1.0)
        return MarkerPose(from.lat + (to.lat - from.lat) * k, from.lon + (to.lon - from.lon) * k, Bearings.lerp(from.bearingDeg, to.bearingDeg, k))
    }

    /** Animer seulement si une durée est demandée et si le déplacement reste local (≤ [SNAP_ABOVE_M]). */
    fun shouldAnimate(from: MarkerPose, to: MarkerPose, animateMs: Long): Boolean =
        animateMs > 0 && GeoPoint(from.lat, from.lon).distanceTo(GeoPoint(to.lat, to.lon)) <= SNAP_ABOVE_M
}
```

Dans `map/MapLayers.kt`, lignes 69-73. Ancien :
```kotlin
    fun user(fix: LocationFix): String = buildJsonObject {
        put("type", "Feature")
        putJsonObject("properties") { put("bearing", (fix.bearingDeg ?: 0f).toDouble()); put("accuracy", fix.accuracyM.toDouble()) }
        putJsonObject("geometry") { put("type", "Point"); put("coordinates", JsonArray(listOf(JsonPrimitive(fix.point.lon), JsonPrimitive(fix.point.lat)))) }
    }.toString()
```
Nouveau :
```kotlin
    fun user(fix: LocationFix): String = user(MarkerInterpolation.pose(fix), fix.accuracyM)

    /** Marqueur à une pose interpolée (C1). */
    fun user(pose: MarkerPose, accuracyM: Float): String = buildJsonObject {
        put("type", "Feature")
        putJsonObject("properties") { put("bearing", pose.bearingDeg); put("accuracy", accuracyM.toDouble()) }
        putJsonObject("geometry") { put("type", "Point"); put("coordinates", JsonArray(listOf(JsonPrimitive(pose.lon), JsonPrimitive(pose.lat)))) }
    }.toString()
```

Dans `map/MapController.kt` :

1. Ajouter aux imports (après la ligne 3) : `import android.animation.ValueAnimator` et `import android.view.animation.LinearInterpolator`.
2. Après la ligne 80 (`private var lastSnapshot: CameraSnapshot? = null`), ajouter :
```kotlin

    /** C1 : animation en cours du marqueur, et pose affichée (point de départ de la prochaine animation). */
    private var markerAnimator: ValueAnimator? = null
    private var shownPose: MarkerPose? = null
```
3. `detach`, lignes 85-89. Ancien :
```kotlin
    internal fun detach() {
        map = null
        styleReady = false
        onRouteClick = null
    }
```
Nouveau :
```kotlin
    internal fun detach() {
        markerAnimator?.cancel()
        markerAnimator = null
        map = null
        styleReady = false
        onRouteClick = null
    }
```
4. `replayState`, ligne 198. Ancien :
```kotlin
        lastFix?.let { style.getSourceAs<GeoJsonSource>(MapIds.SRC_USER)?.setGeoJson(PointFeatureBuilder.user(it)) }
```
Nouveau :
```kotlin
        lastFix?.let { shownPose = MarkerInterpolation.pose(it); style.getSourceAs<GeoJsonSource>(MapIds.SRC_USER)?.setGeoJson(PointFeatureBuilder.user(it)) }
```
5. `setUserLocation`, lignes 217-222. Ancien :
```kotlin
    fun setUserLocation(fix: LocationFix?) {
        lastFix = fix
        if (!styleReady) return
        val src = map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_USER) ?: return
        if (fix == null) src.setGeoJson("""{"type":"FeatureCollection","features":[]}""") else src.setGeoJson(PointFeatureBuilder.user(fix))
    }
```
Nouveau :
```kotlin
    /**
     * Position de l'utilisateur. C1 : le marqueur glisse de la pose affichée vers [fix] en [animateMs] (linéaire, comme `easeCamera`)
     * pour avancer avec la caméra ; 0 = pose immédiate ; un saut de plus de 200 m est posé directement.
     */
    fun setUserLocation(fix: LocationFix?, animateMs: Long = USER_ANIMATION_MS) {
        lastFix = fix
        markerAnimator?.cancel()
        markerAnimator = null
        val target = fix?.let { MarkerInterpolation.pose(it) }
        if (!styleReady) { shownPose = target; return }
        val src = map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_USER) ?: return
        if (fix == null || target == null) {
            shownPose = null
            src.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
            return
        }
        val from = shownPose
        if (from == null || !MarkerInterpolation.shouldAnimate(from, target, animateMs)) {
            shownPose = target
            src.setGeoJson(PointFeatureBuilder.user(target, fix.accuracyM))
            return
        }
        markerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animateMs
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                val pose = MarkerInterpolation.lerp(from, target, (a.animatedValue as Float).toDouble())
                shownPose = pose
                map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_USER)?.setGeoJson(PointFeatureBuilder.user(pose, fix.accuracyM))
            }
            start()
        }
    }
```
6. `applyGuidanceCamera`, lignes 268-287. Ancien (KDoc, signature et ligne 282) :
```kotlin
    /**
     * Caméra de guidage : cible, zoom, cap, inclinaison et marges (fractions de la vue → pixels) ;
     * transition linéaire de [durationMs] pour suivre le rythme des positions (~1 Hz).
     */
    fun applyGuidanceCamera(point: GeoPoint, camera: GuidanceCamera, durationMs: Int = 1000) {
```
puis
```kotlin
            if (durationMs > 0) m.easeCamera(update, durationMs, false) else m.moveCamera(update)
```
Nouveau :
```kotlin
    /**
     * Caméra de guidage : cible, zoom, cap, inclinaison et marges (fractions de la vue → pixels) ;
     * transition de [durationMs], linéaire pour suivre le rythme des positions (~1 Hz), adoucie si [eased] (bascule 2D/3D, recentrage).
     */
    fun applyGuidanceCamera(point: GeoPoint, camera: GuidanceCamera, durationMs: Int = 1000, eased: Boolean = false) {
```
puis
```kotlin
            if (durationMs > 0) m.easeCamera(update, durationMs, eased) else m.moveCamera(update)
```
7. `moveTo`, lignes 289-291. Ancien :
```kotlin
    fun moveTo(point: GeoPoint, zoom: Double, bearing: Double = 0.0, animate: Boolean = true) {
        val action: (MapLibreMap) -> Unit = { m ->
            val pos = CameraPosition.Builder().target(LatLng(point.lat, point.lon)).zoom(zoom).bearing(bearing).tilt(0.0).padding(0.0, 0.0, 0.0, 0.0).build()
```
Nouveau :
```kotlin
    fun moveTo(point: GeoPoint, zoom: Double, bearing: Double = 0.0, animate: Boolean = true, tilt: Double = 0.0) {
        val action: (MapLibreMap) -> Unit = { m ->
            val pos = CameraPosition.Builder().target(LatLng(point.lat, point.lon)).zoom(zoom).bearing(bearing).tilt(tilt).padding(0.0, 0.0, 0.0, 0.0).build()
```
8. `restore`, ligne 310. Ancien :
```kotlin
    fun restore(snapshot: CameraSnapshot) = moveTo(GeoPoint(snapshot.lat, snapshot.lon), snapshot.zoom, snapshot.bearing, animate = false)
```
Nouveau :
```kotlin
    /** Restaure [snapshot] ; l'inclinaison n'est reprise que si [keepTilt] (écran de guidage) — l'accueil reste à plat. */
    fun restore(snapshot: CameraSnapshot, keepTilt: Boolean = false) =
        moveTo(GeoPoint(snapshot.lat, snapshot.lon), snapshot.zoom, snapshot.bearing, animate = false, tilt = if (keepTilt) snapshot.tilt else 0.0)
```
9. Avant l'accolade finale de la classe (après `drawableToBitmap`), ajouter :
```kotlin

    companion object {
        /** Durée (ms) du glissement du marqueur entre deux positions, égale à celle de la caméra (C1). */
        const val USER_ANIMATION_MS = 1_000L
    }
```

`map/CameraSnapshot.kt`, ligne 3. Ancien :
```kotlin
/** Position de caméra mémorisée pour être restaurée (retour sur l'écran d'accueil en mode libre). */
```
Nouveau :
```kotlin
/** Position de caméra mémorisée pour être restaurée (accueil en mode libre, retour sur le guidage) ; [tilt] repris par le guidage seulement. */
```

`feature/trips/.../ReplayScreen.kt`, ligne 82. Ancien :
```kotlin
    LaunchedEffect(frame) { if (frame != null) map.setUserLocation(LocationFix(frame.point, frame.bearingDeg, null, 5f, 0L)) }
```
Nouveau :
```kotlin
    // Relecture : positions rapprochées, le marqueur suit chaque image sans glisser.
    LaunchedEffect(frame) { if (frame != null) map.setUserLocation(LocationFix(frame.point, frame.bearingDeg, null, 5f, 0L), animateMs = 0L) }
```

- [ ] **Étape 4 : vérifier que les tests passent et que les appelants compilent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest :feature:trips:compileDebugKotlin :feature:navigation:compileDebugKotlin :feature:home:compileDebugKotlin --offline -q`

Résultat attendu : SUCCÈS. L'écran de guidage appelle encore `applyGuidanceCamera(point, cam)` : c'est compatible.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/map/src/main/java/fr/roadly/core/map/MarkerInterpolation.kt core/map/src/main/java/fr/roadly/core/map/MapController.kt core/map/src/main/java/fr/roadly/core/map/MapLayers.kt core/map/src/main/java/fr/roadly/core/map/CameraSnapshot.kt core/map/src/test/java/fr/roadly/core/map/MarkerInterpolationTest.kt feature/trips/src/main/java/fr/roadly/feature/trips/ReplayScreen.kt
git commit -m @'
feat(carte): marqueur interpolé sur 1 s avec la caméra, transitions adoucies, inclinaison restaurée au guidage (C1)

Saut de plus de 200 m posé directement ; la relecture de trajet garde un marqueur sans animation ; l'accueil reste à plat.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 3 : C1 — ViewModel : mode caméra de session, compte à rebours annulable, caméra mémorisée

**Fichiers :**
- Modifier : `nav/NavigationViewModel.kt` :
  - imports (après la ligne 14) ;
  - `map3d` (lignes 95-96) et nouvelles propriétés juste après ;
  - `onUserGesture` et `recenter` (lignes 110-123).
- Test : `navtest/NavigationViewModelTest.kt`. Ajouter 3 tests avant l'accolade finale de la classe (ligne 249), et les imports `fr.roadly.core.map.CameraMode3D` et `fr.roadly.core.map.CameraSnapshot`.

**Interfaces :**
- Consomme : `CameraMode3D.default`, `toggled3d` (tâche 1), `CameraSnapshot`.
- Produit (dans `NavigationViewModel`) :
  - `val cameraMode: StateFlow<CameraMode3D>`, `fun toggleCameraMode()`, `fun toggleNorthUp()` ;
  - `val recenterInSeconds: StateFlow<Int?>`, `fun cancelAutoRecenter()` ;
  - `var lastCamera: CameraSnapshot?` (lecture seule), `fun rememberCamera(snapshot: CameraSnapshot?)` ;
  - `map3d` passe en `SharingStarted.Eagerly`. Son type ne change pas.

- [ ] **Étape 1 : écrire les tests qui échouent.**

Dans `navtest/NavigationViewModelTest.kt`, ajouter avant l'accolade finale de la classe :

```kotlin
    @Test fun `the auto recenter countdown ticks every second and can be cancelled`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            assertNull(vm.recenterInSeconds.value)
            vm.onUserGesture(); runCurrent()
            assertEquals(8, vm.recenterInSeconds.value)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(7, vm.recenterInSeconds.value)
            advanceTimeBy(6_000); runCurrent()
            assertEquals(1, vm.recenterInSeconds.value)
            advanceTimeBy(1_000); runCurrent()
            assertNull(vm.recenterInSeconds.value)
            assertTrue(vm.uiState.value.following)
            vm.onUserGesture(); runCurrent()
            advanceTimeBy(3_000); runCurrent()
            assertEquals(5, vm.recenterInSeconds.value)
            vm.cancelAutoRecenter(); runCurrent()
            assertNull(vm.recenterInSeconds.value)
            advanceTimeBy(10_000); runCurrent()
            assertFalse("annulé : la carte reste libre", vm.uiState.value.following)
            vm.recenter(); runCurrent()
            assertTrue(vm.uiState.value.following)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `the camera mode starts from the 3d setting and the buttons change it for the session`() = runTest {
        val vm = NavigationViewModel(session(), prefs, config); runCurrent()
        assertEquals(CameraMode3D.FOLLOW_3D, vm.cameraMode.value)
        vm.toggleCameraMode(); runCurrent()
        assertEquals(CameraMode3D.FOLLOW_2D, vm.cameraMode.value)
        vm.toggleNorthUp(); runCurrent()
        assertEquals(CameraMode3D.NORTH_UP, vm.cameraMode.value)
        vm.toggleNorthUp(); runCurrent()
        assertEquals("retour au mode d'avant", CameraMode3D.FOLLOW_2D, vm.cameraMode.value)
        vm.toggleNorthUp(); vm.toggleCameraMode(); runCurrent()
        assertEquals("depuis le nord en haut, le bouton 3D repasse en 3D", CameraMode3D.FOLLOW_3D, vm.cameraMode.value)
        prefs.setMap3dEnabled(false); runCurrent()
        assertEquals("le choix de la session prime sur le réglage", CameraMode3D.FOLLOW_3D, vm.cameraMode.value)
    }

    @Test fun `with the 3d setting off the session starts in 2d, and the last camera is remembered`() = runTest {
        prefs.setMap3dEnabled(false)
        val vm = NavigationViewModel(session(), prefs, config); runCurrent()
        assertEquals(CameraMode3D.FOLLOW_2D, vm.cameraMode.value)
        vm.rememberCamera(null)
        assertNull(vm.lastCamera)
        val snap = CameraSnapshot(48.8, 2.3, 17.0, 90.0, tilt = 58.0)
        vm.rememberCamera(snap)
        assertEquals(snap, vm.lastCamera)
    }
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q --tests "*NavigationViewModelTest*"`

Résultat attendu : ÉCHEC de compilation (`Unresolved reference: recenterInSeconds`, `cameraMode`).

- [ ] **Étape 3 : écrire l'implémentation.**

Dans `nav/NavigationViewModel.kt` :

1. Après la ligne 14 (`import fr.roadly.core.data.repository.VehicleProfileRepository`), ajouter :
```kotlin
import fr.roadly.core.map.CameraMode3D
import fr.roadly.core.map.CameraSnapshot
```
2. Lignes 95-96. Ancien :
```kotlin
    /** Réglage « Carte 3D en guidage » (l'écran y ajoute l'économiseur de batterie). */
    val map3d: StateFlow<Boolean> = prefs.map3dEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuidanceDefaults.MAP_3D)
```
Nouveau :
```kotlin
    /** Réglage « Carte 3D en guidage » : mode de départ de la caméra (lu sans attendre par les boutons, d'où `Eagerly`). */
    val map3d: StateFlow<Boolean> = prefs.map3dEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.MAP_3D)

    /** C1 : mode choisi par les boutons pour cette session ; null = mode du réglage. */
    private val cameraChoice = MutableStateFlow<CameraMode3D?>(null)
    /** Mode quitté en passant « nord en haut », rétabli au second appui. */
    private var modeBeforeNorthUp: CameraMode3D = CameraMode3D.FOLLOW_3D

    /** C1 : mode de caméra du guidage (3D, 2D, nord en haut), pour la session seulement. */
    val cameraMode: StateFlow<CameraMode3D> = combine(map3d, cameraChoice) { on, choice -> choice ?: CameraMode3D.default(on) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CameraMode3D.default(GuidanceDefaults.MAP_3D))

    private fun currentMode(): CameraMode3D = cameraChoice.value ?: CameraMode3D.default(map3d.value)

    /** Bouton 2D / 3D. */
    fun toggleCameraMode() { cameraChoice.value = currentMode().toggled3d() }

    /** Bouton « nord en haut » : bascule, puis retour au mode d'avant. */
    fun toggleNorthUp() {
        val current = currentMode()
        cameraChoice.value = if (current == CameraMode3D.NORTH_UP) modeBeforeNorthUp else { modeBeforeNorthUp = current; CameraMode3D.NORTH_UP }
    }

    /** Dernière caméra de l'écran de guidage, restaurée avec son inclinaison au retour sur l'écran. */
    var lastCamera: CameraSnapshot? = null
        private set

    fun rememberCamera(snapshot: CameraSnapshot?) { if (snapshot != null) lastCamera = snapshot }
```
3. Lignes 110-123. Ancien :
```kotlin
    /** L'utilisateur a déplacé la carte : suivi suspendu, repris automatiquement après 8 s (spec §5). */
    fun onUserGesture() {
        following.value = false
        refollowJob?.cancel()
        refollowJob = viewModelScope.launch {
            delay(RECENTER_AFTER_MS)
            following.value = true
        }
    }

    fun recenter() {
        refollowJob?.cancel()
        following.value = true
    }
```
Nouveau :
```kotlin
    private val _recenterIn = MutableStateFlow<Int?>(null)
    /** C1 : secondes avant la reprise automatique du suivi après un geste ; null = aucun compte à rebours. */
    val recenterInSeconds: StateFlow<Int?> = _recenterIn

    /** L'utilisateur a déplacé la carte : suivi suspendu, repris automatiquement après 8 s (spec §5), compte à rebours affiché. */
    fun onUserGesture() {
        following.value = false
        refollowJob?.cancel()
        refollowJob = viewModelScope.launch {
            for (s in (RECENTER_AFTER_MS / 1_000).toInt() downTo 1) {
                _recenterIn.value = s
                delay(1_000)
            }
            _recenterIn.value = null
            following.value = true
        }
    }

    /** « Annuler » : pas de reprise automatique, la carte reste libre jusqu'à « Recentrer ». */
    fun cancelAutoRecenter() {
        refollowJob?.cancel()
        _recenterIn.value = null
    }

    fun recenter() {
        refollowJob?.cancel()
        _recenterIn.value = null
        following.value = true
    }
```

- [ ] **Étape 4 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS. Le test existant `a map gesture suspends follow for 8 s…` reste vert : 8 × 1 s = 8 000 ms.

- [ ] **Étape 5 : commiter.**

```powershell
git add feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt
git commit -m @'
feat(navigation): mode caméra de session (3D, 2D, nord en haut), compte à rebours du recentrage annulable (C1)

Le réglage « Carte 3D » donne le mode de départ ; la dernière caméra du guidage est mémorisée pour le retour sur l'écran.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 4 : C1 + C3(d) — écran : effets de carte découplés, boutons 2D/3D et nord en haut, pastille de recentrage

**Fichiers :**
- Modifier : `nav/NavigationScreen.kt` :
  - imports (lignes 3-89) ;
  - `NavigationContent` : ligne 104 et lignes 124-154 ;
  - `PortraitOverlay` : fin de la colonne d'actions, après la ligne 224 ;
  - `LandscapeOverlay` : rangée du bas à droite, lignes 286-293 ;
  - `BottomBlock` : lignes 302-313 ;
  - nouvelles fonctions privées en fin de fichier.
- Modifier : `feature/navigation/src/main/res/values/strings.xml`, après la ligne 12.
- Pas de test JVM : Compose sans test instrumenté à ce stade. La vérification passe par la compilation et par la tâche 8 sur appareil.

**Interfaces :**
- Consomme :
  - tâche 1 : `NavigationCameraController.update`, `requestTransition`, `reset` ; `NavigationCameraInput` ; `CameraMode3D` ;
  - tâche 2 : `MapController.setUserLocation(fix)`, `applyGuidanceCamera(point, camera, durationMs, eased)`, `restore(snapshot, keepTilt = true)`, `cameraPosition()` ;
  - tâche 3 : `vm.cameraMode`, `toggleCameraMode`, `toggleNorthUp`, `recenterInSeconds`, `cancelAutoRecenter`, `lastCamera`, `rememberCamera`.
- Produit :
  - composables privés `GuidanceMapEffects(ui: State<NavigationUiState>, mode: State<CameraMode3D>, powerSave: State<Boolean>, landscape: Boolean, map: MapController, camera: NavigationCameraController)`, `CameraModeButtons(vm: NavigationViewModel)` et `RecenterCountdownPill(seconds: Int, onCancel: () -> Unit)` ;
  - chaînes `nav_recenter_in`, `nav_recenter_cancel`, `nav_view_2d`, `nav_view_3d`, `nav_north_up` et `nav_heading_up`.
- Aucune signature d'overlay ne change.

- [ ] **Étape 1 : ajouter les chaînes.**

Dans `feature/navigation/src/main/res/values/strings.xml`, après la ligne 12 (`<string name="nav_recenter">Recentrer</string>`) :

```xml
    <string name="nav_recenter_in">Recentrage dans %1$d s</string>
    <string name="nav_recenter_cancel">Annuler</string>
    <string name="nav_view_2d">Passer en vue 2D</string>
    <string name="nav_view_3d">Passer en vue 3D</string>
    <string name="nav_north_up">Nord en haut</string>
    <string name="nav_heading_up">Orienter la carte dans le sens de la route</string>
```

- [ ] **Étape 2 : imports de `NavigationScreen.kt`.**

1. Remplacer les lignes 79-81 :
```kotlin
import fr.roadly.core.map.GuidanceCamera
import fr.roadly.core.map.GuidanceCameraInput
import fr.roadly.core.map.GuidanceCameraPolicy
```
par :
```kotlin
import fr.roadly.core.map.CameraMode3D
import fr.roadly.core.map.MapController
import fr.roadly.core.map.NavigationCameraController
import fr.roadly.core.map.NavigationCameraInput
```
2. Ajouter :
   - `import android.os.SystemClock` après la ligne 5 ;
   - `import androidx.compose.runtime.State` et `import androidx.compose.runtime.snapshotFlow` après la ligne 45 ;
   - `import androidx.compose.material.icons.filled.Explore`, `import androidx.compose.material.icons.filled.Map`, `import androidx.compose.material.icons.filled.Navigation` et `import androidx.compose.material.icons.filled.ViewInAr` avec les autres icônes (lignes 26-34) ;
   - `import fr.roadly.core.model.Route` et `import fr.roadly.core.model.RouteProgress` après la ligne 88 ;
   - `import kotlinx.coroutines.flow.distinctUntilChanged` après la ligne 89.

- [ ] **Étape 3 : `NavigationContent` — un seul flux pour la carte (C3 d).**

Ligne 104. Ancien :
```kotlin
    val state by vm.uiState.collectAsStateWithLifecycle()
```
Nouveau :
```kotlin
    // C3 : l'état est lu deux fois — par délégation pour les overlays, par `snapshotFlow` (sans recomposition) pour la carte.
    val uiState = vm.uiState.collectAsStateWithLifecycle()
    val state by uiState
```

Lignes 124-154. Ancien :
```kotlin
    val progress = state.progress
    val following = state.following
    val threeD by vm.map3d.collectAsStateWithLifecycle()
    val powerSave by rememberPowerSaveMode()
    LaunchedEffect(threeD, powerSave, c.buildings3d) { map.setBuildings3d(threeD && !powerSave, c.buildings3d.toHex()) }
    LaunchedEffect(c.onBrand, c.brand) { map.setArrowColor(c.onBrand.toHex(), c.brand.toHex()) }
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

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val landscape = maxWidth > maxHeight
        LaunchedEffect(progress, following, approach != null, threeD, powerSave, landscape) {
            if (route == null || progress == null || !following) return@LaunchedEffect
            val next = route.maneuvers.getOrNull(progress.nextManeuverIndex) ?: return@LaunchedEffect
            val cam = GuidanceCameraPolicy.compute(
                GuidanceCameraInput(state.speedKmh, progress.bearingDeg, progress.distanceToNextManeuverM, next.type, threeD, powerSave, landscape, approaching = approach != null),
                lastCamera[0],
            )
            lastCamera[0] = cam
            map.applyGuidanceCamera(progress.snapped, cam)
        }
```
Nouveau :
```kotlin
    val cameraMode = vm.cameraMode.collectAsStateWithLifecycle()
    val powerSave = rememberPowerSaveMode()
    // Bâtiments 3D : en mode 3D seulement, et jamais en économie d'énergie.
    LaunchedEffect(cameraMode.value, powerSave.value, c.buildings3d) {
        map.setBuildings3d(cameraMode.value == CameraMode3D.FOLLOW_3D && !powerSave.value, c.buildings3d.toHex())
    }
    LaunchedEffect(c.onBrand, c.brand) { map.setArrowColor(c.onBrand.toHex(), c.brand.toHex()) }
    val approach = state.approach
    LaunchedEffect(route?.id, approach?.maneuverShapeIndex) {
        if (route != null && approach != null) map.setManeuverArrow(ManeuverArrowBuilder.build(route.geometry, approach.maneuverShapeIndex)) else map.clearManeuverArrow()
    }
    // Retour depuis la recherche : la dernière caméra du guidage revient avec son inclinaison, sans attendre la prochaine position.
    DisposableEffect(map) { onDispose { vm.rememberCamera(map.cameraPosition()) } }
    LaunchedEffect(map) { vm.lastCamera?.let { map.restore(it, keepTilt = true) } }
    val camera = remember { NavigationCameraController() }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val landscape = maxWidth > maxHeight
        GuidanceMapEffects(uiState, cameraMode, powerSave, landscape, map, camera)
```
Le reste de `NavigationContent` (lignes 155-176) est inchangé, y compris les lignes d'appel des overlays (162-166).

- [ ] **Étape 4 : boutons caméra dans les overlays (ajouts seulement).**

`PortraitOverlay`, après la ligne 224 (`if (state.phase == NavigationPhase.GUIDING) RoundIconButton(Icons.Default.AltRoute, …)`), dernière ligne de la colonne d'actions :
```kotlin
            CameraModeButtons(vm)
```
`LandscapeOverlay`, lignes 286-287. Ancien :
```kotlin
        Row(Modifier.align(Alignment.BottomEnd).padding(start = RoadlySpacing.screenEdge, bottom = RoadlySpacing.screenEdge, end = RoadlySpacing.screenEdge + RoadlySpacing.touchTargetDriving + RoadlySpacing.sm), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs), horizontalAlignment = Alignment.End) {
```
Nouveau. Les boutons vont dans la rangée du bas, faute de hauteur dans la colonne d'actions du paysage (écart 8) :
```kotlin
        Row(Modifier.align(Alignment.BottomEnd).padding(start = RoadlySpacing.screenEdge, bottom = RoadlySpacing.screenEdge, end = RoadlySpacing.screenEdge + RoadlySpacing.touchTargetDriving + RoadlySpacing.sm), verticalAlignment = Alignment.Bottom) {
            CameraModeButtons(vm)
            Spacer(Modifier.width(RoadlySpacing.s))
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs), horizontalAlignment = Alignment.End) {
```

- [ ] **Étape 5 : pastille « Recentrage dans N s · Annuler » dans `BottomBlock`.**

Lignes 302-306. Ancien :
```kotlin
        NavigationPhase.GUIDING -> {
            AnimatedVisibility(state.upcoming.isNotEmpty() || state.hazards.isNotEmpty() || state.communityAhead.isNotEmpty()) {
                HorizonStrip(items = horizonItems(state), title = stringResource(R.string.nav_horizon_title), modifier = Modifier.padding(bottom = RoadlySpacing.s))
            }
            NavInfoBar(
```
Nouveau :
```kotlin
        NavigationPhase.GUIDING -> {
            AnimatedVisibility(state.upcoming.isNotEmpty() || state.hazards.isNotEmpty() || state.communityAhead.isNotEmpty()) {
                HorizonStrip(items = horizonItems(state), title = stringResource(R.string.nav_horizon_title), modifier = Modifier.padding(bottom = RoadlySpacing.s))
            }
            val recenterIn by vm.recenterInSeconds.collectAsStateWithLifecycle()
            recenterIn?.let { RecenterCountdownPill(it, onCancel = vm::cancelAutoRecenter) }
            NavInfoBar(
```

- [ ] **Étape 6 : nouvelles fonctions privées, en fin de fichier.**

```kotlin
/** Ce que la carte du guidage reflète ; comparé pour n'agir que sur un vrai changement (position, suivi, mode, économie). */
private data class MapTick(
    val route: Route?,
    val progress: RouteProgress?,
    val speedKmh: Double?,
    val approaching: Boolean,
    val following: Boolean,
    val mode: CameraMode3D,
    val powerSave: Boolean,
)

/**
 * C3 : position, tronçon parcouru et caméra pilotés par un seul `snapshotFlow`, hors recomposition des overlays
 * (au lieu de `LaunchedEffect` relancés à chaque position). C1 : caméra lissée, transitions, recentrage adouci.
 */
@Composable
private fun GuidanceMapEffects(
    ui: State<NavigationUiState>,
    mode: State<CameraMode3D>,
    powerSave: State<Boolean>,
    landscape: Boolean,
    map: MapController,
    camera: NavigationCameraController,
) {
    LaunchedEffect(map, camera, landscape) {
        var lastProgress: RouteProgress? = null
        var lastRouteId: String? = null
        var wasFollowing = true
        snapshotFlow { ui.value.let { s -> MapTick(s.route, s.progress, s.speedKmh, s.approach != null, s.following, mode.value, powerSave.value) } }
            .distinctUntilChanged()
            .collect { t ->
                val route = t.route ?: return@collect
                val progress = t.progress ?: return@collect
                if (route.id != lastRouteId) { lastRouteId = route.id; camera.reset() }
                if (progress !== lastProgress) {
                    lastProgress = progress
                    map.setUserLocation(LocationFix(progress.snapped, progress.bearingDeg, null, 5f, 0L))
                    map.setTraversed(route.geometry, progress.segmentIndex, progress.snapped)
                }
                if (!t.following) { wasFollowing = false; return@collect }
                if (!wasFollowing) { wasFollowing = true; camera.requestTransition() }
                val next = route.maneuvers.getOrNull(progress.nextManeuverIndex) ?: return@collect
                val frame = camera.update(
                    NavigationCameraInput(
                        point = progress.snapped, bearingDeg = progress.bearingDeg, speedKmh = t.speedKmh,
                        distanceToManeuverM = progress.distanceToNextManeuverM, maneuverType = next.type, approaching = t.approaching,
                        mode = t.mode, powerSave = t.powerSave, landscape = landscape, nowMs = SystemClock.elapsedRealtime(),
                    ),
                )
                map.applyGuidanceCamera(frame.point, frame.camera, frame.durationMs, frame.eased)
            }
    }
}

/** Boutons 2D / 3D et « nord en haut » (C1), côte à côte pour ne prendre qu'une rangée. */
@Composable
private fun CameraModeButtons(vm: NavigationViewModel) {
    val mode by vm.cameraMode.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
        val is3d = mode == CameraMode3D.FOLLOW_3D
        RoundIconButton(
            if (is3d) Icons.Default.Map else Icons.Default.ViewInAr,
            stringResource(if (is3d) R.string.nav_view_2d else R.string.nav_view_3d),
            onClick = vm::toggleCameraMode,
        )
        val northUp = mode == CameraMode3D.NORTH_UP
        RoundIconButton(
            if (northUp) Icons.Default.Navigation else Icons.Default.Explore,
            stringResource(if (northUp) R.string.nav_heading_up else R.string.nav_north_up),
            onClick = vm::toggleNorthUp,
            style = if (northUp) RoundButtonStyle.Brand else RoundButtonStyle.Glass,
        )
    }
}

/** « Recentrage dans N s · Annuler » au-dessus de la barre d'information, pendant la reprise automatique (C1). */
@Composable
private fun RecenterCountdownPill(seconds: Int, onCancel: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.padding(bottom = RoadlySpacing.s), shape = RoadlyShapes.Chip, contentPadding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_recenter_in, seconds), style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassButton(stringResource(R.string.nav_recenter_cancel), onClick = onCancel, style = GlassButtonStyle.Ghost)
        }
    }
}
```

- [ ] **Étape 7 : vérifier la compilation et les tests.**

Lancer : `.\gradlew.bat :feature:navigation:testDebugUnitTest :app:assembleDebug --offline -q`

Résultat attendu : SUCCÈS et BUILD SUCCESSFUL. Aucun avertissement `Unused` sur `GuidanceCamera` ni `GuidanceCameraPolicy` : leurs imports sont retirés.

- [ ] **Étape 8 : commiter.**

```powershell
git add feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt feature/navigation/src/main/res/values/strings.xml
git commit -m @'
feat(navigation): carte pilotée par un seul flux, boutons 2D/3D et nord en haut, « Recentrage dans N s · Annuler » (C1, C3)

Position, tronçon parcouru et caméra ne relancent plus d'effet à chaque position ; overlays et signatures inchangés.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 5 : C2 — style Roadly embarqué (script PowerShell, assets, test) et styles par défaut `asset://`

**Fichiers :**
- Créer : `core/map/tools/upstream/dark.json` et `core/map/tools/upstream/positron.json` (téléchargés).
- Créer : `core/map/tools/build-roadly-styles.ps1`.
- Créer (générés) : `core/map/src/main/assets/styles/roadly-dark.json` et `roadly-light.json`.
- Modifier : `core/data/src/main/java/fr/roadly/core/data/config/ProviderConfig.kt`, lignes 12-27.
- Test : `maptest/RoadlyStyleAssetTest.kt` (nouveau) ; `core/data/src/test/java/fr/roadly/core/data/config/ProviderConfigTest.kt` (nouveau).

**Interfaces :**
- Consomme : `MapController.setStyle(url)` (`Style.Builder().fromUri`, qui accepte `asset://`), `installBuildings` (source `openmaptiles`, conservée).
- Produit :
  - `ProviderConfig.ASSET_SCHEME = "asset://"` ;
  - `ProviderConfig.DEFAULT.mapStyleDarkUrl = "asset://styles/roadly-dark.json"` et `mapStyleLightUrl = "asset://styles/roadly-light.json"` ;
  - `normalized()` accepte `asset://<chemin>` pour les styles ;
  - identifiants de calques du style : `roadly_road_tunnel`, `roadly_road_minor`, `roadly_road_bridge_casing`, `roadly_road_major_casing`, `roadly_road_major`, `roadly_road_motorway_casing`, `roadly_road_motorway`, `roadly_poi_major`, `roadly_poi_other` ;
  - `metadata.roadly.version = "1"`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`maptest/RoadlyStyleAssetTest.kt`. Le répertoire de travail des tests unitaires AGP est le dossier du module :

```kotlin
package fr.roadly.core.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** C2 : styles Roadly embarqués, générés par `tools/build-roadly-styles.ps1`, lus depuis le dossier du module. */
class RoadlyStyleAssetTest {
    private val names = listOf("roadly-dark.json", "roadly-light.json")
    private fun file(name: String) = File("src/main/assets/styles/$name")
    private fun style(name: String): JsonObject = Json.parseToJsonElement(file(name).readText()).jsonObject
    private fun JsonObject.layers(): List<JsonObject> = getValue("layers").jsonArray.map { it.jsonObject }
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content
    private fun List<JsonObject>.byId(id: String): JsonObject = first { it.str("id") == id }

    private fun JsonElement.isZoomCall() = this is JsonArray && size == 1 && (this[0] as? JsonPrimitive)?.content == "zoom"
    private fun JsonElement.op(): String? = ((this as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** MapLibre n'accepte ["zoom"] que comme entrée d'un interpolate ou d'un step placé au premier niveau. */
    private fun zoomOnlyAtTop(value: JsonElement): Boolean {
        val input = when (value.op()) { "interpolate" -> 2; "step" -> 1; else -> -1 }
        if (input >= 0 && (value as JsonArray)[input].isZoomCall()) return value.withIndex().all { (i, e) -> i == input || noZoom(e) }
        return noZoom(value)
    }

    private fun noZoom(e: JsonElement): Boolean = when (e) {
        is JsonArray -> !e.isZoomCall() && e.all { noZoom(it) }
        is JsonObject -> e.values.all { noZoom(it) }
        else -> true
    }

    @Test fun `both styles keep the OpenFreeMap sources, glyphs and sprite`() = names.forEach { n ->
        val s = style(n)
        assertEquals(8, s.getValue("version").jsonPrimitive.content.toInt())
        val omt = s.getValue("sources").jsonObject.getValue("openmaptiles").jsonObject
        assertEquals("vector", omt.str("type"))
        assertTrue(n, omt.str("url")!!.startsWith("https://tiles.openfreemap.org/"))
        assertTrue(n, s.str("glyphs")!!.startsWith("https://tiles.openfreemap.org/fonts/"))
        assertTrue(n, s.str("sprite")!!.startsWith("https://tiles.openfreemap.org/sprites/"))
    }

    @Test fun `metadata carries the roadly style version`() = names.forEach { n ->
        assertEquals(n, "1", style(n).getValue("metadata").jsonObject.getValue("roadly").jsonObject.str("version"))
    }

    @Test fun `buildings come from the tiles`() = names.forEach { n ->
        assertTrue(n, style(n).layers().any { it.str("source-layer") == "building" && it.str("source") == "openmaptiles" })
    }

    @Test fun `road layers are stacked from tunnels to motorways and replace the original ones`() = names.forEach { n ->
        val ids = style(n).layers().map { it.str("id") }
        val stack = listOf(
            "roadly_road_tunnel", "roadly_road_minor", "roadly_road_bridge_casing", "roadly_road_major_casing",
            "roadly_road_major", "roadly_road_motorway_casing", "roadly_road_motorway",
        )
        val idx = stack.map { ids.indexOf(it) }
        assertTrue("$n : $idx", idx.none { it < 0 } && idx == idx.sorted())
        assertTrue(n, ids.none { it == "highway_minor" || it == "highway_motorway_inner" || it == "tunnel_motorway_inner" })
    }

    @Test fun `tunnels are dashed and translucent`() = names.forEach { n ->
        val t = style(n).layers().byId("roadly_road_tunnel")
        assertTrue(n, t.getValue("filter").toString().contains("\"tunnel\""))
        val paint = t.getValue("paint").jsonObject
        assertEquals(n, "[2,2]", paint.getValue("line-dasharray").toString())
        assertEquals(0.6, paint.getValue("line-opacity").jsonPrimitive.double, 1e-9)
    }

    @Test fun `ramps are narrower and motorways 30 percent wider than the original`() = names.forEach { n ->
        val width = style(n).layers().byId("roadly_road_motorway").getValue("paint").jsonObject.getValue("line-width").toString()
        assertTrue(n, width.contains("\"ramp\""))
        assertTrue(n, width.contains("39"))   // 30 × 1,3 au zoom 20
    }

    @Test fun `zoom is only used as the input of a top-level interpolate or step`() = names.forEach { n ->
        style(n).layers().forEach { l ->
            listOf("paint", "layout").forEach { group ->
                (l[group] as? JsonObject)?.forEach { (key, value) -> assertTrue("$n ${l.str("id")} $group.$key", zoomOnlyAtTop(value)) }
            }
        }
    }

    @Test fun `arrays survived the PowerShell conversion`() = names.forEach { n ->
        assertFalse(n, file(n).readText().contains("\"Count\":"))
        style(n).layers().forEach { l ->
            (l["layout"] as? JsonObject)?.get("text-font")?.let { assertTrue("$n ${l.str("id")}", it is JsonArray) }
            l["filter"]?.let { assertTrue("$n ${l.str("id")}", it is JsonArray) }
        }
    }

    @Test fun `layer ids are unique and every source exists`() = names.forEach { n ->
        val s = style(n)
        val ids = s.layers().map { it.str("id") }
        assertEquals(n, ids.size, ids.toSet().size)
        val sources = s.getValue("sources").jsonObject.keys
        s.layers().mapNotNull { it.str("source") }.forEach { assertTrue("$n $it", it in sources) }
    }

    @Test fun `street names are sparser and points of interest come from the tiles`() = names.forEach { n ->
        val layers = style(n).layers()
        val streetNames = layers.filter { it.str("source-layer") == "transportation_name" && it.str("id")!!.contains("name") }
        assertTrue(n, streetNames.isNotEmpty())
        streetNames.forEach { assertEquals("$n ${it.str("id")}", 400, it.getValue("layout").jsonObject.getValue("symbol-spacing").jsonPrimitive.content.toInt()) }
        val major = layers.byId("roadly_poi_major")
        assertEquals("poi", major.str("source-layer"))
        assertEquals(14.0, major.getValue("minzoom").jsonPrimitive.double, 1e-9)
        assertEquals(16.0, layers.byId("roadly_poi_other").getValue("minzoom").jsonPrimitive.double, 1e-9)
    }
}
```

`core/data/src/test/java/fr/roadly/core/data/config/ProviderConfigTest.kt` :

```kotlin
package fr.roadly.core.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderConfigTest {
    @Test fun `the default map styles are the embedded Roadly styles`() {
        assertEquals("asset://styles/roadly-dark.json", ProviderConfig.DEFAULT.mapStyleDarkUrl)
        assertEquals("asset://styles/roadly-light.json", ProviderConfig.DEFAULT.mapStyleLightUrl)
        assertEquals(ProviderConfig.DEFAULT, ProviderConfig.DEFAULT.normalized())
    }

    @Test fun `an external https style stays possible`() {
        val c = ProviderConfig.DEFAULT.copy(mapStyleDarkUrl = " https://tiles.openfreemap.org/styles/dark/ ").normalized()
        assertEquals("https://tiles.openfreemap.org/styles/dark", c.mapStyleDarkUrl)
    }

    @Test fun `asset uris are refused for servers and empty asset paths for styles`() {
        assertThrows(IllegalArgumentException::class.java) { ProviderConfig.DEFAULT.copy(valhallaBaseUrl = "asset://valhalla").normalized() }
        assertThrows(IllegalArgumentException::class.java) { ProviderConfig.DEFAULT.copy(mapStyleLightUrl = "asset://").normalized() }
    }
}
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*RoadlyStyleAssetTest*"`

Résultat attendu : ÉCHEC (`FileNotFoundException: src\main\assets\styles\roadly-dark.json`).

Lancer : `.\gradlew.bat :core:data:testDebugUnitTest --offline -q --tests "*ProviderConfigTest*"`

Résultat attendu : ÉCHEC (`expected asset://styles/roadly-dark.json but was https://tiles.openfreemap.org/styles/dark`).

- [ ] **Étape 3 : télécharger les styles amont (dossier neuf, données JSON uniquement).**

```powershell
New-Item -ItemType Directory -Force core\map\tools\upstream | Out-Null
curl.exe -sSfL "https://tiles.openfreemap.org/styles/dark" -o core\map\tools\upstream\dark.json
curl.exe -sSfL "https://tiles.openfreemap.org/styles/positron" -o core\map\tools\upstream\positron.json
(Get-Content -Raw core\map\tools\upstream\dark.json | ConvertFrom-Json).layers.Count
(Get-Content -Raw core\map\tools\upstream\positron.json | ConvertFrom-Json).layers.Count
```

Résultat attendu au 2026-10-31 : `47` puis `55`. Si les nombres diffèrent, le script vérifie lui-même les calques routiers attendus et s'arrête avec un message explicite si le style amont a changé de forme.

- [ ] **Étape 4 : écrire le script `core/map/tools/build-roadly-styles.ps1`.**

```powershell
<#
.SYNOPSIS
  Génère les styles de carte Roadly (core/map/src/main/assets/styles/roadly-dark.json et roadly-light.json)
  depuis les styles OpenFreeMap enregistrés dans core/map/tools/upstream (dark.json, positron.json).
.DESCRIPTION
  Déterministe : mêmes fichiers amont → mêmes assets. Sources, glyphes et sprite restent les URL OpenFreeMap ; tout ce qui
  est dessiné vient des tuiles OpenMapTiles (rien d'inventé). Modifications :
  1. calques routiers amont remplacés par une pile Roadly (tunnels, rues, liseré des ponts, routes principales, autoroutes) ;
  2. tunnels en pointillé à 60 %, ponts avec un liseré plus sombre ; 3. bretelles (ramp = 1) à 75 % de leur classe ;
  4. noms de rues espacés (400) et 1 px plus petits dès le zoom 15 ; points d'intérêt majeurs dès le zoom 14, autres dès 16 ;
  5. eau et parcs teintés vers la palette Roadly ; 6. bâtiments conservés (extrusion ajoutée à l'exécution par MapController).
  Lancer depuis la racine du dépôt : powershell -NoProfile -ExecutionPolicy Bypass -File core\map\tools\build-roadly-styles.ps1
#>
$ErrorActionPreference = 'Stop'
# PowerShell 5.1 : sans cette ligne, ConvertTo-Json écrit les tableaux imbriqués sous la forme {"value":[…],"Count":n}.
Remove-TypeData System.Array -ErrorAction SilentlyContinue

$RoadlyStyleVersion = '1'
$toolsDir = $PSScriptRoot
$assetsDir = Join-Path (Split-Path -Parent $toolsDir) 'src\main\assets\styles'
New-Item -ItemType Directory -Force -Path $assetsDir | Out-Null

# ── Palettes Roadly (couleurs CSS du style) ──
$palettes = @{
    dark  = [ordered]@{
        motorway = '#4A4560'; trunk = '#403B55'; primary = '#36324A'; secondary = '#2F2B40'; tertiary = '#2F2B40'; minor = '#28243A'
        casing = '#15121F'; bridgeCasing = '#0B0912'; tunnel = '#28243A'; water = '#1B2436'; park = '#1F2A24'
        poiText = '#A8A2BC'; poiHalo = '#0E0B14'
    }
    light = [ordered]@{
        motorway = '#B8B1CC'; trunk = '#C4BED6'; primary = '#D2CDE0'; secondary = '#E0DCEA'; tertiary = '#E0DCEA'; minor = '#E6E4EA'
        casing = '#C9C5D2'; bridgeCasing = '#9C97A8'; tunnel = '#D8D5DF'; water = '#C7D3DD'; park = '#DDE6DA'
        poiText = '#5A5470'; poiHalo = '#FFFFFF'
    }
}

# ── Expressions communes (aucun opérateur dans les listes : en PowerShell la virgule lie plus fort que +) ──
$isLine          = @('match', @('geometry-type'), @('LineString', 'MultiLineString'), $true, $false)
$isTunnel        = @('==', @('get', 'brunnel'), 'tunnel')
$notTunnel       = @('!=', @('get', 'brunnel'), 'tunnel')
$isBridge        = @('==', @('get', 'brunnel'), 'bridge')
$majorClasses    = @('trunk', 'primary', 'secondary', 'tertiary')
$minorClasses    = @('minor', 'service', 'track')
$bridgeClasses   = @('motorway', 'trunk', 'primary', 'secondary', 'tertiary')
$allClasses      = @('motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor', 'service', 'track')
$poiMajorClasses = @('hospital', 'fuel', 'parking', 'railway')
# Bretelles (ramp = 1) : 75 % de la largeur de leur classe.
$ramp         = @('case', @('==', @('get', 'ramp'), 1), 0.75, 1)
# Routes principales : voie rapide > nationale > départementale > locale.
$majorFactor  = @('match', @('get', 'class'), 'trunk', 1.15, 'primary', 1.0, 'secondary', 0.85, 0.7)
# Rues 80 %, voies de service et chemins 55 % de la largeur d'origine.
$minorFactor  = @('match', @('get', 'class'), 'minor', 0.8, 0.55)
# Tunnels : largeur selon la classe.
$classFactor  = @('match', @('get', 'class'), 'motorway', 1.3, 'trunk', 1.15, 'primary', 1.0, 'secondary', 0.85, 'tertiary', 0.7, 0.55)
# Liseré des ponts : plus large que le liseré ordinaire de chaque classe (autoroute : 57 > 52 au zoom 20).
$bridgeFactor = @('match', @('get', 'class'), 'motorway', 1.9, 'trunk', 1.15, 'primary', 1.0, 'secondary', 0.85, 0.7)

function New-RoadLine([string]$Id, [double]$MinZoom, [string]$Cap, [object[]]$Filter, [object]$Color, [object[]]$Width) {
    return [ordered]@{
        id = $Id; type = 'line'; source = 'openmaptiles'; 'source-layer' = 'transportation'; minzoom = $MinZoom; filter = $Filter
        layout = [ordered]@{ 'line-cap' = $Cap; 'line-join' = 'round' }
        paint = [ordered]@{ 'line-color' = $Color; 'line-width' = $Width }
    }
}

# Pile routière Roadly, du dessous vers le dessus ; largeurs : interpolate sur le zoom au premier niveau (exigence MapLibre).
function New-RoadLayers($p) {
    $majorColor = @('match', @('get', 'class'), 'trunk', $p.trunk, 'primary', $p.primary, 'secondary', $p.secondary, $p.tertiary)
    $layers = New-Object System.Collections.ArrayList

    $f = @('all', $isLine, $isTunnel, @('match', @('get', 'class'), $allClasses, $true, $false))
    $w = @('interpolate', @('exponential', 1.3), @('zoom'), 10, @('*', 2, $classFactor), 20, @('*', 20, $classFactor))
    $l = New-RoadLine 'roadly_road_tunnel' 10 'butt' $f $p.tunnel $w
    $l.paint['line-dasharray'] = @(2, 2)
    $l.paint['line-opacity'] = 0.6
    [void]$layers.Add($l)

    $f = @('all', $isLine, $notTunnel, @('match', @('get', 'class'), $minorClasses, $true, $false))
    $w = @('interpolate', @('exponential', 1.55), @('zoom'), 13, @('*', 1.8, $minorFactor), 20, @('*', 20, $minorFactor))
    [void]$layers.Add((New-RoadLine 'roadly_road_minor' 8 'round' $f $p.minor $w))

    $f = @('all', $isLine, $isBridge, @('match', @('get', 'class'), $bridgeClasses, $true, $false))
    $w = @('interpolate', @('exponential', 1.3), @('zoom'), 10, @('*', 4, $bridgeFactor, $ramp), 20, @('*', 30, $bridgeFactor, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_bridge_casing' 11 'butt' $f $p.bridgeCasing $w))

    $f = @('all', $isLine, $notTunnel, @('match', @('get', 'class'), $majorClasses, $true, $false))
    $w = @('interpolate', @('exponential', 1.3), @('zoom'), 10, @('*', 3, $majorFactor, $ramp), 20, @('*', 23, $majorFactor, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_major_casing' 11 'butt' $f $p.casing $w))
    $w = @('interpolate', @('exponential', 1.3), @('zoom'), 10, @('*', 2, $majorFactor, $ramp), 20, @('*', 20, $majorFactor, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_major' 11 'round' $f $majorColor $w))

    $f = @('all', $isLine, $notTunnel, @('==', @('get', 'class'), 'motorway'))
    $w = @('interpolate', @('exponential', 1.4), @('zoom'), 6, @('*', 3.9, $ramp), 20, @('*', 52, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_motorway_casing' 6 'butt' $f $p.casing $w))
    $w = @('interpolate', @('exponential', 1.4), @('zoom'), 6, @('*', 1.7, $ramp), 20, @('*', 39, $ramp))
    [void]$layers.Add((New-RoadLine 'roadly_road_motorway' 6 'round' $f $p.motorway $w))
    return , $layers
}

# Points d'intérêt des tuiles (source-layer poi) : hôpitaux, carburant, parkings et gares dès le zoom 14 (icônes du sprite ofm), le reste dès 16.
function New-PoiLayers($p) {
    $text = @('coalesce', @('get', 'name:fr'), @('get', 'name'))
    $font = @('Noto Sans Regular')
    $major = [ordered]@{
        id = 'roadly_poi_major'; type = 'symbol'; source = 'openmaptiles'; 'source-layer' = 'poi'; minzoom = 14
        filter = @('match', @('get', 'class'), $poiMajorClasses, $true, $false)
        layout = [ordered]@{ 'icon-image' = @('get', 'class'); 'text-field' = $text; 'text-font' = $font; 'text-size' = 11; 'text-anchor' = 'top'; 'text-offset' = @(0, 1.1); 'text-optional' = $true }
        paint = [ordered]@{ 'text-color' = $p.poiText; 'text-halo-color' = $p.poiHalo; 'text-halo-width' = 1 }
    }
    $other = [ordered]@{
        id = 'roadly_poi_other'; type = 'symbol'; source = 'openmaptiles'; 'source-layer' = 'poi'; minzoom = 16
        filter = @('match', @('get', 'class'), $poiMajorClasses, $false, $true)
        layout = [ordered]@{ 'text-field' = $text; 'text-font' = $font; 'text-size' = 10; 'text-max-width' = 8 }
        paint = [ordered]@{ 'text-color' = $p.poiText; 'text-halo-color' = $p.poiHalo; 'text-halo-width' = 1 }
    }
    return , @($major, $other)
}

$roadIds = @(
    'highway_minor', 'highway_major_casing', 'highway_major_inner', 'highway_motorway_casing', 'highway_motorway_inner',
    'highway_motorway_bridge_casing', 'highway_motorway_bridge_inner', 'tunnel_motorway_casing', 'tunnel_motorway_inner'
)
$variants = @(
    [ordered]@{ key = 'dark';  upstream = 'dark.json';     asset = 'roadly-dark.json';  name = 'Roadly sombre' },
    [ordered]@{ key = 'light'; upstream = 'positron.json'; asset = 'roadly-light.json'; name = 'Roadly clair' }
)

foreach ($v in $variants) {
    $p = $palettes[$v.key]
    $style = Get-Content -Raw -Encoding UTF8 (Join-Path $toolsDir "upstream\$($v.upstream)") | ConvertFrom-Json
    $out = New-Object System.Collections.ArrayList
    $removed = 0; $roadsAdded = $false; $poiAdded = $false
    foreach ($l in $style.layers) {
        if ($roadIds -contains $l.id) {
            $removed++
            if (-not $roadsAdded) { foreach ($r in (New-RoadLayers $p)) { [void]$out.Add($r) }; $roadsAdded = $true }
            continue
        }
        if (-not $poiAdded -and $l.'source-layer' -eq 'place') { foreach ($r in (New-PoiLayers $p)) { [void]$out.Add($r) }; $poiAdded = $true }
        if ($l.type -eq 'fill' -and $l.'source-layer' -eq 'water') { $l.paint.'fill-color' = $p.water }
        if ($l.type -eq 'fill' -and ($l.'source-layer' -eq 'park' -or $l.id -eq 'landuse_park')) { $l.paint.'fill-color' = $p.park }
        if ($l.id -eq 'highway_major_subtle') { $l.paint.'line-color' = $p.primary }
        if ($l.id -eq 'highway_motorway_subtle') { $l.paint.'line-color' = $p.motorway }
        if ($l.type -eq 'symbol' -and $l.'source-layer' -eq 'transportation_name' -and $l.id -match 'name') {
            $l.layout | Add-Member -Force -NotePropertyName 'symbol-spacing' -NotePropertyValue 400
            $ts = $l.layout.'text-size'
            if ($ts -is [System.Array]) {
                $lastZoom = [double]$ts[$ts.Count - 2]; $lastSize = [double]$ts[$ts.Count - 1]; $smaller = $lastSize - 1
                if ($ts[0] -eq 'interpolate' -and $lastZoom -lt 15) { $l.layout.'text-size' = @($ts) + @(15, $smaller) }
            } elseif ($null -ne $ts) {
                $size = [double]$ts; $smaller = $size - 1
                $l.layout.'text-size' = @('step', @('zoom'), $size, 15, $smaller)
            }
        }
        [void]$out.Add($l)
    }
    if ($removed -lt 5 -or -not $roadsAdded -or -not $poiAdded) {
        throw "Style amont $($v.upstream) inattendu : $removed calques routiers retirés, points d'intérêt ajoutés : $poiAdded"
    }
    $style.layers = $out.ToArray()
    $style | Add-Member -Force -NotePropertyName name -NotePropertyValue $v.name
    $meta = [ordered]@{ roadly = [ordered]@{ version = $RoadlyStyleVersion; base = "openfreemap/$($v.upstream)"; generator = 'core/map/tools/build-roadly-styles.ps1' } }
    $style | Add-Member -Force -NotePropertyName metadata -NotePropertyValue $meta
    $json = $style | ConvertTo-Json -Depth 50 -Compress
    [System.IO.File]::WriteAllText((Join-Path $assetsDir $v.asset), $json, (New-Object System.Text.UTF8Encoding($false)))
    '{0} : {1} calques, {2} calques routiers remplacés par 7, 2 calques de points d''intérêt ajoutés' -f $v.asset, $out.Count, $removed
}
```

- [ ] **Étape 5 : générer les assets.**

Lancer : `powershell -NoProfile -ExecutionPolicy Bypass -File core\map\tools\build-roadly-styles.ps1`

Résultat attendu, avec les fichiers amont du 2026-10-31 :
```
roadly-dark.json : 51 calques, 5 calques routiers remplacés par 7, 2 calques de points d'intérêt ajoutés
roadly-light.json : 55 calques, 9 calques routiers remplacés par 7, 2 calques de points d'intérêt ajoutés
```
Relancer une seconde fois : `git status` ne doit montrer aucune différence dans `core/map/src/main/assets/styles/` (déterminisme).

- [ ] **Étape 6 : styles par défaut et normalisation.**

`ProviderConfig.kt`, lignes 12-27. Ancien :
```kotlin
    companion object {
        val DEFAULT = ProviderConfig(
            banBaseUrl = "https://api-adresse.data.gouv.fr",
            photonBaseUrl = "https://photon.komoot.io",
            valhallaBaseUrl = "https://valhalla1.openstreetmap.de",
            mapStyleLightUrl = "https://tiles.openfreemap.org/styles/positron",
            mapStyleDarkUrl = "https://tiles.openfreemap.org/styles/dark",
        )
    }
}

/** Normalise chaque URL (espaces et « / » finaux retirés) ; lève [IllegalArgumentException] (message = URL fautive) si l'une n'est pas une URL http(s) valide. */
fun ProviderConfig.normalized(): ProviderConfig {
    fun String.norm(): String = trim().trimEnd('/').also { require(it.toHttpUrlOrNull() != null) { it } }
    return ProviderConfig(banBaseUrl.norm(), photonBaseUrl.norm(), valhallaBaseUrl.norm(), mapStyleLightUrl.norm(), mapStyleDarkUrl.norm())
}
```
Nouveau :
```kotlin
    companion object {
        /** Schéma des styles embarqués (assets de `core/map`), lu par MapLibre (`Style.Builder().fromUri`). */
        const val ASSET_SCHEME = "asset://"

        /** C2 : styles Roadly embarqués ; une URL http(s) de style externe reste possible (Réglages → Fournisseurs). */
        val DEFAULT = ProviderConfig(
            banBaseUrl = "https://api-adresse.data.gouv.fr",
            photonBaseUrl = "https://photon.komoot.io",
            valhallaBaseUrl = "https://valhalla1.openstreetmap.de",
            mapStyleLightUrl = "asset://styles/roadly-light.json",
            mapStyleDarkUrl = "asset://styles/roadly-dark.json",
        )
    }
}

/**
 * Normalise chaque URL (espaces et « / » finaux retirés) ; lève [IllegalArgumentException] (message = URL fautive) si l'une n'est pas valide :
 * http(s) pour les serveurs, http(s) ou `asset://<chemin>` pour les deux styles de carte.
 */
fun ProviderConfig.normalized(): ProviderConfig {
    fun String.norm(): String = trim().trimEnd('/').also { require(it.toHttpUrlOrNull() != null) { it } }
    fun String.normStyle(): String {
        val s = trim()
        if (!s.startsWith(ProviderConfig.ASSET_SCHEME)) return s.norm()
        require(s.length > ProviderConfig.ASSET_SCHEME.length && !s.endsWith("/")) { s }
        return s
    }
    return ProviderConfig(banBaseUrl.norm(), photonBaseUrl.norm(), valhallaBaseUrl.norm(), mapStyleLightUrl.normStyle(), mapStyleDarkUrl.normStyle())
}
```

- [ ] **Étape 7 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:navigation:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS. Points à surveiller :
- `SettingsViewModelTest.valid provider urls are normalised and saved` passe grâce à `normStyle` ;
- `NavigationViewModelTest` compare toujours `ProviderConfig.DEFAULT.mapStyleDarkUrl`.

- [ ] **Étape 8 : commiter.**

```powershell
git add core/map/tools/upstream/dark.json core/map/tools/upstream/positron.json core/map/tools/build-roadly-styles.ps1 core/map/src/main/assets/styles/roadly-dark.json core/map/src/main/assets/styles/roadly-light.json core/map/src/test/java/fr/roadly/core/map/RoadlyStyleAssetTest.kt core/data/src/main/java/fr/roadly/core/data/config/ProviderConfig.kt core/data/src/test/java/fr/roadly/core/data/config/ProviderConfigTest.kt
git commit -m @'
feat(carte): style Roadly sombre et clair embarqué — hiérarchie routière, tunnels, ponts, bretelles, étiquettes allégées (C2)

Généré par un script PowerShell déterministe depuis les styles OpenFreeMap figés au 2026-10-31 ; sources, glyphes et sprite distants
conservés ; points d'intérêt lus dans les tuiles ; URL de style externe toujours possible.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 6 : C2 — niveau de détail de la carte (réglage, réduction automatique à grande vitesse)

**Fichiers :**
- Créer : `core/model/src/main/java/fr/roadly/core/model/MapDetail.kt`, `map/MapDetailPolicy.kt`.
- Modifier : `core/data/.../repository/UserPreferencesRepository.kt`, ligne 11 (import), 32 (défaut), 71 (interface), 95 (clé) et 136 (implémentation).
- Modifier : `core/data/.../testing/FakeUserPreferencesRepository.kt`, ligne 7 (import) et ligne 49.
- Modifier : `map/MapController.kt`. Imports, champs, `loadStyle` (lignes 120-123), `replayState` (ligne 214) et nouvelle `applyDetail`. Dans `map/MapLayers.kt`, `MapIds` (ligne 42).
- Modifier : `nav/NavigationViewModel.kt`, imports et propriété `mapDetail` après `rememberCamera` (tâche 3).
- Modifier : `nav/NavigationScreen.kt`, après l'effet de la flèche (tâche 4).
- Modifier : `feature/settings/.../SettingsViewModel.kt` (lignes 91 et 137), `SettingsScreen.kt` (lignes 144-145, 290-312, imports) et `feature/settings/src/main/res/values/strings.xml` (après la ligne 28).
- Test : `maptest/MapDetailPolicyTest.kt` (nouveau). Ajouts dans `core/data/src/test/.../DataStoreUserPreferencesRepositoryTest.kt` (ligne 31 et un test), dans `navtest/NavigationViewModelTest.kt` et dans `feature/settings/src/test/.../SettingsViewModelTest.kt`.

**Interfaces :**
- Consomme : `NavigationState.Guiding.speedKmh` (vitesse brute du dernier fix), `SymbolLayer.getSourceLayer()`, `getFilter()`, `setFilter()`.
- Produit :
  - `enum class MapDetail { NORMAL, REDUCED }` (package `fr.roadly.core.model`) ;
  - `object MapDetailPolicy { const val AUTO_REDUCE_ABOVE_KMH = 90.0; const val AUTO_RESTORE_BELOW_KMH = 80.0; fun effective(setting: MapDetail, speedKmh: Double?, current: MapDetail): MapDetail }` ;
  - `UserPreferencesRepository.mapDetail: Flow<MapDetail>` et `suspend fun setMapDetail(value: MapDetail)` ; `GuidanceDefaults.MAP_DETAIL = MapDetail.NORMAL` ; `FakeUserPreferencesRepository.detail` ;
  - `MapController.applyDetail(level: MapDetail)` ; `MapIds.STYLE_LYR_POI`, `STYLE_LYR_TRANSPORTATION_NAME` ;
  - `NavigationViewModel.mapDetail: StateFlow<MapDetail>` ;
  - `SettingsViewModel.mapDetail: StateFlow<MapDetail>` et `fun setMapDetail(value: MapDetail)`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`maptest/MapDetailPolicyTest.kt` :

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.MapDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class MapDetailPolicyTest {
    @Test fun `reduced setting always wins`() {
        assertEquals(MapDetail.REDUCED, MapDetailPolicy.effective(MapDetail.REDUCED, 30.0, MapDetail.NORMAL))
        assertEquals(MapDetail.REDUCED, MapDetailPolicy.effective(MapDetail.REDUCED, null, MapDetail.NORMAL))
    }

    @Test fun `normal is reduced above 90 km per h and restored below 80 only`() {
        var d = MapDetail.NORMAL
        val steps = listOf(50.0 to MapDetail.NORMAL, 95.0 to MapDetail.REDUCED, 85.0 to MapDetail.REDUCED, 80.0 to MapDetail.REDUCED, 79.0 to MapDetail.NORMAL, 89.0 to MapDetail.NORMAL)
        for ((speed, expected) in steps) {
            d = MapDetailPolicy.effective(MapDetail.NORMAL, speed, d)
            assertEquals("à $speed km/h", expected, d)
        }
    }

    @Test fun `an unknown speed keeps the current level`() {
        assertEquals(MapDetail.REDUCED, MapDetailPolicy.effective(MapDetail.NORMAL, null, MapDetail.REDUCED))
        assertEquals(MapDetail.NORMAL, MapDetailPolicy.effective(MapDetail.NORMAL, null, MapDetail.NORMAL))
    }
}
```

`DataStoreUserPreferencesRepositoryTest.kt` :
1. Remplacer la ligne 31 :
```kotlin
    private val repo = DataStoreUserPreferencesRepository(InMemoryPreferencesDataStore())
```
par :
```kotlin
    private val store = InMemoryPreferencesDataStore()
    private val repo = DataStoreUserPreferencesRepository(store)
```
2. Ajouter les imports `androidx.datastore.preferences.core.edit`, `androidx.datastore.preferences.core.stringPreferencesKey` et `fr.roadly.core.model.MapDetail`.
3. Ajouter ce test avant l'accolade finale :
```kotlin
    @Test fun `map detail defaults to normal, persists and ignores unknown values`() = runTest {
        assertEquals(MapDetail.NORMAL, repo.mapDetail.first())
        repo.setMapDetail(MapDetail.REDUCED)
        assertEquals(MapDetail.REDUCED, repo.mapDetail.first())
        store.edit { it[stringPreferencesKey("map.detail")] = "ULTRA" }
        assertEquals(MapDetail.NORMAL, repo.mapDetail.first())
    }
```

`navtest/NavigationViewModelTest.kt`. Ajouter l'import `fr.roadly.core.model.MapDetail`, puis ce test :
```kotlin
    @Test fun `map detail follows the setting and is reduced above 90 km per h`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.mapDetail.test {
            s.start(option, RoutePreferences()); runCurrent()
            location.emit(LocationFix(tracker.pointAt(300.0), 90f, 10f, 5f, 0L)); runCurrent()   // 36 km/h
            assertEquals(MapDetail.NORMAL, vm.mapDetail.value)
            prefs.setMapDetail(MapDetail.REDUCED); runCurrent()
            assertEquals(MapDetail.REDUCED, vm.mapDetail.value)
            prefs.setMapDetail(MapDetail.NORMAL); runCurrent()
            assertEquals(MapDetail.NORMAL, vm.mapDetail.value)
            location.emit(LocationFix(tracker.pointAt(330.0), 90f, (100 / 3.6).toFloat(), 5f, 0L)); runCurrent()   // 100 km/h
            assertEquals(MapDetail.REDUCED, vm.mapDetail.value)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

`SettingsViewModelTest.kt`. Ajouter l'import `fr.roadly.core.model.MapDetail`, puis ce test :
```kotlin
    @Test fun `map detail level is exposed and persisted`() = runTest {
        val vm = vm()
        vm.mapDetail.test {
            assertEquals(MapDetail.NORMAL, awaitItem())
            vm.setMapDetail(MapDetail.REDUCED); advanceUntilIdle()
            assertEquals(MapDetail.REDUCED, expectMostRecentItem())
            assertEquals(MapDetail.REDUCED, prefs.detail.value)
        }
    }
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*MapDetailPolicyTest*"`

Résultat attendu : ÉCHEC de compilation (`Unresolved reference: MapDetail`).

- [ ] **Étape 3 : écrire l'implémentation (modèle, règle, préférences).**

`core/model/src/main/java/fr/roadly/core/model/MapDetail.kt` :
```kotlin
package fr.roadly.core.model

/** Niveau de détail de la carte en guidage (C2) : REDUCED masque les points d'intérêt et les noms des petites rues. */
enum class MapDetail { NORMAL, REDUCED }
```

`map/MapDetailPolicy.kt` :
```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.MapDetail

/** C2 : niveau appliqué à la carte — le réglage, ou réduit automatiquement à grande vitesse (spec : « réduis les détails à grande vitesse »). */
object MapDetailPolicy {
    /** Au-delà (km/h), le niveau NORMAL passe en REDUCED. */
    const val AUTO_REDUCE_ABOVE_KMH = 90.0
    /** Sous ce seuil (km/h), le niveau réduit automatiquement revient à NORMAL (hystérésis : pas de clignotement vers 90 km/h). */
    const val AUTO_RESTORE_BELOW_KMH = 80.0

    fun effective(setting: MapDetail, speedKmh: Double?, current: MapDetail): MapDetail = when {
        setting == MapDetail.REDUCED -> MapDetail.REDUCED
        speedKmh == null -> current
        speedKmh > AUTO_REDUCE_ABOVE_KMH -> MapDetail.REDUCED
        current == MapDetail.REDUCED && speedKmh >= AUTO_RESTORE_BELOW_KMH -> MapDetail.REDUCED
        else -> MapDetail.NORMAL
    }
}
```

`UserPreferencesRepository.kt` :
1. Après la ligne 11 (`import fr.roadly.core.model.GeoPoint`), ajouter `import fr.roadly.core.model.MapDetail`.
2. Après la ligne 32 (`const val MAP_3D = true`), ajouter :
```kotlin
    /** Niveau de détail de la carte en guidage. */
    val MAP_DETAIL = MapDetail.NORMAL
```
3. Après la ligne 71 (`suspend fun setMap3dEnabled(value: Boolean)`), ajouter :
```kotlin
    /** C2 : niveau de détail de la carte en guidage (REDUCED : sans points d'intérêt ni noms des petites rues). */
    val mapDetail: Flow<MapDetail>
    suspend fun setMapDetail(value: MapDetail)
```
4. Après la ligne 95 (`private val kMap3d = booleanPreferencesKey("map.3d")`), ajouter :
```kotlin
    private val kMapDetail = stringPreferencesKey("map.detail")
```
5. Après la ligne 136 (`override suspend fun setMap3dEnabled…`), ajouter :
```kotlin
    override val mapDetail: Flow<MapDetail> = store.data.map { p -> p[kMapDetail]?.let { runCatching { MapDetail.valueOf(it) }.getOrNull() } ?: GuidanceDefaults.MAP_DETAIL }
    override suspend fun setMapDetail(value: MapDetail) { store.edit { it[kMapDetail] = value.name } }
```

`FakeUserPreferencesRepository.kt` :
1. Ajouter `import fr.roadly.core.model.MapDetail`.
2. Après la ligne 49 (`override suspend fun setMap3dEnabled…`), ajouter :
```kotlin
    val detail = MutableStateFlow(GuidanceDefaults.MAP_DETAIL)
    override val mapDetail: Flow<MapDetail> = detail
    override suspend fun setMapDetail(value: MapDetail) { detail.value = value }
```

- [ ] **Étape 4 : écrire l'implémentation (carte).**

`map/MapLayers.kt`, `MapIds`. Après la ligne 42 (`const val STYLE_LYR_BUILDING = "building"`), ajouter :
```kotlin
    /** Calques de points d'intérêt et de noms de rues des tuiles OpenMapTiles (niveau de détail, C2). */
    const val STYLE_LYR_POI = "poi"
    const val STYLE_LYR_TRANSPORTATION_NAME = "transportation_name"
```

`map/MapController.kt` :
1. Imports à ajouter :
   - `import fr.roadly.core.model.MapDetail` ;
   - `import org.maplibre.android.style.expressions.Expression` ;
   - `import org.maplibre.android.style.expressions.Expression.all` ;
   - `import org.maplibre.android.style.expressions.Expression.match` ;
   - `import org.maplibre.android.style.expressions.Expression.stop`.
2. Champs, après `private var shownPose: MarkerPose? = null` (tâche 2) :
```kotlin

    /** C2 : niveau de détail courant ; visibilité et filtres d'origine des calques touchés (style courant), pour revenir au niveau normal. */
    private var detail = MapDetail.NORMAL
    private val baseVisibility = mutableMapOf<String, String>()
    private val baseNameFilters = mutableMapOf<String, Expression?>()
```
3. `loadStyle`, lignes 120-123. Ancien :
```kotlin
    private fun loadStyle(url: String) {
        styleReady = false
        map?.setStyle(Style.Builder().fromUri(url)) { style -> installLayers(style); styleReady = true; replayState() }
    }
```
Nouveau :
```kotlin
    private fun loadStyle(url: String) {
        styleReady = false
        baseVisibility.clear()
        baseNameFilters.clear()
        map?.setStyle(Style.Builder().fromUri(url)) { style -> installLayers(style); styleReady = true; replayState() }
    }
```
4. `replayState`, ligne 214. Ancien :
```kotlin
        map?.let { m -> pendingCamera?.let { it(m); pendingCamera = null } }
```
Nouveau :
```kotlin
        applyDetailTo(style)
        map?.let { m -> pendingCamera?.let { it(m); pendingCamera = null } }
```
5. Après `fun setBuildings3d(...)` (lignes 263-266), ajouter :
```kotlin

    /**
     * C2 : niveau de détail. REDUCED masque les points d'intérêt et les noms des rues `minor` / `service` / `track` ; les calques
     * sont repérés par leur `source-layer` (pas par leur identifiant, propre à chaque style). Rejoué après un changement de style.
     */
    fun applyDetail(level: MapDetail) {
        detail = level
        if (styleReady) map?.style?.let { applyDetailTo(it) }
    }

    private fun applyDetailTo(style: Style) {
        val reduced = detail == MapDetail.REDUCED
        for (layer in style.layers) {
            if (layer !is SymbolLayer) continue
            when (layer.sourceLayer) {
                MapIds.STYLE_LYR_POI -> {
                    val base = baseVisibility.getOrPut(layer.id) { layer.visibility.value ?: Property.VISIBLE }
                    layer.setProperties(visibility(if (reduced) Property.NONE else base))
                }
                MapIds.STYLE_LYR_TRANSPORTATION_NAME -> {
                    if (layer.id !in baseNameFilters) baseNameFilters[layer.id] = layer.filter
                    val base = baseNameFilters[layer.id]
                    layer.setFilter(if (reduced) withoutMinorStreets(base) else base ?: literal(true))
                }
            }
        }
    }

    private fun withoutMinorStreets(base: Expression?): Expression {
        val notMinor = match(get("class"), literal(true), stop("minor", false), stop("service", false), stop("track", false))
        return if (base == null) notMinor else all(base, notMinor)
    }
```

- [ ] **Étape 5 : écrire l'implémentation (écran de guidage).**

`nav/NavigationViewModel.kt` :
1. Imports à ajouter :
   - `import fr.roadly.core.map.MapDetailPolicy` ;
   - `import fr.roadly.core.model.MapDetail` ;
   - `import kotlinx.coroutines.flow.distinctUntilChanged` ;
   - `import kotlinx.coroutines.flow.scan`.
2. Après `fun rememberCamera(snapshot: CameraSnapshot?) { if (snapshot != null) lastCamera = snapshot }` (tâche 3), ajouter :
```kotlin

    /** C2 : niveau de détail de la carte — réglage, réduit automatiquement au-delà de 90 km/h (retour sous 80 km/h). */
    val mapDetail: StateFlow<MapDetail> = combine(prefs.mapDetail, session.state) { setting, s -> setting to (s as? NavigationState.Guiding)?.speedKmh }
        .scan(MapDetail.NORMAL) { current, (setting, speed) -> MapDetailPolicy.effective(setting, speed, current) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapDetail.NORMAL)
```

`nav/NavigationScreen.kt`. Juste après l'effet de la flèche (tâche 4) :
```kotlin
    LaunchedEffect(route?.id, approach?.maneuverShapeIndex) {
        if (route != null && approach != null) map.setManeuverArrow(ManeuverArrowBuilder.build(route.geometry, approach.maneuverShapeIndex)) else map.clearManeuverArrow()
    }
```
ajouter :
```kotlin
    val mapDetail by vm.mapDetail.collectAsStateWithLifecycle()
    LaunchedEffect(mapDetail) { map.applyDetail(mapDetail) }
```

- [ ] **Étape 6 : écrire l'implémentation (réglages).**

`SettingsViewModel.kt` :
1. Ajouter l'import `fr.roadly.core.model.MapDetail`.
2. Après la ligne 91 (`val map3d…`), ajouter :
```kotlin
    /** Niveau de détail de la carte en guidage (Normal / Réduit). */
    val mapDetail: StateFlow<MapDetail> = prefs.mapDetail.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuidanceDefaults.MAP_DETAIL)
```
3. Après la ligne 137 (`fun setMap3d…`), ajouter :
```kotlin
    fun setMapDetail(value: MapDetail) { viewModelScope.launch { prefs.setMapDetail(value) } }
```

`feature/settings/src/main/res/values/strings.xml`, après la ligne 28 (`settings_map_3d_subtitle`) :
```xml
    <string name="settings_map_detail">Niveau de détail de la carte</string>
    <string name="settings_map_detail_subtitle">Pendant le guidage. Réduit : sans points d\'intérêt ni noms des petites rues. Réduit automatiquement au-delà de 90 km/h.</string>
    <string name="settings_map_detail_normal">Normal</string>
    <string name="settings_map_detail_reduced">Réduit</string>
```

`SettingsScreen.kt` :
1. Ajouter les imports `androidx.compose.material.icons.filled.Layers` et `fr.roadly.core.model.MapDetail`.
2. Lignes 144-145. Ancien :
```kotlin
            val map3d by vm.map3d.collectAsStateWithLifecycle()
            AppearanceGroup(state.themeMode, onSelect = vm::setTheme, map3d = map3d, onMap3d = vm::setMap3d)
```
Nouveau :
```kotlin
            val map3d by vm.map3d.collectAsStateWithLifecycle()
            val mapDetail by vm.mapDetail.collectAsStateWithLifecycle()
            AppearanceGroup(state.themeMode, onSelect = vm::setTheme, map3d = map3d, onMap3d = vm::setMap3d, mapDetail = mapDetail, onMapDetail = vm::setMapDetail)
```
3. Lignes 290-298. Ancien :
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
```
Nouveau :
```kotlin
@Composable
private fun AppearanceGroup(
    current: ThemeMode, onSelect: (ThemeMode) -> Unit, map3d: Boolean, onMap3d: (Boolean) -> Unit,
    mapDetail: MapDetail, onMapDetail: (MapDetail) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_appearance)) {
        ToggleRow(
            stringResource(R.string.settings_map_3d),
            checked = map3d, onCheckedChange = onMap3d,
            subtitle = stringResource(R.string.settings_map_3d_subtitle),
            icon = Icons.Default.ViewInAr,
        )
        ListRow(
            stringResource(R.string.settings_map_detail),
            subtitle = stringResource(R.string.settings_map_detail_subtitle),
            icon = Icons.Default.Layers,
            divider = false,
        )
        val detailLabels = MapDetail.entries.associateWith { it.label() }
        SegmentedControl(
            options = MapDetail.entries, selected = mapDetail, onSelect = onMapDetail, label = { detailLabels.getValue(it) },
            modifier = Modifier.padding(start = RoadlySpacing.m, end = RoadlySpacing.m, bottom = RoadlySpacing.m),
        )
```
4. Après `private fun ThemeMode.shortLabel()` (ligne 602 et suivantes), ajouter :
```kotlin

@Composable
private fun MapDetail.label(): String = stringResource(
    when (this) {
        MapDetail.NORMAL -> R.string.settings_map_detail_normal
        MapDetail.REDUCED -> R.string.settings_map_detail_reduced
    },
)
```

- [ ] **Étape 7 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest :core:data:testDebugUnitTest :feature:navigation:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDebug --offline -q`

Résultat attendu : SUCCÈS et BUILD SUCCESSFUL.

- [ ] **Étape 8 : commiter.**

```powershell
git add core/model/src/main/java/fr/roadly/core/model/MapDetail.kt core/map/src/main/java/fr/roadly/core/map/MapDetailPolicy.kt core/map/src/test/java/fr/roadly/core/map/MapDetailPolicyTest.kt core/map/src/main/java/fr/roadly/core/map/MapController.kt core/map/src/main/java/fr/roadly/core/map/MapLayers.kt core/data/src/main/java/fr/roadly/core/data/repository/UserPreferencesRepository.kt core/data/src/main/java/fr/roadly/core/data/testing/FakeUserPreferencesRepository.kt core/data/src/test/java/fr/roadly/core/data/repository/DataStoreUserPreferencesRepositoryTest.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt feature/settings/src/main/java/fr/roadly/feature/settings/SettingsViewModel.kt feature/settings/src/main/java/fr/roadly/feature/settings/SettingsScreen.kt feature/settings/src/main/res/values/strings.xml feature/settings/src/test/java/fr/roadly/feature/settings/SettingsViewModelTest.kt
git commit -m @'
feat(carte): niveau de détail Normal / Réduit, réduit automatiquement au-delà de 90 km/h (retour sous 80) (C2)

Calques repérés par leur source-layer (poi, transportation_name), filtres d'origine rétablis, rejoués après changement de style.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 7 : C3 — tronçon parcouru incrémental, signalements projetés une fois, options de rendu et FPS

**Fichiers :**
- Créer : `map/TraversedLine.kt`, `map/FpsWindow.kt`, `map/MapRendering.kt`, `nav/ReportsAheadProjector.kt`.
- Modifier : `map/MapLayers.kt`, `MapIds` (sources et calques du tronçon parcouru).
- Modifier : `map/MapController.kt` :
  - imports et champs ;
  - `attach` (après la ligne 97) ;
  - `installLayers` (lignes 133 et 153-155) ;
  - `replayState` (lignes 200 et 207) ;
  - `setTraversed` (lignes 240-244) ;
  - `companion object`.
- Modifier : `map/RoadlyMap.kt`, lignes 26-38.
- Modifier : `nav/NavigationViewModel.kt`, lignes 8 et 22 (imports) et 74-83 (`reportsAhead`).
- Modifier : `nav/NavigationScreen.kt`, ligne 160 (appel de `RoadlyMap`) et import de `MapRendering`.
- Test : `maptest/TraversedLineTest.kt`, `maptest/FpsWindowTest.kt`, `navtest/ReportsAheadProjectorTest.kt` (nouveaux). `TraversedFeatureBuilderTest` reste inchangé.

**Interfaces :**
- Consomme : `TraversedFeatureBuilder.build`, `EMPTY` (existants) ; `RouteGeometry.project` ; `ReportAheadUi` ; `Formatters.distance` ; `rememberPowerSaveMode` (état de la tâche 4).
- Produit :
  - `class TraversedLine { data class Update(val prefix: String?, val tail: String); fun update(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): Update }` ;
  - `internal class FpsWindow(windowMs: Long = 5_000L) { fun add(fps: Double, nowMs: Long): Double? }` ;
  - `object MapRendering { const val MAX_FPS = 60; const val MAX_FPS_POWER_SAVE = 30; fun options(context: Context): MapLibreMapOptions }` ;
  - `RoadlyMap(…, maximumFps: Int = MapRendering.MAX_FPS)` ;
  - `MapController.FPS_TAG = "RoadlyFps"` ;
  - `internal class ReportsAheadProjector { fun ahead(route: Route, alongM: Double, reports: List<CommunityReport>): List<ReportAheadUi>; val projectionCount: Int }`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`maptest/TraversedLineTest.kt` :

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraversedLineTest {
    private val g = (0..200).map { GeoPoint(48.0 + it * 0.0001, 2.0) }
    private fun coords(json: String) = Json.parseToJsonElement(json).jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray

    @Test fun `for a fixed segment only the two-point tail is rebuilt and its size does not grow`() { // C3 : pas de GeoJSON croissant
        val line = TraversedLine()
        val first = line.update(g, 150, GeoPoint(48.01505, 2.0))
        assertEquals(151, coords(first.prefix!!).size)
        val sizes = (1..50).map { i ->
            val u = line.update(g, 150, GeoPoint(48.0150 + i * 0.000001, 2.0))
            assertNull("préfixe inchangé", u.prefix)
            assertEquals(2, coords(u.tail).size)
            u.tail.length
        }
        assertTrue("queue bornée : $sizes", sizes.max() < 200)
    }

    @Test fun `advancing a segment or switching route rebuilds the prefix`() {
        val line = TraversedLine()
        line.update(g, 10, g[11])
        assertNull(line.update(g, 10, g[11]).prefix)
        assertEquals(12, coords(line.update(g, 11, g[12]).prefix!!).size)
        val other = g.map { GeoPoint(it.lat, 2.1) }
        assertNotNull(line.update(other, 11, other[12]).prefix)
    }

    @Test fun `prefix plus tail draw the same line as the full builder`() {
        val snapped = GeoPoint(48.00125, 2.0)
        val u = TraversedLine().update(g, 12, snapped)
        val full = coords(TraversedFeatureBuilder.build(g, 12, snapped))
        assertEquals(full, JsonArray(coords(u.prefix!!) + coords(u.tail).drop(1)))
    }

    @Test fun `nothing traversed gives empty pieces`() {
        val none = TraversedLine().update(emptyList(), -1, null)
        assertEquals(TraversedFeatureBuilder.EMPTY, none.prefix)
        assertEquals(TraversedFeatureBuilder.EMPTY, none.tail)
        val start = TraversedLine().update(g, 0, g[0])
        assertEquals("p0 seul : pas de préfixe", TraversedFeatureBuilder.EMPTY, start.prefix)
        assertEquals(2, coords(start.tail).size)
    }
}
```

`maptest/FpsWindowTest.kt` :

```kotlin
package fr.roadly.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FpsWindowTest {
    @Test fun `the average is given once per window, then starts over`() {
        val w = FpsWindow(windowMs = 5_000)
        assertNull(w.add(60.0, 0)); assertNull(w.add(50.0, 2_000)); assertNull(w.add(40.0, 4_999))
        assertEquals(47.5, w.add(40.0, 5_000)!!, 1e-9)
        assertNull(w.add(30.0, 6_000))
        assertEquals(30.0, w.add(30.0, 10_000)!!, 1e-9)
    }
}
```

`navtest/ReportsAheadProjectorTest.kt` :

```kotlin
package fr.roadly.feature.navigation

import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.ReportKind
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportsAheadProjectorTest {
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private fun report(id: String, along: Double) = CommunityReport(id, ReportKind.ACCIDENT, tracker.pointAt(along), 0L, 0L, 0, 0, mine = false)
    private val reports = listOf(report("a", 400.0), report("b", 900.0), report("c", 1_300.0))

    @Test fun `each report is projected once per route, then only filtered`() { // C3
        val p = ReportsAheadProjector()
        for (along in 0..380 step 20) p.ahead(route, along.toDouble(), reports)
        assertEquals(3, p.projectionCount)
        val ahead = p.ahead(route, 350.0, reports)
        assertEquals(listOf("a", "b", "c"), ahead.map { it.report.id })
        assertEquals(50.0, ahead[0].distanceM, 1.0)
        assertEquals(3, p.projectionCount)
    }

    @Test fun `results match a full projection`() {
        val geo = RouteGeometry(route.geometry)
        val p = ReportsAheadProjector()
        for (along in listOf(0.0, 380.0, 850.0, 1_250.0)) {
            val expected = reports.mapNotNull { r ->
                val pr = geo.project(r.point) ?: return@mapNotNull null
                val d = pr.alongM - along
                if (pr.offsetM > 60.0 || d < -50.0 || d > 5_000.0) null else r.id to d.coerceAtLeast(0.0)
            }.sortedBy { it.second }
            assertEquals(expected, p.ahead(route, along, reports).map { it.report.id to it.distanceM })
        }
    }

    @Test fun `a new route projects again and stale reports are forgotten`() {
        val p = ReportsAheadProjector()
        p.ahead(route, 0.0, reports)
        val other = route.copy(id = "autre")
        p.ahead(other, 0.0, reports)
        assertEquals(6, p.projectionCount)
        p.ahead(other, 0.0, reports.take(1))
        p.ahead(other, 0.0, reports)
        assertEquals("b et c oubliés puis reprojetés", 8, p.projectionCount)
    }
}
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest :feature:navigation:testDebugUnitTest --offline -q --tests "*TraversedLineTest*" --tests "*FpsWindowTest*" --tests "*ReportsAheadProjectorTest*"`

Résultat attendu : ÉCHEC de compilation (`Unresolved reference: TraversedLine`, `FpsWindow`, `ReportsAheadProjector`).

- [ ] **Étape 3 : écrire l'implémentation (pure).**

`map/TraversedLine.kt` :

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint

/**
 * C3 : tronçon parcouru en deux morceaux — préfixe p_0 … p_segmentIndex, reconstruit seulement quand l'index ou le tracé change,
 * et queue p_segmentIndex → position projetée (2 points), reconstruite à chaque position. Le GeoJSON envoyé par position est borné.
 */
class TraversedLine {
    /** [prefix] = null s'il est inchangé depuis l'appel précédent ; [tail] toujours fourni. */
    data class Update(val prefix: String?, val tail: String)

    private var geometry: List<GeoPoint>? = null
    private var segmentIndex = Int.MIN_VALUE

    fun update(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): Update {
        val prefixChanged = geometry !== this.geometry || segmentIndex != this.segmentIndex
        this.geometry = geometry
        this.segmentIndex = segmentIndex
        val prefix = if (prefixChanged) TraversedFeatureBuilder.build(geometry, segmentIndex, null) else null
        return Update(prefix, tail(geometry, segmentIndex, snapped))
    }

    private fun tail(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): String {
        if (snapped == null || segmentIndex < 0 || geometry.isEmpty()) return TraversedFeatureBuilder.EMPTY
        val start = geometry[segmentIndex.coerceAtMost(geometry.lastIndex)]
        return TraversedFeatureBuilder.build(listOf(start), 0, snapped)
    }
}
```

`map/FpsWindow.kt` :

```kotlin
package fr.roadly.core.map

/** C3 : moyenne des images par seconde du rendu sur une fenêtre de [windowMs] (journal de mesure, builds débogables). */
internal class FpsWindow(private val windowMs: Long = 5_000L) {
    private var startMs = -1L
    private var sum = 0.0
    private var count = 0

    /** Ajoute un échantillon ; renvoie la moyenne quand la fenêtre est écoulée (puis repart de zéro), sinon null. */
    fun add(fps: Double, nowMs: Long): Double? {
        if (startMs < 0) startMs = nowMs
        sum += fps
        count++
        if (nowMs - startMs < windowMs) return null
        return (sum / count).also { startMs = nowMs; sum = 0.0; count = 0 }
    }
}
```

`map/MapRendering.kt` :

```kotlin
package fr.roadly.core.map

import android.content.Context
import org.maplibre.android.maps.MapLibreMapOptions

/** C3 : réglages de rendu de la carte. */
object MapRendering {
    /** Plafond d'images par seconde du rendu. */
    const val MAX_FPS = 60
    /** Plafond en économie d'énergie (économiseur de batterie Android). */
    const val MAX_FPS_POWER_SAVE = 30

    /** Options explicites : SurfaceView (pas de TextureView, plus coûteuse) et inclinaison plafonnée au maximum rendu par MapLibre. */
    fun options(context: Context): MapLibreMapOptions =
        MapLibreMapOptions.createFromAttributes(context).textureMode(false).maxPitchPreference(GuidanceCameraPolicy.MAX_PITCH)
}
```

`nav/ReportsAheadProjector.kt` :

```kotlin
package fr.roadly.feature.navigation

import fr.roadly.core.common.Formatters
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.RouteGeometry

/**
 * C3 : signalements devant soi sans reprojection globale à chaque position. Chaque signalement est projeté une fois par tracé
 * (abscisse et écart mis en cache par identifiant), puis seulement filtré selon l'abscisse courante.
 */
internal class ReportsAheadProjector {
    private var routeId: String? = null
    private var geometry: RouteGeometry? = null
    private val projections = HashMap<String, RouteGeometry.Projection?>()

    /** Nombre de projections calculées depuis la création (contrôle du cache). */
    var projectionCount = 0
        private set

    fun ahead(route: Route, alongM: Double, reports: List<CommunityReport>): List<ReportAheadUi> {
        if (route.id != routeId) {
            routeId = route.id
            geometry = RouteGeometry(route.geometry)
            projections.clear()
        }
        val geo = geometry ?: return emptyList()
        if (projections.size > reports.size) {
            val ids = reports.mapTo(HashSet()) { it.id }
            projections.keys.retainAll(ids)
        }
        return reports.mapNotNull { r ->
            val p = if (r.id in projections) projections[r.id] else geo.project(r.point).also { projections[r.id] = it; projectionCount++ }
            p ?: return@mapNotNull null
            val d = p.alongM - alongM
            if (p.offsetM > MAX_OFFSET_M || d < -BEHIND_M || d > AHEAD_M) null
            else ReportAheadUi(r, d.coerceAtLeast(0.0), Formatters.distance(d.coerceAtLeast(0.0)))
        }.sortedBy { it.distanceM }
    }

    companion object {
        /** Écart maximal (m) au tracé pour qu'un signalement concerne l'itinéraire. */
        const val MAX_OFFSET_M = 60.0
        /** Tolérance (m) derrière la position (signalement que l'on est en train de passer). */
        const val BEHIND_M = 50.0
        /** Portée (m) devant la position, celle de l'Horizon. */
        const val AHEAD_M = 5_000.0
    }
}
```

- [ ] **Étape 4 : brancher dans la carte et dans l'écran.**

`map/MapLayers.kt`, `MapIds`. Après la ligne 21 (`const val SRC_TRAVERSED = "roadly-traversed"`), ajouter :
```kotlin
    /** C3 : queue du tronçon parcouru (dernier point du tracé → position projetée), seule mise à jour à chaque position. */
    const val SRC_TRAVERSED_TAIL = "roadly-traversed-tail"
    const val LYR_ROUTE_TRAVERSED_TAIL = "roadly-route-traversed-tail"
```

`map/MapController.kt` :
1. Imports à ajouter :
   - `import android.content.pm.ApplicationInfo` ;
   - `import android.os.SystemClock` ;
   - `import android.util.Log` ;
   - `import java.util.Locale`.
2. Ligne 67. Ancien :
```kotlin
    private var lastTraversed: String = TraversedFeatureBuilder.EMPTY
```
Nouveau :
```kotlin
    private var lastTraversed: String = TraversedFeatureBuilder.EMPTY
    private var lastTraversedTail: String = TraversedFeatureBuilder.EMPTY
    private val traversed = TraversedLine()
```
3. `attach`. Après la ligne 97 (`map.uiSettings.isTiltGesturesEnabled = false`), ajouter :
```kotlin
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            // C3 : mesure du rendu de la carte (SurfaceView, invisible pour `dumpsys gfxinfo`), builds débogables : adb logcat -s RoadlyFps
            val window = FpsWindow()
            map.setOnFpsChangedListener { fps ->
                window.add(fps, SystemClock.elapsedRealtime())?.let { Log.d(FPS_TAG, String.format(Locale.ROOT, "carte %.1f i/s (moyenne 5 s)", it)) }
            }
        }
```
4. `installLayers`. Après la ligne 133 (`style.addSource(GeoJsonSource(MapIds.SRC_TRAVERSED))`), ajouter :
```kotlin
        style.addSource(GeoJsonSource(MapIds.SRC_TRAVERSED_TAIL))
```
Après le calque `LYR_ROUTE_TRAVERSED` (lignes 153-155), ajouter :
```kotlin
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_TRAVERSED_TAIL, MapIds.SRC_TRAVERSED_TAIL).withProperties(
            lineColor(routeColorInactive), lineWidth(6.5f), lineOpacity(0.9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
```
5. `replayState`, ligne 200. Ancien :
```kotlin
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(lastTraversed)
```
Nouveau :
```kotlin
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(lastTraversed)
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED_TAIL)?.setGeoJson(lastTraversedTail)
```
Ligne 207. Ancien :
```kotlin
        (style.getLayer(MapIds.LYR_ROUTE_TRAVERSED) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
```
Nouveau :
```kotlin
        (style.getLayer(MapIds.LYR_ROUTE_TRAVERSED) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
        (style.getLayer(MapIds.LYR_ROUTE_TRAVERSED_TAIL) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
```
6. `setTraversed`, lignes 240-244. Ancien :
```kotlin
    /** Atténue la portion parcourue de l'itinéraire actif (écran de guidage). */
    fun setTraversed(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?) {
        lastTraversed = TraversedFeatureBuilder.build(geometry, segmentIndex, snapped)
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(lastTraversed)
    }
```
Nouveau :
```kotlin
    /** Atténue la portion parcourue de l'itinéraire actif (écran de guidage). C3 : préfixe renvoyé seulement s'il change, queue à chaque position. */
    fun setTraversed(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?) {
        val u = traversed.update(geometry, segmentIndex, snapped)
        u.prefix?.let { lastTraversed = it }
        lastTraversedTail = u.tail
        if (!styleReady) return
        val style = map?.style ?: return
        u.prefix?.let { style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(it) }
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED_TAIL)?.setGeoJson(u.tail)
    }
```
7. `companion object` (tâche 2). Après `const val USER_ANIMATION_MS = 1_000L`, ajouter :
```kotlin
        /** Étiquette logcat du journal des images par seconde de la carte (builds débogables). */
        const val FPS_TAG = "RoadlyFps"
```

`map/RoadlyMap.kt` :
1. Lignes 34-36. Ancien :
```kotlin
    /** Caméra immobile : cap de la carte en degrés (0 = nord en haut) pour la boussole. */
    onCameraIdle: ((bearingDeg: Double) -> Unit)? = null,
) {
```
Nouveau :
```kotlin
    /** Caméra immobile : cap de la carte en degrés (0 = nord en haut) pour la boussole. */
    onCameraIdle: ((bearingDeg: Double) -> Unit)? = null,
    /** C3 : plafond d'images par seconde du rendu ([MapRendering.MAX_FPS] ; 30 en économie d'énergie). */
    maximumFps: Int = MapRendering.MAX_FPS,
) {
```
2. Ligne 38. Ancien :
```kotlin
    val mapView = remember { MapView(controllerContext(controller)) }
```
Nouveau :
```kotlin
    val mapView = remember { controllerContext(controller).let { ctx -> MapView(ctx, MapRendering.options(ctx)) } }
    // Le moteur de rendu est créé par le constructeur de MapView : le plafond peut être posé tout de suite.
    LaunchedEffect(mapView, maximumFps) { mapView.setMaximumFps(maximumFps) }
```

`nav/NavigationScreen.kt` :
1. Ajouter l'import `fr.roadly.core.map.MapRendering`.
2. Ligne 160. Ancien :
```kotlin
            RoadlyMap(styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onUserGesture = vm::onUserGesture)
```
Nouveau :
```kotlin
            RoadlyMap(
                styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onUserGesture = vm::onUserGesture,
                maximumFps = if (powerSave.value) MapRendering.MAX_FPS_POWER_SAVE else MapRendering.MAX_FPS,
            )
```

`nav/NavigationViewModel.kt` :
1. Supprimer les imports devenus inutiles, `import fr.roadly.core.common.Formatters` (ligne 8) et `import fr.roadly.core.navigation.RouteGeometry` (ligne 22).
2. Lignes 74-83. Ancien :
```kotlin
    /** Signalements projetés sur le tracé courant, devant soi (≤ 60 m du tracé, ≤ 5 km) : alimentent l'Horizon. */
    val reportsAhead: StateFlow<List<ReportAheadUi>> = combine(reports, session.state) { list, s ->
        val g = s as? NavigationState.Guiding ?: return@combine emptyList()
        val geo = RouteGeometry(g.route.geometry)
        list.mapNotNull { r ->
            val p = geo.project(r.point) ?: return@mapNotNull null
            val d = p.alongM - g.progress.distanceAlongM
            if (p.offsetM > 60.0 || d < -50.0 || d > 5_000.0) null else ReportAheadUi(r, d.coerceAtLeast(0.0), Formatters.distance(d.coerceAtLeast(0.0)))
        }.sortedBy { it.distanceM }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```
Nouveau :
```kotlin
    private val reportsProjector = ReportsAheadProjector()

    /** Signalements projetés sur le tracé courant, devant soi (≤ 60 m du tracé, ≤ 5 km) : alimentent l'Horizon. C3 : projection en cache par tracé. */
    val reportsAhead: StateFlow<List<ReportAheadUi>> = combine(reports, session.state) { list, s ->
        val g = s as? NavigationState.Guiding ?: return@combine emptyList()
        reportsProjector.ahead(g.route, g.progress.distanceAlongM, list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

- [ ] **Étape 5 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:map:testDebugUnitTest :feature:navigation:testDebugUnitTest :app:assembleDebug --offline -q`

Résultat attendu : SUCCÈS. Les 3 tests de `TraversedFeatureBuilderTest` restent inchangés et verts. BUILD SUCCESSFUL.

- [ ] **Étape 6 : commiter.**

```powershell
git add core/map/src/main/java/fr/roadly/core/map/TraversedLine.kt core/map/src/main/java/fr/roadly/core/map/FpsWindow.kt core/map/src/main/java/fr/roadly/core/map/MapRendering.kt core/map/src/main/java/fr/roadly/core/map/MapLayers.kt core/map/src/main/java/fr/roadly/core/map/MapController.kt core/map/src/main/java/fr/roadly/core/map/RoadlyMap.kt core/map/src/test/java/fr/roadly/core/map/TraversedLineTest.kt core/map/src/test/java/fr/roadly/core/map/FpsWindowTest.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/ReportsAheadProjector.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/ReportsAheadProjectorTest.kt
git commit -m @'
perf(carte): tronçon parcouru incrémental, signalements projetés une fois par tracé, rendu plafonné à 60 i/s (30 en économie) (C3)

GeoJSON par position borné à 2 points ; journal RoadlyFps du rendu de la carte dans les builds débogables.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 8 : passe appareil HONOR — mesures avant / après, vérifications C et reliquat B, consignation

**Fichiers :**
- Modifier : `ROADLY_3D_NAVIGATION_TESTS.md`, avec l'outil Edit uniquement : ligne 3, lignes 32 et 36 (scénarios 15 et 19), ligne 43 (« Performance »), nouvelle section « Performance (C3) » avant « Journal des essais réels » (ligne 45), et journal (après la ligne 49).
- Créer : `docs/captures/etape-c/nuit-3d.png`, `jour-3d.png`, `nuit-2d.png`, `nuit-nord.png`, `paysage-3d.png`, `detail-reduit.png`.
- Pas de code. Le worktree temporaire `GPS-roadly-perf-base` est créé puis supprimé.

**Interfaces :**
- Consomme : toutes les tâches 1 à 7, le journal `RoadlyFps` (tâche 7) et `MapRendering`.
- Produit : les chiffres mesurés et les résultats des essais, consignés.

- [ ] **Étape 1 : vérification complète sur JVM et build.**

```powershell
.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
$n = 0; Get-ChildItem -Recurse -Filter 'TEST-*.xml' -Path core, feature | Where-Object FullName -match 'test-results' | ForEach-Object { $n += [int]([xml](Get-Content $_.FullName)).testsuite.tests }; $n
git rev-parse --short HEAD
```

Résultat attendu : SUCCÈS ; le nombre affiché vaut au moins 374 plus les tests ajoutés (environ 45). Noter ce nombre et l'empreinte courte : ils servent à l'étape 9.

- [ ] **Étape 2 : mesure de référence, build `c4d05ef` (avant).**

```powershell
$base = "C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-perf-base"
git worktree add $base c4d05ef
Copy-Item "C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\local.properties" $base
& "$base\gradlew.bat" -p $base :app:assembleDebug --offline -q
adb -s (numéro de série retiré) install -r "$base\app\build\outputs\apk\debug\app-debug.apk"
```

Conditions, à fixer par l'utilisateur et identiques avant et après :
- téléphone posé, immobile, en intérieur près d'une fenêtre ;
- luminosité réglée à la main, luminosité adaptative coupée ;
- thème « Sombre » ; réglage « Carte 3D » activé ; économiseur de batterie coupé ;
- guidage lancé vers la même destination (adresse choisie par l'utilisateur et notée dans le journal) ;
- 30 s d'attente avant chaque mesure.

Puis :

```powershell
$d = '(numéro de série retiré)'
adb -s $d shell dumpsys gfxinfo fr.roadly reset
Start-Sleep -Seconds 120
adb -s $d shell dumpsys gfxinfo fr.roadly | Select-String "Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile"
```

Noter les six valeurs. Le build de référence n'a pas de journal `RoadlyFps`.

Batterie : `adb -s $d shell dumpsys batterystats --reset`. Puis :
1. l'utilisateur débranche le câble USB et laisse le guidage stationnaire 20 min, écran allumé ;
2. il rebranche ;
3. lancer aussitôt :

```powershell
adb -s $d shell dumpsys package fr.roadly | Select-String "userId="
adb -s $d shell dumpsys batterystats fr.roadly | Select-String "Computed drain|actual drain|UID u0a"
```

Noter la ligne `UID u0a<n>` qui correspond à `userId=10<n>` (en mAh), et le « Computed drain ».

- [ ] **Étape 3 : mesure du build courant (après).**

```powershell
adb -s (numéro de série retiré) install -r app\build\outputs\apk\debug\app-debug.apk
```

Avant toute mesure, ouvrir Réglages → Fournisseurs → Serveurs et toucher « Réinitialiser » : des URL https enregistrées plus tôt masqueraient le style embarqué (écart 11). Vérifier que les champs de style affichent `asset://styles/roadly-…json`.

Refaire exactement l'étape 2 (gfxinfo 120 s, batterie 20 min), avec en plus le journal du rendu :

```powershell
adb -s (numéro de série retiré) logcat -c
# … pendant les 120 s de gfxinfo …
adb -s (numéro de série retiré) logcat -d -s RoadlyFps:D
```

Noter la moyenne des valeurs « carte N i/s » affichées pendant les 120 s.

Supprimer le worktree temporaire :
```powershell
git worktree remove --force "C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-perf-base"
```

- [ ] **Étape 4 : vérifications C1 sur appareil (build courant).**

À cocher une par une. Noter tout écart, sans le corriger dans cette tâche.

1. **Bascule 2D/3D (scénario 15).** Toucher le bouton « Passer en vue 2D » : transition en environ 0,6 s, sans à-coup, carte à plat, bâtiments masqués. Toucher « Passer en vue 3D » : retour à 58°, bâtiments visibles.
2. **Nord en haut.** Toucher « Nord en haut » : la carte tourne nord en haut et le bouton passe en style marque. Un second appui revient au mode précédent (2D ou 3D).
3. **Compte à rebours du recentrage.**
   - Faire glisser la carte : la pastille affiche « Recentrage dans 8 s », puis 7, 6… ; à 0, la caméra revient en douceur.
   - Refaire glisser, puis toucher « Annuler » : la carte reste libre au-delà de 8 s.
   - Toucher « Recentrer » : retour immédiat.
4. **Retour depuis la recherche.** Toucher « Changer de destination », puis revenir : la carte réapparaît inclinée à l'endroit quitté, sans vue du monde entier.
5. **Accueil à plat.** Arrêter le guidage : l'accueil est à plat (inclinaison 0).
6. **Paysage (scénario 19).** Tourner l'appareil : les deux boutons caméra sont en bas à droite, à gauche du compteur, sans chevauchement. Capture `paysage-3d.png`. En portrait, vérifier que la rangée de boutons ne masque ni le bandeau ni l'Horizon. En cas de chevauchement, le noter pour D5.
7. **Marqueur à 1 Hz (« en roulant » ou à pied, 2 min au moins).** Le marqueur glisse avec la caméra, sans saut visible. Cap stable à l'arrêt (feu rouge) et passage du nord sans tour complet.

Captures (thème sombre, guidage en cours) :

```powershell
$d = '(numéro de série retiré)'
adb -s $d shell screencap -p /sdcard/Download/roadly-c-nuit-3d.png
adb -s $d pull /sdcard/Download/roadly-c-nuit-3d.png docs\captures\etape-c\nuit-3d.png
```

Même procédure pour `nuit-2d.png` (après le bouton 2D), `nuit-nord.png` (nord en haut) et `paysage-3d.png`. Puis :
- `jour-3d.png` : Réglages → Apparence → thème « Clair », reprendre le guidage, faire la capture, remettre le thème « Auto » ;
- `detail-reduit.png` : à l'étape 5.

- [ ] **Étape 5 : vérifications C2 sur appareil.**

1. **Style hors ligne.**
   - Lancer l'application une fois en ligne : les tuiles se mettent en cache.
   - Fermer l'application et activer le mode avion (action de l'utilisateur). Relancer : le style Roadly se charge depuis l'asset, et les zones en cache s'affichent avec la palette Roadly.
   - `adb -s (numéro de série retiré) logcat -d | Select-String -Pattern "Failed to load style|style.*error" -CaseSensitive:$false` ne doit rien renvoyer.
   - Désactiver le mode avion.
2. **Hiérarchie lisible.** Autoroute plus large avec un liseré distinct, routes principales étagées, bretelles plus fines, tunnels en pointillé, ponts bordés d'un liseré plus sombre. Vérifier sur une zone connue de l'utilisateur ; les cas « tunnel / pont » réels relèvent des essais en roulant.
3. **Points d'intérêt.** Au zoom 14 ou plus, près d'une station-service ou d'un parking : icône et nom visibles. Au zoom 16, autres points d'intérêt en texte.
4. **Détail réduit.** Réglages → Apparence → « Niveau de détail de la carte » → « Réduit » : en guidage, plus de points d'intérêt ni de noms de petites rues. Capture `detail-reduit.png`. Repasser en « Normal » : retour à l'affichage complet.
5. **Attribution.** L'icône « i » de MapLibre affiche bien OpenFreeMap, OpenMapTiles et OpenStreetMap.

- [ ] **Étape 6 : reliquat B sur appareil (essais à l'arrêt).**

1. **Mort du processus (scénario 14).** Démarrer un guidage, puis :

```powershell
$d = '(numéro de série retiré)'
$appPid = (adb -s $d shell pidof fr.roadly).Trim()
adb -s $d shell run-as fr.roadly kill -9 $appPid
adb -s $d shell "run-as fr.roadly cat files/datastore/roadly_prefs.preferences_pb" | Select-String "guidance.last"
```

Résultat attendu : les clés `guidance.last.lat`, `guidance.last.route`, etc. sont présentes. Relancer l'application et noter si le service redémarre. Le bouton « Reprendre » relève de E3.

- [ ] **Étape 7 : essais « à faire en roulant » (par l'utilisateur, avec un passager si possible).**

1. **Voix avec musique.**
   - Lancer une musique dans une autre application, puis un trajet avec deux manœuvres rapprochées.
   - Attendu : une annonce FAR en cours est coupée par le NOW. L'annonce NEAR de la manœuvre suivante est bien prononcée (file jamais bloquée, `6ddb64f`). La musique baisse pendant les annonces.
2. **Autoroute à 130 km/h.** NOW jamais sauté, approche à 400 m d'une sortie. Détail réduit automatiquement au-delà de 90 km/h, rétabli sous 80 km/h. Zoom de croisière lissé, sans à-coup.
3. **Rond-point et double rond-point.** Cible de sortie stable ; inclinaison 50° à l'approche, 60° en ville sous 30 km/h.
4. **Tunnel réel.** Tunnel dessiné en pointillé, perte GPS et reprise (scénario 11), marqueur sans saut à la sortie.

- [ ] **Étape 8 : critères d'acceptation C3.**
- Jank stationnaire du build courant : « Janky frames » < 5 %. Si la valeur mesurée est de 5 % ou plus, écrire « ÉCHEC » dans le tableau, sans arrondir ni rejouer jusqu'à obtenir un meilleur chiffre, et laisser une ligne de suivi dans le journal.
- GeoJSON par position borné : couvert par `TraversedLineTest` (queue de 2 points, taille constante).

- [ ] **Étape 9 : consigner dans `ROADLY_3D_NAVIGATION_TESTS.md` (outil Edit uniquement).**

**1. Ligne 3.** Remplacer `État actuel (fin d'étape B, `71b5493`) : 374 tests JVM verts` par `État actuel (fin d'étape C, `<empreinte de l'étape 1>`) : <nombre de l'étape 1> tests JVM verts`, en reportant les deux valeurs réelles.

**2. Ligne 32.** Ancien :
```markdown
| 15 | Changement 2D/3D | JVM `GuidanceCameraPolicy` ; appareil : animation, retour | appareil | C1 |
```
Nouveau :
```markdown
| 15 | Changement 2D/3D | JVM `NavigationCameraControllerTest` (modes, transition 600 ms, cap 359° → 1° sans tour, arrêt sans dérive), `GuidanceCameraPolicyTest`, `NavigationViewModelTest` (mode de session, compte à rebours) ; appareil : animation, retour | appareil | C1 |
```

**3. Ligne 36.** Ancien :
```markdown
| 19 | Mode paysage | instrumenté Compose (overlay paysage) ; appareil captures | appareil | D5 |
```
Nouveau :
```markdown
| 19 | Mode paysage | instrumenté Compose (overlay paysage, D5) ; appareil : captures, boutons caméra en bas à droite (C) | appareil | C1, D5 |
```

**4. Ligne 43.** Ancien :
```markdown
- Performance (C3) : `adb shell dumpsys gfxinfo <package> reset` puis 2 min de guidage stationnaire → % de frames en jank ; `dumpsys batterystats` sur 20 min écran allumé. Valeurs mesurées à consigner ici (avant / après).
```
Nouveau :
```markdown
- Performance (C3) : voir la section « Performance (C3) — mesures » ; JVM : `TraversedLineTest` (GeoJSON par position borné), `ReportsAheadProjectorTest` (une projection par signalement et par tracé).
```

**5. Nouvelle section avant `## Journal des essais réels`.** Chaque cellule reçoit la valeur lue dans la sortie de la commande indiquée, sans estimation :
```markdown
## Performance (C3) — mesures

Conditions : HONOR Magic 8 Pro, guidage stationnaire vers <destination notée>, thème sombre, Carte 3D, luminosité manuelle, économiseur coupé, <date>.

| Mesure (commande) | Avant (`c4d05ef`) | Après (`<empreinte>`) |
|---|---|---|
| Images rendues, 120 s (`gfxinfo` « Total frames rendered ») | … | … |
| Images en jank (`gfxinfo` « Janky frames ») | … % | … % |
| 50e / 90e / 95e / 99e centile (ms) | … | … |
| Rendu de la carte (`logcat -s RoadlyFps`, moyenne 120 s) | non mesurable (pas de journal) | … i/s |
| Batterie, 20 min, application (`batterystats` « UID u0a… ») | … mAh | … mAh |
| Batterie, 20 min, total (« Computed drain ») | … mAh | … mAh |

Acceptation : jank stationnaire < 5 % → <OK / ÉCHEC>. LIMITE : `gfxinfo` ne mesure que les overlays Compose ; le rendu GL de la carte (SurfaceView) est suivi par `RoadlyFps` (builds débogables).
```
Remplacer chaque « … » et chaque `<…>` par la valeur réellement mesurée ou observée avant d'enregistrer.

**6. Journal.** Ajouter après la ligne 49, une ligne par essai réellement fait, avec la date d'exécution et le résultat observé. Format :
```markdown
| <date> | C : 15, 19, style jour / nuit, détail, recentrage, hors ligne ; B : 14 (`kill -9`) | HONOR Magic 8 Pro | <résultat> | captures `docs/captures/etape-c/` ; à faire en roulant : voix avec musique (FAR coupé par NOW, NEAR dit), autoroute 130 km/h, rond-point, tunnel |
```

- [ ] **Étape 10 : commiter.**

```powershell
git add ROADLY_3D_NAVIGATION_TESTS.md docs/captures/etape-c/nuit-3d.png docs/captures/etape-c/jour-3d.png docs/captures/etape-c/nuit-2d.png docs/captures/etape-c/nuit-nord.png docs/captures/etape-c/paysage-3d.png docs/captures/etape-c/detail-reduit.png
git commit -m @'
docs(tests): mesures avant / après de l'étape C sur le HONOR, captures jour / nuit, essais réels consignés (C3)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

## Surface de fusion avec `feat/roadly-ai`

| Fichier | Les deux branches | Résolution |
|---|---|---|
| `nav/NavigationScreen.kt` | IA : signature `NavigationScreen` / `NavigationContent` (emplacements assistant), lignes d'appel des overlays, `assistantButton()` en tête de colonne, `assistantPanel()` en tête des blocs du bas. C : ligne 104, lignes 124-154, ligne 160 (`maximumFps`), `CameraModeButtons(vm)` en fin de colonne portrait et dans la rangée du bas du paysage, `BottomBlock`, nouvelles fonctions privées en fin de fichier | Garder tout. Aucune ligne n'est touchée par les deux branches : l'IA ajoute en tête de colonne, C en fin de colonne ; les lignes d'appel des overlays (162-166) restent celles de l'IA |
| `nav/NavigationViewModel.kt` | IA : constructeur (`GuidanceActions`), corps de `reportsAhead` → `reportsAhead(g, list)`, `stop` / `addStop` / `switchTo`, imports. C : `map3d` en `Eagerly`, `cameraMode`, compte à rebours, `lastCamera`, `mapDetail`, corps de `reportsAhead` → `reportsProjector.ahead(…)`, imports | Conflit sur le corps de `reportsAhead` : garder la ligne C (cache) et retirer l'import `fr.roadly.core.navigation.reportsAhead` s'il devient inutile. La fonction IA `core/navigation/ReportsAhead.kt` reste pour `NavigationTools`. Le reste ne se chevauche pas |
| `navtest/NavigationViewModelTest.kt` | IA : constructeurs (`GuidanceActions`). C : 4 nouveaux tests | Les tests C passent par l'assistant `NavigationViewModel(s, p, c)` : garder la version IA de l'assistant |
| `feature/settings/.../SettingsScreen.kt` | IA : `AssistantGroup()` après `VoiceGroup`, assistants passés en `internal`. C : appel et fonction `AppearanceGroup` | Lignes distinctes : garder les deux |
| `feature/settings/src/main/res/values/strings.xml` | IA : 18 chaînes. C : 4 chaînes après `settings_map_3d_subtitle` | Garder les deux |

`feat/roadly-ai` ne modifie aucun fichier de `core/map`, `core/data`, `core/model`, `feature/trips`, ni les chaînes de `feature/navigation` (vérifié par `git diff c4d05ef...HEAD --stat`).

## LIMITES documentées par cette étape

- **Inclinaison.** La « ville dense » est à 60° et non 62° : MapLibre Android 11.13.5 refuse une inclinaison au-delà de 60° (`MapLibreConstants.MAXIMUM_PITCH`).
- **Marge du panneau inférieur.** `bottomPanelFraction` est livré et testé, mais branché à l'Étape D5 : la mesure du panneau demande un rappel dans les overlays, dont la signature est figée.
- **Points d'intérêt.** Les classes visibles dès le zoom 14 sont `hospital`, `fuel`, `parking` et `railway` (gares) : la classe `station` n'existe pas dans OpenMapTiles. La présence de `poi` dans les tuiles OpenFreeMap reste à voir sur appareil (tâche 8, étape 5).
- **Licence des styles amont.** La licence des styles OpenFreeMap `dark` et `positron`, désormais embarqués, est à confirmer dans le dépôt openfreemap-styles avant une publication sur le Play Store. L'attribution des tuiles reste affichée par MapLibre.
- **Mesures.** `dumpsys gfxinfo` ne mesure pas le rendu GL de la carte. `RoadlyFps` le complète, en build débogable seulement, et n'existe pas pour le build de référence.
- **Bascule 2D/3D.** Une position qui arrive pendant la transition de 600 ms la remplace par un glissement linéaire de 1 s : la position reste continue, mais la vitesse ne l'est pas.
- **Accueil.** Le marqueur y glisse en 1 s alors que la caméra s'anime en 0,6 s (`moveTo`) : léger décalage, hors périmètre du guidage.
- **« Annuler ».** Après « Annuler », la carte reste libre jusqu'à « Recentrer » : c'est voulu, et c'est l'unique sortie.
- **Paysage.** La colonne d'actions, avec le bouton Roadly AI après fusion, approche la hauteur de l'écran du HONOR. La réorganisation des contrôles relève de D5.

---

## Auto-revue

**1. Couverture de la spécification.**

| Exigence de `ROADLY_3D_NAVIGATION_PLAN.md` §C | Tâche | Preuve |
|---|---|---|
| C1 `NavigationCameraController` (core/map), cap filtré par moyenne circulaire, τ ≈ 1,5 s | 1 | `NavigationCameraControllerTest` (pas d'une moyenne à 1,5 s, trou GPS plafonné) |
| C1 acceptation JVM : 359° → 1° sans tour complet, arrêt sans dérive | 1 | `crossing north turns the short way…`, `stopped, the heading does not drift` |
| C1 marqueur interpolé, animation de 1 s synchronisée avec `easeCamera` | 2, 4 | `MarkerInterpolationTest` ; `TICK_MS = USER_ANIMATION_MS = 1 000`, linéaires tous deux ; appareil : tâche 8, étape 4.7 |
| C1 zoom lissé | 1 | `the zoom stays smoothed across a mode change` (pas de 0,5 conservé) |
| C1 inclinaison 58 / 50 / 62 (ville dense < 30 km/h) | 1 | `GuidanceCameraPolicyTest` (2 nouveaux tests) ; 62° → 60° en LIMITE |
| C1 transition 2D ↔ 3D animée (600 ms), bouton 2D/3D, « nord en haut » | 1, 3, 4 | `first frame jumps…`, `2d stays flat, north up…`, `the camera mode starts from the 3d setting…` ; appareil : tâche 8, étapes 4.1 et 4.2 |
| C1 reprise automatique après geste (8 s), compte à rebours annulable, recentrage immédiat | 3, 4 | `the auto recenter countdown…`, test existant des 8 s ; pastille « Recentrage dans N s · Annuler » ; appareil : tâche 8, étape 4.3 |
| C2 style JSON embarqué (dark, light), mêmes sources, glyphes et sprites distants | 5 | `RoadlyStyleAssetTest` (sources, glyphes, sprite, version, bâtiments, sources existantes) |
| C2 hiérarchie routière, tunnels `brunnel`, ponts, bretelles `ramp`, parcs, eau, repères | 5 | script (pile de 7 calques, eau et parcs, POI) ; tests `road layers are stacked…`, `tunnels are dashed…`, `ramps are narrower…` |
| C2 étiquettes allégées en conduite | 5, 6 | `street names are sparser…` (espacement 400, −1 px dès le zoom 15) ; niveau réduit et automatique à grande vitesse (`MapDetailPolicyTest`) |
| C2 bâtiments extrudés déjà en place | 5 | calque `building` conservé (test), `installBuildings` inchangé |
| C2 Fournisseurs : URL de style externe toujours possible | 5 | `ProviderConfigTest.an external https style stays possible`, `SettingsViewModelTest` vert |
| C2 acceptation : chargé hors ligne depuis l'asset, captures jour / nuit, rien d'inventé | 5, 8 | asset `asset://` ; tâche 8, étapes 4 (captures) et 5.1 (mode avion) ; données issues des tuiles uniquement |
| C3 `setMaximumFps(60)` | 7 | `RoadlyMap(maximumFps)`, 30 en économie d'énergie |
| C3 `setTraversed` incrémental, aucune allocation GeoJSON croissante par position | 7 | `TraversedLineTest` (queue de 2 points, taille bornée, équivalente au constructeur complet) |
| C3 `reportsAhead` sans reprojection globale | 7 | `ReportsAheadProjectorTest` (une projection par signalement et par tracé, résultats identiques) |
| C3 `NavigationContent` découpé : carte et effets hors recomposition des overlays | 4 | `GuidanceMapEffects` : un seul `snapshotFlow`, structure du `combine` intacte |
| C3 mesures `gfxinfo` avant / après, `batterystats` 20 min, jank < 5 % | 8 | étapes 2, 3, 8 et 9 (tableau « Performance (C3) ») |
| Scénarios 15 et 19 de `ROADLY_3D_NAVIGATION_TESTS.md` | 3, 4, 8 | matrice mise à jour (tâche 8, étape 9), captures |
| Reliquat B : voix avec musique, mort du processus, autoroute, rond-point, tunnel | 8 | étape 6 (`kill -9` + DataStore), étape 7 (« en roulant ») |

**2. Recherche de trous.**
- Aucun « TBD », « TODO », « à compléter » ni « comme la tâche N ». Chaque étape de code donne le code complet, et chaque bloc `ancien` est le texte exact de `c4d05ef`, ou celui qu'a laissé une tâche citée.
- Les seules valeurs inconnues à l'écriture sont des mesures (tâche 8, étape 9). Le plan dit quelle commande les produit et interdit toute estimation.
- La logique du script PowerShell a été exécutée à blanc le 2026-10-31, en mémoire, sur les styles réels. Résultat : 51 et 55 calques, aucun `"Count":`, aucun `["zoom"]` imbriqué, identifiants uniques, pile routière dans l'ordre. Les nombres attendus de l'étape 5 viennent de cet essai.

**3. Cohérence des noms entre tâches.**
- `CameraMode3D` (`FOLLOW_3D`, `FOLLOW_2D`, `NORTH_UP`, `toggled3d`, `default`) : défini à la tâche 1, utilisé aux tâches 3 et 4.
- `NavigationCameraInput`, `NavigationCameraFrame` (`point`, `camera`, `durationMs`, `eased`), `update`, `requestTransition`, `reset`, `bearing` : définis à la tâche 1, utilisés à la tâche 4.
- `GuidanceCameraPolicy.MAX_PITCH` (tâche 1) est repris par `MapRendering.options` (tâche 7).
- `MapController.setUserLocation(fix, animateMs)`, `applyGuidanceCamera(…, durationMs, eased)`, `restore(snapshot, keepTilt)` et `USER_ANIMATION_MS` (tâche 2) sont utilisés aux tâches 4 et 2 (`ReplayScreen`). `FPS_TAG` est ajouté au même `companion object` à la tâche 7.
- `vm.cameraMode`, `toggleCameraMode`, `toggleNorthUp`, `recenterInSeconds`, `cancelAutoRecenter`, `lastCamera` et `rememberCamera` : définis à la tâche 3, utilisés à la tâche 4. `mapDetail` est défini à la tâche 6 et ancré après `rememberCamera`.
- `MapDetail` (core/model), `MapDetailPolicy.effective(setting, speedKmh, current)`, `GuidanceDefaults.MAP_DETAIL`, `FakeUserPreferencesRepository.detail`, `MapController.applyDetail` et `MapIds.STYLE_LYR_POI` / `STYLE_LYR_TRANSPORTATION_NAME` : tous à la tâche 6.
- `TraversedLine.Update(prefix, tail)`, `MapIds.SRC_TRAVERSED_TAIL` / `LYR_ROUTE_TRAVERSED_TAIL`, `FpsWindow.add`, `MapRendering.MAX_FPS` / `MAX_FPS_POWER_SAVE` et `ReportsAheadProjector.ahead` / `projectionCount` : tous à la tâche 7.
- Identifiants du style (`roadly_road_*`, `roadly_poi_major`, `roadly_poi_other`) : identiques dans le script et dans `RoadlyStyleAssetTest` (tâche 5).
- `powerSave` est un `State<Boolean>` dès la tâche 4 ; il est lu par `.value` à la tâche 7, ligne de `RoadlyMap`.

### Fichiers critiques pour l'implémentation
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\map\src\main\java\fr\roadly\core\map\MapController.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\map\src\main\java\fr\roadly\core\map\GuidanceCameraPolicy.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\feature\navigation\src\main\java\fr\roadly\feature\navigation\NavigationScreen.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\feature\navigation\src\main\java\fr\roadly\feature\navigation\NavigationViewModel.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\data\src\main\java\fr\roadly\core\data\config\ProviderConfig.kt`