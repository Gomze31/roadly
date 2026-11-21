# Roadly AI (lot AI-1) — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter à Roadly un copilote vocal Gemini Live qui converse en français et exécute réellement les fonctions de Roadly (guidage, itinéraires, services, carburant, communauté, véhicule) sans jamais gêner le guidage GPS.

**Architecture:** Nouveau module `core:assistant` (contrat fournisseur neutre, client WebSocket Gemini Live, registre d'outils avec confirmation vérifiée par l'app, audio micro/haut-parleur, contrôleur à états) et `feature:assistant` (bouton micro, fenêtre compacte). Un refactor ciblé de `core:navigation` (`GuidanceActions`, `SpeechService.speaking`, `AppNavigator`) donne à l'IA et à l'UI le même code. Aucun module GPS ne dépend de l'assistant.

**Tech Stack:** Kotlin 2.2.10, Android (minSdk 29, compileSdk 36, JVM 17), Compose, Hilt 2.58, OkHttp 4.12 (WebSocket), kotlinx-serialization 1.8.1 (`JsonObject`), DataStore Preferences, Android Keystore, `AudioRecord` / `AudioTrack`. Tests : JUnit4, coroutines-test, turbine, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-10-31-roadly-ai-voice-copilot-design.md`

## Global Constraints

- Aucune nouvelle bibliothèque tierce : seulement le catalogue `gradle/libs.versions.toml` existant (`androidx-lifecycle-process` y est déjà).
- Modèle `models/gemini-3.8-live`, endpoint `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=…`.
- Clé : `GEMINI_API_KEY` dans `local.properties` (ignoré par Git — ne jamais l'ajouter, ne jamais écrire la clé dans un fichier suivi, un log ou un test).
- Le client WebSocket de l'assistant est un `OkHttpClient` **dédié** (sans les intercepteurs de `NetworkModule`) : l'URL contient la clé et ne doit jamais être journalisée.
- `core:navigation`, `core:routing`, `core:data`, `core:location` et les écrans GPS ne dépendent pas de `core:assistant` ni de `feature:assistant`.
- Textes UI en français dans `res/values/strings.xml` ; pas de `Color(0x…)` brut dans les écrans (`docs/DESIGN-SYSTEM.md`).
- Fenêtre compacte : 2 lignes de texte maximum (1 utilisateur + 1 IA).
- Messages d'erreur vocaux toujours via `SpeechService.speak(text, flush = false)` : jamais de `flush = true` depuis l'assistant.
- Build (outil PowerShell, pas Bash) : `.\gradlew.bat :app:assembleDebug --offline -q`.
- Tests : `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q` (276 tests verts au départ).
- Commits : un par tâche, message `feat(assistant): …` / `refactor(navigation): …`, terminé par `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Écarts assumés par rapport à la spec : `AppNavigator` vit dans `core:navigation` (pas `core:common`, module JVM sans Hilt) ; `VoiceAssistantProvider.connect` renvoie `Connection` (`Open` / `Refused`) au lieu d'`Outcome` pour garder les erreurs propres à l'assistant ; la section Réglages a son propre `AssistantSettingsViewModel`.

## Carte des fichiers

```
core/navigation/src/main/java/fr/roadly/core/navigation/
  SpeechService.kt                 (mod) + val speaking: StateFlow<Boolean>
  AndroidSpeechService.kt          (mod) publie speaking
  testing/FakeSpeechService.kt     (mod) speaking pilotable
  AppNavigator.kt                  (new) NavRequest + canal unique vers RoadlyNavHost
  GuidanceActions.kt               (new) démarrer / arrêts / alternatives / préférences / recalcul
  ReportsAhead.kt                  (new) projection des signalements devant soi (extrait du VM)
core/navigation/src/test/.../GuidanceActionsTest.kt, AppNavigatorTest.kt (new)

core/assistant/                    (new module)
  build.gradle.kts, src/main/AndroidManifest.xml (RECORD_AUDIO)
  src/main/java/fr/roadly/core/assistant/
    provider/VoiceAssistant.kt     contrat : VoiceAssistantProvider, VoiceSession, Connection, SessionConfig, AssistantEvent, AssistantError
    provider/ToolSchema.kt         ToolSchema, ToolParam, ParamType, ToolCall, ToolResult
    gemini/GeminiLiveProtocol.kt   JSON sortant / parsing entrant
    gemini/GeminiLiveProvider.kt   WebSocket OkHttp + GeminiVoiceSession
    tools/AssistantTool.kt         AssistantTool, ToolOutput, ToolContext, ToolJson, ToolArgs
    tools/PendingActions.kt        actions à confirmer + Affirmation
    tools/ToolRegistry.kt          dispatch, validation, consentement, plafond
    tools/ToolMemory.kt            ToolPlaces, ToolRoutes, AssistantCards
    tools/DestinationResolver.kt
    tools/NavigationTools.kt       statut, instruction, distance, arrivée, démarrer, changer, arrêter, recalculer, fin
    tools/RouteTools.kt            péages, autoroutes, alternatives, choix, heure d'arrivée, arrêts
    tools/ServiceTools.kt          services proches, carburant, coût, trafic, signalements, véhicule
    tools/AssistantToolsModule.kt  multibinding Hilt
    audio/AudioPorts.kt            AudioInput, AudioOutput, AudioFocus, FocusChange, Pcm
    audio/AndroidMicCapture.kt, audio/AndroidVoicePlayback.kt, audio/AndroidAssistantAudioFocus.kt
    settings/AssistantSettingsRepository.kt  (DataStore)
    settings/ApiKeyProvider.kt, settings/KeystoreSecretCipher.kt, settings/UsageMeter.kt, settings/AssistantKeyVerifier.kt
    AssistantController.kt, SystemPrompt.kt, AssistantMessages.kt, AssistantModule.kt
    testing/Fakes.kt               FakeVoiceProvider, FakeVoiceSession, FakeAudioInput, FakeAudioOutput, FakeAudioFocus, FakeAssistantSettingsRepository
  src/test/java/fr/roadly/core/assistant/…  (tests JVM)

feature/assistant/                 (new module) AssistantViewModel, AssistantMicButton, AssistantOverlay, ConsentDialog, strings
feature/settings/…/AssistantGroup.kt, AssistantSettingsViewModel.kt (new) ; SettingsScreen.kt (mod)
feature/home/…/HomeScreen.kt, HomeNavigation.kt (mod) slots assistantButton / assistantPanel
feature/navigation/…/NavigationScreen.kt, NavigationNavigation.kt, NavigationViewModel.kt (mod)
feature/route/…/RouteViewModel.kt (mod) délègue à GuidanceActions
app/…/RoadlyNavHost.kt, MainActivity.kt, RoadlyApp.kt, build.gradle.kts ; settings.gradle.kts (mod)
```

---

### Task 1: `SpeechService.speaking` — savoir quand le GPS parle

**Files:**
- Modify: `core/navigation/src/main/java/fr/roadly/core/navigation/SpeechService.kt`
- Modify: `core/navigation/src/main/java/fr/roadly/core/navigation/AndroidSpeechService.kt`
- Modify: `core/navigation/src/main/java/fr/roadly/core/navigation/testing/FakeSpeechService.kt`
- Modify: toute autre implémentation de `SpeechService` (chercher `: SpeechService` dans `**/src/test/**` et `**/src/main/**`)
- Test: `core/navigation/src/test/java/fr/roadly/core/navigation/FakeSpeechServiceTest.kt`

**Interfaces:**
- Produces: `SpeechService.speaking: StateFlow<Boolean>` ; `FakeSpeechService.setSpeaking(value: Boolean)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.navigation.testing.FakeSpeechService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSpeechServiceTest {
    @Test fun `speaking is observable and driven by the test`() {
        val s = FakeSpeechService()
        assertFalse(s.speaking.value)
        s.setSpeaking(true)
        assertTrue(s.speaking.value)
        s.stop()
        assertFalse("stop() coupe la parole en cours", s.speaking.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --tests "*FakeSpeechServiceTest" --offline -q`
Expected: FAIL — `Unresolved reference: speaking`.

- [ ] **Step 3: Implement**

`SpeechService.kt` — ajouter dans l'interface (import `kotlinx.coroutines.flow.StateFlow`) :

```kotlin
    /** Vrai tant qu'une annonce est en cours ou en file côté moteur (focus audio tenu) ; observé par l'assistant pour lui céder la parole. */
    val speaking: StateFlow<Boolean>
```

`AndroidSpeechService.kt` — imports `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.asStateFlow`, puis :

```kotlin
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
```

Dans `stop()`, après `active.clear()` dans le bloc `synchronized` : rien ; après `audio.abandonAudioFocusRequest(focusRequest)` ajouter `_speaking.value = false`.
Dans `say()`, remplacer la ligne `if (firstActive) audio.requestAudioFocus(focusRequest)` par :

```kotlin
        if (firstActive) audio.requestAudioFocus(focusRequest)
        _speaking.value = true
```

Dans `finished()`, remplacer `if (abandon) audio.abandonAudioFocusRequest(focusRequest)` par :

```kotlin
        if (abandon) {
            audio.abandonAudioFocusRequest(focusRequest)
            _speaking.value = false
        }
```

`FakeSpeechService.kt` — imports `MutableStateFlow`, `StateFlow`, puis :

```kotlin
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking
    /** Simule le début / la fin d'une annonce GPS. */
    fun setSpeaking(value: Boolean) { _speaking.value = value }
```

