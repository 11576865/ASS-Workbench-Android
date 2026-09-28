# Roadmap

## Done through 0.13

- [x] ASS-first editable domain with mpv + libass authoritative preview.
- [x] Independent reference video and subtitle selection.
- [x] Edit / Typeset / Review / Event Effects workspaces.
- [x] Imported TTF/OTF Font Registry with OpenType family parsing and cmap glyph checks.
- [x] Bilingual Source/Target pairing and persisted Review sidecar.
- [x] Batch time/Layer/Style edits, touch range selection, Undo/Redo and crash recovery.
- [x] MKV Container Bridge for embedded ASS/font extraction and arm64 no-transcode write-back.
- [x] Unknown ASS sections and opaque/comment/blank lines inside known sections preserved.
- [x] Original Style/Event Format column order and unknown custom column values preserved.

## 0.14 — Stabilization

### Renderer diagnostics
- [x] Configure an app-private mpv log for the authoritative preview.
- [x] Surface recent libass/font-selection log lines beside family and glyph diagnostics.
- [x] Keep family match, cmap coverage and renderer selection as separate diagnostic layers.
- [ ] Confirm on a real Android device which font face libass selects for the known CJK square-glyph failure.
- [ ] Remove any workaround shown to be unnecessary once the root cause is known.

### Review identity
- [x] Replace parse-order IDs in newly persisted Review Sidecar V2 data with stable event fingerprints.
- [x] Decode legacy V1 sidecars and resolve their IDs against the current document.
- [x] Debounce sidecar writes.
- [x] Invalidate confirmation when a confirmed event is structurally edited.
- [ ] Exercise insert/delete/reorder when those editor operations exist.

### Round-trip harness
- [x] Add initial Aegisub-like, custom-column and opaque-section ASS fixtures.
- [x] Exercise open → edit one event field → save → parse for every fixture.
- [ ] Preserve exact original placement/order of extras relative to structured records.
- [ ] Support multiple Format declarations in one section without collapsing schema history.
- [ ] Define or reject pathological custom Format layouts where Text is followed by comma-bearing fields.
- [ ] Expand the corpus with real files from multiple ASS-producing tools.

### Documentation
- [x] Synchronize README version and 0.14 scope.
- [x] Align PRODUCT_SPEC_1_0 with the implemented MKV Container Bridge.
- [x] Remove the unimplemented SRT importer from 1.0 acceptance.
- [x] Remove stale version-specific wording from THIRD_PARTY_NOTICES.

## 0.15 — MKV preservation

- [x] Replace edited ASS in the original track slot instead of remove + append.
- [x] Preserve TrackNumber, TrackUID, track order, language/name, BCP-47 language and disposition flags.
- [x] Preserve attached fonts, chapters and ordinary tags in an end-to-end bridge test.
- [x] Recompute content-derived hash/statistics tags when the source carried them.
- [x] Eliminate the full-size `without-track.mkv` intermediate.
- [x] Build a minimal ASS Workbench bridge executable instead of shipping the full mkvgo CLI.
- [ ] Expand write-back testing to a real-world MKV corpus from multiple muxers.
- [ ] Verify unusual EBML layouts and large files on device.
- [ ] Decide how to handle ASS packets using unsupported lacing in the Android reader.

## 0.20.0 — Professional editor infrastructure (current)

- [x] Add a lossless ASS inline syntax analyzer shared by UI highlighting/validation.
- [x] Preserve unknown tags while identifying standard tag names and their values.
- [x] Highlight override blocks, tag names, values and escapes; underline malformed blocks.
- [x] Keep raw Event Text permanently visible in the Effects inspector and syntax-aware in Review.
- [x] Expand Style editing to SecondaryColour, ScaleX/Y, Angle, BorderStyle and Encoding.
- [x] Add a compact visual Style geometry preview.
- [x] Compact workspace tabs and subtitle rows without hiding ASS structure.
- [x] Add persisted System / Light / Dark appearance modes.
- [x] Cache imported font metadata/font bytes.
- [x] Debounce and bound expensive glyph diagnostics.
- [x] Refresh stable Compose/Core/Lifecycle dependencies and compile against Android 37.
- [x] Remove redundant nested cards from supporting inspectors.
- [ ] Device-check 0.20 as one batch on the primary tablet.
- [ ] Validate large-event-list performance with real ASS files.
- [ ] Continue external-ASS vs MKV placement comparison if geometry still differs.

## 0.19.0 — Batched tablet usability + diagnostics (done)

- [x] Keep ASS override tags visible and directly editable, with syntax-aware rendering that visually separates override blocks, tag names, values and text escapes from dialogue.
- [x] Expose event-level Margin L/R/V in the subtitle inspector with reset-to-Style semantics.
- [x] Allow selected subtitles to bulk-clear managed Style/position overrides.
- [x] Report effective inline override sources in the Style inspector.
- [x] Let the Style editor consume the full inspector height.
- [x] Surface PlayRes, ScaledBorderAndShadow, LayoutRes/YCbCr metadata when present, and override counts in Project diagnostics.
- [x] Commit playback seeks at the end of slider scrubbing instead of on every drag sample.
- [x] Draw the mpv video rectangle separately from the ASS safe area.
- [ ] Device-check the full batch in one session: playback, guide rectangle, margin inheritance, selection-local Style, color picker, and external-vs-MKV placement.
- [ ] If external-vs-MKV placement still differs, capture both final ASS representations and compare Script Info/Style/Event fields automatically.

