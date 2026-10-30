| Virages à venir | L4 | ✅ | `CurveDetector` : ≥ 45° sur ≤ 90 m hors manœuvres || Horizon (route à venir) | L4 | 🟡 | Manœuvres, virages, zones ; trafic ⛔ || Alertes sonores, bips d'approche | L4 | ✅ | Bip à l'entrée de zone (option) || Zones de vigilance | L4 | ✅ | Jeu officiel « Radars automatiques » (Sécurité routière, data.gouv.fr, Licence Ouverte, millésime 2018) en cache Room ; zones de 300 m / 2 km / 4 km selon la limitation, jamais un point exact ; annonce vocale + bip à l'entrée, badge, Horizon ; France seulement (aucune donnée en Suisse) || 6 | Horizon | L4 | 🟡 | Ruban « route à venir » : manœuvres, arrivée, **virages serrés (courbure du tracé, L4)**, **zones de vigilance (base officielle, L4)**, échelle log 0–5 km. Manque : ralentissements/travaux/fermetures (⛔ trafic), services sur le trajet (L5), densité trafic (⛔) |# SILLAXE_PARITY.md â matrice de paritÃ© Sillaxe â Roadly

RÃ©fÃ©rence : https://sillaxe.app/ et la fiche App Store Â« Sillaxe : GPS & radars Â» (iPhone + CarPlay). DÃ©tail des fonctions vÃ©rifiÃ©es : `docs/SILLAXE-ANALYSE.md`. Sources de donnÃ©es et contraintes : `docs/BENCHMARK-SILLAXE.md`, `docs/API-PROVIDERS.md`.

Roadly reproduit l'**expÃ©rience** (Ã©crans, parcours, densitÃ© d'information, interactions) avec son identitÃ© graphique propre. Aucune ressource, aucun code, aucun Ã©lÃ©ment de marque de Sillaxe n'est repris.

RÃ¨gle absolue : **aucune fonction fictive**. Un Ã©cran n'est Â« â Â» que s'il est branchÃ© sur une source rÃ©elle ; sans source, il est Â« â Â» et l'interface le dit (Â« BientÃ´t Â», Â« â Â», explication de ce qui manque).

LÃ©gende des statuts :

| Statut | Sens |
|---|---|
| â | fonctionnelle, testÃ©e, branchÃ©e sur une source rÃ©elle |
| ð¡ | partielle (une partie des sous-fonctions, ou non vÃ©rifiÃ©e en conduite) |
| â | bloquÃ©e par une source externe non configurÃ©e (code prÃªt ou interface prÃªte) |
| â¬ | non commencÃ©e |

Lots (section 15 de la mission) : L1 design system Â· L2 accueil/carte/recherche Â· L3 itinÃ©raires et navigation avancÃ©e Â· L4 Horizon, trafic, alertes Â· L5 carburants, bornes, parkings Â· L6 planificateur et tournÃ©es Â· L7 profils vÃ©hicules Â· L8 communautÃ© Â· L9 compte, statistiques, Replay Â· L10 gamification et personnalisation Â· L11 abonnement et premium Â· L12 Android Auto et finitions.

Mise Ã  jour : 2026-10-30 â audit initial puis LOT 1 (design system). Voir Â« Journal Â» en bas.

---

## 0. Audit du projet existant (2026-10-30)

PrÃ©servÃ© tel quel (ne pas dÃ©grader) :

- `core:navigation` : `NavigationSession`, `RouteTracker`, `AnnouncementPolicy`, `SpeedSmoother`, `NavigationService` (premier plan `location`), notification avec Â« ArrÃªter Â», `AndroidSpeechService` (TTS fr-FR, focus audio), `ValhallaTraceSpeedLimitProvider`.
- 224 tests unitaires JVM (`testDebugUnitTest :core:model:test :core:common:test`) et 3 tests instrumentÃ©s verts sur HONOR Magic 8 Pro (`FavoriteDaoTest` Ã2, `GlassSearchBarTest` Ã1).
- Modules (15) : `app`, `core:{model,common,designsystem,data,location,map,geocoding,routing,navigation}`, `feature:{home,search,route,settings,navigation}`.
- Fournisseurs sans clÃ© : BAN + Photon (gÃ©ocodage), Valhalla FOSSGIS (routage, fair-use), styles MapLibre (URL dans les rÃ©glages). Limitation de vitesse et alerte de dÃ©passement : code prÃªt, actives seulement avec un Valhalla auto-hÃ©bergÃ©.

