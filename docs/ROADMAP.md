# Roadmap

## 0.7 — MKV Container Bridge (current)
- [x] Separate "Open MKV project" from ordinary reference-video mode.
- [x] Pure Kotlin streaming Matroska/EBML scanner; no second FFmpeg native stack is introduced.
- [x] Enumerate S_TEXT/ASS subtitle tracks.
- [x] Reconstruct editable ASS from CodecPrivate + subtitle Blocks/BlockGroups.
- [x] Extract TTF/OTF attachments and register them in the same Font Registry used by libass.
- [x] Multi-track selector; one ASS track becomes the ordinary editable document.
- [x] The same MKV is attached as reference video.
- [ ] Remux modified ASS back to a new MKV with video/audio stream-copy.
- [ ] Preserve/re-attach original attachments during remux.
- [ ] TTC/OTC font collection support.

## 0.8 — Event overrides + visual placement
- [ ] Blur, fade and restrained soft-entry tags.
- [ ] Per-event override model and raw override view.
- [ ] Drag preview to position and X/Y nudge.

## 0.9 — Batch + project/recovery
- [ ] Range/sweep selection for touch.
- [ ] Batch style/alignment/time operations.
- [ ] Project sidecar and crash-recovery journal.
- [ ] Persist review confirmation/pair metadata and divider ratio.

## 0.10 — Round-trip and device audit
## 1.0 — Product acceptance
