#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

HEADER = """[Script Info]
Title: ASS Workbench 0.27 device fixture
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080
ScaledBorderAndShadow: yes

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,54,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,0,0,0,0,100,100,0,0,1,2,1,2,40,40,48,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""

def write(path: Path, text: str) -> tuple[str, int]:
    data = text.encode("utf-8")
    path.write_bytes(data)
    return hashlib.sha256(data).hexdigest(), len(data)

def baseline() -> str:
    return HEADER + (
        "Dialogue: 0,0:00:00.00,0:00:05.00,Default,,0,0,0,,"
        "{\\pos(960,900)\\bord3}Baseline preview\n"
    )

def extreme_numeric() -> str:
    return HEADER + (
        "Dialogue: 0,0:00:00.00,0:00:10.00,Default,,0,0,0,,"
        "{\\frz1000000000\\fax1000000000\\fscx1000000000}"
        "Renderer-risk numeric fixture\n"
    )

def extreme_drawing() -> str:
    return HEADER + (
        "Dialogue: 0,0:00:00.00,0:00:10.00,Default,,0,0,0,,"
        "{\\p1}m -2147483648 -2147483648 l 2147483647 2147483647{\\p0}\n"
    )

def oversized_vector_clip() -> str:
    # 25,100 line commands produce > 50,000 numeric tokens, crossing the
    # current AssRendererRiskAnalyzer token threshold without shipping a huge
    # binary fixture in git.
    payload = "m 0 0 " + ("l 1 1 " * 25_100)
    return HEADER + (
        "Dialogue: 0,0:00:00.00,0:00:10.00,Default,,0,0,0,,"
        "{\\clip(" + payload + ")}Oversized vector clip fixture\n"
    )

def heavy_move() -> str:
    long_text = "ASS Workbench heavy move performance fixture · " * 70
    lines = []
    for i in range(12):
        start_cs = i * 25
        end_cs = start_cs + 3000
        start = f"0:00:{start_cs // 100:02d}.{start_cs % 100:02d}"
        end_seconds = end_cs // 100
        end = f"0:{end_seconds // 60:02d}:{end_seconds % 60:02d}.{end_cs % 100:02d}"
        y = 90 + i * 72
        lines.append(
            "Dialogue: 0,"
            + start + "," + end
            + ",Default,,0,0,0,,"
            + "{\\move(-1600," + str(y) + ",3520," + str(y)
            + ",0,30000)\\t(0,30000,\\frz720\\fscx160\\fscy160)}"
            + long_text
        )
    return HEADER + "\n".join(lines) + "\n"

def long_event_list() -> str:
    lines = []
    for i in range(2000):
        start_ms = i * 500
        end_ms = start_ms + 1800

        def fmt(ms: int) -> str:
            cs = ms // 10
            sec = cs // 100
            return f"{sec // 3600}:{(sec % 3600) // 60:02d}:{sec % 60:02d}.{cs % 100:02d}"

        lines.append(
            f"Dialogue: 0,{fmt(start_ms)},{fmt(end_ms)},Default,,0,0,0,,"
            f"Event {i:04d} {{\\pos(960,900)}} playback-state isolation"
        )
    return HEADER + "\n".join(lines) + "\n"

FIXTURES = {
    "baseline.ass": baseline,
    "renderer-risk-extreme-numeric.ass": extreme_numeric,
    "renderer-risk-extreme-drawing.ass": extreme_drawing,
    "renderer-risk-oversized-vector-clip.ass": oversized_vector_clip,
    "renderer-performance-heavy-move.ass": heavy_move,
    "compose-long-event-list.ass": long_event_list,
}

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Generate deterministic ASS Workbench 0.27 physical-device fixtures."
    )
    parser.add_argument(
        "output",
        nargs="?",
        default="build/device-fixtures-0.27",
        help="output directory (default: build/device-fixtures-0.27)",
    )
    args = parser.parse_args()

    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)

    manifest_lines = ["schema=1"]
    for name, factory in FIXTURES.items():
        sha256, size = write(out / name, factory())
        manifest_lines += [
            f"fixture={name}",
            f"{name}.sha256={sha256}",
            f"{name}.bytes={size}",
        ]

    manifest = "\n".join(manifest_lines) + "\n"
    (out / "manifest.txt").write_text(manifest, encoding="utf-8")
    print(manifest, end="")

if __name__ == "__main__":
    main()
