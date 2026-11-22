# Services en ligne utilisés par Roadly

Roadly n'a pas de serveur à lui. Il s'appuie sur des services publics ou communautaires, exploités par des tiers.

**Aucun de ces services n'est garanti.** Ils sont gratuits, la plupart en usage raisonnable (*fair-use*), sans engagement
de disponibilité ni de débit. Le projet ne promet pas un accès illimité : un service peut ralentir, limiter ou refuser
l'application, et une diffusion large de Roadly sur les serveurs publics n'est pas tenable sans auto-hébergement.

Le détail technique de chaque appel (adresses, paramètres, format) est dans [`API-PROVIDERS.md`](API-PROVIDERS.md). Ce que
chaque service reçoit de l'utilisateur est dans [`../PRIVACY.md`](../PRIVACY.md).

## Tableau des services

| Service | Rôle | Exploitant | Licence des données | Limite connue | Limite appliquée par Roadly | Auto-hébergeable |
|---|---|---|---|---|---|---|
| OpenFreeMap | tuiles de la carte | OpenFreeMap | ODbL (OpenStreetMap) | aucun quota publié, aucune garantie | cache de MapLibre | oui (tuiles OpenMapTiles) |
| Valhalla | itinéraires, profil du tracé | FOSSGIS (serveur public) | ODbL | usage raisonnable, destiné au développement | limiteur de débit commun, une requête par recherche pour le profil, recalcul prioritaire | **oui, recommandé** ([`VALHALLA-HEBERGEMENT.md`](VALHALLA-HEBERGEMENT.md)) |
| Base Adresse Nationale | adresses françaises | IGN (Géoplateforme) | Licence Ouverte 2.0 | 50 requêtes/s par adresse IP, puis blocage de 5 s | 1 requête/s, une seule en vol, retrait après un refus | oui (données ouvertes, addok) |
| Photon | lieux et commerces | Komoot (serveur public) | ODbL | usage raisonnable, peut bloquer un client trop bavard | saisie temporisée | oui |
| Overpass | ralentisseurs, voies (trottinette) | communauté OpenStreetMap | ODbL | usage raisonnable ; politique d'usage non relue pour ce document | une requête en vol, 10 s d'intervalle, cache, retrait après un refus | oui |
| IGN Géoplateforme (WMTS) | photos aériennes | IGN | Licence Ouverte 2.0 | non relevée | seulement si la vue est activée | non |
| Open-Meteo | pluie, température | Open-Meteo | CC BY 4.0 | usage non commercial gratuit, quota journalier | appels espacés | oui |
| Prix des carburants | stations et prix | Ministère de l'Économie | Licence Ouverte 2.0 | non relevée | cache | non (flux public) |
| Points de recharge (IRVE) | bornes rapides | data.gouv.fr | Licence Ouverte 2.0 | non relevée | cache | non (fichier public) |
| Bison Futé | incidents | État | données publiques | non relevée | fichier national téléchargé | non |
| Base des ZFE | zones à faibles émissions | transport.data.gouv.fr | Licence Ouverte 2.0 | non relevée | fichier national téléchargé | non |
| GitHub | mises à jour | GitHub | sans objet | 60 requêtes/h sans jeton par adresse IP | vérification au plus toutes les 30 min | non |
| Service communautaire | signalements | **celui qui le déploie** (aucun par défaut) | sans objet | celles du déploiement | sans objet | oui, uniquement ([`BACKEND.md`](BACKEND.md)) |

« Non relevée » : la limite n'a pas été vérifiée pour ce document. Elle existe peut-être.

## Attribution

L'attribution est une condition de ces licences. Elle est affichée sur la carte et dans Réglages › À propos, et reprise
dans [`../NOTICE`](../NOTICE). Toute version redistribuée de Roadly doit la conserver, en particulier
« © les contributeurs d'OpenStreetMap ».

## Ce que Roadly suppose

- Le réseau peut manquer : le guidage en cours continue sur le tracé déjà reçu ; un recalcul attend le réseau.
- Un service peut répondre « trop de requêtes » : l'application se retire et réessaie plus tard, sans insister.
- Une donnée absente n'est jamais remplacée par une valeur inventée : l'écran indique qu'elle manque.

## Changer de serveur

Réglages › Fournisseurs permet de remplacer l'adresse de Valhalla, de la Base Adresse Nationale, de Photon et d'Overpass.
Un serveur personnel règle à la fois la question des limites et celle de la confidentialité.

## Avant une diffusion publique

Le serveur public de Valhalla et celui de Photon ne sont pas faits pour porter une application diffusée. La décision
d'hébergement (serveur public identifié par un en-tête, ou Valhalla auto-hébergé) est décrite dans
[`ROADLY-1.0/DECISIONS.md`](ROADLY-1.0/DECISIONS.md), section 2. Elle doit être prise avant d'annoncer l'application.
