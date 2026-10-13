# 共享字幕筛选与结果操作

基线：PR #138 `6a6d2799dd1f8118e5cc1a28f1d8bce8fdd554f8`。此前本地 `af626c10` 与此远端提交的 tree 一致；画布工具栏已发布，不重复提交。

本单元落实已批准设计第5节。SubtitleFilterState 属于 EditorState，通过 SavedStateHandle 保存原始字面条件，在 beginWorkspaceBoundary 清空；不扩展 .asswb。快速文本按可见正文/Actor/Style OR，其余正文、Actor、Style、Dialogue/Comment、仅已选按 AND。沿文档顺序返回原 Event；旧 setQuery/query 调用仍可使用。

主界面列表、画布 subtitles 节点和聚焦 SUBTITLES 工具共用 SubtitleNavigationHost、同一状态及结果操作。关闭面板保留条件；无结果不改焦点；结果导航保留选择。全选明确替换选择，点击前标示数量；当前焦点被过滤掉会显示提示，只有明确“清空筛选并定位”才扩大结果集。筛选/导航/选择不提交正文，不写文档 Undo、dirty 或 recovery。

独立审查发现展开条件可挤掉短窗格列表。修复为：不足360dp的字幕窗格仅保留48dp条件入口，点击后用可滚动筛选对话框（避免隐藏画布节点自行弹窗）；其他窗格最多使用40%高度且不超过260dp。生产三个入口均消费同一策略，画布节点列表改为剩余空间权重。新增191dp布局组件触控回归，不冒充视频渲染或整屏视觉证据。

## 验证

- 9项领域筛选测试：缺失实现 RED，GREEN。包含字面匹配、override不匹配、AND/OR、类型、选择交集、稳定顺序、导航边界和原始条件恢复。
- 完整领域 JVM 226/226通过。直接 Kotlin 执行测试方法，断言来自 kotlin-test；补齐 test/resources 后通过。首次缺少资源的尝试在 AssRoundTripCorpusTest.corpusIsSemanticallyStableAcrossParseWriteParse 报 Missing fixture aegisub-basic.ass，不计作通过。
- 2项字幕窗格布局测试：缺失策略 RED，GREEN；191/240/319dp使用对话框，360/900dp保留结果/列表预算。
- 新增Android跨Host筛选/选择/定位、SavedStateHandle序列化与工程重置、短窗格展开条件触控用例；本地无Android SDK，未运行。
- artifact policy、git diff --check通过。
- 独立源码审查：Critical0、Important1（已修复）；Minor1：手工SavedStateHandle恢复用例不是实际Activity/进程恢复链，保留为后续原生验收缺口。不声称进程恢复已验证。

## 最新基线CI证据

6a6d2799 Android CI 38143106983、Fontconfig 38143106937 success；Emulator 38143106952 failure，job114480841370日志可读。四类投影独立运行分别9/3/7/3项通过；完整套件12分钟deadline退出124，不能给完整套件通过数量。

日志明确记录：
- batchUnknownStyleAndNegativeMarginFailBeforePreview：batch-style-action不可见。
- groupedNavigationAndGeometrySectionsPreserveDocument、batchInvalidRegexFailsClosedInsteadOfDroppingTheFilter：ComposeTimeoutException。
- eventTextDraftFollowsTheSameToolAcrossMainAndCanvasWithoutImplicitCommit：Failed to inject touch input。

这些是已取得的失败症状；尚未确认产品、测试滚动路径或性能根因。不删除断言、不增加超时掩盖失败，也不把本单元的JVM通过当作旧模拟器失败已修复。

后续：批量作用范围及变更前后预览、其余专业工具草稿、历史四边/停靠/玻璃能力缺口。PR保持Draft且不合并。UIGS跨仓库写入此前被拒，本单元不重试；记录保留在本仓库。
