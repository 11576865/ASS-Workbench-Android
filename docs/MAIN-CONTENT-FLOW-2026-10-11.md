# Main workspace correction

Baseline: 09df9b1c09c2ee94513ff99336feebb885dc8c9f, PR #138.

The prior task strip did not change the cramped compact layout. This unit implements the constitution 8.2 direction: a scrollable compact body containing an adjustable video reference and a bounded tool/list pane. The pane receives at least a full usable viewport, so the complete preview can leave the screen. Preview scale is saved; temporary IME pressure caps effective reference height without changing the requested scale. Nested LazyColumn/tool scroll areas never receive infinite height.

Navigation is one session-root list/editor choice. The compact second chip row and duplicated local navigation/revision state are removed. WorkbenchTask is separate from persisted WorkbenchTool: POSITION/VECTOR_CLIP select 排版, FRAMES selects 时间, EVENT selects 正文 and KARAOKE selects 效果. Reselecting the current task keeps its specific tool, active instance and binding. The independent searchable directory opens and dismisses without changing the inspector; only choosing a tool changes the editing task.

FIXED and SPATIAL_EXPERIMENTAL remain selectable. Existing shared Event Text drafts and Undo semantics remain; historical multi-UI capability parity is still unproven. This unit does not complete filters, scoped batch workflow or shared drafts for timing/metadata/all professional tools. Wide adaptive layouts and spatial host behavior remain next review scopes.

Verification: catalog/adaptive JVM 13/13; full domain JVM 217/217. Artifact policy and git diff whitespace pass. Independent requesting-code-review caught insufficient scroll range and same-task primary-instance activation; both corrected and re-reviewed. Production instrumentation now covers dirty draft preservation on directory close, category selection, and compact preview scrolling; exact-tool helpers use actual directory. Visual fixtures capture the directory dialog rather than treating it as an inspector.

No local Android SDK. Baseline Android CI 38135092633 and fontconfig 38135092608 succeeded. Baseline emulator 38135092606 failed in editor regression step; detailed log retrieval returned Transport closed. No root cause, instrumentation success, measured usability or device visual acceptance claimed. New CI is asynchronous, PR remains unmerged.
