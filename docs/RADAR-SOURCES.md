# Sources de données radar — recherche du 2026-11-05

Objectif : alimenter Radar Intelligence avec des données **légalement réutilisables**, sans scraping, avec attribution. Chaque source a été vérifiée par son API, son fichier ou sa documentation à la date indiquée ; « non vérifié » est écrit quand ce n'est pas le cas.

## 1. Sources retenues

### 1.1 Liste des radars fixes en France — **source principale**

| | |
|---|---|
| Producteur | Ministère de l'Intérieur, Délégation à la sécurité routière (DSR) |
| URL | https://www.data.gouv.fr/datasets/liste-des-radars-fixes-en-france/ (API : `https://www.data.gouv.fr/api/1/datasets/liste-des-radars-fixes-en-france/`) |
| Données | Numéro de radar, type (ETF, ETD, ETT, ETU, ETVM, ETFR, ETPN), date de mise en service, VMA, latitude, longitude |
| Volume | 3 309 équipements (fichier du 30/12/2025) : ETT 1 492, ETF 605, ETD 554, ETFR 496, ETPN 69, ETVM 66, ETU 27 |
| Précision | Variable selon la ligne : 6 décimales 1 465 lignes, 5 : 1 519, 4 : 74, 3 : 232 (≈ 55 m), 2 : 17 (≈ 550 m), 1 : 2 (≈ 5,5 km) |
| Couverture | France |
| Fréquence | Annuelle (documentation PDF du jeu) |
| Accès | Libre, CSV `;`, encodage ISO-8859-1 |
| Limites | Ni sens contrôlé, ni route, ni commune ; VMA « NA » pour 678 lignes (feux rouges, passages à niveau…) ; un seul point pour les tronçons ETVM |
| Licence | Licence Ouverte 2.0 (attribution) |
| Intégration | **Oui** : existence, type, vitesse, position. La ressource CSV la plus récente est choisie automatiquement via l'API data.gouv.fr |

### 1.2 Radars automatiques (2018) — **enrichissement uniquement**

| | |
|---|---|
| Producteur | Jeu publié sur data.gouv.fr par un particulier, extrait du site officiel radars.securite-routiere.gouv.fr |
| URL | https://www.data.gouv.fr/datasets/radars-automatiques/ (fichier `radars.csv` du 25/10/2018) |
| Données | Sens (« Nord vers Sud », « DOUBLE SENS », « PARIS vers LYON »), route, commune, département, type, longueur de tronçon, vitesses VL et PL, équipementier |
| Volume | 3 350 lignes ; 728 « DOUBLE SENS », 773 sans route |
| Fraîcheur | Figé en octobre 2018 ; le dépôt GitHub cité comme source n'existe plus (404 le 2026-11-05) |
| Licence | Licence Ouverte 2.0 |
| Intégration | **Oui, en enrichissement** : un équipement de la liste 2025 reprend sens, route, commune, département, longueur de tronçon et vitesse PL d'un équipement **compatible à moins de 50 m**. Jamais l'existence, la position ou la vitesse. Mesure réelle : 2 223 équipements enrichis, 674 avec un sens exploitable (cardinal ou double sens) |

### 1.3 MapAtlas Camera Index — **corroboration, sens indicatif, équipements en plus (option)**