Constats de l'audit (Ã©carts avec Sillaxe) :

1. Interface : composants Â« verre Â» corrects mais gÃ©nÃ©riques (icÃ´nes Material brutes, panneaux empilÃ©s sans hiÃ©rarchie, pas de bandeau Horizon, pas de barre d'information de guidage, badge vitesse peu lisible, fiche lieu qui dÃ©borde, tracÃ© masquÃ© par les cartes d'itinÃ©raire).
2. Pas de bouton de signalement sur l'Ã©cran de guidage, pas de bouton Â« replier Â» le bandeau, pas de boussole.
3. Ãcran d'itinÃ©raires : liste de cartes au lieu du panneau Â« Votre choix Â» (durÃ©e en grand, arrivÃ©e, distance, alternatives compactes, Â« Ajouter un arrÃªt Â», bouton Â« Rouler Â»).
4. Aucun des Ã©crans 6 Ã  25 de la mission n'existe (Horizon, signalements, stations, bornes, parkings, planificateur, arrÃªts, profil, classements, ligues, dÃ©fis, trophÃ©es, historique, replay, personnalisation, profils vÃ©hicules multiples, abonnement, notifications, assistance).
5. Pas de backend, pas de compte, pas de stockage des trajets.
6. Pas de mode paysage vÃ©rifiÃ©, pas d'Android Auto, pas de widgets, pas de raccourcis, pas de commandes vocales.

---

## 1. Ãcrans (25) â Ã©tat de reproduction

