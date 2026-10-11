# Candidate / Pending Foundry write

Destination: 11576865/UIGS-Foundry
Related family: inbox/candidates/2026-10-03-spatial-canvas-camera-semantic-zoom-gesture-ownership.md
Source: ASS-Workbench-Android@58e4b4e; docs/MAIN-CANVAS-RECHECK-2026-10-11.md.

Code-level observation: a task entry labelled 排版 invokes STYLE; exact-tool selection activeTool == STYLE leaves POSITION/VECTOR_CLIP without an active category. TaskCategory, ToolInstance and Host have distinct identities. Grouped task navigation should derive selected category from the actual active tool, while exposing actual tool/context independently.

Separate source observation: keeping two presentation routes does not establish preservation of retired-route interaction capabilities. Migration parity needs capability-level evidence; historical 240 UI and all four-edge/glass behaviors remain partial/unverified.

Epistemic level: source inspection and deterministic selection predicate; no new native visual/IME/usability measurement, no broad universal claim. Canonical unchanged. Search performed before recording; existing families related, no verified exact-match promotion.

Status: Pending. Prior automatic approval review rejected Foundry cross-repository disclosure/destination authorization; this check does not retry that rejected external mutation. Source-project record remains available for later ingestion.

## Continuation evidence

Implemented category-level selection, independent directory and root-owned navigation. Important review finding: retaining the same tool enum alone does not preserve active ToolInstance/Binding; invoking openPrimary on task reselection can silently replace a pinned sibling target. Same-tool reselection now changes only presentation mode.

Compact content flow also requires sufficient scroll range: editorHeight = viewport - preview leaves only chrome overflow and cannot scroll the full reference away. A bounded full-viewport pane makes the reference scrollable outside the viewport without unbounded nested LazyColumn measurement. Pure JVM category/layout 13/13 and domain 217/217 pass. Native flow/IME/device execution remains Pending, not usability evidence.

Dedup search repeated against Foundry for `task category host`; returned related role/context candidates but no identical focused case. Append to this existing Pending packet; do not create Canonical or retry rejected cross-repository write.
