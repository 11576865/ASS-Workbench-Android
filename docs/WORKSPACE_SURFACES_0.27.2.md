# Workspace surfaces — 0.27.2

This increment applies the 2026-10-01 Workspace UI constitution to the existing tool-instance kernel. The product is one object-centric professional workspace, with context and learning information presented at the point of operation.

## Implemented

- `WorkspaceSurfaceState` owns dp geometry, presentation size class, layout lock and bounded stacking order. It has no ASS values, event bindings or domain undo state.
- A separate `WorkbenchSurfaceController` owns committed layout and transient gesture candidates. `WorkspaceSurfaceHost` presents tool content; the former experimental file now contains only the remaining preview/interaction migration mechanisms.
- Floating tools live inside the work area below the app bar/tool strip. They cannot intercept the controls needed to open tools, clear the screen or switch preview presentation.
- Position uses graphics-layer translation during a drag. Resize redraws an outline while the content remains at its committed dimensions; release remeasures once, cancellation discards the candidate.
- Object pin and layout lock are distinct controls. Layout lock disables drag, free resize and size presets, while the tool remains editable and its binding remains independent.
- Saveable, versioned layout restores each instance's geometry, lock and stacking order after Activity recreation. Only committed geometry is serialized. Invalid rows are skipped individually.
- Viewport projection clamps the window inside the current work area without rewriting the saved geometry. Rotation / inset changes therefore do not erase a larger-window layout. A move preserves canonical width/height even when the viewport temporarily displays a smaller surface.
- The header uses a separate horizontally scrollable action row so compact tools retain reachable controls.

## Deliberate boundaries

This is the Surface phase, not the complete constitution. Saveable restoration is Android Activity/process-state restoration, not a durable project workspace file. Application defaults, user templates and project recovery remain separate future persistence layers. Docking, minimization, parameter projections, tool composition, per-tool semantic density, lenses and the rotating spatial manipulator are not implemented by this increment. Size classes establish the presentation contract; content-specific semantic density follows in the parameter/tool phase.

Clear-screen suppression stays in `WorkspaceState`; it never destroys instances, bindings or their layout. Surface operations do not touch `AssDocument`, canonical ASS serialization, renderer output, subtitle undo or the existing transient domain interaction protocol.

## Verification

Model tests cover viewport projection, orientation restoration, tiny viewports, invalid persistence rows, independent instance restoration, locking and size classes. Controller tests cover transient/commit/cancel, resize, lock and retained geometry. Connected regression tests exercise real tool opening/stacking/clear/restore/preview controls and resize/lock/recreation while asserting unchanged canonical subtitles.
