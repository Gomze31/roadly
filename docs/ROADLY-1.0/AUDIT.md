# Roadly 1.0 — reliability audit (code reading)

Date: 2026-11-22. Branch `integration/phase5`, HEAD `813d5a0`, working tree as found (other work was in progress in the same tree).

**Method.** Code reading only. No Gradle build, no test run, no device, no adb, no road. Nothing below is validated on a phone or in a car.

**Vocabulary.**

- Check status: `PASS (code reading)` = the code does what is stated, nothing more; `FAIL` = defect visible in code; `INCONCLUSIVE` = cannot be decided by reading; `NOT TESTED` = needs a device or a drive.
- Severity: `BLOCKER` (do not ship 1.0), `MAJOR`, `MINOR`.
- Confidence: `confirmed` (by code), `plausible` (code permits it, trigger not proven), `needs device`.

Line numbers refer to HEAD `813d5a0`. Paths are shortened: `core/navigation/.../X.kt` = `core/navigation/src/main/java/fr/roadly/core/navigation/X.kt`, same pattern for the other modules.

---

## 1. Startup and permissions

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 1.1 | Location services switched off is never detected on the phone UI. Only Android Auto checks it (`CarLocationSwitch.kt:21`). | `core/location/.../FusedLocationRepository.kt:72-83`, `feature/home/.../HomeScreen.kt:423` | Permission granted, location off: Home shows "Localisation…" forever, route screen shows "Position inconnue", guidance never moves. No message, no button. | BLOCKER | confirmed (no check exists); visible result needs device | Expose `LocationManagerCompat.isLocationEnabled` + `PROVIDERS_CHANGED` as a flow in `LocationRepository` (the car code already does it) and show a banner with a button to `Settings.ACTION_LOCATION_SOURCE_SETTINGS` on Home, Route and Guidance. |
| 1.2 | Approximate-only location counts as `GRANTED`. | `FusedLocationRepository.kt:103-106`, `app/.../MainActivity.kt:57` | Fixes arrive with ~2 km accuracy. Arrival and off-route both require ≤ 30 m accuracy, so guidance starts and never progresses. Nothing tells the user that precise location is required. | MAJOR | confirmed (state); behaviour needs device | Add a `GRANTED_APPROXIMATE` state; on Home and before guidance, ask for precise location (re-request `ACCESS_FINE_LOCATION`). |
| 1.3 | A dismissed permission dialog is recorded as "denied forever" and persisted. | `MainActivity.kt:58` | Dismissing the system dialog (back) returns "not granted" with no rationale flag: the app stops asking and only offers "Open settings". | MINOR | plausible, needs device | Mark denied-forever only when the rationale flag was true before the request and false after it. |
| 1.4 | Cold open on a route (`geo:` link, shortcut) can stay on "Position inconnue". Home's location flow sets the mode to `OFF` 5 s after Home leaves the screen, while the route screen is still waiting for its first fix. | `feature/home/.../HomeViewModel.kt:173-179`, `feature/route/.../RouteViewModel.kt:135-139` | If no cached last location exists and the first GPS fix takes more than 5 s, the route screen never gets an origin. | MINOR | plausible, needs device | Count location users (acquire/release) instead of a single shared `setMode`, or have `RouteViewModel` hold `MAP` mode until it has an origin. |
| 1.5 | The route screen's "no origin" panel has no permission or location action. Onboarding is drawn on Home only. | `feature/route/.../RouteScreen.kt:236-245`, `app/.../RoadlyNavHost.kt:160-167` | First launch through a `geo:` link: no permission prompt, only "long-press the map to pick a start". | MINOR | confirmed | Reuse Home's `PermissionPanel` in the `NoOrigin` state. |
| 1.6 | `fused.lastLocation` is accepted as the first fix whatever its age. Sessions filter stale fixes; the route screen and Home do not. | `FusedLocationRepository.kt:73`, `RouteViewModel.kt:100,137` | A route can be computed from an old cached position (phone just switched on, after a flight). | MINOR | plausible | Ignore a last location older than ~2 min for the route origin, or mark it as approximate. |
| 1.7 | Preferences DataStore has no corruption handler and its flows have no `catch`. | `core/data/.../db/DatabaseModule.kt:15`, `core/data/.../repository/UserPreferencesRepository.kt:276` (same pattern throughout) | A corrupt preferences file crashes the app at every start until data is cleared. Rare. | MINOR | plausible | `preferencesDataStore(name, corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() })`. |

