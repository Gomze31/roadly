# Android Auto — review fixes (Roadly 1.0)

Scope: the six defects listed in `docs/ANDROID-AUTO-REVUE.md` (found by code reading), on branch `integration/phase5`.
All changes are inside `feature/car`. Nothing here was run on an emulator, on the DHU or in a car.

Paths: `car/` = `feature/car/src/main/java/fr/roadly/feature/car/`, `test/` = `feature/car/src/test/java/fr/roadly/feature/car/`.
Defect numbers follow the task order, not the severity order of the review document.

## Defects

### 1. `setRoundaboutExitNumber` on a manoeuvre type that refuses it — FIXED

- Library rule checked in the Car App 1.7.0 bytecode (`app-1.7.0.aar`, `Maneuver$Builder.setRoundaboutExitNumber` →
  `Maneuver.isValidTypeWithExitNumber`): only types 32–35 (`TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW/CCW`, with or without angle) are
  accepted; any other type throws `IllegalArgumentException("Maneuver does not include roundaboutExitNumber")`.
- The defect was real: `CarGuidanceMapper.step` keeps the exit number of a `ROUNDABOUT_EXIT` manoeuvre, whose code is
  `TYPE_ROUNDABOUT_EXIT_CCW`.
- Change: `CarGuidanceMapper.acceptsExitNumber` and `CarGuidanceMapper.maneuverBuilder` (`car/CarModels.kt`) set the number only on an
  accepting type; `CarGuidanceScreen.step` uses them (`car/CarGuidanceScreen.kt`).
- Tests (`test/CarReviewFixesTest.kt`): `the library refuses an exit number on a plain roundabout exit` (asserts the library rule
  itself), `a roundabout exit carrying an exit count still builds a manoeuvre`, `the roundabout entry keeps its exit number, and every
  manoeuvre type builds with or without one`.

### 2. Lane without directions drawn as a straight arrow — FIXED

- Change: `CarLanes.displayable` (`car/CarVisuals.kt`) returns no lanes at all when any lane has no direction. It is applied in
  `CarGuidanceMapper.map` (`car/CarModels.kt`, so the model, the display key and the template agree) and in `CarLanes.toCar`.
  `CarGuidanceScreen.step` sends neither `addLane` nor `setLanesImage` in that case. Both `ifEmpty { STRAIGHT }` fallbacks are removed.
- Tests (`test/CarReviewFixesTest.kt`): `known lanes are sent as they are`, `one lane without direction removes the whole lanes block,
  no straight arrow is invented`.
- Not covered: the lanes bitmap (`CarVisuals.lanes`) needs Android graphics.

### 3. Roundabout countdown shown as certain with `RoundaboutConfidence.LOW` — FIXED

- Same condition as the phone panel (`RoundaboutModePanel`: `confidence == LOW && phase == INSIDE`).
- Change (`car/CarModels.kt`): `roundaboutCue` returns null in that case unless `exitIsNext` (the usual manoeuvre text stays);
  `roundabout` no longer passes `vehicleAngleDeg`. The arms keep the last count established with certainty. The approach phase is
  unchanged.
- Tests (`test/CarRoundaboutModeTest.kt`): `an unsure position inside the ring shows no countdown and no vehicle marker`, `an unsure
  position keeps the next exit announcement, which cannot go back`, `an unsure position before the ring does not hide the announced
  countdown`. The LOW state is produced by the real `RoundaboutProgressTracker` with an extrapolated fix.

### 4. Map, puck and last speed frozen in Arrived / Failed — FIXED (stale data removed), live position NOT followed

- Change (`car/CarGuidanceScreen.kt`, `update()` non-Guiding branch, `onStart`, new `clearStale`): the HUD is cleared
  (`CarHudModel.forState`, new in `car/CarModels.kt`, returns `EMPTY` outside guidance), the vehicle puck and the manoeuvre arrow are
  removed. The route line and the destination marker stay.
