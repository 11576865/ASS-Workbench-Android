# ASS Workbench Android — 1.0 Product Specification

## Product boundary

ASS Workbench is a mobile/tablet **ASS editing, review and visual typesetting workbench**. ASS is the canonical editable representation. Video is reference media used for synchronized preview. An MKV may also be opened through the **Container Bridge** so an embedded ASS track and relevant font attachments can be exposed to the same editor.

Primary outputs are a standalone `.ass` file or a new MKV in which the selected ASS track is replaced at container level without re-encoding video/audio. Video encoding, burn-in, video filtering/editing, general-purpose muxing, ASR, OCR and translation are outside 1.0.

## 1.0 acceptance sentence

On an Android phone or tablet, a user can open local video and ASS independently, or open an MKV subtitle project, preview subtitles while the video plays, search and batch-manage events, change global styles or per-event overrides (font, size, emphasis, colors, outline/shadow, spacing, nine-grid alignment, position and simple fade), support overlapping events/layers, import project fonts, undo/redo safely, inspect raw ASS when needed, and save/Save As a standalone ASS without destroying unsupported content.

## Required areas

### Media and project
- Open/change reference video without modifying subtitles.
- Open/change subtitle without modifying video.
- Optional project binding stores both URIs and editor state.
- Persistable Android SAF permissions where possible.

### Editing
- ASS first-class and canonical.
- Event list: start/end/layer/style/text.
- Multiple events may overlap in time.
- Search and jump to event.
- Multi-select and batch operations.
- Undo/Redo with bounded history.
- Save and Save As; autosave recovery is separate from overwriting the source.

### Style and per-event presentation
- Font family, size, bold, italic, underline, strikeout.
- Primary/outline/shadow colors.
- Border width, shadow depth, blur.
- Character spacing.
- Nine-grid alignment (ASS 1–9).
- Margin and fine X/Y positioning.
- Simple `\\fad` and restrained transform presets.
- Global Style editing and per-event override editing must remain distinct.

### Fonts
- Browse usable fonts.
- Import TTF/OTF as project fonts.
- Extract and store actual font family metadata; never assume filename == family.
- One project Font Registry feeds both UI and the final libass preview path.
- Missing/fallback font diagnostics.

### Preview
- Split layout: preview pane + subtitle workbench pane.
- Draggable divider; ratio persisted.
- Phone compact mode uses vertical split.
- Final visual authority is libass.
- Preview supports multiple simultaneous events ordered by Layer.

### Container Bridge
- Open an MKV as a subtitle project and select an embedded `S_TEXT/ASS` track.
- Register supported TTF/OTF attachments.
- Save a new MKV by replacing the selected ASS track without transcoding video/audio.
- Replacement preserves the source subtitle track slot and identity metadata: TrackNumber, TrackUID, ordering, language/name and disposition flags.
- Never modify the source MKV in place.
- Preserve attached fonts, chapters and ordinary tags; protect this with an end-to-end bridge regression test.
- Audit the same preservation contract across a broader real-world corpus before 1.0.

### ASS safety
- Preserve unknown sections and unsupported tags where possible.
- Raw Event/override view hidden by default but available.
- Script resolution (PlayResX/Y) is explicit.
- Validation warns rather than silently rewriting questionable content.

## Not required for 1.0
- SRT import;
- hard-sub video rendering;
- general-purpose container editing/muxing;
- professional waveform/spectrogram timing;
- Lua Automation;
- OCR/ASR;
- translation;
- vector drawing editor;
- complete Aegisub parity.
