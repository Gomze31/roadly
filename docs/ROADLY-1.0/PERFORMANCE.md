# Roadly 1.0 — performance and battery

22 November 2026. Each line says how it was obtained. Nothing here is a battery measurement: none has been made.

## Measured

| What | Method | Result | Status |
|---|---|---|---|
| Map frame rate, idle home screen, stationary indoors | physical device (HONOR Magic 8 Pro, 0.1.215), `RoadlyFps` log, 3 minutes | 57–59 frames per second, continuously | FAIL — the map renders at full rate while nothing moves |
| Full-route search cost, vehicle 60 m beside a 50 000-point route | JVM benchmark `RouteTrackerScanCostTest` | before: 1 431 µs and 4.5 MB allocated per fix; after: 1 177 µs and 1 KB | PASS (fixed in 0.1.215) |
| Route alternatives, server that never answers | JVM test, virtual time | before: 45 s; after: 15 s | PASS (fixed in 0.1.215) |
| GNSS fix rate requested by Roadly | physical device, `dumpsys location` | 1 fix per second, high accuracy, released within the second when Roadly is not visible and not guiding | PASS |

## The idle rendering at 58 frames per second

- **Observation** (device): `RoadlyFps: carte 57–59 i/s` every 5 s on the idle home screen, phone stationary, accuracy 8–12 m.
- **What 0.1.215 already had**: no marker animation when the position moves less than 0.5 m and the heading less than 1°.
- **Cause, by code reading and simulation — not confirmed on the device**: indoors the GPS position wanders by a few metres every
  second. Each fix therefore passed the 0.5 m rule and started a one-second marker animation, which rewrites the marker source on every
  frame. One animation per second, each lasting one second, is continuous rendering.
  `MarkerInterpolationTest` reproduces it: with 2.5 m of jitter, more than 150 of 180 fixes started an animation.
- **Change made** (not installed): `MarkerInterpolation.isStandstillJitter` — when the measured speed is below 0.5 m/s and the fix is
  within the reported accuracy (at least 2 m) of the displayed position, the marker stays where it is. The distance is measured from the
  displayed position, so a slow real movement still gets followed once it exceeds the accuracy. Same test: 0 of 180 fixes animate.
- **Still to verify on the device**: that the frame rate actually drops when stationary with this change. Until then the row above stays FAIL.
- **Not investigated**: whether anything else keeps the map rendering (the FPS listener itself exists only in debuggable builds).

## Not measured

| What | Why | How to measure |
|---|---|---|
| Idle CPU usage | the phone was in use by its owner during this pass; Roadly was not brought to the foreground | `adb shell top -b -n 5 -d 2 -p $(pidof fr.roadly)` on the home screen, stationary |
| Battery drain, 30 min guidance, screen on and off | needs a drive or a long simulation on battery | `adb shell dumpsys batterystats --reset`, run, `adb shell dumpsys batterystats --charged fr.roadly` |
| Overlay frame times | needs the navigation screen on the device | `adb shell dumpsys gfxinfo fr.roadly framestats` (overlay only; the map is a separate surface) |
| Recomposition counts | needs Android Studio attached | Layout Inspector |
| Memory over a long trip | needs a long run | `adb shell dumpsys meminfo fr.roadly` every 10 minutes |
| Rendering when the map is not visible | not observed | `RoadlyFps` log with another screen on top |
| Background behaviour on battery | the only device session ran on USB power | repeat the screen-off test unplugged |

## Known costs left as they are (code reading, unmeasured)

- The guidance notification is rewritten every 50–100 m (its text contains the distance).
- The whole navigation overlay recomposes on every fix (the UI state carries the progress).
- Three preference reads per fix in `NavigationSession`.

None is changed without a measurement.
