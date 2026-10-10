# Canvas navigation: fitted viewport centering and finite-Float anchor zoom

Date: 2026-10-10
Base: ASS-Workbench-Android PR #138, `e0ce553da2754dbed18e41bcea6de58d23f10880`
Status: patch submitted; Android CI / emulator / device acceptance pending

## Code-grounded issue

`fitCanvasCamera` previously aligned the projected bounding box to (16dp, 72dp)
regardless of spare viewport area. A small node therefore appeared near the
top-left rather than centered when the user invoked Overview or Approach.

`InfiniteCanvasCamera.zoomAt` performed `anchorX - x` using Float first,
which can overflow even when both operands and the final projected camera
position are finite. This causes a valid zoom to be silently ignored.

## Correction and contract

- Center the fitted bounds inside the usable viewport while preserving the
  prior chrome reservation (16dp each horizontal side, 72dp top / 88dp bottom).
- Evaluate zoom's anchored affine transform in Double and reject only a
  genuinely non-finite or unrepresentable resulting Float camera offset.
- Preserve source node layout, binding, ASS domain state, project history and
  explicit camera-scale range; regression tests cover centering and extreme
  finite offsets.

## Validation boundaries

Pure model assertions are part of `InfiniteCanvasModelTest`. This patch does
not claim to repair the separate `inspectorDraftSurvivesToolSwitchAndRotation`
instrumentation failure seen in PR #138 emulator run 37984011960; the
post-application failure requires its own diagnostic evidence and repair.
A product-side stacked PR protects the ongoing PR #138 branch.
