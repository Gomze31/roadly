# Étape E — Audio et intégration — plan d'implémentation

> **Pour les agents d'exécution :** SOUS-COMPÉTENCE OBLIGATOIRE : utiliser superpowers:subagent-driven-development (recommandé) ou superpowers:executing-plans pour exécuter ce plan tâche par tâche. Les étapes utilisent des cases à cocher (`- [ ]`) pour le suivi.

Emplacement proposé : `docs/superpowers/plans/2026-11-01-roadly-3d-etape-e.md`.

**Objectif :** fermer la refonte du guidage côté audio et intégration :
- un seul canal de parole partagé par le guidage et l'assistant (E1). L'ordre de priorité est urgence GPS > guidage > assistant. Le NOW coupe l'assistant ; l'assistant reprend 300 ms après le guidage ; les médias sont atténués. L'arrêt et la sourdine de la session ne détruisent plus les messages de l'assistant ;
- un groupe « Navigation » dans Réglages (E2) : vue au départ, zoom, inclinaison, rotation, détail, bâtiments 3D, ligne « puis », économie d'énergie. Tout est persisté et appliqué au guidage ;
- une carte « Reprendre le guidage vers … » sur l'accueil après la mort du processus (E3), plus les vérifications en arrière-plan sur le HONOR.

**Architecture :**
- E1 :
  - l'arbitrage des canaux est en Kotlin pur, testé sur JVM : `VoiceArbiter` (table de décision) et `SpeechQueue` (deux files, péremption, délai de 300 ms) ;
  - `AndroidSpeechService` reste le coordinateur (le « `NavigationVoiceCoordinator` » de la spécification). Il ne fait qu'appliquer ces décisions au moteur `TextToSpeech` et au focus audio ;
  - `SpeechService` gagne des membres additifs, avec un corps par défaut quand c'est possible, et le même `speaking` que `feat/roadly-ai`.
- E2 :
  - les nouveaux réglages passent par `UserPreferencesRepository` (DataStore) ;
  - `CameraMode3D`, `PitchPreset` et `PowerSaveMode` sont dans `core:model`, car `core:data` ne voit pas `core:map` ;
  - `GuidanceCameraPolicy` et `NavigationCameraController` reçoivent le décalage de zoom, l'inclinaison choisie et les bâtiments ;
  - `NavigationViewModel` expose ces réglages en `StateFlow` séparées. Le `combine` de `uiState` n'est pas touché.
