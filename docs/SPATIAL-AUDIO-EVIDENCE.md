# Spatial waveform overlay: stable seeking and input routing

The production waveform used a gesture-start range for seek mapping but re-centered the displayed signal on every playback update. A drag could therefore move the waveform under the finger while still seeking against the old range. The viewport now uses the same captured range for sampling, endpoint labels, playhead projection and seeking until release or cancellation. Removing input handling or changing audio source also releases the capture. Outside a gesture, the waveform resumes following the shared playback position. Seeking does not enter ASS document history.

Pointer positions are mapped directly in the waveform's local coordinates; accumulating the first over-slop delta on top of the drag-start position is avoided. Signal and cursor strokes have a dark outline. Labels have local backing, while the signal canvas remains transparent and unblurred; no whole-window alpha is applied to signal or text.

## Verification and evidence boundaries

- Extracted the old playback-following range for a regression: at playback 13 s during a captured 6–14 s gesture, it incorrectly returned 9–17 s. The regression failed before capture handling and passed afterwards.
- Supplemental standalone Kotlin/JUnit: 177 tests passed, including all domain tests and the adjacent canvas/projection tests. Initial standalone attempts omitted kotlin-test dependencies and corpus resources; correcting the harness classpath resolved those errors without production changes.
- Three new connected Android tests use production InfiniteCanvasHost, InfiniteAudioEvidence and EditorViewModel. They cover range stability until release, range release on cancellation, and switching waveform input to a diagnostic lower surface and back while preserving layer order and document history.
- The lower test surface is a hit-test diagnostic, not VideoPreview. These tests do not prove video playback synchronization, decoded waveform accuracy, rendered transparency, contrast across moving footage, tablet touch usability, or the full subtitle-authoring workflow.
- No local Android SDK, Gradle installation or emulator is available. Android compilation and connected execution remain Pending CI at submission.

## Next acceptance

Run production ModernEditorScreen with moving media and its analyzed audio: verify video time and waveform cursor together, transparency across light/dark frames, lower-video and rod input after pass-through, and no need to move the overlay aside. Preserve the explicit boundary between numeric/input regression and real-media visual evidence. STFT spectrogram remains deferred.
