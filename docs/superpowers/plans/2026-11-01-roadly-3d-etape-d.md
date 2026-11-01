# Étape D — Interface premium — plan d'implémentation

> **Pour les agents d'exécution :** SOUS-COMPÉTENCE OBLIGATOIRE : utiliser superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour exécuter ce plan tâche par tâche. Les étapes utilisent des cases à cocher (`- [ ]`) pour le suivi.

Emplacement proposé : `docs/superpowers/plans/2026-11-01-roadly-3d-etape-d.md`.

**Objectif :** donner au guidage Roadly une interface de cockpit premium :
- un bandeau interactif qui ouvre un aperçu de la manœuvre, avec une ligne « puis… » (D1) ;
- un schéma de rond-point tiré du tracé réel (D2) ;
- un panneau de direction (D3) ;
- des voies à trois états (D4) ;
- un panneau du bas repliable, dont la hauteur mesurée pilote la caméra (D5).

L'icône d'attribution de la carte redevient accessible en portrait.

**Architecture :**
- Les règles restent en Kotlin pur, testées sur JVM :
  - `RoundaboutGeometry` (arc parcouru sur l'anneau), dans `core/navigation` à côté de `RoundaboutResolver` ;
  - `exitSignFor`, `thenFor`, `lanesVisible`, `laneStates`, `tripSteps`, dans `NavigationUiState.kt` ;
  - `BottomPanelGeometry` (marge caméra quantifiée) ;
  - `AttributionMargins`, dans `core/map`.
- Les composables nouveaux sont sans état et prennent des valeurs et des rappels : `RoundaboutManeuverView`, `LaneGuidanceView`, `ExitSignPanel`, `BannerStack` / `ThenRow`, `ManeuverPreviewSheet`, `NavigationBottomPanel`, `TripDetailsSheet`. Ils sont appelés depuis `BottomBlock` ou depuis une seule ligne par overlay (`BannerStack`).
- Les états d'interface qui doivent survivre à la rotation passent par le ViewModel, en `StateFlow` séparées : feuille ouverte, panneau déplié, fraction mesurée, nom de destination. Les overlays, dont la signature est figée, n'ont que `vm` comme canal. Le `combine` de `uiState` n'est pas touché.

**Pile technique :** Kotlin 2.2.10, Jetpack Compose (BOM 2026.05.00, Material 3, `ui-test-junit4`, `DeviceConfigurationOverride`), Hilt 2.58, MapLibre Android 11.13.5 (`UiSettings.setAttributionMargins` / `attributionGravity`), kotlinx.serialization (fixtures FOSSGIS), JUnit 4.13.2, kotlinx-coroutines-test, Turbine, adb (HONOR `AREE026226000174`).

**Spécification :** `ROADLY_3D_NAVIGATION_PLAN.md`, § « Étape D — Interface premium » (D1 à D5 et critères d'acceptation). Lire aussi :
- `ROADLY_3D_NAVIGATION_TESTS.md` : scénarios 4, 5, 7, 8, 18 et 19, journal, et défaut « icône d'attribution sous la barre de navigation à 3 boutons en portrait » de la ligne C ;
- `docs/DESIGN-SYSTEM.md` ;
- `ROADLY_3D_NAVIGATION_AUDIT.md` §5 ;
- `docs/captures/etape-c/nuit-3d.png` (disposition actuelle, écran du HONOR d'environ 457 × 1 021 dp).

## Contraintes globales

**Environnement et commandes**
- Travailler uniquement dans le worktree `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d`, branche `feat/roadly-3d`, base `c33efc5`.
- Lancer Gradle avec l'outil PowerShell, toujours avec `--offline`. Les tâches utilisées sont :
  - `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q` ;
  - `:core:map:testDebugUnitTest` ;
  - `:feature:navigation:testDebugUnitTest` ;
  - `:feature:navigation:compileDebugKotlin` ;
  - `:feature:navigation:compileDebugAndroidTestKotlin` ;
  - `:feature:navigation:connectedDebugAndroidTest` ;
  - `:app:assembleDebug`.
- Pour filtrer un test : `--tests "*NomDuTest*"`.
- Les numéros de ligne cités sont ceux de `c33efc5`. Quand une tâche précédente a déjà modifié un fichier, se repérer au bloc `ancien` cité, qui sert d'ancre.

**Langue et constantes**
- Écrire en français les KDoc, commentaires, chaînes, messages de commit et noms de tests descriptifs. Les noms des tests instrumentés sont en `snake_case`, à cause de la contrainte DEX sur les espaces.
- Chaque seuil est une constante nommée avec sa KDoc :
  - `THEN_WITHIN_M` et `LANES_FROM_M` ;
  - `RoundaboutGeometry.*` ;
  - `BottomPanelGeometry.*` ;
  - `AttributionPlacement.DEFAULT_EDGE` ;
  - `LANDSCAPE_LEFT_COLUMN` et `BANNER_TOGGLE_SIZE`.

**Commits**
- Chaque commit est en français et se termine par la ligne `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

**Données et tests**
- Aucune fausse fonctionnalité. Le schéma de rond-point, le panneau et les voies n'affichent que des données Valhalla. Sans donnée, le composant est absent ou réduit, sans valeur inventée.
- Chaque limite est écrite « LIMITE » dans la KDoc ou dans `ROADLY_3D_NAVIGATION_TESTS.md`.
- Les 431 tests JVM existants restent verts. Aucune assertion existante ne change.
- Les signatures modifiées gagnent des paramètres avec une valeur par défaut, placés en fin de liste.

**Surface de fusion avec `feat/roadly-ai`** (worktree `GPS-roadly-ai`, lecture seule ; base commune `60033fe`)
- Signatures inchangées : `NavigationScreen`, `NavigationContent`, `PortraitOverlay` et `LandscapeOverlay`, ainsi que leurs lignes d'appel (`NavigationScreen.kt` 104, 110, 115, 170 et 172).
- Les lignes qui entourent les points d'insertion de l'IA ne sont pas touchées :
  - portrait 219-224 (en-tête de la colonne d'actions, puis `ReportButton(onReport)`) ;
  - portrait 235-239 (en-tête de la colonne du bas, puis `Row(verticalAlignment = Alignment.Bottom) {`) ;
  - paysage 270-271 (`Column(Modifier.align(Alignment.BottomStart)…) {`, puis `BottomBlock(state, vm, onChangeDestination)`) ;
  - paysage 274-279 (en-tête de la colonne en haut à droite, puis le premier `RoundIconButton(`).
- Le détail est donné dans la section « Surface de fusion » en fin de plan.

**Interface et appareil**
- Pas de `Color(0x…)` dans les écrans. Toutes les couleurs viennent de `LocalRoadlyColors`. Les `backgroundColor = 0xFF…` des annotations `@Preview` suivent l'usage du design system.
- Les chaînes d'interface sont en français, dans `feature/navigation/src/main/res/values/strings.xml`.
- En mode conduite, toute cible tactile fait au moins 56 dp : `RoadlySpacing.touchTargetDriving`, ou la taille par défaut de `RoundIconButton` et `GlassButton` sous `LocalDrivingMode`.
- Chaque composant nouveau a ses aperçus `@Preview` Nuit et Jour.
- Appareil : HONOR `adb -s AREE026226000174`, application `fr.roadly` (debug).
- Modifier `ROADLY_3D_NAVIGATION_TESTS.md` uniquement avec l'outil Edit, en gardant les accents.

---

## Écarts constatés (vérifiés le 2026-11-01) et arbitrages

Ces points ont été vérifiés dans le code, sur les deux fixtures FOSSGIS et dans le diff de `feat/roadly-ai`. Ils précisent les décisions prises sans les rouvrir.

1. **`rememberSaveable` dans `NavigationContent` : aucun chemin vers les overlays.**
   - Constat : le clic du bandeau est dans `PortraitOverlay` et `LandscapeOverlay`. Leurs paramètres sont figés (l'IA les modifie, lignes 193 et 260), et les lignes d'appel 170 et 172 aussi. Un `CompositionLocalProvider` autour de ces appels réindenterait ces deux lignes : conflit certain.
   - Choix : l'état des feuilles passe dans le ViewModel, sur le modèle de `alternativesUi` qui existe déjà : `guidanceSheet: StateFlow<GuidanceSheet>`, `openManeuverPreview()`, `openTripDetails()`, `closeSheet()`. Les feuilles sont rendues dans `NavigationContent` par `GuidanceSheets(state, vm)`, ajouté après la ligne 182.
   - Même raison pour `bottomPanelExpanded`. Un `rememberSaveable` dans `BottomBlock` serait perdu à la rotation, car les overlays portrait et paysage sont deux positions de composition différentes. Le ViewModel garde les deux états à la rotation.
2. **Le décalage du schéma ne se calcule pas depuis le cap d'entrée.**
   - Mesures (anneau de `begin_shape_index(ENTER)` à `begin_shape_index(EXIT)`, cap initial sphérique) :

   | Rond-point | sortie | `exitAngleDeg` (B4) | Σ écarts de cap sur l'anneau | écart entrée / anneau | écart anneau / sortie |
   |---|---|---|---|---|---|
   | Paris, manœuvre 10 | 2e | 0° | −168,1° | +84,2° | +83,9° |
   | Paris, Étoile, 12 | 5e (arrivée dans l'anneau) | — | −167,8° | +86,1° | — |
   | Autoroute, 2 | 2e | −24° | −165,3° | +70,6° | +70,7° |
   | Autoroute, 4 | 2e | −49° | −141,8° | +50,0° | +42,8° |
   | Autoroute, 6 | 1re | +48° | −20,3° | +38,6° | +29,7° |
   | Autoroute, 14 | 3e | **+140°** | **−281,9°** | +36,6° | +25,4° |

   - Sur la 3e sortie de l'autoroute, l'angle B4 (+140°) place la sortie en bas à droite, comme une 1re sortie. Le total « entrée + anneau + sortie » (−220°) ne vaut pas non plus 180° − arc, car les raccords ne sont pas tangents (25° à 86°). Seul l'arc parcouru sur l'anneau (282°) dit au conducteur « trois quarts de tour ».
   - Choix : `RoundaboutGeometry.sweepFromRing(ring)` donne l'arc. `diagramSweepDeg(info)` le borne à [40° ; 340°], avec repli sur `exitAngleDeg`. La fonction prévue `exitAngleFromRing(ringGeometry, entryBearing)` n'est pas créée : son paramètre de cap d'entrée serait inutilisé. La position de la sortie sur le schéma vaut 180° − arc, ce qui équivaut à l'« angle de sortie tiré du tracé ».
3. **Où vivent la règle et ses tests.** `ParisRoute` et `MotorwayRoute` sont dans le jeu de test de `core/navigation`, invisible depuis `feature/navigation`. La règle pure va donc dans `core/navigation/RoundaboutGeometry.kt`, avec son test à côté de `RoundaboutResolverTest`. La vue Compose reste dans `feature/navigation`.
4. **Couleurs du panneau de direction.**
   - Constat : `brandSoft` est translucide (22 % la nuit, 14 % le jour). En texte `onBrand` (blanc) sur la carte claire, le contraste est très insuffisant.
   - Choix :
     - fond `brand` plein, texte `onBrand`, bord `glassBorder`, ombre `softShadow(…, brand)` ;
     - pastille de numéro : fond `onBrand`, texte `brand` ;
     - numéro de route (« A62 ») : bord 1,5 dp `onBrand`, texte `onBrand`.
   - Contraste : blanc sur `#6D3FE0` (jour) ≈ 6:1 ; blanc sur `#8B5CF6` (nuit) ≈ 4,2:1. Les textes du panneau sont « grands » au sens WCAG (`titleMedium` ×1,3 = 22 sp en gras, `NavDigits` 24 sp), avec un seuil de 3:1 : OK.
5. **Largeur de la ligne repliée.**
   - Constat : l'écran fait environ 457 dp, soit environ 433 dp utiles. Trois `StatTile`, plus voix, Recentrer et Arrêter à 56 dp, plus les marges, donnent environ 440 dp : ça ne tient pas.
   - Choix : la ligne repliée montre **Recentrer à la place de l'icône de voix** quand la carte a été déplacée (`!following`). La voix reste dans la colonne d'actions, à son emplacement actuel.
6. **Attribution.**
   - Constat : `enableEdgeToEdge()` est actif (`MainActivity`, ligne 55), donc la carte passe sous les barres système.
   - Choix : marges et coin réglés par `MapController` (`uiSettings.attributionGravity` et `setAttributionMargins`), plutôt qu'un rembourrage de `RoadlyMap`. Rembourrer réduirait la carte : elle ne passerait plus sous les barres, et les fractions de marge de la caméra (C1) seraient faussées.
   - Emplacement en guidage :
     - **portrait** : en haut à droite, sous le bouton « replier le bandeau » (44 dp). En bas à gauche, l'icône serait sous le compteur ; en bas à droite, sous la rangée 2D/3D (capture C : bas de la colonne d'actions à environ 717 dp, haut de l'Horizon à environ 738 dp). Le haut à droite reste libre, que le panneau du bas soit replié ou déplié ;
     - **paysage** : en bas, juste à droite de la colonne gauche (46 %). C'est la seule zone basse libre : la colonne d'actions remplit la hauteur, et la rangée du compteur est en bas à droite.
   - Ailleurs (accueil, itinéraire, relecture) : en bas à gauche, au-dessus de la barre de navigation.
7. **Marge de la caméra.**
   - Ce qui est mesuré : le bloc Horizon + compte à rebours + panneau (`BottomBlock`), pas le compteur, qui est à gauche et étroit.
   - La fraction est arrondie **au-dessus** par pas de 2 %. Ainsi l'animation de dépliage ne recadre pas la caméra à chaque image (une douzaine de recadrages au plus), et la voiture reste toujours au-dessus du panneau.
   - En paysage, la fraction est ignorée par `NavigationCameraController` (règle C, `withBottomPanel`).
8. **Voies sans « active ».** Un serveur peut renvoyer `valid` sans `active`. Dans ce cas, les voies valides sont montrées comme conseillées : sinon rien ne serait mis en avant, alors que `LaneStrip` les mettait déjà en marque.
9. **« Trajet complet » : longueurs par étape.** B2 interdit d'additionner `lengthMeters` pour comparer avec le bandeau. La feuille affiche donc la longueur Valhalla de chaque étape (« sur 1,2 km »), sans distance cumulée depuis la position.
10. **Nom de destination.** La session le mémorise déjà dans `LastGuidance.destinationName` (B6), mis par `RouteViewModel` et gardé pour une alternative vers la même destination. Il est exposé par une `StateFlow` séparée (`prefs.lastGuidance.map { it?.destinationName }`), pas dans `Guiding`. Sans nom, la ligne « Destination » est masquée.
11. **Tests instrumentés.**
    - Les overlays exigent un `NavigationViewModel`. Il est construit avec les fausses implémentations du jeu principal (`FakeLocationRepository`, `FakeUserPreferencesRepository`, `FakeSpeechService`, `FakeNavigationServiceController`, `Noop*`), sans carte.
    - `PortraitOverlay` et `LandscapeOverlay` passent de `private` à `internal`. Les lignes 187 et 254 ne sont pas touchées par l'IA.
    - Le paysage est simulé par `DeviceConfigurationOverride.ForcedSize(DpSize(1021.dp, 457.dp))`. Les tailles ≥ 56 dp sont vérifiées en portrait seulement : en paysage forcé, la densité est réduite.
12. **Roadly AI.** Les emplacements `assistantButton` et `assistantPanel` n'existent pas sur cette branche ; rien n'est réservé. Après fusion, `assistantPanel()` est au-dessus du compteur, donc hors du bloc mesuré (voir LIMITES et « Surface de fusion »).

---

## Structure des fichiers

| Fichier | Rôle | Tâches |
|---|---|---|
| `corenav/RoundaboutGeometry.kt` (nouveau) | arc parcouru sur l'anneau, arc du schéma | 1 |
| `corenavtest/RoundaboutGeometryTest.kt` (nouveau) | Paris, autoroute, anneau parfait, replis | 1 |
| `nav/NavigationUiState.kt` | `roundabout`, `then`, `sign`, `showLanes`, `UpcomingUi.instruction / sign`, `ThenUi`, `ExitSignUi`, `LaneState`, `TripStepUi`, `GuidanceSheet` ; fonctions pures | 2 |
| `navtest/PremiumUiStateTest.kt` (nouveau) | règles D1–D5 de l'état | 2 |
| `nav/BottomPanelGeometry.kt` (nouveau), `navtest/BottomPanelGeometryTest.kt` (nouveau) | fraction quantifiée | 3 |
| `nav/NavigationViewModel.kt`, `navtest/NavigationViewModelTest.kt` | feuilles, panneau, fraction, destination | 3 |
| `nav/RoundaboutManeuverView.kt` (nouveau) | schéma (Canvas), `previewRoundabout`, aperçus | 4 |
| `nav/LaneStrip.kt` → `nav/LaneGuidanceView.kt` (renommé) | voies à trois états | 4 |
| `feature/navigation/build.gradle.kts` | `ui-tooling` (aperçus) (4) ; instrumentation (8) | 4, 8 |
| `feature/navigation/src/main/res/values/strings.xml` | chaînes D | 4, 5, 6 |
| `nav/ManeuverBanner.kt` | clic, rond-point compact, pastille masquée sous panneau | 5 |
| `nav/ExitSignPanel.kt`, `nav/BannerStack.kt`, `nav/ManeuverPreviewSheet.kt` (nouveaux) | panneau, « puis », pile du bandeau, aperçu | 5 |
| `nav/NavigationScreen.kt` | `BannerStack` (5) ; `GuidanceSheets` (5, 6) ; `BottomBlock`, caméra (6) ; attribution (7) ; overlays `internal` (8) | 5, 6, 7, 8 |
| `nav/NavigationBottomPanel.kt`, `nav/TripDetailsSheet.kt` (nouveaux) | panneau du bas, trajet complet | 6 |
| `map/MapAttribution.kt` (nouveau), `maptest/AttributionMarginsTest.kt` (nouveau) | coin et marges de l'attribution | 7 |
| `map/MapController.kt`, `map/RoadlyMap.kt` | `setAttribution`, paramètre `attribution` | 7 |
| `navat/TestNavigation.kt`, `navat/NavigationPremiumUiTest.kt` (nouveaux) | tests instrumentés Compose | 8 |
| `ROADLY_3D_NAVIGATION_TESTS.md`, `docs/captures/etape-d/*.png` | essais réels | 9 |

Préfixes des chemins :
- `corenav/` = `core/navigation/src/main/java/fr/roadly/core/navigation/` ;
- `corenavtest/` = `core/navigation/src/test/java/fr/roadly/core/navigation/` ;
- `nav/` = `feature/navigation/src/main/java/fr/roadly/feature/navigation/` ;
- `navtest/` = `feature/navigation/src/test/java/fr/roadly/feature/navigation/` ;
- `navat/` = `feature/navigation/src/androidTest/java/fr/roadly/feature/navigation/` ;
- `map/` = `core/map/src/main/java/fr/roadly/core/map/` ;
- `maptest/` = `core/map/src/test/java/fr/roadly/core/map/`.

---

### Tâche 1 : D2 — `RoundaboutGeometry` (pur, `core/navigation`)

**Fichiers :**
- Créer : `corenav/RoundaboutGeometry.kt`.
- Test : `corenavtest/RoundaboutGeometryTest.kt` (nouveau).

**Interfaces :**
- Consomme :
  - `RoundaboutInfo(exitCount, exitNumber, entryBearingDeg, exitBearingDeg, ringGeometry, exitStreet)` et `exitAngleDeg` ;
  - `RoundaboutResolver.resolve(route, enterIndex)` ;
  - `internal fun bearing(a: GeoPoint, b: GeoPoint): Double` (`RouteTracker.kt` 308) ;
  - `GeoPoint.distanceTo` ;
  - `normalizeSignedDeg` ;
  - `ParisRoute` et `MotorwayRoute` (test).
- Produit :
  - `object RoundaboutGeometry { const val MIN_SEGMENT_M = 0.5; const val MIN_RING_TURN_DEG = 5.0; const val MIN_DIAGRAM_SWEEP_DEG = 40.0; const val MAX_DIAGRAM_SWEEP_DEG = 340.0; const val UTURN_DEG = 175.0; fun sweepFromRing(ring: List<GeoPoint>): Double?; fun diagramSweepDeg(info: RoundaboutInfo): Double? }`

- [ ] **Étape 1 : écrire le test qui échoue.**

`corenavtest/RoundaboutGeometryTest.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.navigation.testing.TestRoutes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** D2 sur données réelles (scénarios 4 et 5) : arc parcouru sur l'anneau, tel que dessiné par `RoundaboutManeuverView`. */
class RoundaboutGeometryTest {
    private val paris = ParisRoute.route()

    /** Anneau parfait de [n] cordes, rayon 15 m, entrée au sud, parcouru dans le sens antihoraire sur [totalDeg]. */
    private fun circle(totalDeg: Double, n: Int = 36): List<GeoPoint> {
        val lat0 = 43.6045; val lon0 = 1.4440; val r = 15.0
        val mLat = 111_320.0; val mLon = 111_320.0 * cos(Math.toRadians(lat0))
        return (0..n).map { k ->
            val phi = Math.toRadians(-90.0 + totalDeg * k / n)
            GeoPoint(lat0 + r * sin(phi) / mLat, lon0 + r * cos(phi) / mLon)
        }
    }

    /** Index de la manœuvre d'entrée (26) suivie d'une sortie (27) dont le rang de sortie vaut [exitCount] (fixture autoroute). */
    private fun motorwayEntry(exitCount: Int): Int {
        val raw = MotorwayRoute.rawManeuvers
        return raw.indices.first { i ->
            raw[i]["type"]!!.jsonPrimitive.int == 26 && (raw[i]["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull == exitCount &&
                raw.getOrNull(i + 1)?.get("type")?.jsonPrimitive?.int == 27
        }
    }

    @Test fun `a perfect ring - the sweep is the heading turned between the first and the last chord`() {
        assertEquals(270.0 * 35 / 36, RoundaboutGeometry.sweepFromRing(circle(270.0))!!, 0.2)
        assertEquals(90.0 * 35 / 36, RoundaboutGeometry.sweepFromRing(circle(90.0))!!, 0.2)
    }

    @Test fun `champs-elysees - second exit straight on, arc of 168 degrees (scenario 4)`() {
        val info = RoundaboutResolver.resolve(paris, 10)
        assertEquals(168.1, RoundaboutGeometry.sweepFromRing(info.ringGeometry)!!, 0.5)
        assertEquals(168.1, RoundaboutGeometry.diagramSweepDeg(info)!!, 0.5)
    }

    @Test fun `etoile - fifth exit, ring up to the arrival, no exit branch`() {
        val info = RoundaboutResolver.resolve(paris, 12)
        assertEquals(167.8, RoundaboutGeometry.diagramSweepDeg(info)!!, 0.5)
        assertNull("pas de manœuvre de sortie : pas de branche dessinée", info.exitBearingDeg)
    }

    @Test fun `motorway third exit - the arc passes the top although the exit bearing reads +140 (scenario 5)`() {
        val info = RoundaboutResolver.resolve(MotorwayRoute.route(), motorwayEntry(3))
        assertEquals(140.0, info.exitAngleDeg!!, 0.0)
        assertEquals(281.9, RoundaboutGeometry.diagramSweepDeg(info)!!, 0.5)
    }

    @Test fun `motorway first exit - a short arc is widened so the exit never sits on the entry`() {
        val info = RoundaboutResolver.resolve(MotorwayRoute.route(), motorwayEntry(1))
        assertEquals(20.3, RoundaboutGeometry.sweepFromRing(info.ringGeometry)!!, 0.5)
        assertEquals(RoundaboutGeometry.MIN_DIAGRAM_SWEEP_DEG, RoundaboutGeometry.diagramSweepDeg(info)!!, 0.0)
    }

    @Test fun `a straight ring gives no sweep and, without bearings, only the ring is drawn`() {
        val info = RoundaboutResolver.resolve(TestRoutes.twoRoundabouts(), 1)
        assertNull(RoundaboutGeometry.sweepFromRing(info.ringGeometry))
        assertNull(RoundaboutGeometry.diagramSweepDeg(info))
    }

    @Test fun `without a curved ring, the bearings give the arc - right, left, u-turn`() {
        val straightRing = TestRoutes.twoRoundabouts().geometry.subList(20, 24)
        fun info(exitBearing: Double) = RoundaboutInfo(null, 2, 0.0, exitBearing, straightRing, null)
        assertEquals(90.0, RoundaboutGeometry.diagramSweepDeg(info(90.0))!!, 1e-9)
        assertEquals(270.0, RoundaboutGeometry.diagramSweepDeg(info(270.0))!!, 1e-9)
        assertEquals("demi-tour : ±180° lu à gauche, arc maximal", RoundaboutGeometry.MAX_DIAGRAM_SWEEP_DEG, RoundaboutGeometry.diagramSweepDeg(info(180.0))!!, 1e-9)
    }

    @Test fun `repeated points are ignored`() {
        val ring = circle(180.0, n = 12)
        assertEquals(RoundaboutGeometry.sweepFromRing(ring)!!, RoundaboutGeometry.sweepFromRing(ring.flatMap { listOf(it, it) })!!, 1e-9)
    }
}
```

- [ ] **Étape 2 : vérifier l'échec.**

Commande : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*RoundaboutGeometryTest*"`
Résultat attendu : ÉCHEC de compilation, `Unresolved reference: RoundaboutGeometry`.

- [ ] **Étape 3 : écrire l'implémentation.**

`corenav/RoundaboutGeometry.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.normalizeSignedDeg
import kotlin.math.abs

/**
 * D2 : géométrie du schéma de rond-point, tirée du tracé réel de l'anneau (Kotlin pur).
 * L'arc parcouru (somme des écarts de cap le long de l'anneau) place la sortie ; le cap de sortie B4 n'est qu'un repli,
 * car les raccords entrée / sortie ne sont pas tangents (3e sortie de la fixture autoroute : cap +140°, arc 282°).
 * LIMITE : circulation à droite seulement (anneau antihoraire) ; les autres sorties ne sont jamais dessinées.
 */
object RoundaboutGeometry {
    /** Segments plus courts ignorés (m) : points doublés du tracé, cap indéfini. */
    const val MIN_SEGMENT_M = 0.5
    /** Courbure minimale (°) pour qu'un tracé soit lu comme un anneau ; en dessous (tracé rectiligne de test, mini-giratoire), repli sur les caps. */
    const val MIN_RING_TURN_DEG = 5.0
    /** Arc dessiné minimal (°) : la branche de sortie ne se confond jamais avec la branche d'entrée. */
    const val MIN_DIAGRAM_SWEEP_DEG = 40.0
    /** Arc dessiné maximal (°) : un demi-tour reste lisible, sortie juste à gauche de l'entrée. */
    const val MAX_DIAGRAM_SWEEP_DEG = 340.0
    /** Au-delà (°), l'angle de sortie B4 est lu comme un demi-tour : le signe de ±180° n'est pas fiable. */
    const val UTURN_DEG = 175.0

    /** Arc parcouru sur l'anneau (°, > 0 = sens antihoraire) = −Σ des écarts de cap signés entre segments successifs ; null si le tracé ne tourne pas. */
    fun sweepFromRing(ring: List<GeoPoint>): Double? {
        val headings = ring.zipWithNext()
            .filter { (a, b) -> a.distanceTo(b) >= MIN_SEGMENT_M }
            .map { (a, b) -> bearing(a, b) }
        if (headings.size < 2) return null
        val turn = headings.zipWithNext().sumOf { (a, b) -> normalizeSignedDeg(b - a) }
        return if (abs(turn) < MIN_RING_TURN_DEG) null else -turn
    }

    /** Arc à dessiner (°, borné) : celui de l'anneau, sinon 180° − angle de sortie B4 ; null = aucune donnée (anneau et entrée seuls). */
    fun diagramSweepDeg(info: RoundaboutInfo): Double? {
        val sweep = sweepFromRing(info.ringGeometry)
            ?: info.exitAngleDeg?.let { a -> 180.0 - (if (a >= UTURN_DEG) -180.0 else a) }
            ?: return null
        return sweep.coerceIn(MIN_DIAGRAM_SWEEP_DEG, MAX_DIAGRAM_SWEEP_DEG)
    }
}
```

- [ ] **Étape 4 : vérifier le succès.**

Commande : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q`
Résultat attendu : SUCCÈS, avec les 8 nouveaux tests et les tests existants de `core:navigation` verts.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/RoundaboutGeometry.kt core/navigation/src/test/java/fr/roadly/core/navigation/RoundaboutGeometryTest.kt
git commit -m @'
feat(guidage): arc parcouru sur l'anneau du rond-point, base du schéma (D2)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 2 : D1–D5 — état d'écran : rond-point, « puis », panneau, voies, trajet complet

**Fichiers :**
- Modifier `nav/NavigationUiState.kt` :
  - lignes 3-16 (imports) ;
  - ligne 33 (`UpcomingUi`) ;
  - lignes 44-73 (`NavigationUiState`, champs ajoutés en fin) ;
  - lignes 114-148 (branche `Guiding` de `buildUiState`) ;
  - nouvelles déclarations en fin de fichier.
- Test : `navtest/PremiumUiStateTest.kt` (nouveau).

**Interfaces :**
- Consomme :
  - `NavigationState.Guiding.roundabout` et `.upcoming` ;
  - `RouteProgress.distancesToManeuversM` ;
  - `Maneuver.exitNumber`, `exitToward`, `exitBranch`, `instruction`, `streetName`, `lengthMeters` ;
  - `Lane(directions, valid, active)` ;
  - `GuidanceCameraPolicy.isApproaching`.
- Produit :
  - `data class ThenUi(val type: ManeuverType, val label: String)`
  - `data class ExitSignUi(val exitNumber: String?, val toward: String?, val branch: String?)`
  - `enum class LaneState { ACTIVE, VALID, INVALID }`
  - `data class TripStepUi(val index: Int, val type: ManeuverType, val instruction: String, val street: String?, val lengthText: String?, val passed: Boolean, val isNext: Boolean)`
  - `enum class GuidanceSheet { NONE, MANEUVER_PREVIEW, TRIP_DETAILS }`
  - `UpcomingUi(type, distanceText, street, distanceM = 0.0, instruction: String? = null, sign: ExitSignUi? = null)`
  - `NavigationUiState.roundabout: RoundaboutInfo?`, `.then: ThenUi?`, `.sign: ExitSignUi?`, `.showLanes: Boolean`
  - `internal const val THEN_WITHIN_M = 500.0`, `internal const val LANES_FROM_M = 400.0`
  - `internal fun exitSignFor(m: Maneuver): ExitSignUi?`, `internal fun thenFor(next: Maneuver?, gapM: Double?): ThenUi?`, `internal fun lanesVisible(lanes: List<Lane>, distanceToNextM: Double, approaching: Boolean): Boolean`, `internal fun laneStates(lanes: List<Lane>): List<LaneState>`, `internal fun tripSteps(route: Route, nextIndex: Int): List<TripStepUi>`

- [ ] **Étape 1 : écrire les tests qui échouent.**

`navtest/PremiumUiStateTest.kt` :

```kotlin
package fr.roadly.feature.navigation

import fr.roadly.core.common.Formatters
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RoundaboutResolver
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Étape D : données du bandeau premium (rond-point, « puis », panneau, voies) et de la feuille « Trajet complet ». */
class PremiumUiStateTest {
    private val zone = ZoneId.of("Europe/Paris")

    /** `Guiding` à l'abscisse [along] de [r], avec les voies et le rond-point que publierait la session. */
    private fun guiding(r: Route, along: Double): NavigationState.Guiding {
        val t = RouteTracker(r)
        val p = t.update(LocationFix(t.pointAt(along), null, null, 5f, 0L))
        val ms = r.maneuvers
        val upcoming = ms.subList(minOf(p.nextManeuverIndex + 1, ms.size), minOf(p.nextManeuverIndex + 4, ms.size))
        val roundabout = RoundaboutResolver.current(RoundaboutResolver.resolveAll(r), p.nextManeuverIndex)
        return NavigationState.Guiding(r, p, 36.0, null, upcoming, ms[p.nextManeuverIndex].lanes, 0L, gpsLost = false, rerouting = false, roundabout = roundabout)
    }

    private fun ui(s: NavigationState) = buildUiState(s, muted = false, following = true, toleranceKmh = 5, styleUrl = "", zone = zone)

    private val twoLanes = listOf(Lane(setOf(LaneDirection.STRAIGHT), valid = false), Lane(setOf(LaneDirection.RIGHT), valid = true, active = true))

    @Test fun `the roundabout reaches the banner only when the next maneuver is in a roundabout`() {
        val r = TestRoutes.twoRoundabouts()
        val before = ui(guiding(r, 120.0))
        assertEquals(ManeuverType.ROUNDABOUT_ENTER, before.banner!!.type)
        assertEquals(RoundaboutResolver.resolveAll(r)[1], before.roundabout)
        val turn = guiding(TestRoutes.straight(), 300.0).copy(roundabout = RoundaboutResolver.resolveAll(r)[1])
        assertNull("prochaine manœuvre = virage : pas de schéma", ui(turn).roundabout)
    }

    @Test fun `the following maneuver is shown under the banner when it comes within 500 m`() {
        val then = ui(guiding(TestRoutes.twoRoundabouts(), 120.0)).then!!   // entrée à 200 m, sortie 30 m plus loin
        assertEquals(ManeuverType.ROUNDABOUT_EXIT, then.type)
        assertEquals("Rue Médiane", then.label)
        val straight = TestRoutes.straight()
        val t = RouteTracker(straight)
        assertTrue(t.maneuverStartM[2] - t.maneuverStartM[1] >= THEN_WITHIN_M)
        assertNull("arrivée à plus de 500 m après le virage", ui(guiding(straight, 300.0)).then)
    }

    @Test fun `then uses the street, else the instruction, and stops at 500 m`() {
        val arrive = TestRoutes.straight().maneuvers[2]
        assertEquals("Vous êtes arrivé.", thenFor(arrive, 499.0)!!.label)
        assertNull(thenFor(arrive, 500.0)); assertNull(thenFor(null, 10.0)); assertNull(thenFor(arrive, null))
    }

    @Test fun `an exit sign is kept for exits, ramps, forks and merges that carry sign data`() {
        val base = TestRoutes.straight().maneuvers[1]
        val exit = base.copy(type = ManeuverType.EXIT_RIGHT, exitNumber = "31b", exitToward = "Toulouse-Centre · Blagnac", exitBranch = "A62")
        assertEquals(ExitSignUi("31b", "Toulouse-Centre · Blagnac", "A62"), exitSignFor(exit))
        assertEquals(ExitSignUi(null, "Montpellier", null), exitSignFor(base.copy(type = ManeuverType.KEEP_LEFT, exitToward = "Montpellier")))
        assertNull("virage : pas de panneau", exitSignFor(base.copy(exitNumber = "12")))
        assertNull("bretelle sans données de panneau", exitSignFor(base.copy(type = ManeuverType.RAMP_RIGHT)))
    }

    @Test fun `the sign of the next maneuver reaches the state before the approach`() {
        val r0 = TestRoutes.straight()
        val r = r0.copy(maneuvers = r0.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(type = ManeuverType.EXIT_RIGHT, exitNumber = "19", exitToward = "Balma") else m })
        val far = ui(guiding(r, 100.0))
        assertEquals(ExitSignUi("19", "Balma", null), far.sign)
        assertNull("loin : pas d'approche, le panneau n'est que dans l'aperçu", far.approach)
    }

    @Test fun `upcoming maneuvers carry their instruction and sign`() {
        val r = TestRoutes.straight()
        val exit = r.maneuvers[1].copy(type = ManeuverType.EXIT_LEFT, exitNumber = "20", exitToward = "Albi")
        val u = ui(guiding(r, 100.0).copy(upcoming = listOf(exit))).upcoming.single()
        assertEquals(exit.instruction, u.instruction)
        assertEquals(ExitSignUi("20", "Albi", null), u.sign)
    }

    @Test fun `lanes show from 400 m or at the approach, never without data`() {
        assertFalse(lanesVisible(twoLanes, 450.0, approaching = false))
        assertTrue(lanesVisible(twoLanes, 399.0, approaching = false))
        assertTrue("approche verrouillée au-delà de 400 m", lanesVisible(twoLanes, 600.0, approaching = true))
        assertFalse("sans données : rien", lanesVisible(emptyList(), 50.0, approaching = true))
    }

    @Test fun `lanes of the next maneuver appear within 400 m and go after it (scenario 18)`() {
        val r0 = TestRoutes.straight()
        val r = r0.copy(maneuvers = r0.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(lanes = twoLanes) else m })
        val turnAt = RouteTracker(r).maneuverStartM[1]
        assertFalse(ui(guiding(r, turnAt - 450.0)).showLanes)
        val near = ui(guiding(r, turnAt - 390.0))
        assertTrue(near.showLanes); assertEquals(twoLanes, near.lanes)
        assertFalse("après le virage : voies de l'arrivée (aucune)", ui(guiding(r, turnAt + 30.0)).showLanes)
    }

    @Test fun `lane states - advised, possible, forbidden, and valid lanes advised when none is active`() {
        assertEquals(
            listOf(LaneState.VALID, LaneState.ACTIVE, LaneState.INVALID),
            laneStates(listOf(Lane(setOf(LaneDirection.STRAIGHT), true), Lane(setOf(LaneDirection.RIGHT), true, active = true), Lane(setOf(LaneDirection.LEFT), false))),
        )
        assertEquals(
            listOf(LaneState.ACTIVE, LaneState.INVALID),
            laneStates(listOf(Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), true), Lane(setOf(LaneDirection.LEFT), false))),
        )
    }

    @Test fun `the full trip lists every step, passed ones marked, with Valhalla step lengths`() {
        val r = TestRoutes.straight()
        val steps = tripSteps(r, nextIndex = 1)
        assertEquals(3, steps.size)
        assertTrue(steps[0].passed); assertFalse(steps[0].isNext)
        assertTrue(steps[1].isNext); assertFalse(steps[1].passed)
        assertEquals("Rue d'Essai", steps[1].street)
        assertEquals(Formatters.distance(r.maneuvers[1].lengthMeters), steps[1].lengthText)
        assertNull("arrivée : pas de longueur", steps[2].lengthText)
    }
}
```

- [ ] **Étape 2 : vérifier l'échec.**

Commande : `.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q --tests "*PremiumUiStateTest*"`
Résultat attendu : ÉCHEC de compilation, avec par exemple `Unresolved reference: thenFor`, `ExitSignUi`, `laneStates`, `tripSteps`, `then`.

- [ ] **Étape 3 : implémenter dans `nav/NavigationUiState.kt`.**

**1. Imports.** Ancien (lignes 9-13) :
```kotlin
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.navigation.NavigationState
```
Nouveau :
```kotlin
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RoundaboutInfo
```

**2. `UpcomingUi`.** Ancien (lignes 32-33) :
```kotlin
/** Manœuvre à venir ; [distanceM] sert au ruban Horizon (échelle), [distanceText] à l'affichage. */
data class UpcomingUi(val type: ManeuverType, val distanceText: String, val street: String?, val distanceM: Double = 0.0)
```
Nouveau :
```kotlin
/** Manœuvre à venir ; [distanceM] sert au ruban Horizon (échelle), [distanceText] à l'affichage ; [instruction] et [sign] servent à l'aperçu (D1, D3). */
data class UpcomingUi(
    val type: ManeuverType,
    val distanceText: String,
    val street: String?,
    val distanceM: Double = 0.0,
    val instruction: String? = null,
    val sign: ExitSignUi? = null,
)

/** D1 : manœuvre d'après, montrée sous le bandeau (« puis ↱ Rue X ») quand elle suit de près. */
data class ThenUi(val type: ManeuverType, val label: String)

/** D3 : panneau d'une manœuvre (sortie, bretelle, fourche, fusion), tel que Valhalla le décrit ; [toward] = « A · B » (2 directions au plus). */
data class ExitSignUi(val exitNumber: String?, val toward: String?, val branch: String?)

/** D4 : rendu d'une voie : conseillée, possible mais non conseillée, interdite pour la manœuvre. */
enum class LaneState { ACTIVE, VALID, INVALID }

/** D5 : étape de la feuille « Trajet complet » ; [lengthText] = longueur Valhalla de l'étape (null à l'arrivée), jamais cumulée (B2). */
data class TripStepUi(
    val index: Int,
    val type: ManeuverType,
    val instruction: String,
    val street: String?,
    val lengthText: String?,
    val passed: Boolean,
    val isNext: Boolean,
)

/** Feuille ouverte au-dessus du guidage : aperçu de la manœuvre (D1) ou trajet complet (D5). */
enum class GuidanceSheet { NONE, MANEUVER_PREVIEW, TRIP_DETAILS }
```

**3. Champs de `NavigationUiState`.** Ancien (lignes 71-73) :
```kotlin
    val following: Boolean = true,
    val error: AppError? = null,
)
```
Nouveau :
```kotlin
    val following: Boolean = true,
    val error: AppError? = null,
    /** D2 : rond-point de la prochaine manœuvre (entrée ou sortie d'anneau) ; null sinon. */
    val roundabout: RoundaboutInfo? = null,
    /** D1 : manœuvre d'après quand elle suit la prochaine de moins de [THEN_WITHIN_M]. */
    val then: ThenUi? = null,
    /** D3 : panneau de la prochaine manœuvre, si Valhalla en fournit un (bandeau à l'approche, aperçu toujours). */
    val sign: ExitSignUi? = null,
    /** D4 : voies montrées sous le bandeau (dans les [LANES_FROM_M] derniers mètres ou à l'approche, jamais sans données). */
    val showLanes: Boolean = false,
)
```

**4. Branche `Guiding` de `buildUiState`.** Ancien (lignes 119-131) :
```kotlin
            // B2 : distances aux manœuvres suivantes fournies par le suivi, sur la même abscisse que le bandeau ; sans distance, pas d'élément.
            val upcoming = s.upcoming.mapIndexedNotNull { i, u ->
                val d = p.distancesToManeuversM.getOrNull(i) ?: return@mapIndexedNotNull null
                UpcomingUi(u.type, Formatters.maneuverDistance(d), u.streetName, d)
            }
            base.copy(
                phase = NavigationPhase.GUIDING,
                route = s.route,
                progress = p,
                banner = BannerUi(m.type, Formatters.maneuverDistance(p.distanceToNextManeuverM), m.instruction, m.streetName, m.exitNumber, m.roundaboutExit, m.exitToward),
                approach = if (approachLatched(previous, s.route.id, p.nextManeuverIndex, GuidanceCameraPolicy.isApproaching(p.distanceToNextManeuverM, m.type, s.speedKmh))) {
                    ApproachUi(m.type, m.exitNumber, m.exitToward, m.beginShapeIndex)
                } else null,
```
Nouveau :
```kotlin
            // B2 : distances aux manœuvres suivantes fournies par le suivi, sur la même abscisse que le bandeau ; sans distance, pas d'élément.
            val upcoming = s.upcoming.mapIndexedNotNull { i, u ->
                val d = p.distancesToManeuversM.getOrNull(i) ?: return@mapIndexedNotNull null
                UpcomingUi(u.type, Formatters.maneuverDistance(d), u.streetName, d, u.instruction, exitSignFor(u))
            }
            val approaching = approachLatched(previous, s.route.id, p.nextManeuverIndex, GuidanceCameraPolicy.isApproaching(p.distanceToNextManeuverM, m.type, s.speedKmh))
            base.copy(
                phase = NavigationPhase.GUIDING,
                route = s.route,
                progress = p,
                banner = BannerUi(m.type, Formatters.maneuverDistance(p.distanceToNextManeuverM), m.instruction, m.streetName, m.exitNumber, m.roundaboutExit, m.exitToward),
                approach = if (approaching) ApproachUi(m.type, m.exitNumber, m.exitToward, m.beginShapeIndex) else null,
                roundabout = s.roundabout?.takeIf { m.type == ManeuverType.ROUNDABOUT_ENTER || m.type == ManeuverType.ROUNDABOUT_EXIT },
                then = thenFor(s.upcoming.firstOrNull(), p.distancesToManeuversM.firstOrNull()?.let { it - p.distanceToNextManeuverM }),
                sign = exitSignFor(m),
                showLanes = lanesVisible(s.lanes, p.distanceToNextManeuverM, approaching),
```
Les lignes suivantes (`lanes = s.lanes,` … `rerouting = s.rerouting,`) ne changent pas.

**5. Fin de fichier.** Ajouter après l'accolade finale de `buildUiState` (ligne 150) :
```kotlin

/** D1 : la manœuvre d'après est montrée sous le bandeau quand elle suit la prochaine de moins de 500 m. */
internal const val THEN_WITHIN_M = 500.0

/** D4 : voies montrées à partir de 400 m de la manœuvre (même seuil que l'approche d'une sortie). */
internal const val LANES_FROM_M = 400.0

/** D3 : manœuvres qui portent un panneau de direction. */
private val SIGN_TYPES = setOf(
    ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT,
    ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT, ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT,
)

/** D3 : panneau de [m] (numéro, directions, route) ; null hors sortie / bretelle / fourche / fusion ou sans aucune donnée de panneau. */
internal fun exitSignFor(m: Maneuver): ExitSignUi? {
    if (m.type !in SIGN_TYPES) return null
    if (m.exitNumber == null && m.exitToward == null && m.exitBranch == null) return null
    return ExitSignUi(m.exitNumber, m.exitToward, m.exitBranch)
}

/** D1 : « puis … » pour [next] situé [gapM] mètres après la prochaine manœuvre ; rue, sinon instruction. */
internal fun thenFor(next: Maneuver?, gapM: Double?): ThenUi? {
    if (next == null || gapM == null || gapM >= THEN_WITHIN_M) return null
    return ThenUi(next.type, next.streetName ?: next.instruction)
}

/** D4 : voies visibles dans les [LANES_FROM_M] derniers mètres ou à l'approche ; jamais sans données (LIMITE : FOSSGIS public n'en renvoie pas). */
internal fun lanesVisible(lanes: List<Lane>, distanceToNextM: Double, approaching: Boolean): Boolean =
    lanes.isNotEmpty() && (approaching || distanceToNextM < LANES_FROM_M)

/** D4 : état de chaque voie ; sans voie « active » (serveur qui ne renvoie que `valid`), les voies valides sont les voies conseillées. */
internal fun laneStates(lanes: List<Lane>): List<LaneState> {
    val anyActive = lanes.any { it.active }
    return lanes.map { l ->
        when {
            l.active -> LaneState.ACTIVE
            l.valid && !anyActive -> LaneState.ACTIVE
            l.valid -> LaneState.VALID
            else -> LaneState.INVALID
        }
    }
}

/** D5 : toutes les étapes de [route] ; celles avant [nextIndex] sont passées. Longueur Valhalla de l'étape, non cumulée (B2). */
internal fun tripSteps(route: Route, nextIndex: Int): List<TripStepUi> = route.maneuvers.mapIndexed { i, m ->
    TripStepUi(
        index = i, type = m.type, instruction = m.instruction, street = m.streetName,
        lengthText = m.lengthMeters.takeIf { it > 0.0 && m.type != ManeuverType.ARRIVE }?.let { Formatters.distance(it) },
        passed = i < nextIndex, isNext = i == nextIndex,
    )
}
```

- [ ] **Étape 4 : vérifier le succès.**

Commande : `.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q`
Résultat attendu : SUCCÈS. Les 10 nouveaux tests passent, et `BuildUiStateTest` et `NavigationViewModelTest` restent inchangés et verts.

- [ ] **Étape 5 : commiter.**

```powershell
git add feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationUiState.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/PremiumUiStateTest.kt
git commit -m @'
feat(guidage): état d'écran premium — rond-point, « puis », panneau, fenêtre des voies, étapes du trajet (D1–D5)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 3 : D1 / D5 — ViewModel : feuilles, panneau du bas, hauteur mesurée, destination

**Fichiers :**
- Créer : `nav/BottomPanelGeometry.kt`.
- Modifier :
  - `nav/NavigationViewModel.kt`, lignes 33-36 (imports) ;
  - le même fichier, nouveau bloc inséré après la ligne 129 (fin de `mapDetail`).
- Test :
  - `navtest/BottomPanelGeometryTest.kt` (nouveau) ;
  - `navtest/NavigationViewModelTest.kt`, 2 tests ajoutés avant l'accolade finale de la classe (ligne 323).

**Interfaces :**
- Consomme : `GuidanceSheet` (tâche 2), `UserPreferencesRepository.lastGuidance: Flow<LastGuidance?>`, `FakeUserPreferencesRepository.setLastGuidance`.
- Produit :
  - `internal object BottomPanelGeometry { const val STEP = 0.02f; const val MAX_FRACTION = 0.6f; fun fraction(panelTopPx: Float, rootHeightPx: Float): Float? }`
  - Dans `NavigationViewModel` :
    - `val guidanceSheet: StateFlow<GuidanceSheet>`, `fun openManeuverPreview()`, `fun openTripDetails()`, `fun closeSheet()` ;
    - `val bottomPanelExpanded: StateFlow<Boolean>`, `fun toggleBottomPanel()` ;
    - `val bottomPanelFraction: StateFlow<Float?>`, `fun setBottomPanelFraction(fraction: Float?)` ;
    - `val destinationName: StateFlow<String?>`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`navtest/BottomPanelGeometryTest.kt` :

```kotlin
package fr.roadly.feature.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** D5 : part de l'écran couverte par le bloc du bas, arrondie au-dessus par pas de 2 %. */
class BottomPanelGeometryTest {
    @Test fun `the fraction is measured from the bottom and rounded up to 2 percent`() {
        assertEquals(0.30f, BottomPanelGeometry.fraction(700f, 1000f)!!, 1e-4f)
        assertEquals("0,281 → 0,30 : la voiture reste au-dessus du panneau", 0.30f, BottomPanelGeometry.fraction(719f, 1000f)!!, 1e-4f)
        assertEquals(0.28f, BottomPanelGeometry.fraction(720f, 1000f)!!, 1e-4f)
    }

    @Test fun `the fraction is capped and an unknown height gives nothing`() {
        assertEquals(BottomPanelGeometry.MAX_FRACTION, BottomPanelGeometry.fraction(100f, 1000f)!!, 1e-4f)
        assertEquals(0f, BottomPanelGeometry.fraction(1000f, 1000f)!!, 1e-4f)
        assertNull(BottomPanelGeometry.fraction(500f, 0f))
    }
}
```

Dans `navtest/NavigationViewModelTest.kt` :
- ajouter les imports `fr.roadly.core.data.repository.LastGuidance` et `fr.roadly.core.model.GeoPoint` ;
- ajouter ces deux tests avant l'accolade finale de la classe (après le test `with the 3d setting off…`) :

```kotlin
    @Test fun `sheets, the bottom panel and its measured height are kept by the view model`() = runTest {
        val vm = NavigationViewModel(session(), prefs, config); runCurrent()
        assertEquals(GuidanceSheet.NONE, vm.guidanceSheet.value)
        vm.openManeuverPreview()
        assertEquals(GuidanceSheet.MANEUVER_PREVIEW, vm.guidanceSheet.value)
        vm.openTripDetails()
        assertEquals(GuidanceSheet.TRIP_DETAILS, vm.guidanceSheet.value)
        vm.closeSheet()
        assertEquals(GuidanceSheet.NONE, vm.guidanceSheet.value)
        assertFalse(vm.bottomPanelExpanded.value)
        vm.toggleBottomPanel(); assertTrue(vm.bottomPanelExpanded.value)
        vm.toggleBottomPanel(); assertFalse(vm.bottomPanelExpanded.value)
        assertNull(vm.bottomPanelFraction.value)
        vm.setBottomPanelFraction(0.3f)
        assertEquals(0.3f, vm.bottomPanelFraction.value!!, 0f)
    }

    @Test fun `the destination name comes from the remembered guidance`() = runTest {
        val vm = NavigationViewModel(session(), prefs, config)
        vm.destinationName.test {
            assertNull(awaitItem())
            prefs.setLastGuidance(LastGuidance(GeoPoint(43.6045, 1.4440), "Capitole", "r1", 0L))
            assertEquals("Capitole", awaitItem())
            prefs.setLastGuidance(null)
            assertNull(awaitItem())
        }
    }
```

- [ ] **Étape 2 : vérifier l'échec.**

Commande : `.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q --tests "*BottomPanelGeometryTest*" --tests "*NavigationViewModelTest*"`
Résultat attendu : ÉCHEC de compilation, avec `Unresolved reference: BottomPanelGeometry`, `guidanceSheet` et `destinationName`.

- [ ] **Étape 3 : implémenter.**

`nav/BottomPanelGeometry.kt` :

```kotlin
package fr.roadly.feature.navigation

import kotlin.math.ceil

/** D5 : part de la hauteur de l'écran couverte par le bloc du bas (Horizon + panneau), pour la marge basse de la caméra. */
internal object BottomPanelGeometry {
    /** Pas de quantification : la caméra n'est recadrée que pour un écart de 2 % de la hauteur (≈ 20 dp), pas à chaque image d'animation. */
    const val STEP = 0.02f
    /** Plafond : au-delà, la voiture n'aurait plus de place au-dessus du panneau. */
    const val MAX_FRACTION = 0.6f

    /** Fraction couverte sous [panelTopPx] (haut du bloc dans la racine), arrondie au-dessus ; null si la hauteur est inconnue. */
    fun fraction(panelTopPx: Float, rootHeightPx: Float): Float? {
        if (rootHeightPx <= 0f || panelTopPx.isNaN()) return null
        val raw = ((rootHeightPx - panelTopPx) / rootHeightPx).coerceIn(0f, MAX_FRACTION)
        return (ceil(raw / STEP - 1e-3f) * STEP).coerceIn(0f, MAX_FRACTION)
    }
}
```

`nav/NavigationViewModel.kt` :

**1. Import.** Ancien (lignes 34-35) :
```kotlin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.scan
```
Nouveau :
```kotlin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
```

**2. Nouveau bloc.** Ancre, lignes 128-129 :
```kotlin
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapDetail.NORMAL)
```
Insérer juste après :
```kotlin

    private val _sheet = MutableStateFlow(GuidanceSheet.NONE)
    /**
     * D1 / D5 : feuille ouverte au-dessus du guidage. Elle est dans le ViewModel, pas en `rememberSaveable` : les overlays, à signature figée,
     * n'ont que `vm` pour l'ouvrir. Elle est gardée à la rotation.
     */
    val guidanceSheet: StateFlow<GuidanceSheet> = _sheet

    fun openManeuverPreview() { _sheet.value = GuidanceSheet.MANEUVER_PREVIEW }
    fun openTripDetails() { _sheet.value = GuidanceSheet.TRIP_DETAILS }
    fun closeSheet() { _sheet.value = GuidanceSheet.NONE }

    private val _panelExpanded = MutableStateFlow(false)
    /** D5 : panneau du bas déplié (destination, « Trajet complet », « Itinéraires ») ; gardé à la rotation. */
    val bottomPanelExpanded: StateFlow<Boolean> = _panelExpanded

    fun toggleBottomPanel() { _panelExpanded.value = !_panelExpanded.value }

    private val _panelFraction = MutableStateFlow<Float?>(null)
    /** D5 : part de l'écran couverte par le bloc du bas, mesurée par l'écran (`BottomPanelGeometry`) → marge basse de la caméra. */
    val bottomPanelFraction: StateFlow<Float?> = _panelFraction

    fun setBottomPanelFraction(fraction: Float?) { _panelFraction.value = fraction }

    /** D5 : nom de la destination mémorisé par la session (B6, `lastGuidance`) ; null si la course a démarré sans nom. */
    val destinationName: StateFlow<String?> = prefs.lastGuidance.map { it?.destinationName }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
```

- [ ] **Étape 4 : vérifier le succès.**

Commande : `.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q`
Résultat attendu : SUCCÈS.

- [ ] **Étape 5 : commiter.**

```powershell
git add feature/navigation/src/main/java/fr/roadly/feature/navigation/BottomPanelGeometry.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/BottomPanelGeometryTest.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt
git commit -m @'
feat(guidage): feuilles, panneau du bas, hauteur mesurée et destination portés par le ViewModel (D1, D5)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 4 : D2 + D4 — `RoundaboutManeuverView` et `LaneGuidanceView` (remplace `LaneStrip`)

**Fichiers :**
- Créer : `nav/RoundaboutManeuverView.kt`.
- Renommer : `git mv feature/navigation/src/main/java/fr/roadly/feature/navigation/LaneStrip.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/LaneGuidanceView.kt`, puis remplacer tout le contenu.
- Modifier :
  - `feature/navigation/build.gradle.kts`, ligne 25 (aperçus) ;
  - `feature/navigation/src/main/res/values/strings.xml`, après la ligne 44 ;
  - `nav/NavigationScreen.kt`, lignes 204 et 267 : les deux appels `LaneStrip(...)` sont renommés, pour que le code compile ; ces lignes sont remplacées en entier à la tâche 5.

**Interfaces :**
- Consomme :
  - `RoundaboutGeometry.diagramSweepDeg(info)` (tâche 1) ;
  - `RoundaboutInfo` ;
  - `laneStates`, `LaneState` (tâche 2) ;
  - `LaneDirection` ;
  - `GlassPanel`, `LocalRoadlyColors`, `NavDigits`, `RoadlyShapes`.
- Produit :
  - `@Composable fun RoundaboutManeuverView(info: RoundaboutInfo, exitNumber: Int?, compact: Boolean, modifier: Modifier = Modifier)`
  - `internal fun previewRoundabout(sweepDeg: Double, exitNumber: Int?, street: String?, withExit: Boolean = true): RoundaboutInfo` (aperçus et tests instrumentés)
  - `@Composable fun LaneGuidanceView(lanes: List<Lane>, emphasized: Boolean, modifier: Modifier = Modifier)`
  - `internal fun LaneDirection.icon(): ImageVector` (déplacée sans changement)
  - Chaînes `nav_roundabout_desc`, `nav_roundabout_desc_unknown`, `nav_lanes_desc`.

- [ ] **Étape 1 : préparer le module pour les aperçus.**

`feature/navigation/build.gradle.kts`, ancien (ligne 25) :
```kotlin
    implementation(libs.serialization.json)
```
Nouveau :
```kotlin
    implementation(libs.serialization.json)
    // Aperçus Compose des composants D (rendu dans Android Studio).
    debugImplementation(libs.compose.ui.tooling)
```

`strings.xml`, ancien (ligne 44) :
```xml
    <string name="nav_alternatives">Itinéraires</string>
```
Nouveau :
```xml
    <string name="nav_alternatives">Itinéraires</string>
    <string name="nav_roundabout_desc">Rond-point, %1$s</string>
    <string name="nav_roundabout_desc_unknown">Rond-point</string>
    <string name="nav_lanes_desc">Voies conseillées : %1$s sur %2$d</string>
```

- [ ] **Étape 2 : écrire `nav/RoundaboutManeuverView.kt`.**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.navigation.RoundaboutGeometry
import fr.roadly.core.navigation.RoundaboutInfo
import kotlin.math.cos
import kotlin.math.sin

private val COMPACT_SIZE = 48.dp
private val LARGE_SIZE = 160.dp
/** Rayon de l'anneau, en part du côté du schéma. */
private const val RING_RATIO = 0.22f
/** Longueur des branches d'entrée et de sortie, en part du côté. */
private const val STUB_RATIO = 0.20f
/** Centre de l'anneau remonté (part du côté) pour loger la branche d'entrée en bas. */
private const val CENTER_LIFT = 0.04f

/**
 * D2 : schéma du rond-point visé. Il montre :
 * - l'anneau ;
 * - la branche d'entrée en bas ;
 * - l'arc parcouru dans le sens de circulation (antihoraire), jusqu'à la sortie placée par l'arc réel de l'anneau ([RoundaboutGeometry]) ;
 * - une flèche ;
 * - le numéro de sortie au centre.
 * [compact] = 48 dp sur fond marque (bandeau) ; sinon 160 dp + rue de sortie (aperçu).
 * Sans manœuvre de sortie (arrivée dans l'anneau), pas de branche : la flèche suit l'anneau.
 * LIMITE : les autres sorties ne sont jamais dessinées (absentes de `/route`) ; circulation à gauche non gérée.
 */
@Composable
fun RoundaboutManeuverView(info: RoundaboutInfo, exitNumber: Int?, compact: Boolean, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val sweep = remember(info) { RoundaboutGeometry.diagramSweepDeg(info) }
    val side = if (compact) COMPACT_SIZE else LARGE_SIZE
    val ring = if (compact) c.onBrand.copy(alpha = 0.35f) else c.brandSoft
    val path = if (compact) c.onBrand else c.brandBright
    val numberColor = if (compact) c.onBrand else c.textPrimary
    val label = when (exitNumber) {
        null -> null
        1 -> stringResource(R.string.nav_roundabout_exit_first)
        else -> stringResource(R.string.nav_roundabout_exit_n, exitNumber)
    }
    val description = label?.let { stringResource(R.string.nav_roundabout_desc, it) } ?: stringResource(R.string.nav_roundabout_desc_unknown)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(side).then(if (compact) Modifier.background(c.brand, RoadlyShapes.Tile) else Modifier), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(side)) { drawRoundabout(sweep, info.exitBearingDeg != null, ring, path, if (compact) 0.10f else 0.07f) }
            if (exitNumber != null) {
                Text(
                    exitNumber.toString(),
                    modifier = Modifier.offset(y = -(side * CENTER_LIFT)),
                    style = NavDigits.copy(fontSize = if (compact) 13.sp else 30.sp, lineHeight = if (compact) 15.sp else 34.sp),
                    color = numberColor,
                )
            }
        }
        if (!compact) {
            info.exitStreet?.let {
                Text(
                    it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.width(side).padding(top = 4.dp),
                )
            }
        }
    }
}

/** Dessin : 90° = bas (entrée) ; l'arc négatif tourne dans le sens antihoraire à l'écran ; sortie à 90° − arc. */
private fun DrawScope.drawRoundabout(sweep: Double?, exitBranch: Boolean, ringColor: Color, pathColor: Color, strokeRatio: Float) {
    val s = size.minDimension
    val stroke = s * strokeRatio
    val r = s * RING_RATIO
    val stub = s * STUB_RATIO
    val center = Offset(size.width / 2f, size.height / 2f - s * CENTER_LIFT)
    drawCircle(ringColor, radius = r, center = center, style = Stroke(stroke))
    drawLine(pathColor, start = Offset(center.x, center.y + r + stub), end = Offset(center.x, center.y + r), strokeWidth = stroke, cap = StrokeCap.Round)
    if (sweep == null) return
    drawArc(
        pathColor, startAngle = 90f, sweepAngle = -sweep.toFloat(), useCenter = false,
        topLeft = Offset(center.x - r, center.y - r), size = Size(2 * r, 2 * r), style = Stroke(stroke, cap = StrokeCap.Round),
    )
    val theta = Math.toRadians(90.0 - sweep)
    val radial = Offset(cos(theta).toFloat(), sin(theta).toFloat())
    val onRing = center + radial * r
    if (exitBranch) {
        val tip = onRing + radial * stub
        drawLine(pathColor, start = onRing, end = tip - radial * stroke, strokeWidth = stroke, cap = StrokeCap.Round)
        drawArrowHead(tip, radial, stroke * 2.2f, pathColor)
    } else {
        // Tangente dans le sens antihoraire à l'écran (angle décroissant).
        val tangent = Offset(sin(theta).toFloat(), -cos(theta).toFloat())
        drawArrowHead(onRing + tangent * (stroke * 1.4f), tangent, stroke * 2.0f, pathColor)
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, dir: Offset, length: Float, color: Color) {
    val normal = Offset(-dir.y, dir.x)
    val base = tip - dir * length
    val half = length * 0.6f
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normal.x * half, base.y + normal.y * half)
        lineTo(base.x - normal.x * half, base.y - normal.y * half)
        close()
    }
    drawPath(head, color)
}

/**
 * Rond-point de démonstration : anneau parfait de 15 m parcouru sur [sweepDeg], entrée au sud. Il sert uniquement
 * aux aperçus Compose et aux tests instrumentés, jamais au guidage. La correction n/(n−1) donne un arc mesuré égal à [sweepDeg].
 */
internal fun previewRoundabout(sweepDeg: Double, exitNumber: Int?, street: String?, withExit: Boolean = true): RoundaboutInfo {
    val n = 24
    val total = sweepDeg * n / (n - 1)
    val lat0 = 43.6045; val lon0 = 1.4440; val r = 15.0
    val mLat = 111_320.0; val mLon = 111_320.0 * cos(Math.toRadians(lat0))
    val ring = (0..n).map { k ->
        val phi = Math.toRadians(-90.0 + total * k / n)
        GeoPoint(lat0 + r * sin(phi) / mLat, lon0 + r * cos(phi) / mLon)
    }
    val exitBearing = if (withExit) ((180.0 - sweepDeg) % 360.0 + 360.0) % 360.0 else null
    return RoundaboutInfo(exitCount = null, exitNumber = exitNumber, entryBearingDeg = 0.0, exitBearingDeg = exitBearing, ringGeometry = ring, exitStreet = street)
}

@Preview(name = "2e sortie — Nuit", showBackground = true, backgroundColor = 0xFF1F1829)
@Composable
private fun RoundaboutSecondExitPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutManeuverView(previewRoundabout(180.0, 2, "Avenue des Champs-Élysées"), 2, compact = false) }
}

@Preview(name = "3e sortie — Nuit", showBackground = true, backgroundColor = 0xFF1F1829)
@Composable
private fun RoundaboutThirdExitPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutManeuverView(previewRoundabout(282.0, 3, "D 916"), 3, compact = false) }
}

@Preview(name = "Demi-tour — Nuit", showBackground = true, backgroundColor = 0xFF1F1829)
@Composable
private fun RoundaboutUTurnPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutManeuverView(previewRoundabout(355.0, 4, "Route de Revel"), 4, compact = false) }
}

@Preview(name = "Sans numéro — Nuit", showBackground = true, backgroundColor = 0xFF1F1829)
@Composable
private fun RoundaboutNoNumberPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { RoundaboutManeuverView(previewRoundabout(90.0, null, null), null, compact = false) }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 600)
@Composable
private fun RoundaboutDayPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            RoundaboutManeuverView(previewRoundabout(180.0, 2, "Avenue des Champs-Élysées"), 2, compact = false)
            RoundaboutManeuverView(previewRoundabout(282.0, 3, null), 3, compact = false)
            RoundaboutManeuverView(previewRoundabout(180.0, 2, null), 2, compact = true)
            RoundaboutManeuverView(previewRoundabout(168.0, 5, null, withExit = false), 5, compact = true)
        }
    }
}
```

- [ ] **Étape 3 : remplacer le contenu de `nav/LaneGuidanceView.kt` (ex-`LaneStrip.kt`).**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection

/**
 * D4 : voies à l'approche de la manœuvre. Une voie conseillée est en marque, sur fond doux ; une voie possible est en secondaire ;
 * une voie interdite est estompée. Une voie à plusieurs directions montre toutes ses flèches superposées.
 * Le composant est absent sans données (LIMITE : FOSSGIS public n'en renvoie pas).
 */
@Composable
fun LaneGuidanceView(lanes: List<Lane>, emphasized: Boolean, modifier: Modifier = Modifier) {
    if (lanes.isEmpty()) return
    val c = LocalRoadlyColors.current
    val states = remember(lanes) { laneStates(lanes) }
    val cell = if (emphasized) 44.dp else 36.dp
    val icon = if (emphasized) 32.dp else 26.dp
    val advised = states.withIndex().filter { it.value == LaneState.ACTIVE }.joinToString(", ") { (it.index + 1).toString() }
    val description = if (advised.isEmpty()) stringResource(R.string.nav_lanes) else stringResource(R.string.nav_lanes_desc, advised, lanes.size)
    GlassPanel(modifier.semantics { contentDescription = description }, contentPadding = 8.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            lanes.forEachIndexed { i, lane ->
                if (i > 0) Box(Modifier.width(1.dp).height(cell).background(c.glassBorder))
                val state = states[i]
                val tint = when (state) {
                    LaneState.ACTIVE -> c.brandBright
                    LaneState.VALID -> c.textSecondary
                    LaneState.INVALID -> c.textTertiary.copy(alpha = 0.5f)
                }
                Box(
                    Modifier.size(width = cell, height = cell + 8.dp)
                        .then(if (state == LaneState.ACTIVE) Modifier.background(c.brandSoft, RoadlyShapes.Tile) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    lane.directions.sortedBy { it.ordinal }.ifEmpty { listOf(LaneDirection.STRAIGHT) }.forEach { d ->
                        Icon(d.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(if (state == LaneState.ACTIVE) icon + 4.dp else icon))
                    }
                }
            }
        }
    }
}

internal fun LaneDirection.icon(): ImageVector = when (this) {
    LaneDirection.LEFT -> Icons.Default.TurnLeft
    LaneDirection.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    LaneDirection.STRAIGHT -> Icons.Default.Straight
    LaneDirection.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    LaneDirection.RIGHT -> Icons.Default.TurnRight
    LaneDirection.UTURN -> Icons.Default.UTurnLeft
    LaneDirection.MERGE_LEFT, LaneDirection.MERGE_RIGHT -> Icons.Default.Merge
}

private val sampleLanes = listOf(
    Lane(setOf(LaneDirection.LEFT), valid = false),
    Lane(setOf(LaneDirection.STRAIGHT), valid = true),
    Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), valid = true, active = true),
    Lane(setOf(LaneDirection.RIGHT), valid = true, active = true),
)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun LaneGuidanceNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { LaneGuidanceView(sampleLanes, emphasized = true) } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun LaneGuidanceDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { LaneGuidanceView(sampleLanes, emphasized = false) } }
```

- [ ] **Étape 4 : renommer les deux appels dans `nav/NavigationScreen.kt` (compilation intermédiaire).**

Ancien (ligne 204, portrait) :
```kotlin
                    if (state.lanes.isNotEmpty() && (bannerExpanded || state.approach != null)) LaneStrip(state.lanes, emphasized = state.approach != null)
