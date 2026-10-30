# Roadly Phase 2 — Guidage GPS en temps réel — Plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transformer la comparaison d'itinéraires de la v1 en guidage réel : suivi GPS continu à 1 Hz, projection sur le tracé, annonces vocales françaises anticipées, bandeau de manœuvre avec voies et sorties, bandeau « à venir », ETA, recalcul automatique, extrapolation en tunnel, service de premier plan avec notification, mode conduite, réglages Voix et sons / Alertes de vitesse / Préférences d'itinéraire.

**Architecture:** Un nouveau module Android `core:navigation` porte la logique pure Kotlin testée sur JVM (`RouteTracker`, `AnnouncementPolicy`, `SpeedSmoother`, `NavigationSession`) et ses adaptateurs Android derrière des interfaces (`SpeechService` → `TextToSpeech`, `NavigationServiceController` → foreground service, `SpeedLimitProvider` → Valhalla `trace_attributes`, `NavigationClock`). Un nouveau module `feature:navigation` affiche l'état (`StateFlow<NavigationState>`) de la session singleton. `core:model`, `core:routing`, `core:data`, `core:location`, `core:map`, `core:designsystem`, `feature:route`, `feature:settings` et `app` reçoivent des extensions compatibles avec le code v1.

**Tech Stack:** Kotlin 2.2.10, AGP 8.13.2, Gradle 8.14.3, JDK 21 (bytecode 17), Jetpack Compose (BOM 2026.05.00) + Material 3, Navigation Compose typée, Hilt 2.58 (KSP), DataStore Preferences 1.1.7, OkHttp 4.12 + kotlinx.serialization 1.8.1, MapLibre Android 11.13.5, play-services-location 21.3.0, APIs plateforme `TextToSpeech` / `AudioFocusRequest` / `ToneGenerator` / foreground service / `NotificationCompat` (androidx.core 1.17), JUnit 4, Turbine, kotlinx-coroutines-test, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-10-30-roadly-phase2-guidage-design.md` (lire aussi `docs/superpowers/plans/2026-10-30-roadly-v1.md` pour les conventions v1).

## Global Constraints

Spec §8, recopiée telle quelle :

- Spec v1 §Global : package `fr.roadly`, pas de clé, pas de données inventées, strings FR en ressources, fair-use (recalcul ≤ 1/10 s, `trace_attributes` ≤ 1/5 s et seulement sur instance auto-hébergée), batterie (1 Hz en guidage, arrêt du service à l'arrivée), permissions : pas de localisation en arrière-plan sans guidage actif (service premier plan `location` uniquement pendant le guidage).

Contraintes ajoutées pour ce plan :

- Package racine `fr.roadly` ; nouveaux modules `fr.roadly.core.navigation` et `fr.roadly.feature.navigation`.
- `minSdk = 29`, `targetSdk = 36`, `compileSdk = 36` ; Java/Kotlin cible 17.
- Hilt **2.58** (version du catalogue `gradle/libs.versions.toml`) ; aucune nouvelle bibliothèque tierce hors catalogue. TTS, audio focus, `ToneGenerator`, foreground service et notifications sont des API plateforme ; `androidx.core` (`NotificationCompat`, `ServiceCompat`, `ContextCompat`) est déjà au catalogue (`libs.androidx.core.ktx`) ; `BackHandler` vient de `libs.androidx.activity.compose` (déjà au catalogue).
- Textes affichés en français dans `res/values/strings.xml` du module concerné. Exception assumée : les phrases **prononcées** composées par la logique JVM (`SpokenPhrases` : « Dans 300 mètres, … », « Recalcul de l'itinéraire. ») restent en Kotlin pour être testées sur JVM, comme les libellés de variantes v1 de `RouteAlternativesUseCase`.
- Tout élément interactif agit réellement ; rien n'est inventé : la limitation de vitesse reste `null` (« — ») sur l'instance publique Valhalla FOSSGIS ; les voies n'apparaissent que si Valhalla les renvoie.
- Les `feature:*` ne dépendent que de `core:*`. `core:model` et `core:common` restent JVM purs.
- Les 83 tests existants restent verts à chaque tâche.
- Chaque tâche se termine par la commande Gradle exacte et son résultat attendu, puis un commit. Toutes les commandes se lancent depuis `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS` sous PowerShell : `.\gradlew.bat <tâches> --console=plain`. Les commandes > 2 min se lancent en arrière-plan.
- Branche `feat/roadly-v1`. Un commit par tâche, message en français, dernier paragraphe = ligne de trailer exacte : `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` (dans PowerShell : `git commit -m "<titre>" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"`).
- Dans les tests coroutines qui démarrent une `NavigationSession` : la session tourne dans `backgroundScope` avec une boucle de surveillance infinie (`delay(1 s)`) ; utiliser `runCurrent()` / `advanceTimeBy()`, **jamais** `advanceUntilIdle()` après `session.start(...)`.

## Décisions d'exécution (écarts assumés par rapport à la spec)

1. **`NavigationState` vit dans `core:navigation`**, pas dans `core:model` : `NavigationState.Failed` porte un `AppError` (`core:common`), et `core:common` dépend déjà de `core:model` (`api`) → cycle. `RouteProgress`, `Lane`, `LaneDirection`, `RoutePreferences` sont bien dans `core:model`.
2. **`AnnouncementPolicy.next` reçoit aussi `maneuvers: List<Maneuver>`** : `RouteProgress` ne porte pas le texte des manœuvres. Signature : `next(progress, maneuvers, speedKmh, already)`.
3. **Mode GPS de guidage** : `LocationMode.GUIDANCE(1000)` + `LocationRepository.setGuidanceActive(Boolean)`. `LocationMode.FOLLOW` est à 500 ms (2 Hz) alors que la spec impose 1 Hz en guidage, et `HomeViewModel` remet le mode à `OFF` 5 s après avoir quitté l'accueil (`onCompletion`) — ce qui couperait le GPS du guidage. Le drapeau de guidage est prioritaire sur le mode demandé par les écrans.
4. **`GuidanceEngine` (spec §3) est fusionné** dans `NavigationSession` (orchestration) + `AnnouncementPolicy` (décision pure). `DrivingHud` = `SpeedBadge` + badge « GPS perdu ».
5. **`NavigationRoute` est un `@Serializable object`** (l'itinéraire passe par la session, spec §3).
6. **« À venir »** = les 3 manœuvres qui **suivent** celle du bandeau principal (`maneuvers[next+1 .. next+3]`).
7. **Retour système sur l'écran de navigation = « Arrêter »** (il n'existe pas d'entrée « Reprendre le guidage » sur l'accueil en Phase 2 ; la notification ramène à l'écran tant que le guidage tourne).
8. **Test instrumenté du service remplacé par des vérifications `adb`** (Task 12) : `hilt-android-testing` et `androidx.test:rules` ne sont pas au catalogue.
9. **Doubles de test partagés dans `main/…/testing`** (précédent v1 : `FakeLocationRepository`) : `FakeUserPreferencesRepository` (`core:data`), `FakeSpeechService`, `FakeNavigationServiceController`, `TestRoutes` (`core:navigation`).
10. **Vitesse lissée** : moyenne exponentielle `v = 0,3·mesure + 0,7·v_préc` (`SpeedSmoother`), valeur d'implémentation (la spec dit seulement « lissée »).
11. **Changement de destination en guidage** : bouton « Changer de destination » → recherche → comparaison → « Démarrer » ; `NavigationSession.start` remplace la course en cours.

## Dictionnaire des types (nouveaux et modifiés)

```kotlin
// core:model — fr.roadly.core.model
enum class LaneDirection { LEFT, SLIGHT_LEFT, STRAIGHT, SLIGHT_RIGHT, RIGHT, UTURN, MERGE_LEFT, MERGE_RIGHT }
data class Lane(val directions: Set<LaneDirection>, val valid: Boolean)
data class Maneuver(type, instruction, streetName, lengthMeters, durationSeconds, beginShapeIndex, roundaboutExit,
                    val verbalPre: String? = null, val verbalAlert: String? = null, val lanes: List<Lane> = emptyList(), val exitNumber: String? = null)
data class RoutePreferences(val avoidTolls: Boolean = false, val avoidHighways: Boolean = false, val avoidFerries: Boolean = false)
data class RouteRequest(origin, destination, waypoints = emptyList(), costing, vehicle = null, departAtEpochMs = null, val preferences: RoutePreferences = RoutePreferences())
data class RouteProgress(val routeId: String, val segmentIndex: Int, val distanceAlongM: Double, val offRouteM: Double, val nextManeuverIndex: Int,
                         val distanceToNextManeuverM: Double, val remainingDistanceM: Double, val remainingDurationS: Double, val snapped: GeoPoint, val bearingDeg: Float?)

// core:routing — fr.roadly.core.routing
class RouteAlternativesUseCase { suspend operator fun invoke(origin, destination, vehicle, departAtEpochMs, preferences: RoutePreferences = RoutePreferences()): Outcome<List<RouteOption>> }
internal object ValhallaLanes { fun directions(mask: Int): Set<LaneDirection>; fun parse(elements: List<JsonElement>): List<Lane> }

// core:data — fr.roadly.core.data.repository
object GuidanceDefaults { VOICE_ENABLED = true; VOICE_VOLUME = 100; SPEED_TOLERANCE_KMH = 5; SPEED_ALERT_SOUND = false; ALLOWED_TOLERANCES_KMH = listOf(0, 5, 10) }
interface UserPreferencesRepository { /* v1 */ …
    val routePreferences: Flow<RoutePreferences>; suspend fun setRoutePreferences(value: RoutePreferences)
    val voiceEnabled: Flow<Boolean>; suspend fun setVoiceEnabled(value: Boolean)
    val voiceVolume: Flow<Int>; suspend fun setVoiceVolume(value: Int)                       // 0–100, borné
    val speedAlertToleranceKmh: Flow<Int>; suspend fun setSpeedAlertToleranceKmh(value: Int) // 0/5/10, autre valeur ignorée
    val speedAlertSound: Flow<Boolean>; suspend fun setSpeedAlertSound(value: Boolean) }
class fr.roadly.core.data.testing.FakeUserPreferencesRepository : UserPreferencesRepository

// core:location — fr.roadly.core.location
enum class LocationMode(val intervalMs: Long) { OFF(0), MAP(1000), FOLLOW(500), GUIDANCE(1000) }
interface LocationRepository { /* v1 */ …; fun setGuidanceActive(active: Boolean) }
internal fun effectiveMode(requested: LocationMode, guidanceActive: Boolean, permission: LocationPermissionState): LocationMode

// core:navigation — fr.roadly.core.navigation
object GuidanceConstants { OFF_ROUTE_SOFT_M = 40.0; OFF_ROUTE_SOFT_FIXES = 5; OFF_ROUTE_HARD_M = 100.0; ARRIVAL_RADIUS_M = 25.0; REROUTE_MIN_INTERVAL_MS = 10_000;
                           GPS_EXTRAPOLATE_AFTER_MS = 3_000; GPS_LOST_BADGE_AFTER_MS = 10_000; WATCHDOG_TICK_MS = 1_000; SPEED_LIMIT_MIN_INTERVAL_MS = 5_000;
                           SEARCH_BACK_M = 50.0; SEARCH_AHEAD_M = 500.0; RECENT_FIXES = 10; UPCOMING_COUNT = 3 }
class RouteTracker(route: Route) { val cumDistM: DoubleArray; val totalGeometryM: Double; val maneuverStartM: DoubleArray; val isOffRoute: Boolean; val hasArrived: Boolean;
                                   val lastProgress: RouteProgress?; fun initialProgress(): RouteProgress; fun update(fix: LocationFix): RouteProgress;
                                   fun extrapolate(distanceM: Double): RouteProgress; fun pointAt(distanceAlongM: Double): GeoPoint; fun segmentAt(distanceAlongM: Double): Int }
enum class Tier { FAR, NEAR, NOW }
data class Announcement(val text: String, val tier: Tier, val maneuverIndex: Int)
object AnnouncementPolicy { fun next(progress: RouteProgress, maneuvers: List<Maneuver>, speedKmh: Double?, already: Set<Pair<Int, Tier>>): Announcement? }
internal enum class SpeedClass(farM, nearM, nowM) { HIGHWAY(2000, 300, 40), ROAD(600, 150, 40), CITY(300, 80, 40) }
internal object SpokenPhrases { const val REROUTING = "Recalcul de l'itinéraire."; fun distance(meters: Double): String; fun inDistance(meters: Double, instruction: String): String }
class SpeedSmoother(alpha: Double = 0.3) { fun update(sampleKmh: Double?): Double? }
sealed interface NavigationState { Idle; Guiding(route, progress, speedKmh, speedLimitKmh, upcoming, lanes, etaEpochMs, gpsLost, rerouting); Arrived(route); Failed(error) }
fun interface NavigationClock { fun nowMs(): Long }; class SystemNavigationClock : NavigationClock
interface SpeedLimitProvider { suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> }
class NoopSpeedLimitProvider : SpeedLimitProvider; class ValhallaTraceSpeedLimitProvider : SpeedLimitProvider
interface SpeechService { fun speak(text: String, flush: Boolean = false); fun setVolume(percent: Int); fun beep(); fun stop() }
class AndroidSpeechService : SpeechService
interface NavigationServiceController { fun start() }; class ForegroundServiceController : NavigationServiceController
@Singleton class NavigationSession { val state: StateFlow<NavigationState>; val muted: StateFlow<Boolean>; fun start(option: RouteOption, preferences: RoutePreferences); fun stop(); fun retry(); fun setMuted(muted: Boolean) }
class NavigationService : Service { companion object { ACTION_STOP = "fr.roadly.action.STOP_NAVIGATION"; ACTION_OPEN_NAVIGATION = "fr.roadly.action.OPEN_NAVIGATION" } }
// testing : FakeSpeechService, FakeNavigationServiceController, TestRoutes

// core:map — fr.roadly.core.map
object TraversedFeatureBuilder { fun build(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): String }
class MapController { …; fun setTraversed(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?); fun clearTraversed() }

// core:designsystem
@Composable fun SpeedBadge(speedKmh: Double?, limitKmh: Int?, modifier: Modifier = Modifier, toleranceKmh: Int = 5)

// feature:navigation — fr.roadly.feature.navigation
@Serializable object NavigationRoute
fun NavGraphBuilder.navigationScreen(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit)
enum class NavigationPhase { LOADING, GUIDING, ARRIVED, FAILED, IDLE }
data class BannerUi(type, distanceText, instruction, street, exitNumber, roundaboutExit); data class UpcomingUi(type, distanceText, street)
data class NavigationUiState(...)
class NavigationViewModel { val uiState: StateFlow<NavigationUiState>; fun setDarkTheme(Boolean); fun toggleMute(); fun stop(); fun retry(); fun onUserGesture(); fun recenter() }

// feature:route
fun NavGraphBuilder.routeScreen(isDark: Boolean, onBack: () -> Unit, onOpenSettings: () -> Unit, onStartNavigation: (begin: () -> Boolean) -> Unit)
class RouteViewModel { …; fun startGuidance(): Boolean }

// feature:settings
data class VoiceSettings(val enabled: Boolean, val volume: Int, val toleranceKmh: Int, val overSpeedSound: Boolean)
class SettingsViewModel { …; fun setVoiceEnabled(Boolean); fun setVoiceVolume(Int); fun setSpeedTolerance(Int); fun setOverSpeedSound(Boolean);
                          fun setAvoidTolls(Boolean); fun setAvoidHighways(Boolean); fun setAvoidFerries(Boolean); fun testVoice(phrase: String) }
```

## Carte des fichiers

```
core/model        Route.kt (Lane, LaneDirection, Maneuver+, RoutePreferences, RouteRequest+), RouteProgress.kt (nouveau)
core/routing      ValhallaDtos.kt (verbal, lanes, sign), ValhallaLanes.kt (nouveau), ValhallaRoutingProvider.kt (body + mapping), RouteAlternativesUseCase.kt (préférences)
core/data         repository/UserPreferencesRepository.kt (+5 réglages, GuidanceDefaults), testing/FakeUserPreferencesRepository.kt (nouveau)
core/location     LocationRepository.kt (GUIDANCE, setGuidanceActive), FusedLocationRepository.kt (effectiveMode), testing/FakeLocationRepository.kt
core/navigation   (nouveau) GuidanceConstants, RouteTracker, AnnouncementPolicy (+Tier, Announcement, SpeedClass, SpokenPhrases), SpeedSmoother, NavigationState,
                  NavigationClock, SpeedLimitProvider (+Noop), ValhallaTraceSpeedLimitProvider, SpeechService, AndroidSpeechService,
                  NavigationServiceController (+ForegroundServiceController), NavigationSession, NavigationService, NavigationNotifications, NavigationModule,
                  testing/{FakeSpeechService, FakeNavigationServiceController, TestRoutes}, res/{values/strings.xml, drawable/ic_stat_navigation.xml}
core/map          MapLayers.kt (TraversedFeatureBuilder, ids), MapController.kt (couche « parcouru »)
core/designsystem component/SpeedBadge.kt (tolérance)
feature/navigation (nouveau) NavigationNavigation, NavigationUiState, NavigationViewModel, NavigationScreen, ManeuverBanner, LaneStrip, UpcomingStrip, EtaPanel, DrivingHud, strings
feature/route     RouteViewModel (préférences + startGuidance), RouteScreen (« Démarrer »), RouteNavigation (onStartNavigation), strings
feature/settings  SettingsViewModel (+voix, alertes, préférences), SettingsScreen (3 sections), strings
app               MainActivity (POST_NOTIFICATIONS, intent de la notification), RoadlyNavHost (NavigationRoute), AndroidManifest.xml, build.gradle.kts
settings.gradle.kts  + :core:navigation, :feature:navigation
docs              FEATURES.md, ARCHITECTURE.md, README.md, docs/ROADMAP.md
```

## Spécification numérique de `RouteTracker` (référence pour Task 5)

Notations : `p_0 … p_{n-1}` = `route.geometry` (n ≥ 2), `S = n − 1` segments, `K = route.maneuvers.size` (K ≥ 1), `R = 6 371 000 m`, `hav(a, b)` = `GeoPoint.distanceTo` (haversine).

Précalculs (constructeur) :

```
segLenM[i]      = hav(p_i, p_{i+1})                                   i ∈ [0, S)
cumDistM[0]     = 0 ; cumDistM[i+1] = cumDistM[i] + segLenM[i]        taille n : cumDistM[i] = distance le long du tracé au début du segment i
L               = cumDistM[n−1]                                       (totalGeometryM)
manStartM[k]    = cumDistM[clamp(maneuvers[k].beginShapeIndex, 0, n−1)]
manEndM[k]      = manStartM[k+1] si k < K−1, sinon L
suffixDurS[k]   = Σ_{j=k}^{K−1} maneuvers[j].durationSeconds ; suffixDurS[K] = 0
distScale       = route.distanceMeters / L si L > 0 et distanceMeters > 0, sinon 1
```

Projection d'un fix P sur le segment i (A = p_i, B = p_{i+1}), dans le plan local équirectangulaire centré sur P :

```
kLat = R·π/180 ; kLon = kLat·cos(P.lat·π/180)
a = ((A.lon − P.lon)·kLon, (A.lat − P.lat)·kLat) ; d = ((B.lon − A.lon)·kLon, (B.lat − A.lat)·kLat)
t = clamp(−(a·d)/(d·d), 0, 1)   (t = 0 si d·d = 0)
off_i   = |a + t·d|                               mètres
along_i = cumDistM[i] + t·segLenM[i]
snapped = (A.lat + t·(B.lat − A.lat), A.lon + t·(B.lon − A.lon))
```

Recherche du segment :

```
1er fix              : i* = argmin off_i sur [0, S)
fix suivants         : fenêtre glissante W = segments de « lastAlong − 50 m » à « lastAlong + 500 m » (SEARCH_BACK_M / SEARCH_AHEAD_M)
                       i* = argmin off_i sur W ; si off_{i*} > 40 m, recherche globale et on garde le meilleur des deux
égalité              : le premier indice (le plus en amont) l'emporte
```

Progression (même formule pour `update`, `initialProgress` et `extrapolate`) :

```
next  = min{ k : manStartM[k] > along } ; s'il n'existe pas : K−1
c     = max(next − 1, 0)                                         manœuvre en cours
frac  = clamp((manEndM[c] − along) / (manEndM[c] − manStartM[c]), 0, 1)   (0 si la manœuvre a une longueur géométrique nulle)
remainingDurationS     = maneuvers[c].durationSeconds · frac + suffixDurS[c+1]
distanceToNextManeuverM = max(0, manStartM[next] − along)
remainingDistanceM      = max(0, L − along) · distScale
bearingDeg              = fix.bearingDeg si fix.speedMps ≥ 1 m/s, sinon cap initial du segment i*
```

Règles :

```
hors itinéraire : off > 100 m → isOffRoute = true (immédiat) ; 40 m < off ≤ 100 m → compteur++ et isOffRoute = (compteur ≥ 5) ;
                  off ≤ 40 m → compteur = 0, isOffRoute = false
arrivée         : hasArrived devient vrai (définitivement) si next = K−1 et hav(fix, p_{n−1}) < 25 m
extrapolation   : along' = min(L, lastAlong + max(0, distanceM)) ; snapped = pointAt(along') ; off = 0 ; compteur et drapeaux inchangés
pointAt(a)      : i = segmentAt(a) = plus grand i ∈ [0, S) tel que cumDistM[i] ≤ a ; t = (a − cumDistM[i]) / segLenM[i] (0 si segLenM[i] = 0) ; interpolation linéaire lat/lon
```

Annonces (`AnnouncementPolicy`, Task 5) — classe de vitesse sur la vitesse GPS lissée `v` (km/h, `null` → ville) :

| Classe | Condition | FAR (loin) | NEAR (proche) | NOW (maintenant) |
|---|---|---|---|---|
| autoroute | v ≥ 90 | d ≤ 2000 m | d ≤ 300 m | d < 40 m |
| route | 50 ≤ v < 90 | d ≤ 600 m | d ≤ 150 m | d < 40 m |
| ville | v < 50 ou inconnue | d ≤ 300 m | d ≤ 80 m | d < 40 m |

Palier = le plus urgent satisfait (NOW, puis NEAR, puis FAR). Anti-répétition : rien si `(k, t')` déjà annoncé avec `t'.ordinal ≥ palier.ordinal`. Texte : NOW → `verbalPre ?: instruction` ; NEAR/FAR → `"Dans <distance>, " + minuscule-initiale(verbalAlert ?: verbalPre ?: instruction)`.

---

### Task 1 : `core:model` — types de guidage

**Files:**
- Modify: `core/model/src/main/java/fr/roadly/core/model/Route.kt` (fichier entier remplacé)
- Create: `core/model/src/main/java/fr/roadly/core/model/RouteProgress.kt`
- Test: `core/model/src/test/java/fr/roadly/core/model/GuidanceModelTest.kt`

**Interfaces:**
- Consumes: `GeoPoint` (v1).
- Produces: `LaneDirection`, `Lane(directions: Set<LaneDirection>, valid: Boolean)`, `Maneuver(..., verbalPre: String? = null, verbalAlert: String? = null, lanes: List<Lane> = emptyList(), exitNumber: String? = null)`, `RoutePreferences(avoidTolls = false, avoidHighways = false, avoidFerries = false)`, `RouteRequest(..., preferences: RoutePreferences = RoutePreferences())`, `RouteProgress(routeId, segmentIndex, distanceAlongM, offRouteM, nextManeuverIndex, distanceToNextManeuverM, remainingDistanceM, remainingDurationS, snapped, bearingDeg)`.

- [ ] **Step 1 : écrire le test qui échoue**

```kotlin
package fr.roadly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceModelTest {
    @Test fun `maneuver keeps the v1 constructor and defaults the guidance fields`() {
        val m = Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche.", "Rue X", 120.0, 30.0, 4, null)
        assertNull(m.verbalPre)
        assertNull(m.verbalAlert)
        assertNull(m.exitNumber)
        assertTrue(m.lanes.isEmpty())
    }

    @Test fun `route request defaults to no avoidance`() {
        val r = RouteRequest(GeoPoint(48.0, 2.0), GeoPoint(49.0, 3.0), costing = RouteCosting.FASTEST)
        assertEquals(RoutePreferences(avoidTolls = false, avoidHighways = false, avoidFerries = false), r.preferences)
    }

    @Test fun `lane equality ignores direction order but not validity`() {
        assertEquals(Lane(setOf(LaneDirection.LEFT, LaneDirection.STRAIGHT), true), Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.LEFT), true))
        assertNotEquals(Lane(setOf(LaneDirection.LEFT), true), Lane(setOf(LaneDirection.LEFT), false))
    }

    @Test fun `lane directions are ordered left to right`() {
        assertEquals(
            listOf(LaneDirection.LEFT, LaneDirection.SLIGHT_LEFT, LaneDirection.STRAIGHT, LaneDirection.SLIGHT_RIGHT, LaneDirection.RIGHT, LaneDirection.UTURN, LaneDirection.MERGE_LEFT, LaneDirection.MERGE_RIGHT),
            LaneDirection.entries.toList(),
        )
    }

    @Test fun `route progress is a value object`() {
        val a = RouteProgress("r", 3, 120.0, 2.0, 1, 80.0, 900.0, 60.0, GeoPoint(48.0, 2.0), 90f)
        assertEquals(a, a.copy())
        assertEquals(80.0, a.distanceToNextManeuverM, 0.0)
    }
}
```

- [ ] **Step 2 : vérifier l'échec**

Run: `.\gradlew.bat :core:model:test --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'verbalPre'`, `'RoutePreferences'`, `'Lane'`, `'RouteProgress'`.

- [ ] **Step 3 : remplacer `Route.kt` en entier**

```kotlin
package fr.roadly.core.model

enum class RouteCosting { FASTEST, SHORTEST, NO_TOLL }

enum class ManeuverType {
    DEPART, ARRIVE, CONTINUE, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT,
    UTURN, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT, MERGE, RAMP, EXIT, KEEP_LEFT, KEEP_RIGHT, FERRY, OTHER,
}

/** Direction indiquée sur une voie, de gauche à droite (masques Valhalla `kTurnLane*` regroupés). */
enum class LaneDirection { LEFT, SLIGHT_LEFT, STRAIGHT, SLIGHT_RIGHT, RIGHT, UTURN, MERGE_LEFT, MERGE_RIGHT }

/** Une voie à l'approche d'une manœuvre ; [valid] = la voie permet d'effectuer la manœuvre. */
data class Lane(val directions: Set<LaneDirection>, val valid: Boolean)

data class Maneuver(
    val type: ManeuverType,
    val instruction: String,
    val streetName: String?,
    val lengthMeters: Double,
    val durationSeconds: Double,
    val beginShapeIndex: Int,
    val roundaboutExit: Int?,
    /** `verbal_pre_transition_instruction` Valhalla (en français) : prononcée juste avant la manœuvre. */
    val verbalPre: String? = null,
    /** `verbal_transition_alert_instruction` Valhalla (en français) : prononcée en anticipation ; absente au départ. */
    val verbalAlert: String? = null,
    /** Voies à l'approche de la manœuvre ; vide si Valhalla n'en renvoie pas. */
    val lanes: List<Lane> = emptyList(),
    /** Numéro de sortie signalisé (`sign.exit_number_elements[0].text`), p. ex. « 12 ». */
    val exitNumber: String? = null,
)

/** Préférences d'itinéraire appliquées aux trois variantes et au recalcul (`costing_options.auto.use_* = 0`). */
data class RoutePreferences(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false,
)

data class Route(
    val id: String,
    val costing: RouteCosting,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val geometry: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val hasToll: Boolean,
    val providerId: String,
)

data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val waypoints: List<GeoPoint> = emptyList(),
    val costing: RouteCosting,
    val vehicle: VehicleProfile? = null,
    val departAtEpochMs: Long? = null,
    val preferences: RoutePreferences = RoutePreferences(),
)
```

- [ ] **Step 4 : créer `RouteProgress.kt`**

```kotlin
package fr.roadly.core.model

/**
 * Position projetée sur l'itinéraire actif, calculée par `RouteTracker` (core:navigation).
 * Distances en mètres, durées en secondes. [segmentIndex] = segment p_i → p_{i+1} de `Route.geometry`.
 */
data class RouteProgress(
    val routeId: String,
    val segmentIndex: Int,
    val distanceAlongM: Double,
    val offRouteM: Double,
    val nextManeuverIndex: Int,
    val distanceToNextManeuverM: Double,
    val remainingDistanceM: Double,
    val remainingDurationS: Double,
    val snapped: GeoPoint,
    val bearingDeg: Float?,
)
```

- [ ] **Step 5 : vérifier le succès et la non-régression**

Run: `.\gradlew.bat :core:model:test :core:routing:testDebugUnitTest :core:map:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` ; `GuidanceModelTest` 5 tests verts ; tests v1 de `core:model`, `core:routing`, `core:map` toujours verts (les paramètres ajoutés ont des valeurs par défaut).

- [ ] **Step 6 : commit**

```powershell
git add core/model
git commit -m "feat(model): voies, préférences d'itinéraire, consignes vocales et RouteProgress" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2 : `core:routing` — préférences, consignes vocales, voies, numéro de sortie

Constat sur la fixture réelle `core/routing/src/test/resources/fixtures/valhalla_route.json` (Paris, 14 manœuvres, 323 points) : chaque manœuvre porte `verbal_pre_transition_instruction`, `verbal_succinct_transition_instruction`, `verbal_post_transition_instruction` ; `verbal_transition_alert_instruction` est **absent** aux manœuvres 0 (départ) et 11 ; **aucune** manœuvre ne porte `lanes` ni `sign` ; la manœuvre 10 a `roundabout_exit_count = 2`, la 12 `= 5`. La manœuvre 0 a `verbal_pre_transition_instruction = "Conduisez vers l'est."`. Le DTO doit donc être tolérant : `lanes` absent → liste vide ; élément de voie mal typé → ignoré.

Format attendu de `lanes` (versions récentes de Valhalla, sérialiseur JSON natif) : tableau d'objets `{"directions": <masque>, "valid": <masque>?, "active": <masque>?}` avec les masques de `baldr/graphconstants.h` : `NONE=1, THROUGH=2, SHARP_LEFT=4, LEFT=8, SLIGHT_LEFT=16, SLIGHT_RIGHT=32, RIGHT=64, SHARP_RIGHT=128, REVERSE=256, MERGE_TO_LEFT=512, MERGE_TO_RIGHT=1024`. Une voie est valide si `valid` ou `active` est non nul (ou `true`).

**Files:**
- Modify: `core/routing/src/main/java/fr/roadly/core/routing/ValhallaDtos.kt` (fichier entier)
- Create: `core/routing/src/main/java/fr/roadly/core/routing/ValhallaLanes.kt`
- Modify: `core/routing/src/main/java/fr/roadly/core/routing/ValhallaRoutingProvider.kt` (corps de requête + mapping)
- Modify: `core/routing/src/main/java/fr/roadly/core/routing/RouteAlternativesUseCase.kt`
- Test: `core/routing/src/test/java/fr/roadly/core/routing/ValhallaLanesTest.kt` (nouveau), `ValhallaRoutingProviderTest.kt`, `RouteAlternativesUseCaseTest.kt`

**Interfaces:**
- Consumes: `Lane`, `LaneDirection`, `RoutePreferences`, `RouteRequest.preferences`, `Maneuver.verbalPre/verbalAlert/lanes/exitNumber` (Task 1).
- Produces: `RouteAlternativesUseCase.invoke(origin: GeoPoint, destination: GeoPoint, vehicle: VehicleProfile?, departAtEpochMs: Long, preferences: RoutePreferences = RoutePreferences()): Outcome<List<RouteOption>>` ; `ValhallaRoutingProvider` envoie `use_tolls / use_highways / use_ferry = 0` et remplit les nouveaux champs de `Maneuver`.

- [ ] **Step 1 : tests de mapping des voies (échouent)**

`core/routing/src/test/java/fr/roadly/core/routing/ValhallaLanesTest.kt` :

```kotlin
package fr.roadly.core.routing

import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaLanesTest {
    @Test fun `bitmask maps to grouped directions`() {
        assertEquals(setOf(LaneDirection.STRAIGHT), ValhallaLanes.directions(2))
        assertEquals(setOf(LaneDirection.LEFT), ValhallaLanes.directions(4))   // sharp left → LEFT
        assertEquals(setOf(LaneDirection.LEFT), ValhallaLanes.directions(8))
        assertEquals(setOf(LaneDirection.SLIGHT_LEFT), ValhallaLanes.directions(16))
        assertEquals(setOf(LaneDirection.SLIGHT_RIGHT), ValhallaLanes.directions(32))
        assertEquals(setOf(LaneDirection.RIGHT), ValhallaLanes.directions(64 or 128))
        assertEquals(setOf(LaneDirection.UTURN), ValhallaLanes.directions(256))
        assertEquals(setOf(LaneDirection.MERGE_LEFT, LaneDirection.MERGE_RIGHT), ValhallaLanes.directions(512 or 1024))
        assertEquals(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), ValhallaLanes.directions(2 or 64))
    }

    @Test fun `none and empty masks give no direction`() {
        assertTrue(ValhallaLanes.directions(0).isEmpty())
        assertTrue(ValhallaLanes.directions(1).isEmpty())
    }

    @Test fun `parse accepts int or boolean validity and skips malformed lanes`() {
        val raw = Json.parseToJsonElement("""[{"directions":8,"valid":8},{"directions":2},{"directions":66,"active":64},{"directions":2,"valid":true},{"directions":"oops"},42]""").jsonArray
        assertEquals(
            listOf(
                Lane(setOf(LaneDirection.LEFT), true),
                Lane(setOf(LaneDirection.STRAIGHT), false),
                Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), true),
                Lane(setOf(LaneDirection.STRAIGHT), true),
            ),
            ValhallaLanes.parse(raw),
        )
    }
}
```

- [ ] **Step 2 : ajouter les tests fournisseur et cas d'usage (échouent)**

Dans `ValhallaRoutingProviderTest.kt`, ajouter ces imports :

```kotlin
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.RoutePreferences
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertNull
```

puis ces tests avant l'accolade finale de la classe :

```kotlin
    private fun rawManeuvers() = json.parseToJsonElement(fixture()).jsonObject["trip"]!!.jsonObject["legs"]!!.jsonArray[0].jsonObject["maneuvers"]!!.jsonArray

    @Test fun `verbal instructions come from the fixture in french`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val r = (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Success).value.single()
        val raw = rawManeuvers()
        assertEquals(raw[0].jsonObject["verbal_pre_transition_instruction"]!!.jsonPrimitive.content, r.maneuvers[0].verbalPre)
        assertEquals("Conduisez vers l'est.", r.maneuvers[0].verbalPre)
        assertNull("pas d'alerte pour le départ dans la fixture", r.maneuvers[0].verbalAlert)
        raw.forEachIndexed { i, m ->
            assertEquals(m.jsonObject["verbal_pre_transition_instruction"]?.jsonPrimitive?.content, r.maneuvers[i].verbalPre)
            assertEquals(m.jsonObject["verbal_transition_alert_instruction"]?.jsonPrimitive?.content, r.maneuvers[i].verbalAlert)
        }
        assertEquals(2, r.maneuvers[10].roundaboutExit)
        assertTrue("la fixture FOSSGIS ne contient ni voies ni panneaux", r.maneuvers.all { it.lanes.isEmpty() && it.exitNumber == null })
    }

    @Test fun `lanes and exit number are mapped when present`() = runTest {
        val root = json.parseToJsonElement(fixture()).jsonObject
        val trip = root["trip"]!!.jsonObject
        val leg = trip["legs"]!!.jsonArray[0].jsonObject
        val maneuvers = leg["maneuvers"]!!.jsonArray
        val enriched = JsonObject(maneuvers[1].jsonObject + mapOf(
            "lanes" to json.parseToJsonElement("""[{"directions":8,"valid":8},{"directions":2},{"directions":66,"active":64}]"""),
            "sign" to json.parseToJsonElement("""{"exit_number_elements":[{"text":"12"}],"exit_toward_elements":[{"text":"Lyon"}]}"""),
        ))
        val newLeg = JsonObject(leg + ("maneuvers" to JsonArray(maneuvers.toMutableList().also { it[1] = enriched })))
        val body = JsonObject(root + ("trip" to JsonObject(trip + ("legs" to JsonArray(listOf(newLeg))))))
        server.enqueue(MockResponse().setBody(body.toString()))
        val m = (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Success).value.single().maneuvers[1]
        assertEquals(
            listOf(Lane(setOf(LaneDirection.LEFT), true), Lane(setOf(LaneDirection.STRAIGHT), false), Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), true)),
            m.lanes,
        )
        assertEquals("12", m.exitNumber)
    }

    @Test fun `route preferences map to valhalla use flags`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST, preferences = RoutePreferences(avoidTolls = true, avoidHighways = true, avoidFerries = true)))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("0", auto["use_tolls"]!!.jsonPrimitive.content)
        assertEquals("0", auto["use_highways"]!!.jsonPrimitive.content)
        assertEquals("0", auto["use_ferry"]!!.jsonPrimitive.content)
    }

    @Test fun `default preferences add no use flags`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertNull(auto["use_tolls"]); assertNull(auto["use_highways"]); assertNull(auto["use_ferry"])
    }

    @Test fun `shortest variant keeps the avoid preferences`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        provider.route(RouteRequest(origin, dest, costing = RouteCosting.SHORTEST, preferences = RoutePreferences(avoidFerries = true)))
        val auto = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["costing_options"]!!.jsonObject["auto"]!!.jsonObject
        assertEquals("true", auto["shortest"]!!.jsonPrimitive.content)
        assertEquals("0", auto["use_ferry"]!!.jsonPrimitive.content)
        assertNull(auto["use_tolls"])
    }
