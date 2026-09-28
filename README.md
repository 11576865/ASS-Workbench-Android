# ASS Workbench Android

A focused Android ASS subtitle workbench for phones and tablets.

**Current version: 0.19.0**

ASS Workbench treats ASS as the primary editable document. A local video may be attached as reference media, or an MKV can be opened as a subtitle project.

## Current status

- independent reference-video and ASS selection;
- adaptive editor workspace: preview + subtitle dock + context inspector, with expanded/tablet/compact arrangements;
- compact vertical layout on narrow screens;
- searchable event list, Layer, overlap, multi-select and touch range selection;
- bounded Undo/Redo and debounced crash-recovery journal;
- Save / Save As standalone `.ass`;
- mpv + libass authoritative preview;
- edited ASS reload without restarting reference video;
- imported TTF/OTF Font Registry with OpenType family-name parsing;
- explicit Style font assignment;
- family match diagnostics plus actual OpenType `cmap` glyph-coverage checks;
- mpv/libass renderer-log capture for font-selection evidence;
- focused Style typesetting workspace;
- safe-area guide and 60/40 bilingual layout preset generalized from the HSR workbench;
- event-level position, blur, fade and restrained soft-entry overrides;
- bilingual Review workspace with persisted Review Sidecar V2 using stable event fingerprints and V1 compatibility;
- tri-state select-all and batch time/Layer/Style operations;
- MKV Container Bridge:
  - enumerate embedded ASS tracks;
  - register attached TTF/OTF fonts;
  - edit a selected ASS track;
  - replace the selected ASS track in the same track slot while preserving TrackNumber, TrackUID, ordering and track metadata;
  - save a new MKV with video/audio stream-copied instead of transcoded;
- preservation of unknown ASS sections plus opaque/comment lines inside known sections;
- preservation of custom Style/Event Format columns and an initial round-trip regression corpus.

## Product boundary

The primary editable/output artifact remains ASS.

Video encoding and hard-sub rendering are outside scope. MKV support is a **container bridge**, not a video editor: it exposes subtitle tracks/font attachments and can remux an edited ASS track back into a new MKV without re-encoding video/audio.

ASR, OCR and general translation are not core features.

## Build

Current CI uses:

- JDK 21
- Kotlin 2.4.10
- Android Gradle Plugin 9.4.0
- Gradle 9.7.1
- compileSdk 36
- targetSdk 35
- minSdk 26
- Go toolchain for the pinned arm64 MKV bridge

Successful pushes to `main` publish a debug APK as a GitHub prerelease.

The authoritative preview uses `libmpvKt` 0.3.0, which bundles mpv/FFmpeg/libass. Because the distributed native combination is GPL, this repository is licensed under **GPL-3.0-or-later**.

See `docs/PRODUCT_SPEC_1_0.md`, `docs/ROADMAP.md`, and `THIRD_PARTY_NOTICES.md`.

## Workspaces

### Edit

Event text/timing, search, Layer, Style assignment, selection and batch operations. ASS Event Text remains first-class: override blocks stay directly editable and receive syntax-aware rendering instead of being hidden behind a simplified text view.

### Typeset

Font family/size, emphasis, colors, outline/shadow, spacing, nine-grid alignment, margins, safe-area guide and bilingual 60/40 geometry.

### Review

Paired bilingual grouping, source/target Style selection, editing, confirmation state and persistent review metadata.

### Event effects

Managed event-level overrides for position, blur, fade and restrained soft entry. Existing unrelated leading ASS override tags are preserved.

### Container Bridge

An MKV can be opened as a subtitle project. The reader scans Matroska tracks and supported font attachments. On supported arm64 builds, a small ASS Workbench bridge built against the pinned mkvgo revision replaces the selected ASS payload in the original track slot while carrying video/audio streams without transcoding. The source TrackNumber, TrackUID, ordering, language/name and disposition metadata are inherited instead of deleting and appending a new subtitle track.

The original MKV is never modified in place. The write-back path copies the source into app-private storage and writes one new result container; it no longer creates a second full-size intermediate container.

## Current hardening work

0.19.0 batches a larger tablet-usability and diagnostics pass instead of another single-issue hotfix:

- subtitle rows and Event editors keep ASS override tags visible, but syntax-highlight blocks/tags/values/escapes so control syntax is visually distinct from dialogue text;
- the focused subtitle inspector exposes event-level Margin L/R/V directly, including a one-tap reset to Style inheritance;
- selected subtitles can bulk-clear style/position overrides instead of repeating the action one event at a time;
- the Style inspector reports which effective properties are coming from inline ASS overrides versus the shared Style;
- the Style panel uses the full inspector height instead of a fixed 430dp internal cap;
- the Project inspector surfaces PlayRes, ScaledBorderAndShadow, optional LayoutRes/YCbCr fields, and the count of events carrying style/position overrides;
- playback scrubbing no longer issues an exact seek for every slider movement; the seek is committed when the drag finishes;
- guide mode now draws the mpv-reported video rectangle separately from the ASS safe area, making geometry mismatch visible immediately.


