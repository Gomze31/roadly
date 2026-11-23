# Ouverture publique du projet : audit et décisions

État au 2026-11-23.

**Correction du même jour : le dépôt `Gomze31/roadly` est déjà public** (vérifié par l'API GitHub : visibilité publique, aucune
étoile, aucun fork, licence affichée GPL-3.0, releases publiques jusqu'à `v0.1.210`). La première version de ce document le
disait privé, d'après d'anciennes notes non revérifiées. L'historique et les APK publiés sont donc déjà exposés.

Ce document rassemble ce qui a été vérifié, ce qui a été préparé, et les décisions du propriétaire.

## 1. Licence

### Constat

| Point | Résultat | Preuve |
|---|---|---|
| Licence du dépôt jusqu'ici | GPL-3.0 | `LICENSE` de `main` |
| Bibliothèques livrées dans l'APK | 186 : 178 Apache-2.0, 2 BSD-2-Clause, 1 BSD-3-Clause, 1 MIT, 4 Android SDK License | [`../THIRD_PARTY_LICENSES.md`](../THIRD_PARTY_LICENSES.md), produit par script le 2026-11-23 |
| Licence copyleft parmi les dépendances | aucune | même fichier |
| Composant non libre | bibliothèques clientes des services Google Play (4 artefacts), module `core/location` seulement | même fichier |
| Polices | Inter et Nunito, SIL OFL 1.1 | `core/designsystem` |
| Auteurs du code | trois identités dans git : deux semblent être le propriétaire, la troisième est un assistant de programmation | `git shortlog` ; **à confirmer par le propriétaire** |
| Code tiers copié dans les sources | non recherché ligne à ligne | **non vérifié** |

### Compatibilité avec Apache 2.0

- Toutes les dépendances libres sont permissives : aucune n'interdit de distribuer Roadly sous Apache 2.0.
- Les bibliothèques des services Google Play ne sont pas libres. Apache 2.0, licence permissive, ne s'oppose pas à ce
  qu'une application les utilise ; c'était un vrai conflit avec la GPL-3.0 (`ROADLY-1.0/DECISIONS.md`, section 4.2). Mais
  elles ne passent pas sous Apache 2.0 : c'est dit dans `NOTICE`, `THIRD_PARTY_LICENSES.md` et `PRIVACY.md`.
- Les données (OpenStreetMap sous ODbL, données de l'État sous Licence Ouverte) ne sont pas embarquées. Leur attribution
  reste due par quiconque distribue l'application.

### Ce qui a été préparé

`LICENSE` contient désormais le texte de la licence Apache 2.0 ; `NOTICE`, `THIRD_PARTY_LICENSES.md` et le README sont
alignés. C'est un changement local, dans un commit séparé, annulable par `git revert`.

### Décision du propriétaire (2026-11-23)

**Apache 2.0 confirmé** par le propriétaire, qui se déclare seul titulaire des droits. Les versions déjà publiées sous
GPL-3.0 (releases jusqu'à `v0.1.210`, et tout l'historique jusqu'au commit de changement de licence) restent disponibles
sous GPL-3.0 pour qui les a reçues ; Apache 2.0 s'applique à partir du commit qui change `LICENSE`.

### Points examinés pour cette décision

1. **Qu'il est le seul titulaire des droits.** Changer de licence n'est possible que pour qui détient tous les droits.
2. **Qu'il renonce au copyleft.** Sous Apache 2.0, un tiers peut reprendre le code dans un produit fermé. Le README
   affichait jusqu'ici l'intention inverse (« toute version modifiée doit rester libre »).
3. **Que les versions déjà remises à quelqu'un sous GPL-3.0 le restent.** Le dépôt étant public, c'est le cas de toutes
   les versions publiées jusqu'ici.
4. Le statut juridique du code produit avec un assistant de programmation est une question ouverte. Elle n'empêche pas de
   choisir la licence ; elle peut affaiblir une action en contrefaçon. À voir avec un juriste si cela compte.

Ceci est une lecture d'ingénieur, pas un avis juridique.

## 2. Secrets et données personnelles

Recherche automatique du 2026-11-23 sur l'arbre de travail et sur **tout l'historique** (toutes les branches, lignes
ajoutées) : clés privées, jetons GitHub, clés AWS et Google, JWT, adresses Supabase, mots de passe affectés, chemins
d'utilisateur, adresses électroniques, adresses IP privées, fichiers sensibles ayant existé.

| Recherché | Arbre de travail | Historique |
|---|---|---|
| Clés privées, keystores, `local.properties`, `.env`, APK | aucun | aucun n'a jamais été suivi par git |
| Jetons et clés d'API | aucun réel (un jeton factice `github_pat_x` dans un test) | aucun |
| Adresse de service privé (Supabase) | aucune | aucune |
| Adresse IP privée | une, fictive, dans un test | idem |
| Chemins contenant le nom d'utilisateur Windows et un prénom | **retirés** de 9 documents le 2026-11-23 | **toujours présents dans l'historique** |
| Adresses électroniques dans les fichiers | aucune personnelle | aucune |
| Adresses électroniques des auteurs de commits | sans objet | **une adresse personnelle** sur une partie des commits |

Limites : une recherche par motifs ne prouve pas l'absence. Les images n'ont pas été examinées.

### À examiner par le propriétaire (non modifié)

| Élément | Pourquoi |
|---|---|
| `docs/captures/` (59 images, 27 Mo) et `docs/images/` | captures d'écran de cartes : vérifier qu'aucune ne montre un domicile, un trajet habituel ou une notification personnelle. Aucune capture ne doit être publiée sans cet examen |
| `core/navigation/src/test/resources/fixtures/junction_*_trace.json` | tracés de test nommés d'après des lieux réels : vérifier qu'ils ne décrivent pas un trajet personnel |
| `docs/superpowers/` | plans de travail internes, très volumineux, sans intérêt pour un lecteur extérieur : à retirer du dépôt public |
| `SILLAXE_PARITY.md`, `docs/BENCHMARK-SILLAXE.md`, `docs/SILLAXE-ANALYSE.md`, `docs/COMPARATIF.md`, `docs/BENCHMARK-GPS.md` | analyses de produits concurrents nommés : à relire ou à retirer avant publication |
| `docs/PROTOCOLE-TERRAIN-HONOR.md`, `docs/VALIDATION-TELEPHONE.md`, `task_plan.md` | documents de travail internes |
| `backend/` | schéma du service communautaire : sans secret trouvé, à relire |

### L'historique

Rendre le dépôt public publie **tout** l'historique : les chemins personnels et l'adresse électronique des commits y
restent, même retirés des fichiers actuels. Trois options, toutes à décider par le propriétaire :

| Option | Effet | Coût |
|---|---|---|
| A. Publier l'historique tel quel | le nom d'utilisateur Windows, un prénom et une adresse électronique deviennent publics | aucun |
| B. Nouveau dépôt public démarrant par un commit unique de l'état actuel | rien d'ancien n'est publié ; l'ancien dépôt reste privé comme archive | l'historique public commence à zéro ; le `versionCode` (nombre de commits) doit garder sa valeur par `roadly.versionCode` ou une base |
| C. Réécrire l'historique (`git filter-repo`) puis publier | historique conservé et nettoyé | opération lourde et irréversible côté GitHub, tous les identifiants de commits changent |

Décision du propriétaire (2026-11-23) : **A, laisser tel quel.** Le dépôt étant déjà public, ces informations sont déjà
exposées ; il n'a ni fork ni étoile. Aucune réécriture n'a été faite.

## 3. Réglages GitHub à poser avant l'ouverture

Posés le 2026-11-23 avec l'accord du propriétaire, et relus par l'API GitHub : environnement `release` (propriétaire relecteur, limité à `main` et aux tags `v*`), protection de `main` (*pull request*, revue des propriétaires de code, contrôle `check`, pas de *force push* ; les administrateurs peuvent passer outre), *secret scanning* et *push protection*, signalement privé des failles, alertes Dependabot, jeton des workflows en lecture seule, approbation des *pull requests* de nouveaux contributeurs.

Restent à faire, par le propriétaire : déplacer les quatre secrets de signature du dépôt vers l'environnement `release` (leur valeur n'est pas lisible), déclarer `ROADLY_SIGNER_SHA256`, protéger les tags `v*` (non fait : une règle de création bloquerait aussi le workflow qui crée le tag).

| Réglage | Valeur | But |
|---|---|---|
| Environnement `release`, relecteur obligatoire | le propriétaire | aucune signature sans son accord ([`SIGNATURE.md`](SIGNATURE.md)) |
| Secrets de signature | dans l'environnement `release` seulement | hors de portée des autres tâches |
| Variable `ROADLY_SIGNER_SHA256` | empreinte du certificat | identité de signature constante |
| Protection de `main` | *pull request* obligatoire, CI verte, revue des propriétaires de code, pas de *force push* | rien n'entre sans relecture |
| Protection des tags `v*` | création réservée au propriétaire | pas de version créée par un tiers |
| Actions › *Fork pull request workflows* | approbation requise pour les nouveaux contributeurs | pas d'exécution automatique de code inconnu |
| Actions › *Workflow permissions* | lecture seule par défaut | moindre privilège |
| *Secret scanning* et *push protection* | activés | filet contre un secret poussé par erreur |
| *Private vulnerability reporting* | activé | canal de `SECURITY.md` |
| Dependabot (alertes) | activé | dépendances vulnérables |

Ce qui est déjà dans le dépôt : `release.yml` n'a aucun déclencheur `pull_request` ; `ci.yml` s'exécute sans secret avec
un jeton en lecture seule ; `CODEOWNERS` désigne le propriétaire.

## 4. Identité de l'application

| Élément | Valeur | Règle |
|---|---|---|
| Identifiant de l'application | `fr.roadly` | réservé aux versions officielles ; ne change pas |
| Certificat de signature | à inscrire dans [`SIGNATURE.md`](SIGNATURE.md) | une seule clé, pour toutes les versions |
| `versionCode` | nombre de commits de `main` | strictement croissant, contrôlé par le workflow |
| `versionName` | `0.1.<commits>`, puis `1.0.<commits − base>` | `roadly.versionBase` dans `gradle.properties` |
| Canal | releases GitHub, dernière version non marquée *pre-release* | pas de canal bêta |
| Métadonnées de mise à jour | `update.json` joint à chaque release : version, fichier, SHA-256, `minSdk`, empreinte du certificat | produit par le workflow, jamais à la main |
| Version minimale d'Android | 10 (API 29) | |

Reconnaître une version officielle : elle vient des releases du dépôt officiel, et son certificat est celui publié dans
`SIGNATURE.md` (commande `apksigner verify --print-certs`). Une compilation faite par un tiers porte un autre certificat ;
Android refuse de l'installer par-dessus une version officielle, et inversement.

## 5. Nom et logo

La licence Apache 2.0 ne donne aucun droit sur le nom ni sur le logo (article 6). Elle ne les protège pas non plus : seul
un dépôt de marque le fait.

Constat : aucune recherche d'antériorité n'a été faite pour « Roadly ». Le nom est court et descriptif ; d'autres produits
peuvent le porter. **Non vérifié.**

Recommandations, à décider par le propriétaire :

1. Faire une recherche d'antériorité (INPI, EUIPO, magasins d'applications) avant d'annoncer le projet.
2. Selon le résultat, déposer la marque (INPI, classes 9 et 42) ou choisir un autre nom tant que le projet n'est pas public.
3. Publier une courte règle d'usage :
   - une version modifiée et redistribuée change de nom, d'icône et d'identifiant d'application ;
   - « compatible avec Roadly » ou « dérivé de Roadly » sont des mentions permises ;
   - le nom et le logo ne servent pas à présenter une version tierce comme officielle.
4. Garder les fichiers du logo sous une mention de droits réservés distincte de la licence du code, si le propriétaire
   veut en contrôler l'usage.

Cette règle n'existe pas encore dans le dépôt : `CONTRIBUTING.md` y renvoie, elle est à écrire une fois le nom confirmé.

## 6. Services publics

Voir [`SERVICES.md`](SERVICES.md). Point bloquant pour une diffusion : le serveur public de Valhalla n'est pas fait pour
une application diffusée (`ROADLY-1.0/DECISIONS.md`, section 2).

## 7. Ce qui reste à faire pour considérer la chaîne complète

| Étape | État |
|---|---|
| Compilation depuis une copie propre du dépôt | voir `task_plan.md` |
| CI verte sur GitHub | jamais exécutée (rien n'est poussé) |
| APK signé par la clé de publication et vérifié | impossible sans la clé ; chemin de signature essayé en local avec la clé de debug, refusée comme prévu |
| APK publié dans une release | non fait |
| Roadly détecte cette release | non fait |
| Téléchargement et vérifications dans l'application | logique testée sur la JVM ; jamais sur un téléphone |
| Installation réelle et conservation des données sur le HONOR | non fait ([`MIGRATION-HONOR.md`](MIGRATION-HONOR.md)) |

## 8. Décisions attendues du propriétaire, dans l'ordre

1. Licence : confirmer Apache 2.0 (section 1).
2. Historique : option A, B ou C (section 2).
3. Contenu public : examen des captures, retrait des documents internes (section 2).
4. Nom : recherche d'antériorité (section 5).
5. Hébergement de Valhalla (section 6).
6. Clé de publication : création, sauvegarde, secrets ([`SIGNATURE.md`](SIGNATURE.md)).
7. Réglages GitHub (section 3).
8. Migration du téléphone ([`MIGRATION-HONOR.md`](MIGRATION-HONOR.md)).
9. Épinglage de la clé dans l'application, puis ouverture du dépôt.

## 9. Constats du 2026-11-23 sur le dépôt public

| Constat | Preuve |
|---|---|
| Les releases publiées sont signées avec la clé de debug du PC | `apksigner verify --print-certs` sur `roadly-0.1.210.apk` téléchargé de la release : `CN=Android Debug`, SHA-256 `7611a323b2f1b159e445174dd57a19c87a42b7d0648de7398e7935de3aa3c9c2` |
| Le fichier publié est conforme à son `update.json` | SHA-256 recalculé identique |
| Les secrets de signature du dépôt contiennent donc la clé de debug | déduit du constat précédent |
| Le téléphone (même certificat) peut aujourd'hui se mettre à jour en place depuis les releases | même clé |

Conséquence : fusionner la *pull request* de cette branche dans `main` arrête les publications (la clé de debug est désormais refusée) jusqu'à la création de la clé de publication. À partir de là, le téléphone doit être migré ([`MIGRATION-HONOR.md`](MIGRATION-HONOR.md)). Tant que la *pull request* n'est pas fusionnée, rien ne change pour le téléphone.
