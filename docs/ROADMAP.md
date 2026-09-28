# Roadmap

## 0.9 — Batch + recovery (current)
- [x] Long-press range-selection anchor + tap endpoint for touch devices.
- [x] Existing tri-state select-all continues to operate on the current filtered result set.
- [x] Batch time shift in milliseconds.
- [x] Batch Layer assignment.
- [x] Batch Style assignment.
- [x] Every batch mutation is one Undo step.
- [x] Persist split-pane ratio across launches.
- [x] Debounced internal crash-recovery ASS journal.
- [x] Restore/discard recovery controls on startup.
- [ ] Freehand sweep selection is deferred until range-selection ergonomics are tested.
- [ ] Review pair metadata/confirmation persistence still needs a project sidecar.
- [ ] MKV remux-back remains pending.

## 0.10 — Round-trip + container completion
- [ ] Complete MKV remux-back with video/audio stream-copy and attachment preservation.
- [ ] Preserve unsupported ASS content under repeated edit/save cycles.
- [ ] Project sidecar for review/group metadata.
- [ ] CJK/font-family device diagnostics.
- [ ] Real-world large-file / overlapping-event / lifecycle corpus.

## 1.0 — Product acceptance
