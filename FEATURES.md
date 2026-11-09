# FEATURES.md — statut des fonctionnalités Roadly

Référentiel : `docs/SILLAXE-ANALYSE.md` (fonctions vérifiées sur les pages publiques de Sillaxe) et `docs/BENCHMARK-SILLAXE.md` (sources de données et contraintes). Statuts : **non commencée** · **en cours** · **fonctionnelle** · **bloquée** (dépend d'une source externe non configurée). « Palier Sillaxe » = gratuit (G) ou Sillaxe+ (+) chez Sillaxe ; Roadly ne reproduit pas le modèle économique, l'information sert à prioriser.

Mise à jour : 2026-11-09 (repères « au feu », virages serrés annoncés, péage annoncé, feux, routes fermées contournées). Auparavant : 2026-11-05 (passe 1.0 : profil de route, conduite libre, péage en flux libre, véhicule garé — voir `docs/ROADLY-1.0.md`). Auparavant : 2026-10-31 (LOT 1 design system). La matrice de parité complète avec Sillaxe est dans `SILLAXE_PARITY.md` (référence pour les lots suivants) ; le design system est décrit dans `docs/DESIGN-SYSTEM.md`.

État de la Phase 2 : code livré, 224 tests unitaires (JVM) et 3 tests instrumentés passent. Vérifié sur HONOR Magic 8 Pro (Android 17) **à l'arrêt** : démarrage du guidage, service de premier plan, notification et son action « Arrêter », voix et focus audio, écran maintenu allumé, refus de `POST_NOTIFICATIONS` sans plantage. **Non vérifié en conduite** : recalcul, paliers d'annonces, arrivée, « GPS perdu ».

## 1. Navigation et guidage

| Fonction | Palier | Statut Roadly | Phase | Notes |
|---|---|---|---|---|
| Position GPS en direct, cap, vitesse | G | fonctionnelle | 1 | FusedLocation ; 1 Hz carte et guidage, 2 Hz suivi |
| Orientation de la carte (suivi cap) | G | fonctionnelle | 1 | Mode FOLLOW |
| Calcul d'itinéraires (3 variantes) | G | fonctionnelle | 1 | Valhalla ; rapide / court / sans péage |
| Guidage vocal français | G | fonctionnelle | 2 | TTS Android `Locale.FRANCE`, textes Valhalla, 3 paliers selon la vitesse (2000/300/40 m, 600/150/40 m, 300/80/40 m), anti-répétition (manœuvre, palier) |
| Bandeau de manœuvre (prochaine instruction, distance) | G | fonctionnelle | 2 | Icône, distance, instruction, rue, pastille de sortie |
| Affichage des voies | G | en cours | 2 | Décodage et affichage faits ; aucune voie dans les réponses FOSSGIS observées (fixture Paris) : données absentes sur l'instance publique, à confirmer sur un Valhalla récent |
| Ronds-points avec sortie | G | fonctionnelle | 2 | Pastille « 2e sortie » ; numéro de sortie (`sign`) si présent |
| Suivi d'itinéraire, détection de sortie, recalcul | G | fonctionnelle | 2 | RouteTracker (garde de cap) ; hors itinéraire 40 m × 5 fixes ou 100 m ; recalcul ≤ 1/10 s, délai 10 s |
| Temps/distance restants, heure d'arrivée en guidage | G | fonctionnelle | 2 | Durées Valhalla restantes, manœuvre courante au prorata |
| Limitation de vitesse de la route | G | fonctionnelle | 1.0 | Profil du tracé : une requête `trace_attributes` (`edge_walk`) par tracé, y compris FOSSGIS ; « — » là où OSM n'a pas de `maxspeed` ; repli fix par fix sur un Valhalla auto-hébergé. Testé JVM, non vérifié en conduite |
| Prochaine limitation (« 50 dans 300 m ») | G | fonctionnelle | 1.0 | Lue dans le profil, horizon 20 s de trajet (250 m–1,5 km) ; testé JVM |
| Alerte sonore de dépassement (option) | G | fonctionnelle | 2 | Bip + badge rouge, tolérance 0/5/10 km/h ; active dès qu'une limitation est connue (profil 1.0) |
| Annonce des baisses de limitation (option) | — | fonctionnelle | Radar 3 | « Limitation 70. » une fois, confirmée sur deux positions, en guidage et en conduite libre ; jamais pour une hausse ; testé JVM |
| Limitation abaissée par pluie (option) | G | fonctionnelle | 2 | Open-Meteo (CC BY 4.0, sans clé) ; effet visible seulement si une limitation est connue (LOT 4) |
| Changer de destination / ajouter un arrêt en guidage | G | fonctionnelle | 2 | Destination, itinéraire alternatif, ajout d'arrêt (LOT 6) |
| Jusqu'à 10 arrêts réordonnables | G | fonctionnelle | 4 | Écran d'itinéraires (LOT 6) |
| Départ planifié (heure de départ) | G | fonctionnelle | 1 | Décale seulement l'heure d'arrivée |
| « Arriver à l'heure » | G | fonctionnelle | 1 | Départ déduit de la durée du plus rapide, sans trafic (LOT 3) ; rappel avant départ : Phase 4 |
| Itinéraire alternatif en cours de guidage | G | fonctionnelle | 2 | Feuille « Changer d'itinéraire » depuis la position projetée (LOT 3) |
| Options d'itinéraire sur l'écran de choix | G | fonctionnelle | 1 | Puces éviter péages / autoroutes / tunnels / ferries (LOT 3, tunnels 2026-11-09) ; aussi depuis l'accueil (bouton réglages de la barre Domicile / Travail) |
| Options du trajet dans la recherche | G | fonctionnelle | 2026-11-09 | Départ, sans péage / autoroute / tunnel / ferry / chemin non revêtu choisis avant la destination ; pour ce trajet seulement (`TripPreferences`) ou « Mémoriser » ; rappel des options actives sur le choix d'itinéraire ; « Y aller » sur chaque résultat ; micro. Tests JVM |
| Éviter les chemins non revêtus | G | fonctionnelle | 2026-11-09 | Valhalla `exclude_unpaved`, repli sans exclusion si aucun chemin |
| Éviter les tunnels | G | fonctionnelle | 1 | Exclusion stricte Valhalla `exclude_tunnels` ; sans chemin possible, recalcul sans l'exclusion (tunnel inévitable). Testé JVM ; non vérifié sur l'instance FOSSGIS depuis le conteneur de développement |
| Accueil du premier lancement | G | fonctionnelle | 1 | `feature:onboarding` (2026-11-09) : 7 étapes (bienvenue, position, notifications, véhicule, voix et alertes, « Votre route », classement), barre segmentée, « Ignorer » / « Continuer » ; chaque interrupteur écrit le réglage réel. Non vérifié sur appareil |
| Carte « Départ » de l'accueil | G | fonctionnelle | 1 | Partir maintenant / partir à / arriver à, repris par l'écran d'itinéraires (`DeparturePlanner`, en mémoire), effacé au démarrage du guidage |
| Mode paysage | G | en cours | 1 | Accueil, itinéraires et guidage en deux colonnes (LOT 1) ; non vérifié sur appareil |
| Thème clair / sombre / automatique | G | fonctionnelle | 1 | Auto solaire (position) + système |
| Mode Silence | G | fonctionnelle | 2 | Bouton voix de l'écran de guidage |
| Import GPX | ? | non commencée | 5 | |
| Guidage allégé hors connexion | G | non commencée | 5 | Cache tuiles MapLibre + itinéraire mémorisé |
| ZFE / Crit'Air | ? | fonctionnelle | 3 | Base nationale consolidée des ZFE (transport.data.gouv.fr, `aires.geojson`, cache 7 j) : verdict selon la catégorie, la vignette Crit'Air, la date et les horaires (`opening_hours`) ; pastille sur l'écran d'itinéraires + détail (arrêté), feuille sur les alternatives, périmètre sur la carte, ligne sur Android Auto ; réglage « Zones à faibles émissions ». 10 tests JVM, dont un sur le vrai fichier national (`-PzfeFile`) |
| Tunnels (perte GPS) | G | fonctionnelle | 2 | Extrapolation sur la polyligne après 3 s sans fix, plafonnée à 150 m / 10 s ; badge « GPS perdu » après 10 s |

## 2. Horizon (route à venir)

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Ruban Horizon : manœuvres, arrivée | G | fonctionnelle | 2 | `HorizonStrip` (LOT 1) : échelle log 0–5 km, 3 prochaines manœuvres + arrivée ; virages par courbure : LOT 4 |
| Ralentissements, bouchons, incidents dans le bandeau | G | bloquée | 3 | Dépend trafic |
| Services (stations, aires) dans le bandeau | G | bloquée | 3 | Dépend POI |
| Zones de vigilance dans le bandeau | G | fonctionnelle | Radar | Zones de danger dans l'Horizon, la pastille et sur le tracé ; jamais de point d'équipement |

## 2 ter. Image dans l'image

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Guidage en image dans l'image | G | fonctionnelle | 2026-11-09 | Automatique pendant un guidage (Android 12+), à la sortie de l'app sinon : manœuvre, distance, rue, arrivée. Non vérifié sur appareil |

## 2 bis. Commandes vocales

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Commandes vocales intégrées | G | fonctionnelle | 2026-11-09 | Bouton micro (accueil, guidage) : reconnaissance du téléphone (hors ligne si possible), interprétation locale `VoiceCommandParser` (aucun service d'IA, aucune clé) ; domicile / travail / adresse, éviter péages / autoroutes / tunnels / ferries avec l'écart d'arrivée, signalements, heure d'arrivée, reste, prochaine manœuvre, limitation, stations / parkings / bornes / aires / restaurants, couper la voix, recalcul, arrêt, aide. Réponse affichée et prononcée (canal assistant). Remplace le copilote Roadly AI (Gemini), retiré. 12 tests JVM |

## 3. Recherche, lieux, favoris

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Recherche adresse France (BAN) | G | fonctionnelle | 1 | |
| Recherche POI / villes / Europe (Photon) | G | fonctionnelle | 1 | POI après les adresses BAN : tri à améliorer |
| Coordonnées GPS (décimal, DMS) | G | fonctionnelle | 1 | |
| Récents (50), favoris, domicile, travail | G | fonctionnelle | 1 | Local (Room) |
| Fiche lieu : distance, itinéraire, favori, domicile/travail | G | fonctionnelle | 1 | Retour visuel après « Définir » à ajouter |
| Appui long sur la carte → destination | G | fonctionnelle | 1 | |
| Toucher sa position → adresse copiable/partageable | G | fonctionnelle | 1 | Feuille « Ma position » : adresse, coordonnées, lien OSM, Copier / Partager (LOT 2) |
| Boussole | G | fonctionnelle | 1 | Apparaît quand la carte est tournée ; remet le nord en haut (LOT 2) |
| Catégories à proximité (stations, bornes, parkings, aires, restaurants) | G | fonctionnelle | 1 | Photon `reverse` + `osm_tag`, triées par distance ; sans prix ni disponibilité (LOT 2) |
| Horaires d'ouverture des lieux | G | non commencée | 3 | OSM `opening_hours` via Photon/Overpass |
| Adresses des contacts, lieux de rendez-vous | G | non commencée | 4 | Permissions contacts/calendrier |
| Commande vocale (Assistant / App Actions) | G | en cours | 5 | « Navigue vers X » reçu par `google.navigation:q=` → recherche pré-remplie ou itinéraire (LOT 12) ; capacités App Actions : non déclarées |
| Favoris synchronisés (web, multi-appareils) | + | non commencée | 4 | Supabase |

## 4. Itinéraires et coûts

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Comparaison visuelle 3 itinéraires sur carte | G | fonctionnelle | 1 | |
| Coût carburant (profil véhicule saisi) | G | fonctionnelle | 1 | Jamais de prix par défaut |
| Prix des péages | G | bloquée | 3 | Pas d'API publique ; barème à maintenir |
| Itinéraire « économique » | G | non commencée | 3 | Valhalla : coût combiné distance/péage/conso |
| Trafic pris en compte dans le calcul | G | bloquée | 3 | Fournisseur trafic |
| Itinéraire adapté au gabarit (hauteur/largeur/poids) | + | fonctionnelle | 4 | Valhalla `truck` avec le gabarit saisi (LOT 7) |

## 5. Trafic et incidents

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Bouchons, accidents, travaux, fermetures (sources officielles) | G | fonctionnelle | 3 | Bison Futé DATEX II (routes nationales, DIR), 5 min, filtré dans le sens de circulation : pastille et détail sur l'écran d'itinéraires, carte, Horizon du guidage, Android Auto. Autoroutes concédées : non couvertes. Tests sur un extrait réel |
| Routes coupées évitées | G | bloquée | 3 | `exclude_polygons` Valhalla |
| Re-routage sur bouchon | G | bloquée | 3 | |
| Météo sur la carte | G | bloquée | 3 | Météo-France / Open-Meteo |
| ETA par apprentissage collectif | G | non commencée | 5 | Backend, opt-in |

## 6. Zones de vigilance

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Zones de vigilance (France, jamais un point exact) | G | fonctionnelle | Radar | Radar Intelligence (`docs/RADAR-INTELLIGENCE.md`) : liste officielle DSR 2025 (3 309 équipements) enrichie du jeu 2018 (sens, route), synchro incrémentale, zones de danger 300 m–4 km filtrées par sens, écart latéral et numéro de route ; testé JVM et en simulation, non vu sur appareil |
| Alertes visuelles / vocales / sonores anticipées | G/+ | fonctionnelle | Radar | Machine à états : préavis réglable, entrée, fin de zone (option), une annonce par passage, voix seulement si confiance ≥ 60 ; pastille avec compte à rebours et fiabilité |
| Limitation de la zone | + | fonctionnelle | Radar | VMA de la liste officielle quand elle est publiée (« NA » = rien d'affiché) |
| Zones de danger sur la carte | — | fonctionnelle | Radar 2 | Étendue de chaque zone peinte sur le tracé (ambre devant, rouge traversée), téléphone et Android Auto ; jamais de point d'équipement |
| Zones de danger par itinéraire | — | fonctionnelle | Radar 2 | « 3 zones de danger » sur chaque variante (téléphone et Android Auto), même calcul que le guidage |
| Fausses alertes | — | fonctionnelle | Radar 2 | Toucher la pastille (ou « Fausse alerte » dans l'alerte Android Auto) masque la zone sur ce téléphone ; Réglages › Radars : nombre de zones masquées, tout réafficher |
| Alerte sans itinéraire | + | fonctionnelle | 1.0 | « Conduite libre » : zones devant soi (axe du cap), limitation par map matching espacé (public : ≥ 20 s et 250 m), service de premier plan ; testé JVM, non vérifié sur appareil |
| Désactivation par pays (Suisse) | — | en cours | Radar | `RadarRegulation` : France = zone de danger seule, Suisse et Allemagne = aucune alerte ; sans géofence de pays (les données ne couvrent que la France) |

## 7. Stations, bornes, parkings, aires, ponts, ralentisseurs

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Stations-service + prix (6 carburants) | G | fonctionnelle | 3 | Flux instantané data.economie.gouv.fr, tri prix/distance, fraîcheur affichée (LOT 5) |
| Bornes de recharge (puissance, prix kWh si connu) | G | bloquée | 3 | IRVE transport.data.gouv.fr |
| Parkings (tarifs, hauteur, horaires) | G | bloquée | 3 | OSM + open data villes |
| Aires de repos et de service | G | non commencée | 3 | OSM `highway=services|rest_area` |
| Ponts bas | G/+ | non commencée | 3 | OSM `maxheight` + profil véhicule |
| Ralentisseurs | G | non commencée | 3 | OSM `traffic_calming` |

## 8. Planificateur et tournées

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Multi-destinations (10), réorganisation | G | fonctionnelle | 4 | LOT 6 |
| Optimisation de tournée | + | fonctionnelle | 4 | Valhalla `optimized_route`, repli local (LOT 6) |
| Pauses planifiées, aires | G | en cours | 4 | Rappel vocal après deux heures de conduite (option, Sécurité routière), puis toutes les 30 min ; aires : non commencé |
| Heure d'arrivée souhaitée | G | non commencée | 4 | |

## 9. Stationnement et retour à pied

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Parking proche de la destination | G | bloquée | 3 | |
| Chemin à pied jusqu'à la destination | G | non commencée | 4 | Valhalla `pedestrian` |
| Retrouver son véhicule (position mémorisée) | G | fonctionnelle | 1.0 | Position à l'arrivée d'un guidage voiture / moto, ou « Je suis garé ici » ; ligne « Mon véhicule » sur l'accueil (DataStore) ; itinéraire piéton : non commencé |

## 10. Communauté

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Signalements (9 types) | G | fonctionnelle | 3 | Client complet + schéma Supabase fourni ; actif dès que l'exploitant saisit son projet (LOT 8) |
| Confirmation / démenti, expiration | G | en cours | 3 | Serveur fait ; écran de vote à faire |
| Points communautaires | G | fonctionnelle | 4 | Calculés par le serveur, affichés dans « Mon profil » si le service est configuré (LOT 10) |
| Autres conducteurs sur la carte (décalés) | G | non commencée | 5 | Vie privée |

## 11. Gamification

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Profil, km parcourus, temps, trajets | G | fonctionnelle | 4 | « Mes trajets » (LOT 9), « Mon profil » (LOT 10) |
| Classement, ligues mensuelles, défis, trophées, avatars | G | en cours | 4 | Trophées locaux et classement général (LOT 10) ; ligues, défis, avatars : serveur à écrire |

## 12. Fonctions « + »

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Profils véhicule PL / taxi / dimensions | + | fonctionnelle | 4 | LOT 7 ; un seul profil enregistré |
| Partage de trajet en direct | + | non commencée | 4 | Supabase + page web |
| Rappel péage flux libre | + | fonctionnelle | 1.0 | Portiques OSM `toll_gantry` du profil du tracé ; annonce au premier passage, rappel sur l'accueil (France : échéance 72 h) jusqu'à « C'est payé » ; aucun montant ; testé JVM |
| Alerte gare de péage, feux tricolores | + | fonctionnelle | 1.1 | Nœuds OSM `toll_booth` et `traffic_signal` du profil `trace_attributes` ; gares voisines fusionnées (400 m) ; annonce « Péage dans 1 km » une fois par gare (1,2 km → 300 m), Horizon (prochain feu à moins d'1 km), carte d'identité du trajet ; contrôle réel Toulouse → Carcassonne : 2 gares |
| Contournement des routes fermées | + | fonctionnelle | 1.1 | Fermetures Bison Futé en cours dans le sens du trajet : nouveau calcul avec `exclude_polygons` (carrés de 80 m), deux passes au plus, tracé d'origine gardé sans autre chemin ; pastille « Route fermée évitée » ; s'applique aussi au recalcul en guidage et à Android Auto ; contrôle réel sur les fermetures du 2026-11-09 (D8, N122) |
| Stations les moins chères sur le trajet | + | fonctionnelle | 1.1 | Guidage › panneau déplié : flux officiel des prix interrogé par cercles de 9 km tous les 15 km sur les 80 prochains km ; stations à moins de 1,5 km du tracé, devant soi, triées par prix du carburant du véhicule (gazole sans profil), détour ≈ 2 × l'écart ; un appui ajoute l'arrêt ; véhicule électrique : message (pas de prix de recharge publiés) ; testé JVM (le flux n'est pas joignable depuis l'environnement de développement) |
| Repères « au feu » | + | fonctionnelle | 1.2 | Manœuvre de changement de direction dont le point a un feu OSM (`edge.traffic_signal` / `node.traffic_signal`, 40 m avant → 8 m après) : « Au feu, tournez… » aux trois paliers, badge « au feu » sur le bandeau ; contrôle réel Toulouse (Capitole → Purpan : 12 feux, « Au feu, tournez à droite dans Place du Pont Neuf ») |
| Alerte vocale virage serré | + | fonctionnelle | 1.2 | Virages ≥ 60° hors carrefour (`CurveDetector`, côté du virage) annoncés une fois ~8 s avant (120–350 m), seulement au-dessus de 50 km/h ; testé JVM |
| Replay des trajets | + | fonctionnelle | 4 | LOT 9 |
| Historique des dépenses | + | non commencée | 4 | |
| Personnalisation (teintes, icône, silhouettes) | + | en cours | 5 | 5 teintes de cockpit (LOT 10) ; icône, silhouettes : non commencées |

## 13. Paramètres

| Réglage | Statut | Notes |
|---|---|---|
| Apparence (clair / sombre / auto) | fonctionnelle | |
| Véhicule (type, carburant, conso, prix, gabarit, Crit'Air) | fonctionnelle | LOT 7 |
| Fournisseurs (URLs) | fonctionnelle | Validation des URLs ; HTTPS requis |
| Données locales (effacer historique / favoris) | fonctionnelle | |
| Confidentialité (texte) | fonctionnelle | |
| À propos / licences | fonctionnelle | |
| Voix et sons, volume, mode Silence | fonctionnelle | Voix, volume 0–100, son de dépassement, « Tester la voix » ; Silence sur l'écran de guidage |
| Alertes de vitesse (tolérance 0/5/10 km/h) | fonctionnelle | Réglage fait ; l'alerte ne se déclenche que si une limitation est connue (voir « Limitation de vitesse », bloquée) |
| Alertes de vigilance | fonctionnelle | Zones, bip, mise à jour manuelle, pluie (LOT 4) |
| Préférences d'itinéraire (péages, autoroutes, ferries) | fonctionnelle | `use_tolls/use_highways/use_ferry = 0`, 3 variantes et recalcul |
| Domicile / travail dans les réglages | en cours | Définis via la fiche lieu ; pas d'écran dédié |
| Roadly+ (architecture gratuit / premium) | fonctionnelle | `Entitlements`, écran Roadly+ ; tout inclus tant qu'aucune boutique n'est intégrée (LOT 11) |
| Achat, essai, restauration (Google Play Billing) | non commencée | `BillingProvider` prêt ; produit Play à créer par l'exploitant (`docs/PREMIUM.md`) |
| Assistance (bug / idée / autre) | fonctionnelle | Partage Android d'un rapport pré-rempli (LOT 11) ; fil des demandes : nécessite un compte |
| Compte | non commencée | Exige le backend Supabase configuré |

## 14. Plateforme

| Fonction | Statut | Notes |
|---|---|---|
| APK debug installable | fonctionnelle | ~116 Mo (toutes ABI) ; App Bundle en Phase 5 ; installation sur appareil non refaite depuis la Phase 2 |
| Tests unitaires | fonctionnelle | 728 tests (JVM), tous verts le 2026-11-05 |
| Tests instrumentés | en cours | Non exécutés pour la Phase 2 : le HONOR Magic 8 Pro n'était plus visible par ADB (Task 12) |
| Service de premier plan / notification | fonctionnelle | Type `location`, notification (manœuvre, distance, arrivée, Arrêter), arrêt à l'arrivée ; vérifié sur appareil (type location, notification LOW, Arrêter depuis la notification) |
| Widget (domicile, travail, rechercher) | fonctionnelle | 4×1, jour / nuit (LOT 12) |
| Raccourcis statiques, intents geo: / google.navigation: | fonctionnelle | `ExternalIntents`, 4 tests (LOT 12) |
| Android Auto | en cours | Code livré : `feature:car` (Car App 1.7.0, navigation) : accueil, recherche, itinéraires, guidage, conduite libre, zones de danger et limitation sur la carte, « Navigue vers » ; même session que le téléphone. Testé JVM, **non vérifié sur DHU ni en voiture** ; hors Play Store : « Sources inconnues » d'Android Auto requis (`docs/ANDROID-AUTO.md`) |
| Backend Supabase | fonctionnelle | Schéma + client (LOT 8) ; projet à créer par l'exploitant |

## Mode trottinette électrique (LOT 13, 2026-11-01)

Détail, sources et limites : `docs/SCOOTER.md`. « Code livré » = compilé et testé sur JVM dans l'environnement de développement ; les écrans Compose restent à compiler et vérifier sur le HONOR.

| Fonction | Statut | Notes |
|---|---|---|
| Sélecteur Voiture / Moto / Trottinette / Vélo, mode mémorisé | en cours | Vélo « Bientôt » (prévu : même costing) ; écran non vérifié sur appareil |
| Routage trottinette (Valhalla `bicycle` paramétré) | en cours | Testé JVM ; préférences voiture jamais transmises |
| Classification piste / bande / voie verte / route partagée / chemin | en cours | `trace_attributes` + tags OSM (Overpass) ; voie verte et agglomération seulement si taguées |
| Vérification réglementaire France (R412-43-1) + registre par pays | en cours | « Autorisé d'après les données », jamais une garantie ; autres pays : non vérifiable |
| Contournement des portions interdites détectées | en cours | `exclude_polygons`, un second calcul |
| Objectifs rapide / sécurisé / confort / court + préférences | en cours | Trafic : aucune source, approximation par classe de route |
| Comparaison (part de pistes, D+/D−, portions partagées, alertes) | en cours | Couverture des données affichée |
| Fiches trottinette (plusieurs), préréglage MAX G2 | en cours | Room v5 ; migration à vérifier sur appareil (`ScooterMigrationTest`) |
| Estimation d'autonomie, batterie saisie, calibration par trajets | en cours | Modèle physique ; aucune lecture de la batterie réelle |
| Guidage trottinette (paliers 200/80/20 m, phrases, alertes vocales) | en cours | Testé JVM (session de guidage) |
| Caméra 3D trottinette, tracé coloré par type de voie | en cours | Compilé contre MapLibre 11.13.5 ; non vu sur appareil |
| Stationnement / recharge trottinette | en cours | Overpass ; uniquement les lieux présents dans OSM |
| Récapitulatif de trajet, bilan semaine / mois | en cours | Dénivelé et énergie estimés |
| Carte hors connexion du trajet | en cours | Tuiles seulement ; calcul et recalcul en ligne |
| Questions vocales en mode trottinette | fonctionnelle | Reconnaissance vocale du système + intentions locales (itinéraire le plus sûr, autonomie) |

## Mises à jour depuis GitHub (2026-11-01)

| Fonction | Statut | Notes |
|---|---|---|
| Vérification des releases GitHub (à l'ouverture ≤ 1/30 min, ou manuelle) | en cours | Dépôt privé : jeton fine-grained lecture seule, chiffré (Keystore) ; testé JVM |
| Téléchargement, contrôle SHA-256, installation PackageInstaller | en cours | Confirmation Android ; sans confirmation possible ensuite (Android 12+) ; non vérifié sur appareil |
| Publication automatique (tag `v*` → APK signé + `update.json`) | en cours | Workflow non encore exécuté ; secrets de signature à créer (`docs/UPDATES.md`) |
