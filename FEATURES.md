| Tests unitaires | fonctionnelle | 276 tests (JVM) || Km parcourus, temps, trajets | G | fonctionnelle | 4 | « Mes trajets », bilan du mois (LOT 9) || Replay des trajets | + | fonctionnelle | 4 | LOT 9 || Backend Supabase | fonctionnelle | Schéma + client (LOT 8) ; projet à créer par l'exploitant || Tests unitaires | fonctionnelle | 272 tests (JVM) || Confirmation / démenti, expiration | G | en cours | 3 | Serveur fait ; écran de vote à faire || Signalements (9 types) | G | fonctionnelle | 3 | Client complet + schéma Supabase fourni ; actif dès que l'exploitant saisit son projet (LOT 8) || Tests unitaires | fonctionnelle | 268 tests (JVM) || Véhicule (type, carburant, conso, prix, gabarit, Crit'Air) | fonctionnelle | LOT 7 || Profils véhicule PL / taxi / dimensions | + | fonctionnelle | 4 | LOT 7 ; un seul profil enregistré || Itinéraire adapté au gabarit (hauteur/largeur/poids) | + | fonctionnelle | 4 | Valhalla `truck` avec le gabarit saisi (LOT 7) || Optimisation de tournée | + | fonctionnelle | 4 | Valhalla `optimized_route`, repli local (LOT 6) || Multi-destinations (10), réorganisation | G | fonctionnelle | 4 | LOT 6 || Jusqu'à 10 arrêts réordonnables | G | fonctionnelle | 4 | Écran d'itinéraires (LOT 6) || Changer de destination / ajouter un arrêt en guidage | G | fonctionnelle | 2 | Destination, itinéraire alternatif, ajout d'arrêt (LOT 6) || Tests unitaires | fonctionnelle | 264 tests (JVM) || Stations-service + prix (6 carburants) | G | fonctionnelle | 3 | Flux instantané data.economie.gouv.fr, tri prix/distance, fraîcheur affichée (LOT 5) || Tests unitaires | fonctionnelle | 254 tests (JVM) || Alertes visuelles / vocales / sonores anticipées | G/+ | fonctionnelle | 2 | Badge, voix, bip à l'entrée de zone (LOT 4) || Zones de vigilance (France, jamais un point exact) | G | fonctionnelle | 2 | Jeu officiel data.gouv.fr (2018) en cache Room, zones 300 m–4 km, annonce + bip (LOT 4) || Limitation abaissée par pluie (option) | G | fonctionnelle | 2 | Open-Meteo (CC BY 4.0, sans clé) ; effet visible seulement si une limitation est connue (LOT 4) |# FEATURES.md — statut des fonctionnalités Roadly

Référentiel : `docs/SILLAXE-ANALYSE.md` (fonctions vérifiées sur les pages publiques de Sillaxe) et `docs/BENCHMARK-SILLAXE.md` (sources de données et contraintes). Statuts : **non commencée** · **en cours** · **fonctionnelle** · **bloquée** (dépend d'une source externe non configurée). « Palier Sillaxe » = gratuit (G) ou Sillaxe+ (+) chez Sillaxe ; Roadly ne reproduit pas le modèle économique, l'information sert à prioriser.

Mise à jour : 2026-10-31 (LOT 1 design system, branche `feat/roadly-v1`). La matrice de parité complète avec Sillaxe est dans `SILLAXE_PARITY.md` (référence pour les lots suivants) ; le design system est décrit dans `docs/DESIGN-SYSTEM.md`.

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
| Limitation de vitesse de la route | G | bloquée | 2 | Implémentée (`trace_attributes`, ≤ 1/5 s) ; active seulement avec un Valhalla auto-hébergé ; « — » sur FOSSGIS |
| Alerte sonore de dépassement (option) | G | bloquée | 2 | Implémentée (bip + badge rouge, tolérance 0/5/10 km/h) ; dépend de la limitation |
| Limitation abaissée par pluie (option) | G | non commencée | 3 | Dépend météo |
| Changer de destination / ajouter un arrêt en guidage | G | en cours | 2 | Changer de destination : fait ; ajout d'arrêt : Phase 4 |
| Jusqu'à 10 arrêts réordonnables | G | non commencée | 4 | Valhalla multi-locations déjà supporté côté provider |
| Départ planifié (heure de départ) | G | fonctionnelle | 1 | Décale seulement l'heure d'arrivée |
| « Arriver à l'heure » | G | fonctionnelle | 1 | Départ déduit de la durée du plus rapide, sans trafic (LOT 3) ; rappel avant départ : Phase 4 |
| Itinéraire alternatif en cours de guidage | G | fonctionnelle | 2 | Feuille « Changer d'itinéraire » depuis la position projetée (LOT 3) |
| Options d'itinéraire sur l'écran de choix | G | fonctionnelle | 1 | Puces éviter péages / autoroutes / ferries (LOT 3) |
| Mode paysage | G | en cours | 1 | Accueil, itinéraires et guidage en deux colonnes (LOT 1) ; non vérifié sur appareil |
| Thème clair / sombre / automatique | G | fonctionnelle | 1 | Auto solaire (position) + système |
| Mode Silence | G | fonctionnelle | 2 | Bouton voix de l'écran de guidage |
| Import GPX | ? | non commencée | 5 | |
| Guidage allégé hors connexion | G | non commencée | 5 | Cache tuiles MapLibre + itinéraire mémorisé |
| ZFE / Crit'Air | ? | non commencée | 3 | Source à identifier |
| Tunnels (perte GPS) | G | fonctionnelle | 2 | Extrapolation sur la polyligne après 3 s sans fix, plafonnée à 150 m / 10 s ; badge « GPS perdu » après 10 s |

## 2. Horizon (route à venir)

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Ruban Horizon : manœuvres, arrivée | G | fonctionnelle | 2 | `HorizonStrip` (LOT 1) : échelle log 0–5 km, 3 prochaines manœuvres + arrivée ; virages par courbure : LOT 4 |
| Ralentissements, bouchons, incidents dans le bandeau | G | bloquée | 3 | Dépend trafic |
| Services (stations, aires) dans le bandeau | G | bloquée | 3 | Dépend POI |
| Zones de vigilance dans le bandeau | G | bloquée | 3 | Dépend base de zones + cadre légal |

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
| Commande vocale (Assistant / App Actions) | G | non commencée | 5 | |
| Favoris synchronisés (web, multi-appareils) | + | non commencée | 4 | Supabase |

## 4. Itinéraires et coûts

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Comparaison visuelle 3 itinéraires sur carte | G | fonctionnelle | 1 | |
| Coût carburant (profil véhicule saisi) | G | fonctionnelle | 1 | Jamais de prix par défaut |
| Prix des péages | G | bloquée | 3 | Pas d'API publique ; barème à maintenir |
| Itinéraire « économique » | G | non commencée | 3 | Valhalla : coût combiné distance/péage/conso |
| Trafic pris en compte dans le calcul | G | bloquée | 3 | Fournisseur trafic |
| Itinéraire adapté au gabarit (hauteur/largeur/poids) | + | non commencée | 4 | Valhalla `truck` |

## 5. Trafic et incidents

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Bouchons, accidents, travaux, fermetures (sources officielles) | G | bloquée | 3 | Bison Futé DATEX II, Autoroutes.fr ; aucun trafic simulé |
| Routes coupées évitées | G | bloquée | 3 | `exclude_polygons` Valhalla |
| Re-routage sur bouchon | G | bloquée | 3 | |
| Météo sur la carte | G | bloquée | 3 | Météo-France / Open-Meteo |
| ETA par apprentissage collectif | G | non commencée | 5 | Backend, opt-in |

## 6. Zones de vigilance

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Zones de vigilance (France, jamais un point exact) | G | bloquée | 3 | Base de zones à constituer ; art. R413-15 |
| Alertes visuelles / vocales / sonores anticipées | G/+ | non commencée | 3 | |
| Limitation de la zone | + | bloquée | 3 | |
| Alerte sans itinéraire | + | non commencée | 3 | Service premier plan |
| Désactivation par pays (Suisse) | — | non commencée | 3 | Géofence pays |

## 7. Stations, bornes, parkings, aires, ponts, ralentisseurs

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Stations-service + prix (6 carburants) | G | bloquée | 3 | data.economie.gouv.fr flux instantané ; puce « Bientôt » en v1 |
| Bornes de recharge (puissance, prix kWh si connu) | G | bloquée | 3 | IRVE transport.data.gouv.fr |
| Parkings (tarifs, hauteur, horaires) | G | bloquée | 3 | OSM + open data villes |
| Aires de repos et de service | G | non commencée | 3 | OSM `highway=services|rest_area` |
| Ponts bas | G/+ | non commencée | 3 | OSM `maxheight` + profil véhicule |
| Ralentisseurs | G | non commencée | 3 | OSM `traffic_calming` |

## 8. Planificateur et tournées

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Multi-destinations (10), réorganisation | G | non commencée | 4 | |
| Optimisation de tournée | + | non commencée | 4 | Valhalla `optimized_route` |
| Pauses planifiées, aires | G | non commencée | 4 | |
| Heure d'arrivée souhaitée | G | non commencée | 4 | |

## 9. Stationnement et retour à pied

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Parking proche de la destination | G | bloquée | 3 | |
| Chemin à pied jusqu'à la destination | G | non commencée | 4 | Valhalla `pedestrian` |
| Retrouver son véhicule (position mémorisée) | G | non commencée | 4 | Local |

## 10. Communauté

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Signalements (accident, bouchon, obstacle, route fermée, nid-de-poule, feu de chantier, travaux, ralentisseur, chaussée dégradée) | G | bloquée | 3 | Backend Supabase à créer |
| Confirmation / démenti, expiration | G | bloquée | 3 | |
| Points communautaires | G | non commencée | 4 | |
| Autres conducteurs sur la carte (décalés) | G | non commencée | 5 | Vie privée |

## 11. Gamification

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Profil, km parcourus, temps, trajets | G | non commencée | 4 | Table `trips` locale |
| Classement, ligues mensuelles, défis, trophées, avatars | G | non commencée | 4 | Backend ; facultatif |

## 12. Fonctions « + »

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Profils véhicule PL / taxi / dimensions | + | non commencée | 4 | |
| Partage de trajet en direct | + | non commencée | 4 | Supabase + page web |
| Rappel péage flux libre | + | non commencée | 4 | Géofences des portiques (données à constituer) |
| Replay des trajets | + | non commencée | 4 | Traces locales |
| Historique des dépenses | + | non commencée | 4 | |
| Personnalisation (teintes, icône, silhouettes) | + | non commencée | 5 | |

## 13. Paramètres

| Réglage | Statut | Notes |
|---|---|---|
| Apparence (clair / sombre / auto) | fonctionnelle | |
| Véhicule (type, carburant, conso, prix) | fonctionnelle | Dimensions : Phase 4 |
| Fournisseurs (URLs) | fonctionnelle | Validation des URLs ; HTTPS requis |
| Données locales (effacer historique / favoris) | fonctionnelle | |
| Confidentialité (texte) | fonctionnelle | |
| À propos / licences | fonctionnelle | |
| Voix et sons, volume, mode Silence | fonctionnelle | Voix, volume 0–100, son de dépassement, « Tester la voix » ; Silence sur l'écran de guidage |
| Alertes de vitesse (tolérance 0/5/10 km/h) | fonctionnelle | Réglage fait ; l'alerte ne se déclenche que si une limitation est connue (voir « Limitation de vitesse », bloquée) |
| Alertes de vigilance | non commencée | Phase 3 |
| Préférences d'itinéraire (péages, autoroutes, ferries) | fonctionnelle | `use_tolls/use_highways/use_ferry = 0`, 3 variantes et recalcul |
| Domicile / travail dans les réglages | en cours | Définis via la fiche lieu ; pas d'écran dédié |
| Compte, abonnement, assistance | non commencée | Phase 4 (Supabase) |

## 14. Plateforme

| Fonction | Statut | Notes |
|---|---|---|
| APK debug installable | fonctionnelle | ~116 Mo (toutes ABI) ; App Bundle en Phase 5 ; installation sur appareil non refaite depuis la Phase 2 |
| Tests unitaires | fonctionnelle | 238 tests (JVM) |
| Tests instrumentés | en cours | Non exécutés pour la Phase 2 : le HONOR Magic 8 Pro n'était plus visible par ADB (Task 12) |
| Service de premier plan / notification | fonctionnelle | Type `location`, notification (manœuvre, distance, arrivée, Arrêter), arrêt à l'arrivée ; vérifié sur appareil (type location, notification LOW, Arrêter depuis la notification) |
| Widgets, Android Auto | non commencée | Phase 5 |
| Backend Supabase | non commencée | Phase 3 |
