# Roadly 1.0 — audit, stratégie et feuille de route

Rédigé le 2026-11-05 sur `main` (commit de départ `9e61f9b`). Ce document fait suite à `SILLAXE_PARITY.md` (parité Sillaxe, lots 1 à 13) et fixe le cap de la version 1.0. Les statuts détaillés restent dans `FEATURES.md`.

## 1. Audit

### 1.1 Ce qui a été vérifié

- **Build et tests** : `:app:assembleDebug` et toute la suite JVM (`testDebugUnitTest`, `:core:model:test`, `:core:common:test`) passent avant et après les changements de cette passe.
- **Code lu** : `NavigationSession`, `RouteTracker`, fournisseurs de limitation, zones de vigilance, service de premier plan et notifications, `TraceAttributes` (trottinette), accueil, écran de guidage, graphe de navigation.
- **Sources réelles interrogées** (instance publique FOSSGIS, 2026-11-05) :
  - `trace_attributes` en `edge_walk` sur un Paris centre → Étoile (5,9 km) : 170 arêtes, 30 km/h partout où la donnée existe.
  - Paris → Lyon (466 km, 8 625 points) en **une seule requête** : limitation connue sur 99,4 % de la distance (130 : 373 km, 110 : 62 km, etc.).
  - A79 Montmarault → Digoin (97 km) : **6 portiques `toll_gantry`** renvoyés par `end_node.type`, avec le pays (`admins[].country_code`).
  - `map_snap` sur trois points avenue Victoria : arête reconnue, 30 km/h, nom de rue.
  - Overpass public (`overpass-api.de`) : **502, serveur saturé**, et message demandant de ne pas l'utiliser de façon automatisée.

### 1.2 Architecture

19 modules Gradle (`app`, 11 `core:*`, 7 `feature:*`), ≈ 24 500 lignes Kotlin hors tests, ≈ 100 fichiers de tests JVM. Kotlin 2.2, Compose Material 3, Hilt, Room v5, DataStore, OkHttp, MapLibre 11.13.5. Règle « aucune donnée inventée » appliquée partout (« — » ou « Bientôt »). `ARCHITECTURE.md` annonçait 16 modules : corrigé.

Points forts : `NavigationSession` confinée (parallélisme 1, générations contre les publications périmées), `RouteTracker` couvert par des scénarios réels, fournisseurs derrière interfaces, tests systématiques.

### 1.3 Lacunes constatées (avant cette passe)