```

Dans `RouteAlternativesUseCaseTest.kt`, ajouter l'import `import fr.roadly.core.model.RoutePreferences` et ce test :

```kotlin
    @Test fun `preferences are sent with every variant`() = runTest {
        val seen = mutableListOf<RouteRequest>()
        val routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
                synchronized(seen) { seen += request }
                return Outcome.Success(listOf(route(request.costing, 100.0, 60.0, g1)))
            }
        }
        val prefs = RoutePreferences(avoidHighways = true)
        RouteAlternativesUseCase(routing)(o, d, null, 0L, prefs)
        assertEquals(3, seen.size)
        assertTrue(seen.all { it.preferences == prefs })
    }
```

- [ ] **Step 3 : vérifier l'échec**

Run: `.\gradlew.bat :core:routing:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'ValhallaLanes'` et `Too many arguments for 'invoke'`.

- [ ] **Step 4 : remplacer `ValhallaDtos.kt`**

```kotlin
package fr.roadly.core.routing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable internal data class ValhallaResponse(val trip: ValhallaTrip)
@Serializable internal data class ValhallaTrip(val legs: List<ValhallaLeg> = emptyList(), val summary: ValhallaSummary = ValhallaSummary())
@Serializable internal data class ValhallaSummary(val length: Double = 0.0, val time: Double = 0.0, @SerialName("has_toll") val hasToll: Boolean = false)
@Serializable internal data class ValhallaLeg(val shape: String = "", val maneuvers: List<ValhallaManeuver> = emptyList(), val summary: ValhallaSummary = ValhallaSummary())
@Serializable internal data class ValhallaManeuver(
    val type: Int = 0,
    val instruction: String = "",
    val length: Double = 0.0,
    val time: Double = 0.0,
    @SerialName("begin_shape_index") val beginShapeIndex: Int = 0,
    @SerialName("street_names") val streetNames: List<String> = emptyList(),
    @SerialName("roundabout_exit_count") val roundaboutExitCount: Int? = null,
    val toll: Boolean = false,
    @SerialName("verbal_pre_transition_instruction") val verbalPre: String? = null,
    @SerialName("verbal_transition_alert_instruction") val verbalAlert: String? = null,
    /** Brut : le format varie selon les versions de Valhalla ; décodé de façon tolérante par [ValhallaLanes]. */
    val lanes: List<JsonElement> = emptyList(),
    val sign: ValhallaSign? = null,
)
@Serializable internal data class ValhallaSign(@SerialName("exit_number_elements") val exitNumberElements: List<ValhallaSignElement> = emptyList())
@Serializable internal data class ValhallaSignElement(val text: String = "")
@Serializable internal data class ValhallaError(@SerialName("error_code") val errorCode: Int = 0, val error: String = "")
```

- [ ] **Step 5 : créer `ValhallaLanes.kt`**

```kotlin
package fr.roadly.core.routing

import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** Décodage des voies Valhalla (`maneuvers[].lanes`) ; masques de `baldr/graphconstants.h` (`kTurnLane*`). */
internal object ValhallaLanes {
    private const val THROUGH = 1 shl 1
    private const val SHARP_LEFT = 1 shl 2
    private const val LEFT = 1 shl 3
    private const val SLIGHT_LEFT = 1 shl 4
    private const val SLIGHT_RIGHT = 1 shl 5
    private const val RIGHT = 1 shl 6
    private const val SHARP_RIGHT = 1 shl 7
    private const val REVERSE = 1 shl 8
    private const val MERGE_TO_LEFT = 1 shl 9
    private const val MERGE_TO_RIGHT = 1 shl 10

    /** `NONE` (1) et 0 ne donnent aucune direction : la voie est dessinée « tout droit » par l'écran. */
    fun directions(mask: Int): Set<LaneDirection> = buildSet {
        if (mask and (SHARP_LEFT or LEFT) != 0) add(LaneDirection.LEFT)
        if (mask and SLIGHT_LEFT != 0) add(LaneDirection.SLIGHT_LEFT)
        if (mask and THROUGH != 0) add(LaneDirection.STRAIGHT)
        if (mask and SLIGHT_RIGHT != 0) add(LaneDirection.SLIGHT_RIGHT)
        if (mask and (RIGHT or SHARP_RIGHT) != 0) add(LaneDirection.RIGHT)
        if (mask and REVERSE != 0) add(LaneDirection.UTURN)
        if (mask and MERGE_TO_LEFT != 0) add(LaneDirection.MERGE_LEFT)
        if (mask and MERGE_TO_RIGHT != 0) add(LaneDirection.MERGE_RIGHT)
    }

    /** Éléments mal formés ignorés ; jamais d'exception. */
    fun parse(elements: List<JsonElement>): List<Lane> = elements.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val mask = (o["directions"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        Lane(directions(mask), valid = o["valid"].isSet() || o["active"].isSet())
    }

    private fun JsonElement?.isSet(): Boolean {
        val p = this as? JsonPrimitive ?: return false
        return p.booleanOrNull ?: p.intOrNull?.let { it != 0 } ?: false
    }
}
```

- [ ] **Step 6 : `ValhallaRoutingProvider.kt` — corps de requête**

Remplacer :

```kotlin
            putJsonObject("costing_options") {
                putJsonObject("auto") {
                    when (request.costing) {
                        RouteCosting.FASTEST -> Unit
                        RouteCosting.SHORTEST -> put("shortest", true)
                        RouteCosting.NO_TOLL -> put("use_tolls", 0)
                    }
                }
            }
```

par :

```kotlin
            putJsonObject("costing_options") {
                putJsonObject("auto") {
                    val prefs = request.preferences
                    if (request.costing == RouteCosting.SHORTEST) put("shortest", true)
                    // 0 = éviter autant que possible (sémantique Valhalla) ; appliqué aux 3 variantes et au recalcul.
                    if (request.costing == RouteCosting.NO_TOLL || prefs.avoidTolls) put("use_tolls", 0)
                    if (prefs.avoidHighways) put("use_highways", 0)
                    if (prefs.avoidFerries) put("use_ferry", 0)
                }
            }
```

- [ ] **Step 7 : `ValhallaRoutingProvider.kt` — mapping des manœuvres**

Remplacer :

```kotlin
                    roundaboutExit = m.roundaboutExitCount,
                )
```

par :

```kotlin
                    roundaboutExit = m.roundaboutExitCount,
                    verbalPre = m.verbalPre?.takeIf { it.isNotBlank() },
                    verbalAlert = m.verbalAlert?.takeIf { it.isNotBlank() },
                    lanes = ValhallaLanes.parse(m.lanes),
                    exitNumber = m.sign?.exitNumberElements?.firstOrNull { it.text.isNotBlank() }?.text,
                )
```

- [ ] **Step 8 : `RouteAlternativesUseCase.kt`**

Ajouter l'import `import fr.roadly.core.model.RoutePreferences`, puis remplacer :

```kotlin
    suspend operator fun invoke(origin: GeoPoint, destination: GeoPoint, vehicle: VehicleProfile?, departAtEpochMs: Long): Outcome<List<RouteOption>> {
        val results = coroutineScope {
            order.map { c -> async { c to safely { routing.route(RouteRequest(origin, destination, costing = c, vehicle = vehicle, departAtEpochMs = departAtEpochMs)) } } }.map { it.await() }
        }
```

par :

```kotlin
    suspend operator fun invoke(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicle: VehicleProfile?,
        departAtEpochMs: Long,
        preferences: RoutePreferences = RoutePreferences(),
    ): Outcome<List<RouteOption>> {
        val results = coroutineScope {
            order.map { c ->
                async { c to safely { routing.route(RouteRequest(origin, destination, costing = c, vehicle = vehicle, departAtEpochMs = departAtEpochMs, preferences = preferences)) } }
            }.map { it.await() }
        }
```

- [ ] **Step 9 : vérifier le succès**

Run: `.\gradlew.bat :core:routing:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` ; `ValhallaLanesTest` (3), 5 nouveaux tests dans `ValhallaRoutingProviderTest`, 1 nouveau dans `RouteAlternativesUseCaseTest` ; tous les tests v1 du module verts.

- [ ] **Step 10 : commit**

```powershell
git add core/routing
git commit -m "feat(routing): préférences d'itinéraire, consignes vocales Valhalla, voies et numéros de sortie" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 3 : `core:data` — réglages de guidage (DataStore)

**Files:**
- Modify: `core/data/src/main/java/fr/roadly/core/data/repository/UserPreferencesRepository.kt` (fichier entier)
- Create: `core/data/src/main/java/fr/roadly/core/data/testing/FakeUserPreferencesRepository.kt`
- Modify: `feature/settings/src/test/java/fr/roadly/feature/settings/SettingsViewModelTest.kt` (le faux anonyme est remplacé par le faux partagé)
- Test: `core/data/src/test/java/fr/roadly/core/data/repository/DataStoreUserPreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: `RoutePreferences` (Task 1).
- Produces: `GuidanceDefaults`, les 5 paires flux/setter de `UserPreferencesRepository` (voir Dictionnaire), `fr.roadly.core.data.testing.FakeUserPreferencesRepository` (flux publics `theme`, `rationaleSeen`, `deniedForever`, `route`, `voice`, `volume`, `tolerance`, `sound` de type `MutableStateFlow`).

- [ ] **Step 1 : test DataStore (échoue)**

```kotlin
package fr.roadly.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreUserPreferencesRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    // DataStore réel sur fichier temporaire, dispatcher IO réel (pas de temps virtuel pour les écritures disque).
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo by lazy { DataStoreUserPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "prefs.preferences_pb") }) }

    @After fun tearDown() = scope.cancel()

    @Test fun `guidance defaults`() = runTest {
        assertEquals(RoutePreferences(), repo.routePreferences.first())
        assertEquals(GuidanceDefaults.VOICE_ENABLED, repo.voiceEnabled.first())
        assertEquals(GuidanceDefaults.VOICE_VOLUME, repo.voiceVolume.first())
        assertEquals(GuidanceDefaults.SPEED_TOLERANCE_KMH, repo.speedAlertToleranceKmh.first())
        assertEquals(GuidanceDefaults.SPEED_ALERT_SOUND, repo.speedAlertSound.first())
    }

    @Test fun `route preferences round trip`() = runTest {
        repo.setRoutePreferences(RoutePreferences(avoidTolls = true, avoidHighways = false, avoidFerries = true))
        assertEquals(RoutePreferences(avoidTolls = true, avoidHighways = false, avoidFerries = true), repo.routePreferences.first())
    }

    @Test fun `voice volume is clamped to 0-100`() = runTest {
        repo.setVoiceVolume(140); assertEquals(100, repo.voiceVolume.first())
        repo.setVoiceVolume(-3); assertEquals(0, repo.voiceVolume.first())
        repo.setVoiceVolume(42); assertEquals(42, repo.voiceVolume.first())
    }

    @Test fun `tolerance accepts only 0 5 10`() = runTest {
        repo.setSpeedAlertToleranceKmh(10); assertEquals(10, repo.speedAlertToleranceKmh.first())
        repo.setSpeedAlertToleranceKmh(7); assertEquals(10, repo.speedAlertToleranceKmh.first())
        repo.setSpeedAlertToleranceKmh(0); assertEquals(0, repo.speedAlertToleranceKmh.first())
    }

    @Test fun `voice switch and over speed sound persist`() = runTest {
        repo.setVoiceEnabled(false); repo.setSpeedAlertSound(true)
        assertEquals(false, repo.voiceEnabled.first())
        assertEquals(true, repo.speedAlertSound.first())
    }
}
```

- [ ] **Step 2 : vérifier l'échec**

Run: `.\gradlew.bat :core:data:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'routePreferences'`, `'GuidanceDefaults'`.

- [ ] **Step 3 : remplacer `UserPreferencesRepository.kt`**

```kotlin
package fr.roadly.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK, AUTO_SOLAR }

/** Valeurs par défaut et bornes des réglages de guidage (spec Phase 2 §2 et §6). */
object GuidanceDefaults {
    const val VOICE_ENABLED = true
    const val VOICE_VOLUME = 100
    const val SPEED_TOLERANCE_KMH = 5
    /** L'alerte sonore de dépassement est une option : désactivée par défaut. */
    const val SPEED_ALERT_SOUND = false
    val ALLOWED_TOLERANCES_KMH = listOf(0, 5, 10)
}

interface UserPreferencesRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
    val locationRationaleSeen: Flow<Boolean>
    suspend fun setLocationRationaleSeen()
    val locationDeniedForever: Flow<Boolean>
    suspend fun setLocationDeniedForever(value: Boolean)

    val routePreferences: Flow<RoutePreferences>
    suspend fun setRoutePreferences(value: RoutePreferences)
    val voiceEnabled: Flow<Boolean>
    suspend fun setVoiceEnabled(value: Boolean)
    /** Volume des annonces 0–100 (appliqué via `TextToSpeech.Engine.KEY_PARAM_VOLUME`). */
    val voiceVolume: Flow<Int>
    /** Borné à 0–100. */
    suspend fun setVoiceVolume(value: Int)
    val speedAlertToleranceKmh: Flow<Int>
    /** Seules 0, 5 et 10 km/h sont acceptées ; toute autre valeur est ignorée. */
    suspend fun setSpeedAlertToleranceKmh(value: Int)
    val speedAlertSound: Flow<Boolean>
    suspend fun setSpeedAlertSound(value: Boolean)
}

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(private val store: DataStore<Preferences>) : UserPreferencesRepository {
    private val kTheme = stringPreferencesKey("theme.mode")
    private val kRationale = booleanPreferencesKey("location.rationale.seen")
    private val kDeniedForever = booleanPreferencesKey("location.denied.forever")
    private val kAvoidTolls = booleanPreferencesKey("route.avoid.tolls")
    private val kAvoidHighways = booleanPreferencesKey("route.avoid.highways")
    private val kAvoidFerries = booleanPreferencesKey("route.avoid.ferries")
    private val kVoiceEnabled = booleanPreferencesKey("voice.enabled")
    private val kVoiceVolume = intPreferencesKey("voice.volume")
    private val kTolerance = intPreferencesKey("speed.alert.tolerance")
    private val kSpeedSound = booleanPreferencesKey("speed.alert.sound")

    override val themeMode: Flow<ThemeMode> = store.data.map { p -> p[kTheme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AUTO_SOLAR }
    override suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[kTheme] = mode.name } }
    override val locationRationaleSeen: Flow<Boolean> = store.data.map { it[kRationale] ?: false }
    override suspend fun setLocationRationaleSeen() { store.edit { it[kRationale] = true } }
    override val locationDeniedForever: Flow<Boolean> = store.data.map { it[kDeniedForever] ?: false }
    override suspend fun setLocationDeniedForever(value: Boolean) { store.edit { it[kDeniedForever] = value } }

    override val routePreferences: Flow<RoutePreferences> = store.data.map { p ->
        RoutePreferences(avoidTolls = p[kAvoidTolls] ?: false, avoidHighways = p[kAvoidHighways] ?: false, avoidFerries = p[kAvoidFerries] ?: false)
    }
    override suspend fun setRoutePreferences(value: RoutePreferences) {
        store.edit { it[kAvoidTolls] = value.avoidTolls; it[kAvoidHighways] = value.avoidHighways; it[kAvoidFerries] = value.avoidFerries }
    }
    override val voiceEnabled: Flow<Boolean> = store.data.map { it[kVoiceEnabled] ?: GuidanceDefaults.VOICE_ENABLED }
    override suspend fun setVoiceEnabled(value: Boolean) { store.edit { it[kVoiceEnabled] = value } }
    override val voiceVolume: Flow<Int> = store.data.map { (it[kVoiceVolume] ?: GuidanceDefaults.VOICE_VOLUME).coerceIn(0, 100) }
    override suspend fun setVoiceVolume(value: Int) { store.edit { it[kVoiceVolume] = value.coerceIn(0, 100) } }
    override val speedAlertToleranceKmh: Flow<Int> = store.data.map { p ->
        p[kTolerance]?.takeIf { it in GuidanceDefaults.ALLOWED_TOLERANCES_KMH } ?: GuidanceDefaults.SPEED_TOLERANCE_KMH
    }
    override suspend fun setSpeedAlertToleranceKmh(value: Int) {
        if (value !in GuidanceDefaults.ALLOWED_TOLERANCES_KMH) return
        store.edit { it[kTolerance] = value }
    }
    override val speedAlertSound: Flow<Boolean> = store.data.map { it[kSpeedSound] ?: GuidanceDefaults.SPEED_ALERT_SOUND }
    override suspend fun setSpeedAlertSound(value: Boolean) { store.edit { it[kSpeedSound] = value } }
}
```

- [ ] **Step 4 : créer le faux partagé `core/data/src/main/java/fr/roadly/core/data/testing/FakeUserPreferencesRepository.kt`**

```kotlin
package fr.roadly.core.data.testing

import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Double de test en mémoire, mêmes règles de bornage que l'implémentation DataStore. */
class FakeUserPreferencesRepository : UserPreferencesRepository {
    val theme = MutableStateFlow(ThemeMode.AUTO_SOLAR)
    val rationaleSeen = MutableStateFlow(false)
    val deniedForever = MutableStateFlow(false)
    val route = MutableStateFlow(RoutePreferences())
    val voice = MutableStateFlow(GuidanceDefaults.VOICE_ENABLED)
    val volume = MutableStateFlow(GuidanceDefaults.VOICE_VOLUME)
    val tolerance = MutableStateFlow(GuidanceDefaults.SPEED_TOLERANCE_KMH)
    val sound = MutableStateFlow(GuidanceDefaults.SPEED_ALERT_SOUND)

    override val themeMode: Flow<ThemeMode> = theme
    override suspend fun setThemeMode(mode: ThemeMode) { theme.value = mode }
    override val locationRationaleSeen: Flow<Boolean> = rationaleSeen
    override suspend fun setLocationRationaleSeen() { rationaleSeen.value = true }
    override val locationDeniedForever: Flow<Boolean> = deniedForever
    override suspend fun setLocationDeniedForever(value: Boolean) { deniedForever.value = value }
    override val routePreferences: Flow<RoutePreferences> = route
    override suspend fun setRoutePreferences(value: RoutePreferences) { route.value = value }
    override val voiceEnabled: Flow<Boolean> = voice
    override suspend fun setVoiceEnabled(value: Boolean) { voice.value = value }
    override val voiceVolume: Flow<Int> = volume
    override suspend fun setVoiceVolume(value: Int) { volume.value = value.coerceIn(0, 100) }
    override val speedAlertToleranceKmh: Flow<Int> = tolerance
    override suspend fun setSpeedAlertToleranceKmh(value: Int) { if (value in GuidanceDefaults.ALLOWED_TOLERANCES_KMH) tolerance.value = value }
    override val speedAlertSound: Flow<Boolean> = sound
    override suspend fun setSpeedAlertSound(value: Boolean) { sound.value = value }
}
```

- [ ] **Step 5 : `SettingsViewModelTest.kt` utilise le faux partagé**

Ajouter l'import `import fr.roadly.core.data.testing.FakeUserPreferencesRepository`, supprimer l'import devenu inutile `import fr.roadly.core.data.repository.UserPreferencesRepository`, et remplacer :

```kotlin
    private val prefs = object : UserPreferencesRepository {
        val t = MutableStateFlow(ThemeMode.AUTO_SOLAR)
        override val themeMode = t
        override suspend fun setThemeMode(mode: ThemeMode) { t.value = mode }
        override val locationRationaleSeen = flowOf(false)
        override suspend fun setLocationRationaleSeen() = Unit
        override val locationDeniedForever = flowOf(false)
        override suspend fun setLocationDeniedForever(value: Boolean) = Unit
    }
```

par :

```kotlin
    private val prefs = FakeUserPreferencesRepository()
```

- [ ] **Step 6 : vérifier le succès**

Run: `.\gradlew.bat :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` ; 5 nouveaux tests DataStore verts ; `ApiClientTest` et les 4 tests de `SettingsViewModelTest` toujours verts.

- [ ] **Step 7 : commit**

```powershell
git add core/data feature/settings/src/test
git commit -m "feat(data): réglages voix, volume, alertes de vitesse et préférences d'itinéraire (DataStore)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4 : `core:location` — GPS maintenu à 1 Hz pendant le guidage

Problème v1 : `HomeViewModel` appelle `location.setMode(LocationMode.OFF)` dans `onCompletion`, 5 s après que l'accueil n'est plus observé ; le guidage perdrait ses fixes. Solution : un drapeau de guidage prioritaire (`setGuidanceActive`) qui impose `GUIDANCE` (1 Hz, spec §8) tant qu'il est levé.

