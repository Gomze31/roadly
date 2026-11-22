# Roadly 1.0 — release checklist

Audit date: 2026-11-22. Tree audited: `GPS-integration`, branch `integration/phase5` (215 commits; `origin/main` = 210 commits).
Method: reading of code and build files, `git ls-files` / `git grep`. **No Gradle build, no device, no adb, nothing published.**
The working tree was being edited by another session during the audit (15 modified files in `core/map`, `core/navigation`,
`feature/car`, `feature/settings`). Line numbers refer to the working tree as read; only the simulation change (§ 6) was re-read.

Status vocabulary used everywhere below:

| Status | Meaning |
|---|---|
| PASS (code reading) | The property was read in source or build files. Not proven on a built APK. |
| PASS (git) | Verified by a git command on the tracked tree. |
| FAIL | The property is false as read. |
| INCONCLUSIVE | Depends on something not visible from the repository (GitHub secrets, device state). |
| NOT TESTED | Needs a build, a device or a network call. |

Companion documents (not contradicted here): `docs/ROADLY-1.0.md` (roadmap, P0 list), `docs/UPDATES.md` (signing and updater),
`docs/REPARTITION-COMMITS.md` (commit split), `docs/PROTOCOLE-TERRAIN-HONOR.md` (field protocol), `docs/VALHALLA-HEBERGEMENT.md` § 8.

---

## 1. Release blockers and non-blocking items

### 1.1 RELEASE BLOCKERS

| # | Blocker | Evidence | Status |
|---|---|---|---|
| B1 | Never driven: no phone, road or DHU validation of the guidance work of phases 1–5. `ROADLY-1.0.md` P0 still lists the HONOR pass, instrumented tests and updater check as "À faire". | `docs/ROADLY-1.0.md:95-97`, `docs/GUIDAGE-INTELLIGENT.md` § 11.6 ("Téléphone, route, DHU, mesures : non faits") | NOT TESTED |
| B2 | Test-only "Simulation de trajet" toggle is shown to every user in release at the committed HEAD (`813d5a0`). When on, guidance is fed simulated or replayed positions instead of the GPS, and the setting persists. A fix appeared in the working tree during this audit (written by another session, uncommitted, not built): see § 6. | HEAD: `git show HEAD:feature/settings/.../NavigationGroup.kt` lines 79-87, `DriveSimulation.kt` `start()`; `core/data/.../UserPreferencesRepository.kt:183,251` | FAIL at HEAD; fix PASS (code reading), NOT TESTED |
| B3 | Version name scheme is hard-coded to `0.1.<count>` in two places. Tagging `v1.0.0` on a commit already auto-released produces a duplicate `versionCode`, and the next push to `main` would publish `0.1.N` again after `1.0.0`. | `app/build.gradle.kts:18`, `.github/workflows/release.yml:57` | FAIL |
| B4 | A local `assembleRelease` without a configured keystore is silently signed with the debug key. Only CI refuses to build unsigned. | `app/build.gradle.kts:50`; CI guard `release.yml:71-74` | FAIL (local) / PASS (code reading) for CI |
| B5 | Public FOSSGIS Valhalla URL is the hard-coded default. The project's own compliance note says wide distribution is not compatible with the FOSSGIS terms while it stays; `X-Client-Id` and the application announcement requested by Valhalla are not done. | `core/data/.../config/ProviderConfig.kt:23`, `docs/VALHALLA-HEBERGEMENT.md:393-397` | FAIL for a public release; acceptable for a private one |
| B6 | Which key signs published builds, and whether the installed base can update in place, is not knowable from the repository. The HONOR runs a debug-signed 0.1.215. | `docs/UPDATES.md:19-29`, `docs/PROTOCOLE-TERRAIN-HONOR.md:32-35` | INCONCLUSIVE |
| B7 | Release APK never built or inspected for this tree (signature, `versionCode`, merged manifest, `debuggable`). | — | NOT TESTED |

B5 blocks a **public** 1.0 only. If 1.0 stays a private GitHub release for known devices, it moves to § 1.2.

### 1.2 Non-blocking (decide, then ship)

