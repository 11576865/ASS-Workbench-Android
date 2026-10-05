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
projection_classes="io.github.assworkbench.app.WorkspaceParameterProjectionInstrumentedTest,io.github.assworkbench.app.WorkspacePositionProjectionInstrumentedTest,io.github.assworkbench.app.WorkspaceTransformProjectionInstrumentedTest"
gradle :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=$projection_classes" --stacktrace
projection_status=$?
report_results

# Keep the full suite as a mandatory gate, even if the focused run fails.
gradle :app:connectedDebugAndroidTest --stacktrace
suite_status=$?
report_results

if [[ "$projection_status" -ne 0 ]]; then exit "$projection_status"; fi
exit "$suite_status"