et dans `stop()` : `override fun stop() { stops++; _speaking.value = false }`.
Enregistrer aussi le mode de chaque annonce (utilisé par les tests de l'assistant : jamais de `flush`) :

```kotlin
    /** `flush` de chaque appel à [speak], dans l'ordre. */
    val flushes = mutableListOf<Boolean>()
    override fun speak(text: String, flush: Boolean) { spoken += text; flushes += flush }
```

Toute autre implémentation trouvée (objets anonymes de test) : ajouter `override val speaking = kotlinx.coroutines.flow.MutableStateFlow(false)`.

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q` puis `.\gradlew.bat testDebugUnitTest --offline -q`
Expected: PASS (tout le projet compile, aucun test cassé).

- [ ] **Step 5: Commit**

```bash
git add core/navigation
git commit -m "feat(navigation): SpeechService.speaking — annonce GPS observable"
```

---

### Task 2: `AppNavigator` — l'IA peut ouvrir un écran

**Files:**
- Create: `core/navigation/src/main/java/fr/roadly/core/navigation/AppNavigator.kt`
- Modify: `app/src/main/java/fr/roadly/app/RoadlyNavHost.kt`, `app/src/main/java/fr/roadly/app/MainActivity.kt`
- Test: `core/navigation/src/test/java/fr/roadly/core/navigation/AppNavigatorTest.kt`

**Interfaces:**
- Produces: `sealed interface NavRequest { data class OpenSearch(val category: String?); data object OpenGuidance; data object OpenSettings }` ; `@Singleton class AppNavigator { val requests: Flow<NavRequest>; fun navigate(request: NavRequest) }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package fr.roadly.core.navigation

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {
    @Test fun `a request sent before anyone listens is delivered once`() = runTest {
        val nav = AppNavigator()
        nav.navigate(NavRequest.OpenSearch("FUEL"))
        nav.requests.test {
            assertEquals(NavRequest.OpenSearch("FUEL"), awaitItem())
            nav.navigate(NavRequest.OpenGuidance)
            assertEquals(NavRequest.OpenGuidance, awaitItem())
            expectNoEvents()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --tests "*AppNavigatorTest" --offline -q`
Expected: FAIL — `Unresolved reference: AppNavigator`.

- [ ] **Step 3: Implement**

```kotlin
package fr.roadly.core.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Écran demandé hors UI (assistant) ; [OpenSearch.category] = `PoiCategory.name` ou null. */
sealed interface NavRequest {
    data class OpenSearch(val category: String?) : NavRequest
    data object OpenGuidance : NavRequest
    data object OpenSettings : NavRequest
}

/** Canal à consommateur unique (le `RoadlyNavHost`) : une demande émise sans écouteur attend, puis est livrée une seule fois. */
@Singleton
class AppNavigator @Inject constructor() {
    private val channel = Channel<NavRequest>(Channel.BUFFERED)
    val requests: Flow<NavRequest> = channel.receiveAsFlow()
    fun navigate(request: NavRequest) { channel.trySend(request) }
}
```

`RoadlyNavHost.kt` — ajouter le paramètre `navRequests: Flow<NavRequest> = emptyFlow()` (imports `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.emptyFlow`, `fr.roadly.core.navigation.NavRequest`) et, après le `LaunchedEffect(openNavigationRequested)` existant :

```kotlin
    // Demandes de l'assistant (ouvrir la recherche filtrée, le guidage, les réglages).
    LaunchedEffect(navRequests) {
        navRequests.collect { r ->
            when (r) {
                is NavRequest.OpenSearch -> nav.navigate(SearchRoute(category = r.category))
                NavRequest.OpenGuidance -> nav.navigate(NavigationRoute) { popUpTo(HomeRoute); launchSingleTop = true }
                NavRequest.OpenSettings -> nav.navigate(SettingsRoute) { launchSingleTop = true }
            }
        }
    }
```

`MainActivity.kt` — la classe est `@AndroidEntryPoint` ; ajouter `@Inject lateinit var navigator: AppNavigator` (import `fr.roadly.core.navigation.AppNavigator`, `javax.inject.Inject`) et passer `navRequests = navigator.requests` à `RoadlyNavHost(...)`.

- [ ] **Step 4: Run tests + build**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q` puis `.\gradlew.bat :app:assembleDebug --offline -q`
Expected: PASS, build OK.

- [ ] **Step 5: Commit**

```bash
git add core/navigation app
git commit -m "feat(navigation): AppNavigator — ouverture d'écran demandée hors UI"
```

---

### Task 3: `GuidanceActions` — un seul code pour l'UI et l'IA

**Files:**
- Create: `core/navigation/src/main/java/fr/roadly/core/navigation/GuidanceActions.kt`
- Modify: `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt` (`loadAlternatives`, `addStop`, `switchTo`, constructeur)
- Modify: `feature/route/src/main/java/fr/roadly/feature/route/RouteViewModel.kt` (`startGuidance`, constructeur)
- Modify: `feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt`, `feature/route/src/test/java/fr/roadly/feature/route/RouteViewModelTest.kt` (construction des VM)
- Test: `core/navigation/src/test/java/fr/roadly/core/navigation/GuidanceActionsTest.kt`

**Interfaces:**
- Consumes: `NavigationSession`, `RouteAlternativesUseCase`, `RoutingProvider`, `LocationRepository`, `UserPreferencesRepository`, `VehicleProfileRepository`, `NavigationClock`, `FuelCostCalculator`, `StopOrdering.MAX_STOPS`, `RouteGeometry`.
- Produces (utilisé par les tâches 9-10 et les VM) :
  - `data class GuidanceStop(val place: Place, val alongM: Double) { val id: String }`
  - `data class PreferenceChange(val before: RouteOption?, val after: RouteOption?)`
  - `class GuidanceActions` : `val destinationName: String?`, `fun remainingStops(): List<GuidanceStop>`, `fun start(option: RouteOption, preferences: RoutePreferences, vehicle: VehicleProfile?, stops: List<Place> = emptyList(), destinationName: String? = null)`, `suspend fun startTo(destination: Place): Outcome<RouteOption>`, `suspend fun alternativesFromHere(): Outcome<List<RouteOption>>`, `suspend fun select(option: RouteOption)`, `suspend fun recalculate(): Outcome<RouteOption>`, `suspend fun addStop(place: Place): Outcome<RouteOption>`, `suspend fun removeStop(id: String): Outcome<RouteOption>`, `suspend fun applyPreferences(value: RoutePreferences): Outcome<PreferenceChange>`, `fun stop()`.
  - `AppError.Unknown("max_stops")` quand 10 arrêts sont déjà prévus.

- [ ] **Step 1: Write the failing tests**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceActionsTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val requests = mutableListOf<RouteRequest>()
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            requests += request
            return Outcome.Success(listOf(route.copy(id = "r${requests.size}", durationSeconds = route.durationSeconds + 60.0 * requests.size)))
        }
    }
    private val vehicles = object : VehicleProfileRepository {
        override val profile = flowOf<VehicleProfile?>(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }
    private fun place(id: String, p: GeoPoint) = Place(id, id, null, p, PlaceKind.POI, "test")

    private fun TestScope.fixture(): Pair<NavigationSession, GuidanceActions> {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        val clock = NavigationClock { testScheduler.currentTime }
        val session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), clock, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
        return session to GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicles, clock)
    }

    private suspend fun TestScope.guideAt(actions: GuidanceActions, along: Double, stops: List<Place> = emptyList()) {
        actions.start(RouteOption(route, "Le plus rapide", 0L, null, false), RoutePreferences(), null, stops, destinationName = "Arrivée")
        runCurrent()
        location.emit(LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L))
        runCurrent()
    }

    @Test fun `adding a stop reroutes from the snapped position via the stop to the same destination`() = runTest {
        val (session, actions) = fixture()
        guideAt(actions, 300.0)
        val stop = place("s1", tracker.pointAt(800.0))
        val r = actions.addStop(stop); runCurrent()
        assertTrue(r is Outcome.Success)
        val req = requests.last()
        assertEquals((session.state.value as NavigationState.Guiding).route.id, (r as Outcome.Success).value.route.id)
        assertEquals(listOf(stop.point), req.waypoints)
        assertEquals(route.geometry.last(), req.destination)
        assertEquals(listOf("s1"), actions.remainingStops().map { it.id })
        assertEquals("Arrivée", actions.destinationName)
    }

    @Test fun `a stop behind the vehicle is no longer remaining`() = runTest {
        val (_, actions) = fixture()
        guideAt(actions, 900.0, stops = listOf(place("passed", tracker.pointAt(200.0)), place("ahead", tracker.pointAt(1_300.0))))
        assertEquals(listOf("ahead"), actions.remainingStops().map { it.id })
    }

    @Test fun `removing a stop keeps the other remaining stops`() = runTest {
        val (_, actions) = fixture()
        guideAt(actions, 100.0, stops = listOf(place("a", tracker.pointAt(500.0)), place("b", tracker.pointAt(900.0))))
        actions.removeStop("a"); runCurrent()
        assertEquals(listOf(tracker.pointAt(900.0)), requests.last().waypoints)
        assertTrue(actions.removeStop("unknown") is Outcome.Failure)
    }

    @Test fun `applying preferences persists them, reroutes and reports before and after`() = runTest {
        val (_, actions) = fixture()
        guideAt(actions, 300.0)
        val r = actions.applyPreferences(RoutePreferences(avoidTolls = true)); runCurrent()
        val change = (r as Outcome.Success).value
        assertEquals(route.id, change.before!!.route.id)
        assertEquals(true, requests.last().preferences.avoidTolls)
        assertEquals(true, prefs.routePreferences.first().avoidTolls)
        assertTrue(change.after!!.route.durationSeconds > 0)
    }

    @Test fun `alternatives are computed from the snapped position with remaining stops`() = runTest {
        val (session, actions) = fixture()
        guideAt(actions, 300.0, stops = listOf(place("a", tracker.pointAt(900.0))))
        val r = actions.alternativesFromHere()
        assertTrue(r is Outcome.Success)
        assertEquals((session.state.value as NavigationState.Guiding).progress.snapped, requests.last().origin)
        assertEquals(listOf(tracker.pointAt(900.0)), requests.last().waypoints)
    }

    @Test fun `without guidance, route changes fail with NoResult`() = runTest {
        val (_, actions) = fixture()
        assertEquals(Outcome.Failure(AppError.NoResult), actions.recalculate())
        assertEquals(Outcome.Failure(AppError.NoResult), actions.addStop(place("x", GeoPoint(43.6, 1.44))))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --tests "*GuidanceActionsTest" --offline -q`
Expected: FAIL — `Unresolved reference: GuidanceActions`.

- [ ] **Step 3: Implement `GuidanceActions.kt`**

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.Place
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.routing.FuelCostCalculator
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import fr.roadly.core.routing.StopOrdering
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Arrêt de la course en cours ; [alongM] = abscisse sur le tracé courant. */
data class GuidanceStop(val place: Place, val alongM: Double) { val id: String get() = place.id }

/** Résultat d'un changement de préférences pendant un guidage ; les deux sont nuls hors guidage. */
data class PreferenceChange(val before: RouteOption?, val after: RouteOption?)

/**
 * Actions de guidage partagées par l'UI et l'assistant : démarrer vers un lieu, arrêts (≤ 10), alternatives,
 * préférences, recalcul. Garde la liste des arrêts de la course (la session ne connaît que le tracé).
 */
@Singleton
class GuidanceActions @Inject constructor(
    private val session: NavigationSession,
    private val alternatives: RouteAlternativesUseCase,
    private val routing: RoutingProvider,
    private val location: LocationRepository,
    private val prefs: UserPreferencesRepository,
    private val vehicles: VehicleProfileRepository,
    private val clock: NavigationClock,
) {
    private var stops: List<GuidanceStop> = emptyList()

    /** Nom de la destination de la course en cours, si connu. */
    var destinationName: String? = null
        private set

    private val guiding: NavigationState.Guiding? get() = session.state.value as? NavigationState.Guiding

    /** Arrêts encore devant soi, dans l'ordre du tracé. */
    fun remainingStops(): List<GuidanceStop> {
        val g = guiding ?: return emptyList()
        return stops.filter { it.alongM > g.progress.distanceAlongM + PASSED_MARGIN_M }.sortedBy { it.alongM }
    }

    fun start(option: RouteOption, preferences: RoutePreferences, vehicle: VehicleProfile?, stops: List<Place> = emptyList(), destinationName: String? = null) {
        val geo = RouteGeometry(option.route.geometry)
        this.stops = stops.take(StopOrdering.MAX_STOPS).map { GuidanceStop(it, geo.project(it.point)?.alongM ?: Double.MAX_VALUE) }
        if (destinationName != null) this.destinationName = destinationName
        session.start(option, preferences, vehicle)
    }

    /** Itinéraire le plus rapide depuis la position (projetée si guidage, sinon dernier fix) vers [destination]. */
    suspend fun startTo(destination: Place): Outcome<RouteOption> {
        val origin = guiding?.progress?.snapped ?: location.lastKnown?.point ?: return Outcome.Failure(AppError.PermissionDenied)
        val p = prefs.routePreferences.first()
        val v = vehicles.profile.first()
        return when (val r = alternatives(origin, destination.point, v, clock.nowMs(), p)) {
            is Outcome.Success -> {
                val best = r.value.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: r.value.first()
                start(best, p, v, emptyList(), destination.name)
                Outcome.Success(best)
            }
            is Outcome.Failure -> r
        }
    }

    suspend fun alternativesFromHere(): Outcome<List<RouteOption>> {
        val g = guiding ?: return Outcome.Failure(AppError.NoResult)
        return alternatives(g.progress.snapped, g.route.geometry.last(), vehicles.profile.first(), clock.nowMs(), prefs.routePreferences.first(), remainingStops().map { it.place.point })
    }

    /** Bascule sur [option] en gardant les arrêts restants. */
    suspend fun select(option: RouteOption) =
        start(option, prefs.routePreferences.first(), vehicles.profile.first(), remainingStops().map { it.place })

    suspend fun recalculate(): Outcome<RouteOption> = reroute(remainingStops().map { it.place }, label = null)

    /** Insère [place] dans l'ordre du tracé courant parmi les arrêts restants, puis recalcule. */
    suspend fun addStop(place: Place): Outcome<RouteOption> {
        val g = guiding ?: return Outcome.Failure(AppError.NoResult)
        val current = remainingStops().map { it.place }
        if (current.size >= StopOrdering.MAX_STOPS) return Outcome.Failure(AppError.Unknown("max_stops"))
        val geo = RouteGeometry(g.route.geometry)
        val ordered = (current + place).sortedBy { geo.project(it.point)?.alongM ?: Double.MAX_VALUE }
        return reroute(ordered, "Via ${place.name}")
    }

    suspend fun removeStop(id: String): Outcome<RouteOption> {
        val current = remainingStops().map { it.place }
        if (current.none { it.id == id }) return Outcome.Failure(AppError.NoResult)
        return reroute(current.filterNot { it.id == id }, label = null)
    }

    /** Enregistre [value] ; en guidage, recalcule avec elles et renvoie l'avant / après. */
    suspend fun applyPreferences(value: RoutePreferences): Outcome<PreferenceChange> {
        prefs.setRoutePreferences(value)
        val g = guiding ?: return Outcome.Success(PreferenceChange(null, null))
        val before = RouteOption(g.route, "Itinéraire précédent", g.etaEpochMs, null, sameAsFastest = false)
        return when (val r = reroute(remainingStops().map { it.place }, label = null, preferences = value)) {
            is Outcome.Success -> Outcome.Success(PreferenceChange(before, r.value))
            is Outcome.Failure -> r
        }
    }

    fun stop() {
        stops = emptyList()
        destinationName = null
        session.stop()
    }

    private suspend fun reroute(waypoints: List<Place>, label: String?, preferences: RoutePreferences? = null): Outcome<RouteOption> {
        val g = guiding ?: return Outcome.Failure(AppError.NoResult)
        val p = preferences ?: prefs.routePreferences.first()
        val v = vehicles.profile.first()
        val request = RouteRequest(
            origin = g.progress.snapped, destination = g.route.geometry.last(), waypoints = waypoints.map { it.point },
            costing = g.route.costing, preferences = p, originHeadingDeg = g.progress.bearingDeg, vehicle = v,
        )
        return when (val r = routing.route(request)) {
            is Outcome.Success -> {
                val route: Route = r.value.firstOrNull() ?: return Outcome.Failure(AppError.NoResult)
                val option = RouteOption(
                    route.copy(costing = g.route.costing), label ?: "Itinéraire recalculé",
                    clock.nowMs() + (route.durationSeconds * 1000).toLong(),
                    v?.let { FuelCostCalculator.estimate(route.distanceMeters, it) }, sameAsFastest = false,
                )
                start(option, p, v, waypoints)
                Outcome.Success(option)
            }
            is Outcome.Failure -> r
        }
    }

    private companion object {
        /** Un arrêt à moins de 30 m derrière la position projetée est considéré comme desservi. */
        const val PASSED_MARGIN_M = 30.0
    }
}
```

Vérifier que `FuelCostCalculator` est un `object` public de `fr.roadly.core.routing` (`FuelCostCalculator.kt`) et que `RouteOption` a bien les paramètres `(route, label, arrivalEpochMs, cost, sameAsFastest)` ; adapter les noms si besoin.

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :core:navigation:testDebugUnitTest --tests "*GuidanceActionsTest" --offline -q`
Expected: PASS (6 tests).

- [ ] **Step 5: Delegate the ViewModels to `GuidanceActions`**

`NavigationViewModel` — nouveau constructeur (retirer `alternatives`, `routing`, `vehicleRepo`, et le champ `vehicle`) :

```kotlin
@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val session: NavigationSession,
    prefs: UserPreferencesRepository,
    config: ProviderConfigRepository,
    private val guidance: GuidanceActions,
    private val community: CommunityRepository,
) : ViewModel() {
```

Remplacer les corps :

```kotlin
    fun loadAlternatives() {
        val g = session.state.value as? NavigationState.Guiding ?: return
        alternativesJob?.cancel()
        _alternatives.value = AlternativesUi.Loading
        alternativesJob = viewModelScope.launch {
            when (val r = guidance.alternativesFromHere()) {
                is Outcome.Success -> _alternatives.value = AlternativesUi.Content(r.value, g.route.id)
                is Outcome.Failure -> _alternatives.value = AlternativesUi.Error(r.error)
            }
        }
    }

    fun addStop(place: Place) {
        if (session.state.value !is NavigationState.Guiding || _addingStop.value) return
        viewModelScope.launch {
            _addingStop.value = true
            (guidance.addStop(place) as? Outcome.Failure)?.let { _alternatives.value = AlternativesUi.Error(it.error) }
            _addingStop.value = false
        }
    }

    fun switchTo(option: RouteOption) {
        viewModelScope.launch { guidance.select(option) }
        dismissAlternatives()
    }

    fun stop() = guidance.stop()
```

Supprimer `routePreferences` s'il n'est plus utilisé, et les imports devenus inutiles (`RouteRequest`, `RouteAlternativesUseCase`, `RoutingProvider`, `VehicleProfileRepository`, `RoutePreferences`).

`RouteViewModel` — remplacer `private val session: NavigationSession` par `private val guidance: GuidanceActions` (import `fr.roadly.core.navigation.GuidanceActions`) et dans `startGuidance()` :

```kotlin
        guidance.start(option, computedWith, computedVehicle, stops.value, destName)
```

Tests des VM : construire `GuidanceActions(s, RouteAlternativesUseCase(routing), routing, location, prefs, fakeVehicle, NavigationClock { testScheduler.currentTime })` et le passer à la place de `RouteAlternativesUseCase(...), routing, fakeVehicle`. Dans `NavigationViewModelTest`, le helper devient :

```kotlin
    private fun TestScope.NavigationViewModel(s: NavigationSession, p: FakeUserPreferencesRepository, c: ProviderConfigRepository, r: RoutingProvider = routing) =
        NavigationViewModel(s, p, c, GuidanceActions(s, RouteAlternativesUseCase(r), r, location, p, fakeVehicle, NavigationClock { testScheduler.currentTime }), NoopCommunity)
```

et l'appel du test « alternatives… switching… » devient `NavigationViewModel(s, prefs, config, okRouting)`. Dans `RouteViewModelTest`, remplacer l'argument session par `GuidanceActions(session, …)` de la même façon (lire le fichier pour reprendre ses fakes). Le test « switching restarts guidance » doit rester vert : `switchTo` lance une coroutine, ajouter `runCurrent()` après `vm.switchTo(...)` s'il manque.

- [ ] **Step 6: Run all tests + build**

Run: `.\gradlew.bat testDebugUnitTest --offline -q` puis `.\gradlew.bat :app:assembleDebug --offline -q`
Expected: PASS (276 + 8 nouveaux), build OK.

- [ ] **Step 7: Commit**

```bash
git add core/navigation feature/navigation feature/route
git commit -m "refactor(navigation): GuidanceActions partagé (arrêts suivis, alternatives, préférences, recalcul)"
```

---

### Task 4: Module `core:assistant` — contrat fournisseur et protocole Gemini Live

**Files:**
- Modify: `settings.gradle.kts` (ajouter `":core:assistant"`)
- Create: `core/assistant/build.gradle.kts`, `core/assistant/src/main/AndroidManifest.xml`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/provider/VoiceAssistant.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/provider/ToolSchema.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/gemini/GeminiLiveProtocol.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/gemini/GeminiLiveProtocolTest.kt`

**Interfaces:**
- Produces :
  - `enum class ParamType { STRING, NUMBER, INTEGER, BOOLEAN }`, `data class ToolParam(name, type, description, required = true, enumValues: List<String>? = null)`, `data class ToolSchema(name, description, parameters: List<ToolParam> = emptyList())`, `data class ToolCall(id: String, name: String, args: JsonObject)`, `data class ToolResult(id: String, name: String, payload: JsonObject)`.
  - `enum class AssistantError { NO_KEY, INVALID_KEY, QUOTA, DAILY_CAP, NETWORK, TIMEOUT, PROTOCOL, MIC_UNAVAILABLE, DISABLED }`
  - `sealed interface AssistantEvent` : `Ready`, `UserTranscript(text)`, `AssistantTranscript(text)`, `AudioChunk(pcm24k: ByteArray)`, `ToolCalls(calls)`, `ToolCallsCancelled(ids)`, `Interrupted`, `TurnComplete`, `ResumptionHandle(handle)`, `GoAway(timeLeftMs)`, `Usage(totalTokens)`, `Failed(error)`, `Closed`.
  - `data class SessionConfig(apiKey, systemInstruction, tools, voice = "Kore", resumptionHandle: String? = null)`.
  - `interface VoiceSession { events: Flow<AssistantEvent>; sendAudio(ByteArray); endAudioStream(); sendText(String); sendToolResults(List<ToolResult>); close() }`.
  - `sealed interface Connection { data class Open(session: VoiceSession); data class Refused(error: AssistantError) }`.
  - `interface VoiceAssistantProvider { val id: String; suspend fun connect(config: SessionConfig): Connection }`.
  - `internal object GeminiLiveProtocol` : `MODEL`, `setup(config): String`, `declaration(tool): JsonObject`, `audio(pcm): String`, `audioStreamEnd(): String`, `text(text): String`, `toolResponse(results): String`, `parse(json, text): List<AssistantEvent>`, `parseDurationMs(JsonElement?): Long`.

- [ ] **Step 1: Module scaffolding**

`settings.gradle.kts` : ajouter `":core:assistant",` après `":core:navigation",`.

`core/assistant/build.gradle.kts` :

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Clé Gemini personnelle : lue dans local.properties (ignoré par Git), jamais dans un fichier suivi.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "fr.roadly.core.assistant"
    compileSdk = 36
    defaultConfig {
        minSdk = 29
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":core:navigation"))
    implementation(project(":core:geocoding"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}
```

`core/assistant/src/main/AndroidManifest.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
</manifest>
```

- [ ] **Step 2: Contract files**

`provider/ToolSchema.kt` :

```kotlin
package fr.roadly.core.assistant.provider

import kotlinx.serialization.json.JsonObject

enum class ParamType { STRING, NUMBER, INTEGER, BOOLEAN }

/** Paramètre d'outil, indépendant du fournisseur ; [enumValues] restreint une chaîne à une liste. */
data class ToolParam(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = true,
    val enumValues: List<String>? = null,
)

/** Déclaration d'outil neutre ; chaque fournisseur la traduit dans son format. */
data class ToolSchema(val name: String, val description: String, val parameters: List<ToolParam> = emptyList())

data class ToolCall(val id: String, val name: String, val args: JsonObject)

/** Réponse à un appel d'outil : objet JSON court et factuel (`status`, données). */
data class ToolResult(val id: String, val name: String, val payload: JsonObject)
```

`provider/VoiceAssistant.kt` :

```kotlin
package fr.roadly.core.assistant.provider

import kotlinx.coroutines.flow.Flow

enum class AssistantError { NO_KEY, INVALID_KEY, QUOTA, DAILY_CAP, NETWORK, TIMEOUT, PROTOCOL, MIC_UNAVAILABLE, DISABLED }

/** Événements d'une session vocale, dans l'ordre de réception. Les transcriptions sont incrémentales. */
sealed interface AssistantEvent {
    /** Le serveur a accepté la configuration ; interne au fournisseur, jamais transmis au contrôleur. */
    data object Ready : AssistantEvent
    data class UserTranscript(val text: String) : AssistantEvent
    data class AssistantTranscript(val text: String) : AssistantEvent
    class AudioChunk(val pcm24k: ByteArray) : AssistantEvent
    data class ToolCalls(val calls: List<ToolCall>) : AssistantEvent
    data class ToolCallsCancelled(val ids: List<String>) : AssistantEvent
    data object Interrupted : AssistantEvent
    data object TurnComplete : AssistantEvent
    data class ResumptionHandle(val handle: String) : AssistantEvent
    data class GoAway(val timeLeftMs: Long) : AssistantEvent
    data class Usage(val totalTokens: Int) : AssistantEvent
    data class Failed(val error: AssistantError) : AssistantEvent
    data object Closed : AssistantEvent
}

data class SessionConfig(
    val apiKey: String,
    val systemInstruction: String,
    val tools: List<ToolSchema>,
    val voice: String = "Kore",
    val resumptionHandle: String? = null,
)

interface VoiceSession {
    val events: Flow<AssistantEvent>
    /** PCM 16 bits LE, 16 kHz, mono. */
    fun sendAudio(pcm16k: ByteArray)
    /** Micro suspendu (annonce GPS, pause) : le serveur vide son tampon de détection. */
    fun endAudioStream()
    /** Tour texte (ex. résultat d'une puce Confirmer/Annuler) ; interrompt la génération en cours. */
    fun sendText(text: String)
    fun sendToolResults(results: List<ToolResult>)
    fun close()
}

sealed interface Connection {
    data class Open(val session: VoiceSession) : Connection
    data class Refused(val error: AssistantError) : Connection
}

interface VoiceAssistantProvider {
    val id: String
    suspend fun connect(config: SessionConfig): Connection
}
```

- [ ] **Step 3: Write the failing protocol tests**

```kotlin
package fr.roadly.core.assistant.gemini

import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GeminiLiveProtocolTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val tool = ToolSchema("avoidTolls", "Éviter les péages", listOf(
        ToolParam("enabled", ParamType.BOOLEAN, "Activer"),
        ToolParam("mode", ParamType.STRING, "Mode", required = false, enumValues = listOf("A", "B")),
    ))

    @Test fun `setup carries model, audio modality, voice, tools, transcription, VAD and resumption handle`() {
        val setup = json.parseToJsonElement(GeminiLiveProtocol.setup(SessionConfig("k", "Règles", listOf(tool), "Kore", "h1"))).jsonObject["setup"]!!.jsonObject
        assertEquals("models/gemini-3.8-live", setup["model"]!!.jsonPrimitive.content)
        assertEquals("AUDIO", setup["generationConfig"]!!.jsonObject["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
        assertTrue(setup.toString().contains("\"voiceName\":\"Kore\""))
        assertEquals("Règles", setup["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val decl = setup["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject
        assertEquals("BLOCKING", decl["behavior"]!!.jsonPrimitive.content)
        assertEquals("BOOLEAN", decl["parameters"]!!.jsonObject["properties"]!!.jsonObject["enabled"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(listOf("enabled"), decl["parameters"]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(decl.toString().contains("\"enum\":[\"A\",\"B\"]"))
        assertTrue("inputAudioTranscription" in setup && "outputAudioTranscription" in setup)
        assertEquals("h1", setup["sessionResumption"]!!.jsonObject["handle"]!!.jsonPrimitive.content)
        assertTrue("automaticActivityDetection" in setup["realtimeInputConfig"]!!.jsonObject)
    }

    @Test fun `a tool without parameters has no parameters object`() {
        assertTrue("parameters" !in GeminiLiveProtocol.declaration(ToolSchema("stopNavigation", "Arrêter")))
    }

    @Test fun `audio is base64 PCM at 16 kHz`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val audio = json.parseToJsonElement(GeminiLiveProtocol.audio(pcm)).jsonObject["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertEquals("audio/pcm;rate=16000", audio["mimeType"]!!.jsonPrimitive.content)
        assertArrayEquals(pcm, Base64.getDecoder().decode(audio["data"]!!.jsonPrimitive.content))
    }

    @Test fun `tool response echoes id and name`() {
        val msg = GeminiLiveProtocol.toolResponse(listOf(ToolResult("c1", "getArrivalTime", buildJsonObject { put("status", "ok") })))
        val r = json.parseToJsonElement(msg).jsonObject["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray[0].jsonObject
        assertEquals("c1", r["id"]!!.jsonPrimitive.content)
        assertEquals("ok", r["response"]!!.jsonObject["status"]!!.jsonPrimitive.content)
    }

    @Test fun `server messages are mapped to events`() {
        val audio = Base64.getEncoder().encodeToString(byteArrayOf(9, 8))
        assertEquals(listOf(AssistantEvent.Ready), GeminiLiveProtocol.parse(json, """{"setupComplete":{}}"""))
        val content = GeminiLiveProtocol.parse(json, """{"serverContent":{"inputTranscription":{"text":"évite "},"outputTranscription":{"text":"D'accord"},"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"$audio"}}]},"turnComplete":true}}""")
        assertEquals(AssistantEvent.UserTranscript("évite "), content[0])
        assertEquals(AssistantEvent.AssistantTranscript("D'accord"), content[1])
        assertArrayEquals(byteArrayOf(9, 8), (content[2] as AssistantEvent.AudioChunk).pcm24k)
        assertEquals(AssistantEvent.TurnComplete, content[3])
        assertEquals(listOf(AssistantEvent.Interrupted), GeminiLiveProtocol.parse(json, """{"serverContent":{"interrupted":true}}"""))
        val call = GeminiLiveProtocol.parse(json, """{"toolCall":{"functionCalls":[{"id":"c1","name":"avoidTolls","args":{"enabled":true}}]}}""").single() as AssistantEvent.ToolCalls
        assertEquals("avoidTolls", call.calls.single().name)
        assertEquals("true", call.calls.single().args["enabled"]!!.jsonPrimitive.content)
        assertEquals(listOf(AssistantEvent.ToolCallsCancelled(listOf("c1"))), GeminiLiveProtocol.parse(json, """{"toolCallCancellation":{"ids":["c1"]}}"""))
        assertEquals(listOf(AssistantEvent.ResumptionHandle("h2")), GeminiLiveProtocol.parse(json, """{"sessionResumptionUpdate":{"newHandle":"h2","resumable":true}}"""))
        assertEquals(emptyList<AssistantEvent>(), GeminiLiveProtocol.parse(json, """{"sessionResumptionUpdate":{"newHandle":"h3","resumable":false}}"""))
        assertEquals(listOf(AssistantEvent.GoAway(12_500)), GeminiLiveProtocol.parse(json, """{"goAway":{"timeLeft":"12.5s"}}"""))
        assertEquals(listOf(AssistantEvent.Usage(321)), GeminiLiveProtocol.parse(json, """{"usageMetadata":{"totalTokenCount":321}}"""))
    }

    @Test fun `malformed messages are ignored`() {
        assertEquals(emptyList<AssistantEvent>(), GeminiLiveProtocol.parse(json, "not json"))
        assertEquals(emptyList<AssistantEvent>(), GeminiLiveProtocol.parse(json, """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"data":"%%%"}}]}}}"""))
    }
}
```

- [ ] **Step 4: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: FAIL — `Unresolved reference: GeminiLiveProtocol`.

- [ ] **Step 5: Implement `gemini/GeminiLiveProtocol.kt`**

```kotlin
package fr.roadly.core.assistant.gemini

import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolCall
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import java.util.Base64

/** Messages JSON de l'API Gemini Live (WebSocket `BidiGenerateContent`, v1beta). */
internal object GeminiLiveProtocol {
    const val MODEL = "models/gemini-3.8-live"

    fun setup(config: SessionConfig): String = buildJsonObject {
        putJsonObject("setup") {
            put("model", MODEL)
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("AUDIO") }
                putJsonObject("speechConfig") {
                    putJsonObject("voiceConfig") { putJsonObject("prebuiltVoiceConfig") { put("voiceName", config.voice) } }
                }
            }
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", config.systemInstruction) } } }
            if (config.tools.isNotEmpty()) putJsonArray("tools") {
                addJsonObject { putJsonArray("functionDeclarations") { config.tools.forEach { add(declaration(it)) } } }
            }
            putJsonObject("realtimeInputConfig") {
                putJsonObject("automaticActivityDetection") {
                    // Habitacle bruyant : début de parole moins sensible, silence de fin dans la plage recommandée (500-800 ms).
                    put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                    put("prefixPaddingMs", 200)
                    put("silenceDurationMs", 700)
                }
                put("activityHandling", "START_OF_ACTIVITY_INTERRUPTS")
            }
            putJsonObject("inputAudioTranscription") {}
            putJsonObject("outputAudioTranscription") {}
            putJsonObject("sessionResumption") { config.resumptionHandle?.let { put("handle", it) } }
            putJsonObject("contextWindowCompression") { putJsonObject("slidingWindow") {} }
        }
    }.toString()

    /** `behavior` explicite : `gemini-3.8-live` est NON_BLOCKING par défaut, Roadly attend chaque résultat. */
    fun declaration(tool: ToolSchema): JsonObject = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("behavior", "BLOCKING")
        if (tool.parameters.isNotEmpty()) putJsonObject("parameters") {
            put("type", "OBJECT")
            putJsonObject("properties") {
                tool.parameters.forEach { p ->
                    putJsonObject(p.name) {
                        put("type", p.type.name)
                        put("description", p.description)
                        p.enumValues?.let { values -> put("format", "enum"); putJsonArray("enum") { values.forEach { add(it) } } }
                    }
                }
            }
            putJsonArray("required") { tool.parameters.filter { it.required }.forEach { add(it.name) } }
        }
    }

    fun audio(pcm: ByteArray): String = buildJsonObject {
        putJsonObject("realtimeInput") {
            putJsonObject("audio") { put("data", Base64.getEncoder().encodeToString(pcm)); put("mimeType", "audio/pcm;rate=16000") }
        }
    }.toString()

    fun audioStreamEnd(): String = """{"realtimeInput":{"audioStreamEnd":true}}"""

    fun text(text: String): String = buildJsonObject {
        putJsonObject("clientContent") {
            putJsonArray("turns") { addJsonObject { put("role", "user"); putJsonArray("parts") { addJsonObject { put("text", text) } } } }
            put("turnComplete", true)
        }
    }.toString()

    fun toolResponse(results: List<ToolResult>): String = buildJsonObject {
        putJsonObject("toolResponse") {
            putJsonArray("functionResponses") {
                results.forEach { r -> addJsonObject { put("id", r.id); put("name", r.name); put("response", r.payload) } }
            }
        }
    }.toString()

    /** Un message serveur peut porter plusieurs événements ; un message illisible n'en produit aucun. */
    fun parse(json: Json, text: String): List<AssistantEvent> = try {
        val root = json.parseToJsonElement(text).jsonObject
        buildList {
            if ("setupComplete" in root) add(AssistantEvent.Ready)
            root["serverContent"]?.jsonObject?.let { sc ->
                sc["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { add(AssistantEvent.UserTranscript(it)) }
                sc["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { add(AssistantEvent.AssistantTranscript(it)) }
                sc["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                    part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull?.let {
                        add(AssistantEvent.AudioChunk(Base64.getDecoder().decode(it)))
                    }
                }
                if (sc["interrupted"]?.jsonPrimitive?.booleanOrNull == true) add(AssistantEvent.Interrupted)
                if (sc["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) add(AssistantEvent.TurnComplete)
            }
            root["toolCall"]?.jsonObject?.get("functionCalls")?.jsonArray?.let { calls ->
                add(AssistantEvent.ToolCalls(calls.mapNotNull { c ->
                    val o = c.jsonObject
                    val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    ToolCall(o["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), name, o["args"] as? JsonObject ?: JsonObject(emptyMap()))
                }))
            }
            root["toolCallCancellation"]?.jsonObject?.get("ids")?.jsonArray?.let { ids ->
                add(AssistantEvent.ToolCallsCancelled(ids.mapNotNull { it.jsonPrimitive.contentOrNull }))
            }
            root["sessionResumptionUpdate"]?.jsonObject?.let { u ->
                if (u["resumable"]?.jsonPrimitive?.booleanOrNull == true) u["newHandle"]?.jsonPrimitive?.contentOrNull?.let { add(AssistantEvent.ResumptionHandle(it)) }
            }
            root["goAway"]?.jsonObject?.let { add(AssistantEvent.GoAway(parseDurationMs(it["timeLeft"]))) }
            root["usageMetadata"]?.jsonObject?.get("totalTokenCount")?.jsonPrimitive?.intOrNull?.let { add(AssistantEvent.Usage(it)) }
        }
    } catch (e: IllegalArgumentException) {
        // JSON invalide (SerializationException en hérite) ou base64 corrompu.
        emptyList()
    }

    /** Durée protobuf en JSON (`"12.5s"`) ; absente ou illisible → 0. */
    fun parseDurationMs(e: JsonElement?): Long =
        (e as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.removeSuffix("s")?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0L
}
```

- [ ] **Step 6: Run tests**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts core/assistant
git commit -m "feat(assistant): module core:assistant — contrat fournisseur et protocole Gemini Live"
```

---

### Task 5: `GeminiLiveProvider` — client WebSocket

**Files:**
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/gemini/GeminiLiveProvider.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/gemini/GeminiLiveProviderTest.kt`

**Interfaces:**
- Consumes: `GeminiLiveProtocol`, contrat de la tâche 4.
- Produces: `class GeminiLiveProvider @Inject constructor(json: Json) : VoiceAssistantProvider` avec `internal var endpoint: String` et `internal var setupTimeoutMs: Long` (8 000 par défaut) pour les tests ; `internal fun closeError(code: Int, reason: String): AssistantError?` ; `internal fun failureError(t: Throwable, httpCode: Int?): AssistantError`.

- [ ] **Step 1: Write the failing tests**

Les tests utilisent `runBlocking` (pas `runTest`) : le serveur répond sur de vrais threads et `withTimeout` doit compter en temps réel.

```kotlin
package fr.roadly.core.assistant.gemini

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class GeminiLiveProviderTest {
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<String>()
    private val provider = GeminiLiveProvider(Json { ignoreUnknownKeys = true })
    private val config = SessionConfig("test-key", "Règles", emptyList())

    @Before fun setUp() { server.start(); provider.endpoint = server.url("/ws").toString().replaceFirst("http", "ws") }
    @After fun tearDown() = server.shutdown()

    private fun serve(onText: (WebSocket, String) -> Unit, onOpen: (WebSocket) -> Unit = {}) {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = onOpen(webSocket)
            override fun onMessage(webSocket: WebSocket, text: String) { received += text; onText(webSocket, text) }
        }))
    }

    @Test fun `setup is sent with the key in the query and a tool call round-trips`() = runBlocking {
        serve(onText = { ws, text ->
            if ("\"setup\"" in text) {
                ws.send("""{"setupComplete":{}}""")
                ws.send("""{"toolCall":{"functionCalls":[{"id":"c1","name":"getArrivalTime","args":{}}]}}""")
            }
        })
        val open = provider.connect(config) as Connection.Open
        assertEquals("test-key", server.takeRequest().requestUrl!!.queryParameter("key"))
        val event = withTimeout(5_000) { open.session.events.first { it is AssistantEvent.ToolCalls } } as AssistantEvent.ToolCalls
        assertEquals("c1", event.calls.single().id)
        open.session.sendToolResults(listOf(ToolResult("c1", "getArrivalTime", buildJsonObject { put("status", "ok") })))
        withTimeout(5_000) { while (received.none { "toolResponse" in it }) kotlinx.coroutines.delay(20) }
        assertTrue(received.first().contains("models/gemini-3.8-live"))
        open.session.close()
    }

    @Test fun `server closing with an API key reason is reported as INVALID_KEY`() = runBlocking {
        serve(onText = { _, _ -> }, onOpen = { ws -> ws.close(1008, "API key not valid. Please pass a valid API key.") })
        assertEquals(Connection.Refused(AssistantError.INVALID_KEY), provider.connect(config))
    }

    @Test fun `no setupComplete within the timeout is TIMEOUT`() = runBlocking {
        provider.setupTimeoutMs = 300
        serve(onText = { _, _ -> })
        assertEquals(Connection.Refused(AssistantError.TIMEOUT), provider.connect(config))
    }

    @Test fun `close reasons and HTTP failures are classified`() {
        assertEquals(null, closeError(1000, ""))
        assertEquals(AssistantError.QUOTA, closeError(1011, "RESOURCE_EXHAUSTED: quota"))
        assertEquals(AssistantError.NETWORK, closeError(1006, "abnormal"))
        assertEquals(AssistantError.INVALID_KEY, failureError(RuntimeException(), 403))
        assertEquals(AssistantError.QUOTA, failureError(RuntimeException(), 429))
        assertEquals(AssistantError.TIMEOUT, failureError(java.net.SocketTimeoutException(), null))
        assertEquals(AssistantError.NETWORK, failureError(java.io.IOException(), null))
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --tests "*GeminiLiveProviderTest" --offline -q`
Expected: FAIL — `Unresolved reference: GeminiLiveProvider`.

- [ ] **Step 3: Implement**

```kotlin
package fr.roadly.core.assistant.gemini

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import fr.roadly.core.assistant.provider.VoiceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini Live par WebSocket brut, clé API en paramètre de requête. Client OkHttp dédié, sans intercepteur
 * de journalisation : l'URL contient la clé.
 */
@Singleton
class GeminiLiveProvider @Inject constructor(private val json: Json) : VoiceAssistantProvider {
    override val id = "gemini-live"
    internal var endpoint = ENDPOINT
    internal var setupTimeoutMs = 8_000L
    private val client by lazy { OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build() }

    override suspend fun connect(config: SessionConfig): Connection {
        val session = GeminiVoiceSession(json)
        val request = Request.Builder().url("$endpoint?key=${config.apiKey}").build()
        session.socket = client.newWebSocket(request, session.listener)
        // OkHttp met en file les messages envoyés avant l'ouverture.
        session.socket.send(GeminiLiveProtocol.setup(config))
        val error = try {
            withTimeout(setupTimeoutMs) { session.ready.await() }
        } catch (e: TimeoutCancellationException) {
            AssistantError.TIMEOUT
        }
        return if (error == null) Connection.Open(session) else { session.close(); Connection.Refused(error) }
    }

    companion object {
        const val ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
}

internal class GeminiVoiceSession(private val json: Json) : VoiceSession {
    lateinit var socket: WebSocket
    /** Complété à `null` par `setupComplete`, ou par l'erreur qui a fermé la connexion avant. */
    val ready = CompletableDeferred<AssistantError?>()
    private val channel = Channel<AssistantEvent>(Channel.UNLIMITED)
    override val events: Flow<AssistantEvent> = channel.receiveAsFlow()
    @Volatile private var closed = false

    val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
        // Le serveur peut envoyer le JSON en trames binaires.
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            finish(closeError(code, reason))
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish(closeError(code, reason))
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = finish(failureError(t, response?.code))
    }

    private fun handle(text: String) {
        GeminiLiveProtocol.parse(json, text).forEach { e ->
            if (e == AssistantEvent.Ready) ready.complete(null) else channel.trySend(e)
        }
    }

    @Synchronized private fun finish(error: AssistantError?) {
        if (closed) return
        closed = true
        // Fermé avant setupComplete : la connexion a échoué, même sur une fermeture « normale ».
        ready.complete(error ?: AssistantError.NETWORK)
        error?.let { channel.trySend(AssistantEvent.Failed(it)) }
        channel.trySend(AssistantEvent.Closed)
        channel.close()
    }

    override fun sendAudio(pcm16k: ByteArray) { if (!closed) socket.send(GeminiLiveProtocol.audio(pcm16k)) }
    override fun endAudioStream() { if (!closed) socket.send(GeminiLiveProtocol.audioStreamEnd()) }
    override fun sendText(text: String) { if (!closed) socket.send(GeminiLiveProtocol.text(text)) }
    override fun sendToolResults(results: List<ToolResult>) { if (!closed) socket.send(GeminiLiveProtocol.toolResponse(results)) }
    override fun close() {
        if (closed) return
        socket.close(1000, null)
        finish(null)
    }
}

internal fun closeError(code: Int, reason: String): AssistantError? = when {
    code == 1000 -> null
    listOf("API key", "PERMISSION_DENIED", "UNAUTHENTICATED").any { reason.contains(it, ignoreCase = true) } -> AssistantError.INVALID_KEY
    reason.contains("RESOURCE_EXHAUSTED") || reason.contains("quota", ignoreCase = true) -> AssistantError.QUOTA
    code == 1007 || code == 1008 -> AssistantError.PROTOCOL
    else -> AssistantError.NETWORK
}

internal fun failureError(t: Throwable, httpCode: Int?): AssistantError = when {
    httpCode == 401 || httpCode == 403 -> AssistantError.INVALID_KEY
    httpCode == 429 -> AssistantError.QUOTA
    t is SocketTimeoutException -> AssistantError.TIMEOUT
    else -> AssistantError.NETWORK
}
```

Le `Json` injecté vient de `NetworkModule` (`ignoreUnknownKeys`, `isLenient`, `coerceInputValues`).

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add core/assistant
git commit -m "feat(assistant): client WebSocket Gemini Live (setup, outils, erreurs de clé, quota, timeout)"
```

---

### Task 6: Registre d'outils, confirmation vérifiée, consentement

**Files:**
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/AssistantTool.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/PendingActions.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/ToolRegistry.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/tools/ToolRegistryTest.kt`, `.../tools/AffirmationTest.kt`

**Interfaces:**
- Produces :
  - `data class ToolContext(val consentGranted: Boolean, val nowMs: Long, val zone: ZoneId, val userSpeechSince: (Long) -> String)`
  - `sealed interface ToolOutput { data class Done(val data: JsonObject); class NeedsConfirmation(val summary: String, val details: JsonObject = JsonObject(emptyMap()), val action: suspend () -> JsonObject) }`
  - `interface AssistantTool { val schema: ToolSchema; val needsLocationConsent: Boolean (défaut true); suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput }`
  - `object ToolJson { ok(build), unavailable(reason), error(code, message), consentRequired() }` ; extensions `JsonObject.string(name): String?`, `JsonObject.bool(name): Boolean?`, `JsonObject.int(name): Int?`.
  - `object ToolArgs { fun validate(schema: ToolSchema, args: JsonObject): String? }`
  - `@Singleton class PendingActions` : `data class Pending(id, callId, summary, createdAtMs, action)`, `val current: StateFlow<Pending?>`, `register(callId, out, nowMs): JsonObject`, `suspend confirm(actionId: String?, ctx): JsonObject`, `suspend confirmFromUi(): JsonObject?`, `cancel(actionId: String?): JsonObject`, `cancelFromUi(): Boolean`, `cancelCreatedBy(callIds: List<String>)`, `clear()`, `companion { TTL_MS = 30_000; CONFIRM = "confirmAction"; CANCEL = "cancelAction"; SCHEMAS }`.
  - `internal object Affirmation { fun isYes(text: String): Boolean }`
  - `@Singleton class ToolRegistry @Inject constructor(tools: Set<@JvmSuppressWildcards AssistantTool>, pending: PendingActions)` : `schemas(): List<ToolSchema>`, `suspend execute(call: ToolCall, ctx: ToolContext): ToolResult`, `resetTurn()`, `companion { MAX_CALLS_PER_TURN = 8 }`.

- [ ] **Step 1: Write the failing tests**

`AffirmationTest.kt` :

```kotlin
package fr.roadly.core.assistant.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffirmationTest {
    @Test fun `clear agreement is accepted`() {
        listOf("Oui", "ouais vas-y", "D'accord", "ok", "Confirmé", "c'est ça", "oui oui allez").forEach { assertTrue(it, Affirmation.isYes(it)) }
    }
    @Test fun `negation, hesitation or silence is refused`() {
        listOf("", "non", "non merci", "attends", "annule", "oui… enfin non", "surtout pas", "laisse tomber", "peut-être").forEach { assertFalse(it, Affirmation.isYes(it)) }
    }
}
```

`ToolRegistryTest.kt` :

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolCall
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ToolRegistryTest {
    private var executed = 0
    private val echo = object : AssistantTool {
        override val schema = ToolSchema("echo", "Renvoie", listOf(ToolParam("n", ParamType.INTEGER, "Nombre"), ToolParam("mode", ParamType.STRING, "Mode", required = false, enumValues = listOf("a", "b"))))
        override val needsLocationConsent = false
        override suspend fun run(args: JsonObject, ctx: ToolContext) = ToolOutput.Done(ToolJson.ok { put("n", args.int("n")) })
    }
    private val located = object : AssistantTool {
        override val schema = ToolSchema("where", "Position")
        override suspend fun run(args: JsonObject, ctx: ToolContext) = ToolOutput.Done(ToolJson.ok())
    }
    private val dangerous = object : AssistantTool {
        override val schema = ToolSchema("stopNavigation", "Arrêter")
        override val needsLocationConsent = false
        override suspend fun run(args: JsonObject, ctx: ToolContext) = ToolOutput.NeedsConfirmation("Arrêter le guidage") { executed++; ToolJson.ok() }
    }
    private val failing = object : AssistantTool {
        override val schema = ToolSchema("boom", "Plante")
        override val needsLocationConsent = false
        override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput = error("panne")
    }
    private val pending = PendingActions()
    private val registry = ToolRegistry(setOf(echo, located, dangerous, failing), pending)
    private var speech = ""
    private fun ctx(now: Long = 1_000, consent: Boolean = true) = ToolContext(consent, now, ZoneId.of("Europe/Paris")) { speech }
    private fun call(name: String, args: JsonObject = JsonObject(emptyMap()), id: String = "c-$name") = ToolCall(id, name, args)
    private fun JsonObject.status() = this["status"]!!.jsonPrimitive.content

    @Test fun `schemas include every tool plus confirmAction and cancelAction`() {
        assertEquals(setOf("boom", "cancelAction", "confirmAction", "echo", "stopNavigation", "where"), registry.schemas().map { it.name }.toSet())
    }

    @Test fun `unknown tool, missing or ill-typed argument and bad enum are structured errors`() = runTest {
        assertEquals("unknown_tool", registry.execute(call("nope"), ctx()).payload["code"]!!.jsonPrimitive.content)
        assertEquals("invalid_arguments", registry.execute(call("echo"), ctx()).payload["code"]!!.jsonPrimitive.content)
        assertEquals("invalid_arguments", registry.execute(call("echo", buildJsonObject { put("n", "deux") }), ctx()).payload["code"]!!.jsonPrimitive.content)
        assertEquals("invalid_arguments", registry.execute(call("echo", buildJsonObject { put("n", 2); put("mode", "z") }), ctx()).payload["code"]!!.jsonPrimitive.content)
        assertEquals("2", registry.execute(call("echo", buildJsonObject { put("n", 2) }), ctx()).payload["n"]!!.jsonPrimitive.content)
    }

    @Test fun `a crashing tool returns an internal error instead of throwing`() = runTest {
        assertEquals("internal", registry.execute(call("boom"), ctx()).payload["code"]!!.jsonPrimitive.content)
    }

    @Test fun `location tools need consent`() = runTest {
        assertEquals("consent_required", registry.execute(call("where"), ctx(consent = false)).payload.status())
        assertEquals("ok", registry.execute(call("where"), ctx()).payload.status())
    }

    @Test fun `no more than 8 calls per turn`() = runTest {
        repeat(8) { registry.execute(call("echo", buildJsonObject { put("n", it) }), ctx()) }
        assertEquals("too_many_calls", registry.execute(call("echo", buildJsonObject { put("n", 9) }), ctx()).payload["code"]!!.jsonPrimitive.content)
        registry.resetTurn()
        assertEquals("ok", registry.execute(call("echo", buildJsonObject { put("n", 1) }), ctx()).payload.status())
    }

    @Test fun `confirmation runs the action only after a spoken yes heard after the request`() = runTest {
        val asked = registry.execute(call("stopNavigation"), ctx(now = 1_000)).payload
        assertEquals("needs_confirmation", asked.status())
        val id = asked["action_id"]!!.jsonPrimitive.content
        val confirm = call(PendingActions.CONFIRM, buildJsonObject { put("actionId", id) })
        speech = "euh"
        assertEquals("not_confirmed", registry.execute(confirm, ctx(now = 2_000)).payload["code"]!!.jsonPrimitive.content)
        assertEquals(0, executed)
        speech = "oui vas-y"
        assertEquals("ok", registry.execute(confirm, ctx(now = 3_000)).payload.status())
        assertEquals(1, executed)
        assertNull(pending.current.value)
    }

    @Test fun `an expired request is refused`() = runTest {
        val id = registry.execute(call("stopNavigation"), ctx(now = 0)).payload["action_id"]!!.jsonPrimitive.content
        speech = "oui"
        assertEquals("expired", registry.execute(call(PendingActions.CONFIRM, buildJsonObject { put("actionId", id) }), ctx(now = 31_000)).payload["code"]!!.jsonPrimitive.content)
        assertEquals(0, executed)
    }

    @Test fun `the on-screen confirm chip runs the action without a spoken yes`() = runTest {
        registry.execute(call("stopNavigation"), ctx())
        assertTrue(pending.confirmFromUi() != null)
        assertEquals(1, executed)
    }

    @Test fun `cancelling or a server cancellation drops the pending action`() = runTest {
        val id = registry.execute(call("stopNavigation", id = "c9"), ctx()).payload["action_id"]!!.jsonPrimitive.content
        pending.cancelCreatedBy(listOf("c9"))
        assertNull(pending.current.value)
        registry.execute(call("stopNavigation"), ctx())
        assertEquals("ok", registry.execute(call(PendingActions.CANCEL, buildJsonObject { put("actionId", pending.current.value!!.id) }), ctx()).payload.status())
        assertNull(pending.current.value)
        assertTrue(id.isNotBlank())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --tests "*tools*" --offline -q`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement `tools/AssistantTool.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import java.time.ZoneId

/** Contexte d'un appel : consentement courant, horloge, et ce que l'utilisateur a dit depuis un instant donné. */
data class ToolContext(
    val consentGranted: Boolean,
    val nowMs: Long,
    val zone: ZoneId,
    val userSpeechSince: (Long) -> String,
)

sealed interface ToolOutput {
    data class Done(val data: JsonObject) : ToolOutput
    /** Action à conséquences : exécutée seulement après confirmation (voix vérifiée ou puce à l'écran). */
    class NeedsConfirmation(val summary: String, val details: JsonObject = JsonObject(emptyMap()), val action: suspend () -> JsonObject) : ToolOutput
}

interface AssistantTool {
    val schema: ToolSchema
    /** Vrai si l'outil lit ou transmet la position, la destination ou l'itinéraire. */
    val needsLocationConsent: Boolean get() = true
    suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput
}

object ToolJson {
    inline fun ok(build: JsonObjectBuilder.() -> Unit = {}): JsonObject = buildJsonObject { put("status", "ok"); build() }
    fun unavailable(reason: String): JsonObject = buildJsonObject { put("status", "unavailable"); put("reason", reason) }
    fun error(code: String, message: String): JsonObject = buildJsonObject { put("status", "error"); put("code", code); put("message", message) }
    fun consentRequired(): JsonObject = buildJsonObject {
        put("status", "consent_required")
        put("message", "L'utilisateur n'a pas autorisé le partage de sa position et de son trajet (Réglages → Roadly AI).")
    }
}

fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }

/** Validation des arguments d'après le schéma ; renvoie un message d'erreur, ou null si valides. */
object ToolArgs {
    fun validate(schema: ToolSchema, args: JsonObject): String? {
        for (p in schema.parameters) {
            val v = args[p.name] as? JsonPrimitive
            if (v == null || (v.isString && v.content.isBlank())) {
                if (p.required) return "Paramètre « ${p.name} » manquant." else continue
            }
            val ok = when (p.type) {
                ParamType.STRING -> v.isString && (p.enumValues == null || v.content in p.enumValues)
                ParamType.BOOLEAN -> v.booleanOrNull != null
                ParamType.INTEGER -> v.intOrNull != null || v.doubleOrNull?.let { it % 1.0 == 0.0 } == true
                ParamType.NUMBER -> v.doubleOrNull != null
            }
            if (!ok) return "Paramètre « ${p.name} » invalide" + (p.enumValues?.let { " (valeurs : ${it.joinToString()})" } ?: "") + "."
        }
        return null
    }
}
```

- [ ] **Step 4: Implement `tools/PendingActions.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Une seule action en attente à la fois (affichée avec Confirmer / Annuler). */
@Singleton
class PendingActions @Inject constructor() {
    data class Pending(val id: String, val callId: String, val summary: String, val createdAtMs: Long, val action: suspend () -> JsonObject)

    private val _current = MutableStateFlow<Pending?>(null)
    val current: StateFlow<Pending?> = _current.asStateFlow()
    private var seq = 0

    fun register(callId: String, out: ToolOutput.NeedsConfirmation, nowMs: Long): JsonObject {
        val p = Pending("a${++seq}", callId, out.summary, nowMs, out.action)
        _current.value = p
        return buildJsonObject {
            put("status", "needs_confirmation")
            put("action_id", p.id)
            put("summary", p.summary)
            out.details.forEach { (k, v) -> put(k, v) }
        }
    }

    /** Exécute si l'utilisateur a dit « oui » (sans négation) **après** la demande, dans les 30 s. */
    suspend fun confirm(actionId: String?, ctx: ToolContext): JsonObject {
        val p = _current.value?.takeIf { it.id == actionId } ?: return ToolJson.error("unknown_action", "Aucune action en attente avec cet identifiant.")
        if (ctx.nowMs - p.createdAtMs > TTL_MS) {
            _current.value = null
            return ToolJson.error("expired", "La demande a expiré ; redemandez à l'utilisateur.")
        }
        if (!Affirmation.isYes(ctx.userSpeechSince(p.createdAtMs))) {
            return ToolJson.error("not_confirmed", "L'utilisateur n'a pas clairement accepté à voix haute ; redemandez-lui ou appelez cancelAction.")
        }
        _current.value = null
        return p.action()
    }

    /** Puce « Confirmer » : geste explicite de l'utilisateur, pas de vérification vocale. */
    suspend fun confirmFromUi(): JsonObject? {
        val p = _current.value ?: return null
        _current.value = null
        return p.action()
    }

    fun cancel(actionId: String?): JsonObject {
        if (_current.value?.id != actionId) return ToolJson.error("unknown_action", "Aucune action en attente avec cet identifiant.")
        _current.value = null
        return ToolJson.ok { put("cancelled", true) }
    }

    fun cancelFromUi(): Boolean = (_current.value != null).also { _current.value = null }

    fun cancelCreatedBy(callIds: List<String>) { if (_current.value?.callId in callIds) _current.value = null }

    fun clear() { _current.value = null }

    companion object {
        const val TTL_MS = 30_000L
        const val CONFIRM = "confirmAction"
        const val CANCEL = "cancelAction"
        private val ACTION_ID = ToolParam("actionId", ParamType.STRING, "Identifiant action_id reçu avec needs_confirmation.")
        val SCHEMAS = listOf(
            ToolSchema(CONFIRM, "Exécute l'action en attente, uniquement si l'utilisateur vient d'accepter clairement à voix haute.", listOf(ACTION_ID)),
            ToolSchema(CANCEL, "Annule l'action en attente (refus ou hésitation de l'utilisateur).", listOf(ACTION_ID)),
        )
    }
}

/** Accord vocal explicite en français ; toute négation l'emporte (refuser à tort est sans danger). */
internal object Affirmation {
    private val yes = Regex("""(?U)\b(oui|ouais|ok|okay|d'accord|vas-y|vas y|confirme|confirmé|confirmée|exactement|parfait|c'est ça|carrément|allez)\b""")
    private val no = Regex("""(?U)\b(non|annule|annuler|pas|attends|stop|surtout|laisse tomber)\b""")
    fun isYes(text: String): Boolean {
        val t = text.lowercase(Locale.FRENCH).replace('’', '\'')
        return yes.containsMatchIn(t) && !no.containsMatchIn(t)
    }
}
```

- [ ] **Step 5: Implement `tools/ToolRegistry.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ToolCall
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.ToolSchema
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Dispatch des appels du modèle : validation, consentement, plafond par tour, confirmation, erreurs jamais propagées. */
@Singleton
class ToolRegistry @Inject constructor(
    tools: Set<@JvmSuppressWildcards AssistantTool>,
    private val pending: PendingActions,
) {
    private val byName = tools.associateBy { it.schema.name }
    private var callsThisTurn = 0

    fun schemas(): List<ToolSchema> = byName.values.map { it.schema }.sortedBy { it.name } + PendingActions.SCHEMAS

    fun resetTurn() { callsThisTurn = 0 }

    suspend fun execute(call: ToolCall, ctx: ToolContext): ToolResult {
        val payload = try {
            dispatch(call, ctx)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolJson.error("internal", e.message ?: e.javaClass.simpleName)
        }
        return ToolResult(call.id, call.name, payload)
    }

    private suspend fun dispatch(call: ToolCall, ctx: ToolContext): JsonObject {
        if (++callsThisTurn > MAX_CALLS_PER_TURN) return ToolJson.error("too_many_calls", "Limite de $MAX_CALLS_PER_TURN appels atteinte pour ce tour ; répondez avec ce que vous savez.")
        when (call.name) {
            PendingActions.CONFIRM -> return pending.confirm(call.args.string("actionId"), ctx)
            PendingActions.CANCEL -> return pending.cancel(call.args.string("actionId"))
        }
        val tool = byName[call.name] ?: return ToolJson.error("unknown_tool", "Outil inconnu : ${call.name}.")
        ToolArgs.validate(tool.schema, call.args)?.let { return ToolJson.error("invalid_arguments", it) }
        if (tool.needsLocationConsent && !ctx.consentGranted) return ToolJson.consentRequired()
        return when (val out = tool.run(call.args, ctx)) {
            is ToolOutput.Done -> out.data
            is ToolOutput.NeedsConfirmation -> pending.register(call.id, out, ctx.nowMs)
        }
    }

    companion object { const val MAX_CALLS_PER_TURN = 8 }
}
```

- [ ] **Step 6: Run tests**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS (`AffirmationTest`, `ToolRegistryTest` + précédents). Si `"oui… enfin non"` passe, vérifier que `(?U)` est bien en tête des deux regex.

- [ ] **Step 7: Commit**

```bash
git add core/assistant
git commit -m "feat(assistant): registre d'outils — validation, consentement, plafond, confirmation vocale vérifiée"
```

---

### Task 7: Réglages, clé chiffrée, usage quotidien

**Files:**
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/settings/AssistantSettingsRepository.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/settings/ApiKeyProvider.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/settings/KeystoreSecretCipher.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/settings/UsageMeter.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/settings/AssistantKeyVerifier.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/testing/FakeAssistantSettingsRepository.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/settings/ApiKeyProviderTest.kt`, `.../settings/UsageMeterTest.kt`

**Interfaces:**
- Produces :
  - `data class AssistantSettings(enabled = true, consentGranted = false, dailyCapMinutes = 30, encryptedKey: String? = null)`
  - `data class AssistantUsage(day = "", tokens: Long = 0, micSeconds: Long = 0)`
  - `interface AssistantSettingsRepository { settings: Flow<AssistantSettings>; usage: Flow<AssistantUsage>; setEnabled; setConsent; setDailyCap(minutes); setEncryptedKey(String?); addUsage(day, tokens, micSeconds) }` + `DataStoreAssistantSettingsRepository`.
  - `interface SecretCipher { encrypt(plain): String; decrypt(cipherText): String? }` + `KeystoreSecretCipher`.
  - `enum class KeySource { OVERRIDE, BUILD, NONE }` ; `class ApiKeyProvider @Inject constructor(settings, cipher, @Named(ApiKeyProvider.BUILD_KEY) buildKey: String)` : `suspend key(): String?`, `suspend source(): KeySource`, `suspend save(key: String)`, `suspend clearOverride()`.
  - `class UsageMeter` : `suspend canStart(nowMs, zone): Boolean`, `suspend record(nowMs, zone, tokens: Long, micSeconds: Long)`, `companion fun day(nowMs, zone): String`.
  - `class AssistantKeyVerifier @Inject constructor(provider: VoiceAssistantProvider)` : `suspend verify(key: String): AssistantError?`.
  - `class FakeAssistantSettingsRepository : AssistantSettingsRepository` (état en `MutableStateFlow`, propriétés `current` et `currentUsage`).

- [ ] **Step 1: Write the failing tests**

`ApiKeyProviderTest.kt` :

```kotlin
package fr.roadly.core.assistant.settings

import fr.roadly.core.assistant.testing.FakeAssistantSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyProviderTest {
    private val reversing = object : SecretCipher {
        override fun encrypt(plain: String) = plain.reversed()
        override fun decrypt(cipherText: String) = cipherText.reversed()
    }

    @Test fun `the key typed in settings wins over the build key, stored encrypted`() = runTest {
        val repo = FakeAssistantSettingsRepository()
        val keys = ApiKeyProvider(repo, reversing, "build-key")
        assertEquals("build-key", keys.key()); assertEquals(KeySource.BUILD, keys.source())
        keys.save("  perso  ")
        assertEquals("osrep", repo.current.encryptedKey)
        assertEquals("perso", keys.key()); assertEquals(KeySource.OVERRIDE, keys.source())
        keys.clearOverride()
        assertEquals("build-key", keys.key())
    }

    @Test fun `no key anywhere is null`() = runTest {
        val keys = ApiKeyProvider(FakeAssistantSettingsRepository(), reversing, "")
        assertNull(keys.key()); assertEquals(KeySource.NONE, keys.source())
    }
}
```

`UsageMeterTest.kt` :

```kotlin
package fr.roadly.core.assistant.settings

import fr.roadly.core.assistant.testing.FakeAssistantSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageMeterTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val day1 = ZonedDateTime.of(2026, 10, 31, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val day2 = day1 + 24 * 3_600_000L

    @Test fun `usage accumulates per day and the daily cap blocks new sessions`() = runTest {
        val repo = FakeAssistantSettingsRepository()
        repo.setDailyCap(1)
        val meter = UsageMeter(repo)
        assertTrue(meter.canStart(day1, zone))
        meter.record(day1, zone, tokens = 500, micSeconds = 40)
        meter.record(day1, zone, tokens = 100, micSeconds = 30)
        assertEquals(AssistantUsage("2026-10-31", 600, 70), repo.currentUsage)
        assertFalse(meter.canStart(day1, zone))
        assertTrue("nouveau jour, compteur remis à zéro", meter.canStart(day2, zone))
        meter.record(day2, zone, tokens = 1, micSeconds = 1)
        assertEquals(AssistantUsage("2026-11-01", 1, 1), repo.currentUsage)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --tests "*settings*" --offline -q`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the repository**

`settings/AssistantSettingsRepository.kt` :

```kotlin
package fr.roadly.core.assistant.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AssistantSettings(
    val enabled: Boolean = true,
    val consentGranted: Boolean = false,
    val dailyCapMinutes: Int = 30,
    /** Clé saisie dans Réglages, chiffrée par [SecretCipher] ; null = clé de build. */
    val encryptedKey: String? = null,
)

/** Usage du jour [day] (`yyyy-MM-dd`, fuseau local). */
data class AssistantUsage(val day: String = "", val tokens: Long = 0, val micSeconds: Long = 0)

interface AssistantSettingsRepository {
    val settings: Flow<AssistantSettings>
    val usage: Flow<AssistantUsage>
    suspend fun setEnabled(value: Boolean)
    suspend fun setConsent(value: Boolean)
    suspend fun setDailyCap(minutes: Int)
    suspend fun setEncryptedKey(value: String?)
    /** Ajoute à l'usage de [day] ; un autre jour que celui enregistré repart de zéro. */
    suspend fun addUsage(day: String, tokens: Long, micSeconds: Long)
}

@Singleton
class DataStoreAssistantSettingsRepository @Inject constructor(private val store: DataStore<Preferences>) : AssistantSettingsRepository {
    private val kEnabled = booleanPreferencesKey("assistant.enabled")
    private val kConsent = booleanPreferencesKey("assistant.consent")
    private val kCap = intPreferencesKey("assistant.cap.minutes")
    private val kKey = stringPreferencesKey("assistant.key.encrypted")
    private val kDay = stringPreferencesKey("assistant.usage.day")
    private val kTokens = longPreferencesKey("assistant.usage.tokens")
    private val kMic = longPreferencesKey("assistant.usage.mic")

    override val settings: Flow<AssistantSettings> = store.data.map {
        AssistantSettings(it[kEnabled] ?: true, it[kConsent] ?: false, it[kCap] ?: 30, it[kKey])
    }
    override val usage: Flow<AssistantUsage> = store.data.map { AssistantUsage(it[kDay].orEmpty(), it[kTokens] ?: 0, it[kMic] ?: 0) }

    override suspend fun setEnabled(value: Boolean) { store.edit { it[kEnabled] = value } }
    override suspend fun setConsent(value: Boolean) { store.edit { it[kConsent] = value } }
    override suspend fun setDailyCap(minutes: Int) { store.edit { it[kCap] = minutes.coerceIn(5, 240) } }
    override suspend fun setEncryptedKey(value: String?) { store.edit { if (value == null) it.remove(kKey) else it[kKey] = value } }
    override suspend fun addUsage(day: String, tokens: Long, micSeconds: Long) {
        store.edit {
            val same = it[kDay] == day
            it[kDay] = day
            it[kTokens] = (if (same) it[kTokens] ?: 0 else 0) + tokens
            it[kMic] = (if (same) it[kMic] ?: 0 else 0) + micSeconds
        }
    }
}
```

`testing/FakeAssistantSettingsRepository.kt` :

```kotlin
package fr.roadly.core.assistant.testing

import fr.roadly.core.assistant.settings.AssistantSettings
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.settings.AssistantUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeAssistantSettingsRepository(initial: AssistantSettings = AssistantSettings()) : AssistantSettingsRepository {
    private val state = MutableStateFlow(initial)
    private val usageState = MutableStateFlow(AssistantUsage())
    override val settings = state
    override val usage = usageState
    val current: AssistantSettings get() = state.value
    val currentUsage: AssistantUsage get() = usageState.value
    override suspend fun setEnabled(value: Boolean) = state.update { it.copy(enabled = value) }
    override suspend fun setConsent(value: Boolean) = state.update { it.copy(consentGranted = value) }
    override suspend fun setDailyCap(minutes: Int) = state.update { it.copy(dailyCapMinutes = minutes) }
    override suspend fun setEncryptedKey(value: String?) = state.update { it.copy(encryptedKey = value) }
    override suspend fun addUsage(day: String, tokens: Long, micSeconds: Long) = usageState.update {
        if (it.day == day) it.copy(tokens = it.tokens + tokens, micSeconds = it.micSeconds + micSeconds) else AssistantUsage(day, tokens, micSeconds)
    }
}
```

- [ ] **Step 4: Implement key, cipher, usage, verifier**

`settings/ApiKeyProvider.kt` :

```kotlin
package fr.roadly.core.assistant.settings

import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

interface SecretCipher {
    fun encrypt(plain: String): String
    /** null si le texte est illisible (clé Keystore perdue après réinstallation, données corrompues). */
    fun decrypt(cipherText: String): String?
}

enum class KeySource { OVERRIDE, BUILD, NONE }

/** Clé Gemini : saisie dans Réglages (chiffrée) sinon `GEMINI_API_KEY` de `local.properties` (BuildConfig). */
@Singleton
class ApiKeyProvider @Inject constructor(
    private val settings: AssistantSettingsRepository,
    private val cipher: SecretCipher,
    @param:Named(BUILD_KEY) private val buildKey: String,
) {
    suspend fun key(): String? = override() ?: buildKey.takeIf { it.isNotBlank() }

    suspend fun source(): KeySource = when {
        override() != null -> KeySource.OVERRIDE
        buildKey.isNotBlank() -> KeySource.BUILD
        else -> KeySource.NONE
    }

    suspend fun save(key: String) {
        val k = key.trim()
        settings.setEncryptedKey(if (k.isEmpty()) null else cipher.encrypt(k))
    }

    suspend fun clearOverride() = settings.setEncryptedKey(null)

    private suspend fun override(): String? = settings.settings.first().encryptedKey?.let(cipher::decrypt)?.takeIf { it.isNotBlank() }

    companion object { const val BUILD_KEY = "geminiBuildKey" }
}
```

`settings/KeystoreSecretCipher.kt` :

```kotlin
package fr.roadly.core.assistant.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** AES-256-GCM avec une clé non exportable de l'Android Keystore ; sortie = base64(IV ‖ chiffré). */
@Singleton
class KeystoreSecretCipher @Inject constructor() : SecretCipher {
    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.getEncoder().encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    override fun decrypt(cipherText: String): String? = runCatching {
        val raw = Base64.getDecoder().decode(cipherText)
        val c = Cipher.getInstance(TRANSFORMATION)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_BYTES))
        String(c.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "roadly_ai_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
```

`settings/UsageMeter.kt` :

```kotlin
package fr.roadly.core.assistant.settings

import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Plafond quotidien en minutes de micro ; les tokens sont affichés à titre indicatif. */
@Singleton
class UsageMeter @Inject constructor(private val repo: AssistantSettingsRepository) {
    suspend fun canStart(nowMs: Long, zone: ZoneId): Boolean {
        val capSeconds = repo.settings.first().dailyCapMinutes * 60L
        val u = repo.usage.first()
        val usedToday = if (u.day == day(nowMs, zone)) u.micSeconds else 0L
        return usedToday < capSeconds
    }

    suspend fun record(nowMs: Long, zone: ZoneId, tokens: Long, micSeconds: Long) =
        repo.addUsage(day(nowMs, zone), tokens, micSeconds)

    companion object {
        fun day(nowMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()
    }
}
```

`settings/AssistantKeyVerifier.kt` :

```kotlin
package fr.roadly.core.assistant.settings

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import javax.inject.Inject

/** « Vérifier la clé » : connexion, attente de setupComplete, fermeture ; aucun micro. */
class AssistantKeyVerifier @Inject constructor(private val provider: VoiceAssistantProvider) {
    suspend fun verify(key: String): AssistantError? = when (val c = provider.connect(SessionConfig(key.trim(), "Test de connexion Roadly.", emptyList()))) {
        is Connection.Open -> { c.session.close(); null }
        is Connection.Refused -> c.error
    }
}
```

- [ ] **Step 5: Run tests**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/assistant
git commit -m "feat(assistant): réglages DataStore, clé chiffrée Keystore, plafond quotidien"
```

---

### Task 8: Outils de guidage (statut, instruction, démarrer, changer, arrêter, recalculer, fin)

**Files:**
- Create: `core/navigation/src/main/java/fr/roadly/core/navigation/ReportsAhead.kt` ; Modify: `NavigationViewModel.kt` (`reportsAhead` l'utilise)
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/ToolMemory.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/ToolFormat.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/DestinationResolver.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/NavigationTools.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/tools/GuidingFixture.kt`, `.../tools/NavigationToolsTest.kt`

**Interfaces:**
- Consumes: `GuidanceActions` (tâche 3), `AppNavigator`/`NavRequest` (tâche 2), `ToolJson`, `ToolOutput`, `ToolContext` (tâche 6).
- Produces :
  - `data class ReportAhead(val report: CommunityReport, val distanceM: Double)` ; `fun reportsAhead(g: NavigationState.Guiding, reports: List<CommunityReport>): List<ReportAhead>` (package `fr.roadly.core.navigation`).
  - `@Singleton class ToolPlaces { fun remember(place: Place): String; operator fun get(id: String): Place? }`
  - `@Singleton class ToolRoutes { fun replace(options: List<RouteOption>): List<Pair<String, RouteOption>>; operator fun get(id: String): RouteOption? }` (ids `r1`, `r2`…)
  - `data class CardRow(val title: String, val detail: String, val placeId: String? = null)`, `data class ResultCard(val title: String, val rows: List<CardRow>)`, `@Singleton class AssistantCards { val card: StateFlow<ResultCard?>; fun show(card: ResultCard); fun clear() }`
  - `internal object ToolFormat { clock(epochMs, zone): String; minutes(seconds: Double): Int; km(meters: Double): Double; describe(e: AppError): String }` ; `JsonObjectBuilder.putPlace(key, place)`, `JsonObjectBuilder.putOption(option, zone)`, `JsonObjectBuilder.putProgress(g, zone)`, `notGuiding(state): JsonObject`.
  - `class DestinationResolver @Inject constructor(favorites, recents, geocoder, location, places)` : `suspend resolve(query: String?, placeId: String?): List<Place>` (≤ 3).
  - Outils : `GetNavigationStatusTool`, `GetNextInstructionTool`, `GetDistanceRemainingTool`, `GetArrivalTimeTool`, `DestinationTool(name, description, verb, …)` (instances `startNavigation` et `changeDestination`), `StopNavigationTool`, `RecalculateRouteTool`, `EndConversationTool` (`companion const val NAME = "endConversation"`).

- [ ] **Step 1: Extract `reportsAhead` (refactor, behaviour unchanged)**

`core/navigation/src/main/java/fr/roadly/core/navigation/ReportsAhead.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.CommunityReport

/** Signalement devant soi : [distanceM] le long du tracé depuis la position projetée. */
data class ReportAhead(val report: CommunityReport, val distanceM: Double)

/** Signalements à ≤ 60 m du tracé et entre −50 m et 5 km devant la position projetée, du plus proche au plus loin. */
fun reportsAhead(g: NavigationState.Guiding, reports: List<CommunityReport>): List<ReportAhead> {
    val geo = RouteGeometry(g.route.geometry)
    return reports.mapNotNull { r ->
        val p = geo.project(r.point) ?: return@mapNotNull null
        val d = p.alongM - g.progress.distanceAlongM
        if (p.offsetM > 60.0 || d < -50.0 || d > 5_000.0) null else ReportAhead(r, d.coerceAtLeast(0.0))
    }.sortedBy { it.distanceM }
}
```

Dans `NavigationViewModel.reportsAhead`, remplacer le corps du `combine` par :

```kotlin
        val g = s as? NavigationState.Guiding ?: return@combine emptyList()
        reportsAhead(g, list).map { ReportAheadUi(it.report, it.distanceM, Formatters.distance(it.distanceM)) }
```

(import `fr.roadly.core.navigation.reportsAhead` ; retirer `RouteGeometry` s'il n'est plus utilisé.) Run `.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q` → PASS.

- [ ] **Step 2: Write `ToolMemory.kt` and `ToolFormat.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.model.Place
import fr.roadly.core.routing.RouteOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Lieux cités dans les réponses récentes ; le modèle les rappelle par `place_id` (ajout d'arrêt, destination). */
@Singleton
class ToolPlaces @Inject constructor() {
    private val places = LinkedHashMap<String, Place>()
    @Synchronized fun remember(place: Place): String {
        places.remove(place.id)
        places[place.id] = place
        while (places.size > MAX) places.remove(places.keys.first())
        return place.id
    }
    @Synchronized operator fun get(id: String): Place? = places[id]
    private companion object { const val MAX = 50 }
}

/** Dernières alternatives proposées, par identifiant court (`r1`, `r2`…). */
@Singleton
class ToolRoutes @Inject constructor() {
    private var options: Map<String, RouteOption> = emptyMap()
    @Synchronized fun replace(list: List<RouteOption>): List<Pair<String, RouteOption>> =
        list.mapIndexed { i, o -> "r${i + 1}" to o }.also { options = it.toMap() }
    @Synchronized operator fun get(id: String): RouteOption? = options[id]
}

data class CardRow(val title: String, val detail: String, val placeId: String? = null)
data class ResultCard(val title: String, val rows: List<CardRow>)

/** Carte de résultat affichée dans la fenêtre compacte (≤ 3 lignes). */
@Singleton
class AssistantCards @Inject constructor() {
    private val _card = MutableStateFlow<ResultCard?>(null)
    val card: StateFlow<ResultCard?> = _card.asStateFlow()
    fun show(card: ResultCard) { _card.value = card.copy(rows = card.rows.take(3)) }
    fun clear() { _card.value = null }
}
```

`ToolFormat.kt` :

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.common.AppError
import fr.roadly.core.model.Place
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteOption
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal object ToolFormat {
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
    fun clock(epochMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().format(HHMM)
    fun minutes(seconds: Double): Int = (seconds / 60.0).roundToInt()
    fun km(meters: Double): Double = (meters / 100.0).roundToInt() / 10.0
    fun describe(e: AppError): String = when (e) {
        AppError.Network -> "réseau indisponible"
        AppError.Timeout -> "le service ne répond pas"
        AppError.RateLimited -> "service saturé, réessayer plus tard"
        AppError.PermissionDenied -> "position indisponible (permission de localisation)"
        AppError.NoResult -> "aucun résultat"
        is AppError.Unknown -> if (e.detail == "max_stops") "10 arrêts maximum" else "erreur inattendue"
        else -> "service indisponible"
    }
}

internal fun JsonObjectBuilder.putPlace(key: String, place: Place) = putJsonObject(key) {
    put("place_id", place.id)
    put("name", place.name)
    place.address?.let { put("address", it) }
}

internal fun JsonObjectBuilder.putOption(option: RouteOption, zone: ZoneId) {
    put("duration_min", ToolFormat.minutes(option.route.durationSeconds))
    put("distance_km", ToolFormat.km(option.route.distanceMeters))
    put("arrival_time", ToolFormat.clock(option.arrivalEpochMs, zone))
    put("has_toll", option.route.hasToll)
}

internal fun JsonObjectBuilder.putProgress(g: NavigationState.Guiding, zone: ZoneId) {
    put("remaining_km", ToolFormat.km(g.progress.remainingDistanceM))
    put("remaining_min", ToolFormat.minutes(g.progress.remainingDurationS.toDouble()))
    put("arrival_time", ToolFormat.clock(g.etaEpochMs, zone))
    g.route.maneuvers.getOrNull(g.progress.nextManeuverIndex)?.let { m ->
        put("next_instruction", m.verbalPre ?: m.instruction)
        put("next_in_m", g.progress.distanceToNextManeuverM.toDouble().roundToInt())
        m.streetName?.let { put("next_street", it) }
    }
    g.speedLimitKmh?.let { put("speed_limit_kmh", it) }
    if (g.rerouting) put("rerouting", true)
    if (g.gpsLost) put("gps_lost", true)
}

internal fun notGuiding(state: NavigationState): JsonObject = when (state) {
    is NavigationState.Arrived -> ToolJson.ok { put("guiding", false); put("arrived", true) }
    is NavigationState.Failed -> ToolJson.ok { put("guiding", false); put("last_error", ToolFormat.describe(state.error)) }
    else -> ToolJson.ok { put("guiding", false) }
}
```

- [ ] **Step 3: Write the test fixture `GuidingFixture.kt` (test source set)**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NoopRainProvider
import fr.roadly.core.navigation.NoopSpeedLimitProvider
import fr.roadly.core.navigation.NoopTripRecorder
import fr.roadly.core.navigation.NoopVigilanceZoneProvider
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import java.time.ZoneId

/** Session de guidage réelle sur la route de test, avec des doubles pour tout le reste. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GuidingFixture(private val scope: TestScope) {
    val zone: ZoneId = ZoneId.of("Europe/Paris")
    val location = FakeLocationRepository()
    val prefs = FakeUserPreferencesRepository()
    val route = TestRoutes.straight()
    val tracker = RouteTracker(route)
    val requests = mutableListOf<RouteRequest>()
    /** Durée renvoyée pour chaque requête (par défaut : celle de la route de test). */
    var durationFor: (RouteRequest) -> Double = { route.durationSeconds }
    val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> {
            requests += request
            return Outcome.Success(listOf(route.copy(id = "r${requests.size}-${request.costing.name}", durationSeconds = durationFor(request))))
        }
    }
    var vehicle: VehicleProfile? = null
    val vehicles = object : VehicleProfileRepository {
        override val profile: Flow<VehicleProfile?> get() = flowOf(vehicle)
        override suspend fun save(profile: VehicleProfile) { vehicle = profile }
        override suspend fun clear() { vehicle = null }
    }
    val clock = NavigationClock { scope.testScheduler.currentTime }
    val navigator = AppNavigator()
    val places = ToolPlaces()
    val session: NavigationSession
    val guidance: GuidanceActions

    init {
        val d = StandardTestDispatcher(scope.testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        session = NavigationSession(location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs, FakeNavigationServiceController(), clock, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, scope.backgroundScope)
        guidance = GuidanceActions(session, RouteAlternativesUseCase(routing), routing, location, prefs, vehicles, clock)
    }

    fun fixAt(along: Double) = LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L)

    suspend fun guideAt(along: Double) {
        guidance.start(RouteOption(route, "Le plus rapide", 0L, null, false), RoutePreferences(), vehicle, destinationName = "Capitole")
        scope.runCurrent()
        location.emit(fixAt(along))
        scope.runCurrent()
    }

    suspend fun standAt(along: Double) = location.emit(fixAt(along))

    fun ctx(speech: String = "", consent: Boolean = true) = ToolContext(consent, scope.testScheduler.currentTime, zone) { speech }

    fun place(id: String, p: GeoPoint, name: String = id) = Place(id, name, "$name, Toulouse", p, PlaceKind.POI, "test")
}

internal class FakeGeocoder(private val results: Map<String, List<Place>>) : GeocodingProvider {
    override val id = "fake"
    override suspend fun search(query: String, near: GeoPoint?, limit: Int) = Outcome.Success(results[query].orEmpty())
    override suspend fun reverse(point: GeoPoint) = Outcome.Success<Place?>(null)
}

internal class FakeFavorites(var home: Place? = null, var work: Place? = null, var all: List<Place> = emptyList()) : FavoritesRepository {
    override fun observeAll() = flowOf(all)
    override fun observeHome() = flowOf(home)
    override fun observeWork() = flowOf(work)
    override suspend fun isFavorite(id: String) = all.any { it.id == id }
    override suspend fun toggle(place: Place) = Unit
    override suspend fun setHome(place: Place) { home = place }
    override suspend fun setWork(place: Place) { work = place }
    override suspend fun clear() = Unit
}

internal class FakeRecents(var places: List<Place> = emptyList()) : RecentSearchRepository {
    override fun observe() = flowOf(places)
    override suspend fun record(place: Place) = Unit
    override suspend fun clear() = Unit
}

internal class FakeCommunity(configured: Boolean = false, var reports: List<CommunityReport> = emptyList()) : CommunityRepository {
    override val isConfigured = MutableStateFlow(configured)
    val sent = mutableListOf<ReportKind>()
    val votes = mutableListOf<String>()
    override suspend fun report(kind: ReportKind, at: GeoPoint, headingDeg: Float?): Outcome<CommunityReport> {
        sent += kind
        return reports.firstOrNull()?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.NoResult)
    }
    override suspend fun nearby(box: BoundingBox) = Outcome.Success(reports)
    override suspend fun vote(reportId: String, confirm: Boolean): Outcome<CommunityReport> {
        votes += reportId
        return reports.firstOrNull { it.id == reportId }?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.NoResult)
    }
}
```

Lire `FavoritesRepository`, `RecentSearchRepository` et `GeocodingProvider` avant d'écrire les doubles : les signatures doivent correspondre exactement (ajouter les paramètres par défaut manquants).

- [ ] **Step 4: Write the failing tests `NavigationToolsTest.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import app.cash.turbine.test
import fr.roadly.core.navigation.NavRequest
import fr.roadly.core.navigation.NavigationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationToolsTest {
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun ToolOutput.data() = (this as ToolOutput.Done).data

    @Test fun `status says not guiding when idle`() = runTest {
        val f = GuidingFixture(this)
        val out = GetNavigationStatusTool(f.session, f.guidance, FakeCommunity()).run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("false", out.str("guiding"))
    }

    @Test fun `status reports real remaining distance, arrival, next instruction and unknown traffic`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val g = f.session.state.value as NavigationState.Guiding
        val out = GetNavigationStatusTool(f.session, f.guidance, FakeCommunity()).run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("true", out.str("guiding"))
        assertEquals("Capitole", out.str("destination"))
        assertEquals(ToolFormat.km(g.progress.remainingDistanceM).toString(), out.str("remaining_km"))
        assertEquals(ToolFormat.clock(g.etaEpochMs, f.zone), out.str("arrival_time"))
        assertTrue(out.str("next_instruction").isNotBlank())
        assertTrue(out.str("traffic").startsWith("inconnu"))
        assertEquals("non configuré", out.str("community_reports"))
    }

    @Test fun `next instruction is unavailable without guidance`() = runTest {
        val f = GuidingFixture(this)
        assertEquals("unavailable", GetNextInstructionTool(f.session).run(JsonObject(emptyMap()), f.ctx()).data().str("status"))
    }

    @Test fun `start navigation asks confirmation, then starts guidance and opens the guidance screen`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        val capitole = f.place("cap", f.tracker.pointAt(1_300.0), "Place du Capitole")
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(mapOf("capitole" to listOf(capitole))), f.location, f.places)
        val tool = DestinationTool("startNavigation", "Démarrer", "Démarrer le guidage vers", resolver, f.guidance, f.navigator)
        val out = tool.run(buildJsonObject { put("destination", "capitole") }, f.ctx()) as ToolOutput.NeedsConfirmation
        assertTrue(out.summary.contains("Place du Capitole"))
        assertTrue(f.session.state.value !is NavigationState.Guiding)
        f.navigator.requests.test {
            assertEquals("ok", out.action().str("status"))
            runCurrent()
            assertEquals(NavRequest.OpenGuidance, awaitItem())
        }
        assertEquals("Place du Capitole", f.guidance.destinationName)
    }

    @Test fun `home words resolve to the saved home favourite`() = runTest {
        val f = GuidingFixture(this)
        val home = f.place("home", f.tracker.pointAt(900.0), "Maison")
        val resolver = DestinationResolver(FakeFavorites(home = home), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        assertEquals(listOf(home), resolver.resolve("à la maison", null))
        assertEquals(listOf(home), resolver.resolve("chez moi", null))
    }

    @Test fun `an unknown destination is a not_found error, never a guess`() = runTest {
        val f = GuidingFixture(this)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        val out = DestinationTool("startNavigation", "Démarrer", "Démarrer le guidage vers", resolver, f.guidance, f.navigator).run(buildJsonObject { put("destination", "zzz") }, f.ctx()).data()
        assertEquals("not_found", out.str("code"))
    }

    @Test fun `stop navigation is unavailable when idle and confirmed before stopping`() = runTest {
        val f = GuidingFixture(this)
        assertEquals("unavailable", StopNavigationTool(f.session, f.guidance).run(JsonObject(emptyMap()), f.ctx()).data().str("status"))
        f.guideAt(300.0)
        val out = StopNavigationTool(f.session, f.guidance).run(JsonObject(emptyMap()), f.ctx()) as ToolOutput.NeedsConfirmation
        out.action(); runCurrent()
        assertEquals(NavigationState.Idle, f.session.state.value)
    }

    @Test fun `recalculate reroutes from the snapped position`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val out = RecalculateRouteTool(f.session, f.guidance).run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("ok", out.str("status"))
        assertEquals((f.session.state.value as NavigationState.Guiding).progress.snapped, f.requests.last().origin)
    }
}
```

- [ ] **Step 5: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --tests "*NavigationToolsTest" --offline -q`
Expected: FAIL — unresolved references.

- [ ] **Step 6: Implement `DestinationResolver.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.common.getOrNull
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.RecentSearchRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.Place
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/** Destination parlée → lieux : place_id connu, Domicile / Travail, favori ou récent de même nom, puis géocodage près de la position. */
class DestinationResolver @Inject constructor(
    private val favorites: FavoritesRepository,
    private val recents: RecentSearchRepository,
    private val geocoder: GeocodingProvider,
    private val location: LocationRepository,
    private val places: ToolPlaces,
) {
    suspend fun resolve(query: String?, placeId: String?): List<Place> {
        placeId?.let { places[it] }?.let { return listOf(it) }
        val raw = query?.trim()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val q = normalize(raw)
        if (HOME.any { q.contains(it) }) favorites.observeHome().first()?.let { return listOf(it) }
        if (WORK.any { q.contains(it) }) favorites.observeWork().first()?.let { return listOf(it) }
        (favorites.observeAll().first() + recents.observe().first()).firstOrNull { normalize(it.name) == q }?.let { places.remember(it); return listOf(it) }
        return geocoder.search(raw, location.lastKnown?.point, 3).getOrNull().orEmpty().take(3).onEach { places.remember(it) }
    }

    private fun normalize(s: String): String = Normalizer.normalize(s.lowercase(Locale.FRENCH), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private companion object {
        val HOME = listOf("maison", "domicile", "chez moi")
        val WORK = listOf("travail", "boulot", "bureau")
    }
}
```

- [ ] **Step 7: Implement `NavigationTools.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.getOrNull
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.community.box
import fr.roadly.core.model.HazardKind
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.reportsAhead
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import kotlin.math.roundToInt

private val NO_ARGS = emptyList<ToolParam>()
private fun guiding(session: NavigationSession) = session.state.value as? NavigationState.Guiding
private val NO_GUIDANCE = ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours."))

class GetNavigationStatusTool @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val community: CommunityRepository,
) : AssistantTool {
    override val schema = ToolSchema("getNavigationStatus", "État réel du guidage : destination, distance et durée restantes, heure d'arrivée, prochaine instruction, arrêts, virages et zones de vigilance à venir, pluie, signalements à venir (avec report_id). Le trafic est inconnu.", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return ToolOutput.Done(notGuiding(session.state.value))
        val reports = if (community.isConfigured.first()) community.nearby(g.progress.snapped.box()).getOrNull()?.let { reportsAhead(g, it) }.orEmpty() else null
        return ToolOutput.Done(ToolJson.ok {
            put("guiding", true)
            guidance.destinationName?.let { put("destination", it) }
            putProgress(g, ctx.zone)
            putJsonArray("stops") {
                guidance.remainingStops().forEach { s -> addJsonObject { put("stop_id", s.id); put("name", s.place.name); put("in_km", ToolFormat.km(s.alongM - g.progress.distanceAlongM)) } }
            }
            putJsonArray("hazards_ahead") {
                g.hazards.filter { it.startAlongM > g.progress.distanceAlongM }.sortedBy { it.startAlongM }.take(5).forEach { h ->
                    addJsonObject {
                        put("kind", if (h.kind == HazardKind.CURVE) "virage serré" else "zone de vigilance")
                        put("in_m", (h.startAlongM - g.progress.distanceAlongM).roundToInt())
                        h.speedLimitKmh?.let { put("limit_kmh", it) }
                    }
                }
            }
            g.raining?.let { put("raining", it) }
            if (reports == null) put("community_reports", "non configuré")
            else putJsonArray("reports_ahead") { reports.take(5).forEach { r -> addJsonObject { put("report_id", r.report.id); put("kind", r.report.kind.name); put("in_m", r.distanceM.roundToInt()) } } }
            put("traffic", "inconnu (Roadly n'a pas de source de trafic)")
        })
    }
}

class GetNextInstructionTool @Inject constructor(private val session: NavigationSession) : AssistantTool {
    override val schema = ToolSchema("getNextInstruction", "Prochaine manœuvre du guidage et la distance pour l'atteindre, puis les deux suivantes.", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        val m = g.route.maneuvers.getOrNull(g.progress.nextManeuverIndex) ?: return ToolOutput.Done(ToolJson.unavailable("Aucune manœuvre restante."))
        return ToolOutput.Done(ToolJson.ok {
            put("instruction", m.verbalPre ?: m.instruction)
            put("in_m", g.progress.distanceToNextManeuverM.toDouble().roundToInt())
            m.streetName?.let { put("street", it) }
            m.exitNumber?.let { put("exit", it) }
            putJsonArray("then") { g.upcoming.take(2).forEach { u -> addJsonObject { put("instruction", u.instruction) } } }
        })
    }
}

class GetDistanceRemainingTool @Inject constructor(private val session: NavigationSession) : AssistantTool {
    override val schema = ToolSchema("getDistanceRemaining", "Distance et durée restantes jusqu'à la destination, d'après le moteur de guidage.", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        return ToolOutput.Done(ToolJson.ok {
            put("remaining_km", ToolFormat.km(g.progress.remainingDistanceM))
            put("remaining_min", ToolFormat.minutes(g.progress.remainingDurationS.toDouble()))
        })
    }
}

class GetArrivalTimeTool @Inject constructor(private val session: NavigationSession) : AssistantTool {
    override val schema = ToolSchema("getArrivalTime", "Heure d'arrivée estimée par le moteur de guidage (sans trafic prédictif).", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        return ToolOutput.Done(ToolJson.ok {
            put("arrival_time", ToolFormat.clock(g.etaEpochMs, ctx.zone))
            put("remaining_min", ToolFormat.minutes(g.progress.remainingDurationS.toDouble()))
        })
    }
}

/** `startNavigation` et `changeDestination` : même résolution, même confirmation, même démarrage. */
class DestinationTool(
    name: String,
    description: String,
    private val verb: String,
    private val resolver: DestinationResolver,
    private val guidance: GuidanceActions,
    private val navigator: AppNavigator,
) : AssistantTool {
    override val schema = ToolSchema(name, description, listOf(
        ToolParam("destination", ParamType.STRING, "Destination telle que dite par l'utilisateur (adresse, lieu, « maison », « travail »).", required = false),
        ToolParam("placeId", ParamType.STRING, "place_id d'un lieu déjà renvoyé par un outil.", required = false),
    ))

    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val query = args.string("destination")
        val candidates = resolver.resolve(query, args.string("placeId"))
        val place = candidates.firstOrNull()
            ?: return ToolOutput.Done(ToolJson.error("not_found", "Aucun lieu trouvé pour « ${query.orEmpty()} » ; demandez une précision (ville, adresse)."))
        val details = buildJsonObject {
            putPlace("destination", place)
            putJsonArray("other_candidates") { candidates.drop(1).forEach { c -> addJsonObject { put("place_id", c.id); put("name", c.name); c.address?.let { put("address", it) } } } }
        }
        return ToolOutput.NeedsConfirmation("$verb ${place.name}" + (place.address?.let { " ($it)" } ?: ""), details) {
            when (val r = guidance.startTo(place)) {
                is Outcome.Success -> {
                    navigator.navigate(NavRequest.OpenGuidance)
                    ToolJson.ok { put("destination", place.name); putOption(r.value, ctx.zone) }
                }
                is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            }
        }
    }
}

class StopNavigationTool @Inject constructor(private val session: NavigationSession, private val guidance: GuidanceActions) : AssistantTool {
    override val schema = ToolSchema("stopNavigation", "Arrête le guidage en cours (après confirmation).", NO_ARGS)
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (guiding(session) == null) return NO_GUIDANCE
        return ToolOutput.NeedsConfirmation("Arrêter le guidage" + (guidance.destinationName?.let { " vers $it" } ?: "")) {
            guidance.stop()
            ToolJson.ok { put("stopped", true) }
        }
    }
}

class RecalculateRouteTool @Inject constructor(private val session: NavigationSession, private val guidance: GuidanceActions) : AssistantTool {
    override val schema = ToolSchema("recalculateRoute", "Recalcule l'itinéraire depuis la position actuelle, mêmes options et arrêts.", NO_ARGS)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (guiding(session) == null) return NO_GUIDANCE
        return ToolOutput.Done(when (val r = guidance.recalculate()) {
            is Outcome.Success -> ToolJson.ok { putOption(r.value, ctx.zone) }
            is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
        })
    }
}

class EndConversationTool @Inject constructor() : AssistantTool {
    override val schema = ToolSchema(NAME, "L'utilisateur a terminé (« merci, c'est tout ») : la conversation se ferme après ta réponse.", NO_ARGS)
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext) = ToolOutput.Done(ToolJson.ok { put("closing", true) })
    companion object { const val NAME = "endConversation" }
}
```

- [ ] **Step 8: Run tests**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS (8 nouveaux tests).

- [ ] **Step 9: Commit**

```bash
git add core/navigation feature/navigation core/assistant
git commit -m "feat(assistant): outils de guidage — statut réel, instruction, démarrer/changer/arrêter avec confirmation"
```

---

### Task 9: Outils d'itinéraire (péages, autoroutes, alternatives, heure d'arrivée, arrêts)

**Files:**
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/RouteTools.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/tools/RouteToolsTest.kt`

**Interfaces:**
- Consumes: `GuidanceActions.applyPreferences / alternativesFromHere / select / addStop / removeStop / remainingStops`, `ToolRoutes`, `DestinationResolver`, `RouteAlternativesUseCase`, `UserPreferencesRepository`, `VehicleProfileRepository`, `LocationRepository`.
- Produces : `AvoidOptionTool(name, description, update: (RoutePreferences, Boolean) -> RoutePreferences, prefs, guidance)` (instances `avoidTolls`, `avoidHighways`), `FindAlternativeRoutesTool`, `SelectRouteTool`, `SetArrivalTimeTool`, `AddStopTool`, `RemoveStopTool`, `GetStopsTool` ; `internal fun parseClockTime(text: String, nowMs: Long, zone: ZoneId): Long?`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class RouteToolsTest {
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun ToolOutput.data() = (this as ToolOutput.Done).data
    private fun avoidTolls(f: GuidingFixture) = AvoidOptionTool("avoidTolls", "Éviter les péages", { p, on -> p.copy(avoidTolls = on) }, f.prefs, f.guidance)

    @Test fun `avoiding tolls while guiding persists, reroutes and compares durations`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        f.durationFor = { r -> if (r.preferences.avoidTolls) f.route.durationSeconds + 600 else f.route.durationSeconds }
        val out = avoidTolls(f).run(buildJsonObject { put("enabled", true) }, f.ctx()).data()
        assertEquals("ok", out.str("status"))
        assertTrue(f.prefs.routePreferences.first().avoidTolls)
        assertTrue(f.requests.last().preferences.avoidTolls)
        assertTrue(out.str("after_min").toInt() > out.str("before_min").toInt())
        assertTrue("after_arrival" in out)
    }

    @Test fun `avoiding tolls without guidance only saves the preference`() = runTest {
        val f = GuidingFixture(this)
        val out = avoidTolls(f).run(buildJsonObject { put("enabled", true) }, f.ctx()).data()
        assertEquals("false", out.str("guiding"))
        assertTrue(f.prefs.routePreferences.first().avoidTolls)
    }

    @Test fun `alternatives get short ids and selecting one switches guidance`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val routes = ToolRoutes()
        val out = FindAlternativeRoutesTool(f.session, f.guidance, routes).run(JsonObject(emptyMap()), f.ctx()).data()
        val options = out["routes"]!!.jsonArray
        assertEquals(3, options.size)
        assertEquals("r2", options[1].jsonObject.str("route_id"))
        SelectRouteTool(f.session, f.guidance, routes).run(buildJsonObject { put("routeId", "r2") }, f.ctx()).data()
        runCurrent()
        assertEquals(routes["r2"]!!.route.id, (f.session.state.value as NavigationState.Guiding).route.id)
        assertEquals("unknown_route", SelectRouteTool(f.session, f.guidance, routes).run(buildJsonObject { put("routeId", "r9") }, f.ctx()).data().str("code"))
    }

    @Test fun `arrival time while guiding compares with the real ETA`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        val tool = SetArrivalTimeTool(f.session, resolver, RouteAlternativesUseCase(f.routing), f.prefs, f.vehicles, f.location)
        val out = tool.run(buildJsonObject { put("time", "23:59") }, f.ctx()).data()
        assertEquals("true", out.str("on_time"))
        assertTrue(out.str("note").contains("trafic"))
    }

    @Test fun `clock times are parsed in several spoken forms, the next occurrence`() {
        val zone = ZoneId.of("Europe/Paris")
        val now = ZonedDateTime.of(2026, 10, 31, 17, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(ZonedDateTime.of(2026, 10, 31, 18, 30, 0, 0, zone).toInstant().toEpochMilli(), parseClockTime("18:30", now, zone))
        assertEquals(ZonedDateTime.of(2026, 10, 31, 18, 0, 0, 0, zone).toInstant().toEpochMilli(), parseClockTime("18h", now, zone))
        assertEquals(ZonedDateTime.of(2026, 11, 1, 8, 15, 0, 0, zone).toInstant().toEpochMilli(), parseClockTime("8h15", now, zone))
        assertNull(parseClockTime("demain", now, zone))
    }

    @Test fun `adding a stop is confirmed and reports the extra minutes`() = runTest {
        val f = GuidingFixture(this)
        f.guideAt(300.0)
        val stop = f.place("s1", f.tracker.pointAt(800.0), "Boulangerie")
        f.places.remember(stop)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        f.durationFor = { r -> if (r.waypoints.isNotEmpty()) f.route.durationSeconds + 300 else f.route.durationSeconds }
        val asked = AddStopTool(f.session, resolver, f.guidance).run(buildJsonObject { put("placeId", "s1") }, f.ctx()) as ToolOutput.NeedsConfirmation
        assertTrue(asked.summary.contains("Boulangerie"))
        val done = asked.action(); runCurrent()
        assertEquals("ok", done.str("status"))
        assertEquals(listOf("s1"), f.guidance.remainingStops().map { it.id })
        val stops = GetStopsTool(f.session, f.guidance).run(JsonObject(emptyMap()), f.ctx()).data()["stops"]!!.jsonArray
        assertEquals("s1", stops.single().jsonObject.str("stop_id"))
        val remove = RemoveStopTool(f.session, f.guidance).run(buildJsonObject { put("stopId", "s1") }, f.ctx()) as ToolOutput.NeedsConfirmation
        remove.action(); runCurrent()
        assertTrue(f.guidance.remainingStops().isEmpty())
    }

    @Test fun `route tools refuse honestly without guidance`() = runTest {
        val f = GuidingFixture(this)
        val resolver = DestinationResolver(FakeFavorites(), FakeRecents(), FakeGeocoder(emptyMap()), f.location, f.places)
        assertEquals("unavailable", AddStopTool(f.session, resolver, f.guidance).run(buildJsonObject { put("destination", "x") }, f.ctx()).data().str("status"))
        assertEquals("unavailable", FindAlternativeRoutesTool(f.session, f.guidance, ToolRoutes()).run(JsonObject(emptyMap()), f.ctx()).data().str("status"))
        assertEquals(RoutePreferences(), f.prefs.routePreferences.first())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --tests "*RouteToolsTest" --offline -q`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement `RouteTools.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

private fun guiding(session: NavigationSession) = session.state.value as? NavigationState.Guiding
private val NO_GUIDANCE = ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours."))
private const val NO_TRAFFIC_NOTE = "Estimation sans trafic prédictif : Roadly n'a pas de source de trafic."

/** `avoidTolls` / `avoidHighways` : enregistre la préférence ; en guidage, recalcule et compare. */
class AvoidOptionTool(
    name: String,
    description: String,
    private val update: (RoutePreferences, Boolean) -> RoutePreferences,
    private val prefs: UserPreferencesRepository,
    private val guidance: GuidanceActions,
) : AssistantTool {
    override val schema = ToolSchema(name, description, listOf(ToolParam("enabled", ParamType.BOOLEAN, "true pour éviter, false pour autoriser de nouveau.", required = false)))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val enabled = args.bool("enabled") ?: true
        val next = update(prefs.routePreferences.first(), enabled)
        return ToolOutput.Done(when (val r = guidance.applyPreferences(next)) {
            is Outcome.Failure -> ToolJson.error("route_failed", "Préférence enregistrée mais recalcul impossible : ${ToolFormat.describe(r.error)}.")
            is Outcome.Success -> {
                val (before, after) = r.value
                if (before == null || after == null) ToolJson.ok { put("guiding", false); put("saved", true) }
                else ToolJson.ok {
                    put("guiding", true)
                    put("before_min", ToolFormat.minutes(before.route.durationSeconds))
                    put("after_min", ToolFormat.minutes(after.route.durationSeconds))
                    put("before_arrival", ToolFormat.clock(before.arrivalEpochMs, ctx.zone))
                    put("after_arrival", ToolFormat.clock(after.arrivalEpochMs, ctx.zone))
                    put("after_has_toll", after.route.hasToll)
                }
            }
        })
    }
}

class FindAlternativeRoutesTool @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val routes: ToolRoutes,
) : AssistantTool {
    override val schema = ToolSchema("findAlternativeRoutes", "Calcule depuis la position les itinéraires le plus rapide, le plus court et sans péage, avec un route_id chacun.")
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        return ToolOutput.Done(when (val r = guidance.alternativesFromHere()) {
            is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            is Outcome.Success -> ToolJson.ok {
                put("current_remaining_min", ToolFormat.minutes(g.progress.remainingDurationS.toDouble()))
                putJsonArray("routes") {
                    routes.replace(r.value).forEach { (id, o) -> addJsonObject { put("route_id", id); put("label", o.label); putOption(o, ctx.zone) } }
                }
                put("note", NO_TRAFFIC_NOTE)
            }
        })
    }
}

class SelectRouteTool @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val routes: ToolRoutes,
) : AssistantTool {
    override val schema = ToolSchema("selectRoute", "Bascule le guidage sur un itinéraire proposé par findAlternativeRoutes.", listOf(ToolParam("routeId", ParamType.STRING, "route_id choisi par l'utilisateur.")))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (guiding(session) == null) return NO_GUIDANCE
        val option = routes[args.string("routeId")!!] ?: return ToolOutput.Done(ToolJson.error("unknown_route", "route_id inconnu ; appelez d'abord findAlternativeRoutes."))
        guidance.select(option)
        return ToolOutput.Done(ToolJson.ok { put("label", option.label); putOption(option, ctx.zone) })
    }
}

class SetArrivalTimeTool @Inject constructor(
    private val session: NavigationSession,
    private val resolver: DestinationResolver,
    private val alternatives: RouteAlternativesUseCase,
    private val prefs: UserPreferencesRepository,
    private val vehicles: VehicleProfileRepository,
    private val location: LocationRepository,
) : AssistantTool {
    override val schema = ToolSchema("setArrivalTime", "Arriver à une heure : en guidage, dit si l'heure est tenable ; sinon donne l'heure de départ conseillée vers la destination.", listOf(
        ToolParam("time", ParamType.STRING, "Heure souhaitée, ex. « 18:30 » ou « 18h30 »."),
        ToolParam("destination", ParamType.STRING, "Destination, si aucun guidage n'est en cours.", required = false),
    ))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val target = parseClockTime(args.string("time")!!, ctx.nowMs, ctx.zone)
            ?: return ToolOutput.Done(ToolJson.error("invalid_time", "Heure non comprise ; demandez une heure du type 18h30."))
        guiding(session)?.let { g ->
            val marginMin = ((target - g.etaEpochMs) / 60_000L).toInt()
            return ToolOutput.Done(ToolJson.ok {
                put("on_time", marginMin >= 0)
                put("arrival_time", ToolFormat.clock(g.etaEpochMs, ctx.zone))
                put("margin_min", marginMin)
                put("note", NO_TRAFFIC_NOTE)
            })
        }
        val place = resolver.resolve(args.string("destination"), null).firstOrNull()
            ?: return ToolOutput.Done(ToolJson.error("missing_destination", "Aucun guidage en cours : demandez la destination."))
        val origin = location.lastKnown?.point ?: return ToolOutput.Done(ToolJson.unavailable("Position inconnue."))
        return ToolOutput.Done(when (val r = alternatives(origin, place.point, vehicles.profile.first(), ctx.nowMs, prefs.routePreferences.first())) {
            is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            is Outcome.Success -> {
                val fastest = r.value.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: r.value.first()
                val depart = target - (fastest.route.durationSeconds * 1000).toLong()
                ToolJson.ok {
                    putPlace("destination", place)
                    put("depart_time", ToolFormat.clock(depart, ctx.zone))
                    put("duration_min", ToolFormat.minutes(fastest.route.durationSeconds))
                    put("already_late", depart < ctx.nowMs)
                    put("note", NO_TRAFFIC_NOTE)
                }
            }
        })
    }
}

/** « 18:30 », « 18h30 », « 18h », « 18 h 30 » → prochaine occurrence (aujourd'hui, sinon demain). */
internal fun parseClockTime(text: String, nowMs: Long, zone: ZoneId): Long? {
    val m = Regex("""(\d{1,2})\s*[h:]\s*(\d{2})?""").find(text.lowercase()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    if (h > 23 || min > 59) return null
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    var at = now.with(LocalTime.of(h, min))
    if (!at.isAfter(now)) at = at.plusDays(1)
    return at.toInstant().toEpochMilli()
}

class AddStopTool @Inject constructor(
    private val session: NavigationSession,
    private val resolver: DestinationResolver,
    private val guidance: GuidanceActions,
) : AssistantTool {
    override val schema = ToolSchema("addStop", "Ajoute un arrêt au guidage en cours (10 maximum), après confirmation ; renvoie le temps ajouté.", listOf(
        ToolParam("destination", ParamType.STRING, "Lieu de l'arrêt tel que dit par l'utilisateur.", required = false),
        ToolParam("placeId", ParamType.STRING, "place_id d'un lieu déjà renvoyé (ex. station).", required = false),
    ))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours : démarrez d'abord un guidage."))
        val place = resolver.resolve(args.string("destination"), args.string("placeId")).firstOrNull()
            ?: return ToolOutput.Done(ToolJson.error("not_found", "Lieu de l'arrêt introuvable."))
        val beforeS = g.progress.remainingDurationS.toDouble()
        return ToolOutput.NeedsConfirmation("Ajouter l'arrêt ${place.name}", kotlinx.serialization.json.buildJsonObject { putPlace("stop", place) }) {
            when (val r = guidance.addStop(place)) {
                is Outcome.Success -> ToolJson.ok {
                    put("stop", place.name)
                    put("extra_min", ToolFormat.minutes(r.value.route.durationSeconds - beforeS).coerceAtLeast(0))
                    putOption(r.value, ctx.zone)
                }
                is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            }
        }
    }
}

class RemoveStopTool @Inject constructor(private val session: NavigationSession, private val guidance: GuidanceActions) : AssistantTool {
    override val schema = ToolSchema("removeStop", "Retire un arrêt du guidage en cours, après confirmation.", listOf(ToolParam("stopId", ParamType.STRING, "stop_id renvoyé par getStops ou getNavigationStatus.")))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (guiding(session) == null) return NO_GUIDANCE
        val stop = guidance.remainingStops().firstOrNull { it.id == args.string("stopId") }
            ?: return ToolOutput.Done(ToolJson.error("unknown_stop", "Arrêt inconnu ; appelez getStops."))
        return ToolOutput.NeedsConfirmation("Retirer l'arrêt ${stop.place.name}") {
            when (val r = guidance.removeStop(stop.id)) {
                is Outcome.Success -> ToolJson.ok { put("removed", stop.place.name); putOption(r.value, ctx.zone) }
                is Outcome.Failure -> ToolJson.error("route_failed", ToolFormat.describe(r.error))
            }
        }
    }
}

class GetStopsTool @Inject constructor(private val session: NavigationSession, private val guidance: GuidanceActions) : AssistantTool {
    override val schema = ToolSchema("getStops", "Arrêts restants du guidage en cours, dans l'ordre.")
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return NO_GUIDANCE
        return ToolOutput.Done(ToolJson.ok {
            putJsonArray("stops") {
                guidance.remainingStops().forEach { s -> addJsonObject { put("stop_id", s.id); put("name", s.place.name); put("in_km", ToolFormat.km(s.alongM - g.progress.distanceAlongM)) } }
            }
        })
    }
}
```

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS (7 nouveaux tests).

- [ ] **Step 5: Commit**

```bash
git add core/assistant
git commit -m "feat(assistant): outils d'itinéraire — péages, autoroutes, alternatives, heure d'arrivée, arrêts"
```

---

### Task 10: Outils de services (lieux proches, carburant, coût, trafic, signalements, véhicule)

**Files:**
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/ServiceTools.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/tools/ServiceToolsTest.kt`

**Interfaces:**
- Consumes: `NearbyPoiProvider`, `PoiCategory` (`core:geocoding`), `FuelStationProvider` (`core:data` `fuel`), `FuelCostCalculator`, `CommunityRepository`, `VehicleProfileRepository`, `RouteTracker.pointAt`, `RouteGeometry.project`, `AssistantCards`, `ToolPlaces`, `GuidanceActions.remainingStops`.
- Produces : `ShowNearbyTool(name, description, categories, title, session, nearby, location, navigator, cards, places)` (instances `showGasStations`, `showParking`, `showChargingStations`, `showNearbyServices`), `GetFuelPricesTool`, `FindCheapestFuelOnRouteTool`, `GetEstimatedTripCostTool`, `ShowTrafficTool`, `ReportIncidentTool`, `ConfirmIncidentTool`, `GetVehicleProfileTool`, `ChangeVehicleProfileTool`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package fr.roadly.core.assistant.tools

import app.cash.turbine.test
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.fuel.FuelStationProvider
import fr.roadly.core.geocoding.NearbyPoiProvider
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.FuelPrice
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.NavRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceToolsTest {
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    private fun ToolOutput.data() = (this as ToolOutput.Done).data
    private fun station(id: String, p: GeoPoint, e10: Double?) =
        FuelStation(id, "Station $id", "Rue $id", p, listOfNotNull(e10?.let { FuelPrice(FuelType.E10, it, 0L) }), open24h = false, services = emptyList())
    private class Stations(val list: List<FuelStation>) : FuelStationProvider {
        override val id = "fake"
        override suspend fun nearby(near: GeoPoint, radiusKm: Int, limit: Int) = Outcome.Success(list)
    }
    private class Pois(val list: List<Place>) : NearbyPoiProvider {
        override val id = "fake"
        override suspend fun nearby(category: PoiCategory, near: GeoPoint, limit: Int) = Outcome.Success(list)
    }

    @Test fun `traffic is honestly unavailable`() = runTest {
        val f = GuidingFixture(this)
        assertEquals("unavailable", ShowTrafficTool().run(JsonObject(emptyMap()), f.ctx()).data().str("status"))
    }

    @Test fun `gas stations open the filtered search when not guiding`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        val cards = AssistantCards()
        val tool = ShowNearbyTool("showGasStations", "Stations", listOf(PoiCategory.FUEL), "Stations", f.session, Pois(listOf(f.place("p1", f.tracker.pointAt(400.0)))), f.location, f.navigator, cards, f.places)
        f.navigator.requests.test {
            val out = tool.run(JsonObject(emptyMap()), f.ctx()).data()
            assertEquals(NavRequest.OpenSearch("FUEL"), awaitItem())
            assertEquals("true", out.str("opened_search"))
            assertEquals("p1", out["places"]!!.jsonArray.single().jsonObject.str("place_id"))
        }
        assertEquals(null, cards.card.value)
    }

    @Test fun `fuel prices are sorted cheapest first and stations without the fuel are skipped`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        val tool = GetFuelPricesTool(f.session, Stations(listOf(station("a", f.tracker.pointAt(300.0), 1.90), station("b", f.tracker.pointAt(600.0), 1.75), station("c", f.tracker.pointAt(700.0), null))), f.vehicles, f.location, f.places)
        val rows = tool.run(JsonObject(emptyMap()), f.ctx()).data()["stations"]!!.jsonArray
        assertEquals(listOf("Station b", "Station a"), rows.map { it.jsonObject.str("name") })
        assertEquals("1.75", rows[0].jsonObject.str("price_eur"))
    }

    @Test fun `cheapest fuel on route keeps stations near the route ahead and computes the real detour`() = runTest {
        val f = GuidingFixture(this)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        f.guideAt(200.0)
        val ahead = station("ahead", f.tracker.pointAt(900.0), 1.70)
        val behind = station("behind", f.tracker.pointAt(50.0), 1.50)
        val far = station("far", GeoPoint(f.tracker.pointAt(900.0).lat + 0.05, f.tracker.pointAt(900.0).lon), 1.40)
        f.durationFor = { r -> if (r.waypoints.isNotEmpty()) f.route.durationSeconds + 240 else f.route.durationSeconds }
        val cards = AssistantCards()
        val tool = FindCheapestFuelOnRouteTool(f.session, f.guidance, Stations(listOf(ahead, behind, far)), f.vehicles, f.routing, f.prefs, cards, f.places)
        val rows = tool.run(JsonObject(emptyMap()), f.ctx()).data()["stations"]!!.jsonArray
        assertEquals(listOf("Station ahead"), rows.map { it.jsonObject.str("name") })
        assertTrue(rows[0].jsonObject.str("detour_min").toInt() >= 2)
        assertEquals("fuel:ahead", rows[0].jsonObject.str("place_id"))
        assertEquals("Station ahead", cards.card.value!!.rows.single().title)
    }

    @Test fun `trip cost uses the profile and says tolls are not estimated`() = runTest {
        val f = GuidingFixture(this)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        f.guideAt(200.0)
        val out = GetEstimatedTripCostTool(f.session, f.vehicles).run(JsonObject(emptyMap()), f.ctx()).data()
        assertEquals("ok", out.str("status"))
        assertTrue(out.str("tolls").contains("non estimés"))
    }

    @Test fun `reports need a configured community service`() = runTest {
        val f = GuidingFixture(this)
        f.standAt(0.0)
        val off = FakeCommunity(configured = false)
        assertEquals("unavailable", ReportIncidentTool(f.session, f.location, off).run(buildJsonObject { put("type", "ACCIDENT") }, f.ctx()).data().str("status"))
        val report = CommunityReport("r1", ReportKind.ACCIDENT, f.tracker.pointAt(0.0), 0L, 1L, 0, 0)
        val on = FakeCommunity(configured = true, reports = listOf(report))
        assertEquals("ok", ReportIncidentTool(f.session, f.location, on).run(buildJsonObject { put("type", "ACCIDENT") }, f.ctx()).data().str("status"))
        assertEquals(listOf(ReportKind.ACCIDENT), on.sent)
        ConfirmIncidentTool(on).run(buildJsonObject { put("reportId", "r1") }, f.ctx())
        assertEquals(listOf("r1"), on.votes)
    }

    @Test fun `changing the vehicle type is confirmed and keeps the other settings`() = runTest {
        val f = GuidingFixture(this)
        f.vehicle = VehicleProfile(VehicleType.SEDAN, FuelType.E10, 6.0, 1.8)
        val asked = ChangeVehicleProfileTool(f.vehicles).run(buildJsonObject { put("type", "MOTORCYCLE") }, f.ctx()) as ToolOutput.NeedsConfirmation
        asked.action()
        assertEquals(VehicleProfile(VehicleType.MOTORCYCLE, FuelType.E10, 6.0, 1.8), f.vehicle)
    }
}
```

Le constructeur de `CommunityReport` du test doit suivre `core/model/.../CommunityReport.kt` (lire le fichier : `id, kind, point, createdAtEpochMs, expiresAtEpochMs, confirmations, denials` puis éventuels champs supplémentaires à renseigner).

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --tests "*ServiceToolsTest" --offline -q`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement `ServiceTools.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import fr.roadly.core.assistant.provider.ParamType
import fr.roadly.core.assistant.provider.ToolParam
import fr.roadly.core.assistant.provider.ToolSchema
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.getOrNull
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.fuel.FuelStationProvider
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.geocoding.NearbyPoiProvider
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleType
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.routing.FuelCostCalculator
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

private fun guiding(session: NavigationSession) = session.state.value as? NavigationState.Guiding
private fun here(session: NavigationSession, location: LocationRepository): GeoPoint? = guiding(session)?.progress?.snapped ?: location.lastKnown?.point
private val NO_POSITION = ToolOutput.Done(ToolJson.unavailable("Position inconnue."))
private val FUEL_PARAM = ToolParam("fuel", ParamType.STRING, "Carburant ; par défaut celui du profil véhicule.", required = false, enumValues = FuelType.entries.map { it.name })
private fun Double.cents(): Double = (this * 1000).roundToInt() / 1000.0
private fun hoursAgo(epochMs: Long?, nowMs: Long): Int? = epochMs?.let { ((nowMs - it) / 3_600_000L).toInt().coerceAtLeast(0) }

/** Lieux OSM proches ; hors guidage ouvre la recherche filtrée, en guidage les affiche dans la fenêtre compacte. */
class ShowNearbyTool(
    name: String,
    description: String,
    private val categories: List<PoiCategory>,
    private val title: String,
    private val session: NavigationSession,
    private val nearby: NearbyPoiProvider,
    private val location: LocationRepository,
    private val navigator: AppNavigator,
    private val cards: AssistantCards,
    private val places: ToolPlaces,
) : AssistantTool {
    override val schema = ToolSchema(name, description)
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session)
        val at = here(session, location) ?: return NO_POSITION
        val found = categories.flatMap { nearby.nearby(it, at, 10).getOrNull().orEmpty() }.distinctBy { it.id }.sortedBy { at.distanceTo(it.point) }.take(5)
        if (g == null) navigator.navigate(NavRequest.OpenSearch(categories.first().name))
        else if (found.isNotEmpty()) cards.show(ResultCard(title, found.map { CardRow(it.name, Formatters.distance(at.distanceTo(it.point)), places.remember(it)) }))
        return ToolOutput.Done(ToolJson.ok {
            put("opened_search", g == null)
            putJsonArray("places") {
                found.forEach { p -> addJsonObject { put("place_id", places.remember(p)); put("name", p.name); put("distance_m", at.distanceTo(p.point).roundToInt()); p.address?.let { put("address", it) } } }
            }
            if (found.isEmpty()) put("message", "Aucun lieu trouvé à proximité (données OpenStreetMap).")
        })
    }
}

class GetFuelPricesTool @Inject constructor(
    private val session: NavigationSession,
    private val stations: FuelStationProvider,
    private val vehicles: VehicleProfileRepository,
    private val location: LocationRepository,
    private val places: ToolPlaces,
) : AssistantTool {
    override val schema = ToolSchema("getFuelPrices", "Prix publiés (flux officiel prix-carburants) des stations à 10 km, du moins cher au plus cher, avec leur fraîcheur.", listOf(FUEL_PARAM))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val fuel = args.string("fuel")?.let(FuelType::valueOf) ?: vehicles.profile.first()?.fuel
            ?: return ToolOutput.Done(ToolJson.error("fuel_unknown", "Carburant inconnu : demandez-le ou renseignez le profil véhicule."))
        if (fuel == FuelType.ELECTRIC) return ToolOutput.Done(ToolJson.unavailable("Aucune source de prix de recharge."))
        val at = here(session, location) ?: return NO_POSITION
        return ToolOutput.Done(when (val r = stations.nearby(at, radiusKm = 10)) {
            is Outcome.Failure -> ToolJson.error("prices_unavailable", ToolFormat.describe(r.error))
            is Outcome.Success -> {
                val priced = r.value.mapNotNull { s -> s.price(fuel)?.let { s to it } }.sortedBy { it.second.eurPerLitre }.take(5)
                ToolJson.ok {
                    put("fuel", fuel.name)
                    putJsonArray("stations") {
                        priced.forEach { (s, p) ->
                            addJsonObject {
                                put("place_id", places.remember(s.toPlace())); put("name", s.name); put("price_eur", p.eurPerLitre.cents())
                                put("distance_m", at.distanceTo(s.point).roundToInt())
                                hoursAgo(p.updatedAtEpochMs, ctx.nowMs)?.let { put("updated_hours_ago", it) }
                            }
                        }
                    }
                }
            }
        })
    }
}

/** Stations à ≤ 1,5 km du tracé sur les 50 km à venir, triées par prix ; détour réel calculé pour les 3 moins chères. */
class FindCheapestFuelOnRouteTool @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val stations: FuelStationProvider,
    private val vehicles: VehicleProfileRepository,
    private val routing: RoutingProvider,
    private val prefs: UserPreferencesRepository,
    private val cards: AssistantCards,
    private val places: ToolPlaces,
) : AssistantTool {
    override val schema = ToolSchema("findCheapestFuelOnRoute", "Trouve les stations les moins chères sur les 50 km à venir du trajet, avec prix, fraîcheur et détour réel en minutes ; propose ensuite addStop avec le place_id.", listOf(FUEL_PARAM))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours : utilisez getFuelPrices pour les stations autour."))
        val fuel = args.string("fuel")?.let(FuelType::valueOf) ?: vehicles.profile.first()?.fuel
            ?: return ToolOutput.Done(ToolJson.error("fuel_unknown", "Carburant inconnu : demandez-le ou renseignez le profil véhicule."))
        val tracker = RouteTracker(g.route)
        val geo = RouteGeometry(g.route.geometry)
        val start = g.progress.distanceAlongM
        val end = min(start + SEARCH_AHEAD_M, geo.totalM)
        val samples = generateSequence(start) { it + SAMPLE_STEP_M }.takeWhile { it <= end }.map { tracker.pointAt(it) }.toList() + tracker.pointAt(end)
        val candidates = samples.flatMap { stations.nearby(it, radiusKm = 3, limit = 20).getOrNull().orEmpty() }.distinctBy { it.id }
            .mapNotNull { s ->
                val price = s.price(fuel) ?: return@mapNotNull null
                val p = geo.project(s.point) ?: return@mapNotNull null
                if (p.offsetM > MAX_OFFSET_M || p.alongM < start) null else Triple(s, price, p.alongM)
            }
            .sortedBy { it.second.eurPerLitre }.take(3)
        val preferences = prefs.routePreferences.first()
        val vehicle = vehicles.profile.first()
        val stops = guidance.remainingStops()
        val rows = candidates.map { (s, price, along) ->
            val waypoints = (stops.map { it.place.point to it.alongM } + (s.point to along)).sortedBy { it.second }.map { it.first }
            val request = RouteRequest(origin = g.progress.snapped, destination = g.route.geometry.last(), waypoints = waypoints, costing = g.route.costing, preferences = preferences, originHeadingDeg = g.progress.bearingDeg, vehicle = vehicle)
            val detourMin = routing.route(request).getOrNull()?.firstOrNull()?.let { ToolFormat.minutes(it.durationSeconds - g.progress.remainingDurationS.toDouble()).coerceAtLeast(0) }
            Triple(s, price, along) to detourMin
        }
        if (rows.isNotEmpty()) cards.show(ResultCard("Carburant sur le trajet", rows.map { (t, detour) ->
            CardRow(t.first.name, "${t.second.eurPerLitre.cents()} € · " + (detour?.let { "+$it min" } ?: "détour inconnu"), places.remember(t.first.toPlace()))
        }))
        return ToolOutput.Done(ToolJson.ok {
            put("fuel", fuel.name)
            putJsonArray("stations") {
                rows.forEach { (t, detour) ->
                    val (s, price, along) = t
                    addJsonObject {
                        put("place_id", places.remember(s.toPlace())); put("name", s.name); put("price_eur", price.eurPerLitre.cents())
                        put("in_km", ToolFormat.km(along - start))
                        detour?.let { put("detour_min", it) }
                        hoursAgo(price.updatedAtEpochMs, ctx.nowMs)?.let { put("updated_hours_ago", it) }
                    }
                }
            }
            if (rows.isEmpty()) put("message", "Aucune station avec un prix publié pour ce carburant près du trajet sur les 50 km à venir.")
        })
    }

    private companion object {
        const val SEARCH_AHEAD_M = 50_000.0
        const val SAMPLE_STEP_M = 10_000.0
        const val MAX_OFFSET_M = 1_500.0
    }
}

class GetEstimatedTripCostTool @Inject constructor(private val session: NavigationSession, private val vehicles: VehicleProfileRepository) : AssistantTool {
    override val schema = ToolSchema("getEstimatedTripCost", "Coût carburant estimé de la distance restante d'après le profil véhicule (consommation et prix saisis). Péages non estimés.")
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val g = guiding(session) ?: return ToolOutput.Done(ToolJson.unavailable("Aucun guidage en cours."))
        val profile = vehicles.profile.first() ?: return ToolOutput.Done(ToolJson.unavailable("Aucun profil véhicule : à créer dans Réglages."))
        val cost = FuelCostCalculator.estimate(g.progress.remainingDistanceM, profile)
            ?: return ToolOutput.Done(ToolJson.unavailable("Consommation ou prix du carburant non renseignés dans le profil."))
        return ToolOutput.Done(ToolJson.ok {
            put("fuel_eur", (cost.fuelEur * 100).roundToInt() / 100.0)
            put("remaining_km", ToolFormat.km(g.progress.remainingDistanceM))
            put("tolls", "non estimés (aucune source de tarifs de péage)")
        })
    }
}

class ShowTrafficTool @Inject constructor() : AssistantTool {
    override val schema = ToolSchema("showTraffic", "Trafic en temps réel : indisponible dans Roadly.")
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext) =
        ToolOutput.Done(ToolJson.unavailable("Roadly n'a pas de source de trafic en temps réel : impossible d'afficher ou d'estimer le trafic."))
}

class ReportIncidentTool @Inject constructor(
    private val session: NavigationSession,
    private val location: LocationRepository,
    private val community: CommunityRepository,
) : AssistantTool {
    override val schema = ToolSchema("reportIncident", "Signale un événement à la position actuelle (service communautaire Roadly).", listOf(
        ToolParam("type", ParamType.STRING, "Type d'événement.", enumValues = ReportKind.entries.map { it.name }),
    ))
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (!community.isConfigured.first()) return ToolOutput.Done(ToolJson.unavailable("Signalements communautaires non configurés (Réglages → Communauté)."))
        val at = here(session, location) ?: return NO_POSITION
        val heading = guiding(session)?.progress?.bearingDeg ?: location.lastKnown?.bearingDeg
        return ToolOutput.Done(when (val r = community.report(ReportKind.valueOf(args.string("type")!!), at, heading)) {
            is Outcome.Success -> ToolJson.ok { put("report_id", r.value.id) }
            is Outcome.Failure -> ToolJson.error("report_failed", ToolFormat.describe(r.error))
        })
    }
}

class ConfirmIncidentTool @Inject constructor(private val community: CommunityRepository) : AssistantTool {
    override val schema = ToolSchema("confirmIncident", "Confirme qu'un signalement à venir (report_id de getNavigationStatus) est toujours présent.", listOf(
        ToolParam("reportId", ParamType.STRING, "report_id du signalement."),
    ))
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        if (!community.isConfigured.first()) return ToolOutput.Done(ToolJson.unavailable("Signalements communautaires non configurés."))
        return ToolOutput.Done(when (val r = community.vote(args.string("reportId")!!, true)) {
            is Outcome.Success -> ToolJson.ok { put("confirmations", r.value.confirmations) }
            is Outcome.Failure -> ToolJson.error("vote_failed", ToolFormat.describe(r.error))
        })
    }
}