| # | Constat | Gravité |
|---|---|---|
| 1 | Limitation de vitesse **toujours « — »** avec la configuration par défaut : le seul fournisseur refuse l'instance publique ; l'alerte de dépassement ne se déclenchait donc jamais. | Majeure (fonction phare d'un GPS) |
| 2 | Aucune vigilance hors itinéraire (zones, limitation) : `FEATURES.md` « Alerte sans itinéraire : non commencée ». | Majeure |
| 3 | Rappel de péage en flux libre (A79, A13/A14) : non commencé, alors que l'oubli coûte une amende. | Moyenne, forte valeur |
| 4 | « Retrouver son véhicule » : non commencé. | Moyenne |
| 5 | Badge de vitesse de l'accueil sans limitation (« — » en dur). | Mineure (couverte par la conduite libre) |
| 6 | Voies : décodées, mais FOSSGIS n'en renvoie pas (donnée absente). | Dépend du serveur |
| 7 | Trafic, prix des péages, bornes IRVE, parkings : aucune source sans convention ou intégration. | Bloqué (externe) |
| 8 | Android Auto : `androidx.car.app` absent du cache hors ligne. **Livré le 2026-11-05** (`feature:car`, `docs/ANDROID-AUTO.md`), non vérifié sur DHU ni en voiture. | P2 |
| 9 | Dette de vérification sur appareil : écrans trottinette, mises à jour, tests instrumentés ; le HONOR n'était pas connecté (ADB) pendant cette passe. | Risque |
| 10 | Overpass public saturé : l'enrichissement OSM du mode trottinette est fragile. | Risque |
| 11 | Documentation : `FEATURES.md` corrompu ligne 24 (titre recollé), `ARCHITECTURE.md` daté. | Mineure (corrigé) |

Signalés par les notes de la session précédente, non revérifiés ici : deux boutons « Roadly AI » en mode trottinette, `ScooterCamera` inutilisée.

## 2. Fonctionnalités signature

### S1. Profil de route intelligent — **livré**

- **Fonctionnement** : au départ du guidage (et après chaque recalcul), une requête `trace_attributes` en `edge_walk` sur le tracé exact. On en tire, en abscisses du tracé, les tronçons de limitation (OSM `maxspeed`) et les portiques de péage en flux libre. La limitation courante et la **prochaine limitation** (« 50 dans 300 m », horizon de 20 s de trajet) sont lues localement à chaque position, sans aucune requête par fix.
- **Valeur** : débloque la limitation et l'alerte de dépassement sur l'instance publique ; anticipation visuelle des changements.
- **Faisabilité** : vérifiée sur données réelles (§1.1). Coût réseau = un calcul d'itinéraire.
- **Dépendances** : Valhalla (`/trace_attributes`), données OSM. Aucune clé.
- **Complexité** : moyenne. **Intégration** : `RouteAttributes.kt`, `NavigationSession` (repli sur le fournisseur fix par fix si le profil manque), pastille dans `StatusPills`.
- **Limites** : limitation inconnue là où OSM n'a pas de `maxspeed` (« — ») ; hors itinéraire (> 40 m) rien n'est lu dans le profil ; panneaux temporaires et limitations variables non connus.

### S2. Copilote péage en flux libre — **livré**

- **Fonctionnement** : les portiques du profil (S1) sont comptés au passage. Premier portique : annonce vocale unique ; pastille « Péage en flux libre franchi ». À l'arrivée : nombre de portiques, routes et, en France, **échéance de 72 h**. Le rappel reste sur l'accueil jusqu'à « C'est payé » ; il passe en rouge après l'échéance.
- **Valeur** : évite l'amende (oubli fréquent sur l'A79 et l'A13/A14). Aucun concurrent grand public ne le fait de bout en bout.
- **Dépendances** : OSM `highway=toll_gantry` via Valhalla. Aucun montant n'est affiché (pas de source fiable).
- **Complexité** : faible à moyenne. **Intégration** : `DriveMemoryRepository` (DataStore), `NavigationSession`, accueil.
- **Limites** : un portique absent d'OSM n'est pas vu ; hors de France, aucune échéance n'est donnée ; pas de lien de paiement (URL d'exploitant non vérifiée).

### S3. Conduite libre (vigilance sans itinéraire) — **livré**

- **Fonctionnement** : bouton compteur sur l'accueil. Compteur, limitation et nom de la route (map matching Valhalla espacé), zones de vigilance devant soi (base officielle locale, jamais un point), bip de dépassement, durée et distance, enregistrement du trajet dans l'historique. Tourne écran éteint (service de premier plan `location`, notification avec « Arrêter »). Exclusif du guidage : un guidage qui démarre la termine.
- **Fair-use** : instance publique = au plus une requête par 20 s **et** 250 m parcourus, ou 8 s après un virage ; aucune requête à l'arrêt. Instance auto-hébergée : 5 s / 40 m.
- **Valeur** : l'usage quotidien (trajets connus sans itinéraire) devient couvert.
- **Complexité** : moyenne. **Intégration** : `FreeDrive.kt` (`core:navigation`), `FreeDriveScreen.kt`, `NavigationService`.
- **Limites** : après un virage, limitation « — » jusqu'à la réponse suivante ; zones « devant soi » estimées par l'axe du cap (route sinueuse : zone vue plus tard) ; un guidage lancé pendant la conduite libre remplace le trajet en cours d'enregistrement.

### S4. Mon véhicule — **livré (sans itinéraire piéton)**

- **Fonctionnement** : à l'arrivée d'un guidage voiture / moto, la dernière position est mémorisée (« Mon véhicule · 350 m · il y a 2 h »). « Je suis garé ici » dans la fiche « Ma position ». Toucher la ligne montre le véhicule sur la carte ; « Oublier cette position ».
- **Dépendances** : aucune. **Complexité** : faible.
- **Trajet à pied — livré le 2026-11-05** : fiche « Mon véhicule » › « Trajet à pied jusqu'à la voiture » (Valhalla `pedestrian`, `RouteRequest.pedestrian`), tracé cadré sur la carte de l'accueil avec durée et distance ; « Vous êtes à côté de votre voiture » sous 30 m. Pas de guidage pas à pas à pied (le guidage est conçu pour la conduite : radars, vue des carrefours, mémorisation du stationnement).

### S5. Carte d'identité du trajet — **livré le 2026-11-05**