```
Nouveau :
```kotlin
                    if (state.lanes.isNotEmpty() && (bannerExpanded || state.approach != null)) LaneGuidanceView(state.lanes, emphasized = state.approach != null)
```
Même remplacement à la ligne 267 (paysage), qui a 4 espaces d'indentation de moins.

- [ ] **Étape 5 : compiler et tester.**

```powershell
.\gradlew.bat :feature:navigation:compileDebugKotlin --offline -q
.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q
```
Résultat attendu : SUCCÈS. Ouvrir ensuite les aperçus dans Android Studio et vérifier :
- 2e sortie en haut ;
- 3e sortie à gauche, un peu sous l'horizontale ;
- demi-tour juste à gauche de l'entrée ;
- sans numéro : flèche à droite, centre vide ;
- variante compacte lisible sur fond violet.

- [ ] **Étape 6 : commiter.**

```powershell
git add feature/navigation/build.gradle.kts feature/navigation/src/main/res/values/strings.xml feature/navigation/src/main/java/fr/roadly/feature/navigation/RoundaboutManeuverView.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/LaneGuidanceView.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/LaneStrip.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt
git commit -m @'
feat(guidage): schéma de rond-point tiré de l'anneau réel et voies à trois états (D2, D4)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 5 : D1 + D3 — bandeau interactif, « puis », panneau de direction, aperçu de la manœuvre

