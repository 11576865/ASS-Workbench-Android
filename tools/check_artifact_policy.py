#!/usr/bin/env python3
from pathlib import Path
import sys

errors = []
for path in sorted(list(Path(".github/workflows").glob("*.yml")) + list(Path(".github/workflows").glob("*.yaml"))):
    lines = path.read_text(encoding="utf-8").splitlines()
    for i, line in enumerate(lines):
        if "uses: actions/upload-artifact@" not in line:
            continue
        start = max(0, i - 4)
        end = min(len(lines), i + 12)
        block = "\n".join(lines[start:end])
        manual_only = (
            "github.event_name == 'workflow_dispatch'" in block
            or 'github.event_name == "workflow_dispatch"' in block
        )
        if not manual_only:
            errors.append(f"{path}:{i+1}: upload-artifact must be workflow_dispatch-only")
        if "retention-days: 1" not in block:
            errors.append(f"{path}:{i+1}: upload-artifact must use retention-days: 1")

if errors:
    print("Actions artifact policy violation:", file=sys.stderr)
    for error in errors:
        print(" - " + error, file=sys.stderr)
    raise SystemExit(1)

print("Actions artifact policy OK")