**Files:**
- Modify: `core/location/src/main/java/fr/roadly/core/location/LocationRepository.kt`
- Modify: `core/location/src/main/java/fr/roadly/core/location/FusedLocationRepository.kt`
- Modify: `core/location/src/main/java/fr/roadly/core/location/testing/FakeLocationRepository.kt`
- Test: `core/location/src/test/java/fr/roadly/core/location/LocationModeTest.kt`

**Interfaces:**
- Produces: `LocationMode.GUIDANCE(1000)`, `LocationRepository.setGuidanceActive(active: Boolean)`, `internal fun effectiveMode(requested: LocationMode, guidanceActive: Boolean, permission: LocationPermissionState): LocationMode`, `FakeLocationRepository.guidanceActive: Boolean` (lecture seule).

- [ ] **Step 1 : tests (échouent)** — ajouter dans `LocationModeTest` :

```kotlin
    @Test fun `guidance mode is 1 Hz`() = assertEquals(1000L, LocationMode.GUIDANCE.intervalMs)

    @Test fun `guidance overrides the screen mode, permission overrides everything`() {
        assertEquals(LocationMode.GUIDANCE, effectiveMode(LocationMode.OFF, guidanceActive = true, permission = LocationPermissionState.GRANTED))
        assertEquals(LocationMode.GUIDANCE, effectiveMode(LocationMode.FOLLOW, guidanceActive = true, permission = LocationPermissionState.GRANTED))
        assertEquals(LocationMode.MAP, effectiveMode(LocationMode.MAP, guidanceActive = false, permission = LocationPermissionState.GRANTED))
        assertEquals(LocationMode.OFF, effectiveMode(LocationMode.MAP, guidanceActive = true, permission = LocationPermissionState.DENIED))
    }

    @Test fun `fake records the guidance flag`() {
        val fake = fr.roadly.core.location.testing.FakeLocationRepository()
        fake.setGuidanceActive(true)
        assertEquals(true, fake.guidanceActive)
        fake.setGuidanceActive(false)
        assertEquals(false, fake.guidanceActive)
    }
```

- [ ] **Step 2 : vérifier l'échec**

Run: `.\gradlew.bat :core:location:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'GUIDANCE'`, `'effectiveMode'`, `'setGuidanceActive'`.

- [ ] **Step 3 : `LocationRepository.kt`**

Remplacer :

```kotlin
enum class LocationMode(val intervalMs: Long) { OFF(0), MAP(1000), FOLLOW(500) }
```

par :

```kotlin
/** GUIDANCE : 1 Hz pendant le guidage (spec Phase 2 §8), prioritaire sur le mode demandé par les écrans. */
enum class LocationMode(val intervalMs: Long) { OFF(0), MAP(1000), FOLLOW(500), GUIDANCE(1000) }
```

et ajouter dans l'interface, après `fun setMode(mode: LocationMode)` :

```kotlin
    /** Guidage actif : impose [LocationMode.GUIDANCE] tant que la permission est accordée, quel que soit [setMode]. */
    fun setGuidanceActive(active: Boolean)
```

- [ ] **Step 4 : `FusedLocationRepository.kt`**

Ajouter le champ après `private val mode = MutableStateFlow(LocationMode.OFF)` :

```kotlin
    private val guidance = MutableStateFlow(false)
```

Remplacer :

```kotlin
    override val fixes: Flow<LocationFix> = combine(mode, _permission) { m, p -> if (p == LocationPermissionState.GRANTED) m else LocationMode.OFF }
```

par :

```kotlin
    override val fixes: Flow<LocationFix> = combine(mode, guidance, _permission) { m, g, p -> effectiveMode(m, g, p) }
```

Ajouter après `override fun setMode(...)` :

```kotlin
    override fun setGuidanceActive(active: Boolean) { guidance.value = active }
```

Ajouter en fin de fichier (à côté de `nextPermissionState`) :

```kotlin
/** Mode réellement demandé au GPS : rien sans permission ; le guidage l'emporte sur l'écran. */
internal fun effectiveMode(requested: LocationMode, guidanceActive: Boolean, permission: LocationPermissionState): LocationMode = when {
    permission != LocationPermissionState.GRANTED -> LocationMode.OFF
    guidanceActive -> LocationMode.GUIDANCE
    else -> requested
}
```

(`flatMapLatest` existant appelle déjà `updates(m)` avec `m.intervalMs` : rien d'autre à changer.)

- [ ] **Step 5 : `FakeLocationRepository.kt`**

Ajouter après la propriété `mode` :

```kotlin
    var guidanceActive: Boolean = false
        private set
    override fun setGuidanceActive(active: Boolean) { guidanceActive = active }
```

- [ ] **Step 6 : vérifier le succès et la non-régression des écrans**

Run: `.\gradlew.bat :core:location:testDebugUnitTest :feature:home:testDebugUnitTest :feature:route:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` ; 3 nouveaux tests verts ; `HomeViewModelTest` et `RouteViewModelTest` inchangés et verts.

- [ ] **Step 7 : commit**

```powershell
git add core/location
git commit -m "feat(location): mode GUIDANCE 1 Hz prioritaire pendant le guidage" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 5 : `core:navigation` (1/3) — module, `RouteTracker`, `AnnouncementPolicy`, `SpeedSmoother`, `NavigationState`

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/navigation/build.gradle.kts`, `core/navigation/src/main/AndroidManifest.xml`
- Create: `core/navigation/src/main/java/fr/roadly/core/navigation/{GuidanceConstants.kt, RouteTracker.kt, AnnouncementPolicy.kt, SpeedSmoother.kt, NavigationState.kt}`
- Create: `core/navigation/src/main/java/fr/roadly/core/navigation/testing/TestRoutes.kt`
- Create: `core/navigation/src/test/resources/fixtures/valhalla_route.json` (copie de la fixture de `core:routing`)
- Test: `core/navigation/src/test/java/fr/roadly/core/navigation/{ParisRoute.kt, RouteTrackerTest.kt, RouteTrackerOffRouteTest.kt, AnnouncementPolicyTest.kt, SpokenPhrasesTest.kt, SpeedSmootherTest.kt}`

**Interfaces:**
- Consumes: `Route`, `Maneuver` (+ `verbalPre`, `verbalAlert`, `lanes`), `RouteProgress`, `Lane`, `LocationFix`, `GeoPoint` (Task 1) ; `AppError` (`core:common`) ; `Polyline6` (`core:routing`, tests).
- Produces: `GuidanceConstants`, `RouteTracker`, `bearing(a, b)` (internal), `Tier`, `Announcement`, `AnnouncementPolicy.next(progress, maneuvers, speedKmh, already)`, `SpeedClass` (internal), `SpokenPhrases` (internal), `SpeedSmoother`, `NavigationState`, `fr.roadly.core.navigation.testing.TestRoutes { straight(id, costing): Route; north(p, meters): GeoPoint; METERS_PER_DEG_LAT }`.

- [ ] **Step 1 : déclarer le module**

Dans `settings.gradle.kts`, remplacer la ligne :

```kotlin
    ":core:location", ":core:map", ":core:geocoding", ":core:routing",
```

par :

```kotlin
    ":core:location", ":core:map", ":core:geocoding", ":core:routing", ":core:navigation",
```

`core/navigation/build.gradle.kts` :

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "fr.roadly.core.navigation"
    compileSdk = 36
    defaultConfig { minSdk = 29; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:data"))
    api(project(":core:location"))
    api(project(":core:routing"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}
```

`core/navigation/src/main/AndroidManifest.xml` :

```xml
<manifest/>
```

Copier la fixture :

```powershell
New-Item -ItemType Directory -Force "core\navigation\src\test\resources\fixtures" | Out-Null
Copy-Item "core\routing\src\test\resources\fixtures\valhalla_route.json" "core\navigation\src\test\resources\fixtures\valhalla_route.json"
```

- [ ] **Step 2 : constantes (référencées par les tests)**

`GuidanceConstants.kt` :

```kotlin
package fr.roadly.core.navigation

/** Seuils du guidage (spec Phase 2 §2 et §8). */
object GuidanceConstants {
    /** Hors itinéraire si > 40 m pendant 5 fixes consécutifs… */
    const val OFF_ROUTE_SOFT_M = 40.0
    const val OFF_ROUTE_SOFT_FIXES = 5
    /** … ou > 100 m une seule fois. */
    const val OFF_ROUTE_HARD_M = 100.0
    /** Arrivée : < 25 m de la destination et dernière manœuvre. */
    const val ARRIVAL_RADIUS_M = 25.0
    /** Fair-use : au plus un recalcul toutes les 10 s. */
    const val REROUTE_MIN_INTERVAL_MS = 10_000L
    /** Sans fix depuis > 3 s : extrapolation le long du tracé. */
    const val GPS_EXTRAPOLATE_AFTER_MS = 3_000L
    /** Badge « GPS perdu » après 10 s sans fix. */
    const val GPS_LOST_BADGE_AFTER_MS = 10_000L
    const val WATCHDOG_TICK_MS = 1_000L
    /** Fair-use : `trace_attributes` au plus toutes les 5 s. */
    const val SPEED_LIMIT_MIN_INTERVAL_MS = 5_000L
    /** Fenêtre de recherche du segment autour de la dernière position projetée. */
    const val SEARCH_BACK_M = 50.0
    const val SEARCH_AHEAD_M = 500.0
    /** Fixes conservés pour `trace_attributes`. */
    const val RECENT_FIXES = 10
    /** Manœuvres du bandeau « à venir ». */
    const val UPCOMING_COUNT = 3
}
```

`testing/TestRoutes.kt` (dans `src/main`, réutilisé par les tests de `feature:navigation` et `feature:route`) :

```kotlin
package fr.roadly.core.navigation.testing

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import kotlin.math.PI

/** Itinéraires synthétiques déterministes pour les tests. */
object TestRoutes {
    const val LAT = 48.85
    const val METERS_PER_DEG_LAT = 6_371_000.0 * PI / 180.0

    /** 21 points plein est (pas de 0,001° ≈ 73,2 m, ≈ 1 464 m au total) : départ (point 0), virage à droite (point 10), arrivée (point 20). */
    fun straight(id: String = "straight", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = (0..20).map { GeoPoint(LAT, 2.300 + it * 0.001) }
        val total = (0 until 20).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val half = (0 until 10).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, half, 60.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(
                ManeuverType.TURN_RIGHT, "Tournez à droite dans Rue d'Essai.", "Rue d'Essai", total - half, 60.0, 10, null,
                verbalPre = "Tournez à droite dans Rue d'Essai.", verbalAlert = "Tournez à droite dans Rue d'Essai.",
            ),
            Maneuver(
                ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 20, null,
                verbalPre = "Votre destination est sur la droite.", verbalAlert = "Votre destination sera sur la droite.",
            ),
        )
        return Route(id, costing, total, 120.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    /** [p] décalé de [meters] vers le nord (perpendiculaire à [straight]). */
    fun north(p: GeoPoint, meters: Double) = GeoPoint(p.lat + meters / METERS_PER_DEG_LAT, p.lon)
}
```

- [ ] **Step 3 : écrire les tests qui échouent**

`ParisRoute.kt` (test) — construit la `Route` depuis la fixture réelle, indépendamment du parseur `internal` de `core:routing` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.routing.Polyline6
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Itinéraire réel Paris (Hôtel de Ville → Étoile) issu de la fixture Valhalla FOSSGIS. */
internal object ParisRoute {
    private val trip: JsonObject by lazy {
        val text = ParisRoute::class.java.classLoader!!.getResource("fixtures/valhalla_route.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject["trip"]!!.jsonObject
    }
    private val leg: JsonObject get() = trip["legs"]!!.jsonArray[0].jsonObject
    val rawManeuvers: List<JsonObject> get() = leg["maneuvers"]!!.jsonArray.map { it.jsonObject }

    fun route(): Route {
        val maneuvers = rawManeuvers.map { m ->
            fun str(key: String): String? = (m[key] as? JsonPrimitive)?.contentOrNull
            Maneuver(
                type = type(m["type"]!!.jsonPrimitive.int),
                instruction = str("instruction").orEmpty(),
                streetName = (m["street_names"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content,
                lengthMeters = m["length"]!!.jsonPrimitive.double * 1000.0,
                durationSeconds = m["time"]!!.jsonPrimitive.double,
                beginShapeIndex = m["begin_shape_index"]!!.jsonPrimitive.int,
                roundaboutExit = (m["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull,
                verbalPre = str("verbal_pre_transition_instruction"),
                verbalAlert = str("verbal_transition_alert_instruction"),
            )
        }
        val summary = trip["summary"]!!.jsonObject
        return Route(
            id = "paris", costing = RouteCosting.FASTEST,
            distanceMeters = summary["length"]!!.jsonPrimitive.double * 1000.0,
            durationSeconds = summary["time"]!!.jsonPrimitive.double,
            geometry = Polyline6.decode(leg["shape"]!!.jsonPrimitive.content),
            maneuvers = maneuvers, hasToll = false, providerId = "valhalla",
        )
    }

    private fun type(t: Int): ManeuverType = when (t) {
        1, 2, 3 -> ManeuverType.DEPART
        4, 5, 6 -> ManeuverType.ARRIVE
        9 -> ManeuverType.SLIGHT_RIGHT
        10 -> ManeuverType.TURN_RIGHT
        15 -> ManeuverType.TURN_LEFT
        26 -> ManeuverType.ROUNDABOUT_ENTER
        27 -> ManeuverType.ROUNDABOUT_EXIT
        else -> ManeuverType.CONTINUE
    }
}
```

`RouteTrackerTest.kt` — toutes les valeurs attendues sont recalculées depuis la fixture :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {
    private val route = ParisRoute.route()
    private val pts = route.geometry
    /** Distances cumulées recalculées indépendamment (haversine de GeoPoint). */
    private val cum = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + pts[i - 1].distanceTo(pts[i]) }
    private fun fixAt(p: GeoPoint, speedMps: Float? = null, bearing: Float? = null) = LocationFix(p, bearing, speedMps, 5f, 0L)
    private fun mid(a: GeoPoint, b: GeoPoint) = GeoPoint((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)

    @Test fun `fixture has a departure, an arrival and a usable geometry`() {
        assertTrue(pts.size > 100)
        assertEquals(fr.roadly.core.model.ManeuverType.DEPART, route.maneuvers.first().type)
        assertEquals(fr.roadly.core.model.ManeuverType.ARRIVE, route.maneuvers.last().type)
        assertEquals(pts.lastIndex, route.maneuvers.last().beginShapeIndex)
    }

    @Test fun `cumulative distances match the fixture geometry`() {
        val t = RouteTracker(route)
        assertEquals(pts.size, t.cumDistM.size)
        assertEquals(cum.last(), t.totalGeometryM, 1e-6)
        // Valhalla annonce la longueur du trajet ; la somme haversine du tracé doit être cohérente (2 %).
        assertEquals(route.distanceMeters, t.totalGeometryM, route.distanceMeters * 0.02)
        route.maneuvers.forEachIndexed { k, m -> assertEquals(cum[m.beginShapeIndex], t.maneuverStartM[k], 1e-6) }
    }

    @Test fun `vertices project onto themselves with the exact along distance`() {
        val t = RouteTracker(route)
        listOf(10, 50, 100, 150, 200, 250, 300).forEach { k ->
            val p = t.update(fixAt(pts[k]))
            assertEquals("off au point $k", 0.0, p.offRouteM, 0.01)
            assertEquals("along au point $k", cum[k], p.distanceAlongM, 0.01)
        }
    }

    @Test fun `segment midpoints project at half the segment`() {
        val t = RouteTracker(route)
        listOf(20, 80, 160, 240, 310).forEach { k ->
            val p = t.update(fixAt(mid(pts[k], pts[k + 1])))
            assertEquals("along milieu $k", cum[k] + (cum[k + 1] - cum[k]) / 2, p.distanceAlongM, 0.05)
            assertEquals("off milieu $k", 0.0, p.offRouteM, 0.05)
        }
    }

    @Test fun `next maneuver index and distance to it`() {
        val t = RouteTracker(route)
        for (k in 1 until route.maneuvers.size) {
            val begin = route.maneuvers[k].beginShapeIndex
            if (begin - 1 <= route.maneuvers[k - 1].beginShapeIndex) continue // pas de point intermédiaire
            val p = t.update(fixAt(pts[begin - 1]))
            assertEquals("manœuvre suivante depuis le point ${begin - 1}", k, p.nextManeuverIndex)
            assertEquals(cum[begin] - cum[begin - 1], p.distanceToNextManeuverM, 0.05)
        }
    }

    @Test fun `remaining values at departure equal the route totals`() {
        val p = RouteTracker(route).update(fixAt(pts[0]))
        assertEquals(route.maneuvers.sumOf { it.durationSeconds }, p.remainingDurationS, 0.5)
        assertEquals(route.distanceMeters, p.remainingDistanceM, 1.0)
        assertEquals(1, p.nextManeuverIndex)
    }

    @Test fun `remaining duration at each maneuver start is the sum of the remaining Valhalla durations`() {
        val t = RouteTracker(route)
        for (k in 1 until route.maneuvers.lastIndex) {
            val p = t.update(fixAt(pts[route.maneuvers[k].beginShapeIndex]))
            assertEquals("restant au début de la manœuvre $k", route.maneuvers.drop(k).sumOf { it.durationSeconds }, p.remainingDurationS, 0.5)
        }
    }

    @Test fun `remaining duration is prorated inside a maneuver`() {
        val t = RouteTracker(route)
        val m6 = route.maneuvers[6]
        val start = cum[m6.beginShapeIndex]
        val end = cum[route.maneuvers[7].beginShapeIndex]
        val p = t.update(fixAt(t.pointAt(start + (end - start) * 0.25)))
        val expected = m6.durationSeconds * 0.75 + route.maneuvers.drop(7).sumOf { it.durationSeconds }
        assertEquals(expected, p.remainingDurationS, 0.5)
    }

    @Test fun `remaining distance decreases along the route`() {
        val t = RouteTracker(route)
        var last = Double.MAX_VALUE
        for (k in pts.indices step 7) {
            val p = t.update(fixAt(pts[k]))
            assertTrue("point $k", p.remainingDistanceM <= last + 1e-6)
            last = p.remainingDistanceM
        }
    }

    @Test fun `window falls back to a global search after a jump`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[20]))
        assertEquals(cum[280], t.update(fixAt(pts[280])).distanceAlongM, 0.01)
    }

    @Test fun `a point 1 km away is off route at once`() {
        val t = RouteTracker(route)
        val far = GeoPoint(pts.maxOf { it.lat } + 0.01, pts.map { it.lon }.average())
        assertTrue(t.update(fixAt(far)).offRouteM > GuidanceConstants.OFF_ROUTE_HARD_M)
        assertTrue(t.isOffRoute)
    }

    @Test fun `arrival needs the last maneuver and less than 25 m`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[300]))
        val before = t.pointAt(t.totalGeometryM - 60.0)
        assertTrue("garde-fou : point à plus de 25 m de l'arrivée", before.distanceTo(pts.last()) > GuidanceConstants.ARRIVAL_RADIUS_M)
        t.update(fixAt(before))
        assertFalse(t.hasArrived)
        t.update(fixAt(pts.last()))
        assertTrue(t.hasArrived)
    }

    @Test fun `extrapolation advances along the polyline and clamps at the end`() {
        val t = RouteTracker(route)
        t.update(fixAt(pts[100]))
        val p = t.extrapolate(50.0)
        assertEquals(cum[100] + 50.0, p.distanceAlongM, 1e-6)
        assertEquals(0.0, p.offRouteM, 0.0)
        assertEquals(t.pointAt(cum[100] + 50.0), p.snapped)
        assertEquals(t.totalGeometryM, t.extrapolate(1e9).distanceAlongM, 1e-6)
    }

    @Test fun `pointAt returns the vertices at their cumulative distance`() {
        val t = RouteTracker(route)
        listOf(0, 37, 222, pts.lastIndex).forEach { k -> assertEquals("point $k", 0.0, t.pointAt(cum[k]).distanceTo(pts[k]), 0.01) }
    }

    @Test fun `bearing follows the GPS when moving and the segment otherwise`() {
        val t = RouteTracker(route)
        assertEquals(123f, t.update(fixAt(mid(pts[40], pts[41]), speedMps = 10f, bearing = 123f)).bearingDeg!!, 0f)
        val still = t.update(fixAt(mid(pts[40], pts[41]), speedMps = 0.2f, bearing = 123f)).bearingDeg!!
        assertEquals(bearing(pts[40], pts[41]).toFloat(), still, 0.01f)
    }

    @Test fun `initial progress starts at the origin`() {
        val p = RouteTracker(route).initialProgress()
        assertEquals(0.0, p.distanceAlongM, 0.0)
        assertEquals(1, p.nextManeuverIndex)
        assertEquals(pts[0], p.snapped)
    }
}
```

`RouteTrackerOffRouteTest.kt` — règles hors itinéraire sur l'itinéraire synthétique rectiligne (perpendiculaire exacte) :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerOffRouteTest {
    private val route = TestRoutes.straight()
    private fun fix(p: GeoPoint) = LocationFix(p, null, null, 5f, 0L)

    @Test fun `30 m lateral offset is measured and tolerated`() {
        val t = RouteTracker(route)
        val p = t.update(fix(TestRoutes.north(t.pointAt(300.0), 30.0)))
        assertEquals(30.0, p.offRouteM, 0.5)
        assertEquals(300.0, p.distanceAlongM, 0.5)
        assertFalse(t.isOffRoute)
    }

    @Test fun `five consecutive fixes beyond 40 m trigger off route`() {
        val t = RouteTracker(route)
        repeat(4) {
            t.update(fix(TestRoutes.north(t.pointAt(300.0 + it * 10), 60.0)))
            assertFalse("fix ${it + 1}", t.isOffRoute)
        }
        t.update(fix(TestRoutes.north(t.pointAt(340.0), 60.0)))
        assertTrue(t.isOffRoute)
    }

    @Test fun `coming back on route resets the counter`() {
        val t = RouteTracker(route)
        repeat(4) { t.update(fix(TestRoutes.north(t.pointAt(300.0), 60.0))) }
        t.update(fix(t.pointAt(310.0)))
        assertFalse(t.isOffRoute)
        repeat(4) { t.update(fix(TestRoutes.north(t.pointAt(320.0), 60.0))) }
        assertFalse(t.isOffRoute)
    }

    @Test fun `one fix beyond 100 m triggers off route`() {
        val t = RouteTracker(route)
        t.update(fix(TestRoutes.north(t.pointAt(300.0), 150.0)))
        assertTrue(t.isOffRoute)
    }

    @Test fun `degenerate routes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RouteTracker(route.copy(geometry = route.geometry.take(1))) }
        assertThrows(IllegalArgumentException::class.java) { RouteTracker(route.copy(maneuvers = emptyList())) }
    }
}
```

`AnnouncementPolicyTest.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RouteProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class AnnouncementPolicyTest {
    private val maneuvers = ParisRoute.route().maneuvers
    private fun progress(next: Int, dist: Double) = RouteProgress("paris", 0, 0.0, 0.0, next, dist, 1000.0, 100.0, GeoPoint(48.85, 2.35), null)
    private fun next(k: Int, d: Double, v: Double?, already: Set<Pair<Int, Tier>> = emptySet()) = AnnouncementPolicy.next(progress(k, d), maneuvers, v, already)
    private fun lower(s: String) = s.replaceFirstChar { it.lowercase(Locale.FRANCE) }

    @Test fun `city far tier uses the alert text with a spoken distance`() {
        val a = next(1, 250.0, 30.0)!!
        assertEquals(Tier.FAR, a.tier)
        assertEquals(1, a.maneuverIndex)
        assertEquals("Dans 250 mètres, " + lower(maneuvers[1].verbalAlert!!), a.text)
    }

    @Test fun `city near tier at 70 m`() {
        val a = next(1, 70.0, 30.0)!!
        assertEquals(Tier.NEAR, a.tier)
        assertEquals("Dans 70 mètres, " + lower(maneuvers[1].verbalAlert!!), a.text)
    }

    @Test fun `now tier speaks the pre transition text verbatim`() {
        val a = next(1, 30.0, 30.0)!!
        assertEquals(Tier.NOW, a.tier)
        assertEquals(maneuvers[1].verbalPre, a.text)
    }

    @Test fun `nothing beyond the far threshold of the speed class`() {
        assertNull(next(1, 350.0, 30.0))   // ville : 300 m
        assertNull(next(1, 700.0, 70.0))   // route : 600 m
        assertNull(next(1, 2100.0, 110.0)) // autoroute : 2000 m
    }

    @Test fun `highway far tier at 1,5 km`() {
        val a = next(1, 1500.0, 110.0)!!
        assertEquals(Tier.FAR, a.tier)
        assertEquals("Dans 1,5 kilomètre, " + lower(maneuvers[1].verbalAlert!!), a.text)
    }

    @Test fun `road thresholds`() {
        assertEquals(Tier.FAR, next(1, 600.0, 70.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 120.0, 70.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 150.0, 50.0)!!.tier)
    }

    @Test fun `threshold edges`() {
        assertEquals(Tier.FAR, next(1, 300.0, 30.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 80.0, 30.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 40.0, 30.0)!!.tier)
        assertEquals(Tier.NOW, next(1, 39.9, 30.0)!!.tier)
        assertEquals(Tier.FAR, next(1, 2000.0, 90.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 300.0, 90.0)!!.tier)
    }

    @Test fun `unknown speed uses city thresholds`() {
        assertNull(next(1, 500.0, null))
        assertEquals(Tier.FAR, next(1, 250.0, null)!!.tier)
    }

    @Test fun `same tier is never repeated`() = assertNull(next(1, 240.0, 30.0, setOf(1 to Tier.FAR)))

    @Test fun `a tier never comes back after a more urgent one`() {
        assertNull(next(1, 250.0, 30.0, setOf(1 to Tier.NEAR)))
        assertEquals(Tier.NOW, next(1, 30.0, 30.0, setOf(1 to Tier.NEAR))!!.tier)
        assertNull(next(1, 10.0, 30.0, setOf(1 to Tier.NOW)))
    }

    @Test fun `tiers of another maneuver do not block`() {
        val a = next(2, 250.0, 30.0, setOf(1 to Tier.NOW))!!
        assertEquals(2, a.maneuverIndex)
        assertEquals(Tier.FAR, a.tier)
    }

    @Test fun `missing alert text falls back to the pre transition text`() {
        assertNull("la fixture n'a pas d'alerte pour la manœuvre 11", maneuvers[11].verbalAlert)
        assertEquals("Dans 200 mètres, " + lower(maneuvers[11].verbalPre!!), next(11, 200.0, 30.0)!!.text)
    }

    @Test fun `arrival now uses the fixture sentence`() {
        val last = maneuvers.lastIndex
        assertEquals(maneuvers[last].verbalPre, next(last, 10.0, 30.0)!!.text)
    }

    @Test fun `out of range maneuver index gives nothing`() = assertNull(next(99, 10.0, 30.0))
}
```

`SpokenPhrasesTest.kt` :

```kotlin
package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenPhrasesTest {
    @Test fun `distances are rounded for speech`() {
        assertEquals("10 mètres", SpokenPhrases.distance(8.0))
        assertEquals("50 mètres", SpokenPhrases.distance(47.0))
        assertEquals("70 mètres", SpokenPhrases.distance(70.0))
        assertEquals("250 mètres", SpokenPhrases.distance(260.0))
        assertEquals("1 kilomètre", SpokenPhrases.distance(980.0))
        assertEquals("1 kilomètre", SpokenPhrases.distance(1000.0))
        assertEquals("1,5 kilomètre", SpokenPhrases.distance(1540.0))
        assertEquals("2 kilomètres", SpokenPhrases.distance(2000.0))
        assertEquals("12,3 kilomètres", SpokenPhrases.distance(12_340.0))
    }

    @Test fun `in distance lowercases the instruction`() =
        assertEquals("Dans 300 mètres, tournez à droite.", SpokenPhrases.inDistance(300.0, "Tournez à droite."))
}
```

`SpeedSmootherTest.kt` :

```kotlin
package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedSmootherTest {
    @Test fun `exponential smoothing with alpha 0,3`() {
        val s = SpeedSmoother()
        assertNull(s.update(null))
        assertEquals(50.0, s.update(50.0)!!, 1e-9)
        assertEquals(65.0, s.update(100.0)!!, 1e-9)        // 0,3·100 + 0,7·50
        assertEquals(65.0, s.update(null)!!, 1e-9)         // pas de mesure : valeur conservée
        assertEquals(45.5, s.update(0.0)!!, 1e-9)          // 0,3·0 + 0,7·65
    }
}
```

- [ ] **Step 4 : vérifier l'échec**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'RouteTracker'`, `'AnnouncementPolicy'`, `'SpokenPhrases'`, `'SpeedSmoother'`.

- [ ] **Step 5 : `RouteTracker.kt`**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Suit la position sur un itinéraire (pur Kotlin, testé sur JVM). Formules : plan Phase 2,
 * « Spécification numérique de RouteTracker ». Non thread-safe : un seul appelant à la fois.
 */
class RouteTracker(private val route: Route) {
    init {
        require(route.geometry.size >= 2) { "Itinéraire sans géométrie exploitable" }
        require(route.maneuvers.isNotEmpty()) { "Itinéraire sans manœuvre" }
    }