Checks: notifications denied → `PASS (code reading)`, guidance starts anyway and a warning dialog is shown once per launch (`RoadlyNavHost.kt:83-90`, `MainActivity.kt:146-152`); the dialog itself is `NOT TESTED` on device. Permission revoked while running → `PASS (code reading)`: Android kills the process, state is re-read in `onResume` (`MainActivity.kt:155`). Cold start with no network → `NOT TESTED` (offline banner exists, `HomeScreen.kt:237`; map content depends on the MapLibre cache).

## 2. Error and degraded-network states

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 2.1 | Search refuses to send any request unless the network is `VALIDATED`; "Retry" re-reads the same flag. | `feature/search/.../SearchViewModel.kt:152,164,176`, `core/data/.../connectivity/ConnectivityObserver.kt:22-23` | On an unvalidated but working network (some car hotspots, captive portal already passed, VPN), search answers "Pas de connexion réseau" and retry does nothing. | MINOR | plausible | Use connectivity for the banner only; always attempt the request and report its real error. |
| 2.2 | Raw host name and HTTP code reach the user. | `core/data/.../network/ApiClient.kt:100,109`, `core/designsystem/.../component/ErrorMessages.kt:9-10` | "Le service « valhalla1.openstreetmap.de » est indisponible.", "Erreur du service (400)". | MINOR | confirmed | Map hosts to a human name ("calcul d'itinéraire", "recherche") in `userMessage()`; hide the code. |
| 2.3 | Valhalla 400 errors other than 170/171/442 are not translated (distance limit, too many locations). | `core/routing/.../ValhallaRoutingProvider.kt:208-215` | A very long trip, or a long scooter trip, shows "Erreur du service (400)" with a retry that cannot succeed. | MINOR | plausible | Map 153/154 to a dedicated "trajet trop long / trop d'étapes" message without retry. |
| 2.4 | "Nearby" search with no known position reads the position once. | `SearchViewModel.kt:162-163,174-175` | "Position inconnue" stays on screen after the fix arrives; no retry button in that state. | MINOR | confirmed | Wait for `location.fixes.first()` with a timeout, or re-run when a fix arrives. |
| 2.5 | Home address line shows "Localisation…" whenever reverse geocoding has not answered, even with a valid fix. | `HomeScreen.kt:423`, `HomeViewModel.kt:255-258` | Offline at start: looks like GPS is still searching while the position is known. | MINOR | confirmed | With a fix and no address, show coordinates or "Position trouvée". |
| 2.6 | Route screen recomputes serially (`collect`, not `collectLatest`). | `RouteViewModel.kt:147` | Changing an option during a calculation waits for the current one (up to 15 s), then shows an outdated result before the right one. | MINOR | confirmed | `collectLatest`. |

Checks: route error and retry → `PASS (code reading)` (`RouteScreen.kt:246`, `RouteViewModel.kt:385`); no infinite spinner found on the route screen (server-down stops after the first variant, `RouteAlternativesUseCase.kt:57-64`). Guidance start failure → `PASS (code reading)` (`NavigationScreen.kt:724-735`, retry and stop). Reroute failure → `PASS (code reading)`: spoken once, back-off 10/10/20/40/60 s, old route kept (`NavigationSession.kt:864-931`). Geocoding with one provider down → `PASS (code reading)` (`CompositeGeocoder.kt:38-42`).