- Not done: following `deps.location.fixes` after arrival, as the review suggested. It needs a location collector with its own
  lifecycle in this screen, which is more than the minimal change asked for. After arrival the map shows no vehicle rather than a
  stale one.
- Test (`test/CarReviewFixesTest.kt`): `after arrival or a failure the hud carries nothing measured`. It covers the model only; the
  calls to the renderer are not covered by a test.

### 5. GPS lost never shown during guidance — FIXED, drawing not covered by a test that runs here

- Change (`car/CarHudView.kt`): when `model.gpsLost`, a "Signal GPS perdu" pill (`R.string.car_gps_lost`) is drawn at the top centre
  of the free area, below the danger-zone pill when there is one (`drawGpsLost`; `drawZone` now returns its bottom edge).
- Side effect: free drive uses the same HUD model, so the pill also appears there, next to the same text already shown in the
  free-drive card.
- Tests: the flag the view reads is asserted in `test/CarModelsTest.kt` (`hud hides speed when gps is lost`, extended). The drawing
  itself is a `View.onDraw` and has no test that runs on this PC:
  a `hudGpsLost` capture was added to `test/CarScreenshots.kt`, which needs Robolectric and `-PscreenshotsDir` and was not run.
  The pill position has never been seen on a screen.

### 6. `updateTrip` sent on every state emission — FIXED

- Change: pure `CarGuidanceMapper.tripKey` (`car/CarModels.kt`): route id, step index, current step, destination name, manoeuvre
  distance and remaining distance as displayed, arrival minute, remaining minutes; one single key while rerouting or without
  manoeuvre. `CarGuidanceScreen.updateTrip` returns early on an unchanged key, records the key only after the host accepted the
  trip (a refused call is retried on the next emission), and forgets it when guidance stops.
- Consequence: the step ETA inside the trip is refreshed only when the key changes (it is no longer recomputed every second).
- Tests (`test/CarReviewFixesTest.kt`): `positions that display the same trip share one key`, `the key changes with the step, the
  shown distances, the arrival minute and the destination`, `rerouting is one loading trip, sent once however the position moves`.
- Not measured: the actual reduction of host calls (needs the DHU).

## Merge-resolution points

| Point | Finding | Action |
|---|---|---|
| `CarHudView.drawNextLimit` placement | By the arithmetic, the pill starts 10 dp to the right of the limit sign's right edge and cannot overlap the speed bubble or the sign. Not seen on a screen. | None |
| `CarHomeScreen.goHome` | Private, no caller left. | Removed (dead code) |
| `CarMapRenderer` detail | Starts at `MapDetail.REDUCED`, then follows `MapDetailPolicy` through `setSpeed`; `setAerial` is stored and replayed on a new surface. Nothing broken found by reading. | None |

## Validation

Command: `.\gradlew.bat :feature:car:testDebugUnitTest --offline -q` — result line: `45 tests completed, 1 failed`.

| Level | Status | Method and detail |
|---|---|---|
| JVM tests | PASS | 44 of 44 pure JVM tests pass (`CarReviewFixesTest` 9, `CarRoundaboutModeTest` 8, `CarDrivePassTest` 15, `CarModelsTest` 8, `CarTemplatesTest` 4). |
| Robolectric tests | INCONCLUSIVE | `CarHomeStripTest` (1 test) fails with "Unable to load Robolectric native runtime library" (Windows user folder containing a space); it does not reach the code under test. `CarScreenshots` (Robolectric, HUD captures) produced no result in this run. |
| Emulator | NOT TESTED | |
| DHU | NOT TESTED | |
| Real car | NOT TESTED | |

To check on the DHU or in a car: position and legibility of the GPS-lost pill, the guidance screen after arrival (no puck, route
kept), a roundabout taken with a poor signal, host behaviour with the reduced `updateTrip` rate (instrument cluster), and a lane
block withheld because one lane has no direction.
