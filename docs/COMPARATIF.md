# Roadly face aux GPS de référence

Comparaison par aspect avec Waze, Google Maps, Coyote et Organic Maps (novembre 2026). Les colonnes des autres applications résument leurs points forts reconnus, pas une liste exhaustive. Pour Roadly, l'état fonction par fonction est dans [`FEATURES.md`](../FEATURES.md).

Légende : ✅ présent · 🟡 partiel · ❌ absent · ? non vérifié (fonction non confirmée par les sources consultées).

## Tableau

| Aspect | Waze | Google Maps | Coyote | Organic Maps | **Roadly** |
|---|---|---|---|---|---|
| Guidage vocal, voies, ronds-points | ✅ | ✅ | ✅ | ✅ | ✅ voies, numéro de sortie, panneaux, **carrefours en 3D** |
| Route à venir en un coup d'œil | ? | ❌ | ? | ❌ | ✅ **Horizon** (manœuvres, virages, zones, incidents) |
| Trafic et incidents en temps réel | ✅ communauté | ✅ | ✅ communauté | ❌ | 🟡 **incidents officiels Bison Futé** (routes nationales) + signalements communautaires ; pas de vitesse du trafic |
| Recalcul selon le trafic | ✅ | ✅ | ? | ❌ | 🟡 **routes fermées (Bison Futé) contournées automatiquement**, au choix et au recalcul ; pas de déviation sur bouchon |
| Radars, zones de danger | ✅ | ? | ✅ | ❌ | ✅ zones de vigilance issues de la liste officielle, sans point exact (règle française) |
| Limitation de vitesse, alerte | ✅ | ? | ✅ | ? | ✅ OpenStreetMap, baisse annoncée, abaissée par la pluie |
| Directions par repère (« au feu ») | ❌ | ✅ (2026, États-Unis) | ❌ | ❌ | ✅ feux OpenStreetMap au point de manœuvre : voix et badge du bandeau |
| Alerte virage serré | ✅ (2026) | ❌ | ? | ❌ | ✅ voix au-dessus de 50 km/h, côté indiqué, Horizon |
| Alerte ralentisseurs | ✅ (2026) | ❌ | ? | ❌ | ✅ OpenStreetMap (`traffic_calming`) des voies lentes du trajet, voix au-dessus de 30 km/h, Horizon |
| Itinéraire économe | ❌ | ✅ | ❌ | ❌ | ✅ estimation selon la vitesse des tronçons, badge « Le plus économe » à partir de 5 % |
| Alerte véhicules d'urgence | ✅ (partenaires) | ❌ | ? | ❌ | ❌ aucune donnée ouverte en France |
| Alerte péage, feux tricolores | ✅ (2026) | ? | ? | ❌ | ✅ « Péage dans 1 km » annoncé, gares et feux dans l'Horizon et sur la carte d'identité du trajet (OpenStreetMap) |
| ZFE et Crit'Air | ? | ? | ? | ❌ | ✅ **base nationale**, verdict selon votre vignette, la date et les horaires |
| Éviter péages, autoroutes, tunnels, ferries | ? | ✅ | ? | ✅ | ✅ les quatre, aussi à la voix |
| Coût du trajet, prix des carburants | ? | ? | ? | ❌ | ✅ coût selon votre véhicule, **prix officiels** des stations |
| Recherche le long du trajet | ? | ✅ | ? | ❌ | ✅ **stations les moins chères sur le trajet** (prix officiels), ajoutées comme arrêt |
| Arrêts multiples, optimisation | 🟡 | 🟡 | ? | 🟡 | ✅ 10 arrêts, ordre optimisé |
| Partir à / arriver à | ? | ✅ | ? | ❌ | ✅ |
| Commandes vocales | ✅ | ✅ | ? | ❌ | ✅ **sur le téléphone, sans service d'IA** |
| Android Auto | ✅ | ✅ | ? | ? | ✅ guidage, incidents, ZFE, vitesse |
| Image dans l'image | ? | ✅ | ? | ? | ✅ |
| Cartes hors connexion | ❌ | ✅ | ❌ | ✅ | 🟡 carte du trajet téléchargeable ; pas de calcul d'itinéraire hors ligne |
| Vie privée | ❌ | ❌ | ? | ✅ | ✅ pas de compte, pas de traqueur, données ouvertes |
| Code libre | ❌ | ❌ | ❌ | ✅ | ✅ GPL-3.0 |
| Voiture garée, péages en flux libre, historique | ? | 🟡 | ? | ❌ | ✅ |
| Transports en commun, marche, vélo | ❌ | ✅ | ❌ | ✅ | ❌ hors du champ (GPS voiture) ; mode trottinette |

## Écarts restants, par priorité

1. **Trafic fluide / chargé sur toutes les routes.** Waze et Google calculent la vitesse réelle à partir des téléphones de leurs utilisateurs. Aucune source ouverte équivalente n'existe en France ; Roadly affiche les incidents officiels et les signalements, sans couleur de densité.
2. **Déviation automatique sur bouchon.** Les routes fermées sont déjà contournées (`exclude_polygons` Valhalla) ; pour un bouchon, il faudrait comparer le temps perdu annoncé au détour, ce que le flux officiel ne donne pas.
3. **Navigation entièrement hors ligne.** Il faudrait embarquer un moteur d'itinéraires (Valhalla compilé pour Android, tuiles par région) : chantier important.
4. **Voies et limitations partout.** Le serveur public FOSSGIS renvoie peu de voies ; un Valhalla auto-hébergé récent les fournit (voir [`API-PROVIDERS.md`](API-PROVIDERS.md)).
5. **Commandes vocales dans Android Auto.** La reconnaissance vocale de la voiture passe par la bibliothèque Car App ; à brancher sur le même interpréteur.
6. **Bornes de recharge détaillées.** Puissance et connecteurs disponibles dans la base nationale IRVE (transport.data.gouv.fr) ; disponibilité temps réel non publiée librement.
7. **Horaires d'ouverture des lieux.** Présents dans OpenStreetMap (`opening_hours`), pas encore lus.

## Sources

- Comparatifs Waze, Google Maps et Coyote : [Test Achats](https://www.test-achats.be/mobilite/autos/news/meilleure-app-gps-waze-ou-maps), [Fiches Auto](https://www.fiches-auto.fr/articles-auto/infractions-radars-exces-de-vitesse/s-2892-waze-vs-coyote-qui-est-le-plus-efficace-.php), [Presse-citron](https://www.presse-citron.net/meilleure-application-gps-laquelle-choisir-pour-prendre-la-route-sereinement/), [BGR](https://bgr.com/2180839/reasons-waze-better-worse-google-maps/).
- Applications respectueuses de la vie privée : [Privacy Guides](https://www.privacyguides.org/fr/maps), [Proton](https://proton.me/learn/european-alternatives/european-navigation-maps).