- E3 :
  - la session expose la course mémorisée quand elle est au repos (`resumableGuidance`) et sait l'oublier (`forgetLastGuidance`) ;
  - `HomeViewModel` refait un itinéraire depuis la position actuelle (même calcul que `GuidanceActions.startTo` de l'IA), puis appelle `session.start` comme « ROULER » ;
  - l'écran ouvre le guidage par le rappel existant `onResumeGuidance`. Aucune signature d'écran ou de navigation n'est touchée.

**Pile technique :** Kotlin 2.2.10, Jetpack Compose (BOM 2026.05.00, Material 3), Hilt 2.58, Android `TextToSpeech` / `AudioManager` (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`), DataStore Preferences, MapLibre Android 11.13.5, JUnit 4.13.2, kotlinx-coroutines-test, Turbine, adb (HONOR `AREE026226000174`).

**Spécification :** `ROADLY_3D_NAVIGATION_PLAN.md`, § « Étape E — Audio et intégration » (E1 `NavigationVoiceCoordinator`, E2 Réglages, E3 Arrière-plan et reprise). Lire aussi :
- `ROADLY_3D_NAVIGATION_TESTS.md` : scénarios 14, 16, 17 et 20, et le journal (lignes de l'étape C : « kill -9 → accueil sans reprise (E3) », « à faire en roulant : voix avec musique ») ;
- `ROADLY_3D_NAVIGATION_AUDIT.md` : §3 (parole, session), §6 (contrat de coordination vocale avec Roadly AI), §7 (réglages absents).

## Contraintes globales

**Environnement et commandes**
- Travailler uniquement dans le worktree `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d`, branche `feat/roadly-3d`, base `8907dd8`.
- Lancer Gradle avec l'outil PowerShell, toujours avec `--offline`. Les tâches utilisées sont :
  - `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q` ;
  - `:core:model:test` ;
  - `:core:data:testDebugUnitTest` ;
  - `:core:map:testDebugUnitTest` ;
  - `:feature:navigation:testDebugUnitTest` ;
  - `:feature:settings:testDebugUnitTest` ;
  - `:feature:home:testDebugUnitTest` ;
  - `:feature:route:testDebugUnitTest` ;
  - `:core:navigation:compileDebugKotlin` ;
  - `:feature:navigation:compileDebugAndroidTestKotlin` ;
  - `:feature:navigation:connectedDebugAndroidTest` ;
  - `:app:assembleDebug`.
- Pour filtrer un test : `--tests "*NomDuTest*"`.
- Les numéros de ligne cités sont ceux de `8907dd8`. Quand une tâche précédente a déjà modifié un fichier, se repérer au bloc `ancien` cité, qui sert d'ancre.

**Langue et constantes**
- Écrire en français les KDoc, commentaires, chaînes, messages de commit et noms de tests descriptifs.
- Chaque seuil est une constante nommée avec sa KDoc : `VoiceArbiter.ASSISTANT_RESUME_GAP_MS` (300), `VoiceArbiter.RELEASE_DEFER_MAX_MS` (10 000), `GuidanceDefaults.*` (E2), `PitchPreset.degrees`.

**Commits**
- Chaque commit est en français et se termine par la ligne `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.

**Données et tests**
- Aucune fausse fonctionnalité :
  - une reprise refait un vrai itinéraire Valhalla ;
  - un réglage sans effet réel n'est pas proposé ;
  - un échec est affiché, pas masqué.
- Chaque limite est écrite « LIMITE » dans la KDoc ou dans `ROADLY_3D_NAVIGATION_TESTS.md`.
- Les 473 tests JVM existants restent verts, et aucune assertion existante ne change. Les 11 tests instrumentés de `NavigationPremiumUiTest` restent verts (14 instrumentés en tout).
- Les signatures modifiées gagnent des paramètres avec une valeur par défaut, placés en fin de liste. Exception, le constructeur Hilt de `HomeViewModel` : son seul appel de test est mis à jour.

**Surface de fusion avec `feat/roadly-ai`** (worktree `GPS-roadly-ai`, lecture seule ; base commune `60033fe`)
- `SpeechService` :
  - `speaking: StateFlow<Boolean>` a exactement le nom, le type et le sens de l'IA (« en cours ou en file ») ;
  - `speak(text, flush)` est inchangé ;
  - tout le reste est additif.
- `FakeSpeechService` reprend les noms de l'IA : `setSpeaking`, `flushes`, et `stop()` qui remet `speaking` à faux.
- Fichiers que l'IA modifie et que E ne touche pas : `HomeNavigation.kt`, `RoadlyNavHost.kt`, `RouteViewModel.kt`, la signature de `HomeScreen`, et les lignes d'appel des overlays.
- `GuidanceActions.start` (IA) devra transmettre `destinationName` à `session.start` après fusion (voir « Surface de fusion »).

**Interface et appareil**
- Pas de `Color(0x…)` dans les écrans. Toutes les couleurs viennent de `LocalRoadlyColors`. Les `backgroundColor = 0xFF…` des `@Preview` suivent l'usage du design system.
- Les chaînes vont dans le `strings.xml` du module :
  - `feature/settings` : insérer après la ligne 32, jamais en fin de fichier (l'IA y ajoute ses chaînes) ;
  - `feature/home` : insérer après la ligne 20.
- Appareil : HONOR `adb -s AREE026226000174`, application `fr.roadly` (debug, débogable). Le fichier DataStore est `files/datastore/roadly_prefs.preferences_pb`.
- Modifier `ROADLY_3D_NAVIGATION_TESTS.md` uniquement avec l'outil Edit, en gardant les accents.

---

## Écarts constatés (vérifiés le 2026-11-01) et arbitrages

Ces points ont été vérifiés dans le code de `8907dd8` et dans le diff `60033fe..feat/roadly-ai`. Ils précisent les décisions prises sans les rouvrir.

1. **`NavigationVoiceCoordinator` = l'existant.**
   - `AndroidSpeechService` (file B5, époques, délais de garde) et `SpeechQueue` forment déjà un coordinateur à un seul canal. Aucune classe Android n'est ajoutée.
   - L'arbitrage pur va dans `VoiceArbiter`, utilisé par `SpeechQueue`.
   - `AndroidSpeechService` n'est pas testable sur JVM (`TextToSpeech`, `AudioManager`). Il ne contient plus de règle : il applique les décisions de la file. Il est vérifié sur l'appareil (tâche 9), avec des traces `RoadlySpeech` (« Dit », « Coupé », « Fini »).
2. **Table d'arbitrage.**

   | Arrivée | Énoncé en cours | Effet |
   |---|---|---|
   | guidage URGENT (NOW, arrivée) | quelconque | coupé, les deux files vidées : la phrase de l'assistant est coupée, pas mise en pause |
   | guidage NORMAL / LOW | assistant | assistant coupé, puis remis en tête de sa file pour être redit en entier |
   | guidage NORMAL / LOW | guidage ou rien | mis en file |
   | guidage avec `flush` | quelconque | coupé, file du guidage vidée ; un assistant coupé est remis en file |
   | assistant | guidage | mis en file : il ne coupe jamais le guidage, même avec `flush` |
   | assistant avec `flush` | assistant | l'assistant est remplacé (seule sa file est vidée) |

   - L'assistant ne repart que 300 ms après la fin du dernier énoncé du guidage. C'est la règle `RESUME_AFTER_GUIDANCE_MS` de l'IA, appliquée dans la file.
   - La péremption de 8 s vaut aussi pour l'assistant, toutes priorités confondues. Pour un message coupé puis remis en file, l'âge est compté depuis la coupure.
   - LIMITE : `TextToSpeech` ne reprend pas au milieu d'une phrase. Un message préempté est redit depuis le début.
3. **`stop` / `setMuted` / `release`.**
   - `stop()` et `setMuted(muted)` gardent leur sens, sur tous les canaux. C'est ce qu'utilisent le volume à zéro et le « Tester la voix ».
   - La session passe à `stop(VoiceChannel.GUIDANCE)` et `setMuted(muted, VoiceChannel.GUIDANCE)`.
   - `stop(channel: VoiceChannel?)` est déclaré sans valeur par défaut : `stop()` existe déjà, donc un `= null` serait mort. La KDoc dit « null = tous ».
   - `release()` est différé tant qu'un message de l'assistant est en cours ou en file : le guidage se tait, le moteur est libéré quand l'assistant se tait, au plus tard après 10 s. Une annonce du guidage annule cette libération différée.
4. **Focus audio rendu sur le fil principal.**
   - Constat : `finished()` est appelé depuis le fil du moteur, et `stop()` depuis le fil de la session. Les deux appelaient `abandonAudioFocusRequest` hors du fil principal (défaut relevé à l'étape C).
   - Choix : le rendu passe par `settle()`, posté sur `main`. Il vérifie sous verrou que plus rien n'est en cours ni en file. Ainsi, pendant les 300 ms d'attente de l'assistant, la musique n'est pas rendue entre deux phrases.
5. **`speaking` couvre les deux canaux.** C'est la règle « pas de chevauchement ». Après fusion, un message de l'assistant passé par le moteur met aussi en pause la voix live de l'IA (`followGuidanceSpeech`). C'est voulu, et ce n'est pas un défaut.
6. **« Tester la voix » (Réglages).** La phrase passe sur le canal ASSISTANT, en priorité LOW avec `flush` (décision). Une annonce du guidage peut donc la préempter, ce qui sert à l'essai sur appareil (tâche 9). La sourdine du guidage ne la coupe pas, ce qui correspond à la KDoc existante : « même si la voix du guidage est désactivée ».
7. **`CameraMode3D` déménage dans `core:model`.**
   - Constat : il est défini dans `core/map/.../NavigationCameraController.kt` (lignes 7-18). `core:data`, qui doit persister `defaultCameraMode`, ne dépend pas de `core:map`.
   - Choix : l'enum passe telle quelle dans `core/model/.../CameraMode3D.kt`, avec le même nom, `toggled3d()`, `default()`, et `initial()` en plus. Cinq imports sont mis à jour (tâche 4).
   - Pas de `typealias` : l'accès aux entrées d'une enum par alias n'est pas garanti.
8. **`map.3d` devient « Bâtiments 3D ».**
   - La spécification E2 sépare « vue par défaut 2D / 3D » et « bâtiments 3D ». Le réglage actuel « Carte 3D en guidage » fait les deux.
   - Choix :
     - la clé `map.3d` (`map3dEnabled`) ne règle plus que les bâtiments extrudés, en vue 3D ;
     - la vue au départ est `defaultCameraMode`. Tant qu'elle n'a pas été choisie, elle se déduit de `map.3d` (`CameraMode3D.default`). C'est la migration, et elle garde verte l'assertion existante `with the 3d setting off the session starts in 2d` ;
     - pour que basculer les bâtiments ne change pas la vue, `SettingsViewModel.setMap3d` fige d'abord la vue courante (`setDefaultCameraMode(defaultCameraMode.first())`).
   - Les libellés `settings_map_3d` et `settings_map_3d_subtitle` changent de texte (la clé est gardée).
9. **Inclinaison.**
   - La spécification demande 45 / 58 / 65°. MapLibre Android 11.13.5 plafonne à 60° (`MAX_PITCH`, LIMITE de C), d'où `PitchPreset { LOW=45, STANDARD=58, HIGH=60 }`.
   - Le preset remplace `PITCH_3D` en croisière.
   - L'approche reste à 50°, et l'économie d'énergie force 45°.
   - L'inclinaison « ville dense » (60°) ne s'applique qu'avec STANDARD ou HIGH. Avec LOW, on reste à 45°.
   - `PITCH_3D` reste une constante, égale à `PitchPreset.STANDARD.degrees` (testé).
10. **Décalage de zoom à l'approche.**
    - Le décalage s'ajoute au zoom de croisière et au zoom 2D.
    - À l'approche, seul −1 s'applique (`ZOOM_APPROACH + min(offset, 0)`). +1 n'agit pas, car `ZOOM_APPROACH = 17,5` vient du correctif `3c02729` : il fait tenir la flèche entière sous la pile du bandeau.
    - Le sous-titre du réglage le dit.
11. **« Aperçu de la manœuvre suivante » = la ligne « puis … » (D1).**
    - Le filtre est appliqué une fois, dans `NavigationContent` : `full = state.copy(...).withNextManeuverPreview(show)`, ligne 187. `BannerStack` et ses deux appels ne changent pas.
    - L'aperçu (feuille) garde ses « suivantes ».
12. **Économie d'énergie.**
    - `rememberPowerSaveMode()` (économiseur d'Android) est gardé.
    - L'économie effective vaut `PowerSaveMode.effective(systemSaver)` (pur, testé). Elle est calculée dans l'écran par `derivedStateOf`. `GuidanceMapEffects` reçoit toujours un `State<Boolean>`.
    - Tout ce qui lit `powerSave` suit : bâtiments, inclinaison 45°, `MAX_FPS_POWER_SAVE = 30`.
13. **Chemin de reprise (E3).**
    - Constat :
      - il n'y a pas de `GuidanceActions` sur cette branche ;
      - le départ « ROULER » vit dans `RouteViewModel.startGuidance()`, derrière l'écran d'itinéraire (deux touchers) ;
      - l'IA modifie la ligne d'appel de `HomeScreen` dans `HomeNavigation.kt`, et l'appel de `homeScreen` dans `RoadlyNavHost.kt` : un nouveau rappel d'écran y créerait un conflit certain.
    - Choix :
      - `HomeViewModel.resumeLastGuidance()` refait le calcul de `GuidanceActions.startTo` : `RouteAlternativesUseCase` depuis `location.lastKnown` vers `lastGuidance.destination`, préférences et véhicule du moment, option FASTEST ;
      - puis il appelle `session.start(best, prefs, vehicle, destinationName)`, le même appel que « ROULER » ;
      - l'écran ouvre le guidage par `onResumeGuidance` (déjà câblé vers `NavigationRoute`) dès que la session guide ;
      - le tracé enregistré n'est pas réutilisé (décision).
    - LIMITE : ce chemin ne redemande pas `POST_NOTIFICATIONS`. Elle a été demandée au premier guidage, qui a créé `lastGuidance`.
14. **Service et mort du processus.** `NavigationService` reste en `START_NOT_STICKY` (décision), sans code ajouté. Après un `kill -9`, ni service ni notification : la reprise est manuelle, par la carte.
15. **Tests instrumentés.** Aucun n'est ajouté. `TestNavigation.kt` construit `NavigationViewModel` et `FakeSpeechService` avec des signatures inchangées. Les 11 tests de `NavigationPremiumUiTest` sont rejoués à la tâche 9.

---

## Structure des fichiers

| Fichier | Rôle | Tâches |
|---|---|---|
| `corenav/SpeechService.kt` | `VoiceChannel` (1) ; `speaking`, `speak(…, channel)`, `setMuted(…, channel)`, `stop(channel)` (2) | 1, 2 |
| `corenav/VoiceArbiter.kt` (nouveau) | table d'arbitrage, constantes 300 ms / 10 s | 1 |
| `corenav/SpeechQueue.kt` | deux files, péremption, délai de l'assistant | 1 |
| `corenavtest/VoiceArbiterTest.kt` (nouveau) | table, scénario 17, préemption, péremption | 1 |
| `corenav/AndroidSpeechService.kt` | coordinateur à deux canaux | 2 |
| `corenav/testing/FakeSpeechService.kt` | canaux, `speaking`, `flushes` (noms de l'IA) | 2 |
| `corenav/NavigationSession.kt` | canal GUIDANCE (3) ; `resumableGuidance`, `forgetLastGuidance` (8) | 3, 8 |
| `corenavtest/NavigationSessionVoiceTest.kt` (nouveau) | scénario 16, la session ne touche pas l'assistant | 3 |
| `set/SettingsViewModel.kt`, `settest/SettingsViewModelTest.kt` | « Tester la voix » en assistant (3) ; groupe Navigation (7) | 3, 7 |
| `model/CameraMode3D.kt`, `model/PitchPreset.kt`, `model/PowerSaveMode.kt` (nouveaux), `modeltest/NavigationDisplayTest.kt` (nouveau) | types E2 | 4 |
| `data/repository/UserPreferencesRepository.kt`, `data/testing/FakeUserPreferencesRepository.kt`, `datatest/repository/DataStoreUserPreferencesRepositoryTest.kt` | six réglages, migration de la vue | 4 |
| `map/NavigationCameraController.kt`, `maptest/NavigationCameraControllerTest.kt` | enum retirée, import (4) ; zoom, inclinaison, bâtiments (5) | 4, 5 |
| `map/GuidanceCameraPolicy.kt`, `maptest/GuidanceCameraPolicyTest.kt` | zoom, inclinaison | 5 |
| `nav/NavigationViewModel.kt`, `navtest/NavigationViewModelTest.kt` | import (4) ; flux E2, mode de départ (6) | 4, 6 |
| `nav/NavigationScreen.kt` | import (4) ; économie, bâtiments, entrées caméra, « puis » (6) | 4, 6 |
| `nav/NavigationUiState.kt` | `withNextManeuverPreview` | 6 |
| `set/NavigationGroup.kt` (nouveau), `set/SettingsScreen.kt`, `feature/settings/src/main/res/values/strings.xml` | groupe « Navigation » | 7 |
| `corenavtest/NavigationSessionGuidanceTest.kt` | reprise au repos, « Ignorer » | 8 |
| `home/HomeViewModel.kt`, `home/HomeUiState.kt`, `home/ResumeGuidanceCard.kt` (nouveau), `home/HomeScreen.kt`, `feature/home/src/main/res/values/strings.xml`, `feature/home/build.gradle.kts`, `hometest/HomeViewModelTest.kt` | carte de reprise | 8 |
| `ROADLY_3D_NAVIGATION_TESTS.md`, `docs/captures/etape-e/*.png` | essais réels | 9 |

Préfixes des chemins :
- `corenav/` = `core/navigation/src/main/java/fr/roadly/core/navigation/` ;
- `corenavtest/` = `core/navigation/src/test/java/fr/roadly/core/navigation/` ;
- `model/` = `core/model/src/main/java/fr/roadly/core/model/` ;
- `modeltest/` = `core/model/src/test/java/fr/roadly/core/model/` ;
- `data/` = `core/data/src/main/java/fr/roadly/core/data/` ;
- `datatest/` = `core/data/src/test/java/fr/roadly/core/data/` ;
- `map/` = `core/map/src/main/java/fr/roadly/core/map/` ;
- `maptest/` = `core/map/src/test/java/fr/roadly/core/map/` ;
- `nav/` = `feature/navigation/src/main/java/fr/roadly/feature/navigation/` ;
- `navtest/` = `feature/navigation/src/test/java/fr/roadly/feature/navigation/` ;
- `set/` = `feature/settings/src/main/java/fr/roadly/feature/settings/` ;
- `settest/` = `feature/settings/src/test/java/fr/roadly/feature/settings/` ;
- `home/` = `feature/home/src/main/java/fr/roadly/feature/home/` ;
- `hometest/` = `feature/home/src/test/java/fr/roadly/feature/home/`.

---

### Tâche 1 : E1 — `VoiceArbiter` et `SpeechQueue` à deux canaux (pur, JVM)

**Fichiers :**
- Modifier `corenav/SpeechService.kt`, lignes 3-4 : ajout de `VoiceChannel`.
- Créer `corenav/VoiceArbiter.kt`.
- Remplacer `corenav/SpeechQueue.kt` (fichier entier, 28 lignes).
- Test : `corenavtest/VoiceArbiterTest.kt` (nouveau). `SpeechQueueTest.kt` n'est pas modifié et doit rester vert.

**Interfaces :**
- Consomme :
  - `SpeechPriority { URGENT, NORMAL, LOW }` ;
  - `GuidanceConstants.SPEECH_STALE_AFTER_MS` (8 000).
- Produit :
  - `enum class VoiceChannel { GUIDANCE, ASSISTANT }` (public, dans `SpeechService.kt`) ;
  - `internal data class Arbitration(val interrupt: Boolean, val requeueInterrupted: Boolean, val cleared: Set<VoiceChannel>)` ;
  - `internal object VoiceArbiter { const val ASSISTANT_RESUME_GAP_MS = 300L; const val RELEASE_DEFER_MAX_MS = 10_000L; fun arbitrate(channel: VoiceChannel, priority: SpeechPriority, flush: Boolean, active: VoiceChannel?): Arbitration }` ;
  - `internal class SpeechQueue(maxAgeMs: Long = …, resumeGapMs: Long = VoiceArbiter.ASSISTANT_RESUME_GAP_MS)` :
    - `data class Item(text, priority, queuedAtMs, channel: VoiceChannel = GUIDANCE)` ;
    - `val isEmpty` ;
    - `fun offer(item: Item, flush: Boolean = false, active: Item? = null): Boolean` ;
    - `fun poll(nowMs: Long): Item?` ;
    - `fun readyAtMs(nowMs: Long): Long?` ;
    - `fun onFinished(channel: VoiceChannel, nowMs: Long)` ;
    - `fun hasAssistantWork(active: Item?): Boolean` ;
    - `fun clear(channel: VoiceChannel? = null)`.

- [ ] **Étape 1 : écrire le test qui échoue.**

`corenavtest/VoiceArbiterTest.kt` :

```kotlin
package fr.roadly.core.navigation

import fr.roadly.core.navigation.SpeechPriority.LOW
import fr.roadly.core.navigation.SpeechPriority.NORMAL
import fr.roadly.core.navigation.SpeechPriority.URGENT
import fr.roadly.core.navigation.VoiceChannel.ASSISTANT
import fr.roadly.core.navigation.VoiceChannel.GUIDANCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E1 : un seul canal de parole — urgence GPS > guidage > assistant ; l'assistant reprend 300 ms après le guidage (scénario 17). */
class VoiceArbiterTest {
    private fun item(text: String, p: SpeechPriority, at: Long, ch: VoiceChannel) = SpeechQueue.Item(text, p, at, ch)

    /** Moteur simulé comme `AndroidSpeechService` : un énoncé à la fois, fin et relance aux instants choisis par le test. */
    private class Engine(val q: SpeechQueue = SpeechQueue()) {
        var active: SpeechQueue.Item? = null
        /** (instant de début, texte) de chaque énoncé confié au moteur. */
        val said = mutableListOf<Pair<Long, String>>()
        val cut = mutableListOf<String>()

        fun offer(text: String, p: SpeechPriority, ch: VoiceChannel, at: Long, flush: Boolean = false) {
            val current = active
            if (q.offer(SpeechQueue.Item(text, p, at, ch), flush, current)) { current?.let { cut += it.text }; active = null }
            if (active == null) next(at)
        }
        fun finish(at: Long) { active?.let { q.onFinished(it.channel, at) }; active = null; next(at) }
        /** Relance programmée par le coordinateur (fin du délai de 300 ms). */
        fun tick(at: Long) { if (active == null) next(at) }
        private fun next(at: Long) { q.poll(at)?.let { active = it; said += at to it.text } }
    }

    @Test fun `arbitration table - urgent cuts all, guidance pre-empts the assistant, the assistant never pre-empts`() {
        val both = setOf(GUIDANCE, ASSISTANT)
        assertEquals(Arbitration(true, false, both), VoiceArbiter.arbitrate(GUIDANCE, URGENT, false, ASSISTANT))
        assertEquals(Arbitration(true, false, both), VoiceArbiter.arbitrate(GUIDANCE, URGENT, false, null))
        assertEquals(Arbitration(true, true, emptySet()), VoiceArbiter.arbitrate(GUIDANCE, NORMAL, false, ASSISTANT))
        assertEquals("FAR / vigilance aussi", Arbitration(true, true, emptySet()), VoiceArbiter.arbitrate(GUIDANCE, LOW, false, ASSISTANT))
        assertEquals(Arbitration(false, false, emptySet()), VoiceArbiter.arbitrate(GUIDANCE, NORMAL, false, GUIDANCE))
        assertEquals(Arbitration(true, false, setOf(GUIDANCE)), VoiceArbiter.arbitrate(GUIDANCE, NORMAL, true, GUIDANCE))
        assertEquals(Arbitration(true, true, setOf(GUIDANCE)), VoiceArbiter.arbitrate(GUIDANCE, NORMAL, true, ASSISTANT))
        assertEquals(Arbitration(false, false, emptySet()), VoiceArbiter.arbitrate(ASSISTANT, LOW, false, GUIDANCE))
        assertEquals(Arbitration(false, false, setOf(ASSISTANT)), VoiceArbiter.arbitrate(ASSISTANT, LOW, true, GUIDANCE))
        assertEquals(Arbitration(true, false, setOf(ASSISTANT)), VoiceArbiter.arbitrate(ASSISTANT, LOW, true, ASSISTANT))
    }

    @Test fun `scenario 17 - a now cuts the assistant and drops its queue, the near follows, the assistant speaks again 300 ms later`() {
        val e = Engine()
        e.offer("Le parking le plus proche est à 400 mètres.", LOW, ASSISTANT, at = 0)
        e.offer("Il reste 3 places.", LOW, ASSISTANT, at = 100)
        e.offer("Tournez à droite.", URGENT, GUIDANCE, at = 1_000)
        assertEquals("coupé, pas mis en pause", listOf("Le parking le plus proche est à 400 mètres."), e.cut)
        e.offer("Dans 150 mètres, tournez à gauche.", NORMAL, GUIDANCE, at = 1_200)
        e.finish(at = 2_000)
        assertEquals("Dans 150 mètres, tournez à gauche.", e.active!!.text)
        e.offer("Voulez-vous que je vous y guide ?", LOW, ASSISTANT, at = 2_500)
        e.finish(at = 4_000)
        assertNull("l'assistant attend 300 ms après le guidage", e.active)
        assertEquals(4_300L, e.q.readyAtMs(4_000))
        e.tick(at = 4_300)
        assertEquals("Voulez-vous que je vous y guide ?", e.active!!.text)
        assertEquals(
            "« Il reste 3 places. » jeté avec la file de l'assistant",
            listOf(0L to "Le parking le plus proche est à 400 mètres.", 1_000L to "Tournez à droite.", 2_000L to "Dans 150 mètres, tournez à gauche.", 4_300L to "Voulez-vous que je vous y guide ?"),
            e.said,
        )
    }

    @Test fun `a near cuts the assistant, which is said again in full 300 ms after the guidance`() {
        val e = Engine()
        e.offer("Il y a une station à 2 kilomètres.", LOW, ASSISTANT, at = 0)
        e.offer("Dans 300 mètres, tournez à droite.", NORMAL, GUIDANCE, at = 500)
        assertEquals(listOf("Il y a une station à 2 kilomètres."), e.cut)
        assertEquals("Dans 300 mètres, tournez à droite.", e.active!!.text)
        e.finish(at = 2_500)
        assertNull(e.active)
        e.tick(at = 2_799)
        assertNull("pas avant 300 ms", e.active)
        e.tick(at = 2_800)
        assertEquals("redit en entier", "Il y a une station à 2 kilomètres.", e.active!!.text)
    }

    @Test fun `the assistant never cuts the guidance, even with flush, and waits for its end`() {
        val e = Engine()
        e.offer("Dans 2 kilomètres, prenez la sortie 19.", LOW, GUIDANCE, at = 0)
        e.offer("Essai.", LOW, ASSISTANT, at = 100, flush = true)
        assertTrue(e.cut.isEmpty())
        assertEquals("Dans 2 kilomètres, prenez la sortie 19.", e.active!!.text)
        e.finish(at = 3_000)
        e.tick(at = 3_300)
        assertEquals("Essai.", e.active!!.text)
    }

    @Test fun `an assistant flush replaces only the assistant message`() {
        val e = Engine()
        e.offer("Première réponse.", LOW, ASSISTANT, at = 0)
        e.offer("Réponse corrigée.", LOW, ASSISTANT, at = 100, flush = true)
        assertEquals(listOf("Première réponse."), e.cut)
        assertEquals("Réponse corrigée.", e.active!!.text)
    }

    @Test fun `an assistant message waiting more than 8 s is dropped`() {
        val q = SpeechQueue()
        val g = item("Guidage", NORMAL, 0, GUIDANCE)
        q.offer(g)
        assertEquals(g, q.poll(0))
        q.offer(item("Assistant", LOW, 0, ASSISTANT), active = g)
        q.onFinished(GUIDANCE, 9_000)
        assertNull(q.poll(9_300))
        assertNull(q.readyAtMs(9_300))
        assertTrue(q.isEmpty)
    }

    @Test fun `clearing the guidance keeps the assistant messages, clearing all drops both`() {
        val q = SpeechQueue()
        q.offer(item("g", NORMAL, 0, GUIDANCE)); q.offer(item("a", LOW, 0, ASSISTANT))
        q.clear(GUIDANCE)
        assertEquals("a", q.poll(100)!!.text)
        q.offer(item("g", NORMAL, 0, GUIDANCE)); q.offer(item("a", LOW, 0, ASSISTANT))
        q.clear()
        assertTrue(q.isEmpty)
    }

    @Test fun `assistant work covers the message in progress and the queue - the engine release waits for it`() {
        val q = SpeechQueue()
        val a = item("a", LOW, 0, ASSISTANT)
        val g = item("g", NORMAL, 0, GUIDANCE)
        assertFalse(q.hasAssistantWork(null))
        assertFalse(q.hasAssistantWork(g))
        assertTrue("message en cours", q.hasAssistantWork(a))
        q.offer(a, active = g)
        assertTrue("message en file", q.hasAssistantWork(g))
    }
}
```

- [ ] **Étape 2 : vérifier l'échec.**

Commande : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*VoiceArbiterTest*"`
Résultat attendu : ÉCHEC de compilation, `Unresolved reference: VoiceChannel`, `Arbitration`, `VoiceArbiter`, puis `No parameter with name 'active'`.

- [ ] **Étape 3 : écrire l'implémentation.**

`corenav/SpeechService.kt`, ancien (lignes 3-4) :

```kotlin
/** Priorité d'une annonce (B5) : URGENT (« maintenant ») vide la file ; LOW / NORMAL périment après 8 s d'attente. */
enum class SpeechPriority { URGENT, NORMAL, LOW }
```

Nouveau :

```kotlin
/** Priorité d'une annonce (B5) : URGENT (« maintenant ») vide la file ; LOW / NORMAL périment après 8 s d'attente. */
enum class SpeechPriority { URGENT, NORMAL, LOW }

/**
 * Canal d'une annonce (E1) : le guidage (session) ou l'assistant (Roadly AI, essai de voix des Réglages). Un seul moteur de parole :
 * urgence GPS > guidage > assistant ; l'arrêt et la sourdine de la session ne touchent que [GUIDANCE].
 */
enum class VoiceChannel { GUIDANCE, ASSISTANT }
```

`corenav/VoiceArbiter.kt` :

```kotlin
package fr.roadly.core.navigation

/** Effet de l'arrivée d'une annonce sur l'énoncé en cours et sur les files (E1). */
internal data class Arbitration(
    /** L'énoncé en cours est coupé. */
    val interrupt: Boolean,
    /** L'énoncé coupé (de l'assistant) est remis en tête de sa file, pour être redit en entier après le guidage. */
    val requeueInterrupted: Boolean,
    /** Files vidées avant l'ajout. */
    val cleared: Set<VoiceChannel>,
)

/**
 * Arbitrage des canaux de parole (E1, pur, testé sur JVM) : urgence GPS > guidage > assistant.
 * - URGENT du guidage (NOW, arrivée) : coupe l'énoncé en cours, quel qu'il soit, et vide les deux files ; la phrase de l'assistant est coupée, pas mise en pause.
 * - Autre annonce du guidage : coupe un message de l'assistant, redit en entier après le guidage ; `flush` vide en plus la file du guidage.
 * - Assistant : ne coupe jamais le guidage ; `flush` ne vide que sa file et ne coupe qu'un message de l'assistant.
 * LIMITE : `TextToSpeech` ne reprend pas au milieu d'une phrase ; un message préempté est redit depuis le début.
 */
internal object VoiceArbiter {
    /** Silence (ms) entre la fin du dernier énoncé du guidage et la reprise de l'assistant (`RESUME_AFTER_GUIDANCE_MS` de Roadly AI). */
    const val ASSISTANT_RESUME_GAP_MS = 300L

    /** Fin de course pendant un message de l'assistant : la libération du moteur attend qu'il se taise, au plus 10 s. */
    const val RELEASE_DEFER_MAX_MS = 10_000L

    fun arbitrate(channel: VoiceChannel, priority: SpeechPriority, flush: Boolean, active: VoiceChannel?): Arbitration = when (channel) {
        VoiceChannel.GUIDANCE -> when {
            priority == SpeechPriority.URGENT -> Arbitration(interrupt = true, requeueInterrupted = false, cleared = setOf(VoiceChannel.GUIDANCE, VoiceChannel.ASSISTANT))
            flush -> Arbitration(interrupt = true, requeueInterrupted = active == VoiceChannel.ASSISTANT, cleared = setOf(VoiceChannel.GUIDANCE))
            else -> (active == VoiceChannel.ASSISTANT).let { overAssistant -> Arbitration(interrupt = overAssistant, requeueInterrupted = overAssistant, cleared = emptySet()) }
        }
        VoiceChannel.ASSISTANT -> Arbitration(
            interrupt = flush && active == VoiceChannel.ASSISTANT,
            requeueInterrupted = false,
            cleared = if (flush) setOf(VoiceChannel.ASSISTANT) else emptySet(),
        )
    }
}
```

`corenav/SpeechQueue.kt` (remplace tout le fichier) :

```kotlin
package fr.roadly.core.navigation

/**
 * File d'annonces du moteur de synthèse (pur, testé sur JVM), un canal par source (E1). Les décisions viennent de [VoiceArbiter] :
 * - le guidage passe d'abord ;
 * - l'assistant ne part que [resumeGapMs] après la fin du dernier énoncé du guidage ;
 * - un URGENT du guidage vide les deux files.
 * Péremption après [maxAgeMs] : LOW / NORMAL du guidage, et tout message de l'assistant.
 */
internal class SpeechQueue(
    private val maxAgeMs: Long = GuidanceConstants.SPEECH_STALE_AFTER_MS,
    private val resumeGapMs: Long = VoiceArbiter.ASSISTANT_RESUME_GAP_MS,
) {
    data class Item(val text: String, val priority: SpeechPriority, val queuedAtMs: Long, val channel: VoiceChannel = VoiceChannel.GUIDANCE)

    private val guidance = ArrayDeque<Item>()
    private val assistant = ArrayDeque<Item>()
    /** Fin du dernier énoncé du guidage : l'assistant repart [resumeGapMs] après. */
    private var guidanceEndedAtMs: Long? = null

    val isEmpty: Boolean get() = guidance.isEmpty() && assistant.isEmpty()

    /**
     * Ajoute [item] alors que [active] est en cours (null si rien). Rend vrai s'il faut couper l'énoncé en cours : URGENT, `flush`, ou guidage
     * sur un message de l'assistant. Les files désignées par l'arbitre sont vidées avant l'ajout, et un message de l'assistant coupé est remis
     * en tête de sa file (âge compté depuis la coupure).
     */
    fun offer(item: Item, flush: Boolean = false, active: Item? = null): Boolean {
        val a = VoiceArbiter.arbitrate(item.channel, item.priority, flush, active?.channel)
        if (VoiceChannel.GUIDANCE in a.cleared) guidance.clear()
        if (VoiceChannel.ASSISTANT in a.cleared) assistant.clear()
        if (a.interrupt && a.requeueInterrupted && active != null) assistant.addFirst(active.copy(queuedAtMs = item.queuedAtMs))
        queueOf(item.channel).addLast(item)
        return a.interrupt
    }

    /** Prochaine annonce à dire à [nowMs] : guidage d'abord ; assistant seulement après le délai de reprise. Les annonces périmées sont jetées. */
    fun poll(nowMs: Long): Item? {
        dropStale(guidance, nowMs)
        guidance.removeFirstOrNull()?.let { return it }
        dropStale(assistant, nowMs)
        if (assistant.isEmpty() || nowMs < assistantReadyAt()) return null
        return assistant.removeFirst()
    }

    /** Instant où le message de l'assistant en tête de file pourra partir (≥ [nowMs]) ; null si rien n'attend ou si du guidage attend. */
    fun readyAtMs(nowMs: Long): Long? {
        if (guidance.isNotEmpty()) return null
        dropStale(assistant, nowMs)
        if (assistant.isEmpty()) return null
        return maxOf(nowMs, assistantReadyAt())
    }

    /** Fin d'un énoncé (terminé ou coupé par un arrêt) : celle du guidage ouvre le délai de reprise de l'assistant. */
    fun onFinished(channel: VoiceChannel, nowMs: Long) {
        if (channel == VoiceChannel.GUIDANCE) guidanceEndedAtMs = nowMs
    }

    /** Un message de l'assistant est en cours ([active]) ou en file : la libération du moteur l'attend (E1). */
    fun hasAssistantWork(active: Item?): Boolean = active?.channel == VoiceChannel.ASSISTANT || assistant.isNotEmpty()

    /** Vide la file de [channel] ; null = les deux. */
    fun clear(channel: VoiceChannel? = null) {
        if (channel != VoiceChannel.ASSISTANT) guidance.clear()
        if (channel != VoiceChannel.GUIDANCE) assistant.clear()
    }

    private fun queueOf(channel: VoiceChannel) = if (channel == VoiceChannel.GUIDANCE) guidance else assistant

    private fun assistantReadyAt(): Long = guidanceEndedAtMs?.let { it + resumeGapMs } ?: Long.MIN_VALUE

    private fun isStale(item: Item, nowMs: Long): Boolean =
        (item.channel == VoiceChannel.ASSISTANT || item.priority != SpeechPriority.URGENT) && nowMs - item.queuedAtMs > maxAgeMs

    private fun dropStale(queue: ArrayDeque<Item>, nowMs: Long) {
        while (true) {
            val head = queue.firstOrNull() ?: return
            if (!isStale(head, nowMs)) return
            queue.removeFirst()
        }
    }
}
```

- [ ] **Étape 4 : vérifier le succès.**

Commande : `.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q`
Résultat attendu : SUCCÈS. Les 8 tests de `VoiceArbiterTest` et les 4 de `SpeechQueueTest` sont verts, sans changement, ainsi que les tests existants de `core:navigation`. `AndroidSpeechService` compile encore : `offer(item, flush)`, `poll(now)` et `clear()` gardent leur forme.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/SpeechService.kt core/navigation/src/main/java/fr/roadly/core/navigation/VoiceArbiter.kt core/navigation/src/main/java/fr/roadly/core/navigation/SpeechQueue.kt core/navigation/src/test/java/fr/roadly/core/navigation/VoiceArbiterTest.kt
git commit -m @'
feat(voix): arbitrage pur des canaux guidage / assistant — l'urgence coupe tout, le guidage passe avant l'assistant, reprise 300 ms après (E1)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 2 : E1 — `SpeechService` à canaux, coordinateur `AndroidSpeechService`, `FakeSpeechService`

**Fichiers :**
- Modifier `corenav/SpeechService.kt`, lignes 1-2 (import) et lignes 13-34 (interface, après la tâche 1).
- Remplacer `corenav/AndroidSpeechService.kt` (fichier entier, 225 lignes).
- Remplacer `corenav/testing/FakeSpeechService.kt` (fichier entier, 30 lignes).
- Pas de test JVM nouveau : `TextToSpeech` et `AudioManager` ne tournent pas sur JVM (écart 1). La logique est couverte par la tâche 1, et le double de test est exercé par la tâche 3.

**Interfaces :**
- Consomme (tâche 1) : `VoiceChannel`, `SpeechQueue.offer(item, flush, active)`, `poll`, `readyAtMs`, `onFinished`, `hasAssistantWork`, `clear(channel)`, `VoiceArbiter.RELEASE_DEFER_MAX_MS`.
- Produit (`SpeechService`) :
  - `val speaking: StateFlow<Boolean>` ;
  - `fun speak(text: String, flush: Boolean = false, priority: SpeechPriority = SpeechPriority.NORMAL, channel: VoiceChannel = VoiceChannel.GUIDANCE)`, avec un corps par défaut qui ignore le canal ;
  - `fun setMuted(muted: Boolean, channel: VoiceChannel)`, par défaut `setMuted(muted)` ;
  - `fun stop(channel: VoiceChannel?)`, par défaut `stop()` ;
  - inchangés : `speak(text, flush)`, `speak(text, flush, priority)`, `setVolume`, `beep`, `setMuted(muted)`, `stop()`, `release()`.
- Produit (`FakeSpeechService`) :
  - `speaking` et `setSpeaking(value)` ;
  - `spoken`, `flushes`, `priorities` et `channels: MutableList<VoiceChannel>` ;
  - `stops` (tous canaux) et `stoppedChannels: MutableList<VoiceChannel?>` ;
  - `mutedChannels: Set<VoiceChannel>` ; `muted` (= GUIDANCE en sourdine) ;
  - `volume`, `beeps`, `releases`.

- [ ] **Étape 1 : interface.**

`corenav/SpeechService.kt`. Ajouter après `package fr.roadly.core.navigation` (ligne 1) :

```kotlin

import kotlinx.coroutines.flow.StateFlow
```

Remplacer l'interface (de `/** Synthèse vocale du guidage ; aucune voix serveur (spec §2). */` jusqu'à la fin du fichier) par :

```kotlin
/** Synthèse vocale du guidage ; aucune voix serveur (spec §2). */
interface SpeechService {
    /**
     * Vrai tant qu'une annonce est en cours ou en file, tous canaux confondus (focus audio tenu) : observé par l'assistant pour lui céder la
     * parole. Même nom et même sens que sur `feat/roadly-ai`.
     */
    val speaking: StateFlow<Boolean>
    /** [flush] = interrompt ce qui est en cours ; sinon mise en file. */
    fun speak(text: String, flush: Boolean = false)
    /**
     * Variante priorisée (B5). Par défaut : URGENT interrompt (flush), sinon mise en file. Une implémentation qui fait déléguer
     * `speak(text, flush)` à cette variante doit la redéfinir (sinon les deux s'appellent mutuellement).
     */
    fun speak(text: String, flush: Boolean = false, priority: SpeechPriority) = speak(text, flush || priority == SpeechPriority.URGENT)
    /**
     * E1 : variante par canal. [flush] sur [VoiceChannel.ASSISTANT] ne vide que la file de l'assistant et ne coupe jamais le guidage.
     * Par défaut le canal est ignoré (variante priorisée) ; une implémentation qui y fait déléguer les précédentes doit la redéfinir.
     */
    fun speak(text: String, flush: Boolean = false, priority: SpeechPriority = SpeechPriority.NORMAL, channel: VoiceChannel = VoiceChannel.GUIDANCE) =
        speak(text, flush, priority)
    /** 0–100, appliqué via `TextToSpeech.Engine.KEY_PARAM_VOLUME`. */
    fun setVolume(percent: Int)
    /** Son court d'alerte de dépassement. */
    fun beep()
    /**
     * Sourdine de tous les canaux : à `true`, coupe la parole en cours et vide les files ([stop]) ; tant qu'elle est active,
     * [speak] ne fait rien (ni file, ni focus audio).
     */
    fun setMuted(muted: Boolean)
    /** E1 : sourdine d'un seul canal (la session : [VoiceChannel.GUIDANCE]) ; à `true`, coupe et vide ce canal seulement. Par défaut : tous. */
    fun setMuted(muted: Boolean, channel: VoiceChannel) = setMuted(muted)
    /** Coupe la parole en cours et vide les files, tous canaux (volume à zéro, sourdine globale). */
    fun stop()
    /** E1 : coupe et vide un seul canal ([channel] null = tous) ; un message de l'autre canal n'est ni coupé ni effacé. Par défaut : [stop]. */
    fun stop(channel: VoiceChannel?) = stop()
    /**
     * Libère le moteur de synthèse (et le focus audio) ; il sera recréé au prochain [speak]. E1 : différée tant qu'un message de l'assistant
     * est en cours ou en file (au plus [VoiceArbiter.RELEASE_DEFER_MAX_MS]) ; le guidage, lui, se tait aussitôt.
     */
    fun release()
}
```

- [ ] **Étape 2 : coordinateur.**

`corenav/AndroidSpeechService.kt` (remplace tout le fichier) :

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
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinateur vocal (E1, `NavigationVoiceCoordinator` de la spécification). Un seul moteur `TextToSpeech` (`Locale.FRANCE`) pour deux canaux :
 * - le guidage ;
 * - l'assistant.
 *
 * Les règles sont dans [SpeechQueue] / [VoiceArbiter] (testées sur JVM) : urgence GPS > guidage > assistant, assistant repris 300 ms après le
 * guidage, péremption à 8 s.
 *
 * Audio :
 * - focus transitoire avec atténuation des médias (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`) ;
 * - le focus est rendu sur le fil principal quand plus rien n'est en cours ni en file, quel que soit le canal.
 *
 * Moteur :
 * - une seule annonce confiée au moteur à la fois ;
 * - tous les `tts.speak` passent par le fil principal, dans l'ordre de réservation, et un démarrage d'une époque périmée est jeté ;
 * - délai de garde par énoncé ([GuidanceConstants.SPEECH_UTTERANCE_TIMEOUT_MS]) ;
 * - [release] est différée tant que l'assistant parle.
 *
 * Traces `RoadlySpeech` (« Dit », « Coupé », « Fini ») : vérification sur appareil.
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
    /** Annonces en attente, par canal (moteur pas prêt, occupé, ou assistant dans son délai de reprise). */
    private val queue = SpeechQueue()
    /** Énoncé confié au moteur (un seul) et son annonce ; le focus audio est tenu tant qu'il existe ou que la file n'est pas vide. */
    private var activeId: String? = null
    private var activeItem: SpeechQueue.Item? = null
    /** Délais de garde armés, par énoncé (sous [lock]) ; annulés au rappel de fin réel ou à la coupure. */
    private val timeouts = mutableMapOf<String, Runnable>()
    /** Incrémenté à chaque coupure sous [lock] : un démarrage réservé à une époque antérieure est périmé. */
    private var interruptEpoch = 0L
    /** Canaux en sourdine (sous [lock]) : la sourdine de la session ne touche que le guidage. */
    private val mutedChannels = mutableSetOf<VoiceChannel>()
    /** Libération demandée pendant un message de l'assistant (sous [lock]) : faite quand il se tait, au plus tard après 10 s. */
    private var releasePending = false
    private val ids = AtomicLong(0)
    private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var unavailable = false
    @Volatile private var volume = 1f
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    /** Relance programmée : fin du délai de reprise avant un message de l'assistant. */
    private val drainTask = Runnable { drain() }
    /** Libération différée forcée : l'assistant n'a pas fini en [VoiceArbiter.RELEASE_DEFER_MAX_MS]. */
    private val forcedRelease = Runnable { releaseNow() }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    override fun speak(text: String, flush: Boolean) = speak(text, flush, SpeechPriority.NORMAL, VoiceChannel.GUIDANCE)

    override fun speak(text: String, flush: Boolean, priority: SpeechPriority) = speak(text, flush, priority, VoiceChannel.GUIDANCE)

    override fun speak(text: String, flush: Boolean, priority: SpeechPriority, channel: VoiceChannel) {
        if (unavailable || isSilenced(channel) || text.isBlank()) return
        val tts = ensureEngine() ?: return
        var cut = false
        val start = synchronized(lock) {
            // Le guidage reprend la parole : une libération différée (fin de course pendant un message de l'assistant) est annulée.
            if (channel == VoiceChannel.GUIDANCE && releasePending) { releasePending = false; main.removeCallbacks(forcedRelease) }
            val now = SystemClock.elapsedRealtime()
            cut = queue.offer(SpeechQueue.Item(text, priority, now, channel), flush, activeItem)
            if (cut) {
                interruptEpoch++
                activeItem?.let { Log.d(TAG, "Coupé : ${it.channel} ${it.priority}") }
                dropActive()
            }
            refreshSpeaking()
            // Moteur pas prêt : onInit videra la file ; moteur occupé sans coupure : finished() prendra la suite.
            if (!ready || activeId != null) null else nextStart(now, flush = cut)
        }
        when {
            start != null -> say(tts, start)
            // Coupure sans relève immédiate (assistant dans son délai de reprise) : l'énoncé coupé s'arrête quand même.
            cut -> tts.stop()
        }
    }

    override fun setVolume(percent: Int) {
        volume = percent.coerceIn(0, 100) / 100f
        if (volume == 0f) stop()
    }

    override fun setMuted(muted: Boolean) {
        synchronized(lock) { if (muted) mutedChannels += VoiceChannel.entries else mutedChannels.clear() }
        if (muted) stop()
    }

    override fun setMuted(muted: Boolean, channel: VoiceChannel) {
        synchronized(lock) { if (muted) mutedChannels += channel else mutedChannels -= channel }
        if (muted) stop(channel)
    }

    /** Volume à zéro ou [channel] en sourdine : rien n'est mis en file ni prononcé sur ce canal, et le focus n'est pas demandé. */
    private fun isSilenced(channel: VoiceChannel): Boolean = volume == 0f || synchronized(lock) { channel in mutedChannels }

    override fun beep() {
        if (isSilenced(VoiceChannel.GUIDANCE)) return
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
            main.postDelayed({ tone.release() }, 400)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Bip indisponible", e)
        }
    }

    override fun stop() = stop(null)

    override fun stop(channel: VoiceChannel?) {
        val tts = synchronized(lock) {
            queue.clear(channel)
            val current = activeItem
            val cut = current != null && (channel == null || current.channel == channel)
            if (cut) {
                queue.onFinished(current!!.channel, SystemClock.elapsedRealtime())
                interruptEpoch++
                dropActive()
            }
            if (channel == null) main.removeCallbacks(drainTask)
            refreshSpeaking()
            engine.takeIf { cut }
        }
        tts?.stop()
        // Un message de l'autre canal attendait : il repart (relance) ; sinon le focus est rendu, sur le fil principal.
        main.post { drain() }
    }

    override fun release() {
        val defer = synchronized(lock) { queue.hasAssistantWork(activeItem).also { releasePending = it } }
        if (!defer) { releaseNow(); return }
        // Fin de course pendant un message de l'assistant : le guidage se tait, le message finit, le moteur est libéré ensuite.
        stop(VoiceChannel.GUIDANCE)
        main.removeCallbacks(forcedRelease)
        main.postDelayed(forcedRelease, VoiceArbiter.RELEASE_DEFER_MAX_MS)
    }

    private fun releaseNow() {
        main.removeCallbacks(forcedRelease)
        stop()
        val tts = synchronized(lock) {
            releasePending = false
            ready = false
            unavailable = false
            engine.also { engine = null }
        }
        tts?.shutdown()
    }

    /** Crée le moteur au premier usage ; `null` si l'initialisation a déjà échoué de façon synchrone. */
    private fun ensureEngine(): TextToSpeech? = synchronized(lock) {
        engine ?: TextToSpeech(ctx) { status -> onInit(status) }.let {
            if (unavailable) {
                it.shutdown()
                null
            } else {
                it.setAudioAttributes(attributes)
                it.setOnUtteranceProgressListener(listener)
                engine = it
                it
            }
        }
    }

    private fun onInit(status: Int) {
        // Certains moteurs rappellent onInit(ERROR) de façon synchrone dans le constructeur (engine encore null).
        val tts = synchronized(lock) { engine }
        val lang = if (status == TextToSpeech.SUCCESS && tts != null) tts.setLanguage(Locale.FRANCE) else TextToSpeech.LANG_NOT_SUPPORTED
        if (tts == null || lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Voix française indisponible (statut $status, langue $lang)")
            unavailable = true
            synchronized(lock) {
                queue.clear()
                engine = null
                refreshSpeaking()
            }
            tts?.shutdown()
            return
        }
        // Annonces mises en file avant que le moteur soit prêt : la première encore valable part, les suivantes via finished().
        val first = synchronized(lock) { ready = true; if (activeId == null) nextStart(SystemClock.elapsedRealtime(), flush = false) else null }
        first?.let { say(tts, it) }
    }

    private class Start(val text: String, val flush: Boolean, val id: String, val epoch: Long, val channel: VoiceChannel, val priority: SpeechPriority)

    /**
     * Sous [lock] : réserve la prochaine annonce valable (guidage d'abord) dans [activeId] / [activeItem] ; null si rien à dire maintenant.
     * Un message de l'assistant dans son délai de reprise programme une relance ([drainTask]).
     */
    private fun nextStart(nowMs: Long, flush: Boolean): Start? {
        main.removeCallbacks(drainTask)
        val item = queue.poll(nowMs)
        if (item == null) {
            queue.readyAtMs(nowMs)?.let { at -> main.postDelayed(drainTask, (at - nowMs).coerceAtLeast(0L)) }
            refreshSpeaking()
            return null
        }
        val id = "roadly-${ids.incrementAndGet()}"
        activeId = id
        activeItem = item
        refreshSpeaking()
        return Start(item.text, flush, id, interruptEpoch, item.channel, item.priority)
    }

    /** Sous [lock] : oublie l'énoncé en cours (coupé) ; ses rappels tardifs seront des identifiants inconnus, ignorés. */
    private fun dropActive() {
        activeId?.let(::cancelTimeout)
        activeId = null
        activeItem = null
    }

    /** Sous [lock]. */
    private fun refreshSpeaking() {
        _speaking.value = activeId != null || !queue.isEmpty
    }

    /** Confie [s] au moteur sur le fil principal : les `tts.speak` partent dans l'ordre de réservation, un URGENT ne peut être doublé. */
    private fun say(tts: TextToSpeech, s: Start) {
        main.post { sayOnMain(tts, s) }
    }

    private fun sayOnMain(tts: TextToSpeech, s: Start) {
        val current = synchronized(lock) {
            // Époque périmée (une coupure est passée depuis la réservation) ou énoncé déjà purgé (stop) : rien à dire.
            (s.epoch == interruptEpoch && s.id == activeId).also { if (it) armTimeout(s) }
        }
        if (!current || isSilenced(s.channel)) { finished(s.id); return }
        // Redemandé à chaque énoncé (sans effet s'il est déjà tenu) : le démarrage qui l'aurait demandé a pu être jeté comme périmé.
        audio.requestAudioFocus(focusRequest)
        Log.d(TAG, "Dit : ${s.channel} ${s.priority}")
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        if (tts.speak(s.text, if (s.flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, s.id) != TextToSpeech.SUCCESS) finished(s.id)
    }

    /** Délai de garde de [s] : 3 s + 100 ms par caractère, puis l'énoncé est réputé fini (appelé sous [lock]). */
    private fun armTimeout(s: Start) {
        val r = Runnable { finished(s.id) }
        timeouts[s.id] = r
        main.postDelayed(r, GuidanceConstants.SPEECH_UTTERANCE_TIMEOUT_MS + s.text.length * GuidanceConstants.SPEECH_MS_PER_CHAR)
    }

    /** Appelé sous [lock]. */
    private fun cancelTimeout(id: String) {
        timeouts.remove(id)?.let { main.removeCallbacks(it) }
    }

    /**
     * Fin d'un énoncé (terminé, en erreur, interrompu ou délai de garde écoulé) : enchaîne sur la prochaine annonce encore valable, sinon
     * [settle]. Idempotent : un identifiant inconnu (déjà fini, purgé par [stop] ou retiré par une coupure) est ignoré.
     */
    private fun finished(utteranceId: String?) {
        val (tts, next) = synchronized(lock) {
            if (utteranceId == null) return
            cancelTimeout(utteranceId)
            if (utteranceId != activeId) return
            val now = SystemClock.elapsedRealtime()
            activeItem?.let { queue.onFinished(it.channel, now); Log.d(TAG, "Fini : ${it.channel}") }
            activeId = null
            activeItem = null
            engine to (if (ready) nextStart(now, flush = false) else null)
        }
        if (tts != null && next != null) say(tts, next) else settle()
    }

    /** Relance programmée ou après un arrêt, sur le fil principal : la prochaine annonce part, sinon [settle]. */
    private fun drain() {
        val (tts, next) = synchronized(lock) {
            if (!ready || activeId != null) return
            engine to nextStart(SystemClock.elapsedRealtime(), flush = false)
        }
        if (tts != null && next != null) say(tts, next) else settle()
    }

    /**
     * Sur le fil principal (défaut relevé à l'étape C : le focus était rendu depuis le fil du moteur) :
     * - plus rien en cours ni en file → focus rendu, la musique revient ;
     * - une libération différée attend et l'assistant s'est tu → elle est faite.
     */
    private fun settle() {
        main.post {
            val (idle, release) = synchronized(lock) {
                (activeId == null && queue.isEmpty) to (releasePending && !queue.hasAssistantWork(activeItem))
            }
            if (idle) audio.abandonAudioFocusRequest(focusRequest)
            if (release) releaseNow()
        }
    }

    private companion object { const val TAG = "RoadlySpeech" }
}
```

- [ ] **Étape 3 : double de test.**

`corenav/testing/FakeSpeechService.kt` (remplace tout le fichier) :

```kotlin
package fr.roadly.core.navigation.testing

import fr.roadly.core.navigation.SpeechPriority
import fr.roadly.core.navigation.SpeechService
import fr.roadly.core.navigation.VoiceChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Double de test de [SpeechService] : enregistre les annonces (texte, flush, priorité, canal), le volume, les bips, les arrêts et les sourdines. */
class FakeSpeechService : SpeechService {
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking
    /** Simule le début / la fin d'une annonce GPS (même nom que sur `feat/roadly-ai`). */
    fun setSpeaking(value: Boolean) { _speaking.value = value }

    val spoken = mutableListOf<String>()
    /** `flush` de chaque appel à [speak], dans l'ordre. */
    val flushes = mutableListOf<Boolean>()
    /** Priorité de chaque annonce, dans l'ordre de [spoken]. */
    val priorities = mutableListOf<SpeechPriority>()
    /** E1 : canal de chaque annonce, dans l'ordre de [spoken]. */
    val channels = mutableListOf<VoiceChannel>()
    var volume = 100
        private set
    var beeps = 0
        private set
    /** Appels à `stop`, tous canaux confondus. */
    var stops = 0
        private set
    /** E1 : canal de chaque `stop` (null = tous), dans l'ordre. */
    val stoppedChannels = mutableListOf<VoiceChannel?>()
    private val _mutedChannels = mutableSetOf<VoiceChannel>()
    /** E1 : canaux en sourdine. */
    val mutedChannels: Set<VoiceChannel> get() = _mutedChannels.toSet()
    /** Sourdine du guidage. */
    val muted: Boolean get() = VoiceChannel.GUIDANCE in _mutedChannels
    var releases = 0
        private set

    override fun speak(text: String, flush: Boolean) = speak(text, flush, SpeechPriority.NORMAL, VoiceChannel.GUIDANCE)
    override fun speak(text: String, flush: Boolean, priority: SpeechPriority) = speak(text, flush, priority, VoiceChannel.GUIDANCE)
    override fun speak(text: String, flush: Boolean, priority: SpeechPriority, channel: VoiceChannel) {
        spoken += text; flushes += flush; priorities += priority; channels += channel
    }
    override fun setVolume(percent: Int) { volume = percent.coerceIn(0, 100) }
    override fun beep() { beeps++ }
    override fun setMuted(muted: Boolean) { if (muted) _mutedChannels += VoiceChannel.entries else _mutedChannels.clear() }
    override fun setMuted(muted: Boolean, channel: VoiceChannel) { if (muted) _mutedChannels += channel else _mutedChannels -= channel }
    override fun stop() = stop(null)
    override fun stop(channel: VoiceChannel?) { stops++; stoppedChannels += channel; _speaking.value = false }
    override fun release() { releases++ }
}
```

- [ ] **Étape 4 : compiler et vérifier la non-régression.**

```powershell
.\gradlew.bat :core:navigation:compileDebugKotlin --offline -q
.\gradlew.bat :core:navigation:testDebugUnitTest :feature:navigation:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:home:testDebugUnitTest :feature:route:testDebugUnitTest --offline -q
.\gradlew.bat :feature:navigation:compileDebugAndroidTestKotlin :app:assembleDebug --offline -q
```
Résultat attendu : SUCCÈS, avec tous les tests existants verts. En particulier, `muting during the run…` reste vert : `stops` compte les arrêts, et `muted` est vrai après `setMuted(true)`, puisque la session appelle encore les variantes « tous canaux ».

- [ ] **Étape 5 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/SpeechService.kt core/navigation/src/main/java/fr/roadly/core/navigation/AndroidSpeechService.kt core/navigation/src/main/java/fr/roadly/core/navigation/testing/FakeSpeechService.kt
git commit -m @'
feat(voix): coordinateur à deux canaux — speaking, reprise de l'assistant 300 ms après le guidage, focus rendu sur le fil principal, libération différée (E1)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 3 : E1 — la session sur le canal GUIDANCE ; « Tester la voix » sur le canal ASSISTANT (scénario 16)

**Fichiers :**
- Modifier `corenav/NavigationSession.kt` :
  - ligne 139 (`setMuted`) ;
  - lignes 168-170 (`cancelRun`) ;
  - ligne 232 (collecteur de sourdine) ;
  - ligne 454 (`say`).
- Modifier `set/SettingsViewModel.kt` : ligne 27 (imports) et lignes 129-135 (`testVoice`).
- Test : `corenavtest/NavigationSessionVoiceTest.kt` (nouveau) ; `settest/SettingsViewModelTest.kt` (un test ajouté avant la ligne 172).

**Interfaces :**
- Consomme (tâche 2) :
  - `speech.speak(text, priority =, channel =)`, `speech.stop(VoiceChannel.GUIDANCE)`, `speech.setMuted(m, VoiceChannel.GUIDANCE)` ;
  - côté double de test : `FakeSpeechService.channels`, `stoppedChannels`, `mutedChannels`, `priorities`, `flushes`.
- Produit : aucun nouveau nom. Comportement : la session ne touche plus jamais au canal ASSISTANT.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`corenavtest/NavigationSessionVoiceTest.kt` :

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
import fr.roadly.core.navigation.testing.TestRoutes
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E1 : la session parle, se tait et se met en sourdine sur le canal du guidage seulement (scénarios 16 et 17). Pas d'`advanceUntilIdle()`. */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSessionVoiceTest {
    private val location = FakeLocationRepository()
    private val prefs = FakeUserPreferencesRepository()
    private val speech = FakeSpeechService()
    private val route = TestRoutes.straight()
    private val tracker = RouteTracker(route)
    private val option = RouteOption(route, "Le plus rapide", 0L, null, sameAsFastest = false)
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    }

    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(location, routing, NoopSpeedLimitProvider(), speech, prefs, FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope)
    }

    private fun fixAt(along: Double) = LocationFix(tracker.pointAt(along), 90f, 10f, 5f, 0L)

    @Test fun `announcements, stop and mute of the session use the guidance channel only`() = runTest {
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 70.0)); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 62.0)); runCurrent()
        s.setMuted(true); runCurrent()
        s.setMuted(false); runCurrent()
        s.stop(); runCurrent()
        assertEquals(listOf("Conduisez vers l'est.", "Dans 70 mètres, tournez à droite dans Rue d'Essai."), speech.spoken)
        assertEquals(listOf(VoiceChannel.GUIDANCE, VoiceChannel.GUIDANCE), speech.channels)
        assertTrue(speech.stoppedChannels.isNotEmpty())
        assertTrue("ni arrêt de l'assistant, ni arrêt global : ${speech.stoppedChannels}", speech.stoppedChannels.all { it == VoiceChannel.GUIDANCE })
        assertFalse(VoiceChannel.ASSISTANT in speech.mutedChannels)
    }

    @Test fun `scenario 16 - voice off silences the guidance channel, never the assistant`() = runTest {
        prefs.setVoiceEnabled(false)
        val s = session()
        s.start(option, RoutePreferences()); runCurrent()
        location.emit(fixAt(tracker.maneuverStartM[1] - 70.0)); runCurrent()
        assertTrue(speech.spoken.isEmpty())
        assertEquals(setOf(VoiceChannel.GUIDANCE), speech.mutedChannels)
    }

    @Test fun `a run gives back the guidance mute and leaves the assistant mute as it was`() = runTest {
        speech.setMuted(true, VoiceChannel.ASSISTANT)
        val s = session()
        s.setMuted(true)
        s.start(option, RoutePreferences()); runCurrent()
        assertEquals(setOf(VoiceChannel.ASSISTANT, VoiceChannel.GUIDANCE), speech.mutedChannels)
        s.stop(); runCurrent()
        assertEquals("sourdine de l'assistant intacte, celle du guidage rendue avec la course", setOf(VoiceChannel.ASSISTANT), speech.mutedChannels)
    }
}
```

`settest/SettingsViewModelTest.kt`. Ajouter les imports `import fr.roadly.core.navigation.SpeechPriority` et `import fr.roadly.core.navigation.VoiceChannel` (après la ligne 21). Insérer avant l'accolade finale (ligne 172) :

```kotlin

    @Test fun `the voice test is spoken on the assistant channel at low priority, so the guidance can pre-empt it`() = runTest {
        val vm = vm()
        vm.testVoice("Essai de voix."); advanceUntilIdle()
        assertEquals(listOf(VoiceChannel.ASSISTANT), speech.channels)
        assertEquals(listOf(SpeechPriority.LOW), speech.priorities)
        assertEquals("flush : remplace un essai précédent, jamais le guidage", listOf(true), speech.flushes)
    }
