#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: Path, anchor: str, replacement: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if replacement in text:
        return
    if anchor not in text:
        raise SystemExit(f"pinned mkvgo {label} anchor changed; refusing to patch")
    path.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")


# Keep upstream writer support for extended disposition flags aligned with the
# ASS Workbench preservation contract.
writer_path = Path(".mkvgo-src/mkv/writer/writer.go")
flag_anchor = """	if t.IsForced {
		e.uint(mkv.IDFlagForced, 1)
	}
"""
flag_replacement = flag_anchor + """	if t.HearingImpaired {
		e.uint(mkv.IDFlagHearingImpaired, 1)
	}
	if t.VisualImpaired {
		e.uint(mkv.IDFlagVisualImpaired, 1)
	}
	if t.TextDescriptions {
		e.uint(mkv.IDFlagTextDescriptions, 1)
	}
	if t.Original {
		e.uint(mkv.IDFlagOriginal, 1)
	}
	if t.Commentary {
		e.uint(mkv.IDFlagCommentary, 1)
	}
"""
replace_once(writer_path, flag_anchor, flag_replacement, "writer disposition")

# Pinned mkvgo already knows the Matroska FileDescription element ID, but its
# Attachment model/reader/writer do not carry it. Extend that pinned source at
# build time so metadata-only attachment edits can preserve and update the
# description without forking the whole dependency.
model_path = Path(".mkvgo-src/mkv/model.go")
model_anchor = """type Attachment struct {
	ID       uint64 `json:"id"`
	Name     string `json:"name"`
	MIMEType string `json:"mime_type"`
	Size     int64  `json:"size"`
"""
model_replacement = """type Attachment struct {
	ID          uint64 `json:"id"`
	Name        string `json:"name"`
	MIMEType    string `json:"mime_type"`
	Description string `json:"description,omitempty"`
	Size        int64  `json:"size"`
"""
replace_once(model_path, model_anchor, model_replacement, "attachment model")

reader_path = Path(".mkvgo-src/mkv/reader/reader.go")
reader_anchor = """		case mkv.IDFileMimeType:
			if err := p.chargeMeta(eh.Size); err != nil {
				return att, err
			}
			v, err := ebml.ReadString(p.r, eh.Size)
			if err != nil {
				return att, err
			}
			att.MIMEType = v
		case mkv.IDFileData:
"""
reader_replacement = """		case mkv.IDFileMimeType:
			if err := p.chargeMeta(eh.Size); err != nil {
				return att, err
			}
			v, err := ebml.ReadString(p.r, eh.Size)
			if err != nil {
				return att, err
			}
			att.MIMEType = v
		case mkv.IDFileDescription:
			if err := p.chargeMeta(eh.Size); err != nil {
				return att, err
			}
			v, err := ebml.ReadString(p.r, eh.Size)
			if err != nil {
				return att, err
			}
			att.Description = v
		case mkv.IDFileData:
"""
replace_once(reader_path, reader_anchor, reader_replacement, "attachment reader")

stream_reader_path = Path(".mkvgo-src/mkv/reader/stream.go")
stream_anchor = """		case mkv.IDFileMimeType:
			v, err := p.readString(h.Size)
			if err != nil {
				return err
			}
			att.MIMEType = v
		case mkv.IDFileData:
"""
stream_replacement = """		case mkv.IDFileMimeType:
			v, err := p.readString(h.Size)
			if err != nil {
				return err
			}
			att.MIMEType = v
		case mkv.IDFileDescription:
			v, err := p.readString(h.Size)
			if err != nil {
				return err
			}
			att.Description = v
		case mkv.IDFileData:
"""
replace_once(stream_reader_path, stream_anchor, stream_replacement, "stream attachment reader")

writer_attachment_anchor = """		if att.MIMEType != "" {
			m.str(mkv.IDFileMimeType, att.MIMEType)
		}
		if att.ID > 0 {
"""
writer_attachment_replacement = """		if att.MIMEType != "" {
			m.str(mkv.IDFileMimeType, att.MIMEType)
		}
		if att.Description != "" {
			m.str(mkv.IDFileDescription, att.Description)
		}
		if att.ID > 0 {
"""
replace_once(writer_path, writer_attachment_anchor, writer_attachment_replacement, "attachment writer")
