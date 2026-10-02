# UI Stability Boundary

本文件定义 ASS Workbench Android 的 UI Contract 迁移边界。它不是重写计划；第一阶段只建立稳定接口和测试身份，不改变现有 ASS 语义、保存语义、renderer 权威或 UI 外观。

## 1. 目标

长期 presentation（Fixed / Canvas / Pager / Spatial / Tool Instances / Glass / Precision Lens / Subtitle Object / 后续实验）不应直接依赖 EditorViewModel 的全部内部实现。第一阶段允许 EditorViewModel 继续作为实现者，但 UI 只通过稳定 contract 访问被承诺的状态与动作。

## 2. 三类状态

### Editor / Domain State
- AssDocument
- Focus
- Selection
- Write Target
- dirty
- undo / redo
- fonts
- container
- diagnostics

### Workspace State
- active tool
- Tool Binding
- Pinned Context
- active ToolInstance
- tool instance lifecycle

### Presentation / Surface State
- presentation mode
- pane ratio
- floating geometry
- dock
- minimized
- tab stack
- responsive layout

Surface Geometry 不得改变 canonical document 语义。

## 3. 第一阶段 Contract

建议最小对象：

```kotlin
data class EditorUiState(
    val document: DocumentSummary,
    val focus: FocusState,
    val selection: SelectionState,
    val binding: BindingState,
    val writeTarget: WriteTargetState,
    val preview: PreviewState,
    val fonts: FontUiState,
    val container: ContainerUiState,
    val diagnostics: DiagnosticsUiState,
)

interface EditorUiActions {
    fun focusEvent(id: Long)
    fun toggleSelection(id: Long)
    fun edit(command: EditorCommand)
    fun openTool(tool: WorkbenchTool)
    fun undo()
    fun redo()
}
```

这只是稳定边界示意。具体字段必须从现有 EditorState / WorkspaceState 中逐项映射，不得为了“架构整洁”重复创造第二套业务状态。

## 4. 迁移规则

1. 新 presentation 不再直接新增对 EditorViewModel 内部字段的依赖。
2. 现有 presentation 按垂直切片逐步迁移，不做一次性全屏重写。
3. Contract 只暴露稳定产品语义；临时实现字段不进入接口。
4. Focus / Selection / Binding / Write Target / Surface Geometry 必须保持独立。
5. Preview 与 Commit 必须继续分离；Contract 不允许把 transient preview 冒充 canonical commit。
6. 所有长期 presentation 必须与 main 同树编译。
7. 修改 Contract 的 PR 必须触发 presentation smoke。
8. Contract 迁移期间，EditorViewModel 可以实现 EditorUiActions；后续再逐步拆 Controller。
9. Container / Media / Recovery 拆分不属于第一阶段 UI Contract PR。
10. 不在 Contract PR 中顺手增加新的 UI 实验能力。

## 5. 首批迁移切片

按风险而非编号：

- Slice A：Focus / Selection / Undo / Redo / basic document summary
- Slice B：Tool open / Binding / current object identity
- Slice C：Preview state / commit boundary
- Slice D：fonts / container / diagnostics read-only state
- Slice E：write target 与批量编辑 intent

每个 slice 都必须先有测试，再迁移 presentation 调用点。

## 6. 验收不变量

- 切换 presentation 不改变 canonical Event text。
- 切换 presentation 不改变 Focus identity。
- 切换 presentation 不丢 Undo / Redo history。
- Surface geometry 改动不产生 document commit。
- Pinned tool 不因 Focus 改变而静默换 target。
- FollowFocus tool 在 Focus 改变后按 contract 更新 target。
- Draft / preview 不因 Activity recreation 自动变成 commit。
- Contract 缺失能力时，UI 应显式不可用或 unresolved，不得猜测 fallback target。

## 7. 并行开发约束

- #59 稳定化合并前，本分支只允许文档和 contract-oriented tests，不修改 EditorViewModel / ModernEditorScreen 热点实现。
- #59 合并后先同步 current main，再开始运行时代码。
- Edge Bookmark / Timeline Dock 继续依赖现有接口；不得要求 Contract 为单一实验暴露私有字段。
- 同一时间 UI Contract 只允许一个权威分支：`refactor/ui-contract-boundary`。
