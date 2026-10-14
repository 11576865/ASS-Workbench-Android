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


def validate_output(raw: str) -> None:
    if R3_TEST not in raw:
        raise ValueError("the requested R3 test is absent from instrumentation output")
    if not re.search(r"(?m)^OK \(1 test\)\s*$", raw):
        raise ValueError("AndroidJUnitRunner did not report exactly one passing test")
    if not re.search(r"(?m)^INSTRUMENTATION_CODE: -1\s*$", raw):
        raise ValueError("Android instrumentation did not finish with success code -1")
    if any(token in raw for token in ("FAILURES!!!", "INSTRUMENTATION_ABORTED", "INSTRUMENTATION_FAILED")):
        raise ValueError("Android instrumentation contains a failure marker")


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
