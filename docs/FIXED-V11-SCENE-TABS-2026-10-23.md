# FIXED V1.1 four-scene navigation increment

Design authority: Figma 5yNY5dMnmpmaOnYxDlEhY5, node 85:21 (within frozen main frame 85:3).

## Implemented source

- Subtitle, precise timing, geometry and effects/Karaoke tabs, using existing MaterialTheme tokens, active surface and 3dp underline. Native touch height is 48dp; the visual reference strip is 47px. Dimensions are not claimed pixel-identical before capture.
- Scene keys remain session presentation state. No new persisted Tool ID or document store.
- Geometry/effects reuse WorkbenchTask routing. Reselecting preserves an existing specialist (Position, Vector Clip, Karaoke); leaving a specialist for authoring selects the existing Text/Timeline tool.
- Original subtitle/timing switches keep the current inspector for non-specialist tools. All tool switches use the existing selectTool command and existing draft scope/movable inspector.
- External specialist selections project their actual scene, avoiding stale selected tabs.
- Header shows the actual active tool and resolved binding, including selection count and unresolved pinned Event. No fixture Event number is hardcoded.

## Verification boundary

- Added JVM tests for specialist routing, reselection, return routing, original scene compatibility and external-tool projection.
- Added a Compose test for four reachable tabs, selected semantics, one dispatch per click and actual supplied context. Existing sibling draft test remains enabled; it is not a real editor lifecycle proof.
- Targeted Gradle test invocation could not start: Gradle is not installed in the local runtime. Android compilation and all new tests are PENDING external CI. No red/green TDD execution or device pass claimed.
- Local git diff whitespace and Actions artifact-policy checks passed.
- Native screenshot, Figma parity, real renderer, IME and remaining historical regression failures remain separate gates.

## Remaining UI gaps

This increment does not implement the central bilingual authoring dock, full contextual inspector/review layout, task-specific specialist geometry or compact-tablet four-tab placement. The existing header task bar is retained so contextual specialists and all tools remain accessible. SPATIAL is unchanged. PRs #149/#148 remain Draft.

## UIGS Intake

Queried Foundry task/navigation records. This is an application of existing task/Tool identity and truthful-context knowledge; no new reusable finding or Canonical promotion.
