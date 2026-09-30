# ASS Workbench Android

A touch-first, raw-preserving ASS workbench for Android — evolving from subtitle editing into a professional mobile subtitle engineering environment.

**Current construction version: 0.26.0 / versionCode 28**

0.26 is an internal construction and hardening cycle. The first planned real-device product handoff after this cycle is **0.27.0**; the version will not be bumped merely because most feature work is present.

See [docs/ABOUT.md](docs/ABOUT.md) for the longer-term direction, [docs/ROADMAP-0.26.md](docs/ROADMAP-0.26.md) for release semantics, and [docs/HARDENING-0.27.md](docs/HARDENING-0.27.md) / [docs/DEVICE-TEST-0.27.md](docs/DEVICE-TEST-0.27.md) for the current handoff gate.

## Product model

ASS Workbench treats ASS as the canonical editable document.

A local video can be attached as reference media, or an MKV can be opened as a subtitle project. MKV support is a **container bridge**: it exposes embedded ASS tracks and font attachments and can write an edited ASS track back into a new MKV without transcoding video/audio.

The editor follows several core rules:

- Raw ASS is first-class and unknown syntax should survive round trips.
- libass is the authoritative visual renderer.
- continuous gestures use transient preview and commit once at gesture end.
- one semantic domain should have one primary UI owner.
- structured tools should rewrite the smallest owned span rather than normalize whole Event text.
- portrait is the primary workflow layout; landscape is the precision visual layout.
- standalone ASS and MKV projects remain distinct workflows over the same ASS document core.

## 0.26 current capability

The current mainline includes:

- adaptive preview + Event workbench for phones and tablets;
- searchable Event list, selection, range selection and batch operations;
- bounded Undo/Redo and crash recovery;
- standalone ASS open/save with encoding preservation;
- mpv + libass authoritative preview;
- transient preview for continuous editing;
- Style typography and appearance editing;
- structured position/geometry editing for common ASS geometry domains;
- Event insert/duplicate/split/merge/delete and format clipboard operations;
- professionalised timeline zoom/pan, snapping, relation diagnostics and frame metadata;
- Waveform Lite as a bounded timing aid;
- structured fade/transform editing with contextual preview scrubbing;
- font registry, OpenType metadata/glyph diagnostics, renderer evidence and effective font-request inventory;
- quality checks tied to the same timeline/ASS semantic model;
- MKV ASS-track replacement with preservation-oriented bridge logic;
- selected manual TTF/OTF packaging into MKV in the same remux pass, with existing attachments preserved and SHA-based duplicate exclusion;
- preservation of unknown sections, opaque lines and custom Format columns;
- Review Sidecar stable identity and bilingual review infrastructure.

The bounded 0.26 feature pass is now closed. Selected-font MKV packaging is implemented; the remaining gate is hardening, release provenance, and systematic physical-device validation. Karaoke and full Drawing remain deferred beyond the first 0.27 test handoff.

## Workbench ownership

- **Event** — text, timing, Layer, Actor/Name, Comment, structural Event operations and batch navigation.
- **Typography & appearance** — font, size, emphasis, spacing, colors, alpha, border, shadow and blur.
- **Position & geometry** — alignment, margins, pos/move/org, rotation, scale, shear and rectangular clip.
- **Animation** — fad/fade, transforms and transformable visual properties.
- **Timeline** — playback, timing, trim/move, snapping, zoom/pan, waveform and timing relations.
- **Font Manager** — imported/MKV fonts, origin, family metadata, glyph coverage, request matching and replacement.
- **QC** — diagnostics and deep links into the relevant owner.
- **Raw ASS** — escape hatch and preservation boundary for syntax that structured tools do not own.

## 0.27 handoff gate

Before 0.27.0 is produced, the project should pass a feature-freeze and hardening cycle covering:

1. bounded remaining capability closure;
2. current upstream/community failure-case research;
3. performance and lifecycle hardening;
4. semantic-integrity and round-trip tests;
5. destructive combination testing across Raw ASS, structured editing, Undo/Redo, preview, MKV, fonts, waveform, orientation, recovery and save/reopen;
6. blocker/regression fixes only;
7. final version/build identity update and first real-device test handoff.

0.27.0 is therefore a **testable product candidate**, not a claim that the project is finished.

## Build identity

CI builds embed:

- app version and versionCode;
- the source commit SHA;
- the GitHub Actions run number.

The same identity is visible in the in-app **Diagnostics** surface. Release APK filenames and manually requested temporary bundles carry the short commit SHA so a test APK can be traced back to its exact source even while 0.26 remains a rolling construction prerelease.

## Actions artifact policy

Routine push and pull-request workflows retain no GitHub Actions artifacts. Long-lived device-test APKs, build identity and deterministic fixture bundles belong to the canonical GitHub prerelease.

`workflow_dispatch` is the only path that may expose short-lived Actions artifacts, and those bundles use a **1-day retention**. Dependency/build caches remain separate from this policy and are not disabled.

## Build stack

Current CI uses:

- JDK 21
- Kotlin 2.4.10
- Android Gradle Plugin 9.4.0
- Gradle 9.7.1
- compileSdk 36
- targetSdk 35
- minSdk 26
- Go 1.27 for the pinned arm64 MKV bridge
- libmpvKt 0.3.0 with the ASS Workbench Fontconfig-enabled arm64 renderer build

The authoritative preview stack includes mpv, FFmpeg and libass. Because the distributed native combination is GPL, this repository is licensed under **GPL-3.0-or-later**.

## Explicit non-goals

ASS Workbench is not a general video editor or transcoding suite. Video encoding and hard-sub rendering are outside core scope.

OCR, ASR, general machine translation, and broad “support every subtitle format” expansion are also outside the current product boundary.

## Project documents

- [About / long-term direction](docs/ABOUT.md)
- [0.26 → 0.27 roadmap and handoff gate](docs/ROADMAP-0.26.md)
- [0.27 hardening / destructive failure matrix](docs/HARDENING-0.27.md)
- [0.27 physical-device validation](docs/DEVICE-TEST-0.27.md)
- [Device-test results template](docs/DEVICE-TEST-RESULTS.template.md)
- [Actions artifact storage policy](docs/ARTIFACT-STORAGE-POLICY.md)
- [1.0 product specification](docs/PRODUCT_SPEC_1_0.md)
- [Third-party notices](THIRD_PARTY_NOTICES.md)
