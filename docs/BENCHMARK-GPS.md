# Benchmark GPS — comportement de guidage des applications de navigation

Date de la recherche : 21 novembre 2026. Périmètre : guidage (ronds-points, sorties d'autoroute, manœuvres rapprochées, voix, recalcul, GPS incertain, hors-ligne), pas le marketing.

## Comment lire ce document

- **[S]** = affirmation appuyée par une source consultée pendant cette recherche (voir section 5, numéro entre crochets).
- **NV** = **non vérifié** : soit connaissance générale non recoupée pendant cette recherche, soit aucune source trouvée. À confirmer par un essai réel avant de s'en servir comme argument.
- Les valeurs marquées « calcul » sont des conversions faites ici à partir de chiffres sourcés, pas des chiffres publiés.
- Limites de la recherche : les fils Reddit n'ont pas pu être consultés directement (les résultats obtenus viennent de forums officiels, Trustpilot, forums de marques, articles de presse). Les plaintes citées sont des témoignages individuels, pas des statistiques. Aucun essai sur route n'a été fait.

---

## 1. Matrice comparative

### 1.1 Applications grand public et PND

| Application | Rond-point | Sortie d'autoroute / échangeur | Manœuvres rapprochées (« puis ») | Voix (timing, formulation) | Recalcul | GPS incertain | Hors-ligne |
|---|---|---|---|---|---|---|---|
| **Google Maps** | Numéro de sortie annoncé (NV). Bug 2025 : info de rond-point fausse sur le tableau de bord Android Auto alors que l'écran central était juste [S12] | Guidage de voies dans le bandeau ; pas de « junction view » photoréaliste trouvée [S30]. Immersive Navigation (mars 2026, US) : voies, passages piétons, feux, stops en 3D [S9] | Plainte : « rejoignez l'autoroute » alors que la manœuvre suivante est une sortie à droite quelques dizaines de mètres plus loin [S13] | Repères visuels via Gemini (« tournez à droite après le restaurant X », nov. 2025, US seulement) [S8]. Nouvelle voix 2026 très critiquée ; l'ancienne voix ne lirait plus noms de rues ni numéros de sortie [S14][S15] | Changements d'itinéraire soudains signalés [S16] | NV | Cartes hors-ligne, sans trafic (NV) |
| **Waze** | Deux styles : « tournez à droite / continuez tout droit » pour les petits ronds-points « normaux » (rayon < 25 m, 2 à 4 nœuds, sorties alignées), « prenez la Xe sortie » sinon [S17]. Aide rond-point avec voie à choisir annoncée mars 2024, déploiement encore partiel en janv. 2026 [S18][S19] | Guidage de voies ; pas de vue de jonction (NV) | Plainte : la première consigne de voies autorise plusieurs voies, puis il reste 50 m pour deux changements de voie [S20] | Consignes jugées trop tardives par des utilisateurs [S21]. Réglage « moins bavard » [S22] | Recalcul très agressif, détours par petites rues, « sortir puis revenir sur la même route » [S22][S23] | NV | Pas de vrai mode hors-ligne (NV) |
| **TomTom GO / app TomTom 2025** | Numéro de sortie annoncé (cité comme référence par des utilisateurs d'autres apps) [S24] | « Aperçus de sortie d'autoroute » : écran partagé vue de guidage + voies, ou l'un des deux ; couverture non universelle [S25]. Moving Lane Guidance sur certains modèles [S25] | NV | NV | NV | NV | Cartes embarquées (GO) [S26] |
| **TomTom AmiGO** | NV | NV | NV | NV | NV | NV | NV |
| **HERE WeGo** | NV | Assistance de voies en ligne et hors-ligne [S27] ; un avis trouve flèches et indicateurs de voies confus [S27] | NV | SDK HERE : 4 annonces par manœuvre (range, reminder, distance, action), 1300 m sur autoroute par défaut, « Now turn left » à l'action, option feux (« At the next traffic light… ») désactivée par défaut [S5]. Voix jugée « numérique », peu audible [S27] | NV | NV | Fort ; le guidage est annulé si on bascule hors-ligne sans avoir toutes les cartes [S28] |
| **Sygic** | NV | Vue de jonction + panneaux + assistant de voies dynamique (Premium+) [S29][S30] ; un test indépendant (Boston) juge l'assistant de voies animé « nettement supérieur » [S30] | NV | Plainte : certaines consignes de jonction arrivent « presque sur la jonction » [S29] | Recalcul hors-ligne (NV) | NV | Fort (cartes embarquées) [S29] |
| **Apple Maps** | Plainte : mauvaise sortie annoncée (« 1re » au lieu de « 2e », Australie) [S31] | Guidage de voies (19 pays dès 2018) [S32] | NV | Consignes « naturelles » aux feux (NV). Plaintes anciennes : consigne donnée trop tard, voix ne correspondant pas à la carte [S31] | NV | NV | Cartes hors-ligne depuis iOS 17 (NV) |
| **Garmin Drive / DriveSmart** | NV | photoReal / Bird's Eye junction view + Active Lane Guidance avec voix [S33] | NV | Real Directions : repères (bâtiments, feux) dans la voix [S33] ; moins utile en zone rurale [S33] | NV | NV | Embarqué |
| **Garmin zūmo XT2** | NV | Consignes visuelles affichées « assez tôt », tracé du virage / jonction / changement de voie jugé fidèle [S34] | NV | NV | NV | NV | Embarqué ; plaintes matérielles (pannes après vibrations, Garmin Express capricieux) [S34] |

### 1.2 Navigation embarquée premium

| Système | Rond-point | Sortie / échangeur | Voix | Points notables |
|---|---|---|---|---|
| **Mercedes MBUX AR** | Flèches AR « un peu lentes à réagir aux ronds-points » (test 2019) [S35] | Flux caméra + flèche posée sur la bonne voie / la bonne rue [S35] | NV | Surtout utile à basse vitesse aux intersections [S35] ; option payante [S35] |
| **BMW (iDrive, BMW Maps)** | NV | Recommandation de voie et distance à la sortie dans le combiné [S36]. BMW Maps (oct. 2026) : navigation + aides à la conduite dans une seule vue 3D [S36] | NV | Évaluation Georgia Tech 2022 (iX) : vue augmentée « partiellement surchargée », trois types de flèches par manœuvre = encombré [S36] |
| **Tesla** | Plainte : la voix dit « première sortie » alors que la carte montre la deuxième [S37] | Pas de suggestion de voie réclamée par des utilisateurs [S37] | Consignes vocales jugées très tardives en ville inconnue [S37] | Demande d'une alerte plus forte quand on va rater une sortie [S37] |

### 1.3 Moto / scooter

| Application | Rond-point | Voix | Recalcul / hors-ligne | Points notables |
|---|---|---|---|---|
| **Calimoto** | NV | Annonces vocales + alertes « points de vigilance » [S38] | Le recalcul fonctionne mais renvoie vers des routes fermées (détours de 45 min) [S38] ; cartes hors-ligne [S38] | Voix via intercom Bluetooth [S38] |
| **Kurviger** | NV | Voix réservée à l'abonnement Tourer+ ; TTS du téléphone ; choix du canal audio (média / notifications / appels) pour régler le volume séparément [S39]. Réglage de fréquence (« après les points d'instruction », 1 km, 300 m) [S40] | Recalcul hors-ligne possible mais sans les options « sinueux » ; recommandé en dernier recours [S41] | L'option « après les points d'instruction » marche mal quand les virages s'enchaînent [S40] |
| **Liberty Rider** | NV | Alerte à l'approche de chaque virage dangereux [S42] ; guidage vocal détaillé NV | NV | 3 modes d'itinéraire (rapide / compromis / fun) ; dépend du téléphone [S42] |
| **TomTom Rider 550** | NV | NV | Embarqué | Aucun test détaillé trouvé |
| **Beeline Moto II** | NV | Voix seulement via l'abonnement Beeline Plus [S43] | NV | Trait de route net, autres routes estompées ; pas de guidage de voies ; « aide à la navigation », pas un GPS complet [S43] |

### 1.4 Communautaire / open source

| Application | Rond-point | Voies / jonction | Voix | Hors-ligne | Points notables |
|---|---|---|---|---|---|
| **OsmAnd** | Numéro de sortie ; erreurs signalées (« 1re sortie » au lieu de 2e/3e), attribuées aux données [S44] | Voies affichées (NV) | Moteur de timing le mieux documenté du marché (voir 4.2) [S2] ; réglage « annonce d'arrivée » tôt / normal / tard [S2] | Complet | Annonce « signal GPS perdu » après 20 s [S2] |
| **Organic Maps** | Correctif sur le comptage des sorties (voies de service) [S45] | Guidage de voies : demandes et bugs ouverts (« Lane Support Not Working », affichage rogné, confusion gauche/droite) [S46] | Correctif d'annonces TTS manquantes (janv. 2026) [S45] | Complet | Très sobre |
| **CoMaps** | Hérite d'Organic Maps (NV) | Guidage de voies + meilleure description du prochain virage (iOS, juil. 2026) [S47] | TTS [S47] | Complet | Fork 2025 pour raisons de gouvernance [S47] |
| **Magic Earth** | NV | NV | Voix sur autoroute jugée au niveau des apps payantes (test MakeUseOf cité) [S48] | Hors-ligne devenu payant | Passage à l'abonnement (août 2025) puis freemium (déc. 2025) : fonctions retirées, colère des utilisateurs [S48] |

### 1.5 Navigation « IA »

| Fonction | Ce que ça fait réellement | Limites |
|---|---|---|
| **Google Maps + Gemini, repères** (nov. 2025) | Remplace la distance par un repère visible (station-service, restaurant, bâtiment connu), choisi en croisant ~250 M de lieux avec Street View ; le repère est mis en évidence sur la carte [S8] | États-Unis seulement au lancement ; Android Auto « peu après » [S8] |
| **Immersive Navigation** (mars 2026) | Vue 3D (bâtiments, ponts, relief), voies, passages piétons, feux et stops dessinés ; voix « plus naturelle » [S9] | Déploiement US progressif [S9] |
| **Ask Maps** | Questions en langage naturel, réponses personnalisées [S9] | Pas du guidage à proprement parler |
| **Nouvelle voix Gemini** (2026) | Voix plus « naturelle » | Rejet massif : « elle a l'air fâchée », lente ; régression sur les noms de rue / numéros de sortie avec l'ancienne voix [S14][S15] |

---

## 2. Notes par application

### Google Maps
- **Forces** : repères visuels dans la voix [S8], détail 3D des intersections [S9], données de voies très larges (NV).
- **Faiblesses** : « sortez à droite » sans préciser laquelle quand trois sorties se suivent [S15] ; consigne de voie insuffisante quand on est sur la voie de gauche d'une 4 ou 5 voies, guidage de voies inégal d'un utilisateur à l'autre [S49] ; consignes d'insertion absurdes quand la sortie suit immédiatement [S13].
- **Plaintes récurrentes** : changement de voix imposé [S14], début d'annonce coupé en Bluetooth (le canal audio met du temps à s'ouvrir) [S50], changements d'itinéraire soudains [S16].
- **Leçon** : une voix « naturelle » ne compense pas la perte d'informations précises (nom de rue, numéro de sortie). Les utilisateurs veulent la précision d'abord.

