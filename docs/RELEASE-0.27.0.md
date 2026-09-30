# ASS Workbench Android 0.27.0

Release line: 0.27.x  
Initial candidate: 0.27.0 / versionCode 29

0.27.0 is the first packaged product candidate after the 0.26 construction/hardening cycle. It is intentionally published before the full physical-device matrix so that all real-device findings are attributable to one immutable build identity.

## Automated release evidence

Before publication, the release candidate is required to pass:

- core domain/font/container JVM tests;
- Android debug build and deterministic fixture validation;
- Android Emulator editor regression covering recovery, save failure, Event draft retention and Activity recreation;
- Android Emulator renderer lifecycle regression proving `fontRevision` recreates the native mpv/libass core;
- Android Emulator MKV end-to-end write-back using the native `mkvgo` helper and deterministic MKV fixture;
- Fontconfig production arm64 build with the tested MKV helper packaged into the APK;
- build-identity and SHA-bearing release provenance checks.

## Physical-device policy

0.27.0 is the baseline used for systematic real-device testing. Device-specific regressions are not folded back into an unpublished “0.27.0”; they advance the patch version:

- 0.27.1 — first device-found correction set;
- 0.27.2 — subsequent correction set;
- later 0.27.x releases as required.

P0 device failures include crash/process death, data loss, silent semantic rewrite, wrong-workspace mutation, corrupted output, and renderer crash-loop. Such failures block the next patch release until explained or fixed.

## Scope

0.27 focuses on the professional ASS editing core, raw-preserving structured editing, authoritative mpv/libass preview, recovery/data-loss hardening, font diagnostics and publication, timeline/waveform support, and preservation-oriented MKV ASS replacement with selected font packaging.

Karaoke authoring and full Drawing/vector-path authoring remain outside the 0.27 scope.
