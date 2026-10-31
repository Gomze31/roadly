| Date de mise Ã  jour affichÃ©e | L5 | â | Chaque prix porte Â« il y a X h/j Â» ; zones de vigilance : date de tÃ©lÃ©chargement + millÃ©sime || Bornes : puissance, connecteurs, prix kWh, disponibilitÃ© | L5 | â | IRVE consolidÃ© = CSV national volumineux sans API de proximitÃ© (vÃ©rifiÃ© 2026-10-31) ; nÃ©cessite un import serveur ou un dÃ©coupage ; disponibilitÃ© temps rÃ©el : contrat || Carburants SP95/SP98/E10/E85/Gazole/GPL, prix, comparaison | L5 | â | data.economie.gouv.fr `prix-des-carburants-en-france-flux-instantane-v2` (Licence Ouverte, ~10 min) ; fixture rÃ©elle, 2 tests |
| Prix sur la carte, dÃ©tour, horaires dÃ©taillÃ©s | L5 | â¬ | Couche MapLibre, dÃ©tour via Valhalla, `horaires` JSON du flux || 8 | Stations-service | L5 | ð¡ | **Prix rÃ©els (L5)** : flux instantanÃ© data.economie.gouv.fr (Licence Ouverte, ~10 min), 6 carburants, date de chaque prix, 24h/24, services, tri distance / prix, carburant du profil par dÃ©faut, repli OSM si le flux Ã©choue. Manque : prix sur la carte (couche), calcul du dÃ©tour, horaires dÃ©taillÃ©s (L5 suite) || Virages Ã  venir | L4 | â | `CurveDetector` : â¥ 45Â° sur â¤ 90 m hors manÅuvres || Horizon (route Ã  venir) | L4 | ð¡ | ManÅuvres, virages, zones ; trafic â || Alertes sonores, bips d'approche | L4 | â | Bip Ã  l'entrÃ©e de zone (option) || Zones de vigilance | L4 | â | Jeu officiel Â« Radars automatiques Â» (SÃ©curitÃ© routiÃ¨re, data.gouv.fr, Licence Ouverte, millÃ©sime 2018) en cache Room ; zones de 300 m / 2 km / 4 km selon la limitation, jamais un point exact ; annonce vocale + bip Ã  l'entrÃ©e, badge, Horizon ; France seulement (aucune donnÃ©e en Suisse) || 6 | Horizon | L4 | ð¡ | Ruban Â« route Ã  venir Â» : manÅuvres, arrivÃ©e, **virages serrÃ©s (courbure du tracÃ©, L4)**, **zones de vigilance (base officielle, L4)**, Ã©chelle log 0â5 km. Manque : ralentissements/travaux/fermetures (â trafic), services sur le trajet (L5), densitÃ© trafic (â) |# SILLAXE_PARITY.md Ã¢ÂÂ matrice de paritÃÂ© Sillaxe Ã¢ÂÂ Roadly

RÃÂ©fÃÂ©rence : https://sillaxe.app/ et la fiche App Store ÃÂ« Sillaxe : GPS & radars ÃÂ» (iPhone + CarPlay). DÃÂ©tail des fonctions vÃÂ©rifiÃÂ©es : `docs/SILLAXE-ANALYSE.md`. Sources de donnÃÂ©es et contraintes : `docs/BENCHMARK-SILLAXE.md`, `docs/API-PROVIDERS.md`.

Roadly reproduit l'**expÃÂ©rience** (ÃÂ©crans, parcours, densitÃÂ© d'information, interactions) avec son identitÃÂ© graphique propre. Aucune ressource, aucun code, aucun ÃÂ©lÃÂ©ment de marque de Sillaxe n'est repris.

RÃÂ¨gle absolue : **aucune fonction fictive**. Un ÃÂ©cran n'est ÃÂ« Ã¢ÂÂ ÃÂ» que s'il est branchÃÂ© sur une source rÃÂ©elle ; sans source, il est ÃÂ« Ã¢ÂÂ ÃÂ» et l'interface le dit (ÃÂ« BientÃÂ´t ÃÂ», ÃÂ« Ã¢ÂÂ ÃÂ», explication de ce qui manque).

LÃÂ©gende des statuts :

| Statut | Sens |
|---|---|
| Ã¢ÂÂ | fonctionnelle, testÃÂ©e, branchÃÂ©e sur une source rÃÂ©elle |
| Ã°ÂÂÂ¡ | partielle (une partie des sous-fonctions, ou non vÃÂ©rifiÃÂ©e en conduite) |
| Ã¢ÂÂ | bloquÃÂ©e par une source externe non configurÃÂ©e (code prÃÂªt ou interface prÃÂªte) |
| Ã¢Â¬Â | non commencÃÂ©e |

