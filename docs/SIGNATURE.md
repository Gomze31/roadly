# Signature des versions officielles

Android n'installe une mise à jour que si elle est signée par la même clé que l'application déjà installée. La clé de
signature **est** l'identité de Roadly : qui la détient peut publier une mise à jour que tous les téléphones accepteront.

État au 2026-11-23 : la clé de publication est créée et son empreinte déclarée (section « Certificat officiel »). Restent
à faire : ranger ses secrets dans l'environnement `release` (étape 3), publier une première version signée, migrer le
téléphone, qui porte encore une version signée avec la clé de debug du PC, puis épingler la clé (étape 5).

## Ce qu'implique le choix de la clé (à lire avant de la créer)

1. **Le choix est définitif.** Changer de clé plus tard oblige chaque utilisateur à désinstaller (perte des données
   locales), ou impose une rotation de clé, qui ne fonctionne en place qu'à partir d'Android 13.
2. **La perte de la clé est irréparable.** Sans elle, plus aucune mise à jour ne s'installe par-dessus les versions
   publiées. Il n'existe pas de procédure de récupération hors Google Play.
3. **Une fuite de la clé est grave.** Un tiers qui la détient, et qui parvient à faire télécharger son APK, met à jour
   l'application des utilisateurs. La clé ne doit exister qu'à trois endroits : le coffre du propriétaire, une copie hors
   ligne, les secrets GitHub.
4. **Le téléphone actuel devra être réinstallé une fois** (voir [`MIGRATION-HONOR.md`](MIGRATION-HONOR.md)) : sa version
   est signée par la clé de debug, que le pipeline refuse désormais.
5. **La clé de debug ne peut pas servir de clé de publication** : ses mots de passe sont publics (`android`), son
   certificat s'appelle `CN=Android Debug`, et le SDK peut la régénérer sans prévenir. Le workflow de publication refuse
   tout APK signé avec elle.
6. **Google Play** exigerait sa propre gestion de clé (Play App Signing). Une version Play et une version GitHub signées
   différemment ne se mettent pas à jour l'une l'autre.

Options comparées, avec leurs sources : [`ROADLY-1.0/DECISIONS.md`](ROADLY-1.0/DECISIONS.md), section 3.
Recommandation : une clé dédiée (option B).

## 1. Créer la clé (propriétaire seulement, hors ligne)

Sur le PC du propriétaire, dans un dossier hors du dépôt :

```bash
keytool -genkeypair -v -keystore roadly-release.jks -alias roadly -keyalg RSA -keysize 4096 -validity 10000
```

- Deux mots de passe forts et différents (keystore, clé), générés par un gestionnaire de mots de passe.
- Nom du certificat : au choix (`CN=Roadly`). Il est public et ne peut plus changer.

Relever l'empreinte du certificat. Elle est **publique** et sert partout ensuite :

```bash
keytool -list -v -keystore roadly-release.jks -alias roadly
```

Ligne `SHA256:` du résultat. L'inscrire dans la section « Certificat officiel » ci-dessous.

## 2. Sauvegarder

| Copie | Où | Contenu |
|---|---|---|
| Principale | gestionnaire de mots de passe du propriétaire | fichier `.jks` en pièce jointe, les deux mots de passe, l'alias |
| Hors ligne | support chiffré rangé ailleurs que le PC (clé USB chiffrée) | fichier `.jks` ; mots de passe sur papier, à part |

- Jamais dans le dépôt (`.gitignore` exclut `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`).
- Jamais dans un dossier synchronisé en clair, ni dans un message.
- Vérifier une fois par an que la copie hors ligne s'ouvre : `keytool -list -keystore <copie>`.
- Conserver aussi `%USERPROFILE%\.android\debug.keystore` tant que le téléphone n'est pas migré : c'est la seule clé qui
  peut encore mettre à jour sa version actuelle.

## 3. Configurer GitHub

Dépôt › Settings › **Environments** › créer l'environnement `release` :

| Réglage | Valeur |
|---|---|
| Required reviewers | le propriétaire (lui seul) |
| Deployment branches and tags | `main` et les tags `v*` |
| Environment secrets | `ROADLY_KEYSTORE_BASE64`, `ROADLY_KEYSTORE_PASSWORD`, `ROADLY_KEY_ALIAS`, `ROADLY_KEY_PASSWORD` |

Dépôt › Settings › Secrets and variables › Actions › **Variables** : `ROADLY_SIGNER_SHA256` = empreinte relevée à l'étape 1.

