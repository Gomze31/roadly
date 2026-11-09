# Design system Roadly (LOT 1)

Module : `core:designsystem`. Objectif : une interface de cockpit GPS premium (sombre / clair / automatique), panneaux flottants sur la carte, écrans pleine page en cartes opaques, typographie Inter, identité violet (marque) + orange (appel à l'action). Aucun composant Android « brut » dans les écrans : tout passe par ces composants.

## Tokens

`LocalRoadlyColors.current` (`RoadlyColorScheme`) :

| Jeton | Usage |
|---|---|
| `background` | fond des écrans pleine page et de la carte en attente |
| `surface`, `surfaceElevated` | cartes opaques (listes, réglages), feuilles modales |
| `glass`, `glassBorder`, `glassHighlight` | panneaux translucides posés sur la carte |
| `textPrimary`, `textSecondary`, `textTertiary` | texte principal, secondaire, légendes en capitales |
| `brand`, `brandBright`, `onBrand`, `brandSoft` | violet de marque : tracé, sélection, boutons ronds de navigation, tuiles d'icônes |
| `accent`, `onAccent`, `accentSoft` | orange : appels à l'action (Démarrer, Essayer, Valider), surtitres |
| `routeActive`, `routeInactive`, `routeCasing` | couleurs du tracé MapLibre (halo + trait, alternatives, liseré) |
| `buildings3d` | façades des bâtiments en 3D pendant le guidage |
| `danger`, `dangerSoft`, `warning`, `success` | arrêter / erreurs, vigilance / ralentissements, services / en ligne |
| `speedRing` | bord rouge du panneau de limitation |

Teintes du cockpit : `CockpitTint` (VIOLET par défaut, OCEAN, MINT, CORAL, AMBER) → `RoadlyTheme(tint = …)` remplace `brand`, `brandBright`, `routeActive`.

Typographie (`MaterialTheme.typography`, Inter) : `displayMedium` (durée d'itinéraire en grand), `titleLarge/Medium/Small`, `bodyLarge/Medium/Small`, `labelLarge/Medium/Small`. Styles dédiés : `NavDigits` (vitesse/distance), `Overline` (capitales espacées), `StatValue` (valeurs tabulaires). Mode conduite : `RoadlyTheme(drivingMode = true)` → ×1,3 et cibles 56 dp (`LocalDrivingMode`).

Formes : `RoadlyShapes.Tile` 12, `Card` 18, `Chip` 20, `Button` 16, `Panel` 24, `Pill` cercle, `Sheet` 28 haut. Espacements : `RoadlySpacing.xxs/xs/s/sm/m/l/xl/xxl`, `touchTarget` 48, `touchTargetDriving` 56, `screenEdge` 12. Durées : `RoadlyMotion.fast/normal/slow`.

Effets (`theme/Effects.kt`) : `Modifier.softShadow(shape, color)`, `brandGradient()`, `Modifier.glassHighlight()`.

## Tailles et adaptation à l'écran

- **Maquette de référence : 400 dp de large** (téléphone Android courant). En dessous, `LocalUiScale` réduit l'interface jusqu'à 0,85 (`UiScale.forWidth`) : typographie et tailles marquées `* LocalUiScale.current` / `.scaled()`.
- **Typographie proche de l'échelle Material 3** : titre principal 28 sp, titre 19 sp, corps 15 sp ; police arrondie Nunito pour titres, chiffres, boutons et capitales, Inter pour le texte courant. Mode conduite : ×1,15 seulement (la police est déjà très grasse).
- **Taille de police du téléphone respectée** (sp), jamais neutralisée : pas de hauteur fixe sur un élément qui contient du texte (`heightIn(min = …)`), texte sur une ligne avec « … ».
- **Place réduite** (`isCompactSpace()` : < 380 dp ou police > 115 %) : les libellés secondaires disparaissent (« MODIFIER », icônes au-delà de deux sur une alternative) plutôt que de couper le texte principal.
- **Cibles tactiles ≥ 48 dp** (Material / accessibilité Android), 52 dp pour les boutons ronds de la carte.
- Vérification : rendus à 372 dp (HONOR Magic 8 Pro) et à 360 dp avec police 130 % (`*Screenshots`, `-PscreenshotsDir`).

## Composants

| Composant | Rôle |
|---|---|
| `GlassPanel` | panneau translucide sur la carte (ombre douce, reflet, bord) |
| `SurfaceCard` | carte opaque pour les écrans de liste |
| `GlassButton(style = Primary / Brand / Secondary / Danger / Ghost)` | boutons ; `Brand` = dégradé violet (« Rouler ») ; `Primary` = orange |
| `GlassIconButton` | bouton carré verre (barre du haut) ; `badge` = point orange |
| `RoundIconButton(style = Brand / Danger / Glass / Accent)` | bouton rond plein (recentrer, arrêter, replier) |
| `GlassChip(selected, compact, trailing)` | puces de filtre / option |
| `ComingSoonTag`, `PremiumTag`, `Tag`, `OverlineText`, `StatusDot` | étiquettes et surtitres |
| `IconTile(style = Brand / BrandFilled / Accent / Warning / Danger / Neutral)` | tuile d'icône carrée arrondie ; `ColorDotIcon` pastille ronde |
| `StatTile(value, label)` | valeur + légende en capitales ; `StatRow(label, value)` |
| `SpeedBadge(speed, limit, tolerance)` + `SpeedLimitSign(limit)` | compteur et panneau de limitation |
| `NavInfoBar(eta, minutes, km, showRecenter, onRecenter, onStop)` | barre d'information du guidage |
| `HorizonStrip(items, title, density)` | ruban « la route à venir », échelle log 0–5 km (`HorizonItem`, `HorizonKind`, `HorizonScale`) |
| `SettingsGroup(title)`, `ListRow`, `ToggleRow`, `RowDivider`, `SegmentedControl`, `roadlySwitchColors()` | écrans de réglages et listes |
| `RoadlyTopBar(title, onBack, overline, actions)`, `RoadlyScreen` | barre de titre et fond des écrans pleine page |
| `RouteHeadline`, `RouteOptionRow`, `RouteCard` | panneau « Votre choix » et alternatives |
| `GlassBottomSheet` | feuille modale |
| `EmptyState`, `ErrorState`, `LoadingRow`, `OfflineBanner` | états |
| `ManeuverIcon`, `ManeuverType.icon()` | icônes de manœuvre |
| `GlassSearchBar` | barre de recherche |

Règles :

- Jamais de `Color(0x…)` dans un écran : tout vient de `LocalRoadlyColors`.
- Toute action affichée a un effet réel ; une fonction sans source est `enabled = false` + `ComingSoonTag()`.
- Chaque composant a deux aperçus (`@Preview` Nuit et Jour).
- Les chiffres de guidage utilisent `NavDigits` / `StatValue` (`tnum`).
- Panneaux sur la carte : marge `RoadlySpacing.screenEdge`, coins `RoadlyShapes.Panel`.
- Lisibilité en conduite : contrastes ≥ 4,5:1 texte / fond de panneau ; cibles ≥ 56 dp en mode conduite.