| # | Item | Evidence | Status |
|---|---|---|---|
| N1 | R8 is off (`isMinifyEnabled = false`); no ProGuard or consumer rules file exists in any module. Shipping 1.0 unminified is the low-risk choice for a GPL app; cost is APK size. | `app/build.gradle.kts:49`; no `*.pro` / `consumer-rules*` on disk | PASS (code reading) |
| N2 | Guidance journal `RoadlyGuidage` is written with `Log.i` in release for every manoeuvre change (manoeuvre type, distance, speed, GPS accuracy; no coordinates, no address). | `core/navigation/.../NavigationService.kt:62`, `GuidanceTrace.kt:28-53` | PASS (code reading) — content reviewed |
| N3 | `validation/` (field-test logs, may hold GPS traces) is untracked and **not** in `.gitignore`: one `git add -A` would commit it. | `git status`, `.gitignore` | FAIL (hygiene) |
| N4 | No third-party notices file for library dependencies (MapLibre BSD-2, OkHttp / AndroidX / Hilt / kotlinx Apache-2.0, Play services location proprietary). Fonts have their OFL files; data attribution is in the README and in Réglages › À propos. | `LICENSE`, `core/designsystem/LICENSE-Inter-OFL.txt`, `core/designsystem/licenses/Nunito-OFL.txt`, `feature/settings/.../strings.xml:189` | FAIL (missing file) |
| N5 | `REQUEST_INSTALL_PACKAGES` + `UPDATE_PACKAGES_WITHOUT_USER_ACTION` are justified by the GitHub self-updater but are not acceptable on Google Play; Android Auto in a real car requires a Play install. The two goals conflict. | `core/data/src/main/AndroidManifest.xml:4,6`, `docs/ANDROID-AUTO.md:89,147` | PASS (code reading) for GitHub distribution |
| N6 | Room: `exportSchema = false`, no schema history in the repo; migrations 1→7 exist, no destructive fallback. Instrumented migration tests not run. | `core/data/.../db/RoadlyDatabase.kt:10-11`, `DatabaseModule.kt:22` | NOT TESTED |
| N7 | No user data export: `allowBackup="false"` and extraction rules exclude everything, so any uninstall (key change, rollback) loses favourites, trips and settings. | `app/src/main/AndroidManifest.xml:9-10`, `app/src/main/res/xml/data_extraction_rules.xml` | PASS (code reading) — by design |
| N8 | `versionCode` falls back to `1` when `git` is unavailable at build time (source zip, missing git). | `app/build.gradle.kts:14-16` | PASS (code reading) |
| N9 | Debug and release share `applicationId` `fr.roadly` (no suffix): they cannot coexist on one device. | `app/build.gradle.kts:32,47-54` | PASS (code reading) |
| N10 | Photon and OpenFreeMap public instances are fair-use without SLA; Overpass public instance was saturated on 2026-11-05. | `docs/API-PROVIDERS.md` § 1, § 4; `docs/ROADLY-1.0.md:133` | INCONCLUSIVE (terms to re-read before wide release) |
| N11 | Open defects listed for Android Auto and the UI are known and documented, not fixed. | `docs/PROTOCOLE-TERRAIN-HONOR.md` § 8, `docs/GUIDAGE-INTELLIGENT.md` § 10.6, § 11.4 | NOT TESTED |

---

## 2. Versioning strategy

### 2.1 Facts

| Fact | Where | Status |
|---|---|---|
| `versionCode` = `git rev-list --count HEAD`, overridable by `-Proadly.versionCode` | `app/build.gradle.kts:14-17` | PASS (code reading) |
| `versionName` = `0.1.<count>`, overridable by `-Proadly.versionName` | `app/build.gradle.kts:18` | PASS (code reading) |
| CI always passes both properties; code = commit count; name = manual input, tag without `v`, or `0.1.<count>` on a push to `main` | `.github/workflows/release.yml:54-61,83` | PASS (code reading) |
| CI checks out full history (`fetch-depth: 0`) | `release.yml:33` | PASS (code reading) |
| Pushes that touch only `**.md` or `docs/**` publish nothing | `release.yml:11` | PASS (code reading) |
| Updater reads `releases/latest` (drafts and pre-releases never offered), then `update.json` | `core/common/.../update/AppUpdate.kt:23,36-46`, `docs/UPDATES.md:104` | PASS (code reading) |
| Updater compares **`versionCode` only**, strictly greater; `versionName` is display only | `AppUpdate.kt:77` | PASS (code reading) |
| Downloaded APK is checked against the SHA-256 of `update.json` before install | `core/data/.../update/AppUpdateController.kt:156-157` | PASS (code reading) |
| Installed version comes from `BuildConfig.VERSION_CODE/NAME` | `app/.../AppModule.kt:13` | PASS (code reading) |
| Updater end to end on a device for this tree | — | NOT TESTED |

### 2.2 What breaks today if someone just tags `v1.0.0`

1. A push to `main` publishes `v0.1.N` with `versionCode` N. Tagging that same commit `v1.0.0` builds `versionCode` N again:
   devices already on `0.1.N` are told "up to date" (`AppUpdate.kt:77`) and never receive 1.0.0.
2. The next code push to `main` publishes `v0.1.(N+1)` and marks it latest: the name goes backwards after 1.0.0.

### 2.3 Recommended strategy (minimal, two one-line edits, to be made by the owner)

Keep `versionCode` = commit count (already monotonic on a linear `main`). Change only the **name** scheme:
`versionName = 1.0.<count − base>`, where `base` is the commit count of the commit that makes the switch.