0.18.0 focuses on ASS style fidelity and editor semantics:

- makes Style scope explicit: a Style is shared by every event that references it;
- adds a one-action path to clone the current Style for the selected subtitles when selection-local styling is desired;
- expands "inherit Style" so it clears managed inline typography/position overrides and event-level margins on the focused event;
- replaces raw ASS color-string text boxes with a popup RGBA picker that writes canonical `&HAABBGGRR`;
- uses mpv `osd-dimensions` margins as the authoritative displayed-video rectangle for safe-area guides;
- explicitly enables `sub-ass-use-video-data=all` for VSFilter-compatible ASS placement semantics;
- exposes ASS canvas and mpv OSD margin diagnostics to investigate the remaining external-ASS/MKV placement discrepancy.


0.17.0 reorganizes the editor UI around three persistent concepts instead of stacking every tool into one scrolling workbench:

- **Preview** remains visually dominant.
- **Subtitle dock** is a dense searchable event list for selection and navigation.
- **Inspector** is contextual and switches between Subtitle, Style, Effects, Review, and Project tools.
- Expanded tablets use preview/list plus a fixed-width inspector; medium tablets use preview above list + inspector; compact screens switch inspector content below the preview.
- renderer diagnostics and MKV bridge controls move out of the main subtitle list into the Project inspector.
- secondary actions such as Save As, font import, and font-cache rebuild move into the top-bar overflow menu.


0.16.0 is a preview-stabilization build:

- preserves playback position across renderer recreation after font import and reduces global playback-position state churn;
- disables the MKV's built-in subtitle selection before attaching the workbench preview ASS, so MKV and external-ASS paths render the same generated subtitle track;
- reports the active preview subtitle source in renderer diagnostics;
- maps layout/safe-area guides to the actual letterboxed/pillarboxed video rectangle using mpv display dimensions;
- clips the mpv surface during layout/orientation changes to reduce stale-frame spill;
- makes Style typesetting changes auto-apply after a short debounce;
- warns when inline ASS overrides shadow Style changes and provides an explicit action to let the focused event inherit Style again.


0.15.2 is a startup compatibility hotfix:

- fixes an Android/ICU `java.util.regex.PatternSyntaxException` caused by an unescaped closing brace in the ASS override-block regex;
- applies the same brace-safe regex in startup font diagnostics;
- keeps the Fontconfig renderer experiment unchanged so the next device run can test the actual renderer path rather than failing during `EditorViewModel` construction.

0.15.1 is a focused hotfix:

1. **lower-memory MKV attachment scanning** — embedded attachments are delivered one at a time to the importer instead of retaining the full attachment set in the scan result;
2. **forced font binding compatibility mode** — an imported font can explicitly rewrite every Style `Fontname` and every non-empty inline `\\fn` request to one family, matching the established MKV-Fast-Muxer-v3 force-mode semantics;
3. **inline font diagnostics** — explicit `\\fn` requests are included in the font-name diagnostic set instead of checking Style names only.

0.15 hardens MKV write-back:

1. **same-slot ASS replacement** — the selected subtitle keeps its TrackNumber, TrackUID, track ordering, language/name and disposition metadata;
2. **container preservation test** — the pinned mkvgo source receives an ASS Workbench replacement operation at CI time and is tested for track identity, font attachment, chapter and ordinary tag preservation;
3. **derived metadata safety** — content hashes/statistics are recomputed when the source carried them instead of copying stale values;
4. **smaller bridge surface** — the APK now bundles a dedicated `replace-ass` helper rather than the full mkvgo CLI.

0.14 established renderer observability, Review Sidecar V2 stable event identity, the initial ASS round-trip corpus and specification convergence.



1. **renderer observability** — mpv writes an app-private verbose log and recent libass/font-selection lines are surfaced beside family/glyph diagnostics;
2. **Review identity** — new sidecars persist stable event fingerprints instead of parse-order IDs, while legacy V1 sidecars remain readable;
3. **round-trip regression corpus** — representative Aegisub-like, custom-column and opaque-section fixtures exercise open → one-field edit → save → parse;
4. **scope convergence** — product and roadmap documents describe the implemented MKV Container Bridge and no longer promise an unimplemented SRT importer.

Still unresolved before 1.0: real-device confirmation of the CJK renderer path, exact placement of opaque lines, pathological custom Format layouts, broader MKV preservation, and larger real-world corpora.