**Fichiers :**
- Remplacer tout le contenu de `nav/ManeuverBanner.kt` (82 lignes).
- Créer : `nav/ExitSignPanel.kt`, `nav/BannerStack.kt`, `nav/ManeuverPreviewSheet.kt`.
- Modifier `nav/NavigationScreen.kt` :
  - ligne 204 (portrait) et ligne 267 (paysage), avec les lignes 203 et 266 ;
  - ligne 182 (ajout de `GuidanceSheets` après) ;
  - fin de fichier (fonction `GuidanceSheets`).
- Modifier `strings.xml`, après `nav_lanes_desc` (tâche 4).

**Interfaces :**
- Consomme :
  - tâche 2 : `NavigationUiState.roundabout / then / sign / showLanes / upcoming[i].instruction / sign`, `ThenUi`, `ExitSignUi` ;
  - tâche 3 : `GuidanceSheet`, `vm.guidanceSheet`, `vm.openManeuverPreview`, `vm.closeSheet` ;
  - tâche 4 : `RoundaboutManeuverView`, `LaneGuidanceView`.
- Produit :
  - `fun ManeuverBanner(banner: BannerUi, modifier: Modifier = Modifier, expanded: Boolean = true, onClick: (() -> Unit)? = null, roundabout: RoundaboutInfo? = null, signShownBelow: Boolean = false)` ; `internal const val BANNER_TAG = "maneuver_banner"`
  - `@Composable fun ExitSignPanel(exitNumber: String?, toward: String?, branch: String?, lanes: List<Lane>, distanceText: String, isNext: Boolean, modifier: Modifier = Modifier)`
  - `@Composable internal fun ThenRow(then: ThenUi, modifier: Modifier = Modifier)`
  - `@Composable internal fun BannerStack(state: NavigationUiState, expanded: Boolean, onOpenPreview: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun ManeuverPreviewSheet(state: NavigationUiState, onDismiss: () -> Unit)` et `@Composable internal fun ManeuverPreviewContent(state: NavigationUiState, onDismiss: () -> Unit)`
  - `@Composable internal fun GuidanceSheets(state: NavigationUiState, vm: NavigationViewModel)`
  - Chaînes `nav_then`, `nav_preview_title`, `nav_preview_next`, `nav_close`, `nav_sign_next`, `nav_sign_following`.

