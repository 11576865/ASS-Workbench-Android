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
- [x] MKV subtitle write-back is available through the bundled mkvgo bridge with video/audio stream copy.

## 0.10 — Round-trip + review persistence (current)
- [x] Replace the edited ASS track without re-encoding video/audio.
- [x] Persist Review Source/Target Style, confirmation state and immutable reference text in an app-private sidecar keyed to the ASS URI or MKV track.
- [x] Restore Review metadata when reopening the same ASS or MKV track.
- [x] Editing a previously confirmed Review target invalidates that confirmation.
- [ ] Audit attachment preservation and track metadata across a broader MKV corpus.
- [ ] Preserve unsupported ASS content under repeated edit/save cycles.
- [ ] CJK/font-family device diagnostics.
- [ ] Real-world large-file / overlapping-event / lifecycle corpus.

## 1.0 — Product acceptance