    private val pts = route.geometry
    private val segCount = pts.size - 1
    private val segLenM = DoubleArray(segCount) { pts[it].distanceTo(pts[it + 1]) }

    /** cumDistM[i] = distance le long du tracé au point i (début du segment i) ; taille n. */
    val cumDistM: DoubleArray = DoubleArray(pts.size).also { c -> for (i in 0 until segCount) c[i + 1] = c[i] + segLenM[i] }
    val totalGeometryM: Double = cumDistM[segCount]

    private val maneuvers = route.maneuvers
    private val lastManeuver = maneuvers.lastIndex
    /** Distance le long du tracé au début de chaque manœuvre. */
    val maneuverStartM: DoubleArray = DoubleArray(maneuvers.size) { cumDistM[maneuvers[it].beginShapeIndex.coerceIn(0, segCount)] }
    private val maneuverEndM = DoubleArray(maneuvers.size) { k -> if (k < lastManeuver) maneuverStartM[k + 1] else totalGeometryM }
    private val suffixDurS = DoubleArray(maneuvers.size + 1).also { s -> for (k in lastManeuver downTo 0) s[k] = s[k + 1] + maneuvers[k].durationSeconds }
    private val distScale = if (totalGeometryM > 0.0 && route.distanceMeters > 0.0) route.distanceMeters / totalGeometryM else 1.0

    private var consecutiveOff = 0

    var isOffRoute: Boolean = false
        private set
    var hasArrived: Boolean = false
        private set
    var lastProgress: RouteProgress? = null
        private set

    fun initialProgress(): RouteProgress = progressAt(0, 0.0, 0.0, pts[0], bearing(pts[0], pts[1]).toFloat())

    fun update(fix: LocationFix): RouteProgress {
        val prev = lastProgress
        var best = if (prev == null) search(fix.point, 0, segCount - 1) else searchWindow(fix.point, prev.distanceAlongM)
        if (prev != null && best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M) {
            val global = search(fix.point, 0, segCount - 1)
            if (global.offM < best.offM) best = global
        }
        when {
            best.offM > GuidanceConstants.OFF_ROUTE_HARD_M -> { consecutiveOff++; isOffRoute = true }
            best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M -> { consecutiveOff++; isOffRoute = consecutiveOff >= GuidanceConstants.OFF_ROUTE_SOFT_FIXES }
            else -> { consecutiveOff = 0; isOffRoute = false }
        }
        val moving = (fix.speedMps ?: 0f) >= 1f
        val heading = fix.bearingDeg?.takeIf { moving } ?: bearing(pts[best.segment], pts[best.segment + 1]).toFloat()
        val p = progressAt(best.segment, best.alongM, best.offM, best.snapped, heading)
        if (!hasArrived && p.nextManeuverIndex == lastManeuver && fix.point.distanceTo(pts[segCount]) < GuidanceConstants.ARRIVAL_RADIUS_M) {
            hasArrived = true
        }
        lastProgress = p
        return p
    }

    /** Avance de [distanceM] le long du tracé depuis la dernière position (perte GPS) ; ne touche pas aux règles hors itinéraire. */
    fun extrapolate(distanceM: Double): RouteProgress {
        val prev = lastProgress
        val along = ((prev?.distanceAlongM ?: 0.0) + distanceM.coerceAtLeast(0.0)).coerceAtMost(totalGeometryM)
        val seg = segmentAt(along)
        val p = progressAt(seg, along, 0.0, pointAt(along), prev?.bearingDeg ?: bearing(pts[seg], pts[seg + 1]).toFloat())
        lastProgress = p
        return p
    }

    /** Plus grand i ∈ [0, S) tel que cumDistM[i] ≤ [distanceAlongM]. */
    fun segmentAt(distanceAlongM: Double): Int {
        var lo = 0
        var hi = segCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (cumDistM[mid] <= distanceAlongM) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun pointAt(distanceAlongM: Double): GeoPoint {
        val a = distanceAlongM.coerceIn(0.0, totalGeometryM)
        val i = segmentAt(a)
        val t = if (segLenM[i] > 0.0) ((a - cumDistM[i]) / segLenM[i]).coerceIn(0.0, 1.0) else 0.0
        val p = pts[i]
        val q = pts[i + 1]
        return GeoPoint(p.lat + t * (q.lat - p.lat), p.lon + t * (q.lon - p.lon))
    }

    private fun progressAt(segment: Int, along: Double, off: Double, snapped: GeoPoint, heading: Float?): RouteProgress {
        var next = lastManeuver
        for (k in maneuvers.indices) if (maneuverStartM[k] > along) { next = k; break }
        val c = (next - 1).coerceAtLeast(0)
        val span = maneuverEndM[c] - maneuverStartM[c]
        val frac = if (span > 0.0) ((maneuverEndM[c] - along) / span).coerceIn(0.0, 1.0) else 0.0
        return RouteProgress(
            routeId = route.id,
            segmentIndex = segment,
            distanceAlongM = along,
            offRouteM = off,
            nextManeuverIndex = next,
            distanceToNextManeuverM = (maneuverStartM[next] - along).coerceAtLeast(0.0),
            remainingDistanceM = (totalGeometryM - along).coerceAtLeast(0.0) * distScale,
            remainingDurationS = maneuvers[c].durationSeconds * frac + suffixDurS[c + 1],
            snapped = snapped,
            bearingDeg = heading,
        )
    }

    private class Hit(val segment: Int, val alongM: Double, val offM: Double, val snapped: GeoPoint)

    private fun search(p: GeoPoint, from: Int, to: Int): Hit {
        var best = project(p, from)
        for (i in from + 1..to) {
            val h = project(p, i)
            if (h.offM < best.offM) best = h
        }
        return best
    }

    private fun searchWindow(p: GeoPoint, lastAlong: Double): Hit {
        val center = segmentAt(lastAlong)
        var from = center
        while (from > 0 && cumDistM[from] > lastAlong - GuidanceConstants.SEARCH_BACK_M) from--
        var to = center
        while (to < segCount - 1 && cumDistM[to + 1] < lastAlong + GuidanceConstants.SEARCH_AHEAD_M) to++
        return search(p, from, to)
    }

    /** Projection orthogonale dans le plan équirectangulaire local centré sur [p]. */
    private fun project(p: GeoPoint, i: Int): Hit {
        val a = pts[i]
        val b = pts[i + 1]
        val kLat = EARTH_RADIUS_M * PI / 180.0
        val kLon = kLat * cos(p.lat * PI / 180.0)
        val ax = (a.lon - p.lon) * kLon
        val ay = (a.lat - p.lat) * kLat
        val dx = (b.lon - a.lon) * kLon
        val dy = (b.lat - a.lat) * kLat
        val dd = dx * dx + dy * dy
        val t = if (dd == 0.0) 0.0 else (-(ax * dx + ay * dy) / dd).coerceIn(0.0, 1.0)
        val qx = ax + t * dx
        val qy = ay + t * dy
        return Hit(i, cumDistM[i] + t * segLenM[i], sqrt(qx * qx + qy * qy), GeoPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon)))
    }
}

/** Cap initial (degrés, 0 = nord, sens horaire) de [a] vers [b]. */
internal fun bearing(a: GeoPoint, b: GeoPoint): Double {
    val phi1 = a.lat * PI / 180.0
    val phi2 = b.lat * PI / 180.0
    val dLon = (b.lon - a.lon) * PI / 180.0
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    return (atan2(y, x) * 180.0 / PI + 360.0) % 360.0
}
```

- [ ] **Step 6 : `AnnouncementPolicy.kt`**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.RouteProgress
import java.util.Locale
import kotlin.math.roundToInt

enum class Tier { FAR, NEAR, NOW }

data class Announcement(val text: String, val tier: Tier, val maneuverIndex: Int)

/** Seuils d'annonce (m) par classe de vitesse — spec Phase 2 §2. */
internal enum class SpeedClass(val farM: Double, val nearM: Double, val nowM: Double) {
    HIGHWAY(2000.0, 300.0, 40.0),
    ROAD(600.0, 150.0, 40.0),
    CITY(300.0, 80.0, 40.0);

    companion object {
        fun of(speedKmh: Double?): SpeedClass = when {
            speedKmh == null -> CITY
            speedKmh >= 90.0 -> HIGHWAY
            speedKmh >= 50.0 -> ROAD
            else -> CITY
        }
    }
}

/** Décide quoi annoncer pour la prochaine manœuvre ; pur et sans état (l'anti-répétition est portée par [already]). */
object AnnouncementPolicy {
    fun next(progress: RouteProgress, maneuvers: List<Maneuver>, speedKmh: Double?, already: Set<Pair<Int, Tier>>): Announcement? {
        val k = progress.nextManeuverIndex
        val m = maneuvers.getOrNull(k) ?: return null
        val d = progress.distanceToNextManeuverM
        val c = SpeedClass.of(speedKmh)
        val tier = when {
            d < c.nowM -> Tier.NOW
            d <= c.nearM -> Tier.NEAR
            d <= c.farM -> Tier.FAR
            else -> return null
        }
        if (already.any { (i, t) -> i == k && t.ordinal >= tier.ordinal }) return null
        val text = when (tier) {
            Tier.NOW -> m.verbalPre ?: m.instruction
            Tier.NEAR, Tier.FAR -> SpokenPhrases.inDistance(d, m.verbalAlert ?: m.verbalPre ?: m.instruction)
        }
        if (text.isBlank()) return null
        return Announcement(text, tier, k)
    }
}

/** Phrases prononcées composées par la logique JVM (exception assumée à la règle « strings en ressources »). */
internal object SpokenPhrases {
    const val REROUTING = "Recalcul de l'itinéraire."

    fun inDistance(meters: Double, instruction: String): String =
        "Dans ${distance(meters)}, ${instruction.replaceFirstChar { it.lowercase(Locale.FRANCE) }}"

    /** < 100 m : arrondi à 10 m (min. 10) ; sinon à 50 m ; ≥ 1 km : kilomètres à 0,1 près. */
    fun distance(meters: Double): String {
        val rounded = if (meters < 100.0) ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10) else (meters / 50.0).roundToInt() * 50
        if (rounded < 1000) return "$rounded mètres"
        val km = (meters / 100.0).roundToInt() / 10.0
        val text = if (km % 1.0 == 0.0) km.toInt().toString() else String.format(Locale.FRANCE, "%.1f", km)
        return "$text ${if (km < 2.0) "kilomètre" else "kilomètres"}"
    }
}
```

- [ ] **Step 7 : `SpeedSmoother.kt` et `NavigationState.kt`**

```kotlin
package fr.roadly.core.navigation

/** Vitesse GPS lissée (moyenne exponentielle) : référence des paliers d'annonce et de l'alerte de dépassement. */
class SpeedSmoother(private val alpha: Double = 0.3) {
    private var value: Double? = null

    fun update(sampleKmh: Double?): Double? {
        if (sampleKmh == null) return value
        val prev = value
        value = if (prev == null) sampleKmh else alpha * sampleKmh + (1 - alpha) * prev
        return value
    }
}
```

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.model.Lane
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress

/** État publié par `NavigationSession` (spec §4 ; dans core:navigation car il porte un AppError). */
sealed interface NavigationState {
    data object Idle : NavigationState

    /** [upcoming] = manœuvres next+1..next+3 ; [lanes] = voies de la manœuvre next ; [speedLimitKmh] null = inconnue (« — »). */
    data class Guiding(
        val route: Route,
        val progress: RouteProgress,
        val speedKmh: Double?,
        val speedLimitKmh: Int?,
        val upcoming: List<Maneuver>,
        val lanes: List<Lane>,
        val etaEpochMs: Long,
        val gpsLost: Boolean,
        val rerouting: Boolean,
    ) : NavigationState

    data class Arrived(val route: Route) : NavigationState
    data class Failed(val error: AppError) : NavigationState
}
```

- [ ] **Step 8 : vérifier le succès**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` ; `RouteTrackerTest` (16), `RouteTrackerOffRouteTest` (5), `AnnouncementPolicyTest` (14), `SpokenPhrasesTest` (2), `SpeedSmootherTest` (1) verts. Si `cumulative distances match the fixture geometry` échoue sur l'écart de 2 %, ne pas élargir la tolérance sans relever l'écart réel (`totalGeometryM` vs `distanceMeters`) dans le rapport de tâche.

- [ ] **Step 9 : commit**

```powershell
git add settings.gradle.kts core/navigation
git commit -m "feat(navigation): module core:navigation, RouteTracker, politique d'annonces et vitesse lissée" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 6 : `core:navigation` (2/3) — limitation de vitesse, synthèse vocale, horloge, contrôleur de service

**Files:**
- Create: `core/navigation/src/main/java/fr/roadly/core/navigation/{NavigationClock.kt, SpeedLimitProvider.kt, ValhallaTraceSpeedLimitProvider.kt, SpeechService.kt, AndroidSpeechService.kt, NavigationServiceController.kt}`
- Create: `core/navigation/src/main/java/fr/roadly/core/navigation/testing/{FakeSpeechService.kt, FakeNavigationServiceController.kt}`
- Test: `core/navigation/src/test/java/fr/roadly/core/navigation/ValhallaTraceSpeedLimitProviderTest.kt`

**Interfaces:**
- Consumes: `ApiClient.postJson(url: HttpUrl, body: String): Outcome<String>`, `ProviderConfigRepository.config: Flow<ProviderConfig>`, `ProviderConfig.DEFAULT` (`core:data`) ; `Json` (Hilt, `NetworkModule`) ; `GuidanceConstants.SPEED_LIMIT_MIN_INTERVAL_MS`.
- Produces: `fun interface NavigationClock { fun nowMs(): Long }`, `SystemNavigationClock`, `SpeedLimitProvider.speedLimitKmh(recent: List<LocationFix>): Outcome<Int?>`, `NoopSpeedLimitProvider`, `ValhallaTraceSpeedLimitProvider(api, config, json, clock)` + `companion fun isPublicInstance(url: HttpUrl): Boolean`, `SpeechService { speak(text, flush = false); setVolume(percent); beep(); stop() }`, `AndroidSpeechService`, `NavigationServiceController { start() }`, `testing.FakeSpeechService { spoken: List<String>; volume: Int; beeps: Int; stops: Int }`, `testing.FakeNavigationServiceController { starts: Int }`.

Contrat `trace_attributes` (Valhalla Map Matching API) : `POST <base>/trace_attributes`, corps `{"shape":[{"lat":…,"lon":…},…],"costing":"auto","shape_match":"map_snap","units":"kilometers","filters":{"attributes":["edge.speed_limit","edge.names","edge.road_class"],"action":"include"}}` ; réponse `{"edges":[{"speed_limit":50,"names":[…],"road_class":"…"},…]}`. La limitation retenue est celle de la **dernière** arête (celle du fix le plus récent). `speed_limit` absent, nul, ≥ 255 ou non numérique → inconnue (`null`). Jamais d'appel vers `*.openstreetmap.de` (instance publique FOSSGIS) ; au plus un appel toutes les 5 s (sinon on renvoie la dernière valeur).

- [ ] **Step 1 : test du fournisseur de limitation (échoue)**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValhallaTraceSpeedLimitProviderTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private var now = 100_000L
    private val cfg = MutableStateFlow(ProviderConfig.DEFAULT)
    private lateinit var provider: ValhallaTraceSpeedLimitProvider
    private val fixes = listOf(
        LocationFix(GeoPoint(48.8566, 2.3522), 90f, 13f, 5f, 0L),
        LocationFix(GeoPoint(48.8567, 2.3530), 90f, 13f, 5f, 1_000L),
    )

    @Before fun setUp() {
        server.start()
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = cfg
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        provider = ValhallaTraceSpeedLimitProvider(ApiClient(OkHttpClient(), dispatchers), config, json) { now }
    }
    @After fun tearDown() = server.shutdown()

    private fun selfHosted() { cfg.value = ProviderConfig.DEFAULT.copy(valhallaBaseUrl = server.url("/").toString().trimEnd('/')) }

    @Test fun `public FOSSGIS instance is never called`() = runTest {
        assertTrue(ValhallaTraceSpeedLimitProvider.isPublicInstance("https://valhalla1.openstreetmap.de/trace_attributes".toHttpUrl()))
        assertFalse(ValhallaTraceSpeedLimitProvider.isPublicInstance("https://valhalla.exemple.fr/trace_attributes".toHttpUrl()))
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes))
        assertEquals(0, server.requestCount)
    }

    @Test fun `self hosted instance returns the last edge limit and sends the required filters`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":30,"names":["Rue A"]},{"speed_limit":50,"names":["Rue B"],"road_class":"secondary"}]}"""))
        assertEquals(Outcome.Success(50), provider.speedLimitKmh(fixes))
        val req = server.takeRequest()
        assertEquals("/trace_attributes", req.path)
        val body = json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("auto", body["costing"]!!.jsonPrimitive.content)
        assertEquals("map_snap", body["shape_match"]!!.jsonPrimitive.content)
        assertEquals(2, body["shape"]!!.jsonArray.size)
        assertEquals(
            listOf("edge.speed_limit", "edge.names", "edge.road_class"),
            body["filters"]!!.jsonObject["attributes"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test fun `unknown or unlimited limits give null`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":0}]}"""))
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes))
        now += 5_000
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":"unlimited"}]}"""))
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes))
        now += 5_000
        server.enqueue(MockResponse().setBody("""{"edges":[]}"""))
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes))
    }

    @Test fun `at most one call every 5 s`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":70}]}"""))
        assertEquals(Outcome.Success(70), provider.speedLimitKmh(fixes))
        now += 4_999
        assertEquals(Outcome.Success(70), provider.speedLimitKmh(fixes))
        assertEquals(1, server.requestCount)
        now += 1
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":90}]}"""))
        assertEquals(Outcome.Success(90), provider.speedLimitKmh(fixes))
        assertEquals(2, server.requestCount)
    }

    @Test fun `failure clears the limit`() = runTest {
        selfHosted()
        server.enqueue(MockResponse().setBody("""{"edges":[{"speed_limit":70}]}"""))
        provider.speedLimitKmh(fixes)
        now += 5_000
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(provider.speedLimitKmh(fixes) is Outcome.Failure)
        now += 1_000
        assertEquals("pas de valeur périmée après un échec", Outcome.Success(null), provider.speedLimitKmh(fixes))
    }

    @Test fun `fewer than two fixes is not enough`() = runTest {
        selfHosted()
        assertEquals(Outcome.Success(null), provider.speedLimitKmh(fixes.take(1)))
        assertEquals(0, server.requestCount)
    }

    @Test fun `noop provider always answers unknown`() = runTest {
        assertNull((NoopSpeedLimitProvider().speedLimitKmh(fixes) as Outcome.Success).value)
    }
}
```

- [ ] **Step 2 : vérifier l'échec**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'ValhallaTraceSpeedLimitProvider'`, `'NoopSpeedLimitProvider'`.

- [ ] **Step 3 : `NavigationClock.kt` et `SpeedLimitProvider.kt`**

```kotlin
package fr.roadly.core.navigation

import javax.inject.Inject

/** Horloge injectable : temps virtuel en test, `System.currentTimeMillis()` en production. */
fun interface NavigationClock {
    fun nowMs(): Long
}

class SystemNavigationClock @Inject constructor() : NavigationClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
```

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.Outcome
import fr.roadly.core.model.LocationFix
import javax.inject.Inject

/** Limitation de vitesse de la route courante (km/h) ; `null` = inconnue, jamais devinée. */
interface SpeedLimitProvider {
    suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?>
}

class NoopSpeedLimitProvider @Inject constructor() : SpeedLimitProvider {
    override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> = Outcome.Success(null)
}
```

- [ ] **Step 4 : `ValhallaTraceSpeedLimitProvider.kt`**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Limitation via Valhalla `trace_attributes` (map matching des derniers fixes). Actif uniquement sur une
 * instance auto-hébergée (spec §2 et §8) ; au plus un appel toutes les 5 s.
 */
@Singleton
class ValhallaTraceSpeedLimitProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
    private val clock: NavigationClock,
) : SpeedLimitProvider {
    private val mutex = Mutex()
    private var lastCallAtMs: Long? = null
    private var lastValue: Int? = null

    override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> = mutex.withLock {
        val base = try {
            config.config.first().valhallaBaseUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock Outcome.Success(null)
        }
        val url = "${base.trimEnd('/')}/trace_attributes".toHttpUrlOrNull() ?: return@withLock Outcome.Success(null)
        if (isPublicInstance(url)) return@withLock Outcome.Success(null)
        if (recent.size < 2) return@withLock Outcome.Success(lastValue)
        val now = clock.nowMs()
        val last = lastCallAtMs
        if (last != null && now - last < GuidanceConstants.SPEED_LIMIT_MIN_INTERVAL_MS) return@withLock Outcome.Success(lastValue)
        lastCallAtMs = now
        val body = buildJsonObject {
            putJsonArray("shape") { recent.takeLast(GuidanceConstants.RECENT_FIXES).forEach { f -> addJsonObject { put("lat", f.point.lat); put("lon", f.point.lon) } } }
            put("costing", "auto")
            put("shape_match", "map_snap")
            put("units", "kilometers")
            putJsonObject("filters") {
                putJsonArray("attributes") { add("edge.speed_limit"); add("edge.names"); add("edge.road_class") }
                put("action", "include")
            }
        }
        when (val r = api.postJson(url, body.toString())) {
            is Outcome.Success -> {
                lastValue = parse(r.value)
                Outcome.Success(lastValue)
            }
            is Outcome.Failure -> {
                lastValue = null
                r
            }
        }
    }

    private fun parse(text: String): Int? = runCatching {
        val edges = json.parseToJsonElement(text).jsonObject["edges"]?.jsonArray ?: return null
        val last = edges.lastOrNull() as? JsonObject ?: return null
        (last["speed_limit"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..254 }
    }.getOrNull()

    companion object {
        /** Instance publique FOSSGIS (fair-use, développement) : jamais interrogée pour la limitation. */
        fun isPublicInstance(url: HttpUrl): Boolean = url.host == "openstreetmap.de" || url.host.endsWith(".openstreetmap.de")
    }
}
```

- [ ] **Step 5 : `SpeechService.kt` et `NavigationServiceController.kt`**

```kotlin
package fr.roadly.core.navigation

/** Synthèse vocale du guidage ; aucune voix serveur (spec §2). */
interface SpeechService {
    /** [flush] = interrompt ce qui est en cours ; sinon mise en file. */
    fun speak(text: String, flush: Boolean = false)
    /** 0–100, appliqué via `TextToSpeech.Engine.KEY_PARAM_VOLUME`. */
    fun setVolume(percent: Int)
    /** Son court d'alerte de dépassement. */
    fun beep()
    /** Coupe la parole en cours et vide la file. */
    fun stop()
}
```

```kotlin
package fr.roadly.core.navigation

/** Démarre le service de premier plan du guidage ; il s'arrête seul quand la session quitte `Guiding`. */
interface NavigationServiceController {
    fun start()
}
```

