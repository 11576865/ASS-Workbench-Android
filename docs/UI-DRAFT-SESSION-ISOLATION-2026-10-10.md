# Draft-owner isolation and rotation failure diagnostics

Date: 2026-10-10
Status: repair-evidenced / Pending External Validation
Source: ASS-Workbench-Android active canvas-redesign PR #138.

## Problem

The root `ModernEditorScreen` retained one `SaveableStateHolder` across
`workspaceSessionId` changes. Its cached InlineEventEditor keys use tool
instance IDs and ASS Event IDs but not project/session identity. Loading another
project with the same Event ID could retrieve a previous project's uncommitted
editor buffer. No document canonicalization or Undo event should arise from an
uncommitted draft.

Independently, Android Emulator run 37984011960 (PR #138 head
`e0ce553da2754dbed18e41bcea6de58d23f10880`) reported
`inspectorDraftSurvivesToolSwitchAndRotation` failing 1 of 13 tests. Logging
reached the Apply-draft step, but the streamed XML emitted an empty
`<failure>` body. The root cause **cannot** be deduced from that evidence.

## Patch

- Key the root SaveableStateHolder on `workspaceSessionId`; it remains
  stable through Activity recreation, rotation, and viewport changes inside
  one session but is not reused across project sessions.
- Add an instrumentation regression that leaves Event #1 dirty, reloads the
  same Event #1 in a new project session, then checks the new editor is clean.
- Add raw draft and Apply-control assertions at four boundaries in the
  existing rotation test: return from Effects, landscape, tablet, and
  reselect-after-viewport-reset.

## Remaining evidence

The code addresses cross-session saveable buffer scope. It has not yet
shown that the original within-session rotation test passes. Android CI,
emulator, device visual acceptance and full 240-item UI completion remain
separately Pending. No Canonical change is made.
