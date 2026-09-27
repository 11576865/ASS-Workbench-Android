# ASS Workbench Android

A focused Android ASS subtitle workbench for phones and tablets.

**Current version: 0.5.0**

The app edits **ASS subtitles** while a local video is used only as reference media. It does **not** encode hard-subbed video and does **not** mux subtitles into containers.

## 0.5 status

- independent reference-video and ASS file selection;
- tablet preview-left / subtitle-list-right split with draggable divider;
- compact vertical layout on narrow screens;
- searchable event list, overlap/Layer model, multi-select state;
- selected-line text/time editing;
- bounded Undo/Redo;
- Save / Save As `.ass`;
- **mpv + libass authoritative video/subtitle preview**;
- edited ASS is reloaded without restarting the reference video;
- TTF/OTF import into the mpv config `fonts/` directory used by libass;
- the most recently imported font is also installed as `subfont.ttf` fallback for provider-less Android libass;
- OpenType name-table parsing (family name is not inferred from filename);
- font exact/fallback/missing diagnostics;
- ASS and reference video can be opened in either order;
- dedicated launcher icon.

## Product boundary

Output is a standalone `.ass` file. Video encoding, hard-sub rendering, MKV/MP4 muxing, ASR, OCR and translation are deliberately outside scope.

## Build

JDK 17+ and Android SDK 35 are expected. CI installs Gradle 8.9 and builds a debug APK on every push to `main`.

The authoritative preview uses `libmpvKt` 0.3.0, which bundles mpv/FFmpeg/libass. Because the distributed native binaries are GPL, this repository is licensed under **GPL-3.0-or-later**.

See `docs/PRODUCT_SPEC_1_0.md`, `docs/ROADMAP.md`, and `THIRD_PARTY_NOTICES.md`.


## 0.5 Typesetting Workspace

The focused ASS Style now has a dedicated typesetting panel for size, emphasis, spacing, outline, shadow, colors, nine-grid alignment and margins. A preview guide can show the safe area and the two central bilingual boundaries. The 60/40 bilingual geometry is generalized from the HSR layout workbench: 3% horizontal safe margins, 5% vertical safe margins, a protected central gap, 60% of the remaining safe height for the source region above and 40% for the target region below.

Blur, fade and transform-based soft entry are not represented as fake Style properties; they remain planned Event override work.
