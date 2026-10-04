# Spatial position editing: live parameter feedback

The canvas preview already publishes transient position changes through the native rod and EditorViewModel. PositionPane previously read its effective values and X/Y fields only from the committed document, so its displayed coordinates could lag behind the preview until release.

GeometryParameterDisplay now selects the preview Event only when its owner is `geometry:<target ID>` and the target still exists in the committed document. The position tool projects those coordinates and effective values without copying them into its text drafts or calling a mutation callback. During the preview, X/Y are read-only and Apply is disabled. Cancel reveals the original input draft; a committed document change resets the drafts through the existing Event-keyed state. The header identifies the target and temporary versus committed state.

## Verification

- A regression for transient display first failed against committed-only resolution, then passed after owner-validated projection was implemented.
- Standalone Kotlin 2.2.0 / JUnit run: all 163 domain tests and 5 display-projection tests passed (168 total). This is supplemental evidence; the repository's pinned Android CI remains authoritative for Compose compilation.
- Added editor instrumentation: transient X/Y display, cancellation restores typed draft, and preview adds no document Undo entry.
- Added native instrumentation: production VideoPreview, production InfiniteCanvasHost, production rod registry and EditorViewModel preview/commit/Undo; one released gesture is one history step; changing target mid-drag cancels rather than writing either Event.
- The native fixture shares main's deterministic PNG media input, avoiding hosted-emulator H.264 decoder liveness failures. Its canvas-host harness exercises the actual media/rod/editor chain; it does not substitute for real moving-video playback or full ModernEditorScreen workflow acceptance.
- Android compilation and the new connected tests are Pending CI at submission. No local Android SDK/emulator is provisioned.

## Boundaries

This slice updates live position coordinates and the effective readout. It does not synchronize every rotation/scale/shear draft, add a position slider that did not previously exist, or implement spectrogram analysis. Numeric tests establish parameter-state behavior, not libass pixel correctness or tablet usability. Native media fixtures and temporary test output are not committed as build artifacts.

## Transform feedback follow-up (2026-10-04)

The Transform section now projects the active geometry preview for rotation X/Y/Z,
scale X/Y and shear X/Y. External rod previews temporarily make these controls
read-only; cancelling the preview restores the independent typed drafts, including
invalid/unsubmitted input. The pane's own sliders remain interactive and commit
one document edit at release. This does not alter canvas layout history.

Geometry publications advance an observable revision even when ASS values are
unchanged. This avoids StateFlow equality conflation hiding an equal-valued writer
takeover. The pane records its parameter and revision, and each debounced numeric
edit captures its own revision before the delay. An obsolete timer cannot commit
another control's preview. External takeover cancels automatic commits while
retaining draft text; editing the restored draft starts a new submission.

This is preview provenance for the existing geometry path, not a complete global
edit-session fence. A geometry no-op that produces no transient document does not
lock the controls. Origin/move/clip retain their existing editing behavior.

Validation: seven standalone app projection tests passed under JUnit 4/Vintage.
The complete 168-test domain suite passed under its configured Jupiter engine.
Four connected regressions cover all seven projected controls/draft restoration,
slider single-Undo behavior, equal-valued publication revisions and cancellation
of a pending valid numeric draft after an equal-valued external takeover. Android
compilation and connected execution are Pending CI. Previous revision 91087d9e
passed Android CI, emulator regressions and the Fontconfig native probe.