### Waze
- **Forces** : trafic et dangers communautaires ; alertes dos-d'âne, virage serré, péage, baisse de limitation [S18].
- **Faiblesses** : recalcul trop agressif, raccourcis qui n'en sont pas [S22] ; consignes tardives [S21] ; guidage de voies qui ne prépare pas la manœuvre suivante [S20] ; fonctions annoncées en 2024 encore incomplètes début 2026 [S19].
- **Leçon rond-point** : Waze change de formulation selon la géométrie (petit rond-point simple = « tournez à gauche », sinon numéro de sortie) [S17]. C'est une vraie idée de conception, mais la règle dépend de la qualité de la carte et produit des « sortie zéro » en cas d'erreur de cartographie [S17].

### TomTom (GO, app 2025, AmiGO)
- **Forces** : aperçu de sortie d'autoroute en écran partagé [S25], numéro de sortie de rond-point fiable d'après des utilisateurs d'apps concurrentes [S24], cartes embarquées [S26].
- **Faiblesses** : couverture du guidage de voies incomplète [S25] ; RouteBar seulement sur Android [S26] ; GO Navigation rebaptisée GO Expert (poids lourds), confusion de gamme [S26].
- **Non vérifié** : distance d'affichage de l'aperçu de sortie, comportement d'AmiGO, plaintes 2025.

