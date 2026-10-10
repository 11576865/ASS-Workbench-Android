# Fixed Event inspector draft scope across tool switching

Date: 2026-10-10
Lifecycle: repair-submitted
Source: ASS-Workbench-Android PR #138, emulator run 38032992080, exact head `faf660dd5bff6777aa69b1abe6c6257bebc1a027`.

## Observed evidence

The previous Compose provider-key fix reduced full Android emulator failures
from 22/120 (run 38027904104) to 12/120 (run 38032992080), removing duplicate
`SaveableStateHolder` registration crashes.

`inspectorDraftSurvivesToolSwitchAndRotation` now fails its stage-specific
`assertInspectorDraftStillPresent("after-tool-switch")`: `WORKBENCH` is
missing from `event-raw-1` **before rotation**. The fixed workspace displays
one event inspector but switches the selected ToolInstance from TEXT to
EFFECTS and back. `FloatingToolContent` keyed its saved editor buffer by
`instance.id + Event.id`. On return to TEXT, the buffer key can differ
even though the fixed inspector continues editing the same Event.

## Repair scope

The fixed inspector explicitly passes `draftSaveableScope="fixed-inspector"`;
the shared `SaveableStateHolder` stores its mutually exclusive inspector
buffer under that stable scope plus Event ID. Spatial live tool instances
continue to use their instance-specific saveable keys: multiple spatial
editors for the same Event must never share a concurrently mounted provider.

The field's existing `rememberSaveable`, `rawBaseText`, and
`RawEventDraftPolicy` remain authoritative for local draft/conflict logic.
No early canonical commit, ToolInstance rebinding or domain Undo change.
Adds a focused Android instrumentation test that asserts canonical Event text
remains unchanged through TEXT → EFFECTS → TEXT and is updated only on Apply.

## Remaining evidence

This repair targets a specific observed within-session tool-switch failure.
It is not yet verified by a new exact-head emulator run and does not prove
subsequent rotation/tablet-density transitions. Other remaining failures
(viewport history, touch, layout, visual capture) are separate.
