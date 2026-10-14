#!/usr/bin/env python3
"""Confirm the exact one-test Android instrumentation receipt is not a false green."""
import tempfile
import unittest
from pathlib import Path
from verify_r3_instrumentation import validate_output, R3_TEST, R3_CLASS

PASS = f"""INSTRUMENTATION_STATUS: test={R3_TEST}
INSTRUMENTATION_STATUS: class=io.github.assworkbench.app.UigsVisualCaptureInstrumentedTest
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: test={R3_TEST}
INSTRUMENTATION_STATUS_CODE: 0

Time: 4.13

OK (1 test)

INSTRUMENTATION_CODE: -1
"""

TERSE_PASS = f"""
{R3_CLASS}:.

Time: 14.74

OK (1 test)

"""

class R3InstrumentReceiptTest(unittest.TestCase):
    def test_api35_android_junit_runner_real_terse_receipt(self):
        # Actual emulator log on PR #148 run 38590886531.
        validate_output(TERSE_PASS)

    def test_terse_wrong_class_rejected(self):
        with self.assertRaisesRegex(ValueError, "requested R3 test"):
            validate_output(TERSE_PASS.replace(R3_CLASS, "com.example.OtherTest"))

    def test_terse_without_one_test_dot_rejected(self):
        with self.assertRaisesRegex(ValueError, "requested R3 test"):
            validate_output(TERSE_PASS.replace(R3_CLASS + ":.", R3_CLASS + ":"))

    def test_terse_without_time_rejected(self):
        with self.assertRaisesRegex(ValueError, "timing"):
            validate_output(TERSE_PASS.replace("Time: 14.74", ""))

    def test_terse_two_tests_rejected(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            validate_output(TERSE_PASS.replace("OK (1 test)", "OK (2 tests)"))

    def test_actual_single_test_pass(self):
        validate_output(PASS)

    def test_fails_when_another_test_ran(self):
        with self.assertRaisesRegex(ValueError, "requested R3 test"):
            validate_output(PASS.replace(R3_TEST, "someUnrelatedTest"))

    def test_fails_without_pass_marker(self):
        with self.assertRaisesRegex(ValueError, "failure marker"):
            validate_output(PASS.replace("OK (1 test)", "FAILURES!!!"))

    def test_fails_on_failure_exit_even_if_ok_text_present(self):
        with self.assertRaisesRegex(ValueError, "success code"):
            validate_output(PASS.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_CODE: 0"))

    def test_fails_when_multiple_tests_pass(self):
        with self.assertRaisesRegex(ValueError, "exactly one"):
            validate_output(PASS.replace("OK (1 test)", "OK (2 tests)"))

if __name__ == "__main__":
    unittest.main()
