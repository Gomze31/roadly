# Étape B — Fiabilité du guidage — plan d'implémentation

> **Pour les agents d'exécution :** SOUS-COMPÉTENCE OBLIGATOIRE : utiliser superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour exécuter ce plan tâche par tâche. Les étapes utilisent des cases à cocher (`- [ ]`) pour le suivi.

**Objectif :** fiabiliser le guidage pas à pas de Roadly : progression monotone, une seule base de distance, modèle de manœuvre Valhalla enrichi, ronds-points, annonces priorisées par le temps, recalcul robuste aux routes parallèles, et persistance de la course.

**Architecture :** tout le calcul reste dans `core/navigation`, en Kotlin pur testé sur JVM (`RouteTracker`, `AnnouncementPolicy`, nouveaux `RoundaboutResolver` et `SpeechQueue`). `NavigationSession` reste la source de vérité unique. Le modèle (`core/model`) et le parseur Valhalla (`core/routing`) gagnent des champs, tous avec une valeur par défaut. L'écran (`feature/navigation`) ne fait plus que formater : il ne calcule plus aucune distance. La seule règle d'affichage qu'il ajoute est l'hystérésis de l'approche, une fonction pure.

**Pile technique :** Kotlin 2.2.10, Hilt 2.58, kotlinx.serialization, DataStore Preferences, JUnit 4, kotlinx-coroutines-test, MockWebServer, Valhalla FOSSGIS (`https://valhalla1.openstreetmap.de`).

**Spécification :** `ROADLY_3D_NAVIGATION_PLAN.md` §« Étape B — Fiabilité du guidage » (B1 à B6). Lire aussi `ROADLY_3D_NAVIGATION_TESTS.md` (scénarios 1 à 14 et 20, jeux de données) et `ROADLY_3D_NAVIGATION_AUDIT.md` (§1, §2, §3, §8).

## Contraintes globales

- Travailler uniquement dans le worktree `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d`, branche `feat/roadly-3d`, base `b1841c9`.
- Lancer Gradle avec l'outil PowerShell, toujours avec `--offline`. Exemple : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*X*"`. `:core:common` et `:core:model` sont des modules JVM : on les teste avec `:core:common:test` et `:core:model:test`.
- Écrire en français les KDoc, commentaires, chaînes, messages de commit et noms de tests descriptifs. KDoc d'une ligne, dans le style du fichier.
- Placer chaque seuil numérique dans `GuidanceConstants` (`core/navigation`), avec sa KDoc.
- Chaque commit est en français et se termine par la ligne `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Aucune fausse fonctionnalité : toute donnée vient de Valhalla ou de la géométrie. Une donnée absente reste `null`, elle n'est jamais inventée. Chaque limite est écrite « LIMITE » dans la KDoc ou dans `ROADLY_3D_NAVIGATION_TESTS.md`.
- Les 319 tests existants restent verts. Seules les assertions ci-dessous changent, parce que la spécification change le contrat. Elles sont adaptées, aucune n'est supprimée :
  - `NavigationViewModelTest`, lignes 125 et 130 (B2) ;
  - `ValhallaLanesTest`, ligne 35, et `ValhallaRoutingProviderTest`, ligne 206 (B3, `active`) ;
  - `GuidanceCameraPolicyTest`, lignes 48 à 51 (`EXIT` devient `EXIT_RIGHT`) ;
  - `RouteTrackerOffRouteTest` : l'assistant `fix` et 4 tests (B6) ;
  - `NavigationSessionTest` : l'assistant `fixAt` et 2 assertions (B6).
- Surface de fusion avec `feat/roadly-ai` (worktree `GPS-roadly-ai`, lecture seule) :
  - `SpeechService` ne reçoit que des membres ajoutés, avec un corps par défaut, sans renommage ;
  - la structure du `combine` de `NavigationViewModel.uiState` ne change pas : seule la ligne du lambda change ;
  - aucune signature de `NavigationScreen` ni des overlays ne change ;
  - les fichiers communs sont listés à la fin du plan.
- Hors étape : le bouton « Reprendre le guidage » relève de l'Étape E3. Ici, seules la persistance et son test sont livrés.

---

## Structure des fichiers

| Fichier | Rôle | Tâches |
|---|---|---|
| `core/navigation/.../testing/FixScript.kt` (nouveau) | positions GPS simulées déterministes | 1 |
| `core/navigation/.../testing/TestRoutes.kt` | jeux synthétiques `xy`, `twoRoundabouts`, `leftTurn`, `parallelRoads`, `overpass` | 1, 4, 9 |
| `core/navigation/.../GuidanceConstants.kt` | nouveaux seuils | 1, 7, 8, 9 |
| `core/navigation/.../RouteTracker.kt` | index monotone, distances, hors-itinéraire, continuité | 1, 3, 9 |
| `core/model/.../RouteProgress.kt`, `Route.kt` | `distancesToManeuversM`, types avec côté, champs Valhalla, `Lane.active`, `StreetSide` | 3, 4 |
| `core/common/.../Formatters.kt` | table d'arrondi commune, `maneuverDistance` | 3 |
| `core/routing/.../ValhallaDtos.kt`, `ValhallaRoutingProvider.kt`, `ValhallaLanes.kt`, `ValhallaManeuverTypes.kt` (nouveau) | parseur enrichi | 4 |
| fixtures `valhalla_motorway.json` (routing et navigation) | réponse FOSSGIS réelle | 5 |
| `core/navigation/.../RoundaboutResolver.kt` (nouveau), `NavigationState.kt` | `RoundaboutInfo` | 6 |
| `core/navigation/.../SpeechService.kt`, `SpeechQueue.kt` (nouveau), `AndroidSpeechService.kt`, `testing/FakeSpeechService.kt` | priorités et péremption | 7 |
| `core/navigation/.../AnnouncementPolicy.kt`, `NavigationSession.kt` | NOW par le temps, « puis », pas de FAR après manœuvre, priorités, persistance | 7, 8, 10 |
| `feature/navigation/.../NavigationUiState.kt`, `NavigationViewModel.kt`, `NavigationScreen.kt` | hystérésis, distances unifiées | 2, 3 |
| `core/map/.../GuidanceCameraPolicy.kt` | approche imposée, `exitLike` | 2, 4 |
| `core/designsystem/.../ManeuverIcon.kt` | icônes fidèles au côté | 4 |
| `core/data/.../UserPreferencesRepository.kt`, `testing/FakeUserPreferencesRepository.kt` | `lastGuidance` | 10 |

Préfixe des chemins : `core/navigation/src/main/java/fr/roadly/core/navigation/` (abrégé `nav/`) et `core/navigation/src/test/java/fr/roadly/core/navigation/` (abrégé `navtest/`).

---

### Tâche 1 : B1 — progression monotone, marge de passage, `FixScript`

**Fichiers :**
- Créer : `nav/testing/FixScript.kt`.
- Modifier : `nav/testing/TestRoutes.kt`. Ajouter `xy` et `twoRoundabouts` après `north`, ligne 70.
- Modifier : `nav/GuidanceConstants.kt`. Ajouter avant l'accolade finale, ligne 49.
- Modifier : `nav/RouteTracker.kt`, lignes 8-13 (imports), 54-58 (état), 67, 99-101, 117-125 et 150-168.
- Test : `navtest/RouteTrackerProgressTest.kt` (nouveau).

**Interfaces :**
- Consomme : `RouteTracker(route)`, `pointAt`, `maneuverStartM`, `totalGeometryM` et la fonction interne `bearing(a, b)`, tous existants.
- Produit :
  - `class FixScript(route: Route, seed: Int = 42)` avec :
    - `fun drive(fromAlongM: Double, toAlongM: Double, speedKmh: Double, noiseM: Double = 0.0, accuracyM: Float = 5f, lateralM: Double = 0.0, headingOffsetDeg: Double = 0.0, startMs: Long = 0L, periodMs: Long = 1_000L, gaps: Set<Int> = emptySet()): List<LocationFix>`
    - `fun jitter(alongM: Double, count: Int, amplitudeM: Double, accuracyM: Float = 5f, startMs: Long = 0L, periodMs: Long = 1_000L): List<LocationFix>`
  - `TestRoutes.xy(eastM: Double, northM: Double): GeoPoint`
  - `TestRoutes.twoRoundabouts(id: String = "twoRoundabouts", costing: RouteCosting = RouteCosting.FASTEST): Route`
  - `GuidanceConstants.MANEUVER_PASSED_MIN_M`, `BACKTRACK_CONFIRM_M`, `BACKTRACK_CONFIRM_FIXES`
  - `RouteProgress.nextManeuverIndex` devient monotone. Sa signature ne change pas.

- [ ] **Étape 1 : créer l'outillage de test (générateur et jeu de données).**

`nav/testing/FixScript.kt` :

```kotlin
package fr.roadly.core.navigation.testing

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.bearing
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Générateur déterministe de positions GPS le long d'un itinéraire (tests JVM) : vitesse constante, bruit gaussien,
 * décalage latéral, précision annoncée et trous. Même graine → mêmes fixes.
 */
class FixScript(route: Route, seed: Int = 42) {
    private val tracker = RouteTracker(route)
    private val random = Random(seed)

    /**
     * Fixes de [fromAlongM] à [toAlongM] (inclus) à [speedKmh], un toutes les [periodMs] depuis [startMs].
     * [noiseM] = écart-type du bruit gaussien (m, sur chaque axe) ; [lateralM] = décalage à gauche du sens de marche (route parallèle) ;
     * [headingOffsetDeg] = écart du cap GPS au cap du tracé ; [gaps] = rangs des fixes supprimés (perte GPS).
     */
    fun drive(
        fromAlongM: Double,
        toAlongM: Double,
        speedKmh: Double,
        noiseM: Double = 0.0,
        accuracyM: Float = 5f,
        lateralM: Double = 0.0,
        headingOffsetDeg: Double = 0.0,
        startMs: Long = 0L,
        periodMs: Long = 1_000L,
        gaps: Set<Int> = emptySet(),
    ): List<LocationFix> {
        val step = speedKmh / 3.6 * periodMs / 1000.0
        require(step > 0.0) { "Vitesse nulle : utiliser jitter()" }
        val n = ceil((toAlongM - fromAlongM) / step).toInt().coerceAtLeast(0)
        return (0..n).mapNotNull { i ->
            if (i in gaps) return@mapNotNull null
            val along = (fromAlongM + i * step).coerceAtMost(toAlongM)
            fixAt(along, noiseM, accuracyM, lateralM, headingOffsetDeg, speedKmh, startMs + i * periodMs)
        }
    }

    /** [count] fixes immobiles autour de l'abscisse [alongM] : bruit uniforme borné à ±[amplitudeM] le long du tracé et en travers. */
    fun jitter(alongM: Double, count: Int, amplitudeM: Double, accuracyM: Float = 5f, startMs: Long = 0L, periodMs: Long = 1_000L): List<LocationFix> =
        (0 until count).map { i ->
            val a = alongM + random.nextDouble(-amplitudeM, amplitudeM)
            val side = random.nextDouble(-amplitudeM, amplitudeM)
            LocationFix(offset(tracker.pointAt(a), routeBearing(a) - 90.0, side), null, 0f, accuracyM, startMs + i * periodMs)
        }

    private fun fixAt(along: Double, noiseM: Double, accuracyM: Float, lateralM: Double, headingOffsetDeg: Double, speedKmh: Double, t: Long): LocationFix {
        val b = routeBearing(along)
        var p = offset(tracker.pointAt(along), b - 90.0, lateralM)
        if (noiseM > 0.0) {
            p = offset(p, 0.0, gaussian() * noiseM)
            p = offset(p, 90.0, gaussian() * noiseM)
        }
        val heading = ((b + headingOffsetDeg) % 360.0 + 360.0) % 360.0
        return LocationFix(p, heading.toFloat(), (speedKmh / 3.6).toFloat(), accuracyM, t)
    }

    /** Cap du tracé à l'abscisse [along] (corde d'1 m vers l'avant). */
    private fun routeBearing(along: Double): Double {
        val a = along.coerceIn(0.0, (tracker.totalGeometryM - 1.0).coerceAtLeast(0.0))
        return bearing(tracker.pointAt(a), tracker.pointAt(a + 1.0))
    }

    /** [p] déplacé de [meters] dans la direction [bearingDeg] (plan local). */
    private fun offset(p: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        if (meters == 0.0) return p
        val rad = bearingDeg * PI / 180.0
        val dLat = meters * cos(rad) / TestRoutes.METERS_PER_DEG_LAT
        val dLon = meters * sin(rad) / (TestRoutes.METERS_PER_DEG_LAT * cos(p.lat * PI / 180.0))
        return GeoPoint(p.lat + dLat, p.lon + dLon)
    }

    /** Tirage gaussien centré réduit (Box-Muller). */
    private fun gaussian(): Double {
        val u1 = 1.0 - random.nextDouble()
        val u2 = random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}
```

Dans `nav/testing/TestRoutes.kt`, ajouter après `fun north(...)` (ligne 70), avant l'accolade finale :

```kotlin
    /** Point à [eastM] m à l'est et [northM] m au nord de (LAT, 2,300). */
    fun xy(eastM: Double, northM: Double): GeoPoint {
        val lonPerMeter = 1.0 / (METERS_PER_DEG_LAT * cos(LAT * PI / 180.0))
        return GeoPoint(LAT + northM / METERS_PER_DEG_LAT, 2.300 + eastM * lonPerMeter)
    }

    /**
     * Deux ronds-points rapprochés (scénario 6) : 600 m plein est (61 points, pas de 10 m) ; entrée 1 au point 20 (200 m), sortie au point 23,
     * entrée 2 au point 35 (350 m, 120 m après la sortie 1), sortie au point 38, arrivée au point 60.
     */
    fun twoRoundabouts(id: String = "twoRoundabouts", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = (0..60).map { xy(it * 10.0, 0.0) }
        val cum = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + pts[i - 1].distanceTo(pts[i]) }
        fun len(a: Int, b: Int) = cum[b] - cum[a]
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, len(0, 20), 24.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(
                ManeuverType.ROUNDABOUT_ENTER, "Entrez dans le rond-point et prenez la 2e sortie.", "Rond-point Nord", len(20, 23), 4.0, 20, 2,
                verbalPre = "Entrez dans le rond-point et prenez la 2e sortie.", verbalAlert = "Entrez dans le rond-point et prenez la 2e sortie.",
            ),
            Maneuver(
                ManeuverType.ROUNDABOUT_EXIT, "Quittez le rond-point dans Rue Médiane.", "Rue Médiane", len(23, 35), 14.0, 23, null,
                verbalPre = "Quittez le rond-point dans Rue Médiane.",
            ),
            Maneuver(
                ManeuverType.ROUNDABOUT_ENTER, "Entrez dans le rond-point et prenez la 1re sortie.", "Rond-point Sud", len(35, 38), 4.0, 35, 1,
                verbalPre = "Entrez dans le rond-point et prenez la 1re sortie.", verbalAlert = "Entrez dans le rond-point et prenez la 1re sortie.",
            ),
            Maneuver(
                ManeuverType.ROUNDABOUT_EXIT, "Quittez le rond-point dans Rue Finale.", "Rue Finale", len(38, 60), 26.0, 38, null,
                verbalPre = "Quittez le rond-point dans Rue Finale.",
            ),
            Maneuver(
                ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 60, null,
                verbalPre = "Votre destination est sur la droite.", verbalAlert = "Votre destination sera sur la droite.",
            ),
        )
        return Route(id, costing, cum.last(), 72.0, pts, maneuvers, hasToll = false, providerId = "test")
    }
```

- [ ] **Étape 2 : écrire les tests qui échouent.**

`navtest/RouteTrackerProgressTest.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** B1 : progression monotone, marge de passage, recul confirmé (scénarios 2 et 6). */
class RouteTrackerProgressTest {
    private val route = TestRoutes.straight()
    private fun fixAt(t: RouteTracker, along: Double, accuracyM: Float = 5f) = LocationFix(t.pointAt(along), null, null, accuracyM, 0L)

    @Test fun `jitter of 8 m around the maneuver point keeps the index for 10 fixes`() {
        val t = RouteTracker(route)
        val script = FixScript(route, seed = 7)
        val turnAt = t.maneuverStartM[1]
        script.drive(turnAt - 200.0, turnAt - 40.0, speedKmh = 30.0).forEach { t.update(it) }
        val indices = script.jitter(turnAt, count = 10, amplitudeM = 8.0).map { t.update(it).nextManeuverIndex }
        assertEquals(List(10) { 1 }, indices)
    }

    @Test fun `a maneuver is passed only 10 m beyond its point`() {
        val t = RouteTracker(route)
        val turnAt = t.maneuverStartM[1]
        assertEquals(1, t.update(fixAt(t, turnAt + 9.0)).nextManeuverIndex)
        assertEquals(2, t.update(fixAt(t, turnAt + 11.0)).nextManeuverIndex)
    }

    @Test fun `the passing margin is twice the accuracy when it exceeds 10 m`() {
        val t = RouteTracker(route)
        val turnAt = t.maneuverStartM[1]
        assertEquals(1, t.update(fixAt(t, turnAt + 30.0, accuracyM = 20f)).nextManeuverIndex)
        assertEquals(2, t.update(fixAt(t, turnAt + 41.0, accuracyM = 20f)).nextManeuverIndex)
    }

    @Test fun `once passed, jitter back into the margin never brings the index back`() {
        val t = RouteTracker(route)
        val script = FixScript(route, seed = 11)
        val turnAt = t.maneuverStartM[1]
        script.drive(turnAt - 100.0, turnAt + 30.0, speedKmh = 30.0).forEach { t.update(it) }
        assertEquals(2, t.lastProgress!!.nextManeuverIndex)
        // ±8 m autour de +15 m : une partie des fixes retombe dans la marge de 10 m, l'indice reste 2.
        val indices = script.jitter(turnAt + 15.0, count = 10, amplitudeM = 8.0).map { t.update(it).nextManeuverIndex }
        assertEquals(List(10) { 2 }, indices)
    }

    @Test fun `a confirmed move back of more than 30 m restores the previous maneuver`() {
        val t = RouteTracker(route)
        val turnAt = t.maneuverStartM[1]
        t.update(fixAt(t, turnAt - 20.0))
        t.update(fixAt(t, turnAt + 20.0))
        assertEquals(2, t.lastProgress!!.nextManeuverIndex)
        assertEquals("un seul fix en arrière ne suffit pas", 2, t.update(fixAt(t, turnAt - 15.0)).nextManeuverIndex)
        assertEquals(1, t.update(fixAt(t, turnAt - 20.0)).nextManeuverIndex)
    }

    @Test fun `two roundabouts 120 m apart are two distinct targets, none skipped`() {
        val r = TestRoutes.twoRoundabouts()
        val t = RouteTracker(r)
        val seen = FixScript(r, seed = 3).drive(0.0, t.totalGeometryM - 30.0, speedKmh = 30.0, noiseM = 3.0)
            .map { t.update(it).nextManeuverIndex }
        assertEquals("indices visités dans l'ordre, sans saut", listOf(1, 2, 3, 4, 5), seen.distinct())
        assertTrue("jamais de retour en arrière", seen.zipWithNext().all { (a, b) -> b >= a })
    }
}
```

- [ ] **Étape 3 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*RouteTrackerProgressTest*"`

Résultat attendu : ÉCHEC.
- Les tests jitter attendent `[1, 1, …]` et reçoivent des 2.
- `a maneuver is passed only 10 m beyond its point` attend 1 et reçoit 2.
- `a confirmed move back…` attend 2 et reçoit 1.

- [ ] **Étape 4 : écrire l'implémentation.**

Dans `nav/GuidanceConstants.kt`, avant l'accolade finale (ligne 49) :

```kotlin
    /** B1 : une manœuvre est passée quand `along` dépasse son début de max(10 m, 2 × précision du fix). */
    const val MANEUVER_PASSED_MIN_M = 10.0
    /** B1 : l'indice de manœuvre ne recule que sur un recul de plus de 30 m sous l'abscisse maximale atteinte… */
    const val BACKTRACK_CONFIRM_M = 30.0
    /** … confirmé par 2 fixes consécutifs. */
    const val BACKTRACK_CONFIRM_FIXES = 2
```

Dans `nav/RouteTracker.kt` :

1. Imports. Après `import kotlin.math.cos` (ligne 10), ajouter `import kotlin.math.max`.

2. État. Après la ligne 58 (`private var lastRealAlong = 0.0`), ajouter :

```kotlin
    /** Première manœuvre visée : le départ (0) est réputé effectué, sauf itinéraire réduit à une manœuvre. */
    private val firstTarget = min(1, lastManeuver)
    /** B1 : indice de la prochaine manœuvre retenu, monotone (voir [advanceIndex]). */
    private var committedNext = firstTarget
    /** Plus grande abscisse atteinte depuis le dernier changement d'indice : référence du recul confirmé. */
    private var peakAlong = 0.0
    private var backtrackFixes = 0
    /** Précision du dernier fix réel : marge de passage reprise par l'extrapolation. */
    private var lastAccuracyM = 0f
```

3. Ligne 67. Remplacer :

```kotlin
    fun initialProgress(): RouteProgress = progressAt(0, 0.0, 0.0, pts[0], bearing(pts[0], pts[1]).toFloat())
```

par :

```kotlin
    fun initialProgress(): RouteProgress = progressAt(0, 0.0, 0.0, pts[0], bearing(pts[0], pts[1]).toFloat(), firstTarget)
```

4. Ligne 101. Remplacer :

```kotlin
        val p = progressAt(best.segment, best.alongM, best.offM, best.snapped, heading)
```

par :

```kotlin
        lastAccuracyM = fix.accuracyM
        val p = progressAt(best.segment, best.alongM, best.offM, best.snapped, heading, advanceIndex(best.alongM, fix.accuracyM))
```

5. Lignes 124 et 125, dans `extrapolate`. Remplacer :

```kotlin
        val p = progressAt(seg, along, 0.0, pointAt(along), prev?.bearingDeg ?: bearing(pts[seg], pts[seg + 1]).toFloat())
            .copy(extrapolated = true)
```

par :

```kotlin
        val p = progressAt(seg, along, 0.0, pointAt(along), prev?.bearingDeg ?: bearing(pts[seg], pts[seg + 1]).toFloat(), advanceIndex(along, lastAccuracyM))
            .copy(extrapolated = true)
```

6. Lignes 150 à 153. Remplacer :

```kotlin
    private fun progressAt(segment: Int, along: Double, off: Double, snapped: GeoPoint, heading: Float?): RouteProgress {
        var next = lastManeuver
        for (k in maneuvers.indices) if (maneuverStartM[k] > along) { next = k; break }
        val c = (next - 1).coerceAtLeast(0)
```

par :

```kotlin
    /**
     * B1 : prochaine manœuvre, monotone. Elle avance quand `along` dépasse son début de max(10 m, 2 × précision) ; elle ne recule que sur
     * un recul confirmé (> 30 m sous l'abscisse maximale atteinte, 2 fixes consécutifs). Un recalcul crée un nouveau suivi (index remis à zéro).
     */
    private fun advanceIndex(along: Double, accuracyM: Float): Int {
        val margin = max(GuidanceConstants.MANEUVER_PASSED_MIN_M, 2.0 * accuracyM)
        var raw = firstTarget
        while (raw < lastManeuver && along > maneuverStartM[raw] + margin) raw++
        when {
            raw > committedNext -> { committedNext = raw; peakAlong = along; backtrackFixes = 0 }
            raw < committedNext && along < peakAlong - GuidanceConstants.BACKTRACK_CONFIRM_M -> {
                backtrackFixes++
                if (backtrackFixes >= GuidanceConstants.BACKTRACK_CONFIRM_FIXES) { committedNext = raw; peakAlong = along; backtrackFixes = 0 }
            }
            else -> backtrackFixes = 0
        }
        peakAlong = max(peakAlong, along)
        return committedNext
    }

    private fun progressAt(segment: Int, along: Double, off: Double, snapped: GeoPoint, heading: Float?, next: Int): RouteProgress {
        val c = (next - 1).coerceAtLeast(0)
```