```

- [ ] **Étape 2 : vérifier l'échec.**

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*NavigationSessionVoiceTest*"
.\gradlew.bat :feature:settings:testDebugUnitTest --offline -q --tests "*SettingsViewModelTest*"
```
Résultat attendu : 3 ÉCHECS pour la session :
- `stoppedChannels` contient `null` ;
- `mutedChannels` vaut `{GUIDANCE, ASSISTANT}` ;
- la sourdine de l'assistant est perdue.

1 ÉCHEC pour les Réglages : `channels` vaut `[GUIDANCE]`.

- [ ] **Étape 3 : écrire l'implémentation.**

`corenav/NavigationSession.kt`, ligne 139. Ancien :

```kotlin
        if (muted) speech.stop()
```
Nouveau :
```kotlin
        // E1 : seul le guidage se tait ; un message de l'assistant en cours ou en file n'est ni coupé ni effacé.
        if (muted) speech.stop(VoiceChannel.GUIDANCE)
```

Lignes 168-170. Ancien :
```kotlin
        speech.stop()
        // La sourdine appartient à la course : le service de synthèse (singleton) est rendu tel quel.
        speech.setMuted(false)
```
Nouveau :
```kotlin
        speech.stop(VoiceChannel.GUIDANCE)
        // La sourdine appartient à la course : le canal du guidage est rendu tel quel, celui de l'assistant n'est pas touché (E1).
        speech.setMuted(false, VoiceChannel.GUIDANCE)
```

Ligne 232. Ancien :
```kotlin
                    .collect { speech.setMuted(it) }
```
Nouveau :
```kotlin
                    .collect { speech.setMuted(it, VoiceChannel.GUIDANCE) }
```

Ligne 454. Ancien :
```kotlin
            speech.speak(text, priority = priority)
```
Nouveau :
```kotlin
            speech.speak(text, priority = priority, channel = VoiceChannel.GUIDANCE)
```

