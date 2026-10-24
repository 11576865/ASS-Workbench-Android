import base64
import hashlib
import io
import random
import struct
import unittest
import zlib
from contextlib import redirect_stdout

from emit_native_capture_evidence import emit_capture
from extract_r3_png_logcat import extract_capture


def png(width=32, height=32):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    rows = b''.join(b'\0' + random.Random(y).randbytes(width * 3) for y in range(height))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


def envelope(payload):
    name = 'ASS.V11.FIXED.32x32.png'
    digest = hashlib.sha256(payload).hexdigest()
    encoded = base64.b64encode(payload).decode()
    return (f'V11_PNG_BEGIN:{name}:{len(payload)}:{digest}:1\nV11_PNG:0:{encoded}\n'
            f'V11_PNG_END:{name}\nV11_NATIVE_CAPTURE_VALID: file={name} sha256={digest} size={len(payload)}\n')


class NativeCaptureEvidenceTest(unittest.TestCase):
    def test_verified_full_png_round_trip(self):
        payload = png()
        output = io.StringIO()
        with redirect_stdout(output):
            emit_capture(envelope(payload), 32, 32)
        self.assertEqual(payload, extract_capture(output.getvalue().replace('V11_PNG', 'R3_PNG'), 'ASS.V11.FIXED.32x32.png'))

    def test_correct_hash_does_not_make_wrong_dimensions_valid(self):
        with self.assertRaisesRegex(ValueError, 'dimensions'):
            emit_capture(envelope(png(33, 32)), 32, 32)

    def test_android_logcat_envelope_is_not_runner_file_evidence(self):
        with self.assertRaisesRegex(ValueError, 'found 0'):
            emit_capture(envelope(png()).replace('V11_PNG', 'R3_PNG'), 32, 32)

    def test_missing_gate_receipt_rejected(self):
        with self.assertRaisesRegex(ValueError, 'receipt'):
            emit_capture(envelope(png()).split('V11_NATIVE_CAPTURE_VALID:')[0], 32, 32)

    def test_forged_receipt_rejected(self):
        log = envelope(png()).replace('sha256=', 'sha256=' + '0' * 64)
        with self.assertRaisesRegex(ValueError, 'receipt'):
            emit_capture(log, 32, 32)

    def test_missing_chunk_rejected(self):
        log = '\n'.join(line for line in envelope(png()).splitlines() if not line.startswith('V11_PNG:'))
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            emit_capture(log, 32, 32)

    def test_valid_envelope_with_invalid_png_rejected(self):
        with self.assertRaisesRegex(ValueError, 'PNG'):
            emit_capture(envelope(b'x' * 2048), 32, 32)


if __name__ == '__main__':
    unittest.main()
