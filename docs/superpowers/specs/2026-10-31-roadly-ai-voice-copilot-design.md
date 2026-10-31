# Roadly AI — copilote vocal (lot AI-1) — design

Date : 2026-10-31 · Statut : validé en brainstorming, en attente de relecture.

## 1. Contexte et décisions

Roadly est une application GPS Android à usage **strictement personnel** (HONOR Magic 8 Pro), non distribuée.
Roadly AI est un copilote vocal conversationnel qui **exécute** les fonctionnalités de Roadly via des outils
(function calling), et pas seulement un chatbot.

Décisions prises :

| Sujet | Décision |
|---|---|
| Moteur | Gemini Live API, modèle `gemini-3.8-live` (stable, sept. 2026), audio natif bidirectionnel |
| Transport | WebSocket brut (OkHttp) vers `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=…` ; pas de SDK, pas de Firebase, pas d'App Check, pas de backend |
| Clé API | `GEMINI_API_KEY` dans `local.properties` (ignoré par Git, vérifié) → `BuildConfig` ; remplaçable sans recompiler depuis Réglages (stockée chiffrée) |
| Fournisseurs | Contrat neutre `VoiceAssistantProvider` ; OpenAI ajouté plus tard (lot AI-2) sans toucher aux outils ni à l'UI |
| Session | Activation manuelle (tap) ; auto-fermeture après 15 s sans parole ; contexte repris pendant 2 h |
| Indépendance | Aucun module GPS ne dépend de l'assistant ; sans clé, sans réseau ou IA en panne, le guidage est inchangé |

Découpage du module complet :

- **AI-1 (ce document)** : cœur, Gemini Live, outils, priorité audio GPS, bouton micro, fenêtre compacte, réglages.
- **AI-2** : fournisseur OpenAI Realtime + sélection du fournisseur dans les réglages.
- **AI-3** : mot d'activation facultatif « Salut Roadly » (détecteur embarqué).
- **AI-4** : écran dédié Roadly AI, historique des conversations, suggestions contextuelles.

Faits de l'API (doc ai.google.dev, oct. 2026) utilisés par ce design : entrée PCM 16 bits LE 16 kHz mono
(`audio/pcm;rate=16000`), sortie PCM 16 bits LE 24 kHz ; transcription via `inputAudioTranscription` /
`outputAudioTranscription` ; VAD serveur `realtimeInputConfig.automaticActivityDetection` et barge-in
`serverContent.interrupted` ; `sessionResumption` (handle valable 2 h) ; `goAway` avant la coupure (~10 min) ;
session audio ≤ 15 min ; langue imposée par l'instruction système (pas de `languageCode` sur l'audio natif) ;
`behavior: "BLOCKING"` explicite sur chaque fonction (le défaut de 3.8 est `NON_BLOCKING`).

## 2. Architecture

### 2.1 Modules

```
core:assistant   (nouveau, sans UI)
  ├─ provider/   VoiceAssistantProvider, VoiceSession, AssistantEvent, SessionConfig
  ├─ gemini/     GeminiLiveProvider, GeminiLiveProtocol (messages @Serializable)
  ├─ tools/      AssistantTool, ToolSchema, ToolRegistry, ToolResult, PendingActions, outils concrets
  ├─ audio/      MicCapture, VoicePlayback, AssistantAudioFocus
  ├─ key/        ApiKeyProvider, KeystoreCipher
  ├─ usage/      UsageMeter (tokens + minutes par jour, plafond)
  └─ AssistantController (@Singleton, machine à états)
feature:assistant (nouveau)
  └─ AssistantMicButton, AssistantOverlay, AssistantViewModel
feature:settings  section « Roadly AI »
core:navigation   + GuidanceActions, + SpeechService.speaking
core:common       + AppNavigator
app               RoadlyNavHost écoute AppNavigator ; RECORD_AUDIO au manifeste
```

Dépendances : `core:assistant` → `core:navigation`, `core:geocoding`, `core:routing`, `core:data`,
`core:location`, `core:common`. Aucune dépendance inverse.

### 2.2 Contrat fournisseur

```kotlin
interface VoiceAssistantProvider {
    val id: String
    suspend fun connect(config: SessionConfig): Outcome<VoiceSession>
}

data class SessionConfig(
    val systemInstruction: String,
    val tools: List<ToolSchema>,
    val voice: String,                 // défaut "Kore"
    val resumptionHandle: String?,
)

interface VoiceSession {
    val events: Flow<AssistantEvent>
    fun sendAudio(pcm16k: ByteArray)
    fun endAudioStream()               // audioStreamEnd : pause micro (annonce GPS, silence)
    fun sendText(text: String)         // clientContent : informe le modèle d'une action faite par une puce Confirmer/Annuler
    fun sendToolResults(results: List<ToolResult>)
    fun close()
}

sealed interface AssistantEvent {
    data class UserTranscript(val text: String) : AssistantEvent          // incrémental
    data class AssistantTranscript(val text: String) : AssistantEvent     // incrémental
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

enum class AssistantError { NO_KEY, INVALID_KEY, QUOTA, NETWORK, TIMEOUT, PROTOCOL }
```