## 3. Route alternatives and vehicle profiles

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 3.1 | **Automatic reroute drops the planned stops.** The session only knows the route; the reroute request has no `waypoints`. `GuidanceActions` keeps its stop list with abscissas of the old route. | `core/navigation/.../NavigationSession.kt:882-888`, `core/navigation/.../GuidanceActions.kt:42,50-53` | Trip with stops + one missed turn: the new route goes straight to the final destination, without any message. The stop list shown afterwards is computed against the wrong route. | BLOCKER | confirmed | Give the session a `remainingWaypoints` supplier (set by `GuidanceActions.start`), pass it in the reroute `RouteRequest`, and re-project the stops on the new geometry. |
| 3.2 | Departure time is never sent to Valhalla (`date_time` absent); "Partir à / Arriver à" only shifts the displayed arrival. The default departure is the time the screen was opened and is not refreshed. | `ValhallaRoutingProvider.kt:69-142`, `RouteViewModel.kt:102,464-468` | Arrival time shown is late by however long the screen stayed open; time-dependent restrictions are ignored. | MINOR | confirmed | Refresh "now" on retry and on start; state in the UI that the time does not change the route. |
| 3.3 | "Sans péage" is a soft avoidance (`use_tolls = 0`) with a fixed label. | `core/routing/.../RouteAlternativesUseCase.kt:38-39,114`, `ValhallaRoutingProvider.kt:115` | A variant titled "Sans péage" can still carry the "péage" pill when no toll-free path is reasonable. | MINOR | plausible | Drop the variant, or relabel it "Péages évités si possible", when `route.hasToll`. |
| 3.4 | Stop optimisation always uses `auto` costing and ignores tunnels / unpaved exclusions. | `ValhallaRoutingProvider.kt:184-190` | Truck or motorcycle profile: stop order optimised for a car. | MINOR | confirmed | Use `costingFor(vehicle?.type)` and the same options as `/route`. |
| 3.5 | Stops, chosen departure/arrival time and selected variant are not in `SavedStateHandle`. | `RouteViewModel.kt:100-107` | Process death on the route screen: stops and time choice are lost, route recomputed from scratch. | MINOR | confirmed | Persist `stops` (JSON), `departAt`, `arriveAt`, `activeId` in `SavedStateHandle`. |
| 3.6 | `sameAsFastest` can never be true: identical geometries are removed just before. | `RouteAlternativesUseCase.kt:69,92` | None (dead flag); near-identical variants (one point apart) are still listed as different. | MINOR | confirmed | Remove the flag, or dedupe on distance + duration tolerance. |

Checks: avoid tolls / motorways / ferries sent for all variants and for reroute → `PASS (code reading)` (`ValhallaRoutingProvider.kt:108-124`, `NavigationSession.kt:885`). Tunnels / unpaved strict exclusion with fallback → `PASS (code reading)` (`:49-54`). Vehicle costing (truck with entered dimensions only, motorcycle, taxi, auto) → `PASS (code reading)` (`:126-133,170-175`). Reroute keeps costing, vehicle and scooter profile → `PASS (code reading)`. Real server behaviour → `NOT TESTED`.

## 4. Scooter restrictions

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 4.1 | **In scooter mode, voice "navigate to …" and the Home "Resume guidance" card compute a car route.** `startTo` and `resumeLastGuidance` call the motor alternatives with the car profile and never read the travel mode; `LastGuidance` does not store the mode. | `GuidanceActions.kt:77-89`, `feature/voice/.../VoiceCommandExecutor.kt:96`, `HomeViewModel.kt:218-238`, `core/data/.../UserPreferencesRepository.kt:57` | A scooter rider gets car guidance (motorways and expressways allowed, no legal check, no range estimate) with no indication that the mode changed. | BLOCKER | confirmed | In `startTo`, read the travel mode: in scooter mode use `ScooterRouteOptionsUseCase` (or refuse and open the route screen). Store the mode in `LastGuidance` and resume through the same path. |
| 4.2 | A scooter route that still contains forbidden or walk-only portions (after the single exclusion pass, 10 polygons max), or whose analysis failed, can be started without confirmation. | `core/routing/.../ScooterRouting.kt:162-172`, `feature/route/.../ScooterRoutePanel.kt:162-178` | Only a small red line "x m interdits d'après les données" or "Analyse des voies indisponible"; the start button is unchanged. | MINOR | confirmed | Confirmation dialog before start when `hasBlockingPortion` or `analysis == null`. |
| 4.3 | Automatic reroute in scooter mode does not redo the forbidden-area exclusion; the analysis is refreshed for display only. | `NavigationSession.kt:819-831,882-888` | After a missed turn the new scooter route may cross portions the first route avoided. | MINOR | confirmed | After the reroute analysis, if `hasBlockingPortion`, request once more with `excludePolygons`, as `computeVariant` does. |

Checks: scooter routing uses Valhalla `bicycle` costing, speed capped at 25 km/h → `PASS (code reading)` (`core/scooter/.../routing/ScooterRoutingProfile.kt:22,59-60`). Motorway, `motorroad=yes`, roads > 50 km/h and rural roads are classified `FORBIDDEN`; unknown limit or unknown urban status is `UNVERIFIED`, never "allowed" → `PASS (code reading)` (`core/scooter/.../regulation/ScooterRegulation.kt:57-72,130-143`). Manual recalculation, added stop and alternatives keep the scooter costing → `PASS (code reading)` (`GuidanceActions.kt:138-147`, `feature/navigation/.../NavigationViewModel.kt:368`). Whether Valhalla's bicycle costing really excludes every French expressway depends on OSM tagging → `INCONCLUSIVE`. Range estimate accuracy → `NOT TESTED`.

