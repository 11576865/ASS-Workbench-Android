# About ASS Workbench Android

ASS Workbench Android is an **ASS-first, touch-first, raw-preserving Android subtitle workbench**.

Today it combines ASS editing, libass-authoritative preview, typography and geometry tools, event operations, timeline work, animation controls, font diagnostics, recovery, quality checks, and an MKV container bridge. Its long-term direction is broader than “an ASS editor for phones”.

## What it may become

ASS Workbench Android may evolve into a **professional mobile subtitle authoring and engineering environment**: a place where subtitle text, timing, typography, fonts, animation, container resources, diagnostics, and final renderer behaviour can be inspected and edited as one coherent project.

That direction is defined by a few constraints:

- **Raw ASS remains the source of truth.** Structured tools should make small, observable edits rather than normalize the whole document into an editor-specific format.
- **libass remains the visual authority.** Guides, handles, sliders, and parameter frames are editing aids; the renderer decides the actual result.
- **Direct manipulation and precision coexist.** Touch gestures, sliders, exact values, and Raw ASS should be different views of the same semantic operation.
- **Dependencies should be observable.** Font requests, Style inheritance, inline overrides, MKV attachments, timing relations, and renderer fallback should be diagnosable rather than hidden.
- **Round-trip safety matters as much as feature count.** Unknown syntax, malformed-but-preservable text, custom fields, attachments, and container metadata should not disappear merely because the editor does not understand them.
- **Editing should remain reversible and recoverable.** Undo/Redo, transient preview, crash recovery, save/reopen behaviour, and project identity are part of the editor model, not secondary conveniences.
- **Phones and tablets are first-class work surfaces.** The goal is not to shrink a desktop UI onto Android, but to build an interaction model around touch, limited screen area, contextual tools, and direct preview feedback.

If these pieces continue to converge, the project may become closer to an **ASS authoring environment / subtitle engineering workbench** than a conventional subtitle editor.

## What it is not trying to become

The project is deliberately not expanding into a general video-production suite.

Video transcoding and hard-sub rendering are outside the core scope. OCR, ASR, general machine translation, and support for hundreds of subtitle formats are not core goals. MKV support exists as a **container bridge** around ASS tracks and font resources, not as a general-purpose video editor.

Future domains such as deeper animation semantics, Karaoke, Drawing, richer timeline tooling, font packaging, stronger QC, broader MKV round-trip preservation, and project-level resource management can grow on top of the same ASS-first foundation.

The intended destination is therefore not simply “Aegisub on Android”.

It is a touch-native subtitle engineering environment built around ASS semantics, renderer truth, preservation, and inspectable workflows.
