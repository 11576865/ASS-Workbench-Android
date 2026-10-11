# Shared subtitle filter continuation

Approved spec: ../specs/2026-10-11-ass-authoring-main-canvas-design.md sections 5, 7–9.

1. Add domain SubtitleFilterState with literal quick OR / field AND / event kind / selection intersection, stable order, navigation and primitive persistence. Write regressions first and observe RED→GREEN.
2. Own filter in EditorState/ViewModel; save with SavedStateHandle, reset at workspace replacement. Preserve query API. Selection-all replaces IDs; navigation changes focus only.
3. Reuse one expandable filter pane and result controls in main/spatial subtitle navigation. Explicit clear-filter-and-locate action for excluded focus; empty results retain focus. Keep finite pane measurement and original tools/drafts.
4. Add Android production-host/session tests. Run full available domain suite, artifact checks; review; update durable task and publish to existing draft PR. Native tests remain Pending without SDK.

Pre-flight: domain matching feeds both hosts via EditorState.filteredEvents. Existing quick-query callers must remain supported. UI filter expansion is presentation state, filter itself is session state. No batch scope or preview changes in this unit.

## Execution ledger
- Domain filter tests: missing implementation RED→GREEN; full domain226/226.
- Both hosts use session-owned filter and common result controls. SavedStateHandle is presentation state; workspace boundary resets it.
- Final review: independent reviewer found one Important short-pane overflow; fixed with production shared height/dialog policy and layout2/2 RED→GREEN.
- Ruling: short subtitle panes use one 48dp filter-dialog button instead of expanded inline fields — preserves results under IME/dual-pane constraints — cost if wrong: one extra close step on short windows.
- Minor (deferred): actual Activity/process recovery chain is not exercised by manual SavedStateHandle serialization test; native acceptance remains Pending.
- Baseline emulator log symptoms obtained and recorded; no unrelated failure is hidden or claimed fixed.
- Artifact/whitespace checks green; no local Android SDK; connected execution Pending.
