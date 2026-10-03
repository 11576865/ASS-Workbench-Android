# Infinite Canvas v1

This native Android version replaces the fixed-position Spatial presentation with a freely arranged workspace. New sessions use it by default; persisted FIXED and other presentation names remain compatible. The debug regression host explicitly starts in FIXED so existing tests do not silently change their subject.

- Pan/pinch on blank canvas; zoom is anchored at the gesture centroid.
- Drag a tool header, resize from the corner, recall hidden/offscreen tools.
- Video, subtitle navigation, a real waveform overlay and actual open tool instances coexist.
- Opening the position tool connects the native rod registry and existing preview/commit callbacks.
- Each surface offers background alpha and explicit raise; selecting content does not reorder layers.
- The waveform's input mode can pass touches to video while keeping its header reachable. Signal and labels do not inherit background opacity.
- Layout candidates are transient until drag end. The saved scene is carried alongside legacy surfaces in controller/project snapshots; no ASS values or Undo entries are stored there.

This iteration follows the user's request to directly build an infinite-canvas version. It deliberately defers arbitrary control extraction, user-authored dependency graphs, nested regions and independent clocks. It removes blanket fullscreen expansion; approach/expand changes observation while preserving node geometry.

Evidence limits: no true spectrogram analyzer is introduced; the audio evidence is the existing decoded waveform. Transparent surfaces do not guarantee every legacy tool's internal background is transparent; the waveform overlay is built for transparent composition. Playback/resource virtualization, real tablet/IME verification and the complete authoring acceptance loop require further measurement. Approximate infinity refers to unbounded world placement within finite Float precision, not unlimited hardware resources.

Verification is recorded in the PR. Pure model/registry tests can run with standalone Kotlin+JUnit; complete Compose compilation and instrumentation use the repository's Android CI setup.
