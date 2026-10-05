#!/usr/bin/env bash
set -uo pipefail

report_results() {
  echo "---- instrumentation XML ----"
  find app/build/outputs/androidTest-results/connected -name 'TEST-*.xml' -type f -print -exec cat {} \; || true
  echo "---- instrumentation logcat tails ----"
  find app/build/outputs/androidTest-results/connected -name 'logcat-*.txt' -type f -print | while read -r file; do
    echo "===== $file ====="
    tail -n 180 "$file" || true
  done
  # Empty XML failures can indicate a process crash; retain the crash buffer too.
  adb logcat -d -b crash || true
  adb logcat -d -s AsswbVisual:I '*:S' || true
}

# Run the projection regressions first, independently of the full editor suite.
# Report immediately because the second Gradle invocation overwrites the output.
projection_status=0
projection_classes=(
  io.github.assworkbench.app.WorkspaceParameterProjectionInstrumentedTest
  io.github.assworkbench.app.WorkspacePositionProjectionInstrumentedTest
  io.github.assworkbench.app.WorkspaceTransformProjectionInstrumentedTest
)
# The comma-separated class filter only ran the first class in observed CI.
# Separate invocations guarantee every family runs before the full editor suite.
for projection_class in "${projection_classes[@]}"; do
  gradle :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$projection_class" --stacktrace
  attempt_status=$?
  if [[ "$projection_status" -eq 0 && "$attempt_status" -ne 0 ]]; then projection_status=$attempt_status; fi
  report_results
done

# Keep the full suite as a mandatory gate, even if the focused run fails.
gradle :app:connectedDebugAndroidTest --stacktrace
suite_status=$?
report_results

if [[ "$projection_status" -ne 0 ]]; then exit "$projection_status"; fi
exit "$suite_status"