`set/SettingsViewModel.kt`. Après la ligne 27 (`import fr.roadly.core.navigation.SpeechService`), ajouter :
```kotlin
import fr.roadly.core.navigation.SpeechPriority
import fr.roadly.core.navigation.VoiceChannel
```
Lignes 129-135. Ancien :
```kotlin
    /** Prononce [phrase] au volume enregistré, même si la voix du guidage est désactivée (test explicite). */
    fun testVoice(phrase: String) {
        viewModelScope.launch {
            speech.setVolume(prefs.voiceVolume.first())
            speech.speak(phrase, flush = true)
        }
    }
```
Nouveau :
```kotlin
    /**
     * Prononce [phrase] au volume enregistré, même si la voix du guidage est désactivée (test explicite). E1 : sur le canal de l'assistant,
     * en priorité basse — la sourdine du guidage ne la coupe pas, une annonce du guidage la préempte (essai de l'arbitrage sur appareil).
     */
    fun testVoice(phrase: String) {
        viewModelScope.launch {
            speech.setVolume(prefs.voiceVolume.first())
            speech.speak(phrase, flush = true, priority = SpeechPriority.LOW, channel = VoiceChannel.ASSISTANT)
        }
    }
```

- [ ] **Étape 4 : vérifier le succès.**

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:navigation:testDebugUnitTest :feature:home:testDebugUnitTest :feature:route:testDebugUnitTest --offline -q
```
Résultat attendu : SUCCÈS. Les 4 tests nouveaux sont verts, et `NavigationSessionTest` reste vert sans changement : `stops`, `muted` et `releases` gardent leur sens.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/NavigationSession.kt core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionVoiceTest.kt feature/settings/src/main/java/fr/roadly/feature/settings/SettingsViewModel.kt feature/settings/src/test/java/fr/roadly/feature/settings/SettingsViewModelTest.kt
git commit -m @'
feat(voix): la session ne coupe et ne met en sourdine que le guidage ; « Tester la voix » passe par le canal de l'assistant (E1, scénario 16)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 4 : E2 — types et persistance : `CameraMode3D` dans `core:model`, `PitchPreset`, `PowerSaveMode`, six réglages

**Fichiers :**
- Créer `model/CameraMode3D.kt`, `model/PitchPreset.kt`, `model/PowerSaveMode.kt`.
- Modifier `map/NavigationCameraController.kt` : lignes 1-18 (retirer l'enum, ajouter l'import).
- Modifier les imports (sans autre changement) :
  - `maptest/NavigationCameraControllerTest.kt`, après la ligne 3 ;
  - `nav/NavigationScreen.kt`, ligne 99 ;
  - `nav/NavigationViewModel.kt`, ligne 14 ;
  - `navtest/NavigationViewModelTest.kt`, ligne 13.
- Modifier `data/repository/UserPreferencesRepository.kt` :
  - lignes 11-13 (imports) ;
  - lignes 32-35 (`GuidanceDefaults`) ;
  - lignes 72-77 (interface) ;
  - après la ligne 103 (clés) ;
  - lignes 142-145 (implémentation).
- Modifier `data/testing/FakeUserPreferencesRepository.kt` : lignes 3-10 et 48-53.
- Tests :
  - `modeltest/NavigationDisplayTest.kt` (nouveau) ;
  - `datatest/repository/DataStoreUserPreferencesRepositoryTest.kt` (2 tests, avant la ligne 86 ; imports après la ligne 10).

**Interfaces :**
- Produit (dans `fr.roadly.core.model`) :
  - `enum class CameraMode3D { FOLLOW_3D, FOLLOW_2D, NORTH_UP; fun toggled3d(); companion { fun default(map3dEnabled: Boolean); fun initial(view: CameraMode3D, autoRotate: Boolean) } }` ;
  - `enum class PitchPreset(val degrees: Double) { LOW(45.0), STANDARD(58.0), HIGH(60.0) }` ;
  - `enum class PowerSaveMode { AUTO, ON, OFF; fun effective(systemSaver: Boolean): Boolean }`.
- Produit (`GuidanceDefaults`) : `DEFAULT_CAMERA_MODE`, `ZOOM_OFFSET = 0`, `ALLOWED_ZOOM_OFFSETS = listOf(-1, 0, 1)`, `PITCH_PRESET`, `AUTO_ROTATE = true`, `SHOW_NEXT_MANEUVER = true`, `POWER_SAVE`.
- Produit (`UserPreferencesRepository`) :
  - `defaultCameraMode: Flow<CameraMode3D>` / `setDefaultCameraMode` ;
  - `zoomOffset: Flow<Int>` / `setZoomOffset` ;
  - `pitchPreset: Flow<PitchPreset>` / `setPitchPreset` ;
  - `autoRotate: Flow<Boolean>` / `setAutoRotate` ;
  - `showNextManeuverPreview: Flow<Boolean>` / `setShowNextManeuverPreview` ;
  - `powerSaveMode: Flow<PowerSaveMode>` / `setPowerSaveMode`.
- Produit (`FakeUserPreferencesRepository`) : `cameraDefault: MutableStateFlow<CameraMode3D?>`, `zoom`, `pitch`, `rotate`, `thenPreview`, `powerSave`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`modeltest/NavigationDisplayTest.kt` :

```kotlin
package fr.roadly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E2 : types des réglages d'affichage du guidage. */
class NavigationDisplayTest {
    @Test fun `the session starts on the chosen view, or north up without automatic rotation`() {
        assertEquals(CameraMode3D.FOLLOW_3D, CameraMode3D.initial(CameraMode3D.FOLLOW_3D, autoRotate = true))
        assertEquals(CameraMode3D.FOLLOW_2D, CameraMode3D.initial(CameraMode3D.FOLLOW_2D, autoRotate = true))
        assertEquals(CameraMode3D.NORTH_UP, CameraMode3D.initial(CameraMode3D.FOLLOW_3D, autoRotate = false))
        assertEquals("ancien réglage « Carte 3D » : migration de la vue au départ", CameraMode3D.FOLLOW_2D, CameraMode3D.default(map3dEnabled = false))
    }

    @Test fun `power save follows the Android saver in auto and is forced otherwise`() {
        assertTrue(PowerSaveMode.AUTO.effective(systemSaver = true))
        assertFalse(PowerSaveMode.AUTO.effective(systemSaver = false))
        assertTrue(PowerSaveMode.ON.effective(systemSaver = false))
        assertFalse(PowerSaveMode.OFF.effective(systemSaver = true))
    }

    @Test fun `pitch presets are 45, 58 and 60 degrees`() {
        assertEquals(listOf(45.0, 58.0, 60.0), PitchPreset.entries.map { it.degrees })
    }
}
```

`datatest/repository/DataStoreUserPreferencesRepositoryTest.kt`. Ajouter après la ligne 10 :
```kotlin
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import androidx.datastore.preferences.core.intPreferencesKey
```
Puis, avant l'accolade finale (ligne 86) :
```kotlin

    @Test fun `navigation display settings default, persist and ignore invalid values`() = runTest {
        assertEquals(CameraMode3D.FOLLOW_3D, repo.defaultCameraMode.first())
        assertEquals(0, repo.zoomOffset.first())
        assertEquals(PitchPreset.STANDARD, repo.pitchPreset.first())
        assertEquals(true, repo.autoRotate.first())
        assertEquals(true, repo.showNextManeuverPreview.first())
        assertEquals(PowerSaveMode.AUTO, repo.powerSaveMode.first())
        repo.setDefaultCameraMode(CameraMode3D.FOLLOW_2D); repo.setZoomOffset(-1); repo.setPitchPreset(PitchPreset.HIGH)
        repo.setAutoRotate(false); repo.setShowNextManeuverPreview(false); repo.setPowerSaveMode(PowerSaveMode.ON)
        assertEquals(CameraMode3D.FOLLOW_2D, repo.defaultCameraMode.first())
        assertEquals(-1, repo.zoomOffset.first())
        assertEquals(PitchPreset.HIGH, repo.pitchPreset.first())
        assertEquals(false, repo.autoRotate.first())
        assertEquals(false, repo.showNextManeuverPreview.first())
        assertEquals(PowerSaveMode.ON, repo.powerSaveMode.first())
        repo.setZoomOffset(2)
        assertEquals("hors de −1 / 0 / +1 : ignoré", -1, repo.zoomOffset.first())
        store.edit {
            it[stringPreferencesKey("map.pitch")] = "VERTICAL"; it[stringPreferencesKey("power.save.mode")] = "TURBO"
            it[stringPreferencesKey("map.camera.default")] = "ORBIT"; it[intPreferencesKey("map.zoom.offset")] = 5
        }
        assertEquals(PitchPreset.STANDARD, repo.pitchPreset.first())
        assertEquals(PowerSaveMode.AUTO, repo.powerSaveMode.first())
        assertEquals(CameraMode3D.FOLLOW_3D, repo.defaultCameraMode.first())
        assertEquals(0, repo.zoomOffset.first())
    }

    @Test fun `without a chosen start view, the former 3d map setting decides it`() = runTest {
        repo.setMap3dEnabled(false)
        assertEquals(CameraMode3D.FOLLOW_2D, repo.defaultCameraMode.first())
        repo.setDefaultCameraMode(CameraMode3D.FOLLOW_3D)
        assertEquals("choix explicite : indépendant des bâtiments", CameraMode3D.FOLLOW_3D, repo.defaultCameraMode.first())
        assertEquals(false, repo.map3dEnabled.first())
    }
```

- [ ] **Étape 2 : vérifier l'échec.**

```powershell
.\gradlew.bat :core:model:test --offline -q --tests "*NavigationDisplayTest*"
.\gradlew.bat :core:data:testDebugUnitTest --offline -q --tests "*DataStoreUserPreferencesRepositoryTest*"
```
Résultat attendu : ÉCHEC de compilation, `Unresolved reference: CameraMode3D` / `PitchPreset` / `PowerSaveMode` / `defaultCameraMode`.

- [ ] **Étape 3 : types du modèle.**

`model/CameraMode3D.kt` :

```kotlin
package fr.roadly.core.model

/**
 * C1 : mode de caméra du guidage, choisi pour la session (bouton 2D/3D, « nord en haut »). Départ : réglages E2 (vue au départ, rotation).
 * Dans `core:model` (E2) : persisté par `core:data`, qui ne dépend pas de `core:map`.
 */
enum class CameraMode3D {
    FOLLOW_3D, FOLLOW_2D, NORTH_UP;

    /** Bouton 2D/3D : 3D → 2D, sinon 3D (depuis « nord en haut », on repasse en 3D). */
    fun toggled3d(): CameraMode3D = if (this == FOLLOW_3D) FOLLOW_2D else FOLLOW_3D

    companion object {
        /** Ancien réglage « Carte 3D en guidage » (clé `map.3d`) : activé → 3D, sinon 2D. Sert de vue au départ tant qu'elle n'a pas été choisie (migration E2). */
        fun default(map3dEnabled: Boolean): CameraMode3D = if (map3dEnabled) FOLLOW_3D else FOLLOW_2D

        /** E2 : mode de départ d'une session — la vue choisie [view], ou nord en haut si la rotation automatique est coupée. */
        fun initial(view: CameraMode3D, autoRotate: Boolean): CameraMode3D = if (autoRotate) view else NORTH_UP
    }
}
```

`model/PitchPreset.kt` :

```kotlin
package fr.roadly.core.model

/**
 * E2 : inclinaison de croisière en vue 3D, en degrés. L'approche reste à 50°, l'économie d'énergie à 45° ; « ville dense » (60°) seulement
 * avec STANDARD ou HIGH. LIMITE : 60° au plus, maximum rendu par MapLibre Android 11.13.5 (la spécification demandait 65°).
 */
enum class PitchPreset(val degrees: Double) { LOW(45.0), STANDARD(58.0), HIGH(60.0) }
```

`model/PowerSaveMode.kt` :

```kotlin
package fr.roadly.core.model

/** E2 : économie d'énergie du guidage (sans bâtiments, inclinaison 45°, 30 images/s) — AUTO suit l'économiseur d'Android, ON / OFF la forcent. */
enum class PowerSaveMode {
    AUTO, ON, OFF;

    /** Économie effective, selon l'économiseur d'Android ([systemSaver]). */
    fun effective(systemSaver: Boolean): Boolean = when (this) {
        AUTO -> systemSaver
        ON -> true
        OFF -> false
    }
}
```

- [ ] **Étape 4 : déplacer `CameraMode3D` hors de `core:map`.**

`map/NavigationCameraController.kt`, lignes 1-18. Ancien (de `package fr.roadly.core.map` à l'accolade fermante de `enum class CameraMode3D`) :
```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import kotlin.math.exp

/** C1 : mode de caméra du guidage, choisi pour la session (bouton 2D/3D, « nord en haut ») ; départ = réglage « Carte 3D ». */
enum class CameraMode3D {
    FOLLOW_3D, FOLLOW_2D, NORTH_UP;

    /** Bouton 2D/3D : 3D → 2D, sinon 3D (depuis « nord en haut », on repasse en 3D). */
    fun toggled3d(): CameraMode3D = if (this == FOLLOW_3D) FOLLOW_2D else FOLLOW_3D

    companion object {
        /** Mode de départ d'une session : réglage « Carte 3D en guidage » activé → 3D, sinon 2D. */
        fun default(map3dEnabled: Boolean): CameraMode3D = if (map3dEnabled) FOLLOW_3D else FOLLOW_2D
    }
}
```
Nouveau :
```kotlin
package fr.roadly.core.map

import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import kotlin.math.exp
```

Imports, sans autre changement :
- `maptest/NavigationCameraControllerTest.kt` : ajouter `import fr.roadly.core.model.CameraMode3D` après la ligne 3 ;
- `nav/NavigationScreen.kt`, ligne 99 : `import fr.roadly.core.map.CameraMode3D` → `import fr.roadly.core.model.CameraMode3D` ;
- `nav/NavigationViewModel.kt`, ligne 14 : même remplacement ;
- `navtest/NavigationViewModelTest.kt`, ligne 13 : même remplacement.

- [ ] **Étape 5 : persistance.**

`data/repository/UserPreferencesRepository.kt`, lignes 11-13. Ancien :
```kotlin
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.RoutePreferences
```
Nouveau :
```kotlin
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import fr.roadly.core.model.RoutePreferences
```

Lignes 32-35. Ancien :
```kotlin
    /** Carte inclinée + bâtiments 3D pendant le guidage. */
    const val MAP_3D = true
    /** Niveau de détail de la carte en guidage. */
    val MAP_DETAIL = MapDetail.NORMAL
```
Nouveau :
```kotlin
    /** E2 « Bâtiments 3D » (clé `map.3d`, ex-« Carte 3D en guidage ») : bâtiments extrudés en vue 3D. */
    const val MAP_3D = true
    /** Niveau de détail de la carte en guidage. */
    val MAP_DETAIL = MapDetail.NORMAL
    /** E2 : vue au départ du guidage ; sans choix enregistré, elle suit [MAP_3D] (migration). */
    val DEFAULT_CAMERA_MODE = CameraMode3D.FOLLOW_3D
    /** E2 : décalage de zoom du guidage, et valeurs admises (plus large, normal, plus proche). */
    const val ZOOM_OFFSET = 0
    val ALLOWED_ZOOM_OFFSETS = listOf(-1, 0, 1)
    /** E2 : inclinaison de croisière en 3D. */
    val PITCH_PRESET = PitchPreset.STANDARD
    /** E2 : carte orientée selon le cap ; non = nord en haut au départ. */
    const val AUTO_ROTATE = true
    /** E2 : ligne « puis … » sous le bandeau (D1). */
    const val SHOW_NEXT_MANEUVER = true
    /** E2 : économie d'énergie du guidage. */
    val POWER_SAVE = PowerSaveMode.AUTO
```

Lignes 72-77 de l'interface. Ancien :
```kotlin
    /** Perspective 3D et bâtiments extrudés pendant le guidage. */
    val map3dEnabled: Flow<Boolean>
    suspend fun setMap3dEnabled(value: Boolean)
    /** C2 : niveau de détail de la carte en guidage (REDUCED : sans points d'intérêt ni noms des petites rues). */
    val mapDetail: Flow<MapDetail>
    suspend fun setMapDetail(value: MapDetail)
```
Nouveau :
```kotlin
    /** E2 « Bâtiments 3D » (ex-« Carte 3D en guidage ») : bâtiments extrudés en vue 3D. */
    val map3dEnabled: Flow<Boolean>
    suspend fun setMap3dEnabled(value: Boolean)
    /** C2 : niveau de détail de la carte en guidage (REDUCED : sans points d'intérêt ni noms des petites rues). */
    val mapDetail: Flow<MapDetail>
    suspend fun setMapDetail(value: MapDetail)
    /** E2 : vue au départ du guidage (3D / 2D) ; tant qu'elle n'a pas été choisie, elle suit [map3dEnabled] (migration de l'ancien réglage). */
    val defaultCameraMode: Flow<CameraMode3D>
    suspend fun setDefaultCameraMode(value: CameraMode3D)
    /** E2 : décalage de zoom du guidage ; seules −1, 0 et +1 sont acceptées, toute autre valeur est ignorée. */
    val zoomOffset: Flow<Int>
    suspend fun setZoomOffset(value: Int)
    /** E2 : inclinaison de croisière en 3D. */
    val pitchPreset: Flow<PitchPreset>
    suspend fun setPitchPreset(value: PitchPreset)
    /** E2 : rotation automatique (cap en haut) ; non = nord en haut au départ. */
    val autoRotate: Flow<Boolean>
    suspend fun setAutoRotate(value: Boolean)
    /** E2 : ligne « puis … » de la manœuvre d'après sous le bandeau. */
    val showNextManeuverPreview: Flow<Boolean>
    suspend fun setShowNextManeuverPreview(value: Boolean)
    /** E2 : économie d'énergie du guidage (AUTO = économiseur d'Android). */
    val powerSaveMode: Flow<PowerSaveMode>
    suspend fun setPowerSaveMode(value: PowerSaveMode)
```

Après la ligne 103 (`private val kTint = stringPreferencesKey("cockpit.tint")`), ajouter :
```kotlin
    private val kCameraDefault = stringPreferencesKey("map.camera.default")
    private val kZoomOffset = intPreferencesKey("map.zoom.offset")
    private val kPitch = stringPreferencesKey("map.pitch")
    private val kAutoRotate = booleanPreferencesKey("map.auto.rotate")
    private val kThenPreview = booleanPreferencesKey("guidance.then.preview")
    private val kPowerSave = stringPreferencesKey("power.save.mode")
```

Après la ligne 145 (`override suspend fun setMapDetail(…)`), ajouter :
```kotlin
    override val defaultCameraMode: Flow<CameraMode3D> = store.data.map { p ->
        p[kCameraDefault]?.let { runCatching { CameraMode3D.valueOf(it) }.getOrNull() } ?: CameraMode3D.default(p[kMap3d] ?: GuidanceDefaults.MAP_3D)
    }
    override suspend fun setDefaultCameraMode(value: CameraMode3D) { store.edit { it[kCameraDefault] = value.name } }
    override val zoomOffset: Flow<Int> = store.data.map { p -> p[kZoomOffset]?.takeIf { it in GuidanceDefaults.ALLOWED_ZOOM_OFFSETS } ?: GuidanceDefaults.ZOOM_OFFSET }
    override suspend fun setZoomOffset(value: Int) {
        if (value !in GuidanceDefaults.ALLOWED_ZOOM_OFFSETS) return
        store.edit { it[kZoomOffset] = value }
    }
    override val pitchPreset: Flow<PitchPreset> = store.data.map { p -> p[kPitch]?.let { runCatching { PitchPreset.valueOf(it) }.getOrNull() } ?: GuidanceDefaults.PITCH_PRESET }
    override suspend fun setPitchPreset(value: PitchPreset) { store.edit { it[kPitch] = value.name } }
    override val autoRotate: Flow<Boolean> = store.data.map { it[kAutoRotate] ?: GuidanceDefaults.AUTO_ROTATE }
    override suspend fun setAutoRotate(value: Boolean) { store.edit { it[kAutoRotate] = value } }
    override val showNextManeuverPreview: Flow<Boolean> = store.data.map { it[kThenPreview] ?: GuidanceDefaults.SHOW_NEXT_MANEUVER }
    override suspend fun setShowNextManeuverPreview(value: Boolean) { store.edit { it[kThenPreview] = value } }
    override val powerSaveMode: Flow<PowerSaveMode> = store.data.map { p -> p[kPowerSave]?.let { runCatching { PowerSaveMode.valueOf(it) }.getOrNull() } ?: GuidanceDefaults.POWER_SAVE }
    override suspend fun setPowerSaveMode(value: PowerSaveMode) { store.edit { it[kPowerSave] = value.name } }
```

`data/testing/FakeUserPreferencesRepository.kt`, lignes 7-10. Ancien :
```kotlin
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
```
Nouveau :
```kotlin
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import fr.roadly.core.model.RoutePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
```
Après la ligne 53 (`override suspend fun setMapDetail(…)`), ajouter :
```kotlin
    /** E2 : vue au départ choisie ; null = jamais choisie (elle suit alors [map3d], comme l'implémentation DataStore). */
    val cameraDefault = MutableStateFlow<CameraMode3D?>(null)
    override val defaultCameraMode: Flow<CameraMode3D> = combine(cameraDefault, map3d) { chosen, on -> chosen ?: CameraMode3D.default(on) }
    override suspend fun setDefaultCameraMode(value: CameraMode3D) { cameraDefault.value = value }
    val zoom = MutableStateFlow(GuidanceDefaults.ZOOM_OFFSET)
    override val zoomOffset: Flow<Int> = zoom
    override suspend fun setZoomOffset(value: Int) { if (value in GuidanceDefaults.ALLOWED_ZOOM_OFFSETS) zoom.value = value }
    val pitch = MutableStateFlow(GuidanceDefaults.PITCH_PRESET)
    override val pitchPreset: Flow<PitchPreset> = pitch
    override suspend fun setPitchPreset(value: PitchPreset) { pitch.value = value }
    val rotate = MutableStateFlow(GuidanceDefaults.AUTO_ROTATE)
    override val autoRotate: Flow<Boolean> = rotate
    override suspend fun setAutoRotate(value: Boolean) { rotate.value = value }
    val thenPreview = MutableStateFlow(GuidanceDefaults.SHOW_NEXT_MANEUVER)
    override val showNextManeuverPreview: Flow<Boolean> = thenPreview
    override suspend fun setShowNextManeuverPreview(value: Boolean) { thenPreview.value = value }
    val powerSave = MutableStateFlow(GuidanceDefaults.POWER_SAVE)
    override val powerSaveMode: Flow<PowerSaveMode> = powerSave
    override suspend fun setPowerSaveMode(value: PowerSaveMode) { powerSave.value = value }
