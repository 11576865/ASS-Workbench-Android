# FIXED V1.1 — emulator regression and tool-directory/IME hardening (2026-10-24)

## Exact-head evidence

Last validated source: `63377555d867567d675241b00c9476c8f4a93ea9`.

- [Source/JVM CI 40074615505](https://github.com/11576865/ASS-Workbench-Android/actions/runs/40074615505): **success**; Android Kotlin/test source and JVM compilation/test checks passed.
- [Emulator CI 40074615531](https://github.com/11576865/ASS-Workbench-Android/actions/runs/40074615531): **failure**. Full suite `187 tests, 4 failures, 0 errors, 0 skipped`.
- Emulator strict native 1440×900 and 1024×768 evidence jobs succeeded; focused projection groups passed. The five-group Event review source tests did **not** appear in the failure list. That does not establish full-suite completion.

Failure ownership from full-suite XML:
1. `batchDraftAndReviewedPreviewFollowSameInstanceAcrossMainAndCanvas`: `tool-BATCH` missing from composed semantics tree after search in spatial directory; `performScrollTo` on a nonexistent lazy child cannot compose it.
2. `inspectorDraftSurvivesToolSwitchAndRotation`: keyboard-close helper waited five seconds on a formerly focused root following viewport reset.
3. `spatialWorkspaceExposesBoardAndNativeEditorWithoutMovingWorldNodes`: `取消驻留` not displayed after pin.
4. `transformFieldsProjectExternalPreviewAndRestoreDrafts`: `geometry-scale-x-1-value` left the semantics tree during `performTextReplacement` after lazy scrolling.

## Bounded repair in this work unit

- In the actual `WorkspaceToolDirectory`, introduce an explicit `LazyListState`, reset position on query changes, and tag the real `LazyColumn` as `tool-results-list`.
- In regression navigation, use `performScrollToNode(hasTestTag(...))` on that collection before selecting `tool-<ID>`. This handles uncomposed candidates and still selects with the search IME active.
- In keyboard-close regression helper, consult the current Activity's IME insets rather than waiting exclusively on a detached Espresso root after rotation. Inspect focused popup insets before declaring a hidden Activity IME sufficient. Timeouts and unknown/visible-IME cases remain failure conditions; no tests are skipped.

These are source-grounded repairs to actual failures, **not remotely validated outcomes**. The resident-pin and Transform lifecycle failures remain open. Keep PR #149 and its R3 parent PR #148 in Draft; do not treat screenshot fixtures as real video/libass acceptance.
