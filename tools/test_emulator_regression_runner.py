"""Verify focused instrumentation coverage and failure propagation without an SDK."""
import os
from pathlib import Path
import subprocess
import signal
import tempfile
import unittest

CLASSES = [
    "io.github.assworkbench.app.WorkspaceParameterProjectionInstrumentedTest",
    "io.github.assworkbench.app.WorkspacePositionProjectionInstrumentedTest",
    "io.github.assworkbench.app.WorkspaceTransformProjectionInstrumentedTest",
    "io.github.assworkbench.app.WorkspaceParameterExtractionInstrumentedTest",
]
SCRIPT = Path(__file__).with_name("run_emulator_regression.sh").resolve()


class EmulatorRegressionRunnerTest(unittest.TestCase):
    def run_gate(self, failure, hang=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle").write_text('''#!/usr/bin/env python3
import os, sys
from pathlib import Path
arg = next((x for x in sys.argv if x.startswith("-Pandroid.testInstrumentationRunnerArguments.class=")), "suite")
with Path(os.environ["TEST_RUN_LOG"]).open("a") as log:
    log.write(arg + "\\n")
if os.environ.get("TEST_HANG") == arg:
    import time
    time.sleep(30)
sys.exit(7 if os.environ.get("TEST_FAILURE") == arg else 0)
''')
            (root / "adb").write_text("#!/bin/sh\nexit 0\n")
            for name in ["gradle", "adb"]:
                (root / name).chmod(0o755)
            log = root / "calls"
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"],
                       TEST_RUN_LOG=str(log), TEST_FAILURE="" if hang else failure,
                       TEST_HANG=failure if hang else "",
                       ASSWB_INITIAL_TEST_TIMEOUT="2s", ASSWB_FOCUSED_TEST_TIMEOUT="2s",
                       ASSWB_SUITE_TEST_TIMEOUT="2s")
            process = subprocess.Popen(["bash", str(SCRIPT)], cwd=root, env=env,
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
            try:
                process.communicate(timeout=15)
                status = process.returncode
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.communicate()
                status = None
            return status, log.read_text().splitlines()

    def test_each_projection_class_has_an_independent_run_before_full_suite(self):
        status, calls = self.run_gate("")
        self.assertEqual(0, status)
        self.assertEqual(["-Pandroid.testInstrumentationRunnerArguments.class=" + c for c in CLASSES] + ["suite"], calls)

    def test_failure_in_any_attempt_keeps_all_other_attempts_and_fails_gate(self):
        expected = ["-Pandroid.testInstrumentationRunnerArguments.class=" + c for c in CLASSES] + ["suite"]
        for failure in expected:
            with self.subTest(failure=failure):
                status, calls = self.run_gate(failure)
                self.assertEqual(expected, calls)
                self.assertEqual(7, status)

    def test_hung_attempt_is_bounded_and_all_remaining_attempts_run(self):
        expected = ["-Pandroid.testInstrumentationRunnerArguments.class=" + c for c in CLASSES] + ["suite"]
        for failure in [expected[0], expected[2], "suite"]:
            with self.subTest(hang=failure):
                status, calls = self.run_gate(failure, hang=True)
                self.assertEqual(124, status)
                self.assertEqual(expected, calls)


if __name__ == "__main__":
    unittest.main()
