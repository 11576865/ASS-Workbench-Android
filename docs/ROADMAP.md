# Roadmap

## 0.8 — Event overrides + visual placement (current)
- [x] Managed per-event `\\pos`, `\\blur` and `\\fad` editing.
- [x] Restrained 98% → 100% soft-entry transform.
- [x] Preserve unrelated leading override tags when managed tags are changed.
- [x] X/Y numeric entry and ±5 px nudge.
- [x] When no `\\pos` exists, nudge starts from the Style alignment/margin anchor.
- [x] Raw Event Text / override view hidden by default.
- [ ] Direct drag-on-video positioning remains pending until mpv Surface pointer arbitration is proven stable.

## 0.9 — Batch + project/recovery
- [ ] Range/sweep selection for touch.
- [ ] Batch style/alignment/time operations.
- [ ] Project sidecar and crash-recovery journal.
- [ ] Persist review confirmation/pair metadata and divider ratio.
- [ ] Complete MKV remux-back with stream-copy and attachment preservation.

## 0.10 — Round-trip and device audit
- [ ] Preserve unsupported ASS content under repeated edit/save cycles.
- [ ] CJK/font-family device diagnostics.
- [ ] Large files, many overlapping events, rotation and lifecycle.
- [ ] MKV scanner/remux real-world corpus.

## 1.0 — Product acceptance