Lots (section 15 de la mission) : L1 design system ÃÂ· L2 accueil/carte/recherche ÃÂ· L3 itinÃÂ©raires et navigation avancÃÂ©e ÃÂ· L4 Horizon, trafic, alertes ÃÂ· L5 carburants, bornes, parkings ÃÂ· L6 planificateur et tournÃÂ©es ÃÂ· L7 profils vÃÂ©hicules ÃÂ· L8 communautÃÂ© ÃÂ· L9 compte, statistiques, Replay ÃÂ· L10 gamification et personnalisation ÃÂ· L11 abonnement et premium ÃÂ· L12 Android Auto et finitions.

Mise ÃÂ  jour : 2026-10-30 Ã¢ÂÂ audit initial puis LOT 1 (design system). Voir ÃÂ« Journal ÃÂ» en bas.

---

## 0. Audit du projet existant (2026-10-30)

PrÃÂ©servÃÂ© tel quel (ne pas dÃÂ©grader) :

- `core:navigation` : `NavigationSession`, `RouteTracker`, `AnnouncementPolicy`, `SpeedSmoother`, `NavigationService` (premier plan `location`), notification avec ÃÂ« ArrÃÂªter ÃÂ», `AndroidSpeechService` (TTS fr-FR, focus audio), `ValhallaTraceSpeedLimitProvider`.
- 224 tests unitaires JVM (`testDebugUnitTest :core:model:test :core:common:test`) et 3 tests instrumentÃÂ©s verts sur HONOR Magic 8 Pro (`FavoriteDaoTest` ÃÂ2, `GlassSearchBarTest` ÃÂ1).
- Modules (15) : `app`, `core:{model,common,designsystem,data,location,map,geocoding,routing,navigation}`, `feature:{home,search,route,settings,navigation}`.
- Fournisseurs sans clÃÂ© : BAN + Photon (gÃÂ©ocodage), Valhalla FOSSGIS (routage, fair-use), styles MapLibre (URL dans les rÃÂ©glages). Limitation de vitesse et alerte de dÃÂ©passement : code prÃÂªt, actives seulement avec un Valhalla auto-hÃÂ©bergÃÂ©.

Constats de l'audit (ÃÂ©carts avec Sillaxe) :

1. Interface : composants ÃÂ« verre ÃÂ» corrects mais gÃÂ©nÃÂ©riques (icÃÂ´nes Material brutes, panneaux empilÃÂ©s sans hiÃÂ©rarchie, pas de bandeau Horizon, pas de barre d'information de guidage, badge vitesse peu lisible, fiche lieu qui dÃÂ©borde, tracÃÂ© masquÃÂ© par les cartes d'itinÃÂ©raire).
2. Pas de bouton de signalement sur l'ÃÂ©cran de guidage, pas de bouton ÃÂ« replier ÃÂ» le bandeau, pas de boussole.
3. ÃÂcran d'itinÃÂ©raires : liste de cartes au lieu du panneau ÃÂ« Votre choix ÃÂ» (durÃÂ©e en grand, arrivÃÂ©e, distance, alternatives compactes, ÃÂ« Ajouter un arrÃÂªt ÃÂ», bouton ÃÂ« Rouler ÃÂ»).
4. Aucun des ÃÂ©crans 6 ÃÂ  25 de la mission n'existe (Horizon, signalements, stations, bornes, parkings, planificateur, arrÃÂªts, profil, classements, ligues, dÃÂ©fis, trophÃÂ©es, historique, replay, personnalisation, profils vÃÂ©hicules multiples, abonnement, notifications, assistance).
5. Pas de backend, pas de compte, pas de stockage des trajets.
6. Pas de mode paysage vÃÂ©rifiÃÂ©, pas d'Android Auto, pas de widgets, pas de raccourcis, pas de commandes vocales.

---

## 1. ÃÂcrans (25) Ã¢ÂÂ ÃÂ©tat de reproduction

