# ASS Workbench Android 0.27 — Hardening and Failure Matrix

Date: 2026-09-30  
Functional freeze baseline: `46057771aea2f1d7437e6d919d35c9dbfbf5eda7`  
Target: publish 0.27.0 after the automated release gate, then validate that exact build on real devices

This document is the post-feature-freeze worklist. It is deliberately not a feature roadmap. A change belongs here only when it reduces crash risk, data-loss risk, semantic drift, lifecycle failure, performance regression, compatibility ambiguity, or release/build ambiguity.

## Current native baseline

The pinned libmpvKt v0.3.0 dependency set used by the Fontconfig production workflow currently declares:

- mpv 0.41.0
- FFmpeg 9.0.1
- libass 0.17.5
- HarfBuzz 14.4.0
- FreeType 2.14.3
- FriBidi 1.0.16
- libplacebo 7.360.1
- Android NDK 29.0.14206865

These exact versions matter: hardening must be based on the renderer actually shipped by ASS Workbench, not on generic assumptions about “mpv” or “libass”.

## External failure cases reviewed at freeze entry

### H1 — Extreme ASS numeric values can enter undefined renderer behaviour

libass issue #948, opened 2026-08-16 against libass 0.17.5, reports deterministic UBSan traps from extreme numeric override values including rotation and shear, with the affected path also discussing position/scale conversion.

Source: https://github.com/libass/libass/issues/948

ASS Workbench impact:

- the production dependency is the same libass release family;
- Raw ASS intentionally preserves arbitrary user input;
- structured Geometry/Transform controls must never become a generator for pathological values;
- imported Raw ASS must remain preservable even if previewing it is considered unsafe.

Implemented hardening:

- `AssRendererRiskAnalyzer` now detects extreme geometry values before they are handed to native preview;
- QC exposes these as `RENDERER_RISK` errors;
- authoritative preview fails closed for a risky render document while the canonical Raw ASS remains unchanged and saveable;
- an already-attached external subtitle is removed when the document becomes risky, preventing stale “safe” subtitle frames from masquerading as the current text;
- the preview surface explains that rendering is suspended and automatically re-attaches the subtitle when the risky input is corrected.

Implemented structured-input bounds:

- Position/Move reject non-finite values and stay inside the Script Resolution canvas;
- Rotation rejects non-finite values and is bounded to ±3600°;
- Scale rejects non-finite values and is bounded to 1–1000%;
- Shear rejects non-finite values and is bounded to ±10;
- structured Origin and rectangular Clip reject non-finite values and use a ±100000 coordinate ceiling;
- these bounds apply only to structured edits. Existing Raw ASS is never silently rewritten to match them.

Still required:

- exercise the preview guard and recovery path on the physical Android target;
- review whether the structured Origin/Clip ceiling is too restrictive for legitimate corpus cases;
- never “fix” Raw ASS by silently clamping stored text.

### H2 — Pathological Drawing input is a native crash / memory-risk domain even though Drawing editing is deferred

libass issue #931 reports signed integer overflow from extreme `\\p` vector coordinates. Issue #945 reports unbounded token allocation for very large ASS drawings and explicitly frames untrusted/downloaded subtitle input as an OOM/process-crash risk.

Sources:

- https://github.com/libass/libass/issues/931
- https://github.com/libass/libass/issues/945

ASS Workbench impact:

- deferring the Drawing editor does **not** remove Drawing from the input surface;
- Raw ASS and unknown syntax preservation mean external `\\p` content still reaches the renderer;
- lossless preservation and renderer safety therefore have to be treated as separate requirements.

Implemented hardening:

- the renderer-risk corpus covers extreme Drawing coordinates and oversized vector-clip/Drawing payloads;
- `\\p` Drawing text and vector `\\clip/\\iclip` payloads share the same conservative preflight;
- risky payloads suspend native preview without rewriting the original Event text;
- the preflight itself stops tokenizing as soon as its cumulative character or numeric-token budget is exceeded, so the safety scanner does not reproduce the unbounded-work pattern it is meant to guard against.

Still required:

- profile parse/editor memory independently from libass renderer memory;
- verify thresholds against a real-world complex typesetting corpus and physical device;
- preserve the original drawing bytes/text even when preview is refused.

### H3 — Complex moving subtitles can remain expensive frame after frame

libass issue #844 shows a long horizontally moving line causing sustained dropped frames, with event structure materially affecting cost.

Source: https://github.com/libass/libass/issues/844

ASS Workbench impact:

- animation/geometry scrubbing must not add avoidable Compose or document-rebuild work on top of inherently expensive libass frames;
- performance tests need at least one long `\\move` / animation fixture rather than only ordinary dialogue;
- a slow renderer case must not create dozens of history commits or trigger renderer recreation per pointer movement.

Transient preview publication hardening:

- ASS serialization/file publication runs off the Compose main thread;
- transient preview reloads are rate-limited to roughly 30 Hz instead of issuing one `sub-reload` per pointer event;
- every render request receives a monotonic generation, so stale/cancelled requests cannot publish an older `current.ass` over a newer preview;
- temporary files are unique per generation and cleaned after success/cancellation, avoiding cross-request `current.ass.tmp` collisions.

### H4 — Font attachment media types have a defined Matroska compatibility surface

The Matroska attachment guidance lists RFC font media types including `font/ttf`, `font/otf`, `font/sfnt` and `font/collection`, plus legacy types seen in existing files. Writers should emit valid font media types; readers may also encounter legacy or octet-stream attachments.

Source: https://www.matroska.org/technical/attachments.html

ASS Workbench impact:

- the new writer emits `font/ttf` and `font/otf`, which is aligned with the documented writer guidance;
- incoming attachment recognition accepts RFC `font/sfnt` in addition to TTF/OTF and legacy media types; ambiguous filenames can be imported when the payload has a supported single-face sfnt signature;
- TrueType/OpenType collections (`ttcf`, usually TTC/OTC) remain explicit unsupported input for 0.27 because the metadata/glyph layer does not yet model face selection;
- attachments rejected by bounded reader limits are now counted explicitly instead of disappearing from MKV diagnostics;
- unsupported attachment types are never a license to delete them during MKV write-back.

### H5 — Waveform decoding must remain lifecycle-safe and optional

Android's MediaCodec documentation requires codecs to be released when no longer used and distinguishes recoverable, transient and fatal codec errors. MediaExtractor likewise requires explicit release. Codec/content/vendor failures are expected operating conditions, not exceptional reasons to corrupt the editor state.

Sources:

- https://developer.android.com/reference/android/media/MediaCodec
- https://developer.android.com/reference/android/media/MediaExtractor

ASS Workbench impact:

- Waveform Lite must continue to fail closed as an optional layer;
- cancellation/project switching must release extractor/codec resources;
- a waveform failure must not alter video, subtitle, MKV or recovery state;
- vendor-specific decoder failure belongs in the test matrix.

Implemented lifecycle hardening:

- media-source replacement explicitly cancels the active waveform Job; subtitle-only replacement keeps analysis alive when the reference video is unchanged;
- decode loops check coroutine cancellation both between codec dequeues and inside large PCM output buffers;
- cancellation is rethrown as cancellation rather than being surfaced as a codec failure;
- `MediaCodec` and `MediaExtractor` release remain in `finally`;
- cancellation is checked again before cache publication, so a waveform belonging to an abandoned project is not published after decode completes;
- cache writes use unique temporary files and delete partial output on cancellation/failure.

### H6 — Compose performance work should focus on invalidation boundaries

Android's current Compose guidance emphasizes caching expensive calculations with `remember`, stable keys for lazy layouts, `derivedStateOf` for rapidly changing state, and deferring state reads when possible.

Source: https://developer.android.com/develop/ui/compose/performance

ASS Workbench impact:

- Event rows already use stable IDs; preserve that invariant;
- playback position, waveform viewport and transient preview are the highest-frequency state streams and should not invalidate unrelated editor surfaces;
- performance work must be measured as recomposition/frame cost, not guessed from code style.

Implemented invalidation-boundary hardening:

- live mpv playback position is now a dedicated `StateFlow<Long>` rather than a field in root `EditorState`;
- normal playback ticks no longer publish a new root editor-state object, avoiding whole-workbench invalidation;
- the timeline and expanded Event timing/effects editor collect the playhead locally;
- preview uses the current playhead value as resume input without observing it through the root workbench;
- explicit seek requests still update canonical seek request/nonce state, because those are commands rather than passive high-frequency telemetry.

### H8 — configuration changes must not discard uncommitted inline edit buffers

The canonical ASS document, focused Event and multi-selection live in `EditorViewModel`, but several expanded-Event fields were previously plain `remember` state. A portrait/landscape recreation could therefore keep the canonical document while silently resetting an uncommitted Raw Event draft or timing/metadata input.

Implemented hardening:

- Raw Event `TextFieldValue` (including cursor/selection), its conflict base text, Start/End text, Layer, Actor, Comment and the local inline-panel choice use `rememberSaveable`;
- canonical changes still reset fields whose saveable key includes that canonical value;
- Raw Event draft remains keyed only by Event ID so an external structured edit still produces the explicit draft/canonical conflict instead of silently replacing the draft.

Physical-device rotation remains in the 0.27 gate because Compose restoration and keyboard/focus behaviour must still be verified on Android.

### H7 — mpv direct-font mode accepts only one non-recursive subtitle font directory

mpv documents `--sub-fonts-dir` as a single directory, and libass does not recursively scan arbitrary sibling font directories. ASS Workbench previously switched the option between the persistent manual-font directory and the project MKV-font directory. Once an MKV contributed any attachment font, manually imported fonts could therefore disappear from the direct-font renderer's visible set.

Implemented hardening:

- renderer font publication now uses one stable `renderer-fonts` directory;
- that directory is the tested union of persistent manual fonts and current-project MKV attachment fonts;
- project changes remove only project-scoped publications while manual fonts remain available;
- filename collision policy is deterministic, with the current project source taking priority;
- the `sub-fonts-dir` path no longer changes when the first MKV attachment font arrives, avoiding an unnecessary mpv option/renderer identity change.
- `fontRevision` intentionally recreates the mpv/libass core after font publication; this is a rare font-management event, not a per-edit/per-gesture path, and avoids depending on undocumented live provider rescans.
- replacing a published font now moves a fully written temporary file over the old file in the same directory; the previous delete-then-rename/direct-copy path could expose a missing or partially written font if publication failed.

The Fontconfig production path continues to use its explicit manual/project/system directories; this union directory primarily closes the compatibility/direct-provider path and keeps both renderer modes semantically aligned.

### H9 — production APK must include the tested MKV write-back bridge

The fast Android CI built and tested the pinned `mkvgo` bridge before assembling its APK, but the independent Fontconfig production workflow previously assembled the canonical Release APK without creating `app/src/main/jniLibs/arm64-v8a/libmkvgo.so`. That could produce a renderer-correct APK whose `MkvGoTool.isAvailable()` was false on device.

Final release hardening:

- the Fontconfig production workflow now checks out the same pinned `mkvgo` commit as Android CI;
- the ASS replacement/font-attachment patch is injected and its Go tests run before packaging;
- the arm64 PIE helper is built into `app/src/main/jniLibs/arm64-v8a/libmkvgo.so`;
- production packaging fails unless the APK actually contains `lib/arm64-v8a/libmkvgo.so`;
- the build-identity manifest records the helper SHA-256;
- the production workflow also runs the core domain/font/container unit-test gate before it can publish the rolling prerelease.

This closes a release-path asymmetry: the APK used for physical-device D12–D16 tests now contains the same tested write-back implementation that the fast CI validates.

## Destructive / combination failure matrix

Status values: **AUTO** = current automated coverage exists; **ADD** = add automated coverage; **DEVICE** = physical Android validation required; **RESEARCH** = policy/compatibility decision still needed.