| # | Ãcran Sillaxe | Lot | Statut | Ãtat Roadly / ce qui manque |
|---|---|---|---|---|
| 1 | Accueil avec carte | L2 | ð¡ | Carte, position, recherche, domicile/travail, recentrage, **boussole (L2)**, **bouton Signaler (L2, envoi L8)**, **toucher sa position â copier/partager (L2)**, **puces Stations/Bornes/Parkings/Aires â recherche Ã  proximitÃ© (L2)**. Manque : carte de profil (L9), autres conducteurs (L8), couches sur la carte (L5) |
| 2 | Recherche | L2 | ð¡ | BAN + Photon + coordonnÃ©es, rÃ©cents, favoris, **catÃ©gories Ã  proximitÃ© (Photon `reverse` + `osm_tag`, triÃ©es par distance, distance affichÃ©e) (L2)**. Manque : horaires d'ouverture (â non fournis par Photon), contacts/calendrier (L6), dictÃ©e (L12) |
| 3 | Fiche de lieu | L2 | ð¡ | Distance, itinÃ©raire, favori, domicile/travail. Manque : horaires, tÃ©lÃ©phone, partage, Â« Enregistrer un repÃ¨re Â», parking Ã  proximitÃ©, mise en page Sillaxe |
| 4 | Choix des itinÃ©raires | L3 | ð¡ | 3 variantes, carte, coÃ»t carburant, panneau Â« Votre choix Â» (L1), **Â« Partir Ã  Â» / Â« Arriver Ã  Â» (L3)**, **puces Ã©viter pÃ©ages / autoroutes / ferries (L3)**. Manque : montant des pÃ©ages (â), trafic (â), Â« Ajouter un arrÃªt Â» (L6), feux/ralentisseurs comptÃ©s (â donnÃ©es) |
| 5 | Navigation GPS | L3 | ð¡ | Bandeau repliable, voies, Horizon, barre ETA Â· min Â· km, vitesse, arrivÃ©e, recalcul, GPS perdu, notification, TTS, Signaler, paysage, **Â« Changer d'itinÃ©raire Â» en cours de trajet (L3)**. Manque : limitation (â Valhalla auto-hÃ©bergÃ©), ajout d'arrÃªt (L6), autres conducteurs (L8) |
| 6 | Horizon | L4 | ð¡ | Ruban Â« route Ã  venir Â» livrÃ© (L1) : manÅuvres et arrivÃ©e rÃ©elles, Ã©chelle log 0â5 km. Manque : virages (courbure), ralentissements/travaux/fermetures/zones/services (â sources) |
| 7 | Signalements | L8 | ð¡ | Bouton Â« Signaler Â» en guidage + feuille des 9 types (dÃ©sactivÃ©s, explication). Manque : envoi, confirmation, invalidation, expiration, carte, points, historique, modÃ©ration (backend Supabase) |
| 8 | Stations-service | L5 | ð¡ | Liste Ã  proximitÃ© (OSM via Photon, L2). â prix : data.economie.gouv.fr (flux instantanÃ©, Licence Ouverte) ; comparaison, dÃ©tour, horaires |
| 9 | Bornes Ã©lectriques | L5 | ð¡ | Liste Ã  proximitÃ© (OSM, L2). â puissance/connecteurs/prix : IRVE consolidÃ© transport.data.gouv.fr ; disponibilitÃ© temps rÃ©el non fournie gratuitement |
| 10 | Parkings | L5 | ð¡ | Liste Ã  proximitÃ© (OSM `amenity=parking`, L2). â tarifs/hauteur/places : open data villes (BNLS) |
| 11 | Planificateur de trajet | L6 | â¬ | Jusqu'Ã  10 arrÃªts, rÃ©organisation, optimisation, pauses, Â« arriver Ã  Â» |
| 12 | Gestion des arrÃªts | L6 | â¬ | Ajout pendant le guidage, rÃ©ordonner, supprimer, mode tournÃ©e |
| 13 | Profil utilisateur | L9 | â¬ | Pseudonyme, avatar, banniÃ¨re, km, trajets, points, public/privÃ©/masquÃ© |
| 14 | Classements | L10 | â¬ | Mois, tous temps, amis (backend) |
| 15 | Ligues | L10 | â¬ | Ligues mensuelles, promotion/relÃ©gation |
| 16 | DÃ©fis | L10 | â¬ | DÃ©fis hebdomadaires |
| 17 | TrophÃ©es | L10 | â¬ | TrophÃ©es locaux (pays traversÃ©s, km, sÃ©ries) |
| 18 | Historique | L9 | â¬ | Trajets locaux (tracÃ©, date, distance, durÃ©e, vitesse moyenne, coÃ»t, arrÃªts, Ã©vÃ©nements) |
| 19 | Replay | L9 | â¬ | Rejeu sur carte, timeline, vitesse Ã0,5âÃ8, Ã©vÃ©nements |
| 20 | ParamÃ¨tres | L2/L11 | ð¡ | VÃ©hicule, apparence, voix et sons, alertes vitesse, prÃ©fÃ©rences d'itinÃ©raire, donnÃ©es, confidentialitÃ©, fournisseurs, Ã  propos. Manque : organisation Sillaxe (groupes, lignes avec icÃ´nes), compte, notifications, autorisations, choix publicitaires, assistance |
| 21 | Personnalisation | L10 | â¬ | Teinte du cockpit, icÃ´ne alternative, avatars, silhouettes de vÃ©hicule |
| 22 | Profils de vÃ©hicules | L7 | ð¡ | 1 profil (type, carburant, conso, prix). Manque : plusieurs profils, moto/taxi/PL/VUL/VE, gabarit, essieux, Crit'Air, influence sur le routage |
| 23 | Abonnement et premium | L11 | â¬ | Architecture gratuit/premium (`Entitlements`), Ã©cran de prÃ©sentation, gestion |
| 24 | Centre de notifications | L11 | â¬ | Rappels de dÃ©part, pÃ©ages flux libre, dÃ©fis, amis |
| 25 | Assistance | L11 | â¬ | Demandes (bug / idÃ©e / autre), fil Â« Mes demandes Â», Ã©tat du service |

---

