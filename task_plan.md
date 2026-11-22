# Roadly 1.0 — P0/P1 execution plan

Approved 2026-11-22. Item IDs come from the Phase 1 consolidated audit of the same day.
Worktree: `GPS-integration` only (branch `integration/phase5`, base `a83cfb1`). The historical `GPS` folder is never modified.
Last update: 2026-11-23, end of the deployment and open-source preparation phase (section "Deployment and open-source preparation"). Local commits only; nothing pushed, published, signed with a release key or installed.

## Strict operational restrictions

- Work only in `GPS-integration`.
- No phone interaction, no ADB install/uninstall/clear/permission change, no DHU, no road test.
- No release build installation, no release key selection, no signer pinning, no signing-key migration.
- No push, no merge to main, no publication, no release, no key generation, no repository made public. Local commits are authorised (owner, 2026-11-23: "Organize changes into meaningful commits").
- No implementation of the deferred decisions. No unrelated refactoring.
- Existing user data and Room migration compatibility are preserved.
- A test is reported as passed only if it actually ran. A failure is not called unrelated without reading its stack trace.
- Stop and ask before anything outside this scope.

## Verification vocabulary

- VERIFIED: diff read by the main agent and tests re-run by the main agent.
- TESTED ON JVM ONLY: logic verified as above; the Android-side part has never run on a device.
- NOT EXECUTED: cannot run here (GitHub workflow).

## P0

| Item | Status | Verification |
|---|---|---|
| REL-1 version scheme | DONE. The 1.0 switch is one property (`roadly.versionBase`) the owner sets at release time | VERIFIED locally (`:app:printRoadlyVersion`, 7 cases; guard logic simulated, 8 cases). CI guard step NOT EXECUTED |
| VAL-4 BAN endpoint | DONE (one request). Host answers 200 but is past its sunset and points to `https://data.geopf.fr/geocodage/`. Default since switched to `https://data.geopf.fr/geocodage` (a saved legacy URL follows the new default), with a request gate: 1 request/s, back-off after 429/5xx | Endpoint check: VERIFIED (one request). Switch and gate: tests re-run (pass, 21 in core:geocoding); diff not re-read by the main agent; the new host has NOT been called from this session |
| NAV-6 / NAV-9 preferences errors | DONE | VERIFIED (full run) |
| TST-9 radar tests in CI, REL-3 ref guard | DONE | YAML parsed; NOT EXECUTED on GitHub |
| SEC-2 workflow hardening | DONE | YAML parsed; NOT EXECUTED on GitHub; local signing-path trial still to do |

## P1

| Item | Status | Verification |
|---|---|---|
| NAV-2 monotonic time base | DONE | Tests re-run by main agent (pass). Diff NOT yet reviewed line by line by the main agent |
| NAV-3 coarse fix ignored (> 100 m) | DONE in guidance, in free drive and as the origin of an automatic resume | Guidance: same as NAV-2. Free drive and resume: VERIFIED (2 new tests) |
| NAV-4 degraded off-route / arrival rule | DONE | same as NAV-2. Thresholds are reasoned, not measured |
| NAV-5 reroute preempts a background request | DONE | same as NAV-2 |
| NAV-1 + TST-1 | DONE: sticky service, one resume attempt per service creation (`GuidanceRestorer`), trip checkpoint (`TripCheckpoint`), 20 service lifecycle tests | Tests re-run (pass) after two test repairs (see 2026-11-23 log). Production diff read by an independent reviewer, not line by line by the main agent. TESTED ON JVM ONLY: whether a phone recreates the service and gives it the position is unknown |
| TST-6 schema export + migration chain | DONE | VERIFIED: six historic schemas compared column by column with git history by the main agent; 10 tests re-run; `7.json` staged in git |
| TST-7 DAO tests | DONE | VERIFIED (14 tests re-run) |
| REL-2 + SEC-1 certificate check before install | DONE, no certificate pinned | VERIFIED for the sequence (a different signer never reaches the installer). TESTED ON JVM ONLY |
| SEC-6 host allow-list + size caps | DONE | VERIFIED (code read, tests re-run). TESTED ON JVM ONLY |
| SEC-7 APK hashed while copied to the install session | DONE | Code read. The Android copy itself has no automated test |
| Data export / import | DONE | VERIFIED (codec and repository tests re-run). File pickers never run on a device |
| TST-4 Valhalla failure tests | DONE (14 tests) + one defect fixed (route without maneuvers -> `NoResult`) | VERIFIED |
| VAL-2 fewer route requests | DONE: direct start 3 -> 1, resume trip 3 -> 1, junction topology on the public host -> 0 | VERIFIED |
| VAL-3 Overpass speed bumps | DONE: one in flight, 10 s interval, cache per packet of ways, back-off; a 429 costs 1 request | VERIFIED |
| AA-3, AA-5, SEC-5, TST-5 | DONE (AA-5: not a defect, guard test kept) | Tests re-run by main agent (68 pass). Diff NOT yet reviewed by the main agent |
| TST-2 voice failure paths | DONE: `AndroidSpeechServiceTest`, 9 tests (init error, no French data, engine fallback, focus denied, phone call, dead engine, engine that never calls back). Focus lost mid-utterance NOT covered | VERIFIED (written and run by the main agent). No production change |
| TST-3 `FusedLocationRepository` | DONE: `FusedLocationRepositoryTest`, 11 tests with a fake fused client (permission, request per mode, release after 5 s, fix conversion, replay). Mock-location detection NOT added (feature, not a test) | VERIFIED |
| SEC-9 log without the error object | DONE (`GuidanceTrace`, `GuidanceTraceTest`) | Tests re-run (pass). Diff not re-read by the main agent |
| SEC-8 | NOT DONE on purpose: it changes which key signs debug builds (deferred signing decision) | — |

