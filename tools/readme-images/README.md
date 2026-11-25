# Images du README

Les images de `docs/images/` sont des **montages d'illustration**, pas des captures de téléphone : les écrans sont les
rendus JVM de l'application (tests `*Screenshots` et `NavigationFullScreenScreenshotTest`), posés sur un rendu des styles
de carte Roadly par MapLibre GL JS, avec les tuiles OpenFreeMap. Le tracé, la zone de danger et la voiture du guidage
sont dessinés par l'outil, sur une route réelle des tuiles.

## Refaire les images

Prérequis : Node.js, Playwright avec Chromium, et `npm install maplibre-gl` dans un dossier de travail (hors du dépôt),
dont le chemin est donné par `MAPLIBRE_DIR`.

```bash
./gradlew testDebugUnitTest --tests '*Screenshots*' -PscreenshotsDir=/chemin/ecrans --continue
./gradlew :feature:navigation:testDebugUnitTest --tests '*NavigationFullScreenScreenshotTest*'
```

```bash
export MAPLIBRE_DIR=/chemin/travail
R=feature/navigation/build/outputs/roborazzi
node tools/readme-images/montage.js core/map/src/main/assets/styles/roadly-dark.json \
  $R/full_vigilance_night_montage_black.png $R/full_vigilance_night_montage_white.png /chemin/sortie/screen-nav.png 2.3712 48.6790 navzone
node tools/readme-images/montage.js core/map/src/main/assets/styles/roadly-light.json \
  /chemin/ecrans/home.png E9E5DF /chemin/sortie/screen-home.png 1.4480 43.6045 carte
node tools/readme-images/montage.js core/map/src/main/assets/styles/roadly-light.json \
  /chemin/ecrans/route_choice.png E9E5DF /chemin/sortie/screen-route.png 1.4480 43.6045 plan
node tools/readme-images/hero.js /chemin/sortie core/designsystem/src/main/res/font /chemin/sortie/hero.png
```

Les captures de `docs/images/captures/` se font de la même façon, avec les autres rendus `*_montage_black` / `_white`
(`motorway_exit_night`, `roundabout_inside_night`, `cruise_day`) et le mode `nav` ; `navzone` ajoute une zone de danger en
cours sur le tracé. `node tools/readme-images/reduire.js 720 <fichiers>` ramène ensuite les images à 720 px de large.

`screen-onboarding.png` et `android-auto-hud.png` sont les rendus `onboarding_route.png` et `car_hud.png`, tels quels.

Le guidage est rendu deux fois, sur fond noir puis blanc : la différence donne la transparence exacte des panneaux et de
leurs ombres. Les autres écrans, rendus sur un aplat clair, sont détourés par couleur.