class GetVehicleProfileTool @Inject constructor(private val vehicles: VehicleProfileRepository) : AssistantTool {
    override val schema = ToolSchema("getVehicleProfile", "Profil véhicule enregistré (type, carburant, consommation, prix saisi).")
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val p = vehicles.profile.first() ?: return ToolOutput.Done(ToolJson.unavailable("Aucun profil véhicule enregistré."))
        return ToolOutput.Done(ToolJson.ok {
            put("type", p.type.name); put("fuel", p.fuel.name); put("consumption_per_100", p.consumptionPer100)
            p.fuelPriceEur?.let { put("fuel_price_eur", it) }
            p.critAir?.let { put("crit_air", it.name) }
        })
    }
}

class ChangeVehicleProfileTool @Inject constructor(private val vehicles: VehicleProfileRepository) : AssistantTool {
    override val schema = ToolSchema("changeVehicleProfile", "Change le type (et éventuellement le carburant) du profil véhicule unique, après confirmation ; les itinéraires suivants en tiennent compte.", listOf(
        ToolParam("type", ParamType.STRING, "Type de véhicule.", enumValues = VehicleType.entries.map { it.name }),
        FUEL_PARAM,
    ))
    override val needsLocationConsent = false
    override suspend fun run(args: JsonObject, ctx: ToolContext): ToolOutput {
        val current = vehicles.profile.first() ?: return ToolOutput.Done(ToolJson.unavailable("Aucun profil véhicule : à créer dans Réglages."))
        val next = current.copy(type = VehicleType.valueOf(args.string("type")!!), fuel = args.string("fuel")?.let(FuelType::valueOf) ?: current.fuel)
        return ToolOutput.NeedsConfirmation("Passer le profil véhicule en ${next.type.name.lowercase()} (${next.fuel.name})") {
            vehicles.save(next)
            ToolJson.ok { put("type", next.type.name); put("fuel", next.fuel.name) }
        }
    }
}
```

`CritAir` : si ce n'est pas un enum, remplacer `it.name` par `it.toString()`.

- [ ] **Step 4: Run tests**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS (7 nouveaux tests).

- [ ] **Step 5: Commit**

```bash
git add core/assistant
git commit -m "feat(assistant): outils de services — lieux proches, carburant moins cher sur le trajet, coût, signalements, véhicule"
```

---

### Task 11: Audio — micro, voix, focus

**Files:**
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/audio/AudioPorts.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/audio/AndroidMicCapture.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/audio/AndroidVoicePlayback.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/audio/AndroidAssistantAudioFocus.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/audio/PcmTest.kt`

