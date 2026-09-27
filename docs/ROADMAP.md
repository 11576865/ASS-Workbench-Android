# Roadmap

## 0.1 — Editing skeleton
- [x] pure Kotlin ASS document/event/style model
- [x] parser/writer with unknown-section preservation
- [x] undo/redo history
- [x] SAF video/subtitle selection
- [x] independent video/subtitle binding in editor state
- [x] searchable subtitle list
- [x] overlapping events and Layer in the model
- [x] multi-select checkboxes
- [x] selected-line text/time editor
- [x] resizable split-pane layout
- [x] Save / Save As ASS

## 0.2 — Authoritative preview + font registry (current)
- [x] replace Compose approximation with mpv + libass authoritative preview
- [x] edit debounce writes one preview ASS and `sub-reload` updates it without restarting video
- [x] `content://` reference video support through Android ContentResolver stream provider
- [x] imported TTF/OTF directory wired directly to mpv `sub-fonts-dir`
- [x] OpenType `name` table parser; filename is never treated as font family
- [x] font SHA-256 + aliases + family/subfamily/PostScript metadata
- [x] basic exact/fallback/missing diagnostics for ASS Style font families
- [x] importing a font rebuilds only the preview core and restores the current playback position
- [ ] real-device audit of font reload behavior on several OEM Android builds

## 0.3 — Styles
- [ ] complete ASS Style editor
- [ ] colors and alpha
- [ ] outline/shadow/blur
- [ ] nine-grid alignment
- [ ] margins and spacing
- [ ] browse registry and assign a font to a Style

## 0.4 — Event overrides and visual placement
- [ ] per-event style override model
- [ ] drag subtitle on preview -> `\\pos`
- [ ] X/Y nudge
- [ ] per-event alignment
- [ ] per-event `\\fad`
- [ ] raw ASS tag view

## 0.5 — Batch workflow
- [ ] batch style apply
- [ ] batch font/alignment/position operations
- [ ] batch time shift
- [ ] duplicate/delete/insert/split/merge events
- [ ] one batch action = one undo step

## 0.6 — Project/recovery
- [ ] saved project binds video URI + subtitle URI + editor state
- [ ] crash recovery journal
- [ ] explicit Save vs Save As
- [ ] atomic source-file replacement where provider supports it

## 0.7 — ASS round-trip hardening
- [ ] fixtures from real-world ASS files
- [ ] preserve unsupported sections/tags/comments where possible
- [ ] PlayRes resampling behavior
- [ ] malformed-file warnings

## 0.8 — Tablet/phone polish
- [ ] persist divider ratio
- [ ] landscape/portrait restoration
- [ ] keyboard/mouse support
- [ ] large-file list performance

## 0.9 — Real-device audit
- [ ] Android 8–15 representative devices
- [ ] large files
- [ ] multiple fonts/weights
- [ ] overlapping/layered events
- [ ] rotation/background lifecycle
- [ ] content:// provider variations

## 1.0
- [ ] satisfy PRODUCT_SPEC_1_0.md without adding encoding/muxing scope