- [ ] **Étape 1 : ajouter les chaînes.**

Ancre (ajoutée à la tâche 4) :
```xml
    <string name="nav_lanes_desc">Voies conseillées : %1$s sur %2$d</string>
```
Ajouter juste après :
```xml
    <string name="nav_then">puis</string>
    <string name="nav_preview_title">Aperçu de la manœuvre</string>
    <string name="nav_preview_next">Ensuite</string>
    <string name="nav_close">Fermer</string>
    <string name="nav_sign_next">Prochaine sortie</string>
    <string name="nav_sign_following">Sortie suivante</string>
```

- [ ] **Étape 2 : remplacer `nav/ManeuverBanner.kt`.**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.RoundaboutInfo

/** Étiquette du bandeau pour les tests instrumentés. */
internal const val BANNER_TAG = "maneuver_banner"

/**
 * Bandeau de manœuvre : tuile de manœuvre (marque) ou schéma de rond-point compact (D2), distance en grand, rue et pastille de sortie.
 * [expanded] = false réduit le bandeau à la tuile, à la distance et à la direction de sortie (bouton « replier »).
 * [onClick] : tout le panneau (≥ 56 dp) ouvre l'aperçu de la manœuvre (D1).
 * [signShownBelow] : le panneau de direction est affiché dessous (D3) ; la pastille et la ligne « Direction … » s'effacent pour ne pas doubler.
 */