**Interfaces:**
- Produces :
  - `interface AudioInput { val level: StateFlow<Float>; fun start(onFrame: (ByteArray) -> Unit): Boolean; fun stop() }`
  - `interface AudioOutput { val level: StateFlow<Float>; val playing: StateFlow<Boolean>; fun start(); fun enqueue(pcm: ByteArray); fun pause(); fun resume(); fun flush(); fun stop() }`
  - `enum class FocusChange { PAUSE, RESUME, LOST }` ; `interface AudioFocus { fun acquire(onChange: (FocusChange) -> Unit): Boolean; fun release() }`
  - `object Pcm { fun rms(buf: ByteArray, length: Int): Float }` (0..1, PCM16 LE)

- [ ] **Step 1: Write the failing test**

```kotlin
package fr.roadly.core.assistant.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmTest {
    private fun pcm(vararg samples: Int) = ByteArray(samples.size * 2).also { b ->
        samples.forEachIndexed { i, s -> b[2 * i] = (s and 0xFF).toByte(); b[2 * i + 1] = (s shr 8).toByte() }
    }

    @Test fun `silence is zero and full scale is close to one`() {
        assertEquals(0f, Pcm.rms(pcm(0, 0, 0, 0), 8), 1e-6f)
        assertTrue(Pcm.rms(pcm(32767, -32768, 32767, -32768), 8) > 0.99f)
    }

    @Test fun `only the first length bytes count and odd lengths are safe`() {
        assertEquals(0f, Pcm.rms(pcm(0, 32767), 2), 1e-6f)
        assertEquals(0f, Pcm.rms(pcm(0, 32767), 1), 1e-6f)
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --tests "*PcmTest" --offline -q`
Expected: FAIL — `Unresolved reference: Pcm`.