| | |
|---|---|
| Producteur | MapAtlas (signalements de conducteurs de l'application MapMetrics, dédoublonnés par coordonnées) |
| URL | https://mapatlas.eu/tools/speed-camera-map ; fichier France : `https://mapatlas.eu/data/camera-index/FR.geojson` |
| Données | Point, `class` (spot, signal = feu rouge, corridor = tronçon, adaptive), `limitKph`, `facingDeg`, `approach` (N, NE… ou « both ») |
| Volume | 3 853 points en France (instantané du 21/05/2024, vérifié le 2026-11-05) |
| Licence | CC BY 4.0, attribution « MapAtlas, https://mapatlas.eu » ; la page indique « no signup, no API key and no request limit » |
| Limites | Instantané de 2024, sans date par équipement ni statut officiel ; radars mobiles quasi absents ; 725 points à « 140 km/h » (valeur inexistante en France, ignorée) |
| Mesures (2026-11-05) | 3 072 points à moins de 100 m d'un équipement officiel (médiane 11 m) ; lettre `approach` conforme au jeu 2018 dans 37 cas sur 42 ; `facingDeg` conforme dans ≈ 55 % des cas ; « both » contredit par 2018 dans 30 % des cas |
| Intégration | **Oui** : équipement officiel confirmé à ≤ 50 m (confiance + 5) ; lettre de sens = **sens indicatif** seulement (avance une alerte si le cap concorde, n'écarte jamais un équipement) ; points à plus de 100 m de tout équipement officiel = équipements « hors liste officielle », désactivés par défaut, alerte visuelle seulement. `facingDeg` et « both » ne sont pas utilisés. Mesure réelle : 2 633 confirmés, 364 sens indicatifs, 781 en plus, 369 ignorés |

Recherche du 2026-11-05 (Reddit, forums OSM, Privacy Guides) : aucune autre source à licence explicite n'a été trouvée. Le site lufop.net (licence ODbL d'après un moteur de recherche) a refusé la lecture (HTTP 403) : non intégré tant que ses conditions n'ont pas été lues. VatioBoard (OSM) ne publie ni jeu téléchargeable ni licence.

### 1.4 Valhalla / OpenStreetMap (déjà utilisé par Roadly)

`trace_attributes` donne la limitation OSM et le nom de la route sous le véhicule (profil de route S1, map matching de la conduite libre). Radar Intelligence s'en sert pour **écarter un équipement dont le numéro de route ne correspond pas** à la route suivie (route parallèle, bretelle). Licence ODbL (données OSM), service FOSSGIS en fair-use.

## 2. Sources étudiées et écartées

| Source | Ce qu'elle offre | Raison de l'exclusion |
|---|---|---|
| radars.securite-routiere.gouv.fr (carte officielle) | Radars fixes, mise à jour régulière | Aucune API documentée ni condition de réutilisation trouvée : pas de scraping |
| OpenStreetMap `highway=speed_camera`, relation `enforcement` | Position, parfois `direction` (forward / backward ou degrés), `maxspeed` | Sens du tag `direction` disputé (wiki) ; Overpass public saturé (502, « at capacity », usage automatisé déconseillé, 2026-11-05) ; une base dérivée intégrée serait soumise au partage à l'identique ODbL. Pistes : extraction hebdomadaire hors application (voir roadmap) |
| Radars fixes (OSM), data.gouv.fr 2015 | Extraction OSM | Figée en 2015 |
| Radars pédagogiques (Toulouse, Bordeaux, Clermont…) | Afficheurs de vitesse | Pas des contrôles : risque de confusion |
| Open-GATSO-POI (GitHub 1e1) | Export POI européen quotidien | Code MIT, mais données en partie issues de lufop.net : licence des données incertaine |
| lufop.net | Base communautaire de zones | Conditions de réutilisation non vérifiées : exclu par prudence |
| JamesSimpson/speedcamera | Base communautaire | Pas de licence publiée pour les données |
| lufop.net | Base communautaire (ODbL d'après un moteur de recherche) | Page refusée (HTTP 403) le 2026-11-05 : conditions non lues |
| SCDB, POIbase, Coyote, TomTom, Waze | Bases complètes, mobiles comprises | Commerciales ou propriétaires (non évaluées plus avant) |

## 3. Cadre légal appliqué

- **France** : le décret n° 2012-3 (art. R413-15 du Code de la route) interdit les dispositifs signalant l'emplacement des contrôles. Les applications signalent des **zones de danger**. Roadly affiche « Zone de danger », sans type, sans icône de radar, sans position ; la zone commence avant le point (300 m en ville, 2 km sur route, 4 km sur voie rapide, ± 15 % selon l'équipement) et finit 150 à 450 m après, selon l'équipement : ni le début, ni la fin, ni « encore 300 m » ne donnent la position du radar. Les longueurs reprennent la pratique déjà retenue par Roadly ; une source de presse cite 2 km en agglomération et 4 km hors agglomération : **à confirmer sur Légifrance** avant diffusion.
- **Allemagne** : usage interdit en conduite (§ 23 StVO, y compris par un passager selon l'OLG Karlsruhe) → aucune alerte (`RadarDisplayMode.DISABLED`).
- **Suisse** : interdiction des avertisseurs → aucune alerte.
- Belgique, Espagne, Italie : non vérifié ; sans donnée hors de France, la question ne se pose pas encore.
- Radars mobiles, voitures radar : aucune source publique légale ; Google Maps a retiré leur signalement en France (août 2024).

## 4. Méthodes d'enrichissement retenues

- **Appariement spatial** par index en grille (≤ 50 m, catégorie compatible : « Radar fixe » 2018 ↔ ETF / ETT / ETU).
- **Sens** : « X vers Y » cardinal → cap de Y, si X et Y sont opposés (± 45°) ; « DOUBLE SENS » → les deux sens ; noms de villes → libellé seul.
- **Précision** déduite des décimales publiées ; coordonnées identiques partagées par ≥ 3 équipements → précision 500 m (aucun cas dans le fichier 2025).
- **Score de confiance** 0–100 : source officielle 70, précision ±, corroboration par le jeu 2018 (≤ 30 m) +10, catégorie inconnue −10. Moins de 40 : ignoré ; 40–59 : alerte visuelle seulement ; 60 et plus : voix.
- **Mises à jour** : comparaison par identifiant (nouveaux, retirés, déplacés > 30 m, limitation modifiée) ; un équipement disparu est **marqué retiré**, pas effacé.

## 5. Références consultées

- data.gouv.fr, API datasets `liste-des-radars-fixes-en-france` et `radars-automatiques` (réponses du 2026-11-05) et PDF « Jeu de données : Liste des radars fixes en France ».
- OpenStreetMap wiki, `Tag:highway=speed_camera` et page de discussion ; liste `tagging`, avril 2018.
- ADAC, « Radarwarner und Blitzer-Apps in Deutschland und im Ausland » ; ZDFheute, « Radarwarner: Welche Regeln für Blitzer-Apps gelten » ; bussgeldkatalog.net, « Radarwarner Schweiz ».
- L'Argus, « Radars : le décret sur les avertisseurs enfin publié » ; Leocare, « Avertisseur de radars : que dit la loi ? » ; L'Automobiliste, retrait du signalement des radars mobiles par Google Maps (2024).
