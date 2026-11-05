# Radar Intelligence — architecture, moteur, mesures et bilan

Rédigé le 2026-11-05. Sources et cadre légal : `docs/RADAR-SOURCES.md`.

## 1. Audit de la partie radar avant cette refonte

| Constat | Conséquence |
|---|---|
| Une seule source : `radars.csv` de **octobre 2018**, URL figée dans le code | Base vieille de 7 ans ; radars installés depuis absents, radars démontés toujours annoncés |
| Aucune notion de sens, de précision ni de confiance | Chaussée opposée, route parallèle et bretelle annoncées si le point est à moins de 60 m du tracé |
| Zones calculées par écart au tracé seulement (60 m) | Pas de distinction de sens sur autoroute |
| Sans itinéraire : cône de 35° devant le cap brut | Faux positifs sur routes parallèles, alerte au moindre écart de cap |
| Remplacement intégral de la table à chaque téléchargement | Aucun historique, aucun contrôle de qualité, aucune détection de radars retirés |
| Une annonce par zone et par course, sans machine à états | Pas de préavis, pas de sortie, ré-annonce possible après recalcul |
| Réglages : activation, bip, téléchargement manuel | Ni voix séparée, ni préavis, ni catégories, ni état de la base |

## 2. Architecture

```
core:radar (JVM pur, testé sur données réelles)
  Radar.kt         modèle (catégorie, sens contrôlé, provenance, précision, confiance, statut)
  RadarData.kt     parseurs des deux jeux officiels, sens, validation, fusion/enrichissement, score, écart entre versions
  GeoMath.kt       géométrie locale, polyligne projetée, index spatial en grille
  RadarEngine.kt   réglementation par pays, longueurs de zone, rapprochement avec itinéraire et sans itinéraire,
                   filtre de cap, machine à états des alertes
core:data
  RadarSync.kt     synchronisation incrémentale (API data.gouv.fr → CSV le plus récent), contrôle qualité, journal
  VigilanceRepository  base Room locale (`vigilance_zones` v6 + `radar_versions`), `radarsIn(box)`
  RadarSettingsRepository  voix, préavis, sortie de zone, familles d'équipements
core:navigation
  RepositoryVigilanceZoneProvider  zones de danger le long d'un itinéraire (RouteRadarMatcher + noms de voie des manœuvres)
  NavigationSession  machine à états sur les zones du tracé → voix, bip, état `radarActive` / `radarNext`
  FreeDriveSession   conduite libre : HeadingFilter + FreeRadarMatcher + machine à états, nom de route du map matching
feature:navigation  pastille DangerZonePill (guidage et conduite libre)
feature:settings    écran « Radars et zones de danger »
```

Aucune alerte ne dépend du réseau : la base est locale (Room), les calculs se font sur le téléphone, au rythme des positions.

## 3. Base de données

`vigilance_zones` (Room v6, migration `MIGRATION_5_6`, colonnes ajoutées nulles par défaut) : identifiant, latitude, longitude, catégorie (`kind`), libellé source (`type`), vitesse VL et PL, sens (texte, cap, double sens), route, commune, département, pays, longueur de tronçon, date de mise en service, source, version de la source, jeu d'enrichissement, date de collecte (`updatedAt`), date de vérification, précision (m), confiance, statut (`ACTIVE` / `REMOVED`).

`radar_versions` : une ligne par synchronisation (appliquée, inchangée, rejetée, échec) avec la ressource, sa date de modification, les nombres d'ajouts, retraits, déplacements, changements de limitation, et la qualité (enrichis, sens connu, haute précision, lignes rejetées). 30 dernières conservées.

Aucun champ n'est inventé : la VMA « NA » reste nulle, un sens « PARIS vers LYON » reste un libellé sans cap.

## 4. Synchronisation

1. Requête légère à l'API data.gouv.fr ; ressource CSV la plus récente (repli sur l'URL de décembre 2025).
2. Même ressource et même date que la dernière synchronisation réussie → **rien n'est téléchargé** (`UNCHANGED`).
3. Sinon : téléchargement de la liste officielle et du jeu 2018 (enrichissement facultatif), fusion, validation (coordonnées en France, identifiants uniques, limitations réglementaires).
4. Contrôle qualité : moins de 1 000 équipements, ou baisse de plus de 30 % → version **rejetée**, base précédente conservée.
5. Écriture atomique : lignes 2018 héritées supprimées, équipements écrits, disparus marqués retirés, journal.
6. Déclenchement : au lancement de l'application si la dernière vérification réussie a plus de 7 jours, et manuellement (Réglages › Radars).

## 5. Moteur géospatial

