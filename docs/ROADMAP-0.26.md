# ASS Workbench Android 0.26 — Professional Capability Expansion

0.26 is the first capability-expansion release after the 0.25 workbench rearchitecture.

The goal is not to add more permanent UI. The goal is to make existing contextual surfaces substantially more capable, visual, and precise while keeping Raw ASS lossless and observable.


## Implementation status — Phase A first end-to-end slice

The first 0.26 runtime slice now establishes the generic render-only transient path:

- `EditorState.previewDocument` is consumed by the authoritative preview before the canonical document.
- ViewModel preview mutations never enter Undo history, dirty state, recovery, or font diagnostics.
- Canonical document publication clears stale transient state.
- Style FontSize / Spacing / Outline / Shadow use `ContinuousParameterControl`.
- Event-level Blur uses the same transient preview path.
- Slider drag is preview-only; gesture end performs one canonical commit.
- Exact-value and non-continuous Style draft edits retain the short auto-commit path.
- App version is now 0.26.0 / versionCode 28 on this branch.

The existing direct `\\pos` drag now uses the same ViewModel-owned `previewDocument` pipeline. `VideoPreview` keeps the canonical document separate from the render-only document so overlays, effective-value inspection, timeline behavior, and commit semantics do not accidentally bind to transient state. The first conservative geometry semantic layer is now present: leading top-level `\\pos`, `\\move`, `\\org`, `\\fr/\\frz`, `\\fscx`, and `\\fscy` can be inspected and minimally patched without rewriting unknown tags or nested `\\t(...)` payloads. `\\pos` drag now uses that patcher and refuses to silently convert an existing `\\move`. Full editable move/origin/rotation/scale surfaces are the next layer.

## Baseline

0.25 remains the architectural baseline:

- portrait-first;
- simple landscape adaptation;
- fixed 16:9 preview stage;
- Event list is also the primary Event editor;
- contextual overlay / sheet for heavyweight tools;
- Raw ASS is first-class;
- standalone ASS and MKV are distinct workflows;
- libass preview is authoritative;
- unknown syntax must survive round trips.

0.26 must not reintroduce:
- permanent function tabs;
- one button per ASS tag;
- duplicate editors for the same semantic domain;
- HSR-specific 60/40 or safe-area behavior;
- desktop-style always-expanded inspector walls.

## Product target

0.26 should feel like a professional ASS workbench rather than a modern shell around a small set of controls.

Professional capability means:

1. major ASS semantic domains are directly editable;
2. continuous visual parameters have immediate feedback;
3. precision remains available beside direct manipulation;
4. one semantic concept has one UI owner;
5. Raw ASS remains the exact escape hatch and source of truth;
6. editing remains reversible and predictable;
7. no UI feature depends on destructive normalization of ASS text.

## Interaction language

Borrow the successful interaction language of the HSR subtitle workbench, not its project-specific business rules.

Prefer:

- preview → adjust → immediate libass feedback;
- slider + exact numeric value + small-step adjustment for continuous values;
- visual guides for geometry;
- direct manipulation on the preview where geometry makes sense;
- compact feature-level entrances;
- transient contextual surfaces;
- inline effective-value/source information.

Do not map every ASS command to a separate permanent control.

## Semantic ownership

Each domain has one primary owner.

### Typography & appearance

Owns:
- font family override;
- size;
- bold / italic / underline / strike;
- character spacing;
- primary / secondary / outline / back colors;
- alpha;
- border;
- shadow;
- blur / edge blur.

Resource-level font installation, source, glyph coverage, replacement, and collection remain in Font Manager.

### Position & geometry

Owns:
- alignment;
- margins;
- pos;
- move;
- org;
- rotation;
- scale;
- shear;
- clip / iclip;
- preview guides and handles.

This tool may expose both Style base geometry and Event overrides, but must label the layer clearly.

### Animation & timing effects

Owns:
- fad;
- fade;
- transform (t);
- transform ranges and acceleration;
- animation presets that compile to ordinary ASS syntax.

Karaoke timing is a related but distinct sub-workspace.

### Event

Owns:
- Start / End;
- Style assignment;
- Layer;
- Actor / Name;
- Comment;
- text / Raw ASS;
- insert / duplicate / split / merge / delete;
- batch selection and navigation.

### Timeline

Owns:
- playback and playhead;
- Event timing;
- trim / move;
- snapping;
- frame stepping;
- zoom / pan;
- waveform when implemented;
- timing navigation.

