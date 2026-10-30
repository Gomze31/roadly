# Roadly — Spécification Phase 2 : guidage GPS en temps réel

Date : 2026-10-30. Statut : périmètre validé par l'utilisateur (« Oui, Phase 2 »).
Base : v1 sur `feat/roadly-v1` (HEAD `4de82ff`), spec v1 `2026-10-30-roadly-v1-design.md`, référentiel `docs/SILLAXE-ANALYSE.md`, statuts `FEATURES.md`.

## 1. Objectif

Transformer la comparaison d'itinéraires de la v1 en un guidage réel : suivi GPS continu, instructions vocales françaises, bandeau de manœuvre, bandeau « à venir », recalcul automatique, service de premier plan, mode conduite. Fonctions équivalentes à Sillaxe : guidage + voies + ronds-points, Horizon (partie manœuvres/virages), compteur + limitation (quand disponible), temps/distance restants, heure d'arrivée, changement de destination en guidage, Voix et sons, préférences d'itinéraire.

Hors périmètre Phase 2 : trafic, zones de vigilance, services dans le bandeau, multi-arrêts (Phase 3/4) ; Android Auto (Phase 5). Chaque élément absent reste marqué « Bientôt » ou n'apparaît pas.

## 2. Décisions

| Sujet | Décision |
|---|---|
| Limitation de vitesse | Interface `SpeedLimitProvider` ; impl `ValhallaTraceSpeedLimitProvider` (POST `/trace_attributes`, `shape` des derniers fixes, `filters.attributes = ["edge.speed_limit","edge.names","edge.road_class"]`) activée **uniquement** si l'URL Valhalla n'est pas celle de FOSSGIS par défaut (instance publique : résultat ignoré → « — »). Jamais de valeur devinée. |
| Voix | `TextToSpeech` Android, `Locale.FRANCE`, focus audio transitoire (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), file d'annonces, pas de voix serveur. |
| Service | `NavigationService` : foreground service type `location`, notification persistante (prochaine manœuvre, distance, ETA, action « Arrêter »), démarré au « Démarrer », arrêté à l'arrivée ou sur action. Permission `POST_NOTIFICATIONS` (API 33+) demandée au premier démarrage. |
| Suivi | `RouteTracker` pur Kotlin (JVM) : projection du fix sur la polyligne (segment le plus proche, index + distance le long), distance à la prochaine manœuvre, restant distance/durée (proportionnel à la durée du segment Valhalla restant), détection hors itinéraire (> 40 m du tracé pendant ≥ 5 fixes consécutifs ou > 100 m une fois), arrivée (< 25 m de la destination et dernière manœuvre). |
| Recalcul | Sur hors-itinéraire : nouvelle requête `RoutingProvider.route(origin = fix, destination, costing courant, préférences)` ; pas plus d'une toutes les 10 s ; annonce « Recalcul de l'itinéraire » ; en échec réseau, le guidage continue sur l'ancien tracé et réessaie. |
| Annonces | Anticipées à 3 distances dépendant de la vitesse : loin (≥ 2 km autoroute / 600 m route / 300 m ville), proche (300 m / 150 m / 80 m), « maintenant » (< 40 m). Texte = `verbal_pre_transition_instruction` / `verbal_transition_alert_instruction` Valhalla (déjà en français) ; anti-répétition par (manœuvre, palier). Vitesse de référence = vitesse GPS lissée. |
| Bandeau « à venir » | Les 3 prochaines manœuvres avec icône, distance, nom de rue ; ronds-points avec numéro de sortie ; voies (`lanes` Valhalla) sous la manœuvre courante si présentes. Pas de trafic ni services (Phase 3). |
| Mode conduite | `LocalDrivingMode = true` sur l'écran de navigation : typographie ×1,3, cibles 56 dp, écran maintenu allumé (`FLAG_KEEP_SCREEN_ON`), aucune animation en boucle, thème sombre forcé si `AUTO_SOLAR` et nuit (inchangé). |
| Préférences d'itinéraire | Éviter péages / autoroutes / ferries → `costing_options.auto.use_tolls / use_highways / use_ferry = 0`. Stockées DataStore, appliquées aux 3 variantes et au recalcul. |
| Alerte de dépassement | Son court + badge rouge si vitesse > limitation + tolérance (réglable 0/5/10 km/h), uniquement si limitation connue. |
| Tunnels / perte GPS | Si aucun fix depuis > 3 s en guidage, extrapolation le long du tracé à la dernière vitesse connue, badge « GPS perdu » après 10 s. |

## 3. Modules

```
core:navigation (nouveau, Android)   RouteTracker (JVM-testable), GuidanceEngine, AnnouncementPolicy, SpeechService,
                                     NavigationService, NavigationSession (StateFlow<NavigationState>), SpeedLimitProvider
feature:navigation (nouveau)         NavigationScreen (mode conduite), ManeuverBanner, UpcomingStrip, DrivingHud,
                                     NavigationRoute(routeId) — l'itinéraire choisi est transmis via NavigationSession, pas par argument
core:routing (modif)                 RouteRequest.preferences(avoidTolls, avoidHighways, avoidFerries) ; Maneuver gagne
                                     verbalPre, verbalAlert, lanes: List<Lane>, exitNumber?, roundaboutExit déjà présent
core:model (modif)                   Lane(directions: Set<LaneDirection>, valid: Boolean), NavigationState, RoutePreferences
core:data (modif)                    UserPreferencesRepository : routePreferences, voiceEnabled, voiceVolume (0–100),
                                     speedAlertToleranceKmh, speedAlertSound
feature:route (modif)                « Démarrer » actif → NavigationSession.start(routeOption) → navigate(NavigationRoute)
feature:settings (modif)             sections « Voix et sons », « Alertes de vitesse », « Préférences d'itinéraire »
app (modif)                          route NavigationRoute, permission POST_NOTIFICATIONS, retour depuis la notification
```