| Step | Change |
|---|---|
| 1 | `gradle.properties`: add `roadly.versionBase=<count of the switch commit>` (run `git rev-list --count HEAD` on `main` and add 1). |
| 2 | `app/build.gradle.kts:18`: default name becomes `"1.0.${gitCommitCount - base}"`. |
| 3 | `.github/workflows/release.yml:57`: same formula for a push to `main`, reading `roadly.versionBase` from `gradle.properties`. |
| 4 | Make this the **last, direct** commit on `main` (not through a merge commit, so the count is predictable). The ordinary push pipeline then publishes `v1.0.0` with `versionCode` = base. The following pushes give `1.0.1`, `1.0.2`, … |

Properties: one release per commit, `versionCode` strictly increasing, updater unchanged, local and CI builds of one commit
still agree. Do **not** also push a `v1.0.0` tag by hand: the workflow creates it.

Zero-code fallback for the 1.0.0 release alone: land a final commit that touches only `*.md` (no automatic release), then run
*Actions › Release Android › Run workflow* with version `1.0.0`. The code is higher than any published one, so the updater offers
it. Steps 1–3 are still required before the next code push, otherwise it is named `0.1.N`.

Guards:

- [ ] Never rewrite or force-push `main` (the count must not decrease).
- [ ] The first 1.0 `versionCode` must be greater than 215 (the HONOR's local build) — true as soon as this branch is merged.
- [ ] `versionCode` shown by `aapt2 dump badging` equals `git rev-list --count HEAD` — NOT TESTED.

---

## 3. Release build configuration

| Check | Finding | Where | Status |
|---|---|---|---|
| Minification | `isMinifyEnabled = false` | `app/build.gradle.kts:49` | PASS (code reading) |
| Resource shrinking | not set (off) | `app/build.gradle.kts:47-54` | PASS (code reading) |
| ProGuard files | none referenced; no `proguard-rules.pro` or `consumer-rules.pro` in any module | whole tree | PASS (code reading) |
| Release signing | `roadly` config if `ROADLY_KEYSTORE` (env) or `roadly.keystore` (`local.properties`) is set, **else the debug config, silently** | `app/build.gradle.kts:25-26,40-45,50` | FAIL |
| This worktree | `local.properties` holds no `roadly.keystore*` entry: a release built here is debug-signed | key names only inspected | PASS (code reading) |
| Debug signing | uses the `roadly` key too when configured | `app/build.gradle.kts:53` | PASS (code reading) |
| CI signing | fails if `ROADLY_KEYSTORE_BASE64` is absent; key decoded after the tests; removed at the end | `release.yml:67-75,105-107` | PASS (code reading) |
| CI actions | pinned to commit hashes | `release.yml:31,35,44,94` | PASS (code reading) |
| `debuggable` | not set anywhere; release default is false | grep on `*.kts`, `*.xml` | PASS (code reading); merged manifest NOT TESTED |
| SDK levels | `minSdk 29`, `targetSdk 36`, `compileSdk 36`; `update.json` hard-codes `"minSdk":29` | `app/build.gradle.kts:30-34`, `release.yml:90` | PASS (code reading) |
| Output format | APK only (no AAB); fine for GitHub, not for Play | `release.yml:83` | PASS (code reading) |
| Unit tests gate the release | `testDebugUnitTest` + pure-Kotlin modules before signing | `release.yml:64` | PASS (code reading) |
| CI workflow has run for this tree | — | — | NOT TESTED |

Recommended fix for B4 (owner's decision, not applied): make `release` fail when no keystore is configured, e.g. only
fall back to debug when an explicit `-Proadly.allowDebugSignedRelease=true` is passed.

---

## 4. R8 / ProGuard

R8 does not run today, so nothing below can break 1.0 **unless** minification is switched on. Recommendation: ship 1.0.0 with
`isMinifyEnabled = false`; treat enabling R8 as a separate change with a full device regression.

### 4.1 Rule coverage if R8 is enabled

| Library | Version | Own rules in repo | Expected source of rules | Status |
|---|---|---|---|---|
| kotlinx.serialization | 1.8.1 | none | bundled in the library; 41 `@Serializable` classes, serializers referenced statically or through reified calls | NOT TESTED |
| Hilt | 2.58 | none | bundled / generated | NOT TESTED |
| Room | 2.8.5 | none | bundled | NOT TESTED |
| OkHttp | 4.12.0 | none | bundled | NOT TESTED |
| MapLibre Android | 11.13.5 | none | bundled (JNI) | NOT TESTED |
| Car App Library | 1.7.0 | none | bundled; service declared in manifest | NOT TESTED |
| Play services location | 21.3.0 | none | bundled | NOT TESTED |
| DataStore preferences | 1.1.7 | none | bundled | NOT TESTED |

### 4.2 Concrete risks visible in code

| Risk | Where | Effect under R8 |
|---|---|---|
| **Class name used as data.** `"error:" + r.error.javaClass.simpleName` is compared with the literal `"error:RateLimited"` | `feature/home/.../HomeViewModel.kt:150` ↔ `HomeScreen.kt:587`; `feature/navigation/.../NavigationViewModel.kt:198` ↔ `NavigationScreen.kt:765` | Obfuscation renames the class: the "rate limited" message silently becomes the generic error. Needs a keep rule or a real `when` on the type. |
| Enum names persisted and re-read with `valueOf` (Room columns, DataStore) | `core/data/.../repository/*.kt` (e.g. `FavoritesRepository.kt:40` unguarded; others in `runCatching`), `vigilance/RadarSync.kt:218` | Safe only with the default Android rules (`proguard-android-optimize.txt` keeps enum `values`/`valueOf`). Must be referenced explicitly. |
| Reified `decodeFromString<List<TollDto>>` / `<ParkedDto>` on private DTOs | `core/data/.../repository/DriveMemoryRepository.kt:98-110` | Relies on the library's bundled rules; verify that stored tolls and the parked car survive an update from an unminified build. |
| JSON field names of GitHub and `update.json` models | `core/common/.../update/AppUpdate.kt:14-46` | A broken serializer would disable the updater: the app could no longer receive a fix. Highest-impact failure mode. |
| Components named only in manifests | `NavigationService`, `RoadlyCarAppService`, `UpdateInstallReceiver`, `RoadlyWidgetProvider`, `RoadlyApp` | Kept by AAPT-generated rules; verify in `seeds.txt`. |
| No `@Keep` anywhere, no `Class.forName`, no `kotlin.reflect`, no own JNI | grep on all `src/main` | Nothing else found. |

### 4.3 Verification steps (to be run by someone else, Gradle idle, from `GPS-integration`)

Baseline, current configuration:

```
.\gradlew.bat :app:assembleRelease -Proadly.versionCode=<count> -Proadly.versionName=1.0.0 --stacktrace
.\gradlew.bat :app:signingReport
.\gradlew.bat :app:lintRelease
.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test :core:scooter:test
```

```
apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk
aapt2 dump badging app\build\outputs\apk\release\app-release.apk
aapt2 dump xmltree --file AndroidManifest.xml app\build\outputs\apk\release\app-release.apk
```

R8 trial (throw-away local edit of `app/build.gradle.kts`, not to be committed with the 1.0): set `isMinifyEnabled = true` and
`proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`, then:

```
.\gradlew.bat :app:minifyReleaseWithR8 --stacktrace
.\gradlew.bat :app:assembleRelease --stacktrace
```

Read `app\build\outputs\mapping\release\missing_rules.txt`, `usage.txt`, `seeds.txt`, `configuration.txt`.

| Verification | Expected | Status |
|---|---|---|
| Release APK builds | success | NOT TESTED |
| Certificate is the release key, not `CN=Android Debug` | `apksigner` output | NOT TESTED |
| `versionCode` / `versionName` as intended | `aapt2 dump badging` | NOT TESTED |
| No `android:debuggable="true"` in the merged manifest | `aapt2 dump xmltree` | NOT TESTED |
| `lintRelease` has no error | report | NOT TESTED |
| R8 trial: `missing_rules.txt` empty | file | NOT TESTED |
| R8 trial on device: search, route, guidance, report (rate-limit message), update check and install, Android Auto, widget, restart with existing data | manual | NOT TESTED |

---

## 5. Manifest and permission review

Manifests with content: `app`, `core/data`, `core/navigation`, `feature/car`, `feature/settings`. The twelve others are empty.

### 5.1 Permissions

| Permission | Declared in | Used by | Verdict | Status |
|---|---|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | `app/.../AndroidManifest.xml:3-4` | OkHttp, MapLibre | needed | PASS (code reading) |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | `app:5-6` | `MainActivity.kt`, `core/location/.../FusedLocationRepository.kt`, `feature/car/.../CarHomeScreen.kt` | needed | PASS (code reading) |
| `ACCESS_BACKGROUND_LOCATION` | not declared | background guidance relies on the foreground service | correct | PASS (code reading) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | `core/navigation:3-4` | `NavigationService` | needed | PASS (code reading) |
| `POST_NOTIFICATIONS` | `core/navigation:5` | `MainActivity.kt`, `OnboardingScreen.kt`, `NavigationNotifications.kt` | needed | PASS (code reading) |
| `REQUEST_INSTALL_PACKAGES` | `core/data:4` | `PackageApkInstaller.kt:30` (GitHub self-update) | justified for GitHub distribution; Play policy does not allow it for self-update | PASS (code reading) |
| `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | `core/data:6` | `PackageApkInstaller.kt:37`; silent only when Roadly installed the current version itself (Android 12+) | justified; same Play caveat | PASS (code reading) |
| `androidx.car.app.NAVIGATION_TEMPLATES`, `ACCESS_SURFACE` | `feature/car:4-5` | navigation templates, map surface | needed | PASS (code reading) |
| `RECORD_AUDIO`, Bluetooth, wake lock, phone state | not declared | voice input goes through the system recogniser intent (`core/designsystem/.../VoiceInput.kt`) | none needed | PASS (code reading) |

### 5.2 Components and flags

| Item | Finding | Where | Status |
|---|---|---|---|
| `MainActivity` | exported (launcher); also handles `geo:` (browsable) and `google.navigation:` from any app | `app:15-42` | PASS (code reading); input hardening covered by `SECURITY.md`, not re-audited |
| `RoadlyWidgetProvider` | exported, required for app widgets | `app:45-53` | PASS (code reading) |
| `NavigationService` | `exported="false"`, `foregroundServiceType="location"` | `core/navigation:13-16` | PASS (code reading) |
| `UpdateInstallReceiver` | `exported="false"`; mutable `PendingIntent` targets it explicitly | `core/data:8`, `PackageApkInstaller.kt:46-47` | PASS (code reading) |
| `RoadlyCarAppService` | exported (required), category `NAVIGATION`, `NAVIGATE` on `geo:` | `feature/car:18-31` | PASS (code reading) |
| Car host validation | all hosts allowed only when the APK is debuggable; otherwise the library's allow-list | `feature/car/.../RoadlyCarAppService.kt:63-67` | PASS (code reading) |
| `minCarApiLevel` | `1`; templates follow the negotiated level | `feature/car:14-16` | PASS (code reading); real car NOT TESTED |
| `automotive_app_desc` | `<uses name="template" />` | `feature/car/src/main/res/xml/automotive_app_desc.xml` | PASS (code reading) |
| `allowBackup` | `false` | `app:9` | PASS (code reading) |
| `dataExtractionRules` | cloud backup and device transfer exclude all five domains | `app/src/main/res/xml/data_extraction_rules.xml` | PASS (code reading) |
| Cleartext | no `usesCleartextTraffic`, no `networkSecurityConfig`: platform default (cleartext blocked at target 36). No `http://` endpoint in `src/main` (one XML namespace string only). A self-hosted server entered as `http://` in Réglages › Fournisseurs will be refused. | grep | PASS (code reading) |
| `debuggable` | not declared | all manifests | PASS (code reading) |
| `<queries>` | TTS service intent; package `com.google.android.projection.gearhead` | `core/navigation:7-11`, `feature/settings:4-6` | PASS (code reading) |
| Merged release manifest (libraries may add permissions) | — | — | NOT TESTED |

---

## 6. Debug-only code audit

| Item | Finding | Where | Reaches release? | Status |
|---|---|---|---|---|
| "Simulation de trajet (test)" toggle and speed selector — committed HEAD `813d5a0` | rendered unconditionally in Réglages › Navigation | `NavigationGroup.kt` at HEAD, lines 79-87 | **Yes** | FAIL |
| Simulation engine — committed HEAD | bound unconditionally; starts whenever the preference is true | `core/navigation/.../NavigationModule.kt:27`, `DriveSimulation.kt` at HEAD, lines 90-96 | **Yes** | FAIL |
| Same two items — working tree (uncommitted change seen during the audit, not written by it) | toggle and speed selector rendered only when the APK is debuggable; `start()` returns false in a non-debuggable build even if the preference was left on | `NavigationGroup.kt:79-91`, `DriveSimulation.kt:92,119-122` (`DriveSimulationGate`) | No, once committed | PASS (code reading); release APK NOT TESTED |
| `replay.gpx` reader | reads `Android/data/fr.roadly/files/replay.gpx` (≤ 20 MB) only after the checks above; the folder is not writable by other apps on Android 11+ | `DriveSimulation.kt` (`gpxTrack`) | behind the toggle | PASS (code reading) |
| `HttpLoggingInterceptor` (level BASIC: logs URLs, which carry coordinates and search text) | added only `if (BuildConfig.DEBUG)`; the library is still packaged | `core/data/.../network/NetworkModule.kt:36` | No | PASS (code reading) |
| `RoadlyFps` map frame-rate log | only when the APK is debuggable | `core/map/.../MapController.kt:158-162` | No | PASS (code reading) |
| Car "allow all hosts" | only when debuggable | `feature/car/.../RoadlyCarAppService.kt:64-65` | No | PASS (code reading) |
| `RoadlyGuidage` journal (`Log.i`) | one line per manoeuvre change: type, distance, mode, speed, accuracy, roundabout state. No coordinates, street names or tokens. A failed state logs the error object (`échec <error>`), whose text was not traced. | `NavigationService.kt:59-63`, `GuidanceTrace.kt:28-53` | **Yes** | PASS (code reading), error text INCONCLUSIVE |
| `RoadlySpeech` logs (`Log.d`/`Log.w`) | channel and priority of announcements, voice name; not the spoken text | `core/navigation/.../AndroidSpeechService.kt:57-414` | Yes | PASS (code reading) |
| Other logs | GL and foreground-service warnings with exceptions | `JunctionGlView.kt:81-121`, `ForegroundServiceController.kt:17-19`, `NavigationService.kt:44-47` | Yes | PASS (code reading) |
| GitHub token | AES-GCM with an Android Keystore key; sent only to the API host; never logged | `core/data/.../update/UpdateSettingsStore.kt:24-69`, `GitHubReleaseSource.kt:24,45` | — | PASS (code reading) |
| `println`, `printStackTrace`, Timber | none in `src/main` | grep | — | PASS (code reading) |
| Any of the above observed with `adb logcat` on a release build | — | — | — | NOT TESTED |

**Risk of the simulation toggle, plainly (committed HEAD):** an end user can switch it on from the normal settings screen. From
then on every guidance session is driven by invented positions along the route (or an old recorded trace) instead of the real
GPS, at up to 4× speed, until the user finds the toggle again — the preference survives restarts and updates. On the road this
means wrong instructions and a false arrival. The uncommitted working-tree change closes both the screen and the engine for
non-debuggable builds; the blocker is lifted only when that change is committed and a release APK is checked (toggle absent,
guidance follows the real GPS with the stored preference still `true`).

---

## 7. Secret audit

Method: `git ls-files` (760 tracked files), `git grep` on value-shaped patterns, `git log --all --diff-filter=A` on file names,
`git check-ignore`. No secret value was printed or copied.

| Check | Result | Status |
|---|---|---|
| Tracked keystores (`*.jks`, `*.keystore`, `*.p12`, `*.pem`) | none | PASS (git) |
| Keystore or `local.properties` ever committed (all branches) | none | PASS (git) |
| Tracked `local.properties`, `keystore.properties`, `.env`, `google-services.json`, APK/AAB, `build/` | none | PASS (git) |
| GitHub tokens (`ghp_`, `github_pat_`), Google keys (`AIza`), `Bearer <literal>`, private-key blocks | no match | PASS (git) |
| Literal assignments to `password` / `token` / `apiKey` / `secret` | no match in tracked files | PASS (git) |
| `.gitignore` covers `local.properties`, `*.jks`, `*.keystore`, `keystore.properties` | yes (`.gitignore:6,11-13`) | PASS (git) |
| `.gitignore` covers `validation/` | **no** — untracked today, one `git add -A` away | FAIL |
| Signing passwords in build files | read from environment or `local.properties` only | PASS (code reading) |
| Debug-keystore credentials written in a doc | `docs/UPDATES.md:21` states the standard, publicly known Android debug keystore alias and password. Not a secret in itself; it matters only if that key is chosen for published builds (see § 9). | PASS (git) |
| GitHub Actions secrets present and correct | not visible from the repository | INCONCLUSIVE |
| Third-party API keys compiled into the app | none found; providers are keyless, the optional AI key is the user's | PASS (code reading) |

Tracked files that should not be: **none found**.

---

## 8. Third-party terms and licences

| Subject | Finding | Source | Gate for 1.0 |
|---|---|---|---|
| FOSSGIS Valhalla (`valhalla1.openstreetmap.de`) | 1 request/s, identifying User-Agent, single server, no availability guarantee, hard-coding the URL discouraged, commercial use only if not substantial | `docs/VALHALLA-HEBERGEMENT.md:289-296,369` | — |
| Client-side compliance | process-wide 1 req/s limiter, cache, `Retry-After`, `Roadly/<version> (+<contact>)` | `docs/VALHALLA-HEBERGEMENT.md:371-381` | PASS (code reading); never run against the public server — NOT TESTED |
| Still open | default URL unchanged; `X-Client-Id` header and application announcement not done; User-Agent contact defaults to the repository URL, which is private | `docs/VALHALLA-HEBERGEMENT.md:393-397`, `core/data/build.gradle.kts:11`, `ProviderConfig.kt:23` | **Blocker for a public release** (B5) |
| Free drive queries the public server | at most one request per 20 s and 250 m, behind the limiter; left for the owner to arbitrate | `docs/PROTOCOLE-TERRAIN-HONOR.md:235` | decision |
| OpenFreeMap tiles | free, keyless, commercial use allowed, attribution must stay visible, no SLA | `docs/API-PROVIDERS.md` § 1 | attribution PASS (code reading) |
| Photon (Komoot) | public instance is fair-use and may block talkative clients | `docs/API-PROVIDERS.md` § 4 | re-read terms before wide release |
| BAN, data.gouv.fr, IGN Géoplateforme | Licence Ouverte 2.0, attribution in README and in-app | `README.md:168-176` | PASS (code reading) |
| Overpass | public instance, saturated on 2026-11-05 | `docs/ROADLY-1.0.md:133` | non-blocking |
| Project licence | GPL-3.0; `LICENSE` present (674 lines, GPLv3 text); README states it | `LICENSE`, `README.md:13,180-182` | PASS (git) |
| Fonts | OFL texts tracked | `core/designsystem/LICENSE-Inter-OFL.txt`, `core/designsystem/licenses/Nunito-OFL.txt` | PASS (git) |
| Third-party notices for code dependencies | no `NOTICE` / `THIRD-PARTY` file, no in-app library licence list | `git ls-files` | FAIL (N4) |
| GPL-3.0 vs Play services location (proprietary) | compatibility of distributing a GPL-3.0 APK that links a proprietary library was not assessed here | `gradle/libs.versions.toml:66` | INCONCLUSIVE — owner to decide |
| Repository visibility | docs describe the repository as private with a per-device token; a GPL binary given to third parties needs the source offered to them | `docs/UPDATES.md:61`, `docs/VALHALLA-HEBERGEMENT.md:393` | decision before a public release |

---

## 9. Signing keys, installation and rollback

### 9.1 Facts

| Fact | Source |
|---|---|
| Android installs an update only if the certificate matches and `versionCode` does not decrease | platform rule; `docs/UPDATES.md:97,103` |
| `docs/UPDATES.md` offers two keys for published builds: the PC's debug keystore (option A) or a dedicated key (option B) | `docs/UPDATES.md:19-29` |
| Which one is in the GitHub secrets | INCONCLUSIVE |
| The HONOR runs **0.1.215, debug-signed**, built from this branch | `docs/PROTOCOLE-TERRAIN-HONOR.md:32-35` |
| A published build cannot install over it: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. The HONOR must be uninstalled once, which erases favourites, history and settings (no backup, no export). | `docs/PROTOCOLE-TERRAIN-HONOR.md:34-35`, § 1.2 N7 |

The one exception to the last row is if the published key is the very same debug keystore file as the PC that built 0.1.215
(option A). Compare certificates before assuming either way:

```
apksigner verify --print-certs <published roadly-x.y.z.apk>
apksigner verify --print-certs app\build\outputs\apk\debug\app-debug.apk
```

A 1.0 for anyone beyond the owner should be signed with a dedicated, backed-up key (option B): the debug keystore's password is
public, and losing the file ends all updates.

### 9.2 Rollback

Android does not downgrade. There is no path that returns a device to an older `versionCode` while keeping its data.

| Situation | Procedure | Data |
|---|---|---|
| Bad release, some devices not yet updated | On GitHub, mark the bad release as pre-release (or delete it and its tag). `releases/latest` then returns the previous release; devices still on it see "up to date". | kept |
| Devices already on the bad release (preferred) | **Roll forward**: `git revert` the faulty commits on `main` and push. The pipeline publishes the old code under a new, higher `versionCode` (for example 1.0.1); the updater offers it normally. Keep the Room version at 7 or higher: there is no downgrade migration and no destructive fallback (`DatabaseModule.kt:22`). | kept |
| A device must run the exact previous APK | Uninstall Roadly, download the previous `roadly-<version>.apk` from its GitHub release, install it. Same signing key, so later updates work again. | **lost** |
| The signing key changed between two published versions | Same as above in both directions: uninstall, then install. No in-place path exists. | **lost** |
| HONOR back to the debug build after trying a published build | Uninstall the published build, `adb install` the debug APK (on explicit agreement, per the field protocol). | **lost** |

- [ ] Before publishing 1.0.0, note the tag and `versionCode` of the release it replaces (currently the latest `v0.1.N` on `origin/main`, N ≥ 210).
- [ ] Keep the previous release and its APK on GitHub; never delete it.
- [ ] Rollback rehearsed on a device — NOT TESTED.

---

## 10. Changelog draft for 1.0

Built from `git log --oneline origin/main -40`, the three local commits of `integration/phase5`, and
`docs/GUIDAGE-INTELLIGENT.md` § 10–12. Everything under "Guidance" is unreleased and has never run on a device.

### Navigation and guidance
- Route profile with real speed limits and overspeed alert; free drive (vigilance without a route); free-flow toll copilot; parked car and walking route back to it; trip identity card.
- Tolls, traffic lights and closed roads announced, closures avoided automatically; "at the lights" landmarks, sharp-bend and speed-bump voice alerts; most economical route; "no tolls" applied to the shortest variant too.
- Cheapest fuel stations along the route; motorway stations on the right side; electric car support (battery at arrival, fast chargers on the route).
- Bison Futé incidents; picture-in-picture guidance; low-emission zones (national base, Crit'Air verdict).
- 3D junction view (exits, forks, roundabouts); IGN aerial imagery on phone and Android Auto.

### Guidance (unreleased branch work, § 10–12)
- Intelligent guidance: driving-situation engine, live roundabout mode with exit countdown, progressive voice, real lane data, fork diagram.
- Position tracking: no more jump to a later passage of the route at the first fix (U-turn, loop); no false arrival across a central reservation or from an imprecise fix; no false off-route at a tunnel exit.
- Rerouting: back-off after failures (10, 10, 20, 40, 60 s), spoken "rerouting impossible, retrying", reroute priority and no cache, routing calls capped at 15 s, bounded `Retry-After`.
- Voice: "GPS signal lost" spoken once; speech engine recreated after an error; no announcement during a phone call or when audio focus is refused; quieter voice overall.
- Interface: uncertain position shown as such (faded distance, hidden horizon strip); cleaner roundabout banner; arrival screen cleaned; light-theme amber contrast 2.4:1 → 5.1:1; collapse button 48 dp.
- Public routing server: at most one request per second, 60 s response cache, identifiable User-Agent.
- Performance: off-route tracking cost 4.5 MB → 1 KB per fix; position marker no longer rewritten when stationary.

### Android Auto
- Roadly on Android Auto (templates, map surface, search, route preview), styled like the phone app; minimum car API level 1; diagnostic screen "Roadly does not appear in Android Auto".

### Interface, settings, voice
- New interface faithful to the reference screens; layout adapted to screen and font size; settings as a tree of sub-pages; built-in voice commands; route options from the search screen; home-screen widget and shortcuts.

### Radar and vigilance
- Radar Intelligence 2 (categories, direction, provenance, confidence), MapAtlas source, limit-drop warning, break reminder.

### Security and distribution
- Full security audit, hardened community service and release pipeline, external links reviewed; GitHub self-update with SHA-256 check; GPL-3.0 licence and open-source README.

### Known limitations to state in the release notes
- Guidance changes above not yet validated on the road.
- Android Auto in a real car requires an installation attributed to Google Play (`docs/ANDROID-AUTO.md:89-95`).
- Guidance is lost if the system kills the process in the background; it resumes only when the app is reopened.
- Six Android Auto defects and two layout defects are documented and open (`PROTOCOLE-TERRAIN-HONOR.md` § 8, `GUIDAGE-INTELLIGENT.md` § 11.4).

---

## 11. Release checklist

### Before the release commit
- [ ] B1 — Field protocol run on the HONOR (`docs/PROTOCOLE-TERRAIN-HONOR.md` § 4–5), defects triaged.
- [ ] B1 — `ROADLY-1.0.md` P0 rows closed: HONOR pass, instrumented tests (Room, Compose), scooter screens, GitHub update on device.
- [ ] B2 — Simulation gate (`DriveSimulationGate`, uncommitted in the working tree on 2026-11-22) committed; toggle verified absent from a release APK.
- [ ] B3 — Version name scheme switched (§ 2.3).
- [ ] B4 — Release build cannot be debug-signed by accident.
- [ ] B5 — Decision recorded: private 1.0 on the public Valhalla instance, or self-hosted server / announcement + `X-Client-Id` + reachable contact for a public 1.0.
- [ ] B6 — Signing key chosen (dedicated key recommended), backed up off the PC, GitHub secrets checked, certificate fingerprint written down.
- [ ] N3 — `validation/` added to `.gitignore`.
- [ ] N4 — Third-party notices file added; licence position on Play services location decided.
- [ ] Uncommitted work split and committed as planned in `docs/REPARTITION-COMMITS.md`.
- [ ] `docs/UPDATES.md` examples updated from `0.1.<count>` / `v0.2.0` to the 1.0 scheme.

### Build verification (§ 4.3 commands)
- [ ] Unit tests green (last recorded: 1054 tests, 0 failure — `GUIDAGE-INTELLIGENT.md` § 11.6; to be re-run on the release commit).
- [ ] `:app:assembleRelease` succeeds; `lintRelease` without error.
- [ ] `apksigner`: release certificate, not the debug one.
- [ ] `aapt2 dump badging`: `versionName 1.0.0`, `versionCode` = commit count, `sdkVersion 29`, `targetSdkVersion 36`.
- [ ] Merged manifest: not debuggable; no permission beyond § 5.1.

### Device verification (release-signed APK)
- [ ] Clean install; onboarding; location and notification prompts.
- [ ] Search, route, guidance with voice, background and screen off, arrival.
- [ ] Update path: install the previous published version, then receive 1.0.0 through Réglages › Mises à jour (data kept).
- [ ] Update from 1.0.0 to a 1.0.1 test release (name and code both increase).
- [ ] Android Auto on the DHU; real car if available.
- [ ] `adb logcat` during guidance: no coordinates, addresses or token in the logs.

### Publication
- [ ] Previous release tag and `versionCode` noted for rollback (§ 9.2).
- [ ] Push to `main`; watch the *Release Android* run; check the release holds `roadly-1.0.0.apk` and `update.json`.
- [ ] SHA-256 in `update.json` equals `sha256sum` of the downloaded APK.
- [ ] Release notes edited from § 10 (generated notes are commit titles only).
- [ ] HONOR: explicit agreement, uninstall the debug build (data loss accepted), install the published APK.

---

## 12. Not done by this audit

- No Gradle task, no APK, no device, no network request to any provider, no GitHub Actions run.
- GitHub repository settings and secrets were not inspected.
- `backend/` and `tools/` were not audited.
- Deep-link input handling (`geo:`, `google.navigation:`) was not re-audited; see `SECURITY.md`.
- Legal reading of provider terms is the project's own notes, not a fresh review of the providers' pages.