| # | ÃÂcran Sillaxe | Lot | Statut | ÃÂtat Roadly / ce qui manque |
|---|---|---|---|---|
| 1 | Accueil avec carte | L2 | Ã°ÂÂÂ¡ | Carte, position, recherche, domicile/travail, recentrage, **boussole (L2)**, **bouton Signaler (L2, envoi L8)**, **toucher sa position Ã¢ÂÂ copier/partager (L2)**, **puces Stations/Bornes/Parkings/Aires Ã¢ÂÂ recherche ÃÂ  proximitÃÂ© (L2)**. Manque : carte de profil (L9), autres conducteurs (L8), couches sur la carte (L5) |
| 2 | Recherche | L2 | Ã°ÂÂÂ¡ | BAN + Photon + coordonnÃÂ©es, rÃÂ©cents, favoris, **catÃÂ©gories ÃÂ  proximitÃÂ© (Photon `reverse` + `osm_tag`, triÃÂ©es par distance, distance affichÃÂ©e) (L2)**. Manque : horaires d'ouverture (Ã¢ÂÂ non fournis par Photon), contacts/calendrier (L6), dictÃÂ©e (L12) |
| 3 | Fiche de lieu | L2 | Ã°ÂÂÂ¡ | Distance, itinÃÂ©raire, favori, domicile/travail. Manque : horaires, tÃÂ©lÃÂ©phone, partage, ÃÂ« Enregistrer un repÃÂ¨re ÃÂ», parking ÃÂ  proximitÃÂ©, mise en page Sillaxe |
| 4 | Choix des itinÃÂ©raires | L3 | Ã°ÂÂÂ¡ | 3 variantes, carte, coÃÂ»t carburant, panneau ÃÂ« Votre choix ÃÂ» (L1), **ÃÂ« Partir ÃÂ  ÃÂ» / ÃÂ« Arriver ÃÂ  ÃÂ» (L3)**, **puces ÃÂ©viter pÃÂ©ages / autoroutes / ferries (L3)**. Manque : montant des pÃÂ©ages (Ã¢ÂÂ), trafic (Ã¢ÂÂ), ÃÂ« Ajouter un arrÃÂªt ÃÂ» (L6), feux/ralentisseurs comptÃÂ©s (Ã¢ÂÂ donnÃÂ©es) |
| 5 | Navigation GPS | L3 | Ã°ÂÂÂ¡ | Bandeau repliable, voies, Horizon, barre ETA ÃÂ· min ÃÂ· km, vitesse, arrivÃÂ©e, recalcul, GPS perdu, notification, TTS, Signaler, paysage, **ÃÂ« Changer d'itinÃÂ©raire ÃÂ» en cours de trajet (L3)**. Manque : limitation (Ã¢ÂÂ Valhalla auto-hÃÂ©bergÃÂ©), ajout d'arrÃÂªt (L6), autres conducteurs (L8) |
| 6 | Horizon | L4 | Ã°ÂÂÂ¡ | Ruban ÃÂ« route ÃÂ  venir ÃÂ» livrÃÂ© (L1) : manÃÂuvres et arrivÃÂ©e rÃÂ©elles, ÃÂ©chelle log 0Ã¢ÂÂ5 km. Manque : virages (courbure), ralentissements/travaux/fermetures/zones/services (Ã¢ÂÂ sources) |
| 7 | Signalements | L8 | Ã°ÂÂÂ¡ | Bouton ÃÂ« Signaler ÃÂ» en guidage + feuille des 9 types (dÃÂ©sactivÃÂ©s, explication). Manque : envoi, confirmation, invalidation, expiration, carte, points, historique, modÃÂ©ration (backend Supabase) |
| 8 | Stations-service | L5 | Ã°ÂÂÂ¡ | Liste ÃÂ  proximitÃÂ© (OSM via Photon, L2). Ã¢ÂÂ prix : data.economie.gouv.fr (flux instantanÃÂ©, Licence Ouverte) ; comparaison, dÃÂ©tour, horaires |
| 9 | Bornes ÃÂ©lectriques | L5 | Ã°ÂÂÂ¡ | Liste ÃÂ  proximitÃÂ© (OSM, L2). Ã¢ÂÂ puissance/connecteurs/prix : IRVE consolidÃÂ© transport.data.gouv.fr ; disponibilitÃÂ© temps rÃÂ©el non fournie gratuitement |
| 10 | Parkings | L5 | Ã°ÂÂÂ¡ | Liste ÃÂ  proximitÃÂ© (OSM `amenity=parking`, L2). Ã¢ÂÂ tarifs/hauteur/places : open data villes (BNLS) |
| 11 | Planificateur de trajet | L6 | Ã¢Â¬Â | Jusqu'ÃÂ  10 arrÃÂªts, rÃÂ©organisation, optimisation, pauses, ÃÂ« arriver ÃÂ  ÃÂ» |
| 12 | Gestion des arrÃÂªts | L6 | Ã¢Â¬Â | Ajout pendant le guidage, rÃÂ©ordonner, supprimer, mode tournÃÂ©e |
| 13 | Profil utilisateur | L9 | Ã¢Â¬Â | Pseudonyme, avatar, banniÃÂ¨re, km, trajets, points, public/privÃÂ©/masquÃÂ© |
| 14 | Classements | L10 | Ã¢Â¬Â | Mois, tous temps, amis (backend) |
| 15 | Ligues | L10 | Ã¢Â¬Â | Ligues mensuelles, promotion/relÃÂ©gation |
| 16 | DÃÂ©fis | L10 | Ã¢Â¬Â | DÃÂ©fis hebdomadaires |
| 17 | TrophÃÂ©es | L10 | Ã¢Â¬Â | TrophÃÂ©es locaux (pays traversÃÂ©s, km, sÃÂ©ries) |
| 18 | Historique | L9 | Ã¢Â¬Â | Trajets locaux (tracÃÂ©, date, distance, durÃÂ©e, vitesse moyenne, coÃÂ»t, arrÃÂªts, ÃÂ©vÃÂ©nements) |
| 19 | Replay | L9 | Ã¢Â¬Â | Rejeu sur carte, timeline, vitesse ÃÂ0,5Ã¢ÂÂÃÂ8, ÃÂ©vÃÂ©nements |
| 20 | ParamÃÂ¨tres | L2/L11 | Ã°ÂÂÂ¡ | VÃÂ©hicule, apparence, voix et sons, alertes vitesse, prÃÂ©fÃÂ©rences d'itinÃÂ©raire, donnÃÂ©es, confidentialitÃÂ©, fournisseurs, ÃÂ  propos. Manque : organisation Sillaxe (groupes, lignes avec icÃÂ´nes), compte, notifications, autorisations, choix publicitaires, assistance |
| 21 | Personnalisation | L10 | Ã¢Â¬Â | Teinte du cockpit, icÃÂ´ne alternative, avatars, silhouettes de vÃÂ©hicule |
| 22 | Profils de vÃÂ©hicules | L7 | Ã°ÂÂÂ¡ | 1 profil (type, carburant, conso, prix). Manque : plusieurs profils, moto/taxi/PL/VUL/VE, gabarit, essieux, Crit'Air, influence sur le routage |
| 23 | Abonnement et premium | L11 | Ã¢Â¬Â | Architecture gratuit/premium (`Entitlements`), ÃÂ©cran de prÃÂ©sentation, gestion |
| 24 | Centre de notifications | L11 | Ã¢Â¬Â | Rappels de dÃÂ©part, pÃÂ©ages flux libre, dÃÂ©fis, amis |
| 25 | Assistance | L11 | Ã¢Â¬Â | Demandes (bug / idÃÂ©e / autre), fil ÃÂ« Mes demandes ÃÂ», ÃÂ©tat du service |