## 4. Contrats

```kotlin
data class RoutePreferences(val avoidTolls: Boolean = false, val avoidHighways: Boolean = false, val avoidFerries: Boolean = false)

data class Lane(val directions: Set<LaneDirection>, val valid: Boolean)
enum class LaneDirection { LEFT, SLIGHT_LEFT, STRAIGHT, SLIGHT_RIGHT, RIGHT, UTURN, MERGE_LEFT, MERGE_RIGHT }

data class RouteProgress(
    val routeId: String, val segmentIndex: Int, val distanceAlongM: Double, val offRouteM: Double,
    val nextManeuverIndex: Int, val distanceToNextManeuverM: Double,
    val remainingDistanceM: Double, val remainingDurationS: Double, val snapped: GeoPoint, val bearingDeg: Float?,
)

sealed interface NavigationState {
    data object Idle : NavigationState
    data class Guiding(val route: Route, val progress: RouteProgress, val speedKmh: Double?, val speedLimitKmh: Int?,
                       val upcoming: List<Maneuver>, val lanes: List<Lane>, val etaEpochMs: Long, val gpsLost: Boolean,
                       val rerouting: Boolean) : NavigationState
    data class Arrived(val route: Route) : NavigationState
    data class Failed(val error: AppError) : NavigationState
}

interface SpeedLimitProvider { suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> }

class RouteTracker(route: Route) { fun update(fix: LocationFix): RouteProgress; val isOffRoute: Boolean; val hasArrived: Boolean }

object AnnouncementPolicy { fun next(progress: RouteProgress, speedKmh: Double?, already: Set<Pair<Int, Tier>>): Announcement? }
enum class Tier { FAR, NEAR, NOW }

@Singleton class NavigationSession { val state: StateFlow<NavigationState>; fun start(option: RouteOption, prefs: RoutePreferences); fun stop(); fun setMuted(Boolean) }
```

## 5. Écran de navigation

- Carte plein écran en mode suivi (zoom 17, cap, tilt 0), tracé actif cyan, tracé parcouru atténué.
- Haut : **bandeau de manœuvre** (verre, 96 dp) : icône de manœuvre 40 dp, distance en `NavDigits` (p. ex. « 350 m »), instruction + nom de rue, numéro de sortie de rond-point en pastille ; voies en dessous quand connues (flèches, voie valide en accent).
- Bas : **bandeau « à venir »** : rangée horizontale de 3 cartes (icône, distance, rue) puis **panneau ETA** : heure d'arrivée, restant (« 12 min · 8,4 km »), bouton « Arrêter » (56 dp), bouton mute, bouton recentrage si l'utilisateur a bougé la carte (retour au suivi après 8 s).
- Gauche bas : `SpeedBadge` (vitesse, limitation ou « — », rouge si dépassement).
- États : « Recalcul… » (bandeau), « GPS perdu » (badge), Arrivée (feuille : « Vous êtes arrivé », bouton Terminer), Échec (message + Réessayer / Arrêter).
- Interactions minimales : aucune saisie texte ; tout bouton ≥ 56 dp.

## 6. Réglages ajoutés

Voix et sons : voix activée, volume des annonces (0–100, appliqué via `TextToSpeech` `KEY_PARAM_VOLUME`), son de dépassement, bouton « Tester la voix ». Alertes de vitesse : tolérance 0/5/10 km/h. Préférences d'itinéraire : éviter péages / autoroutes / ferries. Chaque réglage modifie réellement le comportement (vérifié par test unitaire du ViewModel et par le moteur).

## 7. Tests

- JVM : `RouteTracker` (projection, index de manœuvre, hors-route, arrivée, extrapolation) sur la fixture Valhalla réelle (Paris) ; `AnnouncementPolicy` (paliers, anti-répétition) ; mapping `lanes` Valhalla ; `RoutePreferences` → body Valhalla ; `NavigationViewModel` avec fakes (Turbine).
- Instrumentés : `NavigationService` démarre/arrête, notification présente (Honor Magic 8 Pro via ADB).
- Manuel sur téléphone : trajet réel ou simulation `adb emu`/mock location (si activé par l'utilisateur) ; vérifier annonces, recalcul, arrivée.

## 8. Contraintes

Spec v1 §Global : package `fr.roadly`, pas de clé, pas de données inventées, strings FR en ressources, fair-use (recalcul ≤ 1/10 s, `trace_attributes` ≤ 1/5 s et seulement sur instance auto-hébergée), batterie (1 Hz en guidage, arrêt du service à l'arrivée), permissions : pas de localisation en arrière-plan sans guidage actif (service premier plan `location` uniquement pendant le guidage).
