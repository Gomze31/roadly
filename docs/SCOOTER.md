# Mode trottinette électrique (LOT 13)

État au 2026-11-01. Ce document décrit l'audit du moteur, l'architecture, les données utilisées, ce qui est vérifié et ce qui ne l'est pas.

## 1. Audit du moteur de routage (avant LOT 13)

| Point | Constat | Conséquence |
|---|---|---|
| Moteur | Valhalla (`/route`), costings `auto` / `motorcycle` / `taxi` / `truck` choisis d'après `VehicleProfile.type` | Aucun profil non motorisé |
| Types de voie | Jamais demandés : la réponse `/route` ne dit pas si une portion est une piste, une bande ou une route | Impossible de mesurer la part de pistes ou de vérifier l'accès |
| Altitude | Non demandée | Pas de dénivelé |
| `trace_attributes` | Utilisé seulement pour la limitation de vitesse, jamais sur FOSSGIS (interrogation toutes les 5 s) | — |
| Navigation 3D | **Inexistante** : caméra `tilt(0.0)` partout, gestes d'inclinaison désactivés | La « navigation 3D premium » demandée est créée pour la trottinette (voir §6) ; la voiture garde sa caméra à plat |
| Roadly AI | **Spécification seulement** (`docs/superpowers/specs/2026-10-31-roadly-ai-voice-copilot-design.md`), aucun code | La partie trottinette est livrée sans le copilote Gemini (voir §8) |
| Vélo | Absent | Prévu dans `TravelMode.BICYCLE` (même costing), affiché « Bientôt » |

Données nécessaires au mode trottinette et sources retenues :

