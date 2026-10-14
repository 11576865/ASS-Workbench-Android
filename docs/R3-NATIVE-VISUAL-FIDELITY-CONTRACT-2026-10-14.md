# ASS Workbench R3 — Android native visual-fidelity contract (2026-10-14)

**Authority:** User request: implement the already drawn R3 and match its visual design; visual parity is an acceptance gate, not a statement inferred from code, emulator CI, or design SVGs.

**Design evidence:** `ASS_Workbench_R3_FIXED.png` (1600×960), `ASS_Workbench_R3_SPATIAL.png`, `ASS_Workbench_R3_Tool_Catalog.png`, `ASS_Workbench_R3_Write_Review.png`, and editable SVGs supplied in the conversation package. These are *concepts*, not Native device images.

## Reference geometry for FIXED at the 1600×960 design viewport

| Region | Approximate reference bounds, px | Native interpretation |
| --- | --- | --- |
| Project / Undo / Save / menu bar | y=0..55 (56 tall) | Full-width stable app bar |
| Task navigation / two Host access | y=56..105 (50 tall) | Full-width stable task strip |
| Event navigator | x=0..266, y=106..959 | Finite list, no duplicated editor |
| Video + playback + waveform/Timeline | x=267..1172, y=106..959 | Genuine mpv/libass preview and same media clock |
| Right Event/tool inspector | x=1173..1599, y=106..959 | Live ToolInstance, read source/write target |
| Video image region | center x, y≈106..611 at 16:9 | Scale uniformly; letterbox only when required |
| Playback transport | center x, y≈612..664 | 48dp target outside media; never occlude ASS glyphs |
| Waveform/Timeline | center x, y≈665..941 plus remainder | Actual event and waveform, independent of inspector |
| Bottom metadata/status | y≈941..959 in concept | Separate from needed touch and keyboard safe area |

All design coordinates are drawing pixels, **not a claim that a 1600 physical-pixel Android image equals 1600dp**. Match normalized content bounds after accounting for density, system insets, font rendering and cutouts. Never scale actual media anisotropically to imitate the screenshot.

Reference palette sampled from the actual provided PNG: shell `#0A1929`, tool surface around `#132E42`, highlighted selection around `#123A4C`, actionable cyan `#34D0E7`, white-gray foreground. In DARK mode the palette is an R3 product token set; explicit user-selected LIGHT mode remains supported and is not required to resemble this dark design.

## Visually testable invariants

1. Wide FIXED anchor order remains **Event navigator / video + transport + docked Timeline / inspector**, left-to-right; no duplicate preview heading, external black frame, floating tool chrome or arbitrary video letterbox.
2. Image keeps current media aspect (16:9, 4:3, 9:16 test sources), viewport excludes transport; bottom surplus belongs to actual Timeline and editor, not empty reference padding.
3. Header displays project identity, Undo/Redo, Save, file operations and explicit two-Host navigation. FIXED remains main/default; SPATIAL stays a separate entire UI with its own workscene.
4. Inspector binds to the current Event/ToolInstance and displays truthful binding/write scope; read-source is not silently used as a write-target selector.
5. Raw ASS, shared Style, Event override, Unicode, Transform, Fade, Karaoke, Vector Clip, Batch, QC, fonts, project and diagnostics remain real discoverable tools; adding a menu icon alone does not establish full functionality.
6. Draft, Focus, Selection, media clock, Time dock, Event Bindings and document Undo survive task switches, orientation, IME, and FIXED↔SPATIAL transitions; camera/node geometry is isolated from ASS subtitles.
7. With keyboard open, essential Apply/Discard are reachable and the visible timeline/preview must not overwrite user-chosen saved density.

## Evidence gate for product acceptance

- **Source:** Exact commit path and its implementation of the layout/interaction.
- **CI:** Android Kotlin build, JVM and emulator instrumentation results for exact head SHA.
- **Production visual:** Android compositor capture in a wide viewport, normal media playing and overlays, image compared to the R3 PNG after viewport/density normalization; inspect top-bar, left list, actual video, waveform/timeline, inspector values, and open panels separately.
- **Device:** Real tablet with touch, rotation, IME, VFR frame seeking and libass playback. A deterministic fixture is not enough for production media performance.
- **Disposition:** Every visible mismatch filed as a bounded R3 visual issue, not silently accepted because Compose compiled. Do not claim pixel-perfect fidelity before comparison and review.

## Current implementation checkpoint — PR #148 (Draft)

Implemented as source: three columns in `FixedWorkspace`, aspect-first policy, docked transport, live Timeline/inspector, R3 dark palette, fixed header/task strip heights, Event/binding/write context, JVM policy tests, conditional real compositor capture.

**Not yet accepted:** actual visual diff, exact text/padding/icon fidelity, full R3 provenance and write-review screens, extra professional editor views, and SPATIAL R3 visual parity/legacy feature migration. These remain mandatory work, not waived because the first slice is merged or a screenshot appears.

This file is a test contract, not an approval statement. R3 is under development.
