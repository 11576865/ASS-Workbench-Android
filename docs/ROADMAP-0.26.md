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

The existing direct `\\pos` drag now uses the same ViewModel-owned `previewDocument` pipeline. `VideoPreview` keeps the canonical document separate from the render-only document so overlays, effective-value inspection, timeline behavior, and commit semantics do not accidentally bind to transient state. The first conservative geometry semantic layer is now present: leading top-level `\\pos`, `\\move`, `\\org`, `\\fr/\\frz`, `\\fscx`, and `\\fscy` can be inspected and minimally patched without rewriting unknown tags or nested `\\t(...)` payloads. `\\pos` drag now uses that patcher and refuses to silently convert an existing `\\move`. Editable `\\move` is now the first completed path editor: Start / End handles support direct drag, transient libass preview, and one canonical commit on gesture end; exact endpoint fields are available in Position & Geometry, and existing 6-argument `t1/t2` timing is preserved during spatial edits. `\\org`, the transform-origin marker is now directly editable with exact X/Y fields, transient drag preview, single-commit gesture semantics, add/remove actions, and conservative raw-text removal. Rotation Z (`\\fr` / `\\frz`) now has a unified exact-value + slider + canvas-handle workflow, transient libass preview, one-gesture-one-commit behavior, Style inheritance reset, alias-preserving patching, and conservative override removal. Scale X/Y (`\\fscx` / `\\fscy`) now has paired exact controls, sliders, a shared ratio-lock state, Style inheritance reset, transient preview, conservative semantic patch/remove support, and a static-position canvas Scale gizmo. The gizmo is explicitly a parameter-control frame rather than a claimed libass text bounding box. Shear (`\\fax` / `\\fay`) now has lossless semantic inspect/patch/remove support, paired exact controls and sliders, transient libass preview, reset-to-zero semantics, and a static-position two-axis shear gizmo integrated into the same parameter frame as Scale. Rectangular `\\clip` / `\\iclip` now has conservative semantic inspect/patch/remove support, explicit protection for vector/non-rectangular clips, exact Script Resolution coordinates, clip↔iclip switching, transient libass preview, and four independent corner handles on the preview. Vector clip remains losslessly preserved and is deferred to a later vector-path editor.

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

## Responsive preview policy

0.26 treats portrait as the primary workflow layout and landscape as the precision visual layout; both expose the same capabilities.

- portrait keeps the 16:9 preview full-width on phones, but caps permanent preview width at 568dp on larger windows (~320dp video height);
- landscape keeps Preview and the current owner side-by-side;
- landscape Preview share is contextual rather than fixed: low-priority tools target 50%, normal tools 56%, and visual tools such as Style / Position / Fonts 62%;
- the editor side keeps at least 288dp where the window allows it;
- permanent Preview growth stops around the ~320dp video-height boundary; finer inspection should use a temporary precision/fullscreen zoom rather than permanently consuming editor space;
- capability ownership does not change with orientation.

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

**Core slice complete.** The listed Event operations are now present in their contextual owner surfaces and Android CI #294 passed after the batch/clipboard consolidation.

Implementation has started. The first structural slice now exposes contextual insert-before / insert-after, exact duplicate, merge-with-previous / merge-with-next, and delete actions in the existing Event surface. These operations reuse `AssDocumentEditing` and Undo history rather than introducing a permanent operations toolbar. The Event text editor now retains the real cursor/selection state so split-at-playhead can use the exact text cursor without inventing a second split UI; split is disabled for unsaved text, selections, boundary cursors, or a playhead outside the Event. Batch Style assignment, filtered previous/next navigation, and QC issue previous/next navigation are also wired into their existing contextual surfaces. Multi-select merge and the format clipboard are now consolidated in Batch: merge requires a contiguous Event range, while one paste menu owns Style / Margins / Position / Effects / Overrides / All. Clipboard rewriting is top-level aware, preserves target `{comment}` blocks, excludes source comments, and does not reach into nested `\\t(...)` payloads. Stability pass: unsaved Raw Event Text drafts now survive external canonical edits and surface an explicit conflict instead of being silently overwritten; expanded Event controls retarget the displayed Event before focus-dependent actions; discrete +/- continuous controls now commit through the same gesture contract; structural split/merge no longer trim visible boundary whitespace. A second integrity pass now keeps Undo/Redo focus and selection IDs valid, preserves non-override leading `{comment}` blocks during split/format-copy, clamps batch time shifts as one group so relative spacing cannot collapse at time zero, and makes delayed recovery/MKV writeback snapshot-safe against project switches and edits made while saving.

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

