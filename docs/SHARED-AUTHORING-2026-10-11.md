# Shared authoring checkpoint — 2026-10-11

## Implemented scope

FIXED and SPATIAL_EXPERIMENTAL remain selectable. A session-owned EventTextDraftStore now holds canonical baselines, TextFieldValue/selection and dirty drafts for both production hosts. Primary TEXT/EVENT/EFFECTS and inline Event Text retain their existing shared primary buffer; independent instance owners remain separate. Canonical changes synchronize clean buffers and preserve dirty conflicts. Acknowledgement does not clear active IME composition. Activity saved state restores only a matching session. Deleted Event entries and removed independent instance owners are cleaned.

Main UI now uses one task strip: subtitles, text, typesetting, timeline and complete tools. The directory and visible preview state are pinned outside the task scroller. All original tool keys and the existing grouped/searchable production directory remain. Visual fixture helpers use those real routes rather than obsolete group-row selectors.

## Validation

- Full core/domain pure Kotlin JVM suite: **217/217 PASS** using kotlinc and JUnit platform, with test resources on classpath.
- Catalog/adaptive pure JVM suite: **9/9 PASS**.
- Missing-model/key functions were observed failing before implementation; independent-owner cleanup regression also RED→GREEN.
- Artifact policy and git diff whitespace: PASS.
- Added connected cross-host draft test: text remains uncommitted through main→canvas→main, explicit Apply then one Undo restores original document. Android execution Pending.
- Existing primary TEXT→EFFECTS→TEXT regression retained, now checks native EditableText rather than a partial semantics string with substring=false.
- Android SDK unavailable locally. No Android compilation, native screenshot, IME/device or connected PASS claim.
- Prior emulator run 38117186042 remains failure. Job metadata identifies editor regression suite; log tool returns Transport closed, exact assertion/root cause unknown.

## Review and rulings

Fresh read-only review checked draft ownership, provider lifetime, navigation/test helpers and visibility. The identified independent-owner reuse gap is now cleaned synchronously when tools are removed and covered by a pure model regression. Current TEXT/EVENT/EFFECTS descriptors do not expose duplication: this is a defensive ownership boundary, not a claimed device-reproduced duplicate-text-tool bug.

Ruling: preserve the established primary Event editor family across section changes and primary close/reopen; remove independent owner buffers on actual instance removal. Cost if wrong: primary-close draft policy would need an explicit user-facing lifecycle decision, rather than silently destroying shared drafts.

Ruling: execute natively in the existing feature checkout after the user's implementation approval, without another plan-confirmation loop. No main merge or stable release performed.

## Remaining scope

Time/metadata and arbitrary professional tool drafts have not all migrated to the session store. `.asswb` does not newly serialize dirty drafts. Unified subtitle filtering, scoped batch previews, further main pane and canvas chrome redesign remain subsequent coherent work units. This checkpoint is not whole-redesign or 240-ledger completion.

## UIGS Intake

Existing family: inbox/candidates/2026-10-03-spatial-canvas-camera-semantic-zoom-gesture-ownership.md. New bounded evidence: separate renderer-slot saveable keys avoid duplicate providers but do not themselves establish shared draft ownership. Product-source and pure-model evidence only; Android/IME/runtime confirmation Pending; no Canonical change.

Foundry write status: **Pending**. Automatic approval review rejected the cross-repository push for unconfirmed disclosure/destination authorization. Deduplicated bounded record retained in `.uigs/intake-pending/2026-10-11-shared-drafts.md`; no bypass and no Canonical mutation.