```

- [ ] **Étape 6 : vérifier le succès.**

```powershell
.\gradlew.bat :core:model:test :core:data:testDebugUnitTest :core:map:testDebugUnitTest :feature:navigation:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:home:testDebugUnitTest :feature:route:testDebugUnitTest --offline -q
.\gradlew.bat :feature:navigation:compileDebugAndroidTestKotlin :app:assembleDebug --offline -q
```
Résultat attendu : SUCCÈS, avec 5 tests nouveaux (3 de modèle, 2 DataStore). `NavigationCameraControllerTest` (`the 2d button and the session default`) et `NavigationViewModelTest` (`with the 3d setting off the session starts in 2d`) restent verts sans changement d'assertion.

- [ ] **Étape 7 : commiter.**

```powershell
git add core/model/src/main/java/fr/roadly/core/model/CameraMode3D.kt core/model/src/main/java/fr/roadly/core/model/PitchPreset.kt core/model/src/main/java/fr/roadly/core/model/PowerSaveMode.kt core/model/src/test/java/fr/roadly/core/model/NavigationDisplayTest.kt core/data/src/main/java/fr/roadly/core/data/repository/UserPreferencesRepository.kt core/data/src/main/java/fr/roadly/core/data/testing/FakeUserPreferencesRepository.kt core/data/src/test/java/fr/roadly/core/data/repository/DataStoreUserPreferencesRepositoryTest.kt core/map/src/main/java/fr/roadly/core/map/NavigationCameraController.kt core/map/src/test/java/fr/roadly/core/map/NavigationCameraControllerTest.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt
git commit -m @'
feat(réglages): vue au départ, zoom, inclinaison, rotation, ligne « puis » et économie d'énergie persistés ; CameraMode3D dans core:model (E2)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 5 : E2 — caméra : décalage de zoom, inclinaison choisie, bâtiments 3D

**Fichiers :**
- Modifier `map/GuidanceCameraPolicy.kt` :
  - ligne 3 (import) ;
  - lignes 12-26 (`GuidanceCameraInput`) ;
  - ligne 33 (KDoc de `PITCH_3D`) ;
  - lignes 75-84 (`compute`).
- Modifier `map/NavigationCameraController.kt` :
  - `NavigationCameraInput`, après `topPanelFraction` ;
  - `update` (lignes 61-66 de `8907dd8`, avant la tâche 4).
- Tests : `maptest/GuidanceCameraPolicyTest.kt` (4 tests avant la dernière accolade, import) et `maptest/NavigationCameraControllerTest.kt` (1 test).

**Interfaces :**
- Consomme (tâche 4) : `PitchPreset`.
- Produit :
  - `GuidanceCameraInput(…, buildingsVisible, zoomOffset: Int = 0, pitchPreset: PitchPreset = PitchPreset.STANDARD)` ;
  - `NavigationCameraInput(…, topPanelFraction, zoomOffset: Int = 0, pitchPreset: PitchPreset = PitchPreset.STANDARD, buildings3d: Boolean = true)`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`maptest/GuidanceCameraPolicyTest.kt` : ajouter `import fr.roadly.core.model.PitchPreset` après la ligne 3. Insérer avant l'accolade finale :

```kotlin

    @Test fun `the pitch preset sets the cruise pitch, the approach stays at 50 and power save at 45`() {
        assertEquals(45.0, GuidanceCameraPolicy.compute(input().copy(pitchPreset = PitchPreset.LOW), null).pitch, 1e-9)
        assertEquals(60.0, GuidanceCameraPolicy.compute(input().copy(pitchPreset = PitchPreset.HIGH), null).pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_APPROACH, GuidanceCameraPolicy.compute(input(distance = 100.0).copy(pitchPreset = PitchPreset.HIGH), null).pitch, 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_POWER_SAVE, GuidanceCameraPolicy.compute(input(powerSave = true).copy(pitchPreset = PitchPreset.HIGH), null).pitch, 1e-9)
    }

    @Test fun `dense city tilt needs the standard or high preset`() {
        fun pitch(p: PitchPreset) = GuidanceCameraPolicy.compute(input(speed = 20.0).copy(buildingsVisible = true, pitchPreset = p), null).pitch
        assertEquals("inclinaison basse choisie : pas de 60° en ville", 45.0, pitch(PitchPreset.LOW), 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_DENSE_CITY, pitch(PitchPreset.STANDARD), 1e-9)
        assertEquals(GuidanceCameraPolicy.PITCH_DENSE_CITY, pitch(PitchPreset.HIGH), 1e-9)
    }

    @Test fun `the zoom offset shifts cruise and 2d zoom, widens the approach but never tightens it`() {
        assertEquals(18.0, GuidanceCameraPolicy.compute(input(speed = 50.0).copy(zoomOffset = 1), null).zoom, 1e-9)
        assertEquals(16.0, GuidanceCameraPolicy.compute(input(speed = 50.0).copy(zoomOffset = -1), null).zoom, 1e-9)
        assertEquals(18.0, GuidanceCameraPolicy.compute(input(threeD = false).copy(zoomOffset = 1), null).zoom, 1e-9)
        assertEquals("flèche entière à l'approche (3c02729)", 17.5, GuidanceCameraPolicy.compute(input(distance = 100.0).copy(zoomOffset = 1), null).zoom, 1e-9)
        assertEquals(16.5, GuidanceCameraPolicy.compute(input(distance = 100.0).copy(zoomOffset = -1), null).zoom, 1e-9)
    }

    @Test fun `pitch presets match the policy constants`() {
        assertEquals(GuidanceCameraPolicy.PITCH_POWER_SAVE, PitchPreset.LOW.degrees, 0.0)
        assertEquals(GuidanceCameraPolicy.PITCH_3D, PitchPreset.STANDARD.degrees, 0.0)
        assertEquals(GuidanceCameraPolicy.MAX_PITCH, PitchPreset.HIGH.degrees, 0.0)
    }
```

`maptest/NavigationCameraControllerTest.kt` : ajouter `import fr.roadly.core.model.PitchPreset`. Insérer avant l'accolade finale :

```kotlin

    @Test fun `the zoom offset, the pitch preset and the buildings setting reach the policy`() {
        val noBuildings = NavigationCameraController().update(input(speed = 20.0).copy(zoomOffset = -1, buildings3d = false)).camera
        assertEquals("bâtiments coupés : pas d'inclinaison « ville dense »", 58.0, noBuildings.pitch, 1e-9)
        assertEquals(16.5, noBuildings.zoom, 1e-9)
        assertEquals(60.0, NavigationCameraController().update(input(speed = 20.0)).camera.pitch, 1e-9)
        assertEquals(45.0, NavigationCameraController().update(input().copy(pitchPreset = PitchPreset.LOW)).camera.pitch, 1e-9)
    }
```

- [ ] **Étape 2 : vérifier l'échec.**

Commande : `.\gradlew.bat :core:map:testDebugUnitTest --offline -q`
Résultat attendu : ÉCHEC de compilation, `No parameter with name 'pitchPreset'` / `'zoomOffset'` / `'buildings3d'`.

- [ ] **Étape 3 : écrire l'implémentation.**

`map/GuidanceCameraPolicy.kt`. Après la ligne 3 (`import fr.roadly.core.model.ManeuverType`), ajouter `import fr.roadly.core.model.PitchPreset`.

Lignes 24-26. Ancien :
```kotlin
    /** Bâtiments 3D affichés (mode 3D hors économie d'énergie) : autorise l'inclinaison « ville dense ». */
    val buildingsVisible: Boolean = false,
)
```
Nouveau :
```kotlin
    /** Bâtiments 3D affichés (mode 3D hors économie d'énergie) : autorise l'inclinaison « ville dense ». */
    val buildingsVisible: Boolean = false,
    /** E2 : décalage de zoom choisi (−1, 0, +1) ; à l'approche, seul −1 s'applique. */
    val zoomOffset: Int = 0,
    /** E2 : inclinaison de croisière choisie. */
    val pitchPreset: PitchPreset = PitchPreset.STANDARD,
)
```

Ligne 33. Ancien :
```kotlin
    const val PITCH_3D = 58.0
```
Nouveau :
```kotlin
    /** Inclinaison de croisière par défaut (= `PitchPreset.STANDARD`) ; E2 : remplacée par le preset choisi. */
    const val PITCH_3D = 58.0
```

Lignes 75-84. Ancien :
```kotlin
        if (!input.threeD) return GuidanceCamera(ZOOM_2D, 0.0, bearing, CameraPadding.NONE)
        val approaching = input.approaching ?: isApproaching(input.distanceToManeuverM, input.maneuverType, input.speedKmh)
        val pitch = when {
            input.powerSave -> PITCH_POWER_SAVE
            approaching -> PITCH_APPROACH
            input.buildingsVisible && input.speedKmh != null && input.speedKmh < DENSE_CITY_MAX_KMH -> PITCH_DENSE_CITY
            else -> PITCH_3D
        }
        val target = if (approaching) ZOOM_APPROACH else zoomForSpeed(input.speedKmh)
```
Nouveau :
```kotlin
        if (!input.threeD) return GuidanceCamera(ZOOM_2D + input.zoomOffset, 0.0, bearing, CameraPadding.NONE)
        val approaching = input.approaching ?: isApproaching(input.distanceToManeuverM, input.maneuverType, input.speedKmh)
        val pitch = when {
            input.powerSave -> PITCH_POWER_SAVE
            approaching -> PITCH_APPROACH
            input.buildingsVisible && input.pitchPreset != PitchPreset.LOW && input.speedKmh != null && input.speedKmh < DENSE_CITY_MAX_KMH -> PITCH_DENSE_CITY
            else -> input.pitchPreset.degrees
        }
        // E2 : +1 ne resserre pas l'approche — ZOOM_APPROACH garde la flèche entière sous la pile du bandeau (3c02729).
        val target = if (approaching) ZOOM_APPROACH + minOf(input.zoomOffset, 0) else zoomForSpeed(input.speedKmh) + input.zoomOffset
```

`map/NavigationCameraController.kt`, dans `NavigationCameraInput`. Ancien :
```kotlin
    /** Bas de la pile du haut (bandeau, panneau de direction, voies) / hauteur de la vue (portrait) ; null = marges par défaut. */
    val topPanelFraction: Float? = null,
)
```
Nouveau :
```kotlin
    /** Bas de la pile du haut (bandeau, panneau de direction, voies) / hauteur de la vue (portrait) ; null = marges par défaut. */
    val topPanelFraction: Float? = null,
    /** E2 : décalage de zoom choisi. */
    val zoomOffset: Int = 0,
    /** E2 : inclinaison de croisière choisie. */
    val pitchPreset: PitchPreset = PitchPreset.STANDARD,
    /** E2 « Bâtiments 3D » : sans bâtiments, pas d'inclinaison « ville dense ». */
    val buildings3d: Boolean = true,
)
```
Ajouter `import fr.roadly.core.model.PitchPreset` aux imports.

Dans `update`. Ancien :
```kotlin
                maneuverType = input.maneuverType, threeD = threeD, powerSave = input.powerSave, landscape = input.landscape,
                approaching = input.approaching, buildingsVisible = threeD && !input.powerSave,
```
Nouveau :
```kotlin
                maneuverType = input.maneuverType, threeD = threeD, powerSave = input.powerSave, landscape = input.landscape,
                approaching = input.approaching, buildingsVisible = threeD && !input.powerSave && input.buildings3d,
                zoomOffset = input.zoomOffset, pitchPreset = input.pitchPreset,
```

- [ ] **Étape 4 : vérifier le succès.**

Commande : `.\gradlew.bat :core:map:testDebugUnitTest :feature:navigation:testDebugUnitTest --offline -q`
Résultat attendu : SUCCÈS, avec 5 tests nouveaux. Les tests existants (58°, 17,5 à l'approche, ville dense, plafond de 60°) sont verts sans changement.

- [ ] **Étape 5 : commiter.**

```powershell
git add core/map/src/main/java/fr/roadly/core/map/GuidanceCameraPolicy.kt core/map/src/main/java/fr/roadly/core/map/NavigationCameraController.kt core/map/src/test/java/fr/roadly/core/map/GuidanceCameraPolicyTest.kt core/map/src/test/java/fr/roadly/core/map/NavigationCameraControllerTest.kt
git commit -m @'
feat(caméra): décalage de zoom, inclinaison choisie et bâtiments 3D dans la politique de caméra (E2)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 6 : E2 — réglages appliqués au guidage (ViewModel et écran)

**Fichiers :**
- Modifier `nav/NavigationViewModel.kt` :
  - imports, lignes 14-21 : ajouter `PitchPreset`, `PowerSaveMode` ;
  - lignes 97-118 (mode de caméra) ;
  - après la ligne 130 (`mapDetail`).
- Modifier `nav/NavigationScreen.kt` :
  - imports (ajouter `androidx.compose.runtime.derivedStateOf` après la ligne 50, et `fr.roadly.core.model.PitchPreset` avec les imports `fr.roadly.core.model.*`) ;
  - lignes 152-157 ;
  - après la ligne 170 ;
  - ligne 183 ;
  - lignes 186-187 ;
  - `MapTick`, lignes 558-571 ;
  - `GuidanceMapEffects`, lignes 578-614.
- Modifier `nav/NavigationUiState.kt` : ajout en fin de fichier.
- Test : `navtest/NavigationViewModelTest.kt` (3 tests avant la ligne 363 ; imports après la ligne 21).

**Interfaces :**
- Consomme :
  - tâche 4 : `prefs.defaultCameraMode`, `autoRotate`, `zoomOffset`, `pitchPreset`, `showNextManeuverPreview`, `powerSaveMode`, `CameraMode3D.initial`, `PowerSaveMode.effective`, `GuidanceDefaults.*` ;
  - tâche 5 : `NavigationCameraInput(zoomOffset, pitchPreset, buildings3d)`.
- Produit :
  - `NavigationViewModel.zoomOffset: StateFlow<Int>`, `pitchPreset: StateFlow<PitchPreset>`, `showNextManeuverPreview: StateFlow<Boolean>`, `powerSaveMode: StateFlow<PowerSaveMode>` ;
  - `map3d` garde son nom (sens « Bâtiments 3D ») ;
  - `cameraMode` part de `CameraMode3D.initial(defaultCameraMode, autoRotate)` ;
  - `internal fun NavigationUiState.withNextManeuverPreview(show: Boolean): NavigationUiState`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`navtest/NavigationViewModelTest.kt`. Ajouter après la ligne 21 :
```kotlin
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
```
Insérer avant l'accolade finale de la classe (ligne 363) :

```kotlin

    @Test fun `the start view and north up come from the settings, the session choice still wins`() = runTest {
        prefs.setDefaultCameraMode(CameraMode3D.FOLLOW_2D)
        val vm = NavigationViewModel(session(), prefs, config); runCurrent()
        assertEquals(CameraMode3D.FOLLOW_2D, vm.cameraMode.value)
        prefs.setAutoRotate(false); runCurrent()
        assertEquals(CameraMode3D.NORTH_UP, vm.cameraMode.value)
        vm.toggleNorthUp(); runCurrent()
        assertEquals("nord en haut quitté : vue au départ", CameraMode3D.FOLLOW_2D, vm.cameraMode.value)
        prefs.setAutoRotate(true); prefs.setDefaultCameraMode(CameraMode3D.FOLLOW_3D); runCurrent()
        assertEquals("le choix de la session prime sur les réglages", CameraMode3D.FOLLOW_2D, vm.cameraMode.value)
    }

    @Test fun `zoom, pitch, the then row and power save settings are exposed for the screen`() = runTest {
        val vm = NavigationViewModel(session(), prefs, config); runCurrent()
        assertEquals(0, vm.zoomOffset.value)
        assertEquals(PitchPreset.STANDARD, vm.pitchPreset.value)
        assertTrue(vm.showNextManeuverPreview.value)
        assertEquals(PowerSaveMode.AUTO, vm.powerSaveMode.value)
        prefs.setZoomOffset(1); prefs.setPitchPreset(PitchPreset.LOW); prefs.setShowNextManeuverPreview(false); prefs.setPowerSaveMode(PowerSaveMode.ON); runCurrent()
        assertEquals(1, vm.zoomOffset.value)
        assertEquals(PitchPreset.LOW, vm.pitchPreset.value)
        assertFalse(vm.showNextManeuverPreview.value)
        assertEquals(PowerSaveMode.ON, vm.powerSaveMode.value)
    }

    @Test fun `without the next maneuver preview only the then row is dropped`() {
        val s = NavigationUiState(then = ThenUi(ManeuverType.TURN_RIGHT, "Rue Médiane"))
        assertNull(s.withNextManeuverPreview(false).then)
        assertEquals(s.copy(then = null), s.withNextManeuverPreview(false))
        assertEquals(s, s.withNextManeuverPreview(true))
    }
```

- [ ] **Étape 2 : vérifier l'échec.**

Commande : `.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q --tests "*NavigationViewModelTest*"`
Résultat attendu : ÉCHEC de compilation, `Unresolved reference: zoomOffset` / `withNextManeuverPreview`.

- [ ] **Étape 3 : ViewModel et état.**

`nav/NavigationViewModel.kt`. Après `import fr.roadly.core.model.MapDetail` (ligne 18), ajouter :
```kotlin
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
```

Lignes 97-118. Ancien : de `/** Réglage « Carte 3D en guidage » : mode de départ de la caméra (lu sans attendre par les boutons, d'où `Eagerly`). */` jusqu'à la fin de `fun toggleNorthUp() { … }`. Nouveau :
```kotlin
    /** E2 « Bâtiments 3D » (clé `map.3d`, ex-« Carte 3D en guidage ») : bâtiments extrudés en vue 3D ; lu sans attendre, d'où `Eagerly`. */
    val map3d: StateFlow<Boolean> = prefs.map3dEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.MAP_3D)

    /** E2 : vue au départ (3D / 2D ; suit `map.3d` tant qu'elle n'a pas été choisie) et rotation automatique (non = nord en haut). */
    private val startView: StateFlow<CameraMode3D> = prefs.defaultCameraMode.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.DEFAULT_CAMERA_MODE)
    private val autoRotate: StateFlow<Boolean> = prefs.autoRotate.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.AUTO_ROTATE)

    /** C1 : mode choisi par les boutons pour cette session ; null = mode des réglages. */
    private val cameraChoice = MutableStateFlow<CameraMode3D?>(null)
    /** Mode quitté en passant « nord en haut », rétabli au second appui ; null = vue au départ des réglages. */
    private var modeBeforeNorthUp: CameraMode3D? = null

    /** C1 / E2 : mode de caméra du guidage (3D, 2D, nord en haut), pour la session seulement ; départ = réglages. */
    val cameraMode: StateFlow<CameraMode3D> = combine(startView, autoRotate, cameraChoice) { view, rotate, choice -> choice ?: CameraMode3D.initial(view, rotate) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CameraMode3D.initial(GuidanceDefaults.DEFAULT_CAMERA_MODE, GuidanceDefaults.AUTO_ROTATE))

    private fun currentMode(): CameraMode3D = cameraChoice.value ?: CameraMode3D.initial(startView.value, autoRotate.value)

    /** Bouton 2D / 3D. */
    fun toggleCameraMode() { cameraChoice.value = currentMode().toggled3d() }

    /** Bouton « nord en haut » : bascule, puis retour au mode d'avant (session partie en nord en haut : la vue au départ). */
    fun toggleNorthUp() {
        val current = currentMode()
        cameraChoice.value = if (current == CameraMode3D.NORTH_UP) {
            modeBeforeNorthUp ?: startView.value.takeIf { it != CameraMode3D.NORTH_UP } ?: CameraMode3D.FOLLOW_3D
        } else {
            modeBeforeNorthUp = current
            CameraMode3D.NORTH_UP
        }
    }
```

Après la ligne 130 (`.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapDetail.NORMAL)` de `mapDetail`), ajouter :
```kotlin

    /** E2 : réglages lus par l'écran (caméra, ligne « puis », économie d'énergie), en `StateFlow` séparées : le `combine` de `uiState` n'est pas touché. */
    val zoomOffset: StateFlow<Int> = prefs.zoomOffset.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.ZOOM_OFFSET)
    val pitchPreset: StateFlow<PitchPreset> = prefs.pitchPreset.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.PITCH_PRESET)
    val showNextManeuverPreview: StateFlow<Boolean> = prefs.showNextManeuverPreview.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.SHOW_NEXT_MANEUVER)
    val powerSaveMode: StateFlow<PowerSaveMode> = prefs.powerSaveMode.stateIn(viewModelScope, SharingStarted.Eagerly, GuidanceDefaults.POWER_SAVE)
```

`nav/NavigationUiState.kt`, en fin de fichier :
```kotlin

/** E2 « Aperçu de la manœuvre suivante » coupé : la ligne « puis … » disparaît ; le reste de l'état (aperçu, trajet complet) est intact. */
internal fun NavigationUiState.withNextManeuverPreview(show: Boolean): NavigationUiState = if (show) this else copy(then = null)
```

- [ ] **Étape 4 : écran.**

`nav/NavigationScreen.kt`, lignes 152-157. Ancien :
```kotlin
    val cameraMode = vm.cameraMode.collectAsStateWithLifecycle()
    val powerSave = rememberPowerSaveMode()
    // Bâtiments 3D : en mode 3D seulement, et jamais en économie d'énergie.
    LaunchedEffect(cameraMode.value, powerSave.value, c.buildings3d) {
        map.setBuildings3d(cameraMode.value == CameraMode3D.FOLLOW_3D && !powerSave.value, c.buildings3d.toHex())
    }
```
Nouveau :
```kotlin
    val cameraMode = vm.cameraMode.collectAsStateWithLifecycle()
    // E2 : économie d'énergie du réglage — Auto suit l'économiseur d'Android, Activée / Désactivée le forcent.
    val systemSaver = rememberPowerSaveMode()
    val powerSaveSetting = vm.powerSaveMode.collectAsStateWithLifecycle()
    val powerSave = remember { derivedStateOf { powerSaveSetting.value.effective(systemSaver.value) } }
    // E2 « Bâtiments 3D » : en mode 3D seulement, et jamais en économie d'énergie.
    val buildings3d = vm.map3d.collectAsStateWithLifecycle()
    LaunchedEffect(cameraMode.value, powerSave.value, buildings3d.value, c.buildings3d) {
        map.setBuildings3d(buildings3d.value && cameraMode.value == CameraMode3D.FOLLOW_3D && !powerSave.value, c.buildings3d.toHex())
    }
```

Après la ligne 170 (`val topStack = vm.topStackFraction.collectAsStateWithLifecycle()`), ajouter :
```kotlin
    val zoomOffset = vm.zoomOffset.collectAsStateWithLifecycle()
    val pitchPreset = vm.pitchPreset.collectAsStateWithLifecycle()
```

Ligne 183. Ancien :
```kotlin
        GuidanceMapEffects(uiState, cameraMode, powerSave, landscape, map, camera, bottomPanel, topStack)
```
Nouveau :
```kotlin
        GuidanceMapEffects(uiState, cameraMode, powerSave, landscape, map, camera, bottomPanel, topStack, zoomOffset, pitchPreset, buildings3d)
```

Lignes 186-187. Ancien :
```kotlin
        val reportMessage by vm.reportMessage.collectAsStateWithLifecycle()
        val full = state.copy(communityAhead = reportsAhead)
```
Nouveau :
```kotlin
        val reportMessage by vm.reportMessage.collectAsStateWithLifecycle()
        val showThen by vm.showNextManeuverPreview.collectAsStateWithLifecycle()
        // E2 : la ligne « puis … » suit le réglage, pour les deux overlays (un seul point, `BannerStack` inchangé).
        val full = state.copy(communityAhead = reportsAhead).withNextManeuverPreview(showThen)
