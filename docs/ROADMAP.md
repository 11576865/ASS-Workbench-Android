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

## 0.15.2 — Android startup regex hotfix (current)

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
