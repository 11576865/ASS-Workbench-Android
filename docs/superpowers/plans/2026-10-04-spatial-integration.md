# Spatial Workspace Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver a freely arranged Android workspace that retains real subtitle controls, permits transparent audio evidence over video, and keeps ordinary editing simple.

**Architecture:** Reuse EditorViewModel, WorkbenchPreview, WorkspaceToolInstance and the existing rod registry. Workspace presentation state owns camera, surface placement, background transparency and input capture; ASS parameters retain their existing preview/commit owner. Extract the current fixed Spatial presentation into a host that renders actual tools, not browser fixtures.

**Tech Stack:** Kotlin 2.4.10, Compose, AGP 9.4.0, existing libmpv/libass path, JUnit and Compose instrumented tests.

**Spec:** docs/superpowers/specs/2026-10-04-spatial-integration-design.md

## Global Constraints

- No forced fixed split layout, narrow video strip, mandatory layout mode or manual dependency graph.
- Click/focus does not automatically raise a layer. Raising is explicit.
- Workspace movement never edits ASS or enters document Undo.
- Preserve POSITION / SCALE / SHEAR / ROTATION and orbit-only behavior.
- Background transparency is separate from signal/control opacity and input capture.
- Real media views share the current media time by default.
- Defer arbitrary control extraction, nested regions and multiple independent clocks.
- Do not claim real spectrogram, renderer correctness or tablet usability from a browser fixture.

## Review Focus

- Selection changes during a rod gesture: retain original target or cancel safely; never retarget a write.
- Narrow viewport / IME: saved geometry survives projections and controls remain reachable.
- Transparent foreground: touching video must not hide the audio overlay or steal its controls.
- Source/track changes during audio analysis: stale tiles never replace current-source data.
- Restored old project snapshots: absent presentation fields receive safe defaults without changing ASS.

## Task 1: Stable layer activation and lightweight surface chrome

Files: modify WorkspaceSurface.kt, WorkbenchSurfaceController.kt and WorkspaceSurfaceHost.kt under app/src/main/kotlin/io/github/assworkbench/app/ui/workspace; extend matching unit tests; add WorkspaceOverlayInstrumentedTest.kt.

Interfaces: add SurfacePresentation(backgroundAlpha: Float = 1f, inputMode: SurfaceInputMode = INTERACTIVE), where SurfaceInputMode is INTERACTIVE or PASSTHROUGH. WorkbenchSurfaceController.setPresentation(id: String, fallback: SurfaceGeometry, presentation: SurfacePresentation) stores layout-only state; activate(id: String, fallback: SurfaceGeometry) ensures existence without changing zOrder. Keep bringToFront for explicit raise.

- [ ] Test activation preserves zOrder; explicit raise changes it; presentation save/restore preserves alpha and input mode; legacy v1/v2 snapshots use defaults.
- [ ] Run targeted app unit tests and observe expected failures before implementation.
- [ ] Implement versioned surface-v3 persistence; validate finite alpha in 0..1; preserve v1/v2 decode.
- [ ] Remove unconditional bringToFront on content touch. Use a compact header with target identity and a more-actions menu for existing layout commands; preserve touch hit areas and semantics.
- [ ] Instrument overlapping surfaces: content activation does not change stacking; menu provides explicit raise and recall; passthrough content leaves a reachable header to restore interaction.
- [ ] Run tests, diff check and commit.

## Task 2: Spatial host with real, movable tool instances

Files: extract SpatialWorkspace / SpatialNode from ModernEditorScreen.kt into ui/workspace/SpatialWorkbenchHost.kt; create SpatialCamera.kt and SpatialCameraTest.kt; extend PresentationStateSmokeInstrumentedTest.kt.

Interfaces: SpatialCamera(x: Float, y: Float, scale: Float) exposes zoomAt(anchorX: Float, anchorY: Float, nextScale: Float): SpatialCamera and pan(dx: Float, dy: Float): SpatialCamera. World coordinates use dp; screen mapping explicitly applies density. Extend surface host with a world placement policy while keeping existing screen-constrained hosts.

- [ ] Test touch-anchor invariance at nonunit scale; move world nodes outside viewport without clamping; screen-constrained old hosts retain projection behavior.
- [ ] Implement world placement and camera mapping; reject nonfinite input; do not remove existing viewport checks from old presentations.
- [ ] Render WorkbenchPreview and actual WorkspaceToolInstance content; replace hardcoded three-node positions with saveable user geometry and a movable initial arrangement.
- [ ] Add recall/fit navigation and compact overview representations. Parameter controls keep readable hit areas in edit presentation.
- [ ] Disable camera capture while a document parameter gesture owns input; preserve local preview zoom as separate state.
- [ ] Verify presentation switching/restoration retains document, selection, draft and history; commit.

## Task 3: Position editing through the spatial preview

Files: ModernEditorScreen.kt, VideoPreview.kt, interaction/WorkspaceInteractionOverlay.kt and existing RodInteractionInstrumentedTest.kt / EditorRegressionInstrumentedTest.kt.