Encodage du fichier de clé (PowerShell, résultat dans le presse-papiers) :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("roadly-release.jks")) | Set-Clipboard
```

Supprimer ensuite les quatre secrets du même nom au niveau du dépôt s'ils existent : seuls ceux de l'environnement
doivent rester, sinon la protection de l'environnement ne protège rien.

**Réglage en vigueur depuis le 2026-11-23, choix du propriétaire : publication entièrement automatique.** Le relecteur
obligatoire de l'environnement `release` et la relecture obligatoire sur `main` ont été retirés. Une *pull request* dont la
CI est verte peut se fusionner seule (*auto-merge*), et toute fusion de code dans `main` est signée et publiée sans
intervention. Première version publiée ainsi : `v0.1.245`.

Ce qui protège encore : seul le propriétaire a un droit d'écriture sur le dépôt ; `main` exige une *pull request* et le
contrôle `check` ; l'environnement n'accepte que `main` et les tags `v*` ; le workflow refuse une clé de debug et tout
certificat autre que `ROADLY_SIGNER_SHA256`.

**À revoir avant de donner un droit d'écriture à quiconque** : cette personne pourrait publier une version officielle.
Remettre alors le relecteur obligatoire sur l'environnement `release` (tableau ci-dessus) et la relecture sur `main`.

## 4. Ce que vérifie le workflow

La tâche `sign` échoue, et rien n'est publié, si :

- un secret manque ;
- l'APK signé ne passe pas `apksigner verify` pour Android 10 ;
- il porte plus d'un signataire ;
- le certificat est une clé de debug Android ;
- la variable `ROADLY_SIGNER_SHA256` est absente ;
- le certificat n'est pas celui de `ROADLY_SIGNER_SHA256`.

Vérifié en local le 2026-11-23 : signature et vérification passent, et la clé de debug est refusée. Exécuté sur GitHub le
même jour : `v0.1.243` signée et publiée. Les autres refus (variable absente, certificat différent, secret manquant) n'ont
pas été essayés.

Deux exécutions ont d'abord échoué sans rien publier : `apksigner` 37 (GitHub) écrit `V3.0 Signer: certificate …` et non
`Signer #1 certificate …` comme la version 36 du poste de développement. Le script ne lit plus que la fin de ligne.

## 5. Clé épinglée dans l'application

L'empreinte du certificat officiel est inscrite dans `SignerCheck.PINNED_SIGNERS`
(`core/common/src/main/java/fr/roadly/core/common/update/AppUpdate.kt`). À partir de la version qui contient ce changement, la
mise à jour intégrée refuse, avant de rien remettre à Android :

- tout APK lisible signé par une autre clé, même si l'application installée porte cette autre clé.

Un APK dont le certificat ne peut pas être lu sur l'appareil est remis à Android, qui tranche. Android refuse lui-même toute
mise à jour signée par une autre clé que celle de l'application installée : refuser ce cas dans l'application n'ajouterait
aucune sécurité, et bloquerait toutes les mises à jour sur un appareil où cette lecture échoue.

**Ce que l'épinglage apporte réellement** : un message clair (« réinstallation nécessaire ») avant l'installation, et le refus
d'une mise à jour signée en debug sur une application elle-même en debug. La garantie de fond reste celle d'Android.

**Non observé** : la lecture du certificat d'un APK téléchargé (`PackageManager.getPackageArchiveInfo`) n'a été vérifiée que
sous Robolectric. La mise à jour intégrée de 0.1.243 vers 0.1.245, réussie sur le téléphone le 2026-11-23, ne le prouve pas :
la version 0.1.243 n'était pas épinglée et aurait installé dans les deux cas.

Changer de clé plus tard oblige chaque utilisateur à désinstaller (perte des données
   locales), ou impose une rotation de clé, qui ne fonctionne en place qu'à partir d'Android 13.
2. **La perte de la clé est irréparable.** Sans elle, plus aucune mise à jour ne s'installe par-dessus les versions
   publiées. Il n'existe pas de procédure de récupération hors Google Play.
3. **Une fuite de la clé est grave.** Un tiers qui la détient, et qui parvient à faire télécharger son APK, met à jour
   l'application des utilisateurs. La clé ne doit exister qu'à trois endroits : le coffre du propriétaire, une copie hors
   ligne, les secrets GitHub.
4. **Le téléphone actuel devra être réinstallé une fois** (voir [`MIGRATION-HONOR.md`](MIGRATION-HONOR.md)) : sa version
   est signée par la clé de debug, que le pipeline refuse désormais.