### HERE WeGo
- **Forces** : hors-ligne, assistance de voies hors-ligne [S27], modèle d'annonces à 4 niveaux très propre côté SDK [S5].
- **Faiblesses** : voix de mauvaise qualité [S27], indicateurs de voies jugés confus [S27], pas de saisie vocale d'adresse [S27], guidage annulé lors du passage hors-ligne sans cartes [S28].

### Sygic
- **Forces** : vue de jonction avec panneaux, assistant de voies animé, affichage tête haute [S29][S30].
- **Faiblesses** : consignes parfois trop tardives [S29] ; licences « à vie » qui ne fonctionnent plus, passage forcé à l'abonnement, relances publicitaires, voix payante [S29].
- **Leçon** : ne jamais mettre la voix derrière un paiement ; ne jamais revenir sur une promesse de licence.

### Apple Maps
- **Forces** : guidage de voies apprécié [S31], itinéraires préférés appris (iOS 26) [S51], recherche en langage naturel [S51].
- **Faiblesses** : consignes trop tardives et mauvais numéro de sortie signalés [S31] (témoignages en partie anciens).
- **Non vérifié** : formulation exacte « au prochain feu », comportement actuel en France.

### Garmin (Drive, zūmo)
- **Forces** : vue de jonction photoréaliste, Active Lane Guidance vocal, Real Directions avec repères et feux [S33] ; sur zūmo XT2, visuels affichés tôt et fidèles [S34].
- **Faiblesses** : repères moins utiles hors ville [S33] ; fiabilité matérielle et logiciel PC critiqués sur zūmo [S34].

### Embarqué premium (Mercedes, BMW, Tesla)
- **Mercedes MBUX AR** : la flèche posée sur l'image réelle lève l'ambiguïté « laquelle des deux rues », mais elle réagit lentement dans les ronds-points [S35].
- **BMW** : le retour d'évaluation est clair, trop de flèches à la fois surcharge [S36].
- **Tesla** : voix tardive, pas de suggestion de voie, numéro de sortie vocal qui contredit la carte [S37]. La contradiction voix / carte est le défaut le plus grave relevé.

### Moto / scooter
- **Calimoto** : recalcul vers routes fermées [S38].
- **Kurviger** : le choix du canal audio est une idée directement réutilisable pour les intercoms [S39] ; les annonces s'enchaînent mal dans les séries de virages [S40].
- **Liberty Rider** : alerte de virage dangereux, positionnement sécurité [S42].
- **Beeline** : preuve qu'un affichage minimal (un trait, le reste estompé) suffit pour beaucoup de situations, mais pas pour les voies [S43].
- **Plainte générique moto (MyRoute-app)** : annonces en retard, parfois après la jonction, début d'annonce coupé dans l'intercom ; « prenez la première sortie » quelle que soit la sortie réelle [S24][S52].

### Open source
- **OsmAnd** : très configurable, timing documenté [S2] ; erreurs de numéro de sortie dues aux données [S44].
- **Organic Maps / CoMaps** : simplicité, hors-ligne total ; le guidage de voies reste le chantier ouvert [S46][S47].
- **Magic Earth** : la bascule vers l'abonnement a détruit la confiance [S48].

---

## 3. Ce que Roadly doit reprendre / faire mieux

### P0 — à faire en premier (fiabilité du guidage)