Interfaces: reuse WorkbenchPreview.positionEditEventId, previewEventPosition(id, x, y), setEventPosition(id, x, y) and existing cancel-preview callback; no second parameter store. If a callback can currently retarget via latest selection, capture event ID and document generation at begin and validate them at preview/commit.

- [ ] Add a regression that opens POSITION from the spatial preview, drags its real rod, observes preview without committed text changes, commits once and undoes once.
- [ ] Test selection changes, deleted targets and cancellation during drag; test orbit-only modifies no ASS.
- [ ] Wire the active POSITION binding into SpatialWorkbenchHost. Project the preview anchors through actual video bounds and both observation transforms.
- [ ] Keep rod length/hit region in screen units, independent of camera scale; display live coordinate/unit/delta feedback from the same transient parameter state used by the slider.
- [ ] Verify moving tools and changing observation zoom preserve ASS values; existing scale/shear/rotation tests remain passing; commit.

## Task 4: Transparent real waveform overlay

Files: create ui/AudioEvidenceOverlay.kt; reuse WaveformEnvelope / WaveformViewportSampler and current playback/seek state; wire from SpatialWorkbenchHost.kt. Extend WorkspaceOverlayInstrumentedTest.kt.

Interfaces: AudioEvidenceOverlay accepts the current waveform envelope, visible time range, playhead, onSeek and SurfacePresentation. It creates no player or private media clock.

- [ ] Add a screenshot/interaction test placing the overlay over preview: background transparency reveals preview, signal and controls remain opaque, and the input-mode switch changes gesture recipient.
- [ ] Draw audio evidence on a transparent canvas; remove nested opaque backgrounds in overlay mode; keep ticks and controls legible with local backing, without blur over video.
- [ ] Provide one overlay action with useful defaults and optional alpha adjustment. Keep normal standalone audio view available.
- [ ] Ensure seek and +/- frame commands use the existing real media timebase; no average-fps substitute for VFR.
- [ ] Test restoration, hidden/recall and bright/dark video scenes; commit.

## Task 5: Real spectrogram data before claiming spectrum support

Files: create core/domain/.../SpectrogramTile.kt and SpectrogramAnalyzerTest.kt; app/.../SpectrogramLiteAnalyzer.kt and SpectrogramLiteState.kt; integrate with EditorState.kt / EditorViewModel.kt and AudioEvidenceOverlay.kt. Factor bounded PCM decode shared with WaveformLiteAnalyzer only as necessary.

Interfaces: SpectrogramTile(startMs: Long, hopMs: Double, sampleRate: Int, fftSize: Int, magnitudesDb: List<FloatArray>); analyzeSpectrogram(pcm: FloatArray, sampleRate: Int, fftSize: Int = 1024, hopSamples: Int = 256): SpectrogramTile. Decode by requested time tile, not unbounded whole-file PCM. Cache up to 8 tiles, at most 2 seconds per tile; invalidated by source and selected audio track.

- [ ] Test a 1 kHz tone produces a peak within one bin; silence remains finite and at the declared -80 dB floor; known PCM duration preserves time correspondence.
- [ ] Implement Hann-windowed FFT/STFT and bounded decode; report unavailable/analysis states rather than synthetic evidence.
- [ ] Test source/track changes cancel work and reject stale results; release codec/extractor in cancellation paths.
- [ ] Display real spectrogram tiles on the same time scale as waveform; expose signal opacity independently of background.
- [ ] Validate media fixtures and memory bounds; document evidence limitations; commit.

## Task 6: Default workspace and acceptance

Files: ModernEditorScreen.kt / UiVariantRegistry.kt as required by actual selector ownership; docs/WORKSPACE_MIGRATION_CHECKLIST.md; instrumented workspace regression tests.

- [ ] Only after preceding native checks pass, make spatial shell the startup environment; preserve old layouts as arrangements/compatibility presentations during migration.
- [ ] Replace blanket fullscreen commands with content-sensitive expansion: video/detail view, larger text editing region, compact parameters. Restore original geometry on return.
- [ ] Run the user's subtitle authoring acceptance scenario: video + waveform/spectrogram, seek +/- frame, timing, text, split/merge, roles, batch style, cue override, undo/redo.
- [ ] Record forced page changes, context reconstruction, layer misselection and unnecessary UI organization; unresolved failures remain explicit.
- [ ] Run Android CI, emulator regression and artifact-policy check. CI uses the repository's pinned Gradle setup (no checked-in wrapper).
- [ ] Create a draft implementation PR with actual validation evidence and limitations; perform inline review; record deduplicated UIGS Test/Observation intake without Canonical promotion.

## Execution status

Plan only. No product implementation or passing-test claim. The clean checkout is pinned to 55c4fafc57bf75a3576ef92791ff5e3c025c46ac. Execution recommendation: implement directly in this session, one task at a time. Native build dependencies are not installed in this workspace; compile/device checks must use the existing CI setup or a provisioned Android environment.
