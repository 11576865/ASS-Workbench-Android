# 主界面与画布重新核查 — 2026-10-11

核查源码：58e4b4e04f1efc144177597bc86706f882d0e19d。用户要求重新检查目标与实现，不在本单元直接重做产品。

## 结论与纠正

上一单元是共享正文草稿与导航条改动，不能称为主工作区或无限画布整体重做。保留 FIXED/SPATIAL 两个路由也不能证明历史多 UI 能力完整保留。共享正文 store 有价值，应作为基础修复保留；任务导航的模型与主布局仍须重新设计。

## 核实的事实

1. 主布局没有重构：58e4b4e 相对 900f505 的生产变更只有 ModernEditorScreen、EventTextDraftStore、WorkbenchTaskBar 和 catalog；InfiniteCanvasHost、AdaptiveWorkspacePolicy 未改。主界面仍用固定预览高度（窄屏 196dp，紧凑 112/136dp）和固定分栏权重。旧宪法 8.2 明确优先可延伸内容流、可调预览与有界工具承载；新的书面方案重新只规定窄屏列表/编辑切换，没有把这些要求落实。
2. TaskBar 把任务类别和具体工具混合：label 排版实际调用 STYLE，时间实际调用 TIMELINE，selected 使用 activeTool == tool。POSITION、VECTOR_CLIP、FRAMES、EFFECTS 等工具打开时没有匹配选中任务项。页面内标题仍可能显示当前工具，因此不能称为整个 APP 完全无法识别状态；但任务栏类别指示确实失效。
3. 窄屏重复导航仍存在：上层 TaskBar 的字幕/正文任务与 COMPACT 下层固定列表/当前编辑器行并存。上轮仅减少一个组导航行，并没有统一 navigationGroupRequested、compactPage 和 activeTool 的语义。
4. 完整工具目录使用 CAPABILITIES ToolInstance 并替换当前编辑器/聚焦节点，是一项编辑导航，而不是独立非破坏性查找层。源码可以确认上下文切换；实际效率变差多少未测。
5. 历史多 UI 收缩在本单元之前已发生：UiVariantRegistry 只暴露 FIXED/SPATIAL，八个历史模式名被 resolve 导向 SPATIAL。docs/INFINITE-CANVAS-WORKBENCH-V2.md 明确玻璃模糊/质量模式、全部四边操控与 240 项并无一对一迁移完成证据。因此先前声称保留两个模式就保住以前系统过于宽泛。不能据此断言八种历史模式所有能力均丢失。
6. 全局编辑现场未统一：Event Text 已共享，Start/End、Layer、Actor、Comment 仍用 InlineEventEditor 的局部 rememberSaveable；任意专业工具的草稿所有权、多个 Host 的局部视图状态和 .asswb 未提交草稿保存仍未全部迁移。
7. 既定新功能仍缺口：统一筛选状态、明确批量范围/逐项前后预览、当前字幕完整上下文，以及画布工具栏组织不在本次实现中。

## 验证边界

检查时 Android CI 38134560772 completed/success，说明当前 Android 构建工作流成功。Emulator 38134560746 与 Fontconfig 38134560745 仍 in_progress；未轮询。上一轮 217 领域 JVM + 9 catalog/adaptive 测试不能作为 UI 可用性或画布改版验收。没有新生产截图或真机操作，不把拥挤、误触和性能推断冒充测量结果。

## 下一实施依据

- 以原宪法 8.2 的内容延伸/可调参考区为主界面布局目标，替换只围绕有限视口分区的新版假设；不要禁用合法分栏/断点。
- 分开 TaskCategory、ToolInstance 和 Host。类别能显示所属工具状态，工具查找不必占用当前编辑对象槽。
- 建立历史能力到当前 Host 的迁移表：implemented、partial、missing、pending evidence 分开。多 UI 的保留用能力/现场连续性证明，不用入口数量证明，也不自动复活八套重复根组件。
- 用主 UI 和画布中的同一个完整编辑任务及生产视觉矩阵验证。草稿基础修复保留，但不替代这项验收。

## UIGS Intake — Pending

已查询 Foundry 的 navigation/task/category 家族；与既有 spatial camera/semantic zoom/host ownership 候选关联。新增 source-only Candidate：任务类别与具体工具混用会造成可证明的导航选中态空缺；生产视觉/可用性验证仍 Pending。前次跨仓库写入被自动审批拒绝，不重复尝试或绕过；候选在源项目 .uigs/intake-pending/2026-10-11-task-category-host-audit.md 持久化。Canonical 未修改。
