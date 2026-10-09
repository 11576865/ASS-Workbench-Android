# Infinite Canvas Workbench V2 — replacement interaction contract

Status: **implementation branch** (not a stable product release)
Baseline: `main@12f7885da1ec88702ce4c20a2503dddb8f06ae25`
Scope: replaces the infinite canvas **presentation**, not ASS-domain semantics, tools, renderer, or persistence.

## Why replace the current host

The current host scales both the surface footprint and its live controls by camera zoom. At overview scales this makes interactive sliders, text and touch targets too small. Tools, video and background gestures contend for the same surface, and the always-present toolbar/left rail competes with work area. Adding more navigation commands would not resolve this architectural overlap.

## New operating model

One workspace session, two deliberately separate interaction surfaces:

1. **Spatial board** — an indefinitely pannable, pinch-zoomable arrangement of *semantic surface cards*. Cards convey tool identity, target/binding summary, visibility and spatial relationship. Their touch targets do not expose shrunken live editor controls. Header drag moves a card in world coordinates; the resize grip changes saved world geometry. Explicit Raise is the only action that changes z-order.
2. **Focused editor** — selecting a card opens its actual production Composable at native screen density in a bounded stage, with persistent "Back to board" and object identity. Editing controls are never scaled by the camera, and background panning cannot intercept editor gestures. The same ToolInstance/Binding/EditorUiContract continues to own business semantics.

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

- Spatial zoom changes *cards*, never the live production editor's touch-target size.
- "Open" from card/strip/birdseye opens the actual tool, and "Return" restores the board without changing document history.
- Drag, resize, hide, raise, opacity and audio pass-through remain functional.
- Existing save/reopen and editor undo invariants hold; changing a session removes stale focus state.
- JVM model tests and Android connected tests cover mode transitions, editor isolation, hidden tool recall, and geometry persistence.
- Real device typography, keyboard, waveform interaction, video renderer and OEM system-gesture acceptance remain separate from CI.
