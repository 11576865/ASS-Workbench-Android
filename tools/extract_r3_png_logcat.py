#!/usr/bin/env python3
"""Recover one full Android compositor PNG from an exact R3 logcat envelope.

The installed debug test host may be removed after connectedAndroidTest, so neither
run-as nor /sdcard/Android/data are guaranteed host-visible. Each PNG is emitted
as ordered bounded chunks *during instrumentation*. Treat any missing, mixed,
duplicated or corrupted chunk as a hard evidence failure.

This only establishes compositor bitmap integrity, not visual parity with R3.
"""
from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
from pathlib import Path
import re
import sys

BEGIN = re.compile(r"R3_PNG_BEGIN:([^:\r\n]+):([0-9]+):([a-f0-9]{64}):([0-9]+)")
PART = re.compile(r"R3_PNG:([0-9]+):([A-Za-z0-9+/=]+)")
END = re.compile(r"R3_PNG_END:([^:\r\n]+)")
MAX_BYTES = 5_000_000
MAX_CHUNKS = 3000


def extract_capture(log: str, name: str) -> bytes:
    # Android/GitHub prefixes and logcat timestamps are not part of evidence.
    active = None
    complete = []
    for line in log.splitlines():
        start = BEGIN.search(line)
        if start and start.group(1) == name:
            expected_size, digest, expected_count = (
                int(start.group(2)), start.group(3), int(start.group(4))
            )
            if not (1024 <= expected_size <= MAX_BYTES and
                    1 <= expected_count <= MAX_CHUNKS):
                raise ValueError("R3 PNG envelope declares invalid length or chunk count")
            if active is not None:
                raise ValueError("R3 capture envelope has nested BEGIN")
            active = [expected_size, digest, expected_count, []]
            continue
        if active is None:
            continue
        part = PART.search(line)
        if part:
            index = int(part.group(1))
            if index != len(active[3]) or index >= active[2]:
                raise ValueError("R3 PNG chunks are missing, duplicated or out of order")
            active[3].append(part.group(2))
            continue
        ending = END.search(line)
        if ending:
            if ending.group(1) != name:
                raise ValueError("R3 capture END does not match BEGIN")
            expected_size, digest, count, parts = active
            if len(parts) != count:
                raise ValueError(f"R3 capture incomplete: {len(parts)} of {count} chunks")
            try:
                data = base64.b64decode("".join(parts), validate=True)
            except binascii.Error as exc:
                raise ValueError("R3 capture base64 is damaged") from exc
            if len(data) != expected_size:
                raise ValueError("R3 capture byte length mismatch")
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError("R3 capture SHA-256 mismatch")
            complete.append(data)
            active = None

    if active is not None:
        raise ValueError("R3 PNG envelope has no matching END")
    if len(complete) != 1:
        raise ValueError(f"expected exactly one complete R3 PNG, found {len(complete)}")
    return complete[0]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("logcat", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--name", default="ASS.R3.FIXED.PRODUCTION_REFERENCE.png")
    args = parser.parse_args()
    try:
        # Strict PNG verification is performed separately by
        # verify_r3_png_capture.py, using decoded dimensions and CRC.
        payload = extract_capture(args.logcat.read_text(errors="replace"), args.name)
        args.output.write_bytes(payload)
    except (OSError, ValueError) as exc:
        print(f"R3_NATIVE_LOGCAT_RECOVERY_INVALID: {exc}", file=sys.stderr)
        return 1
    print(f"R3_NATIVE_LOGCAT_RECOVERED: {args.output}, {len(payload)} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
