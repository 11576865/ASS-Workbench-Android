#!/usr/bin/env python3
"""Regression tests for strict R3 compositor screenshot acceptance."""
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

from verify_r3_png_capture import check_capture, SIGNATURE


def chunk(kind: bytes, data: bytes) -> bytes:
    return (struct.pack(">I", len(data)) + kind + data +
            struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))


def fixture(width: int, height: int) -> bytes:
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    raw = (b"\x00" + bytes(width * 4)) * height
    return (SIGNATURE + chunk(b"IHDR", header) +
            chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


class R3PngEvidenceTest(unittest.TestCase):
    def check(self, payload: bytes, width: int = 1600, height: int = 960):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "capture.png"
            path.write_bytes(payload)
            return check_capture(path, width, height)

    def test_real_png_correct_reference_dimensions(self):
        self.assertIn("valid compositor PNG", self.check(fixture(1600, 960)))

    def test_adb_plain_text_rejected_even_when_nonempty(self):
        with self.assertRaisesRegex(ValueError, "not a real compositor PNG"):
            self.check(b"run-as: package not debuggable or file not found!\n")

    def test_portrait_rotation_not_accepted_as_landscape(self):
        with self.assertRaisesRegex(ValueError, "wrong screenshot dimensions"):
            self.check(fixture(960, 1600))

    def test_corrupted_pixel_data_rejected(self):
        data = bytearray(fixture(1600, 960))
        i = data.index(b"IDAT") + 10
        data[i] ^= 0x12
        with self.assertRaisesRegex(ValueError, "bad PNG CRC"):
            self.check(data)

    def test_truncated_stream_rejected(self):
        with self.assertRaisesRegex(ValueError, "truncated PNG chunk|missing"):
            self.check(fixture(1600, 960)[:-12])


if __name__ == "__main__":
    unittest.main()