Le reste de `progressAt` ne change pas.

- [ ] **Étape 5 : vérifier que les tests passent, puis lancer tout le module.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*RouteTrackerProgressTest*"`

Résultat attendu : SUCCÈS.

Lancer ensuite : `.\gradlew.bat :core:navigation:testDebugUnitTest :feature:navigation:testDebugUnitTest :feature:route:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS. Pourquoi aucun test existant ne casse :
- tous les points de `RouteTrackerTest` sont soit à plus de 10 m du début de la manœuvre précédente (vérifié sur la fixture Paris : écart minimal 53,8 m), soit exactement sur un début de manœuvre ;
- au départ, l'indice reste 1.

- [ ] **Étape 6 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/testing/FixScript.kt core/navigation/src/main/java/fr/roadly/core/navigation/testing/TestRoutes.kt core/navigation/src/main/java/fr/roadly/core/navigation/GuidanceConstants.kt core/navigation/src/main/java/fr/roadly/core/navigation/RouteTracker.kt core/navigation/src/test/java/fr/roadly/core/navigation/RouteTrackerProgressTest.kt
git commit -m @'
feat(navigation): index de manœuvre monotone et marge de passage (B1)

Une manœuvre est passée à max(10 m, 2 × précision) au-delà de son point ; l'index ne recule que sur un recul confirmé de plus de 30 m.
Ajoute FixScript (positions GPS déterministes) et TestRoutes.twoRoundabouts.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 2 : B1 — hystérésis de l'approche (écran et caméra)

**Où placer la règle ?** Dans `buildUiState`, sous la forme d'une fonction pure `approachLatched(previous, …)`.

- La règle d'approche (`GuidanceCameraPolicy.isApproaching`) vit dans `core:map`. `core:navigation` n'en dépend pas, et ne doit pas en dépendre : mettre un drapeau dans `RouteProgress` imposerait ce couplage.
- L'état précédent (`NavigationUiState`) contient déjà tout ce qu'il faut : `approach`, `route.id`, `progress.nextManeuverIndex`. La règle se teste donc sans coroutine.
- La caméra reçoit la décision de l'écran par un nouveau champ facultatif `GuidanceCameraInput.approaching`.

**Fichiers :**
- Modifier : `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationUiState.kt`, lignes 100, 101 et 123.
- Modifier : `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt`, lignes 98 et 99.
- Modifier : `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt`, ligne 149 (dans `NavigationContent`, aucune signature ne change).
- Modifier : `core/map/src/main/java/fr/roadly/core/map/GuidanceCameraPolicy.kt`, lignes 12-22 et 62.
- Test : `feature/navigation/src/test/java/fr/roadly/feature/navigation/BuildUiStateTest.kt` (nouveau) et `core/map/src/test/java/fr/roadly/core/map/GuidanceCameraPolicyTest.kt` (ajout).

**Interfaces :**
- Consomme : `NavigationState.Guiding`, `RouteTracker`, `TestRoutes.straight()`.
- Produit :
  - `internal fun buildUiState(s: NavigationState, muted: Boolean, following: Boolean, toleranceKmh: Int, styleUrl: String, zone: ZoneId, previous: NavigationUiState? = null): NavigationUiState`
  - `internal fun approachLatched(previous: NavigationUiState?, routeId: String, nextIndex: Int, approachingNow: Boolean): Boolean`
  - `GuidanceCameraInput(..., landscape: Boolean, approaching: Boolean? = null)`

- [ ] **Étape 1 : écrire les tests qui échouent.**

`feature/navigation/src/test/java/fr/roadly/feature/navigation/BuildUiStateTest.kt` :

```kotlin
package fr.roadly.feature.navigation

import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.RouteTracker
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** Formatage pur de l'état de guidage (B1 hystérésis, B2 distances). */
class BuildUiStateTest {
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val zone = ZoneId.of("Europe/Paris")

    /** État `Guiding` à l'abscisse [along] de [r] (suivi neuf, vitesse 36 km/h). */
    private fun guiding(along: Double, r: Route = route): NavigationState.Guiding {
        val t = RouteTracker(r)
        val p = t.update(LocationFix(t.pointAt(along), null, null, 5f, 0L))
        val ms = r.maneuvers
        val upcoming = ms.subList(minOf(p.nextManeuverIndex + 1, ms.size), minOf(p.nextManeuverIndex + 4, ms.size))
        return NavigationState.Guiding(r, p, 36.0, null, upcoming, emptyList(), 0L, gpsLost = false, rerouting = false)
    }

    private fun ui(s: NavigationState, previous: NavigationUiState? = null) =
        buildUiState(s, muted = false, following = true, toleranceKmh = 5, styleUrl = "", zone = zone, previous = previous)

    @Test fun `approach stays on when the distance flickers back above the threshold`() {
        val turnAt = tracker.maneuverStartM[1]
        val near = ui(guiding(turnAt - 140.0))
        assertNotNull(near.approach)
        assertNotNull("hystérésis : l'approche reste active", ui(guiding(turnAt - 160.0), previous = near).approach)
        assertNull("sans état précédent, 160 m n'est pas une approche", ui(guiding(turnAt - 160.0)).approach)
    }

    @Test fun `approach is released when the next maneuver changes`() {
        val turnAt = tracker.maneuverStartM[1]
        val near = ui(guiding(turnAt - 140.0))
        val after = ui(guiding(turnAt + 20.0), previous = near)
        assertEquals(2, after.progress!!.nextManeuverIndex)
        assertNull("l'arrivée n'a jamais d'approche", after.approach)
    }

    @Test fun `approach of another route is not carried over`() {
        val turnAt = tracker.maneuverStartM[1]
        val near = ui(guiding(turnAt - 140.0))
        assertNull(ui(guiding(turnAt - 160.0, TestRoutes.straight("rerouted")), previous = near).approach)
    }
}
```

Dans `core/map/src/test/java/fr/roadly/core/map/GuidanceCameraPolicyTest.kt`, ajouter avant l'accolade finale de la classe :

```kotlin
    @Test fun `an approach decided by the screen overrides the distance rule`() {
        assertEquals(GuidanceCameraPolicy.PITCH_APPROACH, GuidanceCameraPolicy.compute(input(distance = 160.0).copy(approaching = true), null).pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_3D, GuidanceCameraPolicy.compute(input(distance = 100.0).copy(approaching = false), null).pitch, 1e-9)
    }
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :feature:navigation:testDebugUnitTest :core:map:testDebugUnitTest --offline -q --tests "*BuildUiStateTest*" --tests "*GuidanceCameraPolicyTest*"`

Résultat attendu : ÉCHEC de compilation (« No parameter with name 'previous' », « No parameter with name 'approaching' »).

- [ ] **Étape 3 : écrire l'implémentation.**

Dans `core/map/.../GuidanceCameraPolicy.kt`, remplacer :

```kotlin
    val landscape: Boolean,
)
```

par :

```kotlin
    val landscape: Boolean,
    /** Approche décidée par l'écran (hystérésis B1) ; null = règle de distance [GuidanceCameraPolicy.isApproaching]. */
    val approaching: Boolean? = null,
)
```

Puis remplacer :

```kotlin
        val approaching = isApproaching(input.distanceToManeuverM, input.maneuverType, input.speedKmh)
```

par :

```kotlin
        val approaching = input.approaching ?: isApproaching(input.distanceToManeuverM, input.maneuverType, input.speedKmh)