- [ ] **Step 6 : `AndroidSpeechService.kt`** (non testable sur JVM : vérifié sur l'appareil en Task 12)

```kotlin
package fr.roadly.core.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `TextToSpeech` en `Locale.FRANCE`, focus audio transitoire avec atténuation des autres sources
 * (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), file d'annonces, volume par annonce. Moteur créé au premier usage.
 */
@Singleton
class AndroidSpeechService @Inject constructor(@param:ApplicationContext private val ctx: Context) : SpeechService {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val pending = ArrayDeque<Pair<String, Boolean>>()
    private val inFlight = AtomicInteger(0)
    private val ids = AtomicLong(0)
    private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var unavailable = false
    @Volatile private var volume = 1f

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished()
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished()
        override fun onError(utteranceId: String?, errorCode: Int) = finished()
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished()
    }

    override fun speak(text: String, flush: Boolean) {
        if (unavailable || text.isBlank()) return
        val tts = ensureEngine()
        synchronized(lock) {
            if (!ready) {
                if (flush) pending.clear()
                pending.addLast(text to flush)
                return
            }
        }
        say(tts, text, flush)
    }

    override fun setVolume(percent: Int) { volume = percent.coerceIn(0, 100) / 100f }

    override fun beep() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            main.postDelayed({ tone.release() }, 400)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bip indisponible", e)
        }
    }

    override fun stop() {
        synchronized(lock) { pending.clear() }
        engine?.stop()
        inFlight.set(0)
        audio.abandonAudioFocusRequest(focusRequest)
    }

    private fun ensureEngine(): TextToSpeech = synchronized(lock) {
        engine ?: TextToSpeech(ctx) { status -> onInit(status) }.also {
            it.setAudioAttributes(attributes)
            it.setOnUtteranceProgressListener(listener)
            engine = it
        }
    }

    private fun onInit(status: Int) {
        // Certains moteurs rappellent onInit(ERROR) de façon synchrone dans le constructeur (engine encore null).
        val tts = synchronized(lock) { engine }
        val lang = if (status == TextToSpeech.SUCCESS && tts != null) tts.setLanguage(Locale.FRANCE) else TextToSpeech.LANG_NOT_SUPPORTED
        if (tts == null || lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Voix française indisponible (statut $status, langue $lang)")
            unavailable = true
            synchronized(lock) { pending.clear() }
            return
        }
        val queued = synchronized(lock) { ready = true; pending.toList().also { pending.clear() } }
        queued.forEach { (text, flush) -> say(tts, text, flush) }
    }

    private fun say(tts: TextToSpeech, text: String, flush: Boolean) {
        if (inFlight.getAndIncrement() == 0) audio.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        val id = "roadly-${ids.incrementAndGet()}"
        if (tts.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) finished()
    }

    private fun finished() {
        if (inFlight.decrementAndGet() <= 0) {
            inFlight.set(0)
            audio.abandonAudioFocusRequest(focusRequest)
        }
    }

    private companion object { const val TAG = "RoadlySpeech" }
}
```

- [ ] **Step 7 : doubles de test partagés**

`testing/FakeSpeechService.kt` :

```kotlin
package fr.roadly.core.navigation.testing

import fr.roadly.core.navigation.SpeechService

class FakeSpeechService : SpeechService {
    val spoken = mutableListOf<String>()
    var volume = 100
        private set
    var beeps = 0
        private set
    var stops = 0
        private set

    override fun speak(text: String, flush: Boolean) { spoken += text }
    override fun setVolume(percent: Int) { volume = percent.coerceIn(0, 100) }
    override fun beep() { beeps++ }
    override fun stop() { stops++ }
}
```

`testing/FakeNavigationServiceController.kt` :

```kotlin
package fr.roadly.core.navigation.testing

import fr.roadly.core.navigation.NavigationServiceController

class FakeNavigationServiceController : NavigationServiceController {
    var starts = 0
        private set
    override fun start() { starts++ }
}
```

- [ ] **Step 8 : vérifier le succès**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` ; `ValhallaTraceSpeedLimitProviderTest` (7) vert ; tests de la Task 5 toujours verts ; `AndroidSpeechService` compile.

- [ ] **Step 9 : commit**

```powershell
git add core/navigation
git commit -m "feat(navigation): limitation de vitesse trace_attributes (auto-hébergé), synthèse vocale française, horloge" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7 : `core:navigation` (3/3) — `NavigationSession`, service de premier plan, module Hilt

**Files:**
- Create: `core/navigation/src/main/java/fr/roadly/core/navigation/{NavigationSession.kt, NavigationService.kt, NavigationNotifications.kt, ForegroundServiceController.kt, NavigationModule.kt}`
- Create: `core/navigation/src/main/res/values/strings.xml`, `core/navigation/src/main/res/drawable/ic_stat_navigation.xml`
- Test: `core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionTest.kt`

**Interfaces:**
- Consumes: `LocationRepository.{permissionState, fixes, setGuidanceActive}` (Task 4), `RoutingProvider.route(RouteRequest)`, `RouteOption` (`core:routing`), `UserPreferencesRepository.{voiceEnabled, voiceVolume, speedAlertToleranceKmh, speedAlertSound}` (Task 3), `DispatcherProvider`, `@ApplicationScope CoroutineScope` (`core:data`), tout ce que produisent les Tasks 5 et 6.
- Produces: `NavigationSession(location, routing, speedLimits, speech, prefs, serviceController, clock, dispatchers, scope)` avec `state: StateFlow<NavigationState>`, `muted: StateFlow<Boolean>`, `start(option: RouteOption, preferences: RoutePreferences)`, `stop()`, `retry()`, `setMuted(muted: Boolean)` ; `NavigationService.ACTION_STOP`, `NavigationService.ACTION_OPEN_NAVIGATION` ; liaisons Hilt `SpeedLimitProvider → ValhallaTraceSpeedLimitProvider`, `SpeechService → AndroidSpeechService`, `NavigationServiceController → ForegroundServiceController`, `NavigationClock → SystemNavigationClock`.

Comportement de `NavigationSession` (à tester tel quel) :

| Événement | Effet |
|---|---|
| `start` sans permission de localisation | `Failed(PermissionDenied)`, ni GPS ni service |
| `start` | `Guiding` immédiat (progression initiale), `setGuidanceActive(true)`, service démarré, volume appliqué, annonce du départ (`verbalPre` de la manœuvre 0) |
| fix | lissage vitesse → `RouteTracker.update` → arrivée ? → hors itinéraire ? → annonce (si pas en recalcul) → limitation (≤ 1/5 s côté fournisseur) → alerte de dépassement → publication `Guiding` (ETA = maintenant + restant) |
| hors itinéraire | recalcul si aucun depuis 10 s : annonce « Recalcul de l'itinéraire. », `rerouting = true`, `RoutingProvider.route(origin = fix, destination = fin du tracé initial, costing initial, préférences)` ; succès → nouveau tracé ; échec → on garde l'ancien et on réessaiera |
| aucun fix depuis > 3 s | tic d'1 s : extrapolation `vitesse lissée × temps écoulé` ; `gpsLost` après 10 s |
| arrivée | annonce de la dernière manœuvre si pas déjà dite, `Arrived(route)`, GPS relâché, course terminée |
| dépassement | si limitation connue et `v > limite + tolérance` : un bip à l'entrée en dépassement si « son de dépassement » est activé et voix non coupée ; réarmé quand `v` repasse sous le seuil |
| `setMuted(true)` | plus aucune annonce ni bip, parole en cours coupée |
| `stop()` | `Idle`, GPS relâché, parole coupée ; le service s'arrête en voyant `Idle` |

- [ ] **Step 1 : tests de session (échouent)**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val service = FakeNavigationServiceController()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    private val requests = mutableListOf<RouteRequest>()
    private var routingResult: Outcome<List<Route>> = Outcome.Success(listOf(TestRoutes.straight("rerouted")))
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += request; return routingResult }
    }
    private var limit: Int? = null
    private val speedLimits = object : SpeedLimitProvider {
        override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> = Outcome.Success(limit)
    }

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, speedLimits, speech, prefs, service, { testScheduler.currentTime }, dispatchers, backgroundScope)
    }

    private fun fixAt(along: Double, speedMps: Float? = 10f, northM: Double = 0.0) =
        LocationFix(TestRoutes.north(tracker.pointAt(along), northM), 90f, speedMps, 5f, 0L)

    private fun guiding(s: NavigationSession) = s.state.value as NavigationState.Guiding

    @Test fun `start guides at once, holds the GPS, starts the service and announces the departure`() = runTest {
        val s = session()
        s.start(option, RoutePreferences())
        assertTrue(s.state.value is NavigationState.Guiding)
        assertEquals(0.0, guiding(s).progress.distanceAlongM, 0.0)
        assertTrue(location.guidanceActive)
        assertEquals(1, service.starts)
        runCurrent()
        assertEquals(listOf("Conduisez vers l'est."), speech.spoken)
        assertEquals(100, speech.volume)
    }

    @Test fun `a fix updates progress and ETA`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(300.0)); runCurrent()
        val g = guiding(s)
        assertEquals(300.0, g.progress.distanceAlongM, 0.5)
        assertEquals(1, g.progress.nextManeuverIndex)
        assertEquals(testScheduler.currentTime + g.progress.remainingDurationS * 1000, g.etaEpochMs.toDouble(), 1.0)
        assertEquals(36.0, g.speedKmh!!, 1e-9)
        assertEquals(listOf(route.maneuvers[2]), g.upcoming)
        assertTrue(g.lanes.isEmpty())
    }

    @Test fun `near announcement is spoken once`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        val turnAt = tracker.maneuverStartM[1]
        location.emit(fixAt(turnAt - 70.0)); runCurrent()
        location.emit(fixAt(turnAt - 62.0)); runCurrent()
        assertEquals(listOf("Conduisez vers l'est.", "Dans 70 mètres, tournez à droite dans Rue d'Essai."), speech.spoken)
    }

    @Test fun `five fixes 60 m off route trigger one reroute with the preferences`() = runTest {
        val s = session()
        val prefsForRoute = RoutePreferences(avoidTolls = true)
        s.start(option, prefsForRoute); runCurrent()
        repeat(5) { location.emit(fixAt(300.0 + it * 5, northM = 60.0)); runCurrent() }
        assertEquals(1, requests.size)
        val r = requests.single()
        assertEquals(fixAt(320.0, northM = 60.0).point, r.origin)
        assertEquals(route.geometry.last(), r.destination)
        assertEquals(RouteCosting.FASTEST, r.costing)
        assertEquals(prefsForRoute, r.preferences)
        assertTrue(speech.spoken.contains(SpokenPhrases.REROUTING))
        assertEquals("rerouted", guiding(s).route.id)
        assertFalse(guiding(s).rerouting)
    }

    @Test fun `reroute is limited to one every 10 s and the old route is kept on failure`() = runTest {
        routingResult = Outcome.Failure(AppError.Network)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(5) { location.emit(fixAt(300.0, northM = 60.0)); runCurrent() }
        assertEquals(1, requests.size)
        assertEquals("straight", guiding(s).route.id)
        advanceTimeBy(5_000); runCurrent()
        location.emit(fixAt(300.0, northM = 60.0)); runCurrent()
        assertEquals(1, requests.size)
        advanceTimeBy(5_001); runCurrent()
        location.emit(fixAt(300.0, northM = 60.0)); runCurrent()
        assertEquals(2, requests.size)
    }

    @Test fun `arrival announces, publishes Arrived and releases the GPS`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.totalGeometryM)); runCurrent()
        assertEquals(NavigationState.Arrived(route), s.state.value)
        assertFalse(location.guidanceActive)
        assertEquals("Votre destination est sur la droite.", speech.spoken.last())
    }

    @Test fun `GPS loss extrapolates after 3 s and shows the badge after 10 s`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(100.0, speedMps = 10f)); runCurrent()
        advanceTimeBy(4_000); runCurrent()
        assertEquals(140.0, guiding(s).progress.distanceAlongM, 0.5)
        assertFalse(guiding(s).gpsLost)
        advanceTimeBy(7_000); runCurrent()
        assertEquals(210.0, guiding(s).progress.distanceAlongM, 0.5)
        assertTrue(guiding(s).gpsLost)
    }

    @Test fun `muted session says nothing`() = runTest {
        val s = session()
        s.setMuted(true)
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 70.0)); runCurrent()
        assertTrue(speech.spoken.isEmpty())
        assertTrue(s.muted.value)
    }

    @Test fun `disabled voice says nothing`() = runTest {
        prefs.setVoiceEnabled(false)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 70.0)); runCurrent()
        assertTrue(speech.spoken.isEmpty())
    }

    @Test fun `volume preference is applied`() = runTest {
        prefs.setVoiceVolume(35)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        assertEquals(35, speech.volume)
    }

    @Test fun `over speed beeps once when the sound option is on`() = runTest {
        limit = 50
        prefs.setSpeedAlertSound(true)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(3) { location.emit(fixAt(100.0 + it * 20, speedMps = 20f)); runCurrent() } // 72 km/h > 50 + 5
        assertEquals(1, speech.beeps)
        assertEquals(50, guiding(s).speedLimitKmh)
    }

    @Test fun `over speed is silent when the sound option is off`() = runTest {
        limit = 50
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(3) { location.emit(fixAt(100.0 + it * 20, speedMps = 20f)); runCurrent() }
        assertEquals(0, speech.beeps)
    }

    @Test fun `unknown limit never beeps`() = runTest {
        prefs.setSpeedAlertSound(true)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        repeat(3) { location.emit(fixAt(100.0 + it * 20, speedMps = 40f)); runCurrent() }
        assertEquals(0, speech.beeps)
        assertEquals(null, guiding(s).speedLimitKmh)
    }

    @Test fun `stop goes Idle and releases everything`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        s.stop()
        assertEquals(NavigationState.Idle, s.state.value)
        assertFalse(location.guidanceActive)
        assertTrue(speech.stops >= 1)
        location.emit(fixAt(300.0)); runCurrent()
        assertEquals("plus aucune publication après l'arrêt", NavigationState.Idle, s.state.value)
    }

    @Test fun `retry restarts the last option`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        s.stop()
        s.retry()
        assertEquals(route.id, guiding(s).route.id)
    }

    @Test fun `without location permission the session fails`() = runTest {
        location.permission.value = LocationPermissionState.DENIED
        val s = session()
        s.start(option, RoutePreferences())
        assertEquals(NavigationState.Failed(AppError.PermissionDenied), s.state.value)
        assertEquals(0, service.starts)
        assertFalse(location.guidanceActive)
    }

    @Test fun `a route without maneuvers fails cleanly`() = runTest {
        val s = session()
        s.start(option.copy(route = route.copy(maneuvers = emptyList())), RoutePreferences())
        assertEquals(NavigationState.Failed(AppError.NoResult), s.state.value)
    }

    @Test fun `starting a new route replaces the current one`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        val other = TestRoutes.straight("other")
        s.start(option.copy(route = other), RoutePreferences()); runCurrent()
        assertEquals("other", guiding(s).route.id)
        location.emit(fixAt(300.0)); runCurrent()
        assertEquals("other", guiding(s).route.id)
        assertEquals(2, service.starts)
    }
}
```

Note sur `GPS loss…` : le tic de surveillance tombe aux instants 1 000, 2 000, … ms ; à 4 000 ms, 4 000 > 3 000 → avance de 10 m/s × 4 s = 40 m ; à 11 000 ms → 110 m au total depuis le fix (100 + 110 = 210 m), 11 000 > 10 000 → badge.

- [ ] **Step 2 : vérifier l'échec**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'NavigationSession'`.

- [ ] **Step 3 : `NavigationSession.kt`**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Session de guidage unique de l'application (spec §2, §4). Logique pure Kotlin testée sur JVM ;
 * la voix, le service Android, la limitation et l'horloge sont injectés derrière des interfaces.
 * Toute une course s'exécute sur un dispatcher à parallélisme 1 : aucun verrou n'est nécessaire.
 */
@Singleton
class NavigationSession @Inject constructor(
    private val location: LocationRepository,
    private val routing: RoutingProvider,
    private val speedLimits: SpeedLimitProvider,
    private val speech: SpeechService,
    private val prefs: UserPreferencesRepository,
    private val serviceController: NavigationServiceController,
    private val clock: NavigationClock,
    dispatchers: DispatcherProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val confined = dispatchers.default.limitedParallelism(1)
    private var job: Job? = null
    @Volatile private var generation = 0
    private var lastOption: RouteOption? = null
    private var lastPreferences = RoutePreferences()

    fun start(option: RouteOption, preferences: RoutePreferences) {
        cancelRun()
        lastOption = option
        lastPreferences = preferences
        if (location.permissionState.value != LocationPermissionState.GRANTED) {
            _state.value = NavigationState.Failed(AppError.PermissionDenied)
            return
        }
        val tracker = try {
            RouteTracker(option.route)
        } catch (e: IllegalArgumentException) {
            _state.value = NavigationState.Failed(AppError.NoResult)
            return
        }
        val run = Run(++generation, option.route, tracker, preferences)
        _state.value = run.guiding(tracker.initialProgress(), clock.nowMs(), gpsLost = false)
        location.setGuidanceActive(true)
        serviceController.start()
        job = scope.launch(confined) { run.execute() }
    }

    fun stop() {
        cancelRun()
        _state.value = NavigationState.Idle
    }

    fun retry() {
        lastOption?.let { start(it, lastPreferences) }
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        if (muted) speech.stop()
    }

    private fun cancelRun() {
        generation++
        job?.cancel()
        job = null
        location.setGuidanceActive(false)
        speech.stop()
    }

    /** Une course : son état n'est lu et écrit que sur [confined]. */
    private inner class Run(val gen: Int, var route: Route, var tracker: RouteTracker, val routePreferences: RoutePreferences) {
        private val destination: GeoPoint = route.geometry.last()
        private val costing = route.costing
        private val announced = mutableSetOf<Pair<Int, Tier>>()
        private val smoother = SpeedSmoother()
        private val recent = ArrayDeque<LocationFix>()
        private var speedKmh: Double? = null
        private var speedLimitKmh: Int? = null
        private var lastFixAtMs = clock.nowMs()
        private var lastAdvanceAtMs = lastFixAtMs
        private var lastRerouteAtMs: Long? = null
        private var rerouting = false
        private var overSpeedAlerted = false
        private var limitJob: Job? = null
        private lateinit var runScope: CoroutineScope

        suspend fun execute() = coroutineScope {
            runScope = this
            speech.setVolume(prefs.voiceVolume.first())
            launch { prefs.voiceVolume.collect { speech.setVolume(it) } }
            val depart = route.maneuvers.first()
            announced += 0 to Tier.NOW
            say(depart.verbalPre ?: depart.instruction)
            launch { watchdog() }
            location.fixes.collect { onFix(it) }
        }

        private suspend fun onFix(fix: LocationFix) {
            val now = clock.nowMs()
            lastFixAtMs = now
            lastAdvanceAtMs = now
            recent.addLast(fix)
            while (recent.size > GuidanceConstants.RECENT_FIXES) recent.removeFirst()
            speedKmh = smoother.update(fix.speedMps?.let { it * 3.6 })
            val progress = tracker.update(fix)
            if (tracker.hasArrived) { arrive(); return }
            if (tracker.isOffRoute) maybeReroute(fix, now)
            announce(progress)
            refreshSpeedLimit()
            checkOverSpeed()
            publish(guiding(progress, now, gpsLost = false))
        }

        private suspend fun announce(progress: RouteProgress) {
            if (rerouting) return
            val a = AnnouncementPolicy.next(progress, route.maneuvers, speedKmh, announced) ?: return
            announced += a.maneuverIndex to a.tier
            say(a.text)
        }

        private suspend fun arrive() {
            val last = route.maneuvers.lastIndex
            if ((last to Tier.NOW) !in announced) {
                announced += last to Tier.NOW
                val m = route.maneuvers[last]
                say(m.verbalPre ?: m.instruction)
            }
            publish(NavigationState.Arrived(route))
            if (gen == generation) location.setGuidanceActive(false)
            runScope.cancel()
        }

        private suspend fun maybeReroute(fix: LocationFix, now: Long) {
            if (rerouting) return
            val last = lastRerouteAtMs
            if (last != null && now - last < GuidanceConstants.REROUTE_MIN_INTERVAL_MS) return
            lastRerouteAtMs = now
            rerouting = true
            say(SpokenPhrases.REROUTING)
            runScope.launch {
                val result = try {
                    routing.route(RouteRequest(origin = fix.point, destination = destination, costing = costing, preferences = routePreferences))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Outcome.Failure(AppError.Unknown(e.message))
                }
                val fresh = (result as? Outcome.Success)?.value?.firstOrNull()?.copy(costing = costing)
                val freshTracker = fresh?.let { r -> try { RouteTracker(r) } catch (e: IllegalArgumentException) { null } }
                if (fresh != null && freshTracker != null) {
                    route = fresh
                    tracker = freshTracker
                    announced.clear()
                    announced += 0 to Tier.NOW
                    freshTracker.update(fix)
                }
                // Échec : on continue sur l'ancien tracé ; un nouveau recalcul sera tenté après 10 s.
                rerouting = false
                publish(guiding(tracker.lastProgress ?: tracker.initialProgress(), clock.nowMs(), gpsLost = false))
            }
        }

        private suspend fun watchdog() {
            while (true) {
                delay(GuidanceConstants.WATCHDOG_TICK_MS)
                val now = clock.nowMs()
                val silentMs = now - lastFixAtMs
                if (silentMs <= GuidanceConstants.GPS_EXTRAPOLATE_AFTER_MS) continue
                val mps = (speedKmh ?: 0.0) / 3.6
                val progress = tracker.extrapolate(mps * (now - lastAdvanceAtMs) / 1000.0)
                lastAdvanceAtMs = now
                announce(progress)
                publish(guiding(progress, now, gpsLost = silentMs > GuidanceConstants.GPS_LOST_BADGE_AFTER_MS))
            }
        }

        private fun refreshSpeedLimit() {
            if (limitJob?.isActive == true) return
            val snapshot = recent.toList()
            limitJob = runScope.launch {
                speedLimitKmh = when (val r = speedLimits.speedLimitKmh(snapshot)) {
                    is Outcome.Success -> r.value
                    is Outcome.Failure -> null
                }
            }
        }

        private suspend fun checkOverSpeed() {
            val limit = speedLimitKmh
            val v = speedKmh
            val over = limit != null && v != null && v > limit + prefs.speedAlertToleranceKmh.first()
            if (over && !overSpeedAlerted) {
                overSpeedAlerted = true
                if (prefs.speedAlertSound.first() && !_muted.value) speech.beep()
            } else if (!over) {
                overSpeedAlerted = false
            }
        }

        private suspend fun say(text: String) {
            if (text.isBlank() || _muted.value || !prefs.voiceEnabled.first()) return
            speech.speak(text)
        }

        private fun publish(s: NavigationState) {
            if (gen == generation) _state.value = s
        }

        fun guiding(progress: RouteProgress, now: Long, gpsLost: Boolean): NavigationState.Guiding {
            val ms = route.maneuvers
            val next = progress.nextManeuverIndex.coerceIn(0, ms.lastIndex)
            return NavigationState.Guiding(
                route = route,
                progress = progress,
                speedKmh = speedKmh,
                speedLimitKmh = speedLimitKmh,
                upcoming = ms.subList(min(next + 1, ms.size), min(next + 1 + GuidanceConstants.UPCOMING_COUNT, ms.size)),
                lanes = ms[next].lanes,
                etaEpochMs = now + (progress.remainingDurationS * 1000).roundToLong(),
                gpsLost = gpsLost,
                rerouting = rerouting,
            )
        }
    }
}
```

- [ ] **Step 4 : vérifier le succès des tests de session**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` ; `NavigationSessionTest` (18) vert ; tests des Tasks 5 et 6 toujours verts.

- [ ] **Step 5 : ressources du service**

`core/navigation/src/main/res/values/strings.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="nav_channel_name">Guidage</string>
    <string name="nav_channel_description">Instruction suivante, distance et heure d\'arrivée pendant le guidage</string>
    <string name="nav_notif_title_default">Guidage Roadly</string>
    <string name="nav_notif_rerouting">Recalcul de l\'itinéraire…</string>
    <string name="nav_notif_text">%1$s · arrivée %2$s</string>
    <string name="nav_notif_stop">Arrêter</string>
</resources>
```

`core/navigation/src/main/res/drawable/ic_stat_navigation.xml` :

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2L4.5,20.29l0.71,0.71L12,18l6.79,3 0.71,-0.71z" />
</vector>
```

- [ ] **Step 6 : `NavigationNotifications.kt`**

```kotlin
package fr.roadly.core.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.roadly.core.common.Formatters

/** Canal, contenu et actions de la notification persistante du guidage. */
internal object NavigationNotifications {
    const val CHANNEL_ID = "roadly.navigation"
    const val NOTIFICATION_ID = 4201

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // IMPORTANCE_LOW : visible et persistante, sans son (la voix du guidage suffit).
        val channel = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.nav_channel_name), NotificationManager.IMPORTANCE_LOW)
        channel.description = ctx.getString(R.string.nav_channel_description)
        nm.createNotificationChannel(channel)
    }

    /** (titre, texte) affichés ; sert aussi à ne notifier que lorsqu'ils changent. */
    fun content(ctx: Context, state: NavigationState.Guiding?): Pair<String, String> {
        if (state == null) return ctx.getString(R.string.nav_notif_title_default) to ""
        if (state.rerouting) return ctx.getString(R.string.nav_notif_rerouting) to ""
        val m = state.route.maneuvers[state.progress.nextManeuverIndex.coerceIn(0, state.route.maneuvers.lastIndex)]
        val text = ctx.getString(R.string.nav_notif_text, Formatters.distance(state.progress.distanceToNextManeuverM), Formatters.clockTime(state.etaEpochMs))
        return m.instruction to text
    }

    fun build(ctx: Context, content: Pair<String, String>): Notification {
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            action = NavigationService.ACTION_OPEN_NAVIGATION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = open?.let { PendingIntent.getActivity(ctx, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val stopPending = PendingIntent.getService(
            ctx, 2, Intent(ctx, NavigationService::class.java).setAction(NavigationService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_navigation)
            .setContentTitle(content.first)
            .setContentText(content.second)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openPending)
            .addAction(0, ctx.getString(R.string.nav_notif_stop), stopPending)
            .build()
    }

    /** API 33+ : sans POST_NOTIFICATIONS, la notification n'est pas affichée (le service tourne quand même). */
    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    fun notify(ctx: Context, notification: Notification) {
        if (canNotify(ctx)) NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
    }
}
```

- [ ] **Step 7 : `NavigationService.kt`**

```kotlin
package fr.roadly.core.navigation

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service de premier plan de type `location`, actif uniquement pendant le guidage (spec §2, §8) :
 * notification persistante (manœuvre, distance, arrivée, « Arrêter »), arrêt automatique hors `Guiding`.
 */
@AndroidEntryPoint
class NavigationService : Service() {
    @Inject lateinit var session: NavigationSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var lastContent: Pair<String, String>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NavigationNotifications.ensureChannel(this)
        val content = NavigationNotifications.content(this, session.state.value as? NavigationState.Guiding)
        // startForeground dans les 5 s suivant startForegroundService : immédiatement, avant toute autre chose.
        val foreground = try {
            ServiceCompat.startForeground(this, NavigationNotifications.NOTIFICATION_ID, NavigationNotifications.build(this, content), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            lastContent = content
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Service de premier plan refusé (permission de localisation ?)", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Service de premier plan refusé (démarrage en arrière-plan ?)", e)
            false
        }
        if (intent?.action == ACTION_STOP) session.stop()
        if (!foreground || intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (collector == null) collector = scope.launch { session.state.collect { render(it) } }
        return START_NOT_STICKY
    }

    private fun render(state: NavigationState) {
        if (state !is NavigationState.Guiding) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val content = NavigationNotifications.content(this, state)
        if (content == lastContent) return
        lastContent = content
        NavigationNotifications.notify(this, NavigationNotifications.build(this, content))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "fr.roadly.action.STOP_NAVIGATION"
        const val ACTION_OPEN_NAVIGATION = "fr.roadly.action.OPEN_NAVIGATION"
        private const val TAG = "RoadlyNavService"
    }
}
```

- [ ] **Step 8 : `ForegroundServiceController.kt` et `NavigationModule.kt`**

```kotlin
package fr.roadly.core.navigation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ForegroundServiceController @Inject constructor(@param:ApplicationContext private val ctx: Context) : NavigationServiceController {
    override fun start() {
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, NavigationService::class.java))
        } catch (e: IllegalStateException) {
            // Démarrage refusé hors premier plan : le guidage continue écran allumé, sans notification.
            Log.w("RoadlyNavService", "startForegroundService refusé", e)
        } catch (e: SecurityException) {
            Log.w("RoadlyNavService", "startForegroundService refusé", e)
        }
    }
}
```

```kotlin
package fr.roadly.core.navigation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NavigationModule {
    /** S'auto-désactive sur l'instance publique FOSSGIS (limitation « — »). */
    @Binds abstract fun speedLimits(impl: ValhallaTraceSpeedLimitProvider): SpeedLimitProvider
    @Binds abstract fun speech(impl: AndroidSpeechService): SpeechService
    @Binds abstract fun serviceController(impl: ForegroundServiceController): NavigationServiceController
    @Binds abstract fun clock(impl: SystemNavigationClock): NavigationClock
}
```

- [ ] **Step 9 : vérifier compilation Android + tests**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest :core:navigation:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL` ; 63 tests du module verts (16 + 5 + 14 + 2 + 1 + 7 + 18) ; KSP/Hilt génère `NavigationService_GeneratedInjector` sans erreur.

- [ ] **Step 10 : commit**

```powershell
git add core/navigation
git commit -m "feat(navigation): NavigationSession (suivi, annonces, recalcul, perte GPS, dépassement), service de premier plan et notification" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 8 : `core:map` et `core:designsystem` — tracé parcouru atténué, tolérance du badge de vitesse

**Files:**
- Modify: `core/map/src/main/java/fr/roadly/core/map/MapLayers.kt`
- Modify: `core/map/src/main/java/fr/roadly/core/map/MapController.kt`
- Modify: `core/designsystem/src/main/java/fr/roadly/core/designsystem/component/SpeedBadge.kt`
- Test: `core/map/src/test/java/fr/roadly/core/map/TraversedFeatureBuilderTest.kt`

**Interfaces:**
- Produces: `TraversedFeatureBuilder.EMPTY: String`, `TraversedFeatureBuilder.build(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): String`, `MapController.setTraversed(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?)`, `MapController.clearTraversed()`, `SpeedBadge(speedKmh: Double?, limitKmh: Int?, modifier: Modifier = Modifier, toleranceKmh: Int = 5)`.

Choix MapLibre 11.13.5 : une **seconde source GeoJSON** (`roadly-traversed`) et une couche `LineLayer` posée au-dessus de la couche active, couleur `routeInactive`, opacité 0,9. Uniquement des API déjà employées par la v1 (`GeoJsonSource.setGeoJson(String)`, `LineLayer`, `PropertyFactory`) ; pas de `line-gradient`/`line-trim-offset` (dépend de `lineMetrics`, non utilisé ailleurs).

- [ ] **Step 1 : test (échoue)**

```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TraversedFeatureBuilderTest {
    private val g = listOf(GeoPoint(48.0, 2.0), GeoPoint(48.1, 2.1), GeoPoint(48.2, 2.2))
    private fun coords(json: String) = Json.parseToJsonElement(json).jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray

    @Test fun `line runs from the start to the snapped position`() {
        val c = coords(TraversedFeatureBuilder.build(g, 1, GeoPoint(48.15, 2.15)))
        assertEquals(3, c.size)
        assertEquals("2.0", c[0].jsonArray[0].jsonPrimitive.content)       // [lon, lat]
        assertEquals("2.15", c[2].jsonArray[0].jsonPrimitive.content)
        assertEquals("48.15", c[2].jsonArray[1].jsonPrimitive.content)
    }

    @Test fun `nothing traversed gives an empty collection`() {
        assertEquals(TraversedFeatureBuilder.EMPTY, TraversedFeatureBuilder.build(g, -1, null))
        assertEquals(TraversedFeatureBuilder.EMPTY, TraversedFeatureBuilder.build(g, 0, null))
        assertEquals(TraversedFeatureBuilder.EMPTY, TraversedFeatureBuilder.build(emptyList(), 3, GeoPoint(48.0, 2.0)))
    }

    @Test fun `index beyond the geometry is clamped`() = assertEquals(3, coords(TraversedFeatureBuilder.build(g, 10, null)).size)
}
```

- [ ] **Step 2 : vérifier l'échec**

Run: `.\gradlew.bat :core:map:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'TraversedFeatureBuilder'`.

- [ ] **Step 3 : `MapLayers.kt`**

Ajouter l'import `import fr.roadly.core.model.GeoPoint`. Dans `MapIds`, après `const val SRC_MARKERS = "roadly-markers"` ajouter :

```kotlin
    const val SRC_TRAVERSED = "roadly-traversed"
    const val LYR_ROUTE_TRAVERSED = "roadly-route-traversed"
```

Ajouter en fin de fichier :

```kotlin
/** Portion déjà parcourue de l'itinéraire actif (écran de guidage) : p_0 … p_segmentIndex puis la position projetée. */
object TraversedFeatureBuilder {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    fun build(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): String {
        if (geometry.isEmpty() || segmentIndex < 0) return EMPTY
        val pts = geometry.subList(0, (segmentIndex + 1).coerceAtMost(geometry.size)) + listOfNotNull(snapped)
        if (pts.size < 2) return EMPTY
        return buildJsonObject {
            put("type", "Feature")
            putJsonObject("properties") {}
            putJsonObject("geometry") {
                put("type", "LineString")
                put("coordinates", JsonArray(pts.map { p -> JsonArray(listOf(JsonPrimitive(p.lon), JsonPrimitive(p.lat))) }))
            }
        }.toString()
    }
}
```

- [ ] **Step 4 : `MapController.kt`**

Ajouter le champ après `private var lastMarkers: List<Place> = emptyList()` :

```kotlin
    private var lastTraversed: String = TraversedFeatureBuilder.EMPTY
```

Dans `installLayers`, remplacer :

```kotlin
        style.addSource(GeoJsonSource(MapIds.SRC_ROUTES))
```

par :

```kotlin
        style.addSource(GeoJsonSource(MapIds.SRC_ROUTES))
        style.addSource(GeoJsonSource(MapIds.SRC_TRAVERSED))
```

puis remplacer :

```kotlin
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_ACTIVE, MapIds.SRC_ROUTES).withProperties(
            lineColor(routeColorActive), lineWidth(6f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("active"), literal(true))))
