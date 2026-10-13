# Birdseye tool discovery: search, hidden recall and stable spatial context

Date: 2026-10-10
Status: implemented in stacked PR / Android verification pending
Source: ASS-Workbench-Android PR #138 workspace redesign.

## Problem and functional gap

The existing birdseye dialog had a normalized 180dp map and a vertically
scrolling list. Searching a large number of real ToolInstances required
scanning the full list; hidden tools were mixed among visible ones. It also
did not explain an empty search result because it had no search affordance.

The map must keep full-scene projection while a result set narrows: refitting
to the filtered subset on every keystroke would create false spatial motion.

## Implementation

- Search matching each tool's user title, binding/identity subtitle and
  stable ToolInstance ID (case insensitive, surrounding whitespace ignored).
- Show an explicit `仅已收回` filter and matched/total count; allow clearing
  the search without closing the dialog.
- Keep all world markers in the original normalization; dim nonmatching
  markers, exclude them from hit testing and list entries.
- Show actionable no-results and truly-empty states.
- Clicking a matched hidden tool uses the existing focus/recall path.
  No new ownership of ToolInstance, ASS canonical Event, Undo, node position,
  hidden flag or camera state is invented by searching or filtering.

## Automated regression scope

Two pure JVM tests cover title/subtitle/ID search and ordering, hidden
filtering, geometry immutability, stable map and filtered map hit testing.
Two Android instrumentation tests cover hidden-node recall after search/
clear/filter and no-results recovery.

These are **submitted tests**, not verified execution. Because this stacked
PR targets an unmerged redesign branch, it may not independently trigger
main-only workflows. Verify on the exact parent-branch head after integration.
Existing editor-draft rotation/device acceptance is separate and remains open.
