# FIXED V1.1 integrated tablet page delivery

## Scope and source outcome

User request: stop fragmentary delivery; implement the complete approved main-page composition as one coherent work package. This is a source implementation package, not a claim of final visual/device acceptance.

Design authority: Figma 5yNY5dMnmpmaOnYxDlEhY5, main 85:3, tabs 85:21, rail 85:35, authoring dock 85:322, inspector 85:337. Existing Native/Material3 components, stable Tool IDs, session draft stores and domain commands are reused.

- Permanent task rail in wide, sufficiently tall non-IME windows. Existing navigation, specialists, fonts and full directory remain actual commands; existing semantics tags are retained. Style/text retain explicit entries rather than disappearing behind categorization. Command strip keeps specialist menu, preview preference and separate experimental host switch. Narrow/IME-constrained windows keep the existing task bar.
- New V1.1 geometry: at 1440px, 56 rail / 309 Event navigator / 326 inspector / remaining center. Finite video and Raw caps preserve a permanent live timeline; user-hidden preview stays hidden. Legacy R3 layout policy/tests are retained separately.
- Four task scenes also available on >=720dp tablet windows. Central authoring shows the actual canonical Raw line and a separate dirty-state label above the existing shared Raw editor. It does not infer source/translation semantics from Raw line breaks.
- Wide docked Event timing inputs are vertical rather than two narrow fields crammed beside an Apply button. Narrow and SPATIAL timing layouts retain their prior implementation.
- Both wide and dual-pane inspectors include actual Event-scoped saved-document QC counts, navigation to real QC, and a full-width current-Raw review action. The action captures a frozen receipt; confirmation revalidates session, canonical Event text and exact draft text. It never pretends to apply all Style/geometry/metadata drafts atomically.

## Verification and screenshots

Local checks executed: 15 strict PNG/instrumentation validator tests, seven compact evidence extractor tests, bash syntax, whitespace and Actions artifact-policy checks. All passed. Android Gradle/JVM/Compose execution is unavailable locally and remains external Pending.

New tests cover reference column allocation/finite budgets, rail command reachability, and inspector review preserving canonical data while an external edit invalidates the receipt. Existing device/lifecycle/projection/Undo tests remain enabled.

Native screenshot gates now explicitly run 1440x900 and 1024x768 via direct instrumentation before APK uninstall, alongside the existing independent 1600x960 R3 gate. They wait for the correct landscape width and hidden IME, require real production surfaces and trusted foreground ownership, verify one completed test, and validate exact PNG dimensions/chunk CRC/pixel data. Failures propagate red; neither image validity nor compile success is design parity. Target-size capture receipts are included in compact CI evidence.

The capture host contains two deterministic Events and disables renderer/media. These are real Native production-layout fixture screenshots, not real video/libass acceptance. No new screenshot has yet been obtained for this source package.

## Explicit gaps and risks

- Actual screenshot-to-Figma comparison, real media/renderer/tablet/IME acceptance and historical failing regressions remain open.
- Real bilingual source/translation mapping, full property/provenance micro-layout and review scope beyond Raw are unfinished.
- Reuse native app Material icons. Direct Figma SVG download failed with proxy timeout; exact glyph parity is not claimed.
- Existing global project bar, all 18 professional tools, SPATIAL implementation, ASS parser and domain ownership are retained.
- Extra vertical/compact chrome consumes real space: validate large text, keyboard and very short windows on the new head.

## UIGS Intake

Foundry dedupe located the exact existing Candidate: inbox/candidates/2026-10-14-product-block-delivery-over-pr-fragmentation.md. Append the new explicit user feedback and this source response after durable product checkpoint; no Canonical modification or general productivity claim.

## Verified source checkpoint and recoverable visual evidence

Source `020e20761b024b64502c8ea582c163db86aa2ca9` passed source/JVM workflow
`39956067332`. Emulator workflow `39956067357` validated both complete native
captures: 1440×900 (134758 bytes, SHA256
`2f8be9e6b8abce36284927958a2e009c6fb4a7c4f40ce3f1ca30852711e9e472`) and
1024×768 (89506 bytes, SHA256
`1e3460a7a2c9c49af7d8e56871f08423ce4fd61ae317fcadfe45347d591e89ad`).
The four projection groups passed 22 tests. The full suite ran 179 tests with
4 failures, 0 errors and 0 skipped. This is **not** a full regression pass.

The complete editor job log could not be downloaded through the connector
(internal/transport errors). Therefore, the native bitmaps have not yet been
visually assessed against Figma. Capture integrity is not visual parity.

Two independent `native-visual-<size>` jobs now recover one full compositor
PNG each from the original job's runner-owned, run-as file envelope. Android logcat chunks are excluded because logd has dropped image chunks in earlier runs. They require its matching valid gate
receipt and verify byte count, SHA256, PNG CRC and decoded dimensions before
emitting a bounded, ordered envelope. They record run, attempt, tested merge SHA,
source head SHA and the original regression result. These jobs do not alter
regression status, upload artifacts, replace the image with a thumbnail, or
claim media renderer coverage. A missing/oversized/corrupt capture fails explicitly.
Seven local defensive tests cover valid round-trip, wrong dimensions, missing or
forged receipt, missing chunks, a forged non-PNG payload and exclusion of Android logcat envelopes.

The bounded repair also removes compact directory `hideKeyboard()` before
result selection (the same dialog-dismissal problem already handled in the
spatial/capture drivers), and avoids requesting subtitle navigation twice after
rotation. All existing display, draft persistence, document, undo and lifecycle
assertions remain. Both repair effects require new Android execution. The spatial
pin and geometry-field failures remain unresolved and visible.
