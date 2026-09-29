# ASS Workbench Android 0.27 — Hardening and Failure Matrix

Date: 2026-09-30  
Functional freeze baseline: `46057771aea2f1d7437e6d919d35c9dbfbf5eda7`  
Target: first systematic real-device handoff as 0.27.0

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

Required work:

- add a renderer-risk diagnostic corpus for extreme finite numeric values;
- verify every structured exact-value commit has practical bounds, not only slider bounds;
- decide a non-destructive preview policy for input known to be dangerous to the native renderer;
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

Required work:

- include extreme-coordinate and very-large-drawing fixtures in the destructive corpus;
- measure parse/editor memory independently from libass renderer memory;
- define a preview refusal/degradation path if a bounded safety rule is introduced;
- preserve the original drawing bytes/text even when structured preview is refused.

### H3 — Complex moving subtitles can remain expensive frame after frame

libass issue #844 shows a long horizontally moving line causing sustained dropped frames, with event structure materially affecting cost.

Source: https://github.com/libass/libass/issues/844

ASS Workbench impact:

- animation/geometry scrubbing must not add avoidable Compose or document-rebuild work on top of inherently expensive libass frames;
- performance tests need at least one long `\\move` / animation fixture rather than only ordinary dialogue;
- a slow renderer case must not create dozens of history commits or trigger renderer recreation per pointer movement.

### H4 — Font attachment media types have a defined Matroska compatibility surface

The Matroska attachment guidance lists RFC font media types including `font/ttf`, `font/otf`, `font/sfnt` and `font/collection`, plus legacy types seen in existing files. Writers should emit valid font media types; readers may also encounter legacy or octet-stream attachments.

Source: https://www.matroska.org/technical/attachments.html

ASS Workbench impact:

- the new writer emits `font/ttf` and `font/otf`, which is aligned with the documented writer guidance;
- incoming attachment recognition must remain conservative and compatibility-oriented;
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

### H6 — Compose performance work should focus on invalidation boundaries

Android's current Compose guidance emphasizes caching expensive calculations with `remember`, stable keys for lazy layouts, `derivedStateOf` for rapidly changing state, and deferring state reads when possible.

Source: https://developer.android.com/develop/ui/compose/performance

ASS Workbench impact:

- Event rows already use stable IDs; preserve that invariant;
- playback position, waveform viewport and transient preview are the highest-frequency state streams and should not invalidate unrelated editor surfaces;
- performance work must be measured as recomposition/frame cost, not guessed from code style.

## Destructive / combination failure matrix

Status values: **AUTO** = current automated coverage exists; **ADD** = add automated coverage; **DEVICE** = physical Android validation required; **RESEARCH** = policy/compatibility decision still needed.

