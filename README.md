# ASS Workbench Android

A focused Android ASS subtitle workbench for phones and tablets.

**Current version: 0.14.0**

ASS Workbench treats ASS as the primary editable document. A local video may be attached as reference media, or an MKV can be opened as a subtitle project.

## Current status

- independent reference-video and ASS selection;
- tablet preview-left / workbench-right split with draggable divider;
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

Event text/timing, search, Layer, Style assignment, selection and batch operations.

### Typeset

Font family/size, emphasis, colors, outline/shadow, spacing, nine-grid alignment, margins, safe-area guide and bilingual 60/40 geometry.

### Review

Paired bilingual grouping, source/target Style selection, editing, confirmation state and persistent review metadata.

### Event effects

Managed event-level overrides for position, blur, fade and restrained soft entry. Existing unrelated leading ASS override tags are preserved.

### Container Bridge

An MKV can be opened as a subtitle project. The reader scans Matroska tracks and supported font attachments. On supported arm64 builds, the bundled pinned Go helper replaces the selected ASS track into a new MKV while carrying video/audio streams without transcoding.

The original MKV is never modified in place. The current implementation uses app-private temporary files, so large containers require corresponding free storage.

## Current hardening work

0.14 is a stabilization release:

1. **renderer observability** — mpv writes an app-private verbose log and recent libass/font-selection lines are surfaced beside family/glyph diagnostics;
2. **Review identity** — new sidecars persist stable event fingerprints instead of parse-order IDs, while legacy V1 sidecars remain readable;
3. **round-trip regression corpus** — representative Aegisub-like, custom-column and opaque-section fixtures exercise open → one-field edit → save → parse;
4. **scope convergence** — product and roadmap documents describe the implemented MKV Container Bridge and no longer promise an unimplemented SRT importer.

Still unresolved before 1.0: real-device confirmation of the CJK renderer path, exact placement of opaque lines, pathological custom Format layouts, broader MKV preservation, and larger real-world corpora.