### Avec itinéraire (`RouteRadarMatcher`)
Projection de chaque équipement sur le tracé, puis rejet si :
- statut retiré, confiance < 40, catégorie désactivée ;
- **écart latéral** > 18 m + précision du point (plafonnée à 42 m) ;
- **zone masquée** par l'utilisateur (fausse alerte signalée) ;
- **sens contrôlé connu** à plus de 60° du cap du segment (chaussée opposée, même dans un terre-plein étroit) ;
- **numéro de route** connu des deux côtés et différent (route parallèle, bretelle).
Les zones qui se chevauchent sont fusionnées (limitation la plus basse, confiance la plus haute) : une seule alerte.

### Sans itinéraire (`FreeRadarMatcher`)
- **Cap filtré** (`HeadingFilter`) : moyenne circulaire sur 6 s au-delà de 3 m/s ; virage régulier accepté, dispersion > 25° → cap inconnu (arrêt, rond-point) : aucune nouvelle zone.
- **Courbure** estimée sur la fenêtre : la trajectoire est prolongée en arc (rayon minimal 150 m).
- **Couloir latéral** : 18 m + précision + 4 % de la distance devant ; GPS moins précis que 50 m → aucune nouvelle zone.
- **Sens connu** : écart de cap ≤ 60°. **Numéro de route** : celui du map matching (conduite libre).
- **Sens inconnu** : à plusieurs kilomètres, 1–2° d'erreur de cap valent 70–140 m latéraux et la chaussée opposée devient indiscernable. Ces équipements ne sont confirmés qu'à **300 m** avec un couloir strict (1 %). Choix assumé : une alerte plus tardive plutôt qu'une fausse alerte.
- Une zone ouverte reste suivie jusqu'à sa fin (tronçons de plusieurs kilomètres compris), même si le cap devient incertain.

## 6. Machine à états des alertes (`RadarAlertEngine`)

Une entrée par zone : (rien) → **PREWARN** → **INSIDE** → **COOLDOWN** → (rien).
- Ouverture après 3 observations consécutives sans itinéraire (1 avec itinéraire) : un fix isolé ne déclenche rien.
- Préavis réglable (0, 250, 500, 1 000 m) ; entrée toujours annoncée une fois ; sortie confirmée 50 m après la fin, annoncée si l'option est active.
- Pas de ré-annonce pendant 3 min après la sortie (bruit GPS, arrêt dans la zone, demi-tour).
- Zone perdue de vue 3 mises à jour : refermée en silence.
- Confiance 40–59 : alerte visuelle seulement ; ≥ 60 : voix (si activée). Dépassement dans la zone : signalé par le bip général de dépassement (pas de second son).

## 7. Interface

- **Guidage et conduite libre** : une seule pastille « Zone de danger · dans 800 m » (compte à rebours, ≤ 3 km) puis « Zone de danger · encore 1,2 km », limitation si connue, fiabilité en trois barres. Ni type, ni position. Lecture TalkBack complète.
- **Réglages › Radars et zones de danger** : activation, voix, bip, fin de zone, préavis, familles (vitesse, tronçons, feux rouges, passages à niveau), nombre d'équipements actifs, version de la liste, qualité (% précis à 10 m, % sens connu, % enrichis), dernière mise à jour (ajouts, retraits, déplacements, limitations), dernière vérification, synchronisation manuelle, sources et licence.

## 8. Tests et mesures

| Fichier | Contenu |
|---|---|
| `core/radar/.../RadarDataTest.kt` (8) | Fichiers officiels réels : 3 309 lignes, codes, encodage, fusion (2 223 enrichis, 674 sens exploitables), rejets, précision, sens, numéros de route, écart entre versions |
| `core/radar/.../RadarScenarioTest.kt` (19) | Autoroute 2x2 (notre chaussée, chaussée opposée avec et sans sens, route parallèle, bretelle), sens inverse, double sens, virage de 1 km de rayon, arrêt 5 min dans une zone, cap instable, GPS imprécis, tronçon 5 km, doublons, retiré, sans vitesse, confiance basse / moyenne, préavis, dépassement, demi-tour, itinéraire |
| `core/data/.../RadarSyncTest.kt` (7) | Première synchro, source inchangée, retraits conservés, fichier tronqué rejeté, hors connexion (base intacte), repli d'API, aller-retour entité |
| `core/data/androidTest/.../RadarMigrationTest.kt` (2) | Migration v5 → v6 et service des seuls actifs — **compilé, non exécuté** (aucun appareil) |
| `core/navigation` | Session : zone annoncée une fois (« Zone de danger »), conduite libre avec confirmation |

**Mesures (simulation, 50 courses par niveau de bruit, 2 équipements concernés et 4 pièges par course, sans itinéraire)** :