```

par :

```kotlin
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_ACTIVE, MapIds.SRC_ROUTES).withProperties(
            lineColor(routeColorActive), lineWidth(6f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("active"), literal(true))))
        // Tronçon déjà parcouru, dessiné par-dessus le tracé actif (spec §5 : « tracé parcouru atténué »).
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_TRAVERSED, MapIds.SRC_TRAVERSED).withProperties(
            lineColor(routeColorInactive), lineWidth(6.5f), lineOpacity(0.9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
```

Dans `replayState`, remplacer :

```kotlin
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_ROUTES)?.setGeoJson(RouteFeatureBuilder.build(lastRoutes, lastActiveRouteId))
```

par :

```kotlin
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_ROUTES)?.setGeoJson(RouteFeatureBuilder.build(lastRoutes, lastActiveRouteId))
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(lastTraversed)
```

et :

```kotlin
        (style.getLayer(MapIds.LYR_ROUTE_INACTIVE) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
```

par :

```kotlin
        (style.getLayer(MapIds.LYR_ROUTE_INACTIVE) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
        (style.getLayer(MapIds.LYR_ROUTE_TRAVERSED) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
```

Ajouter après `setMarkers(...)` :

```kotlin
    /** Atténue la portion parcourue de l'itinéraire actif (écran de guidage). */
    fun setTraversed(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?) {
        lastTraversed = TraversedFeatureBuilder.build(geometry, segmentIndex, snapped)
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(lastTraversed)
    }

    fun clearTraversed() = setTraversed(emptyList(), -1, null)
```

- [ ] **Step 5 : `SpeedBadge.kt` — tolérance réglable**

Remplacer :

```kotlin
/** [speedKmh] null = pas de fix GPS. [limitKmh] null = limitation inconnue (affiche « — »). */
@Composable
fun SpeedBadge(speedKmh: Double?, limitKmh: Int?, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val over = speedKmh != null && limitKmh != null && speedKmh > limitKmh + 5
```

par :

```kotlin
/** [speedKmh] null = pas de fix GPS. [limitKmh] null = limitation inconnue (affiche « — »). Rouge au-delà de limite + [toleranceKmh]. */
@Composable
fun SpeedBadge(speedKmh: Double?, limitKmh: Int?, modifier: Modifier = Modifier, toleranceKmh: Int = 5) {
    val c = LocalRoadlyColors.current
    val over = speedKmh != null && limitKmh != null && speedKmh > limitKmh + toleranceKmh
```

- [ ] **Step 6 : vérifier**

Run: `.\gradlew.bat :core:map:testDebugUnitTest :core:designsystem:compileDebugKotlin :feature:home:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL` ; `TraversedFeatureBuilderTest` (3) et `RouteFeatureBuilderTest` verts ; `feature:home` compile sans modification (paramètre par défaut).

- [ ] **Step 7 : commit**

```powershell
git add core/map core/designsystem
git commit -m "feat(map): couche du tracé parcouru ; badge de vitesse à tolérance réglable" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9 : `feature:navigation` — écran de guidage en mode conduite

**Files:**
- Modify: `settings.gradle.kts`
- Create: `feature/navigation/build.gradle.kts`, `feature/navigation/src/main/AndroidManifest.xml`, `feature/navigation/src/main/res/values/strings.xml`
- Create: `feature/navigation/src/main/java/fr/roadly/feature/navigation/{NavigationNavigation.kt, NavigationUiState.kt, NavigationViewModel.kt, NavigationScreen.kt, ManeuverBanner.kt, LaneStrip.kt, UpcomingStrip.kt, EtaPanel.kt, DrivingHud.kt}`
- Test: `feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt`

**Interfaces:**
- Consumes: `NavigationSession.{state, muted, stop, retry, setMuted}`, `NavigationState`, `RouteTracker` + `TestRoutes` + `FakeSpeechService` + `FakeNavigationServiceController` (tests), `UserPreferencesRepository.speedAlertToleranceKmh`, `ProviderConfigRepository.config`, `Formatters`, `MapController.{setRoutes, setMarkers, setUserLocation, setTraversed, clearTraversed, applyCameraMode, setRouteColors}`, `RoadlyMap(onUserGesture)`, `SpeedBadge(toleranceKmh)`, `ManeuverIcon`, `GlassPanel`, `GlassButton`, `GlassIconButton`, `RoadlyTheme(drivingMode = true)`, `NavDigits`, `userMessage()`.
- Produces: `@Serializable object NavigationRoute`, `NavGraphBuilder.navigationScreen(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit)`, `NavigationViewModel.RECENTER_AFTER_MS = 8_000L`.

- [ ] **Step 1 : déclarer le module**

Dans `settings.gradle.kts`, remplacer :

```kotlin
    ":feature:home", ":feature:search", ":feature:route", ":feature:settings",
```

par :

```kotlin
    ":feature:home", ":feature:search", ":feature:route", ":feature:settings", ":feature:navigation",
```

`feature/navigation/build.gradle.kts` :

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "fr.roadly.feature.navigation"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":core:routing"))
    implementation(project(":core:map"))
    implementation(project(":core:navigation"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
```

`feature/navigation/src/main/AndroidManifest.xml` :

```xml
<manifest/>
```

`feature/navigation/src/main/res/values/strings.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="nav_loading">Préparation du guidage…</string>
    <string name="nav_rerouting">Recalcul…</string>
    <string name="nav_gps_lost">GPS perdu</string>
    <string name="nav_eta">Arrivée %1$s</string>
    <string name="nav_remaining">%1$s · %2$s</string>
    <string name="nav_stop">Arrêter</string>
    <string name="nav_mute">Couper la voix</string>
    <string name="nav_unmute">Réactiver la voix</string>
    <string name="nav_recenter">Recentrer</string>
    <string name="nav_change_destination">Changer de destination</string>
    <string name="nav_exit_number">Sortie %1$s</string>
    <string name="nav_roundabout_exit_first">1re sortie</string>
    <string name="nav_roundabout_exit_n">%1$de sortie</string>
    <string name="nav_lanes">Voies conseillées</string>
    <string name="nav_arrived_title">Vous êtes arrivé</string>
    <string name="nav_finish">Terminer</string>
    <string name="nav_failed_title">Guidage impossible</string>
    <string name="nav_retry">Réessayer</string>
</resources>
```

- [ ] **Step 2 : état d'écran et route (requis par les tests)**

`NavigationNavigation.kt` :

```kotlin
package fr.roadly.feature.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** L'itinéraire est porté par NavigationSession, pas par un argument de navigation (spec §3). */
@Serializable object NavigationRoute

fun NavGraphBuilder.navigationScreen(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit) {
    composable<NavigationRoute> { NavigationScreen(isDark = isDark, onExit = onExit, onChangeDestination = onChangeDestination) }
}
```

`NavigationUiState.kt` :

```kotlin
package fr.roadly.feature.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Formatters
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.model.Lane
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.navigation.NavigationState
import java.time.ZoneId

/** LOADING = avant la première émission ; IDLE = guidage terminé → l'écran se ferme. */
enum class NavigationPhase { LOADING, GUIDING, ARRIVED, FAILED, IDLE }

data class BannerUi(
    val type: ManeuverType,
    val distanceText: String,
    val instruction: String,
    val street: String?,
    val exitNumber: String?,
    val roundaboutExit: Int?,
)

data class UpcomingUi(val type: ManeuverType, val distanceText: String, val street: String?)

data class NavigationUiState(
    val phase: NavigationPhase = NavigationPhase.LOADING,
    val styleUrl: String = "",
    val route: Route? = null,
    val progress: RouteProgress? = null,
    val banner: BannerUi? = null,
    val lanes: List<Lane> = emptyList(),
    val upcoming: List<UpcomingUi> = emptyList(),
    val etaClock: String = "",
    val remainingDuration: String = "",
    val remainingDistance: String = "",
    val speedKmh: Double? = null,
    val speedLimitKmh: Int? = null,
    val toleranceKmh: Int = GuidanceDefaults.SPEED_TOLERANCE_KMH,
    val gpsLost: Boolean = false,
    val rerouting: Boolean = false,
    val muted: Boolean = false,
    val following: Boolean = true,
    val error: AppError? = null,
)

/** Formatage pur (testé via le ViewModel) : distances et durées par `Formatters`, comme la v1. */
internal fun buildUiState(s: NavigationState, muted: Boolean, following: Boolean, toleranceKmh: Int, styleUrl: String, zone: ZoneId): NavigationUiState {
    val base = NavigationUiState(styleUrl = styleUrl, muted = muted, following = following, toleranceKmh = toleranceKmh)
    return when (s) {
        NavigationState.Idle -> base.copy(phase = NavigationPhase.IDLE)
        is NavigationState.Failed -> base.copy(phase = NavigationPhase.FAILED, error = s.error)
        is NavigationState.Arrived -> base.copy(phase = NavigationPhase.ARRIVED, route = s.route)
        is NavigationState.Guiding -> {
            val p = s.progress
            val ms = s.route.maneuvers
            val next = p.nextManeuverIndex.coerceIn(0, ms.lastIndex)
            val m = ms[next]
            // Distance jusqu'à la manœuvre next+1+i = distance à next + longueurs Valhalla de next … next+i.
            var acc = p.distanceToNextManeuverM
            val upcoming = s.upcoming.mapIndexed { i, u ->
                acc += ms[next + i].lengthMeters
                UpcomingUi(u.type, Formatters.distance(acc), u.streetName)
            }
            base.copy(
                phase = NavigationPhase.GUIDING,
                route = s.route,
                progress = p,
                banner = BannerUi(m.type, Formatters.distance(p.distanceToNextManeuverM), m.instruction, m.streetName, m.exitNumber, m.roundaboutExit),
                lanes = s.lanes,
                upcoming = upcoming,
                etaClock = Formatters.clockTime(s.etaEpochMs, zone),
                remainingDuration = Formatters.duration(p.remainingDurationS),
                remainingDistance = Formatters.distance(p.remainingDistanceM),
                speedKmh = s.speedKmh,
                speedLimitKmh = s.speedLimitKmh,
                gpsLost = s.gpsLost,
                rerouting = s.rerouting,
            )
        }
    }
}
```

- [ ] **Step 3 : tests du ViewModel (échouent)**

```kotlin
package fr.roadly.feature.navigation

import app.cash.turbine.test
import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationViewModelTest {
    private val main = StandardTestDispatcher()
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val config = object : ProviderConfigRepository {
        override val config = flowOf(ProviderConfig.DEFAULT)
        override suspend fun override(config: ProviderConfig) = Unit
        override suspend fun reset() = Unit
    }
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)

    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, dispatchers, backgroundScope)
    }

    private fun fixAt(along: Double) = LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L)

    @Test fun `guiding state is formatted for the banner, the upcoming strip and the ETA`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            location.emit(fixAt(300.0)); runCurrent()
            val ui = vm.uiState.value
            val g = s.state.value as NavigationState.Guiding
            assertEquals(NavigationPhase.GUIDING, ui.phase)
            assertEquals(ManeuverType.TURN_RIGHT, ui.banner!!.type)
            assertEquals(Formatters.distance(g.progress.distanceToNextManeuverM), ui.banner!!.distanceText)
            assertEquals(route.maneuvers[1].instruction, ui.banner!!.instruction)
            assertEquals("Rue d'Essai", ui.banner!!.street)
            assertEquals(1, ui.upcoming.size)
            assertEquals(ManeuverType.ARRIVE, ui.upcoming[0].type)
            assertEquals(Formatters.distance(g.progress.distanceToNextManeuverM + route.maneuvers[1].lengthMeters), ui.upcoming[0].distanceText)
            assertEquals(Formatters.clockTime(g.etaEpochMs), ui.etaClock)
            assertEquals(Formatters.duration(g.progress.remainingDurationS), ui.remainingDuration)
            assertEquals(Formatters.distance(g.progress.remainingDistanceM), ui.remainingDistance)
            assertEquals(36.0, ui.speedKmh!!, 1e-9)
            assertNull("limitation inconnue sur l'instance publique", ui.speedLimitKmh)
            assertEquals(5, ui.toleranceKmh)
            assertEquals(ProviderConfig.DEFAULT.mapStyleDarkUrl, ui.styleUrl)
            assertTrue(ui.following)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `lanes and exit number of the next maneuver reach the screen`() = runTest {
        val withLanes = route.copy(maneuvers = route.maneuvers.mapIndexed { i, m ->
            if (i == 1) m.copy(lanes = listOf(Lane(setOf(LaneDirection.STRAIGHT), false), Lane(setOf(LaneDirection.RIGHT), true)), exitNumber = "12") else m
        })
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option.copy(route = withLanes), RoutePreferences()); runCurrent()
            location.emit(fixAt(300.0)); runCurrent()
            assertEquals(2, vm.uiState.value.lanes.size)
            assertTrue(vm.uiState.value.lanes[1].valid)
            assertEquals("12", vm.uiState.value.banner!!.exitNumber)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `mute toggles the session and stop ends in IDLE`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            vm.toggleMute(); runCurrent()
            assertTrue(s.muted.value)
            assertTrue(vm.uiState.value.muted)
            vm.toggleMute(); runCurrent()
            assertFalse(vm.uiState.value.muted)
            vm.stop(); runCurrent()
            assertEquals(NavigationPhase.IDLE, vm.uiState.value.phase)
            assertEquals(NavigationState.Idle, s.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `a map gesture suspends follow for 8 s and recenter restores it at once`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            vm.onUserGesture(); runCurrent()
            assertFalse(vm.uiState.value.following)
            advanceTimeBy(NavigationViewModel.RECENTER_AFTER_MS - 1); runCurrent()
            assertFalse(vm.uiState.value.following)
            advanceTimeBy(1); runCurrent()
            assertTrue(vm.uiState.value.following)
            vm.onUserGesture(); runCurrent()
            vm.recenter(); runCurrent()
            assertTrue(vm.uiState.value.following)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `arrival is exposed`() = runTest {
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            location.emit(fixAt(tracker.totalGeometryM)); runCurrent()
            assertEquals(NavigationPhase.ARRIVED, vm.uiState.value.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `failure is exposed and retry restarts guidance`() = runTest {
        location.permission.value = LocationPermissionState.DENIED
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            assertEquals(NavigationPhase.FAILED, vm.uiState.value.phase)
            assertEquals(AppError.PermissionDenied, vm.uiState.value.error)
            location.permission.value = LocationPermissionState.GRANTED
            vm.retry(); runCurrent()
            assertEquals(NavigationPhase.GUIDING, vm.uiState.value.phase)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `tolerance preference is exposed for the badge`() = runTest {
        prefs.setSpeedAlertToleranceKmh(10)
        val s = session()
        val vm = NavigationViewModel(s, prefs, config)
        vm.uiState.test {
            s.start(option, RoutePreferences()); runCurrent()
            assertEquals(10, vm.uiState.value.toleranceKmh)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 4 : vérifier l'échec**

Run: `.\gradlew.bat :feature:navigation:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Unresolved reference 'NavigationViewModel'` (et `NavigationScreen` référencé par `NavigationNavigation.kt`).

- [ ] **Step 5 : `NavigationViewModel.kt`**

```kotlin
package fr.roadly.feature.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.navigation.NavigationSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val session: NavigationSession,
    prefs: UserPreferencesRepository,
    config: ProviderConfigRepository,
) : ViewModel() {
    private val isDark = MutableStateFlow(true)
    private val following = MutableStateFlow(true)
    private var refollowJob: Job? = null
    private val styleUrl = combine(isDark, config.config) { dark, cfg -> if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl }

    val uiState: StateFlow<NavigationUiState> = combine(session.state, session.muted, following, prefs.speedAlertToleranceKmh, styleUrl) { s, muted, follow, tolerance, style ->
        buildUiState(s, muted = muted, following = follow, toleranceKmh = tolerance, styleUrl = style, zone = ZoneId.systemDefault())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationUiState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun toggleMute() = session.setMuted(!session.muted.value)
    fun stop() = session.stop()
    fun retry() = session.retry()

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

    companion object { const val RECENTER_AFTER_MS = 8_000L }
}
```

- [ ] **Step 6 : composants**

`ManeuverBanner.kt` :

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.ManeuverIcon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.NavDigits
import fr.roadly.core.designsystem.theme.RoadlyShapes
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Bandeau de manœuvre (verre, 96 dp) : icône 40 dp, distance en NavDigits, instruction, rue, pastille de sortie. */
@Composable
fun ManeuverBanner(banner: BannerUi, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.fillMaxWidth().heightIn(min = 96.dp), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ManeuverIcon(banner.type, Modifier.size(40.dp), tint = c.accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(banner.distanceText, style = NavDigits.copy(fontSize = 34.sp, lineHeight = 38.sp, textAlign = TextAlign.Start), color = c.textPrimary)
                    exitLabel(banner)?.let { label ->
                        Spacer(Modifier.width(RoadlySpacing.s))
                        ExitPill(label)
                    }
                }
                Text(banner.instruction, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                banner.street?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
```

`LaneStrip.kt` :

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection

/** Voies sous la manœuvre courante (si Valhalla les fournit) : flèches, voie valide en accent. */
@Composable
fun LaneStrip(lanes: List<Lane>, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    val description = stringResource(R.string.nav_lanes)
    GlassPanel(modifier.semantics { contentDescription = description }, contentPadding = 8.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            lanes.forEach { lane ->
                val tint = if (lane.valid) c.accent else c.textSecondary.copy(alpha = 0.5f)
                Box(Modifier.size(width = 32.dp, height = 40.dp), contentAlignment = Alignment.Center) {
                    lane.directions.sortedBy { it.ordinal }.ifEmpty { listOf(LaneDirection.STRAIGHT) }.forEach { d ->
                        Icon(d.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
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
```

`UpcomingStrip.kt` :

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.ManeuverIcon
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Bandeau « à venir » : jusqu'à 3 cartes (icône, distance, rue). Pas de trafic ni de services (Phase 3). */
@Composable
fun UpcomingStrip(items: List<UpcomingUi>, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
        items.forEach { u ->
            GlassPanel(Modifier.weight(1f), contentPadding = 10.dp) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ManeuverIcon(u.type, Modifier.size(24.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(u.distanceText, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1)
                    }
                    Text(u.street.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
```

`EtaPanel.kt` :

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassIconButton
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Heure d'arrivée, restant, puis actions (≥ 56 dp en mode conduite) : voix, recentrage (si la carte a bougé), destination, Arrêter. */
@Composable
fun EtaPanel(
    etaClock: String,
    remainingDuration: String,
    remainingDistance: String,
    muted: Boolean,
    following: Boolean,
    onStop: () -> Unit,
    onToggleMute: () -> Unit,
    onRecenter: () -> Unit,
    onChangeDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            Text(stringResource(R.string.nav_eta, etaClock), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Text(stringResource(R.string.nav_remaining, remainingDuration, remainingDistance), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    stringResource(if (muted) R.string.nav_unmute else R.string.nav_mute),
                    onClick = onToggleMute,
                )
                if (!following) GlassIconButton(Icons.Default.MyLocation, stringResource(R.string.nav_recenter), onClick = onRecenter)
                GlassIconButton(Icons.Default.Search, stringResource(R.string.nav_change_destination), onClick = onChangeDestination)
                Spacer(Modifier.weight(1f))
                GlassButton(stringResource(R.string.nav_stop), onClick = onStop, icon = Icons.Default.Close)
            }
        }
    }
}
```

`DrivingHud.kt` :

```kotlin
package fr.roadly.feature.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.SpeedBadge
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Bas gauche : vitesse + limitation (« — » si inconnue, rouge si dépassement) et badge « GPS perdu ». */
@Composable
fun DrivingHud(speedKmh: Double?, limitKmh: Int?, toleranceKmh: Int, gpsLost: Boolean, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        SpeedBadge(speedKmh, limitKmh, toleranceKmh = toleranceKmh)
        if (gpsLost) {
            Spacer(Modifier.width(RoadlySpacing.s))
            GlassPanel(contentPadding = 10.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GpsOff, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.nav_gps_lost), style = MaterialTheme.typography.labelLarge, color = c.danger)
                }
            }
        }
    }
}
```

- [ ] **Step 7 : `NavigationScreen.kt`**

```kotlin
package fr.roadly.feature.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.common.AppError
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.map.CameraMode
import fr.roadly.core.map.RoadlyMap
import fr.roadly.core.map.rememberMapController
import fr.roadly.core.model.LocationFix
import java.util.Locale

@Composable
fun NavigationScreen(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit, vm: NavigationViewModel = hiltViewModel()) {
    // Mode conduite (spec §2) : typographie ×1,3 et cibles 56 dp via LocalDrivingMode = true.
    RoadlyTheme(darkTheme = isDark, drivingMode = true) {
        NavigationContent(isDark, onExit, onChangeDestination, vm)
    }
}

@Composable
private fun NavigationContent(isDark: Boolean, onExit: () -> Unit, onChangeDestination: () -> Unit, vm: NavigationViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = LocalRoadlyColors.current
    val map = rememberMapController()
    KeepScreenOn()
    // Retour système = « Arrêter » (pas d'entrée « Reprendre » sur l'accueil en Phase 2).
    BackHandler { vm.stop() }

    LaunchedEffect(isDark) { vm.setDarkTheme(isDark) }
    LaunchedEffect(c.routeActive, c.routeInactive) { map.setRouteColors(c.routeActive.toHex(), c.routeInactive.toHex()) }
    LaunchedEffect(state.phase) { if (state.phase == NavigationPhase.IDLE) onExit() }
    val route = state.route
    LaunchedEffect(route?.id) {
        if (route != null) {
            map.setRoutes(listOf(route), route.id)
            map.setMarkers(emptyList())
            map.clearTraversed()
        }
    }
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

    Box(Modifier.fillMaxSize().background(c.background)) {
        if (state.styleUrl.isNotEmpty()) {
            RoadlyMap(styleUrl = state.styleUrl, controller = map, modifier = Modifier.fillMaxSize(), onUserGesture = vm::onUserGesture)
        }
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(RoadlySpacing.m),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            state.banner?.let { ManeuverBanner(it) }
            if (state.lanes.isNotEmpty()) LaneStrip(state.lanes)
            if (state.rerouting) {
                GlassPanel(contentPadding = 12.dp) { Text(stringResource(R.string.nav_rerouting), style = MaterialTheme.typography.titleMedium, color = c.accent) }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(RoadlySpacing.m),
            verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s),
        ) {
            DrivingHud(state.speedKmh, state.speedLimitKmh, state.toleranceKmh, state.gpsLost)
            when (state.phase) {
                NavigationPhase.GUIDING -> {
                    if (state.upcoming.isNotEmpty()) UpcomingStrip(state.upcoming)
                    EtaPanel(
                        etaClock = state.etaClock, remainingDuration = state.remainingDuration, remainingDistance = state.remainingDistance,
                        muted = state.muted, following = state.following,
                        onStop = vm::stop, onToggleMute = vm::toggleMute, onRecenter = vm::recenter, onChangeDestination = onChangeDestination,
                    )
                }
                NavigationPhase.ARRIVED -> ArrivedPanel(onFinish = vm::stop)
                NavigationPhase.FAILED -> FailedPanel(state.error, onRetry = vm::retry, onStop = vm::stop)
                NavigationPhase.LOADING, NavigationPhase.IDLE -> GlassPanel(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.nav_loading), color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ArrivedPanel(onFinish: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            Text(stringResource(R.string.nav_arrived_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            GlassButton(stringResource(R.string.nav_finish), onClick = onFinish, icon = Icons.Default.Flag, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FailedPanel(error: AppError?, onRetry: () -> Unit, onStop: () -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            Text(stringResource(R.string.nav_failed_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text((error ?: AppError.Unknown(null)).userMessage(), style = MaterialTheme.typography.bodyMedium, color = c.danger)
            Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(stringResource(R.string.nav_retry), onClick = onRetry, modifier = Modifier.weight(1f))
                GlassButton(stringResource(R.string.nav_stop), onClick = onStop, style = GlassButtonStyle.Secondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** `FLAG_KEEP_SCREEN_ON` tant que l'écran de guidage est affiché (spec §2, mode conduite). */
@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Color.toHex(): String = String.format(Locale.ROOT, "#%06X", 0xFFFFFF and toArgb())
```

- [ ] **Step 8 : vérifier le succès**

Run: `.\gradlew.bat :feature:navigation:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` ; `NavigationViewModelTest` (7) vert ; l'écran et les composants compilent.

- [ ] **Step 9 : commit**

```powershell
git add settings.gradle.kts feature/navigation
git commit -m "feat(navigation-ui): écran de guidage en mode conduite (bandeau, voies, à venir, ETA, HUD, arrivée)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10 : `feature:route` — bouton « Démarrer » actif, préférences d'itinéraire appliquées

**Files:**
- Modify: `feature/route/build.gradle.kts`, `app/build.gradle.kts` (dépendance `:core:navigation`)
- Modify: `feature/route/src/main/java/fr/roadly/feature/route/RouteViewModel.kt` (fichier entier)
- Modify: `feature/route/src/main/java/fr/roadly/feature/route/RouteScreen.kt`
- Modify: `feature/route/src/main/java/fr/roadly/feature/route/RouteNavigation.kt`
- Modify: `feature/route/src/main/res/values/strings.xml`
- Test: `feature/route/src/test/java/fr/roadly/feature/route/RouteViewModelTest.kt`

**Interfaces:**
- Consumes: `UserPreferencesRepository.routePreferences` (Task 3), `RouteAlternativesUseCase(..., preferences)` (Task 2), `NavigationSession.start(option, preferences)` (Task 7), `FakeUserPreferencesRepository`, `FakeSpeechService`, `FakeNavigationServiceController`, `NoopSpeedLimitProvider` (tests).
- Produces: `RouteViewModel(savedStateHandle, alternatives, vehicleRepo, location, config, prefs, session)`, `RouteViewModel.startGuidance(): Boolean`, `NavGraphBuilder.routeScreen(isDark, onBack, onOpenSettings, onStartNavigation: (begin: () -> Boolean) -> Unit = { begin -> begin() })` — le paramètre par défaut garde `app` compilable jusqu'à la Task 12, qui fournit la vraie navigation.

- [ ] **Step 1 : dépendances** — dans `feature/route/build.gradle.kts`, après `implementation(project(":core:map"))` ajouter :

```kotlin
    implementation(project(":core:navigation"))
```

et dans `app/build.gradle.kts`, après `implementation(project(":core:routing"))` ajouter la même ligne (le graphe Hilt de l'app doit voir `NavigationModule` dès que `RouteViewModel` injecte `NavigationSession`) :

```kotlin
    implementation(project(":core:navigation"))
```

- [ ] **Step 2 : tests (échouent)**

Dans `RouteViewModelTest.kt`, ajouter ces imports :

```kotlin
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertFalse
```

Remplacer le faux `routing` (les itinéraires ont désormais un départ et une arrivée, nécessaires au guidage) :

```kotlin
    private var routing: RoutingProvider = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest) = Outcome.Success(listOf(
            Route(request.costing.name, request.costing, 10_000.0, 600.0, listOf(request.origin, request.destination), emptyList(), false, "fake"),
        ))
    }
```

par :

```kotlin
    private var routing: RoutingProvider = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest) = Outcome.Success(listOf(
            Route(
                request.costing.name, request.costing, 10_000.0, 600.0, listOf(request.origin, request.destination),
                listOf(
                    Maneuver(ManeuverType.DEPART, "Conduisez.", null, 10_000.0, 600.0, 0, null),
                    Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 1, null),
                ),
                false, "fake",
            ),
        ))
    }
    private val prefs = FakeUserPreferencesRepository()
    private lateinit var session: NavigationSession
```

Remplacer :

```kotlin
    private fun vm() = RouteViewModel(handle, RouteAlternativesUseCase(routing), vehicle, location, config)
```

par :

```kotlin
    private fun TestScope.vm(): RouteViewModel {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, dispatchers, backgroundScope)
        return RouteViewModel(handle, RouteAlternativesUseCase(routing), vehicle, location, config, prefs, session)
    }
```

Ajouter ces tests :

```kotlin
    @Test fun `start guidance hands the active option to the session`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            val content = expectMostRecentItem().phase as RouteUiState.Content
            val shortest = content.options.first { it.route.costing == RouteCosting.SHORTEST }
            vm.selectRoute(shortest.route.id)
            assertTrue(vm.startGuidance())
            // Pas d'advanceUntilIdle ensuite : la session tourne (boucle de surveillance).
            assertEquals(shortest.route.id, (session.state.value as NavigationState.Guiding).route.id)
            assertTrue(location.guidanceActive)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `start guidance without routes does nothing`() = runTest {
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            assertFalse(vm.startGuidance())
            assertEquals(NavigationState.Idle, session.state.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `route preferences reach the three variants and a change recomputes`() = runTest {
        val seen = mutableListOf<RouteRequest>()
        val base = routing
        routing = object : RoutingProvider {
            override val id = "fake"
            override suspend fun route(request: RouteRequest): Outcome<List<Route>> { seen += request; return base.route(request) }
        }
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        prefs.setRoutePreferences(RoutePreferences(avoidHighways = true))
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()
            assertEquals(3, seen.size)
            assertTrue(seen.all { it.preferences == RoutePreferences(avoidHighways = true) })
            prefs.setRoutePreferences(RoutePreferences(avoidHighways = true, avoidTolls = true))
            advanceUntilIdle()
            expectMostRecentItem()
            assertEquals(6, seen.size)
            assertTrue(seen.drop(3).all { it.preferences.avoidTolls })
        }
    }
```

- [ ] **Step 3 : vérifier l'échec**

Run: `.\gradlew.bat :feature:route:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Too many arguments for constructor 'RouteViewModel'`, `Unresolved reference 'startGuidance'`.

- [ ] **Step 4 : remplacer `RouteViewModel.kt`**

```kotlin
package fr.roadly.feature.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alternatives: RouteAlternativesUseCase,
    private val vehicleRepo: VehicleProfileRepository,
    private val location: LocationRepository,
    private val config: ProviderConfigRepository,
    private val prefs: UserPreferencesRepository,
    private val session: NavigationSession,
) : ViewModel() {

    // Arguments lus par nom de champ de RouteRoute : c'est ce que fait toRoute(), mais sans
    // passer par Bundle (indisponible en test unitaire JVM sans Robolectric).
    private val destName: String = savedStateHandle.get<String>("destName").orEmpty()
    private val destination = GeoPoint(
        requireNotNull(savedStateHandle.get<Double>("destLat")) { "destLat manquant" },
        requireNotNull(savedStateHandle.get<Double>("destLon")) { "destLon manquant" },
    )
    private val origin = MutableStateFlow(location.lastKnown?.point)
    private val departAt = MutableStateFlow(System.currentTimeMillis())
    private val activeId = MutableStateFlow<String?>(null)
    private val isDark = MutableStateFlow(true)
    private val computed = MutableStateFlow<RouteUiState>(RouteUiState.Loading)
    private val reload = MutableStateFlow(0)
    /** Préférences du dernier calcul affiché : transmises à la session (et donc au recalcul). */
    private var computedWith = RoutePreferences()

    private data class Params(val origin: GeoPoint?, val departAt: Long, val vehicle: VehicleProfile?, val preferences: RoutePreferences)

    init {
        viewModelScope.launch {
            combine(origin, departAt, vehicleRepo.profile, prefs.routePreferences, reload) { o, d, v, p, _ -> Params(o, d, v, p) }.collect { (o, d, v, p) ->
                if (o == null) { computed.value = RouteUiState.NoOrigin(destName); return@collect }
                computed.value = RouteUiState.Loading
                when (val r = alternatives(o, destination, v, d, p)) {
                    is Outcome.Success -> {
                        val options = r.value
                        // L'option active doit toujours exister dans la liste affichée.
                        if (activeId.value == null || options.none { it.route.id == activeId.value }) activeId.value = options.first().route.id
                        computedWith = p
                        computed.value = RouteUiState.Content(o, destName, options, activeId.value!!, v != null, d)
                    }
                    is Outcome.Failure -> computed.value = RouteUiState.Error(r.error)
                }
            }
        }
    }

    val uiState: StateFlow<RouteScreenState> = combine(computed, activeId, isDark, config.config) { s, id, dark, cfg ->
        val phase = if (s is RouteUiState.Content) s.copy(activeId = id ?: s.activeId) else s
        RouteScreenState(styleUrl = if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl, phase = phase)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteScreenState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun selectRoute(id: String) { activeId.value = id }
    fun setDepartAt(epochMs: Long) { departAt.value = epochMs }
    fun retry() { reload.value++ }
    /** Origine choisie par appui long sur la carte quand la position est inconnue. */
    fun setOrigin(point: GeoPoint) { origin.value = point }
    val destinationPoint: GeoPoint get() = destination

    /** Démarre le guidage sur l'itinéraire sélectionné ; false si aucun itinéraire n'est affiché. */
    fun startGuidance(): Boolean {
        val content = computed.value as? RouteUiState.Content ?: return false
        val id = activeId.value ?: content.activeId
        val option = content.options.firstOrNull { it.route.id == id } ?: return false
        session.start(option, computedWith)
        return true
    }
}
```

- [ ] **Step 5 : `RouteScreen.kt` et `RouteNavigation.kt`**

Dans `RouteScreen.kt`, ajouter l'import `import androidx.compose.material.icons.filled.Navigation`, remplacer la signature :

```kotlin
fun RouteScreen(isDark: Boolean, onBack: () -> Unit, onOpenSettings: () -> Unit, vm: RouteViewModel = hiltViewModel()) {
```

par :

```kotlin
fun RouteScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartNavigation: (begin: () -> Boolean) -> Unit,
    vm: RouteViewModel = hiltViewModel(),
) {
```

et remplacer :

```kotlin
                    // Guidage hors périmètre de la phase 1 : bouton présent mais désactivé.
                    GlassButton(stringResource(R.string.route_start_phase2), onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
```

par :

```kotlin
                    // L'hôte (app) demande d'abord POST_NOTIFICATIONS (API 33+), puis appelle begin() et ouvre l'écran de guidage.
                    GlassButton(
                        stringResource(R.string.route_start),
                        onClick = { onStartNavigation(vm::startGuidance) },
                        icon = Icons.Default.Navigation,
                        modifier = Modifier.fillMaxWidth(),
                    )
```

`RouteNavigation.kt` — remplacer la fonction :

```kotlin
fun NavGraphBuilder.routeScreen(isDark: Boolean, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    composable<RouteRoute> { RouteScreen(isDark = isDark, onBack = onBack, onOpenSettings = onOpenSettings) }
}
```

par :

```kotlin
/** [onStartNavigation] reçoit `begin` (démarre la session, false si rien à guider) ; l'app y ajoute permission et navigation. */
fun NavGraphBuilder.routeScreen(
    isDark: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartNavigation: (begin: () -> Boolean) -> Unit = { begin -> begin() },
) {
    composable<RouteRoute> { RouteScreen(isDark = isDark, onBack = onBack, onOpenSettings = onOpenSettings, onStartNavigation = onStartNavigation) }
}
```

Dans `feature/route/src/main/res/values/strings.xml`, supprimer la ligne :

```xml
    <string name="route_start_phase2">Guidage : Phase 2</string>
```

(`route_start` = « Démarrer » existe déjà.)

- [ ] **Step 6 : vérifier**

Run: `.\gradlew.bat :feature:route:testDebugUnitTest :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL` ; 5 tests v1 + 3 nouveaux de `RouteViewModelTest` verts ; `app` compile (paramètre par défaut).

- [ ] **Step 7 : commit**

```powershell
git add feature/route app/build.gradle.kts
git commit -m "feat(route): bouton Démarrer actif et préférences d'itinéraire appliquées aux variantes" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---
### Task 11 : `feature:settings` — Voix et sons, Alertes de vitesse, Préférences d'itinéraire

**Files:**
- Modify: `feature/settings/build.gradle.kts`
- Modify: `feature/settings/src/main/java/fr/roadly/feature/settings/SettingsViewModel.kt` (fichier entier)
- Modify: `feature/settings/src/main/java/fr/roadly/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/res/values/strings.xml`
- Test: `feature/settings/src/test/java/fr/roadly/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `UserPreferencesRepository` (5 nouveaux réglages, Task 3), `GuidanceDefaults`, `SpeechService.{setVolume, speak}` (Task 6), `FakeSpeechService` (tests).
- Produces: `VoiceSettings(enabled, volume, toleranceKmh, overSpeedSound)`, `SettingsUiState(vehicle, themeMode, providers, voice: VoiceSettings, routePreferences: RoutePreferences)`, `SettingsViewModel(vehicleRepo, prefs, config, recents, favorites, speech)` et ses setters (Dictionnaire).

Effet réel de chaque réglage (spec §6) : voix → `NavigationSession.say` (test `disabled voice says nothing`) ; volume → `SpeechService.setVolume` (test `volume preference is applied`) ; son de dépassement et tolérance → `checkOverSpeed` (tests `over speed …`) et `SpeedBadge` (test `tolerance preference is exposed for the badge`) ; préférences d'itinéraire → corps Valhalla (Task 2) et `RouteViewModel` (Task 10).

- [ ] **Step 1 : dépendance** — dans `feature/settings/build.gradle.kts`, après `implementation(project(":core:routing"))` ajouter :

```kotlin
    implementation(project(":core:navigation"))
```

- [ ] **Step 2 : tests (échouent)**

Dans `SettingsViewModelTest.kt`, ajouter les imports :

```kotlin
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.navigation.testing.FakeSpeechService
```

ajouter le champ `private val speech = FakeSpeechService()` après `private val prefs = FakeUserPreferencesRepository()`, remplacer :

```kotlin
    private fun vm() = SettingsViewModel(vehicle, prefs, config, recents, favorites)
```

par :

```kotlin
    private fun vm() = SettingsViewModel(vehicle, prefs, config, recents, favorites, speech)
```

puis ajouter :

```kotlin
    @Test fun `voice settings are exposed and persisted`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.setVoiceEnabled(false); vm.setVoiceVolume(140); vm.setOverSpeedSound(true); advanceUntilIdle()
            val v = expectMostRecentItem().voice
            assertEquals(false, v.enabled)
            assertEquals(100, v.volume)
            assertEquals(true, v.overSpeedSound)
            assertEquals(false, prefs.voice.value)
        }
    }

    @Test fun `speed tolerance accepts only 0 5 10`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.setSpeedTolerance(10); advanceUntilIdle()
            assertEquals(10, expectMostRecentItem().voice.toleranceKmh)
            vm.setSpeedTolerance(7); advanceUntilIdle()
            assertEquals(10, prefs.tolerance.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `route preference switches are independent`() = runTest {
        val vm = vm()
        vm.uiState.test {
            vm.setAvoidTolls(true); advanceUntilIdle()
            vm.setAvoidFerries(true); advanceUntilIdle()
            assertEquals(RoutePreferences(avoidTolls = true, avoidHighways = false, avoidFerries = true), expectMostRecentItem().routePreferences)
            vm.setAvoidTolls(false); vm.setAvoidHighways(true); advanceUntilIdle()
            assertEquals(RoutePreferences(avoidTolls = false, avoidHighways = true, avoidFerries = true), expectMostRecentItem().routePreferences)
        }
    }

    @Test fun `test voice applies the saved volume then speaks at once`() = runTest {
        prefs.setVoiceVolume(40)
        val vm = vm()
        vm.testVoice("Essai de voix."); advanceUntilIdle()
        assertEquals(40, speech.volume)
        assertEquals(listOf("Essai de voix."), speech.spoken)
    }
```

- [ ] **Step 3 : vérifier l'échec**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest --console=plain`
Expected: FAIL à la compilation — `Too many arguments for constructor 'SettingsViewModel'`, `Unresolved reference 'setVoiceEnabled'`.

- [ ] **Step 4 : remplacer `SettingsViewModel.kt`**

```kotlin
package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.config.normalized
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.SpeechService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceSettings(
    val enabled: Boolean = GuidanceDefaults.VOICE_ENABLED,
    val volume: Int = GuidanceDefaults.VOICE_VOLUME,
    val toleranceKmh: Int = GuidanceDefaults.SPEED_TOLERANCE_KMH,
    val overSpeedSound: Boolean = GuidanceDefaults.SPEED_ALERT_SOUND,
)

data class SettingsUiState(
    val vehicle: VehicleProfile? = null,
    val themeMode: ThemeMode = ThemeMode.AUTO_SOLAR,
    val providers: ProviderConfig = ProviderConfig.DEFAULT,
    val voice: VoiceSettings = VoiceSettings(),
    val routePreferences: RoutePreferences = RoutePreferences(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vehicleRepo: VehicleProfileRepository,
    private val prefs: UserPreferencesRepository,
    private val config: ProviderConfigRepository,
    private val recents: RecentSearchRepository,
    private val favorites: FavoritesRepository,
    private val speech: SpeechService,
) : ViewModel() {

    private val voice = combine(prefs.voiceEnabled, prefs.voiceVolume, prefs.speedAlertToleranceKmh, prefs.speedAlertSound) { e, v, t, s -> VoiceSettings(e, v, t, s) }

    val uiState: StateFlow<SettingsUiState> = combine(vehicleRepo.profile, prefs.themeMode, config.config, voice, prefs.routePreferences) { v, t, p, vo, r ->
        SettingsUiState(v, t, p, vo, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** [consumption] et [price] sont des saisies libres ("7,5", "1.89", ""). Prix vide = inconnu (aucun défaut inventé). */
    fun saveVehicle(type: VehicleType, fuel: FuelType, consumption: String, price: String) {
        val cons = consumption.toFrDouble() ?: return
        val pr = price.toFrDouble()?.takeIf { it > 0 }
        viewModelScope.launch { vehicleRepo.save(VehicleProfile(type, fuel, cons, pr)) }
    }

    fun clearVehicle() { viewModelScope.launch { vehicleRepo.clear() } }
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { prefs.setThemeMode(mode) } }
    fun clearHistory() { viewModelScope.launch { recents.clear() } }
    fun clearFavorites() { viewModelScope.launch { favorites.clear() } }

    // ── Voix et sons, alertes de vitesse ──
    fun setVoiceEnabled(enabled: Boolean) { viewModelScope.launch { prefs.setVoiceEnabled(enabled) } }
    fun setVoiceVolume(percent: Int) { viewModelScope.launch { prefs.setVoiceVolume(percent) } }
    fun setOverSpeedSound(enabled: Boolean) { viewModelScope.launch { prefs.setSpeedAlertSound(enabled) } }
    fun setSpeedTolerance(kmh: Int) {
        if (kmh !in GuidanceDefaults.ALLOWED_TOLERANCES_KMH) return
        viewModelScope.launch { prefs.setSpeedAlertToleranceKmh(kmh) }
    }

    /** Prononce [phrase] au volume enregistré, même si la voix du guidage est désactivée (test explicite). */
    fun testVoice(phrase: String) {
        viewModelScope.launch {
            speech.setVolume(prefs.voiceVolume.first())
            speech.speak(phrase, flush = true)
        }
    }

    // ── Préférences d'itinéraire ──
    fun setAvoidTolls(value: Boolean) = updateRoutePreferences { it.copy(avoidTolls = value) }
    fun setAvoidHighways(value: Boolean) = updateRoutePreferences { it.copy(avoidHighways = value) }
    fun setAvoidFerries(value: Boolean) = updateRoutePreferences { it.copy(avoidFerries = value) }

    private fun updateRoutePreferences(change: (RoutePreferences) -> RoutePreferences) {
        viewModelScope.launch { prefs.setRoutePreferences(change(prefs.routePreferences.first())) }
    }

    private val _providerError = MutableStateFlow<String?>(null)
    /** URL fautive du dernier enregistrement refusé (null si tout va bien) ; l'écran la formate en « URL invalide : … ». */
    val providerError: StateFlow<String?> = _providerError.asStateFlow()

    fun saveProviders(cfg: ProviderConfig) {
        viewModelScope.launch {
            val clean = runCatching { cfg.normalized() }.onFailure { _providerError.value = it.message ?: "?" }.getOrNull() ?: return@launch
            config.override(clean)
            _providerError.value = null
        }
    }
    fun resetProviders() { viewModelScope.launch { config.reset(); _providerError.value = null } }

    private fun String.toFrDouble(): Double? = trim().replace(',', '.').toDoubleOrNull()
}
```

Note : `setAvoidTolls(false); setAvoidHighways(true)` lancés à la suite sur `viewModelScope` (dispatcher principal de test, un seul fil) s'exécutent dans l'ordre ; chacun relit la valeur enregistrée par le précédent avant d'écrire.

- [ ] **Step 5 : chaînes** — ajouter dans `feature/settings/src/main/res/values/strings.xml`, avant `<string name="settings_data">` :

```xml
    <string name="settings_voice">Voix et sons</string>
    <string name="settings_voice_enabled">Annonces vocales</string>
    <string name="settings_voice_volume">Volume des annonces : %1$d %%</string>
    <string name="settings_speed_sound">Son de dépassement de vitesse</string>
    <string name="settings_voice_test">Tester la voix</string>
    <string name="settings_voice_test_phrase">Dans 300 mètres, tournez à droite. Ceci est un test de la voix de guidage Roadly.</string>

    <string name="settings_speed_alerts">Alertes de vitesse</string>
    <string name="settings_speed_tolerance">Tolérance avant alerte</string>
    <string name="settings_speed_tolerance_value">+%1$d km/h</string>
    <string name="settings_speed_limit_note">La limitation n\'est connue qu\'avec un serveur Valhalla auto-hébergé (Fournisseurs). Sinon le badge affiche « — » et aucune alerte n\'est émise.</string>

    <string name="settings_route_prefs">Préférences d\'itinéraire</string>
    <string name="settings_avoid_tolls">Éviter les péages</string>
    <string name="settings_avoid_highways">Éviter les autoroutes</string>
    <string name="settings_avoid_ferries">Éviter les ferries</string>
```

- [ ] **Step 6 : `SettingsScreen.kt`**

Ajouter les imports :

```kotlin
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.semantics.Role
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.model.RoutePreferences
import kotlin.math.roundToInt
```

Dans `SettingsScreen`, remplacer :

```kotlin
            ThemeSection(state.themeMode, onSelect = vm::setTheme)
```

par :

```kotlin
            ThemeSection(state.themeMode, onSelect = vm::setTheme)
            VoiceSection(state.voice, onEnabled = vm::setVoiceEnabled, onVolume = vm::setVoiceVolume, onOverSpeedSound = vm::setOverSpeedSound, onTest = vm::testVoice)
            SpeedAlertSection(state.voice.toleranceKmh, onSelect = vm::setSpeedTolerance)
            RoutePreferencesSection(state.routePreferences, onTolls = vm::setAvoidTolls, onHighways = vm::setAvoidHighways, onFerries = vm::setAvoidFerries)
```

Ajouter après la section « Thème » :

```kotlin
// ── Voix et sons ────────────────────────────────────────────────────────────

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalRoadlyColors.current
    // Toute la ligne est cliquable (cible ≥ 48 dp) ; le Switch ne fait qu'afficher l'état.
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = RoadlySpacing.touchTarget).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.glass, uncheckedBorderColor = c.glassBorder),
        )
    }
}

@Composable
private fun VoiceSection(voice: VoiceSettings, onEnabled: (Boolean) -> Unit, onVolume: (Int) -> Unit, onOverSpeedSound: (Boolean) -> Unit, onTest: (String) -> Unit) {
    val c = LocalRoadlyColors.current
    // Valeur locale pendant le glissement ; enregistrée au relâchement.
    var volume by remember(voice.volume) { mutableFloatStateOf(voice.volume.toFloat()) }
    val phrase = stringResource(R.string.settings_voice_test_phrase)
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_voice))
            SettingsSwitch(stringResource(R.string.settings_voice_enabled), voice.enabled, onEnabled)
            Text(stringResource(R.string.settings_voice_volume, volume.roundToInt()), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Slider(
                value = volume, onValueChange = { volume = it }, onValueChangeFinished = { onVolume(volume.roundToInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.glassBorder),
            )
            SettingsSwitch(stringResource(R.string.settings_speed_sound), voice.overSpeedSound, onOverSpeedSound)
            GlassButton(stringResource(R.string.settings_voice_test), onClick = { onTest(phrase) }, icon = Icons.AutoMirrored.Filled.VolumeUp, style = GlassButtonStyle.Secondary)
        }
    }
}

// ── Alertes de vitesse ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedAlertSection(toleranceKmh: Int, onSelect: (Int) -> Unit) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            SectionTitle(stringResource(R.string.settings_speed_alerts))
            Text(stringResource(R.string.settings_speed_tolerance), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GuidanceDefaults.ALLOWED_TOLERANCES_KMH.forEach { t ->
                    GlassChip(stringResource(R.string.settings_speed_tolerance_value, t), onClick = { onSelect(t) }, selected = toleranceKmh == t)
                }
            }
            Text(stringResource(R.string.settings_speed_limit_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

// ── Préférences d'itinéraire ────────────────────────────────────────────────

@Composable
private fun RoutePreferencesSection(prefs: RoutePreferences, onTolls: (Boolean) -> Unit, onHighways: (Boolean) -> Unit, onFerries: (Boolean) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs)) {
            SectionTitle(stringResource(R.string.settings_route_prefs))
            SettingsSwitch(stringResource(R.string.settings_avoid_tolls), prefs.avoidTolls, onTolls)
            SettingsSwitch(stringResource(R.string.settings_avoid_highways), prefs.avoidHighways, onHighways)
            SettingsSwitch(stringResource(R.string.settings_avoid_ferries), prefs.avoidFerries, onFerries)
        }
    }
}
```

(`Icons` est déjà importé par l'écran via `androidx.compose.material.icons.Icons`.)

- [ ] **Step 7 : vérifier**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL` ; 4 tests v1 + 4 nouveaux de `SettingsViewModelTest` verts ; `app` compile (Hilt fournit `SpeechService` via `NavigationModule`).

- [ ] **Step 8 : commit**

```powershell
git add feature/settings
git commit -m "feat(settings): Voix et sons, alertes de vitesse et préférences d'itinéraire" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 12 : `app` — écran de guidage, permissions, service, notification ; build complet et essai sur le téléphone

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml` (fichier entier)
- Modify: `app/src/main/java/fr/roadly/app/MainActivity.kt` (fichier entier)
- Modify: `app/src/main/java/fr/roadly/app/RoadlyNavHost.kt` (fichier entier)

**Interfaces:**
- Consumes: `NavigationRoute`, `navigationScreen(isDark, onExit, onChangeDestination)` (Task 9), `routeScreen(..., onStartNavigation)` (Task 10), `NavigationService.ACTION_OPEN_NAVIGATION` (Task 7), `HomeRoute`, `SearchRoute`, `SettingsRoute`, `RouteRoute` (v1).
- Produces: `RoadlyNavHost(isDark, onRequestLocationPermission, onOpenAppSettings, withNotificationPermission: (() -> Unit) -> Unit, openNavigationRequested: Boolean, onOpenNavigationHandled: () -> Unit)`.

- [ ] **Step 1 : dépendance** — dans `app/build.gradle.kts`, après `implementation(project(":feature:settings"))` ajouter :

```kotlin
    implementation(project(":feature:navigation"))
```

- [ ] **Step 2 : remplacer `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <!-- Guidage (Phase 2) : service de premier plan de type location, uniquement pendant le guidage.
         Pas d'ACCESS_BACKGROUND_LOCATION : aucune localisation en arrière-plan sans guidage actif (spec §8). -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application
        android:name=".RoadlyApp"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:supportsRtl="true"
        android:theme="@style/Theme.Roadly">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden|uiMode"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name="fr.roadly.core.navigation.NavigationService"
            android:exported="false"
            android:foregroundServiceType="location" />
    </application>
</manifest>
```

- [ ] **Step 3 : remplacer `RoadlyNavHost.kt`**

```kotlin
package fr.roadly.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import fr.roadly.feature.home.HomeRoute
import fr.roadly.feature.home.homeScreen
import fr.roadly.feature.navigation.NavigationRoute
import fr.roadly.feature.navigation.navigationScreen
import fr.roadly.feature.route.RouteRoute
import fr.roadly.feature.route.routeScreen
import fr.roadly.feature.search.SearchRoute
import fr.roadly.feature.search.searchScreen
import fr.roadly.feature.settings.SettingsRoute
import fr.roadly.feature.settings.settingsScreen

/** Graphe de navigation typé : Accueil → Recherche → Itinéraire → Guidage, Réglages. */
@Composable
fun RoadlyNavHost(
    isDark: Boolean,
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    withNotificationPermission: (() -> Unit) -> Unit,
    openNavigationRequested: Boolean,
    onOpenNavigationHandled: () -> Unit,
) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = HomeRoute) {
        homeScreen(
            isDark = isDark,
            onSearch = { nav.navigate(SearchRoute) },
            onSettings = { nav.navigate(SettingsRoute) },
            onNavigateToRoute = { p -> nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) },
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenAppSettings = onOpenAppSettings,
        )
        searchScreen(
            onBack = { nav.popBackStack() },
            // popUpTo(HomeRoute) : Retour depuis l'itinéraire revient à l'accueil (y compris en changement de destination pendant le guidage).
            onNavigateToRoute = { p -> nav.navigate(RouteRoute(p.point.lat, p.point.lon, p.name)) { popUpTo(HomeRoute) } },
        )
        routeScreen(
            isDark = isDark,
            onBack = { nav.popBackStack() },
            onOpenSettings = { nav.navigate(SettingsRoute) },
            onStartNavigation = { begin ->
                // API 33+ : POST_NOTIFICATIONS demandée avant de démarrer le service (spec §2) ; refus = notification masquée seulement.
                withNotificationPermission {
                    if (begin()) nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
                }
            },
        )
        settingsScreen(onBack = { nav.popBackStack() })
        navigationScreen(
            isDark = isDark,
            onExit = { if (!nav.popBackStack(HomeRoute, inclusive = false)) nav.navigate(HomeRoute) },
            onChangeDestination = { nav.navigate(SearchRoute) },
        )
    }
    // Après NavHost : le graphe est posé avant de traiter une ouverture demandée par la notification.
    LaunchedEffect(openNavigationRequested) {
        if (openNavigationRequested) {
            nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
            onOpenNavigationHandled()
        }
    }
}
```

- [ ] **Step 4 : remplacer `MainActivity.kt`**

```kotlin
package fr.roadly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import fr.roadly.core.designsystem.theme.RoadlyTheme
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.location.PermissionEvents
import fr.roadly.core.navigation.NavigationService
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var location: LocationRepository
    @Inject lateinit var permissionEvents: PermissionEvents
    private val themeVm: ThemeViewModel by viewModels()

    /** Ouverture de l'écran de guidage demandée par la notification. */
    private val openNavigation = MutableStateFlow(false)
    private var afterNotificationPermission: (() -> Unit)? = null

    // Demande FINE + COARSE ; refus sans rationale = « refusé définitivement ».
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) location.markDeniedForever() else location.refreshPermission()
        permissionEvents.changed.tryEmit(Unit)
    }

    // Accordée ou non, le guidage démarre : sans la permission, seule la notification est masquée.
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        afterNotificationPermission?.invoke()
        afterNotificationPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent?.action == NavigationService.ACTION_OPEN_NAVIGATION) openNavigation.value = true
        setContent {
            val mode by themeVm.themeMode.collectAsStateWithLifecycle()
            val now by themeVm.nowTick.collectAsStateWithLifecycle()
            val openNav by openNavigation.collectAsStateWithLifecycle()
            val p = location.lastKnown?.point
            val isDark = ThemeResolver.isDark(mode, isSystemInDarkTheme(), p?.lat, p?.lon, now)
            RoadlyTheme(darkTheme = isDark) {
                RoadlyNavHost(
                    isDark = isDark,
                    onRequestLocationPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                    onOpenAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) },
                    withNotificationPermission = ::withNotificationPermission,
                    openNavigationRequested = openNav,
                    onOpenNavigationHandled = { openNavigation.value = false },
                )
            }
        }
    }

    /** Toucher la notification de guidage ramène à l'écran de guidage (activité déjà ouverte). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == NavigationService.ACTION_OPEN_NAVIGATION) openNavigation.value = true
    }

    /** API 33+ : POST_NOTIFICATIONS demandée au premier démarrage du guidage, puis [block] dans tous les cas. */
    private fun withNotificationPermission(block: () -> Unit) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) { block(); return }
        afterNotificationPermission = block
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Au retour (ex. depuis les réglages système), on resynchronise l'état de la permission.
    override fun onResume() { super.onResume(); location.refreshPermission(); permissionEvents.changed.tryEmit(Unit) }
}
```

(`FLAG_KEEP_SCREEN_ON` est posé et retiré par `NavigationScreen` via `DisposableEffect` — Task 9, `KeepScreenOn()` — uniquement pendant que l'écran de guidage est affiché.)

- [ ] **Step 5 : build et tests complets** (en arrière-plan, > 2 min)

Run: `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL` ; 185 tests unitaires verts (83 de la v1 + 102 de la Phase 2 : Task 1 : 5, Task 2 : 9, Task 3 : 5, Task 4 : 3, Task 5 : 38, Task 6 : 7, Task 7 : 18, Task 8 : 3, Task 9 : 7, Task 10 : 3, Task 11 : 4) ; APK `app\build\outputs\apk\debug\app-debug.apk`. Compter : `Get-ChildItem -Recurse -Filter "TEST-*.xml" -Path . | Where-Object { $_.FullName -match "test-results" } | ForEach-Object { ([xml](Get-Content $_.FullName)).testsuite } | Measure-Object -Property tests -Sum`.

- [ ] **Step 6 : installation sur le HONOR Magic 8 Pro (ADB)**

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: l'appareil est listé `device` ; `Success`.

- [ ] **Step 7 : essai sur l'appareil (sans position simulée)**

La position simulée n'est peut-être pas activée : on vérifie ce qui est observable à l'arrêt ou en marchant quelques dizaines de mètres. Ouvrir un second terminal : `adb logcat -s RoadlySpeech RoadlyNavService` (aucune ligne `W` attendue).

1. Lancer Roadly, accorder la localisation, rechercher une adresse à 1–3 km, ouvrir la comparaison : le bouton **« Démarrer »** est actif (plus de « Guidage : Phase 2 »).
2. Toucher « Démarrer » : au premier démarrage, la boîte système **« Autoriser Roadly à envoyer des notifications ? »** apparaît (Android 13+) ; accepter.
3. L'écran de guidage s'ouvre : **bandeau** en haut avec l'icône et l'instruction de la 1re manœuvre après le départ (texte Valhalla en français), la distance (« 80 m »…), la rue ; bandeau **« à venir »** et panneau **« Arrivée HH:MM »** / « N min · X km » en bas ; badge de vitesse avec limitation **« — »** (instance FOSSGIS) ; boutons d'au moins 56 dp.
4. Dans les 2 s : la **voix française** prononce la consigne de départ (« Conduisez vers … »). Si rien : Réglages › Voix et sons › « Tester la voix » ; si toujours rien, installer la voix française du moteur TTS du téléphone (le log `RoadlySpeech` indique « Voix française indisponible »).
5. **Notification** : tirer le volet → notification « Guidage » avec l'instruction, « 350 m · arrivée 14:32 » et l'action **« Arrêter »**. Vérification ADB :
   `adb shell dumpsys activity services fr.roadly | Select-String "NavigationService|isForeground|foregroundServiceType"` → le service est listé, `isForeground=true`, type `location` (0x00000008) ;
   `adb shell dumpsys notification --noredact | Select-String "roadly.navigation"` → une entrée sur le canal `roadly.navigation`.
6. **Écran maintenu allumé** : `adb shell dumpsys window windows | Select-String "KEEP_SCREEN_ON"` → présent tant que l'écran de guidage est affiché ; l'écran ne s'éteint pas après le délai de veille.
7. **Mode conduite / carte** : la carte suit la position (zoom 17, cap) ; faire glisser la carte → le bouton **Recentrer** apparaît ; sans action, le suivi reprend au bout de 8 s ; toucher Recentrer le reprend aussitôt.
8. **Silence** : toucher le bouton voix → icône barrée ; plus d'annonce ; le retoucher → annonces réactivées.
9. **Retour depuis la notification** : bouton Accueil du téléphone, puis toucher la notification → retour à l'écran de guidage.
10. **Arrêt** : toucher « Arrêter » dans la notification → la notification disparaît, l'application revient à l'accueil ; `adb shell dumpsys activity services fr.roadly | Select-String NavigationService` → plus rien. Relancer un guidage puis toucher « Arrêter » à l'écran → même résultat. La touche Retour sur l'écran de guidage arrête aussi le guidage.
11. **Changer de destination** : pendant un guidage, bouton loupe → recherche → nouvelle destination → « Démarrer » → le guidage repart vers la nouvelle destination (une seule notification).
12. **Réglages** : « Voix et sons » (interrupteur, curseur de volume, son de dépassement, « Tester la voix » prononce la phrase au volume choisi), « Alertes de vitesse » (0 / +5 / +10 km/h, une seule pastille sélectionnée), « Préférences d'itinéraire » (3 interrupteurs) ; revenir à une comparaison d'itinéraire → elle est recalculée avec les préférences (vérifiable en Debug : `adb logcat -s okhttp.OkHttpClient` montre les requêtes `/route`).
13. **Recalcul et arrivée** (si possible) : en marchant/roulant hors du tracé > 40 m pendant 5 s, annonce « Recalcul de l'itinéraire. » et bandeau « Recalcul… » ; à moins de 25 m de la destination, « Vous êtes arrivé » + « Terminer ». Si la position simulée est activée par l'utilisateur (Options pour les développeurs › Application de position fictive), rejouer un trajet ; sinon noter ces deux points comme « non vérifiés sur appareil » dans le rapport.

- [ ] **Step 8 : commit**

```powershell
git add app
git commit -m "feat(app): écran de guidage, permissions de notification et de service, retour depuis la notification" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 13 : Documentation

