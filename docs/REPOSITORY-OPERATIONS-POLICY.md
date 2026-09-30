# Repository Operations Policy

This policy defines how ASS Workbench Android should handle GitHub repository operations and how the same operating discipline should be carried into future repositories.

It exists because repository access has more than one failure layer: GitHub itself can reject an operation, while the ChatGPT connector/tool layer can also block an otherwise authorized GitHub operation before GitHub receives it. Those cases must not be conflated.

## 1. Distinguish the failure layer

When a repository operation fails, classify the failure before changing the plan.

### A. GitHub-originated failure

Examples include an explicit GitHub API response such as:

- 401 / authentication failure;
- 403 / insufficient permission;
- branch protection or ruleset rejection;
- repository/file/ref not found;
- update conflict caused by a stale blob SHA or branch head.

Treat these as repository/authentication/configuration problems. Do not claim that reconnecting the connector will necessarily fix them.

### B. Connector / tool-layer block

Examples include a tool response saying that the operation was blocked by a safety check, while GitHub did not return a repository permission error.

Treat this as an execution-path failure, not evidence that the repository permission is absent.

Do **not** immediately conclude that the requested repository change is impossible.

## 2. Recovery protocol for a connector/tool-layer block

Use the following bounded retry sequence:

1. Ask the user to explicitly re-select or re-invoke the GitHub connector if it is not already active in the current turn.
2. Re-read the target file/ref from GitHub and obtain the current blob SHA / branch HEAD.
3. Retry the smallest meaningful repository write:
   - one file before multiple files;
   - one workflow change before combining workflow edits, permission expansion, cleanup and documentation;
   - no force update unless the repository policy explicitly requires it.
4. If that scoped retry succeeds, continue the authorized task in small auditable commits.
5. If the scoped retry is blocked again, stop escalating automatically and report the exact failure layer and operation.

Do not loop indefinitely. One explicit connector re-invocation plus one scoped retry is the normal recovery path.

## 3. High-impact repository operations

The following operations require explicit user authorization in the active task before execution:

- modifying GitHub Actions workflow permissions;
- deleting Actions artifacts, Releases, tags or branches;
- changing branch protection/rulesets;
- force-updating refs;
- publishing or replacing release binaries;
- changing repository visibility or security-sensitive settings.

Once the user has authorized a clearly bounded migration or cleanup, its necessary sub-steps may proceed without repeatedly asking for confirmation, provided the scope does not expand.

## 4. Workflow-edit discipline

Workflow changes should be made so that failures remain attributable.

Prefer:

- one functional concern per commit;
- SHA-pinned or otherwise intentionally pinned external dependencies where practical;
- explicit artifact retention;
- release assets with build identity;
- post-publication verification for canonical binaries;
- ordinary CI artifacts only when they serve an actual short-lived diagnostic purpose.

Avoid combining unrelated permission escalation, cleanup, release publication and functional build changes into one opaque edit.

## 5. Storage policy

For ASS Workbench Android:

- ordinary push / pull-request workflows retain no Actions artifacts;
- manual `workflow_dispatch` diagnostic artifacts are limited to 1 day;
- canonical APKs, build identity manifests and device-test fixture bundles live in GitHub Release;
- Actions Cache is not treated as a release store and is managed separately;
- historical Actions artifacts may be deleted as an explicit one-time migration operation.

## 6. Evidence before conclusions

Before saying that a repository operation "cannot be done", record which layer actually rejected it:

- GitHub API rejection;
- connector/tool safety block;
- unsupported connector capability;
- stale/ref conflict;
- repository rule/protection.

A tool-layer block must never be rewritten as "GitHub denied permission" unless GitHub actually returned that result.

Likewise, a successful scoped retry must not be generalized into a claim that all future high-impact GitHub operations will always be permitted.

## 7. Future repositories

New repositories should adopt this policy, or a repository-specific derivative, early in their lifecycle.

Minimum carry-over rules:

- classify GitHub failure vs connector failure;
- retry a connector-layer block once through explicit connector re-invocation and a smaller write;
- keep high-impact actions explicitly authorized;
- make build/release provenance inspectable;
- keep long-lived software assets in the appropriate long-lived store rather than using Actions artifacts as archival storage;
- document repository-specific branch, release and artifact policies before automation becomes complex.

This file is intended to be reusable as a baseline for future projects, with repository-specific sections adjusted rather than copied blindly.