| GPS simulé | Faux négatifs | Faux positifs | Doublons |
|---|---|---|---|
| Sans bruit | 0 / 2 | 0 / 4 | 0 |
| σ 3 m, cap σ 2° | 0 / 100 | **1 / 200 (0,5 %)** | 0 |
| σ 6 m, cap σ 6° | 0 / 100 | **5 / 200 (2,5 %)** | 0 |

Les faux positifs restants concernent tous le même piège : la chaussée opposée à 34 m quand le jeu officiel ne publie pas le sens contrôlé. Avec itinéraire, les quatre pièges sont écartés (écart latéral, sens, numéro de route). Virage de 1 km de rayon : équipement sans sens annoncé à ≈ 250 m.

**Ce que ces mesures ne prouvent pas** : le bruit simulé est indépendant d'un fix à l'autre (un vrai GPS a des biais durables), les géométries sont synthétiques, et rien n'a été vérifié sur route ni sur appareil.

## 9. Bilan — réellement implémenté

| Élément | Statut |
|---|---|
| Source officielle 2025 détectée automatiquement, enrichissement 2018 | Fait, testé sur les fichiers réels |
| Modèle radar avec provenance, précision, confiance, statut | Fait |
| Room v6, journal des versions, retraits conservés | Fait ; migration testée en compilation seulement |
| Synchronisation incrémentale, contrôle qualité, hors connexion | Fait, testé JVM |
| Rapprochement avec / sans itinéraire, sens, route, précision GPS, cap filtré, courbure | Fait, scénarios simulés |
| Machine à états (préavis, entrée, sortie, silence, confirmation) | Fait |
| Pastille guidage et conduite libre, écran de réglages | Fait, compilé ; **non vu sur appareil** |
| Écran éteint / arrière-plan | Hérité du service de premier plan (guidage et conduite libre) ; non revérifié sur appareil |
| Radars mobiles, voitures radar, contrôles de voies réservées | Non : aucune source légale |
| Téléchargement par pays / région | Non : seule la France a une source exploitable ; l'architecture (pays dans le modèle, réglementation par pays) le permet |

## 10. Radar Intelligence 2 (2026-11-05, même jour)

| Élément | Détail | Vérifié |
|---|---|---|
| Position non déductible | Longueur avant le point × 0,85–1,15 et traîne de 150 à 450 m, tirées de l'identifiant (FNV-1a) : même zone à chaque passage et sur tout téléphone, mais la fin de zone (ou « encore 300 m ») ne donne plus l'emplacement. Mesure sur les 3 309 équipements : traîne de 150 à 449 m, tiers de la plage à 1 131 / 1 071 / 1 107 équipements | JVM (`RadarZonePlacementTest`) ; taux de faux positifs et négatifs inchangés |
| Zones peintes sur le tracé | Calque MapLibre `roadly-danger` : étendue de chaque zone sur 30 km devant, ambre, rouge pour celle traversée ; téléphone et Android Auto | JVM (`DangerZoneFeaturesTest`) ; **non vu sur appareil** |
| Zones par itinéraire | Nombre de zones de danger sur chaque variante (écran d'itinéraires, aperçu Android Auto), même calcul que le guidage | Compilé ; **non vu sur appareil** |
| Fausses alertes | Toucher la pastille → « Masquer la zone » ; Android Auto : alerte de l'hôte (8 s) avec « Fausse alerte ». Effet immédiat (pastille, carte, voix), réversible dans Réglages, 500 zones au plus, stocké sur le téléphone seulement | JVM (`HiddenZonesTest`) ; **non vu sur appareil** |
| Alerte Android Auto | Une alerte de l'hôte par zone et par passage (préavis ou entrée), distance, limitation, fiabilité | Compilé ; **non vu sur DHU** |

Écarté : la vitesse moyenne sur les tronçons. Elle désignerait un contrôle de vitesse moyenne et son étendue, et son statut au regard de l'article R413-15 n'a pas pu être vérifié.

## 11. Roadmap

- **P0** : passe sur le HONOR (guidage réel, conduite libre, écran éteint) ; exécuter `RadarMigrationTest` ; vérifier les longueurs de zone sur Légifrance.
- **P1** : affiner le sens des « PARIS vers LYON » (géocodage des deux villes → cap) ; enrichissement OSM par extraction hebdomadaire hors application (Geofabrik, ODbL) ; tronçons ETVM : point d'entrée et de sortie.
- **P2** : signalements communautaires de contrôles temporaires comme zones de danger (Supabase, expiration courte, conforme R413-15) ; zones de danger sur la carte en mode aperçu ; WorkManager pour la vérification hebdomadaire même sans ouvrir l'application.
- **P3** : autres pays dès qu'une source ouverte existe (registre `RadarRegulation`) ; apprentissage des faux positifs signalés par l'utilisateur.
