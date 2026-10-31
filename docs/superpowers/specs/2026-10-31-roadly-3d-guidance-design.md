# Guidage 3D Roadly — design

Date : 2026-10-31. Branche : `feat/roadly-3d` (worktree `GPS-roadly-3d`, base `feat/roadly-v1` @ `027f272`).

## Objectif

Pendant le guidage, Roadly affiche une carte en perspective comme Sillaxe : inclinée, orientée selon le cap du véhicule, zoom adaptatif, bâtiments en 3D. À l'approche d'une sortie ou d'une intersection, la vue se resserre, une flèche de manœuvre est dessinée sur le tracé, un panneau de sortie (numéro + direction) apparaît dans le bandeau et les voies conseillées sont mises en avant.

Décisions prises avec l'utilisateur :
- 3D : carte inclinée **et** bâtiments extrudés (réglage « Carte 3D », activé par défaut).
- Sorties : zoom serré + flèche sur la carte + panneau de sortie avec direction.
- Paysage : même caméra 3D, voiture décalée bas-droite hors des panneaux.
- Batterie : réglage + économie automatique (économiseur Android actif → bâtiments masqués, inclinaison 45°).

Hors périmètre : relief, 3D sur l'accueil, animation 3D du marqueur, sens interdits.

## Contraintes

- Aucune fausse fonctionnalité. Les bâtiments ne sont ajoutés que si le style expose la source `openmaptiles` et la couche `building` (vrai pour `tiles.openfreemap.org/styles/dark` et `positron`).
- Ne pas restructurer les signatures de `NavigationScreen` (`assistantButton` / `assistantPanel` sur `feat/roadly-ai`) ni la délégation de `NavigationViewModel` à `GuidanceActions`.
- Textes UI en français, couleurs via `LocalRoadlyColors`, Gradle via l'outil PowerShell.
- Guidage existant (voix, recalcul, alertes, notification, tests) inchangé.

## Architecture

### 1. Caméra de guidage — `core/map`

`GuidanceCameraPolicy` (objet Kotlin pur, sans Android) :

```kotlin
data class GuidanceCameraInput(
    val speedKmh: Double?,
    val bearingDeg: Float?,
    val distanceToManeuverM: Double,
    val maneuverType: ManeuverType,
    val threeD: Boolean,        // réglage « Carte 3D »
    val powerSave: Boolean,     // économiseur Android
    val landscape: Boolean,
)
data class GuidanceCamera(val zoom: Double, val pitch: Double, val bearing: Double, val padding: CameraPadding)
/** Fractions de la taille de la vue (0..1) : gauche, haut, droite, bas. */
data class CameraPadding(val left: Float, val top: Float, val right: Float, val bottom: Float)

object GuidanceCameraPolicy {
    fun compute(input: GuidanceCameraInput, previous: GuidanceCamera?): GuidanceCamera
    fun isApproaching(distanceM: Double, type: ManeuverType, speedKmh: Double?): Boolean
}
```

Règles :
- **Pitch** : 58° ; 45° en économie d'énergie ; 0° si `threeD = false`.
- **Zoom croisière** (interpolation linéaire sur la vitesse) : ≤ 30 km/h → 17,5 ; 50 → 17,0 ; 90 → 16,3 ; ≥ 110 → 15,8. Vitesse inconnue → 17,0. En 2D (`threeD = false`) : 17,0 fixe (comportement actuel).
- **Approche** (`isApproaching`) : distance < 400 m si type ∈ {EXIT, RAMP, MERGE, KEEP_LEFT, KEEP_RIGHT} et vitesse > 70 km/h ; sinon distance < 150 m et type ∉ {DEPART, ARRIVE, CONTINUE}. En approche : zoom 18,0, pitch 50° (45° en économie, 0° en 2D).
- **Cap** : `bearingDeg` ; si vitesse < 5 km/h ou cap inconnu, cap de `previous` (sinon 0).
- **Padding** : portrait → `(0, 0.45, 0, 0)` (voiture aux 2/3 bas) ; paysage → `(0.40, 0.25, 0, 0)`. En 2D : `(0, 0, 0, 0)`.
- Le zoom varie au plus de 0,5 par appel (lissage) ; le passage en approche saute directement.