---

## 2. Navigation (mission ÃÂ§4)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| GPS temps rÃÂ©el | Ã¢ÂÂ | Ã¢ÂÂ | FusedLocation, 1 Hz en guidage |
| Guidage vocal | Ã¢ÂÂ | Ã¢ÂÂ | TTS fr-FR, 3 paliers, anti-rÃÂ©pÃÂ©tition |
| Bandeau de direction | L3 | Ã¢ÂÂ | ÃÂ restyler (icÃÂ´ne en tuile, distance en grand, rue) |
| Indication des voies | L3 | Ã°ÂÂÂ¡ | DÃÂ©codage et affichage faits ; aucune voie dans les rÃÂ©ponses FOSSGIS observÃÂ©es |
| Sorties de rond-point | Ã¢ÂÂ | Ã¢ÂÂ | Pastille ÃÂ« 2e sortie ÃÂ», `exit_number` |
| Compteur de vitesse | L3 | Ã¢ÂÂ | Vitesse brute GPS |
| Limitations de vitesse | Ã¢ÂÂ | Ã¢ÂÂ | `trace_attributes` ; Valhalla auto-hÃÂ©bergÃÂ© requis |
| Alertes de dÃÂ©passement | Ã¢ÂÂ | Ã¢ÂÂ | Bip + badge rouge ; dÃÂ©pend de la limitation |
| Zones de vigilance | L4 | Ã¢ÂÂ | Base de zones ÃÂ  constituer ; France : jamais un point exact (R413-15) ; Suisse : dÃÂ©sactivÃÂ© |
| Alertes sonores, bips d'approche | L4 | Ã¢Â¬Â | |
| Horizon (route ÃÂ  venir) | L4 | Ã¢Â¬Â | ManÃÂuvres et virages d'abord, puis trafic/zones |
| Virages ÃÂ  venir | L4 | Ã¢Â¬Â | Courbure de la polyligne |
| Ralentissements, travaux, routes fermÃÂ©es | L4 | Ã¢ÂÂ | Bison FutÃÂ© DATEX II, Autoroutes.fr ; fournisseur ÃÂ  conventionner |
| Recalcul automatique | Ã¢ÂÂ | Ã¢ÂÂ | Non vÃÂ©rifiÃÂ© en conduite rÃÂ©elle |
| ItinÃÂ©raire alternatif en cours de trajet | L3 | Ã¢ÂÂ | Feuille ÃÂ« Changer d'itinÃÂ©raire ÃÂ» : 3 variantes depuis la position projetÃÂ©e, bascule sans quitter le guidage |
| Changement de destination en guidage | Ã¢ÂÂ | Ã¢ÂÂ | |
| Ajout d'arrÃÂªts pendant le guidage | L6 | Ã¢Â¬Â | Valhalla multi-locations dÃÂ©jÃÂ  supportÃÂ© |
| Autres conducteurs sur la carte | L8 | Ã¢Â¬Â | Backend ; dÃÂ©calage Ã¢ÂÂ¥ 1 km et Ã¢ÂÂ¥ 90 s, dÃÂ©sactivable |
| Perte GPS | Ã¢ÂÂ | Ã¢ÂÂ | Extrapolation plafonnÃÂ©e, badge aprÃÂ¨s 10 s |
| ÃÂcran verrouillÃÂ© / arriÃÂ¨re-plan | Ã¢ÂÂ | Ã¢ÂÂ | Service de premier plan, notification |

