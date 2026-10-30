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
| `danger`, `dangerSoft`, `warning`, `success` | arrêter / erreurs, vigilance / ralentissements, services / en ligne |
| `speedRing` | bord rouge du panneau de limitation |

Teintes du cockpit : `CockpitTint` (VIOLET par défaut, OCEAN, MINT, CORAL, AMBER) → `RoadlyTheme(tint = …)` remplace `brand`, `brandBright`, `routeActive`.

Typographie (`MaterialTheme.typography`, Inter) : `displayMedium` (durée d'itinéraire en grand), `titleLarge/Medium/Small`, `bodyLarge/Medium/Small`, `labelLarge/Medium/Small`. Styles dédiés : `NavDigits` (vitesse/distance), `Overline` (capitales espacées), `StatValue` (valeurs tabulaires). Mode conduite : `RoadlyTheme(drivingMode = true)` → ×1,3 et cibles 56 dp (`LocalDrivingMode`).

Formes : `RoadlyShapes.Tile` 12, `Card` 18, `Chip` 20, `Button` 16, `Panel` 24, `Pill` cercle, `Sheet` 28 haut. Espacements : `RoadlySpacing.xxs/xs/s/sm/m/l/xl/xxl`, `touchTarget` 48, `touchTargetDriving` 56, `screenEdge` 12. Durées : `RoadlyMotion.fast/normal/slow`.

Effets (`theme/Effects.kt`) : `Modifier.softShadow(shape, color)`, `brandGradient()`, `Modifier.glassHighlight()`.

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