## 5. Speed limits and alerts

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 5.1 | Rain lowering applies the French rule everywhere and to every road with a limit ≥ 90. | `core/navigation/.../RainProvider.kt:50-55`, `NavigationSession.kt:715-722` | Abroad (e.g. a 100 km/h road), the badge shows 80 when it rains and the alert uses it. Opt-in, off by default; a "rain" pill explains it except in the reduced layout during complex manoeuvres. | MINOR | confirmed | Apply only when the route profile country is FR; keep the rain mark next to the badge in every layout. |
| 5.2 | Overspeed beep has no hysteresis. | `NavigationSession.kt:989-999` | Driving right at limit + tolerance: repeated beeps as GPS speed oscillates. | MINOR | plausible, needs device | Re-arm only below limit + tolerance − 3 km/h, or after a minimum delay. |
| 5.3 | Rain state is refreshed every 10 min at the last position. | `NavigationSession.kt:701-712` | The lowered limit can persist up to 10 min after the rain stopped (or 100+ km further on a motorway). | MINOR | confirmed | Refresh by distance as well (e.g. every 20 km). |

Checks: guidance limit comes from the route profile (one `trace_attributes` per route), cleared when off-route and after a reroute, unknown = "—" → `PASS (code reading)` (`NavigationSession.kt:647-661,918`, `core/navigation/.../RouteAttributes.kt:57-87`). Profile rejected when edge lengths and geometry differ by more than 5 % → `PASS (code reading)` (`RouteAttributes.kt:116-120`). Free drive clears the limit after a turn and on request failure → `PASS (code reading)` (`core/navigation/.../FreeDrive.kt:303-304,325-326`). Live map-matching limit is disabled on the public instance → `PASS (code reading)` (`ValhallaTraceSpeedLimitProvider.kt:53`). Correctness of OSM `maxspeed` on real roads → `NOT TESTED`.

## 6. Voice

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 6.1 | No French TTS voice (or engine init failure): the service marks itself unavailable and only logs. `SpeechService` exposes no availability state. | `core/navigation/.../AndroidSpeechService.kt:111,289-299` | The whole trip is silent, with no on-screen notice; it retries only at the next guidance. | MAJOR | confirmed (no surfacing); how often it happens needs device | Expose `available: StateFlow<Boolean>`; show a banner in guidance and a row in voice settings with a link to install the voice data. |
| 6.2 | `URGENT` guidance items never expire in the queue. | `core/navigation/.../SpeechQueue.kt:72-73` | If the engine is slow to initialise (first announcement of a trip), "turn now" can be spoken several seconds late. | MINOR | plausible, needs device | Expire `URGENT` after a short age (3–4 s), or warm the engine at guidance start. |
| 6.3 | Announcements are dropped, not delayed, when audio focus is refused or during a call (by design). | `AndroidSpeechService.kt:356-363` | A manoeuvre can pass unspoken during a call; the screen is the only cue. | MINOR | confirmed (documented choice) | Short tone or haptic for `URGENT` items while in call. |

Checks: voice commands use the system recognizer activity, no `RECORD_AUDIO` permission, missing recognizer handled → `PASS (code reading)` (`core/designsystem/.../component/VoiceInput.kt:22-34`). Mute is per trip and per channel; volume 0 silences without taking focus → `PASS (code reading)` (`AndroidSpeechService.kt:135-151`, `NavigationSession.kt:252-256`). Dead engine is recreated at the next announcement → `PASS (code reading)` (`:377-390`), `NOT TESTED` on device. Behaviour with music, Bluetooth, calls → `NOT TESTED`.

