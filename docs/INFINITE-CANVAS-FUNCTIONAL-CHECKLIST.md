# Native infinite canvas v1: functional closure

The native v1 implementation is complete for the scope below. Automated Android
validation remains asynchronous; real device, moving-media and authoring acceptance
are owned by the user. Implementation, CI validation and user acceptance are three
separate states. This document does not declare all 240 UI requirements complete.

| Function | Implemented behavior | Evidence |
| --- | --- | --- |
| Canvas navigation | World-space pan, centroid-anchored zoom, overview, explicit approach and recall | JVM camera/model tests; connected presentation tests |
| Real surfaces | Video/ASS renderer, subtitle navigation and existing production tool contents coexist | Existing Android/native regression paths |
| Surface geometry | Header drag, corner resize, hidden/offscreen recall, persistent layout lock | JVM model tests; connected measurement/lock tests |
| Tool access | Direct tool picker; explicit opening reveals and approaches a hidden/offscreen instance | New connected lifecycle regression |
| Object binding | Style/Position can pin current Event, unpin and display an unresolved target without fallback | Workspace binding tests; new lifecycle regression |
| Multiple instances | Declared-capability duplication with current binding or FollowFocus; independent instances | Workspace model tests; new lifecycle regression |
| Tool lifecycle | Hide retains the instance/layout; close removes the instance and obsolete scene entry | JVM pruning test; new lifecycle regression |
| Layer behavior | Only explicit Raise changes existing z-order; selection/open/recall do not raise existing nodes | JVM raise/model invariants |
| Transparent audio | Real PCM waveform/STFT, independent background alpha, explicit audio input pass-through | Spectrum/domain tests; connected audio regressions |
| Seeking | Audio/video share media clock; seeking uses a frozen displayed range during drag | JVM range tests; connected seek regressions |
| Geometry editing | Native rods and read-only live position/move/origin/rotation/scale/shear/clip projection | JVM semantics/provenance tests; connected geometry regressions |
| Draft ownership | External preview preserves drafts; obsolete delayed commits and unowned-pane cleanup are rejected | JVM lease tests; connected takeover/close regressions |
| Document history | Canvas/layout/binding/lifecycle operations do not create ASS Undo entries | Presentation invariants; connected lifecycle regression |
| Persistence | Workspace instances/bindings and infinite camera/node scene travel with `.asswb` snapshots | Existing project/controller tests; JVM round trips |
| Compatibility | `infinite-v2` adds layout lock; `infinite-v1` nine-field rows remain readable | New persistence regression |
| Gesture arbitration | Workspace mutation controls are disabled during an owned native rod gesture | Host guards and existing rod paths |

Layout lock freezes world position and dimensions, while approach/recall still
changes observation. Closing a tool differs from hiding it: a closed sibling's
identifier can safely be reused without inheriting its stale hidden geometry.
The direct picker reuses an existing primary and preserves its pinned binding.
Only Style/Position advertise duplication/pinned Event support; resource/player
owners are not silently cloned.

## Automated evidence at submission

- Supplemental JVM run: **214 passed**, no failures/skips on integrated latest-main/Timeline Dock tree:
  178 domain Jupiter, 12 parameter projection/provenance Vintage and
  24 canvas/workspace/Timeline Dock Vintage.
- Four closure regressions first failed on hidden-primary reopening, locked
  movement, lock persistence and closed-node retention, then passed after fixes.
- New connected tests: lifecycle pin/follow-copy/close/hide/reopen/history;
  locked drag/resize/unlock persistence; closing an unowned Position pane preserves
  another writer's preview; switching a pane target clears its owned pending preview. These tests are **Pending CI**, not locally executed.
- Artifact policy and whitespace checks passed. Static review found no
  Critical/Important issue in the closure implementation.
- Integrated baseline 3e383231 passed Android CI and Fontconfig. Emulator
  regression failed in transformFieldsProjectExternalPreviewAndRestoreDrafts:
  a Scale field was not yet composed by LazyColumn, so performScrollTo could not
  locate it. The test now scrolls the parameter list to the target tag before
  editing/asserting it. Its rerun is Pending CI. Other move/origin/rectangle,
  audio and native rod regressions passed in that baseline.

The persistent Timeline Dock and current Style inheritance fixes from the
Workspace-authority branch are retained by this change.

There is no local Android SDK/emulator in this execution environment. The current
Android build and complete module/connected suites run through the existing CI.
No user-device or moving-media acceptance result is inferred from JVM tests.

## Separate extensions

The UI constitution identifies parameter extraction/composition as a future
extension requiring its own descriptor/intent contract. Arbitrary control
extraction, nested regions, user-authored relation graphs, independent clocks,
full global edit-session fencing, disk/tiled spectrum cache and adjustable STFT
settings are outside this native v1 closure. Their omission is explicit rather
than counted as implementation or a failed real-device acceptance test.

## User acceptance entry

Use the new `＋ 工具` menu to open a tool. In Style/Position's header menu, pin an
Event or create a second instance, then switch focus to compare bindings. `收回`
hides and retains a node; `关闭工具` removes its instance. Reopen a hidden primary
from the picker or use `召回`. `锁定布局` blocks moving/resizing; `靠近 / 展开`
still navigates to it. These actions retain document Undo for subtitle edits.

Real video/transparent audio composition, OEM touch behavior, tablet/IME use,
performance and the complete subtitle-making workflow are left to user acceptance.
The PR's pending CI result determines automated build/test validation separately.