## 2. Navigation (mission Â§4)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| GPS temps rÃ©el | â | â | FusedLocation, 1 Hz en guidage |
| Guidage vocal | â | â | TTS fr-FR, 3 paliers, anti-rÃ©pÃ©tition |
| Bandeau de direction | L3 | â | Ã restyler (icÃ´ne en tuile, distance en grand, rue) |
| Indication des voies | L3 | ð¡ | DÃ©codage et affichage faits ; aucune voie dans les rÃ©ponses FOSSGIS observÃ©es |
| Sorties de rond-point | â | â | Pastille Â« 2e sortie Â», `exit_number` |
| Compteur de vitesse | L3 | â | Vitesse brute GPS |
| Limitations de vitesse | â | â | `trace_attributes` ; Valhalla auto-hÃ©bergÃ© requis |
| Alertes de dÃ©passement | â | â | Bip + badge rouge ; dÃ©pend de la limitation |
| Zones de vigilance | L4 | â | Base de zones Ã  constituer ; France : jamais un point exact (R413-15) ; Suisse : dÃ©sactivÃ© |
| Alertes sonores, bips d'approche | L4 | â¬ | |
| Horizon (route Ã  venir) | L4 | â¬ | ManÅuvres et virages d'abord, puis trafic/zones |
| Virages Ã  venir | L4 | â¬ | Courbure de la polyligne |
| Ralentissements, travaux, routes fermÃ©es | L4 | â | Bison FutÃ© DATEX II, Autoroutes.fr ; fournisseur Ã  conventionner |
| Recalcul automatique | â | â | Non vÃ©rifiÃ© en conduite rÃ©elle |
| ItinÃ©raire alternatif en cours de trajet | L3 | â | Feuille Â« Changer d'itinÃ©raire Â» : 3 variantes depuis la position projetÃ©e, bascule sans quitter le guidage |
| Changement de destination en guidage | â | â | |
| Ajout d'arrÃªts pendant le guidage | L6 | â¬ | Valhalla multi-locations dÃ©jÃ  supportÃ© |
| Autres conducteurs sur la carte | L8 | â¬ | Backend ; dÃ©calage â¥ 1 km et â¥ 90 s, dÃ©sactivable |
| Perte GPS | â | â | Extrapolation plafonnÃ©e, badge aprÃ¨s 10 s |
| Ãcran verrouillÃ© / arriÃ¨re-plan | â | â | Service de premier plan, notification |

## 3. Trafic et communautÃ© (Â§5)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| 9 types de signalement | L8 | â¬ | Accident, bouchon, obstacle, route fermÃ©e, travaux, mÃ©tÃ©o, nid-de-poule, feu de chantier, danger |
| CrÃ©ation rapide (bouton sur le guidage) | L8 | â¬ | Interface en L3, envoi en L8 |
| Confirmation / invalidation / expiration | L8 | â¬ | Supabase + Edge Function d'expiration |
| Affichage sur la carte | L8 | â¬ | Couche MapLibre |
| Points, historique des contributions | L8/L10 | â¬ | Calcul cÃ´tÃ© serveur, anti-triche |
| ModÃ©ration, dÃ©tection des incohÃ©rences | L8 | â¬ | |
| Autres utilisateurs sur la carte | L8 | â¬ | ReprÃ©sentation discrÃ¨te, dÃ©calÃ©e |

## 4. DonnÃ©es routiÃ¨res et services (Â§6)

| DonnÃ©e | Lot | Statut | Source retenue |
|---|---|---|---|
| Carburants SP95/SP98/E10/E85/Gazole/GPL, prix sur la carte, comparaison, dÃ©tour, horaires | L5 | â | data.economie.gouv.fr `prix-carburants-fichier-instantane-test-ods-copie` (Licence Ouverte, ~10 min) |
| Bornes : puissance, connecteurs, prix kWh, disponibilitÃ© | L5 | â | IRVE consolidÃ© (transport.data.gouv.fr) ; disponibilitÃ© : non disponible sans contrat |
| Aires de repos et de service | L5 | â¬ | OSM `highway=services|rest_area` (Overpass) |
| Parkings gratuits/payants | L5 | â | OSM + BNLS |
| Ponts bas | L7 | â¬ | OSM `maxheight` + profil vÃ©hicule |
| Ralentisseurs | L4 | â¬ | OSM `traffic_calming` |
| PÃ©ages, pÃ©ages en flux libre | L3/L11 | â | Pas d'API publique ; `hasToll` Valhalla seulement ; montant jamais inventÃ© |
| ZFE | L7 | â¬ | Source Ã  identifier (donnÃ©es ZFE communales) |
| Date de mise Ã  jour affichÃ©e | L5 | â¬ | Obligatoire sur chaque donnÃ©e affichÃ©e |

