# Central Raw authoring dock (source increment)

Design placement: Figma 5yNY5dMnmpmaOnYxDlEhY5 / 85:322. This increment implements the central text location in the wide FIXED subtitle scene. It does not implement independent source/translated language lanes: the project currently has a Raw Event Text buffer, and assigning languages by splitting Raw would invent data semantics.

## Implementation

Extract the existing Raw text field, canonical reconciliation, dirty/conflict handling, split operation and write-review/commit path into EventRawTextEditor. FixedV11TextDock invokes the same editor with the same LocalEventTextDraftStore and ToolInstance owner. InlineEventEditor keeps timing/metadata and omits the Raw projection only while the wide dock is mounted. Other hosts/layouts still render the shared editor at its prior location.

The inspector retains a single movable identity with an explicit presentation flag. The dock has finite scrollable height; layout policy reserves its budget before video/timeline allocation. No ASS parser, bilingual inference, domain writer, Undo owner, tool registry or SPATIAL behavior is added.

## Verification

New sources: two JVM geometry cases (budget conservation and hidden-media preference), one Compose test with a real EditorViewModel/recovery document and shared draft store verifying dirty Raw persists across dock disposal/remount without a canonical write. Existing domain/inspector/write-review tests remain enabled.

Local git diff whitespace and Actions artifact-policy checks pass. Android/Compose/JVM execution is not available locally because Gradle is absent. New extraction, layout and interaction remain Pending External Validation; no red/green or screenshot parity is claimed.

Prior exact head 1f38dec: source/JVM run 39895419325 passed. Emulator 39895419351 failed: strict R3 capture observed accessibility package android and unfocused target decor; the full suite timed out after 20 minutes and XML was empty/unreadable. Four focused projection groups completed 22 tests with zero failures. Neither those projections nor source CI prove full suite, new tab interaction, Figma parity or real-device acceptance. The system overlay/focus cause is not established or fixed by this UI change.

## Remaining

Real bilingual authoring data mapping; full contextual inspector; 1440x900/1024x768 native visual comparison; IME/device validation; foreground/ANR investigation and historical failures. PRs remain Draft.

UIGS Intake: application of existing draft-ownership/relocation and evidence-boundary knowledge; no new reusable finding or Canonical change.
