# Roadmap

## 0.4 — Foundation repair + explicit font assignment
- [x] Fix split-pane divider accumulation; no snap-back during drag.
- [x] Wire Adaptive Icon resources.
- [x] Tri-state select-all for the current filtered result set.
- [x] Imported-font picker for the focused Style.
- [x] Selected events can apply one imported family to all referenced Styles in one undoable edit.
- [x] Show exact/fallback/missing font status for the focused Style.
- [ ] Real-device verify CJK rendering after explicitly assigning the imported family to Style.

## 0.5 — Typesetting Workspace
- [ ] Generalize HSR layout capabilities: font size/emphasis/colors/outline/shadow/blur.
- [ ] Nine-grid alignment, margins and spacing.
- [ ] Safe-area, collision and overflow diagnostics.
- [ ] Fade and restrained soft-entry controls.
- [ ] Bilingual layout presets including the HSR 60/40 model.

## 0.6 — Bilingual + Review Workspace
- [ ] Paired bilingual view so two ASS Events may render as one UI row.
- [ ] Grouping layer so event count can double without list count doubling.
- [ ] Generalize HSR source/reference/final proofreading.
- [ ] Filters for modified, missing, timing mismatch and unreviewed.
- [ ] Store pair/group relations as project metadata, not ASS syntax.

## 0.7 — MKV Container Bridge
- [ ] Open MKV as a subtitle project.
- [ ] Enumerate ASS tracks and font attachments.
- [ ] Register attached fonts for preview.
- [ ] Save edited ASS back by remuxing with video/audio stream copy.
- [ ] Keep ordinary reference-video mode separate.

## 0.8 — Event overrides + visual placement
## 0.9 — Batch + project/recovery
## 0.10 — Round-trip and device audit
## 1.0 — Product acceptance