**Files:**
- Modify: `FEATURES.md`, `ARCHITECTURE.md`, `README.md`, `docs/ROADMAP.md`

**Interfaces:** aucune (documentation).

- [ ] **Step 1 : `FEATURES.md`** — remplacer chaque ligne de gauche par celle de droite (texte exact) :

| Ancienne ligne | Nouvelle ligne |
|---|---|
| `Mise à jour : 2026-10-30 (fin de la v1, branche `feat/roadly-v1`).` | `Mise à jour : 2026-10-30 (Phase 2 guidage, branche `feat/roadly-v1`).` |
| `\| Position GPS en direct, cap, vitesse \| G \| fonctionnelle \| 1 \| FusedLocation ; 1 Hz carte, 2 Hz suivi \|` | `\| Position GPS en direct, cap, vitesse \| G \| fonctionnelle \| 1 \| FusedLocation ; 1 Hz carte et guidage, 2 Hz suivi \|` |
| `\| Guidage vocal français \| G \| non commencée \| 2 \| TTS Android `fr-FR`, anti-répétition \|` | `\| Guidage vocal français \| G \| fonctionnelle \| 2 \| TTS Android `Locale.FRANCE`, textes Valhalla, 3 paliers selon la vitesse, anti-répétition (manœuvre, palier) \|` |
| `\| Bandeau de manœuvre (prochaine instruction, distance) \| G \| non commencée \| 2 \| Manœuvres Valhalla déjà décodées \|` | `\| Bandeau de manœuvre (prochaine instruction, distance) \| G \| fonctionnelle \| 2 \| Icône, distance, instruction, rue, pastille de sortie \|` |
| `\| Affichage des voies \| G \| non commencée \| 2 \| `lanes` Valhalla si `turn:lanes` OSM \|` | `\| Affichage des voies \| G \| en cours \| 2 \| Décodage et affichage faits ; aucune voie dans les réponses FOSSGIS observées (fixture Paris) : à confirmer sur un Valhalla récent \|` |
| `\| Ronds-points avec sortie \| G \| non commencée \| 2 \| `roundabout_exit_count` déjà mappé \|` | `\| Ronds-points avec sortie \| G \| fonctionnelle \| 2 \| Pastille « 2e sortie » ; numéro de sortie (`sign`) si présent \|` |
| `\| Suivi d'itinéraire, détection de sortie, recalcul \| G \| non commencée \| 2 \| Projection sur polyligne \|` | `\| Suivi d'itinéraire, détection de sortie, recalcul \| G \| fonctionnelle \| 2 \| RouteTracker ; hors itinéraire 40 m × 5 fixes ou 100 m ; recalcul ≤ 1/10 s \|` |
| `\| Temps/distance restants, heure d'arrivée en guidage \| G \| non commencée \| 2 \| ETA statique déjà en comparaison \|` | `\| Temps/distance restants, heure d'arrivée en guidage \| G \| fonctionnelle \| 2 \| Durées Valhalla restantes, manœuvre courante au prorata \|` |
| `\| Limitation de vitesse de la route \| G \| bloquée \| 2 \| Nécessite Valhalla auto-hébergé (`trace_attributes`) ; « — » affiché \|` | `\| Limitation de vitesse de la route \| G \| bloquée \| 2 \| Implémentée (`trace_attributes`, ≤ 1/5 s) ; active seulement avec un Valhalla auto-hébergé ; « — » sur FOSSGIS \|` |
| `\| Alerte sonore de dépassement (option) \| G \| non commencée \| 2 \| Dépend de la limitation \|` | `\| Alerte sonore de dépassement (option) \| G \| bloquée \| 2 \| Implémentée (bip + badge rouge, tolérance 0/5/10 km/h) ; dépend de la limitation \|` |
| `\| Changer de destination / ajouter un arrêt en guidage \| G \| non commencée \| 2 \| \|` | `\| Changer de destination / ajouter un arrêt en guidage \| G \| en cours \| 2 \| Changer de destination : fait ; ajout d'arrêt : Phase 4 \|` |
| `\| Mode Silence \| G \| non commencée \| 2 \| Avec le TTS \|` | `\| Mode Silence \| G \| fonctionnelle \| 2 \| Bouton voix de l'écran de guidage \|` |
| `\| Tunnels (perte GPS) \| G \| non commencée \| 2 \| Extrapolation sur la polyligne \|` | `\| Tunnels (perte GPS) \| G \| fonctionnelle \| 2 \| Extrapolation après 3 s, badge « GPS perdu » après 10 s \|` |
| `\| Bandeau « à venir » : manœuvres, virages \| G \| non commencée \| 2 \| Design original Roadly (verre, bas d'écran) \|` | `\| Bandeau « à venir » : manœuvres, virages \| G \| fonctionnelle \| 2 \| 3 prochaines manœuvres (icône, distance, rue) \|` |
| `\| Voix et sons, volume, mode Silence \| non commencée \| Phase 2 \|` | `\| Voix et sons, volume, mode Silence \| fonctionnelle \| Voix, volume 0–100, son de dépassement, « Tester la voix » ; Silence sur l'écran de guidage \|` |
| `\| Alertes de vitesse / de vigilance \| non commencée \| Phase 2 / 3 \|` | `\| Alertes de vitesse / de vigilance \| en cours \| Tolérance 0/5/10 km/h faite ; vigilance : Phase 3 \|` |
| `\| Préférences d'itinéraire (péages, autoroutes, ferries) \| non commencée \| Phase 2 (options Valhalla) \|` | `\| Préférences d'itinéraire (péages, autoroutes, ferries) \| fonctionnelle \| `use_tolls/use_highways/use_ferry = 0`, 3 variantes et recalcul \|` |
| `\| Tests unitaires \| fonctionnelle \| 83 tests \|` | `\| Tests unitaires \| fonctionnelle \| 185 tests \|` |
| `\| Service de premier plan / notification \| non commencée \| Phase 2 \|` | `\| Service de premier plan / notification \| fonctionnelle \| Type `location`, notification (manœuvre, distance, arrivée, Arrêter), arrêt à l'arrivée \|` |

