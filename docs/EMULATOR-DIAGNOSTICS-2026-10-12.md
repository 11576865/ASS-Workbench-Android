# 模拟器失败证据整理

基线2afe2e9cbfb30217a69ab16593ade7fe3e014301：Android CI38165660471、Fontconfig38165660491成功；Emulator38165660505/job114547572877失败。回归step18:53:46至19:08:30UTC。两次日志读取均返回Transport closed，未获得断言名；无artifact。产品失败根因未知，不将传输错误归因于XML大小。

## 改动

原runner打印完整XML，工作流又grep含tests=的整行；单行XML可带大量成功system-out和截图编码。新只读Python摘要显示各文件计数、所有失败/error身份、最多500字符消息和6000字符堆栈，截断时明确给出原始XML位置。成功输出不再整份打印；已有AsswbVisual截图分块输出与logcat诊断保留。缺失、损坏或计数不完整的XML明确标记，不推定通过。

每次runner运行创建独立run目录，每次attempt归档原始connected结果后再开始下一次。开始前清空实时结果目录；清理失败则该attempt失败，拒绝将旧XML归属给新attempt。源码审查发现的该证据错误先用回归复现，再修复。重复本地运行不会混合旧archive。仅workflow_dispatch上传一天诊断artifact；自动PR运行不上传。生成目录忽略，不提交运行数据。

四次既有projection尝试与强制full-suite、8m/3m/12m期限、timeout清理和Gradle退出码保持原样；未修改产品断言或降低回归门禁。摘要脚本不是通过判据。

## 验证与边界

python -m unittest tools.test_emulator_regression_runner tools.test_instrumentation_report：9/9通过。覆盖失败/error身份与堆栈、成功输出抑制、长堆栈截断后仍列后续失败、缺失/损坏XML、嵌套suite计数，以及实际shell的尝试数量、失败/timeout传播和无XML不复用旧结果。新增摘要测试先因缺失脚本失败；无XML归属测试先复现旧证据重复。bash -n、artifact policy、git diff --check通过。

这些是工具测试，不是Android界面或触控验收。新诊断head原生CI待运行；模拟器产品失败仍待真实断言证据，Draft PR138未合并。
