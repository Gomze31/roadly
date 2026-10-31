# Intégration Android (LOT 12)

## Points d'entrée externes

Tout arrive dans `MainActivity` (`handleIntent`), est traduit par `ExternalIntents.parse(action, dataString)` en `ExternalRequest`, puis traité par `RoadlyNavHost` une fois le graphe posé (`LaunchedEffect(externalRequest)`).

| Entrée | Intent | Résultat |
|---|---|---|
| Raccourci / widget « Domicile » | `fr.roadly.action.NAVIGATE_HOME` | Itinéraire vers le domicile ; non défini → recherche + message |
| Raccourci / widget « Travail » | `fr.roadly.action.NAVIGATE_WORK` | Idem travail |
| Raccourci / widget « Rechercher » | `fr.roadly.action.SEARCH` | Écran de recherche |
| Autre application, Assistant | `VIEW geo:lat,lon`, `geo:lat,lon?q=libellé`, `geo:0,0?q=lat,lon(libellé)` | Itinéraire direct |
| Autre application, Assistant | `VIEW geo:0,0?q=adresse`, `google.navigation:q=adresse` | Recherche pré-remplie (`SearchRoute.query`) |
| Autre application, Assistant | `google.navigation:q=lat,lon` | Itinéraire direct |

Tests JVM : `app/src/test/.../ExternalIntentsTest.kt`. Vérification sur appareil :

```bash
adb shell am start -a android.intent.action.VIEW -d "geo:43.6045,1.444?q=Capitole" fr.roadly
```

```bash
adb shell am start -a android.intent.action.VIEW -d "google.navigation:q=Gare%20Matabiau" fr.roadly
```

```bash
adb shell am start -a fr.roadly.action.NAVIGATE_HOME -n fr.roadly/fr.roadly.app.MainActivity
```

## Raccourcis statiques

`app/src/main/res/xml/shortcuts.xml` (déclaré par `android.app.shortcuts` sur `MainActivity`) : domicile, travail, rechercher. Un raccourci « Signaler » exigerait le service communautaire configuré et une position : non ajouté (règle « aucun bouton inactif »).

## Widget

`RoadlyWidgetProvider` + `res/layout/widget_roadly.xml` + `res/xml/widget_roadly_info.xml` : trois actions (RemoteViews, `PendingIntent` immuables), fond et accent alignés sur la palette jour (`values/colors.xml`) et nuit (`values-night/colors.xml`). Pas de données affichées (le domicile et le travail restent privés), donc pas de rafraîchissement périodique.

## Assistant et commandes vocales

« Navigue vers X » envoyé par l'Assistant Google à l'application de navigation choisie arrive sous `google.navigation:q=X` : couvert. Les capacités App Actions (`shortcuts.xml` avec `<capability android:name="actions.intent…">`) exigent une fiche Play Console publiée et le test via l'outil App Actions : non déclarées tant que l'application n'est pas publiée.

## Android Auto — à faire

`androidx.car.app:app` n'est pas dans le cache Gradle hors ligne, donc rien n'est livré (pas de déclaration fictive). Marche à suivre :

1. Dépendance `androidx.car.app:app:1.4.0` (ou plus récente) dans un module `feature:auto`.
2. Manifest : `<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc"/>` avec `<automotiveApp><uses name="template"/></automotiveApp>`, service `RoadlyCarAppService` (`androidx.car.app.CarAppService`, catégorie `androidx.car.app.category.NAVIGATION`), `minCarApiLevel` 1.
3. Écrans : `NavigationTemplate` branché sur `NavigationSession` (manœuvre, distance, ETA, carte via `SurfaceCallback` + MapLibre `MapSnapshotter` ou rendu Surface), `SearchTemplate` pour la destination, `MessageTemplate` pour les erreurs. Le guidage reste porté par `NavigationService` (notification + TTS), partagé avec le téléphone.
4. Test : Desktop Head Unit (DHU) du SDK Android, puis revue Google Play (catégorie Navigation).
