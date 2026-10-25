# Current Event unified review — 2026-10-24

## Functional outcome

The user prioritized functional completeness over further visual redesign. The
current Event review action now collects the existing shared Raw text, Start/End
and Layer/Actor/Comment drafts into one frozen review. The follow-up also stages the Event Style reference as a fourth group, selected from the current document's existing Styles. It shows before/after
values for edited groups. Confirmation uses one document/history commit, so one
Undo restores all of that command's changes and Redo reapplies them.

The separate existing field-apply operations remain usable. Applied groups become
clean and therefore disappear from the next unified review. This command is not
Save: it marks the document dirty and does not claim filesystem persistence. It
is limited to one Event's text/time/metadata/Style reference, not Style definitions or every
professional tool's independent drafts.

## Data and concurrency contract

`EventEditReviewReceipt` contains immutable snapshots from the existing stores.
It has no UI/focus/document-owner state. The write target is its explicit Event ID,
not whichever Event happens to have focus when the dialog is confirmed.

- Style reference proposals use a separate session/saveable store instance with the same owner normalization. They never share the Raw ASS store. Canonical reconciliation covers all owners; dirty proposals survive host changes and restore.
- The Style picker only stages a name. Unified review must confirm it. The target Style must still exist at confirmation; external deletion/renaming or a conflicting reference change rejects the whole write. Assignment preserves Raw inline overrides and does not modify shared Style definitions.
- Only dirty groups are proposed. Untouched canonical groups and other Event
  fields (Style, margins, effects, extra fields) are copied from the latest Event.
- Invalid timing, reversed Start/End or Layer outside -999..999 rejects the whole
  proposal. No other group is partially committed.
- An external change to any dirty group's canonical baseline rejects the whole
  proposal. Unrelated canonical changes remain intact.
- The modal freezes submitted values. Editing a dirty proposal after opening the
  review disables confirmation and asks the user to reopen review.
- Session, resolved Event/owner and current canonical values are rechecked.
  Different owner/Event/session resets the dialog. The ViewModel checks session
  and target again and performs one `editDocument` operation without changing
  focus or selection.
- After success, existing store entries acknowledge the committed values. Text
  reconciliation retains the existing IME/selection-preservation behavior.

The existing `r3-write-*` semantic tags remain on the review, Raw before/after
values and confirmation so prior Raw-conflict regression remains meaningful.

## Validation work

Six new domain cases cover grouped writes and unaffected field preservation,
conflicts in each dirty group, untouched-group concurrent changes, invalid input,
missing/wrong Event, and clean/format-only timing. The domain module uses its
existing `kotlin.test`/JUnit Platform setup.

Two new real ViewModel/Compose cases cover: opening review without write; three
groups applied with one Undo/Redo; another Event and focus unchanged; changed
frozen draft disabling confirm; wrong session and stale canonical rejection.
The existing Raw-only conflict test now supplies all three existing stores.

Read-only static review found no blocking transaction/target/owner issue. The
local worker has no Gradle, Kotlin compiler or Android SDK, so these new tests and
Kotlin sources have not executed locally and no red/green cycle is claimed.
Existing35 Python capture/instrumentation/evidence tests passed, as did artifact
policy and `git diff --check`. Remote Kotlin/JVM/Android validation remains Pending.

Also repaired the directly observed capture-driver regression: only call
`performScrollTo()` for CAPABILITIES when the actual scrollable tablet rail is
present; the non-scrolling taskbar still requires display and a real click.
No full-suite assertions are weakened or skipped.

## Initial three-group feature baseline (historical)

Source `994dd94ac25f376d3f84b878c9739c865869ed52`:
source/JVM40058804721 SUCCESS; emulator40058804716 FAILURE.
Four projection groups22 passed; full182 tests/4 failures/0 errors/0 skipped.
Both tablet PNG gates and bounded evidence jobs succeeded:
1440×900 SHA256 `62772d1f43e2a0453d29a9191f8d90d3cc8b2422767072d9a99a30a2a176d64e`;
1024×768 SHA256 `c777a113d67c04d95f1ce11da044c8920cfef328b0c0718aa06349c1ba2c0cd4`.

Four remaining baseline failures: rotation draft stale row, spatial resident-pin
visibility, disposed transform field during text replacement, and capture-driver
scroll on a non-scrollable taskbar. Only the last receives a scoped driver repair
here; its effect still requires new Android execution.

## Intake and next acceptance

Foundry searches for review/snapshot/draft/commit and native draft contracts found
prior draft/Undo cases. The offline-v2 Bug is a distinct artifact and is not closed
or appended as if it were this Android implementation. This is an unvalidated
product feature following existing contracts, not a newly proven reusable rule;
no duplicate Candidate or Canonical promotion.

Next: compile and run domain/Android tests, verify actual review→confirm→Undo→Redo,
and resolve remaining full-suite failures from concrete device evidence. Keep
PR149 and parent148 Draft; this feature does not establish whole-project completion.


## Style-reference extension validation status

The earlier three-group implementation at source `e29bc60306bbeb7c934ee1e151b13996ec95dc6b`
passed source/JVM workflow40061239628. Emulator40061239623 completed184 tests with
3 failures,0 errors,0 skipped; both unified-review device cases were outside the
failure list, four projection groups22/22 passed, and both strict tablet PNG gates
and bounded evidence jobs succeeded. Remaining failures: inspector rotation IME,
spatial resident-pin visibility, and disposed transform field during text input.

The new Style extension adds3 domain cases, expands the main real-VM case to4-group
commit/one Undo/Redo, adds a missing-style whole-write rejection/store-restore case,
and adds a real dropdown staging/reset case. Prior Raw-only fixtures explicitly
provide their separate Style store. Static review found no blocking owner/session,
API compatibility, provider or transaction issue. These new changes have not been
compiled/executed locally (no Gradle/Kotlin/Android toolchain); remote validation
remains Pending. Existing35 Python evidence checks, artifact policy and diff check
passed. No visual redesign or full-suite gate suppression in this work package.
