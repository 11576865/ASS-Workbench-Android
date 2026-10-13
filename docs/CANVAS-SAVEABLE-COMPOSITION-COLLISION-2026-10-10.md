# Canvas SaveableStateProvider key collisions — Android Emulator regression

Date: 2026-10-10
Lifecycle: repair-submitted
Evidence: ASS-Workbench-Android PR #138, commit `ff81f273e7c4c6d9b3ffcaa4cdb80111ab29c96c`; Android Emulator Regression run 38027904104.

## Observed failure

Android CI and Fontconfig were green, but the emulator ran 120 tests with
22 failures. Several independently exercised flows, including
`offscreenMediaSuspendsWithoutUnmountingGeneralEditors`,
`focusedToolTitleSwitchesDirectlyToAnotherToolWithoutLosingSpatialLayout`,
`unifiedCanvasSwitchesDirectlyBetweenRealPreviewAndSubtitleAuthoring` and
`spatialWorkspaceLongPressDragExtractsShearWithoutEditingAss`, raised
`java.lang.IllegalArgumentException: Key ... was used multiple times`
from `androidx.compose.runtime.saveable.SaveableStateHolderImpl`.

The canvas used a single `SaveableStateHolder` and selected keys from
`sessionId + ToolInstanceID` across the board, native focused editor,
layered audio/video, and reference preview. Overlap during composition/
navigation permitted the same provider key to be mounted twice at once.

## Scoped correction

- Explicitly namespace saveable providers by presentation role:
  `BOARD`, `FOCUSED`, `FOCUSED_LAYER`, or `REFERENCE`.
- Layer/reference keys also include the focused owner ID because overlays
  with the same content identity can be created by different editors.
- Scope the holder to the workspace session without changing domain binding,
  scene node geometry, document Undo, renderer clock, or shared EditorViewModel.
- Add a JVM key uniqueness/determinism regression. Existing connected tests
  supply the actual multi-presentation navigation checks.

## Boundaries

A unique UI provider key prevents this exception; it does not establish that
ordinary `remember` drafts can move between *all* presentation roles.
In particular, focus/board draft continuity still requires connected
verification or explicit draft ownership. Remaining emulator failures,
such as viewport history assertions, may be unrelated and are not claimed
fixed by this patch. Await exact-head Android CI + Emulator + Fontconfig.
