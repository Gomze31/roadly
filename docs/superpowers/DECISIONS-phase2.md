# Décisions prises pendant l'exécution du plan Phase 2 (ledger SDD)

Source : `.superpowers/sdd/2026-10-30-roadly-phase2-guidage/progress.md`. Chaque ligne : décision prise sans validation humaine, dans l'ordre, avec son coût si elle est fausse.

- Ruling: NavigationState lives in core:navigation (not core:model) — AppError dependency would create a cycle — cost if wrong: none.
- Ruling: AnnouncementPolicy.next gains a `maneuvers` parameter — RouteProgress carries no text — cost if wrong: none.
- Ruling: LocationMode.GUIDANCE(1000) + LocationRepository.setGuidanceActive(Boolean) overriding screen modes — spec §8 1 Hz and HomeViewModel's OFF-after-5 s would cut guidance — cost if wrong: location API change touching core:location consumers (tests must stay green).
- Ruling: service instrumented test replaced by manual `adb dumpsys` checklist — no hilt-android-testing in catalog — cost if wrong: service regressions caught only manually.
- Ruling: lanes/exit signs untested on real data (fixture has none; FOSSGIS may never send lanes) — feature marked « en cours » — cost if wrong: lane strip never shows until a self-hosted Valhalla is configured.
- Ruling: in-memory DataStore in the repository test accepted — Windows file rename limitation in JVM tests; disk persistence is exercised by the app on device — cost if wrong: a serialization regression would only show on device.
- Ruling: controller-performed review for Tasks 3-4 — harness blocked the dispatch; cost if wrong: covered by final review.
- Ruling: fix both; also rule in: ignore fixes with accuracy > 50 m for the 100 m hard rule; arrival also when along ≥ L−25 m and off ≤ 40 m — safety-relevant and cheap — cost if wrong: slightly later off-route detection with bad GPS.
- Ruling: fix all five; for (3) use matched_points[last].edge_index when present/in range, else edges.last() — cost if wrong: a wrong edge → wrong limit on self-hosted only.
- Ruling: beep follows voiceEnabled too (voice off = no sounds) — user expectation; implement in review fix if reviewer agrees it is cheap, else defer to settings task.
- Ruling: fix all three; also rule in: ignore replayed fixes older than run start (stale cached fix → immediate reroute) and `withTimeout(10_000)` on the reroute call — cheap, prevents stale-state hazards — cost if wrong: none significant.
- Ruling: controller-performed review for Task 8 — harness blocked the dispatch; cost if wrong: covered by final review.
- Ruling: controller-performed review for Task 9 — harness blocked the dispatch; cost if wrong: covered by final review + device test.
- Ruling: controller-performed review for Tasks 10-11 — harness blocked the dispatch; cost if wrong: covered by final review + device test.
- Ruling: one fix wave for #1–#3 plus two cheap minors (« Recalcul » spoken once per off-route episode; badge shows raw speed, smoothed kept for logic); heading via new optional `RouteRequest.originHeadingDeg` mapped to Valhalla `heading` + `heading_tolerance` 45 when speed ≥ 2.5 m/s — cost if wrong: one more re-review.
