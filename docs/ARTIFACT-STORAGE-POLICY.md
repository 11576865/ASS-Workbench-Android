# Actions Artifact Storage Policy

This repository treats GitHub Actions artifacts as temporary diagnostics, not archival storage.

## Rules

1. Normal `push` and `pull_request` workflows must not upload Actions artifacts.
2. `actions/upload-artifact` is permitted only for explicit `workflow_dispatch` runs.
3. Every permitted Actions artifact must set `retention-days: 1`.
4. Long-lived APKs, build identity manifests and device-test fixture bundles belong in GitHub Release, not Actions artifacts.
5. Actions Cache is separate from Actions artifacts and is not disabled by this policy.
6. One-time artifact cleanup must not leave permanent `actions: write` permission in routine CI.
7. If a future workflow genuinely requires artifacts for cross-job transport, this policy must be deliberately revised first; do not silently add an exception.

## Enforcement

`tools/check_artifact_policy.py` scans workflow YAML files and fails CI if an `upload-artifact` step is not restricted to `workflow_dispatch` or does not use one-day retention.

This policy is intended to be reusable for future repositories. Copy it and the checker early, then adjust only when a repository has a documented reason to retain Actions artifacts.