| Donnée | Source | Disponibilité |
|---|---|---|
| Accès cycles, pistes (`separated`), bandes (`dedicated`), escaliers, trottoirs, classe de route, revêtement, pente, limitation | Valhalla `/trace_attributes` (`shape_match: edge_walk` sur le tracé calculé, une requête par itinéraire) | FOSSGIS et Valhalla auto-hébergé |
| Altitude le long du tracé | Valhalla `/route` avec `elevation_interval: 30` | Si l'instance a les données d'altitude |
| Voie verte, agglomération (`maxspeed:type`, `source:maxspeed`, `zone:maxspeed`), éclairage (`lit`), restrictions EDPM (`small_electric_vehicle`), restrictions conditionnelles (`*:conditional`), revêtement détaillé (`surface`, `smoothness`) | Tags OSM des voies via Overpass API (`way(id:…)`, une requête par itinéraire, cache de 5 000 voies) | Réglable (Réglages › Trottinette), URL modifiable (Réglages › Fournisseurs) |
| Stationnement, bornes | Overpass : `amenity=kick-scooter_parking`, `small_electric_vehicle=yes/designated`, `amenity=charging_station` + `small_electric_vehicle` / `scooter` / `bicycle` | Là où OSM est renseigné |
| Température | Open-Meteo `current=temperature_2m` (sans clé) | En ligne |
| Batterie | **Saisie manuelle** (Roadly n'est pas connecté à la trottinette) | — |

Pourquoi `trace_attributes` est appelé sur FOSSGIS alors que la limitation ne l'est pas : la limitation interrogeait le serveur toutes les 5 s pendant tout le guidage ; l'analyse trottinette envoie **une** requête par itinéraire calculé (même charge qu'un calcul d'itinéraire), plus une après un recalcul.

## 2. Architecture

```
core:model     TravelMode, ElectricScooterProfile, ScooterRoutePreferences, ScooterRouteRequest,
               RouteRequest.scooter / excludePolygons, Route.elevationM, Trip (+ mode, D+, énergie, batterie), TripPeriodStats
core:scooter   (nouveau, JVM pur, 62 tests)
  profile/     ScooterPresets (MAX G2)
  routing/     ScooterRoutingProfile (costing Valhalla bicycle), ForbiddenAreas (exclude_polygons)
  regulation/  ScooterRegulation, FranceEdpmRegulation, UnsupportedCountryRegulation, ScooterRegulations (registre par pays)
  segment/     EdgeAttributes, OsmWayTags, ScooterRouteSegment, WayCategory, ScooterSegmentClassifier
  analysis/    ScooterComfortAnalyzer, ScooterRouteAnalysis, ScooterRouteRanking, ScooterRouteAnalysisBuilder
  range/       ScooterRangeEstimator, RangeCalibration, TripCalibration
  guidance/    ScooterPhrases, ScooterNavigationCoordinator, ScooterNavigationUiState
  assistant/   ScooterCommandParser, ScooterAssistant, ScooterAssistantTools
  map/         RouteDisplayGroup, ScooterRouteFeatures (GeoJSON), ScooterCamera (3D)
  valhalla/ osm/ geo/  TraceAttributes, Overpass, Polyline6Encoder, RouteLine
core:routing   ValhallaRoutingProvider (branche bicycle + altitude + exclude_polygons), ScooterRouteAnalyzer
               (ValhallaScooterRouteAnalyzer), ScooterRouteOptionsUseCase, ScooterPoiProvider (Overpass)
core:navigation NavigationSession (paramètre ScooterGuidanceConfig, coordinateur, recalcul vélo, analyse après recalcul),
               AnnouncementPolicy (paliers 200/80/20 m, texte trottinette), TripRecorder (contexte, extras),
               TemperatureProvider (Open-Meteo), ScooterServices (agrégat injecté dans les écrans, Roadly AI)
core:data      Room v5 (scooter_profiles + colonnes trips, migration 4→5), ScooterProfileRepository,
               MobilityPreferencesRepository (mode, préférences trottinette, batterie, OSM), ProviderConfig.overpassBaseUrl
core:map       couches trottinette par type de voie + vigilance, followNavigation (inclinaison, marge haute), OfflineMapDownloader
core:designsystem TravelModeSelector, WayCategoryBadge, WayShareBar, WayLegend, BatteryDialog, rememberVoiceInput
feature:route  sélecteur de mode, ScooterChoicePanel, Roadly AI, carte hors connexion
feature:navigation ScooterGuidancePanel, caméra 3D, stationnement / recharge, ScooterRecapPanel, Roadly AI
feature:settings ScooterSettingsGroup + ScooterSettingsViewModel (fiches, batterie, calibration, bilan, OSM)
feature:trips  trajets trottinette repérés (icône, vitesse moyenne, D+)
```

Noms demandés → classes : `ElectricScooterProfile` (core:model), `ScooterRoutingProfile`, `ScooterRoutePreferences` (core:model), `ScooterRangeEstimator`, `ScooterComfortAnalyzer`, `ScooterNavigationCoordinator`, `ScooterRouteSegment`, `ScooterNavigationUiState`.

Isolation voiture / moto : les préférences trottinette ont leurs propres clés DataStore (`scooter.*`) ; une requête trottinette n'envoie jamais `RoutePreferences` ni `VehicleProfile` (tests `ScooterRoutingTest`, `ScooterRouteViewModelTest`) ; le coût carburant n'est jamais calculé pour un trajet trottinette. Les ViewModels reçoivent `ScooterServices?` : nul dans les tests existants, ce qui garantit que les parcours voiture / moto n'ont pas changé (tous les tests existants exécutés passent).

Mode mémorisé : `mobility.travel.mode`. Sans choix enregistré, le mode est déduit du profil existant (moto si `VehicleType.MOTORCYCLE`, sinon voiture) : aucun changement pour les installations existantes.

## 3. Routage

- Costing `bicycle` (Valhalla n'a pas de costing EDPM ; `motor_scooter` vise les cyclomoteurs et éviterait les pistes cyclables). Options dérivées de l'objectif, des préférences et du profil : `use_roads` (rapide 0,4 → sécurisé 0,05 ; ≤ 0,15 avec « pistes cyclables », ≤ 0,1 avec « éviter grands axes »), `use_hills`, `avoid_bad_surfaces` (selon pneus, suspension, roues ; jamais 1), `bicycle_type` (`road` pour petites roues / pneus pleins si « éviter non revêtu »), `cycling_speed` = croisière bornée à 25 km/h (France), `steps_factor` 1000, `use_ferry` 0,2, `shortest` pour « court ».
- Trois variantes : l'objectif choisi + deux autres ; les tracés identiques sont fusionnés.
- Vérification : chaque tracé est analysé (§4). Si des portions **interdites ou à pied** sont détectées, un second calcul les exclut (`exclude_polygons`, rectangles de 8 m autour de la partie centrale de chaque portion, hors 30 m du départ et de l'arrivée) ; il n'est retenu que s'il réduit ces portions. Sinon l'itinéraire reste affiché avec l'alerte.
- « Éviter les routes très fréquentées » : **aucune donnée de trafic** n'est disponible ; l'approximation est la classe de route (Valhalla) et la limitation de vitesse, et c'est annoncé comme tel (Roadly AI le dit).

## 4. Réglementation et classification

France (`FranceEdpmRegulation`, Code de la route R412-43-1 en vigueur depuis le 1er sept. 2023, R311-1, décret 2023-848) :

| Portion | Statut |
|---|---|
| Piste cyclable, voie verte | Autorisé d'après les données (en et hors agglomération), sauf `small_electric_vehicle=no`, `access=no/private`, `bicycle=dismount` |
| Bande cyclable | Autorisé en agglomération ; interdit hors agglomération ; **non vérifiable** si l'agglomération n'est pas taguée |
| Route partagée | Autorisé si agglomération taguée **et** limitation ≤ 50 km/h ; interdit si > 50 km/h ou hors agglomération ; non vérifiable sinon |
| Zone de rencontre / aire piétonne | Autorisé (allure du pas pour l'aire piétonne) sauf hors agglomération |
| Trottoir / voie piétonne | Interdit en roulant (sauf autorisation locale, non vérifiable) |
| Autoroute, `motorroad=yes`, voie en travaux | Interdit |
| Escaliers, pied à terre | À pied |
| Chemin, bac, type inconnu | Non vérifiable |

« Autorisé » = conforme aux données disponibles, **jamais une garantie légale** ; l'écran le rappelle. Autres pays : `UnsupportedCountryRegulation` (n'affirme aucune autorisation, garde les interdictions explicites). Pays déterminé par `admins[].country_code` de `trace_attributes` ; France par défaut.

Catégories (`WayCategory`) : piste, bande, voie verte (`traffic_sign=FR:C115`, ou `path` + `bicycle=designated` + `foot=designated` non séparés), zone de rencontre, route partagée, chemin ouvert aux cycles, chemin, aire piétonne, trottoir, traversée, escaliers, bac, travaux, inconnu. Les portions sont découpées par catégorie, revêtement, tranche de pente (±6 %, ±10 %), limitation, éclairage, statut.

## 5. Confort, sécurité, comparaison

Avertissements (`ScooterComfortAnalyzer`), uniquement depuis des données présentes : forte montée / descente (≥ 6 % sur ≥ 40 m, danger ≥ 10 %), pente au-delà de la capacité annoncée, revêtement dégradé (pavés `sett`/`cobblestone`, `smoothness` mauvaise, `paved_rough`), non revêtu, route partagée, circulation rapide (> 30 km/h), travaux, intersection complexe (tourne-à-gauche, demi-tour ou rond-point sur un axe principal), interdit, à pied, aire piétonne, non éclairé (`lit=no`), restriction conditionnelle, accès non vérifiable.

Comparaison : distance, durée, part pistes / bandes / voies vertes (barre colorée), D+ / D− (profil d'altitude, sinon pentes des arêtes, sinon « — »), nombre de portions partagées, passages délicats, longueurs interdites / à pied, batterie à l'arrivée, badges « plus rapide / plus sécurisé / plus confortable / plus court » calculés **entre les variantes** à partir des données connues (aucune note de sécurité inventée). La couverture des données (tags OSM, revêtement OSM, pente, limitation, agglomération, éclairage) est affichée en pourcentage du trajet. Le revêtement « Valhalla » peut être déduit du type de voie : sa source est indiquée.

## 6. Navigation et carte

- Paliers d'annonce trottinette : 200 / 80 / 20 m. Formulations : « Dans 100 mètres, prenez la piste cyclable à droite. », « Continuez sur la voie verte pendant 800 mètres. », « Attention, vous allez rejoindre une route partagée limitée à 30 kilomètres heure. », « Forte montée dans 300 mètres. », « Au prochain carrefour, traversez en suivant l'itinéraire et en respectant la signalisation. », « Votre autonomie estimée devient insuffisante pour rejoindre la destination. ». Sans analyse, le texte Valhalla est gardé. Aucune annonce d'alerte sur une position extrapolée (GPS perdu).
- Caméra 3D trottinette : zoom 17,4 et inclinaison 52° en circulation, 18,1 et 35° à moins de 60 m d'une manœuvre (transition continue entre 150 et 60 m), position au tiers bas de l'écran, animation 900 ms. La voiture garde la caméra à plat d'origine.
- Tracé coloré : piste (vert), bande (vert clair), voie verte (sarcelle), route partagée (ambre), chemin / aire piétonne (brun), vigilance (pointillés rouges : interdit, à pied, dangers).
- Panneau : voie courante (pictogramme), voie prise à la prochaine intersection, prochaine piste, 2 alertes, batterie estimée maintenant → à l'arrivée (touchez pour corriger).

## 7. Autonomie

Modèle physique (roulement + traînée + pente, rendement 0,75, aucune récupération en descente sauf freinage récupératif déclaré, 15 %) ; capacité utilisable = 90 % de la capacité nominale (ou déduite de l'autonomie annoncée, signalée « optimiste ») ; température : 100 % ≥ 20 °C, 95 % à 10 °C, 85 % à 0 °C, 70 % à −10 °C ; marge ±25 % sans calibration. Contrôle de cohérence : MAX G2, 75 kg, 16 km/h, plat → ≈ 72 km (constructeur : 70 km) ; à 25 km/h ≈ 46 km. Calibration : rapport énergie observée / prévue (moyenne géométrique pondérée) sur les trajets où la batterie a été saisie au départ et à l'arrivée (écran récapitulatif). Risque : insuffisant (arrivée < 0 %), juste (borne pessimiste < 10 %), suffisant. Sans batterie saisie : « Batterie inconnue », aucune valeur affichée.

## 8. Roadly AI (mode trottinette)

Reconnaissance vocale du système (`RecognizerIntent`, sans clé) → `ScooterCommandParser` (mots-clés français) → `ScooterAssistant` (réponses tirées de l'analyse, de l'estimation et du profil) → synthèse vocale. Commandes : trajet le plus sécurisé / rapide / confortable / court, éviter la circulation, privilégier les pistes, moins de montées, éviter pavés et chemins, « assez de batterie (pour rentrer) ? » (calcul réel jusqu'au domicile), prochaine piste, destination plus proche (bornes dans la moitié de l'autonomie à plat), « batterie à 65 % ». Ce n'est **pas** le copilote conversationnel Gemini de la spécification AI-1 (non implémenté) : les intentions sont aussi décrites en outils (`ScooterAssistantTools`) pour y être branchées.

## 9. Profils

Plusieurs fiches, profil actif, préréglage **Segway Ninebot MAX G2** (fiche Segway UE et manuel) : 25 km/h, 70 km annoncés à 16 km/h / 75 kg / plat, 551 Wh, 36 V, 450 W nominal, 24,3 kg, 120 kg max, 10" tubeless autoréparants, suspension avant hydraulique + arrière à ressorts, 22 %. Freinage récupératif : non confirmé, laissé « inconnu ». Tout est modifiable ; un champ vide = inconnu.

## 10. Hors connexion

Téléchargement des tuiles de carte du trajet (emprise + 500 m, zooms 12–16, MapLibre `OfflineManager`). Le calcul, le recalcul, l'analyse des voies, la température et les lieux restent **en ligne** (Valhalla, Overpass, Open-Meteo). Sans réseau, le guidage continue sur le tracé déjà calculé.

## 11. Ce qui ne peut pas être vérifié

- Arrêtés municipaux (interdictions locales, trottoirs ou aires autorisés, dérogation ≤ 80 km/h hors agglomération) quand ils ne sont pas dans OSM.
- Agglomération sans tag `maxspeed:type` / `source:maxspeed` / `zone:maxspeed` : la plupart des routes restent « non vérifiables ».
- Obligation d'emprunter la piste lorsqu'elle existe parallèlement à la route (non détectable sur le tracé).
- Trafic réel, qualité réelle du revêtement non taguée, éclairage non tagué, travaux non saisis dans OSM, fermetures temporaires.
- Niveau réel de la batterie (saisie manuelle), compatibilité réelle d'une borne `bicycle=yes`.
- `scooter=yes` est ambigu dans OSM (trottinette ou scooter selon les pays) : affiché comme « à vérifier ».

## 12. Vérification

Exécuté dans l'environnement de développement (JVM, sans SDK Android — Google Maven inaccessible) : `core:model`, `core:common`, `core:scooter`, `core:routing`, `core:navigation` et les ViewModels `RouteViewModel`, `NavigationViewModel` compilés et testés (273 tests, 0 échec, dont 82 nouveaux), `MapController` / `OfflineMapDownloader` compilés contre l'AAR MapLibre 11.13.5. **Non compilés ici** : écrans Compose, Hilt/KSP, Room (KSP), ressources. À faire sur le poste : `.\gradlew.bat :app:assembleDebug testDebugUnitTest connectedDebugAndroidTest` (dont `ScooterMigrationTest`, schéma v4 → v5), puis essais sur le HONOR Magic 8 Pro (§13).

Scénarios couverts par des jeux de données contrôlés : trajet urbain avec pistes, sans piste, route interdite (> 50 km/h, autoroute), restriction locale (`small_electric_vehicle=no`), voie verte, route partagée, forte montée, descente, plusieurs changements de voie, batterie insuffisante, GPS imprécis (pas d'alerte sur position extrapolée), recalcul après erreur de parcours (costing vélo + nouvelle analyse), comparaison voiture / trottinette, conservation du mode et de la batterie après redémarrage. Hors connexion : non testé automatiquement (MapLibre sur appareil).

## 13. Essais sur appareil à faire

1. Réglages › Trottinette : la fiche MAX G2 apparaît ; modifier le poids, enregistrer.
2. Itinéraire en ville (Toulouse ou Paris) en mode Trottinette : 3 variantes, part de pistes, D+, légende, tracé coloré.
3. Saisir la batterie, vérifier « ≈ x % à l'arrivée ».
4. Guidage : caméra inclinée, annonces piste / route partagée / montée, panneau voie courante ; sortir volontairement de l'itinéraire.
5. Arrivée : récapitulatif, saisir la batterie ; refaire un trajet et vérifier « Calibration : 1 trajet ».
6. Bouton micro : « Est-ce que j'ai assez de batterie pour rentrer ? ».
7. Carte hors connexion, puis mode avion.