Implementation has started. The timeline viewport is no longer hard-wired to playback: it now has 5/10/30/60/120-second horizontal zoom levels, direct touch panning on a dedicated ruler, an explicit follow-playhead mode, and a return-to-playhead action. The playhead is drawn only when it is actually inside the viewport instead of being falsely clamped to an edge. Existing focused-Event trim/body-drag and snapping remain the first editing layer. The second slice adds chronological ordering and explicit Gap/Overlap annotations. Shared `AssTimelineRelations` frontier analysis drives both timeline indicators and QC, so long Events containing several shorter Events no longer hide later overlaps behind the immediately previous short Event. Existing `-1 frame` / `+1 frame` transport controls already use mpv `frame-back-step` / `frame-step`; Phase D therefore strengthens that owner instead of creating another frame UI. The transport now surfaces mpv estimated frame number and video FPS beside the existing clock. Setting Event Start/End from the playhead after a frame step continues to reuse the existing Timeline controls. Exact VFR frame-boundary snapping remains a separate enhancement and must not be approximated by fixed-millisecond arithmetic. Timeline snapping is also strengthened in-place: the existing Snap menu now owns light/normal/strong thresholds, and whole-Event moves can snap either Start or End while preserving duration. A tested `AssTimelineSnap` engine is shared by trim and move gestures; no additional timing toolbar is introduced.

Add:
- horizontal zoom;
- pan;
- stable playhead;
- focused Event emphasis;
- trim handles;
- whole-Event drag;
- snap strength / targets;
- overlap and gap indications;
- frame-aware timing / frame metadata integration;
- waveform data model and renderer.

Waveform Lite is implemented as a deliberately limited 0.26 timing aid. Android MediaExtractor/MediaCodec decode the first supported audio track in a cancellable background job into cached 20ms min/max peak buckets. Timeline rendering samples only the current viewport, the waveform and playhead are separate layers, and tapping the waveform seeks without changing Event timing. Unsupported codecs/containers fail closed as an optional unavailable layer and never block video, ASS, or MKV editing. No spectrogram, independent waveform viewport, karaoke lane, automatic speech detection, waveform-driven Event trimming, or continuous waveform scrubbing is introduced in 0.26.

### Phase E — Animation

Implementation has started inside the existing Event “效果” owner. The first slice adds conservative top-level `\\fad` / `\\fade` inspection and replacement, including explicit conflict handling when both forms coexist. Blur / Soft Entry are now separated from Fade ownership so changing Blur no longer rewrites Fade or nested `\\t(...)` payloads. No new Animation workbench is introduced. The second slice adds a contextual Transform sub-interface inside that owner: all four ASS `\\t` forms are parsed, multiple transforms are listed independently, nested comma-bearing payloads such as `\\clip(...)` remain intact, and edits replace only the selected top-level transform span. Malformed transforms are surfaced as Raw-only rather than normalized or guessed. Preview scrubbing is now part of the same contextual Animation surface: an Event-relative slider seeks the existing mpv preview without changing Event timing or creating history entries, uses ~33ms seek throttling during drag, and each structured Transform indicates whether the current preview position lies inside its effective range. The third slice introduces a selected-property editor for common numeric transformable tags (scale, rotation, shear, border, shadow, blur, spacing and font size) without turning every tag into a permanent row. It rewrites only the selected tag span, preserves sibling/unknown payloads, previews through `previewDocument`, and surfaces compatibility notes for non-animatable tags, vector clip, clip/iclip mixing, nested transforms, duplicate properties, and `\\fs` hinting risk. Color / alpha / rectangular clip now have dedicated contextual controls as a fourth Animation slice. Color input is presented as `#RRGGBB` while the semantic layer writes ASS BGR `&HBBGGRR&`; alpha exposes ASS 0–255 semantics; rectangular `\\clip` / `\\iclip` can be edited structurally. Vector clip is intentionally locked to Raw tags so drawing paths are never replaced by a rectangle.

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
