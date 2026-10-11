# 跨 UI 批量草稿与预检连续性

基线：远端 a167287bbe6d108d8953dda8e36ddb4207a45b6f，与本地 f0eb483e 的 tree 相同。本单元沿用已批准工作区核心统一方向，补齐上一单元明确留下的批量草稿连续性。

所有规则/结构化搜索的原始字符串、开关和显式作用范围现在属于 workspaceSessionId + ToolInstance ID。根级 BatchAuthoringDraftStore 同时供固定主界面、画布节点、聚焦编辑器消费，同实例观察同一输入，独立实例保有独立值。主界面已有 activeForTool 选择原实例，无须新增展示身份。真正 close 时从 store 同步删除；hide、UI切换和重新挂载保留。

输入改变清空两条处理管线的预检及执行错误；字面参数继续保留。document/目标 IDs 不匹配时预检删除，不能在恢复原目标后仅由 Pane 自动复活。工作区级效应也校验未显示的实例。新预检重置分页，UI切换同一内存预检时保留页数。分页缓存只存整数状态，不持有过期 AssBatchReview/前后文档。

同会话 saver 持久化字面输入，永不序列化文档/审查授权；Activity恢复参数后必须重新预检。snapshot 拒绝不同session，避免新工程复用instance ID时继承旧参数。

## 恢复边界与审查裁定

独立审查指出：VM workspaceEpoch 在新进程从1起始，旧工程session常为2或更高。当前 saver 会拒绝不匹配快照，因此不能保证系统杀进程后恢复字面参数。本轮范围明确为同会话跨Host连续和同session快照；不改变高影响的工程身份/恢复链，也不声称进程恢复完成。代价是进程重启可能丢失批量草稿，该项保留后续工作。真正Activity重建也仍需原生执行证明。

外部文档/选择快速变化后回到完全相等的基线，如果中间状态被StateFlow/Compose合并而没有被观察，工作区效应无法知道曾发生变化；同基线预检可能仍保留。提交仍拒绝任何不同基线/session，不会覆盖不同文档。严格的“任何中间变化都要求重预检”需要未来由命令层递增版本令牌，不在本单元冒充完成。

## 验证

- 8项新domain测试：同实例重挂载、参数修改清空两管线、文档改变、目标改变后不可自动恢复、默认不覆盖显式选择、输入/owner字面序列化且不恢复预检、session/格式拒绝、实例关闭及ID复用。
- 缺失 BatchAuthoringDraft/Snapshot API 的RED编译已取得；实现后完整domain243/243由kotlinc+kotlin-test直接执行通过，资源在classpath。不是Android/Gradle证明。
- 新增真实Activity主界面→画布参数及预览/提交/Undo回归，同session Activity recreation恢复参数但要求新预检，以及双挂载store可观察更新/独立实例隔离回归。本地无Android SDK，未运行。
- 独立源码审查确认共享观察/默认初始化/调用路径；进程恢复Important作为明确未完成边界保留，隐藏期间观察与分页引用两项已限定/改进。无已知同session跨Host源码阻断问题。
- artifact policy及git diff --check通过。

上一提交a167287b Android CI38161380919（build job114534837069）单元测试、APK构建通过；本轮新提交CI待触发。上一模拟器问题、实机UI/IME/视觉验收不因领域测试变绿而关闭。PR138继续Draft，不合并。历史四边/停靠/玻璃与其他专业工具草稿仍待后续。

UIGS：查询既有draft/persistence/session/preview族后，在既有本仓库Pending intake中追加限定的源级案例。此前跨仓库自动审批拒绝尚无新授权证据，不重试被拒绝外部写入，不修改Canonical。
