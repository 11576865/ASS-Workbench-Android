# Main and spatial navigation — bounded UI iteration

Base: redesign/infinite-canvas-workbench-v2 at 6edd6a283f3695506bd2b0e4673cb20ac16540d9 (draft PR #138).

## Result

- Fixed workspace object/list navigation now shows the actual Subtitle Navigation action rather than stale inspector-group tools. Global tool commands, compact list/editor chips and closing the Text inspector update the requested group consistently.
- Every catalog group keeps its declared tools accessible, including the workspace directory. Group changes reset only the local tool strip scroll state.
- Preview density stays outside the scrolling fixed tool strip.
- Birdseye stays outside the spatial action scroller so a 320 dp board always has a visible route back to hidden or distant tools. Wide-screen toolbar width is capped at 900 dp.

## Verification

- 31 pure JVM catalog, adaptive-policy and infinite-canvas model tests pass using Kotlin CLI and a reflection runner over real JUnit assertions.
- Replaying the previous subtitle/directory exclusion against the new catalog regressions fails as expected. This is an exclusion-regression replay, not a claim of a complete Android red/green cycle.
- Added connected regressions for list-group/external-command/Text-close consistency, preview accessibility and narrow-board birdseye recall without changing world geometry.
- Artifact policy and git diff whitespace checks pass.
- Reviewer found a Text-close navigation path; it is corrected and added to the connected regression.

Android compilation, connected tests, native renderer, visual/large-font/IME and real-device touch acceptance remain Pending. No Android SDK is installed in the local environment. This iteration does not close the 240-item ledger or establish project completion.

## Next work unit

Read exact-head Android/emulator results before further UI changes. If clear, evaluate the main and spatial toolbar at phone/tablet widths, large fonts and IME, then address the next demonstrated issue. Keep PR #138 Draft until broader UI acceptance is established.