1. **La voix et l'écran ne doivent jamais se contredire.** Le numéro de sortie de rond-point prononcé, celui affiché et le tracé dessiné doivent venir de la même valeur (`roundabout_exit_count` de Valhalla [S6]). Test automatique : pour chaque rond-point d'un jeu d'itinéraires, compter les sorties sur la géométrie et comparer. Défaut constaté chez Tesla, Apple, OsmAnd, MyRoute-app [S37][S31][S44][S24].
2. **Rond-point : formulation selon la géométrie.** Reprendre l'idée Waze [S17] : petit rond-point à 3 ou 4 branches bien alignées = « au rond-point, tournez à gauche » ; sinon « prenez la 3e sortie ». Borner comme Valhalla (comptage valide de 1 à 10 [S7]) et, hors bornes, dire seulement « au rond-point, suivez X ». Toujours dessiner la géométrie réelle avec la sortie mise en évidence, et faire défiler un décompte visuel des sorties passées (aucune app n'a été trouvée faisant ce décompte : NV, piste de différenciation).
3. **Timing vocal en secondes, pas en mètres fixes.** Trois annonces par manœuvre, calculées sur le temps restant (voir section 4.5). Une consigne légèrement trop tôt vaut mieux que trop tard [S10]. Plainte la plus fréquente toutes apps confondues : « trop tard » [S21][S29][S37][S52].
4. **Regrouper les manœuvres rapprochées.** Si deux manœuvres sont à moins de ~10 à 13 s l'une de l'autre, les annoncer ensemble avant la première (« tournez à droite, puis tout de suite à gauche ») : FHWA dit 10 s [S10], Valhalla 13 s (`verbal_multi_cue`) [S7]. Afficher la manœuvre suivante en vignette secondaire dans ce cas. Supprimer la consigne « rejoignez l'autoroute » quand la manœuvre suivante est une sortie immédiate (plainte Google [S13]).
5. **Guidage de voies qui regarde une manœuvre plus loin.** Quand deux manœuvres s'enchaînent, ne garder actives que les voies qui permettent aussi la seconde (plainte Waze : 50 m pour deux changements de voie [S20]). Valhalla distingue `valid` et `active` [S6] ; Mapbox donne la priorité aux voies sur la « manœuvre suivante » dans le sous-bandeau [S4].
6. **Sortie d'autoroute : toujours nommer la sortie.** Numéro de sortie + direction (éléments `sign` de Valhalla : numéro, branche, direction, nom [S6]). Ne jamais dire « sortez à droite » seul (plainte Google [S15]). Première annonce vers 1,3 à 2 km (HERE : 1300 m par défaut sur autoroute [S5]).
7. **Début d'annonce jamais coupé.** Demander le focus audio avec atténuation, attendre l'ouverture du canal Bluetooth avant de parler, et tenir compte de ce délai dans le calcul de distance (OsmAnd a un réglage de délai pour cela [S2] ; plaintes Google et MyRoute-app [S50][S52]). Proposer le choix du canal audio (média / notification / appel) comme Kurviger [S39], utile pour scooter + intercom.

### P1 — différenciation

