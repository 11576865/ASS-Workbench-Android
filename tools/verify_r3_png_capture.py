#!/usr/bin/env python3
"""Verify that an Android compositor artifact is an actual decodable PNG.

The R3 screen capture is not evidence if adb emitted a diagnostic string,
a skipped JUnit method, a partial stream, or the wrong display orientation.
Standard library only; safe to run on GitHub's emulator CI host.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import struct
import sys
import zlib

SIGNATURE = b"\x89PNG\r\n\x1a\n"


def check_capture(path: Path, expected_width: int, expected_height: int) -> str:
    data = path.read_bytes()
    if len(data) < 1024 or not data.startswith(SIGNATURE):
        raise ValueError("not a real compositor PNG (empty, short, or bad signature)")
    position = len(SIGNATURE)
    width = height = 0
    image_chunks: list[bytes] = []
    found_header = found_end = False
    while position + 12 <= len(data):
        length = struct.unpack_from(">I", data, position)[0]
        end = position + 12 + length
        if end > len(data):
            raise ValueError("truncated PNG chunk")
        kind = data[position + 4 : position + 8]
        payload = data[position + 8 : position + 8 + length]
        crc = struct.unpack_from(">I", data, position + 8 + length)[0]
        if zlib.crc32(kind + payload) & 0xFFFFFFFF != crc:
            raise ValueError(f"bad PNG CRC for {kind!r}")
        if kind == b"IHDR":
            if found_header or length != 13 or position != 8:
                raise ValueError("invalid PNG IHDR")
            width, height, depth, color, compression, filter_mode, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
            if depth != 8 or color not in (2, 6) or compression or filter_mode or interlace:
                raise ValueError("unexpected image format for Android screenshot")
            found_header = True
        elif kind == b"IDAT":
            image_chunks.append(payload)
        elif kind == b"IEND":
            if length:
                raise ValueError("invalid IEND")
            found_end = True
            if end != len(data):
                raise ValueError("unexpected bytes after IEND")
            break
        position = end

    if not found_header or not image_chunks or not found_end:
        raise ValueError("PNG missing IHDR, IDAT or IEND")
    if (width, height) != (expected_width, expected_height):
        raise ValueError(f"wrong screenshot dimensions: {width}x{height}; "
                         f"need {expected_width}x{expected_height}")
    raw = zlib.decompress(b"".join(image_chunks))
    channels = 4 if color == 6 else 3
    expected_bytes = height * (1 + width * channels)
    if len(raw) != expected_bytes:
        raise ValueError(f"PNG pixel stream incomplete: {len(raw)} != {expected_bytes}")
    for y in range(height):
        row_size = 1 + width * channels
        if raw[y * row_size] > 4:
            raise ValueError(f"invalid PNG scanline filter on row {y}")
    return f"{width}x{height} valid compositor PNG, {len(data)} encoded bytes"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("capture", type=Path)
    parser.add_argument("--width", type=int, default=1600)
    parser.add_argument("--height", type=int, default=960)
    args = parser.parse_args()
    try:
        print(check_capture(args.capture, args.width, args.height))
        return 0
    except (ValueError, OSError, zlib.error, struct.error) as exc:
        print(f"R3_NATIVE_CAPTURE_INVALID: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