@Composable
fun ManeuverBanner(
    banner: BannerUi,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onClick: (() -> Unit)? = null,
    roundabout: RoundaboutInfo? = null,
    signShownBelow: Boolean = false,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.testTag(BANNER_TAG).defaultMinSize(minHeight = RoadlySpacing.touchTargetDriving), contentPadding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (roundabout != null) {
                RoundaboutManeuverView(roundabout, banner.roundaboutExit ?: roundabout.exitNumber, compact = true)
            } else {
                IconTile(banner.type.icon(), style = IconTileStyle.BrandFilled, size = 48.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(banner.distanceText, style = NavDigits.copy(fontSize = 28.sp, lineHeight = 32.sp, textAlign = TextAlign.Start), color = c.textPrimary, maxLines = 1)
                    if (!signShownBelow) {
                        exitLabel(banner)?.let { label ->
                            Spacer(Modifier.width(8.dp))
                            ExitPill(label)
                        }
                    }
                }
                // Direction de sortie visible, bandeau replié ou non (spec §3) ; à l'approche, le panneau D3 la reprend.
                if (!signShownBelow) {
                    banner.exitToward?.let {
                        Text(stringResource(R.string.nav_exit_toward, it), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                AnimatedVisibility(expanded) {
                    Column {
                        Text(banner.instruction, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        banner.street?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

@Composable
private fun exitLabel(b: BannerUi): String? {
    val exit = b.exitNumber
    val roundabout = b.roundaboutExit
    return when {
        exit != null -> stringResource(R.string.nav_exit_number, exit)
        roundabout == 1 -> stringResource(R.string.nav_roundabout_exit_first)
        roundabout != null -> stringResource(R.string.nav_roundabout_exit_n, roundabout)
        else -> null
    }
}

@Composable
private fun ExitPill(label: String) {
    val c = LocalRoadlyColors.current
    Box(Modifier.background(c.accent, RoadlyShapes.Chip).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.onAccent)
    }
}

private val sampleBanner = BannerUi(ManeuverType.ROUNDABOUT_ENTER, "150 m", "Au rond-point, prenez la 2e sortie.", "Avenue des Champs-Élysées", null, 2)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 420)
@Composable
private fun ManeuverBannerNightPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { ManeuverBanner(sampleBanner, onClick = {}, roundabout = previewRoundabout(180.0, 2, "Avenue des Champs-Élysées")) }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 420)
@Composable
private fun ManeuverBannerDayPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) { ManeuverBanner(sampleBanner.copy(type = ManeuverType.TURN_RIGHT, roundaboutExit = null), onClick = {}) }
}
```

- [ ] **Étape 3 : écrire `nav/ExitSignPanel.kt`.**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.Overline
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.designsystem.theme.softShadow
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection

/**
 * D3 : panneau de direction façon signalisation. Il comprend :
 * - le surtitre « Prochaine sortie » ([isNext]) ou « Sortie suivante » ;
 * - la distance ;
 * - la pastille de numéro ;
 * - la route (`exitBranch`, p. ex. « A62 ») ;
 * - jusqu'à 2 directions ;
 * - les voies conseillées dessous (D4).
 * Couleurs : fond `brand` plein et texte `onBrand` (`brandSoft`, translucide, n'assure pas le contraste sur la carte claire) ;
 * pastille `onBrand` / texte `brand` ; route bordée `onBrand`.
 */
@Composable
fun ExitSignPanel(
    exitNumber: String?,
    toward: String?,
    branch: String?,
    lanes: List<Lane>,
    distanceText: String,
    isNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    val towardLines = remember(toward) { toward?.split(" · ")?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(2).orEmpty() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        Column(
            Modifier.fillMaxWidth()
                .softShadow(RoadlyShapes.Panel, c.brand, 10.dp)
                .clip(RoadlyShapes.Panel)
                .background(c.brand)
                .border(1.dp, c.glassBorder, RoadlyShapes.Panel)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (isNext) R.string.nav_sign_next else R.string.nav_sign_following).uppercase(), style = Overline, color = c.onBrand.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
                Text(distanceText, style = NavDigits.copy(fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.End), color = c.onBrand, maxLines = 1)
            }
            if (exitNumber != null || branch != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    exitNumber?.let {
                        Box(Modifier.background(c.onBrand, RoadlyShapes.Tile).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.nav_exit_number, it), style = MaterialTheme.typography.labelLarge, color = c.brand)
                        }
                    }
                    branch?.let {
                        Box(Modifier.border(1.5.dp, c.onBrand, RoadlyShapes.Tile).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = c.onBrand)
                        }
                    }
                }
            }
            towardLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.titleMedium, color = c.onBrand, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LaneGuidanceView(lanes, emphasized = true)
    }
}

private val signLanes = listOf(Lane(setOf(LaneDirection.STRAIGHT), valid = false), Lane(setOf(LaneDirection.SLIGHT_RIGHT), valid = true, active = true))

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 420)
@Composable
private fun ExitSignNightPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) { ExitSignPanel("31b", "Toulouse-Centre · Blagnac", "A62", signLanes, "450 m", isNext = true) }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 420)
@Composable
private fun ExitSignDayPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) { ExitSignPanel("19", "Balma", null, emptyList(), "1,2 km", isNext = false) }
}
```

- [ ] **Étape 4 : écrire `nav/BannerStack.kt`.**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType

/**
 * Pile du bandeau, commune aux deux overlays : bandeau (D1, D2), ligne « puis … » (D1), panneau de direction à l'approche (D3)
 * ou voies seules (D4). Un seul appel par overlay garde la surface de fusion minimale.
 */
@Composable
internal fun BannerStack(state: NavigationUiState, expanded: Boolean, onOpenPreview: () -> Unit, modifier: Modifier = Modifier) {
    val banner = state.banner ?: return
    val sign = state.sign?.takeIf { state.approach != null }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        ManeuverBanner(banner, Modifier.fillMaxWidth(), expanded = expanded, onClick = onOpenPreview, roundabout = state.roundabout, signShownBelow = sign != null)
        state.then?.let { ThenRow(it) }
        if (sign != null) {
            ExitSignPanel(sign.exitNumber, sign.toward, sign.branch, if (state.showLanes) state.lanes else emptyList(), banner.distanceText, isNext = true, Modifier.fillMaxWidth())
        } else if (state.showLanes) {
            LaneGuidanceView(state.lanes, emphasized = state.approach != null)
        }
    }
}

/** D1 : « puis ↱ Rue X », manœuvre d'après à moins de 500 m de la prochaine (icône 20 dp + texte). */
@Composable
internal fun ThenRow(then: ThenUi, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier, shape = RoadlyShapes.Chip, contentPadding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.nav_then), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.width(8.dp))
            Icon(then.type.icon(), contentDescription = null, tint = c.brandBright, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(then.label, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun ThenRowNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { ThenRow(ThenUi(ManeuverType.TURN_RIGHT, "Rue Louis Plana")) } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun ThenRowDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { ThenRow(ThenUi(ManeuverType.ROUNDABOUT_EXIT, "Rue Médiane")) } }
```

- [ ] **Étape 5 : écrire `nav/ManeuverPreviewSheet.kt`.**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType

/** D1 : feuille « Aperçu de la manœuvre ». Son contenu suit l'état en direct ; la carte et le guidage continuent derrière. */
@Composable
fun ManeuverPreviewSheet(state: NavigationUiState, onDismiss: () -> Unit) {
    if (state.banner == null) return
    GlassBottomSheet(onDismiss = onDismiss) { ManeuverPreviewContent(state, onDismiss) }
}

/**
 * Contenu de l'aperçu :
 * - grande icône ou schéma de rond-point ;
 * - distance, instruction, rue ;
 * - panneau de direction, ou voies ;
 * - les 3 manœuvres suivantes (avec leur panneau « Sortie suivante » s'il existe) ;
 * - « Fermer ».
 */
@Composable
internal fun ManeuverPreviewContent(state: NavigationUiState, onDismiss: () -> Unit) {
    val c = LocalRoadlyColors.current
    val banner = state.banner ?: return
    Column(
        Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm),
    ) {
        OverlineText(stringResource(R.string.nav_preview_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val roundabout = state.roundabout
            if (roundabout != null) {
                RoundaboutManeuverView(roundabout, banner.roundaboutExit ?: roundabout.exitNumber, compact = false)
            } else {
                IconTile(banner.type.icon(), style = IconTileStyle.BrandFilled, size = 96.dp)
            }
            Spacer(Modifier.width(RoadlySpacing.m))
            Column(Modifier.weight(1f)) {
                Text(banner.distanceText, style = NavDigits.copy(fontSize = 34.sp, lineHeight = 38.sp, textAlign = TextAlign.Start), color = c.textPrimary)
                Text(banner.instruction, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                banner.street?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            }
        }
        val sign = state.sign
        if (sign != null) {
            ExitSignPanel(sign.exitNumber, sign.toward, sign.branch, state.lanes, banner.distanceText, isNext = true, Modifier.fillMaxWidth())
        } else {
            LaneGuidanceView(state.lanes, emphasized = true)
        }
        if (state.upcoming.isNotEmpty()) {
            Text(stringResource(R.string.nav_preview_next), style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
            state.upcoming.forEach { u ->
                ListRow(title = u.street ?: u.instruction ?: stringResource(R.string.nav_horizon_maneuver), icon = u.type.icon(), value = u.distanceText, divider = false)
                u.sign?.let { s -> ExitSignPanel(s.exitNumber, s.toward, s.branch, emptyList(), u.distanceText, isNext = false, Modifier.fillMaxWidth()) }
            }
        }
        GlassButton(stringResource(R.string.nav_close), onClick = onDismiss, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

private val sampleState = NavigationUiState(
    phase = NavigationPhase.GUIDING,
    banner = BannerUi(ManeuverType.ROUNDABOUT_ENTER, "150 m", "Au rond-point, prenez la 2e sortie.", "Avenue des Champs-Élysées", null, 2),
    roundabout = previewRoundabout(180.0, 2, "Avenue des Champs-Élysées"),
    upcoming = listOf(
        UpcomingUi(ManeuverType.ROUNDABOUT_EXIT, "200 m", "Avenue des Champs-Élysées", 200.0),
        UpcomingUi(ManeuverType.EXIT_RIGHT, "2,4 km", null, 2_400.0, "Prenez la sortie 19.", ExitSignUi("19", "Balma", null)),
    ),
)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF1F1829, widthDp = 420)
@Composable
private fun ManeuverPreviewNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { ManeuverPreviewContent(sampleState, onDismiss = {}) } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 420)
@Composable
private fun ManeuverPreviewDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { ManeuverPreviewContent(sampleState, onDismiss = {}) } }
```

- [ ] **Étape 6 : brancher dans `nav/NavigationScreen.kt`.**

**1. Portrait.** Ancien (lignes 203-204, après la tâche 4) :
```kotlin
                    state.banner?.let { ManeuverBanner(it, Modifier.fillMaxWidth(), expanded = bannerExpanded) }
                    if (state.lanes.isNotEmpty() && (bannerExpanded || state.approach != null)) LaneGuidanceView(state.lanes, emphasized = state.approach != null)
```
Nouveau :
```kotlin
                    BannerStack(state, bannerExpanded, onOpenPreview = vm::openManeuverPreview)
```

**2. Paysage.** Ancien (lignes 266-267) :
```kotlin
                state.banner?.let { ManeuverBanner(it, Modifier.fillMaxWidth(), expanded = bannerExpanded) }
                if (state.lanes.isNotEmpty() && (bannerExpanded || state.approach != null)) LaneGuidanceView(state.lanes, emphasized = state.approach != null)
```
Nouveau :
```kotlin
                BannerStack(state, bannerExpanded, onOpenPreview = vm::openManeuverPreview)
```

**3. Feuilles.** Ancien (ligne 182, fin de `NavigationContent`) :
```kotlin
    if (alternatives != AlternativesUi.Hidden) AlternativesSheet(alternatives, onSelect = vm::switchTo, onRetry = vm::loadAlternatives, onDismiss = vm::dismissAlternatives, onAddStop = { vm.dismissAlternatives(); onAddStop() })
}
```
Nouveau :
```kotlin
    if (alternatives != AlternativesUi.Hidden) AlternativesSheet(alternatives, onSelect = vm::switchTo, onRetry = vm::loadAlternatives, onDismiss = vm::dismissAlternatives, onAddStop = { vm.dismissAlternatives(); onAddStop() })
    GuidanceSheets(state, vm)
}
```

**4. Fin du fichier.** Ajouter après `RecenterCountdownPill` :
```kotlin

/** D1 / D5 : feuilles ouvertes depuis le bandeau ou le panneau du bas ; le contenu suit l'état en direct, la carte continue derrière. */
@Composable
internal fun GuidanceSheets(state: NavigationUiState, vm: NavigationViewModel) {
    val sheet by vm.guidanceSheet.collectAsStateWithLifecycle()
    if (state.phase != NavigationPhase.GUIDING) return
    when (sheet) {
        GuidanceSheet.MANEUVER_PREVIEW -> ManeuverPreviewSheet(state, onDismiss = vm::closeSheet)
        GuidanceSheet.TRIP_DETAILS, GuidanceSheet.NONE -> Unit
    }
}
```
La branche `TRIP_DETAILS` est complétée à la tâche 6, en même temps que le bouton qui l'ouvre.

- [ ] **Étape 7 : compiler et tester.**

```powershell
.\gradlew.bat :feature:navigation:compileDebugKotlin --offline -q
.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q
```
Résultat attendu : SUCCÈS. Si un avertissement signale un import inutilisé (`ManeuverBanner` n'est plus référencé directement dans `NavigationScreen.kt`), il n'y a rien à faire : le symbole est dans le même paquet.

- [ ] **Étape 8 : commiter.**

```powershell
git add feature/navigation/src/main/res/values/strings.xml feature/navigation/src/main/java/fr/roadly/feature/navigation/ManeuverBanner.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/ExitSignPanel.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/BannerStack.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/ManeuverPreviewSheet.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt
git commit -m @'
feat(guidage): bandeau interactif, « puis », panneau de direction et aperçu de la manœuvre (D1, D3)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 6 : D5 — `NavigationBottomPanel`, `TripDetailsSheet`, marge caméra mesurée

**Fichiers :**
- Créer : `nav/NavigationBottomPanel.kt`, `nav/TripDetailsSheet.kt`.
- Modifier `nav/NavigationScreen.kt` :
  - imports (lignes 3-101) ;
  - après la ligne 154 ;
  - ligne 158 ;
  - lignes 312-325 (`BottomBlock`, branche `GUIDING`) ;
  - lignes 477-487 (`MapTick`), 494-501 (signature), 508 (`snapshotFlow`), 522-528 (`camera.update`) ;
  - `GuidanceSheets` (tâche 5).
- Modifier `strings.xml`, après `nav_sign_following`.

**Interfaces :**
- Consomme :
  - tâche 2 : `tripSteps`, `TripStepUi` ;
  - tâche 3 : `vm.bottomPanelExpanded`, `toggleBottomPanel`, `destinationName`, `setBottomPanelFraction`, `bottomPanelFraction`, `openTripDetails`, `closeSheet`, `BottomPanelGeometry.fraction` ;
  - `NavigationCameraInput.bottomPanelFraction` (C).
- Produit :
  - `@Composable fun NavigationBottomPanel(state: NavigationUiState, expanded: Boolean, destinationName: String?, onToggleExpanded: () -> Unit, onToggleMute: () -> Unit, onRecenter: () -> Unit, onStop: () -> Unit, onTripDetails: () -> Unit, onAlternatives: () -> Unit, modifier: Modifier = Modifier)` ; `internal const val PANEL_SUMMARY_TAG = "bottom_panel_summary"`
  - `@Composable fun TripDetailsSheet(state: NavigationUiState, destinationName: String?, onDismiss: () -> Unit)`
  - Chaînes `nav_panel_expand`, `nav_panel_collapse`, `nav_destination`, `nav_trip_details`, `nav_trip_title_fallback`, `nav_trip_summary`, `nav_trip_step_length`.

- [ ] **Étape 1 : ajouter les chaînes.**

Ancre :
```xml
    <string name="nav_sign_following">Sortie suivante</string>
```
Ajouter juste après :
```xml
    <string name="nav_panel_expand">Afficher les détails du trajet</string>
    <string name="nav_panel_collapse">Masquer les détails du trajet</string>
    <string name="nav_destination">Destination</string>
    <string name="nav_trip_details">Trajet complet</string>
    <string name="nav_trip_title_fallback">Itinéraire</string>
    <string name="nav_trip_summary">%1$s · %2$s · arrivée %3$s</string>
    <string name="nav_trip_step_length">sur %1$s</string>
```

- [ ] **Étape 2 : écrire `nav/NavigationBottomPanel.kt`.**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.RoundButtonStyle
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.StatTile
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

/** Étiquette de la ligne résumée (ETA) pour les tests instrumentés. */
internal const val PANEL_SUMMARY_TAG = "bottom_panel_summary"

/** Glissement vertical minimal sur le panneau pour le déplier (vers le haut) ou le replier (vers le bas). */
private val DRAG_THRESHOLD = 24.dp

/**
 * D5 : panneau du bas du guidage.
 * - Replié : poignée, heure d'arrivée, temps et distance restants, voix (Recentrer à sa place quand la carte a été déplacée :
 *   trois boutons de 56 dp ne tiennent pas sur ~433 dp) et Arrêter (rouge).
 * - Déplié (toucher la ligne ETA, ou tirer la poignée vers le haut) : destination, « Trajet complet », « Itinéraires ».
 * L'Horizon reste au-dessus, hors du panneau.
 */
@Composable
fun NavigationBottomPanel(
    state: NavigationUiState,
    expanded: Boolean,
    destinationName: String?,
    onToggleExpanded: () -> Unit,
    onToggleMute: () -> Unit,
    onRecenter: () -> Unit,
    onStop: () -> Unit,
    onTripDetails: () -> Unit,
    onAlternatives: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    val threshold = with(LocalDensity.current) { DRAG_THRESHOLD.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val dragState = rememberDraggableState { delta -> drag += delta }
    val toggleLabel = stringResource(if (expanded) R.string.nav_panel_collapse else R.string.nav_panel_expand)
    GlassPanel(
        modifier.fillMaxWidth().draggable(dragState, Orientation.Vertical, onDragStopped = {
            if ((drag < -threshold && !expanded) || (drag > threshold && expanded)) onToggleExpanded()
            drag = 0f
        }),
        shape = RoadlyShapes.Panel, contentPadding = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f)
                        .testTag(PANEL_SUMMARY_TAG)
                        .defaultMinSize(minHeight = RoadlySpacing.touchTargetDriving)
                        .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggleExpanded),
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(36.dp).height(4.dp).background(c.textTertiary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatTile(state.etaClock, stringResource(R.string.nav_eta_label))
                        Spacer(Modifier.width(16.dp))
                        StatTile(state.remainingDurationSplit.value, state.remainingDurationSplit.unit)
                        Spacer(Modifier.width(16.dp))
                        StatTile(state.remainingDistanceSplit.value, state.remainingDistanceSplit.unit)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!state.following) {
                        RoundIconButton(Icons.Default.Navigation, stringResource(R.string.nav_recenter), onClick = onRecenter, style = RoundButtonStyle.Brand)
                    } else {
                        RoundIconButton(
                            if (state.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            stringResource(if (state.muted) R.string.nav_unmute else R.string.nav_mute),
                            onClick = onToggleMute,
                        )
                    }
                    RoundIconButton(Icons.Default.Close, stringResource(R.string.nav_stop), onClick = onStop, style = RoundButtonStyle.Danger)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.sm)) {
                    RowDivider(inset = 0.dp)
                    destinationName?.let { name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Default.Flag, style = IconTileStyle.BrandFilled, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                OverlineText(stringResource(R.string.nav_destination), color = c.textTertiary, dash = false)
                                Text(name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                        GlassButton(stringResource(R.string.nav_trip_details), onClick = onTripDetails, icon = Icons.AutoMirrored.Filled.List, style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f))
                        GlassButton(stringResource(R.string.nav_alternatives), onClick = onAlternatives, icon = Icons.Default.AltRoute, style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private val samplePanel = NavigationUiState(
    phase = NavigationPhase.GUIDING, etaClock = "23:24",
    remainingDurationSplit = SplitValue("19", "min"), remainingDistanceSplit = SplitValue("4,6", "km"),
)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14, widthDp = 440)
@Composable
private fun BottomPanelNightPreview() {
    RoadlyTheme(darkTheme = true, drivingMode = true) {
        NavigationBottomPanel(samplePanel, expanded = true, destinationName = "Capitole, Toulouse", onToggleExpanded = {}, onToggleMute = {}, onRecenter = {}, onStop = {}, onTripDetails = {}, onAlternatives = {})
    }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8, widthDp = 440)
@Composable
private fun BottomPanelDayPreview() {
    RoadlyTheme(darkTheme = false, drivingMode = true) {
        NavigationBottomPanel(samplePanel.copy(following = false), expanded = false, destinationName = null, onToggleExpanded = {}, onToggleMute = {}, onRecenter = {}, onStop = {}, onTripDetails = {}, onAlternatives = {})
    }
}
```

- [ ] **Étape 3 : écrire `nav/TripDetailsSheet.kt`.**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassBottomSheet
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.IconTile
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.OverlineText
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.ManeuverType

/** D5 : feuille « Trajet complet » : toutes les manœuvres du tracé, les passées estompées, la prochaine en marque, ouverte sur la prochaine. */
@Composable
fun TripDetailsSheet(state: NavigationUiState, destinationName: String?, onDismiss: () -> Unit) {
    val route = state.route ?: return
    val next = state.progress?.nextManeuverIndex ?: 0
    val steps = remember(route.id, next) { tripSteps(route, next) }
    val c = LocalRoadlyColors.current
    GlassBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = RoadlySpacing.l).padding(bottom = RoadlySpacing.l).navigationBarsPadding()) {
            OverlineText(stringResource(R.string.nav_trip_details))
            Spacer(Modifier.height(4.dp))
            Text(destinationName ?: stringResource(R.string.nav_trip_title_fallback), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.nav_trip_summary, state.remainingDistance, state.remainingDuration, state.etaClock), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Spacer(Modifier.height(RoadlySpacing.m))
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = next.coerceIn(0, (steps.size - 1).coerceAtLeast(0)))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), state = listState) {
                items(steps, key = { it.index }) { step -> TripStepRow(step) }
            }
            Spacer(Modifier.height(RoadlySpacing.sm))
            GlassButton(stringResource(R.string.nav_close), onClick = onDismiss, style = GlassButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Une étape : icône (prochaine = marque pleine, passée = neutre), instruction, rue et longueur Valhalla de l'étape. */
@Composable
internal fun TripStepRow(step: TripStepUi) {
    val c = LocalRoadlyColors.current
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(
            step.type.icon(), size = 36.dp,
            style = when { step.isNext -> IconTileStyle.BrandFilled; step.passed -> IconTileStyle.Neutral; else -> IconTileStyle.Brand },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.instruction, style = MaterialTheme.typography.bodyLarge, color = if (step.passed) c.textTertiary else c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val length = step.lengthText?.let { stringResource(R.string.nav_trip_step_length, it) }
            val detail = listOfNotNull(step.street, length).joinToString(" · ")
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val sampleSteps = listOf(
    TripStepUi(0, ManeuverType.DEPART, "Conduisez vers l'est.", null, "80 m", passed = true, isNext = false),
    TripStepUi(1, ManeuverType.TURN_LEFT, "Tournez à gauche dans Rue Louis Plana.", "Rue Louis Plana", "1,2 km", passed = false, isNext = true),
    TripStepUi(2, ManeuverType.ARRIVE, "Vous êtes arrivé.", null, null, passed = false, isNext = false),
)

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF1F1829, widthDp = 420)
@Composable
private fun TripStepsNightPreview() { RoadlyTheme(darkTheme = true, drivingMode = true) { Column { sampleSteps.forEach { TripStepRow(it) } } } }

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 420)
@Composable
private fun TripStepsDayPreview() { RoadlyTheme(darkTheme = false, drivingMode = true) { Column { sampleSteps.forEach { TripStepRow(it) } } } }
```

- [ ] **Étape 4 : `BottomBlock`, branche `GUIDING`, dans `nav/NavigationScreen.kt`.**

Ancien (lignes 312-325) :
```kotlin
        NavigationPhase.GUIDING -> {
            AnimatedVisibility(state.upcoming.isNotEmpty() || state.hazards.isNotEmpty() || state.communityAhead.isNotEmpty()) {
                HorizonStrip(items = horizonItems(state), title = stringResource(R.string.nav_horizon_title), modifier = Modifier.padding(bottom = RoadlySpacing.s))
            }
            val recenterIn by vm.recenterInSeconds.collectAsStateWithLifecycle()
            recenterIn?.let { RecenterCountdownPill(it, onCancel = vm::cancelAutoRecenter) }
            NavInfoBar(
                etaClock = state.etaClock, etaLabel = stringResource(R.string.nav_eta_label),
                minutesValue = state.remainingDurationSplit.value, minutesLabel = state.remainingDurationSplit.unit,
                kmValue = state.remainingDistanceSplit.value, kmLabel = state.remainingDistanceSplit.unit,
                showRecenter = !state.following, onRecenter = vm::recenter, onStop = vm::stop,
                recenterDescription = stringResource(R.string.nav_recenter), stopDescription = stringResource(R.string.nav_stop),
            )
        }
```
Nouveau :
```kotlin
        NavigationPhase.GUIDING -> {
            val expanded by vm.bottomPanelExpanded.collectAsStateWithLifecycle()
            val destination by vm.destinationName.collectAsStateWithLifecycle()
            val recenterIn by vm.recenterInSeconds.collectAsStateWithLifecycle()
            // D5 : hauteur du bloc (Horizon + panneau) mesurée → marge basse de la caméra (portrait ; ignorée en paysage).
            Column(
                Modifier.fillMaxWidth().onGloballyPositioned { coords ->
                    vm.setBottomPanelFraction(BottomPanelGeometry.fraction(coords.positionInRoot().y, coords.findRootCoordinates().size.height.toFloat()))
                },
            ) {
                AnimatedVisibility(state.upcoming.isNotEmpty() || state.hazards.isNotEmpty() || state.communityAhead.isNotEmpty()) {
                    HorizonStrip(items = horizonItems(state), title = stringResource(R.string.nav_horizon_title), modifier = Modifier.padding(bottom = RoadlySpacing.s))
                }
                recenterIn?.let { RecenterCountdownPill(it, onCancel = vm::cancelAutoRecenter) }
                NavigationBottomPanel(
                    state = state, expanded = expanded, destinationName = destination,
                    onToggleExpanded = vm::toggleBottomPanel, onToggleMute = vm::toggleMute, onRecenter = vm::recenter, onStop = vm::stop,
                    onTripDetails = vm::openTripDetails, onAlternatives = vm::loadAlternatives,
                )
            }
        }
```

**Imports.**
- Ajouter dans l'ordre alphabétique :
  - `import androidx.compose.ui.layout.findRootCoordinates`
  - `import androidx.compose.ui.layout.onGloballyPositioned`
  - `import androidx.compose.ui.layout.positionInRoot`
- Retirer `import fr.roadly.core.designsystem.component.NavInfoBar` (ligne 73). `NavInfoBar` reste dans le design system.

- [ ] **Étape 5 : brancher la caméra (C → D5).**

**1.** Ancien (ligne 154) :
```kotlin
    val camera = remember { NavigationCameraController() }
```
Nouveau :
```kotlin
    val camera = remember { NavigationCameraController() }
    val bottomPanel = vm.bottomPanelFraction.collectAsStateWithLifecycle()
```

**2.** Ancien (ligne 158) :
```kotlin
        GuidanceMapEffects(uiState, cameraMode, powerSave, landscape, map, camera)
```
Nouveau :
```kotlin
        GuidanceMapEffects(uiState, cameraMode, powerSave, landscape, map, camera, bottomPanel)
```

**3.** `MapTick`. Ancien (lignes 485-487) :
```kotlin
    val powerSave: Boolean,
    val landscape: Boolean,
)
```
Nouveau :
```kotlin
    val powerSave: Boolean,
    val landscape: Boolean,
    /** D5 : part de l'écran sous le panneau du bas (quantifiée), null avant la première mesure. */
    val bottomPanel: Float?,
)
```

**4.** Signature. Ancien (lignes 499-501) :
```kotlin
    map: MapController,
    camera: NavigationCameraController,
) {
```
Nouveau :
```kotlin
    map: MapController,
    camera: NavigationCameraController,
    bottomPanel: State<Float?>,
) {
```

**5.** Ancien (ligne 508) :
```kotlin
        snapshotFlow { ui.value.let { s -> MapTick(s.route, s.progress, s.speedKmh, s.approach != null, s.following, mode.value, powerSave.value, isLandscape.value) } }
```
Nouveau :
```kotlin
        snapshotFlow { ui.value.let { s -> MapTick(s.route, s.progress, s.speedKmh, s.approach != null, s.following, mode.value, powerSave.value, isLandscape.value, bottomPanel.value) } }
```

**6.** Ancien (lignes 524-526) :
```kotlin
                        point = progress.snapped, bearingDeg = progress.bearingDeg, speedKmh = t.speedKmh,
                        distanceToManeuverM = progress.distanceToNextManeuverM, maneuverType = next.type, approaching = t.approaching,
                        mode = t.mode, powerSave = t.powerSave, landscape = t.landscape, nowMs = SystemClock.elapsedRealtime(),
```
Nouveau :
```kotlin
                        point = progress.snapped, bearingDeg = progress.bearingDeg, speedKmh = t.speedKmh,
                        distanceToManeuverM = progress.distanceToNextManeuverM, maneuverType = next.type, approaching = t.approaching,
                        mode = t.mode, powerSave = t.powerSave, landscape = t.landscape, nowMs = SystemClock.elapsedRealtime(),
                        bottomPanelFraction = t.bottomPanel,
```

- [ ] **Étape 6 : brancher la feuille « Trajet complet ».**

Ancien (dans `GuidanceSheets`, tâche 5) :
```kotlin
    val sheet by vm.guidanceSheet.collectAsStateWithLifecycle()
    if (state.phase != NavigationPhase.GUIDING) return
    when (sheet) {
        GuidanceSheet.MANEUVER_PREVIEW -> ManeuverPreviewSheet(state, onDismiss = vm::closeSheet)
        GuidanceSheet.TRIP_DETAILS, GuidanceSheet.NONE -> Unit
    }
```
Nouveau :
```kotlin
    val sheet by vm.guidanceSheet.collectAsStateWithLifecycle()
    val destination by vm.destinationName.collectAsStateWithLifecycle()
    if (state.phase != NavigationPhase.GUIDING) return
    when (sheet) {
        GuidanceSheet.MANEUVER_PREVIEW -> ManeuverPreviewSheet(state, onDismiss = vm::closeSheet)
        GuidanceSheet.TRIP_DETAILS -> TripDetailsSheet(state, destination, onDismiss = vm::closeSheet)
        GuidanceSheet.NONE -> Unit
    }
```

- [ ] **Étape 7 : compiler et tester.**

```powershell
.\gradlew.bat :feature:navigation:compileDebugKotlin --offline -q
.\gradlew.bat :feature:navigation:testDebugUnitTest :core:map:testDebugUnitTest --offline -q
```
Résultat attendu : SUCCÈS. Le test C `NavigationCameraControllerTest` (`bottomPanel` 0,30 / 0,50 / paysage) reste vert : c'est la règle désormais alimentée.

- [ ] **Étape 8 : commiter.**

```powershell
git add feature/navigation/src/main/res/values/strings.xml feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationBottomPanel.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/TripDetailsSheet.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt
git commit -m @'
feat(guidage): panneau du bas repliable, trajet complet, marge caméra suivant la hauteur mesurée (D5)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 7 : D5 — icône d'attribution accessible (portrait sous le bouton « replier », paysage à droite de la colonne)

**Fichiers :**
- Créer : `map/MapAttribution.kt`.
- Test : `maptest/AttributionMarginsTest.kt` (nouveau).
- Modifier :
  - `map/MapController.kt` : import `android.view.Gravity` ; champs après la ligne 106 ; lignes 117-123 (`attach`) ; nouvelle fonction après `setMaximumFps` (lignes 147-150) ;
  - `map/RoadlyMap.kt` : signature (fin de liste), effet après `LaunchedEffect(mapView, maximumFps) { … }` ;
  - `nav/NavigationScreen.kt` : après la ligne 157 (`val landscape = …`), appel `RoadlyMap` (lignes 164-167), constantes en fin de fichier.

**Interfaces :**
- Produit :
  - `data class AttributionPlacement(val top: Boolean, val end: Boolean, val vertical: Dp, val side: Dp) { companion object { val DEFAULT_EDGE: Dp } }`
  - `internal object AttributionMargins { fun of(top: Boolean, end: Boolean, verticalPx: Int, sidePx: Int): IntArray }` (gauche, haut, droite, bas)
  - `fun MapController.setAttribution(top: Boolean, end: Boolean, verticalPx: Int, sidePx: Int)`
  - `RoadlyMap(…, attribution: AttributionPlacement? = null)`

- [ ] **Étape 1 : écrire le test qui échoue.**

`maptest/AttributionMarginsTest.kt` :

```kotlin
package fr.roadly.core.map

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** D5 : marges MapLibre (gauche, haut, droite, bas) selon le coin choisi pour l'icône d'attribution. */
class AttributionMarginsTest {
    @Test fun `bottom start - side on the left, vertical at the bottom`() {
        assertArrayEquals(intArrayOf(10, 0, 0, 40), AttributionMargins.of(top = false, end = false, verticalPx = 40, sidePx = 10))
    }

    @Test fun `top end - side on the right, vertical at the top`() {
        assertArrayEquals(intArrayOf(0, 120, 30, 0), AttributionMargins.of(top = true, end = true, verticalPx = 120, sidePx = 30))
    }
}
```

- [ ] **Étape 2 : vérifier l'échec.**

Commande : `.\gradlew.bat :core:map:testDebugUnitTest --offline -q --tests "*AttributionMarginsTest*"`
Résultat attendu : ÉCHEC de compilation, `Unresolved reference: AttributionMargins`.

- [ ] **Étape 3 : implémenter.**

`map/MapAttribution.kt` :

```kotlin
package fr.roadly.core.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * D5 : coin de l'icône d'attribution ([top] haut / bas, [end] droite / gauche) et marges en dp depuis les bords de la carte,
 * barres système comprises. La carte reste sous les barres (bord à bord) : on déplace l'icône, pas la carte.
 */
data class AttributionPlacement(val top: Boolean, val end: Boolean, val vertical: Dp, val side: Dp) {
    companion object {
        /** Marge par défaut au-dessus de la barre de navigation et depuis le bord gauche. */
        val DEFAULT_EDGE = 4.dp
    }
}

/** Marges `UiSettings.setAttributionMargins(gauche, haut, droite, bas)` (px) pour un coin donné (Kotlin pur). */
internal object AttributionMargins {
    fun of(top: Boolean, end: Boolean, verticalPx: Int, sidePx: Int): IntArray = intArrayOf(
        if (end) 0 else sidePx,
        if (top) verticalPx else 0,
        if (end) sidePx else 0,
        if (top) 0 else verticalPx,
    )
}
```

`map/MapController.kt` :

**1.** Ajouter `import android.view.Gravity` dans le bloc d'imports `android.*`.

**2.** Ancre (ligne 106) :
```kotlin
    var onRouteClick: ((String) -> Unit)? = null
```
Ajouter juste après :
```kotlin

    /** D5 : coin et marges (px) de l'icône d'attribution, appliqués à l'attache et à chaque changement. */
    private var attributionTop = false
    private var attributionEnd = false
    private var attributionMargins = intArrayOf(0, 0, 0, 0)
```

**3.** Ancien (lignes 117-123) :
```kotlin
    internal fun attach(map: MapLibreMap) {
        this.map = map
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isAttributionEnabled = true
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = false
```
Nouveau :
```kotlin
    internal fun attach(map: MapLibreMap) {
        this.map = map
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isAttributionEnabled = true
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = false
        applyAttribution(map)
```

**4.** Ancre (lignes 147-150, `setMaximumFps`) :
```kotlin
    fun setMaximumFps(fps: Int) {
        markerGate.minIntervalMs = FrameGate.intervalFor(fps)
    }
```
Ajouter juste après :
```kotlin

    /** D5 : place l'icône d'attribution ([top] / [end] = coin ; marges en px depuis les bords de la carte, barres système comprises). */
    fun setAttribution(top: Boolean, end: Boolean, verticalPx: Int, sidePx: Int) {
        attributionTop = top
        attributionEnd = end
        attributionMargins = AttributionMargins.of(top, end, verticalPx, sidePx)
        map?.let { applyAttribution(it) }
    }

    private fun applyAttribution(m: MapLibreMap) {
        m.uiSettings.attributionGravity = (if (attributionTop) Gravity.TOP else Gravity.BOTTOM) or (if (attributionEnd) Gravity.END else Gravity.START)
        val (left, top, right, bottom) = attributionMargins
        m.uiSettings.setAttributionMargins(left, top, right, bottom)
    }
```

`map/RoadlyMap.kt` :

**1. Imports** à ajouter :
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
```

**2. Signature.** Ancien :
```kotlin
    /** C3 : plafond d'images par seconde du rendu ([MapRendering.MAX_FPS] ; 30 en économie d'énergie). */
    maximumFps: Int = MapRendering.MAX_FPS,
) {
```
Nouveau :
```kotlin
    /** C3 : plafond d'images par seconde du rendu ([MapRendering.MAX_FPS] ; 30 en économie d'énergie). */
    maximumFps: Int = MapRendering.MAX_FPS,
    /** D5 : emplacement de l'icône d'attribution ; null = en bas à gauche, au-dessus de la barre de navigation (bord à bord). */
    attribution: AttributionPlacement? = null,
) {
```

**3. Effet.** Ancre :
```kotlin
    LaunchedEffect(mapView, maximumFps) {
        mapView.setMaximumFps(maximumFps)
        controller.setMaximumFps(maximumFps)
    }
```
Ajouter juste après :
```kotlin
    // D5 : sans emplacement imposé, l'icône d'attribution reste au-dessus de la barre de navigation (elle était dessous, inaccessible).
    val density = LocalDensity.current
    val navBars = WindowInsets.navigationBars
    val navBottom = navBars.getBottom(density)
    val navLeft = navBars.getLeft(density, LayoutDirection.Ltr)
    LaunchedEffect(attribution, navBottom, navLeft, density) {
        val p = attribution
        if (p == null) {
            val edge = with(density) { AttributionPlacement.DEFAULT_EDGE.roundToPx() }
            controller.setAttribution(top = false, end = false, verticalPx = navBottom + edge, sidePx = navLeft + edge)
        } else {
            controller.setAttribution(p.top, p.end, with(density) { p.vertical.roundToPx() }, with(density) { p.side.roundToPx() })
        }
    }
```

`nav/NavigationScreen.kt` :

**1. Imports** à ajouter :
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.LayoutDirection
import fr.roadly.core.map.AttributionPlacement
```
Ne pas ajouter `androidx.compose.foundation.layout.navigationBars` si `navigationBarsPadding` est déjà importé : ce sont deux symboles distincts. Ajouter alors aussi `import androidx.compose.foundation.layout.navigationBars`.

**2.** Ancien (lignes 157-158, après la tâche 6) :
```kotlin
        val landscape = maxWidth > maxHeight
        GuidanceMapEffects(uiState, cameraMode, powerSave, landscape, map, camera, bottomPanel)
```
Nouveau :
```kotlin
        val landscape = maxWidth > maxHeight
        // D5 : icône d'attribution toujours accessible. Portrait : sous le bouton « replier » (en haut à droite), zone libre que le panneau
        // du bas soit replié ou déplié. Paysage : en bas, juste à droite de la colonne gauche.
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navInsets = WindowInsets.navigationBars.asPaddingValues()
        val attribution = if (landscape) {
            AttributionPlacement(
                top = false, end = false, vertical = navInsets.calculateBottomPadding() + RoadlySpacing.xs,
                side = navInsets.calculateLeftPadding(LayoutDirection.Ltr) + maxWidth * LANDSCAPE_LEFT_COLUMN + RoadlySpacing.s,
            )
        } else {
            AttributionPlacement(top = true, end = true, vertical = statusTop + RoadlySpacing.screenEdge + BANNER_TOGGLE_SIZE + RoadlySpacing.s, side = RoadlySpacing.screenEdge + RoadlySpacing.sm)
        }
        GuidanceMapEffects(uiState, cameraMode, powerSave, landscape, map, camera, bottomPanel)
```

**3.** Ancien (lignes 164-167) :
```kotlin
            RoadlyMap(
                styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onUserGesture = vm::onUserGesture,
                maximumFps = if (powerSave.value) MapRendering.MAX_FPS_POWER_SAVE else MapRendering.MAX_FPS,
            )
```
Nouveau :
```kotlin
            RoadlyMap(
                styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onUserGesture = vm::onUserGesture,
                maximumFps = if (powerSave.value) MapRendering.MAX_FPS_POWER_SAVE else MapRendering.MAX_FPS,
                attribution = attribution,
            )
```

**4.** Fin de fichier :
```kotlin

/** Part de la largeur occupée par la colonne gauche de `LandscapeOverlay` (même valeur que `fillMaxWidth(0.46f)`). */
private const val LANDSCAPE_LEFT_COLUMN = 0.46f

/** Taille du bouton « replier le bandeau » des overlays (`size = 44.dp`) : l'attribution se place dessous en portrait. */
private val BANNER_TOGGLE_SIZE = 44.dp
```

- [ ] **Étape 4 : vérifier le succès.**

```powershell
.\gradlew.bat :core:map:testDebugUnitTest --offline -q
.\gradlew.bat :feature:navigation:compileDebugKotlin :feature:home:compileDebugKotlin :feature:route:compileDebugKotlin :feature:trips:compileDebugKotlin --offline -q
```
Résultat attendu : SUCCÈS. Les autres écrans compilent sans changement grâce au paramètre par défaut.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/map/src/main/java/fr/roadly/core/map/MapAttribution.kt core/map/src/test/java/fr/roadly/core/map/AttributionMarginsTest.kt core/map/src/main/java/fr/roadly/core/map/MapController.kt core/map/src/main/java/fr/roadly/core/map/RoadlyMap.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt
git commit -m @'
fix(carte): icône d'attribution hors de la barre de navigation — portrait sous le bouton « replier », paysage à droite de la colonne (D5)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 8 : tests instrumentés Compose (HONOR) — bandeau → aperçu, rond-point, voies, panneau et caméra, paysage

**Fichiers :**
- Modifier :
  - `feature/navigation/build.gradle.kts`, ligne 12 (`defaultConfig`) et fin des dépendances ;
  - `nav/NavigationScreen.kt`, ligne 187 (`private fun PortraitOverlay(`) et ligne 254 (`private fun LandscapeOverlay(`).
- Créer : `navat/TestNavigation.kt`, `navat/NavigationPremiumUiTest.kt`.

**Interfaces :**
- Consomme :
  - `PortraitOverlay` et `LandscapeOverlay` (devenus `internal`) ;
  - `GuidanceSheets`, `BANNER_TAG`, `PANEL_SUMMARY_TAG`, `previewRoundabout`, `RoundaboutManeuverView`, `buildUiState` ;
  - `vm.guidanceSheet`, `vm.bottomPanelFraction` ;
  - fausses implémentations du jeu principal : `FakeLocationRepository`, `FakeUserPreferencesRepository`, `FakeSpeechService`, `FakeNavigationServiceController`, `NoopSpeedLimitProvider`, `NoopVigilanceZoneProvider`, `NoopRainProvider`, `NoopTripRecorder`, `TestRoutes`.
- Produit : `internal fun testNavigationViewModel(prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository()): NavigationViewModel` et `internal fun guidingUi(route: Route, along: Double): NavigationUiState` (jeu `androidTest`).

- [ ] **Étape 1 : instrumentation du module.**

`feature/navigation/build.gradle.kts`, ancien (ligne 12) :
```kotlin
    defaultConfig { minSdk = 29 }
```
Nouveau :
```kotlin
    defaultConfig { minSdk = 29; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
```
Ancien (dernières lignes de `dependencies`) :
```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
```
Nouveau :
```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Compose tire espresso-core 3.5.0, qui plante sur Android 16+ (InputManager.getInstance retiré) : même parade que core:designsystem.
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
```

- [ ] **Étape 2 : overlays visibles des tests.**

`nav/NavigationScreen.kt`, ancien (ligne 187) :
```kotlin
private fun PortraitOverlay(
```
Nouveau :
```kotlin
internal fun PortraitOverlay(
```
Ancien (ligne 254) :
```kotlin
private fun LandscapeOverlay(
```
Nouveau :
```kotlin
internal fun LandscapeOverlay(
```

- [ ] **Étape 3 : écrire `navat/TestNavigation.kt`.**

```kotlin
package fr.roadly.feature.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.RoundaboutResolver
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import java.time.ZoneId

/** ViewModel de guidage sans réseau ni carte : session inactive, fausses implémentations du jeu principal (tests instrumentés). */
internal fun testNavigationViewModel(prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository()): NavigationViewModel {
    val routing = object : RoutingProvider {
        override val id = "test"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }
    val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Main
    }
    val session = NavigationSession(
        FakeLocationRepository(), routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(),
        NavigationClock { System.currentTimeMillis() }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers,
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )
    val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }
    val vehicles = object : VehicleProfileRepository {
        override val profile = flowOf<VehicleProfile?>(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }
    return NavigationViewModel(session, prefs, config, RouteAlternativesUseCase(routing), routing, vehicles, NoCommunity)
}

/** État d'écran de guidage à l'abscisse [along] de [route] (36 km/h, voies et rond-point comme les publierait la session). */
internal fun guidingUi(route: Route, along: Double): NavigationUiState {
    val t = RouteTracker(route)
    val p = t.update(LocationFix(t.pointAt(along), null, null, 5f, 0L))
    val ms = route.maneuvers
    val upcoming = ms.subList(minOf(p.nextManeuverIndex + 1, ms.size), minOf(p.nextManeuverIndex + 4, ms.size))
    val g = NavigationState.Guiding(
        route, p, 36.0, null, upcoming, ms[p.nextManeuverIndex].lanes, 0L, gpsLost = false, rerouting = false,
        roundabout = RoundaboutResolver.current(RoundaboutResolver.resolveAll(route), p.nextManeuverIndex),
    )
    return buildUiState(g, muted = false, following = true, toleranceKmh = 5, styleUrl = "", zone = ZoneId.of("Europe/Paris"))
}

private object NoCommunity : CommunityRepository {
    override val isConfigured = flowOf(false)
    override suspend fun report(kind: ReportKind, at: GeoPoint, headingDeg: Float?) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun nearby(box: BoundingBox) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun vote(reportId: String, confirm: Boolean) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun profile() = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
    override suspend fun leaderboard(limit: Int) = Outcome.Failure(AppError.ProviderUnavailable("communauté"))
}
```

- [ ] **Étape 4 : écrire `navat/NavigationPremiumUiTest.kt`.**

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Étape D sur appareil : bandeau → aperçu (D1), rond-point (D2, scénario 4), voies (D4, scénario 18), panneau du bas et caméra (D5), paysage (scénario 19). */
class NavigationPremiumUiTest {
    @get:Rule val rule = createComposeRule()

    private fun newVm(): NavigationViewModel = rule.runOnUiThread { testNavigationViewModel() }

    private fun setOverlay(state: NavigationUiState, vm: NavigationViewModel, landscape: Boolean = false) {
        rule.setContent {
            RoadlyTheme(darkTheme = true, drivingMode = true) {
                if (landscape) {
                    // HONOR en paysage, barres retirées : ~1 021 × 457 dp.
                    DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(1021.dp, 457.dp))) {
                        Box(Modifier.fillMaxSize()) { LandscapeOverlay(state, true, {}, {}, {}, vm); GuidanceSheets(state, vm) }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) { PortraitOverlay(state, true, {}, {}, {}, vm); GuidanceSheets(state, vm) }
                }
            }
        }
    }

    @Test fun banner_click_opens_the_maneuver_preview_and_close_dismisses_it() {
        val vm = newVm()
        setOverlay(guidingUi(TestRoutes.straight(), 300.0), vm)
        rule.onNodeWithTag(BANNER_TAG).assertHeightIsAtLeast(56.dp).performClick()
        rule.onNodeWithText("Fermer").assertIsDisplayed()
        assertEquals(GuidanceSheet.MANEUVER_PREVIEW, vm.guidanceSheet.value)
        rule.onNodeWithText("Fermer").performClick()
        rule.onNodeWithText("Fermer").assertDoesNotExist()
        assertEquals(GuidanceSheet.NONE, vm.guidanceSheet.value)
    }

    @Test fun roundabout_view_shows_the_exit_number_and_the_exit_street() {
        rule.setContent {
            RoadlyTheme(darkTheme = true, drivingMode = true) {
                RoundaboutManeuverView(previewRoundabout(270.0, 3, "Avenue de la Gare"), exitNumber = 3, compact = false)
            }
        }
        rule.onNodeWithContentDescription("Rond-point, 3e sortie").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
        rule.onNodeWithText("Avenue de la Gare").assertIsDisplayed()
    }

    @Test fun the_banner_draws_the_roundabout_when_the_next_maneuver_enters_one() {
        setOverlay(guidingUi(TestRoutes.twoRoundabouts(), 120.0), newVm())
        rule.onNodeWithContentDescription("Rond-point, 2e sortie").assertIsDisplayed()
        rule.onNodeWithText("puis").assertIsDisplayed()
        rule.onNodeWithText("Rue Médiane").assertIsDisplayed()
    }

    @Test fun lanes_are_absent_without_data() {
        val r = TestRoutes.straight()
        setOverlay(guidingUi(r, RouteTracker(r).maneuverStartM[1] - 300.0), newVm())
        rule.onNodeWithContentDescription("Voies conseillées", substring = true).assertDoesNotExist()
    }

    @Test fun lanes_show_within_400_m_when_the_server_sends_them() {
        val r0 = TestRoutes.straight()
        val lanes = listOf(Lane(setOf(LaneDirection.STRAIGHT), valid = false), Lane(setOf(LaneDirection.RIGHT), valid = true, active = true))
        val r = r0.copy(maneuvers = r0.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(lanes = lanes) else m })
        setOverlay(guidingUi(r, RouteTracker(r).maneuverStartM[1] - 300.0), newVm())
        rule.onNodeWithContentDescription("Voies conseillées : 2 sur 2").assertIsDisplayed()
    }

    @Test fun the_bottom_panel_expands_and_the_camera_margin_follows() {
        val vm = newVm()
        setOverlay(guidingUi(TestRoutes.straight(), 300.0), vm)
        rule.waitForIdle()
        val collapsed = vm.bottomPanelFraction.value!!
        rule.onNodeWithContentDescription("Arrêter").assertWidthIsAtLeast(56.dp).assertHeightIsAtLeast(56.dp)
        rule.onNodeWithContentDescription("Couper la voix").assertWidthIsAtLeast(56.dp)
        rule.onNodeWithTag(PANEL_SUMMARY_TAG).assertHeightIsAtLeast(56.dp).performClick()
        rule.onNodeWithText("Trajet complet").assertIsDisplayed()
        val expanded = vm.bottomPanelFraction.value!!
        assertTrue("déplié ($expanded) > replié ($collapsed)", expanded > collapsed)
        rule.onNodeWithTag(PANEL_SUMMARY_TAG).performClick()
        rule.onNodeWithText("Trajet complet").assertDoesNotExist()
        assertEquals(collapsed, vm.bottomPanelFraction.value!!, 1e-4f)
    }

    @Test fun the_landscape_overlay_composes_with_banner_and_bottom_panel() {
        setOverlay(guidingUi(TestRoutes.twoRoundabouts(), 120.0), newVm(), landscape = true)
        rule.onNodeWithTag(BANNER_TAG).assertIsDisplayed()
        rule.onNodeWithTag(PANEL_SUMMARY_TAG).assertIsDisplayed()
        rule.onNodeWithContentDescription("Arrêter").assertIsDisplayed()
        rule.onNodeWithText("Rue Médiane").assertIsDisplayed()
    }
}
```

Note : « Couper la voix » est la description de `RoundIconButton` dans la ligne repliée (suivi actif, voix active). La colonne d'actions porte le même bouton : si l'assertion trouve deux nœuds (« Expected exactly '1' node »), remplacer cette ligne par `rule.onAllNodesWithContentDescription("Couper la voix")[1].assertWidthIsAtLeast(56.dp)`, avec l'import `androidx.compose.ui.test.onAllNodesWithContentDescription`. Le panneau du bas est composé après la colonne d'actions.

- [ ] **Étape 5 : compiler.**

Commande : `.\gradlew.bat :feature:navigation:compileDebugAndroidTestKotlin --offline -q`
Résultat attendu : SUCCÈS.

- [ ] **Étape 6 : lancer sur le HONOR** (téléphone branché, écran déverrouillé, `adb devices` liste `AREE026226000174`).

Commande : `.\gradlew.bat :feature:navigation:connectedDebugAndroidTest --offline`
Résultat attendu : `BUILD SUCCESSFUL`, 7 tests verts. Rapport : `feature\navigation\build\reports\androidTests\connected\debug\index.html`.

Si `DeviceConfigurationOverride` est introuvable hors ligne (artefact `ui-test` absent du cache) : remplacer le bloc paysage par `Box(Modifier.requiredSize(1021.dp, 457.dp))` et retirer `assertIsDisplayed` pour les nœuds hors de l'écran. Noter l'écart dans le journal.

- [ ] **Étape 7 : non-régression JVM.**

Commande : `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q`
Résultat attendu : SUCCÈS.

- [ ] **Étape 8 : commiter.**

```powershell
git add feature/navigation/build.gradle.kts feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt feature/navigation/src/androidTest/java/fr/roadly/feature/navigation/TestNavigation.kt feature/navigation/src/androidTest/java/fr/roadly/feature/navigation/NavigationPremiumUiTest.kt
git commit -m @'
test(guidage): tests Compose instrumentés — aperçu, rond-point, voies, panneau du bas et caméra, paysage (D1–D5)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 9 : passe appareil HONOR — captures, rond-point réel, accessibilité, cibles 56 dp, consignation

