#!/usr/bin/env python3
"""Read-only, bounded diagnostics. The Gradle attempt exit status owns the gate."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

TRACE_LIMIT = 6000
MESSAGE_LIMIT = 500


def summarize(root: Path) -> None:
    files = sorted(root.rglob("TEST-*.xml")) if root.is_dir() else []
    if not files:
        print(f"NO XML RESULTS: {root}; consult Gradle/crash diagnostics, no pass inferred")
        return
    for path in files:
        try:
            tree = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            print(f"UNREADABLE XML: {path}: {error}")
            continue
        cases = list(tree.iter("testcase"))
        failures = sum(case.find("failure") is not None for case in cases)
        errors = sum(case.find("error") is not None for case in cases)
        skipped = sum(case.find("skipped") is not None for case in cases)
        print(f"RESULT {path}: tests={len(cases)} failures={failures} errors={errors} skipped={skipped}")
        # A partial runner report can declare more cases than it actually records.
        leaf_suites = [suite for suite in tree.iter("testsuite") if suite.find("testsuite") is None]
        declared = [suite.get("tests", "") for suite in leaf_suites]
        if declared and all(value.isdigit() for value in declared):
            total = sum(int(value) for value in declared)
            if total != len(cases):
                print(f"INCOMPLETE COUNTS: declared={total} recorded={len(cases)}; no full-suite pass inferred")
        for case in cases:
            for tag, label in (("failure", "FAIL"), ("error", "ERROR")):
                for issue in case.findall(tag):
                    identity = f"{case.get('classname', '?')}#{case.get('name', '?')}"
                    message = issue.get("message", "")[:MESSAGE_LIMIT]
                    print(f"{label} {identity}: {message}")
                    trace = "".join(issue.itertext()).strip()
                    print(trace[:TRACE_LIMIT])
                    if len(trace) > TRACE_LIMIT:
                        print(f"[trace truncated; raw XML retained at {path}]")


if __name__ == "__main__":
    summarize(Path(sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/androidTest-results/connected"))
