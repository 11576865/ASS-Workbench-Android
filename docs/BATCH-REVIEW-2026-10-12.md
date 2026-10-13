# 批量作用范围与已审查事务

基线：PR #138 `0e8d849a6338cbbbbcc877f9f4bd213b415ff262`。本单元落实批准设计第6节，保留主界面及画布，通过现有 RuleBatchPane 生产工具共享流程。

四种范围为已选、筛选结果、已选与结果交集、整个文档；目标解析为稳定 Event ID，保留文档顺序，空集不回退全量。保留原工具初始意图：有选择时已选，否则整个文档。后续显式范围选择不会因选择数量变化被自动改写。范围数量独立于规则额外 AND 条件的命中数量；UI 清楚提示未提交草稿不自动应用。

规则批处理及结构化搜索替换均显式预检。后者复用 AssSearchReplace 原 parser/token 边界，沿用上方范围；仅匹配范围内事件，但预览和提交保留完整文档、样式和范围外事件。正文跨 override block 的命中可能无法替换，界面说明该边界；Raw ASS 保持明确标识。

AssBatchReview 保存不可变基线、session 和预检结果；应用提交审查的结果，不重跑不等价的目标扫描。UI 在参数、实际目标 IDs、session 或文档变化后失效预览；VM 再次检查 canonical baseline/session，过期时只报状态。仅一个 editDocument/history.commit。分页每页最多5条，使用限定索引，避免复制其余全部 IDs；点击条目只 focusEvent(seek=false)，不改选择/文档。共用结果组件展示时间、Style/Layer、Actor、Comment、Margins、Effect 和原始 ASS 前后变化，覆盖仅标签变化。

## 验证与审查

- 新增9项领域测试：默认范围、空选择、共享结果顺序、交集、全量显式行为、所有变更分页、结构化搜索范围与标签保护、空搜索范围、过期文档与同文档不同session拒绝。
- 缺失 scope/review/search/default API 的 RED 编译记录已取得；实现后完整 core/domain 的235个测试方法由 kotlinc/kotlin-test 直接执行通过，test/resources 在 classpath；不是 Gradle 或 Android 构建证明。
- 独立审查修正两项 Important：错误缩小默认范围、缺失点击定位。另修正执行失败禁止重试及分页复制开销。最终审查无剩余 Critical/Important。
- 新增生产组件手机宽度分页定位 instrumentation，真实 Activity 中跨 Host 范围失效、定位不改选择/文档、VM 单Undo和旧预览/session拒绝回归。本地无Android SDK，未执行。
- artifact policy / git diff --check通过。现有失败用例未删除、放宽或增加超时。

基线一次CI读取：Android CI 38160312321 success；Emulator38160312619及Fontconfig38160312322 in_progress。这些不是本轮提交的验证。上轮模拟器历史超时/批量控件不可见尚未由本轮证明解决。

主/画布 batch 参数和预览仍是各自 presentation 状态，没有宣称所有专业工具草稿已共享。尚待真实UI、IME、Activity/process恢复与设备视觉验收，以及历史四边/停靠/玻璃能力补齐。PR继续Draft、不合并。

UIGS已查询Foundry既有族，新增源级案例保留Pending于现有本仓库 intake 记录。此前外部跨仓库写入被自动审批拒绝，未重试；Canonical不变。
