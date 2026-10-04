# Workspace Parameter Descriptor / Intent Contract

This slice establishes the semantic boundary required before arbitrary controls are
extracted from existing tools into independent infinite-canvas surfaces.

## Contract

A **descriptor** identifies what is edited. It owns a stable semantic key, source
tool, context, value shape and the set of presentations allowed for that value.

A **presentation** identifies only how a descriptor is shown. Numeric fields,
sliders, angle dials and XY pads do not create different parameter identities.

An **address** combines one projection instance with the existing WorkspaceBinding.
Event parameters therefore continue to use FollowFocus or PinnedEvent instead of
mutating global focus to reach a target.

An **intent** is PREVIEW, COMMIT or CANCEL plus a monotonic revision. The contract
validates descriptor existence, binding compatibility, arity and finite values.
It intentionally does not call EditorViewModel yet.

Initial descriptors cover Position XY, Rotation Z, Scale XY and Shear XY because
those parameters already have stable preview/commit owners in the Position tool.

## Deliberate boundary

This is the contract-first slice for UI-240 items 109/110. It does **not** yet:

- add drag-to-extract UI;
- persist extracted parameter instances in WorkspaceState;
- route intents into EditorViewModel;
- claim that arbitrary controls can already be composed;
- mark either 240 item complete.

Those steps must build on the descriptor/intent identity rather than coupling a
new canvas control directly to an existing pane's local draft state.


## Live projection slice

The first consumer is Rotation Z:

- the Position tool can create an independent canvas projection without changing
  the descriptor or Event binding;
- projections are persisted in WorkspaceState schema v4 and coexist with v1-v3
  restoration;
- one descriptor can have multiple independent projection instances;
- live NUMBER, SLIDER and ANGLE_DIAL presentations read the same effective Rotation Z;
- preview ownership is extended with the projection id
  (`geometry:<event>:<projection>`) so an extracted control is observable as an
  external preview by the original Position pane instead of impersonating its local draft;
- commit still uses EditorViewModel's canonical document mutation and therefore
  creates normal ASS Undo history.

The extraction affordance is currently explicit ("拆出旋转控件"), not drag-to-extract.
Angle Dial now has a live renderer: relative counterclockwise motion preserves complete
turns and unwraps the ±180° boundary. Release commits once; cancellation clears only
the projection-owned preview. Entering the center dead zone cancels the drag instead of publishing an unstable direction.
Accessibility actions adjust one degree through the same document authority.
Android dial drag/Undo and cancellation regressions remain Pending CI.
XY Pad and broader parameter families remain separate follow-ups.
Items 109/110 therefore remain Partial.


## Angle Dial validation slice

Supplemental Kotlin/JUnit execution: 209 domain tests and 14 parameter/model
tests passed. The ±180° regression fails if angle unwrapping is removed
(expected 42°, incorrect −318°), and passes in the production implementation.
Two connected regressions cover drag preview/release/one Undo and cancellation.
They are Pending CI; no local Android compile or user-device acceptance is claimed.
Gesture callbacks retain their starting session/Event, and cancellation only
clears that session's matching owner. Source artifact/whitespace checks passed.


## Position X/Y live projection

Position's Placement section now exposes “拆出位置控件”. Position XY supports
XY_PAD and NUMBER_PAIR presentations with persisted identity and FollowFocus or
PinnedEvent binding. Relative pointer displacement uses the actual board dimensions
and separate ASS PlayResX/PlayResY scales. Grabbing does not relocate the subtitle;
bounds follow the existing EditorViewModel position-edit contract (0..PlayRes).
Clamping does not accumulate drift, and returning to the starting coordinate cancels
rather than adding an inherited-position override.

Static anchor resolution reuses preview discovery's existing alignment/margin
logic. This is an approximate inherited anchor, not libass glyph measurement.
Explicit static positions are supported; motion, conflicting positions, invalid
script dimensions and late anchor tags fail closed. A position projection never
silently flattens an existing move path.

Preview ownership uses geometry:<Event>:<projection>, with original-session and
binding checks on writes and cleanup. Numeric edits are read-only during a foreign
preview. Release/Apply creates ordinary ASS document Undo; cancellation only clears
the matching preview. Presentation changes retain descriptor and binding.

Supplemental validation: 209 domain Jupiter and 29 parameter/anchor Vintage tests
passed (238 total). Three connected tests cover pinned paired-number editing and
one Undo, pad cancel/release/Undo, and foreign preview takeover. Android compile and
connected/native validation are Pending CI. Real-device acceptance belongs to the
user. UI-240 109/110 remain Partial: Scale/Shear projections and arbitrary drag
extraction are not claimed complete.
