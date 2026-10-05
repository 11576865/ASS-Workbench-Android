"""Verify focused instrumentation coverage and failure propagation without an SDK."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

CLASSES = [
    "io.github.assworkbench.app.WorkspaceParameterProjectionInstrumentedTest",
    "io.github.assworkbench.app.WorkspacePositionProjectionInstrumentedTest",
    "io.github.assworkbench.app.WorkspaceTransformProjectionInstrumentedTest",
]
SCRIPT = Path(__file__).with_name("run_emulator_regression.sh").resolve()


class EmulatorRegressionRunnerTest(unittest.TestCase):
    def run_gate(self, failure):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle").write_text('''#!/usr/bin/env python3
import os, sys
from pathlib import Path
arg = next((x for x in sys.argv if x.startswith("-Pandroid.testInstrumentationRunnerArguments.class=")), "suite")
with Path(os.environ["TEST_RUN_LOG"]).open("a") as log:
    log.write(arg + "\\n")
sys.exit(7 if os.environ.get("TEST_FAILURE") == arg else 0)
''')
            (root / "adb").write_text("#!/bin/sh\nexit 0\n")
            for name in ["gradle", "adb"]:
                (root / name).chmod(0o755)
            log = root / "calls"
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"],
                       TEST_RUN_LOG=str(log), TEST_FAILURE=failure)
            result = subprocess.run(["bash", str(SCRIPT)], cwd=root, env=env, capture_output=True)
            return result.returncode, log.read_text().splitlines()

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


if __name__ == "__main__":
    unittest.main()