5. **La clé de debug ne peut pas servir de clé de publication** : ses mots de passe sont publics (`android`), son
   certificat s'appelle `CN=Android Debug`, et le SDK peut la régénérer sans prévenir. Le workflow de publication refuse
   tout APK signé avec elle.
6. **Google Play** exigerait sa propre gestion de clé (Play App Signing). Une version Play et une version GitHub signées
   différemment ne se mettent pas à jour l'une l'autre.

Options comparées, avec leurs sources : [`ROADLY-1.0/DECISIONS.md`](ROADLY-1.0/DECISIONS.md), section 3.
Recommandation : une clé dédiée (option B).

## 1. Créer la clé (propriétaire seulement, hors ligne)

Sur le PC du propriétaire, dans un dossier hors du dépôt :

```bash
keytool -genkeypair -v -keystore roadly-release.jks -alias roadly -keyalg RSA -keysize 4096 -validity 10000
```

- Deux mots de passe forts et différents (keystore, clé), générés par un gestionnaire de mots de passe.
- Nom du certificat : au choix (`CN=Roadly`). Il est public et ne peut plus changer.

Relever l'empreinte du certificat. Elle est **publique** et sert partout ensuite :

```bash
keytool -list -v -keystore roadly-release.jks -alias roadly
```

Ligne `SHA256:` du résultat. L'inscrire dans la section « Certificat officiel » ci-dessous.

## 2. Sauvegarder

| Copie | Où | Contenu |
|---|---|---|
| Principale | gestionnaire de mots de passe du propriétaire | fichier `.jks` en pièce jointe, les deux mots de passe, l'alias |
| Hors ligne | support chiffré rangé ailleurs que le PC (clé USB chiffrée) | fichier `.jks` ; mots de passe sur papier, à part |

- Jamais dans le dépôt (`.gitignore` exclut `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`).
- Jamais dans un dossier synchronisé en clair, ni dans un message.
- Vérifier une fois par an que la copie hors ligne s'ouvre : `keytool -list -keystore <copie>`.
- Conserver aussi `%USERPROFILE%\.android\debug.keystore` tant que le téléphone n'est pas migré : c'est la seule clé qui
  peut encore mettre à jour sa version actuelle.

## 3. Configurer GitHub

Dépôt › Settings › **Environments** › créer l'environnement `release` :

| Réglage | Valeur |
|---|---|
| Required reviewers | le propriétaire (lui seul) |
| Deployment branches and tags | `main` et les tags `v*` |
| Environment secrets | `ROADLY_KEYSTORE_BASE64`, `ROADLY_KEYSTORE_PASSWORD`, `ROADLY_KEY_ALIAS`, `ROADLY_KEY_PASSWORD` |

Dépôt › Settings › Secrets and variables › Actions › **Variables** : `ROADLY_SIGNER_SHA256` = empreinte relevée à l'étape 1.

Encodage du fichier de clé (PowerShell, résultat dans le presse-papiers) :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("roadly-release.jks")) | Set-Clipboard
```

Supprimer ensuite les quatre secrets du même nom au niveau du dépôt s'ils existent : seuls ceux de l'environnement
doivent rester, sinon la protection de l'environnement ne protège rien.

**Réglage en vigueur depuis le 2026-11-23, choix du propriétaire : publication entièrement automatique.** Le relecteur
obligatoire de l'environnement `release` et la relecture obligatoire sur `main` ont été retirés. Une *pull request* dont la
CI est verte peut se fusionner seule (*auto-merge*), et toute fusion de code dans `main` est signée et publiée sans
intervention. Première version publiée ainsi : `v0.1.245`.

Ce qui protège encore : seul le propriétaire a un droit d'écriture sur le dépôt ; `main` exige une *pull request* et le
contrôle `check` ; l'environnement n'accepte que `main` et les tags `v*` ; le workflow refuse une clé de debug et tout
certificat autre que `ROADLY_SIGNER_SHA256`.

**À revoir avant de donner un droit d'écriture à quiconque** : cette personne pourrait publier une version officielle.
Remettre alors le relecteur obligatoire sur l'environnement `release` (tableau ci-dessus) et la relecture sur `main`.

## 4. Ce que vérifie le workflow

La tâche `sign` échoue, et rien n'est publié, si :

- un secret manque ;
- l'APK signé ne passe pas `apksigner verify` pour Android 10 ;
- il porte plus d'un signataire ;
- le certificat est une clé de debug Android ;
- la variable `ROADLY_SIGNER_SHA256` est absente ;
- le certificat n'est pas celui de `ROADLY_SIGNER_SHA256`.

Vérifié en local le 2026-11-23 : signature et vérification passent, et la clé de debug est refusée. Exécuté sur GitHub le
même jour : `v0.1.243` signée et publiée. Les autres refus (variable absente, certificat différent, secret manquant) n'ont
pas été essayés.

Deux exécutions ont d'abord échoué sans rien publier : `apksigner` 37 (GitHub) écrit `V3.0 Signer: certificate …` et non
`Signer #1 certificate …` comme la version 36 du poste de développement. Le script ne lit plus que la fin de ligne.