| ID | Domain | Destructive case | Required invariant | Status |
| --- | --- | --- | --- | --- |
| R1 | Raw ASS | unknown section + one Event edit + save/reopen | opaque section remains | AUTO |
| R2 | Raw ASS | custom Style/Event Format columns + edit | columns and values remain | AUTO |
| R3 | Encoding | UTF-8 BOM / UTF-16LE standalone ASS save | original detected encoding retained | AUTO |
| R4 | Raw draft | unsaved Event/project state while opening/replacing workspace | replacement requires explicit discard; Event draft vs canonical conflict has explicit tested policy | AUTO policy + DEVICE UI |
| R5 | Raw/native | extreme rotation/shear/position/scale literals | project remains editable; preview policy is safe and explicit | AUTO + DEVICE |
| R6 | Drawing/native | extreme `\\p` / vector-clip coordinates | no silent rewrite; native failure does not destroy project data | AUTO + DEVICE |
| R7 | Drawing/native | very large Drawing / vector-clip token stream | bounded preview behaviour; project state survives | AUTO + DEVICE |
| E1 | Event ops | split → merge → Undo → Redo | document snapshots and boundary whitespace round-trip exactly; focus/selection still needs UI/device verification | AUTO document + DEVICE UI |
| E2 | Event ops | multi-select batch time shift across t=0 | relative spacing retained; group clamp only | AUTO |
| E3 | Clipboard | paste Position/Effects beside nested `\\t(...)` | nested transform payload untouched | AUTO |
| G1 | Geometry | drag pos/move/org/rotation/scale/shear | transient preview model leaves UndoHistory untouched; one canonical end-state commit is reversible | AUTO domain + DEVICE UI wiring |
| G2 | Geometry | vector clip opened in rectangle editor | vector clip stays Raw-only and byte/text-equivalent | AUTO semantic |
| A1 | Animation | multiple transforms + malformed sibling | selected transform only changes; malformed sibling preserved | AUTO |
| A2 | Animation | scrub long/high-cost moving subtitle | no document commit and no unrelated workbench invalidation | DEVICE |
| T1 | Timeline | pan/zoom while playback advances | viewport does not snap back unless follow-playhead is enabled; zoom only clamps to legal start | AUTO policy + DEVICE gesture |
| T2 | Timeline | overlap frontier with nested long/short Events | later overlap not hidden | AUTO |
| W1 | Waveform | project switch during analysis | stale result ignored; codec/extractor released; cancelled result not cached | code-hardened + DEVICE |
| W2 | Waveform | unsupported/vendor-failing codec | waveform becomes unavailable only; editing remains usable | code-path guarded + DEVICE |
| W3 | Waveform | media shorter than timeline viewport | post-audio region renders silence | AUTO |
| F1 | Fonts | unused Style definitions | no false required-font request | AUTO |
| F2 | Fonts | inline `\\rStyle` + `\\fn` | effective request inventory includes both | AUTO |
| F3 | Fonts | missing glyph / fallback | diagnostic and renderer evidence agree or disagreement is visible | DEVICE |
| F4 | Fonts | imported font added while renderer active | stable renderer-font directory keeps manual + project fonts visible; fontRevision recreates mpv/libass so discovery occurs from a fresh core | AUTO lifecycle + DEVICE font-selection evidence |
| M1 | MKV | multiple ASS tracks; edit one | selected TrackNumber/UID/order/metadata retained | AUTO bridge |
| M2 | MKV | chapters/tags/existing attachments + ASS replacement | all preservation families retained | AUTO bridge |
| M3 | MKV | selected TTF/OTF packaged during replacement | original attachments retained; selected font appended once | AUTO bridge + Android Emulator E2E |
| M4 | MKV | same font already embedded | same SHA is not selected for repackaging even under a different filename | AUTO planner |
| M5 | MKV | same attachment filename, different font bytes | source attachment not overwritten; selected font receives deterministic collision-safe name | AUTO bridge |
| M6 | MKV | large source + many font attachments | streaming preservation remains bounded; no UI ANR | DEVICE |
| P1 | Compose | fast playback-position updates with long Event list | root EditorState does not emit per playback tick; only timeline/focused timing consumers observe playhead flow | code-hardened + DEVICE/profile |
| P2 | Renderer | long moving line / heavy transform fixture | no renderer recreation per gesture; transient ASS publication is generation-safe and capped to ~30 reloads/s | code-hardened + DEVICE/profile |
| L1 | Lifecycle | rotate portrait↔landscape during active edit | canonical document/focus/selection live in ViewModel; inline timing/Event/raw draft buffers use saveable state across configuration recreation | AUTO Activity recreation + DEVICE IME/gesture spot-check |
| L2 | Lifecycle | process death/recovery after unsaved edit | recovery restores expected snapshot without inheriting stale MKV/container state | AUTO journal/fresh-ViewModel path + DEVICE process-death spot-check |
| S1 | Save | start MKV save, then edit/switch workspace | completed output is save-start snapshot; stale callback cannot mutate the later workspace | code-hardened + DEVICE |
| B1 | Build | APK identity inspection | generated BuildConfig is checked against versionCode/version/commit/run; SHA-bearing APK ships with a hash manifest; Diagnostics exposes the same identity | AUTO + DEVICE spot-check |
| B2 | Release | 0.27.x production asset | production workflow runs core tests, packages/verifies the tested arm64 MKV bridge, and publishes SHA-bearing APK + identity + fixtures | AUTO workflow |

## Release provenance hardening

Actions storage policy is intentionally narrow:

- ordinary push/PR workflows retain **zero** Actions artifacts;
- `workflow_dispatch` may expose diagnostic/native/production bundles for **1 day** only;
- the canonical long-lived APK, build-identity manifest and device fixtures live in GitHub Release;
- dependency/build caches are not treated as release assets and remain outside this artifact-retention policy;
- historical Actions artifacts are removed as a one-time migration cleanup.

The production workflow now treats release publication as part of the build gate rather than a best-effort upload:

