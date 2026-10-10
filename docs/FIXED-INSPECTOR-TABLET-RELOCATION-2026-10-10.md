# Fixed inspector draft ownership when adaptive layout changes

Date: 2026-10-10
Status: repair-submitted / Pending External Validation
Source product: ASS-Workbench-Android draft PR #138
Source exact head: `df336e0b187470258f606f9970975bf1b0acfb80`
Emulator evidence: [run 38050733194](https://github.com/11576865/ASS-Workbench-Android/actions/runs/38050733194)

## Reproduced boundary

The focused Event #1 raw draft `Recovered line WORKBENCH` remained uncommitted and survived TEXT→EFFECTS→TEXT and an initial landscape rotation. During the emulator's `wm size 1920x1200`, `wm density 160` switch to a tablet-width configuration, the existing instrumentation assertion `assertInspectorDraftStillPresent("tablet-landscape")` observed `Recovered line` instead. The source Event remained canonical and unmodified. This is lost local editing state, not evidence of a successful Apply.

`FixedWorkspace` had three separate composition branches for `COMPACT`, `DUAL_PANE`, `THREE_PANE`. Each invoked the `inspector` lambda from a different call site. Compose can dispose/recreate the subtree while layout policy changes, even though the logical inspector and its draft owner are unchanged. Existing `SaveableStateHolder` isolation did not guarantee continuous composition under this transition.

## Focused intervention

Keep a single remembered `movableContentOf<Modifier>` identity for the fixed inspector and move it between adaptive layouts, rather than independently entering `inspector` at each branch. `rememberUpdatedState` provides the current inspector parameters/callbacks inside the moved content. No document, ASS Event, Undo stack, ToolInstance binding, renderer clock or saved camera geometry is modified.

The existing connected rotation test retains strict text-content assertions after each stage, plus an explicit check that resizing did not change the `workspaceSessionId`. This distinguishes a layout reparenting defect from a project-session reset.

## Evidence boundary

The mechanism is strongly suggested by source structure and runtime stage, but this patch is **not confirmed** until a new emulator result passes. Also, the last exact-head emulator job reported 16 tests / 2 failures; it did not cover the entire earlier 120-test suite, so it must not be misrepresented as full regression success. Separate viewport-history and visual capture failures remain open.
