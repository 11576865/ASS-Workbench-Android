#!/usr/bin/env bash
set -uo pipefail

set +e
gradle :app:connectedDebugAndroidTest --stacktrace
status=$?
set -e

echo "---- instrumentation XML ----"
find app/build/outputs/androidTest-results/connected -name 'TEST-*.xml' -type f -print -exec cat {} \; || true

echo "---- instrumentation logcat tails ----"
find app/build/outputs/androidTest-results/connected -name 'logcat-*.txt' -type f -print | while read -r file; do
  echo "===== $file ====="
  tail -n 180 "$file" || true
done

exit "$status"