### Font Manager

Owns:
- imported fonts;
- MKV attachment fonts;
- font origin;
- font family metadata;
- glyph coverage;
- missing-font diagnostics;
- replacement / substitution;
- collection for export / MKV.

### QC

Owns rules and diagnostics, not editing surfaces.

A QC finding may deep-link into the relevant owner.

## Capability phases

### Phase A — Visual control foundation

Introduce reusable controller components:

- continuous parameter row;
- slider;
- exact numeric editor;
- reset/inherit action;
- optional decrement/increment actions;
- source/effective badge;
- live preview callback;
- commit callback.

Initial targets:
- font size;
- spacing;
- border;
- shadow;
- blur;
- opacity;
- rotation;
- scale;
- fade duration.

Requirements:
- no document-history commit on every pointer move;
- preview can be transient;
- one commit on gesture end;
- undo should treat one gesture as one edit.

### Phase B — Geometry workbench

Extend current direct pos manipulation to:

- move start/end handles and path;
- org handle;
- rotate handle;
- scale bounding box;
- shear controls;
- clip rectangle;
- later: vector clip / drawing path editing.

Preview must remain libass-authoritative where practical.

### Phase C — Event operations

Add:
- insert before / after;
- duplicate;
- split at playhead;
- merge with previous / next;
- delete with undo;
- copy formatting;
- paste formatting;
- batch Style assignment;
- batch time shift;
- filtered navigation;
- next/previous QC issue.

Keep these contextual; do not create a permanent toolbar for all operations.

### Phase D — Timeline professionalisation

Add:
- horizontal zoom;
- pan;
- stable playhead;
- focused Event emphasis;
- trim handles;
- whole-Event drag;
- snap strength / targets;
- overlap and gap indications;
- frame-aware stepping;
- waveform data model and renderer.

Waveform UI appears only after the waveform implementation exists.

### Phase E — Animation

Provide a structured editor for:

- fad / fade;
- t(start,end,accel,tags);
- multiple transforms;
- transformable visual properties;
- preview scrubbing.

The structured editor must not delete unknown tags inside or around transforms.

### Phase F — Font reliability and packaging

Finish:
- no live native cache mutation;
- atomic font publication;
- glyph diagnostics off main thread;
- font replacement workflow;
- collect fonts used by document;
- detect unused embedded fonts;
- export/attach selected fonts to MKV.

### Phase G — Karaoke and drawing

Professional ASS cannot permanently omit these, but they may land late in 0.26 or move to 0.27 if stability work dominates.

Karaoke:
- k / K / kf / ko / kt;
- syllable segmentation;
- timing editing;
- visual timing lane.

Drawing:
- p / pbo;
- vector command parsing;
- shape preview;
- clip / drawing reuse where possible.

## Architecture work required

0.26 should stop each UI component from independently parsing ASS tags.

Introduce a shared lossless semantic layer:

Raw Event Text
  -> lossless lexical tokens
  -> semantic override model
  -> effective-value evaluator
  -> controller/read model
  -> structured editor / QC / direct manipulation

Rules:

- preserve raw token order;
- preserve unknown tags;
- preserve malformed-but-round-trippable text;
- never use a normalising compiler as the canonical write path;
- structured edits should rewrite only the smallest owned span.

## Performance requirements

- visual gesture preview should target interactive latency;
- one gesture should not create dozens of history entries;
- QC and glyph diagnostics remain off the main thread;
- waveform decoding and caching must be asynchronous;
- long Event lists should avoid full-list recomposition;
- no renderer recreation for ordinary font/style/position edits.

## Acceptance criteria

0.26 is not complete merely because controls exist.

A feature is complete only when:

- the feature has one obvious entry;
- the owner surface contains all closely related controls;
- preview feedback is visible where appropriate;
- exact values are available;
- undo/redo is correct;
- Raw ASS round-trip is preserved;
- malformed/unknown syntax is not silently lost;
- the feature works in portrait;
- landscape remains usable without becoming a separate design target;
- the feature survives process interruption/recovery where applicable.

## Explicit non-goals

0.26 is not a general subtitle platform.

Do not expand into:
- OCR;
- speech-to-text;
- machine translation;
- hundreds of subtitle formats;
- video transcoding suites;
- HSR-specific bilingual layout presets as core behavior.

These may be separate integrations later. 0.26 remains ASS-first.