**Fichiers :**
- Modifier `ROADLY_3D_NAVIGATION_TESTS.md`, avec l'outil Edit uniquement : ligne 3, lignes 22, 23, 25, 26, 35 et 36, et journal (après la ligne 74).
- Créer dans `docs/captures/etape-d/` :
  - `portrait-nuit-replie.png`, `portrait-nuit-deplie.png`, `portrait-jour-replie.png` ;
  - `apercu-nuit.png`, `apercu-jour.png` ;
  - `trajet-complet-nuit.png` ;
  - `rond-point-bandeau.png`, `rond-point-apercu.png` ;
  - `paysage-nuit.png`, `paysage-jour.png` ;
  - `attribution-portrait.png`.
- Pas de code.

**Interfaces :**
- Consomme : les tâches 1 à 8.
- Produit : les essais consignés.

- [ ] **Étape 1 : vérification complète et installation.**

```powershell
.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
$n = 0; Get-ChildItem -Recurse -Filter 'TEST-*.xml' -Path core, feature | Where-Object FullName -match 'test-results' | ForEach-Object { $n += [int]([xml](Get-Content $_.FullName)).testsuite.tests }; $n
git rev-parse --short HEAD
adb -s AREE026226000174 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s AREE026226000174 shell wm density
```
Résultat attendu : SUCCÈS. Le nombre de tests vaut au moins 431 + 32 (1 : 8 ; 2 : 10 ; 3 : 4 ; 7 : 2 ; plus ceux de la tâche 8, instrumentés et non comptés ici). Noter ce nombre, l'empreinte et la densité (par exemple `440` → facteur 2,75).