## 0.18.0 — ASS style fidelity and placement diagnostics (done)

- [x] Explain that Style edits apply to every subtitle that references that Style.
- [x] Allow selected subtitles to receive an automatically cloned independent Style.
- [x] Make "inherit Style" clear managed inline font/size/emphasis/color/border/alignment/position overrides and event-level margins.
- [x] Replace raw ASS color entry fields with a popup RGBA picker.
- [x] Map safe-area guides using mpv `osd-dimensions` margins instead of reconstructed aspect-ratio geometry.
- [x] Explicitly request `sub-ass-use-video-data=all` to match standard VSFilter/libass semantics.
- [x] Surface ASS PlayRes plus OSD dimensions/margins in renderer diagnostics.
- [ ] Verify the safe-area overlay on the target tablet.
- [ ] Re-test external ASS vs MKV-derived ASS vertical placement with the exact same font and script.
- [ ] If placement still differs, capture and compare the final serialized `current.ass` against the MKV CodecPrivate/events reconstruction.

## 0.17.0 — Adaptive workspace UI (done)

- [x] Replace the single oversized workbench column with Preview + Subtitle Dock + Inspector.
- [x] Keep the video preview as the dominant primary pane.
- [x] Keep subtitle search/list selection persistently accessible on tablet layouts.
- [x] Move Style, Effects, Review, Project/MKV and renderer diagnostics into contextual inspector sections.
- [x] Use different expanded, tablet and compact compositions instead of stretching one layout.
- [x] Reduce top-bar action density with an overflow menu for secondary actions.
- [ ] Device-check landscape and portrait tablet ergonomics.
- [ ] Tune pane proportions and minimum widths from real tablet screenshots.
- [ ] Decide whether the medium-width inspector should become a draggable supporting pane.

## 0.16.0 — Preview-path stabilization (done)

- [x] Preserve playback position across font-renderer recreation.
- [x] Throttle editor-wide playback position publication to reduce unnecessary recomposition.
- [x] Disable embedded MKV subtitle selection before attaching the workbench-generated preview ASS.
- [x] Surface active preview subtitle source/SID in renderer diagnostics.
- [x] Map safe-area guides to the actual displayed video rectangle instead of the whole preview container.
- [x] Clip the preview surface while the layout changes orientation/size.
- [x] Auto-apply Style typesetting edits after a short debounce.
- [x] Detect inline ASS tags that shadow Style edits and let the focused event return to Style control.
- [ ] Device-compare external ASS and MKV-derived preview positions using the same ASS/font pair.
- [ ] Confirm whether orientation transitions are fully stable on the target tablet.

## 0.15.2 — Android startup regex hotfix (done)

- [x] Escape the closing brace in ASS override-block regexes for Android/ICU compatibility.
- [x] Prevent `EditorViewModel` construction from failing in initial font diagnostics with `PatternSyntaxException`.
- [ ] Re-run the Fontconfig experimental APK on the device and continue renderer-selected-font diagnostics.

## 0.15.1 — MKV/font hotfix (done)

- [x] Avoid retaining all embedded MKV font payloads after scanning.
- [x] Deliver attachments to FontStore one at a time during the scan.
- [x] Add an explicit compatibility action that rewrites all Style Fontname values and non-empty inline `\\fn` overrides to one imported family.
- [x] Include inline `\\fn` requests in font-name diagnostics.
- [ ] Confirm the formerly crashing MKV opens on the affected Android device.
- [ ] Confirm the forced binding mode removes the known CJK square-glyph failure on the affected device.

## Next hardening

### MKV preservation audit
- [x] Verify attached fonts survive write-back in the synthetic preservation fixture.
- [x] Verify chapters and ordinary tags in the synthetic preservation fixture.
- [x] Verify default/forced/extended flags, TrackUID, order, legacy/BCP-47 language and name in the synthetic preservation fixture.
- [ ] Verify large files and unusual EBML.
- [ ] Decide how to handle ASS packets using unsupported lacing.

### Android/device reliability
- [ ] Fixed smoke-test checklist on at least one primary tablet and one phone.
- [ ] Divider gesture and orientation/lifecycle regression checks.
- [ ] App icon confirmation on a current build.
- [ ] Large event-list performance.
- [ ] Compose/instrumentation coverage for critical flows.

## 1.0 blockers

- [ ] Known CJK font-rendering failure is diagnosed and fixed on real hardware.
- [ ] ASS round-trip behavior is documented and protected by a representative corpus.
- [ ] MKV write-back preservation is audited on representative containers.
- [ ] Review sidecar identity remains correct across supported structural edits.
- [ ] Core open/edit/save/reopen flows pass the device smoke-test checklist.
- [ ] Product specification, README, roadmap and release behavior agree.

## Deferred unless evidence justifies promotion

- Freehand sweep selection beyond the existing anchor + endpoint interaction.
- Generalized multilingual SubtitleGroup beyond current 1:1 Source/Target pairing.
- Word-alignment sidecars and Karaoke authoring.
- General ASR, OCR or translation.
- Video encoding, filters or hard-sub rendering.
- Full Aegisub parity, Lua Automation, vector drawing and professional waveform/spectrogram timing.
