# Confidentialité

Ce document décrit ce que fait l'application Roadly telle qu'elle est publiée par le projet. Une version modifiée par un
tiers peut se comporter autrement.

## En bref

- Roadly n'a **pas de compte**, pas de publicité, pas d'outil d'analyse ni de mesure d'audience.
- Le projet n'exploite **aucun serveur** qui recevrait vos données. Il n'y a pas de « serveur Roadly ».
- Favoris, trajets, véhicule et réglages restent **sur le téléphone**.
- Votre position n'est envoyée qu'aux services nécessaires à la fonction en cours, au moment où elle sert. Ces services
  sont des tiers : ils voient votre adresse IP et appliquent leur propre politique.

## Ce qui reste sur le téléphone

| Donnée | Où | Remarque |
|---|---|---|
| Favoris, domicile, travail, recherches récentes | base locale | exportables (Réglages › Données › Sauvegarde) pour les favoris |
| Trajets et leur tracé | base locale | exportables ; un trajet en cours est aussi sauvegardé dans un fichier local pour survivre à un arrêt de l'application |
| Véhicule, fiches trottinette, réglages | base et préférences locales | |
| Dernière destination guidée | préférences locales | sert à proposer « Reprendre le guidage » |
| Proches à appeler (nom et numéro, quatre au plus) | préférences locales | ceux que vous choisissez vous-même ; jamais envoyés, Roadly ne passe pas l'appel |
| Jeton GitHub (facultatif, dépôt privé) | chiffré par le Keystore Android | envoyé seulement à `api.github.com` |

La sauvegarde Android et le transfert d'appareil à appareil sont désactivés pour Roadly : ces données ne partent ni vers
Google ni vers un autre téléphone. Conséquence : **désinstaller Roadly les efface**. Exportez-les avant.

Les journaux de l'application (journal système d'Android, lisible seulement avec un outil de développement) ne contiennent
ni position, ni adresse, ni nom de rue, ni jeton. Pendant un guidage, ils contiennent le type de la prochaine manœuvre, sa
distance et la vitesse.

## Ce qui est envoyé, à qui, et quand

| Service | Ce qu'il reçoit | Quand |
|---|---|---|
| Valhalla (serveur public de FOSSGIS par défaut) | départ, arrivée, arrêts ; puis la géométrie du tracé | calcul et recalcul d'itinéraire |
| Base Adresse Nationale (Géoplateforme IGN), Photon (Komoot) | texte recherché, position approximative pour trier | recherche |
| OpenFreeMap | tuiles de la zone affichée | carte |
| IGN Géoplateforme | tuiles de la zone affichée | vue photos aériennes, si activée |
| Overpass (OpenStreetMap) | identifiants des voies du tracé | ralentisseurs ; mode trottinette |
| Open-Meteo | dernière position connue | pluie, température |
| Prix des carburants (data.economie.gouv.fr) | cercles autour de soi ou le long du tracé | stations-service |
| Base nationale des points de recharge (data.gouv.fr) | zones le long du tracé | bornes rapides (voiture électrique) |
| Bison Futé, base des ZFE | rien : fichiers nationaux téléchargés | incidents, ZFE |
| GitHub | rien de votre position ; votre adresse IP | recherche et téléchargement des mises à jour |
| Service communautaire | position d'un signalement, zone de 0,1° autour de soi, identifiant d'appareil aléatoire | **seulement si vous le configurez** ; aucun n'est configuré par défaut |

Chaque requête révèle votre adresse IP au service contacté. Le projet ne contrôle ni leurs journaux ni leur durée de
conservation : voir la politique de chacun, liée depuis [`docs/SERVICES.md`](docs/SERVICES.md).

Pour réduire ce qui sort du téléphone : Réglages › Fournisseurs permet de pointer vers vos propres serveurs (Valhalla,
Photon, Overpass), et chaque fonction optionnelle (photos aériennes, ralentisseurs, météo, communauté) se désactive.

## Autorisations Android

| Autorisation | Pourquoi |
|---|---|
| Position précise et approximative | vous situer et vous guider |
| Service de premier plan (localisation) | continuer le guidage écran éteint ; une notification reste affichée |
| Notifications | notification du guidage |
| Internet, état du réseau | services ci-dessus |
| Installer des applications | mise à jour intégrée ; Android demande votre accord |
| Android Auto | affichage sur l'écran de la voiture |

Roadly ne demande pas la position « en arrière-plan » : hors guidage ou conduite libre, il ne lit pas votre position.
Il n'a pas l'autorisation du micro : la recherche et les commandes vocales ouvrent la reconnaissance vocale du téléphone,
qui traite la voix selon ses propres réglages et rend seulement le texte. Il n'a pas l'autorisation de lire vos contacts :
pour « Appeler un proche », le sélecteur de contacts d'Android lui remet la seule fiche que vous choisissez. Il n'a pas non
plus celle d'appeler : il ouvre le téléphone avec le numéro composé, et c'est vous qui lancez l'appel. Il n'accède pas aux
fichiers hors de ceux que vous choisissez pour une sauvegarde.

## Composant de Google

La position est fournie par les services Google Play du téléphone (bibliothèque non libre, voir
[`THIRD_PARTY_LICENSES.md`](THIRD_PARTY_LICENSES.md)). Roadly n'envoie rien à Google par lui-même ; le traitement de la
position par les services Google Play dépend des réglages de votre appareil.

## Vos droits

Le projet ne détient aucune donnée vous concernant : il n'a rien à communiquer, corriger ou effacer. Pour tout effacer,
utilisez Réglages › Données, ou désinstallez l'application. Pour les données reçues par un service tiers, adressez-vous à
ce service.

## Modifications

Toute évolution de ce document passe par une modification publique de ce fichier dans le dépôt. Une nouvelle fonction qui
envoie une donnée à un nouveau service doit y être inscrite avant d'être publiée.