## 3. Trafic et communautÃÂ© (ÃÂ§5)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| 9 types de signalement | L8 | Ã¢Â¬Â | Accident, bouchon, obstacle, route fermÃÂ©e, travaux, mÃÂ©tÃÂ©o, nid-de-poule, feu de chantier, danger |
| CrÃÂ©ation rapide (bouton sur le guidage) | L8 | Ã¢Â¬Â | Interface en L3, envoi en L8 |
| Confirmation / invalidation / expiration | L8 | Ã¢Â¬Â | Supabase + Edge Function d'expiration |
| Affichage sur la carte | L8 | Ã¢Â¬Â | Couche MapLibre |
| Points, historique des contributions | L8/L10 | Ã¢Â¬Â | Calcul cÃÂ´tÃÂ© serveur, anti-triche |
| ModÃÂ©ration, dÃÂ©tection des incohÃÂ©rences | L8 | Ã¢Â¬Â | |
| Autres utilisateurs sur la carte | L8 | Ã¢Â¬Â | ReprÃÂ©sentation discrÃÂ¨te, dÃÂ©calÃÂ©e |

## 4. DonnÃÂ©es routiÃÂ¨res et services (ÃÂ§6)

| DonnÃÂ©e | Lot | Statut | Source retenue |
|---|---|---|---|
| Carburants SP95/SP98/E10/E85/Gazole/GPL, prix sur la carte, comparaison, dÃÂ©tour, horaires | L5 | Ã¢ÂÂ | data.economie.gouv.fr `prix-carburants-fichier-instantane-test-ods-copie` (Licence Ouverte, ~10 min) |
| Bornes : puissance, connecteurs, prix kWh, disponibilitÃÂ© | L5 | Ã¢ÂÂ | IRVE consolidÃÂ© (transport.data.gouv.fr) ; disponibilitÃÂ© : non disponible sans contrat |
| Aires de repos et de service | L5 | Ã¢Â¬Â | OSM `highway=services|rest_area` (Overpass) |
| Parkings gratuits/payants | L5 | Ã¢ÂÂ | OSM + BNLS |
| Ponts bas | L7 | Ã¢Â¬Â | OSM `maxheight` + profil vÃÂ©hicule |
| Ralentisseurs | L4 | Ã¢Â¬Â | OSM `traffic_calming` |
| PÃÂ©ages, pÃÂ©ages en flux libre | L3/L11 | Ã¢ÂÂ | Pas d'API publique ; `hasToll` Valhalla seulement ; montant jamais inventÃÂ© |
| ZFE | L7 | Ã¢Â¬Â | Source ÃÂ  identifier (donnÃÂ©es ZFE communales) |
| Date de mise ÃÂ  jour affichÃÂ©e | L5 | Ã¢Â¬Â | Obligatoire sur chaque donnÃÂ©e affichÃÂ©e |

## 5. ItinÃÂ©raires et planification (ÃÂ§7)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Comparaison de plusieurs itinÃÂ©raires | Ã¢ÂÂ | Ã¢ÂÂ | Rapide / court / sans pÃÂ©age |
| CoÃÂ»t carburant | Ã¢ÂÂ | Ã¢ÂÂ | Profil vÃÂ©hicule saisi ; jamais de prix par dÃÂ©faut |
| CoÃÂ»t des pÃÂ©ages | L3 | Ã¢ÂÂ | PrÃÂ©sence oui/non seulement |
| DurÃÂ©e, distance, heure d'arrivÃÂ©e | Ã¢ÂÂ | Ã¢ÂÂ | |
| Trafic prÃÂ©vu | L4 | Ã¢ÂÂ | |
| DÃÂ©part planifiÃÂ© | Ã¢ÂÂ | Ã¢ÂÂ | DÃÂ©cale l'heure d'arrivÃÂ©e |
| ArrivÃÂ©e ÃÂ  une heure souhaitÃÂ©e | L3 | Ã¢ÂÂ | DÃÂ©part = arrivÃÂ©e Ã¢ÂÂ durÃÂ©e du plus rapide (sans trafic prÃÂ©visionnel) |
| Rappel avant le dÃÂ©part planifiÃÂ© | L6 | Ã¢Â¬Â | WorkManager |
| Jusqu'ÃÂ  10 arrÃÂªts, rÃÂ©organisation manuelle | L6 | Ã¢Â¬Â | |
| Optimisation automatique | L6 | Ã¢Â¬Â | Valhalla `optimized_route` |
| Pauses, aires, arrÃÂªts personnalisÃÂ©s | L6 | Ã¢Â¬Â | |
| Mode tournÃÂ©e | L6 | Ã¢Â¬Â | |
| Modifier le trajet sans quitter le guidage | L6 | Ã°ÂÂÂ¡ | Changer de destination : fait ; arrÃÂªts : non |