## Test results (main agent, 2026-11-23, after the resume)

Command: `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test :core:scooter:test :core:radar:test :app:compileDebugKotlin :app:assembleRelease --continue`: BUILD SUCCESSFUL.

| Module | Run | Skipped | Failed |
|---|---|---|---|
| app | 14 | 0 | 0 |
| core:common | 30 | 0 | 0 |
| core:data | 243 | 2 | 0 |
| core:designsystem | 8 | 0 | 0 |
| core:geocoding | 21 | 0 | 0 |
| core:location | 24 | 0 | 0 |
| core:map | 104 | 0 | 0 |
| core:model | 21 | 0 | 0 |
| core:navigation | 511 | 4 | 0 |
| core:radar | 40 | 0 | 0 |
| core:routing | 101 | 2 | 0 |
| core:scooter | 62 | 0 | 0 |
| feature:car | 69 | 0 | 0 |
| feature:home | 12 | 0 | 0 |
| feature:navigation | 111 | 0 | 0 |
| feature:onboarding | 5 | 0 | 0 |
| feature:route | 23 | 0 | 0 |
| feature:search | 12 | 0 | 0 |
| feature:settings | 25 | 0 | 0 |
| feature:trips | 2 | 0 | 0 |
| feature:voice | 12 | 0 | 0 |
| Total | 1450 | 8 | 0 |

- Counts read from the JUnit XML reports of this run (some tasks came from the build cache).
- `:app:assembleRelease` produced `app-release-unsigned.apk` only. Nothing signed, nothing installed.
- The 8 skipped tests are the "live" tests that call real servers; no public server was contacted.
- `CarHomeStripTest` (the one failure of the 2026-11-22 checkpoint) passes in this run.

## Log of the 2026-11-23 resume

State found: 106 changed files, everything compiled, 12 tests failing (work cut at 13:28 on 2026-11-22 in the middle of the NAV-1 / TST batch).

- `NavigationServiceTest`, 10 failures: `UnsupportedOperationException` from `UnconfinedTestDispatcher` under `limitedParallelism` (the sessions confine their work). The test now runs the sessions on Robolectric's main dispatcher. 1 more failure: the test expected the first maneuver in the notification; the notification shows the next one. Assertion corrected. Test-only changes.
- `PackageApkInstallerTest`, 2 failures: Robolectric 4.18.1 succeeds the session inside `commit` and never adds `EXTRA_STATUS`. The tests now send the verdict through the status receiver given to `commit`, with a fill-in intent, as Android does (this also proves `FLAG_MUTABLE`). Test-only changes.
- Independent review of the navigation and Android Auto production diff (sub-agent, static read). Fixed from it, each with a test:
  - `RoomTripRecorder.end` deleted the checkpoint before saving and let a database error reach the guidance loop (arrival overwritten by `Failed`). Now the checkpoint is deleted only after a successful save and the error is contained. Test observed failing first.
  - `GuidanceRestorer` accepted a coarse fix as the origin of the resumed route. Now refused (NAV-3).
- NAV-3 applied to free drive (`FreeDrive.Drive.onFix`).
- The two NAV-3 tests were written with their fixes; they were not observed failing first.

## Deployment and open-source preparation (2026-11-23)

Owner request of 2026-11-23: final GitHub deployment and in-app update phase (A to F) and public open-source strategy. Boundaries kept: no push, no publication, no release, no key generated or selected, no signer pinned, no device touched, repository still private.

