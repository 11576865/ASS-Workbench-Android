#!/usr/bin/env python3
"""Defensive tests: reject missing, reordered, duplicated and forged PNG chunks."""
import base64
import hashlib
import unittest

from extract_r3_png_logcat import extract_capture

NAME = "ASS.R3.FIXED.PRODUCTION_REFERENCE.png"
PAYLOAD = b"\x89PNG\r\n\x1a\n" + bytes(range(256)) * 8


def log_for(payload=PAYLOAD):
    parts = [base64.b64encode(payload).decode("ascii")[i:i+35]
             for i in range(0, len(base64.b64encode(payload)), 35)]
    return (
        f"10-14 I AsswbVisual: R3_PNG_BEGIN:{NAME}:{len(payload)}:"
        f"{hashlib.sha256(payload).hexdigest()}:{len(parts)}\n" +
        "".join(f"10-14 I AsswbVisual: R3_PNG:{i}:{part}\n"
                for i, part in enumerate(parts)) +
        f"10-14 I AsswbVisual: R3_PNG_END:{NAME}\n"
    )


class R3LogcatTransportTest(unittest.TestCase):
    def test_exact_complete_envelope(self):
        self.assertEqual(PAYLOAD, extract_capture(log_for(), NAME))

    def test_missing_part_fails(self):
        lines = log_for().splitlines()
        lines.pop(5)
        with self.assertRaisesRegex(ValueError, "missing|order"):
            extract_capture("\n".join(lines), NAME)

    def test_checksum_forgery_fails(self):
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            extract_capture(log_for().replace(hashlib.sha256(PAYLOAD).hexdigest(), "a"*64), NAME)

    def test_duplicate_capture_fails(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            extract_capture(log_for() + log_for(), NAME)

    def test_partial_trailing_segment_fails(self):
        with self.assertRaisesRegex(ValueError, "no matching END"):
            extract_capture(log_for().split("R3_PNG_END")[0], NAME)

    def test_foreign_preview_is_not_full_png(self):
        with self.assertRaisesRegex(ValueError, "found 0"):
            extract_capture("I AsswbVisual: R3_PREVIEW_JPEG:0:/9j/\n", NAME)


if __name__ == "__main__":
    unittest.main()
