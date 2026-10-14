#!/usr/bin/env python3
"""Fail-closed check for a single direct AndroidJUnitRunner R3 screenshot test.

adb shell am instrument may return status 0 on a failed instrumentation test.
Use the real instrumentation report and exit status as two separate gates.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

R3_TEST = "captureR3FixedReferenceWhenExpanded"


R3_CLASS = "io.github.assworkbench.app.UigsVisualCaptureInstrumentedTest"


def validate_output(raw: str) -> None:
    # The AndroidJUnitRunner text output on API 35 has two valid formats:
    # verbose INSTRUMENTATION_STATUS: test=... + INSTRUMENTATION_CODE:-1,
    # or default human JUnit summary "Class:. / Time / OK (1 test)".
    # We intentionally invoke exactly one test with `am instrument -e class`.
    # Accept the terse report only for the exact class, not any passing suite.
    if any(token in raw for token in (
        "FAILURES!!!", "INSTRUMENTATION_ABORTED", "INSTRUMENTATION_FAILED",
        "INSTRUMENTATION_STATUS_CODE: -2", "INSTRUMENTATION_RESULT: shortMsg=",
    )):
        raise ValueError("Android instrumentation contains a failure marker")
    if not re.search(r"(?m)^OK \(1 test\)[ \t]*$", raw):
        raise ValueError("AndroidJUnitRunner did not report exactly one passing test")

    if "INSTRUMENTATION_STATUS:" in raw:
        if R3_TEST not in raw or f"class={R3_CLASS}" not in raw:
            raise ValueError("the requested R3 test is absent from instrumentation output")
        if not re.search(r"(?m)^INSTRUMENTATION_CODE: -1[ \t]*$", raw):
            raise ValueError("Android instrumentation did not finish with success code -1")
        return

    if R3_TEST in raw and not re.search(r"(?m)^INSTRUMENTATION_CODE: -1[ \t]*$", raw):
        # A partially truncated verbose receipt must not be treated as terse.
        raise ValueError("Android instrumentation did not finish with success code -1")
    if not re.search(
        rf"(?m)^{re.escape(R3_CLASS)}:\.[ \t]*$", raw
    ):
        raise ValueError("the requested R3 test is absent from instrumentation output")
    if not re.search(r"(?m)^Time: [0-9]+(?:\.[0-9]+)?[ \t]*$", raw):
        raise ValueError("AndroidJUnitRunner terse summary omitted timing evidence")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("result", type=Path)
    args = parser.parse_args()
    try:
        validate_output(args.result.read_text(encoding="utf-8", errors="replace"))
    except (ValueError, OSError) as exc:
        print(f"R3_NATIVE_TEST_FAILED: {exc}", file=sys.stderr)
        return 1
    print("R3_NATIVE_TEST_PASSED: exactly one Android compositor capture test")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
