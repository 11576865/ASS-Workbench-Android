#!/usr/bin/env python3
"""Re-emit one validated full compositor PNG in a small, independently readable job.

The source job still owns regression status. This transport receipt establishes
bitmap integrity and dimensions, never visual parity or media renderer coverage.
"""
import argparse
import base64
import hashlib
from pathlib import Path
import re
import tempfile

from extract_r3_png_logcat import extract_capture
from verify_r3_png_capture import check_capture


def emit_capture(log: str, width: int, height: int) -> None:
    name = f'ASS.V11.FIXED.{width}x{height}.png'
    # Android logcat is lossy and may repeat captures in diagnostic tails.
    # Only use the runner's verified run-as file envelope, never Android logs.
    transport = '\n'.join(line.replace('V11_PNG', 'R3_PNG')
                          for line in log.splitlines() if 'V11_PNG' in line)
    payload = extract_capture(transport, name)
    digest = hashlib.sha256(payload).hexdigest()
    receipt = re.compile(r'V11_NATIVE_CAPTURE_VALID: file=' + re.escape(name)
                         + r' sha256=' + digest + r' size=' + str(len(payload)) + r'\s*$')
    if not any(receipt.search(line) for line in log.splitlines()):
        raise ValueError('missing or mismatched native capture gate receipt')
    emit_payload(payload, width, height)


def emit_payload(payload: bytes, width: int, height: int) -> None:
    name = f'ASS.V11.FIXED.{width}x{height}.png'
    digest = hashlib.sha256(payload).hexdigest()
    # A bounded evidence job must remain readable through the connector. Never
    # silently replace the full screenshot with a thumbnail or truncated stream.
    if len(payload) > 750_000:
        raise ValueError('full PNG exceeds bounded evidence transport budget')
    with tempfile.TemporaryDirectory() as directory:
        capture = Path(directory) / name
        capture.write_bytes(payload)
        check_capture(capture, width, height)
    encoded = base64.b64encode(payload).decode('ascii')
    parts = [encoded[i:i + 2048] for i in range(0, len(encoded), 2048)]
    print(f'NATIVE_VISUAL_EVIDENCE_VALID: file={name} sha256={digest} size={len(payload)}')
    print(f'V11_PNG_BEGIN:{name}:{len(payload)}:{digest}:{len(parts)}')
    for index, part in enumerate(parts):
        print(f'V11_PNG:{index}:{part}')
    print(f'V11_PNG_END:{name}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--source-log', type=Path)
    source.add_argument('--capture', type=Path)
    parser.add_argument('--width', type=int, required=True)
    parser.add_argument('--height', type=int, required=True)
    args = parser.parse_args()
    if args.capture:
        emit_payload(args.capture.read_bytes(), args.width, args.height)
    else:
        emit_capture(args.source_log.read_text(errors='replace'), args.width, args.height)
