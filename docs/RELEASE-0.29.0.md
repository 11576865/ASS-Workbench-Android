# 0.29.0 — Engineering workbench

Version: 0.29.0 / versionCode 33.

## Product change

0.29 keeps ASS as the canonical editable representation while adding two UI presentations:

- Fixed UI: stable, predictable production layout.
- Canvas UI (experimental): floating tool instances with object bindings, docking, minimization, tab stacks and direct-manipulation experiments.

Both share the same document, Undo/Redo, libass preview, QC, font system and MKV bridge.

## New engineering domains

- `.asswb` project snapshots.
- ASS Linter + explicit Quick Fix.
- CFR/VFR frame timing.
- Compatibility analysis profiles.
- Rule-based batch pipeline.
- Karaoke authoring.
- Vector clip authoring.
- Actual font-dependency inventory.
- SRT import/export interoperability.

## Preservation boundary

SRT is an interchange format, not a replacement canonical model. Import converts SRT into ASS. Export writes the representable visible/timing subset and leaves the working ASS document untouched.

Font subsetting is deliberately excluded. ASS Workbench identifies required families and can select complete fonts for MKV write-back; creation of glyph-subset font binaries belongs to packaging/muxing tooling.

## Release gate

0.29.0 is not ready for merge until core tests, Android build and emulator regression pass on the feature branch.