- [ ] **Étape 2 : captures portrait et paysage, jour et nuit.**

Lancer un guidage vers une destination choisie par l'utilisateur et notée dans le journal. Thème « Sombre » ou « Auto » de nuit. Pour chaque capture :
```powershell
$d = 'AREE026226000174'
adb -s $d shell screencap -p /sdcard/Download/roadly-d.png
adb -s $d pull /sdcard/Download/roadly-d.png docs\captures\etape-d\portrait-nuit-replie.png
```
Séquence :
1. `portrait-nuit-replie.png` ;
2. toucher la ligne ETA → `portrait-nuit-deplie.png` : destination, « Trajet complet », « Itinéraires » ;
3. toucher « Trajet complet » → `trajet-complet-nuit.png`, puis « Fermer » ;
4. toucher le bandeau → `apercu-nuit.png`, puis « Fermer » ;
5. toucher l'icône « i » en haut à droite, sous le bouton « replier » → `attribution-portrait.png` (fenêtre OpenFreeMap / OpenMapTiles / OpenStreetMap) ;
6. tourner l'appareil → `paysage-nuit.png` ;
7. Réglages → Apparence → « Clair », reprendre le guidage → `portrait-jour-replie.png`, `apercu-jour.png`, `paysage-jour.png` ; remettre « Auto ».