8. **Panneau de sortie dessiné** à partir des données `sign` (cartouche vert/bleu avec numéro, direction), affiché en même temps que les voies, comme l'écran partagé TomTom [S25]. Une vue de jonction 3D n'a de valeur que si elle reste simple : l'évaluation BMW montre que trois flèches simultanées surchargent [S36].
9. **Repères dans la voix, version sobre.** En France et sur données OSM, commencer par les repères fiables : feux (« au prochain feu »), ronds-points, ponts, tunnels, stations-service en bord de route. HERE et Garmin le font [S5][S33] ; Google l'étend aux commerces via IA [S8]. Ne jamais remplacer le nom de rue ou le numéro de sortie par le repère : l'ajouter.
10. **Recalcul calme.** Hystérésis : ne proposer un nouvel itinéraire que pour un gain net et durable, jamais de « sortir puis revenir sur la même route » (Waze [S22][S23]). Respecter les routes fermées lors du recalcul (Calimoto [S38]). Annonce « hors itinéraire » temporisée (OsmAnd : 20 s [S2]).
11. **GPS incertain dit clairement.** Annonce et indication visuelle après ~20 s sans signal (OsmAnd [S2]) ; en tunnel, continuer à avancer à l'estime sur l'itinéraire et geler les annonces qui dépendent d'une position précise. Comportement des concurrents : NV.
12. **Hors-ligne sans surprise.** Si le guidage ne peut pas continuer hors-ligne, le dire avant le départ, pas l'annuler en route (HERE [S28]). Indiquer quelles options sont perdues hors-ligne (Kurviger [S41]).
13. **Réglage unique « annonces : tôt / normal / tard »** (multiplicateurs 1,5 / 1 / 0,5 d'OsmAnd [S2]) plutôt qu'une forêt d'options ; plus un mode « moins bavard » (Waze [S22]).

### P2 — plus tard

14. **Profil scooter / cyclo** : annonces plus tôt en ville dense, alerte de virage serré (Liberty Rider [S42], Waze [S18]), affichage ultra-épuré type Beeline en option [S43], enchaînements de virages regroupés (défaut Kurviger [S40]).
15. **Itinéraires préférés appris** (Apple iOS 26, Waze) [S51][S18].
16. **Modèle économique** : ne jamais rendre payante la voix ou une fonction déjà donnée (Sygic, Magic Earth, Kurviger, Beeline) [S29][S48][S39][S43].

### Règles d'ergonomie à tenir (charge visuelle)

- Tout ce qui se lit en roulant doit se comprendre en un regard de moins de 2 s ; une tâche complète ne doit pas dépasser 12 s de regard cumulé (NHTSA, lignes directrices volontaires 2013) [S11]. Le Japon (JAMA) fixe 8 s au total [S53].
- Texte en conduite : 1 à 3 lignes, 120 caractères au maximum ; message de la carte de manœuvre : 2 lignes ; cibles tactiles d'au moins 76 dp ; pas de notification pendant une manœuvre ; pas de texte défilant (Android for Cars) [S54].
- Un seul bandeau de manœuvre à la fois (Mapbox) [S4].
- ESoP européen, critères AAM (2 s / 20 s), hauteur de caractères ISO 15008 : **non vérifié** pendant cette recherche.

---

## 4. Règles de timing vocal trouvées

### 4.1 Valhalla

Champs du récit [S6] : `verbal_transition_alert_instruction` (prépare à la transition), `verbal_pre_transition_instruction` (« immédiatement avant »), `verbal_post_transition_instruction` (« immédiatement après »), `verbal_multi_cue` (la consigne suivante est accolée à la consigne courante). La documentation ne donne **aucune distance** : c'est au client de décider.

Sérialiseur au format OSRM (`voice_instructions`), d'après le diff de la PR #4506 [S1] (à revérifier sur le code fusionné) :

| Constante | Valeur |
|---|---|
| Alerte de transition | 15 s avant la manœuvre |
| Consigne de pré-transition | 5 s avant la manœuvre |
| Longueur approximative de la consigne post-transition | 110 m (jouée 10 m après le début, seulement si la manœuvre est assez longue) |

- Les secondes sont converties en distance à partir du **temps de parcours de l'itinéraire** (coût écoulé par arête), pas d'une vitesse fixe [S1].
- Pas de plancher : si la manœuvre dure moins que la cible, l'annonce est placée au début de la manœuvre [S1].
- Départ : la consigne est toujours jouée au début ; si l'étape est courte, alerte à la moitié et pré-transition au quart [S1].
- Conversion (calcul) : 15 s / 5 s = 125 m / 42 m à 30 km/h ; 208 m / 69 m à 50 km/h ; 333 m / 111 m à 80 km/h ; 542 m / 181 m à 130 km/h. Sur autoroute, 15 s est court comparé aux autres moteurs.

Constantes du générateur de récit (`narrativebuilder.cc`, branche master) [S7] :

| Constante | Valeur | Rôle |
|---|---|---|
| `kVerbalMultiCueTimeThreshold` | 13 s | seuil de temps pour créer une consigne multiple (« puis ») |
| `kVerbalMultiCueTimeStartManeuverThreshold` | 39 s (3 × 13) | même seuil pour la manœuvre de départ |
| `kVerbalPostMinimumRampLength` | 2,0 km | longueur minimale de bretelle pour une consigne post-transition |
| `kVerbalAlertMergePriorManeuverMinimumLength` | 2,0 km | une insertion ne reçoit une alerte que si la manœuvre précédente est plus longue |
| `kRoundaboutExitCountLowerBound` / `UpperBound` | 1 / 10 | bornes du numéro de sortie prononcé |

### 4.2 OsmAnd

Principe [S2] : distance d'annonce = temps d'avance × **vitesse par défaut du profil** (voiture 45 km/h, vélo 10 km/h, piéton 4 km/h). Les annonces proches sont ensuite ajustées à la vitesse réelle.

| Annonce | Temps d'avance | Distance voiture (45 km/h) | Ajustement vitesse | Remarques |
|---|---|---|---|---|
| « Tournez maintenant » | 6,7 s | 83 m | proportionnel à vitesse réelle / vitesse par défaut | plancher 12 m |
| « Dans X m, tournez » | 22 s | 275 m | à la hausse seulement | sautée s'il reste moins de 15 s |
| « Préparez-vous à tourner dans X m » | 115 s | 1438 m | aucun | sautée si à moins de 150 m de la précédente |
| Préparation longue | 300 s | — | aucun | seulement si vitesse par défaut ≥ 108 km/h |
| « Continuez tout droit » | > 300 s | 3750 m | aucun | après calcul ou après un virage si le prochain est loin |
| Arrivée | 5 s | 63 m | aucun | minimum 12 m |
| Alarme standard / proche | 12 s / 7 s | 150 m / 88 m | à la hausse | |
| Hors itinéraire | 20 s | 250 m | aucun | désactivable |
| Signal GPS perdu | 20 s | — | — | après 20 s de perte |

- Réglage utilisateur « annonce d'arrivée » : tôt × 1,5, normal × 1, tard × 0,5, très tard × 0,25 [S2].
- La formule du temps « tournez maintenant » rapportée par la page (max(8, √vitesse)) ne colle pas avec la valeur 6,7 s du tableau : **non vérifié**, lire le code du routeur vocal avant de la reprendre.

### 4.3 Mapbox Directions / Navigation SDK (et OSRM)

- `voiceInstructions[].distanceAlongGeometry` : en mètres, distance **avant la manœuvre à venir** à laquelle l'annonce commence ; même champ pour les bandeaux, un seul bandeau à la fois [S4].
- Emplacements : annonce principale près de la **fin** de l'étape ; annonces supplémentaires « si le temps le permet » à une distance confortable avant la fin et près du début ; une annonce peut couvrir deux manœuvres proches [S3]. Bandeau visuel près du **début** de l'étape, points supplémentaires quand les voies deviennent pertinentes [S3].
- Paramètre `roundabout_exits` : entrée et sortie de rond-point en deux consignes séparées [S4].
- Composants de bandeau : `exit`, `exit-number`, `lane` (avec `active` et `active_direction`), icône de cartouche de route [S4].
- Les seuils chiffrés exacts de Mapbox ne sont pas publiés dans la doc consultée : **non vérifié**.
- OSRM (moteur open source) ne génère pas lui-même d'annonces vocales ; elles viennent de l'API Mapbox ou du sérialiseur Valhalla : **non vérifié** en détail.

### 4.4 HERE SDK

- Quatre annonces par manœuvre par défaut : `range` (la plus lointaine), `reminder`, `distance`, `action` (à la manœuvre) [S5].
- Autoroute, voiture : annonce de distance à **1300 m** par défaut ; paramètre temporel par défaut **18 s** ; valeurs réglables par mode de transport et type de route, en mètres ou en secondes [S5]. Valeurs par défaut urbain / rural : **non vérifié**.
- Formulations : « After 1 kilometer turn left onto… », « Now turn left », « After 250 meters use the right two lanes and turn right », « At the next traffic light turn left onto… » [S5].

### 4.5 Recherche en facteurs humains

- FHWA (d'après Green et George, 1995) [S10] : à 40 mph (64 km/h), consigne finale à environ 450 pieds (137 m) avant le virage ; ajouter 15 pieds par mph (environ 2,8 m par km/h) ; plus de marge pour un virage à gauche (conduite à droite) et pour les conducteurs âgés (jusqu'à 119 pieds, soit 36 m) ; une consigne un peu trop tôt vaut mieux que trop tard ; si deux manœuvres sont à moins de 10 s, les annoncer ensemble. Étude limitée aux virages vers une rue latérale ; les sorties d'autoroute ne sont pas couvertes. Les équations exactes sont dans une figure non lisible : **non vérifié**.
- Conversion (calcul, extrapolation linéaire hors du domaine de l'étude) : environ 98 m à 50 km/h, 182 m à 80 km/h, 210 m à 90 km/h.

### 4.6 Synthèse proposée pour Roadly (à valider sur route)

| Niveau | Ville (≤ 50 km/h) | Route (80-90 km/h) | Autoroute (110-130 km/h) | Source d'inspiration |
|---|---|---|---|---|
| Préparation lointaine | — (ou ~400 m si l'étape est longue) | ~1 km | 2 km puis 1,3 km | OsmAnd 115 s, HERE 1300 m |
| Annonce principale | ~20 s (≈ 250-300 m) | ~20 s (≈ 500 m) | ~20-25 s (≈ 700-800 m) | OsmAnd 22 s, HERE 18 s, Valhalla 15 s |
| Consigne d'action | ~6 s, plancher ~100 m à 50 km/h | ~6 s (≈ 150 m) | ~6-7 s (≈ 220-250 m) | OsmAnd 6,7 s, Valhalla 5 s, FHWA 137 m à 64 km/h |
| Regroupement « puis » | manœuvres à moins de 10-13 s | idem | idem | FHWA 10 s, Valhalla 13 s |

Ces valeurs sont une proposition dérivée des sources, pas une norme. Soustraire le délai d'ouverture du canal audio et la durée de la phrase elle-même.

---

## 5. Sources

Timing et moteurs
- [S1] Valhalla, PR #4506 (voice instructions au format OSRM), diff : https://github.com/valhalla/valhalla/pull/4506.diff
- [S2] OsmAnd, Navigation Voice Prompt Triggering : https://docs.osmand.net/docs/technical/algorithms/voice-prompt-triggering
- [S3] Mapbox Navigation SDK iOS (legacy), Instructions : https://docs.mapbox.com/ios/legacy/navigation/guides/instructions
- [S4] Mapbox Directions API : https://docs.mapbox.com/api/navigation/directions/
- [S5] HERE SDK, voice guidance : https://docs.here.com/here-sdk/docs/ios-navigation-voice-guidance
- [S6] Valhalla, Route API reference : https://valhalla.github.io/valhalla/api/route/api-reference/
- [S7] Valhalla, `src/odin/narrativebuilder.cc` : https://raw.githubusercontent.com/valhalla/valhalla/master/src/odin/narrativebuilder.cc
- Mapzen, Voice guidance (ancien, contexte) : https://www.mapzen.com/blog/voice-guidance

Google / IA
- [S8] Google, Gemini navigation features (5 nov. 2025) : https://blog.google/products/maps/gemini-navigation-features-landmark-lens/ ; https://9to5google.com/2025/11/05/google-maps-landmark-navigation-gemini-lens/ ; https://techcrunch.com/2025/11/05/google-maps-bakes-in-gemini-to-improve-navigation-and-hands-free-use
- [S9] Google, Ask Maps et Immersive Navigation (12 mars 2026) : https://blog.google/products-and-platforms/products/maps/ask-maps-immersive-navigation/ ; https://9to5google.com/2026/03/12/google-maps-immersive-navigation ; https://www.androidcentral.com/apps-software/google-maps/immersive-navigation-in-google-maps-is-like-opening-a-new-app-for-the-modern-era-of-driving
- [S12] T3, bug rond-point Android Auto : https://www.t3.com/news/new-update-fixes-one-major-issue-with-android-auto-and-google-maps
- [S13] Straight Dope, « Has google driving navigation got noticeably worse? » : https://boards.straightdope.com/t/has-google-driving-navigation-got-noticeably-worse/1020160
- [S14] CBC, nouvelle voix Google Maps : https://www.cbc.ca/news/world/google-maps-voice-change-9.7393031 ; https://support.google.com/maps/thread/463794692/looks-like-google-has-changed-the-voice-for-directions-i-prefer-the-old-one?hl=en
- [S15] Synthèse d'avis (site tiers, à prendre avec prudence) : https://unstar.app/blog/google-maps-new-navigation-voice-old-voice-no-street-names-reviews-2026
- [S16] India.com, problèmes de guidage audio : https://www.india.com/technology/google-maps-audio-navigation-voice-directions-driver-safety-navigation-issues-route-changes-gps-app-driving-risks-road-safety-navigation-errors-app-update-tech-news-8264893/
- [S49] UX Collective, « Google Maps: about that left turn » : https://uxdesign.cc/google-maps-about-that-left-turn-34953cdc204
- [S50] Coupure du début d'annonce en Bluetooth : https://forums.androidcentral.com/ask-question/566440-google-navigation-app-beginning-each-voice-announcement-cut-off.html ; https://discussions.apple.com/thread/251941423 ; https://support.google.com/maps/answer/11523237

Waze
- [S17] Wazeopedia, Roundabout : https://wazeopedia.waze.com/wiki/USA/Roundabout ; https://www.waze.com/discuss/t/roundabouts/379774
- [S18] Waze, 6 nouveautés (mars 2024) : https://blog.google/waze/6-updates-to-waze-to-help-you-get-around-safely-and-conveniently/ ; https://techcrunch.com/2024/03/05/waze-now-helps-you-navigate-roundabouts-alerts-you-about-speed-limit-changes-and-more
- [S19] 9to5Google, déploiement tardif (janv. 2026) : https://9to5google.com/2026/01/22/waze-speed-bump-new-features-rolling-out/
- [S20] Forum Waze, guidage de voies sur carrefours en plusieurs temps : https://www.waze.com/discuss/t/lane-guidance-i-flerstegskorsningar/393459
- [S21] Trustpilot Waze : https://www.trustpilot.com/review/waze.com ; forum : https://www.waze.com/discuss/t/waze-is-effectively-broken-for-me-also-outright-dangerous-to-use/296669
- [S22] BGR, raisons de ne pas aimer Waze : https://www.bgr.com/2222297/reasons-users-dislike-waze/ ; SlashGear : https://www.slashgear.com/2243446/common-problems-with-waze-how-to-fix-them/
- [S23] Wazeopedia, Problems and Issues : https://wazeopedia.waze.com/wiki/USA/Problems_and_Issues ; PhoneArena : https://www.phonearena.com/news/waze-users-say-that-routing-no-longer-reliable_id168661

TomTom, HERE, Sygic, Apple, Garmin
- [S24] Forum MyRoute-app, consignes vocales au rond-point : https://forum.myrouteapp.com/topic/6862/voice-instructions-at-the-roundabout ; https://forum.myrouteapp.com/topic/6882/voice-prompts-are-wrong-at-roundabouts
- [S25] TomTom, manuels (Advanced / Moving Lane Guidance, Motorway exit previews) : https://download.tomtom.com/open/manuals/VIA_53/html/en-gb/Motorwayexitpreviews.htm ; https://download.tomtom.com/open/manuals/new_GO/html/en-gb/AdvancedLaneGuidance.htm ; https://help.tomtom.com/hc/en-gb/articles/360013961719
- [S26] TomTom, app 2025 et GO Navigation : https://www.tomtom.com/newsroom/product-focus/drive-smarter-with-the-tomtom-app ; https://www.tomtom.com/en_ie/navigation/mobile-apps/go-navigation-app/
- [S27] HERE WeGo, page produit et avis : https://www.here.com/products/wego ; https://justuseapp.com/en/app/955837609/here-wego-maps-navigation/reviews
- [S28] HERE WeGo, problèmes résolus : https://docs.here.com/wego/docs/resolved-issues
- [S29] Sygic, Trustpilot et Premium+ : https://uk.trustpilot.com/review/www.sygic.com?page=2 ; https://www.sygic.com/what-is/premium-subscription
- [S30] MSPoweruser, Sygic vs Google Maps : https://mspoweruser.com/sygic-vs-google-maps ; aide Sygic : https://help.sygic.com/hc/en-us/articles/16860484134162-How-does-Sygic-compare-to-Google-Maps-or-Waze
- [S31] Apple, discussions : https://discussions.apple.com/thread/256010484 ; https://discussions-cn-prz.apple.com/thread/255957704 ; Tom's Guide : https://www.tomsguide.com/phones/iphones/apple-maps-is-my-go-to-choice-for-directions-heres-how-apple-intelligence-could-make-it-even-better
- [S32] MacRumors, extension du guidage de voies : https://www.macrumors.com/2018/04/16/apple-maps-lane-guidance-expands-in-europe/
- [S33] Garmin DriveSmart : https://www.toptenreviews.com/navigation-gps-garmin-drivesmart-61-review ; https://www.crutchfield.com/ISEO-rccbcspd/p_150DS51LMT/Garmin-DriveSmart-51LMT-S.html ; https://www.expertreviews.co.uk/?p=1415087
- [S34] Garmin zūmo XT2 : https://motorcyclenews.com/bike-kit/accessories/garmin-zumo-xt2-review ; https://www.bennetts.co.uk/bikesocial/reviews/products/motorcycle-technology/garmin-zumo-xt2-review-motorcycle-gps
- [S51] Apple Maps iOS 26 : https://9to5mac.com/2025/10/01/apple-maps-got-a-big-ios-26-upgrade-for-an-essential-feature/ ; https://www.tomsguide.com/computing/mobile-apps/apple-maps-gets-natural-language-search-in-ios-26-beta-what-that-means-for-your-navigation

Embarqué premium
- [S35] Mercedes MBUX AR : https://carmagazine.co.uk/car-news/tech/mercedes-augmented-reality-navigation-how-does-it-work ; https://www.thedrive.com/tech/23816/daddy-mbux-testing-mercedes-benzs-new-infotainment-system-in-the-2019-a-class ; https://techradar.com/opinion/i-was-ready-to-give-up-on-ar-but-electric-cars-have-changed-my-mind
- [S36] BMW : https://www.bmwgroup.com/en/news/general/2026/bmw-maps.html ; https://purdy.gatech.edu/wp-content/uploads/2022/03/Infotainment_Summary_BMXiX_144ppi.pdf ; https://www.press.bmwgroup.com/usa/article/detail/T0327734EN_US/the-all-new-bmw-idrive
- [S37] Tesla Motors Club : https://teslamotorsclub.com/tmc/threads/navigation-ui-lack-of-visual-audio-cues-lane-cues.250082/ ; https://teslamotorsclub.com/tmc/threads/can-the-model-3-satnav-count.208779/latest ; https://teslatap.com/desired-features/interaction-frequency/

Moto / scooter
- [S38] Calimoto : https://apps.apple.com/us/app/id1209129603 ; https://www.bennetts.co.uk/bikesocial/reviews/products/motorcycle-technology/calimoto-motorcycle-route-planner-review
- [S39] Kurviger, guidage vocal : https://docs.kurviger.com/voiceguidance
- [S40] Forum Kurviger : https://forum.kurviger.de/t/all-curvy-routes-voice-guidance/2452 ; https://forum.kurviger.de/t/how-to-repeat-turn-instructions/879
- [S41] Kurviger PRO, recalcul hors-ligne : https://docs.kurviger.com/app/manual/pro ; journal des modifications : https://docs.kurviger.com/changelog
- [S42] Liberty Rider : https://liberty-rider.com/en/moto-gps ; https://www.dafy-moto.com/dafy-et-liberty-rider.html
- [S43] Beeline Moto II : https://motorcyclenews.com/bike-kit/accessories/beeline-moto-ii-review ; https://bennetts.co.uk/bikesocial/reviews/products/motorcycle-technology/beeline-moto-ii-2-review ; https://au.trustpilot.com/review/beeline.co
- [S52] Forum MyRoute-app, annonces tardives : https://forum.myrouteapp.com/topic/1478/late-voice-guidance-occasionally-missing-altogether-at-individual-junctions/2

Open source
- [S44] OsmAnd, mauvais numéro de sortie (2020) : https://groups.google.com/g/osmand/c/X7x3x-d7asM/m/NoiGvfY3AQAJ
- [S45] Organic Maps, nouveautés : https://organicmaps.app/news/2026-01-16/ ; https://organicmaps.app/news/
- [S46] Organic Maps, tickets (index) : https://issues.ecosyste.ms/hosts/GitHub/repositories/organicmaps%2Forganicmaps/issues
- [S47] CoMaps : https://www.comaps.app/news/2026-07-16/Release-2026.07.16-release-notes/ ; https://www.comaps.app/support/how-do-the-features-differ-from-those-of-organic-maps/ ; https://wiki.openstreetmap.org/wiki/CoMaps
- [S48] Magic Earth : https://wiki.openstreetmap.org/wiki/Magic_Earth ; https://alternativeto.net/software/magic-earth/about ; https://www.webpronews.com/magic-earth-review-privacy-focused-google-maps-alternative

Facteurs humains
- [S10] FHWA, lignes directrices, chapitre 5 (timing des consignes auditives) : https://www.fhwa.dot.gov/publications/research/safety/98057/ch05.cfm ; rapport UMTRI (George, Green, Fleming) : https://deepblue.lib.umich.edu/items/d88a0772-d611-4846-9609-4ac6c0c04e03/full
- [S11] NHTSA, Visual-Manual Driver Distraction Guidelines (2013) : https://www.govinfo.gov/link/fr/78/24861 ; https://www.transportation.gov/briefing-room/us-dot-releases-guidelines-minimize-vehicle-distractions
- [S53] Transports Canada / VTTI, revue des lignes directrices (JAMA 8 s) : https://www.vtti.vt.edu/PDFs/Transport%20Canada%20T8080-160051_Guidelines.pdf
- [S54] Android for Cars, principes de conception et modèle de navigation : https://developer.android.com/design/ui/cars/guides/foundations/design-principles ; https://developer.android.com/design/ui/cars/guides/templates/navigation-template