## 7. Favourites, recents, trip history

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 7.1 | The trip is recorded under the variant label, not the destination name. | `NavigationSession.kt:215` (`tripRecorder.begin(option.label, …)`), `feature/trips/.../TripsScreen.kt:95` | History rows are titled "Le plus rapide", "Sans péage", "Itinéraire recalculé", "Via …". Wrong data is stored permanently. | MAJOR | confirmed by code; screen `NOT TESTED` | Pass `name ?: option.label` (the destination name is in scope two lines above). |
| 7.2 | Every `session.start` during a guidance restarts the recording: `begin` clears points and distance and nothing is saved first. | `NavigationSession.kt:187,215`, `core/navigation/.../TripRecorder.kt:66-70` | Choosing an alternative, adding or removing a stop, manual recalculation or a voice "avoid tolls" discards the trip recorded so far; distance, cost and stats are understated. | MAJOR | confirmed | Call `begin` only when the previous state was not `Guiding`. |
| 7.3 | Trip lists and statistics load every trip with its full GPS track (`SELECT *` + decode), with no retention limit. A single row above ~2 MB (about 1 200 km in one guidance) cannot be read by the cursor. | `core/data/.../db/Daos.kt:97`, `core/data/.../repository/TripRepository.kt:28,58-59`, `core/data/.../db/Entities.kt:126` | Trips, Profile and scooter settings get slower with history size; one very long trip can crash those screens at every opening. | MAJOR | plausible (growth confirmed, crash threshold computed, not reproduced) | Summary query without `pointsCsv` for lists and stats; load the track only in Replay; downsample or split tracks above ~20 000 points. |
| 7.4 | A trip in progress exists only in memory. | `TripRecorder.kt:57,87-107` | Process killed during guidance: the whole trip is missing from history. | MINOR | confirmed | Checkpoint the track to disk every few minutes. |
| 7.5 | `PlaceKind.valueOf(kind)` on stored favourites, no fallback. | `core/data/.../repository/FavoritesRepository.kt:40` | Renaming or removing a `PlaceKind` in 1.0 would crash Home, Search and Route for users with such favourites. | MINOR | plausible (no rename seen) | `runCatching { valueOf }.getOrDefault(PlaceKind.COORDINATES)`. |
| 7.6 | `exportSchema = false`: no schema history to check migrations against. | `core/data/.../db/RoadlyDatabase.kt:11` | No direct impact; a wrong hand-written migration is only found at upgrade time, as a startup crash. | MINOR | confirmed | Export schemas and use `MigrationTestHelper` over 1→7. |

Checks: migration strategy → `PASS (code reading)`: version 7, migrations 1→2 … 6→7 all registered, no destructive fallback (`DatabaseModule.kt:22`); column lists of migrations 4→5, 5→6, 6→7 compared by hand with the entities and found consistent. Instrumented migration tests exist (`RadarMigrationTest`, `ScooterMigrationTest`) but were not run → upgrade 0.1.x → 1.0 on a device is `NOT TESTED`. Backup and device transfer are disabled on purpose (`AndroidManifest.xml:9`, `data_extraction_rules.xml`): data does not survive a reinstall or a new phone. Upgrade also requires the same signing key and a higher `versionCode` (git commit count, `app/build.gradle.kts:17`) → `INCONCLUSIVE` from code.

## 8. Day/night themes

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 8.1 | A failed style load is not handled: `styleReady` stays false, no retry (same URL is not reloaded by the screen). | `core/map/.../MapController.kt:213-224` | Day/night switch (or aerial toggle) with no network and no cached style: overlays (route line, marker) stop being applied, possibly on a blank map, until the next style change. | MINOR | plausible, needs device | Listen to `addOnDidFailLoadingMapListener`; fall back to the previous style and retry when connectivity returns. |
| 8.2 | The theme is re-evaluated once a minute from `lastKnown`, read outside any state. | `MainActivity.kt:78-79`, `app/.../ThemeViewModel.kt:47-48` | Up to 60 s late at sunset / sunrise; without a position the fallback is fixed 7 h–20 h. | MINOR | confirmed | Acceptable; optionally re-evaluate on the first fix. |

Checks: style switch during guidance reinstalls layers and replays state → `PASS (code reading)` (`MapController.kt:223`). Automatic mode = sun elevation below −0.833° at the current position → `PASS (code reading)` (`core/common/.../SunTimes.kt`). Hard-coded colours in feature and design-system code: 17 occurrences, all shadows, scrims or self-contained signs (parking badge, speed sign) → no theme break found, `PASS (code reading)`. Readability in both themes on a real screen, tunnels → `NOT TESTED`.