## 6. Profils de vÃÂ©hicules (ÃÂ§8)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Voiture / moto / taxi / PL / VUL / VE | L7 | Ã°ÂÂÂ¡ | `VehicleType` = CITY, SEDAN, SUV, VAN ; ÃÂ  ÃÂ©tendre |
| Hauteur, largeur, longueur, poids, essieux | L7 | Ã¢Â¬Â | Valhalla `truck` costing (`height`, `width`, `length`, `weight`, `axle_load`) |
| Consommation, type de carburant | Ã¢ÂÂ | Ã¢ÂÂ | |
| Crit'Air | L7 | Ã¢Â¬Â | |
| Influence rÃÂ©elle sur les itinÃÂ©raires | L7 | Ã¢Â¬Â | Seulement avec `truck` costing et donnÃÂ©es OSM |
| Restrictions PL, pÃÂ©ages PL | L7 | Ã¢ÂÂ | PÃÂ©ages PL : pas de source |

## 7. Premium (ÃÂ§9)

| Fonction | Lot | Statut |
|---|---|---|
| Architecture gratuit/premium (`Entitlements`, flags testables) | L11 | Ã¢Â¬Â |
| Absence de publicitÃÂ© | Ã¢ÂÂ | Ã¢ÂÂ (Roadly n'a pas de publicitÃÂ©) |
| Zones de vigilance complÃÂ¨tes, alertes sans trajet | L4 | Ã¢ÂÂ |
| Profils vÃÂ©hicules avancÃÂ©s | L7 | Ã¢Â¬Â |
| ItinÃÂ©raires temps rÃÂ©el longs trajets | L4 | Ã¢ÂÂ |
| Mode tournÃÂ©e | L6 | Ã¢Â¬Â |
| Partage de trajet en direct | L9 | Ã¢Â¬Â |
| Rappels pÃÂ©ages flux libre | L11 | Ã¢ÂÂ (gÃÂ©ofences des portiques ÃÂ  constituer) |
| Replay | L9 | Ã¢Â¬Â |
| Synchronisation favoris, domicile, travail | L9 | Ã¢Â¬Â |
| Historique kilomÃÂ©trique, coÃÂ»t des dÃÂ©placements | L9 | Ã¢Â¬Â |
| Couleurs, icÃÂ´nes, avatars, silhouettes | L10 | Ã¢Â¬Â |

## 8. Gamification (ÃÂ§10)

| Fonction | Lot | Statut |
|---|---|---|
| Points, rÃÂ©compenses | L10 | Ã¢Â¬Â (serveur, anti-triche) |
| Classement mensuel / gÃÂ©nÃÂ©ral | L10 | Ã¢Â¬Â |
| Ligues mensuelles | L10 | Ã¢Â¬Â |
| DÃÂ©fis hebdomadaires | L10 | Ã¢Â¬Â |
| TrophÃÂ©es | L10 | Ã¢Â¬Â |
| Profil public, amis | L10 | Ã¢Â¬Â |
| Km parcourus, bilan mensuel | L9 | Ã¢Â¬Â |
| Avatars, banniÃÂ¨res, rÃÂ©compenses ÃÂ  dÃÂ©bloquer | L10 | Ã¢Â¬Â |

## 9. Android (ÃÂ§11)

| Fonction | Lot | Statut |
|---|---|---|
| Android Auto (catÃÂ©gorie navigation) | L12 | Ã¢Â¬Â |
| App Shortcuts (domicile, travail, rechercher, signaler) | L12 | Ã¢Â¬Â |
| Widgets | L12 | Ã¢Â¬Â |
| Notifications (guidage) | Ã¢ÂÂ | Ã¢ÂÂ |
| Commandes vocales (App Actions : maison, travail, adresse, accident, destination) | L12 | Ã¢Â¬Â |
| Intents (geo:, google.navigation:, partage) | L12 | Ã¢Â¬Â |
| Partage natif | L2 | Ã¢ÂÂ (fiche lieu : copier l'adresse, partager) |
| Mode paysage | L3 | Ã°ÂÂÂ¡ (accueil, itinÃÂ©raires, guidage disposÃÂ©s en deux colonnes ; guidage vÃÂ©rifiÃÂ© sur appareil) |
| Permissions | Ã¢ÂÂ | Ã¢ÂÂ |
| Localisation en arriÃÂ¨re-plan, service persistant | Ã¢ÂÂ | Ã¢ÂÂ |

## 10. Replay et historique (ÃÂ§12)

| Fonction | Lot | Statut |
|---|---|---|
| Enregistrement du tracÃÂ© (Room `trips`, `trip_points`, `trip_events`) | L9 | Ã¢Â¬Â |
| Date, distance, durÃÂ©e, vitesse moyenne, coÃÂ»t, arrÃÂªts, ÃÂ©vÃÂ©nements | L9 | Ã¢Â¬Â |
| Replay : carte, timeline, vitesse de lecture, ÃÂ©vÃÂ©nements | L9 | Ã¢Â¬Â |

## 11. QualitÃÂ© (ÃÂ§14)

| Exigence | Statut |
|---|---|
| Aucune fonction fictive, aucun bouton inactif | Ã¢ÂÂ (puces ÃÂ« BientÃÂ´t ÃÂ» dÃÂ©sactivÃÂ©es et marquÃÂ©es) |
| Aucune donnÃÂ©e simulÃÂ©e | Ã¢ÂÂ |
| Tous les ÃÂ©crans testÃÂ©s | Ã°ÂÂÂ¡ (ViewModels testÃÂ©s ; ÃÂ©crans : 1 test Compose) |
| Compilation stable | Ã¢ÂÂ |

---

## Journal

- 2026-10-30 Ã¢ÂÂ Audit complet, crÃÂ©ation de ce fichier.
- 2026-10-31 Ã¢ÂÂ **LOT 1 livrÃÂ©** : design system (`docs/DESIGN-SYSTEM.md`) Ã¢ÂÂ tokens violet/orange, surfaces opaques et verre, `Overline`/`StatValue`, boutons Primary/Brand/Secondary/Danger, boutons ronds, tuiles d'icÃÂ´ne, `NavInfoBar`, `HorizonStrip` (ÃÂ©chelle log, 3 tests), `ListRow`/`ToggleRow`/`SegmentedControl`, `RoadlyTopBar`, `RouteHeadline`/`RouteOptionRow`, teintes de cockpit (`CockpitTint`) prÃÂªtes pour L10 ; halo + liserÃÂ© du tracÃÂ© MapLibre ; `fitBounds` ÃÂ  marges distinctes. ÃÂcrans rÃÂ©alignÃÂ©s sur la rÃÂ©fÃÂ©rence : accueil (tuiles Domicile/Travail, recentrage rond marque, catÃÂ©gories ÃÂ« BientÃÂ´t ÃÂ»), recherche (catÃÂ©gories ÃÂ« BientÃÂ´t ÃÂ», favoris/rÃÂ©cents en cartes, fiche lieu avec tuiles d'action + copier/partager rÃÂ©els), itinÃÂ©raires (en-tÃÂªte destination / ÃÂ« Partir de ÃÂ», panneau ÃÂ« Votre choix ÃÂ» : durÃÂ©e en grand, arrivÃÂ©e, distance, puces, alternatives compactes, ÃÂ« Ajouter un arrÃÂªt ÃÂ» BientÃÂ´t, ÃÂ« Rouler ÃÂ»), guidage (bandeau repliable, bouton Signaler Ã¢ÂÂ feuille des 9 types dÃÂ©sactivÃÂ©s avec explication, voix, destination, compteur + panneau de limitation, Horizon alimentÃÂ© par les manÃÂuvres rÃÂ©elles, barre ETA ÃÂ· min ÃÂ· km ÃÂ· recentrer ÃÂ· arrÃÂªter, **mode paysage**), rÃÂ©glages (groupes, lignes ÃÂ  tuiles d'icÃÂ´ne, sÃÂ©lecteurs segmentÃÂ©s, fournisseurs repliÃÂ©s). Build `assembleDebug` vert, **230 tests unitaires** verts. **VÃÂ©rifiÃÂ© sur HONOR Magic 8 Pro** (ÃÂ  l'arrÃÂªt, Toulouse Ã¢ÂÂ Place du Capitole) : accueil, recherche, fiche lieu, itinÃÂ©raires, guidage portrait et paysage, feuille ÃÂ« Signaler ÃÂ» ; aucun `FATAL EXCEPTION` dans logcat.
  - ÃÂcrans 1, 2, 3, 4, 5, 20 : passent de ÃÂ« gÃÂ©nÃÂ©rique ÃÂ» ÃÂ  ÃÂ« fidÃÂ¨le ÃÂ» graphiquement ; leur statut fonctionnel reste Ã°ÂÂÂ¡ (voir tableau 1).
  - ÃÂcran 6 (Horizon) : composant livrÃÂ© et affichÃÂ© en guidage avec manÃÂuvres + arrivÃÂ©e ; trafic/zones/services restent Ã¢ÂÂ (L4).
  - ÃÂcran 7 (Signalements) : interface de saisie livrÃÂ©e, envoi Ã¢ÂÂ (L8).
- 2026-10-31 Ã¢ÂÂ **LOT 2 livrÃÂ©** (accueil, carte, recherche) : `NearbyPoiProvider` / `PhotonNearbyPoiProvider` (Photon `reverse` + `radius` + `osm_tag`, fixture rÃÂ©elle, 2 tests) ; recherche : puces Stations / Bornes / Parkings / Aires / Restaurants rÃÂ©elles (lieux OSM triÃÂ©s par distance, distance par ligne, ÃÂ©tats ÃÂ« position inconnue ÃÂ» et ÃÂ« aucun lieu ÃÂ», pied de carte rappelant l'absence de prix/disponibilitÃÂ©), route `SearchRoute(category)` ; accueil : boussole animÃÂ©e (apparaÃÂ®t quand la carte est tournÃÂ©e, remet le nord), bouton Signaler (feuille partagÃÂ©e dans `core:designsystem`), position courante Ã¢ÂÂ feuille Copier/Partager (adresse + coordonnÃÂ©es + lien OSM), puces de catÃÂ©gories branchÃÂ©es sur la recherche, ÃÂ« Incidents ÃÂ» reste ÃÂ« BientÃÂ´t ÃÂ». 235 tests verts ; vÃÂ©rifiÃÂ© sur HONOR : stations ÃÂ  630 m, parkings ÃÂ  730 m, feuille position, bouton Signaler.
- 2026-10-31 Ã¢ÂÂ **LOT 3 livrÃÂ©** (itinÃÂ©raires et navigation avancÃÂ©e) : mode ÃÂ« Arriver ÃÂ  ÃÂ» (sÃÂ©lecteur Partir ÃÂ  / Arriver ÃÂ , dÃÂ©part dÃÂ©duit du plus rapide, arrivÃÂ©es recalÃÂ©es, test `ArrivalShiftTest`) ; puces d'options ÃÂ©viter pÃÂ©ages / autoroutes / ferries sur le panneau ÃÂ« Votre choix ÃÂ» (persistÃÂ©es, appliquÃÂ©es aux 3 variantes et au recalcul) ; en guidage, bouton ÃÂ« ItinÃÂ©raires ÃÂ» Ã¢ÂÂ feuille ÃÂ« Changer d'itinÃÂ©raire ÃÂ» (3 variantes depuis la position projetÃÂ©e, arrivÃÂ©e, pÃÂ©age, bascule immÃÂ©diate du guidage ; test VM). 238 tests verts ; vÃÂ©rifiÃÂ© sur HONOR (bascule vers ÃÂ« Le plus court ÃÂ» : 26 min ÃÂ· 4,6 km).
- 2026-10-31 â **LOT 4 livrÃ©** (Horizon, alertes) : `CurveDetector` (virages serrÃ©s par courbure, hors manÅuvres) ; zones de vigilance depuis le jeu officiel Â« Radars automatiques Â» (data.gouv.fr, Licence Ouverte, 3 350 enregistrements, millÃ©sime 2018) tÃ©lÃ©chargÃ© au premier lancement et tous les 30 jours (Room v2, migration), reconstituÃ©es en zones de 300 m / 2 km / 4 km (jamais un point exact), annonce vocale + bip Ã  l'entrÃ©e, badge avec limitation, Horizon ; pluie Open-Meteo (toutes les 10 min) avec option Â« limitation abaissÃ©e par pluie Â» (R413-2) ; rÃ©glages Â« Alertes Â» (zones, bip, mise Ã  jour manuelle avec compteur et date, pluie). Trafic en direct, travaux, fermetures : toujours â (aucune source gratuite ; Bison FutÃ© Ã  conventionner). 254 tests verts ; vÃ©rifiÃ© sur HONOR (3 350 zones chargÃ©es, Horizon avec virage + zone sur Toulouse â Montgiscard).
- 2026-10-31 — **LOT 5 (partie carburants) livré** : `FuelStationProvider` / `OpenDataFuelStationProvider` (flux instantané du ministère de l'Économie, Licence Ouverte, requête de proximité ODSQL `within_distance`, fixture réelle), modèle `FuelStation`/`FuelPrice` (seuls les prix publiés, avec leur date) ; recherche « Stations » : carburant du profil par défaut, puces de carburant, tri distance / prix, prix par ligne, « — » si non publié, repli OSM si le flux échoue ; fiche station : tous les prix avec fraîcheur, 24h/24, services, source. Bornes (IRVE) : pas d'API de proximité gratuite → reste ⛔ (liste OSM seulement) ; parkings : tarifs ⛔. 258 tests verts ; vérifié sur HONOR (Balma : Gazole 2,499 € il y a 41 h, E85 0,869 €).
