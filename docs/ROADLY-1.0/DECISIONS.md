# Roadly 1.0 - postponed decisions: options and recommendations

Date: 2026-11-22. Scope: the four decision areas listed as postponed in `docs/suivi/task_plan.md` (AA-1, VAL-1, release signing key,
REL-5). This is a recommendation document. Nothing was implemented, built, installed, signed, committed or pushed to produce it.

Not legal advice. Sections 1 and 4 quote licence and policy texts and give an engineering reading of them. A decision with
legal weight (publishing under a given licence, accepting store terms) should be checked by someone qualified.

## 0. How to read this document

Each statement of fact carries one of these labels:

- **VERIFIED (source, 2026-11-22)**: observed that day, either in the worktree (file and line), in Gradle output, in a Maven
  POM downloaded that day, in a local read-only command, or on the cited web page fetched that day.
- **FROM REPO DOC**: taken from an existing document of the repository, not re-checked. The document is cited.
- **NOT VERIFIED**: recollection, inference or extrapolation. Do not treat it as established.

The worktree examined is the branch `integration/phase5` (225 commits).

Summary of recommendations:

| Area | Recommendation |
|---|---|
| 1. AA-1 distribution | Two product flavors. `github` stays the daily channel (updater kept). `play` (no updater, AAB) goes to the Play **internal testing track** only, to get Roadly into the real car. No production listing for 1.0. |
| 1a. SEC-3 | Remove the UnknownAuto / Shizuku guidance from the app. Keep one clearly labelled paragraph in `docs/ANDROID-AUTO.md`. |
| 1b. `onAutoDriveEnabled` | Not needed for GitHub or for the internal track. Mandatory before any Play review (closed, open, production). Implement it when the `play` flavor is created. |
| 2. VAL-1 routing host | Stay on the public FOSSGIS instance for 1.0 while Roadly runs only on the owner's devices, after adding `X-Client-Id` and a real contact. Self-host France before the first copy goes to anyone else. |
| 3. Signing key | Dedicated release key, kept outside the repository and backed up twice. Migrate the HONOR once, by export / uninstall / install / import, after AA-1 is decided. Back up the PC debug keystore today. |
| 4. REL-5 licence | Keep GPL-3.0, add a GPLv3 section 7 additional permission for the Google Play services client libraries, add a third-party notices file. Plan a `LocationManager` implementation after 1.0. |

---

## 1. AA-1 - distribution: private GitHub releases or Google Play

### 1.1 Facts

Current state of the code:

- **VERIFIED (worktree, 2026-11-22)** `core/data/src/main/AndroidManifest.xml` declares `REQUEST_INSTALL_PACKAGES` and
  `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, plus the non-exported `UpdateInstallReceiver`.
- **VERIFIED (worktree)** The self-updater lives in `core/data/src/main/java/fr/roadly/core/data/update/`
  (`AppUpdateController.kt`, `GitHubReleaseSource.kt`, `PackageApkInstaller.kt`, `UpdateModule.kt`, `UpdateSettingsStore.kt`).
- **VERIFIED (worktree)** `.github/workflows/release.yml` has three jobs: `build` (no secret, unsigned APK), `sign` (only job
  that sees the key; runs `zipalign` and `apksigner`, prints the signer DN and SHA-256), `publish` (uploads the APK and
  `update.json` to a GitHub release marked latest). It triggers on every push to `main` outside documentation paths.
- **VERIFIED (worktree)** `feature/car/src/main/java/fr/roadly/feature/car/RoadlyCarSession.kt` lines 44-50 install a
  `NavigationManagerCallback` that overrides `onStopNavigation` only. No file in the worktree contains `onAutoDriveEnabled`.
- **VERIFIED (worktree)** The app tells users to install through UnknownAuto with Shizuku:
  `feature/settings/.../AndroidAutoReadiness.kt` (lines 35, 40, 46-47, 53-60: the wording, the URL
  `https://github.com/GitMag/UnknownAuto/releases/latest`, the five steps), `AndroidAutoGroup.kt` (lines 96-97: two buttons,
  "Shizuku" and "UnknownAuto"), `UpdatesGroup.kt` (lines 49-50 and 79: when the installer is `com.android.vending`, the update
  screen tells the user to install the new version with UnknownAuto instead of the Roadly button). A unit test asserts the
  wording (`AndroidAutoReadinessTest.kt`).
- **FROM REPO DOC** `docs/ANDROID-AUTO.md` section 3 and 3 bis: on 2026-11-09 and 2026-11-14 Roadly, installed from an APK
  and then by its own updater, appeared on the Desktop Head Unit but not in the launcher of a real car (Ford SYNC 3, Android
  Auto 17.9), with developer mode and "unknown sources" enabled.

Google documentation:

- **VERIFIED (https://developer.android.com/training/cars/testing, 2026-11-22)** "To test your app in real vehicles, you must
  install it from a trusted source such as Google Play, with one exception detailed in Allow unknown sources. You can use
  Internal App Sharing or an Internal Test Track to distribute your app to devices without going through the Google Play
  review process." And: the unknown-sources developer option "applies to media, messaging notifications, and parked apps but
  doesn't apply to apps built using the Android for Cars App Library."
- **VERIFIED (https://developer.android.com/training/cars/apps/navigation, section "Simulate navigation", 2026-11-22)** "To
  verify your app's navigation functionality when you submit it to the Google Play Store, your app must implement the
  `NavigationManagerCallback.onAutoDriveEnabled` callback. When this callback is called, your app must simulate navigation to
  the chosen destination when the user begins navigation." Test command given on the page:
  `adb shell dumpsys activity service CAR_APP_SERVICE_NAME AUTO_DRIVE`.
- **VERIFIED (https://developer.android.com/docs/quality-guidelines/car-app-quality, 2026-11-22)** Navigation criteria, all
  in the "Tier 2 - Car optimized" table, which the page says apps "built for use while driving must meet ... to be accepted
  on the Google Play Store":
  - NF-1 "The app must provide turn-by-turn navigation directions."
  - NF-2 "The app draws only map content on the surface of the navigation templates. Text-based turn-by-turn directions,
    lane guidance, and estimated arrival time must be displayed on the relevant components of the navigation template."
  - NF-3 turn-by-turn directions must also trigger navigation notifications.
  - NF-4 next-turn information must be sent to the vehicle's cluster display (navigation metadata).
  - NF-5 no notifications, voice or cluster information while another navigation app is guiding.
  - NF-6 "The app must handle navigation requests from other apps."
  - NF-7 "The app must provide a 'test drive' mode that simulates driving."
  - DD-1 the navigation audio channel is for navigation instructions only. MR-1 light / dark map on request.
  - NF-9 (cluster rendering) is in the Tier 1 table, not required for acceptance.
- **VERIFIED (https://support.google.com/googleplay/android-developer/answer/9888379, 2026-11-22)** "An app distributed via
  Google Play may not modify, replace, or update itself using any method other than Google Play's update mechanism."
- **VERIFIED (https://support.google.com/googleplay/android-developer/answer/12085295, 2026-11-22)** `REQUEST_INSTALL_PACKAGES`
  is allowed on Play only when installing packages is the core purpose of the app; otherwise "you must remove it from your
  app's manifest".
- **VERIFIED (https://developer.android.com/guide/app-bundle, 2026-11-22)** "From August 2021, new apps are required to
  publish with the Android App Bundle on Google Play."
- **VERIFIED (https://support.google.com/googleplay/android-developer/answer/14151465, 2026-11-22)** Personal Play Console
  accounts created after 2023-11-13 must run a closed test with at least 12 testers opted in continuously for 14 days before
  they can apply for production access. Internal testing: requirement to access the track "None"; it can be used before app
  setup is complete.
- **VERIFIED (https://support.google.com/googleplay/android-developer/answer/9845334, 2026-11-22)** Internal testing
  distributes to "up to 100 testers".
- **VERIFIED (https://support.google.com/googleplay/android-developer/answer/6112435, 2026-11-22)** Play Console
  registration: "US$25 one-time registration fee"; Google "may require a valid government ID".
- **VERIFIED (https://developer.android.com/developer-verification and its FAQ, 2026-11-22)** Android developer verification:
  protections started 2026-09-30 for participating stores in Brazil, Indonesia, Singapore and Thailand; "Verification
  capability will be expanded globally for all apps on certified Android devices in 2027." The FAQ states that sideloading is
  not yet affected, that ADB installs never need registration, that "Unregistered apps can only be installed or updated when
  the advanced flow is enabled or by using ADB", that a free "limited distribution account" lets a hobbyist "share apps with
  up to 20 specific devices ... without ID verification", that the full account costs $25, and that apps using Play App
  Signing are claimed automatically. Package registration is tied to signing keys ("If you lose your signing key you won't be
  able to register your packages").

Consequence of the last point: "GitHub only" is not a zero-Google option beyond 2027. On a certified device, a self-updating
APK whose package name is not registered will need either the advanced flow enabled on each phone or a registered package
(limited distribution account or Play Console). The date for France is not published.

### 1.2 Options

| | A. GitHub only | B. Play only | C. Both, product flavors | D. Flavors + Play internal track only |
|---|---|---|---|---|
| What ships | signed APK + `update.json` on private releases (today) | AAB on Play, production listing | `github` flavor (APK, updater) and `play` flavor (AAB, no updater), both maintained and published | same two flavors; `play` uploaded to the internal testing track only |
| Real car | not shown (Google doc + observation 2026-11-09 / 14) unless the installer is spoofed (SEC-3) | shown | shown for Play installs | shown for internal-track installs (Google doc; not tested on the car) |
| Review | none | navigation review, NF-1 to NF-7, DD-1, MR-1; closed test 12 testers / 14 days first on a new personal account | same as B for the Play side | none (Google doc) |
| Code to change | none | remove updater, both permissions, the updates settings group; AAB; `onAutoDriveEnabled`; store listing, privacy policy, data safety form | flavor split of `core/data` update package and manifest, `feature/settings` updates group; AAB task; `onAutoDriveEnabled` | same flavor split; `onAutoDriveEnabled` not required by Google at this stage |
| Security | APK integrity rests on the owner's key and GitHub account; a fine-grained read-only token is stored on the phone; `UPDATE_PACKAGES_WITHOUT_USER_ACTION` allows silent updates | Play protect, Play App Signing, no install permission in the app | two artifacts to keep honest; the `play` one has the smaller attack surface | as C, with a much smaller audience |
| Legal / policy | no store policy; GPL obligations start only when a copy goes to someone else | Play Developer Distribution Agreement, policies above, public identity of the developer, review of a GPS app that warns about speed cameras (policy fit NOT VERIFIED) | both sets | Play agreement applies, review policies are not exercised yet |
| Maintenance | lowest today; developer verification registration needed from 2027 | one channel, but release speed depends on review; no instant hotfix | highest: two pipelines, two signing situations (section 3), two test passes | moderate: second artifact built from the same commit, uploaded by hand or by a later CI job |
| Cost | none | US$25 once | US$25 once | US$25 once |

Notes that apply to B, C and D:

- The `play` flavor must not contain the updater, `REQUEST_INSTALL_PACKAGES` or `UPDATE_PACKAGES_WITHOUT_USER_ACTION` (Play
  policy quoted above). A flavor-specific source set or a separate Gradle module that only the `github` flavor depends on
  keeps the permission out of the merged manifest. Verify with `aapt2 dump badging` on the built artifact.
- A Play install is signed by the Play app signing key, a GitHub install by the owner's key. Unless the owner gives Google a
  copy of his key, one phone cannot move from one channel to the other without an uninstall (section 3.4).
- `versionCode` is the commit count on both channels, so it stays monotonic on each.
- The GPL covers Play testers too: anyone who receives the app must be able to get the corresponding source. The repository
  is private today, so a source offer would have to be arranged (section 4.4).

### 1.3 Recommendation: D, then decide about production after 1.0

Reasons:

1. The only thing GitHub distribution cannot do is the real car, and Google documents the internal track as the supported
   way to do exactly that without review.
2. The owner keeps the fast loop (push to `main`, update on the phone) for everyday use and for the Desktop Head Unit.
3. Production on Play needs things that do not exist yet: 12 testers for 14 days, a test-drive mode, a store listing, a
   privacy policy, a self-hosted routing server (section 2: FOSSGIS terms rule out a widely distributed app on their host).
   None of this should gate 1.0.
4. A Play Console account also solves the 2027 developer verification question for the `fr.roadly` package.

Order of work once the owner agrees: create the flavors; build the `play` AAB; open the Play Console account; upload to the
internal track; install on the HONOR from the internal track link; check the car launcher. The HONOR's channel must be
chosen before its signing migration (section 3.5), because a channel change means an uninstall.

If the owner refuses any Google account: option A stays valid for the phone and the Desktop Head Unit, Roadly will not be in
the real car without the SEC-3 workaround, and the 2027 registration question remains open.

### 1.4 SEC-3 - the UnknownAuto / Shizuku guidance

What the guidance does, from the repository's own description (`docs/ANDROID-AUTO.md` section 3 bis, **FROM REPO DOC**): the
user starts Shizuku through wireless debugging, which gives a third-party app shell-level (ADB) privileges, then uses
UnknownAuto to install the Roadly APK with the installer recorded as `com.android.vending`. Android Auto's trusted-source
check is defeated on purpose.

| Option | Consequences |
|---|---|
| Keep as is | The app recommends, with buttons, two third-party tools it does not control. The UnknownAuto link points to `releases/latest`, an unpinned binary. Shell privileges granted through Shizuku are not limited to this one install. The update screen steers the user away from the verified in-app update toward that path. Incompatible with a `play` flavor: a Play build that explains how to fake a Play install would be an obvious policy problem (NOT VERIFIED against a specific policy clause). |
| Keep with a risk label | Same mechanics; the label must say: unsupported by Google, bypasses a safety check of Android Auto, needs wireless debugging and shell privileges for a third-party app, binary not audited by Roadly, may stop working with any Android Auto update. Should be `github` flavor only, behind an explicit "advanced" disclosure, without one-tap buttons. |
| Remove from the app (recommended) | Delete the steps, the two buttons and the UnknownAuto branch of the update screen; keep the neutral diagnostic ("installed outside Google Play: a real car will not list Roadly; use the Play internal track"). Update `AndroidAutoReadinessTest.kt`. Keep one paragraph in `docs/ANDROID-AUTO.md`, labelled as an unsupported workaround with the risks above. |

Recommendation: remove from the app. With option D the workaround has no remaining purpose. If the owner chooses option A,
the labelled variant is the fallback, never the unlabelled one.

Not verified today: the licence (MIT according to the repo doc), the maintenance state and the code of UnknownAuto; the
current behaviour of Shizuku on Android 17.

### 1.5 `onAutoDriveEnabled`

- GitHub only, Desktop Head Unit, internal track: not required by anything Google enforces. **VERIFIED** only to the extent
  that the testing page says the internal track skips the review.
- Any track that is reviewed: required. Two independent sources quoted in 1.1: the navigation guide ("must implement") and
  criterion NF-7.

Recommendation: implement it when the `play` flavor is created, not before. It is small: the callback sets a flag in the
session; when guidance starts with the flag set, positions come from a simulated run along the route. The worktree already
has `core/navigation/.../DriveSimulation.kt` and `core/location/.../LocationReplay.kt`, which look like the right base
(**NOT VERIFIED**: both files were located, not reviewed). Test with the `dumpsys ... AUTO_DRIVE` command on the Desktop
Head Unit.

### 1.6 Still to verify

- That an internal-track install really appears in the launcher of the owner's car (Google documents it; not tested).
- Whether a new personal Play Console account needs identity or device verification before the internal track can be used
  (the help page mentions "device verification requirements" for personal accounts; details not read).
- Whether Play review accepts Roadly's category mix (navigation plus speed-camera alerts) and a MapLibre-rendered map.
- How NF-4 is judged for an app that sends a `Trip` but has no cluster session (`docs/ANDROID-AUTO.md` line 149).
- The date developer verification reaches France, and whether a limited distribution account fits a self-updating APK.

---

## 2. VAL-1 - routing host: public FOSSGIS instance or self-hosted Valhalla

### 2.1 Facts

- **VERIFIED (worktree)** `core/data/src/main/java/fr/roadly/core/data/config/ProviderConfig.kt` line 23: the default is
  `valhallaBaseUrl = "https://valhalla1.openstreetmap.de"`. The user can override it in the provider settings.
- **VERIFIED (worktree)** No Kotlin file contains `X-Client-Id`. The User-Agent is `Roadly/<version> (+<contact>)`, the
  contact coming from the Gradle property `roadly.contact` (`core/data/build.gradle.kts`, `UserAgentInterceptor.kt`).
- **FROM REPO DOC** `docs/VALHALLA-HEBERGEMENT.md` section 8: a process-wide limiter keeps Valhalla traffic to
  `*.openstreetmap.de` at one request per second, one in flight; the three route alternatives are sent one after another;
  429 and 503 are handled; unit-tested, not verified against the real server.
- **FROM REPO DOC** `docs/API-PROVIDERS.md` section 6: fix-by-fix speed limits through `/trace_attributes` are never sent to
  `openstreetmap.de` hosts; that feature needs a self-hosted server. Self-hosting is described there as the production
  prerequisite.

FOSSGIS terms of use, **VERIFIED (https://fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen, fetched 2026-11-22;
the page serves a German and an English version)**. German wording quoted, with a translation:

- Scope: "die Routingserver des FOSSGIS e.V. (routing.openstreetmap.de, valhalla1.openstreetmap.de)".
- Purpose: "Ihr Hauptzweck ist, die Nutzung auf unserer Website sowie die Beitragenden zu OpenStreetMap bei ihrer Arbeit zu
  unterstuetzen." (main purpose: use on their website and support of OpenStreetMap contributors). "OpenStreetMap-Daten
  koennen von jedem frei und kostenlos genutzt werden. Unsere Services hingegen nicht."
- Routing servers: "Maximal eine Anfrage pro Sekunde." (at most one request per second).
- "Gueltiger HTTP-User-Agent, der die Anwendung identfiziert. Die User-Agents von Bibliotheken sind nicht ausreichend. Das
  Vortaeuschen einer anderen Anwendung fuehrt sicher zur Sperrung." (valid User-Agent identifying the application; library
  User-Agents are not enough; impersonating another application leads to a block).
- "Ausdruecklich empfohlen: Die URLs unserer Services sollten nicht in der App fest einprogrammiert werden." (explicitly
  recommended: do not hard-code their URLs in the app).
- For apps, an operator e-mail address must be easy to find on the app's website and in its app-store entries.
- "Die gewerbliche Nutzung ist nur gestattet, wenn die Nutzung der Dienste keinen wesentlichen Teil eines Onlineangebots
  darstellt." (commercial use only if the services are not an essential part of an online offer). "Webseiten mit hohem
  Trafficaufkommen duerfen unsere Services grundsaetzlich nicht nutzen." "Das massenhafte Herunterladen von Daten ist
  verboten."
- Availability: "Wir behalten uns vor, Dienste jederzeit ohne Angabe von Gruenden oder eine Vorankuendigung einzustellen."
  "Bitte bedenken Sie, dass hinter den Services jeweils nur ein einziger Server steht. Wir garantieren keine Verfuegbarkeit."
  The permission to use the services "kann ... jederzeit ohne Angabe von Gruenden widerrufen werden".
- Attribution: OpenStreetMap credit with a link to `https://www.openstreetmap.org/fixthemap`.
- Contact given on the page: the association's general address (see the page).

(Umlauts are transliterated above to keep this file ASCII; the page has the original spelling.)

Valhalla README, **VERIFIED (https://raw.githubusercontent.com/valhalla/valhalla/master/README.md, 2026-11-22)**:

- "FOSSGIS e.V. hosts a demo server which is open to the public and includes a full planet graph ... The HTTP API is
  accessible on a slightly different subdomain, e.g. https://valhalla1.openstreetmap.de/isochrone."
- "Usage of the demo server follows the usual fair-usage policy as OSRM & Nominatim demo servers (somewhat enforced by rate
  limits)."
- "In case you publish apps to end users (be it mobile, web or desktop) which request to the public demo server, we'd ask
  you to let us know via Github Discussions and to include an identifying `X-Client-Id` request header, e.g.
  `X-Client-Id: newroutingapp.io`."
- "Valhalla, and all of the projects under the Valhalla organization, use the MIT License."

Sizing and cost for a France-only server, **FROM REPO DOC** (`docs/VALHALLA-HEBERGEMENT.md` section 3, dated 2026-11-21).
That document marks its own France table as an extrapolation, not a measurement:

| Item | Value in the repo doc | Status in the repo doc |
|---|---|---|
| Geofabrik France extract | 5.12 GB | verified there (HTTP header) |
| Tile archive | about 10 GB | extrapolated |
| Disk during build | about 50 GB | extrapolated |
| RAM to build | 8 GB minimum, 16 GB comfortable | extrapolated |
| Build time | about 1 h 30 on 16 cores; 4 to 8 h on a 4 vCPU VPS | extrapolated |
| Elevation (only needed for the scooter profile) | 3 to 4 GB | extrapolated |
| RAM to serve | 4 GB minimum, 8 GB comfortable | extrapolated |
| vCPU to serve, personal use | 2 | extrapolated |
| Disk to serve | 30 GB | extrapolated |
| Monthly cost | VPS 4 vCPU / 8 GB: 8 to 20 EUR | "from memory, not read from a hosting price list" |
| Update | full rebuild per update, weekly suggested; tiles can be built on a PC and copied | from documentation reading |

No price was checked on 2026-11-22. **NOT VERIFIED**: every cost figure.

### 2.2 Options

| | A. Public instance, compliant | B. Self-hosted France | C. Public default now, self-host before wider distribution |
|---|---|---|---|
| Technical | 1 request/s for everything (routes, reroutes, three alternatives in about 2 s, background `trace_attributes`); no fix-by-fix speed limits; single server, no availability guarantee, version not controlled | no rate cap, all features, pinned version; France only unless more disk and RAM are paid for; a weekly rebuild job to run | A today, B later; the client already supports both (limiter applies to `openstreetmap.de` hosts only) |
| Security / privacy | origins, destinations and traces go to a third party under its privacy policy | positions stay on the owner's server, whose proxy logs then hold them (short rotation needed); Valhalla has no authentication: reverse proxy, TLS, API key, rate limit, firewall; a key shipped in an APK can be extracted | as A, then as B |
| Legal / terms | acceptable for a private, non-commercial app with a handful of users that identifies itself; not for a widely distributed or commercial navigation app (purpose clause, hard-coding recommendation, high-traffic and commercial clauses) | only Valhalla's MIT licence, ODbL attribution for the data, and the host's contract | compliant at each stage if the switch really happens before distribution |
| Maintenance | none, but exposed to a block or a shutdown without notice | a server to patch, monitor and rebuild; about one more thing that can be down on a trip | deferred |
| Cost | none | 8 to 20 EUR per month (NOT VERIFIED) plus time | none now |

What "compliant" still requires for A and C (all client changes, none done):

1. Send `X-Client-Id` on every Valhalla request. `ApiClient.postJson(url, body, headers)` already accepts headers
   (**FROM REPO DOC**, `docs/VALHALLA-HEBERGEMENT.md` 4.5); send it only to the configured Valhalla host. Value: a stable
   public identifier of the app, not a person (for example the application id `fr.roadly`, or a project domain if one
   exists).
2. Put a reachable contact in the User-Agent through `roadly.contact`. The current default is the URL of a private
   repository, which nobody at FOSSGIS can open.
3. Announce the app in the Valhalla GitHub Discussions. The README asks this of apps "published to end users"; for a private
   single-user app it is a courtesy, and it makes the project name public, so the owner decides when.
4. Show the OpenStreetMap attribution with the "fix the map" link wherever the route is displayed (to check in the About
   screen and on the map).
5. Keep the limiter; consider reducing background `trace_attributes` on the public host
   (`docs/VALHALLA-HEBERGEMENT.md` 8.2 item 5).

The hard-coded default URL itself cannot be made to match the recommendation without removing the default, which would make
a fresh install unable to route. That is one more reason the public host is a private-use solution only.

### 2.3 Recommendation: C

- Today Roadly is used by one person. One request per second, identified, is the kind of use the terms tolerate, and the
  client-side limiter already exists. Paying for and operating a server before the app has been driven is premature.
- Do items 1, 2 and 4 of the list above before 1.0. Do item 3 when the owner is ready to name the project in public.
- Self-hosting (France, tiles built on the PC, small VPS, reverse proxy with key and rate limit as in
  `docs/VALHALLA-HEBERGEMENT.md` section 4) becomes mandatory at the first of these events: a copy of the app goes to another
  person (Play internal testers included); the owner wants fix-by-fix speed limits; the public instance answers 429 or is
  down during real use.
- For a navigation app, the absence of any availability guarantee is the strongest technical argument for B in the long
  run: no route server means no guidance and no rerouting.

### 2.4 Still to verify

- Hosting prices, and a measured France build (time, disk, RAM); the repo doc's figures are extrapolations.
- The actual rate limiting enforced by the public instance (the README links a GitHub discussion; not read).
- Whether the OpenStreetMap attribution in the app includes the "fix the map" link.
- Whether the operators have any objection to this kind of use; nobody was contacted.
- Whether one routing region is enough: trips leaving France fail on a France-only server.

---

## 3. Release signing key and migration of the debug-signed phone

### 3.1 Facts

- **VERIFIED (worktree)** `app/build.gradle.kts` lines 60-80: a `roadly` signing config exists only when a keystore path is
  given (environment or `local.properties`). Without it, a release build is unsigned; `-Proadly.debugSignedRelease=true`
  signs it with the debug key and adds `-debugsigned` to the version name. A debug build uses the `roadly` key when it is
  configured.
- **VERIFIED (worktree)** `.github/workflows/release.yml`, job `sign`: fails when `ROADLY_KEYSTORE_BASE64` is absent, signs
  with `apksigner`, then prints `Signer #1 certificate DN` and `SHA-256` into the job log.
- **FROM REPO DOC** `docs/UPDATES.md` offers two keys for the GitHub secrets: option A, the PC debug keystore; option B, a
  dedicated key (`keytool -genkeypair ... -keyalg RSA -keysize 4096 -validity 10000`).
- **FROM REPO DOC** `docs/ROADLY-1.0/RELEASE-CHECKLIST.md` line 442: the HONOR runs `fr.roadly` 0.1.215, signer
  `CN=Android Debug`, certificate SHA-256 `7611a323...c9c2`.
- **VERIFIED (local read-only command, 2026-11-22)** `keytool -list -v` on the PC's `%USERPROFILE%\.android\debug.keystore`,
  alias `androiddebugkey`, gives exactly that SHA-256. The key is RSA 2048, valid from 2026-08-08 to 2056-07-31. So the
  phone's installed app can only be updated in place by an APK signed with that one file on that PC.
- **FROM REPO DOC** `docs/PROTOCOLE-TERRAIN-HONOR.md` line 41: the phone (HONOR Magic 8 Pro) runs Android 17.
- **VERIFIED (worktree)** `app/src/main/AndroidManifest.xml` lines 9-10: `android:allowBackup="false"` with data extraction
  rules. An uninstall erases everything stored by the app.
- **NOT VERIFIED** Which key is in the GitHub secrets. Secrets cannot be read. Two ways to find out without changing
  anything: read the `Signature` step log of the last successful release run (it prints the SHA-256), or download the latest
  release APK and run `apksigner verify --print-certs` on it. If the value is `7611a323...c9c2`, CI signs with the debug key.
- **ASSUMPTION** An export and import of favourites and trips exists by the time of the migration. It is being implemented
  in parallel and is not in the worktree in a form that could be checked. The same parallel work adds a certificate check
  before install. Everything below that mentions export, import or that check depends on this assumption.

Platform facts:

- **VERIFIED (https://source.android.com/docs/security/features/apksigning/v3, 2026-11-22)** "Android 9 supports APK key
  rotation, which gives apps the ability to change their signing key as part of an APK update." "APK key rotation is not
  recommended for Android 12 (API level 31) and earlier. In Android 13 (API level 33) and higher, checkSignatures recognizes
  the proof-of-rotation and returns the newest signature. Developers enrolled in Play App Signing can request a key upgrade
  through the Play console." The proof-of-rotation is a list of the earlier signing certificates.
- **VERIFIED (https://developer.android.com/tools/apksigner, 2026-11-22)** `apksigner rotate --out <lineage> --old-signer
  --ks <old> --new-signer --ks <new>` creates the lineage. `--rotation-min-sdk-version`: "By default, rotated signing keys,
  which are supported on devices that run Android 13 (API level 33) or higher, are used with the v3.1 signing block"; the
  original key is used for older platform versions.
- **VERIFIED (https://support.google.com/googleplay/android-developer/answer/9842756, 2026-11-22)** Play App Signing: the
  developer holds an upload key (RSA 2048 or more), Google holds the app signing key that signs what users install. New apps
  are enrolled automatically with Google-generated keys. Before a release reaches open testing or production, the developer
  may instead "Provide a copy of your app signing key". A lost upload key can be reset by Google; a self-managed app signing
  key that is lost cannot.

### 3.2 Options for the key

| | A. Keep the debug key | B. Dedicated release key | C. Dedicated key + v3 rotation from the debug key | D. Play App Signing (only for a Play flavor) |
|---|---|---|---|---|
| Technical | nothing to do; phone updates in place | new certificate; one uninstall per device that has the debug-signed app | new certificate, and devices on Android 13+ accept the update in place thanks to the lineage; CI must carry two keystores and the lineage file | Google signs Play installs; the owner signs uploads |
| Security | keystore and key passwords are the documented default `android`; the file is a development artefact in the user profile, read by every Android build tool on the PC and easy to copy or to delete (the SDK then regenerates a different one, silently); whoever has the file can sign an update Android accepts over the installed app | strong passwords chosen by the owner; key used only for releases; in CI only the `sign` job sees it | as B, but the debug key remains an ancestor in the lineage and must be kept as long as devices below the rotation minimum are supported | key protected by Google; upload key resettable |
| Legal / store | certificate subject `CN=Android Debug`; Play does not accept debug-signed uploads (NOT VERIFIED today); developer verification ties the package name to its signing key, so registering a debug certificate would freeze a poor choice | clean identity for Play and for package registration | lineage containing a debug certificate; acceptance by Play and by package registration NOT VERIFIED | required in practice for a new Play app (AAB) |
| Maintenance | none, until the file is lost: then every device must uninstall | a secret to back up for the life of the app | highest: rotation flags in the `sign` job, an untested path, the in-app certificate check must understand rotated signers | least for the Play side |

About B, practical points:

- Generation: on the owner's PC, offline, with the command already in `docs/UPDATES.md` (RSA 4096, validity 10000 days), with
  two different strong passwords.
- Storage: the keystore file and both passwords in a password manager, plus one offline copy (encrypted USB key or printed
  recovery sheet kept apart from the PC). Never in the repository, never in a cloud folder synced in clear, never in
  `local.properties` of a shared machine. In GitHub: the four existing secrets; consider attaching them to a protected
  environment so that the `sign` job needs an approval (not configured today).
- Write the SHA-256 of the certificate in `docs/UPDATES.md`. It is public information and lets anyone check an APK.
- Loss of the key: no more in-place updates, every device reinstalls. Compromise: publish a rotated key (C) or a new package.
- Rotation later (B to another key) uses the same `apksigner rotate` mechanism. With `minSdk = 29`, devices on Android 10 to
  12 keep verifying with the original key by default, so the old key cannot be thrown away while those versions are
  supported.

About D, if a Play flavor exists: either let Google generate the app signing key (Play installs and GitHub installs then
have different certificates) or upload a copy of the dedicated key so both channels share one certificate. The second choice
lets a phone change channel without uninstalling, at the price of giving Google a copy of the key that also signs the
GitHub releases. Recommendation: keep them separate; the phone picks one channel.

### 3.3 Recommendation

Option B: a dedicated release key, a clean reinstall of the HONOR through export and import, done once and only after AA-1
is decided. Option C is the alternative if the owner wants zero data loss and accepts to rehearse it first.

Reasons: one phone is concerned, so the cost of a clean break is one evening; the result is a certificate history with no
debug key in it, which is what Play and package registration will look at; the release pipeline stays as simple as it is.
C is technically attractive on an Android 17 phone but adds a permanent second secret to CI and a path nobody has tested.

Do today, whatever the choice, and without touching the phone: copy `%USERPROFILE%\.android\debug.keystore` to a safe place.
Until the migration is done it is the only key that can update the installed app, and it is the key the phone needs for the
in-place update to a build that contains the export.

### 3.4 Migration of the HONOR, per option

Nothing below is to be executed now. "Lost" means not recoverable from the phone after the step.

**Option A - keep the debug key.**

1. Confirm that CI signs with the debug key (log or `apksigner`, see 3.1). If not, put the debug keystore in the secrets.
2. The phone updates in place from the next release with a higher version code.

Data lost: none. Risk kept: all of column A above, for the life of the app.

**Option B - dedicated key, clean reinstall (recommended).**

1. Generate the key, back it up, record its SHA-256. Put it in the GitHub secrets and in `local.properties`.
2. Still with the debug key: build a debug-signed version that contains the export (a higher version code installs over the
   0.1.215 in place, same certificate). Assumption: export exists.
3. On the phone: export favourites and trips to a file; copy that file off the phone; open it on the PC and check that it is
   complete and readable.
4. Rehearse the import on an emulator or a second device with a build signed by the new key. Do not uninstall anything
   before this passes.
5. Note by hand what the export does not carry (see below).
6. Uninstall Roadly from the phone. Install the build signed with the dedicated key. Import the file.
7. Redo permissions, battery exemptions, Android Auto setup; re-enter the GitHub token; check that the next release installs
   through the in-app updater.

Data lost, even with a working export of favourites and trips: all settings and preferences, the GitHub token (encrypted with
an Android Keystore key that dies with the app), provider overrides, scooter sheets, parked-car position, caches and
downloaded data, the "location denied forever" flag, any state kept only in Room tables that the export does not cover, and
the "installed by Roadly" status that allows updates without confirmation (regained after the first in-app update). The exact
list depends on what the export finally contains: **to verify against the implementation**.

**Option C - dedicated key with rotation.**

1. Generate and back up the dedicated key. Create the lineage: `apksigner rotate --out roadly.lineage --old-signer --ks
   debug.keystore --new-signer --ks roadly-release.jks`.
2. Change the `sign` job to sign with both signers and the lineage. Decide `--rotation-min-sdk-version`: the default (33)
   keeps the debug key as the signer seen by Android 10 to 12; the phone runs Android 17 and would see the new key.
3. Rehearse on an emulator: install a debug-signed build, then the rotated build over it; check that the data is intact and
   that a later rotated build also installs.
4. Check the in-app certificate check (parallel work) against a rotated APK: it must accept a signer whose lineage contains
   the installed certificate, and nothing else.
5. Take an export first anyway, as a safety net. Then update the phone in place.

Data lost: none if it works. If step 5 fails on the device, fall back to option B from its step 3. Kept forever: the debug
keystore (still needed to sign for devices below the rotation minimum) and its weak passwords, unless the minimum supported
Android version is raised to 13.

**Option D - phone moves to a Play track (internal testing).**

1. Export, copy the file off the phone, verify it (as B steps 2 to 4, with a `play` flavor build for the import rehearsal).
2. Uninstall the debug-signed app. Install from the internal track link. Import.

Data lost: as option B. The phone then updates through Play only; a GitHub APK no longer installs over it (different
certificate), and the reverse move costs another uninstall. If the owner uploaded a copy of the dedicated key to Play App
Signing, the phone could instead first follow option B and later switch channel without uninstalling (NOT VERIFIED on a
device).

Sequencing: decide AA-1 first. If the phone is to end on the Play internal track, do option D directly and skip B for that
phone; the dedicated key is still needed for the GitHub channel and the Desktop Head Unit builds.

### 3.5 Still to verify

- Which certificate the published releases carry today (not readable from here).
- The scope and the format of the export, and a full round trip on a real device.
- That the certificate check added before install handles a rotated signer (only relevant to option C).
- Rotation in practice: in-place update of a debug-signed install on Android 17, behaviour of
  `UPDATE_PACKAGES_WITHOUT_USER_ACTION` after a rotated update.
- Whether Play and Android developer verification accept a lineage that starts with a debug certificate, and whether Play
  rejects debug-signed uploads (both from recollection or unknown).

---

## 4. REL-5 - licence and third-party notices

### 4.1 Verified facts

(a) Repository licence.

- **VERIFIED (worktree)** `LICENSE` is the unmodified text of the GNU General Public License, version 3, 29 June 2007
  (35,823 bytes). `README.md` line 13 carries a "GPL-3.0" badge and lines 180-182 state "GNU General Public License v3.0".
- **VERIFIED (worktree, `git grep`)** No Kotlin or Gradle file carries an SPDX identifier or a GPL header. Nothing in the
  repository says "version 3 only" or "version 3 or any later version". The licence is therefore GPL-3.0 with the
  only / or-later choice left unstated.
- **VERIFIED (`git shortlog -sn HEAD`)** 225 commits, 3 author identities: 190, 28 and 7 commits. Two identities (190 and 7)
  appear to be the owner under two names; the third (28) is an automated coding assistant. 216 commit messages carry a
  co-author trailer naming an AI assistant. No other human contributor appears in the history. That the two human identities
  are one person is an inference to be confirmed by the owner.

(b) The Google location dependency.

- **VERIFIED (worktree)** `gradle/libs.versions.toml` line 18 `playLocation = "21.3.0"` and line 66
  `play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playLocation" }`.
  Coordinates: `com.google.android.gms:play-services-location:21.3.0`.
- **VERIFIED (worktree)** Declared in `core/location/build.gradle.kts` line 22 (`implementation(libs.play.services.location)`)
  together with line 23 `implementation(libs.coroutines.play.services)`
  (`org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2`).
- **VERIFIED (worktree, search for `com.google.android.gms` in all Kotlin, XML and Gradle files)** Used in exactly one source
  file: `core/location/src/main/java/fr/roadly/core/location/FusedLocationRepository.kt`, imports `LocationCallback`,
  `LocationRequest`, `LocationResult`, `LocationServices`, `Priority`. No source file imports `kotlinx.coroutines.tasks`, so
  the coroutines bridge is declared but unused. The string `com.google.android.gms.car.application` in
  `feature/car/src/main/AndroidManifest.xml` is only the name of a manifest metadata entry, not a library.

(c) Its licence and its transitive dependencies.

- **VERIFIED (Gradle, `:core:location:dependencies --configuration releaseRuntimeClasspath`, 2026-11-22)** Subtree:

```
com.google.android.gms:play-services-location:21.3.0
+--- com.google.android.gms:play-services-base:18.5.0
|    +--- androidx.collection:collection, androidx.core:core, androidx.fragment:fragment
|    +--- com.google.android.gms:play-services-basement:18.4.0
|    |    +--- androidx.collection:collection, androidx.core:core, androidx.fragment:fragment
|    \--- com.google.android.gms:play-services-tasks:18.2.0
|         \--- com.google.android.gms:play-services-basement:18.4.0
+--- com.google.android.gms:play-services-basement:18.4.0
+--- com.google.android.gms:play-services-tasks:18.2.0
+--- org.jetbrains.kotlin:kotlin-stdlib
+--- org.jetbrains.kotlinx:kotlinx-coroutines-android
\--- org.jetbrains.kotlinx:kotlinx-coroutines-core
```

- **VERIFIED (POM files downloaded from https://dl.google.com/android/maven2/, 2026-11-22)** Licence declared in each POM:

| Artifact | Licence name in the POM | Licence URL in the POM |
|---|---|---|
| `com.google.android.gms:play-services-location:21.3.0` | Android Software Development Kit License | https://developer.android.com/studio/terms.html |
| `com.google.android.gms:play-services-base:18.5.0` | Android Software Development Kit License | https://developer.android.com/studio/terms.html |
| `com.google.android.gms:play-services-basement:18.4.0` | Android Software Development Kit License | https://developer.android.com/studio/terms.html |
| `com.google.android.gms:play-services-tasks:18.2.0` | Android Software Development Kit License | https://developer.android.com/studio/terms.html |

  POM path pattern: `com/google/android/gms/<artifact>/<version>/<artifact>-<version>.pom`. The other nodes of the subtree
  (AndroidX, Kotlin, kotlinx) declare Apache-2.0 (table in (d)).
- **VERIFIED (Gradle, `:app:dependencies --configuration releaseRuntimeClasspath`)** The four Play services artifacts are on
  the runtime classpath of the application, so their classes are packaged into the APK. They are the only artifacts of the
  186 resolved ones whose POM declares a non-free licence.
- **VERIFIED (https://developer.android.com/studio/terms, agreement dated July 27, 2021, fetched 2026-11-22)** Relevant
  clauses: 3.1 "Google grants you a limited, worldwide, royalty-free, non-assignable, non-exclusive, and non-sublicensable
  license to use the SDK solely to develop applications for compatible implementations of Android." 3.4 "Except to the extent
  required by applicable third party licenses, you may not copy (except for backup purposes), modify, adapt, redistribute,
  decompile, reverse engineer, disassemble, or create derivative works of the SDK or any part of the SDK." 3.5 "Use,
  reproduction and distribution of components of the SDK licensed under an open source software license are governed solely
  by the terms of that open source software license and not the License Agreement."
- **NOT VERIFIED** Whether other Google terms also apply to these libraries (for example the Google APIs Terms of Service);
  the page checked for this had no such statement. Whether Google intends clause 3.4 to restrict shipping the client library
  inside an app: every Android app that uses Play services does it, but the text gives no explicit redistribution grant.

(d) Other dependencies. Versions are those Gradle resolved for the release runtime classpath of `:app`; when the catalog
asks for an older version, the resolved one is shown. Licence names are quoted from the POM (or its parent POM).

| Artifact | Version | Licence declared | Source of the fact |
|---|---|---|---|
| `org.maplibre.gl:android-sdk` | 11.13.5 | "BSD", URL opensource.org/licenses/BSD-2-Clause | POM, Maven Central |
| `org.maplibre.gl:maplibre-android-gestures` | 0.0.4 | "BSD", URL BSD-2-Clause | POM, Maven Central |
| `org.maplibre.gl:android-sdk-geojson`, `android-sdk-turf` | 6.0.1 | The Apache Software License, Version 2.0 | POM, Maven Central |
| `com.squareup.okhttp3:okhttp`, `logging-interceptor` | 4.12.0 | The Apache Software License, Version 2.0 | POM, Maven Central |
| `com.squareup.okio:okio`, `okio-jvm` | 3.6.0 | The Apache Software License, Version 2.0 | POM, Maven Central |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core`, `-android`, `-play-services` | 1.10.2 | Apache-2.0 | POM, Maven Central |
| `org.jetbrains.kotlinx:kotlinx-serialization-json`, `-core` | 1.8.1 | Apache-2.0 | POM, Maven Central |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.20 | Apache-2.0 | POM, Maven Central |
| `com.google.dagger:hilt-android`, `hilt-core`, `dagger` | 2.58 | Apache 2.0 | POM, Maven Central |
| `androidx.hilt:hilt-navigation-compose` | 1.2.0 | The Apache Software License, Version 2.0 | POM, Google Maven |
| `androidx.room:room-runtime`, `room-ktx` | 2.8.5 | The Apache Software License, Version 2.0 | POM, Google Maven |
| `androidx.datastore:datastore-preferences` and 10 related | 1.1.7 | The Apache Software License, Version 2.0 | POM, Google Maven |
| `androidx.datastore:datastore-preferences-external-protobuf` | 1.1.7 | BSD-3-Clause | POM, Google Maven |
| `androidx.car.app:app`, `app-projected` | 1.7.0 | The Apache Software License, Version 2.0 | POM, Google Maven |
| `androidx.compose:compose-bom` and the Compose artifacts it selects (ui, foundation, animation, runtime 1.11.1; material3 1.4.0; material-icons-extended 1.7.8) | 2026.05.00 | The Apache Software License, Version 2.0 | POM, Google Maven |
| `androidx.core:core-ktx`, `appcompat`, `activity-compose`, `lifecycle-*`, `navigation-compose` and the remaining AndroidX artifacts | 1.17.0, 1.7.1, 1.10.1, 2.9.4, 2.9.4 | The Apache Software License, Version 2.0 | POM, Google Maven |
| `com.google.guava:guava` (transitive) | 31.1-android | Apache License, Version 2.0 | POM, Maven Central |
| `com.google.code.gson:gson` (transitive) | 2.10.1 | Apache-2.0 | POM, Maven Central |
| `com.jakewharton.timber:timber` (transitive) | 5.0.1 | The Apache Software License, Version 2.0 | POM, Maven Central |
| `org.checkerframework:checker-qual` (transitive) | 3.12.0 | The MIT License | POM, Maven Central |
| `javax.inject`, `jakarta.inject-api`, `jsr305`, `j2objc-annotations`, `error_prone_annotations`, `jspecify`, `org.jetbrains:annotations` (transitive) | various | Apache-2.0 (several spellings) | POM, Maven Central |
| Build time only: `androidx.room:room-compiler` 2.8.5, `com.google.dagger:hilt-android-compiler` 2.58 | | Apache-2.0 | POM |
| Tests only: `junit:junit` | 4.13.2 | Eclipse Public License 1.0 | POM, Maven Central |
| Tests only: `org.robolectric:robolectric` | 4.18.1 | The MIT License | POM, Maven Central |
| Tests only: `app.cash.turbine:turbine` 1.2.0, `io.github.takahirom.roborazzi:roborazzi` 1.79.0, `com.squareup.okhttp3:mockwebserver` 4.12.0, `kotlinx-coroutines-test` 1.10.2, `androidx.test.ext:junit` 1.3.0, `androidx.test:runner` 1.7.0, `espresso-core` 3.7.0 | | Apache-2.0 | POM |

Totals for the 186 artifacts of the application's release runtime classpath: 178 Apache-2.0, 2 BSD-2-Clause (MapLibre),
1 BSD-3-Clause, 1 MIT, 4 Android Software Development Kit License. The full per-artifact list was produced by a script and
is not stored in the repository; a licence report plugin (4.5) will reproduce it.

Limits of this table: a POM states what the publisher declares. It does not list code bundled inside an artifact. The
MapLibre Android AAR contains a native library built from MapLibre Native and its own third-party components; their
notices were **not** checked. Fonts are covered separately by `core/designsystem/LICENSE-Inter-OFL.txt` and
`core/designsystem/licenses/Nunito-OFL.txt` (**VERIFIED** present).

### 4.2 GPL-3.0 and a proprietary library: the evidence

What the GPLv3 says (**VERIFIED**, quoted from the repository's `LICENSE`):

- Section 5(c): "You must license the entire work, as a whole, under this License to anyone who comes into possession of a
  copy. This License will therefore apply, along with any applicable section 7 additional terms, to the whole of the work,
  and all its parts, regardless of how they are packaged."
- Section 6 lets object code be conveyed only together with the "Corresponding Source".
- Section 1: Corresponding Source "does not include the work's System Libraries" but does include "the source code for shared
  libraries and dynamically linked subprograms that the work is specifically designed to require". System Libraries are
  "anything, other than the work as a whole, that (a) is included in the normal form of packaging a Major Component, but
  which is not part of that Major Component, and (b) serves only to enable use of the work with that Major Component, or to
  implement a Standard Interface for which an implementation is available to the public in source code form." A Major
  Component is "a major essential component (kernel, window system, and so on) of the specific operating system (if any) on
  which the executable work runs".
- Section 7: "Additional permissions" make exceptions from the licence's conditions; those that apply to the entire program
  are treated as part of the licence.

What the FSF publishes (**VERIFIED**, https://www.gnu.org/licenses/gpl-faq.html, 2026-11-22):

- "#GPLIncompatibleLibs": "If you want your program to link against a library not covered by the system library exception,
  you need to provide permission to do that. ... Only the copyright holders for the program can legally release their
  software under these terms. If you wrote the whole program yourself, then assuming your employer or school does not claim
  the copyright, you are the copyright holder - so you can authorize the exception. But if you want to use parts of other
  GPL-covered programs by other authors in your code, you cannot authorize the exception for them." The FAQ then gives the
  GPLv3 wording: "Additional permission under GNU GPL version 3 section 7. If you modify this Program, or any covered work,
  by linking or combining it with [name of library] (or a modified version of that library), containing parts covered by the
  terms of [name of library's license], the licensors of this Program grant you additional permission to convey the resulting
  work." A further sentence about the library's source is to be removed "if not everybody can distribute source for the
  libraries you intend to link with".
- "#SystemLibraryException": a library that meets the system library criteria needs nothing special.
- "#WindowsRuntimeAndGPL": "libraries can only qualify as System Libraries as long as they're not distributed with the program
  itself. If you distribute the DLLs with the program, they won't be eligible for this exception anymore".
- "#FSWithNFLibs": a free program that depends on a nonfree library "won't be fully usable in a free environment"; the FSF
  asks authors to say so in the README and to plan the removal of the dependency.

Applying this to Roadly (engineering reading, not legal advice):

1. The Play services client libraries are not free software (POM licence, clause 3.4) and their source is not published
   with them.
2. They are distributed inside the APK (Gradle evidence above). Following the FSF's own reasoning on bundled runtime
   libraries, they cannot be treated as System Libraries of the APK. The Google Play services app on the phone is a separate
   program reached through inter-process calls; it is the bundled client classes that raise the question.
3. So an APK conveyed under plain GPL-3.0 has a part for which the conveyor cannot provide Corresponding Source and which
   cannot be licensed under the GPL. The FSF's published answer is the section 7 additional permission.
4. The copyright holder is not bound by his own licence when he distributes his own program, so nobody is exposed today by
   the owner's builds. The defect is for recipients: without the exception they cannot lawfully redistribute the binary under
   the GPL, and the "GPL-3.0" statement in the README promises more than the APK delivers.
5. The exception can be granted only by the holder of all the copyright in Roadly. The git history shows no other human
   author. Two caveats: third-party GPL code pasted into the sources would not be covered (none was looked for; all library
   dependencies are permissively licensed, which makes it unlikely); and a large share of the commits is co-authored by an AI
   assistant. Whether machine-generated portions are protected by copyright at all, and who would own them, is unsettled
   law. This does not prevent the owner from choosing or changing the licence, since no other person can claim the code, but
   it could weaken the enforceability of the copyleft on those portions. To be raised with a lawyer if it matters.
6. The exception fixes the GPL side only. A recipient who redistributes the APK still redistributes Google's classes and
   must look at Google's terms for that. It also does not make the app acceptable to free-software-only catalogues.
7. Today the repository and its releases are private and the APK goes to the owner's devices. The GPL's conditions on
   conveying start with the first copy given to someone else, Play testers included.

### 4.3 Options

| | A. Keep GPL-3.0, add a section 7 exception | B. Replace Play services location by the platform `LocationManager` | C. Change the project licence | D. Product flavors (FOSS flavor without Play services) |
|---|---|---|---|---|
| Technical | no code change; a notice text in `LICENSE` companion file, README and source headers | one module to rewrite (below); location quality on the phone to re-validate | no code change | two implementations of `LocationRepository` to keep and to test; combines with the `github` / `play` flavors of section 1 |
| Security / privacy | unchanged: a closed-source Google component stays in the app | removes the only closed-source code from the APK; the app also works on devices without Google services | unchanged | the FOSS flavor is free of closed code |
| Legal | follows the FSF's published method; depends on the owner being the sole copyright holder; does not touch Google's terms | the question disappears: every remaining dependency is Apache-2.0, BSD or MIT, all compatible with GPL-3.0 | possible only for the holder of all copyright; a permissive or weak-copyleft licence (Apache-2.0, MPL-2.0) has no conflict with a proprietary library; gives up strong copyleft; copies already received under GPL-3.0 stay under it | clean for the FOSS flavor; the other flavor still needs A |
| Maintenance | lowest | one dependency less, no Play services version to track; edge cases of the platform API to own | lowest | highest |

What option B changes in the code (**VERIFIED** file list; API details are from recollection):

- `core/location/src/main/java/fr/roadly/core/location/FusedLocationRepository.kt`: the five `com.google.android.gms.location`
  imports, the `fused` field, and the `updates(m)` function (last known location, `LocationRequest.Builder(Priority.
  PRIORITY_HIGH_ACCURACY, intervalMs)` with a minimum interval of half that value, the `LocationCallback`, registration on
  the main looper, removal in `awaitClose`). The rest of the class (modes, permission state, replay, `toFix`) uses only
  platform and project types and stays. The class would be renamed.
- `core/location/.../LocationModule.kt`: the binding to the renamed class. Fakes under `core/location/.../testing` unchanged.
- `core/location/build.gradle.kts` lines 22-23 and `gradle/libs.versions.toml` lines 18, 56 and 66: remove both entries.
- Replacement API: `android.location.LocationManager` (already imported in `LocationReadiness.kt` and
  `feature/car/.../CarLocationSwitch.kt`), probably through `androidx.core.location.LocationManagerCompat` and
  `LocationRequestCompat`, on the GPS provider, and on the platform fused provider where it exists. **NOT VERIFIED today**:
  the exact compat API, the API level of the platform fused provider (believed to be 31, with `minSdk` 29), and whether the
  fix rate, first-fix time, bearing and speed fields match what the guidance engine was tuned on.

### 4.4 Recommendation

1. For 1.0: option A. Keep GPL-3.0, add the FSF's section 7 additional permission naming the Google Play services client
   libraries (`com.google.android.gms:play-services-location`, `-base`, `-basement`, `-tasks`) under the Android Software
   Development Kit License, without the optional sentence about the library's source. Put it in a file next to `LICENSE`
   (do not edit `LICENSE` itself: its text may not be changed), mention it in the README licence section, and say there, as
   the FSF asks, that the dependency on a nonfree library is a drawback to be removed. Decide and state "GPL-3.0-only" or
   "GPL-3.0-or-later" at the same time.
2. Add the third-party notices file (4.5).
3. After 1.0, once guidance has been validated on the road with the current location source: implement option B behind the
   existing `LocationRepository` interface, compare both on the same drives (the replay tooling helps), and drop Play
   services if the platform source is as good. The exception can then be withdrawn for later versions.

Why not B now: location is the input of everything the guidance engine does, the engine has not been driven yet, and
swapping its source just before 1.0 would mix two unknowns. Why not C: nothing is gained that A does not give, and copyleft
is a stated intent of the README. Why not D: a second flavor dimension is real maintenance for one developer; it only makes
sense if a Google-free build is wanted while the Google location source is kept for others.

Before giving the app to anyone else, also settle how recipients get the corresponding source while the repository is
private (make the repository public, attach a source archive to each release, or include a written offer).

### 4.5 Third-party notices: content and generation

Content the file must have (and an in-app "open-source licences" entry should show the same):

- For every component shipped in the APK: name, version, copyright holder or project, licence name, and the licence text
  once per distinct licence.
  - Apache-2.0 (section 4 of that licence): a copy of the licence, and the content of any `NOTICE` file the component ships.
  - BSD-2-Clause and BSD-3-Clause (MapLibre, the DataStore protobuf artifact): the copyright notice, the list of conditions
    and the disclaimer, reproduced in the documentation or materials provided with the binary.
  - MIT (checker-qual): the copyright notice and the permission notice.
  - SIL OFL 1.1 (Inter, Nunito): copyright notice and licence, already in `core/designsystem`.
- The notices of MapLibre Native's own bundled third-party code, taken from the MapLibre distribution (not checked yet).
- Google Play services client libraries: name, versions, "Android Software Development Kit License", the URL, and a
  sentence saying they are not free software and are covered by the section 7 permission.
- Data and service attributions (OpenStreetMap ODbL, OpenFreeMap, BAN, IGN, Open-Meteo and the others): they already exist
  in `README.md` lines 165-176 and in the About screen string `settings_licenses`; the notices file can point to them.
- Test-only and build-only dependencies (JUnit under EPL-1.0, Robolectric, compilers) are not distributed in the APK and need
  no entry; listing them in a separate section is harmless.

Generation, without adding anything now. Candidates, to be evaluated when the task is scheduled:

| Tool | What it does | Remark |
|---|---|---|
| `com.google.android.gms:oss-licenses-plugin` | generates an in-app licence screen from POM data | **VERIFIED** listed as version 0.13.0 on https://developers.google.com/android/guides/setup (page updated 2026-11-09); it needs the runtime library `play-services-oss-licenses` (17.5.3), itself a Play services artifact, which goes against the direction of this section |
| `app.cash.licensee` | fails the build when a dependency has a licence outside an allow-list; writes a JSON report | NOT VERIFIED today (name, current version, maintenance); the allow-list is what would have caught the Play services licence |
| `com.github.jk1.dependency-license-report` | writes a text or HTML report of all dependencies and licences | NOT VERIFIED today |
| `com.mikepenz.aboutlibraries` | generates the data and a Compose screen listing libraries and licences | NOT VERIFIED today |

Suggested shape: one plugin applied to `:app` on the release runtime classpath; a CI step in the `build` job that fails on an
unknown licence; the generated report committed as `THIRD-PARTY-NOTICES.md` (or produced at release time and attached to the
release) and shown from the About screen. Until a plugin is chosen, the totals in 4.1 (d) can be regenerated with
`gradlew :app:dependencies --configuration releaseRuntimeClasspath` plus the POM of each artifact.

### 4.6 Still to verify

- That the owner is the only copyright holder (the two human identities in git, any code pasted from other projects).
- The legal status of the AI-assisted portions, if the owner intends to rely on copyleft enforcement.
- Whether other Google terms apply to the Play services client libraries, and Google's position on their redistribution
  inside an APK by third parties.
- MapLibre Native's bundled third-party notices.
- The platform location API details and its behaviour on the HONOR (option B).
- The licence report plugins: names, versions, compatibility with AGP 8.13 and Gradle 8.14.

---

## 5. Dependencies between the four decisions

1. AA-1 comes first. It fixes which flavors exist, whether the SEC-3 guidance has any remaining purpose, when
   `onAutoDriveEnabled` is needed, and on which channel the HONOR will live.
2. The signing migration follows AA-1: one uninstall, onto the final channel. Back up the debug keystore now.
3. VAL-1 is tied to distribution: the public host is acceptable only while the app stays on the owner's devices. The first
   external tester (Play internal track included) is the trigger for self-hosting.
4. REL-5 is tied to distribution in the same way: the exception, the notices and a way to obtain the source must exist before
   the first copy leaves the owner's hands.

## 6. Everything that could not be verified (consolidated)

- Which signing key the GitHub secrets hold, hence which certificate the published releases carry.
- Existence, scope and format of the data export / import and of the certificate check before install (parallel work,
  assumed).
- That a Play internal-track install appears in the owner's real car; account prerequisites for a new personal Play Console
  account before the internal track is usable.
- Play review outcome for this app (speed-camera alerts, MapLibre map, `Trip` without a cluster session).
- The date Android developer verification applies in France; whether a limited distribution account fits a self-updating
  APK; whether a debug certificate, or a lineage starting with one, can be registered.
- APK key rotation on the actual phone, and the updater's behaviour after a rotated update.
- UnknownAuto: licence, maintenance and code; Shizuku behaviour on Android 17.
- Hosting prices and every France sizing figure (extrapolations in `docs/VALHALLA-HEBERGEMENT.md`); the public instance's
  real rate limiting; the operators' view of this use.
- Whether the in-app OpenStreetMap attribution has the "fix the map" link.
- Other Google terms applicable to the Play services client libraries; Google's position on redistribution of those classes.
- Third-party notices bundled inside the MapLibre native library.
- Sole copyright ownership, and the copyright status of AI-assisted code.
- Platform `LocationManager` API details and location quality on the HONOR.
- Licence report plugins: current names, versions and compatibility.

## 7. Sources

Fetched on 2026-11-22:

- https://developer.android.com/training/cars/testing
- https://developer.android.com/training/cars/apps/navigation
- https://developer.android.com/docs/quality-guidelines/car-app-quality
- https://developer.android.com/training/cars/distribute
- https://developer.android.com/training/cars/apps/auto
- https://developer.android.com/developer-verification and /developer-verification/guides/faq, /guides/limited-distribution
- https://developer.android.com/guide/app-bundle
- https://developer.android.com/tools/apksigner
- https://developer.android.com/studio/terms
- https://developers.google.com/android/guides/setup
- https://source.android.com/docs/security/features/apksigning/v3
- https://support.google.com/googleplay/android-developer/answer/9888379 (device and network abuse)
- https://support.google.com/googleplay/android-developer/answer/12085295 (`REQUEST_INSTALL_PACKAGES`)
- https://support.google.com/googleplay/android-developer/answer/14151465 (testing requirements, personal accounts)
- https://support.google.com/googleplay/android-developer/answer/9845334 (testing tracks)
- https://support.google.com/googleplay/android-developer/answer/9842756 (Play App Signing)
- https://support.google.com/googleplay/android-developer/answer/6112435 (registration)
- https://fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen
- https://raw.githubusercontent.com/valhalla/valhalla/master/README.md
- https://www.gnu.org/licenses/gpl-faq.html
- POM files under https://dl.google.com/android/maven2/ and https://repo1.maven.org/maven2/ for the 186 artifacts of the
  release runtime classpath and for the test and build dependencies listed in 4.1 (d)

Local, on 2026-11-22: the worktree files cited inline; `gradlew :core:location:dependencies` and `:app:dependencies` with
`--configuration releaseRuntimeClasspath`; `git shortlog -sn HEAD`; `keytool -list -v` on the PC debug keystore (read only).

Repository documents relied on without re-checking: `docs/VALHALLA-HEBERGEMENT.md`, `docs/API-PROVIDERS.md`,
`docs/UPDATES.md`, `docs/ANDROID-AUTO.md`, `docs/ROADLY-1.0/RELEASE-CHECKLIST.md`, `docs/PROTOCOLE-TERRAIN-HONOR.md`.
