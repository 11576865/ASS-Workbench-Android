"""A separate report must retain failures without replaying screenshot logs."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("extract_emulator_evidence.py")


class EmulatorEvidenceTest(unittest.TestCase):
    def report(self, log):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "job.log"
            path.write_text(log)
            return subprocess.run([sys.executable, str(SCRIPT), str(path)],
                                  capture_output=True, text=True)

    def test_timestamped_summary_excludes_screenshot_and_logcat_payloads(self):
        result = self.report("2026-10-12T02:00:00.0000000Z IMAGE_PAYLOAD\n"
            "2026-10-12T02:00:01.0000000Z ---- instrumentation failure summary (attempt 1) ----\n"
            "2026-10-12T02:00:02.0000000Z RESULT raw.xml: tests=2 failures=1 errors=0 skipped=0\n"
            "2026-10-12T02:00:03.0000000Z FAIL Editor#touch: invisible button\n"
            "2026-10-12T02:00:04.0000000Z at Editor.kt:42\n"
            "2026-10-12T02:00:05.0000000Z ---- instrumentation logcat tails ----\n"
            "2026-10-12T02:00:06.0000000Z IMAGE_PAYLOAD\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("FAIL Editor#touch", result.stdout)
        self.assertIn("Editor.kt:42", result.stdout)
        self.assertNotIn("IMAGE_PAYLOAD", result.stdout)

    def test_all_attempts_and_missing_xml_remain_distinct(self):
        result = self.report("---- instrumentation failure summary (attempt 1) ----\n"
            "FAIL A#one: assertion\ntrace\n---- instrumentation logcat tails ----\n"
            "---- instrumentation failure summary (attempt 2) ----\n"
            "NO XML RESULTS: attempt 2\n---- instrumentation logcat tails ----\n")
        self.assertEqual(0, result.returncode)
        self.assertIn("attempt 1", result.stdout)
        self.assertIn("attempt 2", result.stdout)
        self.assertIn("NO XML RESULTS", result.stdout)

    def test_no_summary_fails_explicitly_without_printing_raw_log(self):
        result = self.report("IMAGE_PAYLOAD\nGradle compile failed\n")
        self.assertEqual(1, result.returncode)
        self.assertIn("NO ATTEMPT SUMMARY", result.stdout)
        self.assertNotIn("IMAGE_PAYLOAD", result.stdout)

    def test_trace_limit_does_not_hide_next_failure_identity(self):
        result = self.report("---- instrumentation failure summary (attempt 1) ----\n"
            "FAIL A#one: assertion\n" + "x" * 100000 + "\n"
            "ERROR B#two: crash\nat B.kt:1\n---- instrumentation logcat tails ----\n")
        self.assertEqual(0, result.returncode)
        self.assertIn("FAIL A#one", result.stdout)
        self.assertIn("ERROR B#two", result.stdout)
        self.assertIn("trace truncated", result.stdout)
        self.assertLess(len(result.stdout), 4000)

    def test_unterminated_summary_is_marked_incomplete(self):
        result = self.report("---- instrumentation failure summary (attempt 1) ----\nFAIL A#one: assertion\n")
        self.assertEqual(1, result.returncode)
        self.assertIn("INCOMPLETE ATTEMPT SUMMARY", result.stdout)
        self.assertIn("FAIL A#one", result.stdout)

    def test_final_partial_trace_line_is_marked_truncated(self):
        result = self.report("---- instrumentation failure summary (attempt 1) ----\n"
            "FAIL A#one: assertion\n" + ("x" * 700 + "\n") * 2
            + "---- instrumentation logcat tails ----\n")
        self.assertEqual(0, result.returncode)
        self.assertIn("trace truncated", result.stdout)


if __name__ == "__main__":
    unittest.main()