## 9. Navigation UX leftovers

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 9.1 | Drive simulation toggle is a normal user setting at HEAD; when on, guidance replaces GPS with computed positions and no "simulation" mark is shown in guidance. | `feature/settings/.../NavigationGroup.kt:79-84`, `core/navigation/.../DriveSimulation.kt:91` (HEAD) | A driver who enables it sees the vehicle drive itself; trip history and parked-car position are recorded from fake positions. | BLOCKER at HEAD | confirmed at HEAD. **An uncommitted change found in the working tree during this audit** hides the toggle and refuses to start outside debuggable builds (`DriveSimulationGate`); not reviewed further, not tested. | Commit that change with its test; also clear the stored `test.simulateDrive` value in release builds. |
| 9.2 | Resume offer has no age limit (stated in the code). | `NavigationSession.kt:128-132`, `UserPreferencesRepository.kt:57` | "Reprendre le guidage vers …" can appear days after the interrupted trip. | MINOR | confirmed | Offer only if `now − startedAtEpochMs` < ~12 h (the timestamp is already stored). |
| 9.3 | Exit-sign overline takes the remaining width and is ellipsised. | `feature/navigation/.../ExitSignPanel.kt:75-78` | "PROCHAINE SOR…" when the panel is narrow (next to the branch diagram). | MINOR | confirmed (truncation rule); visual result `NOT TESTED` | Shorter label when narrow ("SORTIE"), or two lines. |
| 9.4 | Up to four pills (danger zone, next limit, free-flow toll, rain) are stacked above the speed badge with no cap. | `feature/navigation/.../NavigationScreen.kt:605-647` | Possible overlap with other panels when speeding in a zone. | MINOR | `INCONCLUSIVE` by code, needs device | Cap at two pills by priority (zone > next limit > others). |
| 9.5 | An external destination (`geo:` etc.) received during guidance pops the guidance screen; the in-app search path keeps it. | `RoadlyNavHost.kt:196,205` vs `:115-118` | Guidance keeps running in the service but its screen is gone; return only through the Home card. | MINOR | confirmed | Same rule as the search path: `popUpTo(NavigationRoute)` when guidance is in the stack. |

## 10. Process, lifecycle, external intents

| # | Finding | File:line | User impact | Severity | Confidence | Suggested fix |
|---|---|---|---|---|---|---|
| 10.1 | `geo:` URIs with parameters in the path (`geo:48.85,2.35;u=35`, `;crs=…`) are rejected: the coordinate pattern is anchored on the whole path. | `app/.../ExternalRequest.kt:36,43,48` | Roadly opens on Home with no destination and no message. | MINOR | confirmed | Cut the path at the first `;` before matching. |
| 10.2 | The launch intent is replayed when the task is recreated from Recents (no `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` check). | `MainActivity.kt:71,139-143` | Reopening Roadly from Recents after leaving with Back reopens the old external destination. | MINOR | plausible, needs device | Ignore `VIEW` intents carrying that flag. |
| 10.3 | A rejected or unparsable external link gives no feedback. | `ExternalRequest.kt:55-57`, `MainActivity.kt:142` | Out-of-range coordinates or an unknown form: the app just opens. | MINOR | confirmed | Toast "Lien de destination non reconnu". |
| 10.4 | Search query typed by the user is not saved (only the initial route argument is). | `SearchViewModel.kt:90` | Process death on the search screen: typed text lost. | MINOR | confirmed | `savedStateHandle.getStateFlow("query", "")` and write on change. |

Checks: external intents never start guidance by themselves, they open the route screen or search → `PASS (code reading)` (`RoadlyNavHost.kt:194-208`). Coordinates bounded, text capped at 200 characters, URI at 2 000 → `PASS (code reading)` (`ExternalRequest.kt:32-34,62-66`). Guidance screen restored after process death with an idle session exits to Home, where the resume card is offered → `PASS (code reading)` (`NavigationScreen.kt:189`, `NavigationSession.kt:130-132`). Service is `START_NOT_STICKY`, a refused foreground start is only logged → known and documented (`GUIDAGE-INTELLIGENT.md` § 12.2), unchanged. Real process death and Doze on the HONOR → `NOT TESTED`.

---

## Ranked 1.0 blockers