| ID | Domain | Destructive case | Required invariant | Status |
| --- | --- | --- | --- | --- |
| R1 | Raw ASS | unknown section + one Event edit + save/reopen | opaque section remains | AUTO |
| R2 | Raw ASS | custom Style/Event Format columns + edit | columns and values remain | AUTO |
| R3 | Encoding | UTF-8 BOM / UTF-16LE standalone ASS save | original detected encoding retained | AUTO |
| R4 | Raw draft | unsaved Event text while another canonical edit occurs | draft not silently overwritten; conflict visible | ADD |
| R5 | Raw/native | extreme rotation/shear/position/scale literals | project remains editable; preview policy is safe and explicit | RESEARCH |
| R6 | Drawing/native | extreme `\\p` coordinates | no silent rewrite; native failure does not destroy project data | RESEARCH |
| R7 | Drawing/native | very large Drawing token stream | bounded behaviour; project state survives | RESEARCH |
| E1 | Event ops | split → merge → Undo → Redo | text boundary whitespace and focus/selection remain valid | AUTO + ADD combinations |
| E2 | Event ops | multi-select batch time shift across t=0 | relative spacing retained; group clamp only | AUTO |
| E3 | Clipboard | paste Position/Effects beside nested `\\t(...)` | nested transform payload untouched | AUTO |
| G1 | Geometry | drag pos/move/org/rotation/scale/shear | transient preview only during gesture; one history commit at end | ADD UI/ViewModel test |
| G2 | Geometry | vector clip opened in rectangle editor | vector clip stays Raw-only and byte/text-equivalent | AUTO semantic |
| A1 | Animation | multiple transforms + malformed sibling | selected transform only changes; malformed sibling preserved | AUTO |
| A2 | Animation | scrub long/high-cost moving subtitle | no document commit and no unrelated workbench invalidation | DEVICE |
| T1 | Timeline | pan/zoom while playback advances | viewport does not snap back unless follow-playhead is enabled | ADD |
| T2 | Timeline | overlap frontier with nested long/short Events | later overlap not hidden | AUTO |
| W1 | Waveform | project switch during analysis | stale result ignored; codec/extractor released | ADD + DEVICE |
| W2 | Waveform | unsupported/vendor-failing codec | waveform becomes unavailable only; editing remains usable | DEVICE |
| W3 | Waveform | media shorter than timeline viewport | post-audio region renders silence | AUTO |
| F1 | Fonts | unused Style definitions | no false required-font request | AUTO |
| F2 | Fonts | inline `\\rStyle` + `\\fn` | effective request inventory includes both | AUTO |
| F3 | Fonts | missing glyph / fallback | diagnostic and renderer evidence agree or disagreement is visible | DEVICE |
| F4 | Fonts | imported font added while renderer active | no unsafe live native cache mutation | DEVICE |
| M1 | MKV | multiple ASS tracks; edit one | selected TrackNumber/UID/order/metadata retained | AUTO bridge |
| M2 | MKV | chapters/tags/existing attachments + ASS replacement | all preservation families retained | AUTO bridge |
| M3 | MKV | selected TTF/OTF packaged during replacement | original attachments retained; selected font appended once | AUTO bridge |
| M4 | MKV | same font already embedded | same SHA is not selected for repackaging | ADD planner/ViewModel |
| M5 | MKV | same attachment filename, different font bytes | source attachment not overwritten; result policy explicit | ADD |
| M6 | MKV | large source + many font attachments | streaming preservation remains bounded; no UI ANR | DEVICE |
| P1 | Compose | fast playback-position updates with long Event list | unrelated rows/tools avoid high-frequency recomposition | DEVICE/profile |
| P2 | Renderer | long moving line / heavy transform fixture | no renderer recreation per gesture; editor remains responsive enough to recover | DEVICE/profile |
| L1 | Lifecycle | rotate portrait↔landscape during active edit | canonical document, focus, selection and draft semantics survive | DEVICE |
| L2 | Lifecycle | process death/recovery after unsaved edit | recovery restores expected snapshot without overwriting source | DEVICE |
| S1 | Save | start MKV save, then edit current document | completed output is save-start snapshot; current dirty state remains | current code + DEVICE |
| B1 | Build | APK identity inspection | versionCode/version/commit/run are visible and match artifact name | AUTO + DEVICE |
| B2 | Release | rolling 0.26 prerelease asset | APK filename/diagnostics identify exact source commit | AUTO |

## Freeze priorities

### P0 — before any 0.27 version bump

- native renderer crash/OOM exposure from hostile/extreme Raw ASS;
- MKV write-back preservation and font-package collision semantics;
- save/recovery/project-switch races;
- renderer startup/provider failure;
- any reproducible data loss, silent semantic rewrite, or process crash.

### P1 — before first serious device handoff if reproducible

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

The 0.27 version bump is permitted only after:

1. P0 cases are either fixed or demonstrated not to affect the shipped path;
2. Android CI and the Fontconfig production workflow are green on the release-candidate commit;
3. the destructive corpus has no unexplained preservation regression;
4. a physical-device pass covers startup, open/save, MKV write-back, selected font packaging, preview, waveform failure behaviour, orientation, recovery and save/reopen;
5. the build identity shown in Diagnostics matches the distributed APK;
6. remaining limitations are documented rather than silently hidden.
