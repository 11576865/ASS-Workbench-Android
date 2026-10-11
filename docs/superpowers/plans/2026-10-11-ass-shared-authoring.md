# ASS Shared Authoring Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Preserve both UI hosts while sharing uncommitted Event Text and replacing two scrolling navigation rows with a task strip and complete tool directory.

**Architecture:** The project-session root owns an observable raw draft store. Tool/Event identity, not presentation slots, identifies drafts; primary TEXT/EVENT/EFFECTS share their existing single editor buffer, sibling instances remain isolated. Both fixed and spatial production editors use it.

**Tech Stack:** Kotlin, Jetpack Compose, kotlin.test/JUnit.

**Spec:** docs/superpowers/specs/2026-10-11-ass-authoring-main-canvas-design.md

## Global Constraints
- Keep FIXED and SPATIAL_EXPERIMENTAL selectable; preserve canonical document, ToolInstance ID, Binding, scene geometry and Undo boundaries.
- Raw external conflicts remain explicit; navigation never implicitly applies text.
- Project/session changes cannot inherit another project's draft.
- CI is asynchronous; keep PR #138 Draft and unmerged.
- Native execution within the existing feature checkout; user approval covers implementation, no repeated handoff question.

## Review Focus
- Simultaneously mounted board/focused editors share updates without duplicate SaveableStateProvider keys.
- TEXT→EFFECTS→TEXT preserves the primary Event buffer.
- Deleted/reused Event IDs must not inherit stale buffers.
- IME composition is not reset on every text update.
- All original tools remain reachable on 320 dp widths.

### Task 1: Session-owned raw draft state
**Files:** Create core/domain/.../RawEventDraftBuffer.kt and RawEventDraftBufferTest.kt; create app/.../ui/EventTextDraftStore.kt; modify ModernEditorScreen.kt; add EditorRegressionInstrumentedTest.kt coverage.
**Interfaces:** RawEventDraftBuffer.reconcile(canonicalText: String): RawEventDraftBuffer; EventTextDraftKey.owner(instanceId: String?): String. UI store exposes a MutableState<TextFieldValue> per owner/Event and canonical baseline.
- [ ] Write tests for clean synchronization, conflict preservation, explicit apply acknowledgement, primary-family sharing, sibling isolation and save/restore/session rejection.
- [ ] Run pure Kotlin tests, expect missing implementation failure.
- [ ] Implement buffer model/store and connect fixed, spatial and inline Event Text to shared owners.
- [ ] Add connected tests for fixed↔canvas uncommitted text and document/Undo invariance.
- [ ] Run pure suite and artifact checks; commit.

### Task 2: Task navigation chrome
**Files:** WorkbenchToolCatalog.kt, new WorkbenchTaskBar.kt, ModernEditorScreen.kt, catalog and connected tests.
**Interfaces:** mainAuthoringTools: List<WorkbenchTool>; WorkbenchTaskBar(activeTool, listRequested, requestedPreview, onTool, onPreview).
- [ ] Write catalog test: SUBTITLES, TEXT, STYLE, TIMELINE, CAPABILITIES stable order and unique IDs.
- [ ] Run test, expect missing function failure.
- [ ] Replace fixed two-row groups/tools with stable task strip; complete grouped searchable tools remain in existing capability directory.
- [ ] Update navigation and visual fixture helpers to use visible new directory; no invisible test-only controls.
- [ ] Run catalog/domain tests, source checks; commit and push branch to trigger CI.

### Task 3: Evidence and durable checkpoint
- [ ] Review diff against spec, record exact implemented scope and Android limitations.
- [ ] Deduplicate UIGS Foundry, register Candidate/Observation or durable Pending outbox.
- [ ] Update task record, push and query CI once; no waiting loop and no merge.

## Execution ledger

- User approved the revised direction; native execution in existing feature checkout.
- Task 1: source implemented; raw buffer/key tests RED→GREEN; full domain 217/217, connected execution Pending.
- Task 2: source implemented; catalog entry tests RED→GREEN; catalog/adaptive 9/9, Android execution Pending.
- Final review: independent-owner cleanup and native editable-text assertion corrected; no Android execution inferred.
- Task 3: source checkpoint ready; external CI and Foundry processing asynchronous.
- Remaining filters/batch/layout changes continue from durable task state, not from a project-complete claim.
