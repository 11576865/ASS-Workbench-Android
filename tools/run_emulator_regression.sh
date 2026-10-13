#!/usr/bin/env bash
set -uo pipefail

runner_tools_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
report_index=0
attempt_results_ready=false
mkdir -p diagnostics/instrumentation || exit 1
report_session_dir="$(mktemp -d diagnostics/instrumentation/run-XXXXXX)" || exit 1

report_results() {
  report_index=$((report_index + 1))
  echo "---- instrumentation failure summary (attempt $report_index) ----"
  local result_dir="app/build/outputs/androidTest-results/connected"
  local archive_dir="$report_session_dir/attempt-$report_index"
  if [[ "$attempt_results_ready" != true ]]; then
    echo "NO XML RESULTS: this attempt could not isolate its result directory"
    return
  fi
  if [[ -d "$result_dir" ]]; then
    mkdir -p "$archive_dir"
    cp -R "$result_dir/." "$archive_dir/"
  fi
  python3 "$runner_tools_dir/summarize_instrumentation.py" "$archive_dir" || true
  echo "---- instrumentation logcat tails ----"
  find app/build/outputs/androidTest-results/connected -name 'logcat-*.txt' -type f -print | while read -r file; do
    echo "===== $file ====="
    tail -n 180 "$file" || true
  done
  # Empty XML failures can indicate a process crash; retain the crash buffer too.
  timeout 10s adb logcat -d -b crash || true
  timeout 10s adb logcat -d -s AsswbRegression:I TestRunner:I AndroidRuntime:E '*:S' || true
  timeout 10s adb logcat -d -s AsswbVisual:I '*:S' || true
}

run_attempt() {
  local limit="$1"
  shift
  echo "---- instrumentation attempt (deadline $limit): $* ----"
  # The previous attempt was archived before this one starts. A compile or
  # connection failure must not relabel those old XML files as current evidence.
  attempt_results_ready=false
  if ! rm -rf -- app/build/outputs/androidTest-results/connected; then
    echo "Cannot isolate current instrumentation results; failing this attempt"
    return 1
  fi
  attempt_results_ready=true
  timeout --kill-after=30s "$limit" gradle :app:connectedDebugAndroidTest "$@" --stacktrace
  local status=$?
  if [[ "$status" -eq 124 || "$status" -eq 137 ]]; then
    echo "---- instrumentation timed out or was killed; collecting state before cleanup ----"
    local app_pid
    app_pid=$(timeout 10s adb shell pidof io.github.assworkbench.app 2>/dev/null || true)
    if [[ "$app_pid" =~ ^[0-9]+$ ]]; then
      timeout 10s adb shell run-as io.github.assworkbench.app kill -3 "$app_pid" || true
    fi
    timeout 10s adb shell dumpsys activity lastanr || true
    timeout 10s adb shell dumpsys activity top || true
    # A timeout is a failed gate. Stop the old instrumentation before the next
    # independent attempt; otherwise its process could contaminate that attempt.
    timeout 10s adb shell am force-stop io.github.assworkbench.app.test || true
    timeout 10s adb shell am force-stop io.github.assworkbench.app || true
  fi
  return "$status"
}

# Run the projection regressions first, independently of the full editor suite.
# Report immediately because the second Gradle invocation overwrites the output.
projection_status=0
projection_classes=(
  io.github.assworkbench.app.WorkspaceParameterProjectionInstrumentedTest
  io.github.assworkbench.app.WorkspacePositionProjectionInstrumentedTest
  io.github.assworkbench.app.WorkspaceTransformProjectionInstrumentedTest
  io.github.assworkbench.app.WorkspaceParameterExtractionInstrumentedTest
)
# The comma-separated class filter only ran the first class in observed CI.
# Separate invocations guarantee every family runs before the full editor suite.
attempt_limit="${ASSWB_INITIAL_TEST_TIMEOUT:-8m}"
for projection_class in "${projection_classes[@]}"; do
  run_attempt "$attempt_limit" "-Pandroid.testInstrumentationRunnerArguments.class=$projection_class"
  attempt_status=$?
  if [[ "$projection_status" -eq 0 && "$attempt_status" -ne 0 ]]; then projection_status=$attempt_status; fi
  report_results
  attempt_limit="${ASSWB_FOCUSED_TEST_TIMEOUT:-3m}"
done

# Keep the full suite as a mandatory gate, even if the focused run fails.
run_attempt "${ASSWB_SUITE_TEST_TIMEOUT:-12m}"
suite_status=$?
report_results

if [[ "$projection_status" -ne 0 ]]; then exit "$projection_status"; fi
exit "$suite_status"