1. **3.1 — Automatic reroute drops planned stops** (`NavigationSession.kt:882-888`). Silent wrong route after any missed turn on a multi-stop trip. Confirmed by code.
2. **4.1 — Scooter mode: voice navigation and "Resume" compute car routes** (`GuidanceActions.kt:77-89`, `HomeViewModel.kt:218-238`). The only confirmed path that can guide a scooter onto motorway-class roads without any warning.
3. **1.1 — Location services off is not detected on the phone** (`FusedLocationRepository.kt:72-83`). Common situation, endless "Localisation…", no way out offered.
4. **9.1 — Simulation toggle exposed to end users** (`NavigationGroup.kt:79-84` at HEAD). A fix exists uncommitted in the working tree; it must be tested and committed before 1.0.

Major, next in line: 7.1 and 7.2 (trip history title and truncated recording, same line `NavigationSession.kt:215`), 6.1 (silent guidance when no French voice), 1.2 (approximate-only location accepted), 7.3 (trip lists load every GPS track).

## Verified as correct (code reading only)

- Room: version 7, complete migration chain 1→7, no destructive fallback; migration SQL matches the entities (read by hand).
- Route preferences (tolls, motorways, ferries, tunnels, unpaved) are sent for every variant and kept by the automatic reroute; strict exclusions fall back when no path exists.
- Vehicle costing: truck (only entered dimensions), motorcycle, taxi, auto; reroute keeps the vehicle and the scooter profile.
- Scooter legal classification never states "allowed" without data; motorway, `motorroad`, > 50 km/h and rural roads are flagged forbidden; speed capped at 25 km/h.
- Speed limit in guidance: unknown is shown as unknown; cleared when off-route and after a reroute; free drive clears it after a turn and on failure; public instance never queried for live limits in guidance.
- Network layer: timeouts, 429/503 back-off, bounded `Retry-After`, 15 s cap per call on the public host; route screen stops at the first server-down variant; errors reach the screen with a retry.
- Search: both geocoders isolated, one may fail; coordinates typed by hand need no network.
- Voice: system recognizer (no microphone permission), per-channel mute, focus requested per utterance and released when idle, dead engine recreated.
- External intents: validated bounds and lengths, never auto-start guidance.
- Map style switch reinstalls layers and replays state; no theme-breaking hard-coded colour found.
- Process death on the guidance screen returns to Home with a resume offer.

None of the items above has been validated on a device or on the road.

## Status after the audit (22 November 2026, uncommitted)

| Finding | Status | Evidence |
|---|---|---|
| Blocker 1 — automatic reroute drops planned stops | FIXED | `NavigationSession.rerouteWaypoints`, set by `GuidanceActions`; stops re-projected on the new route. JVM test `an automatic reroute after leaving the route keeps the stops still ahead` — PASS |
| Blocker 2 — scooter or bicycle mode routed as a car by voice, Android Auto shortcut and Home « Reprendre » | FIXED by refusal | `GuidanceActions.startTo` and `HomeViewModel.resumeLastGuidance` refuse in those modes. JVM test `scooter or bicycle mode - the car shortcut ... is refused` — PASS. The Home refusal has no dedicated test, and neither path computes a scooter route yet: the rider must use the route screen |
| Blocker 3 — location services off never detected on the phone | FIXED | `LocationReadiness` (core:location): banner on Home with a button to the location settings, re-read on return to the foreground and on each change of the switch; guidance start refused meanwhile. JVM tests `LocationReadinessTest`, `StartChecksTest` — PASS. Never seen on a device |
| Blocker 4 — simulation toggle visible to end users | FIXED | `DriveSimulationGate`: setting hidden and start refused outside debuggable builds. JVM test — PASS; release APK built, not run |
| Major 5 — trip saved under the variant label | FIXED | recorded under the destination name; covered by the existing suite only |
| Major 6 — recording restarts on every route change | FIXED | `begin` skipped when guidance continues to the same destination; covered by the existing suite only |
| Major 7 — no French voice: silent trip with no notice | FIXED | `SpeechService.voiceAvailable`; dialog once per app launch with a button to the text-to-speech settings; guidance continues on screen. The once-only rule is tested (`StartChecksTest`); the engine-failure path itself has no test (no Android engine on JVM) |
| Major 8 — approximate-only location counts as granted | FIXED | same banner and start refusal as blocker 3, with the steps to enable precise location. JVM tests — PASS |
| Major 9 — trip lists load every full GPS track | FIXED | `TripDao.observeSummaries` returns every column except the track; the track is read only by `byId` (replay screen). JVM test `TripSummaryQueryTest` — PASS. The query has not run against a real database |

None of these fixes has run on a device.
