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

## Single-experiment UI consolidation — 2026-10-10 (PR #138)

- **Product registry:** `FIXED` remains the standard main UI, `SPATIAL_EXPERIMENTAL` is the sole selectable experiment and still the configured new-session default. Eight older variants have `ARCHIVED` status and are omitted from the chooser. Their legacy persisted `workspace_mode` strings migrate to the infinite canvas without resetting document or ToolInstance data. Their old private code paths remain unreachable until safe removal of source/test dependencies.
- **Main UI:** a direct action toggles between standard workspace and canvas; the former UI Lab chooser is now a two-layout workspace selector.
- **Timeline Dock:** a real `ModernTimelinePane` can remain open beneath the focused native editor or board; compact and expanded presentations preserve the same playback model and free the viewport above the dock.
- **Side Bookmark:** a ToolInstance with `WorkspaceToolPresence.BOOKMARKED` appears on a right-side spatial rail and is no longer duplicated as a world-window. Focusing its bookmark uses the same tool ID and saved world geometry. A contextual command can reverse bookmark status.
- **Object-first authoring:** native preview long press supplies `PreviewObjectPick` at its frozen playback position. A single reliable candidate changes document Focus explicitly; ambiguous hits use the existing `ObjectCandidatePicker` instead of guessing.
- **Precision and glass:** Position can enable the real `PrecisionInteractionOverlay`, excluding the ordinary rod overlay while precision is active; world-node opacity now changes actual composited alpha using a persisted 100/75/50/25% cycle. Audio-specific input pass-through remains separate.
- **Regression migration:** old presentation smoke tests target the two supported modes; legacy presentation-only UI tests have been rewritten to exercise equivalent actions inside the unified canvas. New connected coverage includes live timeline and reversible side bookmarks.
- **Scope and limits:** This is not removal of every old private Kotlin function, nor parity with all 240 UI experiments. Named canvas islands, fully four-sided docking, global gesture arbitration, complete visual evidence, real-device render/IME performance and generalized multi-writer draft safety remain separately unverified. All newest CI/Emulator results are **Pending** at submission; keep PR Draft.

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

## Bounded offscreen composition — 2026-10-09

- A camera/world-space intersection predicate now uses Double intermediates and a 128 dp prefetch margin; this is distinct from drawing-layer clipping.
- At close-up semantic zoom, heavy media surfaces (`preview`, `audio`) may be removed from active composition while fully offscreen; their persisted world nodes remain accessible through the spatial map and quick strip.
- **Safety restriction:** do **not** virtualize subtitle authoring, parameters, or arbitrary ToolInstances yet. The current `SaveableStateHolder` contract does not establish recovery for every non-saveable pending edit draft. General editor Composables remain mounted despite offscreen placement until their edit-session ownership/restoration has dedicated tests.
- Added two JVM predicate tests and an Android instrumentation regression for media suspension, far-node recall, and retained general editors.
- No performance measurements or emulator PASS are claimed from source changes alone. **Pending CI**.

## Usability and v1 capability recovery — 2026-10-10

The v2 host originally improved low-zoom legibility while silently losing portions of v1's production workbench contract. This slice restores concrete, direct-manipulation functionality:

- **Native tool creation from either mode:** `spatial-add-tool` is available on the spatial board and focused editor. Existing and newly opened ToolInstances can be selected without returning to an unreadable, scaled directory.
- **Fast focus switching:** tap the active tool title in the focused editor to select another registered tool immediately. Birdseye remains available for actual spatial navigation.
- **Tool ownership actions:** each capability-declared tool can expose Close, Duplicate preserving its binding, Duplicate with FollowFocus, and Pin/Unpin its *read* Event. All commands delegate to `WorkspaceState`, including capability checks. Pinning a read source is explicitly **not** a new write-target policy.
- **Per-node layout lock:** saved `infinite-v2` layout rows add `layoutLocked`, retaining read compatibility with `infinite-v1` rows. Lock prevents header movement and corner resize, not recall or camera navigation. The header displays a lock rather than a misleading drag affordance.
- **Spatial organization:** explicit one-column/two-column arrange moves only shown, unlocked nodes; hidden nodes and intentionally locked anchors keep geometry, z-order, alpha and identity. Unfittable arrangements direct the user to birdseye.
- **Close versus hide:** Close removes the domain ToolInstance (or parameter projection) and a known removed scene identity; Hide retains both node and domain tool for recall. Initial project scene restore is not pruned just because WorkspaceState has not hydrated yet.
- **Draft-safe semantic zoom:** general editor Composables remain mounted at native component size while the low-zoom card masks their visible control surface. Media nodes can be culled offscreen as before. This addresses a lifecycle hole where toggling LOD destroyed plain `remember` (non-saveable) drafts.
- **Responsive focused chrome:** the essential Back/Tool Picker/Birdseye/Context entry points remain visible on narrow screens; document Undo/Redo is in the context menu at narrow widths and in the header on wider screens. Document history remains separate from canvas actions.

Tests have been added for persistence v1/v2, layout lock, safe arrangement, focused toolbar reachability, tool lifecycle callbacks, focused switcher, and unsaveable editor draft continuity through semantic zoom.

**Validation boundary:** the initial v2 emulator run had 5 remaining failures out of 111 tests (four missing `spatial-add-tool` on initial focused entry and one resident left-rail visibility). New code and tests are **Pending CI/Emulator**; this document does not claim they passed. Real-device touch, IME, drawing, very-large-workspace performance and full multi-tool simultaneous write-conflict semantics remain separate acceptance tasks.

## Usable focused-stage composition — 2026-10-10 (follow-up)

The former focused editor was a full-screen replacement for the canvas. That prevented a subtitle/parameter author from observing the real video/ASS result without switching views. The updated focused stage instead composes the **real video preview and real production editor simultaneously** whenever a separate visible preview node exists:

- On narrow Android viewports: a vertically stacked reference preview above the editor. On >=840 world-dp width: a horizontal video+editor split.
- The user can hide the reference, restore it, or cycle its approximate proportion between 25%, 40% and 54%; all are presentation-only state. The preview uses the existing renderer, document state and media clock, not a screenshot, replacement scene graph or fabricated preview.
- Soft-keyboard visibility temporarily collapses the reference to recover authoring height, without overwriting the saved user choice.
- A resident tool rail is given dedicated width in the focused stage rather than covering the real editor's hit targets. Narrow rails use 88 dp, otherwise 112 dp.
- The primary editor is hosted in a keyed `movableContentOf` scope and the active node/renderer callbacks are read via `rememberUpdatedState`. This is intended to preserve plain `remember` draft state while the preview is resized/hidden/restored and pane layout changes. The secondary media renderer can be torn down when hidden.
- New connected regression exercises the real two-pane composition, reference controls and a non-saveable draft across visibility/size changes.

This does **not** prove native moving-video latency, IME ergonomics on all OEM devices, arbitrary dual-authoring tools, or draft continuity across workspace-session replacement. Automated tests for the latest PR head are **Pending CI/Emulator**, and visual/device acceptance is still outstanding.
