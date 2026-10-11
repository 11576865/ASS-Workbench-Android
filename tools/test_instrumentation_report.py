"""The diagnostic report must stay readable without hiding failed test identity."""
import subprocess
import sys
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).with_name("summarize_instrumentation.py")


class InstrumentationReportTest(unittest.TestCase):
    def report(self, files):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
            result = subprocess.run([sys.executable, str(SCRIPT), directory], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            for name, content in files.items():
                self.assertEqual(content, (root / name).read_text())
            return result.stdout

    def test_reports_failure_and_error_with_names_and_stack(self):
        output = self.report({"device/TEST-suite.xml": '''<testsuite tests="3" failures="1" errors="1" skipped="0">
<testcase classname="Editor" name="safe"/>
<testcase classname="Editor" name="touch"><failure message="Button below keyboard">AssertionError: Button below keyboard\nat Editor.kt:12</failure></testcase>
<testcase classname="Canvas" name="cancel"><error message="Crash">IllegalStateException\nat Canvas.kt:8</error></testcase>
</testsuite>'''})
        self.assertIn("tests=3 failures=1 errors=1 skipped=0", output)
        self.assertIn("FAIL Editor#touch", output)
        self.assertIn("ERROR Canvas#cancel", output)
        self.assertIn("Editor.kt:12", output)
        self.assertIn("Canvas.kt:8", output)

    def test_success_output_is_omitted_without_changing_raw_xml(self):
        output = self.report({"TEST-ok.xml": '<testsuite tests="1" failures="0" errors="0"><testcase name="capture"><system-out>' + 'IMAGE_PAYLOAD' * 100000 + '</system-out></testcase></testsuite>'})
        self.assertIn("tests=1", output)
        self.assertNotIn("IMAGE_PAYLOAD", output)
        self.assertLess(len(output), 1000)

    def test_each_failure_identity_survives_bounded_stack(self):
        output = self.report({"TEST-long.xml": '<testsuite tests="2" failures="2"><testcase classname="A" name="first"><failure>' + 'x' * 100000 + '</failure></testcase><testcase classname="B" name="second"><failure>Second assertion</failure></testcase></testsuite>'})
        self.assertIn("FAIL A#first", output)
        self.assertIn("trace truncated; raw XML retained", output)
        self.assertIn("FAIL B#second", output)
        self.assertIn("Second assertion", output)
        self.assertLess(len(output), 10000)

    def test_missing_or_malformed_xml_is_not_reported_as_pass(self):
        self.assertIn("NO XML RESULTS", self.report({}))
        output = self.report({"TEST-broken.xml": '<testsuite tests="999"'})
        self.assertIn("UNREADABLE XML", output)
        self.assertNotIn("PASS", output)

    def test_nested_suites_do_not_double_count_test_cases(self):
        output = self.report({"TEST-nested.xml": '<testsuites><testsuite tests="1" failures="0"><testcase classname="Editor" name="safe"/></testsuite><testsuite tests="1" failures="1"><testcase classname="Canvas" name="cancel"><failure>cancel failed</failure></testcase></testsuite></testsuites>'})
        self.assertIn("tests=2 failures=1 errors=0 skipped=0", output)
        self.assertEqual(1, output.count("FAIL Canvas#cancel"))


if __name__ == "__main__":
    unittest.main()
