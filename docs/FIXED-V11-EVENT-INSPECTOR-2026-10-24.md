# FIXED V1.1 Event inspector — 2026-10-24

## User-visible work package

Implement the Event property body of approved Figma `85:337` in the existing native
Compose inspector, including heading/binding hierarchy, aligned property fields,
per-field provenance and the inline QC entry. This package uses the existing
session-owned timing and metadata drafts and existing ViewModel commands.

Previously, wide TEXT with the central Raw dock exposed only Start/End on the
right. Layer and Actor required switching to EVENT, and the inspector omitted the
Style reference and saved Raw color evidence. In docked TEXT/EVENT, the property
form now presents Start, End, Layer, the actual Style name and Actor in one aligned
label/value grid, with Comment/Dialogue, explicit timing/metadata actions, reset
and conflict messages. EVENT structural actions remain below the form. The central
Raw editor and its independent frozen review remain in place.

The inspector heading separates its title from the resolved target/binding pill.
QC displays the actual saved-document issue count, errors and warnings for the
resolved Event (or the document when no Event is resolved) and opens the existing
QC tool. It performs no automatic repair.

## Deliberate semantic differences from the design prototype

- The Style **name** is an Event field referencing a Style; it is labeled
  `Event · Style 引用`. Effective font/color inheritance is separate provenance.
- Missing Style references display an explicit warning; no Style is created or
  silently substituted by this form.
- The saved Raw primary color is inspected using `AssEffectiveInspector` and
  `r3ValueEvidence`, including its existing first-span/time-varying caveat. It is
  not represented as independent source/translation colors: that domain mapping
  does not exist yet. Unparseable evidence is explicitly shown as unavailable.
- Timing, metadata and Raw retain their existing separate application boundaries.
  No single atomic whole-inspector write is claimed.
- Only the docked wide TEXT/EVENT form changes. Existing compact editors and
  specialist Style/Position/Effects editors retain their existing implementation.

## Validation and review boundaries

Added two Compose instrumented cases covering shared draft survival across remount,
explicit callbacks, invalid/reversed timing, invalid Layer, external metadata
conflict preservation and missing Style disclosure. They have not run locally:
this worker has no Android SDK/Gradle toolchain. Kotlin/Android execution is Pending.
Read-only code review found an incomplete-text Compose assertion; it was changed to assert the full actual label before commit. No other blocking issue was identified by static review. Existing 35 Python capture/instrumentation/evidence checks passed locally;
`git diff --check` and Actions artifact policy passed. These do not prove the new
Compose form compiles or matches Figma visually.

The Figma design context and render for `85:337` were read before implementation;
this node has no static image/SVG assets. Existing MaterialTheme tokens and session
stores are reused. Actual native screenshot comparison remains Pending.

## Latest external evidence before this package

Source `39ffe1a3604d9cbcd3ed12717983800f033bc7f9` passed source/JVM workflow
`39971371387`. Emulator `39971371534` failed:

- R3 and both tablet captures were rejected with accessibility root `android`,
  target decor `focused=false`, `shown=true`; no screenshot was accepted.
- Device last-ANR diagnostics recorded an input-dispatch timeout with no focused
  window, at 14:44:15 UTC, before the screenshot rejection. The recorded resumed
  Activity was Pixel Launcher. This supports an environment-readiness problem;
  it does not independently identify all contributing causes.
- Four projection groups passed 9/3/7/3 tests.
- Full-suite execution timed out and its XML was empty. No full-suite test count
  or pass/fail delta can be inferred for this source.
- The two bounded screenshot jobs correctly failed because no complete accepted
  PNG envelope existed. Their failure is not evidence of a decoder defect.

The older `020e207` 179-test/4-failure receipt and valid tablet capture gates remain
historical evidence only. Neither is relabeled as the latest source's result.

## Next acceptance

Compile the new form and Android tests, recover accepted full native screenshots
at both design sizes, and compare the actual page with the frozen Figma targets.
Resolve remaining full-suite failures from a completed, uncontaminated run; keep
all current foreground, PNG integrity and regression assertions strict. PR #149
and its parent #148 stay Draft; no main merge is part of this package.
