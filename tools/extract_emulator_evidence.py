#!/usr/bin/env python3
"""Extract existing per-attempt summaries; never infer a regression pass."""
from pathlib import Path
import re
import sys

TIMESTAMP = re.compile(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z ")
START = re.compile(r"^---- instrumentation failure summary \(attempt \d+\) ----$")
END = "---- instrumentation logcat tails ----"
TRACE_LIMIT = 1200
TERMINAL_SEQUENCE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]|\x1b\][^\x07\x1b]*(?:\x07|\x1b\\)")
CONTROL = re.compile(r"[\x00-\x08\x0b-\x1f\x7f]")


def extract(path: Path) -> int:
    active = False
    count = 0
    incomplete = False
    remaining = TRACE_LIMIT
    truncated = False
    with path.open(encoding="utf-8", errors="replace") as source:
        for raw in source:
            clean = CONTROL.sub("", TERMINAL_SEQUENCE.sub("", raw.rstrip("\r\n")))
            line = TIMESTAMP.sub("", clean)
            if START.fullmatch(line):
                if active:
                    print("EVIDENCE INCOMPLETE ATTEMPT SUMMARY: previous block never ended")
                    incomplete = True
                active = True
                count += 1
                remaining = TRACE_LIMIT
                truncated = False
                print("EVIDENCE " + line)
            elif active and line == END:
                active = False
            elif active:
                if line.startswith(("FAIL ", "ERROR ", "RESULT ", "NO XML RESULTS:",
                                    "UNREADABLE XML:", "INCOMPLETE COUNTS:")):
                    print("EVIDENCE " + line)
                    remaining = TRACE_LIMIT
                    truncated = False
                elif remaining:
                    partial = len(line) > remaining
                    print("EVIDENCE " + line[:remaining])
                    remaining = max(0, remaining - len(line) - 1)
                    if partial:
                        print("EVIDENCE [trace truncated; original source job log retained]")
                        truncated = True
                elif not truncated:
                    print("EVIDENCE [trace truncated; original source job log retained]")
                    truncated = True
    if not count:
        print("EVIDENCE NO ATTEMPT SUMMARY: consult source job; no pass inferred")
        return 1
    if active or incomplete:
        print("EVIDENCE INCOMPLETE ATTEMPT SUMMARY: consult source job; no pass inferred")
        return 1
    print(f"EVIDENCE extracted {count} attempt summaries; source job owns regression status")
    return 0


if __name__ == "__main__":
    raise SystemExit(extract(Path(sys.argv[1])))