`MapController` :
- `fun applyGuidanceCamera(point: GeoPoint, camera: GuidanceCamera, durationMs: Int = 1000)` : construit `CameraPosition` (target, zoom, bearing, tilt, padding en px depuis `map.width/height`) et `easeCamera` sans interpolateur (linéaire). Commande mémorisée via `pendingCamera` comme `moveTo`.
- `moveTo` reste en tilt 0 (accueil, recherche).
- `fun setBuildings3d(enabled: Boolean, color: String)` : ajoute/retire `roadly-buildings-3d`.
- `cameraPosition()` / `restore` : `CameraSnapshot` gagne `tilt: Double = 0.0`.

### 2. Bâtiments 3D — `core/map`

Dans `installLayers`, si `style.getSource("openmaptiles") != null` : `FillExtrusionLayer(MapIds.LYR_BUILDINGS_3D, "openmaptiles")` avec `sourceLayer = "building"`, `minZoom = 15f`, `fillExtrusionHeight(get("render_height"))`, `fillExtrusionBase(get("render_min_height"))`, `fillExtrusionOpacity(0.85f)`, `fillExtrusionColor(buildingsColor)`, visibilité `NONE` par défaut. Insérée **sous** le premier calque de type `symbol` du style (`style.addLayerBelow`). `setBuildings3d` bascule la visibilité et la couleur ; état rejoué dans `replayState` (changement de style clair/sombre).

Couleur : nouveau token `buildings3d` dans `RoadlyColors` (sombre : surface légèrement éclaircie ; clair : gris chaud), passé à `setBuildings3d` depuis l'écran comme les couleurs de tracé.

### 3. Approche et flèche de manœuvre

`core/map` — `ManeuverArrowBuilder` (pur) :
```kotlin
object ManeuverArrowBuilder {
    const val BEFORE_M = 80.0; const val AFTER_M = 40.0
    /** GeoJSON : LineString de la flèche + Point de la tête (propriété `bearing`). EMPTY si géométrie insuffisante. */
    fun build(geometry: List<GeoPoint>, maneuverShapeIndex: Int): String
}
```
Marche le long de `geometry` depuis `maneuverShapeIndex` : 80 m en arrière, 40 m en avant (haversine via `GeoPoint`/`core:common`), interpole les extrémités. Tête = dernier point, `bearing` = cap du dernier segment.

Calques (`installLayers`, au-dessus de `roadly-route-traversed`, sous les marqueurs) : `roadly-arrow-casing` (liseré `routeCasing`, 14 px), `roadly-arrow` (blanc/`onBrand`, 8 px, `line-cap round`), `roadly-arrow-head` (`SymbolLayer`, image `roadly-img-arrow-head`, `iconRotate(get("bearing"))`, alignement carte). `fun setManeuverArrow(geoJson: String)` / `clearManeuverArrow()`.

`feature/navigation` — `NavigationUiState` :
```kotlin
data class ApproachUi(val type: ManeuverType, val exitNumber: String?, val exitToward: String?, val maneuverShapeIndex: Int)
val approach: ApproachUi? = null
```
Calculé dans `buildUiState` avec `GuidanceCameraPolicy.isApproaching(p.distanceToNextManeuverM, m.type, s.speedKmh)`. `BannerUi` gagne `exitToward: String?`.

`NavigationScreen` (modifications minimales) :
- `LaunchedEffect(progress, following, approach, threeD, powerSave, landscape)` : remplace `applyCameraMode(FOLLOW)` par `applyGuidanceCamera` ; `setManeuverArrow` quand `approach != null`, sinon `clearManeuverArrow()`.
- `LaunchedEffect(threeD, powerSave, c.buildings3d)` → `setBuildings3d(threeD && !powerSave, …)`.
- `LaneStrip` affiché si `state.lanes.isNotEmpty() && (bannerExpanded || state.approach != null)` (portrait et paysage).
- `landscape` est déjà connu dans `NavigationContent` (`BoxWithConstraints`) ; la caméra est appliquée dans ce bloc.

