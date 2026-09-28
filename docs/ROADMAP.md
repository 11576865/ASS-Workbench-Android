# Roadmap

## 0.7.1 — MKV Container Bridge write-back (current)
- [x] Stream-scan MKV without decoding video/audio.
- [x] Enumerate embedded S_TEXT/ASS tracks.
- [x] Reconstruct editable ASS from CodecPrivate + subtitle Blocks.
- [x] Extract TTF/OTF attachments into the shared Font Registry.
- [x] Use the original MKV as reference video automatically.
- [x] Bundle a pinned pure-Go mkvgo helper for arm64 Android.
- [x] Replace the selected ASS track through lossless Matroska remux.
- [x] Preserve inherited attachments while rewriting.
- [x] Save as a new MKV; never overwrite the source container in place.
- [ ] Expand the mkvgo helper to armeabi-v7a/x86_64 after arm64 real-device verification.
- [ ] Avoid the temporary full source copy by adding a SAF-aware streaming filesystem bridge.
- [ ] Preserve original subtitle default/forced flags when replacing a track.

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
