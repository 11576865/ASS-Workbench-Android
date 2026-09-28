# Roadmap

## 0.6 — Bilingual + Review Workspace (current)
- [x] Pair View collapses two Style tracks into one review row.
- [x] Pairing is a derived UI layer; ASS Events remain ordinary ASS Events.
- [x] Explicit Source Style / Target Style selectors.
- [x] Greedy time-based pairing with unmatched rows preserved.
- [x] Timing mismatch diagnostics.
- [x] Source / Reference / Final proofreading model generalized from HSR.
- [x] Reference is the target text captured when the ASS file was loaded.
- [x] Final edits write back only to the target ASS Event.
- [x] Per-target confirmation state is independent from text modification.
- [x] Filters: modified, unreviewed, missing side, timing mismatch.
- [ ] Persist pair/confirmation metadata in a project sidecar; 0.6 keeps review state in the current app session.

## 0.7 — MKV Container Bridge
- [ ] Open MKV as a subtitle project.
- [ ] Enumerate ASS tracks and font attachments.
- [ ] Register attached fonts for libass preview.
- [ ] Save edited ASS back by remuxing with video/audio stream copy.
- [ ] Keep ordinary reference-video mode separate.

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
