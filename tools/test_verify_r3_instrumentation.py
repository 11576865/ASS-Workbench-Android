#!/usr/bin/env python3
"""Confirm the exact one-test Android instrumentation receipt is not a false green."""
import tempfile
import unittest
from pathlib import Path
from verify_r3_instrumentation import validate_output, R3_TEST

PASS = f"""INSTRUMENTATION_STATUS: test={R3_TEST}
INSTRUMENTATION_STATUS: class=io.github.assworkbench.app.UigsVisualCaptureInstrumentedTest
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: test={R3_TEST}
INSTRUMENTATION_STATUS_CODE: 0

Time: 4.13

OK (1 test)

INSTRUMENTATION_CODE: -1
"""

class R3InstrumentReceiptTest(unittest.TestCase):
    def test_actual_single_test_pass(self):
        validate_output(PASS)

    def test_fails_when_another_test_ran(self):
        with self.assertRaisesRegex(ValueError, "requested R3 test"):
            validate_output(PASS.replace(R3_TEST, "someUnrelatedTest"))

    def test_fails_without_pass_marker(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            validate_output(PASS.replace("OK (1 test)", "FAILURES!!!"))

    def test_fails_on_failure_exit_even_if_ok_text_present(self):
        with self.assertRaisesRegex(ValueError, "success code"):
            validate_output(PASS.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"))

    def test_fails_when_multiple_tests_pass(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            validate_output(PASS.replace("OK (1 test)", "OK (2 tests)"))

if __name__ == "__main__":
    unittest.main()
