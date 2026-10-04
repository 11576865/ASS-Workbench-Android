# Infinite-canvas spectrogram v1

## Functional scope

The audio evidence surface now switches between waveform and a real PCM-derived spectrogram. Both modes use EditorViewModel's shared playback position, the same seek mapping and frozen drag viewport. The evidence surface retains movable/resizable geometry, background alpha and explicit signal-area touch pass-through. In pass-through mode the internal mode/retry buttons are replaced with passive text; restore audio input from the surface menu to change modes.

Selecting spectrogram starts background decoding of the current media/audio track with MediaExtractor/MediaCodec, reusing the existing waveform decoder and downmixing channel samples to mono. It does not transform waveform peak buckets into a spectrum. A request token, project session, media URI and selected track guard completion. Switching project/media/track cancels old analysis; reselecting the same track is a no-op. Errors remain visible with explicit retry. The generated data survives mode switching in EditorState but does not enter project document history.

## Analysis and rendering

- Streaming 2048-sample Hann-window FFT; nominal 40 ms frame hop.
- 128 linear frequency bands over 0–min(8000 Hz, Nyquist). Each displayed band uses maximum FFT magnitude in its frequency interval.
- Magnitudes are window-normalized, converted to −80..0 dBFS and stored as bytes. These are relative digital amplitudes, not calibrated sound-pressure measurements.
- Frame-center timestamps come from decoded PCM timestamps. Gaps reset the window rather than bridging absent audio; rendering leaves missing-frame intervals transparent.
- Silence is transparent. Stronger bins carry color and up to 80% alpha; no opaque spectrum field or lower-video blur.
- Viewport raster is bounded to 600×128 pixels, independent of the full timeline length. Full analysis has a 131072-frame limit (about 87 minutes at a 40 ms hop), with an explicit error on overflow.

## Small-screen repair

The failed prior smoke regression recalled a 650×190 world-space audio surface to a phone-fit scale. Its screen height could fall below its fixed-size labels and controls, leaving a zero-height waveform Canvas. Audio approach/expand now reserves up to 240 screen dp, bounded by viewport height, by increasing the saved world-space height. Width, position, layer and ASS document stay unchanged. Compact single-line status prevents long analysis errors from consuming the entire signal region. This is explicit expansion, not a hidden rendered/saved-size mismatch.

## Verification

- STFT regressions failed against the empty baseline, then passed: known 1 kHz tone peak, silence floor, nonzero timestamp, audio gap and memory cap.
- Audio expansion regression failed at 190 world dp and passed at 480 dp for a 0.5 camera scale; the expanded height persists.
- Supplemental Kotlin/JUnit: 183 tests passed across the domain, canvas, geometry projection and audio viewport suites. This is supplemental JVM evidence, not an Android build claim.
- New connected test creates a deterministic PCM WAV, invokes production media decode and STFT through the production audio mode, checks its tone frequency and seek behavior, and verifies same-track reselect does not clear it or create document Undo. Updated routing test verifies mode buttons disappear in pass-through mode. These connected tests remain Pending CI at submission.
- Static independent review found the decoder completion and same-track cancellation defects; both were corrected before submission.

## Remaining boundaries

Android compilation, WAV decoder availability and connected tests require CI. Previous revision 4d06fb13 passed Android CI and Fontconfig native probe; emulator regression was 47/48, with the audio recall display assertion failing. No claim that this exact revision is green before CI.

This v1 analyzes on demand and retains one result in memory. It does not yet provide a spectrogram disk cache, tiled/incremental results, partial-range analysis, adjustable FFT/band settings or independent clocks. Switching away from spectrogram keeps the analysis running for reuse; switching source or track cancels it. Long-media analysis speed, playback synchronization against moving video, light/dark scene readability, device memory behavior and the complete tablet subtitle workflow remain unvalidated.

UIGS intake belongs to the existing native workspace Observation/Test family (#6) and the transparent spectrum acceptance family (#5); no Canonical promotion.
