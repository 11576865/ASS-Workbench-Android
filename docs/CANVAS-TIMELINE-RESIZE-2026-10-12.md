# 画布常驻时间轴连续高度恢复

基线：remote6eec59ac5046eb93a901d461a60fc70486941db5 / local02e90a83（相同tree）。用户要求继续合并、改进、重绘、修复；本单元恢复迁移报告明确缺失的时间轴尺寸手势，保留FIXED与SPATIAL，不增加第三种布局。

## 产品与状态

- 重画时间轴顶部：圆角承载、48dp抓手、明确的“拖动调整高度”提示；紧凑/展开及关闭按钮独立于抓手。
- 垂直拖动从实际投影高度开始，不跳到一个百分比预设。移动只改变临时比例；取消清除，松手更新会话首选值。距稳定高度16dp以内才吸附，其他高度连续保留。
- 紧凑142dp；展开最多72%且给Host上部留208dp。可用高度不足350dp（如键盘、多窗口）时零高度暂挂时间轴，保留其组合、局部时间视口和首选值；恢复高度后还原。208dp是Host剩余空间，不冒充编辑表单最小可用高度或触控验收。
- 手势已有原生写入所有者时，抓手不接管，紧凑/展开和关闭禁用；窗口或ownership改变取消候选。世界节点、相机、ASS文档Undo不由此修改。
- 首选比例由会话键rememberSaveable持有，暂不进入.asswb格式。显式关闭仍沿用旧生命周期；本单元只证明临时约束下不卸载，不宣称关闭再打开保留所有非saveable现场。

## 验证与边界

- 新测试先因缺失canvas* API编译失败（RED），不是设备触摸失败；实现后policy7/7通过，包含旧3项与新增4项。纯Kotlin执行使用临时assert适配器，断言未删减；Gradle/JUnit仍由CI执行。
- 新增生产Host夹具：拖动增高、触摸取消回原值、gesture-owned禁用、600→240→600dp保留非saveable timeline草稿与高度；验证节点和Undo callback不变。夹具内容不是实际时间轴视觉证据。
- 新增实际Activity/ModernTimelinePane回归：拖动后文档、Focus、Selection、dirty和Undo状态不变，真实timeline canvas仍存在。两个native新增尚未执行。
- 独立源码审查无Critical/Important；artifact policy和git diff --check通过。
- 基线6eec59ac Android CI38163363710 success；基线Emulator38163363565、Fontconfig38163363538读取时in_progress。上一轮分页编译问题已在Android构建消除。新时间轴提交没有原生GREEN，不继承基线CI结论。

仅PR138开放；没有其他待合并功能PR。用户授权合并，但模拟器尚未提供本次可用结果，草稿仍未合入main。历史多轨、四边完整承接、停靠/标签组、玻璃以及设备/视觉/IME验收不因此完成。