## 5. ItinÃ©raires et planification (Â§7)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Comparaison de plusieurs itinÃ©raires | â | â | Rapide / court / sans pÃ©age |
| CoÃ»t carburant | â | â | Profil vÃ©hicule saisi ; jamais de prix par dÃ©faut |
| CoÃ»t des pÃ©ages | L3 | â | PrÃ©sence oui/non seulement |
| DurÃ©e, distance, heure d'arrivÃ©e | â | â | |
| Trafic prÃ©vu | L4 | â | |
| DÃ©part planifiÃ© | â | â | DÃ©cale l'heure d'arrivÃ©e |
| ArrivÃ©e Ã  une heure souhaitÃ©e | L3 | â | DÃ©part = arrivÃ©e â durÃ©e du plus rapide (sans trafic prÃ©visionnel) |
| Rappel avant le dÃ©part planifiÃ© | L6 | â¬ | WorkManager |
| Jusqu'Ã  10 arrÃªts, rÃ©organisation manuelle | L6 | â¬ | |
| Optimisation automatique | L6 | â¬ | Valhalla `optimized_route` |
| Pauses, aires, arrÃªts personnalisÃ©s | L6 | â¬ | |
| Mode tournÃ©e | L6 | â¬ | |
| Modifier le trajet sans quitter le guidage | L6 | ð¡ | Changer de destination : fait ; arrÃªts : non |

## 6. Profils de vÃ©hicules (Â§8)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Voiture / moto / taxi / PL / VUL / VE | L7 | ð¡ | `VehicleType` = CITY, SEDAN, SUV, VAN ; Ã  Ã©tendre |
| Hauteur, largeur, longueur, poids, essieux | L7 | â¬ | Valhalla `truck` costing (`height`, `width`, `length`, `weight`, `axle_load`) |
| Consommation, type de carburant | â | â | |
| Crit'Air | L7 | â¬ | |
| Influence rÃ©elle sur les itinÃ©raires | L7 | â¬ | Seulement avec `truck` costing et donnÃ©es OSM |
| Restrictions PL, pÃ©ages PL | L7 | â | PÃ©ages PL : pas de source |

## 7. Premium (Â§9)

