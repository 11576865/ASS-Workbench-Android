# FIXED V1.1 native visual audit and tablet layout correction — 2026-10-24

## Verified native baseline

Product source `89f69e97cd001f7522eea2f45ace5622d4272626`, tested merge
`15fa69f8dde774bcf494535759082ad337407799`:

- Source/JVM workflow `40016179211`: SUCCESS.
- Emulator workflow `40016179217`: FAILURE; four projection families 22/22 passed;
  full suite 181 tests, 6 failures, 0 errors, 0 skipped.
- Both native visual jobs succeeded. Full PNGs were reconstructed from all ordered
  runner-owned chunks, SHA256-verified and independently PNG-decoded locally.
- 1440×900: 143771 bytes, SHA256
  `7f244ee4e7176b6af5d74eb1d3d69299f7e012374b87907908f4581388e07f6c`,
  job `120638956190`.
- 1024×768: 92442 bytes, SHA256
  `fff425c5ce4efffb1901eca6b0486a5daf2a65eddd220e2ac4ef31c2b77a6a49`,
  job `120638956101`.

These are accepted compositor images of the native app, not HTML/mockup renders.
The fixture has two Events and renderer disabled. Black safe-mode preview and no
real audio waveform do not demonstrate real-media operation or parity with the
populated design fixture. The images establish native layout evidence only.

## Actual visual comparison

Compared both full native images with high-fidelity Figma contexts/renders for
`85:3` and `85:942` in file `5yNY5dMnmpmaOnYxDlEhY5`.

| Region | Observed native baseline | Frozen design | Correction in this package |
|---|---|---|---|
| 1440 body start | approximately y=152 | y=106 | move tool/preview/host commands into the existing four-scene tab row; remove the extra command row |
| 1024 body start | approximately y=203 | y=106 | same combined tab row; drawer command moves into it, removing the separate drawer-header row |
| 1024 task rail | absent | 56px permanent rail | reuse the existing native rail on tablet DUAL_PANE |
| 1024 inspector | about 472px, nearly half the display | about 310px | explicit finite inspector budget near 310dp, leaving center width near 656dp |
| 1024 Raw authoring | nested inside the inspector | separate central authoring dock | reuse the same central Raw dock and store, with the resolved instance target |
| Inspector property space | repeated scope card, tool title, close button below outer heading/tabs | one target/binding heading, then properties | docked inspector suppresses duplicate inner chrome; sidebar/drawer navigation remains available |
| Data and rendering | two English fixture Events, black safe mode | populated bilingual/video concept | unchanged factual limitation; no invented translation model, fake waveform or media |

This is the first completed **actual** dual-size native-vs-design layout audit for
this branch. It identifies structural gaps; it is not a pixel-parity acceptance.
Exact icons, typography, media transport details and bilingual authoring remain
open. The current correction requires a new native render before visual approval.

## Behavior and state constraints

- Tablet chrome requires width >=960dp, height >=720dp and hidden IME. Existing
  compact/IME fallbacks retain their own controls and header.
- One movable inspector, the same resolved instance/Event, session draft stores,
  canonical document, media clock and ViewModel commands remain authoritative.
- Compact geometry reserves independent finite preview, timeline and Raw budgets;
  hidden preview and timing-priority behavior remain explicit.
- Task and drawer commands retain their existing semantic tags and callbacks.
  The legacy R3 host marker remains on the new combined command row.
- No production SPATIAL implementation or full-suite assertions were weakened.

## Verification

Added a compact 1024 geometry JVM case and an Android combined-command callback
case. Strengthened exact-size native capture assertions to require rail, central
Raw dock and property form at both sizes; on 1024 require 300..326px inspector
width and non-overlap with the central dock. The redundant text-close button must
be absent in the docked inspector. Existing image integrity/foreground checks and
mandatory full suite remain strict.

Read-only review caught the legacy `fixed-r3-main-host` marker missing after moving
commands; it was restored before submission. No other blocking issue was found
by static review. Existing 35 Python tests passed, as did artifact policy and
`git diff --check`. This worker has no Kotlin/Android toolchain; new JVM/Compose
cases, compilation and native geometry remain Pending External Validation.

## Still-open baseline failures

All six were in `EditorRegressionInstrumentedTest`: POSITION directory in unified
canvas, stale event row after rotation, spatial resident pin visibility, BATCH
entry during keyboard workflow, disposed transform field during text input, and
invalid-field workflow calling scroll on a directory entry without a scrolling
ancestor. No causal attribution to the new inspector form is inferred just from
the count. The two previous test-driver edits did not establish those workflows
as resolved. They remain failed gates, separate from accepted screenshot bytes.
