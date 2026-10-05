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
Position XY, Scale XY and Shear XY are implemented in the subsequent slices below.
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
user. UI-240 109/110 remain Partial: arbitrary drag extraction and composition are
not claimed complete. Scale/Shear are implemented in the next slice.


## Scale / Shear live projection closure

The Position tool now exposes “拆出缩放 X/Y” and “拆出错切 X/Y”. Both retain
WorkspaceState v4 instance identity and FollowFocus/PinnedEvent binding, and switch
between NUMBER_PAIR, SLIDER_PAIR and XY_PAD without changing parameter identity.
X/Y are independent in the extracted control. Canonical bounds are Scale 1..1000%
and Shear -10..10. Shear pad coordinates are translated from that signed domain,
so zero is at the center and negative values remain valid. Accessibility exposes
actual parameter values, with 1% scale and 0.05 shear increments.

Preview uses geometry:<Event>:<projection>. Input and commit are gated by the
starting workspace session, resolved target and matching preview owner. Numeric
Apply and pad/slider release use existing canonical setters; Cancel, disposal and
presentation switching clear only the projection-owned preview. Material Slider
nodes are keyed by session/owner/presentation to prevent an old drag continuing
against a replacement Event or project. DragInteraction.Cancel clears the draft
and preview without applying. Foreign preview takeover cannot be cleared by this
projection's Cancel.

Validation in this slice: supplemental Kotlin/JUnit execution passed all 241 tests
(209 domain plus 32 parameter/anchor tests). The scale range mutation 1..1000 →
0..1000 failed two assertions (invalid zero draft accepted and shifted pad
coordinates); the production range passed the same tests. Seven connected
regressions are added for scale/shear paired Apply and Undo, negative shear pad
cancel/release, scale slider release, foreign takeover, focus switch mid-drag and
slider cancellation. They have not run locally: Android SDK/Gradle is unavailable.
Exact-revision Android compilation and connected validation remain Pending CI.

The emulator runner executes the projection classes independently before the full
suite and prints each run's XML, logcat tails and crash buffer immediately. Both
runs remain mandatory gates; three runner status-propagation scenarios passed.
Previous revision 8095cea Android/native checks passed, but emulator run
37232386964 stopped at EditorRegressionInstrumentedTest.
inspectorDraftSurvivesToolSwitchAndRotation with an empty failure before the
projection classes ran. This slice improves evidence collection; it does not
claim that unresolved failure has been fixed.

The four declared descriptor families now have live renderers. Arbitrary
drag-to-extract, user-defined composition, graph relations and nested regions
remain outside this approved slice. UI-240 109/110 remain Partial. Real-device
acceptance belongs to the user; these changes do not promote the prerelease to a
stable official release.
## Drag extraction and typed intent routing follow-up

- Rotation Z supports long-press drag extraction from the Position tool; release creates the persisted workspace projection while a normal click remains a fallback.
- Extraction changes WorkspaceState only and must not create an ASS document Undo entry.
- NUMBER, SLIDER and the current-main ANGLE_DIAL all dispatch typed `WorkspaceParameterIntent` values through `WorkspaceParameterIntentRouter`.
- The router validates descriptor/binding/value/revision semantics first, then delegates PREVIEW / COMMIT / CANCEL to the existing EditorViewModel geometry mutation boundary.
- The current-main Angle Dial implementation remains authoritative for dial motion, multi-turn behavior, cancellation and accessibility semantics; this follow-up does not replace it with a second dial implementation.

Items 109/110 remain Partial because arbitrary drag extraction and user-defined composition are not yet connected. Vector projections are provided by the Position/Scale/Shear slice above; Rotation Z retains current main's typed intent routing.



## 2026-10-05 integration evidence

Revision 6b33ee5 passed Android CI (37274834480), Fontconfig native probe
(37274834459), and Android Emulator Regression (37274834432). The complete
instrumentation XML reports 84 tests, zero failures/errors/skips, including all
seven transform tests and inspectorDraftSurvivesToolSwitchAndRotation. The prior
empty failure did not reproduce; its root cause remains unconfirmed. A passing
later run is not evidence of a specific repair.

This branch integrates main ce0d905 (PR #118): Rotation Z long-press drag extraction
and typed intent routing coexist with Position XY / Scale XY / Shear XY renderers.
Both independent doc additions are retained. The integrated revision's Android
checks remain Pending CI; prior 6b33ee5 results are not attributed to that revision.


Focused-run correction: the 6b33ee5 first-run XML contained only the three Rotation
Z tests despite the comma-separated three-class filter. Position/Transform were
executed successfully only in the complete 84-test run. The runner now invokes
all three classes separately, reporting each immediately, then always executes
the full suite. The runner regression first failed with five assertions and now
passes both tests across five execution scenarios. Exact-revision Android evidence
for the new sequence remains Pending CI.
