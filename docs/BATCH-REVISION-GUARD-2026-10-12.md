# 批量预览修订版本校验

基线：PR #138 的 29e81a786315a75b517061d60d84ba6ae9744f7f。Android CI 38168176578、Fontconfig 38168176580 通过；Emulator 38168176589 / job 114555009385 失败。日志读取返回 Transport closed，未获得失败断言，不将本次修复视为模拟器根因修复。

## 复现与修复

旧 AssBatchReview 只比较工程会话与文档内容。真实 UndoHistory 修改后撤销恢复同一内容，旧预览仍成功提交；独立复现断言失败：Expected IllegalArgumentException, but was completed successfully。

EditorViewModel 将所有状态发布集中到 updateState，在文档内容改变时原子递增 documentRevision。直接编辑、手势、导入、恢复、Undo 和 Redo 均经过这个边界；定位、筛选、预览和布局变化不递增。即使 StateFlow 合并中间画面，版本也不会回退。AssBatchReview 的规则与搜索流程同时记录并校验版本。共享草稿也比较该版本，隐藏工具的预览会被清理，字面参数继续保留。恢复快照不恢复预览权限。

## 验证与边界

完整领域源码和全部领域测试使用本地 Kotlin 编译器重新编译；反射运行去掉 Test 注解的测试副本，并在 classpath 加入原仓库测试资源：248/248 通过。初次运行漏加资源，两个 AssRoundTripCorpusTest 因 Missing fixture aegisub-basic.ass 失败；加回原测试资源后全部通过。这不是 Gradle/JUnit/Android 构建结果。

源码校验确认 ViewModel 差异仅包含发布包装器、调用及返回标签替换和提交版本传递，没有混入其他行为改动；唯一 _state.update 位于包装器内。git diff --check 和 artifact policy 通过。

新增 Android 回归用真实 ViewModel 在单次 runOnIdle 内执行修改和 Undo，检查版本增加、草稿失效、旧提交被拒绝且 Redo 保留，再重新预检、提交并单次 Undo。既有提交回归同步传入当前版本。没有本地 Android SDK，这些设备用例尚未执行。原生门禁、视觉验收与进程恢复缺口保留，PR 继续 Draft。