```

Dans `NavigationUiState.kt`, remplacer les lignes 100 et 101 :

```kotlin
/** Formatage pur (testé via le ViewModel) : distances et durées par `Formatters`, comme la v1. */
internal fun buildUiState(s: NavigationState, muted: Boolean, following: Boolean, toleranceKmh: Int, styleUrl: String, zone: ZoneId): NavigationUiState {
```

par :

```kotlin
/** B1 : une approche reste active jusqu'au changement de manœuvre visée (pas de clignotement au seuil des 150 / 400 m). */
internal fun approachLatched(previous: NavigationUiState?, routeId: String, nextIndex: Int, approachingNow: Boolean): Boolean {
    if (approachingNow) return true
    val prev = previous ?: return false
    return prev.approach != null && prev.route?.id == routeId && prev.progress?.nextManeuverIndex == nextIndex
}

/** Formatage pur (testé via le ViewModel) ; [previous] = dernier état produit (hystérésis de l'approche, B1). */
internal fun buildUiState(s: NavigationState, muted: Boolean, following: Boolean, toleranceKmh: Int, styleUrl: String, zone: ZoneId, previous: NavigationUiState? = null): NavigationUiState {
```

Puis remplacer la ligne 123 :

```kotlin
                approach = if (GuidanceCameraPolicy.isApproaching(p.distanceToNextManeuverM, m.type, s.speedKmh)) ApproachUi(m.type, m.exitNumber, m.exitToward, m.beginShapeIndex) else null,
```

par :

```kotlin
                approach = if (approachLatched(previous, s.route.id, p.nextManeuverIndex, GuidanceCameraPolicy.isApproaching(p.distanceToNextManeuverM, m.type, s.speedKmh))) {
                    ApproachUi(m.type, m.exitNumber, m.exitToward, m.beginShapeIndex)
                } else null,
```

Dans `NavigationViewModel.kt`, remplacer les lignes 98 et 99 :

```kotlin
    val uiState: StateFlow<NavigationUiState> = combine(session.state, session.muted, following, prefs.speedAlertToleranceKmh, styleUrl) { s, muted, follow, tolerance, style ->
        buildUiState(s, muted = muted, following = follow, toleranceKmh = tolerance, styleUrl = style, zone = ZoneId.systemDefault())
```

par :

```kotlin
    /** Dernier état produit : hystérésis de l'approche (B1). */
    private var lastUi: NavigationUiState? = null

    val uiState: StateFlow<NavigationUiState> = combine(session.state, session.muted, following, prefs.speedAlertToleranceKmh, styleUrl) { s, muted, follow, tolerance, style ->
        buildUiState(s, muted = muted, following = follow, toleranceKmh = tolerance, styleUrl = style, zone = ZoneId.systemDefault(), previous = lastUi).also { lastUi = it }
```

Dans `NavigationScreen.kt`, remplacer la ligne 149 :

```kotlin
                GuidanceCameraInput(state.speedKmh, progress.bearingDeg, progress.distanceToNextManeuverM, next.type, threeD, powerSave, landscape),
```

par :

```kotlin
                GuidanceCameraInput(state.speedKmh, progress.bearingDeg, progress.distanceToNextManeuverM, next.type, threeD, powerSave, landscape, approaching = approach != null),
```

- [ ] **Étape 4 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :feature:navigation:testDebugUnitTest :core:map:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS, y compris `NavigationViewModelTest` (approche à 82 m).

- [ ] **Étape 5 : commiter.**

```powershell
git add feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationUiState.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt core/map/src/main/java/fr/roadly/core/map/GuidanceCameraPolicy.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/BuildUiStateTest.kt core/map/src/test/java/fr/roadly/core/map/GuidanceCameraPolicyTest.kt
git commit -m @'
feat(navigation): hystérésis de l'approche jusqu'au changement de manœuvre (B1)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 3 : B2 — base de distance unique et table d'arrondi partagée

**Fichiers :**
- Modifier : `core/model/src/main/java/fr/roadly/core/model/RouteProgress.kt`, lignes 18 et 19.
- Modifier : `nav/RouteTracker.kt`, dans `progressAt` : ligne `distanceToNextManeuverM` et nouvelle fonction.
- Modifier : `core/common/src/main/java/fr/roadly/core/common/Formatters.kt`, après la ligne 17.
- Modifier : `nav/AnnouncementPolicy.kt`, lignes 5, 6 et 64-71.
- Modifier : `nav/NavigationNotifications.kt`, ligne 36.
- Modifier : `feature/navigation/.../NavigationUiState.kt`, lignes 112-117 et 122.
- Test : `core/common/src/test/java/fr/roadly/core/common/FormattersTest.kt`, `navtest/SpokenPhrasesTest.kt`, `navtest/RouteTrackerProgressTest.kt` et `BuildUiStateTest.kt` (ajouts) ; `NavigationViewModelTest.kt`, lignes 125 et 130 (contrat).

**Interfaces :**
- Consomme : `RouteTracker.advanceIndex` (tâche 1), `buildUiState(previous)` (tâche 2).
- Produit :
  - `RouteProgress.distancesToManeuversM: List<Double> = emptyList()`. Ce sont les distances aux manœuvres next+1 … next+3, alignées sur `Guiding.upcoming`. Elles sont mises à l'échelle `distScale`, comme `distanceToNextManeuverM`.
  - `Formatters.roundManeuverMeters(meters: Double): Int`
  - `Formatters.maneuverDistance(meters: Double): String`
  - `SpokenPhrases.distance` utilise la table commune.

- [ ] **Étape 1 : écrire les tests qui échouent.**

Dans `FormattersTest.kt`, ajouter :

```kotlin
    @Test fun `maneuver distance uses the voice rounding`() {
        assertEquals("450 m", Formatters.maneuverDistance(430.0))
        assertEquals("850 m", Formatters.maneuverDistance(846.0))
        assertEquals("50 m", Formatters.maneuverDistance(47.0))
        assertEquals("10 m", Formatters.maneuverDistance(8.0))
        assertEquals("1,0 km", Formatters.maneuverDistance(980.0))
        assertEquals("1,2 km", Formatters.maneuverDistance(1234.0))
        assertEquals("12,3 km", Formatters.maneuverDistance(12_340.0))
    }

    @Test fun `rounding table - 10 m under 100 m, 50 m under 1 km, 100 m beyond`() {
        assertEquals(70, Formatters.roundManeuverMeters(72.0))
        assertEquals(450, Formatters.roundManeuverMeters(430.0))
        assertEquals(1_500, Formatters.roundManeuverMeters(1_540.0))
    }
```

Dans `navtest/SpokenPhrasesTest.kt`, ajouter :

```kotlin
    @Test fun `430 m is spoken 450 meters like the banner shows 450 m`() {
        assertEquals("450 mètres", SpokenPhrases.distance(430.0))
        assertEquals("450 m", fr.roadly.core.common.Formatters.maneuverDistance(430.0))
    }
```

Dans `navtest/RouteTrackerProgressTest.kt`, ajouter :

```kotlin
    @Test fun `distances to the following maneuvers share the banner base`() {
        val paris = ParisRoute.route()
        val t = RouteTracker(paris)
        val scale = paris.distanceMeters / t.totalGeometryM
        val p = t.update(LocationFix(paris.geometry[100], null, null, 5f, 0L))
        assertEquals(7, p.nextManeuverIndex)
        assertEquals((t.maneuverStartM[7] - p.distanceAlongM) * scale, p.distanceToNextManeuverM, 1e-6)
        assertEquals(3, p.distancesToManeuversM.size)
        p.distancesToManeuversM.forEachIndexed { i, d -> assertEquals((t.maneuverStartM[8 + i] - p.distanceAlongM) * scale, d, 1e-6) }
        val nearEnd = RouteTracker(paris).update(LocationFix(paris.geometry[290], null, null, 5f, 0L))
        assertEquals(12, nearEnd.nextManeuverIndex)
        assertEquals("seule l'arrivée suit", 1, nearEnd.distancesToManeuversM.size)
    }
```

Dans `BuildUiStateTest.kt`, ajouter (avec l'import `fr.roadly.core.common.Formatters`) :

```kotlin
    @Test fun `horizon and banner use the same base and ignore Valhalla lengths`() {
        val skewed = route.copy(maneuvers = route.maneuvers.mapIndexed { i, m -> if (i == 1) m.copy(lengthMeters = 9_999.0) else m })
        val ui = ui(guiding(300.0, skewed))
        val t = RouteTracker(skewed)
        assertEquals(t.maneuverStartM[2] - t.maneuverStartM[1], ui.upcoming[0].distanceM - ui.progress!!.distanceToNextManeuverM, 1.0)
        assertEquals(Formatters.maneuverDistance(ui.progress!!.distanceToNextManeuverM), ui.banner!!.distanceText)
        assertEquals(Formatters.maneuverDistance(ui.upcoming[0].distanceM), ui.upcoming[0].distanceText)
    }

    @Test fun `430 m reads 450 m on the banner`() {
        assertEquals("450 m", ui(guiding(tracker.maneuverStartM[1] - 430.0)).banner!!.distanceText)
    }
```

Adapter le contrat dans `NavigationViewModelTest.kt`. Remplacer la ligne 125 :

```kotlin
            assertEquals(Formatters.distance(g.progress.distanceToNextManeuverM), ui.banner!!.distanceText)
```

par :

```kotlin
            assertEquals(Formatters.maneuverDistance(g.progress.distanceToNextManeuverM), ui.banner!!.distanceText)
```

Puis remplacer la ligne 130 :

```kotlin
            assertEquals(Formatters.distance(g.progress.distanceToNextManeuverM + route.maneuvers[1].lengthMeters), ui.upcoming[0].distanceText)
```

par :

```kotlin
            assertEquals(Formatters.maneuverDistance(g.progress.distancesToManeuversM[0]), ui.upcoming[0].distanceText)
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:common:test --offline -q --tests "*FormattersTest*"`

Résultat attendu : ÉCHEC de compilation (`maneuverDistance` inconnu).

- [ ] **Étape 3 : écrire l'implémentation.**

`RouteProgress.kt`. Remplacer :

```kotlin
    val extrapolated: Boolean = false,
)
```

par :

```kotlin
    val extrapolated: Boolean = false,
    /** B2 : distances (m) aux manœuvres next+1 … next+3 (alignées sur `Guiding.upcoming`), même base que [distanceToNextManeuverM]. */
    val distancesToManeuversM: List<Double> = emptyList(),
)
```

`RouteTracker.kt`, dans `progressAt`. Remplacer :

```kotlin
            distanceToNextManeuverM = (maneuverStartM[next] - along).coerceAtLeast(0.0),
```

par :

```kotlin
            distanceToNextManeuverM = distanceTo(next, along),
            distancesToManeuversM = (next + 1..min(next + GuidanceConstants.UPCOMING_COUNT, lastManeuver)).map { distanceTo(it, along) },
```

Puis ajouter juste après la fonction `progressAt` :

```kotlin
    /** B2 : distance (m) de [along] au début de la manœuvre [k], le long du tracé mis à l'échelle de la longueur Valhalla (`distScale`). */
    private fun distanceTo(k: Int, along: Double): Double = (maneuverStartM[k] - along).coerceAtLeast(0.0) * distScale
```

`Formatters.kt`. Après la fonction `distance` (ligne 17), ajouter :

```kotlin
    /** Table d'arrondi commune à la voix et à l'écran (B2) : < 100 m → 10 m près (min. 10) ; < 1 km → 50 m près ; au-delà → 100 m près. */
    fun roundManeuverMeters(meters: Double): Int = when {
        meters < 100.0 -> ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10)
        meters < 1000.0 -> (meters / 50.0).roundToInt() * 50
        else -> (meters / 100.0).roundToInt() * 100
    }

    /** Distance à une manœuvre (bandeau, Horizon, notification), même arrondi que la voix : « 450 m », « 1,2 km ». */
    fun maneuverDistance(meters: Double): String {
        val r = roundManeuverMeters(meters)
        return if (r < 1000) "$r m" else String.format(fr, "%.1f km", r / 1000.0)
    }
```

`AnnouncementPolicy.kt`. Remplacer l'import `import kotlin.math.roundToInt` par `import fr.roadly.core.common.Formatters`. Puis remplacer les lignes 64 à 67 :

```kotlin
    /** < 100 m : arrondi à 10 m (min. 10) ; sinon à 50 m ; ≥ 1 km : kilomètres à 0,1 près. */
    fun distance(meters: Double): String {
        val rounded = if (meters < 100.0) ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10) else (meters / 50.0).roundToInt() * 50
        if (rounded < 1000) return "$rounded mètres"
        val km = (meters / 100.0).roundToInt() / 10.0
```

par :

```kotlin
    /** Arrondi partagé avec l'écran (`Formatters.roundManeuverMeters`, B2) ; ≥ 1 km : kilomètres à 0,1 près. */
    fun distance(meters: Double): String {
        val rounded = Formatters.roundManeuverMeters(meters)
        if (rounded < 1000) return "$rounded mètres"
        val km = rounded / 1000.0
```

`NavigationNotifications.kt`, ligne 36. Remplacer `Formatters.distance(state.progress.distanceToNextManeuverM)` par `Formatters.maneuverDistance(state.progress.distanceToNextManeuverM)`.

`NavigationUiState.kt`. Remplacer les lignes 112 à 117 :

```kotlin
            // Distance jusqu'à la manœuvre next+1+i = distance à next + longueurs Valhalla de next … next+i.
            var acc = p.distanceToNextManeuverM
            val upcoming = s.upcoming.mapIndexed { i, u ->
                acc += ms[next + i].lengthMeters
                UpcomingUi(u.type, Formatters.distance(acc), u.streetName, acc)
            }
```

par :

```kotlin
            // B2 : distances aux manœuvres suivantes fournies par le suivi, sur la même abscisse que le bandeau ; sans distance, pas d'élément.
            val upcoming = s.upcoming.mapIndexedNotNull { i, u ->
                val d = p.distancesToManeuversM.getOrNull(i) ?: return@mapIndexedNotNull null
                UpcomingUi(u.type, Formatters.maneuverDistance(d), u.streetName, d)
            }
```

Ligne 122 : remplacer `Formatters.distance(p.distanceToNextManeuverM)` par `Formatters.maneuverDistance(p.distanceToNextManeuverM)`.

**LIMITE (à écrire dans la KDoc de `hazardsAhead`) :** les virages et les zones gardent les abscisses de la géométrie brute. L'écart avec les manœuvres est inférieur à 2 % (`RouteTrackerTest` : 0,06 % sur Paris).

- [ ] **Étape 4 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:common:test :core:model:test :core:navigation:testDebugUnitTest :feature:navigation:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS.
- `SpokenPhrasesTest.distances are rounded for speech` reste vert : 980 donne « 1 kilomètre », 12 340 donne « 12,3 kilomètres ».
- `RouteTrackerTest.next maneuver index and distance to it` reste dans sa tolérance de 0,05 m : l'écart d'échelle est de 0,027 m au plus sur Paris.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/model/src/main/java/fr/roadly/core/model/RouteProgress.kt core/navigation/src/main/java/fr/roadly/core/navigation/RouteTracker.kt core/common/src/main/java/fr/roadly/core/common/Formatters.kt core/navigation/src/main/java/fr/roadly/core/navigation/AnnouncementPolicy.kt core/navigation/src/main/java/fr/roadly/core/navigation/NavigationNotifications.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationUiState.kt core/common/src/test/java/fr/roadly/core/common/FormattersTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/SpokenPhrasesTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/RouteTrackerProgressTest.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/BuildUiStateTest.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt
git commit -m @'
feat(navigation): base de distance unique et arrondi commun voix / écran (B2)

Les distances de l'Horizon viennent du suivi (même abscisse que le bandeau, mise à l'échelle Valhalla) au lieu des longueurs Valhalla ;
430 m s'affiche « 450 m » et se dit « 450 mètres ».

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 4 : B3 — modèle de manœuvre enrichi et parseur Valhalla

**Fichiers :**
- Modifier : `core/model/src/main/java/fr/roadly/core/model/Route.kt`, lignes 5-36.
- Créer : `core/routing/src/main/java/fr/roadly/core/routing/ValhallaManeuverTypes.kt`.
- Modifier : `core/routing/src/main/java/fr/roadly/core/routing/ValhallaDtos.kt`, lignes 10 et 13-27.
- Modifier : `core/routing/src/main/java/fr/roadly/core/routing/ValhallaRoutingProvider.kt`, lignes 144, 148, 162-163 et 182-203.
- Modifier : `core/routing/src/main/java/fr/roadly/core/routing/ValhallaLanes.kt`, ligne 40.
- Modifier : `core/designsystem/src/main/java/fr/roadly/core/designsystem/component/ManeuverIcon.kt`, lignes 11-21 et 33-51.
- Modifier : `core/map/src/main/java/fr/roadly/core/map/GuidanceCameraPolicy.kt`, ligne 43.
- Modifier : `nav/testing/TestRoutes.kt`, lignes 41 et 54 ; ajout de `leftTurn`.
- Créer : `navtest/ValhallaFixture.kt`. Remplacer le contenu de `navtest/ParisRoute.kt`.
- Test :
  - `core/model/src/test/java/fr/roadly/core/model/GuidanceModelTest.kt` (ajouts) ;
  - `core/routing/src/test/java/fr/roadly/core/routing/ValhallaManeuverTypesTest.kt` (nouveau) ;
  - `ValhallaRoutingProviderTest.kt` (ajout, et contrat ligne 206) ;
  - `ValhallaLanesTest.kt` (contrat ligne 35) ;
  - `core/designsystem/src/test/java/fr/roadly/core/designsystem/ManeuverIconTest.kt` (nouveau) ;
  - `GuidanceCameraPolicyTest.kt` (contrat lignes 48-51) ;
  - `navtest/RouteTrackerProgressTest.kt` (ajout).

**Tous les `when` et ensembles portant sur `ManeuverType`** (relevé exhaustif par `grep`) :
- `ManeuverIcon.kt:33-51`, à mettre à jour ;
- `GuidanceCameraPolicy.kt:43` (`exitLike`) et `:44` (`neverApproach`, inchangé) ;
- `ValhallaRoutingProvider.kt:182-203` (`mapType`), qui délègue désormais ;
- `ParisRoute.kt` (`type()`), remplacé par la table publique ;
- `NavigationScreen.kt:324` : correspondance vers `HorizonKind` (`ARRIVE → ARRIVAL`, sinon `MANEUVER`). Aucun changement.

`AnnouncementPolicy` n'a aucun `when` sur le type. La tâche 8 n'y ajoute qu'un test `== ROUNDABOUT_EXIT`.

Les anciennes valeurs `UTURN` et `EXIT` sont **supprimées**. Elles n'apparaissaient que dans `ManeuverIcon`, `GuidanceCameraPolicy`, sa classe de test, `TestRoutes` et `mapType`. `RAMP` (Valhalla 17, tout droit) et `MERGE` (Valhalla 25, sans côté) **restent** : ce sont des types Valhalla réels sans côté.

**Interfaces :**
- Produit :
  - `enum class ManeuverType { DEPART, ARRIVE, CONTINUE, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT, UTURN_LEFT, UTURN_RIGHT, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT, MERGE, MERGE_LEFT, MERGE_RIGHT, RAMP, RAMP_LEFT, RAMP_RIGHT, EXIT_LEFT, EXIT_RIGHT, KEEP_LEFT, KEEP_RIGHT, FERRY, OTHER }`
  - `enum class StreetSide { LEFT, RIGHT }`
  - `fun normalizeSignedDeg(deg: Double): Double` (résultat dans ]−180, 180])
  - `Lane(directions: Set<LaneDirection>, valid: Boolean, active: Boolean = false)`
  - Nouveaux champs de `Maneuver`, tous avec une valeur par défaut : `bearingBeforeDeg: Int?`, `bearingAfterDeg: Int?`, `endShapeIndex: Int?`, `verbalPost: String?`, `streetNames: List<String>`, `sideOfStreet: StreetSide?`. Propriété calculée `turnAngleDeg: Double?`.
  - `object ValhallaManeuverTypes { fun of(t: Int): ManeuverType }`
  - `internal class ValhallaFixture(resource: String, id: String) { val rawManeuvers: List<JsonObject>; fun route(): Route }`, dans les tests de `core:navigation`.
  - `TestRoutes.leftTurn(id: String = "leftTurn", costing: RouteCosting = RouteCosting.FASTEST): Route`

- [ ] **Étape 1 : écrire les tests qui échouent.**

Dans `GuidanceModelTest.kt`, ajouter :

```kotlin
    @Test fun `turn angle is the signed bearing difference normalised to -180 180`() {
        fun m(before: Int?, after: Int?) = Maneuver(ManeuverType.TURN_LEFT, "", null, 0.0, 0.0, 0, null, bearingBeforeDeg = before, bearingAfterDeg = after)
        assertEquals(-90.0, m(90, 0).turnAngleDeg!!, 0.0)
        assertEquals(90.0, m(350, 80).turnAngleDeg!!, 0.0)
        assertEquals(-20.0, m(10, 350).turnAngleDeg!!, 0.0)
        assertEquals(180.0, m(0, 180).turnAngleDeg!!, 0.0)
        assertNull(m(null, 90).turnAngleDeg)
    }

    @Test fun `new valhalla fields default to absent and lanes are not active by default`() {
        val m = Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche.", "Rue X", 120.0, 30.0, 4, null)
        assertNull(m.bearingBeforeDeg); assertNull(m.endShapeIndex); assertNull(m.verbalPost); assertNull(m.sideOfStreet)
        assertTrue(m.streetNames.isEmpty())
        assertEquals(false, Lane(setOf(LaneDirection.LEFT), true).active)
    }
```

Créer `core/routing/src/test/java/fr/roadly/core/routing/ValhallaManeuverTypesTest.kt` :

```kotlin
package fr.roadly.core.routing

import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Test

class ValhallaManeuverTypesTest {
    @Test fun `ramps, exits, merges and u-turns keep their side`() {
        mapOf(
            12 to ManeuverType.UTURN_RIGHT, 13 to ManeuverType.UTURN_LEFT,
            17 to ManeuverType.RAMP, 18 to ManeuverType.RAMP_RIGHT, 19 to ManeuverType.RAMP_LEFT,
            20 to ManeuverType.EXIT_RIGHT, 21 to ManeuverType.EXIT_LEFT,
            22 to ManeuverType.CONTINUE, 23 to ManeuverType.KEEP_RIGHT, 24 to ManeuverType.KEEP_LEFT,
            25 to ManeuverType.MERGE, 37 to ManeuverType.MERGE_RIGHT, 38 to ManeuverType.MERGE_LEFT,
            26 to ManeuverType.ROUNDABOUT_ENTER, 27 to ManeuverType.ROUNDABOUT_EXIT,
            1 to ManeuverType.DEPART, 6 to ManeuverType.ARRIVE, 99 to ManeuverType.OTHER,
        ).forEach { (code, type) -> assertEquals("type Valhalla $code", type, ValhallaManeuverTypes.of(code)) }
    }
}
```

Dans `ValhallaRoutingProviderTest.kt`, ajouter l'import `fr.roadly.core.model.StreetSide`, puis ajouter ce test avant l'accolade finale :

```kotlin
    @Test fun `bearings, end index, post instruction, street names and side come from the fixture`() = runTest {
        server.enqueue(MockResponse().setBody(fixture()))
        val r = (provider.route(RouteRequest(origin, dest, costing = RouteCosting.FASTEST)) as Outcome.Success).value.single()
        rawManeuvers().forEachIndexed { i, raw ->
            val m = raw.jsonObject
            assertEquals("bearing_before $i", m["bearing_before"]?.jsonPrimitive?.int, r.maneuvers[i].bearingBeforeDeg)
            assertEquals("bearing_after $i", m["bearing_after"]?.jsonPrimitive?.int, r.maneuvers[i].bearingAfterDeg)
            assertEquals("end_shape_index $i", m["end_shape_index"]?.jsonPrimitive?.int, r.maneuvers[i].endShapeIndex)
            assertEquals("verbal_post $i", m["verbal_post_transition_instruction"]?.jsonPrimitive?.content, r.maneuvers[i].verbalPost)
            assertEquals("street_names $i", (m["street_names"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(), r.maneuvers[i].streetNames)
        }
        // Relevé dans la fixture Paris : entrée du rond-point des Champs-Élysées (manœuvre 10) et destination à gauche.
        assertEquals(296, r.maneuvers[10].bearingBeforeDeg)
        assertEquals(355, r.maneuvers[10].bearingAfterDeg)
        assertEquals(224, r.maneuvers[10].endShapeIndex)
        assertNull(r.maneuvers[0].bearingBeforeDeg)
        assertEquals(StreetSide.LEFT, r.maneuvers.last().sideOfStreet)
        assertNull(r.maneuvers[5].sideOfStreet)
    }
```

Adapter le contrat de `ValhallaRoutingProviderTest.kt`. Remplacer la ligne 206 :

```kotlin
            listOf(Lane(setOf(LaneDirection.LEFT), true), Lane(setOf(LaneDirection.STRAIGHT), false), Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), true)),
```

par :

```kotlin
            listOf(Lane(setOf(LaneDirection.LEFT), true), Lane(setOf(LaneDirection.STRAIGHT), false), Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), valid = true, active = true)),
```

Adapter le contrat de `ValhallaLanesTest.kt`. Remplacer la ligne 35 :

```kotlin
                Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), true),
```

par :

```kotlin
                Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.RIGHT), valid = true, active = true),
```

Adapter le contrat de `GuidanceCameraPolicyTest.kt`. Remplacer les lignes 48 à 51 :

```kotlin
        assertTrue(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT, 100.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT, 50.0))
        assertTrue(GuidanceCameraPolicy.isApproaching(140.0, ManeuverType.EXIT, 50.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(410.0, ManeuverType.EXIT, 100.0))
```

par :

```kotlin
        assertTrue(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT_RIGHT, 100.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT_RIGHT, 50.0))
        assertTrue(GuidanceCameraPolicy.isApproaching(140.0, ManeuverType.EXIT_RIGHT, 50.0))
        assertFalse(GuidanceCameraPolicy.isApproaching(410.0, ManeuverType.EXIT_RIGHT, 100.0))
        // scénario 7 : approche à 400 m pour toute sortie / bretelle, quel que soit le côté
        assertTrue(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.EXIT_LEFT, 100.0))
        assertTrue(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.RAMP_LEFT, 100.0))
        assertTrue(GuidanceCameraPolicy.isApproaching(390.0, ManeuverType.MERGE_RIGHT, 100.0))
```

Créer `core/designsystem/src/test/java/fr/roadly/core/designsystem/ManeuverIconTest.kt` :

```kotlin
package fr.roadly.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import fr.roadly.core.designsystem.component.icon
import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Test

/** B3 : l'icône suit le côté donné par Valhalla (scénario 3 : virage à gauche). */
class ManeuverIconTest {
    @Test fun `left and right variants get their own icon`() {
        ManeuverType.entries.forEach { it.icon() }
        assertEquals(Icons.Default.TurnLeft.name, ManeuverType.TURN_LEFT.icon().name)
        assertEquals(Icons.Default.RampLeft.name, ManeuverType.RAMP_LEFT.icon().name)
        assertEquals(Icons.Default.RampRight.name, ManeuverType.RAMP_RIGHT.icon().name)
        assertEquals(Icons.Default.ForkLeft.name, ManeuverType.EXIT_LEFT.icon().name)
        assertEquals(Icons.Default.UTurnRight.name, ManeuverType.UTURN_RIGHT.icon().name)
    }
}
```

Repli : si ce test échoue sur JVM avec « Method … not mocked » (Android est absent sous JVM), le supprimer. Le `when` exhaustif suffit alors comme garde, avec `assembleDebug` à la tâche 10. Noter ce repli dans le commit.

Dans `navtest/RouteTrackerProgressTest.kt`, ajouter (imports `fr.roadly.core.model.ManeuverType`) :

```kotlin
    @Test fun `left turn has a negative angle and a stable index under jitter`() { // scénario 3
        val r = TestRoutes.leftTurn()
        assertEquals(ManeuverType.TURN_LEFT, r.maneuvers[1].type)
        assertEquals(-90.0, r.maneuvers[1].turnAngleDeg!!, 0.0)
        val t = RouteTracker(r)
        val script = FixScript(r, seed = 5)
        val turnAt = t.maneuverStartM[1]
        script.drive(0.0, turnAt - 30.0, speedKmh = 30.0).forEach { t.update(it) }
        assertEquals(List(10) { 1 }, script.jitter(turnAt, count = 10, amplitudeM = 8.0).map { t.update(it).nextManeuverIndex })
    }
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:model:test --offline -q`

Résultat attendu : ÉCHEC de compilation (`bearingBeforeDeg` inconnu).

- [ ] **Étape 3 : écrire l'implémentation (modèle).**

`Route.kt`. Remplacer les lignes 5 à 8 :

```kotlin
enum class ManeuverType {
    DEPART, ARRIVE, CONTINUE, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT,
    UTURN, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT, MERGE, RAMP, EXIT, KEEP_LEFT, KEEP_RIGHT, FERRY, OTHER,
}
```

par :

```kotlin
/** Types de manœuvre ; le côté des bretelles, sorties, fusions et demi-tours est conservé (B3). */
enum class ManeuverType {
    DEPART, ARRIVE, CONTINUE, TURN_LEFT, TURN_RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT, SHARP_LEFT, SHARP_RIGHT,
    UTURN_LEFT, UTURN_RIGHT, ROUNDABOUT_ENTER, ROUNDABOUT_EXIT,
    /** Fusion sans côté indiqué (Valhalla 25). */
    MERGE, MERGE_LEFT, MERGE_RIGHT,
    /** Bretelle tout droit (Valhalla 17). */
    RAMP, RAMP_LEFT, RAMP_RIGHT,
    EXIT_LEFT, EXIT_RIGHT, KEEP_LEFT, KEEP_RIGHT, FERRY, OTHER,
}

/** Côté de la chaussée où se trouve une étape (`locations[].side_of_street`). */
enum class StreetSide { LEFT, RIGHT }

/** Ramène un angle (°) dans ]−180, 180]. */
fun normalizeSignedDeg(deg: Double): Double {
    val r = ((deg % 360.0) + 360.0) % 360.0
    return if (r > 180.0) r - 360.0 else r
}
```

Remplacer les lignes 13 et 14 :

```kotlin
/** Une voie à l'approche d'une manœuvre ; [valid] = la voie permet d'effectuer la manœuvre. */
data class Lane(val directions: Set<LaneDirection>, val valid: Boolean)
```

par :

```kotlin
/** Une voie à l'approche d'une manœuvre ; [valid] = elle permet la manœuvre ; [active] = voie conseillée par Valhalla (toujours valide). */
data class Lane(val directions: Set<LaneDirection>, val valid: Boolean, val active: Boolean = false)
```

Remplacer les lignes 34 à 36 :

```kotlin
    /** Route signalée sur le panneau de sortie (`sign.exit_branch_elements[0]`), p. ex. « A62 ». */
    val exitBranch: String? = null,
)
```

par :

```kotlin
    /** Route signalée sur le panneau de sortie (`sign.exit_branch_elements[0]`), p. ex. « A62 ». */
    val exitBranch: String? = null,
    /** Cap (°, 0 = nord) avant la manœuvre (`bearing_before`) ; absent au départ. */
    val bearingBeforeDeg: Int? = null,
    /** Cap (°) après la manœuvre (`bearing_after`) ; absent à l'arrivée. */
    val bearingAfterDeg: Int? = null,
    /** Dernier point du tracé de la manœuvre (`end_shape_index`, décalé comme [beginShapeIndex]). */
    val endShapeIndex: Int? = null,
    /** `verbal_post_transition_instruction` Valhalla (« Continuez pendant 200 mètres. »). */
    val verbalPost: String? = null,
    /** Tous les noms de voie (`street_names`) ; [streetName] reste le premier. */
    val streetNames: List<String> = emptyList(),
    /** Côté de l'étape atteinte (`locations[].side_of_street`), porté par la dernière manœuvre de chaque étape. */
    val sideOfStreet: StreetSide? = null,
) {
    /** Angle signé du virage (°, −180..180, > 0 = à droite) = `bearing_after − bearing_before` ; null sans les deux caps. */
    val turnAngleDeg: Double?
        get() {
            val before = bearingBeforeDeg ?: return null
            val after = bearingAfterDeg ?: return null
            return normalizeSignedDeg((after - before).toDouble())
        }
}
```

- [ ] **Étape 4 : écrire l'implémentation (routage).**

Créer `core/routing/src/main/java/fr/roadly/core/routing/ValhallaManeuverTypes.kt` :

```kotlin
package fr.roadly.core.routing

import fr.roadly.core.model.ManeuverType

/** Types de manœuvre Valhalla (`odin`, 0–38) → [ManeuverType], côté conservé (B3) ; public pour les fixtures de test des autres modules. */
object ValhallaManeuverTypes {
    fun of(t: Int): ManeuverType = when (t) {
        1, 2, 3 -> ManeuverType.DEPART
        4, 5, 6 -> ManeuverType.ARRIVE
        7, 8, 22 -> ManeuverType.CONTINUE
        9 -> ManeuverType.SLIGHT_RIGHT
        10 -> ManeuverType.TURN_RIGHT
        11 -> ManeuverType.SHARP_RIGHT
        12 -> ManeuverType.UTURN_RIGHT
        13 -> ManeuverType.UTURN_LEFT
        14 -> ManeuverType.SHARP_LEFT
        15 -> ManeuverType.TURN_LEFT
        16 -> ManeuverType.SLIGHT_LEFT
        17 -> ManeuverType.RAMP
        18 -> ManeuverType.RAMP_RIGHT
        19 -> ManeuverType.RAMP_LEFT
        20 -> ManeuverType.EXIT_RIGHT
        21 -> ManeuverType.EXIT_LEFT
        23 -> ManeuverType.KEEP_RIGHT
        24 -> ManeuverType.KEEP_LEFT
        25 -> ManeuverType.MERGE
        26 -> ManeuverType.ROUNDABOUT_ENTER
        27 -> ManeuverType.ROUNDABOUT_EXIT
        28, 29 -> ManeuverType.FERRY
        37 -> ManeuverType.MERGE_RIGHT
        38 -> ManeuverType.MERGE_LEFT
        else -> ManeuverType.OTHER
    }
}
```

`ValhallaDtos.kt`. Remplacer la ligne 10 :

```kotlin
@Serializable internal data class ValhallaLocation(@SerialName("original_index") val originalIndex: Int? = null)
```

par :

```kotlin
@Serializable internal data class ValhallaLocation(
    @SerialName("original_index") val originalIndex: Int? = null,
    @SerialName("side_of_street") val sideOfStreet: String? = null,
)
```

Dans `ValhallaManeuver`, remplacer :

```kotlin
    @SerialName("verbal_transition_alert_instruction") val verbalAlert: String? = null,
```

par :

```kotlin
    @SerialName("verbal_transition_alert_instruction") val verbalAlert: String? = null,
    @SerialName("verbal_post_transition_instruction") val verbalPost: String? = null,
    @SerialName("end_shape_index") val endShapeIndex: Int? = null,
    @SerialName("bearing_before") val bearingBefore: Int? = null,
    @SerialName("bearing_after") val bearingAfter: Int? = null,
```

`ValhallaRoutingProvider.kt` :
- Ligne 144 : `trip.legs.forEach { leg ->` devient `trip.legs.forEachIndexed { legIndex, leg ->`.
- Ligne 148 : `leg.maneuvers.forEach { m ->` devient `leg.maneuvers.forEachIndexed { mi, m ->`.
- Ligne 162 : remplacer :

```kotlin
                    exitBranch = m.sign?.exitBranchElements?.firstOrNull { it.text.isNotBlank() }?.text?.trim(),
                )
```

par :

```kotlin
                    exitBranch = m.sign?.exitBranchElements?.firstOrNull { it.text.isNotBlank() }?.text?.trim(),
                    bearingBeforeDeg = m.bearingBefore,
                    bearingAfterDeg = m.bearingAfter,
                    endShapeIndex = m.endShapeIndex?.let { offset + it },
                    verbalPost = m.verbalPost?.takeIf { it.isNotBlank() },
                    streetNames = m.streetNames.map { it.trim() }.filter { it.isNotEmpty() },
                    // L'étape `legIndex` se termine au point `locations[legIndex + 1]`.
                    sideOfStreet = if (mi == leg.maneuvers.lastIndex) streetSide(trip.locations.getOrNull(legIndex + 1)?.sideOfStreet) else null,
                )
```

- Lignes 182 à 203 : remplacer tout le `when` de `mapType` par :

```kotlin
    internal fun mapType(t: Int): ManeuverType = ValhallaManeuverTypes.of(t)

    private fun streetSide(s: String?): StreetSide? = when (s) {
        "left" -> StreetSide.LEFT
        "right" -> StreetSide.RIGHT
        else -> null
    }
```

- Ajouter l'import `fr.roadly.core.model.StreetSide`.

`ValhallaLanes.kt`, ligne 40. Remplacer :

```kotlin
        Lane(directions(mask), valid = o["valid"].isSet() || o["active"].isSet())
```

par :

```kotlin
        // B3 : `active` (voie conseillée) est distinct de `valid` ; une voie active est aussi valide.
        Lane(directions(mask), valid = o["valid"].isSet() || o["active"].isSet(), active = o["active"].isSet())
```

- [ ] **Étape 5 : écrire l'implémentation (icônes, caméra, jeux de test).**

`ManeuverIcon.kt`. Ajouter les imports `androidx.compose.material.icons.filled.RampLeft` et `androidx.compose.material.icons.filled.UTurnRight`. Remplacer les lignes 43 à 48 :

```kotlin
    ManeuverType.UTURN -> Icons.Default.UTurnLeft
    ManeuverType.ROUNDABOUT_ENTER, ManeuverType.ROUNDABOUT_EXIT -> Icons.Default.RoundaboutRight
    ManeuverType.MERGE -> Icons.Default.Merge
    ManeuverType.RAMP -> Icons.Default.RampRight
    ManeuverType.EXIT, ManeuverType.KEEP_RIGHT -> Icons.Default.ForkRight
    ManeuverType.KEEP_LEFT -> Icons.Default.ForkLeft
```

par :

```kotlin
    ManeuverType.UTURN_LEFT -> Icons.Default.UTurnLeft
    ManeuverType.UTURN_RIGHT -> Icons.Default.UTurnRight
    ManeuverType.ROUNDABOUT_ENTER, ManeuverType.ROUNDABOUT_EXIT -> Icons.Default.RoundaboutRight
    ManeuverType.MERGE -> Icons.Default.Merge
    // Pas d'icône Material « fusion à gauche / à droite » : la flèche légère du bon côté.
    ManeuverType.MERGE_LEFT -> Icons.Default.TurnSlightLeft
    ManeuverType.MERGE_RIGHT -> Icons.Default.TurnSlightRight
    ManeuverType.RAMP -> Icons.Default.Straight
    ManeuverType.RAMP_LEFT -> Icons.Default.RampLeft
    ManeuverType.RAMP_RIGHT -> Icons.Default.RampRight
    ManeuverType.EXIT_LEFT, ManeuverType.KEEP_LEFT -> Icons.Default.ForkLeft
    ManeuverType.EXIT_RIGHT, ManeuverType.KEEP_RIGHT -> Icons.Default.ForkRight
```

`GuidanceCameraPolicy.kt`, ligne 43. Remplacer :

```kotlin
    private val exitLike = setOf(ManeuverType.EXIT, ManeuverType.RAMP, ManeuverType.MERGE, ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT)
```

par :

```kotlin
    private val exitLike = setOf(
        ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT,
        ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT, ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT,
    )
```

`TestRoutes.kt` :
- Ligne 41 : `Manœuvres : DEPART (point 0), UTURN (point 12), ARRIVE (point 25).` devient `Manœuvres : DEPART (point 0), UTURN_LEFT (point 12), ARRIVE (point 25).`
- Ligne 54 : `ManeuverType.UTURN, "Faites demi-tour.", null, total - leg, 60.0, 12, null,` devient `ManeuverType.UTURN_LEFT, "Faites demi-tour.", null, total - leg, 60.0, 12, null,`
- Ajouter l'import `fr.roadly.core.model.StreetSide`, puis ajouter après `twoRoundabouts` :

```kotlin
    /** Virage à gauche (scénario 3) : 500 m plein est (pas de 50 m) puis 500 m plein nord ; caps 90° → 0° (angle −90°). */
    fun leftTurn(id: String = "leftTurn", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = (0..10).map { xy(it * 50.0, 0.0) } + (1..10).map { xy(500.0, it * 50.0) }
        val total = (0 until pts.lastIndex).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val leg = (0 until 10).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, leg, 50.0, 0, null, verbalPre = "Conduisez vers l'est.", bearingAfterDeg = 90),
            Maneuver(
                ManeuverType.TURN_LEFT, "Tournez à gauche dans Rue du Nord.", "Rue du Nord", total - leg, 50.0, 10, null,
                verbalPre = "Tournez à gauche dans Rue du Nord.", verbalAlert = "Tournez à gauche dans Rue du Nord.",
                bearingBeforeDeg = 90, bearingAfterDeg = 0,
            ),
            Maneuver(
                ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 20, null,
                verbalPre = "Votre destination est sur la gauche.", bearingBeforeDeg = 0, sideOfStreet = StreetSide.LEFT,
            ),
        )
        return Route(id, costing, total, 100.0, pts, maneuvers, hasToll = false, providerId = "test")
    }
```

Créer `navtest/ValhallaFixture.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.StreetSide
import fr.roadly.core.routing.Polyline6
import fr.roadly.core.routing.ValhallaManeuverTypes
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

/** Réponse Valhalla FOSSGIS enregistrée (`src/test/resources/fixtures`, une étape) → [Route], mêmes règles que `ValhallaRoutingProvider`. */
internal class ValhallaFixture(private val resource: String, private val id: String) {
    private val root: JsonObject by lazy {
        Json.parseToJsonElement(ValhallaFixture::class.java.classLoader!!.getResource(resource)!!.readText()).jsonObject
    }
    private val trip: JsonObject get() = root["trip"]!!.jsonObject
    private val leg: JsonObject get() = trip["legs"]!!.jsonArray[0].jsonObject
    val rawManeuvers: List<JsonObject> get() = leg["maneuvers"]!!.jsonArray.map { it.jsonObject }

    fun route(): Route {
        val raw = rawManeuvers
        val destinationSide = ((trip["locations"] as? JsonArray)?.lastOrNull() as? JsonObject)?.str("side_of_street")
        val maneuvers = raw.mapIndexed { i, m ->
            val sign = m["sign"] as? JsonObject
            Maneuver(
                type = ValhallaManeuverTypes.of(m["type"]!!.jsonPrimitive.int),
                instruction = m.str("instruction").orEmpty(),
                streetName = (m["street_names"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content,
                lengthMeters = m["length"]!!.jsonPrimitive.double * 1000.0,
                durationSeconds = m["time"]!!.jsonPrimitive.double,
                beginShapeIndex = m["begin_shape_index"]!!.jsonPrimitive.int,
                roundaboutExit = (m["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull,
                verbalPre = m.str("verbal_pre_transition_instruction")?.takeIf { it.isNotBlank() },
                verbalAlert = m.str("verbal_transition_alert_instruction")?.takeIf { it.isNotBlank() },
                exitNumber = sign.texts("exit_number_elements").firstOrNull(),
                exitToward = sign.texts("exit_toward_elements").take(2).joinToString(" · ").takeIf { it.isNotEmpty() },
                bearingBeforeDeg = (m["bearing_before"] as? JsonPrimitive)?.intOrNull,
                bearingAfterDeg = (m["bearing_after"] as? JsonPrimitive)?.intOrNull,
                endShapeIndex = (m["end_shape_index"] as? JsonPrimitive)?.intOrNull,
                verbalPost = m.str("verbal_post_transition_instruction")?.takeIf { it.isNotBlank() },
                streetNames = (m["street_names"] as? JsonArray)?.map { it.jsonPrimitive.content.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
                sideOfStreet = if (i == raw.lastIndex) side(destinationSide) else null,
            )
        }
        val summary = trip["summary"]!!.jsonObject
        return Route(
            id = id, costing = RouteCosting.FASTEST,
            distanceMeters = summary["length"]!!.jsonPrimitive.double * 1000.0,
            durationSeconds = summary["time"]!!.jsonPrimitive.double,
            geometry = Polyline6.decode(leg["shape"]!!.jsonPrimitive.content),
            maneuvers = maneuvers, hasToll = false, providerId = "valhalla",
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject?.texts(key: String): List<String> =
        (this?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonObject)?.str("text")?.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    private fun side(s: String?): StreetSide? = when (s) { "left" -> StreetSide.LEFT; "right" -> StreetSide.RIGHT; else -> null }
}
```

Remplacer tout le contenu de `navtest/ParisRoute.kt` par :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.Route
import kotlinx.serialization.json.JsonObject

/** Itinéraire réel Paris (Hôtel de Ville → Étoile) issu de la fixture Valhalla FOSSGIS. */
internal object ParisRoute {
    private val fixture = ValhallaFixture("fixtures/valhalla_route.json", "paris")
    val rawManeuvers: List<JsonObject> get() = fixture.rawManeuvers
    fun route(): Route = fixture.route()
}
```

Les types de la fixture Paris sont 1, 10, 15, 9, 26, 27 et 6. La table publique les traduit comme l'ancienne fonction locale : aucun test existant ne change.

- [ ] **Étape 6 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:model:test :core:routing:testDebugUnitTest :core:map:testDebugUnitTest :core:designsystem:testDebugUnitTest :core:navigation:testDebugUnitTest :feature:navigation:testDebugUnitTest :feature:route:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS.

- [ ] **Étape 7 : commiter.**

```powershell
git add core/model core/routing/src/main core/routing/src/test core/designsystem/src/main/java/fr/roadly/core/designsystem/component/ManeuverIcon.kt core/designsystem/src/test core/map core/navigation/src/main/java/fr/roadly/core/navigation/testing/TestRoutes.kt core/navigation/src/test/java/fr/roadly/core/navigation/ValhallaFixture.kt core/navigation/src/test/java/fr/roadly/core/navigation/ParisRoute.kt core/navigation/src/test/java/fr/roadly/core/navigation/RouteTrackerProgressTest.kt
git commit -m @'
feat(routage): côté des bretelles et sorties, caps, voies actives et champs Valhalla (B3)

ManeuverType distingue gauche / droite (RAMP, EXIT, MERGE, UTURN) ; Maneuver gagne bearing_before / after, turnAngleDeg,
end_shape_index, verbal_post, tous les street_names et side_of_street ; Lane sépare valid et active ; icônes fidèles au côté.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 5 : B3 — fixture FOSSGIS réelle d'un trajet autoroutier (sortie numérotée et échangeur)

**Fichiers :**
- Créer (téléchargement) : `core/routing/src/test/resources/fixtures/valhalla_motorway.json` et sa copie identique `core/navigation/src/test/resources/fixtures/valhalla_motorway.json`.
- Créer : `core/routing/src/test/java/fr/roadly/core/routing/ValhallaMotorwayFixtureTest.kt`, `navtest/MotorwayRoute.kt` et `navtest/MotorwayGuidanceTest.kt`.
- Modifier : `ROADLY_3D_NAVIGATION_TESTS.md`, section « Jeux de données » (lignes 7 à 11).

**Interfaces :**
- Consomme : `ValhallaManeuverTypes.of`, `ValhallaFixture`, `FixScript` et les champs de la tâche 4.
- Produit : `internal object MotorwayRoute { val rawManeuvers: List<JsonObject>; fun route(): Route }`.

**Règle impérative :** les assertions se déduisent du fichier téléchargé. Ne jamais inventer de valeur.
- Les tests « pilotés par les données » relisent le JSON brut.
- Le test « repère » contient les valeurs **lues le 2026-10-31** sur ce même trajet : sortie 20 « 31b » vers Toulouse-centre / Tarbes - Lourdes / Montpellier, puis KEEP_LEFT (24) ; sortie 20 « 19 » ; rond-point 26 de 3e sortie suivi de 27 « D 916 » ; destination à gauche ; aucune voie.
- Si le script d'inspection affiche d'autres valeurs, remplacer celles du test « repère » par les valeurs affichées.

- [ ] **Étape 1 : télécharger la réponse réelle (Bash).**

```bash
cd "/c/Users/Forza PC/Documents/Yannis/Projet Yannis/GPS-roadly-3d"
curl -s -m 60 -G "https://valhalla1.openstreetmap.de/route" \
  --data-urlencode 'json={"locations":[{"lat":43.6300,"lon":1.4100},{"lat":43.5540,"lon":1.5000}],"costing":"auto","units":"kilometers","language":"fr-FR"}' \
  -o core/routing/src/test/resources/fixtures/valhalla_motorway.json
cp core/routing/src/test/resources/fixtures/valhalla_motorway.json core/navigation/src/test/resources/fixtures/valhalla_motorway.json
```

Résultat attendu : un fichier JSON d'environ 15 à 40 Ko qui commence par `{"trip":`. S'il commence par `{"error`, relancer après 1 minute (limite d'usage FOSSGIS).

- [ ] **Étape 2 : inspecter le contenu réel.**

```bash
PYTHONIOENCODING=utf-8 python -c "
import json
d=json.load(open('core/routing/src/test/resources/fixtures/valhalla_motorway.json',encoding='utf-8'))
t=d['trip'];print('longueur',t['summary']['length'],'autoroute',t['summary'].get('has_highway'),'côtés',[l.get('side_of_street') for l in t['locations']])
for i,m in enumerate(t['legs'][0]['maneuvers']):
  print(i,m['type'],m.get('bearing_before'),m.get('bearing_after'),m.get('roundabout_exit_count'),m.get('street_names'),'|',m['instruction'],'| sign=',json.dumps(m.get('sign'),ensure_ascii=False),'| voies=',len(m.get('lanes',[])))
"
```

Il faut au moins une manœuvre de type 20 ou 21 avec `exit_number_elements`, suivie plus loin d'un type 18, 19, 20, 21, 23 ou 24.

**Sinon (LIMITE)**, refaire les étapes 1 et 2 avec ces paires à essayer :
- `{"lat":44.8890,"lon":-0.6050}` → `{"lat":44.7990,"lon":-0.5560}` (rocade de Bordeaux) ;
- `{"lat":45.7980,"lon":4.8120}` → `{"lat":45.7100,"lon":4.8460}` (A6/A7 Lyon).

Si aucune ne renvoie de `sign`, garder la fixture de Toulouse, retirer les assertions de panneau, et écrire dans `ROADLY_3D_NAVIGATION_TESTS.md` : « LIMITE : le serveur public ne renvoie pas de panneau de sortie sur les trajets essayés (date, paires) ».

- [ ] **Étape 3 : écrire les tests.**

`core/routing/src/test/java/fr/roadly/core/routing/ValhallaMotorwayFixtureTest.kt` :

```kotlin
package fr.roadly.core.routing

import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfig
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.StreetSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Fixture FOSSGIS réelle (Toulouse, périphérique et A620, enregistrée le 2026-10-31) : sorties numérotées, échangeur, ronds-points (B3, scénarios 7 et 8). */
class ValhallaMotorwayFixtureTest {
    private val server = MockWebServer()
    private lateinit var provider: ValhallaRoutingProvider
    private val json = Json { ignoreUnknownKeys = true }
    private fun fixture() = javaClass.classLoader!!.getResource("fixtures/valhalla_motorway.json")!!.readText()
    private fun raw(): List<JsonObject> = json.parseToJsonElement(fixture()).jsonObject["trip"]!!.jsonObject["legs"]!!.jsonArray[0].jsonObject["maneuvers"]!!.jsonArray.map { it.jsonObject }

    @Before fun setUp() {
        server.start()
        val dispatchers = object : DispatcherProvider {
            override val io = Dispatchers.Unconfined; override val default = Dispatchers.Unconfined; override val main = Dispatchers.Unconfined
        }
        val config = object : ProviderConfigRepository {
            override val config = flowOf(ProviderConfig.DEFAULT.copy(valhallaBaseUrl = server.url("/").toString().trimEnd('/')))
            override suspend fun override(config: ProviderConfig) = Unit
            override suspend fun reset() = Unit
        }
        provider = ValhallaRoutingProvider(ApiClient(OkHttpClient(), dispatchers), config, json)
    }
    @After fun tearDown() = server.shutdown()

    private suspend fun parsed(): Route {
        server.enqueue(MockResponse().setBody(fixture()))
        return (provider.route(RouteRequest(GeoPoint(43.6300, 1.4100), GeoPoint(43.5540, 1.5000), costing = RouteCosting.FASTEST)) as Outcome.Success).value.single()
    }

    private fun JsonObject.type() = this["type"]!!.jsonPrimitive.int
    private fun JsonObject.signTexts(key: String): List<String> =
        ((this["sign"] as? JsonObject)?.get(key) as? JsonArray)?.map { it.jsonObject["text"]!!.jsonPrimitive.content }.orEmpty()

    @Test fun `the recorded trip contains a numbered exit followed by an interchange`() {
        val raw = raw()
        val firstExit = raw.indexOfFirst { it.type() in 20..21 && it.signTexts("exit_number_elements").any { t -> t.isNotBlank() } }
        assertTrue("au moins une sortie numérotée", firstExit >= 0)
        assertTrue("échangeur : bretelle, sortie ou bifurcation après la sortie", raw.drop(firstExit + 1).any { it.type() in setOf(18, 19, 20, 21, 23, 24) })
    }

    @Test fun `every maneuver keeps its side, bearings, sign and street names`() = runTest {
        val r = parsed()
        raw().forEachIndexed { i, m ->
            val p = r.maneuvers[i]
            when (m.type()) {
                20 -> assertEquals("sortie à droite $i", ManeuverType.EXIT_RIGHT, p.type)
                21 -> assertEquals("sortie à gauche $i", ManeuverType.EXIT_LEFT, p.type)
                18 -> assertEquals("bretelle à droite $i", ManeuverType.RAMP_RIGHT, p.type)
                19 -> assertEquals("bretelle à gauche $i", ManeuverType.RAMP_LEFT, p.type)
                23 -> assertEquals("serrer à droite $i", ManeuverType.KEEP_RIGHT, p.type)
                24 -> assertEquals("serrer à gauche $i", ManeuverType.KEEP_LEFT, p.type)
                else -> assertEquals("type $i", ValhallaManeuverTypes.of(m.type()), p.type)
            }
            assertEquals("bearing_before $i", m["bearing_before"]?.jsonPrimitive?.int, p.bearingBeforeDeg)
            assertEquals("bearing_after $i", m["bearing_after"]?.jsonPrimitive?.int, p.bearingAfterDeg)
            assertEquals("numéro $i", m.signTexts("exit_number_elements").firstOrNull { it.isNotBlank() }, p.exitNumber)
            assertEquals(
                "direction $i",
                m.signTexts("exit_toward_elements").map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(" · ").takeIf { it.isNotEmpty() },
                p.exitToward,
            )
            assertEquals("noms $i", (m["street_names"] as? JsonArray)?.map { it.jsonPrimitive.content.trim() }?.filter { it.isNotEmpty() }.orEmpty(), p.streetNames)
        }
    }

    /** Valeurs lues dans la réponse du 2026-10-31 (script de la tâche 5, étape 2) ; à remplacer par celles du fichier s'il diffère. */
    @Test fun `reference values read in the recorded response`() = runTest {
        val r = parsed()
        val exit31b = r.maneuvers.single { it.exitNumber == "31b" }
        assertEquals(ManeuverType.EXIT_RIGHT, exit31b.type)
        assertEquals("Toulouse-centre · Tarbes - Lourdes", exit31b.exitToward)
        assertEquals(listOf("A 620"), exit31b.streetNames)
        assertEquals("échangeur juste après la sortie 31b", ManeuverType.KEEP_LEFT, r.maneuvers[r.maneuvers.indexOf(exit31b) + 1].type)
        assertEquals(ManeuverType.EXIT_RIGHT, r.maneuvers.single { it.exitNumber == "19" }.type)
        assertEquals(StreetSide.LEFT, r.maneuvers.last().sideOfStreet)
        assertTrue("LIMITE : aucune voie renvoyée par le serveur public sur ce trajet", r.maneuvers.all { it.lanes.isEmpty() })
    }
}
```

`navtest/MotorwayRoute.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.Route
import kotlinx.serialization.json.JsonObject

/** Itinéraire réel Toulouse (périphérique, A620, sorties 31b et 19) issu de la fixture Valhalla FOSSGIS du 2026-10-31. */
internal object MotorwayRoute {
    private val fixture = ValhallaFixture("fixtures/valhalla_motorway.json", "motorway")
    val rawManeuvers: List<JsonObject> get() = fixture.rawManeuvers
    fun route(): Route = fixture.route()
}
```

`navtest/MotorwayGuidanceTest.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.testing.FixScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scénarios 7 et 8 sur la fixture autoroutière réelle : sortie numérotée visée, échangeur derrière, index stable sous bruit. */
class MotorwayGuidanceTest {
    private val route = MotorwayRoute.route()
    private val exitIndex = route.maneuvers.indexOfFirst { (it.type == ManeuverType.EXIT_RIGHT || it.type == ManeuverType.EXIT_LEFT) && it.exitNumber != null }
    private val sides = setOf(ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT, ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT)

    @Test fun `at 400 m the numbered exit is the target and the interchange is the next maneuver`() {
        assertTrue("la fixture doit contenir une sortie numérotée", exitIndex > 0)
        val t = RouteTracker(route)
        val exitAt = t.maneuverStartM[exitIndex]
        val p = FixScript(route).drive(exitAt - 600.0, exitAt - 400.0, speedKmh = 110.0).map { t.update(it) }.last()
        assertEquals(exitIndex, p.nextManeuverIndex)
        assertEquals(400.0 * route.distanceMeters / t.totalGeometryM, p.distanceToNextManeuverM, 0.5)
        assertNotNull(route.maneuvers[exitIndex].exitToward)
        assertTrue("échangeur après la sortie : ${route.maneuvers[exitIndex + 1].type}", route.maneuvers[exitIndex + 1].type in sides)
        assertEquals(t.maneuverStartM[exitIndex + 1] - t.maneuverStartM[exitIndex], (p.distancesToManeuversM[0] - p.distanceToNextManeuverM) * t.totalGeometryM / route.distanceMeters, 0.5)
    }

    @Test fun `GPS noise at the exit at motorway speed never flips the target`() {
        val t = RouteTracker(route)
        val exitAt = t.maneuverStartM[exitIndex]
        val indices = FixScript(route, seed = 23).drive(exitAt - 300.0, exitAt + 5.0, speedKmh = 110.0, noiseM = 4.0, accuracyM = 8f).map { t.update(it).nextManeuverIndex }
        assertTrue(indices.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(exitIndex, indices.last())
    }
}
```

Dans `ROADLY_3D_NAVIGATION_TESTS.md`, ligne 10, remplacer la puce « **À créer** : … » par :

```markdown
- Créés (Étape B) : `TestRoutes.leftTurn`, `TestRoutes.twoRoundabouts` (120 m d'écart), `TestRoutes.parallelRoads` (desserte à 25 m), `TestRoutes.overpass` (passage supérieur) ; fixture FOSSGIS réelle `valhalla_motorway.json` (Toulouse 43.6300,1.4100 → 43.5540,1.5000, enregistrée le 2026-10-31 : sorties 31b et 19 avec panneaux, échangeur A620, 4 ronds-points). LIMITE : aucune voie (`lanes`) renvoyée par le serveur public sur ce trajet.
```

- [ ] **Étape 4 : lancer les tests.**

Lancer : `.\gradlew.bat :core:routing:testDebugUnitTest :core:navigation:testDebugUnitTest --offline -q --tests "*Motorway*"`

Résultat attendu : SUCCÈS. En cas d'échec du test « repère », corriger ses valeurs **à partir de la sortie de l'étape 2**, jamais l'inverse.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/routing/src/test/resources/fixtures/valhalla_motorway.json core/navigation/src/test/resources/fixtures/valhalla_motorway.json core/routing/src/test/java/fr/roadly/core/routing/ValhallaMotorwayFixtureTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/MotorwayRoute.kt core/navigation/src/test/java/fr/roadly/core/navigation/MotorwayGuidanceTest.kt ROADLY_3D_NAVIGATION_TESTS.md
git commit -m @'
test(routage): fixture FOSSGIS réelle autoroutière (sorties 31b et 19, échangeur A620) (B3)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 6 : B4 — ronds-points (`RoundaboutInfo`, `RoundaboutResolver`)

**Fichiers :**
- Créer : `nav/RoundaboutResolver.kt`.
- Modifier : `nav/NavigationState.kt`, lignes 31 à 33.
- Modifier : `nav/NavigationSession.kt` : état de `Run` (après la ligne 168), recalcul (lignes 341 et 342), `guiding()` (ligne 434).
- Test : `navtest/RoundaboutResolverTest.kt` (nouveau) et `navtest/NavigationSessionGuidanceTest.kt` (nouveau, réutilisé aux tâches 7 à 10).

**Interfaces :**
- Consomme : `Maneuver.bearingBeforeDeg`, `bearingAfterDeg`, `endShapeIndex`, `roundaboutExit` et `normalizeSignedDeg` (tâche 4) ; l'index monotone (tâche 1).
- Produit :
  - `data class RoundaboutInfo(exitCount: Int?, exitNumber: Int?, entryBearingDeg: Double?, exitBearingDeg: Double?, ringGeometry: List<GeoPoint>, exitStreet: String?)`, avec la propriété calculée `exitAngleDeg: Double?`.
  - `object RoundaboutResolver` :
    - `fun resolve(route: Route, enterIndex: Int): RoundaboutInfo`
    - `fun resolveAll(route: Route): Map<Int, RoundaboutInfo>`
    - `fun current(roundabouts: Map<Int, RoundaboutInfo>, nextIndex: Int): RoundaboutInfo?`
  - `NavigationState.Guiding.roundabout: RoundaboutInfo? = null`

Choix documenté : `exitNumber` est le rang de sortie à prendre (`roundabout_exit_count` de l'entrée Valhalla). `exitCount` est le nombre total de sorties de l'anneau. **LIMITE :** `/route` ne fournit jamais ce total, `exitCount` reste donc `null`. Les autres sorties ne sont jamais dessinées.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`navtest/RoundaboutResolverTest.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B4 sur données réelles (scénarios 4 et 5) : Paris (2e et 5e sorties) et autoroute de Toulouse (3e sortie). */
class RoundaboutResolverTest {
    private val paris = ParisRoute.route()

    @Test fun `champs-elysees roundabout - second exit straight through, ring between entry and exit`() {
        val info = RoundaboutResolver.resolve(paris, 10)
        assertEquals(2, info.exitNumber)
        assertNull("le nombre total de sorties n'est pas fourni par /route", info.exitCount)
        assertEquals(296.0, info.entryBearingDeg!!, 0.0)
        assertEquals(296.0, info.exitBearingDeg!!, 0.0)
        assertEquals(0.0, info.exitAngleDeg!!, 0.0)
        assertEquals(paris.geometry.subList(207, 225), info.ringGeometry)
        assertEquals("Avenue des Champs-Élysées", info.exitStreet)
    }

    @Test fun `etoile - fifth exit without an exit maneuver, nothing invented`() {
        val info = RoundaboutResolver.resolve(paris, 12)
        assertEquals(5, info.exitNumber)
        assertEquals(295.0, info.entryBearingDeg!!, 0.0)
        assertNull(info.exitBearingDeg); assertNull(info.exitAngleDeg); assertNull(info.exitStreet)
        assertEquals(paris.geometry.subList(298, 323), info.ringGeometry)
    }

    @Test fun `motorway fixture - a roundabout of three exits or more is resolved from the raw data`() {
        val r = MotorwayRoute.route()
        val raw = MotorwayRoute.rawManeuvers
        val k = raw.indices.first { i ->
            raw[i]["type"]!!.jsonPrimitive.int == 26 && ((raw[i]["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull ?: 0) >= 3 &&
                raw.getOrNull(i + 1)?.get("type")?.jsonPrimitive?.int == 27
        }
        val info = RoundaboutResolver.resolve(r, k)
        assertEquals((raw[k]["roundabout_exit_count"] as JsonPrimitive).intOrNull, info.exitNumber)
        assertEquals(r.maneuvers[k + 1].streetName, info.exitStreet)
        assertEquals(r.geometry.subList(r.maneuvers[k].beginShapeIndex, r.maneuvers[k + 1].beginShapeIndex + 1), info.ringGeometry)
        assertEquals(raw[k + 1]["bearing_after"]!!.jsonPrimitive.int.toDouble(), info.exitBearingDeg!!, 0.0)
    }

    @Test fun `all roundabouts are indexed by their entry`() {
        assertEquals(setOf(10, 12), RoundaboutResolver.resolveAll(paris).keys)
        assertEquals(setOf(1, 3), RoundaboutResolver.resolveAll(TestRoutes.twoRoundabouts()).keys)
    }

    @Test fun `the roundabout is shown before entry and kept inside the ring`() {
        val all = RoundaboutResolver.resolveAll(paris)
        assertEquals(all[10], RoundaboutResolver.current(all, 10))
        assertEquals("dans l'anneau : prochaine = sortie", all[10], RoundaboutResolver.current(all, 11))
        assertEquals("Étoile : prochaine = arrivée, courante = entrée", all[12], RoundaboutResolver.current(all, 13))
        assertNull(RoundaboutResolver.current(all, 9))
    }

    @Test fun `exit target is frozen under jitter at the entry and inside the ring`() {
        val t = RouteTracker(paris)
        val all = RoundaboutResolver.resolveAll(paris)
        val script = FixScript(paris, seed = 13)
        val enterAt = t.maneuverStartM[10]
        script.drive(enterAt - 150.0, enterAt - 30.0, speedKmh = 30.0).forEach { t.update(it) }
        assertEquals(List(10) { 10 }, script.jitter(enterAt, 10, 8.0).map { t.update(it).nextManeuverIndex })
        script.drive(enterAt - 8.0, enterAt + 25.0, speedKmh = 20.0).forEach { t.update(it) }
        val inRing = script.jitter(enterAt + 28.0, 10, 8.0).map { RoundaboutResolver.current(all, t.update(it).nextManeuverIndex) }
        assertTrue(inRing.all { it == all[10] })
    }

    @Test fun `the exit of the roundabout is announced inside the ring only`() {
        val t = RouteTracker(paris)
        val enterAt = t.maneuverStartM[10]
        val exitAt = t.maneuverStartM[11]
        val already = mutableSetOf<Pair<Int, Tier>>()
        val said = mutableListOf<Pair<Announcement, Double>>()
        FixScript(paris, seed = 17).drive(enterAt - 200.0, exitAt + 20.0, speedKmh = 20.0).forEach { f ->
            val p = t.update(f)
            AnnouncementPolicy.next(p, paris.maneuvers, 20.0, already)?.let { a -> already += a.maneuverIndex to a.tier; said += a to p.distanceAlongM }
        }
        val exitCalls = said.filter { it.first.maneuverIndex == 11 }
        assertTrue("la sortie est annoncée", exitCalls.isNotEmpty())
        assertTrue("jamais avant d'être entré dans l'anneau", exitCalls.all { it.second > enterAt })
        assertEquals(Tier.NOW, exitCalls.last().first.tier)
        assertEquals(paris.maneuvers[11].verbalPre, exitCalls.last().first.text)
    }
}
```

`navtest/NavigationSessionGuidanceTest.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.testing.FakeUserPreferencesRepository
import fr.roadly.core.location.testing.FakeLocationRepository
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.navigation.testing.FakeNavigationServiceController
import fr.roadly.core.navigation.testing.FakeSpeechService
import fr.roadly.core.navigation.testing.FixScript
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session de bout en bout sur trajets simulés (`FixScript`) : ronds-points, annonces, recalcul, persistance (Étape B). */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionGuidanceTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val requests = mutableListOf<RouteRequest>()
    private var routingResult: Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += request; return routingResult }
    }

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, NoopSpeedLimitProvider(), speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
    }

    private fun option(route: Route) = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    private fun guiding(s: NavigationSession) = s.state.value as NavigationState.Guiding

    /** Émet [fixes] une par seconde (horodatage = horloge de test) ; [onEach] est appelé après le traitement de chaque fix. */
    private suspend fun TestScope.play(fixes: List<LocationFix>, onEach: (LocationFix) -> Unit = {}) {
        fixes.forEach { f ->
            advanceTimeBy(1_000)
            location.emit(f.copy(timestampMs = testScheduler.currentTime)); runCurrent()
            onEach(f)
        }
    }

    @Test fun `the guiding state exposes the roundabout ahead and keeps it inside the ring`() = runTest {
        val route = TestRoutes.twoRoundabouts()
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(FixScript(route).drive(150.0, 150.0, speedKmh = 30.0))
        assertEquals(2, guiding(s).roundabout!!.exitNumber)
        play(FixScript(route).drive(215.0, 215.0, speedKmh = 30.0))
        assertEquals(2, guiding(s).progress.nextManeuverIndex)
        assertEquals("cible figée dans l'anneau", 2, guiding(s).roundabout!!.exitNumber)
        play(FixScript(route).drive(260.0, 260.0, speedKmh = 30.0))
        assertEquals(1, guiding(s).roundabout!!.exitNumber)
        play(FixScript(route).drive(400.0, 400.0, speedKmh = 30.0))
        assertNull(guiding(s).roundabout)
    }
}
```

`assertFalse`, `assertNotNull` et `assertTrue` sont importés dès maintenant pour les tâches suivantes. Un import inutilisé ne produit qu'un avertissement : le projet ne traite pas les avertissements comme des erreurs.

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*RoundaboutResolverTest*" --tests "*NavigationSessionGuidanceTest*"`

Résultat attendu : ÉCHEC de compilation (`RoundaboutResolver` inconnu).

- [ ] **Étape 3 : écrire l'implémentation.**

`nav/RoundaboutResolver.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.normalizeSignedDeg

/** Rond-point visé (B4), uniquement depuis les données Valhalla : rien n'est inventé, les autres sorties ne sont jamais dessinées. */
data class RoundaboutInfo(
    /** Nombre total de sorties de l'anneau : LIMITE, `/route` ne le fournit pas → toujours null aujourd'hui. */
    val exitCount: Int?,
    /** Rang de la sortie à prendre (`roundabout_exit_count` de l'entrée) ; null = inconnu, aucune valeur inventée. */
    val exitNumber: Int?,
    /** Cap d'arrivée sur l'anneau (`bearing_before` de l'entrée). */
    val entryBearingDeg: Double?,
    /** Cap de sortie (`bearing_after` de la manœuvre de sortie) ; null sans manœuvre de sortie. */
    val exitBearingDeg: Double?,
    /** Tracé de l'anneau parcouru, du début de l'entrée au début de la sortie (inclus). */
    val ringGeometry: List<GeoPoint>,
    /** Rue de sortie (premier `street_names` de la sortie) ; null si absente. */
    val exitStreet: String?,
) {
    /** Angle de sortie signé (−180..180, > 0 = à droite) = `bearing_after(EXIT) − bearing_before(ENTER)`. */
    val exitAngleDeg: Double?
        get() {
            val entry = entryBearingDeg ?: return null
            val exit = exitBearingDeg ?: return null
            return normalizeSignedDeg(exit - entry)
        }
}

/** Calcule les [RoundaboutInfo] d'un itinéraire (Kotlin pur) depuis ENTER (Valhalla 26) et EXIT (27). */
object RoundaboutResolver {
    /** Rond-point dont la manœuvre d'entrée est [enterIndex] ; la sortie est la manœuvre suivante si elle est de type EXIT. */
    fun resolve(route: Route, enterIndex: Int): RoundaboutInfo {
        val ms = route.maneuvers
        val enter = ms[enterIndex]
        val exit = ms.getOrNull(enterIndex + 1)?.takeIf { it.type == ManeuverType.ROUNDABOUT_EXIT }
        val last = route.geometry.lastIndex
        val from = enter.beginShapeIndex.coerceIn(0, last)
        val to = (exit?.beginShapeIndex ?: enter.endShapeIndex ?: ms.getOrNull(enterIndex + 1)?.beginShapeIndex ?: from).coerceIn(from, last)
        return RoundaboutInfo(
            exitCount = null,
            exitNumber = enter.roundaboutExit?.takeIf { it > 0 },
            entryBearingDeg = enter.bearingBeforeDeg?.toDouble(),
            exitBearingDeg = exit?.bearingAfterDeg?.toDouble(),
            ringGeometry = route.geometry.subList(from, to + 1).toList(),
            exitStreet = exit?.streetName,
        )
    }

    /** Tous les ronds-points de [route], indexés par leur manœuvre d'entrée (calculés une fois par tracé). */
    fun resolveAll(route: Route): Map<Int, RoundaboutInfo> =
        route.maneuvers.indices.filter { route.maneuvers[it].type == ManeuverType.ROUNDABOUT_ENTER }.associateWith { resolve(route, it) }

    /**
     * Rond-point à montrer quand la prochaine manœuvre est [nextIndex] : son entrée, ou l'entrée qui la précède (on est dans l'anneau).
     * La cible est figée à l'entrée : l'index de manœuvre est monotone (B1).
     */
    fun current(roundabouts: Map<Int, RoundaboutInfo>, nextIndex: Int): RoundaboutInfo? = roundabouts[nextIndex] ?: roundabouts[nextIndex - 1]
}
```

`nav/NavigationState.kt`. Remplacer :

```kotlin
        /** `speedLimitKmh` a été abaissé pour la pluie (option). */
        val limitLoweredByRain: Boolean = false,
    ) : NavigationState
```

par :

```kotlin
        /** `speedLimitKmh` a été abaissé pour la pluie (option). */
        val limitLoweredByRain: Boolean = false,
        /** B4 : rond-point de la prochaine manœuvre, ou dans lequel on se trouve ; null sinon. */
        val roundabout: RoundaboutInfo? = null,
    ) : NavigationState
```

`nav/NavigationSession.kt`. Après la ligne 168 (`private val zonesAnnounced = mutableSetOf<Double>()`), ajouter :

```kotlin
        /** B4 : ronds-points du tracé courant, calculés une fois par tracé. */
        private var roundabouts: Map<Int, RoundaboutInfo> = RoundaboutResolver.resolveAll(route)
```

Dans le recalcul, remplacer :

```kotlin
                    route = fresh
                    tracker = freshTracker
```

par :

```kotlin
                    route = fresh
                    tracker = freshTracker
                    roundabouts = RoundaboutResolver.resolveAll(fresh)
```

Dans `guiding()`, remplacer :

```kotlin
                rerouting = rerouting,
            )
```

par :

```kotlin
                rerouting = rerouting,
                roundabout = RoundaboutResolver.current(roundabouts, next),
            )
```

- [ ] **Étape 4 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest :feature:navigation:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS. Si le test autoroutier ne trouve aucun rond-point d'au moins 3 sorties dans la fixture téléchargée, le supprimer : l'Étoile (5e sortie) couvre seule le scénario 5. Écrire cette LIMITE dans `ROADLY_3D_NAVIGATION_TESTS.md`.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/RoundaboutResolver.kt core/navigation/src/main/java/fr/roadly/core/navigation/NavigationState.kt core/navigation/src/main/java/fr/roadly/core/navigation/NavigationSession.kt core/navigation/src/test/java/fr/roadly/core/navigation/RoundaboutResolverTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionGuidanceTest.kt
git commit -m @'
feat(navigation): RoundaboutInfo depuis l'entrée et la sortie Valhalla, cible figée (B4)

Rang de sortie, caps d'entrée et de sortie, anneau et rue de sortie ; aucune sortie inventée (nombre total : LIMITE /route).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 7 : B5 — priorités de parole et péremption (`SpeechPriority`, `SpeechQueue`)

**Fichiers :**
- Modifier : `nav/SpeechService.kt`, lignes 1 à 6.
- Créer : `nav/SpeechQueue.kt`.
- Modifier : `nav/AndroidSpeechService.kt`, lignes 10-11 (imports), 20-23 (KDoc), 34-36, 53-64, 90-96, 129-140 et 143-156.
- Modifier : `nav/testing/FakeSpeechService.kt`, ligne 19.
- Modifier : `nav/GuidanceConstants.kt` (ajout).
- Modifier : `nav/AnnouncementPolicy.kt` : extension `Tier.speechPriority()`.
- Modifier : `nav/NavigationSession.kt` : lignes 250, 282, 290 et 404-410.
- Test : `navtest/SpeechQueueTest.kt` (nouveau) et `NavigationSessionGuidanceTest.kt` (ajout).

**Interfaces :**
- Produit :
  - `enum class SpeechPriority { URGENT, NORMAL, LOW }`
  - `SpeechService.speak(text: String, flush: Boolean = false, priority: SpeechPriority)`. C'est un **nouveau membre avec corps par défaut**, l'ancien `speak(text, flush)` reste.
  - `internal class SpeechQueue(maxAgeMs: Long = GuidanceConstants.SPEECH_STALE_AFTER_MS)` avec `data class Item(text: String, priority: SpeechPriority, queuedAtMs: Long)`, `fun offer(item: Item, flush: Boolean = false): Boolean`, `fun poll(nowMs: Long): Item?`, `fun clear()` et `val isEmpty: Boolean`.
  - `internal fun Tier.speechPriority(): SpeechPriority`
  - `FakeSpeechService.priorities: MutableList<SpeechPriority>`
  - `GuidanceConstants.SPEECH_STALE_AFTER_MS = 8_000L`

- [ ] **Étape 1 : écrire les tests qui échouent.**

`navtest/SpeechQueueTest.kt` :

```kotlin
package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B5 : file de parole — un NOW (URGENT) coupe un FAR long et vide la file ; une phrase périmée n'est pas dite. */
class SpeechQueueTest {
    private fun item(text: String, p: SpeechPriority, at: Long = 0L) = SpeechQueue.Item(text, p, at)

    @Test fun `items are said in order of arrival`() {
        val q = SpeechQueue()
        assertFalse(q.offer(item("a", SpeechPriority.NORMAL)))
        assertFalse(q.offer(item("b", SpeechPriority.LOW)))
        assertEquals("a", q.poll(1_000)!!.text)
        assertEquals("b", q.poll(1_000)!!.text)
        assertNull(q.poll(1_000))
    }

    @Test fun `a long far in progress is cut by a now, and queued phrases are dropped`() {
        val q = SpeechQueue()
        q.offer(item("Dans 2 kilomètres, prenez la sortie 19 vers Carcassonne.", SpeechPriority.LOW))
        q.offer(item("Dans 300 mètres, prenez la sortie.", SpeechPriority.NORMAL))
        assertTrue("URGENT interrompt l'annonce en cours", q.offer(item("Prenez la sortie.", SpeechPriority.URGENT, 100)))
        assertEquals("Prenez la sortie.", q.poll(200)!!.text)
        assertNull("FAR et NEAR en attente jetés", q.poll(200))
    }

    @Test fun `low and normal phrases expire after 8 s of waiting, urgent never`() {
        val q = SpeechQueue()
        q.offer(item("lent", SpeechPriority.LOW)); q.offer(item("normal", SpeechPriority.NORMAL))
        assertNull(q.poll(GuidanceConstants.SPEECH_STALE_AFTER_MS + 1))
        q.offer(item("urgent", SpeechPriority.URGENT))
        assertEquals("urgent", q.poll(60_000)!!.text)
    }

    @Test fun `flush also interrupts`() = assertTrue(SpeechQueue().offer(item("x", SpeechPriority.NORMAL), flush = true))
}
```

Dans `NavigationSessionGuidanceTest.kt`, ajouter :

```kotlin
    @Test fun `announcements carry their priority - now urgent, near normal, far low`() = runTest {
        val route = TestRoutes.straight()
        val turnAt = RouteTracker(route).maneuverStartM[1]
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(listOf(turnAt - 250.0, turnAt - 70.0, turnAt - 30.0).map { FixScript(route).drive(it, it, speedKmh = 36.0).single() })
        play(FixScript(route).drive(RouteTracker(route).totalGeometryM, RouteTracker(route).totalGeometryM, speedKmh = 36.0))
        assertEquals(
            listOf(SpeechPriority.NORMAL, SpeechPriority.LOW, SpeechPriority.NORMAL, SpeechPriority.URGENT, SpeechPriority.URGENT),
            speech.priorities,
        )
    }
```

Les cinq priorités correspondent, dans l'ordre, au départ, au FAR, au NEAR, au NOW et à l'arrivée.

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*SpeechQueueTest*"`

Résultat attendu : ÉCHEC de compilation (`SpeechQueue` et `SpeechPriority` inconnus).

- [ ] **Étape 3 : écrire l'implémentation (pure).**

`GuidanceConstants.kt`, avant l'accolade finale :

```kotlin
    /** B5 : une annonce LOW / NORMAL restée plus de 8 s en file n'est plus dite (palier dépassé). */
    const val SPEECH_STALE_AFTER_MS = 8_000L
```

`SpeechService.kt`. Remplacer les lignes 1 à 6 :

```kotlin
package fr.roadly.core.navigation

/** Synthèse vocale du guidage ; aucune voix serveur (spec §2). */
interface SpeechService {
    /** [flush] = interrompt ce qui est en cours ; sinon mise en file. */
    fun speak(text: String, flush: Boolean = false)
```

par :

```kotlin
package fr.roadly.core.navigation

/** Priorité d'une annonce (B5) : URGENT (« maintenant ») vide la file ; LOW / NORMAL périment après 8 s d'attente. */
enum class SpeechPriority { URGENT, NORMAL, LOW }

/** Synthèse vocale du guidage ; aucune voix serveur (spec §2). */
interface SpeechService {
    /** [flush] = interrompt ce qui est en cours ; sinon mise en file. */
    fun speak(text: String, flush: Boolean = false)
    /**
     * Variante priorisée (B5). Par défaut : URGENT interrompt (flush), sinon mise en file. Une implémentation qui fait déléguer
     * `speak(text, flush)` à cette variante doit la redéfinir (sinon les deux s'appellent mutuellement).
     */
    fun speak(text: String, flush: Boolean = false, priority: SpeechPriority) = speak(text, flush || priority == SpeechPriority.URGENT)
```

`nav/SpeechQueue.kt` :

```kotlin
package fr.roadly.core.navigation

/** File d'annonces du moteur de synthèse (pur, testé sur JVM) : un URGENT vide la file et interrompt ; LOW / NORMAL périment après 8 s. */
internal class SpeechQueue(private val maxAgeMs: Long = GuidanceConstants.SPEECH_STALE_AFTER_MS) {
    data class Item(val text: String, val priority: SpeechPriority, val queuedAtMs: Long)

    private val items = ArrayDeque<Item>()

    val isEmpty: Boolean get() = items.isEmpty()

    /** Ajoute [item] ; rend vrai s'il faut couper l'annonce en cours (URGENT ou [flush]) — la file est alors vidée avant l'ajout. */
    fun offer(item: Item, flush: Boolean = false): Boolean {
        val interrupt = flush || item.priority == SpeechPriority.URGENT
        if (interrupt) items.clear()
        items.addLast(item)
        return interrupt
    }

    /** Prochaine annonce encore valable à [nowMs] ; les LOW / NORMAL en attente depuis plus de [maxAgeMs] sont jetées. */
    fun poll(nowMs: Long): Item? {
        while (true) {
            val head = items.removeFirstOrNull() ?: return null
            if (head.priority == SpeechPriority.URGENT || nowMs - head.queuedAtMs <= maxAgeMs) return head
        }
    }

    fun clear() = items.clear()
}
```

`AnnouncementPolicy.kt`. Après `data class Announcement(...)` (ligne 10), ajouter :

```kotlin
/** B5 : NOW → URGENT, NEAR → NORMAL, FAR → LOW. */
internal fun Tier.speechPriority(): SpeechPriority = when (this) {
    Tier.NOW -> SpeechPriority.URGENT
    Tier.NEAR -> SpeechPriority.NORMAL
    Tier.FAR -> SpeechPriority.LOW
}
```

`testing/FakeSpeechService.kt`. Ajouter l'import `fr.roadly.core.navigation.SpeechPriority`. Remplacer la ligne 19 :

```kotlin
    override fun speak(text: String, flush: Boolean) { spoken += text }
```

par :

```kotlin
    /** Priorité de chaque annonce, dans l'ordre de [spoken]. */
    val priorities = mutableListOf<SpeechPriority>()

    override fun speak(text: String, flush: Boolean) = speak(text, flush, SpeechPriority.NORMAL)
    override fun speak(text: String, flush: Boolean, priority: SpeechPriority) { spoken += text; priorities += priority }
```

- [ ] **Étape 4 : écrire l'implémentation (session).**

Dans `NavigationSession.kt` :
- Ligne 250. `say(SpokenPhrases.vigilanceZone(zone.speedLimitKmh), routeId = progress.routeId)` devient `say(SpokenPhrases.vigilanceZone(zone.speedLimitKmh), routeId = progress.routeId, priority = SpeechPriority.LOW)`.
- Ligne 282. `say(a.text, routeId = progress.routeId)` devient `say(a.text, routeId = progress.routeId, priority = a.tier.speechPriority())`.
- Ligne 290, dans `arrive()`. `                say(m.verbalPre ?: m.instruction)` devient `                say(m.verbalPre ?: m.instruction, priority = SpeechPriority.URGENT)`.
- Lignes 404 à 410. Remplacer :

```kotlin
        private suspend fun say(text: String, routeId: String? = null) {
```

par :

```kotlin
        /** [priority] : NOW et arrivée URGENT, NEAR / départ / recalcul NORMAL, FAR / vigilance LOW (B5). */
        private suspend fun say(text: String, routeId: String? = null, priority: SpeechPriority = SpeechPriority.NORMAL) {
```

Puis remplacer `            speech.speak(text)` par `            speech.speak(text, priority = priority)`.

- [ ] **Étape 5 : écrire l'implémentation Android.**

Le code Android n'a pas de test JVM. Il est vérifié par `assembleDebug` et sur le HONOR.

`AndroidSpeechService.kt` :

1. Après `import android.os.Looper`, ajouter l'import `android.os.SystemClock`.

2. Remplacer la KDoc de classe (lignes 20 à 23) :

```kotlin
/**
 * `TextToSpeech` en `Locale.FRANCE`, focus audio transitoire avec atténuation des autres sources
 * (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), file d'annonces, volume par annonce. Moteur créé au premier usage.
 */
```

par :

```kotlin
/**
 * `TextToSpeech` en `Locale.FRANCE`, focus audio transitoire avec atténuation des autres sources
 * (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), file d'annonces priorisée (B5 : un URGENT coupe et vide la file, LOW / NORMAL
 * périment après 8 s), volume par annonce. Une seule annonce est confiée au moteur à la fois. Moteur créé au premier usage.
 */
```

3. Remplacer les lignes 34 à 36 :

```kotlin
    private val pending = ArrayDeque<Pair<String, Boolean>>()
    /** Identifiants des énoncés en cours ou en file côté moteur ; le focus audio est tenu tant qu'il n'est pas vide. */
    private val active = mutableSetOf<String>()
```

par :

```kotlin
    /** Annonces en attente (moteur pas prêt ou occupé). */
    private val queue = SpeechQueue()
    /** Énoncés confiés au moteur (un seul, deux le temps d'une interruption) ; le focus audio est tenu tant qu'il n'est pas vide. */
    private val active = mutableSetOf<String>()
```

4. Remplacer les lignes 53 à 64, c'est-à-dire tout `override fun speak(text: String, flush: Boolean) { … }`, par :

```kotlin
    override fun speak(text: String, flush: Boolean) = speak(text, flush, SpeechPriority.NORMAL)

    override fun speak(text: String, flush: Boolean, priority: SpeechPriority) {
        if (unavailable || isSilenced() || text.isBlank()) return
        val tts = ensureEngine() ?: return
        val start = synchronized(lock) {
            val interrupt = queue.offer(SpeechQueue.Item(text, priority, SystemClock.elapsedRealtime()), flush)
            // Moteur pas prêt : onInit videra la file ; moteur occupé sans interruption : finished() prendra la suite.
            if (!ready || !(interrupt || active.isEmpty())) return
            nextStart(SystemClock.elapsedRealtime(), flush = interrupt)
        } ?: return
        say(tts, start)
    }
```

5. Dans `stop()` (lignes 91 à 95), remplacer :

```kotlin
        val tts = synchronized(lock) {
            pending.clear()
            active.clear()
            engine
        }
```

par :

```kotlin
        val tts = synchronized(lock) {
            queue.clear()
            active.clear()
            engine
        }
```

6. Dans `onInit`, remplacer :

```kotlin
            synchronized(lock) {
                pending.clear()
                engine = null
            }
```

par :

```kotlin
            synchronized(lock) {
                queue.clear()
                engine = null
            }
```

Puis remplacer :

```kotlin
        val queued = synchronized(lock) { ready = true; pending.toList().also { pending.clear() } }
        queued.forEach { (text, flush) -> say(tts, text, flush) }
```

par :

```kotlin
        // Annonces mises en file avant que le moteur soit prêt : la première encore valable part, les suivantes via finished().
        val first = synchronized(lock) { ready = true; if (active.isEmpty()) nextStart(SystemClock.elapsedRealtime(), flush = false) else null }
        first?.let { say(tts, it) }
```

7. Remplacer les lignes 143 à 156 (`private fun say(...)` et `private fun finished(...)`) par :

```kotlin
    private class Start(val text: String, val flush: Boolean, val id: String, val requestFocus: Boolean)

    /** Tête de file encore valable, identifiant réservé dans [active] (appelé sous [lock]) ; null si rien à dire. */
    private fun nextStart(nowMs: Long, flush: Boolean): Start? {
        val item = queue.poll(nowMs) ?: return null
        val id = "roadly-${ids.incrementAndGet()}"
        val requestFocus = active.isEmpty()
        active += id
        return Start(item.text, flush, id, requestFocus)
    }

    private fun say(tts: TextToSpeech, s: Start) {
        if (isSilenced()) { finished(s.id); return }
        if (s.requestFocus) audio.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        if (tts.speak(s.text, if (s.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, s.id) != TextToSpeech.SUCCESS) finished(s.id)
    }

    /**
     * Fin d'un énoncé (terminé, en erreur ou interrompu) : enchaîne sur la prochaine annonce encore valable, sinon rend le focus.
     * Les identifiants inconnus (déjà purgés par [stop]) sont ignorés.
     */
    private fun finished(utteranceId: String?) {
        val (tts, next) = synchronized(lock) {
            if (utteranceId == null || !active.remove(utteranceId) || active.isNotEmpty()) return
            engine to (if (ready) nextStart(SystemClock.elapsedRealtime(), flush = false) else null)
        }
        if (tts != null && next != null) say(tts, next) else audio.abandonAudioFocusRequest(focusRequest)
    }
```

- [ ] **Étape 6 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest :feature:settings:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS.

Puis lancer : `.\gradlew.bat :core:navigation:compileDebugKotlin --offline -q`

Résultat attendu : compilation sans erreur de `AndroidSpeechService`.

- [ ] **Étape 7 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/SpeechService.kt core/navigation/src/main/java/fr/roadly/core/navigation/SpeechQueue.kt core/navigation/src/main/java/fr/roadly/core/navigation/AndroidSpeechService.kt core/navigation/src/main/java/fr/roadly/core/navigation/testing/FakeSpeechService.kt core/navigation/src/main/java/fr/roadly/core/navigation/GuidanceConstants.kt core/navigation/src/main/java/fr/roadly/core/navigation/AnnouncementPolicy.kt core/navigation/src/main/java/fr/roadly/core/navigation/NavigationSession.kt core/navigation/src/test/java/fr/roadly/core/navigation/SpeechQueueTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionGuidanceTest.kt
git commit -m @'
feat(voix): priorités URGENT / NORMAL / LOW, un NOW coupe la file, phrases périmées jetées (B5)

SpeechService gagne une surcharge priorisée (corps par défaut, ajout seulement) ; AndroidSpeechService confie une annonce à la fois
au moteur et jette les LOW / NORMAL en attente depuis plus de 8 s.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 8 : B5 — NOW selon le temps, phrase « puis », pas de FAR juste après une manœuvre

**Fichiers :**
- Modifier : `nav/AnnouncementPolicy.kt`, lignes 1-6 (imports), 12-26 (`SpeedClass`) et 29-51 (`next`), plus `SpokenPhrases.then`.
- Modifier : `nav/GuidanceConstants.kt` (ajout).
- Modifier : `nav/NavigationSession.kt` : `announce()` (après la tâche 7) et le bloc de recalcul.
- Test : `navtest/AnnouncementPolicyTest.kt` (assistant ligne 14, plus des ajouts) et `NavigationSessionGuidanceTest.kt` (ajouts).

**Interfaces :**
- Consomme : `RouteProgress.distancesToManeuversM` (tâche 3), `Tier.speechPriority()` (tâche 7).
- Produit :
  - `AnnouncementPolicy.next(progress: RouteProgress, maneuvers: List<Maneuver>, speedKmh: Double?, already: Set<Pair<Int, Tier>>, sincePreviousManeuverMs: Long? = null): Announcement?`
  - `internal fun AnnouncementPolicy.nowThresholdM(speedKmh: Double?): Double`
  - `SpokenPhrases.then(first: String, second: String): String`
  - `GuidanceConstants.NOW_MIN_M`, `NOW_LEAD_S`, `COMBINE_NEXT_MANEUVER_M`, `FAR_QUIET_AFTER_MANEUVER_MS`

- [ ] **Étape 1 : écrire les tests qui échouent.**

Dans `AnnouncementPolicyTest.kt`, remplacer les lignes 14 et 15 :

```kotlin
    private fun next(k: Int, d: Double, v: Double?, already: Set<Pair<Int, Tier>> = emptySet(), extrapolated: Boolean = false) =
        AnnouncementPolicy.next(progress(k, d, extrapolated), maneuvers, v, already)
```

par :

```kotlin
    private fun next(k: Int, d: Double, v: Double?, already: Set<Pair<Int, Tier>> = emptySet(), extrapolated: Boolean = false, sincePrevious: Long? = null) =
        AnnouncementPolicy.next(progress(k, d, extrapolated), maneuvers, v, already, sincePrevious)
```

Ajouter ensuite (import `org.junit.Assert.assertTrue`) :

```kotlin
    @Test fun `now threshold is four seconds of travel, at least 40 m`() {
        assertEquals(40.0, AnnouncementPolicy.nowThresholdM(null), 1e-9)
        assertEquals(40.0, AnnouncementPolicy.nowThresholdM(30.0), 1e-9)
        assertEquals(4 * 130 / 3.6, AnnouncementPolicy.nowThresholdM(130.0), 1e-9)
        assertEquals(Tier.NOW, next(1, 140.0, 130.0)!!.tier)
        assertEquals(Tier.NEAR, next(1, 150.0, 130.0)!!.tier)
    }

    @Test fun `at 130 km h and 1 Hz the now tier is never skipped`() {
        val step = 130 / 3.6
        var d = 2_500.0
        val already = mutableSetOf<Pair<Int, Tier>>()
        var nowAt: Double? = null
        while (d >= 0.0) {
            AnnouncementPolicy.next(progress(1, d), maneuvers, 130.0, already)?.let { a -> already += 1 to a.tier; if (a.tier == Tier.NOW) nowAt = d }
            d -= step
        }
        assertTrue("NOW au moins 3 s avant la manœuvre (à $nowAt m)", nowAt != null && nowAt!! >= 3 * step)
        assertEquals(setOf(Tier.FAR, Tier.NEAR, Tier.NOW), already.map { it.second }.toSet())
    }

    @Test fun `a following maneuver less than 200 m after is chained with puis`() {
        // Paris : manœuvre 1 (Rue de Lobau) puis 2 (Quai de l'Hôtel de Ville) 77 m plus loin.
        val p = progress(1, 70.0).copy(distancesToManeuversM = listOf(70.0 + 77.0, 400.0, 600.0))
        val a = AnnouncementPolicy.next(p, maneuvers, 30.0, emptySet())!!
        assertEquals(Tier.NEAR, a.tier)
        assertEquals("Dans 70 mètres, " + lower(maneuvers[1].verbalAlert!!).removeSuffix(".") + ", puis " + lower(maneuvers[2].verbalPre!!), a.text)
    }

    @Test fun `no chaining beyond 200 m, for the far tier, or toward the exit of the roundabout just entered`() {
        assertEquals(maneuvers[1].verbalPre, AnnouncementPolicy.next(progress(1, 30.0).copy(distancesToManeuversM = listOf(280.0)), maneuvers, 30.0, emptySet())!!.text)
        assertEquals("Dans 250 mètres, " + lower(maneuvers[1].verbalAlert!!), AnnouncementPolicy.next(progress(1, 250.0).copy(distancesToManeuversM = listOf(327.0)), maneuvers, 30.0, emptySet())!!.text)
        assertEquals(maneuvers[10].verbalPre, AnnouncementPolicy.next(progress(10, 30.0).copy(distancesToManeuversM = listOf(87.0)), maneuvers, 30.0, emptySet())!!.text)
    }

    @Test fun `no far announcement within 10 s of the previous maneuver`() {
        assertNull(next(1, 250.0, 30.0, sincePrevious = 9_999L))
        assertEquals(Tier.FAR, next(1, 250.0, 30.0, sincePrevious = 10_000L)!!.tier)
        assertEquals(Tier.NEAR, next(1, 70.0, 30.0, sincePrevious = 2_000L)!!.tier)
    }
```

Dans `NavigationSessionGuidanceTest.kt`, ajouter :

```kotlin
    @Test fun `straight drive - distances decrease and no announcement is repeated`() = runTest { // scénario 1
        val route = TestRoutes.straight()
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        val distances = mutableListOf<Pair<Int, Double>>()
        play(FixScript(route).drive(0.0, route.distanceMeters - 30.0, speedKmh = 50.0)) {
            val p = guiding(s).progress
            distances += p.nextManeuverIndex to p.distanceToNextManeuverM
        }
        assertEquals(speech.spoken.distinct(), speech.spoken)
        distances.groupBy { it.first }.values.forEach { d -> assertTrue(d.map { it.second }.zipWithNext().all { (a, b) -> b <= a }) }
    }

    @Test fun `130 km h at 1 Hz - the now announcement comes at least 3 s ahead, once`() = runTest {
        val route = TestRoutes.straight()
        val turnAt = RouteTracker(route).maneuverStartM[1]
        val now = route.maneuvers[1].verbalPre!!
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        var nowSpokenAt: Double? = null
        play(FixScript(route).drive(0.0, turnAt + 30.0, speedKmh = 130.0)) {
            if (nowSpokenAt == null && now in speech.spoken) nowSpokenAt = turnAt - guiding(s).progress.distanceAlongM
        }
        assertNotNull(nowSpokenAt)
        assertTrue("NOW à $nowSpokenAt m", nowSpokenAt!! >= 3 * 130 / 3.6)
        assertEquals(1, speech.spoken.count { it == now })
    }

    @Test fun `jitter at the turn never repeats an announcement over 10 fixes`() = runTest {
        val route = TestRoutes.straight()
        val turnAt = RouteTracker(route).maneuverStartM[1]
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(FixScript(route).drive(turnAt - 200.0, turnAt - 20.0, speedKmh = 30.0))
        play(FixScript(route, seed = 4).jitter(turnAt, count = 10, amplitudeM = 8.0))
        assertEquals(speech.spoken.distinct(), speech.spoken)
    }

    @Test fun `two close roundabouts - each entry said once, exit chained with puis, no doubled far`() = runTest { // scénario 6
        val route = TestRoutes.twoRoundabouts()
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(FixScript(route, seed = 9).drive(0.0, route.distanceMeters - 40.0, speedKmh = 30.0, noiseM = 2.0))
        assertEquals(speech.spoken.distinct(), speech.spoken)
        assertEquals(1, speech.spoken.count { it == "Entrez dans le rond-point et prenez la 2e sortie." })
        assertEquals(1, speech.spoken.count { it == "Entrez dans le rond-point et prenez la 1re sortie." })
        assertTrue(speech.spoken.any { it == "Quittez le rond-point dans Rue Médiane, puis entrez dans le rond-point et prenez la 1re sortie." })
        assertTrue("pas de FAR pour le 2e rond-point (moins de 10 s après la sortie du 1er)", speech.spoken.none { it.startsWith("Dans 100 mètres, entrez") || it.startsWith("Dans 150 mètres, entrez") })
    }
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*AnnouncementPolicyTest*"`

Résultat attendu : ÉCHEC de compilation (`nowThresholdM` inconnu, trop d'arguments pour `next`).

- [ ] **Étape 3 : écrire l'implémentation.**

`GuidanceConstants.kt`, avant l'accolade finale :

```kotlin
    /** B5 : l'annonce « maintenant » part à max(40 m, 4 s de trajet à la vitesse lissée). */
    const val NOW_MIN_M = 40.0
    const val NOW_LEAD_S = 4.0
    /** B5 : une manœuvre suivante à moins de 200 m est annoncée avec la courante (« …, puis … »). */
    const val COMBINE_NEXT_MANEUVER_M = 200.0
    /** B5 : pas d'annonce lointaine (FAR) dans les 10 s qui suivent le passage d'une manœuvre. */
    const val FAR_QUIET_AFTER_MANEUVER_MS = 10_000L
```

`AnnouncementPolicy.kt` :

1. Remplacer les imports (lignes 3 à 6, avec l'import `Formatters` de la tâche 3) par :

```kotlin
import fr.roadly.core.common.Formatters
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.RouteProgress
import java.util.Locale
import kotlin.math.max
```

2. Remplacer les lignes 12 à 16 :

```kotlin
/** Seuils d'annonce (m) par classe de vitesse — spec Phase 2 §2. */
internal enum class SpeedClass(val farM: Double, val nearM: Double, val nowM: Double) {
    HIGHWAY(2000.0, 300.0, 40.0),
    ROAD(600.0, 150.0, 40.0),
    CITY(300.0, 80.0, 40.0);
```

par :

```kotlin
/** Seuils FAR / NEAR (m) par classe de vitesse — spec Phase 2 §2 ; le seuil NOW dépend du temps (B5, `AnnouncementPolicy.nowThresholdM`). */
internal enum class SpeedClass(val farM: Double, val nearM: Double) {
    HIGHWAY(2000.0, 300.0),
    ROAD(600.0, 150.0),
    CITY(300.0, 80.0);
```

3. Remplacer tout `object AnnouncementPolicy { … }` (lignes 28 à 51) par :

```kotlin
/** Décide quoi annoncer pour la prochaine manœuvre ; pur et sans état (l'anti-répétition est portée par [already]). */
object AnnouncementPolicy {
    /** B5 : « maintenant » à max(40 m, 4 s × vitesse) — 144 m à 130 km/h, jamais sauté entre deux fixes à 1 Hz. */
    internal fun nowThresholdM(speedKmh: Double?): Double =
        max(GuidanceConstants.NOW_MIN_M, GuidanceConstants.NOW_LEAD_S * (speedKmh ?: 0.0) / 3.6)

    /** [sincePreviousManeuverMs] = temps depuis le passage de la manœuvre précédente (null = aucune depuis le départ) : pas de FAR avant 10 s. */
    fun next(
        progress: RouteProgress,
        maneuvers: List<Maneuver>,
        speedKmh: Double?,
        already: Set<Pair<Int, Tier>>,
        sincePreviousManeuverMs: Long? = null,
    ): Announcement? {
        val k = progress.nextManeuverIndex
        val m = maneuvers.getOrNull(k) ?: return null
        val d = progress.distanceToNextManeuverM
        val c = SpeedClass.of(speedKmh)
        val tier = when {
            d < nowThresholdM(speedKmh) -> Tier.NOW
            d <= c.nearM -> Tier.NEAR
            d <= c.farM -> Tier.FAR
            else -> return null
        }
        // Position extrapolée (GPS perdu) : jamais d'annonce « maintenant », trop risquée sans fix réel.
        if (tier == Tier.NOW && progress.extrapolated) return null
        if (tier == Tier.FAR && sincePreviousManeuverMs != null && sincePreviousManeuverMs < GuidanceConstants.FAR_QUIET_AFTER_MANEUVER_MS) return null
        if (already.any { (i, t) -> i == k && t.ordinal >= tier.ordinal }) return null
        val text = when (tier) {
            Tier.NOW -> m.verbalPre ?: m.instruction
            Tier.NEAR, Tier.FAR -> SpokenPhrases.inDistance(d, m.verbalAlert ?: m.verbalPre ?: m.instruction)
        }
        if (text.isBlank()) return null
        return Announcement(if (tier == Tier.FAR) text else chained(text, progress, maneuvers, k), tier, k)
    }

    /** B5 : manœuvre suivante à moins de 200 m → « …, puis … » (sauf la sortie du rond-point qu'on aborde, déjà dite par l'entrée). */
    private fun chained(text: String, progress: RouteProgress, maneuvers: List<Maneuver>, k: Int): String {
        val following = maneuvers.getOrNull(k + 1) ?: return text
        val gap = (progress.distancesToManeuversM.firstOrNull() ?: return text) - progress.distanceToNextManeuverM
        if (gap >= GuidanceConstants.COMBINE_NEXT_MANEUVER_M || following.type == ManeuverType.ROUNDABOUT_EXIT) return text
        val then = (following.verbalPre ?: following.instruction).trim()
        return if (then.isEmpty()) text else SpokenPhrases.then(text, then)
    }
}
```

4. Dans `SpokenPhrases`, après `fun inDistance(...)`, ajouter :

```kotlin
    /** « Tournez à droite dans X, puis tournez à gauche dans Y. » */
    fun then(first: String, second: String): String =
        "${first.trimEnd().removeSuffix(".")}, puis ${second.replaceFirstChar { it.lowercase(Locale.FRANCE) }}"
```

`NavigationSession.kt` :

1. Après la déclaration de `roundabouts` (tâche 6), ajouter :

```kotlin
        /** B5 : dernier indice de manœuvre vu et instant de son changement (passage d'une manœuvre). */
        private var lastNextIndex: Int? = null
        private var indexChangedAtMs: Long? = null
```

2. Remplacer l'`announce()` issu de la tâche 7 :

```kotlin
        private suspend fun announce(progress: RouteProgress) {
            if (rerouting || progress.routeId != route.id) return
            val a = AnnouncementPolicy.next(progress, route.maneuvers, speedKmh, announced) ?: return
            announced += a.maneuverIndex to a.tier
            say(a.text, routeId = progress.routeId, priority = a.tier.speechPriority())
        }
```

par :

```kotlin
        private suspend fun announce(progress: RouteProgress) {
            if (rerouting || progress.routeId != route.id) return
            val now = clock.nowMs()
            if (progress.nextManeuverIndex != lastNextIndex) {
                if (lastNextIndex != null) indexChangedAtMs = now
                lastNextIndex = progress.nextManeuverIndex
            }
            val a = AnnouncementPolicy.next(progress, route.maneuvers, speedKmh, announced, indexChangedAtMs?.let { now - it }) ?: return
            announced += a.maneuverIndex to a.tier
            say(a.text, routeId = progress.routeId, priority = a.tier.speechPriority())
        }
```

3. Dans le recalcul, remplacer :

```kotlin
                    announced.clear()
                    announced += 0 to Tier.NOW
```

par :

```kotlin
                    announced.clear()
                    announced += 0 to Tier.NOW
                    lastNextIndex = null; indexChangedAtMs = null
```

- [ ] **Étape 4 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS. Pourquoi les tests existants de `AnnouncementPolicyTest` passent :
- leur `RouteProgress` n'a pas de `distancesToManeuversM`, donc aucun enchaînement « puis » ;
- leurs vitesses donnent un seuil NOW de 40 m sous 36 km/h ;
- `threshold edges` reste vert : à 90 km/h, 300 m est un NEAR.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/AnnouncementPolicy.kt core/navigation/src/main/java/fr/roadly/core/navigation/GuidanceConstants.kt core/navigation/src/main/java/fr/roadly/core/navigation/NavigationSession.kt core/navigation/src/test/java/fr/roadly/core/navigation/AnnouncementPolicyTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionGuidanceTest.kt
git commit -m @'
feat(voix): NOW à 4 s de trajet, manœuvres rapprochées « puis », pas de FAR juste après une manœuvre (B5)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 9 : B6 — hors-itinéraire (cap, précision) et continuité (routes parallèles, ponts)

**Fichiers :**
- Modifier : `nav/GuidanceConstants.kt`, lignes 31-32 et 36-37.
- Modifier : `nav/RouteTracker.kt` : état (ligne 54), bloc `when` de `update` (lignes 90-98), `Hit` et `hold` (lignes 170-176), `headingOk` (lignes 178-183), filtre de `search` (lignes 195-200), retour de `project` (ligne 227).
- Modifier : `nav/testing/TestRoutes.kt` : ajout de `PARALLEL_ROAD_OFFSET_M`, `parallelRoads` et `overpass`.
- Test : `navtest/RouteTrackerParallelRoadsTest.kt` (nouveau), `NavigationSessionGuidanceTest.kt` (ajouts), `RouteTrackerOffRouteTest.kt` (contrat), `NavigationSessionTest.kt` (contrat).

**Interfaces :**
- Produit :
  - `GuidanceConstants.OFF_ROUTE_MAX_ACCURACY_M` (30f), `OFF_ROUTE_HEADING_DEG` (50.0), `OFF_ROUTE_HEADING_FIXES` (3), `CONTINUITY_TIE_M` (15.0), `CONTINUITY_MIN_GAP_M` (50.0), `CONTINUITY_BACK_M` (30.0).
  - `HARD_RULE_MAX_ACCURACY_M` et `SEGMENT_TIE_M` sont supprimés : ils ne sont utilisés que dans `RouteTracker`.
  - `TestRoutes.PARALLEL_ROAD_OFFSET_M = 25.0`, `TestRoutes.parallelRoads(...)`, `TestRoutes.overpass(...)`.

**Pourquoi un écart minimal de 50 m le long du tracé** pour qu'un candidat compte comme « autre passage » ? C'est l'écart que les simulations ont fait choisir.
- Simulation hors dépôt (Python, sans modification de fichier) sur les fixtures Paris et Toulouse, à 1 Hz et 30, 50 et 130 km/h.
- La règle actuelle (égalité à 10 m, écart > 10 m) produit des abscisses fausses de −10 à −13 m aux angles aigus (Paris : 6 points à 50 km/h).
- Une égalité à 15 m avec un écart > 10 m produit jusqu'à −31 m au virage serré du Pont d'Arcole.
- Une égalité à 15 m avec un écart > 50 m, une projection intérieure au segment et un recul limité à 30 m ne produit **aucune erreur** sur les deux fixtures. Elle garde le demi-tour de `outAndBack` (écart de 70 m) et le pont (écart de 438 m).

- [ ] **Étape 1 : créer les jeux de données.**

Dans `TestRoutes.kt`, ajouter :

```kotlin
    /** Écart (m) de la voie de desserte de [parallelRoads] (scénario 9). */
    const val PARALLEL_ROAD_OFFSET_M = 25.0

    /**
     * Route principale 1 km plein est (pas de 50 m), virage à droite à 600 m ; la voie de desserte, hors itinéraire, est parallèle
     * à [PARALLEL_ROAD_OFFSET_M] au nord (positions simulées par `FixScript.drive(lateralM = …)`).
     */
    fun parallelRoads(id: String = "parallelRoads", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = (0..20).map { xy(it * 50.0, 0.0) }
        val total = (0 until 20).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val first = (0 until 12).sumOf { pts[it].distanceTo(pts[it + 1]) }
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, first, 43.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(
                ManeuverType.TURN_RIGHT, "Tournez à droite dans Rue de la Desserte.", "Rue de la Desserte", total - first, 29.0, 12, null,
                verbalPre = "Tournez à droite dans Rue de la Desserte.", verbalAlert = "Tournez à droite dans Rue de la Desserte.",
            ),
            Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 20, null, verbalPre = "Votre destination est sur la droite."),
        )
        return Route(id, costing, total, 72.0, pts, maneuvers, hasToll = false, providerId = "test")
    }

    /**
     * Passage supérieur (B6, « pont au-dessus ») : 300 m plein est, 50 m nord, 175 m ouest, puis 100 m sud en recoupant le premier
     * tronçon à 125 m du départ (abscisse ≈ 563 m au croisement). Manœuvres : départ, 3 virages à gauche, arrivée.
     */
    fun overpass(id: String = "overpass", costing: RouteCosting = RouteCosting.FASTEST): Route {
        val pts = listOf(0.0, 50.0, 100.0, 150.0, 200.0, 250.0, 300.0).map { xy(it, 0.0) } +
            listOf(25.0, 50.0).map { xy(300.0, it) } +
            listOf(250.0, 200.0, 150.0, 125.0).map { xy(it, 50.0) } +
            listOf(25.0, 0.0, -25.0, -50.0).map { xy(125.0, it) }
        val cum = DoubleArray(pts.size).also { c -> for (i in 1 until pts.size) c[i] = c[i - 1] + pts[i - 1].distanceTo(pts[i]) }
        fun len(a: Int, b: Int) = cum[b] - cum[a]
        val maneuvers = listOf(
            Maneuver(ManeuverType.DEPART, "Conduisez vers l'est.", null, len(0, 6), 30.0, 0, null, verbalPre = "Conduisez vers l'est."),
            Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche.", null, len(6, 8), 8.0, 6, null, verbalPre = "Tournez à gauche."),
            Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche.", null, len(8, 12), 18.0, 8, null, verbalPre = "Tournez à gauche."),
            Maneuver(ManeuverType.TURN_LEFT, "Tournez à gauche sur le pont.", null, len(12, 16), 12.0, 12, null, verbalPre = "Tournez à gauche sur le pont."),
            Maneuver(ManeuverType.ARRIVE, "Vous êtes arrivé.", null, 0.0, 0.0, 16, null, verbalPre = "Votre destination est devant vous."),
        )
        return Route(id, costing, cum.last(), 68.0, pts, maneuvers, hasToll = false, providerId = "test")
    }
```

- [ ] **Étape 2 : écrire les tests qui échouent et adapter le contrat.**

`navtest/RouteTrackerParallelRoadsTest.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.testing.FixScript
import fr.roadly.core.navigation.testing.TestRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B6 : routes parallèles, GPS imprécis, pont au-dessus (scénarios 9 et 10). */
class RouteTrackerParallelRoadsTest {
    @Test fun `8 s on a parallel road 25 m away never trigger off route`() {
        val r = TestRoutes.parallelRoads()
        val t = RouteTracker(r)
        FixScript(r).drive(100.0, 100.0 + 7 * 50 / 3.6, speedKmh = 50.0, lateralM = TestRoutes.PARALLEL_ROAD_OFFSET_M).forEach {
            t.update(it); assertFalse(t.isOffRoute)
        }
    }

    @Test fun `a parallel road 45 m away in the same direction is not off route, a diverging heading is`() {
        val r = TestRoutes.parallelRoads()
        val same = RouteTracker(r)
        FixScript(r).drive(100.0, 300.0, speedKmh = 50.0, lateralM = 45.0).forEach { same.update(it) }
        assertFalse("même sens que le tracé : pas de recalcul", same.isOffRoute)
        val away = RouteTracker(r)
        FixScript(r).drive(100.0, 300.0, speedKmh = 50.0, lateralM = 45.0, headingOffsetDeg = -70.0).forEach { away.update(it) }
        assertTrue("cap écarté de 70° sur plus de 3 fixes : hors itinéraire", away.isOffRoute)
    }

    @Test fun `imprecise fixes decide neither off route nor a now announcement`() { // scénario 10
        val r = TestRoutes.straight()
        val t = RouteTracker(r)
        t.update(LocationFix(t.pointAt(280.0), null, null, 5f, 0L))
        FixScript(r, seed = 21).jitter(300.0, count = 20, amplitudeM = 50.0, accuracyM = 60f).forEach { f ->
            val p = t.update(f)
            assertFalse(t.isOffRoute)
            assertTrue(AnnouncementPolicy.next(p, r.maneuvers, 30.0, setOf(1 to Tier.FAR))?.tier != Tier.NOW)
        }
    }

    @Test fun `a bridge over an earlier part of the route keeps the along and the index`() {
        val r = TestRoutes.overpass()
        val t = RouteTracker(r)
        t.update(LocationFix(TestRoutes.xy(100.0, 0.0), null, null, 5f, 0L))
        t.update(LocationFix(TestRoutes.xy(115.0, 0.0), null, null, 5f, 1_000L))
        // 12 m au nord du croisement : 0 m du passage supérieur (abscisse ≈ 563), 12 m du passage en cours (abscisse 125).
        val p = t.update(LocationFix(TestRoutes.xy(125.0, 12.0), null, null, 5f, 2_000L))
        assertEquals(125.0, p.distanceAlongM, 1.0)
        assertEquals(1, p.nextManeuverIndex)
        assertFalse(t.isOffRoute)
    }
}
```

Dans `NavigationSessionGuidanceTest.kt`, ajouter :

```kotlin
    @Test fun `8 s on a parallel road 45 m away do not trigger a reroute`() = runTest { // scénario 9
        val route = TestRoutes.parallelRoads()
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(FixScript(route).drive(100.0, 100.0 + 7 * 50 / 3.6, speedKmh = 50.0, lateralM = 45.0))
        assertTrue(requests.isEmpty())
        assertFalse(guiding(s).rerouting)
    }

    @Test fun `after a reroute the maneuver index and the announcements start over`() = runTest { // scénario 12
        val route = TestRoutes.straight()
        routingResult = Outcome.Success(listOf(TestRoutes.straight("rerouted")))
        val tracker = RouteTracker(route)
        val turnAt = tracker.maneuverStartM[1]
        val near = "Dans 70 mètres, tournez à droite dans Rue d'Essai."
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        play(FixScript(route).drive(turnAt - 70.0, turnAt - 70.0, speedKmh = 36.0))
        assertEquals(1, speech.spoken.count { it == near })
        play(listOf(LocationFix(TestRoutes.north(tracker.pointAt(turnAt - 60.0), 150.0), 20f, 10f, 5f, 0L)))
        assertEquals("rerouted", guiding(s).route.id)
        play(FixScript(route).drive(turnAt - 66.0, turnAt - 66.0, speedKmh = 36.0))
        assertEquals(1, guiding(s).progress.nextManeuverIndex)
        assertEquals("annonces réinitialisées : NEAR redit sur le nouveau tracé", 2, speech.spoken.count { it == near })
    }
```

Contrat de `RouteTrackerOffRouteTest.kt` :

1. Remplacer la ligne 13 :

```kotlin
    private fun fix(p: GeoPoint, accuracyM: Float = 5f) = LocationFix(p, null, null, accuracyM, 0L)
```

par :

```kotlin
    /** Cap et vitesse inconnus par défaut : seule la règle des 100 m peut alors déclencher (B6). */
    private fun fix(p: GeoPoint, accuracyM: Float = 5f, bearing: Float? = null, speedMps: Float? = null) = LocationFix(p, bearing, speedMps, accuracyM, 0L)
    /** Fix qui s'éloigne du tracé plein est : cap 20°, soit 70° d'écart (> 50°). */
    private fun diverging(p: GeoPoint, accuracyM: Float = 5f) = fix(p, accuracyM, bearing = 20f, speedMps = 10f)
```

2. Dans `five consecutive fixes beyond 40 m trigger off route`, remplacer :

```kotlin
            t.update(fix(TestRoutes.north(t.pointAt(300.0 + it * 10), 60.0)))
```

par :

```kotlin
            t.update(diverging(TestRoutes.north(t.pointAt(300.0 + it * 10), 60.0)))
```

Puis remplacer :

```kotlin
        t.update(fix(TestRoutes.north(t.pointAt(340.0), 60.0)))
        assertTrue(t.isOffRoute)
    }
```

par :

```kotlin
        t.update(diverging(TestRoutes.north(t.pointAt(340.0), 60.0)))
        assertTrue(t.isOffRoute)
    }

    @Test fun `five fixes beyond 40 m on the route heading are not off route`() {
        val t = RouteTracker(route)
        repeat(8) { t.update(fix(TestRoutes.north(t.pointAt(300.0 + it * 10), 60.0), bearing = 90f, speedMps = 10f)) }
        assertFalse("route parallèle suivie dans le même sens", t.isOffRoute)
    }
```

3. Dans `coming back on route resets the counter`, remplacer les deux occurrences de `repeat(4) { t.update(fix(TestRoutes.north(` par `repeat(4) { t.update(diverging(TestRoutes.north(`.

4. Remplacer tout le test `a single imprecise fix beyond 100 m cannot trigger the hard rule` (de `    @Test fun \`a single imprecise fix beyond 100 m cannot trigger the hard rule\`() {` jusqu'à son `    }`) par :

```kotlin
    @Test fun `imprecise fixes beyond 100 m are ignored by every off-route rule`() {
        val t = RouteTracker(route)
        t.update(fix(t.pointAt(300.0)))
        val far = TestRoutes.north(t.pointAt(300.0), 150.0)
        repeat(6) {
            t.update(diverging(far, accuracyM = 80f))
            assertFalse("fix imprécis ${it + 1}", t.isOffRoute)
        }
        t.update(diverging(far, accuracyM = 10f))
        assertTrue("un fix précis à 150 m déclenche", t.isOffRoute)
    }
```

5. Remplacer tout le test `a fix of exactly 50 m accuracy still triggers the hard rule` par :

```kotlin
    @Test fun `a fix of exactly 30 m accuracy still decides, 31 m does not`() {
        val t = RouteTracker(route)
        t.update(fix(TestRoutes.north(t.pointAt(300.0), 150.0), accuracyM = 31f))
        assertFalse(t.isOffRoute)
        t.update(fix(TestRoutes.north(t.pointAt(300.0), 150.0), accuracyM = 30f))
        assertTrue(t.isOffRoute)
    }
```

Contrat de `NavigationSessionTest.kt` :

1. Remplacer les lignes 71 et 72 :

```kotlin
    private fun fixAt(along: Double, speedMps: Float? = 10f, northM: Double = 0.0, timestampMs: Long = 0L) =
        LocationFix(TestRoutes.north(tracker.pointAt(along), northM), 90f, speedMps, 5f, timestampMs)
```

par :

```kotlin
    /** Un écart latéral simulé s'accompagne d'un cap qui s'éloigne du tracé (20°, soit 70° d'écart, B6) ; sinon cap du tracé (90°). */
    private fun fixAt(along: Double, speedMps: Float? = 10f, northM: Double = 0.0, timestampMs: Long = 0L, bearing: Float = if (northM > 0.0) 20f else 90f) =
        LocationFix(TestRoutes.north(tracker.pointAt(along), northM), bearing, speedMps, 5f, timestampMs)
```

2. Dans `a reroute on a moving fix passes the GPS heading`, remplacer `        assertEquals(90f, requests.single().originHeadingDeg)` par `        assertEquals(20f, requests.single().originHeadingDeg)`.

3. Dans `a reroute on a slow fix sends no heading`, remplacer :

```kotlin
        repeat(5) { location.emit(fixAt(300.0, speedMps = 1f, northM = 60.0)); runCurrent() }
```

par :

```kotlin
        // Lent : pas de cap GPS, seule la règle des 100 m s'applique (B6).
        repeat(5) { location.emit(fixAt(300.0, speedMps = 1f, northM = 150.0)); runCurrent() }
```

- [ ] **Étape 3 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*RouteTrackerParallelRoadsTest*" --tests "*RouteTrackerOffRouteTest*"`

Résultat attendu : ÉCHEC.
- `a parallel road 45 m away…` : hors itinéraire à tort.
- `a bridge…` : abscisse 563 au lieu de 125.
- `imprecise fixes beyond 100 m…` : déclenche à tort.
- `a fix of exactly 30 m…` : 31 m déclenche.

- [ ] **Étape 4 : écrire l'implémentation.**

`GuidanceConstants.kt`. Remplacer les lignes 31 et 32 :

```kotlin
    /** Un fix de précision > 50 m ne peut pas déclencher seul la règle « hors itinéraire > 100 m ». */
    const val HARD_RULE_MAX_ACCURACY_M = 50f
```

par :

```kotlin
    /** B6 : un fix de précision > 30 m est ignoré par toute décision « hors itinéraire » (compteurs inchangés). */
    const val OFF_ROUTE_MAX_ACCURACY_M = 30f
    /** B6 : la règle des 5 fixes > 40 m exige aussi un cap écarté de plus de 50° du tracé sur 3 fixes consécutifs (route parallèle). */
    const val OFF_ROUTE_HEADING_DEG = 50.0
    const val OFF_ROUTE_HEADING_FIXES = 3
```

Remplacer les lignes 36 et 37 :

```kotlin
    /** Quasi-égalité de distance perpendiculaire (m) : on préfère alors la continuité le long du tracé. */
    const val SEGMENT_TIE_M = 10.0
```

par :

```kotlin
    /** B6 : à écart perpendiculaire quasi égal (≤ 15 m), un autre passage du tracé proche de la dernière abscisse l'emporte (pont, échangeur)… */
    const val CONTINUITY_TIE_M = 15.0
    /** … s'il est à plus de 50 m le long du tracé (en deçà : simple angle du même passage)… */
    const val CONTINUITY_MIN_GAP_M = 50.0
    /** … et ne recule pas de plus de 30 m (même valeur que le recul confirmé de B1). */
    const val CONTINUITY_BACK_M = 30.0
```

`RouteTracker.kt` :

1. Ligne 54. Remplacer `    private var consecutiveOff = 0` par :

```kotlin
    private var consecutiveOff = 0
    /** B6 : fixes consécutifs dont le cap GPS s'écarte de plus de 50° du tracé. */
    private var consecutiveHeadingOff = 0
```

2. Lignes 90 à 98. Remplacer :

```kotlin
        when {
            best.offM > GuidanceConstants.OFF_ROUTE_HARD_M && fix.accuracyM <= GuidanceConstants.HARD_RULE_MAX_ACCURACY_M -> {
                consecutiveOff++; isOffRoute = true
            }
            best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M -> {
                consecutiveOff++; isOffRoute = consecutiveOff >= GuidanceConstants.OFF_ROUTE_SOFT_FIXES
            }
            else -> { consecutiveOff = 0; isOffRoute = false }
        }
```

par :

```kotlin
        // B6 : un fix imprécis (> 30 m) ne change aucune décision « hors itinéraire ».
        if (fix.accuracyM <= GuidanceConstants.OFF_ROUTE_MAX_ACCURACY_M) updateOffRoute(best, gpsHeading)
```

3. Lignes 170 à 183. Remplacer :

```kotlin
    private class Hit(val segment: Int, val alongM: Double, val offM: Double, val snapped: GeoPoint)
```

par :

```kotlin
    /** [interior] = projection strictement à l'intérieur du segment (pas sur une extrémité). */
    private class Hit(val segment: Int, val alongM: Double, val offM: Double, val snapped: GeoPoint, val interior: Boolean = false)

    /**
     * B6 : hors itinéraire si > 100 m une fois, ou > 40 m sur 5 fixes consécutifs **et** cap écarté de plus de 50° sur 3 fixes
     * consécutifs : une route parallèle suivie dans le même sens n'est pas une sortie d'itinéraire.
     */
    private fun updateOffRoute(best: Hit, gpsHeading: Float?) {
        val deviates = gpsHeading != null && headingDiffDeg(best.segment, gpsHeading) > GuidanceConstants.OFF_ROUTE_HEADING_DEG
        consecutiveHeadingOff = if (deviates) consecutiveHeadingOff + 1 else 0
        when {
            best.offM > GuidanceConstants.OFF_ROUTE_HARD_M -> { consecutiveOff++; isOffRoute = true }
            best.offM > GuidanceConstants.OFF_ROUTE_SOFT_M -> {
                consecutiveOff++
                isOffRoute = consecutiveOff >= GuidanceConstants.OFF_ROUTE_SOFT_FIXES &&
                    consecutiveHeadingOff >= GuidanceConstants.OFF_ROUTE_HEADING_FIXES
            }
            else -> { consecutiveOff = 0; isOffRoute = false }
        }
    }
```

Puis remplacer :

```kotlin
    /** Vrai si le segment [i] est à au plus 90° du cap [headingDeg] (un segment nul est toujours accepté). */
    private fun headingOk(i: Int, headingDeg: Float): Boolean {
        val b = segBearingDeg[i]
        if (b.isNaN()) return true
        return abs(((b - headingDeg + 540.0) % 360.0) - 180.0) <= GuidanceConstants.HEADING_MAX_DIFF_DEG
    }
```

par :

```kotlin
    /** Écart (0..180°) entre le cap du segment [i] et [headingDeg] ; 0 pour un segment nul. */
    private fun headingDiffDeg(i: Int, headingDeg: Float): Double {
        val b = segBearingDeg[i]
        if (b.isNaN()) return 0.0
        return abs(((b - headingDeg + 540.0) % 360.0) - 180.0)
    }

    /** Vrai si le segment [i] est à au plus 90° du cap [headingDeg] (un segment nul est toujours accepté). */
    private fun headingOk(i: Int, headingDeg: Float): Boolean = headingDiffDeg(i, headingDeg) <= GuidanceConstants.HEADING_MAX_DIFF_DEG
```

4. Dans `search`, mettre à jour la KDoc (lignes 185 à 189) : « À distance perpendiculaire quasi égale (≤ 15 m), on préfère, pour un candidat d'un autre passage (> 50 m le long du tracé, projection intérieure, recul ≤ 30 m), celui dont `along` est le plus proche de [lastAlong] (B6). »

Remplacer ensuite les lignes 195 à 200 :

```kotlin
        if (lastAlong != null) {
            val alt = pool
                .filter { it.offM < best.offM + GuidanceConstants.SEGMENT_TIE_M && abs(it.alongM - best.alongM) > GuidanceConstants.SEGMENT_TIE_M }
                .minByOrNull { abs(it.alongM - lastAlong) }
            if (alt != null && abs(alt.alongM - lastAlong) < abs(best.alongM - lastAlong)) best = alt
        }
```

par :

```kotlin
        if (lastAlong != null) {
            // B6 : continuité plutôt que distance brute (pont, échangeur, chaussées séparées) ; une extrémité de segment voisin n'est pas
            // un autre passage, et un candidat qui recule de plus de 30 m est écarté.
            val alt = pool
                .filter {
                    it.interior && it.offM <= best.offM + GuidanceConstants.CONTINUITY_TIE_M &&
                        abs(it.alongM - best.alongM) > GuidanceConstants.CONTINUITY_MIN_GAP_M &&
                        it.alongM >= lastAlong - GuidanceConstants.CONTINUITY_BACK_M
                }
                .minByOrNull { abs(it.alongM - lastAlong) }
            if (alt != null && abs(alt.alongM - lastAlong) < abs(best.alongM - lastAlong)) best = alt
        }
```

5. Ligne 227, dans `project`. Remplacer :

```kotlin
        return Hit(i, cumDistM[i] + t * segLenM[i], sqrt(qx * qx + qy * qy), GeoPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon)))
```

par :

```kotlin
        return Hit(i, cumDistM[i] + t * segLenM[i], sqrt(qx * qx + qy * qy), GeoPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon)), interior = t > 0.0 && t < 1.0)
```

- [ ] **Étape 5 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:navigation:testDebugUnitTest :feature:navigation:testDebugUnitTest :feature:route:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS. Points de contrôle :
- les 6 tests de `RouteTrackerContinuityTest` restent verts (demi-tour sans cap : l'aller l'emporte car il est plus proche de la dernière abscisse) ;
- les tests de recalcul de `NavigationSessionTest` déclenchent toujours au 5e fix.

- [ ] **Étape 6 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/GuidanceConstants.kt core/navigation/src/main/java/fr/roadly/core/navigation/RouteTracker.kt core/navigation/src/main/java/fr/roadly/core/navigation/testing/TestRoutes.kt core/navigation/src/test/java/fr/roadly/core/navigation/RouteTrackerParallelRoadsTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionGuidanceTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/RouteTrackerOffRouteTest.kt core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionTest.kt
git commit -m @'
feat(navigation): hors-itinéraire exigeant un écart de cap, fixes imprécis ignorés, continuité aux ponts (B6)

Route parallèle suivie dans le même sens : pas de recalcul ; précision > 30 m : aucune décision ; à écart quasi égal (≤ 15 m),
un autre passage du tracé à plus de 50 m le long cède à la continuité (supprime aussi les erreurs de -10 à -13 m aux angles aigus).

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 10 : B6 — persistance de la course (`lastGuidance`) et vérification finale

**Fichiers :**
- Modifier : `core/data/src/main/java/fr/roadly/core/data/repository/UserPreferencesRepository.kt`, lignes 3-13 (imports), 31 (`LastGuidance`), 68 (interface) et 87-124 (implémentation DataStore).
- Modifier : `core/data/src/main/java/fr/roadly/core/data/testing/FakeUserPreferencesRepository.kt`, lignes 49-52.
- Modifier : `nav/NavigationSession.kt`, lignes 79-113 (`start`, `stop`) et `arrive()` (lignes 292-293).
- Modifier : `feature/route/src/main/java/fr/roadly/feature/route/RouteViewModel.kt`, ligne 138.
- Modifier : `ROADLY_3D_NAVIGATION_TESTS.md`, « Journal des essais réels » (ligne 47).
- Test : `core/data/src/test/java/fr/roadly/core/data/repository/DataStoreUserPreferencesRepositoryTest.kt` et `NavigationSessionGuidanceTest.kt` (ajouts), `feature/route/src/test/java/fr/roadly/feature/route/RouteViewModelTest.kt` (nouveau test).

**Interfaces :**
- Produit :
  - `data class LastGuidance(val destination: GeoPoint, val destinationName: String?, val routeId: String, val startedAtEpochMs: Long)`, dans `fr.roadly.core.data.repository`.
  - `UserPreferencesRepository.lastGuidance: Flow<LastGuidance?>` et `suspend fun setLastGuidance(value: LastGuidance?)`.
  - `NavigationSession.start(option: RouteOption, preferences: RoutePreferences, vehicle: VehicleProfile? = null, destinationName: String? = null)`. Le paramètre est ajouté avec une valeur par défaut.
- Hors étape : le bouton « Reprendre le guidage vers … » relève de l'**Étape E3**, qui consommera `lastGuidance`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

Dans `DataStoreUserPreferencesRepositoryTest.kt`, ajouter (imports `fr.roadly.core.model.GeoPoint` et `org.junit.Assert.assertNull`) :

```kotlin
    @Test fun `last guidance round trip and clear`() = runTest {
        assertNull(repo.lastGuidance.first())
        val saved = LastGuidance(GeoPoint(48.8738, 2.2950), "Arc de Triomphe", "route-1", 1_782_131_520_000L)
        repo.setLastGuidance(saved)
        assertEquals(saved, repo.lastGuidance.first())
        repo.setLastGuidance(saved.copy(destinationName = null))
        assertEquals(null, repo.lastGuidance.first()!!.destinationName)
        repo.setLastGuidance(null)
        assertNull(repo.lastGuidance.first())
    }
```

Dans `NavigationSessionGuidanceTest.kt`, ajouter (imports `fr.roadly.core.data.repository.LastGuidance`, `fr.roadly.core.location.LocationPermissionState` et `kotlinx.coroutines.flow.first`) :

```kotlin
    @Test fun `the run is remembered at start and forgotten at stop`() = runTest { // scénario 14 (JVM)
        val route = TestRoutes.straight()
        val s = session(); s.start(option(route), RoutePreferences(), destinationName = "Maison"); runCurrent()
        assertEquals(LastGuidance(route.geometry.last(), "Maison", route.id, 0L), prefs.lastGuidance.first())
        s.stop(); runCurrent()
        assertNull(prefs.lastGuidance.first())
    }

    @Test fun `the run is forgotten at arrival`() = runTest {
        val route = TestRoutes.straight()
        val s = session(); s.start(option(route), RoutePreferences()); runCurrent()
        assertNotNull(prefs.lastGuidance.first())
        play(FixScript(route).drive(RouteTracker(route).totalGeometryM, RouteTracker(route).totalGeometryM, speedKmh = 36.0))
        assertTrue(s.state.value is NavigationState.Arrived)
        assertNull(prefs.lastGuidance.first())
    }

    @Test fun `nothing is remembered when the run cannot start`() = runTest {
        location.permission.value = LocationPermissionState.DENIED
        val s = session(); s.start(option(TestRoutes.straight()), RoutePreferences()); runCurrent()
        assertNull(prefs.lastGuidance.first())
    }
```

Dans `RouteViewModelTest.kt`, ajouter avant l'accolade finale de la classe (imports `kotlinx.coroutines.flow.first` et `kotlinx.coroutines.test.runCurrent` si absents) :

```kotlin
    @Test fun `start guidance remembers the destination name`() = runTest {
        location.lastKnown = LocationFix(GeoPoint(48.8, 2.3), null, null, 5f, 0L)
        val vm = vm()
        vm.uiState.test {
            advanceUntilIdle()
            expectMostRecentItem()
            assertTrue(vm.startGuidance())
            runCurrent()
            assertEquals("Test", prefs.lastGuidance.first()?.destinationName)
            cancelAndIgnoreRemainingEvents()
        }
    }
```

- [ ] **Étape 2 : vérifier que les tests échouent.**

Lancer : `.\gradlew.bat :core:data:testDebugUnitTest --offline -q --tests "*DataStoreUserPreferencesRepositoryTest*"`

Résultat attendu : ÉCHEC de compilation (`LastGuidance` inconnu).

- [ ] **Étape 3 : écrire l'implémentation (données).**

`UserPreferencesRepository.kt` :

1. Ajouter les imports `androidx.datastore.preferences.core.doublePreferencesKey`, `androidx.datastore.preferences.core.longPreferencesKey` et `fr.roadly.core.model.GeoPoint`.

2. Après `object GuidanceDefaults { … }` (ligne 30), ajouter :

```kotlin
/** B6 : course de guidage en cours ou interrompue (mort du processus) — base du « Reprendre le guidage » de l'Étape E3. */
data class LastGuidance(val destination: GeoPoint, val destinationName: String?, val routeId: String, val startedAtEpochMs: Long)
```

3. Dans l'interface, remplacer :

```kotlin
    suspend fun setCockpitTint(name: String?)
}
```

par :

```kotlin
    suspend fun setCockpitTint(name: String?)
    /** B6 : course en cours, mémorisée au démarrage du guidage ; null après un arrêt ou une arrivée. */
    val lastGuidance: Flow<LastGuidance?>
    suspend fun setLastGuidance(value: LastGuidance?)
}
```

4. Dans `DataStoreUserPreferencesRepository`, après `private val kTint = stringPreferencesKey("cockpit.tint")`, ajouter :

```kotlin
    private val kLastLat = doublePreferencesKey("guidance.last.lat")
    private val kLastLon = doublePreferencesKey("guidance.last.lon")
    private val kLastName = stringPreferencesKey("guidance.last.name")
    private val kLastRoute = stringPreferencesKey("guidance.last.route")
    private val kLastAt = longPreferencesKey("guidance.last.at")
```

Puis remplacer :

```kotlin
    override suspend fun setCockpitTint(name: String?) { store.edit { if (name == null) it.remove(kTint) else it[kTint] = name } }
}
```

par :

```kotlin
    override suspend fun setCockpitTint(name: String?) { store.edit { if (name == null) it.remove(kTint) else it[kTint] = name } }
    override val lastGuidance: Flow<LastGuidance?> = store.data.map { p ->
        val lat = p[kLastLat]; val lon = p[kLastLon]; val routeId = p[kLastRoute]; val at = p[kLastAt]
        if (lat == null || lon == null || routeId == null || at == null) null else LastGuidance(GeoPoint(lat, lon), p[kLastName], routeId, at)
    }
    override suspend fun setLastGuidance(value: LastGuidance?) {
        store.edit {
            if (value == null) {
                it.remove(kLastLat); it.remove(kLastLon); it.remove(kLastName); it.remove(kLastRoute); it.remove(kLastAt)
            } else {
                it[kLastLat] = value.destination.lat; it[kLastLon] = value.destination.lon
                it[kLastRoute] = value.routeId; it[kLastAt] = value.startedAtEpochMs
                if (value.destinationName == null) it.remove(kLastName) else it[kLastName] = value.destinationName
            }
        }
    }
}
```

`FakeUserPreferencesRepository.kt`. Ajouter l'import `fr.roadly.core.data.repository.LastGuidance`. Remplacer :

```kotlin
    override suspend fun setCockpitTint(name: String?) { tint.value = name }
}
```

par :

```kotlin
    override suspend fun setCockpitTint(name: String?) { tint.value = name }
    val last = MutableStateFlow<LastGuidance?>(null)
    override val lastGuidance: Flow<LastGuidance?> = last
    override suspend fun setLastGuidance(value: LastGuidance?) { last.value = value }
}
```

- [ ] **Étape 4 : écrire l'implémentation (session et écran d'itinéraire).**

`NavigationSession.kt` :

1. Ajouter l'import `fr.roadly.core.data.repository.LastGuidance`.

2. Remplacer les lignes 79 à 81 :

```kotlin
    /** Démarre (ou remplace) la course sur [option] ; les [preferences] sont réutilisées lors des recalculs. */
    /** [vehicle] : profil transmis au recalcul (costing poids lourd / moto / taxi) ; null = auto. */
    fun start(option: RouteOption, preferences: RoutePreferences, vehicle: VehicleProfile? = null) {
```

par :

```kotlin
    /** Démarre (ou remplace) la course sur [option] ; les [preferences] sont réutilisées lors des recalculs. */
    /** [vehicle] : profil transmis au recalcul (costing poids lourd / moto / taxi) ; null = auto. [destinationName] : nom mémorisé (B6). */
    fun start(option: RouteOption, preferences: RoutePreferences, vehicle: VehicleProfile? = null, destinationName: String? = null) {
```

3. Remplacer :

```kotlin
        job = scope.launch(confined) { run.execute() }
    }
```

par :

```kotlin
        job = scope.launch(confined) { run.execute() }
        // B6 : course mémorisée pour être proposée à la reprise après la mort du processus (bouton : Étape E3).
        persistLastGuidance(LastGuidance(option.route.geometry.last(), destinationName, option.route.id, clock.nowMs()))
    }
```

4. Remplacer :

```kotlin
        cancelRun(NavigationState.Idle)
        speech.release()
```

par :

```kotlin
        cancelRun(NavigationState.Idle)
        speech.release()
        persistLastGuidance(null)
```

5. Après la fonction `publishFailure`, ajouter :

```kotlin
    /** Écrit ou efface ([value] null) la dernière course ; l'ordre est garanti par [confined], une erreur DataStore ne touche pas au guidage. */
    private fun persistLastGuidance(value: LastGuidance?) {
        scope.launch(confined) {
            try {
                prefs.setLastGuidance(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Mémoire non disponible : la reprise ne sera simplement pas proposée.
            }
        }
    }
```

6. Dans `arrive()`, remplacer :

```kotlin
        publish(NavigationState.Arrived(route))
        tripRecorder.end(arrived = true, nowMs = clock.nowMs())
```

par :

```kotlin
        publish(NavigationState.Arrived(route))
        persistLastGuidance(null)
        tripRecorder.end(arrived = true, nowMs = clock.nowMs())
```

`RouteViewModel.kt`, ligne 138. Remplacer :

```kotlin
        session.start(option, computedWith, computedVehicle)
```

par :

```kotlin
        session.start(option, computedWith, computedVehicle, destinationName = destName.ifBlank { null })
```

- [ ] **Étape 5 : vérifier que les tests passent.**

Lancer : `.\gradlew.bat :core:data:testDebugUnitTest :core:navigation:testDebugUnitTest :feature:route:testDebugUnitTest --offline -q`

Résultat attendu : SUCCÈS.

- [ ] **Étape 6 : vérification complète.**

Lancer : `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q`

Résultat attendu : SUCCÈS, avec au moins 319 tests existants verts et les nouveaux tests de l'étape.

Lancer : `.\gradlew.bat :app:assembleDebug --offline -q`

Résultat attendu : BUILD SUCCESSFUL. Cette commande vérifie aussi `AndroidSpeechService`, `ManeuverIcon` et `NavigationScreen`.

Dans `ROADLY_3D_NAVIGATION_TESTS.md`, ajouter une ligne au journal (après la ligne 47) :

```markdown
| (date d'exécution) | B : 1–14, 20 automatisés (JVM) | — | verts | essais réels à faire : `adb shell am kill fr.roadly` en guidage → `lastGuidance` présent (bouton « Reprendre » : E3), 130 km/h sur autoroute, double rond-point |
```

- [ ] **Étape 7 : commiter.**

```powershell
git add core/data/src/main/java/fr/roadly/core/data/repository/UserPreferencesRepository.kt core/data/src/main/java/fr/roadly/core/data/testing/FakeUserPreferencesRepository.kt core/data/src/test/java/fr/roadly/core/data/repository/DataStoreUserPreferencesRepositoryTest.kt core/navigation/src/main/java/fr/roadly/core/navigation/NavigationSession.kt core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionGuidanceTest.kt feature/route/src/main/java/fr/roadly/feature/route/RouteViewModel.kt feature/route/src/test/java/fr/roadly/feature/route/RouteViewModelTest.kt ROADLY_3D_NAVIGATION_TESTS.md
git commit -m @'
feat(navigation): course mémorisée dans DataStore au démarrage, effacée à l'arrêt et à l'arrivée (B6)

Destination, nom, identifiant du tracé et date ; le bouton « Reprendre le guidage » viendra à l'Étape E3.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

## Surface de fusion avec `feat/roadly-ai`

| Fichier | Les deux branches | Résolution |
|---|---|---|
| `nav/SpeechService.kt` | IA : `val speaking` ; B : `SpeechPriority` et la surcharge `speak(text, flush, priority)` | garder les deux : seuls des ajouts |
| `nav/AndroidSpeechService.kt` | IA : `_speaking` (dans `stop`, `say`, `finished`) et des logs ; B : `SpeechQueue`, `nextStart`, `Start` | garder la version B. Placer `_speaking.value = true` dans `nextStart` (après `active += id`), `_speaking.value = active.isNotEmpty() \|\| next != null` dans `finished`, `_speaking.value = false` dans `stop` |
| `nav/testing/FakeSpeechService.kt` | IA : `speaking`, `setSpeaking`, `flushes` ; B : `priorities` et la surcharge | garder tout. Dans la surcharge à 3 paramètres : `spoken += text; flushes += flush; priorities += priority` |
| `NavigationViewModel.kt` | IA : constructeur `GuidanceActions`, `reportsAhead` ; B : champ `lastUi` et une ligne du lambda `combine` | aucune ligne commune ; la structure du `combine` est inchangée |
| `NavigationScreen.kt` | IA : emplacements assistant ; B : une ligne de `NavigationContent` (`approaching = approach != null`) | aucune signature touchée |
| `RouteViewModel.kt` | IA : `guidance.start(option, …, destName)` ; B : `session.start(…, destinationName = …)` | garder l'appel IA, et faire transmettre `destinationName` à `session.start` par `GuidanceActions.start` |
| `NavigationViewModelTest.kt`, `RouteViewModelTest.kt` | constructeurs modifiés par l'IA | réappliquer les 2 assertions B2 et le nouveau test B6 |

Aucun fichier de `core/routing`, `core/model`, `RouteTracker`, `AnnouncementPolicy` ou `NavigationUiState` n'est modifié par `feat/roadly-ai`.

## LIMITES documentées par cette étape

- Voies : le serveur public n'en renvoie aucune sur le trajet de Toulouse (test « repère »).
- Le nombre total de sorties d'un rond-point est absent de `/route` : `exitCount` vaut `null`, et les autres sorties ne sont jamais dessinées.
- Les virages et les zones de l'Horizon gardent l'abscisse de la géométrie brute (moins de 2 %, Paris 0,06 %).
- `MERGE_LEFT` et `MERGE_RIGHT` utilisent les flèches « légèrement à gauche / à droite » : Material n'a pas d'icône de fusion avec côté.
- Panneau : `exit_name_elements` (« Le Palays ») est présent sur le serveur mais n'est pas modélisé (Étape D3).

---

## Auto-revue

**1. Couverture de la spécification.**

| Exigence de `ROADLY_3D_NAVIGATION_PLAN.md` §B | Tâche | Test d'acceptation |
|---|---|---|
| B1 index monotone sauf recalcul ou recul > 30 m ; passée à `max(10 m, 2 × précision)` | 1 | `RouteTrackerProgressTest` (jitter ±8 m sur 10 positions, marge, recul confirmé) |
| B1 fenêtre conservée, hystérésis d'approche | 1, 2 | `BuildUiStateTest` (approche maintenue, relâchée au changement d'index), `GuidanceCameraPolicyTest` |
| B1 double rond-point à 120 m | 1, 8 | `two roundabouts 120 m apart…`, scénario 6 (session) |
| B2 même abscisse, `distScale`, plus de `lengthMeters` | 3 | `distances to the following maneuvers share the banner base`, `horizon and banner use the same base…` (écart < 1 m) |
| B2 table d'arrondi commune (430 m → « 450 mètres » / « 450 m ») | 3 | `FormattersTest`, `SpokenPhrasesTest`, `430 m reads 450 m on the banner` |
| B3 bearings, `end_shape_index`, `verbal_post`, `side_of_street`, tous les `street_names`, `valid` / `active` séparés | 4 | `ValhallaRoutingProviderTest`, `ValhallaLanesTest`, `GuidanceModelTest` |
| B3 côté de `ManeuverType`, `turnAngleDeg`, icônes fidèles | 4 | `ValhallaManeuverTypesTest`, `ManeuverIconTest`, scénario 3 (`leftTurn`) |
| B3 fixture FOSSGIS autoroutière réelle (sortie et échangeur), sans régression | 5 | `ValhallaMotorwayFixtureTest`, `MotorwayGuidanceTest` (scénarios 7 et 8) |
| B4 `RoundaboutInfo` depuis ENTER et EXIT, anneau, angle, rien d'inventé | 6 | `RoundaboutResolverTest` (Paris 2e et 5e sorties, Toulouse 3e) |
| B4 cible figée, sortie annoncée au bon moment, exposée dans `Guiding` | 6 | `exit target is frozen…`, `the exit … announced inside the ring only`, test de session |
| B5 NOW à `max(40 m, 4 s × v)` ; 130 km/h à 1 Hz jamais sauté | 8 | `AnnouncementPolicyTest`, test de session à 130 km/h |
| B5 « puis » sous 200 m ; pas de FAR à moins de 10 s | 8 | `…chained with puis`, `no far announcement within 10 s…`, scénario 6 |
| B5 priorités, NOW vide la file, phrase périmée non dite | 7 | `SpeechQueueTest` (FAR long et NOW, 8 s), `announcements carry their priority` |
| B5 aucune annonce dupliquée sur 10 positions | 8 | `jitter at the turn never repeats…`, scénario 1 |
| B6 écart de cap > 50° sur 3 fixes ou > 100 m ; précision > 30 m ignorée | 9 | `RouteTrackerParallelRoadsTest`, `RouteTrackerOffRouteTest` (contrat), scénario 10 |
| B6 continuité (route parallèle, pont) | 9 | `8 s on a parallel road…` (tracker et session), `a bridge over…` |
| B6 persistance (`lastOption`, désormais `lastGuidance`) | 10 | `DataStoreUserPreferencesRepositoryTest`, sessions démarrage / arrêt / arrivée, `RouteViewModelTest` |
| Scénarios 11, 13, 20 | existants | `NavigationSessionTest` (perte GPS, arrivée, échec de recalcul qui garde l'ancien tracé) |
| Scénario 12 (recalcul : index et annonces réinitialisés) | 9 | `after a reroute the maneuver index and the announcements start over` |
| Scénario 14 (mort du processus) | 10 | JVM : persistance. Appareil : `am kill`, à consigner. Bouton « Reprendre » : E3 |

**2. Recherche de trous.**
- Aucun « TBD », « TODO », « à compléter » ni « comme la tâche N ».
- Les seules valeurs qui dépendent d'une donnée externe sont dans le test « repère » de la tâche 5. Elles ont été lues dans la réponse réelle du 2026-10-31, et l'étape 2 dit comment les remplacer depuis le fichier téléchargé.

**3. Cohérence des noms entre tâches.**
- `FixScript.drive` et `FixScript.jitter` : mêmes paramètres nommés (`speedKmh`, `noiseM`, `lateralM`, `headingOffsetDeg`, `accuracyM`, `amplitudeM`) dans les tâches 1, 4, 5, 6, 8 et 9.
- `TestRoutes.xy` est défini à la tâche 1 et utilisé aux tâches 4 et 9.
- `RouteProgress.distancesToManeuversM` est défini à la tâche 3 et utilisé aux tâches 5 et 8.
- `ValhallaManeuverTypes.of` est défini à la tâche 4 et utilisé aux tâches 4 et 5.
- `ValhallaFixture`, `ParisRoute` et `MotorwayRoute` : tâches 4 et 5, utilisés à la tâche 6.
- `RoundaboutResolver.resolve / resolveAll / current(map, nextIndex)` : tâche 6 uniquement.
- `SpeechPriority`, `SpeechQueue.Item(text, priority, queuedAtMs)`, `Tier.speechPriority()` et `FakeSpeechService.priorities` : définis à la tâche 7, `speechPriority` est repris dans l'`announce()` de la tâche 8.
- `AnnouncementPolicy.next(…, sincePreviousManeuverMs)` et `nowThresholdM` : tâche 8.
- `LastGuidance(destination, destinationName, routeId, startedAtEpochMs)` et `setLastGuidance` : tâche 10.
- Constantes : `MANEUVER_PASSED_MIN_M`, `BACKTRACK_CONFIRM_*` (tâche 1) ; `SPEECH_STALE_AFTER_MS` (tâche 7) ; `NOW_MIN_M`, `NOW_LEAD_S`, `COMBINE_NEXT_MANEUVER_M`, `FAR_QUIET_AFTER_MANEUVER_MS` (tâche 8) ; `OFF_ROUTE_MAX_ACCURACY_M`, `OFF_ROUTE_HEADING_*`, `CONTINUITY_*` (tâche 9). `HARD_RULE_MAX_ACCURACY_M` et `SEGMENT_TIE_M` sont supprimés à la tâche 9 et plus utilisés nulle part.
- Les blocs `old` des tâches 3, 6, 7, 8 et 9 dans `RouteTracker`, `NavigationSession` et `AnnouncementPolicy` reprennent le texte laissé par les tâches précédentes : `progressAt(…, next)` de la tâche 1, `announce()` de la tâche 7, bloc de recalcul complété aux tâches 6 et 8.

### Fichiers critiques pour l'implémentation
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\navigation\src\main\java\fr\roadly\core\navigation\RouteTracker.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\navigation\src\main\java\fr\roadly\core\navigation\NavigationSession.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\navigation\src\main\java\fr\roadly\core\navigation\AnnouncementPolicy.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\routing\src\main\java\fr\roadly\core\routing\ValhallaRoutingProvider.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\feature\navigation\src\main\java\fr\roadly\feature\navigation\NavigationUiState.kt`
