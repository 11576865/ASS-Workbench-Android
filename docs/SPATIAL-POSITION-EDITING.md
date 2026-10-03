# Spatial position editing: live parameter feedback

The canvas preview already publishes transient position changes through the native rod and EditorViewModel. PositionPane previously read its effective values and X/Y fields only from the committed document, so its displayed coordinates could lag behind the preview until release.

GeometryParameterDisplay now selects the preview Event only when its owner is `geometry:<target ID>` and the target still exists in the committed document. The position tool projects those coordinates and effective values without copying them into its text drafts or calling a mutation callback. During the preview, X/Y are read-only and Apply is disabled. Cancel reveals the original input draft; a committed document change resets the drafts through the existing Event-keyed state. The header identifies the target and temporary versus committed state.

## Verification

- A regression for transient display first failed against committed-only resolution, then passed after owner-validated projection was implemented.
- Standalone Kotlin 2.2.0 / JUnit run: all 163 domain tests and 5 display-projection tests passed (168 total). This is supplemental evidence; the repository's pinned Android CI remains authoritative for Compose compilation.
- Added editor instrumentation: transient X/Y display, cancellation restores typed draft, and preview adds no document Undo entry.
- Added native instrumentation: production VideoPreview, production InfiniteCanvasHost, production rod registry and EditorViewModel preview/commit/Undo; one released gesture is one history step; changing target mid-drag cancels rather than writing either Event.
- The native fixture shares the existing deterministic MP4. Its canvas-host harness exercises the actual media/rod/editor chain; it does not substitute for full ModernEditorScreen workflow acceptance.
- Android compilation and the new connected tests are Pending CI at submission. No local Android SDK/emulator is provisioned.

## Boundaries

This slice updates live position coordinates and the effective readout. It does not synchronize every rotation/scale/shear draft, add a position slider that did not previously exist, or implement spectrogram analysis. Numeric tests establish parameter-state behavior, not libass pixel correctness or tablet usability. Native media fixtures and temporary test output are not committed as build artifacts.