- the SHA-bearing APK, build-identity manifest, and device-fixture bundle are all required assets;
- replacing a rolling prerelease is safe even when no previous SHA-bearing asset exists, avoiding the `grep | while` + `pipefail` false failure found in Fontconfig run #294;
- the historical unsuffixed `ASS-Workbench-Android-<version>-debug.apk` is removed because it cannot identify its source commit;
- post-publication verification checks all required asset names and rejects the ambiguous legacy APK if it remains.

## Automated Android emulator release gate

The post-freeze Android regression layer now runs on an API 35 x86_64 Emulator and complements the JVM/domain suite.

Verified on the Emulator:

- crash-recovery entry, restore semantics and explicit discard;
- recovered journal persistence until explicit save/discard;
- uncommitted Raw Event draft retention across collapse/reopen and Event switching;
- Activity recreation with saveable inline draft state;
- ordinary ASS save failure remaining contained while dirty/recovery state survives;
- fresh ViewModel recovery of the latest journaled edit;
- native mpv/libass core recreation when `fontRevision` changes;
- native `MkvGoTool.replaceAss` execution against a deterministic MKV fixture, followed by re-parse verifying the selected ASS TrackNumber, replacement text, preservation of the existing font attachment, and addition of the selected new font attachment.

The current Emulator regression corpus is therefore an executable release gate rather than a compile-only check. It does not replace physical-device validation for OEM SAF behaviour, production arm64 renderer/font selection, GPU/native stability, touch/IME ergonomics, waveform codec variance, or large-media performance.

## Freeze priorities

### P0 — before publishing 0.27.0

Current race-hardening state:

- MKV scans now have a workspace epoch and cancellable Job; stale completion/failure callbacks cannot overwrite a later workspace.
- MKV attachment-font imports are session-gated inside `FontStore`, so an old scan cannot repopulate `project-fonts` after a project switch.
- selecting another ASS track is blocked while MKV write-back is active.
- MKV write-back uses a unique per-operation cache directory instead of a shared destructive workspace.
- success/failure callbacks are bound to the exact workspace epoch + container URI + track; reopening the same URI later does not let an old save mutate the new session.
- recovery read/write/clear operations are serialized, and recovery restore explicitly drops current container/MKV-font state instead of inheriting an unrelated container.
- recovered ASS remains editable/saveable; container write-back must be re-established explicitly after recovery.
- destructive workspace replacement is now explicit in the UI: dirty ASS → open another ASS, dirty ASS → new project, and dirty MKV track → another track all require a discard confirmation.
- the ViewModel also refuses an unconfirmed dirty MKV track switch, so UI mistakes cannot silently reset the canonical document.

- native renderer crash/OOM exposure from hostile/extreme Raw ASS;
- MKV write-back preservation and font-package collision semantics;
- save/recovery/project-switch races;
- renderer startup/provider failure (crash-loop safe mode implemented; physical-device provider validation still required);
- any reproducible data loss, silent semantic rewrite, or process crash.

Renderer startup crash-loop protection is now present:

- normal preview writes a persistent `normal_preview_core=starting` breadcrumb immediately before `rememberMpv` and `success` only after the core returns;
- if the previous app run ended between those markers, the next launch keeps the editor in **Renderer safe mode** and does not load mpv/libass automatically;
- the user can explicitly retry native preview from the placeholder while all document/save/container workflows remain available without the renderer.

### P1 — physical-device validation for the 0.27.x patch line

- waveform codec/resource lifecycle;
- high-frequency Compose invalidation;
- long/heavy subtitle renderer interaction;
- orientation and draft-state integrity;
- font fallback/glyph diagnostic disagreements.

### P2 — may survive into a documented 0.27 test candidate

- renderer compatibility differences where Raw ASS is preserved and the limitation is visible;
- cosmetic rendering differences inherited from libass;
- deferred Karaoke/Drawing authoring capability.

## Exit condition

0.27.0 may be published once:

1. P0 cases are either fixed or demonstrated not to affect the shipped path under automated coverage;
2. Android CI, Android Emulator Regression, and the Fontconfig production workflow are green on the release-candidate commit;
3. the destructive corpus has no unexplained preservation regression;
4. the build identity shown in Diagnostics matches the distributed APK and its SHA-bearing build-identity manifest;
5. remaining limitations are documented rather than silently hidden.

Physical-device validation then runs against the published 0.27.0 artifact using `docs/DEVICE-TEST-RESULTS.template.md`. Reproducible device failures are fixed in the 0.27.x patch line (0.27.1, 0.27.2, ...); P0 device failures block the next patch release, not the existence of 0.27.0 itself.