Vérifier sur chaque capture :
- panneau replié : aucune intersection masquée ;
- caméra : la voiture est au-dessus du bloc du bas, replié comme déplié (la caméra suit en moins d'une seconde) ;
- paysage : chevauchement éventuel entre la pile du bandeau et le bloc du bas (à noter, voir LIMITES) ;
- panneau déplié en portrait : la rangée 2D/3D est-elle recouverte ? (à noter).

- [ ] **Étape 3 : rond-point réel (D2, scénarios 4 et 5).**
- Choisir, avec l'utilisateur, une destination dont la **première manœuvre** depuis la position du téléphone est un rond-point (« Au rond-point, prenez la n-e sortie »). En stationnaire, le bandeau montre la prochaine manœuvre.
- Vérifier :
  - le schéma compact dans le bandeau (numéro au centre) → `rond-point-bandeau.png` ;
  - le schéma grand dans l'aperçu, avec la rue de sortie → `rond-point-apercu.png` ;
  - la position de la sortie cohérente avec la carte (2e sortie en face, 1re à droite, 3e à gauche) ;
  - aucune autre sortie dessinée.
- Si aucun rond-point n'est la première manœuvre près du téléphone : noter « à faire en roulant ». Les aperçus Compose et le test instrumenté couvrent le rendu.

- [ ] **Étape 4 : accessibilité et cibles de 56 dp (mesure).**

```powershell
$d = 'AREE026226000174'
adb -s $d shell uiautomator dump /sdcard/Download/roadly-ui.xml
adb -s $d pull /sdcard/Download/roadly-ui.xml "$env:TEMP\roadly-ui.xml"
[xml]$ui = Get-Content "$env:TEMP\roadly-ui.xml" -Encoding UTF8
$dens = 2.75   # densité de l'étape 1 / 160
$ui.SelectNodes('//node[@content-desc!=""]') | ForEach-Object {
  if ($_.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { '{0} : {1:N0} × {2:N0} dp' -f $_.'content-desc', (([int]$matches[3]-[int]$matches[1])/$dens), (([int]$matches[4]-[int]$matches[2])/$dens) }
}
```
Résultat attendu : des descriptions présentes pour « Arrêter », « Couper la voix », « Rond-point, … » (si rond-point), « Voies conseillées … » (si voies), « Afficher les détails du trajet » (action de la ligne ETA). « Arrêter », « Couper la voix » et la ligne ETA mesurent au moins 56 × 56 dp.

Si `uiautomator` répond « could not get idle state » (carte animée en continu) : passer en vue 2D et réessayer. À défaut, mesurer le diamètre du bouton Arrêter sur `portrait-nuit-replie.png` en pixels, divisé par la densité, et noter la méthode.

- [ ] **Étape 5 : à faire en roulant (par l'utilisateur, avec un passager si possible).**
1. **Autoroute avec sortie (scénario 7).** Panneau violet à l'approche (≤ 400 m au-delà de 70 km/h) : « SORTIE n », route, directions. La pastille orange du bandeau disparaît à ce moment.
2. **Échangeur (scénario 8).** « puis … » visible quand la manœuvre suivante est à moins de 500 m. L'aperçu montre « Sortie suivante ».
3. **Rond-point en approche réelle.** Schéma stable, numéro juste, flèche à la bonne branche.
4. **Voies (scénario 18).** Sur FOSSGIS public, aucune voie attendue (LIMITE). Noter si un trajet en renvoie.

- [ ] **Étape 6 : consigner dans `ROADLY_3D_NAVIGATION_TESTS.md` (outil Edit uniquement).**

**1. Ligne 3.** Remplacer ``État actuel (fin d'étape C, `5906c87`) : 431 tests JVM verts, 3 instrumentés, aucune CI.`` par ``État actuel (fin d'étape D, `<empreinte de l'étape 1>`) : <nombre de l'étape 1> tests JVM verts, 10 instrumentés, aucune CI.``.

**2. Ligne 22.** Ancien :
```markdown
| 4 | Rond-point 2 sorties | JVM `ParisRoute` : `RoundaboutInfo`, cible stable, sortie annoncée au bon moment ; Compose preview | oui | B4, D2 |
```
Nouveau :
```markdown
| 4 | Rond-point 2 sorties | JVM `ParisRoute` : `RoundaboutInfo`, cible stable, sortie annoncée au bon moment ; `RoundaboutGeometryTest` (arc 168°) ; aperçus Compose ; instrumenté (numéro, rue, bandeau) | oui | B4, D2 |
```

**3. Ligne 23.** Ancien :
```markdown
| 5 | Rond-point ≥ 3 sorties | JVM `ParisRoute` (5e sortie) ; preview | oui | B4, D2 |
```
Nouveau :
```markdown
| 5 | Rond-point ≥ 3 sorties | JVM `ParisRoute` (5e sortie), autoroute (3e sortie : arc 282° alors que le cap de sortie lit +140°) ; aperçus Compose (3e sortie, demi-tour) | oui | B4, D2 |
```

**4. Ligne 25.** Ancien :
```markdown
| 7 | Sortie d'autoroute | JVM fixture autoroute : `exitNumber`, `exitToward`, approche 400 m, panneau | oui | B3, D3 |
```
Nouveau :
```markdown
| 7 | Sortie d'autoroute | JVM fixture autoroute : `exitNumber`, `exitToward`, approche 400 m ; `exitSignFor`, `sign` dans l'état (D3) ; aperçus `ExitSignPanel` | oui | B3, D3 |
```

**5. Ligne 26.** Ancien :
```markdown
| 8 | Échangeur plusieurs bretelles | JVM fixture : côté de la bretelle, manœuvre suivante affichée sans masquer l'actuelle | oui | B3, D3 |
```
Nouveau :
```markdown
| 8 | Échangeur plusieurs bretelles | JVM fixture : côté de la bretelle ; « puis … » sous le bandeau à moins de 500 m (`thenFor`), « Sortie suivante » dans l'aperçu sans masquer l'actuelle | oui | B3, D1, D3 |
```

**6. Ligne 35.** Ancien :
```markdown
| 18 | Affichage sans données de voies | JVM : `lanes` vide → composant absent ; instrumenté Compose | appareil | D4 |
```
Nouveau :
```markdown
| 18 | Affichage sans données de voies | JVM : `lanesVisible` (vide → absent, fenêtre de 400 m), `laneStates` ; instrumenté Compose (absentes sans données, présentes à 300 m) | appareil | D4 |
```

**7. Ligne 36.** Ancien :
```markdown
| 19 | Mode paysage | instrumenté Compose (overlay paysage, D5) ; appareil : captures, boutons caméra en bas à droite (C) | appareil | C1, D5 |
```
Nouveau :
```markdown
| 19 | Mode paysage | instrumenté Compose (overlay paysage 1 021 × 457 dp : bandeau, panneau du bas, « puis ») ; appareil : captures jour / nuit, boutons caméra en bas à droite (C) | appareil | C1, D5 |
```

**8. Journal.** Ajouter après la ligne 74, une ligne par essai réellement fait, en remplaçant chaque `<…>` par la valeur observée :
```markdown
| <date> | D : 4, 5 (rond-point : <bandeau / aperçu / à faire en roulant>), 18, 19 ; captures jour / nuit ; attribution ; accessibilité | HONOR Magic 8 Pro | <résultat> | captures `docs/captures/etape-d/` ; destination <notée> ; cibles mesurées : Arrêter <n> dp, voix <n> dp, ligne ETA <n> dp (<méthode>) ; attribution portrait accessible sous le bouton « replier » (défaut C corrigé) ; panneau déplié <recouvre / ne recouvre pas> la rangée 2D/3D ; paysage <chevauchement ou non> ; instrumentés : 7/7 verts ; à faire en roulant : sortie d'autoroute (panneau), échangeur (« puis »), rond-point en approche |
```

- [ ] **Étape 7 : commiter.**

```powershell
git add ROADLY_3D_NAVIGATION_TESTS.md docs/captures/etape-d
git commit -m @'
docs(tests): passe appareil de l'étape D — captures jour / nuit, rond-point, cibles 56 dp, attribution corrigée (D1–D5)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

## Surface de fusion avec `feat/roadly-ai`

Base commune `60033fe`. L'IA modifie dans `feature/navigation` les fichiers `NavigationNavigation.kt`, `NavigationScreen.kt`, `NavigationViewModel.kt` et `NavigationViewModelTest.kt` (vérifié par `git diff 60033fe HEAD --stat` dans `GPS-roadly-ai`).

| Fichier | Les deux branches | Résolution |
|---|---|---|
| `nav/NavigationScreen.kt` | **IA :** signatures 104 / 114-115 (emplacements assistant), appels 110 / 170 / 172, paramètres ajoutés après `vm: NavigationViewModel,` (lignes 193 et 260), `assistantButton()` en tête des colonnes d'actions (après 223 et 278), `assistantPanel()` en tête de la colonne du bas portrait (après 238) et avant `BottomBlock` en paysage (après 270). **D :** imports ; ligne après 154 (`bottomPanel`) ; 157-158 (attribution, `GuidanceMapEffects`) ; 164-167 (`RoadlyMap`) ; ligne après 182 (`GuidanceSheets`) ; 187 et 254 (`private` → `internal`) ; 203-204 et 266-267 (→ `BannerStack`) ; 312-325 (`BottomBlock`) ; 477-528 (`MapTick`, `GuidanceMapEffects`) ; fonctions et constantes en fin de fichier | Garder tout. Aucune ligne n'est touchée par les deux branches. Les lignes qui entourent les insertions de l'IA (219-224, 235-239, 270-271, 274-279) sont intactes, et chaque modification D en est séparée d'au moins une ligne inchangée. Après fusion, les appels des tests instrumentés `PortraitOverlay(state, true, {}, {}, {}, vm)` et `LandscapeOverlay(…)` doivent passer `{}` et `{}` pour les deux nouveaux paramètres de l'IA |
| `nav/NavigationViewModel.kt` | **IA :** constructeur (`GuidanceActions`), `stop`, `loadAlternatives`, `addStop`, `reportsAhead`, imports. **D :** import `flow.map`, bloc inséré après `mapDetail` (code C, absent de l'IA) | Pas de chevauchement de lignes. L'import `map` s'ajoute près de `distinctUntilChanged` / `scan` (C) |
| `navtest/NavigationViewModelTest.kt` | **IA :** constructeurs. **D :** 2 tests (assistant `NavigationViewModel(s, p, c)`) | Garder la version IA de l'assistant |
| `navat/TestNavigation.kt` (D) | — | Après fusion, construire le ViewModel avec la signature IA (`GuidanceActions` au lieu de `RouteAlternativesUseCase`, `RoutingProvider`, `VehicleProfileRepository`) |
| `nav/NavigationNavigation.kt`, `app/RoadlyNavHost.kt` | IA seulement | Rien à résoudre : D n'y touche pas. Les emplacements assistant restent ceux de l'IA |

`feat/roadly-ai` ne touche aucun fichier de `core/map`, `core/navigation` déjà présent (il en ajoute d'autres), `core/model`, ni `feature/navigation/src/main/res`. Les fichiers nouveaux de D (`BannerStack.kt`, `ExitSignPanel.kt`, `RoundaboutManeuverView.kt`, `LaneGuidanceView.kt`, `NavigationBottomPanel.kt`, `TripDetailsSheet.kt`, `ManeuverPreviewSheet.kt`, `BottomPanelGeometry.kt`, `RoundaboutGeometry.kt`, `MapAttribution.kt`) n'ont pas d'équivalent IA.

**Après fusion :** `assistantPanel()` est au-dessus du compteur en portrait, donc hors du bloc mesuré par `BottomBlock`. Pour que la caméra le compte aussi (spec D5 : « ce qui règle aussi l'`assistantPanel` »), déplacer, dans le commit de fusion, le `onGloballyPositioned` de `BottomBlock` sur la colonne du bas de `PortraitOverlay` (ligne 236). Ce changement d'une ligne n'est possible qu'une fois les deux branches réunies.

## LIMITES documentées par cette étape

- **Rond-point, autres sorties.** Elles ne sont jamais dessinées : `/route` ne les fournit pas.
- **Rond-point, schéma.** Il suit l'arc parcouru sur l'anneau, pas le cap exact de la branche de sortie (3e sortie de l'autoroute : cap +140°, arc 282°). Les arcs courts sont portés à 40°, les demi-tours bornés à 340°.
- **Rond-point, sens de circulation.** Seule la circulation à droite est gérée.
- **Panneau de direction.** Il n'apparaît que si Valhalla renvoie `sign` (FOSSGIS : sorties 31b et 19 de la fixture autoroute). Contraste nuit d'environ 4,2:1, admis pour du « grand texte » seulement.
- **Voies.** Le serveur public FOSSGIS n'en renvoie pas sur les trajets enregistrés : le composant est réel mais rarement visible. Il est validé sur JVM et en instrumenté.
- **Trajet complet.** Longueur par étape (Valhalla), pas de distance cumulée depuis la position (B2).
- **Panneau déplié (portrait).** Sur un écran d'environ 1 000 dp, il peut recouvrir la rangée 2D/3D de la colonne d'actions. Replié, aucune intersection (tâche 9, étape 2).
- **Paysage.** Sur environ 457 dp, la pile bandeau + « puis » + panneau de sortie + voies peut chevaucher l'Horizon et le panneau du bas, à l'approche d'une sortie avec voies (à mesurer en roulant).
- **Marge de la caméra.** Elle est quantifiée par pas de 2 % (arrondi au-dessus) et ignorée en paysage (règle C).
- **Roadly AI.** Aucun emplacement sur cette branche. Après fusion, `assistantPanel` n'est pas compté dans la marge tant que la mesure n'est pas déplacée (voir « Surface de fusion »).
- **Nom de destination.** Il n'existe que si la course a été lancée avec un nom (`RouteViewModel`) ou vers la même destination (alternative). Sinon, la ligne est masquée et la feuille titre « Itinéraire ».

---

## Auto-revue

**1. Couverture de la spécification.**

| Exigence de `ROADLY_3D_NAVIGATION_PLAN.md` §D (et décisions) | Tâche | Preuve |
|---|---|---|
| D1 tap sur le bandeau → feuille « Aperçu de la manœuvre » (grande icône / schéma, instruction, rue, voies, 3 suivantes, « Fermer ») | 3, 5 | `GuidanceSheets`, `ManeuverPreviewContent` ; instrumenté `banner_click_opens_the_maneuver_preview…` |
| D1 la feuille ne bloque pas les mises à jour | 5 | contenu lu sur `state` en direct ; état dans le ViewModel (écart 1) |
| D1 « puis → Rue X » à moins de 500 m, dans les deux overlays | 2, 5 | `thenFor`, `THEN_WITHIN_M`, tests JVM ; `BannerStack` dans les deux overlays ; instrumenté (« puis », « Rue Médiane », portrait et paysage) |
| D2 anneau, entrée en bas, arc parcouru, sortie à l'angle calculé, flèche, numéro, rue ; compact / grand | 1, 4, 5 | `RoundaboutGeometryTest` (Paris, autoroute) ; `RoundaboutManeuverView` ; bandeau (`roundabout`) et aperçu |
| D2 aperçus Compose : 2e, 3e sortie, demi-tour, sans numéro ; rendu sur appareil | 4, 9 | 4 aperçus Nuit + 1 Jour ; tâche 9, étape 3 |
| D2 `roundabout` exposé dans l'état (`combine` intact) | 2 | `the roundabout reaches the banner only…` |
| D3 `ExitSignPanel` : numéro, directions, route, voies, distance, prochaine / suivante, à l'approche | 2, 5 | `exitSignFor`, `sign` ; `BannerStack` (approche) ; aperçu (« Sortie suivante » des suivantes) |
| D3 la pastille « SORTIE n · Direction » est remplacée à l'approche | 5 | `signShownBelow` dans `ManeuverBanner` |
| D4 actives / valides / interdites, multi-directions, à 400 m, disparition après la manœuvre, masquées sans données | 2, 4 | `laneStates`, `lanesVisible`, test « appear within 400 m and go after it » ; `LaneGuidanceView` ; instrumentés « absent / within 400 m » |
| D4 `LaneStrip` renommé, 2 appels mis à jour | 4, 5 | `git mv` ; appels renommés (4), puis absorbés par `BannerStack` (5) |
| D5 panneau repliable (poignée / tap / glissement), ETA, temps, distance, voix, Arrêter, destination, « Trajet complet », « Itinéraires », Horizon au-dessus | 3, 6 | `NavigationBottomPanel`, `TripDetailsSheet` ; instrumenté `the_bottom_panel_expands…` |
| D5 hauteur mesurée → `bottomPanelFraction` → caméra, dans les deux états | 3, 6 | `BottomPanelGeometry` (tests), `setBottomPanelFraction`, `MapTick.bottomPanel` → `NavigationCameraInput` ; instrumenté (fraction déplié > replié, puis retour) |
| D5 contrôles carte à leur place ; Roadly AI absent (documenté) | 6, fin | colonnes d'actions intactes ; écart 12, LIMITES, surface de fusion |
| D5 acceptation : captures portrait / paysage, cibles ≥ 56 dp, aucune intersection cachée en replié | 8, 9 | instrumenté (Arrêter, voix, ligne ETA ≥ 56 dp) ; tâche 9, étapes 2 et 4 |
| Défaut C : attribution sous la barre de navigation en portrait | 7, 9 | `AttributionMargins` (tests), `MapController.setAttribution`, `RoadlyMap(attribution)` ; capture `attribution-portrait.png` |
| Scénarios 4, 5, 7, 8, 18, 19 | 1, 2, 8, 9 | matrice mise à jour (tâche 9, étape 6) |

**2. Recherche de trous.**
- Aucun « TBD », « TODO », « à compléter » ni « comme la tâche N ».
- La seule branche provisoire est `GuidanceSheet.TRIP_DETAILS, GuidanceSheet.NONE -> Unit` (tâche 5). C'est un état réel, rien ne l'ouvre encore. La tâche 6 la remplace, avec l'ancien texte cité.
- Les valeurs inconnues à l'écriture sont des observations d'appareil (tâche 9). Chacune a sa commande ou sa méthode.
- Les valeurs attendues de `RoundaboutGeometryTest` (168,1 ; 167,8 ; 281,9 ; 20,3) ont été calculées le 2026-11-01 sur les deux fixtures, avec la même formule de cap que `RouteTracker.bearing` et le même seuil de 0,5 m (aucun segment écarté). La tolérance des tests est de 0,5°.

**3. Cohérence des noms entre tâches.**
- `RoundaboutGeometry.sweepFromRing` / `diagramSweepDeg` / `MIN_DIAGRAM_SWEEP_DEG` / `MAX_DIAGRAM_SWEEP_DEG` : tâche 1, utilisés aux tâches 4 et 1.
- Tâche 2, utilisés aux tâches 4, 5, 6 et 8 :
  - `ThenUi(type, label)`, `ExitSignUi(exitNumber, toward, branch)`, `LaneState`, `TripStepUi`, `GuidanceSheet` ;
  - `UpcomingUi.instruction` et `.sign` ;
  - `NavigationUiState.roundabout`, `then`, `sign`, `showLanes` ;
  - `exitSignFor`, `thenFor`, `lanesVisible`, `laneStates`, `tripSteps`, `THEN_WITHIN_M`, `LANES_FROM_M`.
- Tâche 3, utilisés aux tâches 5, 6 et 8 : `vm.guidanceSheet`, `openManeuverPreview`, `openTripDetails`, `closeSheet`, `bottomPanelExpanded`, `toggleBottomPanel`, `bottomPanelFraction`, `setBottomPanelFraction`, `destinationName`, `BottomPanelGeometry.fraction`.
- Tâche 4, utilisés aux tâches 5 et 8 : `RoundaboutManeuverView(info, exitNumber, compact)`, `previewRoundabout(sweepDeg, exitNumber, street, withExit)`, `LaneGuidanceView(lanes, emphasized)`.
- Tâche 5, utilisés aux tâches 6 et 8 : `ManeuverBanner(…, onClick, roundabout, signShownBelow)`, `BANNER_TAG`, `ExitSignPanel(exitNumber, toward, branch, lanes, distanceText, isNext)`, `BannerStack(state, expanded, onOpenPreview)`, `ThenRow`, `ManeuverPreviewSheet`, `GuidanceSheets(state, vm)`.
- Tâche 6, utilisés aux tâches 6 et 8 : `NavigationBottomPanel(…)`, `PANEL_SUMMARY_TAG`, `TripDetailsSheet(state, destinationName, onDismiss)`, `TripStepRow`.
- Tâche 7 : `AttributionPlacement(top, end, vertical, side)`, `AttributionMargins.of`, `MapController.setAttribution`, `RoadlyMap(attribution)`, `LANDSCAPE_LEFT_COLUMN`, `BANNER_TOGGLE_SIZE`.
- Tâche 8 : `testNavigationViewModel`, `guidingUi`.
- Chaînes : `nav_roundabout_desc` et `nav_lanes_desc` (4) ; `nav_then`, `nav_preview_*`, `nav_close`, `nav_sign_*` (5) ; `nav_panel_*`, `nav_destination`, `nav_trip_*` (6). Chaque ancre `strings.xml` est la dernière chaîne ajoutée par la tâche précédente.

### Fichiers critiques pour l'implémentation
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\feature\navigation\src\main\java\fr\roadly\feature\navigation\NavigationScreen.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\feature\navigation\src\main\java\fr\roadly\feature\navigation\NavigationUiState.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\feature\navigation\src\main\java\fr\roadly\feature\navigation\NavigationViewModel.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\navigation\src\main\java\fr\roadly\core\navigation\RoundaboutResolver.kt` (voisin du nouveau `RoundaboutGeometry.kt`)
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\map\src\main\java\fr\roadly\core\map\MapController.kt`
- Référence de fusion (lecture seule) : `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-ai\feature\navigation\src\main\java\fr\roadly\feature\navigation\NavigationScreen.kt`
