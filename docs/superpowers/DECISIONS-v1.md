# Décisions prises pendant l'exécution du plan v1 (ledger SDD)

Chaque ligne est une décision prise par le contrôleur sans validation humaine, dans l'ordre chronologique, avec son coût si elle est fausse. Source : `.superpowers/sdd/2026-10-30-roadly-v1/progress.md`.

- Ruling: in-place branch `feat/roadly-v1` instead of a separate worktree — Android/Gradle paths and SDK caches are simpler in one checkout; nothing else uses this folder — cost if wrong: none practical.
- Ruling: FormattersTest clockTime epoch corrected to 1_782_131_520_000L — arithmetic error in plan — cost if wrong: one failing test, trivially visible.
- Ruling: `_permissionInitialized` moved above `_permission` in FusedLocationRepository — Kotlin init order bug — cost if wrong: permission state stuck at UNKNOWN after denial.
- Ruling: Hilt 2.60.1 → 2.58 (2.60.x requires AGP 9) — keeps AGP 8.13.2 from cache; cost if wrong: none (2.58 supports KSP + Kotlin 2.2).
- Ruling: fix all three against the plan's code — spec §5.1 mandates ≥48/56 dp targets and premium finish; GlassPanel gains optional `onClick: (() -> Unit)? = null` applied after clip — cost if wrong: small API addition only used inside designsystem.
- Ruling: (1) fix with resume(value) { _, _, _ -> response.close() }; (2) switch to registerDefaultNetworkCallback + recompute current() in every callback; (3) toggle() on a home/work row deletes the row (un-star = remove, home/work shortcut cleared too) — spec v1 has no separate "retirer domicile" action and a dead star is worse — cost if wrong: user loses the home shortcut when un-starring, recoverable via "Définir comme domicile".
- Ruling: fix both; also fix Double.toString() scientific notation for |coord|<0.001 (Greenwich meridian crosses France) — real-world failure, one-line — cost if wrong: none.
- Ruling: fix both (guard async bodies with safely-style helper; add 2-leg + waypoint tests).
- Ruling: fix both; also rule in minor "camera commands before attach dropped" (fitBounds right after routes arrive is the normal Task 11 flow) → keep one pending camera action replayed on style ready; add detach() on dispose — cost if wrong: slight extra state in controller.
- Ruling: controller-performed scoped re-review for Task 8 round 1 because the dispatch was blocked by the harness — cost if wrong: a missed regression in a 55-line diff, covered again by the final whole-branch review.
- Ruling: fix distance; also rule in two spec-relevant minors: offline with query must still show recents/favorites (spec §6.2), and selectedIsFavorite derived from favorites flow (removes stale flag + uncaught exception in launch) — cost if wrong: small VM reshuffle covered by tests.
- Ruling: controller-performed task review for Task 10 — harness blocked the dispatch; cost if wrong: covered by the final whole-branch review.
- Ruling: fix the map-visibility defect before review (brief defect, spec §6.4/§8 requires origin pick by long-press): styleUrl lifted out of Content into a top-level `RouteScreenState(styleUrl, phase)`-style split — cost if wrong: small state reshape covered by tests.
- Ruling: controller-performed task review for Task 11 — harness blocked the dispatch; cost if wrong: covered by the final whole-branch review.
- Ruling: controller-performed task review for Task 12 — harness blocked the dispatch; cost if wrong: covered by the final whole-branch review.
- Ruling: controller-performed task review for Task 13 — harness blocked the dispatch; cost if wrong: covered by the final whole-branch review.
- Ruling: one fix wave covering #1–#6 + pre-merge triage items + "Auto solaire follows system by day" (spec §5.1) — implement 429 retry and allowBackup=false; LocationManager fallback and cleartext become documented écarts (no new code beyond an explicit error message); cost if wrong: one more fix round before merge.
