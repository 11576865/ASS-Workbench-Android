#!/usr/bin/env python3
from pathlib import Path

path = Path(".mkvgo-src/mkv/writer/writer.go")
text = path.read_text(encoding="utf-8")
anchor = """	if t.IsForced {
		e.uint(mkv.IDFlagForced, 1)
	}
"""
replacement = anchor + """	if t.HearingImpaired {
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
if anchor not in text:
    raise SystemExit("pinned mkvgo writer anchor changed; refusing to patch")
if replacement in text:
    raise SystemExit(0)
path.write_text(text.replace(anchor, replacement, 1), encoding="utf-8")
