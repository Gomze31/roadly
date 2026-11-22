# Migration du téléphone de développement vers la clé de publication

Procédure pour le HONOR Magic 8 Pro. **Rien n'a été exécuté.** Aucune étape ne doit l'être sans l'accord explicite du
propriétaire : la désinstallation efface les données de l'application.

## Constat

| Fait | Source | État |
|---|---|---|
| Le téléphone porte `fr.roadly` signé `CN=Android Debug`, empreinte `7611a323…c9c2` | `ROADLY-1.0/RELEASE-CHECKLIST.md` | relevé antérieur, **à reconfirmer** (étape 1) |
| Cette empreinte est celle de `%USERPROFILE%\.android\debug.keystore` du PC | `ROADLY-1.0/DECISIONS.md` §3.1, commande `keytool` du 2026-11-22 | vérifié sur le PC |
| La clé de publication n'existe pas encore | [`SIGNATURE.md`](SIGNATURE.md) | à créer par le propriétaire |
| Android refuse une mise à jour signée par une autre clé | règle de la plateforme | certain |
| La sauvegarde Android est désactivée (`allowBackup="false"`) | `app/src/main/AndroidManifest.xml` | vérifié |
| Le workflow de publication refuse la clé de debug | `.github/workflows/release.yml` | essayé en local |

**Conclusion : une mise à jour en place n'est pas possible.** Une version signée par la clé de publication ne s'installera
pas par-dessus la version actuelle. Il faut exporter, désinstaller, installer, importer. Seule exception : une rotation
de clé (`apksigner rotate`, Android 13 et plus), écartée parce qu'elle laisserait la clé de debug dans la lignée du
certificat et n'a jamais été essayée (`DECISIONS.md` §3.2, option C).

## Ce qui sera conservé, et ce qui sera perdu

| Donnée | Sort |
|---|---|
| Favoris (dont domicile et travail) | **conservés** par l'export et l'import |
| Trajets et leurs tracés | **conservés** (au-delà de 24 Mio de tracés, les plus anciens perdent leur tracé) |
| Réglages, thème, préférences d'itinéraire | perdus : à refaire |
| Profil du véhicule, fiches trottinette | perdus : à noter avant |
| Recherches récentes | perdues |
| Jeton GitHub | perdu (chiffré par une clé qui disparaît avec l'application) : à recréer ou à ressaisir |
| Adresses des fournisseurs personnalisées | perdues : à noter avant |
| Position de la voiture garée, dernière destination | perdues |
| Autorisations, exemption de batterie, réglage Android Auto | à refaire |
| Statut « installé par Roadly » (mises à jour sans confirmation) | retrouvé après la première mise à jour intégrée |

L'export et l'import sont testés sur la JVM (format, bornes, import sans doublon). **Le sélecteur de fichiers et l'aller-
retour complet n'ont jamais été exécutés sur un téléphone.** C'est le point à valider en premier.

## Procédure

Chaque étape a une condition d'arrêt. Ne pas passer à la suivante si elle n'est pas remplie.

### Avant de toucher au téléphone

1. **Reconfirmer le certificat installé** (lecture seule) :

   ```bash
   adb shell dumpsys package fr.roadly | grep -A3 "signatures"
   ```

   ou, depuis l'APK installé copié sur le PC, `apksigner verify --print-certs`. Attendu : l'empreinte de la clé de debug.
   *Arrêt* : si l'empreinte est autre, cette procédure ne s'applique pas telle quelle.
2. **Sauvegarder la clé de debug** du PC : copier `%USERPROFILE%\.android\debug.keystore` en lieu sûr. Tant que le
   téléphone n'est pas migré, c'est la seule clé qui peut le mettre à jour.
3. **Créer et sauvegarder la clé de publication**, configurer GitHub : [`SIGNATURE.md`](SIGNATURE.md), étapes 1 à 3.
4. **Comparer les deux certificats** : ils diffèrent, par construction. C'est ce qui impose la suite.

### Mettre le téléphone à jour une dernière fois avec la clé de debug

5. Compiler sur le PC une version de debug du code actuel (elle contient l'export) et l'installer par-dessus : même clé,
   `versionCode` supérieur, les données restent.
   *Arrêt* : Réglages › Données › Sauvegarde doit être présent.

### Exporter et vérifier

6. Réglages › Données › Sauvegarde › « Exporter mes favoris et trajets ». Enregistrer **hors de Roadly** (Téléchargements).
7. Copier le fichier `roadly-sauvegarde-AAAA-MM-JJ.json` sur le PC. L'ouvrir : JSON lisible, nombre de favoris et de
   trajets conforme à ce qu'affiche l'application.
   *Arrêt* : fichier absent, vide ou incomplet.
8. Noter à la main ce que l'export ne contient pas : véhicule, fiches trottinette, fournisseurs, réglages utiles.

### Répéter l'import ailleurs

9. Sur un émulateur ou un second appareil : installer une version signée par la clé de publication, importer le fichier,
   contrôler favoris et trajets.
   *Arrêt* : import en échec ou données manquantes. **Ne rien désinstaller tant que cette répétition n'a pas réussi.**

### Basculer

10. Désinstaller Roadly du téléphone. *Point de non-retour : les données locales sont effacées.*
11. Installer la version officielle signée (APK de la release GitHub).
12. Vérifier le certificat installé (commande de l'étape 1) : il doit être celui de [`SIGNATURE.md`](SIGNATURE.md).
13. Importer la sauvegarde. Contrôler favoris et trajets.
14. Refaire : autorisations de position et de notification, exemption de batterie, Android Auto, véhicule, jeton GitHub
    si le dépôt est encore privé.

### Valider la chaîne de mise à jour

15. Publier une version suivante par le workflow.
16. Sur le téléphone : Réglages › Mises à jour la propose, la télécharge, vérifie l'empreinte et le certificat, Android
    demande la confirmation, l'application redémarre à la nouvelle version.
17. Contrôler que favoris, trajets et réglages sont intacts après cette mise à jour.

C'est seulement après l'étape 17 que la chaîne « code vérifié → GitHub → APK signé → mise à jour dans l'application »
est démontrée de bout en bout.

## En cas d'échec

| Échec | Conduite |
|---|---|
| Export impossible ou incomplet (étapes 6 à 8) | ne pas désinstaller ; corriger l'export, republier en debug, recommencer |
| Import en échec sur l'émulateur (étape 9) | ne pas désinstaller ; corriger, recommencer |
| Import en échec après la désinstallation (étape 13) | le fichier exporté reste sur le PC ; corriger l'import et réimporter, les données ne sont pas perdues tant que ce fichier existe |
| La version officielle ne s'installe pas (étape 11) | réinstaller une version de debug depuis le PC et réimporter |
| La mise à jour intégrée échoue (étape 16) | installer l'APK de la release à la main ; relever le message ; ne pas épingler la clé tant que ce n'est pas compris |

## Ce que cette procédure ne couvre pas

- Le passage par Google Play (clé gérée par Google) : `DECISIONS.md`, section 1.
- D'autres appareils : il n'y en a pas d'autres connus. Tout appareil portant une version de debug suit la même procédure.