`ManeuverBanner` : quand `b.exitNumber != null`, `ExitPill` devient panneau de sortie : « SORTIE 12 » (pastille `accent`) + `exitToward` en `bodyMedium` sur la ligne du dessous si présent. `LaneStrip` : voies valides en `brand` (déjà) et, si `approach`, hauteur 36 dp au lieu de 28 dp.

### 4. Modèle et parseur — `core/model`, `core/routing`

- `Maneuver.exitToward: String? = null` (« Toulouse-Centre · Blagnac ») et `exitBranch: String? = null` (« A62 »).
- `ValhallaSign` : `exit_toward_elements`, `exit_branch_elements` ; `ValhallaRoutingProvider` : jointure des 2 premiers textes non vides par « · ».
- Fixture `valhalla_route.json` (core/routing) : ajouter `exit_toward_elements` à la manœuvre qui a déjà `exit_number_elements` ; test du parseur.

### 5. Réglage et économie d'énergie

- `UserPreferencesRepository.map3dEnabled: Flow<Boolean>` (défaut `true`), `setMap3dEnabled`. Interrupteur « Carte 3D en guidage » dans l'écran Réglages (section carte / guidage existante), sous-titre « Perspective et bâtiments pendant la navigation ».
- `NavigationViewModel` : `val map3d: StateFlow<Boolean>` exposé séparément (pas de changement du `combine` existant).
- `core/map` : `@Composable fun rememberPowerSaveMode(): State<Boolean>` (`PowerManager.isPowerSaveMode` + `BroadcastReceiver` sur `ACTION_POWER_SAVE_MODE_CHANGED`, `DisposableEffect`).

### 6. Données

Flux : `NavigationSession` → `NavigationState.Guiding` → `buildUiState` (approche) → `NavigationScreen` → `GuidanceCameraPolicy.compute` → `MapController.applyGuidanceCamera` / `setManeuverArrow`. La politique caméra tient `previous` dans un `remember` de l'écran (cap conservé à l'arrêt).

Recentrer (`vm.recenter`) remet `following = true` → la caméra 3D est réappliquée au prochain `progress`. Un geste utilisateur (`onUserGesture`) laisse la carte telle quelle (inclinaison comprise) jusqu'au recentrage, comme aujourd'hui.

## Gestion des erreurs

- Style sans source `openmaptiles` : aucun calque bâtiments, `setBuildings3d` sans effet.
- `render_height` absent : MapLibre évalue `null` → hauteur 0 ; on ajoute `coalesce(get("render_height"), 0)`.
- Géométrie trop courte pour la flèche : `EMPTY`.
- Carte non prête : commandes mémorisées et rejouées (`pendingCamera`, `replayState`).

## Tests

JVM :
- `GuidanceCameraPolicyTest` (core/map) : zoom par vitesse (bornes et interpolation), approche sortie à 390 m / 100 km/h vs 390 m / 50 km/h, rond-point à 140 m, cap conservé sous 5 km/h, économie → pitch 45 et 2D → pitch 0, padding paysage, lissage ±0,5.
- `ManeuverArrowBuilderTest` (core/map) : longueur ≈ 120 m, tête au bon endroit, `EMPTY` si 1 point.
- `NavigationViewModelTest` : `approach` non nul à 100 m d'un `TURN_RIGHT`, nul à 2 km ; `exitToward` propagé dans le bandeau.
- Parseur Valhalla : `exitToward` depuis la fixture.
- Commande : `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q`.

Appareil (HONOR Magic 8 Pro, `AREE026226000174`) : `assembleDebug`, installation, trajet simulé ou réel avec une sortie ; captures portrait / paysage (`adb exec-out screencap`) ; `logcat -d | grep FATAL`.

## Suivi

`SILLAXE_PARITY.md` : nouvelle ligne « Guidage 3D (caméra, bâtiments, sortie) » + journal.