`GeminiLiveProvider` traduit `ToolSchema` en `functionDeclarations` (avec `behavior: "BLOCKING"`), envoie
`setup`, attend `setupComplete` (timeout 8 s), encode l'audio en base64, mappe les messages serveur ci-dessus.
Codes de fermeture : 1008 / message contenant `API key` → `INVALID_KEY` ; `RESOURCE_EXHAUSTED` / 429 → `QUOTA`.

### 2.3 Outils

```kotlin
interface AssistantTool {
    val schema: ToolSchema                       // nom, description FR, paramètres (JSON Schema neutre)
    val confirmation: Confirmation               // NONE | REQUIRED
    val needsLocationConsent: Boolean
    suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult
}
```

`ToolContext` : consentement courant, transcription utilisateur depuis le début du tour, horloge.
`ToolRegistry` : résout le nom, valide les arguments contre le schéma, applique le consentement, le plafond de
**8 appels par tour**, le protocole de confirmation, et transforme toute exception en `ToolResult.error`. Les
résultats sont des objets JSON courts et factuels (`status`, données, `unavailable_reason`).

### 2.4 Refactor ciblé (comportement inchangé)

- **`GuidanceActions`** (@Singleton, `core:navigation`) : `startTo(place)`, `addStop(place)`, `removeStop(id)`,
  `stops: StateFlow<List<GuidanceStop>>`, `alternativesFromHere()`, `select(option)`, `recalculate()`,
  `applyPreferences(prefs)`. La logique de `NavigationViewModel.addStop` / `switchTo` y est déplacée ;
  `RouteViewModel.startGuidance` et `NavigationViewModel` délèguent. Les arrêts portent leur abscisse le long du
  tracé ; un arrêt dépassé (abscisse < progression) est retiré de la liste.
- **`SpeechService.speaking: StateFlow<Boolean>`** : vrai tant que l'ensemble `active` d'`AndroidSpeechService`
  n'est pas vide ; `FakeSpeechService` l'expose aussi.
- **`AppNavigator`** (@Singleton, `core:common`) : `SharedFlow<NavRequest>` (`OpenSearch(category)`,
  `OpenGuidance`, `OpenSettings`) collecté dans `RoadlyNavHost` (même chemin que `openNavigationRequested`).

## 3. Outils d'AI-1

« Indisponible » = l'outil renvoie `status: "unavailable"` et une raison ; jamais de donnée inventée.