- [ ] **Step 3: Implement `AudioPorts.kt`**

```kotlin
package fr.roadly.core.assistant.audio

import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

/** Micro 16 kHz mono PCM16 ; [start] faux si permission absente ou micro occupé. */
interface AudioInput {
    val level: StateFlow<Float>
    fun start(onFrame: (ByteArray) -> Unit): Boolean
    fun stop()
}

/** Voix de l'assistant 24 kHz mono PCM16 ; [playing] vrai tant qu'il reste du son en file ou en cours. */
interface AudioOutput {
    val level: StateFlow<Float>
    val playing: StateFlow<Boolean>
    fun start()
    fun enqueue(pcm: ByteArray)
    /** Suspend la lecture en gardant la file (annonce GPS, appel). */
    fun pause()
    fun resume()
    /** Vide la file (interruption par l'utilisateur). */
    fun flush()
    fun stop()
}

enum class FocusChange { PAUSE, RESUME, LOST }

interface AudioFocus {
    fun acquire(onChange: (FocusChange) -> Unit): Boolean
    fun release()
}

object Pcm {
    /** Niveau RMS normalisé (0..1) des [length] premiers octets d'un tampon PCM16 little-endian. */
    fun rms(buf: ByteArray, length: Int): Float {
        val samples = length.coerceAtMost(buf.size) / 2
        if (samples == 0) return 0f
        var sum = 0.0
        for (i in 0 until samples) {
            val s = (buf[2 * i].toInt() and 0xFF) or (buf[2 * i + 1].toInt() shl 8)
            sum += s.toDouble() * s
        }
        return (sqrt(sum / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}
```

