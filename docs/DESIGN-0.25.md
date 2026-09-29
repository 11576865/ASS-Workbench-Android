# ASS Workbench Android 0.25 — Interaction and Layout Contract

0.25 is a workbench rearchitecture, not a visual reskin. New UI work should preserve the following contract.

## Product identity

ASS Workbench is a professional ASS-only subtitle workbench for Android. It should retain the information density, precision, raw-format visibility, and deterministic behavior expected from professional tools while using modern mobile interaction patterns to reduce permanent UI occupancy.

## Core interaction principle

Use the least permanent screen space possible while keeping capabilities discoverable.

State changes should be:
- continuous;
- reversible;
- predictable;
- contextual;
- local to the object being edited when possible.

Advanced ASS information is not hidden merely because it is advanced. Users should be able to operate the concepts they understand without being forced through a separate beginner/expert mode.

## Permanent surfaces

The normal workbench has five stable surface roles:

1. App bar
2. Fixed 16:9 preview stage
3. Transport + timeline strip
4. Event workspace
5. Supporting workspace

Transient UI uses anchored popups, sheets, menus, or overlays. Do not add a permanent toolbar, tab, or panel merely because a new feature exists.

## Geometry

Primary layout rhythm:
- 4dp: micro spacing / internal alignment
- 8dp: default component spacing
- 12dp: compact grouped spacing
- 16dp: section spacing
- 24dp: major separation

Touch targets should normally be at least 48dp even when the visible affordance is smaller.

Window guidance:
- < 600dp: compact; supporting tools use transient/sheet presentation
- 600–839dp: medium; prefer the tablet portrait workbench when content remains usable
- >= 840dp: expanded; persistent dual-pane presentation is preferred

Do not substitute device labels such as "phone" or "tablet" for actual window constraints.

## Preview

The preview stage is always 16:9 and must not change height because an Event expands, a tool opens, or the supporting pane changes state.

Video content is contained inside the stage without stretching. Non-16:9 media may letterbox.

## Event workspace

The subtitle list is also the primary Event editor.

Default row:
- select + seek on tap;
- long press enters multi-select;
- no permanent checkbox;
- no row-end play button.

Focused row:
- transforms in place;
- timing and body editing occur in the row;
- Style, position, font, effects, and Event metadata remain visibly reachable;
- Raw ASS remains visibly represented and replaces its own surface when editing.

Do not duplicate the same Event editor in the supporting pane.

## Raw ASS

Raw ASS is first-class.

Structured editing and Raw ASS editing are projections of the same canonical document. Unknown syntax must be preserved whenever possible.

If a simplified editor cannot safely round-trip an Event, it must become non-destructive/read-only and direct the user to Raw ASS rather than normalizing or discarding unknown syntax.

## Supporting workspace

Only one heavyweight supporting tool owns the pane at a time.

Examples:
- Timeline
- Style
- Position
- Effects
- Font Manager
- QC
- Batch
- Project
- Diagnostics

Opening a supporting tool should be reversible. Preserve a lightweight previous-tool history rather than sending every tool back to Timeline.

Information that merely needs reference while another tool is open should prefer:
- inline metadata;
- overlay;
- anchored popup;
- badge;
- transient surface.

Do not solve every simultaneous-information problem by adding another permanent pane.

## Timeline

Timeline has two states only:

### Strip
Always present inside the transport surface. It reuses the old seek-bar footprint to show:
- playback position;
- Event distribution;
- focused Event;
- direct seek/scrub;
- Timeline entry affordance.

Frame backward / frame forward are first-class transport actions.

### Pane
Provides:
- Start trim;
- End trim;
- whole-Event move after focus;
- playhead;
- configurable snapping;
- Event boundary and playhead snapping;
- time grid;
- timing-to-playhead actions;
- overlap/gap visualization where useful.

No separate "Focus Timeline" mode is required.

Waveform is not allocated permanent UI until implemented.

## Selection

Long press enters multi-select.

Entering selection mode must not cause major layout movement. Reuse stable row/app-bar regions rather than inserting new permanent controls.

Batch tools appear only after the user explicitly requests them.

## QC

QC status is contextual, not a permanent page requirement.

A row badge may expose an anchored transient surface describing the actual issue. Tapping elsewhere dismisses it. Severity should be distinguishable without forcing the user to navigate away from the Event.

## Standalone and Matroska workflows

Standalone ASS and Matroska are distinct project workflows sharing the same editing surfaces.

Standalone:
- ASS document;
- optional reference video;
- optional imported fonts.

Matroska:
- video/container;
- selected ASS track;
- embedded font attachments;
- write-back to a new MKV.

The UI must not pretend these project lifecycles are identical.

## Fonts

Font import must not rebuild the whole Compose/mpv tree as a side effect.

Font operations should:
- validate off the UI thread;
- record origin;
- update renderer state transactionally;
- preserve the document if renderer reload fails.

Font origin may be shown contextually:
- manual import;
- MKV attachment;
- fallback/unknown.

## Removed 0.25 legacy assumptions

Do not reintroduce:
- five permanent top-level editing tabs;
- HSR-specific 60/40 layout behavior;
- HSR/black-screen terminology;
- the old generic "safe area" derived from HSR geometry;
- an "advanced mode" whose only purpose is hiding native ASS fields;
- duplicate Event editors outside the Event workspace;
- permanent checkboxes in every subtitle row;
- destructive renderer recreation via key(fontRevision).

## Design references

Use Android adaptive/canonical-layout guidance as the platform baseline, interaction-pattern literature as a decision framework, and mature professional editors as case studies.

The goal is not to imitate Material visuals. The goal is to combine modern contextual interaction with professional subtitle editing density and precision.
