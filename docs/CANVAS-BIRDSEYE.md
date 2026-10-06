# Clickable native canvas birdseye

Intent: recover an arbitrary existing tool or preview without having to drag the
camera until it happens to enter the viewport. This is a bounded navigation slice
of UI-240006, separate from document editing and parameter composition.

The toolbar's `鸟瞰` opens a square map and a scrollable tool-name list. The map
includes the current entries' saved nodes, including hidden ones; stale rows with
no live entry are excluded. A grey marker/list status identifies a hidden node.
Clicking a marker approaches its node; overlapping markers choose the nearest
rectangle, then the highest layer. The name list provides normal-size touch and
accessibility targets even when markers overlap or become sub-pixel at extreme
distances. Selecting a hidden node reveals it through the existing focus path.

Map coordinates use Double for world bounds before conversion to normalized
Float coordinates, avoiding overflow when opposite finite Float world positions
are combined. This changes neither the camera's zoom range nor saved geometry.
The existing `总览` fit command retains its minimum camera scale; birdseye remains
usable when nodes are too far apart for that fit command to show all of them.

Native rod ownership disables navigation from both map and list. Callbacks resolve
the current node before focus; the map callback reads the latest focus handler.
An open birdseye and the explicit detailed-node override are keyed by workspace
session, preventing one project's transient navigation mode from leaking into a
replacement project. Navigation does not call document Undo/Redo or parameter
editing paths. Audio recall retains its existing expansion behavior.

Evidence at submission:

- Five map tests first failed against an empty implementation, then passed:
  far/hidden nodes, extreme finite coordinates, overlapping layers/empty hit,
  tiny-marker neighborhoods and invalid/empty geometry.
- Supplementary JVM suite: 257 tests passed, zero failures/skips, including the
  five new map tests and seven existing infinite camera/node tests.
- Five connected regressions are added for hidden/far recall, map-marker focus,
  rod ownership, session replacement, and production document/Undo/Redo invariants.
  Android compile and connected execution are Pending CI; no local Android SDK.
- Parent d5524ab passed Android CI 37294144536, native probe 37294144547 and
  emulator run 37294144527. Complete XML: 97 tests, zero failures/errors/skips.
  PR #130 merged as f75272ab3336646ad00f37cd16f9d73e3db966bc. These are parent
  results, not verification of this new slice.

Device touch/IME, visual quality and full subtitle-authoring acceptance remain
separate. No stable release, nested regions, arbitrary composition or global
edit-session fencing is declared complete.