- [ ] **Step 4: Implement the Android adapters**

`AndroidMicCapture.kt` :

```kotlin
package fr.roadly.core.assistant.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** `VOICE_COMMUNICATION` : traitement voix du constructeur ; annulation d'écho et réduction de bruit si disponibles. */
@Singleton
class AndroidMicCapture @Inject constructor(@param:ApplicationContext private val ctx: Context) : AudioInput {
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    override fun start(onFrame: (ByteArray) -> Unit): Boolean {
        if (running) return true
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, FRAME_BYTES * 4))
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); return false }
        val effects = listOfNotNull<AudioEffect>(
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true } else null,
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true } else null,
        )
        running = true
        record.startRecording()
        Thread({
            val buf = ByteArray(FRAME_BYTES)
            while (running) {
                val n = record.read(buf, 0, buf.size)
                if (n > 0) { _level.value = Pcm.rms(buf, n); onFrame(buf.copyOf(n)) }
            }
            record.stop()
            effects.forEach { it.release() }
            record.release()
            _level.value = 0f
        }, "roadly-ai-mic").start()
        return true
    }

    override fun stop() { running = false }

    private companion object {
        const val RATE = 16_000
        /** 40 ms de PCM16 mono. */
        const val FRAME_BYTES = RATE * 2 * 40 / 1000
    }
}
```

`AndroidVoicePlayback.kt` :

```kotlin
package fr.roadly.core.assistant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** `AudioTrack` en flux, `USAGE_ASSISTANT` : distinct du canal de guidage, qui reste prioritaire. */
@Singleton
class AndroidVoicePlayback @Inject constructor() : AudioOutput {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level.asStateFlow()
    private val _playing = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = _playing.asStateFlow()
    @Volatile private var paused = false
    @Volatile private var running = false
    @Volatile private var track: AudioTrack? = null
    private var thread: Thread? = null

    override fun start() {
        if (running) return
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        paused = false
        running = true
        t.play()
        thread = Thread({
            while (running) {
                if (paused) { Thread.sleep(20); continue }
                val chunk = queue.poll(50, TimeUnit.MILLISECONDS)
                if (chunk == null) { _playing.value = false; _level.value = 0f; continue }
                _playing.value = true
                _level.value = Pcm.rms(chunk, chunk.size)
                t.write(chunk, 0, chunk.size)
            }
        }, "roadly-ai-voice").also { it.start() }
    }

    override fun enqueue(pcm: ByteArray) { queue.offer(pcm); _playing.value = true }
    override fun pause() { paused = true; track?.pause() }
    override fun resume() { paused = false; track?.play() }
    override fun flush() {
        queue.clear()
        track?.let { it.pause(); it.flush(); if (!paused) it.play() }
        _playing.value = false
        _level.value = 0f
    }
    override fun stop() {
        running = false
        queue.clear()
        thread?.join(300)
        thread = null
        track?.let { it.pause(); it.flush(); it.release() }
        track = null
        _playing.value = false
        _level.value = 0f
    }

    private companion object { const val RATE = 24_000 }
}
```

`AndroidAssistantAudioFocus.kt` :

```kotlin
package fr.roadly.core.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Focus transitoire pendant la session (la musique se met en pause). Une annonce GPS (`MAY_DUCK`) arrive en
 * `LOSS_TRANSIENT_CAN_DUCK` : ignorée ici, c'est `SpeechService.speaking` qui met l'assistant en pause.
 */
@Singleton
class AndroidAssistantAudioFocus @Inject constructor(@param:ApplicationContext ctx: Context) : AudioFocus {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    override fun acquire(onChange: (FocusChange) -> Unit): Boolean {
        release()
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> onChange(FocusChange.LOST)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onChange(FocusChange.PAUSE)
                    AudioManager.AUDIOFOCUS_GAIN -> onChange(FocusChange.RESUME)
                }
            }
            .build()
        request = r
        return audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun release() {
        request?.let { audio.abandonAudioFocusRequest(it) }
        request = null
    }
}
```

- [ ] **Step 5: Run tests + compile**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS (`PcmTest` + précédents, module compilé).

- [ ] **Step 6: Commit**

```bash
git add core/assistant
git commit -m "feat(assistant): audio — micro VOICE_COMMUNICATION, voix USAGE_ASSISTANT, focus transitoire"
```

---

### Task 12: `AssistantController` — cycle de vie et priorité GPS

**Files:**
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/AssistantController.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/SystemPrompt.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/AssistantMessages.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/testing/Fakes.kt`
- Test: `core/assistant/src/test/java/fr/roadly/core/assistant/AssistantControllerTest.kt`

**Interfaces:**
- Consumes: tout ce qui précède ; `SpeechService.speaking`, `NavigationClock`, `@ApplicationScope CoroutineScope` (`fr.roadly.core.data.di.ApplicationScope`).
- Produces (utilisé par `feature:assistant` et `app`) :
  - `enum class AssistantPhase { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, PAUSED_FOR_GUIDANCE }`
  - `data class AssistantUiState(phase, userText, assistantText, pending: String?, message: String?, error: AssistantError?)`
  - `@Singleton class AssistantController` : `val state: StateFlow<AssistantUiState>`, `val micLevel: StateFlow<Float>`, `val voiceLevel: StateFlow<Float>`, `fun toggle()`, `fun start()`, `fun stop()`, `fun onAppBackground()`, `fun confirmFromUi()`, `fun cancelFromUi()`, `fun addStopFromCard(placeId: String)` ; constantes `IDLE_TIMEOUT_MS = 15_000`, `RESUME_AFTER_GUIDANCE_MS = 300`, `RESUME_WINDOW_MS = 2 h`, `MAX_RECONNECTS = 1`, `VOICE = "Kore"`.
  - `object SystemPrompt { fun build(guiding: Boolean): String }` ; `object AssistantMessages { fun of(error: AssistantError): String }`.
  - Doubles (`testing/Fakes.kt`) : `FakeVoiceProvider(results: ArrayDeque<Connection>?)` avec `configs`, `sessions` ; `FakeVoiceSession` avec `emit(event)`, `audioFrames`, `streamEnds`, `texts`, `toolResults`, `closed` ; `FakeAudioInput` (`push(frame)`, `startResult`) ; `FakeAudioOutput` (`chunks`, `paused`, `flushes`, `playing`) ; `FakeAudioFocus` (`trigger(change)`).

- [ ] **Step 1: Write the doubles `testing/Fakes.kt` (main source set, comme `core:navigation/testing`)**

```kotlin
package fr.roadly.core.assistant.testing

import fr.roadly.core.assistant.audio.AudioFocus
import fr.roadly.core.assistant.audio.AudioInput
import fr.roadly.core.assistant.audio.AudioOutput
import fr.roadly.core.assistant.audio.FocusChange
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolResult
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import fr.roadly.core.assistant.provider.VoiceSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class FakeVoiceSession : VoiceSession {
    private val channel = Channel<AssistantEvent>(Channel.UNLIMITED)
    override val events: Flow<AssistantEvent> = channel.receiveAsFlow()
    val audioFrames = mutableListOf<ByteArray>()
    var streamEnds = 0
    val texts = mutableListOf<String>()
    val toolResults = mutableListOf<ToolResult>()
    var closed = false
    fun emit(event: AssistantEvent) { channel.trySend(event) }
    override fun sendAudio(pcm16k: ByteArray) { audioFrames += pcm16k }
    override fun endAudioStream() { streamEnds++ }
    override fun sendText(text: String) { texts += text }
    override fun sendToolResults(results: List<ToolResult>) { toolResults += results }
    override fun close() {
        if (closed) return
        closed = true
        channel.trySend(AssistantEvent.Closed)
        channel.close()
    }
}

/** Chaque [connect] consomme le prochain résultat de [results] ; à défaut, ouvre une nouvelle [FakeVoiceSession]. */
class FakeVoiceProvider(private val results: ArrayDeque<Connection> = ArrayDeque()) : VoiceAssistantProvider {
    override val id = "fake"
    val configs = mutableListOf<SessionConfig>()
    val sessions = mutableListOf<FakeVoiceSession>()
    override suspend fun connect(config: SessionConfig): Connection {
        configs += config
        return results.removeFirstOrNull() ?: Connection.Open(FakeVoiceSession().also { sessions += it })
    }
}

class FakeAudioInput : AudioInput {
    override val level: StateFlow<Float> = MutableStateFlow(0f)
    var startResult = true
    var running = false
    private var onFrame: ((ByteArray) -> Unit)? = null
    override fun start(onFrame: (ByteArray) -> Unit): Boolean { this.onFrame = onFrame; running = startResult; return startResult }
    override fun stop() { running = false; onFrame = null }
    fun push(frame: ByteArray) { onFrame?.invoke(frame) }
}

class FakeAudioOutput : AudioOutput {
    override val level: StateFlow<Float> = MutableStateFlow(0f)
    override val playing = MutableStateFlow(false)
    val chunks = mutableListOf<ByteArray>()
    var paused = false
    var flushes = 0
    var stopped = false
    override fun start() { stopped = false }
    override fun enqueue(pcm: ByteArray) { chunks += pcm; playing.value = true }
    override fun pause() { paused = true }
    override fun resume() { paused = false }
    override fun flush() { flushes++; playing.value = false }
    override fun stop() { stopped = true; playing.value = false }
    /** Simule la fin de lecture de la file. */
    fun drain() { playing.value = false }
}

