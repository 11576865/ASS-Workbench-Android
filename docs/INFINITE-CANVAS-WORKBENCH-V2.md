# Infinite Canvas Workbench V2 — replacement interaction contract

Status: **implementation branch** (not a stable product release)
Baseline: `main@12f7885da1ec88702ce4c20a2503dddb8f06ae25`
Scope: replaces the infinite canvas **presentation**, not ASS-domain semantics, tools, renderer, or persistence.

## Why replace the current host

The current host scales both the surface footprint and its live controls by camera zoom. At overview scales this makes interactive sliders, text and touch targets too small. Tools, video and background gestures contend for the same surface, and the always-present toolbar/left rail competes with work area. Adding more navigation commands would not resolve this architectural overlap.

## New operating model

One workspace session, two deliberately separate interaction surfaces:

1. **Spatial board** — a pannable, pinch-zoomable world. At overview scale (`scale < 0.95`) each node is a lightweight semantic card showing identity and binding summary. At close-up scale (`scale >= 0.95`), multiple actual production tools can coexist in their saved world positions with **unscaled native text and touch controls**. The camera changes spatial placement and surface footprint, never font or touch-target density. Header drag moves world geometry; corner drag resizes it. Explicit Raise is the only action that changes z-order.
2. **Focused editor** — selecting a card opens its actual production Composable at native screen density in a bounded stage, with persistent "Back to board" and object identity. Camera gestures cannot intercept editor gestures. When editing video/audio, the real preview and transparent audio can be layered rather than substituting a screenshot. The same ToolInstance/Binding/EditorUiContract continues to own business semantics.

Tool access is available from the bottom project strip and from the existing birdseye map. Hidden entries can be recalled. The board maintains its spatial arrangement when editing is opened or closed. No document Undo entries are created for camera, layout or focus switching.

## Mandatory safety boundaries

- No new ASS parser/renderer. Actual tool contents must come from the existing `content(id, ...)` renderer.
- Do not modify canonical ASS state, Focus/Selection/Binding or Write Target implicitly.
- No loss of tool identity, hidden state, z-order, background opacity, audio pass-through or saved node geometry.
- No cross-project leakage of temporary focused editor or modal state.
- Native rod gesture ownership disables spatial mutations and tool switching.
- Layout changes retain existing `.asswb` scene wire format; optional extended fields require compatibility tests.
- Legacy visual-capture and interaction tests must be reviewed, not reported as passing merely because code compiles.

## Acceptance

- Spatial zoom switches low-detail cards to close-up native-density live surfaces; zoom does not rescale the live editor's fonts or touch targets.
- "Open" from card/strip/birdseye opens the actual tool, and "Return" restores the board without changing document history. At close-up zoom, two real tools can coexist without opening a second product UI.
- Drag, resize, hide, raise, opacity and audio pass-through remain functional.
- Existing save/reopen and editor undo invariants hold; changing a session removes stale focus state.
- JVM model tests and Android connected tests cover mode transitions, editor isolation, hidden tool recall, and geometry persistence.
- Real device typography, keyboard, waveform interaction, video renderer and OEM system-gesture acceptance remain separate from CI.


## Implementation status — 2026-10-09

- Branch: \`redesign/infinite-canvas-workbench-v2\`.
- Replaces \`InfiniteCanvasHost\` composition; model adds Double-based fit and a lower, persistable overview camera scale. Existing nine-field \`infinite-v1\` persistence rows remain readable.
- Adds a compact board toolbar, spatial grid, tool access strip, optional left drawer, full-density focused editor, Back navigation, semantic LOD and native close-up coexistence.
- Preserves audio/video layered editing and explicit audio pass-through policy, and protects camera/tool focus mutations during an owned gesture.
- Initial PR #138 head `01f58b4` ran Android CI **success**, Fontconfig probe **success**, Emulator **failure** (107 tests, 7 failures, zero errors/skips; run `37903981669`).
- Four failures were tied to a removed `＋ 工具` entry and old assumptions about a permanently composed scaled-window tool directory. The repair adds a stable `spatial-add-tool` action, native-density directory stage, and selected ToolInstance handoff for both new and existing tools.
- One failure concerned left-rail residency after returning from a focused editor. The return and Back paths now reopen a resident drawer.
- Two failures concerned birdseye recall and camera/hidden-node evidence. The focus path now publishes restored scene state before its active-tool callback, and instrumented tests separately assert that the native editor becomes visible.
- Follow-up commits and regressions are submitted. **Post-repair CI is Pending**; these fixes are not yet presented as confirmed successful.
- **Not established**: full-device usability, renderer/frame-time benchmarks, 240-UI completion, arbitrary parameter extraction, universal draft ownership, or guaranteed viewport virtualization for very large numbers of concurrently live tools.

## Follow-up execution boundary — 2026-10-09

- Product: pinned `spatial-add-tool` in the bottom command strip, separating it from horizontally scrollable instance tabs. This fixes the loss of a reliably visible tool-directory action on narrow layouts.
- Product: explicit birdseye/recall focus clears an abandoned tool-picker transaction. A later unrelated `activeInstanceId` update must not redirect the editor.
- Android instrumentation: `pickerSwitchesToAnAlreadyExistingToolInstance` now checks that the directory affordance is actually displayed; `explicitBirdseyeNavigationCancelsAbandonedToolPickerSelection` covers stale selection invalidation.
- Verification: new head CI has started, but final outcomes are **Pending CI**, **Pending Emulator**, and **Pending Fontconfig**. Source-code and test submission are not equivalent to emulator PASS.
- Merge policy: keep PR #138 in Draft until Android Emulator is green, then review visual evidence and user-device acceptance separately.