| Fonction | Lot | Statut |
|---|---|---|
| Architecture gratuit/premium (`Entitlements`, flags testables) | L11 | â¬ |
| Absence de publicitÃ© | â | â (Roadly n'a pas de publicitÃ©) |
| Zones de vigilance complÃ¨tes, alertes sans trajet | L4 | â |
| Profils vÃ©hicules avancÃ©s | L7 | â¬ |
| ItinÃ©raires temps rÃ©el longs trajets | L4 | â |
| Mode tournÃ©e | L6 | â¬ |
| Partage de trajet en direct | L9 | â¬ |
| Rappels pÃ©ages flux libre | L11 | â (gÃ©ofences des portiques Ã  constituer) |
| Replay | L9 | â¬ |
| Synchronisation favoris, domicile, travail | L9 | â¬ |
| Historique kilomÃ©trique, coÃ»t des dÃ©placements | L9 | â¬ |
| Couleurs, icÃ´nes, avatars, silhouettes | L10 | â¬ |

## 8. Gamification (Â§10)

| Fonction | Lot | Statut |
|---|---|---|
| Points, rÃ©compenses | L10 | â¬ (serveur, anti-triche) |
| Classement mensuel / gÃ©nÃ©ral | L10 | â¬ |
| Ligues mensuelles | L10 | â¬ |
| DÃ©fis hebdomadaires | L10 | â¬ |
| TrophÃ©es | L10 | â¬ |
| Profil public, amis | L10 | â¬ |
| Km parcourus, bilan mensuel | L9 | â¬ |
| Avatars, banniÃ¨res, rÃ©compenses Ã  dÃ©bloquer | L10 | â¬ |

## 9. Android (Â§11)

| Fonction | Lot | Statut |
|---|---|---|
| Android Auto (catÃ©gorie navigation) | L12 | â¬ |
| App Shortcuts (domicile, travail, rechercher, signaler) | L12 | â¬ |
| Widgets | L12 | â¬ |
| Notifications (guidage) | â | â |
| Commandes vocales (App Actions : maison, travail, adresse, accident, destination) | L12 | â¬ |
| Intents (geo:, google.navigation:, partage) | L12 | â¬ |
| Partage natif | L2 | â (fiche lieu : copier l'adresse, partager) |
| Mode paysage | L3 | ð¡ (accueil, itinÃ©raires, guidage disposÃ©s en deux colonnes ; guidage vÃ©rifiÃ© sur appareil) |
| Permissions | â | â |
| Localisation en arriÃ¨re-plan, service persistant | â | â |

## 10. Replay et historique (Â§12)

| Fonction | Lot | Statut |
|---|---|---|
| Enregistrement du tracÃ© (Room `trips`, `trip_points`, `trip_events`) | L9 | â¬ |
| Date, distance, durÃ©e, vitesse moyenne, coÃ»t, arrÃªts, Ã©vÃ©nements | L9 | â¬ |
| Replay : carte, timeline, vitesse de lecture, Ã©vÃ©nements | L9 | â¬ |

## 11. QualitÃ© (Â§14)

| Exigence | Statut |
|---|---|
| Aucune fonction fictive, aucun bouton inactif | â (puces Â« BientÃ´t Â» dÃ©sactivÃ©es et marquÃ©es) |
| Aucune donnÃ©e simulÃ©e | â |
| Tous les Ã©crans testÃ©s | ð¡ (ViewModels testÃ©s ; Ã©crans : 1 test Compose) |
| Compilation stable | â |

---

## Journal

- 2026-10-30 â Audit complet, crÃ©ation de ce fichier.
- 2026-10-31 â **LOT 1 livrÃ©** : design system (`docs/DESIGN-SYSTEM.md`) â tokens violet/orange, surfaces opaques et verre, `Overline`/`StatValue`, boutons Primary/Brand/Secondary/Danger, boutons ronds, tuiles d'icÃ´ne, `NavInfoBar`, `HorizonStrip` (Ã©chelle log, 3 tests), `ListRow`/`ToggleRow`/`SegmentedControl`, `RoadlyTopBar`, `RouteHeadline`/`RouteOptionRow`, teintes de cockpit (`CockpitTint`) prÃªtes pour L10 ; halo + liserÃ© du tracÃ© MapLibre ; `fitBounds` Ã  marges distinctes. Ãcrans rÃ©alignÃ©s sur la rÃ©fÃ©rence : accueil (tuiles Domicile/Travail, recentrage rond marque, catÃ©gories Â« BientÃ´t Â»), recherche (catÃ©gories Â« BientÃ´t Â», favoris/rÃ©cents en cartes, fiche lieu avec tuiles d'action + copier/partager rÃ©els), itinÃ©raires (en-tÃªte destination / Â« Partir de Â», panneau Â« Votre choix Â» : durÃ©e en grand, arrivÃ©e, distance, puces, alternatives compactes, Â« Ajouter un arrÃªt Â» BientÃ´t, Â« Rouler Â»), guidage (bandeau repliable, bouton Signaler â feuille des 9 types dÃ©sactivÃ©s avec explication, voix, destination, compteur + panneau de limitation, Horizon alimentÃ© par les manÅuvres rÃ©elles, barre ETA Â· min Â· km Â· recentrer Â· arrÃªter, **mode paysage**), rÃ©glages (groupes, lignes Ã  tuiles d'icÃ´ne, sÃ©lecteurs segmentÃ©s, fournisseurs repliÃ©s). Build `assembleDebug` vert, **230 tests unitaires** verts. **VÃ©rifiÃ© sur HONOR Magic 8 Pro** (Ã  l'arrÃªt, Toulouse â Place du Capitole) : accueil, recherche, fiche lieu, itinÃ©raires, guidage portrait et paysage, feuille Â« Signaler Â» ; aucun `FATAL EXCEPTION` dans logcat.
  - Ãcrans 1, 2, 3, 4, 5, 20 : passent de Â« gÃ©nÃ©rique Â» Ã  Â« fidÃ¨le Â» graphiquement ; leur statut fonctionnel reste ð¡ (voir tableau 1).
  - Ãcran 6 (Horizon) : composant livrÃ© et affichÃ© en guidage avec manÅuvres + arrivÃ©e ; trafic/zones/services restent â (L4).
  - Ãcran 7 (Signalements) : interface de saisie livrÃ©e, envoi â (L8).
- 2026-10-31 â **LOT 2 livrÃ©** (accueil, carte, recherche) : `NearbyPoiProvider` / `PhotonNearbyPoiProvider` (Photon `reverse` + `radius` + `osm_tag`, fixture rÃ©elle, 2 tests) ; recherche : puces Stations / Bornes / Parkings / Aires / Restaurants rÃ©elles (lieux OSM triÃ©s par distance, distance par ligne, Ã©tats Â« position inconnue Â» et Â« aucun lieu Â», pied de carte rappelant l'absence de prix/disponibilitÃ©), route `SearchRoute(category)` ; accueil : boussole animÃ©e (apparaÃ®t quand la carte est tournÃ©e, remet le nord), bouton Signaler (feuille partagÃ©e dans `core:designsystem`), position courante â feuille Copier/Partager (adresse + coordonnÃ©es + lien OSM), puces de catÃ©gories branchÃ©es sur la recherche, Â« Incidents Â» reste Â« BientÃ´t Â». 235 tests verts ; vÃ©rifiÃ© sur HONOR : stations Ã  630 m, parkings Ã  730 m, feuille position, bouton Signaler.
- 2026-10-31 â **LOT 3 livrÃ©** (itinÃ©raires et navigation avancÃ©e) : mode Â« Arriver Ã  Â» (sÃ©lecteur Partir Ã  / Arriver Ã , dÃ©part dÃ©duit du plus rapide, arrivÃ©es recalÃ©es, test `ArrivalShiftTest`) ; puces d'options Ã©viter pÃ©ages / autoroutes / ferries sur le panneau Â« Votre choix Â» (persistÃ©es, appliquÃ©es aux 3 variantes et au recalcul) ; en guidage, bouton Â« ItinÃ©raires Â» â feuille Â« Changer d'itinÃ©raire Â» (3 variantes depuis la position projetÃ©e, arrivÃ©e, pÃ©age, bascule immÃ©diate du guidage ; test VM). 238 tests verts ; vÃ©rifiÃ© sur HONOR (bascule vers Â« Le plus court Â» : 26 min Â· 4,6 km).
- 2026-10-31 — **LOT 4 livré** (Horizon, alertes) : `CurveDetector` (virages serrés par courbure, hors manœuvres) ; zones de vigilance depuis le jeu officiel « Radars automatiques » (data.gouv.fr, Licence Ouverte, 3 350 enregistrements, millésime 2018) téléchargé au premier lancement et tous les 30 jours (Room v2, migration), reconstituées en zones de 300 m / 2 km / 4 km (jamais un point exact), annonce vocale + bip à l'entrée, badge avec limitation, Horizon ; pluie Open-Meteo (toutes les 10 min) avec option « limitation abaissée par pluie » (R413-2) ; réglages « Alertes » (zones, bip, mise à jour manuelle avec compteur et date, pluie). Trafic en direct, travaux, fermetures : toujours ⛔ (aucune source gratuite ; Bison Futé à conventionner). 254 tests verts ; vérifié sur HONOR (3 350 zones chargées, Horizon avec virage + zone sur Toulouse → Montgiscard).
