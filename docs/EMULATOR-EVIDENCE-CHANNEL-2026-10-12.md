# 独立模拟器证据通道

本轮基线：远端 eaab9382cc6ea49ef2937bde452949697b424316 与本地 49a51c83 的 tree 都为 0def435e241a3b9077016a2b5e25f6ec49201ce3。

实际结果：Android CI 38194661353、Fontconfig 38194661354 成功；Emulator 38194661365/job114634577024 在回归步骤失败。日志工具仍返回 Transport closed。check-runs 元数据可读取，但 annotations 子资源被连接器拒绝为不支持的 endpoint。尚未取得本次断言，不推定产品根因，也不证明输出大小导致传输中断。

既有 runner 已产生每次尝试的有界 XML 摘要，但摘要和截图、logcat 仍在同一 job 日志中。新增 emulator-evidence job，依赖实际回归 job、always 执行，以 contents:read/actions:read 查询同一 run/attempt 的来源 job，下载其日志到临时目录后只输出已有摘要。报告标识来源 repository/run/attempt/SHA/result/job URL；无仓库写入，无新 artifact，无产品断言修改。

解析器逐行去除 GitHub 时间戳，只提取明确起止标记之间的摘要；保留所有尝试、计数与失败/error 身份，每条堆栈额外限制1200字符，截断明确标注。无摘要或未闭合摘要返回非零，缺失 XML 保留原提示；提取成功仅说明通道读到了摘要，不等于回归成功。所有输出加 EVIDENCE 前缀，避免测试文本被当成工作流命令。回归 job 独立保有原门禁和失败结论。

验证：6项新解析回归先失败再通过；包含时间戳、截图排除、所有尝试、无 XML、无摘要、长堆栈后续失败、未闭合摘要和最后部分行截断。tools 下完整 Python 测试15/15；workflow YAML 解析、只读权限/依赖核对、artifact policy、git diff --check通过。新 job 的真实 GitHub 执行待验证，本地没有 Android SDK。本轮不修改产品 UI，不合并 PR。

UIGS 去重查询既有证据身份及 Android emulator offline 观察族，追加既有项目侧 Pending intake；此前跨仓库写入被自动审批拒绝，未重试，不修改 Canonical。