class FakeAudioFocus : AudioFocus {
    var held = false
    private var onChange: ((FocusChange) -> Unit)? = null
    override fun acquire(onChange: (FocusChange) -> Unit): Boolean { this.onChange = onChange; held = true; return true }
    override fun release() { held = false; onChange = null }
    fun trigger(change: FocusChange) { onChange?.invoke(change) }
}
```

- [ ] **Step 2: Write the failing controller tests**

```kotlin
package fr.roadly.core.assistant

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.ToolCall
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantSettings
import fr.roadly.core.assistant.settings.SecretCipher
import fr.roadly.core.assistant.settings.UsageMeter
import fr.roadly.core.assistant.testing.FakeAssistantSettingsRepository
import fr.roadly.core.assistant.testing.FakeAudioFocus
import fr.roadly.core.assistant.testing.FakeAudioInput
import fr.roadly.core.assistant.testing.FakeAudioOutput
import fr.roadly.core.assistant.testing.FakeVoiceProvider
import fr.roadly.core.assistant.tools.AssistantCards
import fr.roadly.core.assistant.tools.EndConversationTool
import fr.roadly.core.assistant.tools.GuidingFixture
import fr.roadly.core.assistant.tools.PendingActions
import fr.roadly.core.assistant.tools.ToolRegistry
import fr.roadly.core.navigation.testing.FakeSpeechService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantControllerTest {
    private val identity = object : SecretCipher {
        override fun encrypt(plain: String) = plain
        override fun decrypt(cipherText: String) = cipherText
    }

    private inner class Rig(scope: TestScope, buildKey: String = "key", settings: AssistantSettings = AssistantSettings(consentGranted = true), results: ArrayDeque<Connection> = ArrayDeque()) {
        val f = GuidingFixture(scope)
        val provider = FakeVoiceProvider(results)
        val input = FakeAudioInput()
        val output = FakeAudioOutput()
        val focus = FakeAudioFocus()
        val speech = FakeSpeechService()
        val repo = FakeAssistantSettingsRepository(settings)
        val pending = PendingActions()
        val registry = ToolRegistry(setOf(EndConversationTool()), pending)
        val controller = AssistantController(
            provider, registry, pending, input, output, focus, speech, ApiKeyProvider(repo, identity, buildKey), repo, UsageMeter(repo),
            f.session, f.guidance, f.places, AssistantCards(), f.clock, f.zone, scope.backgroundScope,
        )
        val voice get() = provider.sessions.last()
    }

    @Test fun `tapping opens a session that streams the microphone`() = runTest {
        val r = Rig(this)
        r.controller.toggle(); runCurrent()
        assertEquals(AssistantPhase.LISTENING, r.controller.state.value.phase)
        assertEquals("key", r.provider.configs.single().apiKey)
        assertTrue(r.provider.configs.single().systemInstruction.contains("français"))
        r.input.push(byteArrayOf(1, 2)); 
        assertEquals(1, r.voice.audioFrames.size)
        assertTrue(r.focus.held)
        r.controller.toggle(); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
        assertTrue(r.voice.closed)
        assertFalse(r.focus.held)
    }

    @Test fun `a GPS announcement pauses the assistant voice and microphone, then resumes`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.AudioChunk(byteArrayOf(0, 0))); runCurrent()
        r.speech.setSpeaking(true); runCurrent()
        assertTrue(r.output.paused)
        assertEquals(AssistantPhase.PAUSED_FOR_GUIDANCE, r.controller.state.value.phase)
        assertEquals(1, r.voice.streamEnds)
        r.input.push(byteArrayOf(1))
        assertTrue("le modèle n'entend pas la voix GPS", r.voice.audioFrames.isEmpty())
        r.speech.setSpeaking(false); runCurrent()
        assertTrue("reprise après 300 ms seulement", r.output.paused)
        advanceTimeBy(301); runCurrent()
        assertFalse(r.output.paused)
        r.input.push(byteArrayOf(1))
        assertEquals(1, r.voice.audioFrames.size)
    }

    @Test fun `barge-in flushes the assistant voice`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.AudioChunk(byteArrayOf(0, 0)))
        r.voice.emit(AssistantEvent.Interrupted); runCurrent()
        assertEquals(1, r.output.flushes)
    }

    @Test fun `the session closes after 15 s without speech and records usage`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        advanceTimeBy(14_000); runCurrent()
        assertEquals(AssistantPhase.LISTENING, r.controller.state.value.phase)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
        assertTrue(r.voice.closed)
        assertTrue(r.repo.currentUsage.micSeconds >= 15)
    }

    @Test fun `speech keeps the session open`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        advanceTimeBy(10_000); r.voice.emit(AssistantEvent.UserTranscript("combien de temps")); runCurrent()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(AssistantPhase.LISTENING, r.controller.state.value.phase)
        assertEquals("combien de temps", r.controller.state.value.userText)
    }

    @Test fun `tool calls are executed and endConversation closes once the voice has played`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.ToolCalls(listOf(ToolCall("c1", EndConversationTool.NAME, JsonObject(emptyMap())))))
        r.voice.emit(AssistantEvent.AudioChunk(byteArrayOf(0, 0)))
        r.voice.emit(AssistantEvent.TurnComplete); runCurrent()
        assertEquals("c1", r.voice.toolResults.single().id)
        assertEquals(AssistantPhase.SPEAKING, r.controller.state.value.phase)
        r.output.drain(); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
    }

    @Test fun `no key, daily cap or refused connection are announced in the queue, never flushing guidance`() = runTest {
        val noKey = Rig(this, buildKey = "")
        noKey.controller.start(); runCurrent()
        assertEquals(AssistantError.NO_KEY, noKey.controller.state.value.error)
        assertTrue(noKey.provider.configs.isEmpty())

        val capped = Rig(this, settings = AssistantSettings(consentGranted = true, dailyCapMinutes = 0))
        capped.controller.start(); runCurrent()
        assertEquals(AssistantError.DAILY_CAP, capped.controller.state.value.error)

        val offline = Rig(this, results = ArrayDeque(listOf(Connection.Refused(AssistantError.NETWORK))))
        offline.controller.start(); runCurrent()
        assertEquals(AssistantPhase.IDLE, offline.controller.state.value.phase)
        assertTrue(offline.speech.spoken.single().contains("guidage continue"))
        assertEquals(listOf(false), offline.speech.flushes)
    }

    @Test fun `goAway reconnects with the latest resumption handle`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.ResumptionHandle("h1"))
        r.voice.emit(AssistantEvent.GoAway(5_000)); runCurrent()
        assertEquals(2, r.provider.configs.size)
        assertEquals("h1", r.provider.configs[1].resumptionHandle)
        assertEquals(AssistantPhase.LISTENING, r.controller.state.value.phase)
    }

    @Test fun `a lost network mid-session reconnects once, then gives up with a spoken message`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.voice.emit(AssistantEvent.Failed(AssistantError.NETWORK)); runCurrent()
        assertEquals(2, r.provider.sessions.size)
        r.voice.emit(AssistantEvent.Failed(AssistantError.NETWORK)); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
        assertTrue(r.speech.spoken.last().contains("guidage continue"))
    }

    @Test fun `phone call focus loss pauses, regain resumes`() = runTest {
        val r = Rig(this)
        r.controller.start(); runCurrent()
        r.focus.trigger(fr.roadly.core.assistant.audio.FocusChange.PAUSE)
        assertTrue(r.output.paused)
        r.focus.trigger(fr.roadly.core.assistant.audio.FocusChange.RESUME)
        assertFalse(r.output.paused)
        r.focus.trigger(fr.roadly.core.assistant.audio.FocusChange.LOST); runCurrent()
        assertEquals(AssistantPhase.IDLE, r.controller.state.value.phase)
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --tests "*AssistantControllerTest" --offline -q`
Expected: FAIL — `Unresolved reference: AssistantController`.

- [ ] **Step 4: Implement `SystemPrompt.kt` and `AssistantMessages.kt`**

```kotlin
package fr.roadly.core.assistant

/** Instruction système : règles seulement, aucune donnée personnelle (elles passent par les outils, à la demande). */
object SystemPrompt {
    fun build(guiding: Boolean): String = """
        Tu es Roadly AI, le copilote vocal de l'application GPS Roadly. Réponds toujours en français, en une ou deux phrases courtes et naturelles : l'utilisateur conduit.
        N'invente jamais une position, une durée, une distance, une heure, un prix, un trafic ou un événement : obtiens-les uniquement avec les outils. Si un outil répond "unavailable" ou "error", dis-le simplement, sans deviner.
        Roadly n'a pas de trafic en temps réel ni de tarifs de péage : ne prétends jamais le contraire.
        Quand un outil répond "needs_confirmation", lis le résumé et demande une confirmation claire. Appelle confirmAction avec action_id uniquement si l'utilisateur accepte ; sinon appelle cancelAction.
        Si un outil répond "consent_required", explique qu'il faut autoriser le partage dans Réglages, Roadly AI.
        Tu ne peux pas modifier les alertes, la voix du GPS ni les réglages de sécurité.
        Pour une station ou un lieu proposé, utilise son place_id dans addStop ou startNavigation.
        Quand l'utilisateur a terminé (par exemple « merci, c'est tout »), réponds brièvement puis appelle endConversation.
        Guidage en cours : ${if (guiding) "oui" else "non"}.
    """.trimIndent()
}

object AssistantMessages {
    fun of(error: fr.roadly.core.assistant.provider.AssistantError): String = when (error) {
        fr.roadly.core.assistant.provider.AssistantError.NO_KEY -> "Ajoutez votre clé Gemini dans les réglages de Roadly AI."
        fr.roadly.core.assistant.provider.AssistantError.INVALID_KEY -> "Clé API refusée, vérifiez-la dans les réglages."
        fr.roadly.core.assistant.provider.AssistantError.QUOTA -> "Quota Gemini atteint pour le moment."
        fr.roadly.core.assistant.provider.AssistantError.DAILY_CAP -> "Plafond quotidien de Roadly AI atteint."
        fr.roadly.core.assistant.provider.AssistantError.MIC_UNAVAILABLE -> "Micro indisponible."
        fr.roadly.core.assistant.provider.AssistantError.DISABLED -> "Roadly AI est désactivé dans les réglages."
        fr.roadly.core.assistant.provider.AssistantError.NETWORK,
        fr.roadly.core.assistant.provider.AssistantError.TIMEOUT,
        fr.roadly.core.assistant.provider.AssistantError.PROTOCOL -> "Roadly AI est indisponible, le guidage continue."
    }
}
```

(Écrire `AssistantMessages` dans son propre fichier `AssistantMessages.kt` avec `import fr.roadly.core.assistant.provider.AssistantError` et les noms courts.)

- [ ] **Step 5: Implement `AssistantController.kt`**

```kotlin
package fr.roadly.core.assistant

import fr.roadly.core.assistant.audio.AudioFocus
import fr.roadly.core.assistant.audio.AudioInput
import fr.roadly.core.assistant.audio.AudioOutput
import fr.roadly.core.assistant.audio.FocusChange
import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import fr.roadly.core.assistant.provider.VoiceSession
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.settings.UsageMeter
import fr.roadly.core.assistant.tools.AssistantCards
import fr.roadly.core.assistant.tools.EndConversationTool
import fr.roadly.core.assistant.tools.PendingActions
import fr.roadly.core.assistant.tools.ToolContext
import fr.roadly.core.assistant.tools.ToolPlaces
import fr.roadly.core.assistant.tools.ToolRegistry
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationClock
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.SpeechService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class AssistantPhase { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, PAUSED_FOR_GUIDANCE }

data class AssistantUiState(
    val phase: AssistantPhase = AssistantPhase.IDLE,
    val userText: String = "",
    val assistantText: String = "",
    /** Résumé de l'action en attente de confirmation. */
    val pending: String? = null,
    /** Message d'erreur ou d'information (déjà annoncé à voix haute). */
    val message: String? = null,
    val error: AssistantError? = null,
)

/**
 * Session Roadly AI : connexion, micro, voix, outils, auto-fermeture (15 s), reprise de contexte (2 h).
 * Le guidage est prioritaire : pendant une annonce GPS, voix et micro de l'assistant sont suspendus.
 */
@Singleton
class AssistantController @Inject constructor(
    private val provider: VoiceAssistantProvider,
    private val registry: ToolRegistry,
    private val pending: PendingActions,
    private val input: AudioInput,
    private val output: AudioOutput,
    private val focus: AudioFocus,
    private val speech: SpeechService,
    private val keys: ApiKeyProvider,
    private val settings: AssistantSettingsRepository,
    private val usage: UsageMeter,
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val places: ToolPlaces,
    private val cards: AssistantCards,
    private val clock: NavigationClock,
    private val zone: ZoneId,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = combine(_state, pending.current) { s, p -> s.copy(pending = p?.summary) }
        .stateIn(scope, SharingStarted.Eagerly, AssistantUiState())
    val micLevel: StateFlow<Float> get() = input.level
    val voiceLevel: StateFlow<Float> get() = output.level

    private var job: Job? = null
    @Volatile private var voice: VoiceSession? = null
    private var resumeHandle: String? = null
    private var resumeHandleAtMs = 0L
    private val heard = mutableListOf<Pair<Long, String>>()
    @Volatile private var lastActivityMs = 0L
    /** -1 = aucune session ouverte (le temps virtuel des tests commence à 0). */
    private var tokens = 0L
    @Volatile private var gpsPaused = false
    @Volatile private var focusPaused = false
    private var closeAfterTurn = false
    private var newUserTurn = true
    private var newAssistantTurn = true

    private sealed interface End {
        data object Closed : End
        data class Reconnect(val error: AssistantError?) : End
        data class Fatal(val error: AssistantError) : End
    }

    /** Bouton micro : ouvre une session, ou ferme celle en cours. */
    fun toggle() { if (job?.isActive == true) stop() else start() }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    fun stop() { job?.cancel() }

    fun onAppBackground() = stop()

    fun confirmFromUi() {
        scope.launch { pending.confirmFromUi()?.let { voice?.sendText("[Action confirmée à l'écran par l'utilisateur] $it") } }
    }

    fun cancelFromUi() {
        if (pending.cancelFromUi()) voice?.sendText("[Action annulée à l'écran par l'utilisateur]")
    }

    /** Tap sur une ligne de la carte de résultat : ajout direct de l'arrêt (geste explicite). */
    fun addStopFromCard(placeId: String) {
        val place = places[placeId] ?: return
        scope.launch {
            val note = when (guidance.addStop(place)) {
                is Outcome.Success -> "[Arrêt ajouté à l'écran : ${place.name}]"
                is Outcome.Failure -> "[Ajout de l'arrêt ${place.name} impossible]"
            }
            cards.clear()
            voice?.sendText(note)
        }
    }

    private suspend fun run() {
        val s = settings.settings.first()
        val key = keys.key()
        val refusal = when {
            !s.enabled -> AssistantError.DISABLED
            key == null -> AssistantError.NO_KEY
            !usage.canStart(clock.nowMs(), zone) -> AssistantError.DAILY_CAP
            else -> null
        }
        if (refusal != null || key == null) return fail(refusal ?: AssistantError.NO_KEY)
        resetConversation()
        _state.value = AssistantUiState(phase = AssistantPhase.CONNECTING)
        try {
            var current = connect(key) ?: return
            if (!input.start(::onMicFrame)) {
                current.close()
                return fail(AssistantError.MIC_UNAVAILABLE)
            }
            focus.acquire(::onFocusChange)
            output.start()
            coroutineScope {
                launch { followGuidanceSpeech() }
                launch { closeWhenIdle() }
                var reconnects = 0
                while (true) {
                    voice = current
                    if (!gpsPaused) setPhase(AssistantPhase.LISTENING)
                    val end = consume(current, this)
                    if (end !is End.Reconnect) {
                        if (end is End.Fatal) announce(end.error)
                        break
                    }
                    current.close()
                    if (reconnects++ >= MAX_RECONNECTS) { announce(end.error ?: AssistantError.NETWORK); break }
                    current = connect(key) ?: break
                }
                coroutineContext.cancelChildren()
            }
        } finally {
            withContext(NonCancellable) { teardown() }
        }
    }

    private suspend fun connect(key: String): VoiceSession? {
        val handle = resumeHandle?.takeIf { clock.nowMs() - resumeHandleAtMs < RESUME_WINDOW_MS }
        val config = SessionConfig(key, SystemPrompt.build(session.state.value is NavigationState.Guiding), registry.schemas(), VOICE, handle)
        return when (val c = provider.connect(config)) {
            is Connection.Open -> c.session
            is Connection.Refused -> { announce(c.error); null }
        }
    }

    private suspend fun consume(s: VoiceSession, sessionScope: CoroutineScope): End {
        var end: End = End.Closed
        s.events.takeWhile { e ->
            when (e) {
                is AssistantEvent.GoAway -> { end = End.Reconnect(null); false }
                is AssistantEvent.Failed -> {
                    end = if (e.error == AssistantError.NETWORK || e.error == AssistantError.TIMEOUT) End.Reconnect(e.error) else End.Fatal(e.error)
                    false
                }
                AssistantEvent.Closed -> false
                else -> { onEvent(s, e, sessionScope); true }
            }
        }.collect()
        return end
    }

    private suspend fun onEvent(s: VoiceSession, e: AssistantEvent, sessionScope: CoroutineScope) {
        val now = clock.nowMs()
        when (e) {
            is AssistantEvent.UserTranscript -> {
                lastActivityMs = now
                heard += now to e.text
                val base = if (newUserTurn) "" else _state.value.userText
                newUserTurn = false
                _state.update { it.copy(userText = (base + e.text).trim(), message = null, error = null) }
            }
            is AssistantEvent.AssistantTranscript -> {
                val base = if (newAssistantTurn) "" else _state.value.assistantText
                newAssistantTurn = false
                _state.update { it.copy(assistantText = (base + e.text).trim()) }
            }
            is AssistantEvent.AudioChunk -> {
                lastActivityMs = now
                output.enqueue(e.pcm24k)
                if (!gpsPaused) setPhase(AssistantPhase.SPEAKING)
            }
            is AssistantEvent.ToolCalls -> {
                lastActivityMs = now
                if (!gpsPaused) setPhase(AssistantPhase.THINKING)
                val ctx = toolContext()
                s.sendToolResults(e.calls.map { registry.execute(it, ctx) })
                if (e.calls.any { it.name == EndConversationTool.NAME }) closeAfterTurn = true
                lastActivityMs = clock.nowMs()
            }
            is AssistantEvent.ToolCallsCancelled -> pending.cancelCreatedBy(e.ids)
            AssistantEvent.Interrupted -> output.flush()
            AssistantEvent.TurnComplete -> {
                registry.resetTurn()
                newUserTurn = true
                newAssistantTurn = true
                sessionScope.launch {
                    output.playing.first { !it }
                    lastActivityMs = clock.nowMs()
                    if (closeAfterTurn) stop() else if (!gpsPaused) setPhase(AssistantPhase.LISTENING)
                }
            }
            is AssistantEvent.ResumptionHandle -> { resumeHandle = e.handle; resumeHandleAtMs = now }
            is AssistantEvent.Usage -> tokens = maxOf(tokens, e.totalTokens.toLong())
            else -> Unit
        }
    }

    private suspend fun toolContext(): ToolContext {
        val consent = settings.settings.first().consentGranted
        val snapshot = heard.toList()
        return ToolContext(consent, clock.nowMs(), zone) { since -> snapshot.filter { it.first >= since }.joinToString(" ") { it.second } }
    }

    private fun onMicFrame(frame: ByteArray) { if (!gpsPaused && !focusPaused) voice?.sendAudio(frame) }

    /** Priorité absolue au guidage : voix et micro suspendus pendant l'annonce, reprise 300 ms après. */
    private suspend fun followGuidanceSpeech() {
        speech.speaking.collectLatest { speaking ->
            if (speaking) {
                if (!gpsPaused) {
                    gpsPaused = true
                    output.pause()
                    voice?.endAudioStream()
                    setPhase(AssistantPhase.PAUSED_FOR_GUIDANCE)
                }
            } else if (gpsPaused) {
                delay(RESUME_AFTER_GUIDANCE_MS)
                gpsPaused = false
                if (!focusPaused) output.resume()
                lastActivityMs = clock.nowMs()
                setPhase(if (output.playing.value) AssistantPhase.SPEAKING else AssistantPhase.LISTENING)
            }
        }
    }

    private fun onFocusChange(change: FocusChange) {
        when (change) {
            FocusChange.LOST -> stop()
            FocusChange.PAUSE -> { focusPaused = true; output.pause(); voice?.endAudioStream() }
            FocusChange.RESUME -> if (focusPaused) {
                focusPaused = false
                if (!gpsPaused) output.resume()
                lastActivityMs = clock.nowMs()
            }
        }
    }

    private suspend fun closeWhenIdle() {
        while (true) {
            delay(1_000)
            val idle = clock.nowMs() - lastActivityMs
            if (_state.value.phase == AssistantPhase.LISTENING && !output.playing.value && idle >= IDLE_TIMEOUT_MS) { stop(); return }
        }
    }

    private fun setPhase(phase: AssistantPhase) = _state.update { it.copy(phase = phase) }

    private fun announce(error: AssistantError) {
        val text = AssistantMessages.of(error)
        // En file, jamais `flush` : une instruction de guidage en cours n'est pas coupée.
        speech.speak(text, flush = false)
        _state.update { it.copy(message = text, error = error) }
    }

    private fun fail(error: AssistantError) {
        announce(error)
        setPhase(AssistantPhase.IDLE)
    }

    private fun resetConversation() {
        heard.clear()
        tokens = 0
        closeAfterTurn = false
        newUserTurn = true
        newAssistantTurn = true
        startedAtMs = clock.nowMs()
        lastActivityMs = startedAtMs
        cards.clear()
    }

    private suspend fun teardown() {
        input.stop()
        output.stop()
        focus.release()
        voice?.close()
        voice = null
        pending.clear()
        gpsPaused = false
        focusPaused = false
        if (startedAtMs >= 0) usage.record(clock.nowMs(), zone, tokens, (clock.nowMs() - startedAtMs) / 1000)
        startedAtMs = -1L
        setPhase(AssistantPhase.IDLE)
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 15_000L
        const val RESUME_AFTER_GUIDANCE_MS = 300L
        const val RESUME_WINDOW_MS = 2 * 60 * 60 * 1000L
        const val MAX_RECONNECTS = 1
        const val VOICE = "Kore"
    }
}
```

Notes pour l'implémenteur :
- `NavigationSession.state` doit exposer `NavigationState` ; `FakeSpeechService.setSpeaking` vient de la tâche 1.
- Si `@param:ApplicationScope` ne compile pas, utiliser `@ApplicationScope` (même forme que les autres injections du projet, voir `NavigationSession`).
- Le test « tapping opens… » appelle `push` sans `runCurrent` : `onMicFrame` est synchrone.

- [ ] **Step 6: Run tests**

Run: `.\gradlew.bat :core:assistant:testDebugUnitTest --offline -q`
Expected: PASS (10 tests du contrôleur + précédents). En cas d'échec sur le temps virtuel, vérifier que toute attente passe par `delay` et `clock` (jamais `System.currentTimeMillis()`).

- [ ] **Step 7: Commit**

```bash
git add core/assistant
git commit -m "feat(assistant): AssistantController — session, priorité GPS, interruption, auto-fermeture, reconnexion"
```

---

### Task 13: Liaisons Hilt de l'assistant

**Files:**
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/AssistantModule.kt`
- Create: `core/assistant/src/main/java/fr/roadly/core/assistant/tools/AssistantToolsModule.kt`
- Modify: `app/build.gradle.kts` (`implementation(project(":core:assistant"))`)

**Interfaces:**
- Consumes: toutes les classes des tâches 4 à 12.
- Produces: graphe Hilt complet — `AssistantController` injectable partout (`SingletonComponent`).

- [ ] **Step 1: Write `AssistantModule.kt`**

```kotlin
package fr.roadly.core.assistant

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.roadly.core.assistant.audio.AndroidAssistantAudioFocus
import fr.roadly.core.assistant.audio.AndroidMicCapture
import fr.roadly.core.assistant.audio.AndroidVoicePlayback
import fr.roadly.core.assistant.audio.AudioFocus
import fr.roadly.core.assistant.audio.AudioInput
import fr.roadly.core.assistant.audio.AudioOutput
import fr.roadly.core.assistant.gemini.GeminiLiveProvider
import fr.roadly.core.assistant.provider.VoiceAssistantProvider
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.settings.DataStoreAssistantSettingsRepository
import fr.roadly.core.assistant.settings.KeystoreSecretCipher
import fr.roadly.core.assistant.settings.SecretCipher
import java.time.ZoneId
import javax.inject.Named

/** Fournisseur IA (Gemini Live aujourd'hui ; OpenAI = une autre liaison de [VoiceAssistantProvider]), audio, réglages. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantModule {
    @Binds abstract fun provider(impl: GeminiLiveProvider): VoiceAssistantProvider
    @Binds abstract fun input(impl: AndroidMicCapture): AudioInput
    @Binds abstract fun output(impl: AndroidVoicePlayback): AudioOutput
    @Binds abstract fun focus(impl: AndroidAssistantAudioFocus): AudioFocus
    @Binds abstract fun cipher(impl: KeystoreSecretCipher): SecretCipher
    @Binds abstract fun settings(impl: DataStoreAssistantSettingsRepository): AssistantSettingsRepository

    companion object {
        @Provides @Named(ApiKeyProvider.BUILD_KEY) fun buildKey(): String = BuildConfig.GEMINI_API_KEY
        @Provides fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
```

Si Hilt signale un `ZoneId` déjà lié ailleurs, remplacer par `@Provides @Named("assistantZone")` et qualifier le paramètre `zone` d'`AssistantController`.

- [ ] **Step 2: Write `AssistantToolsModule.kt`**

```kotlin
package fr.roadly.core.assistant.tools

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.geocoding.NearbyPoiProvider
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavigationSession

/** Outils exposés au modèle ; ajouter un outil = une liaison ici. Aucun outil ne touche aux alertes, à la voix GPS ni aux réglages. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantToolsModule {
    @Binds @IntoSet abstract fun status(t: GetNavigationStatusTool): AssistantTool
    @Binds @IntoSet abstract fun nextInstruction(t: GetNextInstructionTool): AssistantTool
    @Binds @IntoSet abstract fun distance(t: GetDistanceRemainingTool): AssistantTool
    @Binds @IntoSet abstract fun arrival(t: GetArrivalTimeTool): AssistantTool
    @Binds @IntoSet abstract fun stop(t: StopNavigationTool): AssistantTool
    @Binds @IntoSet abstract fun recalculate(t: RecalculateRouteTool): AssistantTool
    @Binds @IntoSet abstract fun end(t: EndConversationTool): AssistantTool
    @Binds @IntoSet abstract fun alternatives(t: FindAlternativeRoutesTool): AssistantTool
    @Binds @IntoSet abstract fun selectRoute(t: SelectRouteTool): AssistantTool
    @Binds @IntoSet abstract fun arrivalTime(t: SetArrivalTimeTool): AssistantTool
    @Binds @IntoSet abstract fun addStop(t: AddStopTool): AssistantTool
    @Binds @IntoSet abstract fun removeStop(t: RemoveStopTool): AssistantTool
    @Binds @IntoSet abstract fun stops(t: GetStopsTool): AssistantTool
    @Binds @IntoSet abstract fun fuelPrices(t: GetFuelPricesTool): AssistantTool
    @Binds @IntoSet abstract fun cheapestFuel(t: FindCheapestFuelOnRouteTool): AssistantTool
    @Binds @IntoSet abstract fun tripCost(t: GetEstimatedTripCostTool): AssistantTool
    @Binds @IntoSet abstract fun traffic(t: ShowTrafficTool): AssistantTool
    @Binds @IntoSet abstract fun report(t: ReportIncidentTool): AssistantTool
    @Binds @IntoSet abstract fun confirmIncident(t: ConfirmIncidentTool): AssistantTool
    @Binds @IntoSet abstract fun vehicle(t: GetVehicleProfileTool): AssistantTool
    @Binds @IntoSet abstract fun changeVehicle(t: ChangeVehicleProfileTool): AssistantTool

    companion object {
        @Provides @IntoSet
        fun startNavigation(r: DestinationResolver, g: GuidanceActions, n: AppNavigator): AssistantTool =
            DestinationTool("startNavigation", "Démarre le guidage vers une destination, après confirmation.", "Démarrer le guidage vers", r, g, n)

        @Provides @IntoSet
        fun changeDestination(r: DestinationResolver, g: GuidanceActions, n: AppNavigator): AssistantTool =
            DestinationTool("changeDestination", "Remplace la destination du guidage en cours, après confirmation.", "Changer la destination pour", r, g, n)

        @Provides @IntoSet
        fun avoidTolls(p: UserPreferencesRepository, g: GuidanceActions): AssistantTool =
            AvoidOptionTool("avoidTolls", "Évite (ou autorise de nouveau) les péages ; en guidage, recalcule et compare les durées.", { prefs, on -> prefs.copy(avoidTolls = on) }, p, g)

        @Provides @IntoSet
        fun avoidHighways(p: UserPreferencesRepository, g: GuidanceActions): AssistantTool =
            AvoidOptionTool("avoidHighways", "Évite (ou autorise de nouveau) les autoroutes ; en guidage, recalcule et compare les durées.", { prefs, on -> prefs.copy(avoidHighways = on) }, p, g)

        @Provides @IntoSet
        fun gasStations(s: NavigationSession, n: NearbyPoiProvider, l: LocationRepository, nav: AppNavigator, c: AssistantCards, pl: ToolPlaces): AssistantTool =
            ShowNearbyTool("showGasStations", "Stations-service proches (OpenStreetMap). Pour les prix : getFuelPrices ou findCheapestFuelOnRoute.", listOf(PoiCategory.FUEL), "Stations proches", s, n, l, nav, c, pl)

        @Provides @IntoSet
        fun parking(s: NavigationSession, n: NearbyPoiProvider, l: LocationRepository, nav: AppNavigator, c: AssistantCards, pl: ToolPlaces): AssistantTool =
            ShowNearbyTool("showParking", "Parkings proches (OpenStreetMap ; tarifs et places libres inconnus).", listOf(PoiCategory.PARKING), "Parkings proches", s, n, l, nav, c, pl)

        @Provides @IntoSet
        fun charging(s: NavigationSession, n: NearbyPoiProvider, l: LocationRepository, nav: AppNavigator, c: AssistantCards, pl: ToolPlaces): AssistantTool =
            ShowNearbyTool("showChargingStations", "Bornes de recharge proches (OpenStreetMap ; disponibilité inconnue).", listOf(PoiCategory.CHARGING), "Bornes proches", s, n, l, nav, c, pl)

        @Provides @IntoSet
        fun services(s: NavigationSession, n: NearbyPoiProvider, l: LocationRepository, nav: AppNavigator, c: AssistantCards, pl: ToolPlaces): AssistantTool =
            ShowNearbyTool("showNearbyServices", "Aires de service et restaurants proches (OpenStreetMap).", listOf(PoiCategory.REST_AREA, PoiCategory.RESTAURANT), "Services proches", s, n, l, nav, c, pl)
    }
}
```

- [ ] **Step 2b: Guard against a missing tool**

Ajouter à `ToolRegistryTest` un test qui liste les noms exigés par la spec, pour qu'un outil oublié dans le module se voie (le module Hilt lui-même n'est pas testable en JVM, ce test documente la liste) :

```kotlin
    @Test fun `the spec tool list is stable`() {
        val expected = setOf(
            "startNavigation", "stopNavigation", "changeDestination", "recalculateRoute", "getNavigationStatus", "getNextInstruction",
            "avoidTolls", "avoidHighways", "findAlternativeRoutes", "selectRoute", "setArrivalTime", "addStop", "removeStop", "getStops",
            "showTraffic", "showGasStations", "showParking", "showChargingStations", "showNearbyServices", "findCheapestFuelOnRoute",
            "reportIncident", "confirmIncident", "changeVehicleProfile", "getVehicleProfile",
            "getFuelPrices", "getEstimatedTripCost", "getDistanceRemaining", "getArrivalTime", "endConversation",
        )
        assertEquals(29, expected.size)
    }
```

Puis, sur l'appareil (tâche 16), vérifier dans logcat que le `setup` envoyé déclare bien 31 fonctions (29 + `confirmAction` + `cancelAction`) : ajouter temporairement `Log.d("RoadlyAI", "tools=${config.tools.size}")` dans `GeminiLiveProvider.connect`, vérifier, puis retirer le log.

- [ ] **Step 3: Build**

`app/build.gradle.kts` : ajouter `implementation(project(":core:assistant"))` après `implementation(project(":core:navigation"))`.

Run: `.\gradlew.bat :app:assembleDebug --offline -q` puis `.\gradlew.bat testDebugUnitTest --offline -q`
Expected: build OK (graphe Hilt valide), tests PASS.

- [ ] **Step 4: Commit**

```bash
git add core/assistant app/build.gradle.kts
git commit -m "feat(assistant): liaisons Hilt — fournisseur, audio, réglages, 29 outils"
```

---

### Task 14: Réglages « Roadly AI »