## 5. Clé épinglée dans l'application

L'empreinte du certificat officiel est inscrite dans `SignerCheck.PINNED_SIGNERS`
(`core/common/src/main/java/fr/roadly/core/common/update/AppUpdate.kt`). À partir de la version qui contient ce changement, la
mise à jour intégrée refuse, avant de rien remettre à Android :

- tout APK signé par une autre clé, même si l'application installée porte cette autre clé ;
- tout APK dont le certificat ne peut pas être lu.

**Risque connu.** La lecture du certificat d'un APK téléchargé (`PackageManager.getPackageArchiveInfo`) n'a jamais été
observée sur un téléphone, seulement sous Robolectric. Si elle échouait sur un appareil, une version épinglée refuserait
toutes les mises à jour intégrées ; il resterait l'installation manuelle de l'APK de la release, qu'Android accepte (même
clé). C'est pourquoi une mise à jour intégrée doit avoir réussi une fois, sur une version non épinglée, avant de publier une
version épinglée.

Changer de clé plus tard : ajouter la nouvelle empreinte à cette liste dans une version encore signée par l'ancienne, la
laisser se diffuser, puis signer avec la nouvelle (rotation `apksigner`).

## Certificat officiel

| | |
|---|---|
| Identifiant de l'application | `fr.roadly` |
| Empreinte SHA-256 du certificat | `fd785ce163d39cf00ab361b3e5c2bd4b9b49b8d074a91df59d9156b7e0f27834` |
| La même, telle que l'affiche `keytool` | `FD:78:5C:E1:63:D3:9C:F0:0A:B3:61:B3:E5:C2:BD:4B:9B:49:B8:D0:74:A9:1D:F5:9D:91:56:B7:E0:F2:78:34` |
| Déclarée dans la variable GitHub `ROADLY_SIGNER_SHA256` | oui, le 2026-11-23 |

Clé créée par le propriétaire le 2026-11-23. Les versions publiées avant (jusqu'à `v0.1.210`) portent la clé de debug
`7611a323…c9c2` et ne sont pas des versions officielles au sens de ce document.

Première version officielle : **`v0.1.243`**, publiée le 2026-11-23 par le workflow après approbation du propriétaire. Contrôle
indépendant le même jour, sur les fichiers téléchargés de la release : `sha256sum -c SHA256SUMS.txt` conforme, SHA-256 de
l'APK identique à `update.json`, `apksigner verify --print-certs` : `CN=Gomez Yannis, O=Synxio, L=Toulouse, C=FR`, empreinte
ci-dessus, paquet `fr.roadly`, `versionCode` 243.

## Vérifier un APK téléchargé

Chaque release publie l'APK, `update.json`, `SHA256SUMS.txt` et, dans ses notes, l'empreinte du fichier et celle du
certificat.

Intégrité du fichier :

```bash
sha256sum -c SHA256SUMS.txt
```

Sous Windows :

```powershell
Get-FileHash roadly-1.0.0.apk -Algorithm SHA256
```

Identité du signataire (outil `apksigner` du SDK Android) :

```bash
apksigner verify --print-certs roadly-1.0.0.apk
```

La ligne `Signer #1 certificate SHA-256 digest` doit être l'empreinte du tableau ci-dessus. L'empreinte du fichier seule
ne prouve rien sur l'auteur : elle vient de la même page que le fichier. C'est le certificat qui fait foi.

## Perte, fuite, rotation

| Situation | Conduite |
|---|---|
| Clé perdue, sauvegarde intacte | restaurer depuis la sauvegarde ; rien d'autre |
| Clé et sauvegardes perdues | nouvelle clé, annonce publique, réinstallation par chaque utilisateur après export de ses données |
| Clé peut-être copiée | retirer les secrets GitHub ; nouvelle clé ; publier une version à clé tournée (`apksigner rotate`), en place à partir d'Android 13, réinstallation en dessous ; annoncer l'ancienne empreinte comme révoquée |
| Changement volontaire de clé | même mécanisme de rotation, préparé et essayé sur émulateur avant |

La rotation n'a jamais été essayée sur ce projet.