| Outil | Implémentation | Confirmation |
|---|---|---|
| `startNavigation(destination)` | Résolution : favoris (Domicile, Travail) → récents → `CompositeGeocoder.search(q, position)`. Plusieurs candidats proches en score → renvoie la liste (≤ 3) pour que l'IA demande. Puis `GuidanceActions.startTo` (option la plus rapide, préférences et véhicule courants) + `AppNavigator.OpenGuidance` | oui |
| `changeDestination(destination)` | Idem, remplace la course en cours | oui |
| `stopNavigation()` | `NavigationSession.stop()` | oui |
| `recalculateRoute()` | `GuidanceActions.recalculate()` depuis la position, même costing | non |
| `getNavigationStatus()` | Lecture de `NavigationState` : état, destination, km et min restants, ETA, prochaine instruction, recalcul en cours, GPS perdu, zones et virages à venir, pluie, signalements à venir | non |
| `getNextInstruction()` | `route.maneuvers[nextManeuverIndex]` + distance | non |
| `getDistanceRemaining()`, `getArrivalTime()` | `RouteProgress` / `etaEpochMs` | non |
| `avoidTolls(enabled)`, `avoidHighways(enabled)` | `prefs.setRoutePreferences` ; en guidage : `applyPreferences` → recalcul, application, renvoie durée et arrivée avant/après | non |
| `findAlternativeRoutes()` | `GuidanceActions.alternativesFromHere()` → options avec id, durée, arrivée, distance, péage | non |
| `selectRoute(routeId)` | `GuidanceActions.select` | non |
| `setArrivalTime(time, destination?)` | En guidage : compare l'ETA à l'heure voulue. Hors guidage : heure de départ = arrivée − durée la plus rapide. Mentionne l'absence de trafic prédictif | non |
| `addStop(destination)` | Résolution comme `startNavigation`, puis `GuidanceActions.addStop` (≤ 10), renvoie le surcoût en minutes | oui |
| `removeStop(stopId)`, `getStops()` | `GuidanceActions` | `removeStop` : oui |
| `showGasStations()`, `showParking()`, `showChargingStations()`, `showNearbyServices()` | Hors guidage : `AppNavigator.OpenSearch(category)`. En guidage : renvoie les 5 plus proches (`FuelStationProvider` / `NearbyPoiProvider`) et les affiche dans la fenêtre compacte, sans quitter le guidage | non |
| `findCheapestFuelOnRoute(fuel?)` | Points tous les 10 km sur les 50 km restants → `FuelStationProvider.nearby(r = 3 km)`, dédoublonnage, tri prix ; pour les 3 moins chères, détour réel = durée via station − durée actuelle (Valhalla). Proposition d'ajout d'arrêt (→ `addStop`, confirmation) | non |
| `getFuelPrices(fuel?)` | `FuelStationProvider` autour de la position, carburant du profil par défaut, avec date du prix | non |
| `getEstimatedTripCost()` | `FuelCostCalculator` sur la distance restante ; péages « non estimés » | non |
| `reportIncident(type)` | `CommunityRepository.report` à la position et au cap courants si configuré ; sinon indisponible | non |
| `confirmIncident(id)` | `CommunityRepository.vote(id, true)` (ids issus de `getNavigationStatus`) | non |
| `getVehicleProfile()` | `VehicleProfileRepository.profile` | non |
| `changeVehicleProfile(type, fuel?)` | Modifie le profil unique (type, carburant) | oui |
| `showTraffic()` | Indisponible : aucune source de trafic dans Roadly | — |
| `confirmAction(actionId)`, `cancelAction(actionId)` | Protocole de confirmation (§4) | — |
| `endConversation()` | Ferme la session après la réponse en cours | non |

## 4. Sécurité

- **Confirmation vérifiée par l'app.** Un outil `REQUIRED` ne s'exécute pas : il enregistre une action en attente
  (`PendingActions`, expiration 30 s) et renvoie `needs_confirmation` + `actionId` + résumé. `confirmAction`
  n'exécute que si la transcription utilisateur reçue **après** la création contient un accord (`oui`, `ouais`,
  `d'accord`, `vas-y`, `ok`, `confirme`, `c'est ça`) et aucune négation (`non`, `annule`, `pas`). Les puces
  Confirmer / Annuler de la fenêtre compacte exécutent ou annulent directement.
- **Périmètre fermé.** Aucun outil pour les alertes de vitesse, les zones de vigilance, la voix GPS (volume,
  sourdine), les permissions, la clé ou les réglages de l'assistant.
- **Pas d'invention.** Instruction système : toute position, durée, distance, prix ou événement provient d'un
  outil ; en cas d'`unavailable`, le dire. Réponses courtes (conduite).
- **Données minimales.** Le prompt ne contient que des règles et « en guidage : oui/non ». Position, destination,
  itinéraire, véhicule ne sortent que dans les résultats d'outils demandés. Consentement « Partager ma position et
  mon trajet avec Gemini » (Réglages, demandé au premier usage) ; sans lui, les outils concernés renvoient
  `consent_required`.
- **Robustesse modèle.** Outil inconnu, arguments invalides ou hors bornes → erreur structurée renvoyée au modèle ;
  plafond de 8 appels par tour ; `toolCallCancellation` annule les actions en attente correspondantes.

## 5. Audio et priorité GPS

- **Capture** : `AudioRecord` `VOICE_COMMUNICATION`, 16 kHz mono PCM16, trames de 40 ms ; `AcousticEchoCanceler`
  et `NoiseSuppressor` si disponibles. Niveau RMS exposé pour l'animation.
- **Lecture** : `AudioTrack` 24 kHz mono PCM16, `USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`, mode stream ; RMS exposé.
- **Focus** : `AUDIOFOCUS_GAIN_TRANSIENT` pendant la session (la musique se met en pause). Perte transitoire (appel)
  → pause ; perte définitive → fermeture.
- **Priorité GPS** : `AssistantController` observe `SpeechService.speaking`. À `true` : `AudioTrack.pause()` (file
  conservée), micro non transmis + `endAudioStream()`. Retour à `false` + 300 ms : reprise. Le TTS GPS et son focus
  `MAY_DUCK` ne changent pas.
- **Barge-in** : `Interrupted` → `AudioTrack.pause()+flush()`, file vidée.
- **Écho** : risque d'auto-interruption à vérifier sur l'appareil ; si constaté, seuil d'énergie micro pendant que
  l'IA parle.
- Pas de SCO Bluetooth : sortie sur la route média courante, micro du téléphone.