Sur l'écran d'itinéraires, sous la durée de la variante choisie : part du trajet à limitation connue, temps sur les routes limitées au-dessus de 90 (compté à la limitation, borne basse), **péages en flux libre** franchis, zones de danger. `RouteIdentity` (pur, testé) à partir du profil S1 ; une requête `trace_attributes` par variante, variante active d'abord, mise en cache par `ValhallaRouteAttributesProvider`. Sans profil (hors ligne, échec) : pas de carte, rien d'inventé. Vérifié sur le HONOR (Toulouse → Capitole : 89 %, 0 min, 0, 1).

### S6. Roadly AI proactif — P1

Le copilote Gemini Live existe (optionnel, clé de l'utilisateur). Étape suivante : outils en lecture du profil S1 (« quelle est la limitation ? », « y a-t-il un péage en flux libre ? ») et un résumé vocal de départ facultatif. Dépendance : clé Gemini (déjà gérée). Le GPS ne dépend jamais de l'IA.

### S7. Coût réel du trajet — P2

Carburant (déjà calculé sur le profil véhicule) + historique mensuel des dépenses. Le péage reste « — » faute de barème public fiable.

## 3. Feuille de route

### P0 — indispensable à une 1.0 stable

| Élément | Statut |
|---|---|
| Limitation de vitesse réelle et alerte de dépassement (S1) | **Fait** (tests JVM) |
| Vigilance sans itinéraire (S3) | **Fait** (tests JVM) |
| Passe complète sur le HONOR Magic 8 Pro : guidage réel, conduite libre, notification, écran éteint, Doze | À faire (appareil non connecté le 2026-11-05) |
| Tests instrumentés (Room v5, Compose) sur appareil | À faire |
| Vérification des écrans trottinette et des mises à jour GitHub sur appareil | À faire |
| Valhalla auto-hébergé recommandé pour la diffusion (fair-use FOSSGIS) | Documenté (`docs/API-PROVIDERS.md`) |

### P1 — évolutions majeures

- Rappel de péage en flux libre (S2) — **fait**.
- Mon véhicule (S4) — **fait**, trajet à pied compris.
- Carte d'identité du trajet (S5) — **fait**.
- Annonce vocale optionnelle des baisses de limitation.
- Roadly AI : outils sur le profil de route (S6).
- Badge de limitation sur l'accueil quand la conduite libre est active — **fait** (cas utile : conduite libre lancée depuis Android Auto, téléphone sur l'accueil ; non vérifié à l'écran).
- Écran d'itinéraires ouvert à froid (lien `geo:`, raccourci) : attend la première position au lieu de rester sur « Position inconnue » — **corrigé** (constaté sur le HONOR).

### P2 — premium / expérimental

- Android Auto (Car App Library, catégorie navigation) — **fait** (`docs/ANDROID-AUTO.md`) ; passe DHU faite le 2026-11-05 (téléphone verrouillé compris), reste l'essai en voiture ; publication Play Store hors champ (revue Google).
- Coût réel et historique des dépenses (S7).
- Carte hors connexion du corridor pour tous les modes (existe pour la trottinette).
- Widget « Conduite libre » et tuile Réglages rapides.

### P3 — 1.1 et suivantes

- Trafic et incidents officiels (Bison Futé DATEX II : convention nécessaire).
- Bornes IRVE, parkings open data.
- Synchronisation (Supabase) et partage de trajet en direct.
- Ligues et défis communautaires (serveur à écrire).

## 4. Dépendances externes de la 1.0

| Service | Usage | Clé / contrat | Remarque |
|---|---|---|---|
| Valhalla FOSSGIS (`valhalla1.openstreetmap.de`) | Itinéraires, profil de route, map matching espacé | Aucun | Fair-use, développement ; auto-hébergement conseillé en diffusion |
| BAN, Photon | Recherche | Aucun | Inchangé |
| data.gouv.fr (zones de vigilance) | Zones | Aucun | Jeu 2018, cache Room |
| Open-Meteo | Pluie | Aucun | Inchangé |
| Gemini Live | Roadly AI (optionnel) | Clé de l'utilisateur | Jamais requis pour le GPS |
| Overpass | Tags OSM trottinette | Aucun | Public saturé (502 le 2026-11-05) |

## 5. Ce que cette passe n'a pas fait

- Aucune vérification sur appareil (HONOR absent d'ADB) : la 1.0 n'est pas déclarée prête.
- Pas d'itinéraire piéton vers le véhicule, pas d'annonce vocale des limitations, pas de S5 / S6.
- Aucun commit ni push : un push sur `main` publie une release installée par le téléphone.