| Area | Done | Verification |
|---|---|---|
| In-app updater: download cancellation | `AppUpdateController.cancelDownload`, "Annuler" button; a cancelled or interrupted download returns to "available" and leaves no partial file (before: the state stayed "downloading" for the life of the process) | VERIFIED: 3 new tests, 20 in `AppUpdateControllerTest`. TESTED ON JVM ONLY |
| Release workflow | `sign` job in the protected environment `release`; full `apksigner verify`; refuses a debug certificate, a second signer, a missing or different `ROADLY_SIGNER_SHA256`; `SHA256SUMS.txt`; verification block in the release notes; `signerSha256` in `update.json`; manual run may leave the version empty | YAML parsed. Signing step run locally with the Android debug key: sign and verify pass, debug key refused (exit 1). Other refusals and the run on GitHub NOT EXECUTED |
| CI for contributions | `.github/workflows/ci.yml`: `pull_request` and non-main pushes, read-only token, no secret; tests, unsigned release build, licence check, forbidden key files | YAML parsed. NOT EXECUTED on GitHub |
| Licence audit | `tools/licenses/third_party_licenses.py` (+ Gradle init script) writes `THIRD_PARTY_LICENSES.md`: 186 artifacts, 178 Apache-2.0, 2 BSD-2-Clause, 1 BSD-3-Clause, 1 MIT, 4 Android SDK License (Google Play services, not free) | VERIFIED: script run, `--check` passes. Same totals as the 2026-11-22 audit |
| Licence | `LICENSE` replaced by the Apache 2.0 text (taken from 23 identical copies in cached library jars); `NOTICE` added; README, DEVELOPMENT, COMPARATIF aligned | Previous licence was GPL-3.0. Owner confirmation required before publication (`docs/PUBLICATION.md` section 1) |
| Secrets and personal data | Pattern scan of the working tree and of the whole history (all refs): no real secret, no key file ever tracked. Windows user name and first name removed from 9 documents | Still in history: those paths and one personal e-mail address in commit metadata. Images not examined |
| Public documents | `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `PRIVACY.md`, `docs/SERVICES.md`, `docs/SIGNATURE.md`, `docs/MIGRATION-HONOR.md`, `docs/PUBLICATION.md`; issue and pull-request templates; `CODEOWNERS`; README (project status, verification, licence), `SECURITY.md`, `docs/UPDATES.md` updated | Written from the code and the existing audits. Privacy claims checked against the manifest and the code |

Final run after these changes: same command as above, BUILD SUCCESSFUL, 1453 tests run, 8 skipped, 0 failed; licence check passes.

Commits: the changes are grouped by theme in local commits on `integration/phase5`. Only the final tree was built and tested; intermediate commits were not built one by one.

### Phase F status (release chain end to end)

| Step | Status |
|---|---|
| Clean checkout builds | see "Clean clone" below |
| CI passes | NOT EXECUTED (nothing pushed) |
| Release APK signed and verified | not possible without the release key |
| APK published through GitHub Releases | not done (not authorised) |
| Roadly detects the release, downloads and verifies it | JVM tests only |
| Real install and data preservation on the HONOR | not done (not authorised) |

### Decisions waiting for the owner (in order, detail in `docs/PUBLICATION.md` section 8)

1. Licence: confirm Apache 2.0 (sole copyright holder, giving up copyleft).
2. History: publish as is, start a new public repository from one commit (recommended), or rewrite.
3. Public content: review `docs/captures` and test traces, remove internal documents (`docs/superpowers`, competitor analyses).
4. Name: prior-art search for "Roadly".
5. Valhalla hosting (public host is not meant for a distributed app).
6. Release key: create, back up, GitHub environment and variable (`docs/SIGNATURE.md`).
7. GitHub settings (`docs/PUBLICATION.md` section 3).
8. HONOR migration (`docs/MIGRATION-HONOR.md`): the phone is debug-signed and the workflow now refuses the debug key, so it cannot receive a release until it is migrated.
9. Pin the signer in the app, then open the repository.

Consequence to know: once this branch reaches `main`, a push to `main` no longer publishes anything until decisions 6 and 7 are done.

## Remaining work

1. Local `apksigner` trial of the CI signing path with a throw-away key (not done: it creates a key, left to the owner's go-ahead).
2. Docs: `RELEASE-CHECKLIST.md` statuses for NAV-1, TST-1, TST-2, TST-3.
3. Everything Android-side on a real phone (see residual risks).

## Review findings left open (2026-11-23, none fixed, owner's call)

| Finding | Status |
|---|---|
| A phone that only ever gives fixes coarser than 100 m (approximate-location permission) gets a guidance that never moves and shows "GPS lost" | PLAUSIBLE. Needs a product decision (message or refusal at start) |
| `ACTION_STOP` during a resume attempt does not cancel the attempt explicitly; it is cancelled in `onDestroy` | PLAUSIBLE. `NavigationServiceTest` covers the case with `destroy()` called right after |
| `GuidanceRestorer.MAX_AGE_MS` comment says the age counts from the last route change; it counts from the start only (a trip killed after 6 h is not resumed) | CONFIRMED by the reviewer, comment vs behaviour |
| A resume that gives up (no position, route failure) still uses one of the 3 resumes per hour | By design; noted |
| `tripRecorder.end` launched on a failure path is not ordered against an immediate retry | PLAUSIBLE, unlikely |
| `TripRecorder` reads and writes the checkpoint file under its lock, sometimes on the main thread | Small files; no change |

## Open points inside the approved scope (not yet done)

| Point | Recommended handling |
|---|---|
| VAL-3: `ScooterRouting.kt` Overpass queries (way tags, POIs) have no limiter or back-off | DONE after the checkpoint (`OverpassGate.kt`, `OverpassGateTest`, `ScooterRoutingTest`). Tests re-run (pass); diff not re-read by the main agent |
| VAL-2: `RouteViewModel.kt` requests one route profile (`trace_attributes`) per displayed variant, up to 3 per search | DONE after the checkpoint: only the active variant's profile is requested, another one when it is selected (`RouteViewModelTest`). Tests re-run (pass); diff read by the main agent |
| NAV-2 leftover: `TripRecorder` durations still use phone time (`ValhallaTraceSpeedLimitProvider` is done) | A clock change mid-drive distorts one stored trip duration. Not done |
| NAV-5: a reroute that gets a 429 still waits 2 s + 8 s retries, which can exceed the 10 s reroute timeout | Not done |
| SEC-5: a `geo:` intent from Android Auto still starts guidance without confirmation | Product decision (no confirmation screen exists) |
| VAL-4: BAN default URL | Switched after the checkpoint (see P0 row). Still to do: one real `/search/` and one `/reverse/` on the new host |

## Known residual risks

- Direct start and resume no longer fall back to another route variant when the fastest fails.
- Speed-bump cache: two parallel streets read in the same packet of ways cannot be told apart (already true within one route before).
- Certificate check: compares with the installed app only; nothing is pinned; unreadable certificates are handed to Android.
- All Android-side code of the updater, the export and the car permission guard has never run on a device.
- The reworked GitHub workflow has never run.
- `core/data` tests now need Robolectric's SDK 34 image on CI.
- `LocationFix`, `NavigationClock`, `ApkInstaller` and `OverpassSpeedBumpProvider` signatures changed; all callers compile.

## Deferred decisions (document only: `docs/ROADLY-1.0/DECISIONS.md`)

- AA-1 distribution (private GitHub vs Google Play); SEC-3 and `onAutoDriveEnabled` depend on it.
- VAL-1 self-hosted Valhalla vs public host with `X-Client-Id`.
- Release signing key, signer pinning (`SignerCheck.PINNED_SIGNERS`) and migration of the debug-signed HONOR; SEC-8.
- REL-5 licence and notices.

## Log of earlier steps

### VAL-4 request (2026-11-22)

`GET https://api-adresse.data.gouv.fr/search/?q=mairie+paris&limit=1`, generic User-Agent, redirects not followed: HTTP 200, GeoJSON in the parsed format; headers `deprecation` and `sunset: Sat, 31 Jan 2026 22:59:59 GMT`, `x-api-deprecated: true`, `x-api-new-host: https://data.geopf.fr/geocodage/`, `x-ratelimit-limit-second: 1`.

### REL-1 checks

`:app:printRoadlyVersion -q`: no property -> `code=225 name=0.1.225`; `versionBase=225` -> `1.0.0`; `=220` -> `1.0.5`; `=300` -> build error; `=abc` -> build error; `versionBase=220 versionCode=231` -> `1.0.11`; `versionCode=225 versionName=1.2.3` -> `1.2.3`.

### Checkpoint fixes by the main agent (VAL-2 / VAL-3 scope)

- `HomeViewModel` resume: `alternatives.fastest` (3 -> 1 request), asserted in `HomeViewModelTest`.
- `ApiClient.postForm(retryRateLimited = false)` for speed bumps: a 429 costs 1 request instead of 3, asserted in `RequestBudgetTest`.
- `SpeedBumps.kt`: bumps kept per packet of ways; a route only gets bumps from packets containing one of its ways; new test in `RequestBudgetTest`.
- These three tests were written together with the fixes; they were not observed failing first.