## 6. Cycle de vie

États : `Idle → Connecting → Listening ⇄ Thinking ⇄ Speaking → Idle`, plus `PausedForGuidance` et `Error`.

1. Tap micro : clé présente, assistant activé, plafond non atteint, `RECORD_AUDIO` accordée (demandée au premier
   usage), consentement (demandé au premier usage) — sinon message et lien Réglages.
2. `connect` avec le handle de reprise s'il a moins de 2 h ; bip court d'écoute.
3. Fermeture : 15 s sans parole ni réponse ; `endConversation` ; application en arrière-plan (`ON_STOP`).
4. `GoAway` ou coupure : une reconnexion avec le handle ; échec → fermeture annoncée.

## 7. Erreurs, quotas, coûts

- Messages vocaux courts **en file** sur `SpeechService` (jamais `flush`) + texte dans la fenêtre compacte :
  réseau/timeout (8 s) « Roadly AI est indisponible, le guidage continue. » ; `INVALID_KEY` « Clé API refusée,
  vérifiez-la dans les réglages. » ; `QUOTA` « Quota Gemini atteint pour le moment. ».
- `UsageMeter` (DataStore) : tokens (`usageMetadata`) et minutes de micro par jour ; plafond quotidien en minutes
  (30 par défaut, réglable) au-delà duquel la session ne s'ouvre pas.

## 8. Interface

- **Bouton micro** (`RoundIconButton` couleur marque) dans la colonne d'actions de droite de l'accueil et du
  guidage (portrait et paysage). États : repos, connexion (anneau tournant), écoute (pulsation selon RMS micro),
  réponse (barres selon RMS lecture), pause GPS (atténué).
- **Fenêtre compacte** : carte verre au-dessus du bloc du bas, **2 lignes maximum** en mode conduite (dernière
  phrase utilisateur, dernière phrase de l'IA, en direct) ; puces Confirmer / Annuler ; carte de résultat (≤ 3
  lignes, ex. stations avec prix et détour, tap = ajouter l'arrêt) ; disparaît 4 s après la fin.
- **Réglages « Roadly AI »** : activer ; clé API (masquée, « Vérifier la clé » = connexion, attente de `setupComplete`, fermeture, sans micro) ;
  consentement ; plafond quotidien ; usage du jour.
- **Clé** : saisie chiffrée AES-GCM avec une clé Android Keystore, stockée dans DataStore ; `ApiKeyProvider` =
  saisie sinon `BuildConfig.GEMINI_API_KEY` (vide si absente de `local.properties`).

## 9. Tests

JVM (JUnit4, coroutines-test, turbine, fakes à la manière du projet) :

- `GeminiLiveProtocolTest` : JSON de `setup` (outils, transcription, VAD, reprise) ; parsing `toolCall`,
  `toolCallCancellation`, `interrupted`, transcriptions, `goAway`, `sessionResumptionUpdate`, `usageMetadata`,
  message malformé ignoré.
- `GeminiLiveProviderTest` (MockWebServer WebSocket) : handshake + `setupComplete` ; aller-retour d'outil ;
  fermeture clé invalide → `INVALID_KEY` ; timeout → `TIMEOUT`.
- `ToolRegistryTest` + un test par famille d'outils : statut depuis un `Guiding` réel ; `startNavigation` →
  `needs_confirmation` ; `confirmAction` refusé sans accord transcrit, accepté avec ; `avoidTolls` met à jour les
  préférences et recalcule ; `showTraffic` indisponible ; outil inconnu ; arguments invalides ; plafond 8 ;
  consentement absent ; signalement non configuré.
- `AssistantControllerTest` (fake provider, fake audio, `FakeSpeechService.speaking`, temps virtuel) : annonce GPS
  → pause lecture et micro puis reprise ; barge-in → file vidée ; auto-fermeture à 15 s ; erreur réseau → message
  en file sans `flush` ; plafond quotidien ; `GoAway` → reconnexion avec handle.
- `GuidanceActionsTest` ; tous les tests existants restent verts (`NavigationViewModelTest`, `RouteViewModel`).

Appareil (HONOR Magic 8 Pro) : les trois conversations d'exemple (station la moins chère sur le trajet, éviter les
péages, temps restant) ; interruption vocale ; annonce GPS pendant une réponse ; mode avion en session ; aucun
`FATAL EXCEPTION` ; `dumpsys batterystats` sur une session de 5 min.

## 10. Hors périmètre d'AI-1

OpenAI (AI-2), mot d'activation (AI-3), écran dédié / historique / suggestions (AI-4), micro en arrière-plan,
SCO Bluetooth, trafic temps réel, estimation des péages, plusieurs profils de véhicule.