```

`MapTick`, lignes 568-571. Ancien :
```kotlin
    /** Part de l'écran couverte par la pile du haut (portrait, quantifiée), null avant la première mesure. */
    val topPanel: Float?,
)
```
Nouveau :
```kotlin
    /** Part de l'écran couverte par la pile du haut (portrait, quantifiée), null avant la première mesure. */
    val topPanel: Float?,
    /** E2 : décalage de zoom, inclinaison choisie, bâtiments 3D. */
    val zoomOffset: Int,
    val pitchPreset: PitchPreset,
    val buildings3d: Boolean,
)
```

`GuidanceMapEffects`, lignes 585-587. Ancien :
```kotlin
    bottomPanel: State<Float?>,
    topPanel: State<Float?>,
) {
```
Nouveau :
```kotlin
    bottomPanel: State<Float?>,
    topPanel: State<Float?>,
    zoomOffset: State<Int>,
    pitchPreset: State<PitchPreset>,
    buildings3d: State<Boolean>,
) {
```

Ligne 594. Ancien :
```kotlin
        snapshotFlow { ui.value.let { s -> MapTick(s.route, s.progress, s.speedKmh, s.approach != null, s.following, mode.value, powerSave.value, isLandscape.value, bottomPanel.value, topPanel.value) } }
```
Nouveau :
```kotlin
        snapshotFlow {
            ui.value.let { s ->
                MapTick(
                    s.route, s.progress, s.speedKmh, s.approach != null, s.following, mode.value, powerSave.value, isLandscape.value, bottomPanel.value, topPanel.value,
                    zoomOffset.value, pitchPreset.value, buildings3d.value,
                )
            }
        }
```

Ligne 613. Ancien :
```kotlin
                        bottomPanelFraction = t.bottomPanel, topPanelFraction = t.topPanel,
```
Nouveau :
```kotlin
                        bottomPanelFraction = t.bottomPanel, topPanelFraction = t.topPanel,
                        zoomOffset = t.zoomOffset, pitchPreset = t.pitchPreset, buildings3d = t.buildings3d,
```

- [ ] **Étape 5 : vérifier le succès.**

```powershell
.\gradlew.bat :feature:navigation:testDebugUnitTest --offline -q
.\gradlew.bat :feature:navigation:compileDebugAndroidTestKotlin :app:assembleDebug --offline -q
```
Résultat attendu : SUCCÈS, avec 3 tests nouveaux. Les tests existants sur le mode de caméra (lignes 300-324) sont verts sans changement.

- [ ] **Étape 6 : commiter.**

```powershell
git add feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationViewModel.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationUiState.kt feature/navigation/src/test/java/fr/roadly/feature/navigation/NavigationViewModelTest.kt
git commit -m @'
feat(guidage): réglages E2 appliqués — vue au départ, nord en haut, zoom et inclinaison, bâtiments, économie forcée, ligne « puis » (E2)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 7 : E2 — groupe « Navigation » dans Réglages

**Fichiers :**
- Créer `set/NavigationGroup.kt`.
- Modifier `set/SettingsViewModel.kt` :
  - imports (après la ligne 22) ;
  - après la ligne 68 (`SettingsUiState`) ;
  - après la ligne 94 (`mapDetail`) ;
  - ligne 140 (`setMap3d`).
- Modifier `set/SettingsScreen.kt` :
  - imports : retirer `Layers` (ligne 44), `ViewInAr` (ligne 58) et `MapDetail` (ligne 110) ;
  - lignes 146-148 ;
  - lignes 293-330 (`AppearanceGroup`) ;
  - lignes 629-635 (`MapDetail.label()`, déplacé).
- Modifier `feature/settings/src/main/res/values/strings.xml` : lignes 27-28 (textes) et après la ligne 32 (ajouts).
- Test : `settest/SettingsViewModelTest.kt` (2 tests avant la dernière accolade ; imports).

**Interfaces :**
- Consomme (tâche 4) : les six réglages, `GuidanceDefaults.ALLOWED_ZOOM_OFFSETS`, `CameraMode3D`, `PitchPreset`, `PowerSaveMode`.
- Produit :
  - `data class NavigationSettings(defaultCameraMode, zoomOffset, pitchPreset, autoRotate, mapDetail, buildings3d, showNextManeuverPreview, powerSaveMode)` ;
  - `SettingsViewModel.navigation: StateFlow<NavigationSettings>` ;
  - `setDefaultCameraMode`, `setZoomOffset`, `setPitchPreset`, `setAutoRotate`, `setShowNextManeuverPreview`, `setPowerSaveMode` ;
  - `setMap3d`, qui fige la vue au départ ;
  - `internal fun NavigationGroup(s: NavigationSettings, vm: SettingsViewModel)`.

- [ ] **Étape 1 : écrire les tests qui échouent.**

`settest/SettingsViewModelTest.kt`. Ajouter les imports :
```kotlin
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
```
Insérer avant l'accolade finale :
```kotlin

    @Test fun `the navigation group exposes and persists its settings`() = runTest {
        val vm = vm()
        vm.navigation.test {
            assertEquals(NavigationSettings(), awaitItem())
            vm.setDefaultCameraMode(CameraMode3D.FOLLOW_2D); vm.setZoomOffset(1); vm.setPitchPreset(PitchPreset.LOW); vm.setAutoRotate(false)
            vm.setShowNextManeuverPreview(false); vm.setPowerSaveMode(PowerSaveMode.OFF); vm.setMapDetail(MapDetail.REDUCED); advanceUntilIdle()
            assertEquals(
                NavigationSettings(
                    defaultCameraMode = CameraMode3D.FOLLOW_2D, zoomOffset = 1, pitchPreset = PitchPreset.LOW, autoRotate = false,
                    mapDetail = MapDetail.REDUCED, buildings3d = true, showNextManeuverPreview = false, powerSaveMode = PowerSaveMode.OFF,
                ),
                expectMostRecentItem(),
            )
            vm.setZoomOffset(3); advanceUntilIdle()
            assertEquals("hors de −1 / 0 / +1 : ignoré", 1, prefs.zoom.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `turning 3d buildings off keeps the start view`() = runTest {
        val vm = vm()
        vm.setMap3d(false); advanceUntilIdle()
        assertEquals(false, prefs.map3d.value)
        assertEquals("vue au départ jamais choisie : figée en 3D avant le changement", CameraMode3D.FOLLOW_3D, prefs.cameraDefault.value)
    }
```

- [ ] **Étape 2 : vérifier l'échec.**

Commande : `.\gradlew.bat :feature:settings:testDebugUnitTest --offline -q --tests "*SettingsViewModelTest*"`
Résultat attendu : ÉCHEC de compilation, `Unresolved reference: NavigationSettings` / `navigation`.

- [ ] **Étape 3 : ViewModel.**

`set/SettingsViewModel.kt`. Après `import fr.roadly.core.model.MapDetail` (ligne 22), ajouter :
```kotlin
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
```
Après `SettingsUiState` (ligne 68), ajouter :
```kotlin

/** E2 : groupe « Navigation » — vue au départ, zoom, inclinaison, rotation, détail, bâtiments 3D, ligne « puis », économie d'énergie. */
data class NavigationSettings(
    val defaultCameraMode: CameraMode3D = GuidanceDefaults.DEFAULT_CAMERA_MODE,
    val zoomOffset: Int = GuidanceDefaults.ZOOM_OFFSET,
    val pitchPreset: PitchPreset = GuidanceDefaults.PITCH_PRESET,
    val autoRotate: Boolean = GuidanceDefaults.AUTO_ROTATE,
    val mapDetail: MapDetail = GuidanceDefaults.MAP_DETAIL,
    val buildings3d: Boolean = GuidanceDefaults.MAP_3D,
    val showNextManeuverPreview: Boolean = GuidanceDefaults.SHOW_NEXT_MANEUVER,
    val powerSaveMode: PowerSaveMode = GuidanceDefaults.POWER_SAVE,
)
```
Après la ligne 94 (`val mapDetail: StateFlow<MapDetail> = …`), ajouter :
```kotlin
    /** E2 : réglages du groupe « Navigation », appliqués au prochain guidage. */
    val navigation: StateFlow<NavigationSettings> = combine(
        prefs.defaultCameraMode, prefs.zoomOffset, prefs.pitchPreset, prefs.autoRotate,
        prefs.mapDetail, prefs.map3dEnabled, prefs.showNextManeuverPreview, prefs.powerSaveMode,
    ) { v ->
        NavigationSettings(v[0] as CameraMode3D, v[1] as Int, v[2] as PitchPreset, v[3] as Boolean, v[4] as MapDetail, v[5] as Boolean, v[6] as Boolean, v[7] as PowerSaveMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NavigationSettings())

    fun setDefaultCameraMode(value: CameraMode3D) { viewModelScope.launch { prefs.setDefaultCameraMode(value) } }
    fun setZoomOffset(value: Int) {
        if (value !in GuidanceDefaults.ALLOWED_ZOOM_OFFSETS) return
        viewModelScope.launch { prefs.setZoomOffset(value) }
    }
    fun setPitchPreset(value: PitchPreset) { viewModelScope.launch { prefs.setPitchPreset(value) } }
    fun setAutoRotate(value: Boolean) { viewModelScope.launch { prefs.setAutoRotate(value) } }
    fun setShowNextManeuverPreview(value: Boolean) { viewModelScope.launch { prefs.setShowNextManeuverPreview(value) } }
    fun setPowerSaveMode(value: PowerSaveMode) { viewModelScope.launch { prefs.setPowerSaveMode(value) } }
```
Ligne 140. Ancien :
```kotlin
    fun setMap3d(value: Boolean) { viewModelScope.launch { prefs.setMap3dEnabled(value) } }
```
Nouveau :
```kotlin
    /** E2 « Bâtiments 3D » : la vue au départ, qui suivait l'ancien réglage tant qu'elle n'était pas choisie, est d'abord figée (écart 8). */
    fun setMap3d(value: Boolean) {
        viewModelScope.launch {
            prefs.setDefaultCameraMode(prefs.defaultCameraMode.first())
            prefs.setMap3dEnabled(value)
        }
    }
```

- [ ] **Étape 4 : chaînes.**

`feature/settings/src/main/res/values/strings.xml`, lignes 27-28. Ancien :
```xml
    <string name="settings_map_3d">Carte 3D en guidage</string>
    <string name="settings_map_3d_subtitle">Perspective et bâtiments pendant la navigation. Allégée en économie d\'énergie (sans bâtiments, inclinaison réduite).</string>
```
Nouveau :
```xml
    <string name="settings_map_3d">Bâtiments 3D</string>
    <string name="settings_map_3d_subtitle">Bâtiments en relief en vue 3D. Jamais en économie d\'énergie.</string>
```
Après la ligne 32 (`<string name="settings_map_detail_reduced">Réduit</string>`), ajouter :
```xml
    <string name="settings_group_navigation">Navigation</string>
    <string name="settings_nav_footer">Appliqués au prochain guidage. Les boutons 2D / 3D et « nord en haut » restent disponibles pendant la course.</string>
    <string name="settings_nav_view">Vue au départ</string>
    <string name="settings_nav_view_3d">3D</string>
    <string name="settings_nav_view_2d">2D</string>
    <string name="settings_nav_rotate">Rotation automatique</string>
    <string name="settings_nav_rotate_subtitle">La carte suit le cap. Désactivée : nord en haut au départ.</string>
    <string name="settings_nav_zoom">Zoom</string>
    <string name="settings_nav_zoom_subtitle">À l\'approche d\'une manœuvre, « Plus proche » ne resserre pas la vue : la flèche reste entière.</string>
    <string name="settings_nav_zoom_wide">Plus large</string>
    <string name="settings_nav_zoom_normal">Normal</string>
    <string name="settings_nav_zoom_close">Plus proche</string>
    <string name="settings_nav_pitch">Inclinaison en 3D</string>
    <string name="settings_nav_pitch_subtitle">En croisière. 50° à l\'approche, 45° en économie d\'énergie ; 60° au plus (limite de la carte).</string>
    <string name="settings_nav_pitch_value">%1$d°</string>
    <string name="settings_nav_then">Aperçu de la manœuvre suivante</string>
    <string name="settings_nav_then_subtitle">Ligne « puis … » sous le bandeau quand la manœuvre d\'après est à moins de 500 m.</string>
    <string name="settings_nav_power">Économie d\'énergie</string>
    <string name="settings_nav_power_subtitle">Sans bâtiments, inclinaison 45°, 30 images par seconde. Auto : suit l\'économiseur d\'Android.</string>
    <string name="settings_nav_power_auto">Auto</string>
    <string name="settings_nav_power_on">Activée</string>
    <string name="settings_nav_power_off">Désactivée</string>
```

- [ ] **Étape 5 : composable du groupe.**

`set/NavigationGroup.kt` :

```kotlin
package fr.roadly.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.RowDivider
import fr.roadly.core.designsystem.component.SegmentedControl
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.component.ToggleRow
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.model.CameraMode3D
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.PitchPreset
import fr.roadly.core.model.PowerSaveMode
import kotlin.math.roundToInt

/** Retrait d'un séparateur sous une ligne avec tuile d'icône (même valeur que `RowContentInset` de `SettingsScreen`). */
private val NavRowInset = 64.dp

/** Vues proposées au départ : le nord en haut passe par « Rotation automatique ». */
private val START_VIEWS = listOf(CameraMode3D.FOLLOW_3D, CameraMode3D.FOLLOW_2D)

/** E2 : groupe « Navigation » des Réglages. Les libellés sont résolus ici, le sélecteur segmenté prend une fonction non composable. */
@Composable
internal fun NavigationGroup(s: NavigationSettings, vm: SettingsViewModel) {
    SettingsGroup(stringResource(R.string.settings_group_navigation), footer = stringResource(R.string.settings_nav_footer)) {
        val views = START_VIEWS.associateWith { stringResource(if (it == CameraMode3D.FOLLOW_3D) R.string.settings_nav_view_3d else R.string.settings_nav_view_2d) }
        ChoiceRow(stringResource(R.string.settings_nav_view), null, Icons.Default.ViewInAr, START_VIEWS, s.defaultCameraMode, views, vm::setDefaultCameraMode)
        ToggleRow(
            stringResource(R.string.settings_nav_rotate), checked = s.autoRotate, onCheckedChange = vm::setAutoRotate,
            subtitle = stringResource(R.string.settings_nav_rotate_subtitle), icon = Icons.Default.Explore,
        )
        val zooms = mapOf(
            -1 to stringResource(R.string.settings_nav_zoom_wide), 0 to stringResource(R.string.settings_nav_zoom_normal), 1 to stringResource(R.string.settings_nav_zoom_close),
        )
        ChoiceRow(stringResource(R.string.settings_nav_zoom), stringResource(R.string.settings_nav_zoom_subtitle), Icons.Default.ZoomIn, GuidanceDefaults.ALLOWED_ZOOM_OFFSETS, s.zoomOffset, zooms, vm::setZoomOffset)
        val pitches = PitchPreset.entries.associateWith { stringResource(R.string.settings_nav_pitch_value, it.degrees.roundToInt()) }
        ChoiceRow(stringResource(R.string.settings_nav_pitch), stringResource(R.string.settings_nav_pitch_subtitle), Icons.Default.Landscape, PitchPreset.entries, s.pitchPreset, pitches, vm::setPitchPreset)
        val details = MapDetail.entries.associateWith { it.label() }
        ChoiceRow(stringResource(R.string.settings_map_detail), stringResource(R.string.settings_map_detail_subtitle), Icons.Default.Layers, MapDetail.entries, s.mapDetail, details, vm::setMapDetail)
        ToggleRow(
            stringResource(R.string.settings_map_3d), checked = s.buildings3d, onCheckedChange = vm::setMap3d,
            subtitle = stringResource(R.string.settings_map_3d_subtitle), icon = Icons.Default.LocationCity,
        )
        ToggleRow(
            stringResource(R.string.settings_nav_then), checked = s.showNextManeuverPreview, onCheckedChange = vm::setShowNextManeuverPreview,
            subtitle = stringResource(R.string.settings_nav_then_subtitle), icon = Icons.Default.TurnRight,
        )
        val powers = PowerSaveMode.entries.associateWith { it.label() }
        ChoiceRow(stringResource(R.string.settings_nav_power), stringResource(R.string.settings_nav_power_subtitle), Icons.Default.BatterySaver, PowerSaveMode.entries, s.powerSaveMode, powers, vm::setPowerSaveMode, last = true)
    }
}

/** Ligne titrée suivie d'un sélecteur segmenté, puis d'un séparateur (sauf la dernière ligne du groupe). */
@Composable
private fun <T> ChoiceRow(
    title: String, subtitle: String?, icon: ImageVector, options: List<T>, selected: T, labels: Map<T, String>, onSelect: (T) -> Unit, last: Boolean = false,
) {
    ListRow(title, subtitle = subtitle, icon = icon, divider = false)
    SegmentedControl(
        options = options, selected = selected, onSelect = onSelect, label = { labels.getValue(it) },
        modifier = Modifier.padding(start = RoadlySpacing.m, end = RoadlySpacing.m, bottom = RoadlySpacing.m),
    )
    if (!last) RowDivider(inset = NavRowInset)
}

@Composable
private fun MapDetail.label(): String = stringResource(
    when (this) {
        MapDetail.NORMAL -> R.string.settings_map_detail_normal
        MapDetail.REDUCED -> R.string.settings_map_detail_reduced
    },
)

@Composable
private fun PowerSaveMode.label(): String = stringResource(
    when (this) {
        PowerSaveMode.AUTO -> R.string.settings_nav_power_auto
        PowerSaveMode.ON -> R.string.settings_nav_power_on
        PowerSaveMode.OFF -> R.string.settings_nav_power_off
    },
)
```

- [ ] **Étape 6 : écran des Réglages.**

`set/SettingsScreen.kt`, lignes 146-148. Ancien :
```kotlin
            val map3d by vm.map3d.collectAsStateWithLifecycle()
            val mapDetail by vm.mapDetail.collectAsStateWithLifecycle()
            AppearanceGroup(state.themeMode, onSelect = vm::setTheme, map3d = map3d, onMap3d = vm::setMap3d, mapDetail = mapDetail, onMapDetail = vm::setMapDetail)
```
Nouveau :
```kotlin
            val navigation by vm.navigation.collectAsStateWithLifecycle()
            NavigationGroup(navigation, vm)
            AppearanceGroup(state.themeMode, onSelect = vm::setTheme)
```

Lignes 293-316. Ancien :
```kotlin
@Composable
private fun AppearanceGroup(
    current: ThemeMode, onSelect: (ThemeMode) -> Unit, map3d: Boolean, onMap3d: (Boolean) -> Unit,
    mapDetail: MapDetail, onMapDetail: (MapDetail) -> Unit,
) {
    SettingsGroup(stringResource(R.string.settings_group_appearance)) {
        ToggleRow(
            stringResource(R.string.settings_map_3d),
            checked = map3d, onCheckedChange = onMap3d,
            subtitle = stringResource(R.string.settings_map_3d_subtitle),
            icon = Icons.Default.ViewInAr,
        )
        ListRow(
            stringResource(R.string.settings_map_detail),
            subtitle = stringResource(R.string.settings_map_detail_subtitle),
            icon = Icons.Default.Layers,
            divider = false,
        )
        val detailLabels = MapDetail.entries.associateWith { it.label() }
        SegmentedControl(
            options = MapDetail.entries, selected = mapDetail, onSelect = onMapDetail, label = { detailLabels.getValue(it) },
            modifier = Modifier.padding(start = RoadlySpacing.m, end = RoadlySpacing.m, bottom = RoadlySpacing.m),
        )
        RowDivider(inset = RowContentInset)
        ListRow(
```
Nouveau :
```kotlin
@Composable
private fun AppearanceGroup(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SettingsGroup(stringResource(R.string.settings_group_appearance)) {
        ListRow(
```

Supprimer les lignes 629-635. Ancien :
```kotlin

@Composable
private fun MapDetail.label(): String = stringResource(
    when (this) {
        MapDetail.NORMAL -> R.string.settings_map_detail_normal
        MapDetail.REDUCED -> R.string.settings_map_detail_reduced
    },
)
```
Nouveau : rien.

Supprimer les imports devenus inutiles :
- ligne 44 : `import androidx.compose.material.icons.filled.Layers` ;
- ligne 58 : `import androidx.compose.material.icons.filled.ViewInAr` ;
- ligne 110 : `import fr.roadly.core.model.MapDetail`.

Vérifier par une recherche dans le fichier que `ViewInAr`, `Layers` et `MapDetail` n'apparaissent plus.

- [ ] **Étape 7 : vérifier le succès.**

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
```
Résultat attendu : SUCCÈS, avec 2 tests nouveaux. `map detail level is exposed and persisted` reste vert : `vm.mapDetail` est gardé.

- [ ] **Étape 8 : commiter.**

```powershell
git add feature/settings/src/main/java/fr/roadly/feature/settings/NavigationGroup.kt feature/settings/src/main/java/fr/roadly/feature/settings/SettingsViewModel.kt feature/settings/src/main/java/fr/roadly/feature/settings/SettingsScreen.kt feature/settings/src/main/res/values/strings.xml feature/settings/src/test/java/fr/roadly/feature/settings/SettingsViewModelTest.kt
git commit -m @'
feat(réglages): groupe « Navigation » — vue au départ, rotation, zoom, inclinaison, détail, bâtiments 3D, « puis », économie d'énergie (E2)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 8 : E3 — « Reprendre le guidage vers … » après la mort du processus

**Fichiers :**
- Modifier `corenav/NavigationSession.kt` : ligne 30 (import `Flow`) et après la ligne 69 (`muted`).
- Modifier `home/HomeViewModel.kt` :
  - imports (lignes 6-40) ;
  - constructeur (lignes 47-56) ;
  - après la ligne 124 (`isGuiding`).
- Modifier `home/HomeUiState.kt` : fin de fichier.
- Créer `home/ResumeGuidanceCard.kt`.
- Modifier `home/HomeScreen.kt` : après la ligne 119 ; ligne 215 ; `BottomPanel`, lignes 254-269.
- Modifier `feature/home/src/main/res/values/strings.xml` : après la ligne 20.
- Modifier `feature/home/build.gradle.kts` : après la ligne 31, pour les aperçus.
- Tests :
  - `corenavtest/NavigationSessionGuidanceTest.kt` (2 tests avant la ligne 224 ; import de `GeoPoint`) ;
  - `hometest/HomeViewModelTest.kt` (doubles de test lignes 82-100, 4 tests).

**Interfaces :**
- Consomme :
  - `prefs.lastGuidance` et `LastGuidance(destination, destinationName, routeId, startedAtEpochMs)` ;
  - `NavigationSession.start(option, preferences, vehicle, destinationName)` ;
  - `RouteAlternativesUseCase(origin, destination, vehicle, departAtEpochMs, preferences)`, `RouteCosting.FASTEST`.
- Produit :
  - `NavigationSession.resumableGuidance: Flow<LastGuidance?>` et `NavigationSession.forgetLastGuidance()` ;
  - `enum class ResumePhase { OFFER, ROUTING, FAILED }` et `data class ResumeCardUi(val destinationName: String?, val phase: ResumePhase = ResumePhase.OFFER)` ;
  - `HomeViewModel.resumeCard: StateFlow<ResumeCardUi?>`, `resumeLastGuidance()`, `dismissResume()` ;
  - `internal fun ResumeGuidanceCard(card: ResumeCardUi, canResume: Boolean, onResume: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Étape 1 : écrire les tests de session qui échouent.**

`corenavtest/NavigationSessionGuidanceTest.kt`. Ajouter `import fr.roadly.core.model.GeoPoint` après la ligne 9. Insérer avant l'accolade finale (ligne 224) :

```kotlin

    @Test fun `after a process death the remembered run is offered while idle and forgotten on ignore`() = runTest { // scénario 14 (E3)
        val saved = LastGuidance(GeoPoint(43.6045, 1.4440), "Capitole", "r-avant", 0L)
        prefs.setLastGuidance(saved)
        val s = session(); runCurrent()
        assertEquals(NavigationState.Idle, s.state.value)
        assertEquals(saved, s.resumableGuidance.first())
        s.forgetLastGuidance(); runCurrent()
        assertNull(prefs.lastGuidance.first())
        assertNull(s.resumableGuidance.first())
    }

    @Test fun `no resume is offered while guiding, and ignore keeps the current run`() = runTest {
        val route = TestRoutes.straight()
        val s = session(); s.start(option(route), RoutePreferences(), destinationName = "Maison"); runCurrent()
        assertNull(s.resumableGuidance.first())
        s.forgetLastGuidance(); runCurrent()
        assertNotNull(prefs.lastGuidance.first())
    }
```

- [ ] **Étape 2 : écrire les tests de l'accueil qui échouent.**

`hometest/HomeViewModelTest.kt`. Ajouter les imports :
```kotlin
import fr.roadly.core.data.repository.LastGuidance
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.routing.RouteAlternativesUseCase
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
```

Lignes 82-100. Ancien : de `private val routing = object : RoutingProvider {` jusqu'à la fin de `private fun TestScope.vm(…) = HomeViewModel(…)`. Nouveau :
```kotlin
    /** Itinéraires renvoyés par le faux fournisseur (échec par défaut) et requêtes reçues. */
    private var routeResult: Outcome<List<Route>> = Outcome.Failure(AppError.Network)
    private val requests = mutableListOf<RouteRequest>()
    private val routing = object : RoutingProvider {
        override val id = "fake"
        override suspend fun route(request: RouteRequest): Outcome<List<Route>> { requests += request; return routeResult }
    }
    private val prefs = FakeUserPreferencesRepository()
    private val vehicles = object : VehicleProfileRepository {
        override val profile = flowOf<VehicleProfile?>(null)
        override suspend fun save(profile: VehicleProfile) = Unit
        override suspend fun clear() = Unit
    }

    /** Vraie session de guidage câblée sur des doubles (comme `NavigationViewModelTest`), mêmes préférences que le ViewModel. */
    private fun TestScope.session(): NavigationSession {
        val d = StandardTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val io: CoroutineDispatcher = d; override val default: CoroutineDispatcher = d; override val main: CoroutineDispatcher = d
        }
        return NavigationSession(
            location, routing, NoopSpeedLimitProvider(), FakeSpeechService(), prefs,
            FakeNavigationServiceController(), { testScheduler.currentTime }, NoopVigilanceZoneProvider(), NoopRainProvider(), NoopTripRecorder(), dispatchers, backgroundScope,
        )
    }

    private fun TestScope.vm(session: NavigationSession = session()) =
        HomeViewModel(location, favorites, geocoder, connectivity, config, PermissionEvents(), session, NoopCommunity, prefs, RouteAlternativesUseCase(routing), vehicles)
```

Insérer après le test `isGuiding reflects the session state` (avant l'accolade finale de la classe, ligne 149) :
```kotlin

    @Test fun `an interrupted run is offered at home while idle, and hidden while guiding`() = runTest { // scénario 14 (E3)
        prefs.setLastGuidance(LastGuidance(GeoPoint(43.6045, 1.4440), "Capitole", "r-avant", 0L))
        val s = session()
        val vm = vm(s)
        vm.resumeCard.test {
            runCurrent()
            assertEquals(ResumeCardUi("Capitole"), expectMostRecentItem())
            s.start(RouteOption(TestRoutes.straight(), "Le plus rapide", 0L, null, sameAsFastest = false), RoutePreferences()); runCurrent()
            assertNull("un guidage en cours n'est pas une reprise", expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `ignore forgets the interrupted run`() = runTest {
        prefs.setLastGuidance(LastGuidance(GeoPoint(43.6045, 1.4440), null, "r-avant", 0L))
        val vm = vm()
        vm.resumeCard.test {
            runCurrent()
            assertEquals("sans nom : « Reprendre le dernier guidage »", ResumeCardUi(null), expectMostRecentItem())
            vm.dismissResume(); runCurrent()
            assertNull(expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertNull(prefs.last.value)
    }

    @Test fun `resume routes from the current position to the saved destination and starts guiding with its name`() = runTest {
        routeResult = Outcome.Success(listOf(TestRoutes.straight()))
        val saved = LastGuidance(TestRoutes.straight().geometry.last(), "Capitole", "r-avant", 0L)
        prefs.setLastGuidance(saved)
        location.lastKnown = fix(48.85, 2.35)
        val s = session()
        val vm = vm(s)
        vm.resumeCard.test {
            runCurrent()
            vm.resumeLastGuidance(); runCurrent()
            assertNull("guidage repris : plus de carte", expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue("nouvel itinéraire depuis la position actuelle, pas le tracé enregistré", requests.isNotEmpty() && requests.all { it.origin == GeoPoint(48.85, 2.35) && it.destination == saved.destination })
        assertEquals(TestRoutes.straight().id, (s.state.value as NavigationState.Guiding).route.id)
        assertEquals("Capitole", prefs.last.value!!.destinationName)
    }

    @Test fun `a resume without route keeps the card, marked failed, and the remembered run`() = runTest {
        val saved = LastGuidance(GeoPoint(43.6045, 1.4440), "Capitole", "r-avant", 0L)
        prefs.setLastGuidance(saved)
        location.lastKnown = fix(43.60, 1.44)
        val vm = vm()
        vm.resumeCard.test {
            runCurrent()
            vm.resumeLastGuidance(); runCurrent()
            assertEquals(ResumeCardUi("Capitole", ResumePhase.FAILED), expectMostRecentItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(saved, prefs.last.value)
    }
```

- [ ] **Étape 3 : vérifier l'échec.**

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*NavigationSessionGuidanceTest*"
.\gradlew.bat :feature:home:testDebugUnitTest --offline -q
```
Résultat attendu : ÉCHEC de compilation, `Unresolved reference: resumableGuidance` / `forgetLastGuidance` / `ResumeCardUi` / `resumeCard`, et `Too many arguments for HomeViewModel`.

- [ ] **Étape 4 : session.**

`corenav/NavigationSession.kt`. Après la ligne 30 (`import kotlinx.coroutines.flow.MutableStateFlow`), ajouter `import kotlinx.coroutines.flow.Flow`.

Après la ligne 69 (`val muted: StateFlow<Boolean> = _muted.asStateFlow()`), ajouter :
```kotlin

    /**
     * E3 : course mémorisée à proposer à la reprise (« Reprendre le guidage vers … »), seulement au repos — après la mort du processus ou un
     * redémarrage (un arrêt ou une arrivée l'ont effacée). Null pendant un guidage. LIMITE : aucune limite d'âge.
     */
    val resumableGuidance: Flow<LastGuidance?> = combine(prefs.lastGuidance, state) { last, s -> last.takeIf { s is NavigationState.Idle } }
        .distinctUntilChanged()

    /** E3 « Ignorer » : oublie la course mémorisée ; sans effet pendant un guidage, dont la course est celle qui est mémorisée. */
    fun forgetLastGuidance() {
        if (_state.value is NavigationState.Idle) persistLastGuidance(null)
    }
```

- [ ] **Étape 5 : état et ViewModel de l'accueil.**

`home/HomeUiState.kt`, en fin de fichier :
```kotlin

/** E3 : étape de la reprise proposée par l'accueil. */
enum class ResumePhase { OFFER, ROUTING, FAILED }

/** E3 : carte « Reprendre le guidage vers … » ; [destinationName] null si la course avait démarré sans nom. */
data class ResumeCardUi(val destinationName: String?, val phase: ResumePhase = ResumePhase.OFFER)
```

`home/HomeViewModel.kt`. Imports à ajouter :
```kotlin
import fr.roadly.core.data.repository.LastGuidance
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.flow.first
```

Lignes 54-56. Ancien :
```kotlin
    navigationSession: NavigationSession,
    private val community: CommunityRepository,
) : ViewModel() {
```
Nouveau :
```kotlin
    private val navigationSession: NavigationSession,
    private val community: CommunityRepository,
    private val prefs: UserPreferencesRepository,
    private val alternatives: RouteAlternativesUseCase,
    private val vehicles: VehicleProfileRepository,
) : ViewModel() {
```

Après la ligne 124 (fin de `isGuiding`), ajouter :
```kotlin

    private val resumePhase = MutableStateFlow(ResumePhase.OFFER)
    private val resumable: StateFlow<LastGuidance?> = navigationSession.resumableGuidance.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** E3 : carte « Reprendre le guidage vers … » — course interrompue et session au repos ; null sinon. */
    val resumeCard: StateFlow<ResumeCardUi?> = combine(resumable, resumePhase) { last, phase -> last?.let { ResumeCardUi(it.destinationName, phase) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * E3 « Reprendre » : nouvel itinéraire depuis la position actuelle vers la destination mémorisée (pas le tracé enregistré), avec les
     * préférences et le véhicule du moment, puis départ comme « ROULER » (`session.start` avec le nom). Même calcul que
     * `GuidanceActions.startTo` de Roadly AI. Échec : la carte le dit, la course reste mémorisée. Sans position connue : rien.
     */
    fun resumeLastGuidance() {
        val last = resumable.value ?: return
        val origin = location.lastKnown?.point ?: return
        if (resumePhase.value == ResumePhase.ROUTING) return
        resumePhase.value = ResumePhase.ROUTING
        viewModelScope.launch {
            val p = prefs.routePreferences.first()
            val v = vehicles.profile.first()
            when (val r = alternatives(origin, last.destination, v, System.currentTimeMillis(), p)) {
                is Outcome.Success -> {
                    val best = r.value.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: r.value.first()
                    resumePhase.value = ResumePhase.OFFER
                    navigationSession.start(best, p, v, last.destinationName)
                }
                is Outcome.Failure -> resumePhase.value = ResumePhase.FAILED
            }
        }
    }

    /** E3 « Ignorer » : la course mémorisée est oubliée ; plus de carte jusqu'au prochain guidage interrompu. */
    fun dismissResume() {
        resumePhase.value = ResumePhase.OFFER
        navigationSession.forgetLastGuidance()
    }
```

- [ ] **Étape 6 : carte, chaînes et écran.**

`feature/home/src/main/res/values/strings.xml`, après la ligne 20 (`home_resume_guidance`) :
```xml
    <string name="home_resume_last">Reprendre le dernier guidage</string>
    <string name="home_resume_last_to">Reprendre le guidage vers %1$s</string>
    <string name="home_resume_action">Reprendre</string>
    <string name="home_resume_routing">Calcul de l\'itinéraire…</string>
    <string name="home_resume_dismiss">Ignorer</string>
    <string name="home_resume_failed">Itinéraire impossible pour l\'instant. Réessayez.</string>
    <string name="home_resume_waiting_position">En attente de la position…</string>
```

`feature/home/build.gradle.kts`, après la ligne 31 (`implementation(libs.serialization.json)`), ajouter :
```kotlin
    debugImplementation(libs.compose.ui.tooling)
```

`home/ResumeGuidanceCard.kt` :
```kotlin
package fr.roadly.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.GlassPanel
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing
import fr.roadly.core.designsystem.theme.RoadlyTheme

/**
 * E3 : « Reprendre le guidage vers … » (course interrompue par la mort du processus ou un redémarrage) ; « Ignorer » l'oublie.
 * [canResume] : position connue (l'itinéraire part de la position actuelle).
 */
@Composable
internal fun ResumeGuidanceCard(card: ResumeCardUi, canResume: Boolean, onResume: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalRoadlyColors.current
    Column(modifier.fillMaxWidth()) {
        Text(
            card.destinationName?.let { stringResource(R.string.home_resume_last_to, it) } ?: stringResource(R.string.home_resume_last),
            style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        val failed = card.phase == ResumePhase.FAILED
        val status = when {
            failed -> R.string.home_resume_failed
            !canResume -> R.string.home_resume_waiting_position
            else -> null
        }
        status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = if (failed) c.danger else c.textSecondary) }
        Spacer(Modifier.height(RoadlySpacing.s))
        Row(horizontalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
            val routing = card.phase == ResumePhase.ROUTING
            GlassButton(
                stringResource(if (routing) R.string.home_resume_routing else R.string.home_resume_action),
                onClick = onResume, icon = Icons.Default.Navigation, style = GlassButtonStyle.Brand,
                enabled = canResume && !routing, modifier = Modifier.weight(1f),
            )
            GlassButton(stringResource(R.string.home_resume_dismiss), onClick = onDismiss, style = GlassButtonStyle.Ghost)
        }
    }
}

@Preview(name = "Nuit", showBackground = true, backgroundColor = 0xFF0E0B14)
@Composable
private fun ResumeGuidanceCardNightPreview() {
    RoadlyTheme(darkTheme = true) { GlassPanel { ResumeGuidanceCard(ResumeCardUi("Capitole"), canResume = true, onResume = {}, onDismiss = {}) } }
}

@Preview(name = "Jour", showBackground = true, backgroundColor = 0xFFF5F3F8)
@Composable
private fun ResumeGuidanceCardDayPreview() {
    RoadlyTheme(darkTheme = false) { GlassPanel { ResumeGuidanceCard(ResumeCardUi("Capitole", ResumePhase.FAILED), canResume = true, onResume = {}, onDismiss = {}) } }
}
```

`home/HomeScreen.kt`. Après la ligne 119 (`val isGuiding by vm.isGuiding.collectAsStateWithLifecycle()`), ajouter :
```kotlin
    val resumeCard by vm.resumeCard.collectAsStateWithLifecycle()
    // E3 : après « Reprendre », l'écran de guidage s'ouvre dès que la session guide (même chemin que « Reprendre le guidage »).
    var resumeRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(resumeRequested, isGuiding) { if (resumeRequested && isGuiding) { resumeRequested = false; onResumeGuidance() } }
    LaunchedEffect(resumeCard?.phase) { if (resumeCard?.phase == ResumePhase.FAILED) resumeRequested = false }
```

Ligne 215. Ancien :
```kotlin
                LocationPermissionState.GRANTED -> BottomPanel(state, isGuiding, onNavigateToRoute, onDefine = onSearch, onResumeGuidance = onResumeGuidance, onPosition = { showPosition = true }, onCategory = onSearchCategory)
```
Nouveau :
```kotlin
                LocationPermissionState.GRANTED -> BottomPanel(
                    state, isGuiding, onNavigateToRoute, onDefine = onSearch, onResumeGuidance = onResumeGuidance, onPosition = { showPosition = true }, onCategory = onSearchCategory,
                    resumeCard = resumeCard, onResumeLast = { resumeRequested = true; vm.resumeLastGuidance() }, onDismissResume = vm::dismissResume,
                )
```

Lignes 261-269. Ancien :
```kotlin
    onCategory: (PoiCategory) -> Unit,
) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column {
            if (isGuiding) {
                GlassButton(stringResource(R.string.home_resume_guidance), onClick = onResumeGuidance, icon = Icons.Default.Navigation, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(RoadlySpacing.sm))
            }
```
Nouveau :
```kotlin
    onCategory: (PoiCategory) -> Unit,
    resumeCard: ResumeCardUi? = null,
    onResumeLast: () -> Unit = {},
    onDismissResume: () -> Unit = {},
) {
    val c = LocalRoadlyColors.current
    GlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Column {
            if (isGuiding) {
                GlassButton(stringResource(R.string.home_resume_guidance), onClick = onResumeGuidance, icon = Icons.Default.Navigation, style = GlassButtonStyle.Brand, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(RoadlySpacing.sm))
            }
            // E3 : course interrompue (mort du processus, redémarrage) : reprise vers la même destination, depuis la position actuelle.
            if (!isGuiding && resumeCard != null) {
                ResumeGuidanceCard(resumeCard, canResume = state.fix != null, onResume = onResumeLast, onDismiss = onDismissResume)
                Spacer(Modifier.height(RoadlySpacing.sm))
            }
```

- [ ] **Étape 7 : vérifier le succès.**

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest :feature:home:testDebugUnitTest --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
```
Résultat attendu : SUCCÈS, avec 6 tests nouveaux (2 de session, 4 de l'accueil). Les 4 tests existants de `HomeViewModelTest` restent verts sans changement d'assertion.

- [ ] **Étape 8 : commiter.**

```powershell
git add core/navigation/src/main/java/fr/roadly/core/navigation/NavigationSession.kt core/navigation/src/test/java/fr/roadly/core/navigation/NavigationSessionGuidanceTest.kt feature/home/src/main/java/fr/roadly/feature/home/HomeViewModel.kt feature/home/src/main/java/fr/roadly/feature/home/HomeUiState.kt feature/home/src/main/java/fr/roadly/feature/home/ResumeGuidanceCard.kt feature/home/src/main/java/fr/roadly/feature/home/HomeScreen.kt feature/home/src/main/res/values/strings.xml feature/home/build.gradle.kts feature/home/src/test/java/fr/roadly/feature/home/HomeViewModelTest.kt
git commit -m @'
feat(accueil): « Reprendre le guidage vers … » après la mort du processus — itinéraire refait depuis la position, « Ignorer » l'oublie (E3, scénario 14)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

### Tâche 9 : passe appareil HONOR — voix avec musique, préemption, réglages, reprise, arrière-plan, consignation

**Fichiers :**
- Modifier `ROADLY_3D_NAVIGATION_TESTS.md`, avec l'outil Edit uniquement : ligne 3, lignes 31, 33 et 34, et journal (après la ligne 78).
- Créer dans `docs/captures/etape-e/` :
  - `reglages-navigation.png` ;
  - `guidage-2d-nord.png` ;
  - `guidage-inclinaison-basse.png` ;
  - `reprise-carte.png` ;
  - `reprise-guidage.png`.
- Pas de code.

**Interfaces :**
- Consomme : les tâches 1 à 8.
- Produit : les essais consignés.

- [ ] **Étape 1 : vérification complète et installation.**

```powershell
.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
$n = 0; Get-ChildItem -Recurse -Filter 'TEST-*.xml' -Path core, feature | Where-Object FullName -match 'test-results' | ForEach-Object { $n += [int]([xml](Get-Content $_.FullName)).testsuite.tests }; $n
git rev-parse --short HEAD
adb -s AREE026226000174 install -r app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :feature:navigation:connectedDebugAndroidTest --offline
```
Résultat attendu :
- SUCCÈS. Au moins 473 + 33 = 506 tests JVM (tâches 1 : 8 ; 3 : 4 ; 4 : 5 ; 5 : 5 ; 6 : 3 ; 7 : 2 ; 8 : 6) ;
- les 11 tests instrumentés de `NavigationPremiumUiTest` sont verts.

Noter le nombre de tests et l'empreinte.

- [ ] **Étape 2 : voix avec musique et préemption de l'assistant (E1, scénario 17).**

1. L'utilisateur lance une musique dans une autre application.
2. Réglages → « Tester la voix ». Attendu : la musique baisse pendant la phrase, puis revient. Pendant la phrase :
   ```powershell
   $d = 'AREE026226000174'
   adb -s $d shell dumpsys audio | Select-String -Pattern "fr.roadly|MAY_DUCK"
   ```
   Attendu : une entrée de focus `fr.roadly`, gain `GAIN_TRANSIENT_MAY_DUCK`. Après la phrase, la commande ne renvoie plus `fr.roadly` : le focus est rendu.
3. Préemption (annonce de départ, priorité NORMAL, sur la phrase d'essai, canal ASSISTANT LOW) :
   ```powershell
   adb -s $d logcat -c
   ```
   - Choisir une destination (appui long sur la carte → « Itinéraire »). Sur l'écran d'itinéraire : Réglages → « Tester la voix ». Revenir aussitôt et toucher « ROULER » pendant la phrase (environ 5 s).
   - Puis :
   ```powershell
   adb -s $d logcat -d -v time -s RoadlySpeech:D
   ```
   Attendu, dans l'ordre :
   - `Dit : ASSISTANT LOW` ;
   - `Coupé : ASSISTANT LOW` ;
   - `Dit : GUIDANCE NORMAL` ;
   - `Fini : GUIDANCE` ;
   - `Dit : ASSISTANT LOW`, au moins 300 ms après `Fini : GUIDANCE` (noter l'écart lu dans les horodatages).

   La phrase d'essai est redite en entier, et la musique reste baissée entre les deux phrases.
4. Voix coupée (scénario 16) : Réglages → Voix → désactivée, puis lancer un guidage. Attendu : aucune annonce de départ, et « Tester la voix » parle toujours (canal assistant). Réactiver ensuite la voix.

- [ ] **Étape 3 : réglages « Navigation » (E2) et persistance.**

1. Réglages → Navigation : capture `reglages-navigation.png`. Choisir :
   - Vue au départ « 2D » ;
   - Rotation automatique désactivée ;
   - Zoom « Plus large » ;
   - Inclinaison « 45° » ;
   - Aperçu de la manœuvre suivante désactivé ;
   - Économie d'énergie « Activée ».
2. Relancer l'application et vérifier la persistance :
   ```powershell
   adb -s $d shell am force-stop fr.roadly
   adb -s $d shell "run-as fr.roadly cat files/datastore/roadly_prefs.preferences_pb" | Select-String -Pattern "map.camera.default|map.auto.rotate|map.zoom.offset|map.pitch|guidance.then.preview|power.save.mode"
   adb -s $d shell monkey -p fr.roadly -c android.intent.category.LAUNCHER 1
   ```
   Attendu : les six clés sont présentes, et Réglages → Navigation montre les mêmes valeurs.
3. Lancer un guidage :
   - départ en nord en haut, en 2D → capture `guidage-2d-nord.png` ;
   - pas de ligne « puis … » ;
   - économie d'énergie : `adb -s $d logcat -d -s RoadlyFps:D` donne environ 30 images/s, alors que `adb -s $d shell settings get global low_power` renvoie `0`.
4. Remettre la rotation, la vue en 3D et l'économie en « Désactivée ». Lancer un guidage → inclinaison de 45° visible → capture `guidage-inclinaison-basse.png`.
5. Remettre toutes les valeurs par défaut.

- [ ] **Étape 4 : reprise après la mort du processus (E3, scénario 14).**

Lancer un guidage vers une destination nommée (par la recherche), puis :
```powershell
$appPid = (adb -s $d shell pidof fr.roadly).Trim()
adb -s $d shell run-as fr.roadly kill -9 $appPid
adb -s $d shell "run-as fr.roadly cat files/datastore/roadly_prefs.preferences_pb" | Select-String "guidance.last"
adb -s $d shell monkey -p fr.roadly -c android.intent.category.LAUNCHER 1
```
Attendu :
- les clés `guidance.last.*` sont présentes ;
- l'accueil affiche la carte « Reprendre le guidage vers <nom> » → capture `reprise-carte.png` ;
- « Reprendre » → « Calcul de l'itinéraire… », puis l'écran de guidage. L'itinéraire part de la position actuelle → capture `reprise-guidage.png`.

Recommencer `kill -9`, relancer, puis « Ignorer » : la carte disparaît, et la commande `Select-String "guidance.last"` ne renvoie plus rien.

Mode avion à la reprise (scénario 20, action de l'utilisateur) : « Itinéraire impossible pour l'instant. Réessayez. », et la carte reste. Rétablir le réseau.

- [ ] **Étape 5 : arrière-plan, notification, rotation (E3).**

1. Notification : pendant un guidage, `adb -s $d shell input keyevent KEYCODE_HOME`. L'utilisateur touche la notification « Roadly » : retour à l'écran de guidage, sans remise à zéro.
2. Rotation : l'utilisateur tourne l'appareil pendant le guidage. L'état est gardé (bandeau, panneau, caméra).
3. Écran verrouillé 10 min (stationnaire) :
   ```powershell
   adb -s $d shell input keyevent KEYCODE_SLEEP
   ```
   Attendre 10 min, téléphone posé, puis :
   ```powershell
   adb -s $d shell dumpsys activity services fr.roadly | Select-String -Pattern "NavigationService|isForeground"
   adb -s $d shell dumpsys notification --noredact | Select-String "roadly.navigation"
   adb -s $d shell dumpsys location | Select-String "fr.roadly"
   adb -s $d shell pidof fr.roadly
   adb -s $d shell input keyevent KEYCODE_WAKEUP
   ```
   Attendu :
   - le service est au premier plan (`isForeground=true`) ;
   - la notification est vivante ;
   - une demande de localisation `fr.roadly` est active ;
   - le processus est vivant.

   L'utilisateur déverrouille : guidage en cours, heure d'arrivée à jour.
4. À faire en roulant (par l'utilisateur, avec un passager si possible) :
   - annonces prononcées écran verrouillé pendant 10 min ;
   - NOW (URGENT) qui coupe une phrase en cours, avec la musique ;
   - après fusion avec Roadly AI : voix live de l'assistant coupée par un NOW, puis reprise environ 300 ms après.

- [ ] **Étape 6 : consigner dans `ROADLY_3D_NAVIGATION_TESTS.md` (outil Edit uniquement).**

**1. Ligne 3.** Remplacer ``État actuel (fin d'étape D, `5d82e35`) : 473 tests JVM verts, 14 instrumentés (dont 11 de `NavigationPremiumUiTest`, verts sur le HONOR), aucune CI.`` par ``État actuel (fin d'étape E, `<empreinte de l'étape 1>`) : <nombre de l'étape 1> tests JVM verts, 14 instrumentés (dont 11 de `NavigationPremiumUiTest`, verts sur le HONOR), aucune CI.``.

**2. Ligne 31.** Ancien :
```markdown
| 14 | Reprise après arrière-plan / mort du processus | JVM : `lastOption` persisté ; appareil : `am kill` → reprise proposée | appareil | B6, E3 |
```
Nouveau :
```markdown
| 14 | Reprise après arrière-plan / mort du processus | JVM : `lastGuidance` persisté, proposé au repos et oublié par « Ignorer » (`NavigationSessionGuidanceTest`), carte, reprise depuis la position et échec (`HomeViewModelTest`) ; appareil : `kill -9` → « Reprendre le guidage vers … » → itinéraire refait ; écran verrouillé 10 min, notification, rotation | appareil | B6, E3 |
```

**3. Ligne 33.** Ancien :
```markdown
| 16 | Désactivation des annonces | JVM existant (sourdine, voix off) | appareil | — |
```
Nouveau :
```markdown
| 16 | Désactivation des annonces | JVM existant (sourdine, voix off) ; `NavigationSessionVoiceTest` : voix off et sourdine ne coupent que le canal du guidage ; appareil : voix off, « Tester la voix » parle encore | appareil | E1 |
```

**4. Ligne 34.** Ancien :
```markdown
| 17 | Interruption audio par Roadly AI | JVM `FakeSpeechService` : NOW coupe l'assistant, reprise après | appareil, musique | E1 |
```
Nouveau :
```markdown
| 17 | Interruption audio par Roadly AI | JVM `VoiceArbiterTest` : NOW coupe l'assistant et vide sa file, NEAR ensuite, assistant repris ≥ 300 ms après ; NEAR / FAR coupent l'assistant, redit en entier ; l'assistant ne coupe jamais le guidage ; `NavigationSessionVoiceTest` (canal du guidage) ; appareil : « Tester la voix » (canal assistant) coupée par l'annonce de départ, redite après la pause, musique atténuée. Voix live de l'IA : après fusion | appareil, musique | E1 |
```

**5. Journal.** Ajouter après la ligne 78, une ligne par essai réellement fait, en remplaçant chaque `<…>` par la valeur observée :
```markdown
| <date> | E : 14 (`kill -9` → reprise, « Ignorer »), 16, 17 (préemption « Tester la voix » par l'annonce de départ), 20 (reprise hors ligne) ; réglages Navigation ; arrière-plan | HONOR Magic 8 Pro | <résultat> | APK construit à `<empreinte>` ; captures `docs/captures/etape-e/` ; focus `GAIN_TRANSIENT_MAY_DUCK` vu dans `dumpsys audio` puis rendu ; journal `RoadlySpeech` : Dit ASSISTANT → Coupé ASSISTANT → Dit GUIDANCE → Fini GUIDANCE → Dit ASSISTANT après <n> ms ; réglages persistés après `force-stop` (6 clés) ; départ 2D nord en haut, sans « puis », économie forcée à <n> i/s (économiseur Android coupé) ; inclinaison 45° ; reprise vers <nom> depuis la position ; écran verrouillé 10 min : service au premier plan, notification, localisation, processus vivants ; notification → guidage ; rotation sans perte ; instrumentés 11/11 verts. À faire en roulant : annonces écran verrouillé, NOW sur une phrase en cours avec musique, voix live de Roadly AI après fusion |
```

- [ ] **Étape 7 : commiter.**

```powershell
git add ROADLY_3D_NAVIGATION_TESTS.md docs/captures/etape-e
git commit -m @'
docs(tests): passe appareil de l'étape E — voix avec musique et préemption, réglages Navigation persistés, reprise après kill -9, arrière-plan (E1–E3)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
'@
```

---

## Surface de fusion avec `feat/roadly-ai`

Base commune `60033fe`. Ce qui suit a été vérifié par `git diff 60033fe HEAD --stat` et par les diffs fichier par fichier dans `GPS-roadly-ai`.

| Fichier | Les deux branches | Résolution |
|---|---|---|
| `corenav/SpeechService.kt` | **IA :** `val speaking: StateFlow<Boolean>`. **E :** même membre, avec le même sens, plus `VoiceChannel`, `speak(…, priority, channel)`, `setMuted(…, channel)`, `stop(channel)` | Garder la version E : c'est un surensemble de même nom et de même sens. Une seule définition de `speaking` |
| `corenav/AndroidSpeechService.kt` | **IA :** `_speaking` sur l'ancienne file `pending` (base). **E :** fichier réécrit, `speaking` = en cours ou en file | Garder la version E entière. Les blocs IA portent sur du code qui n'existe plus depuis B5 |
| `corenav/testing/FakeSpeechService.kt` | **IA :** `speaking`, `setSpeaking`, `flushes`, `stop()` → faux. **E :** mêmes noms, plus `channels`, `stoppedChannels`, `mutedChannels`, `priorities` | Garder la version E. `FakeSpeechServiceTest` (IA) passe tel quel |
| `core/assistant/.../AssistantController.kt` (IA seulement) | `announce` : `speech.speak(text, flush = false)` (canal GUIDANCE par défaut) | Dans le commit de fusion, passer à `speech.speak(text, priority = SpeechPriority.LOW, channel = VoiceChannel.ASSISTANT)`. Ainsi l'arrêt et la sourdine de la session n'effacent plus ses messages (audit §6). `followGuidanceSpeech` et `RESUME_AFTER_GUIDANCE_MS` restent tels quels |
| `corenav/GuidanceActions.kt` (IA seulement) | `start(…, destinationName)` mémorise le nom mais appelle `session.start(option, preferences, vehicle)` | Dans le commit de fusion : `session.start(option, preferences, vehicle, destinationName ?: this.destinationName)`. Sinon `lastGuidance.destinationName` (B6, D5, E3) est perdu. Ensuite, `HomeViewModel.resumeLastGuidance` peut déléguer à `guidance.startTo(Place(…))`, qui fait le même calcul |
| `nav/NavigationViewModel.kt` | **IA :** constructeur, `stop`, `loadAlternatives`, `addStop`, `reportsAhead`, imports. **E :** import ligne 14, bloc 97-118 (code C, absent de l'IA), insertion après la ligne 130 | Pas de chevauchement de lignes. Les imports se résolvent par union |
| `nav/NavigationScreen.kt` | **IA :** signatures (lignes 105/129 actuelles), appels 196/198, emplacements assistant dans les overlays. **E :** import 99 et `derivedStateOf`, 152-157, après 170, 183, 186-187, `MapTick`, `GuidanceMapEffects` | Aucune ligne touchée par les deux. Les lignes E sont dans `NavigationContent`, avant les appels des overlays (188-195 inchangées entre les deux) |
| `home/HomeScreen.kt` | **IA :** paramètres après la ligne 115, `assistantButton()` dans la colonne d'actions (après 164), `assistantPanel()` et `Spacer` (après 196). **E :** après la ligne 119, ligne 215, `BottomPanel` 261-269 | Pas de chevauchement : les lignes 116-119 et 197-214 sont inchangées entre les deux. La signature de `HomeScreen` n'est pas touchée par E |
| `set/SettingsScreen.kt` | **IA :** imports (vers 80), `AssistantGroup()` après la ligne `VoiceGroup` (150), aides `private` → `internal` (167-196). **E :** imports 44/58/110 retirés, 146-148, 293-316, 629-635 retirés | Pas de chevauchement : les lignes 149-150 sont inchangées. `NavigationGroup` vit dans son propre fichier |
| `feature/settings/.../strings.xml` | **IA :** 18 chaînes en fin de fichier. **E :** lignes 27-28 et insertion après 32 | Pas de chevauchement |
| `home/HomeNavigation.kt`, `app/RoadlyNavHost.kt`, `feature/route/RouteViewModel.kt` | IA seulement | Rien à résoudre : E n'y touche pas (écart 13) |
| `UserPreferencesRepository`, `FakeUserPreferencesRepository`, `core:model`, `core:map` | E seulement | Aucune implémentation IA de ces interfaces (vérifié) |

**Après fusion :**
- `speaking` couvre aussi le canal ASSISTANT. Un message de l'assistant dit par le moteur met donc en pause sa voix live (`PAUSED_FOR_GUIDANCE`). C'est voulu : jamais deux voix à la fois.
- La voix live (Gemini, `USAGE_ASSISTANT`, `GAIN_TRANSIENT`) reste hors du moteur. Elle cède la parole par `speaking`, comme prévu par l'IA.

## LIMITES documentées par cette étape

- **Préemption.** `TextToSpeech` ne reprend pas au milieu d'une phrase : un message de l'assistant coupé par le guidage est redit depuis le début. Après un NOW, il est jeté avec sa file.
- **Péremption.** Un message de l'assistant qui attend plus de 8 s (longue chaîne d'annonces) n'est pas dit. Pour un message coupé, l'âge part de la coupure.
- **Libération différée.** Le moteur reste vivant au plus 10 s après la fin de course si l'assistant parle.
- **Coordinateur.** `AndroidSpeechService` n'est pas testé sur JVM. Ses règles le sont (`VoiceArbiterTest`) ; le reste est vérifié sur appareil par les traces `RoadlySpeech`.
- **Bip.** Le bip de dépassement et de zone reste sur `STREAM_MUSIC`, hors focus (défaut de l'audit, hors périmètre E).
- **Voix live de Roadly AI.** Elle n'existe pas sur cette branche. Sa coupure par un NOW se vérifie après fusion.
- **Inclinaison.** 60° au plus (MapLibre Android 11.13.5), alors que la spécification demandait 65°. L'inclinaison basse supprime aussi les 60° « ville dense ».
- **Zoom.** « Plus proche » ne resserre pas l'approche (flèche entière, `3c02729`).
- **Vue au départ.** Tant qu'elle n'a jamais été choisie, elle suit l'ancien « Carte 3D » (`map.3d`). Les Réglages la figent au premier changement des bâtiments.
- **Reprise.** Elle a quatre limites :
  - pas de demande de permission de notification sur ce chemin ;
  - pas d'arrêts intermédiaires (non mémorisés) ;
  - préférences et véhicule du moment ;
  - aucune limite d'âge pour la course mémorisée.
- **Service.** En `START_NOT_STICKY`, après la mort du processus il n'y a ni service ni notification : la reprise est manuelle, par la carte d'accueil.
- **Écran verrouillé.** Vérifié à l'arrêt (service, notification, localisation, processus). Les annonces prononcées écran verrouillé sont à faire en roulant.
- **Réglages absents.** Le choix de la voix et le débit de synthèse (audit §7) ne font pas partie de E2.

---

## Auto-revue

**1. Couverture de la spécification.**

| Exigence (`ROADLY_3D_NAVIGATION_PLAN.md` §E, audit §6 / §7, décisions) | Tâche | Preuve |
|---|---|---|
| E1 canal unique de parole, priorités urgence > guidage > assistant | 1, 2 | `VoiceArbiter.arbitrate` (table testée), `SpeechQueue` (guidage d'abord), `AndroidSpeechService` |
| E1 pas de chevauchement ; l'assistant est coupé (pas seulement mis en pause) par un NOW | 1 | `scenario 17 - a now cuts the assistant and drops its queue…` |
| E1 guidage NORMAL / LOW avant l'assistant, reprise 300 ms | 1, 2 | `a near cuts the assistant…`, `ASSISTANT_RESUME_GAP_MS`, `readyAtMs` → `drainTask` |
| E1 l'assistant ne coupe jamais le guidage ; péremption de 8 s pour l'assistant | 1 | `the assistant never cuts the guidance…`, `an assistant message waiting more than 8 s is dropped` |
| E1 atténuation des médias (`MAY_DUCK`, attributs navigation) ; focus rendu après la dernière annonce, sur le fil principal | 2, 9 | `focusRequest` inchangé, `settle()` posté sur `main` ; tâche 9, étape 2 (`dumpsys audio`) |
| E1 `stop` / `setMuted` / `release` de la session n'effacent plus les messages de l'assistant | 2, 3 | `stop(VoiceChannel.GUIDANCE)`, `setMuted(…, GUIDANCE)`, `release()` différée ; `NavigationSessionVoiceTest` (3 tests) ; `clearing the guidance keeps the assistant messages…` |
| E1 contrat IA : `speaking` fidèle (file comprise), `speak(text, flush)` | 2 | `refreshSpeaking()` (en cours ou en file) ; interface (mêmes nom et type) ; surface de fusion |
| E1 tests avec `FakeSpeechService` (avec `channels`) ; essai sur appareil avec musique | 2, 3, 9 | `channels`, `stoppedChannels`, `mutedChannels` ; tâche 9, étape 2 |
| Scénario 16 (voix coupée) | 3, 9 | `scenario 16 - voice off silences the guidance channel…` ; tâche 9, étape 2.4 |
| Scénario 17 | 1, 3, 9 | `VoiceArbiterTest` ; « Tester la voix » en ASSISTANT LOW (`SettingsViewModelTest`) ; tâche 9, étape 2.3 |
| E2 vue par défaut 2D / 3D | 4, 6, 7 | `defaultCameraMode` (DataStore, migration) ; `cameraMode` = `CameraMode3D.initial` ; « Vue au départ » |
| E2 zoom −1 / 0 / +1 | 4, 5, 6, 7 | `zoomOffset` (bornes testées) ; `GuidanceCameraPolicy` (test) ; `MapTick` → `NavigationCameraInput` |
| E2 inclinaison (45 / 58 / 60, écart 9) | 4, 5, 7 | `PitchPreset` ; tests de la politique (croisière, approche 50°, économie 45°, ville dense) |
| E2 rotation auto ou nord en haut | 4, 6, 7 | `autoRotate` → `NORTH_UP` au départ (test VM) |
| E2 détail cartographique | 7 | déplacé dans « Navigation » (`ChoiceRow` `MapDetail`) ; `mapDetail` gardé |
| E2 bâtiments 3D | 4, 5, 6, 7 | `map.3d` = « Bâtiments 3D » (écart 8) ; `buildings3d` → `setBuildings3d` et `buildingsVisible` (test du contrôleur) ; `setMap3d` fige la vue (test) |
| E2 aperçu de la manœuvre suivante | 4, 6, 7 | `showNextManeuverPreview` → `withNextManeuverPreview` (test), ligne 187 |
| E2 économie d'énergie manuelle | 4, 6, 7, 9 | `PowerSaveMode.effective` (test) ; `derivedStateOf` dans l'écran ; tâche 9, étape 3 (environ 30 i/s, économiseur coupé) |
| E2 tout est persisté (DataStore), groupe « Navigation » dans Réglages | 4, 7, 9 | tests DataStore ; `NavigationGroup` ; tâche 9, étape 3.2 (`force-stop`, 6 clés) |
| E2 `StateFlow` séparées, `combine` de `uiState` intact | 6 | `zoomOffset`, `pitchPreset`, `showNextManeuverPreview`, `powerSaveMode` ; `uiState` non modifié |
| E3 reprise après `am kill` (B6) : carte, reprise par un nouvel itinéraire, « Ignorer » | 8, 9 | `resumableGuidance`, `forgetLastGuidance` (2 tests) ; `HomeViewModelTest` (4 tests) ; tâche 9, étape 4 |
| E3 écran verrouillé 10 min, notification vivante, retour depuis la notification, rotation | 9 | tâche 9, étape 5 (`dumpsys` service, notification, localisation ; annonces : à faire en roulant) |
| E3 `START_NOT_STICKY` gardé | — | écart 14 ; LIMITES |
| Scénarios 14, 16, 17, 20 consignés | 9 | matrice et journal (tâche 9, étape 6) |

**2. Recherche de trous.**
- Aucun « TBD », « TODO », « à compléter » ni « comme la tâche N ».
- Chaque étape de code donne le code complet, ou un bloc ancien / nouveau ancré sur `8907dd8`.
- La tâche 2 n'a pas de test JVM. C'est expliqué (écart 1) : sa logique est testée à la tâche 1, et le double de test est exercé à la tâche 3.
- Les valeurs inconnues à l'écriture sont des observations d'appareil (tâche 9) : empreinte, nombres, écart en ms, i/s. Chacune a sa commande.
- Calculs de test vérifiés :
  - zoom de croisière à 50 km/h = 17,0 (±1 → 18,0 / 16,0) ;
  - 2D = 17,0 + 1 ;
  - approche 17,5 (−1 → 16,5) ;
  - 20 km/h = 17,5 − 1 = 16,5 ;
  - 58° sans bâtiments à 20 km/h, 60° avec.

**3. Cohérence des noms entre tâches.**
- `VoiceChannel { GUIDANCE, ASSISTANT }` : tâche 1, utilisé aux tâches 2, 3 et 9 (traces).
- `VoiceArbiter.arbitrate`, `ASSISTANT_RESUME_GAP_MS`, `RELEASE_DEFER_MAX_MS`, `Arbitration(interrupt, requeueInterrupted, cleared)` : tâche 1, utilisés à la tâche 2.
- `SpeechQueue.Item(…, channel)`, `offer(item, flush, active)`, `poll`, `readyAtMs`, `onFinished`, `hasAssistantWork`, `clear(channel)` : tâche 1, utilisés à la tâche 2.
- `SpeechService.speaking`, `speak(…, priority, channel)`, `setMuted(muted, channel)`, `stop(channel)` : tâche 2, utilisés aux tâches 3 et 8.
- `FakeSpeechService.channels`, `stoppedChannels`, `mutedChannels`, `muted`, `flushes`, `priorities`, `setSpeaking` : tâche 2, utilisés à la tâche 3.
- `CameraMode3D` (`fr.roadly.core.model`) `.initial` / `.default` / `.toggled3d`, `PitchPreset.degrees`, `PowerSaveMode.effective` : tâche 4, utilisés aux tâches 5, 6 et 7.
- `GuidanceDefaults.DEFAULT_CAMERA_MODE`, `ZOOM_OFFSET`, `ALLOWED_ZOOM_OFFSETS`, `PITCH_PRESET`, `AUTO_ROTATE`, `SHOW_NEXT_MANEUVER`, `POWER_SAVE` : tâche 4, utilisés aux tâches 6 et 7.
- `defaultCameraMode`, `zoomOffset`, `pitchPreset`, `autoRotate`, `showNextManeuverPreview`, `powerSaveMode` et leurs `set…` : tâche 4, utilisés aux tâches 6 et 7.
- Doubles de test `cameraDefault`, `zoom`, `pitch`, `rotate`, `thenPreview`, `powerSave` : tâche 4, utilisés aux tâches 6 et 7.
- `GuidanceCameraInput.zoomOffset` / `.pitchPreset` et `NavigationCameraInput.zoomOffset` / `.pitchPreset` / `.buildings3d` : tâche 5, utilisés à la tâche 6.
- `NavigationViewModel.zoomOffset`, `pitchPreset`, `showNextManeuverPreview`, `powerSaveMode`, `map3d`, et `withNextManeuverPreview` : tâche 6.
- `NavigationSettings`, `SettingsViewModel.navigation` et ses setters, `NavigationGroup` : tâche 7.
- `NavigationSession.resumableGuidance` et `forgetLastGuidance` : tâche 8, utilisés par `HomeViewModel` dans la même tâche.
- `ResumePhase`, `ResumeCardUi`, `HomeViewModel.resumeCard`, `resumeLastGuidance`, `dismissResume`, `ResumeGuidanceCard` : tâche 8.
- Chaînes :
  - `settings_group_navigation`, `settings_nav_*` (tâche 7, après la ligne 32) ;
  - `settings_map_3d*` (textes changés) ;
  - `home_resume_*` (tâche 8, après la ligne 20).

### Fichiers critiques pour l'implémentation
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\navigation\src\main\java\fr\roadly\core\navigation\AndroidSpeechService.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\navigation\src\main\java\fr\roadly\core\navigation\SpeechQueue.kt` (et `VoiceArbiter.kt`, nouveau, à côté)
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\navigation\src\main\java\fr\roadly\core\navigation\NavigationSession.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\core\data\src\main\java\fr\roadly\core\data\repository\UserPreferencesRepository.kt`
- `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-3d\feature\navigation\src\main\java\fr\roadly\feature\navigation\NavigationScreen.kt`
- Références de fusion (lecture seule) :
  - `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-ai\core\assistant\src\main\java\fr\roadly\core\assistant\AssistantController.kt`
  - `C:\Users\Forza PC\Documents\Yannis\Projet Yannis\GPS-roadly-ai\core\navigation\src\main\java\fr\roadly\core\navigation\GuidanceActions.kt`