(Dans le fichier, les `\|` sont de simples `|`.)

- [ ] **Step 2 : `ARCHITECTURE.md`**

Remplacer `État au 2026-10-30 (fin de la v1).` par `État au 2026-10-30 (Phase 2 : guidage).` (le reste de la ligne est inchangé), et `## 2. Modules (13)` par `## 2. Modules (15)`.

Dans le bloc des modules, remplacer :

```
core:location       LocationRepository (Fused), LocationMode, LocationPermissionState, PermissionEvents, FakeLocationRepository
```

par :

```
core:location       LocationRepository (Fused), LocationMode (+ GUIDANCE 1 Hz prioritaire), LocationPermissionState, PermissionEvents, FakeLocationRepository
```

ajouter après la ligne `core:routing …` :

```
core:navigation     RouteTracker, AnnouncementPolicy, SpeedSmoother, NavigationSession (StateFlow<NavigationState>), SpeechService (TTS fr-FR),
                    SpeedLimitProvider (Valhalla trace_attributes, auto-hébergé), NavigationService (premier plan location + notification)
```

remplacer :

```
feature:route       comparaison 3 itinéraires, heure de départ, coût
feature:settings    véhicule, thème, données, fournisseurs, à propos
```

par :

```
feature:route       comparaison 3 itinéraires, heure de départ, coût, préférences d'itinéraire, « Démarrer »
feature:settings    véhicule, thème, voix et sons, alertes de vitesse, préférences d'itinéraire, données, fournisseurs, à propos
feature:navigation  écran de guidage en mode conduite : bandeau, voies, « à venir », ETA, vitesse/limitation, arrivée
```

Ajouter à la fin de la section 3 :

```
- **Guidage** : « Démarrer » → (API 33+ POST_NOTIFICATIONS) → `NavigationSession.start` → `LocationRepository.setGuidanceActive(true)` (1 Hz) + `NavigationService` (premier plan `location`) → pour chaque fix : `SpeedSmoother` → `RouteTracker.update` → `AnnouncementPolicy` → `SpeechService` ; hors itinéraire → `RoutingProvider.route` (≤ 1/10 s) ; > 3 s sans fix → extrapolation ; `StateFlow<NavigationState>` → `NavigationViewModel` / notification. Arrivée ou « Arrêter » → GPS relâché, service arrêté.
```

Remplacer toute la section 6 :

```
## 6. Cibles Phase 2 (guidage) — architecture prévue

- `core:navigation` (nouveau) : `RouteTracker` (projection sur polyligne, distance à la manœuvre, sortie d'itinéraire, recalcul), `GuidanceEngine` (file d'annonces, anticipation, anti-répétition), `SpeechService` (TTS `fr-FR`, focus audio), `NavigationService` (foreground service + notification, `LocationMode.FOLLOW`).
- `feature:navigation` : écran conduite (bandeau manœuvre, vitesse/limitation, bandeau « à venir », ETA/restant), `LocalDrivingMode = true`.
- Limitation de vitesse : `SpeedLimitProvider` (interface) ; impl Valhalla `trace_attributes` sur instance auto-hébergée ; `null` tant qu'indisponible.
```

par :

```
## 6. Guidage (Phase 2) — implémenté

- `core:navigation` : `RouteTracker` (pur Kotlin : projection équirectangulaire locale sur le segment le plus proche dans une fenêtre −50/+500 m, repli global ; restant = durées Valhalla avec la manœuvre courante au prorata ; hors itinéraire 40 m × 5 fixes ou 100 m ; arrivée < 25 m sur la dernière manœuvre), `AnnouncementPolicy` (paliers loin/proche/maintenant selon la vitesse lissée, anti-répétition par (manœuvre, palier)), `NavigationSession` (`@Singleton`, dispatcher confiné, recalcul ≤ 1/10 s, extrapolation après 3 s, badge après 10 s, bip de dépassement), `SpeechService` (TTS `Locale.FRANCE`, focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, volume par annonce), `NavigationService` (premier plan `location`, notification avec « Arrêter », arrêt hors guidage).
- `core:location` : `LocationMode.GUIDANCE` (1 Hz) imposé par `setGuidanceActive(true)`, prioritaire sur le mode des écrans.
- `feature:navigation` : écran de guidage, `RoadlyTheme(drivingMode = true)`, `FLAG_KEEP_SCREEN_ON`, tracé parcouru atténué (`MapController.setTraversed`, seconde source GeoJSON).
- Limitation de vitesse : `SpeedLimitProvider` ; `ValhallaTraceSpeedLimitProvider` n'interroge jamais `*.openstreetmap.de` et au plus toutes les 5 s ; `null` (« — ») sinon.
- Doubles de test partagés : `FakeUserPreferencesRepository` (`core:data`), `FakeSpeechService`, `FakeNavigationServiceController`, `TestRoutes` (`core:navigation`).
```

- [ ] **Step 3 : `README.md`**

Dans le tableau des fournisseurs, remplacer :

```
| Limitation de vitesse | Non disponible en v1 (affichage « — ») | Phase 2 via `trace_attributes` sur un Valhalla auto-hébergé | — |
```

par :

```
| Limitation de vitesse | Valhalla `trace_attributes`, **uniquement** sur une instance auto-hébergée (jamais sur FOSSGIS : « — ») | Au plus une requête toutes les 5 s pendant le guidage | — |
```

Dans « Marqué « Bientôt » ou désactivé », supprimer la ligne `- Bouton « Démarrer » d'un itinéraire (libellé « Guidage : Phase 2 »).` et remplacer `- Limitation de vitesse : inconnue en v1.` par `- Limitation de vitesse : « — » tant qu'aucun Valhalla auto-hébergé n'est configuré (voir « Limitation de vitesse : Valhalla auto-hébergé »).`

Ajouter, juste avant `## Marqué « Bientôt » ou désactivé` :

````markdown
## Guidage (Phase 2)

- « Démarrer » sur la comparaison d'itinéraires lance le guidage : suivi GPS à 1 Hz, bandeau de manœuvre (icône, distance, instruction, rue, sortie de rond-point), voies si Valhalla les fournit, bandeau « à venir » (3 manœuvres), heure d'arrivée et restant, vitesse et limitation.
- Voix française (synthèse vocale Android, sans serveur) : annonces anticipées selon la vitesse, anti-répétition, bouton Silence.
- Recalcul automatique hors itinéraire (au plus un toutes les 10 s), extrapolation en tunnel, badge « GPS perdu ».
- Service de premier plan avec notification persistante (instruction, distance, arrivée, « Arrêter ») ; arrêt automatique à l'arrivée.
- Réglages : Voix et sons, Alertes de vitesse (tolérance 0/5/10 km/h), Préférences d'itinéraire (éviter péages, autoroutes, ferries).

## Permissions Android

| Permission | Pourquoi | Quand |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Position sur la carte et guidage | Au premier affichage de la carte |
| `POST_NOTIFICATIONS` (Android 13+) | Notification persistante du guidage | Au premier « Démarrer » ; un refus masque seulement la notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Continuer le guidage écran éteint ou application en arrière-plan | Accordées à l'installation ; service actif uniquement pendant un guidage |

Roadly ne demande pas `ACCESS_BACKGROUND_LOCATION` : aucune localisation en arrière-plan hors guidage actif.

## Limitation de vitesse : Valhalla auto-hébergé

L'instance publique FOSSGIS n'est jamais interrogée pour la limitation (fair-use). Pour l'activer :

```powershell
mkdir valhalla_tiles
docker run -dt --name valhalla -p 8002:8002 -v "${PWD}\valhalla_tiles:/custom_files" -e tile_urls=https://download.geofabrik.de/europe/france/ile-de-france-latest.osm.pbf ghcr.io/valhalla/valhalla-scripted:latest
```

La construction des tuiles prend du temps (commencer par une région ; `europe/france-latest.osm.pbf` pour tout le pays). Exposer ensuite le port 8002 en **HTTPS** (reverse proxy, par exemple `caddy reverse-proxy --from valhalla.exemple.fr --to localhost:8002`) et saisir `https://valhalla.exemple.fr` dans **Réglages › Fournisseurs › Itinéraires (Valhalla)**. Vérification :

```powershell
Invoke-RestMethod -Method Post -Uri "https://valhalla.exemple.fr/trace_attributes" -ContentType "application/json" -Body '{"shape":[{"lat":48.8566,"lon":2.3522},{"lat":48.8570,"lon":2.3530}],"costing":"auto","shape_match":"map_snap","filters":{"attributes":["edge.speed_limit"],"action":"include"}}'
```

La réponse contient `edges[].speed_limit` (km/h) quand OpenStreetMap renseigne `maxspeed` ; sinon la limitation reste « — ».
````

- [ ] **Step 4 : `docs/ROADMAP.md`**

Sous `## Phase 2 : guidage`, ajouter la ligne :

```
Statut : livrée le 2026-10-30 (plan `docs/superpowers/plans/2026-10-30-roadly-phase2-guidage.md`). Restent bloquées la limitation de vitesse et l'alerte de dépassement tant qu'aucun Valhalla auto-hébergé n'est configuré ; l'affichage des voies dépend de la réponse Valhalla.
```

Remplacer `- Mode conduite complet (interface dédiée, bouton « Démarrer » activé ; il est désactivé en v1 avec le libellé « Guidage : Phase 2 »).` par `- Mode conduite complet (interface dédiée, bouton « Démarrer » activé).`

Remplacer le bloc `### Modules impactés` de la Phase 2 (les 7 puces de `feature:route` à `app`) par :

```
- `core:navigation` (nouveau) : suivi sur le tracé, annonces, recalcul, perte GPS, synthèse vocale, limitation (`trace_attributes`), service de premier plan.
- `feature:navigation` (nouveau) : écran de guidage en mode conduite.
- `core:model`, `core:routing` : voies, consignes vocales, numéros de sortie, préférences d'itinéraire.
- `core:data` : réglages voix, volume, alertes, préférences (DataStore).
- `core:location` : mode GUIDANCE 1 Hz prioritaire.
- `core:map`, `core:designsystem` : tracé parcouru, tolérance du badge de vitesse.
- `feature:route`, `feature:settings`, `app` : « Démarrer », réglages, permissions, service, retour depuis la notification.
```

- [ ] **Step 5 : vérifier** — relire les quatre fichiers ; `Select-String -Path FEATURES.md,README.md,ARCHITECTURE.md,docs\ROADMAP.md -Pattern "Guidage : Phase 2"` ne renvoie plus que la ligne historique éventuelle de la spec v1 (aucune dans ces quatre fichiers).

Expected: aucune occurrence.

- [ ] **Step 6 : commit**

```powershell
git add FEATURES.md ARCHITECTURE.md README.md docs/ROADMAP.md
git commit -m "docs: Phase 2 guidage (statuts, architecture, permissions, Valhalla auto-hébergé, feuille de route)" -m "Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Auto-revue du plan

### 1. Couverture de la spec

| Spec | Exigence | Tâche(s) |
|---|---|---|
| §1 | Suivi GPS continu, guidage réel | 4, 5, 7, 9, 12 |
| §1 | Instructions vocales françaises | 2 (textes Valhalla), 5 (`AnnouncementPolicy`), 6 (`AndroidSpeechService`), 7 |
| §1 | Bandeau de manœuvre, voies, ronds-points | 1, 2, 9 (`ManeuverBanner`, `LaneStrip`) |
| §1 | Bandeau « à venir » / Horizon (manœuvres) | 7 (`upcoming`), 9 (`UpcomingStrip`) |
| §1 | Recalcul automatique | 5 (`isOffRoute`), 7 (`maybeReroute`) |
| §1 | Service de premier plan | 7 (`NavigationService`), 12 (manifeste, permissions) |
| §1 | Mode conduite | 9 (`RoadlyTheme(drivingMode = true)`, `KeepScreenOn`, 56 dp) |
| §1 | Compteur + limitation (si disponible) | 6, 7, 8 (`SpeedBadge` tolérance), 9 (`DrivingHud`) |
| §1 | Temps/distance restants, heure d'arrivée | 5 (formules), 7 (`etaEpochMs`), 9 (`EtaPanel`) |
| §1 | Changement de destination en guidage | 9 (bouton), 12 (navigation vers la recherche), 7 (`start` remplace la course) |
| §1 | Voix et sons, préférences d'itinéraire | 3, 11 ; 2 et 10 pour l'effet sur Valhalla |
| §1 | Hors périmètre absent ou « Bientôt » | aucun trafic/zones/services/multi-arrêts ajoutés ; FEATURES mis à jour (13) |
| §2 Limitation | Interface, `trace_attributes`, filtres, uniquement hors FOSSGIS, jamais devinée | 6 (`isPublicInstance`, tests) |
| §2 Voix | `TextToSpeech`, `Locale.FRANCE`, `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, file | 6 |
| §2 Service | type `location`, notification (manœuvre, distance, ETA, Arrêter), arrêt arrivée/action, POST_NOTIFICATIONS | 7, 12 |
| §2 Suivi | projection, index, distance manœuvre, restant proportionnel, 40 m × 5 / 100 m, < 25 m | 5 (spécification numérique + tests) |
| §2 Recalcul | origine = fix, destination, costing, préférences, ≤ 1/10 s, annonce, échec réseau → ancien tracé | 7 (tests `five fixes…`, `reroute is limited…`) |
| §2 Annonces | 3 distances par classe de vitesse, textes Valhalla, anti-répétition, vitesse lissée | 5 (`SpeedClass`, `SpeedSmoother`, tests de seuils) |
| §2 « À venir » | 3 manœuvres, sortie de rond-point, voies | 7, 9 |
| §2 Mode conduite | ×1,3, 56 dp, écran allumé, pas d'animation en boucle, thème inchangé | 9 (aucune animation infinie ajoutée ; `isDark` résolu par l'app comme en v1) |
| §2 Préférences | `use_tolls/use_highways/use_ferry = 0`, DataStore, 3 variantes + recalcul | 2, 3, 10, 7 |
| §2 Dépassement | son + badge rouge, tolérance 0/5/10, seulement si limitation connue | 7 (`checkOverSpeed`), 8, 11 |
| §2 Tunnels | extrapolation > 3 s, badge 10 s | 5 (`extrapolate`), 7 (`watchdog`) |
| §3 | Modules et responsabilités | 5–7 (`core:navigation`), 9 (`feature:navigation`) ; écarts : décisions 1, 4, 5 |
| §4 | Contrats | 1 (`RoutePreferences`, `Lane`, `LaneDirection`, `RouteProgress`), 5 (`NavigationState`, `RouteTracker`, `AnnouncementPolicy`, `Tier`), 6 (`SpeedLimitProvider`), 7 (`NavigationSession`) ; écart : décision 2 |
| §5 | Carte suivi zoom 17 / cap / tilt 0, tracé actif cyan, parcouru atténué | 8, 9 (`applyCameraMode(FOLLOW)` = zoom 17, cap, tilt 0) |
| §5 | Bandeau 96 dp, icône 40 dp, NavDigits, pastille, voies | 9 |
| §5 | Bas : 3 cartes, ETA, restant « 12 min · 8,4 km », Arrêter 56 dp, mute, recentrage 8 s | 9 |
| §5 | SpeedBadge bas gauche ; états Recalcul, GPS perdu, Arrivée (Terminer), Échec (Réessayer/Arrêter) | 9 |
| §6 | Réglages et effet réel vérifié par tests | 11 (VM), 7 (moteur), 10 (préférences) |
| §7 | Tests JVM : tracker, politique, voies, préférences → body, VM (Turbine) | 5, 2, 9, 10, 11 |
| §7 | Instrumentés : service démarre/arrête, notification | 12 (vérifications `adb dumpsys`, décision 8) |
| §7 | Manuel sur téléphone | 12 Step 7 |
| §8 | Contraintes | Global Constraints ; 4 (1 Hz), 6 (≤ 1/5 s, auto-hébergé), 7 (≤ 1/10 s, arrêt à l'arrivée), 12 (pas de localisation d'arrière-plan) |

Aucun manque identifié.

### 2. Recherche de trous

Relu : aucune occurrence de « TBD », « TODO », « à implémenter plus tard », « gérer les cas limites » ; chaque étape de code montre le code (fichiers complets pour les créations, remplacements exacts ancien → nouveau pour les modifications) ; chaque tâche finit par une commande Gradle, son résultat attendu et un commit. Les seuls textes à adapter à l'exécution sont l'URL d'exemple `valhalla.exemple.fr` du README (exemple documentaire) et le résultat des points 13 du test sur appareil (dépend de la position simulée).

### 3. Cohérence des types et signatures

- `RouteRequest.preferences` (T1) ← utilisé par `ValhallaRoutingProvider` (T2), `RouteAlternativesUseCase` (T2), `NavigationSession.maybeReroute` (T7), tests T2/T7/T10.
- `Maneuver.verbalPre/verbalAlert/lanes/exitNumber` (T1) ← mapping T2, `AnnouncementPolicy` (T5), `NavigationSession.guiding` (T7), `buildUiState` (T9), `TestRoutes` (T5).
- `RouteProgress` (T1) : champs utilisés à l'identique par `RouteTracker.progressAt` (T5), `AnnouncementPolicy` (T5), `NavigationSession` (T7), `buildUiState` / `NavigationScreen` (T9).
- `UserPreferencesRepository` : `routePreferences`, `voiceEnabled`, `voiceVolume`, `speedAlertToleranceKmh`, `speedAlertSound` + setters (T3) ← T7, T9, T10, T11 ; `FakeUserPreferencesRepository.{voice, volume, tolerance}` utilisés en T11.
- `LocationRepository.setGuidanceActive` / `FakeLocationRepository.guidanceActive` / `.permission` (T4) ← T7, T9, T10.
- `GuidanceConstants.*` (T5) ← T5, T6 (`SPEED_LIMIT_MIN_INTERVAL_MS`, `RECENT_FIXES`), T7.
- `AnnouncementPolicy.next(progress, maneuvers, speedKmh, already)` (T5) ← T7 avec les mêmes paramètres.
- `SpokenPhrases.REROUTING` (T5, `internal`) ← T7 et son test (même module).
- `NavigationClock` (fun interface, T6) ← lambdas `{ testScheduler.currentTime }` en T7/T9/T10 et `{ now }` en T6.
- `SpeechService.{speak, setVolume, beep, stop}` (T6) ← T7, T11 ; `FakeSpeechService.{spoken, volume, beeps, stops}` ← T7, T11.
- `NavigationSession(location, routing, speedLimits, speech, prefs, serviceController, clock, dispatchers, scope)` : même ordre en T7, T9, T10 ; `start(option, preferences)`, `stop()`, `retry()`, `setMuted()`, `state`, `muted` ← T9, T10.
- `NavigationService.ACTION_OPEN_NAVIGATION` (T7) ← `NavigationNotifications` (T7), `MainActivity` (T12).
- `MapController.setTraversed(geometry, segmentIndex, snapped)` / `clearTraversed()` (T8) ← `NavigationScreen` (T9).
- `SpeedBadge(..., toleranceKmh)` (T8) ← `DrivingHud` (T9).
- `routeScreen(..., onStartNavigation: (begin: () -> Boolean) -> Unit)` (T10) ← `RoadlyNavHost` (T12) ; `RouteViewModel.startGuidance(): Boolean` ← `RouteScreen` (`vm::startGuidance`).
- `navigationScreen(isDark, onExit, onChangeDestination)` et `NavigationRoute` (T9) ← T12.
- `SettingsViewModel(..., speech)` (T11) ← test T11.

### 4. Risques connus (à surveiller pendant l'exécution)

- La fixture Valhalla FOSSGIS ne contient **ni `lanes` ni `sign`** : le format des voies est codé d'après les masques `kTurnLane*` et testé sur JSON synthétique ; à confirmer sur une réponse réelle d'un Valhalla récent.
- Format exact de `edge.speed_limit` dans `trace_attributes` (entier km/h, 0 ou chaîne pour « inconnue/illimitée ») : le décodage est tolérant (1..254 sinon `null`).
- `TextToSpeech` : voix française parfois absente sur l'appareil → annonces muettes signalées par le log `RoadlySpeech` ; vérifier en Task 12.
- Android 14+ : `startForeground(type location)` exige la permission de localisation accordée et l'application au premier plan ; les deux cas d'échec sont interceptés (guidage sans notification).
- Écart de 2 % toléré entre la longueur haversine du tracé et `summary.length` Valhalla (test `cumulative distances…`) : si dépassé, relever l'écart avant toute correction.
