# 0.29+ Workbench expansion charter

This document records the feature expansion accepted on 2026-10-01 after the 0.28 canvas-workspace milestone.

## Product invariants

The expansion does **not** change the core safety model:

- ASS remains the canonical editable representation.
- libass remains the authoritative preview renderer.
- Structured editors rewrite only owned semantic spans.
- Unsupported or unknown ASS syntax remains visible and round-trip preserving.
- Video transcoding / hard-sub encoding stay outside ASS Workbench.
- MKV remains a subtitle/container bridge, not a general video editor.

## Workspace presentation

Two first-class presentation modes are required over the same editor/domain state.

### Fixed Workspace

The stable/default product UI. Preview, subtitle navigation, timeline and one contextual inspector use deterministic adaptive layout. There is no arbitrary floating-window placement.

### Canvas Workspace (Experimental)

The 0.28 object-centric surface model remains available as the experimental UI. It supports tool instances, object binding and spatial manipulation.

The two modes must share:

- the same `AssDocument`;
- the same Undo/Redo history;
- the same renderer;
- the same QC/linter engine;
- the same project state;
- the same command/batch semantics.

Switching presentation must never mutate subtitle data.

## Durable Project file

Add an optional versioned `.asswbproj` project manifest. It is **not** a subtitle format and must never be required to edit a normal ASS/SRT file.

The manifest may persist:

- canonical subtitle URI and source format;
- reference-video URI;
- MKV source URI and selected ASS TrackNumber/TrackUID when applicable;
- Fixed / Canvas workspace mode;
- open tool instances and bindings;
- committed surface geometry / dock / minimize / tab-stack state;
- review-sidecar identity;
- imported project-font references;
- timeline viewport / markers where available;
- compatibility/QC profile selections.

Deleting the project file must not make the underlying subtitle unusable.

## Canvas window management

Canvas Workspace gains explicit surface presentation states:

- Floating;
- Dock left / right / bottom;
- Minimized;
- Tab stack.

Binding/pinning and layout presentation remain separate concepts. Minimizing, docking or stacking a tool must not destroy drafts, tool instance identity or Event binding.

## Advanced QC / ASS Linter

Promote QC from a fixed check list to a versioned rule engine. Initial rule families:

- timing: zero/short/long duration, overlap, tiny gap, reading speed;
- syntax: malformed blocks/tags, conflicting geometry, duplicate effective tags;
- style: missing/unused Style and shadowed Style values;
- geometry: positions/clips outside PlayRes and renderer-risk values;
- fonts: requested family/glyph/renderer mismatches through the existing font diagnostic layer;
- compatibility: profile-dependent portability warnings.

Each issue carries rule id, severity, target Event when applicable, message and zero or more explicit Quick Fix actions.

Quick Fix is always user-invoked. The linter must not silently rewrite the document.

## Frame-accurate timing

Keep ASS serialization time-based, but add a frame-time mapping layer.

- CFR may derive frame boundaries from an exact rational rate.
- VFR must use timestamp/frame-boundary data; it must never be approximated by a fixed millisecond frame duration.
- Timeline may expose frame number, previous/next frame, and start/end snapping to frame boundaries.
- Video keyframes may be shown as encoding metadata but are not subtitle timing boundaries by default.

## Compatibility profiles

Compatibility checking is analysis, not a claim that ASS Workbench emulates every renderer.

Initial profiles:

- libass native;
- portable/conservative ASS;
- VSFilter-oriented review.

A profile reports syntax/features that deserve cross-render verification. libass remains the preview authority unless another renderer is actually integrated in the future.

## Batch Rule Engine

Replace one-off batch buttons with a composable pipeline:

```
Scope -> Filters -> Actions -> Diff preview -> one Undo transaction
```

Filters include Style, Actor, Layer, time range, text/regex, tag presence, selection and QC rule ids.

Actions include time shift, Style/Layer changes, text replacement, margin changes and conservative owned-tag operations.

Recipes are serializable. Saved recipes are the first automation layer; arbitrary scripting is deferred until the command contract is stable.

## Karaoke workspace

Add a dedicated Karaoke sub-workspace for `\\k`, `\\K/\\kf`, `\\ko` and `\\kt`.

It should provide:

- lossless karaoke-tag inspection;
- syllable timing lane;
- drag/exact retiming;
- split/merge operations only when they can be represented safely;
- total-duration consistency checks;
- waveform overlay when available;
- Raw ASS escape hatch.

ASR/WhisperX is not a core dependency. Alignment sidecars may be imported later.

## Vector Clip

Implement visual editing of vector `\\clip` / `\\iclip` before opening full ASS Drawing authoring.

Requirements:

- lossless path token model;
- direct point/Bezier manipulation where command semantics are known;
- exact coordinate editing;
- scale preservation;
- Raw fallback for malformed/unsupported paths.

Full `\\p` Drawing authoring remains a later layer built on the same path model.

## Font dependency inventory, not font subsetting

ASS Workbench should automatically derive the font families requested by:

- referenced Styles;
- inline `\\fn`;
- `\\rStyle` resets;
- relevant style inheritance.

It should reconcile those requests against imported/system/MKV fonts and expose missing/unused resources.

**Font subsetting is intentionally not added.** Packaging/size optimization belongs to the mux/packaging workflow, and a separate soft-mux workbench already owns that problem.

## Additional subtitle formats

ASS remains canonical internally, but lightweight subtitle formats may be imported as conversion sources.

### SRT

SRT import is promoted into scope:

- parse SRT timing/text into an ASS document;
- preserve line breaks;
- map only safe common inline emphasis where practical;
- opening SRT must not make Save overwrite the source with ASS bytes accidentally;
- Save/Save As continues to produce ASS unless an explicit lossy SRT export action is chosen.

Other formats (for example WebVTT) should follow the same adapter boundary rather than contaminate the ASS domain model.
