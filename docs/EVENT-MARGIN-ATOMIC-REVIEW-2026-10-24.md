# Event margins in unified review — 2026-10-24

## Functional slice

The existing FIXED V1.1 Event inspector now stages Event-level `Margin L/R/V`
as a fifth independent draft group, alongside Raw, Start/End, metadata and
the Event Style reference. Zero explicitly inherits the referenced Style's
corresponding margin. Editing does not alter Style definitions or unknown Raw
overrides. The control is included only in the existing docked Event inspector,
not duplicated as a separate authoring engine.

The per-session saveable store is keyed by existing normalized Event owner and
Event ID. It survives remount and recovery, prunes deleted Events and closed
surface owners, and conservatively preserves dirty entries across external writes.

## Transaction and safety

- All three margins must parse as 0..9999 integers before confirmation.
- Only a dirty margin group is written; clean margins are copied from the
  latest canonical Event, preserving unrelated concurrent changes.
- Changes to any Event margin after the review baseline reject the **whole**
  receipt; text/time/metadata/Style cannot partially apply.
- Frozen receipt values and the live draft are compared again at confirm.
- The existing ViewModel single `editDocument` command remains the exclusive
  writer and produces exactly one Undo/Redo history command.
- Rejecting a transaction preserves drafts, and the UI allows explicit
  margin-draft reset without overwriting the canonical document.
- The new margin store is independent of Raw text, Style reference and
  metadata stores.

## Evidence boundary

Added domain tests for valid/invalid/overflow margins, conflict and
reconciliation, versioned save/restore isolation and atomic Event application.
Extended real Android ViewModel/Compose review instrumentation to stage five
groups, confirm them together, assert no write before confirmation and verify
one Undo/Redo restores all fields. Added native Compose field/remount coverage.
These are test sources submitted for remote execution; this document does not
claim local Gradle or emulator execution. The feature continues under Draft PR
#149 on top of Draft PR #148.