**Files:**
- Create: `feature/settings/src/main/java/fr/roadly/feature/settings/AssistantSettingsViewModel.kt`
- Create: `feature/settings/src/main/java/fr/roadly/feature/settings/AssistantGroup.kt`
- Modify: `feature/settings/src/main/java/fr/roadly/feature/settings/SettingsScreen.kt` (`SettingsField`, `ExpandArrow`, `ExpandableContent` passent en `internal` ; `SettingsField` gagne `masked`; appel `AssistantGroup()`)
- Modify: `feature/settings/build.gradle.kts` (`implementation(project(":core:assistant"))`), `feature/settings/src/main/res/values/strings.xml`
- Test: `feature/settings/src/test/java/fr/roadly/feature/settings/AssistantSettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `AssistantSettingsRepository`, `ApiKeyProvider`, `KeySource`, `AssistantKeyVerifier`, `UsageMeter.day`.
- Produces: `AssistantSettingsViewModel` (`settings`, `todayUsage: StateFlow<AssistantUsage>`, `source`, `verify: StateFlow<KeyCheck>`, `setEnabled`, `setConsent`, `setCap`, `saveKey`, `clearKey`, `verifyNow`) ; `sealed interface KeyCheck { Idle, Running, Ok, Failed(error) }`.

- [ ] **Step 1: Write the failing test**

```kotlin
package fr.roadly.feature.settings

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantKeyVerifier
import fr.roadly.core.assistant.settings.KeySource
import fr.roadly.core.assistant.settings.SecretCipher
import fr.roadly.core.assistant.testing.FakeAssistantSettingsRepository
import fr.roadly.core.assistant.testing.FakeVoiceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantSettingsViewModelTest {
    private val main = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(main)
    @After fun tearDown() = Dispatchers.resetMain()
    private val identity = object : SecretCipher { override fun encrypt(plain: String) = plain; override fun decrypt(cipherText: String) = cipherText }

    @Test fun `saving a key switches the source to override and verifies it`() = runTest(main) {
        val repo = FakeAssistantSettingsRepository()
        val provider = FakeVoiceProvider(ArrayDeque(listOf(Connection.Refused(AssistantError.INVALID_KEY))))
        val vm = AssistantSettingsViewModel(repo, ApiKeyProvider(repo, identity, ""), AssistantKeyVerifier(provider))
        runCurrent()
        assertEquals(KeySource.NONE, vm.source.value)
        vm.saveKey("abc"); runCurrent()
        assertEquals(KeySource.OVERRIDE, vm.source.value)
        assertEquals("abc", provider.configs.single().apiKey)
        assertEquals(KeyCheck.Failed(AssistantError.INVALID_KEY), vm.verify.value)
        vm.clearKey(); runCurrent()
        assertEquals(KeySource.NONE, vm.source.value)
    }
}
```

Ajouter `testImplementation(libs.coroutines.test)` s'il manque dans `feature/settings/build.gradle.kts` (il y est déjà).

- [ ] **Step 2: Run to verify failure**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest --tests "*AssistantSettingsViewModelTest" --offline -q`
Expected: FAIL — unresolved references.

- [ ] **Step 3: Implement the ViewModel**

```kotlin
package fr.roadly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.settings.ApiKeyProvider
import fr.roadly.core.assistant.settings.AssistantKeyVerifier
import fr.roadly.core.assistant.settings.AssistantSettings
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.settings.AssistantUsage
import fr.roadly.core.assistant.settings.KeySource
import fr.roadly.core.assistant.settings.UsageMeter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Running : KeyCheck
    data object Ok : KeyCheck
    data class Failed(val error: AssistantError) : KeyCheck
}

/** Réglages → Roadly AI : activation, consentement, clé (jamais affichée), plafond, usage du jour. */
@HiltViewModel
class AssistantSettingsViewModel @Inject constructor(
    private val repo: AssistantSettingsRepository,
    private val keys: ApiKeyProvider,
    private val verifier: AssistantKeyVerifier,
) : ViewModel() {
    val settings: StateFlow<AssistantSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantSettings())
    val todayUsage: StateFlow<AssistantUsage> = repo.usage.map { u ->
        if (u.day == UsageMeter.day(System.currentTimeMillis(), ZoneId.systemDefault())) u else AssistantUsage()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUsage())
    private val _source = MutableStateFlow(KeySource.NONE)
    val source: StateFlow<KeySource> = _source.asStateFlow()
    private val _verify = MutableStateFlow<KeyCheck>(KeyCheck.Idle)
    val verify: StateFlow<KeyCheck> = _verify.asStateFlow()

    init { viewModelScope.launch { _source.value = keys.source() } }

    fun setEnabled(value: Boolean) { viewModelScope.launch { repo.setEnabled(value) } }
    fun setConsent(value: Boolean) { viewModelScope.launch { repo.setConsent(value) } }
    fun setCap(minutes: Int) { viewModelScope.launch { repo.setDailyCap(minutes) } }

    fun saveKey(key: String) {
        viewModelScope.launch {
            keys.save(key)
            _source.value = keys.source()
            check()
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            keys.clearOverride()
            _source.value = keys.source()
            _verify.value = KeyCheck.Idle
        }
    }

    fun verifyNow() { viewModelScope.launch { check() } }

    private suspend fun check() {
        _verify.value = KeyCheck.Running
        val key = keys.key()
        _verify.value = if (key == null) KeyCheck.Failed(AssistantError.NO_KEY) else verifier.verify(key)?.let { KeyCheck.Failed(it) } ?: KeyCheck.Ok
    }
}
```

- [ ] **Step 4: Implement the group, strings and wiring**

`SettingsScreen.kt` : rendre `settingsFieldColors`, `SettingsField`, `ExpandArrow` et `ExpandableContent` `internal` ; changer `SettingsField` :

```kotlin
@Composable
internal fun SettingsField(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType, masked: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true, colors = settingsFieldColors(), shape = RoadlyShapes.Tile, modifier = Modifier.fillMaxWidth(),
    )
}
```

(imports `androidx.compose.ui.text.input.PasswordVisualTransformation`, `androidx.compose.ui.text.input.VisualTransformation`), et dans la colonne de `SettingsScreen`, juste après `VoiceGroup(...)` : `AssistantGroup()`.

`AssistantGroup.kt` :

```kotlin
package fr.roadly.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.assistant.settings.KeySource
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassChip
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AssistantGroup(vm: AssistantSettingsViewModel = hiltViewModel()) {
    val c = LocalRoadlyColors.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val usage by vm.todayUsage.collectAsStateWithLifecycle()
    val source by vm.source.collectAsStateWithLifecycle()
    val verify by vm.verify.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    SettingsGroup(stringResource(R.string.settings_group_assistant), footer = stringResource(R.string.settings_assistant_footer)) {
        ToggleRow(stringResource(R.string.settings_assistant_enabled), s.enabled, vm::setEnabled, icon = Icons.Default.Mic)
        ToggleRow(
            stringResource(R.string.settings_assistant_consent), s.consentGranted, vm::setConsent,
            subtitle = stringResource(R.string.settings_assistant_consent_subtitle), icon = Icons.Default.Shield,
        )
        ListRow(
            stringResource(R.string.settings_assistant_key),
            subtitle = stringResource(when (source) {
                KeySource.OVERRIDE -> R.string.settings_assistant_key_override
                KeySource.BUILD -> R.string.settings_assistant_key_build
                KeySource.NONE -> R.string.settings_assistant_key_none
            }),
            icon = Icons.Default.Key, onClick = { editing = !editing }, chevron = false, divider = false,
            trailing = { ExpandArrow(editing) },
        )
        ExpandableContent(editing) {
            SettingsField(stringResource(R.string.settings_assistant_key_field), key, { key = it }, KeyboardType.Password, masked = true)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s), verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                GlassButton(stringResource(R.string.settings_save), onClick = { vm.saveKey(key); key = "" }, enabled = key.isNotBlank())
                GlassButton(stringResource(R.string.settings_assistant_verify), onClick = vm::verifyNow, enabled = verify != KeyCheck.Running)
                if (source == KeySource.OVERRIDE) GlassButton(stringResource(R.string.settings_assistant_key_clear), onClick = vm::clearKey, style = GlassButtonStyle.Danger)
            }
            val verifyText = when (val v = verify) {
                KeyCheck.Idle -> null
                KeyCheck.Running -> stringResource(R.string.settings_assistant_verify_running)
                KeyCheck.Ok -> stringResource(R.string.settings_assistant_verify_ok)
                is KeyCheck.Failed -> stringResource(R.string.settings_assistant_verify_failed, v.error.name)
            }
            verifyText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (verify == KeyCheck.Ok) c.success else c.textSecondary) }
        }
        Text(stringResource(R.string.settings_assistant_cap), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(top = RoadlySpacing.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            listOf(15, 30, 60).forEach { m -> GlassChip(stringResource(R.string.settings_assistant_cap_minutes, m), onClick = { vm.setCap(m) }, selected = s.dailyCapMinutes == m) }
        }
        Text(
            stringResource(R.string.settings_assistant_usage, usage.micSeconds / 60, s.dailyCapMinutes, usage.tokens),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(vertical = RoadlySpacing.s),
        )
    }
}
```

`strings.xml` (feature/settings) — ajouter :

```xml
    <string name="settings_group_assistant">Roadly AI</string>
    <string name="settings_assistant_footer">Copilote vocal Gemini Live. Le guidage reste prioritaire et fonctionne sans l\'assistant, même hors ligne. La clé n\'est jamais affichée.</string>
    <string name="settings_assistant_enabled">Activer Roadly AI</string>
    <string name="settings_assistant_consent">Partager ma position et mon trajet avec Gemini</string>
    <string name="settings_assistant_consent_subtitle">Seulement quand l\'assistant en a besoin pour répondre (position, destination, itinéraire, véhicule).</string>
    <string name="settings_assistant_key">Clé API Gemini</string>
    <string name="settings_assistant_key_override">Clé saisie ici (chiffrée sur le téléphone)</string>
    <string name="settings_assistant_key_build">Clé de local.properties</string>
    <string name="settings_assistant_key_none">Aucune clé</string>
    <string name="settings_assistant_key_field">Nouvelle clé</string>
    <string name="settings_assistant_key_clear">Oublier la clé saisie</string>
    <string name="settings_assistant_verify">Vérifier la clé</string>
    <string name="settings_assistant_verify_running">Vérification…</string>
    <string name="settings_assistant_verify_ok">Clé acceptée par Gemini.</string>
    <string name="settings_assistant_verify_failed">Échec : %1$s</string>
    <string name="settings_assistant_cap">Plafond quotidien d\'écoute</string>
    <string name="settings_assistant_cap_minutes">%1$d min</string>
    <string name="settings_assistant_usage">Aujourd\'hui : %1$d / %2$d min d\'écoute · ≈ %3$d tokens</string>
```

`feature/settings/build.gradle.kts` : `implementation(project(":core:assistant"))`.

- [ ] **Step 5: Run tests + build**

Run: `.\gradlew.bat :feature:settings:testDebugUnitTest --offline -q` puis `.\gradlew.bat :app:assembleDebug --offline -q`
Expected: PASS, build OK.

- [ ] **Step 6: Commit**

```bash
git add feature/settings
git commit -m "feat(settings): section Roadly AI — activation, consentement, clé chiffrée vérifiable, plafond, usage"
```

---

### Task 15: `feature:assistant` — bouton micro, fenêtre compacte, branchement

**Files:**
- Modify: `settings.gradle.kts` (`":feature:assistant"`)
- Create: `feature/assistant/build.gradle.kts`, `feature/assistant/src/main/AndroidManifest.xml`, `feature/assistant/src/main/res/values/strings.xml`
- Create: `feature/assistant/src/main/java/fr/roadly/feature/assistant/AssistantViewModel.kt`
- Create: `feature/assistant/src/main/java/fr/roadly/feature/assistant/AssistantMicButton.kt`
- Create: `feature/assistant/src/main/java/fr/roadly/feature/assistant/AssistantOverlay.kt`
- Modify: `feature/home/.../HomeScreen.kt`, `HomeNavigation.kt` ; `feature/navigation/.../NavigationScreen.kt`, `NavigationNavigation.kt` (slots)
- Modify: `app/build.gradle.kts`, `app/src/main/java/fr/roadly/app/RoadlyNavHost.kt`, `app/src/main/java/fr/roadly/app/RoadlyApp.kt`

**Interfaces:**
- Consumes: `AssistantController` (`state`, `micLevel`, `voiceLevel`, `toggle`, `start`, `confirmFromUi`, `cancelFromUi`, `addStopFromCard`, `onAppBackground`), `AssistantCards.card`, `AssistantSettingsRepository.setConsent`.
- Produces: `@Composable fun AssistantMicButton(modifier: Modifier = Modifier)`, `@Composable fun AssistantOverlay(modifier: Modifier = Modifier)` ; paramètres `assistantButton: @Composable () -> Unit = {}` et `assistantPanel: @Composable () -> Unit = {}` sur `HomeScreen`, `homeScreen`, `NavigationScreen`, `navigationScreen`.

- [ ] **Step 1: Module scaffolding**

`settings.gradle.kts` : ajouter `":feature:assistant",`.

`feature/assistant/build.gradle.kts` :

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "fr.roadly.feature.assistant"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:assistant"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
```

`AndroidManifest.xml` : `<manifest />` (vide, comme les autres modules feature).

`res/values/strings.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="assistant_mic">Roadly AI</string>
    <string name="assistant_mic_stop">Fermer Roadly AI</string>
    <string name="assistant_phase_connecting">Connexion…</string>
    <string name="assistant_phase_listening">Je vous écoute</string>
    <string name="assistant_phase_thinking">Je regarde…</string>
    <string name="assistant_phase_speaking">Roadly AI</string>
    <string name="assistant_phase_paused">Pause : instruction GPS</string>
    <string name="assistant_confirm">Confirmer</string>
    <string name="assistant_cancel">Annuler</string>
    <string name="assistant_consent_title">Roadly AI et vos données</string>
    <string name="assistant_consent_text">Pour répondre, Roadly AI envoie à Google Gemini votre voix et, seulement quand c\'est nécessaire, votre position, votre destination, votre itinéraire et votre profil véhicule. Modifiable dans Réglages → Roadly AI.</string>
    <string name="assistant_consent_accept">Accepter</string>
    <string name="assistant_consent_refuse">Plus tard</string>
</resources>
```

- [ ] **Step 2: ViewModel**

```kotlin
package fr.roadly.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.assistant.AssistantController
import fr.roadly.core.assistant.settings.AssistantSettingsRepository
import fr.roadly.core.assistant.tools.AssistantCards
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val controller: AssistantController,
    private val settings: AssistantSettingsRepository,
    cards: AssistantCards,
) : ViewModel() {
    val state = controller.state
    val micLevel = controller.micLevel
    val voiceLevel = controller.voiceLevel
    val card = cards.card
    /** null tant que les réglages ne sont pas lus (pas de dialogue de consentement prématuré). */
    val consentGranted = settings.settings.map<_, Boolean?> { it.consentGranted }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onMic() = controller.toggle()
    fun grantConsentAndStart() { viewModelScope.launch { settings.setConsent(true); controller.start() } }
    fun confirm() = controller.confirmFromUi()
    fun cancel() = controller.cancelFromUi()
    fun onCardRow(placeId: String) = controller.addStopFromCard(placeId)
}
```

Si `map<_, Boolean?>` ne compile pas, écrire `settings.settings.map { s -> s.consentGranted as Boolean? }`.

- [ ] **Step 3: Mic button with halo, permission and consent**

```kotlin
package fr.roadly.feature.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.assistant.AssistantPhase
import fr.roadly.core.designsystem.component.RoundButtonStyle
import fr.roadly.core.designsystem.component.RoundIconButton
import fr.roadly.core.designsystem.theme.LocalRoadlyColors

/** Bouton micro de la colonne d'actions : ouvre / ferme Roadly AI ; halo animé selon l'état et le niveau sonore. */
@Composable
fun AssistantMicButton(modifier: Modifier = Modifier, vm: AssistantViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val mic by vm.micLevel.collectAsStateWithLifecycle()
    val voice by vm.voiceLevel.collectAsStateWithLifecycle()
    val consent by vm.consentGranted.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var askConsent by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (consent == true) vm.onMic() else askConsent = true }
    }
    val active = state.phase != AssistantPhase.IDLE
    Box(modifier, contentAlignment = Alignment.Center) {
        AssistantHalo(state.phase, mic, voice)
        RoundIconButton(
            if (state.phase == AssistantPhase.SPEAKING) Icons.Default.GraphicEq else Icons.Default.Mic,
            stringResource(if (active) R.string.assistant_mic_stop else R.string.assistant_mic),
            onClick = {
                when {
                    active -> vm.onMic()
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> permission.launch(Manifest.permission.RECORD_AUDIO)
                    consent != true -> askConsent = true
                    else -> vm.onMic()
                }
            },
            style = if (active) RoundButtonStyle.Brand else RoundButtonStyle.Glass,
        )
    }
    if (askConsent) {
        AlertDialog(
            onDismissRequest = { askConsent = false },
            title = { Text(stringResource(R.string.assistant_consent_title)) },
            text = { Text(stringResource(R.string.assistant_consent_text)) },
            confirmButton = { TextButton(onClick = { askConsent = false; vm.grantConsentAndStart() }) { Text(stringResource(R.string.assistant_consent_accept)) } },
            dismissButton = { TextButton(onClick = { askConsent = false }) { Text(stringResource(R.string.assistant_consent_refuse)) } },
        )
    }
}

@Composable
private fun AssistantHalo(phase: AssistantPhase, mic: Float, voice: Float) {
    val c = LocalRoadlyColors.current
    val target = when (phase) {
        AssistantPhase.LISTENING -> mic
        AssistantPhase.SPEAKING -> voice
        else -> 0f
    }
    // La parole tourne autour de 0,05-0,2 en RMS : amplifiée pour être visible.
    val level by animateFloatAsState((target * 4f).coerceIn(0f, 1f), label = "assistantLevel")
    val angle by rememberInfiniteTransition(label = "assistantSpin").animateFloat(0f, 360f, infiniteRepeatable(tween(1_200, easing = LinearEasing)), label = "assistantAngle")
    Canvas(Modifier.size(84.dp)) {
        val r = size.minDimension / 2
        when (phase) {
            AssistantPhase.IDLE -> Unit
            AssistantPhase.CONNECTING, AssistantPhase.THINKING ->
                drawArc(c.brand, angle, 110f, useCenter = false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            AssistantPhase.PAUSED_FOR_GUIDANCE -> drawCircle(c.textTertiary.copy(alpha = 0.35f), radius = r * 0.8f)
            AssistantPhase.LISTENING, AssistantPhase.SPEAKING -> drawCircle(c.brand.copy(alpha = 0.18f + 0.3f * level), radius = r * (0.72f + 0.28f * level))
        }
    }
}
```

- [ ] **Step 4: Compact overlay (2 lines max)**

```kotlin
package fr.roadly.feature.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.roadly.core.assistant.AssistantPhase
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import kotlinx.coroutines.delay

/** Fenêtre compacte : état, 1 ligne utilisateur + 1 ligne IA, Confirmer / Annuler, carte de résultat (≤ 3 lignes). */
@Composable
fun AssistantOverlay(modifier: Modifier = Modifier, vm: AssistantViewModel = hiltViewModel()) {
    val c = LocalRoadlyColors.current
    val state by vm.state.collectAsStateWithLifecycle()
    val card by vm.card.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.phase, state.message) {
        if (state.phase != AssistantPhase.IDLE) visible = true
        else if (visible || state.message != null) { visible = true; delay(4_000); visible = false }
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.xs)) {
                Text(stringResource(phaseLabel(state.phase)), style = MaterialTheme.typography.labelMedium, color = c.brand)
                if (state.userText.isNotBlank()) {
                    Text(state.userText, maxLines = 1, overflow = TextOverflow.StartEllipsis, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                val reply = state.message ?: state.assistantText
                if (reply.isNotBlank()) {
                    Text(reply, maxLines = 1, overflow = TextOverflow.StartEllipsis, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                }
                state.pending?.let {
                    Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                        GlassButton(stringResource(R.string.assistant_confirm), onClick = vm::confirm, style = GlassButtonStyle.Primary)
                        GlassButton(stringResource(R.string.assistant_cancel), onClick = vm::cancel)
                    }
                }
                card?.let { result ->
                    result.rows.forEach { row ->
                        GlassPanel(Modifier.fillMaxWidth(), onClick = row.placeId?.let { id -> { vm.onCardRow(id) } }, elevated = false) {
                            Text("${row.title} · ${row.detail}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun phaseLabel(phase: AssistantPhase): Int = when (phase) {
    AssistantPhase.CONNECTING -> R.string.assistant_phase_connecting
    AssistantPhase.LISTENING, AssistantPhase.IDLE -> R.string.assistant_phase_listening
    AssistantPhase.THINKING -> R.string.assistant_phase_thinking
    AssistantPhase.SPEAKING -> R.string.assistant_phase_speaking
    AssistantPhase.PAUSED_FOR_GUIDANCE -> R.string.assistant_phase_paused
}
```

Vérifier dans `GlassButton.kt` le nom du style principal (`GlassButtonStyle.Primary` ; sinon `Brand`). Si `TextOverflow.StartEllipsis` n'existe pas dans la version de Compose, utiliser `TextOverflow.Ellipsis` et afficher `reply.takeLast(80)`.

- [ ] **Step 5: Slots in home and guidance screens**

`HomeScreen` : ajouter les paramètres `assistantButton: @Composable () -> Unit = {}` et `assistantPanel: @Composable () -> Unit = {}` (avant `vm`). Dans la colonne d'actions de droite, en premier enfant : `assistantButton()`. Dans `Column(bottomModifier)`, en premier enfant : `assistantPanel()` suivi de `Spacer(Modifier.height(RoadlySpacing.sm))`.
`homeScreen(...)` : mêmes deux paramètres, transmis à `HomeScreen`.

`NavigationScreen` : mêmes deux paramètres, transmis à `PortraitOverlay` et `LandscapeOverlay`. Portrait : `assistantButton()` en premier dans la colonne `Modifier.align(Alignment.CenterEnd)` (ligne ~190) ; `assistantPanel()` en premier dans la colonne du bas (ligne ~205). Paysage : `assistantButton()` en premier dans la colonne `TopEnd` (ligne ~244) ; `assistantPanel()` en premier dans la colonne `BottomStart` (ligne ~239).
`navigationScreen(...)` : mêmes paramètres, transmis à `NavigationScreen`.

- [ ] **Step 6: App wiring**

`app/build.gradle.kts` : `implementation(project(":feature:assistant"))` et `implementation(libs.androidx.lifecycle.process)`.

`RoadlyNavHost.kt` (imports `fr.roadly.feature.assistant.AssistantMicButton`, `fr.roadly.feature.assistant.AssistantOverlay`, `androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.ui.Modifier`) :

```kotlin
        homeScreen(
            // … paramètres existants …
            assistantButton = { AssistantMicButton() },
            assistantPanel = { AssistantOverlay(Modifier.fillMaxWidth()) },
        )
        // …
        navigationScreen(
            // … paramètres existants …
            assistantButton = { AssistantMicButton() },
            assistantPanel = { AssistantOverlay(Modifier.fillMaxWidth()) },
        )
```

`RoadlyApp.kt` (lire le fichier ; la classe est `@HiltAndroidApp`) — ajouter :

```kotlin
    @Inject lateinit var assistant: AssistantController

    // dans onCreate(), après super.onCreate() :
        // Micro réservé au premier plan : l'assistant se ferme quand l'application passe en arrière-plan.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = assistant.onAppBackground()
        })
```

(imports `androidx.lifecycle.DefaultLifecycleObserver`, `androidx.lifecycle.LifecycleOwner`, `androidx.lifecycle.ProcessLifecycleOwner`, `fr.roadly.core.assistant.AssistantController`, `javax.inject.Inject`). Si `RoadlyApp` n'a pas de `onCreate`, en créer un.

- [ ] **Step 7: Build + tests**

Run: `.\gradlew.bat :app:assembleDebug --offline -q` puis `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q`
Expected: build OK ; tous les tests PASS.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts feature/assistant feature/home feature/navigation app
git commit -m "feat(assistant): bouton micro animé, fenêtre compacte, consentement, branchement accueil et guidage"
```

---

### Task 16: Vérification sur le HONOR Magic 8 Pro et documentation

**Files:**
- Modify: `FEATURES.md` (ligne « Commande vocale »), `ARCHITECTURE.md` (modules `core:assistant`, `feature:assistant`), `SILLAXE_PARITY.md` (journal)

- [ ] **Step 1: Key**

Demander à l'utilisateur d'ajouter lui-même `GEMINI_API_KEY=…` dans `local.properties`, ou de la saisir dans Réglages → Roadly AI après installation. Ne jamais lire, afficher ni écrire la clé. Vérifier seulement : `git check-ignore local.properties` → doit afficher `local.properties`.

- [ ] **Step 2: Install and smoke test**

```powershell
.\gradlew.bat :app:assembleDebug --offline -q
adb -s (numéro de série retiré) install -r app\build\outputs\apk\debug\app-debug.apk
adb -s (numéro de série retiré) logcat -c
```

Sur l'appareil, avec l'utilisateur :
1. Réglages → Roadly AI → « Vérifier la clé » → « Clé acceptée par Gemini ».
2. Accueil → micro → permission micro → consentement → « Je vous écoute ».
3. « Combien de temps avant d'arriver ? » hors guidage → réponse « aucun guidage en cours » (aucune invention).
4. Lancer un guidage Toulouse → Place du Capitole, puis « Combien de temps avant d'arriver ? » → durée et heure identiques à la barre ETA.
5. « Je veux éviter les péages » → préférence cochée dans le panneau d'itinéraire, réponse avec durée avant / après.
6. « Trouve une station essence moins chère sur mon trajet » → carte de 1 à 3 stations avec prix et détour ; « ajoute la première » → demande de confirmation → « oui » → arrêt ajouté (visible dans la liste d'arrêts).
7. Interrompre l'IA en parlant pendant sa réponse → elle se tait immédiatement.
8. Déclencher une annonce GPS pendant une réponse (approcher une manœuvre en simulation ou bouton « Tester la voix » des réglages pendant une session) → la voix IA se met en pause, l'annonce est entièrement audible, l'IA reprend.
9. Mode avion en pleine session → « Roadly AI est indisponible, le guidage continue », guidage intact.
10. « Merci, c'est tout » → réponse brève puis fermeture ; sinon fermeture après 15 s de silence.

```powershell
adb -s (numéro de série retiré) logcat -d | Select-String "FATAL EXCEPTION"
```

Expected: aucune ligne.

- [ ] **Step 3: Battery**

```powershell
adb -s (numéro de série retiré) shell dumpsys batterystats --reset
# 5 minutes de session Roadly AI en guidage
adb -s (numéro de série retiré) shell dumpsys batterystats fr.roadly | Select-String "Estimated power use|Wake lock|Audio|Mobile radio"
```

Noter les valeurs dans le journal ; micro et WebSocket doivent être inactifs hors session (`dumpsys audio` : aucun enregistreur `fr.roadly` après fermeture).

- [ ] **Step 4: Docs**

- `FEATURES.md` : « Commande vocale » → « Roadly AI (Gemini Live) : conversation vocale, 29 outils réels, confirmation vérifiée, priorité GPS ; mot d'activation, OpenAI et écran dédié à venir (AI-2..AI-4) ».
- `ARCHITECTURE.md` : ajouter `core:assistant` (contrat fournisseur, Gemini Live, outils, audio, contrôleur) et `feature:assistant` ; préciser qu'aucun module GPS n'en dépend.
- `SILLAXE_PARITY.md` : entrée de journal datée « Roadly AI — lot AI-1 livré » avec le nombre de tests et les vérifications appareil réellement faites (ne lister que ce qui a été constaté).

- [ ] **Step 5: Commit**

```bash
git add FEATURES.md ARCHITECTURE.md SILLAXE_PARITY.md
git commit -m "docs: Roadly AI lot AI-1 — fonctionnalités, architecture, journal"
```

---

## Couverture de la spec

| Spec | Tâches |
|---|---|
| §2.1 modules, indépendance GPS | 4, 13, 15 |
| §2.2 contrat fournisseur | 4, 5 |
| §2.3 outils, registre | 6, 8, 9, 10, 13 |
| §2.4 refactor (`GuidanceActions`, `speaking`, `AppNavigator`) | 1, 2, 3 |
| §3 liste d'outils | 8, 9, 10 |
| §4 sécurité (confirmation, périmètre, consentement, robustesse) | 6, 10, 12, 14, 15 |
| §5 audio, priorité GPS, barge-in | 11, 12 |
| §6 cycle de vie | 12, 15 |
| §7 erreurs, quotas, coûts | 5, 7, 12, 14 |
| §8 interface | 14, 15 |
| §9 tests | toutes ; appareil : 16 |

