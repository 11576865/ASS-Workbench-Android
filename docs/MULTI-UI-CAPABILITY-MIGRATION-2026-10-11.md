# 历史多 UI 能力迁移核对

核对基线：PR #138 `fb3953f24a1654aa24666fa43d0b7920fab84fbf`，并包含本提交的聚焦工具栏整理。历史依据：2026-10-01 UI 宪法、2026-10-02 四边书签集成报告、2026-10-05 无限画布报告及仓库 240 UI 账本。本表按能力和当前生产调用路径核对，不计算 240 项完成率。

模式名称可解码、数据字段可保存、交互有生产入口、自动化通过、真实验收，是五种不同证据。`UiVariantRegistry.resolve()` 仅把退役布局名称导向 SPATIAL_EXPERIMENTAL，不构成交互等价迁移。

| 历史布局 | 当前生产承接 | 明确边界／缺口 | 本轮源码证据 |
| --- | --- | --- | --- |
| CANVAS_EXPERIMENTAL 自由 Canvas | 世界坐标节点、相机、拖动/resize、布局锁、隐藏/召回 | 旧 Surface 停靠、最小化、标签组没有当前 Host 消费路径；旧几何与无限场景是独立保存格式，不自动投影等价 | InfiniteCanvasHost、InfiniteCanvasModel；WorkspaceSurface、WorkbenchSurfaceController |
| PAGER_EXPERIMENTAL 聚焦翻页 | 画布聚焦原生编辑器、工具标题切换、返回画布 | 原三页 Pager 手势/页现场未迁移；当前以聚焦切换承接用途 | InfiniteCanvasHost focused stage / switcher |
| TOOL_INSTANCES_EXPERIMENTAL 工具实例 | WorkspaceState 实例、活动身份、Binding、关闭/隐藏/书签；Style/Position 支持声明的复制与固定对象 | 所有工具草稿尚未会话化；正文已有共享草稿，时间/元数据等仍有局部状态 | WorkspaceModel、WorkbenchToolCatalog descriptors、EventTextDraftStore；SpatialWorkspace lifecycle callbacks |
| PRECISION_LENS_EXPERIMENTAL 精密放大 | Position/预览阶段的 PrecisionInteractionOverlay、精密开关与原生操纵杆 | 源码入口保留不等于 OEM 视频采样、触觉、动态媒体验收 | ModernEditorScreen PrecisionInteractionOverlay 调用；interaction/PrecisionInteractionOverlay |
| GLASS_LAYERED_EXPERIMENTAL 玻璃叠层 | 世界节点透明度、音频背景透明与显式输入穿透 | 三材质、真实背景模糊、模糊性能模式、临时看穿没有当前 Host 入口；GlassToolWindow/GlassLayerModel 定义保留但无生产调用，不算承接完成 | InfiniteCanvasHost alpha/passthrough；GlassToolWindow、GlassLayerModel 无外部生产调用 |
| SUBTITLE_OBJECT_EXPERIMENTAL 字幕对象 | 视频长按的冻结时刻候选选择、置信信息、对象选择 | 未逐项重审所有对象关系与能力 UI；不以候选选择器替代完整历史对象工作台 | SpatialWorkspace pickCanvasObject、ObjectCandidatePicker；PreviewTargetResolver |
| EDGE_BOOKMARK_EXPERIMENTAL 四边书签 | BOOKMARKED 实例右侧轨、可驻留左目录 | 四边独立临时/驻留边层、尺寸拖动/吸附、书签侧别/排序/分组没有当前 Host 调用；EdgeWorkspaceModel 仅保留定义 | InfiniteCanvasHost spatial-bookmark-rail / spatial-edge-rail-left；EdgeWorkspaceModel 无生产消费者 |
| TIMELINE_DOCK_EXPERIMENTAL 常驻时间轴 | 紧凑/展开的真实 Timeline Dock，与媒体共用时间现场 | 本轮源码补回连续高度抓手、附近吸附与短窗口暂挂；保留紧凑/展开按钮，原生触控与窗口恢复 Pending | InfiniteCanvasHost timelineDockVisible/Expanded、renderTimeline；SpatialWorkspace Timeline pane |

“多 UI 保留”的准确结论：FIXED 和 SPATIAL 两个可选 Host 存在，共享领域工具与一部分旧交互已承接；八种退役模式不是仍可独立使用的 UI，也没有完整能力等价迁移。保留状态字段不等于功能可用。用户原来担心的能力流失确有具体缺口，而不是单纯布局数量的问题。

本单元修正画布聚焦工具栏：返回画布、当前工具、工具目录、鸟瞰及实例管理保持在固定标题行；时间轴、精密操控、文档 Undo/Redo、参考显示/大小使用独立的 48dp 横向滚动操作栏。驻留目录和书签轨从标题64dp + divider1dp + 操作48dp 以下开始，只对生产编辑内容保留侧边占位，避免 320dp 下双轨挤掉核心导航。Undo/Redo 在原生手势归属期间禁用。

新增 Android 回归包含 320dp、左目录驻留、右书签、Position 和真实溢出的参考操作；检查核心控件边界、最终操作滚入视口、手势归属禁用、scene 节点不变。用例使用生产 Host，简单测试内容不冒充生产编辑器视觉证据。本地无 Android SDK，未执行该用例，未获得截图或 IME 实测。独立源代码审查补出了双轨宽度和无溢出测试覆盖缺陷，修正后无 Critical/Important 遗留。

JVM 相机/节点/鸟瞰/布局注册 33/33 通过；编译使用从生产 Host 原样提取的 InfiniteCanvasEntry 声明，不使用伪造替身。首轮命令遗漏该声明，编译失败且零测试，不计为测试通过；补齐生产声明后才得到33项通过。领域与主界面 JVM 230项为上一单元基线结果，未重复计作本单元新增覆盖。

CI 时点：fb3953f2 Android CI 38142464433、Emulator 38142464426、Fontconfig 38142464464 均在运行中。此前 Emulator 38135092606 失败日志不可读取，根因未确认。本提交另触发异步 CI，不合并 PR。

后续顺序：字幕筛选/批量已有后续源码实现及各自验证记录；本轮连续时间轴尺寸见 `CANVAS-TIMELINE-RESIZE-2026-10-12.md`。专业工具草稿统一、四边、停靠/标签组、玻璃仍分单元推进。上述缺口不因旧记录写有 Implemented 就宣布当前完成，也不在本轮工具栏改动中偷偷扩大为新子系统。

## 2026-10-14 · Issue #146 双 Host 入口核对（PR #147 第一单元）

**产品选择只有两套 UI**：`FIXED` 默认主工作台，`SPATIAL_EXPERIMENTAL` 为独立无限画布；它不是 FIXED 内的一张画布卡片。历史八种 UI 的 `enum` token 仅作旧项目解码，不恢复八套入口。两 Host 共享 `EditorViewModel` / `AssDocument`，但每套保留自己的可操作导航与布局。

### 18 项专业工具：现行入口策略

以下表格说明**目前可指向的生产调用路径**，不表示每个功能的实际编辑闭环/Native 设备验收已经完成。FIXED 的可见「工具」→ `WorkspaceToolDirectory` 和 SPATIAL 的可见「空间功能」→「专业工具目录」复用同一个组件；后者再经既有 `showToolPicker → onAddTool → CAPABILITIES:primary → onOpenTool` 路由至原生工具。目录内排除 `CAPABILITIES` 自身以防无限递归，目录按钮就是它的实际入口。

| 稳定 Tool ID | FIXED 主 UI 入口 | SPATIAL 独立 UI 入口 |
| --- | --- | --- |
| `SUBTITLES` | 字幕任务 / 全部工具目录 | 目录 / 已有字幕节点 |
| `TEXT` | 正文任务 / 目录 | 目录 → 聚焦原生正文 |
| `EVENT` | 正文当前任务工具 / 目录 | 目录 → 聚焦事件结构 |
| `TIMELINE` | 时间任务 / 目录 | 目录 → 时间轴工具；另有可调常驻时间轴 |
| `FRAMES` | 时间当前任务工具 / 目录 | 目录 → 帧时间工具 |
| `STYLE` | 排版任务 / 目录 | 目录 → 样式工具（允许符合 descriptor 的多实例） |
| `POSITION` | 排版当前任务工具 / 目录 | 目录 → 位置工具；精密操纵在 Position 聚焦时可调用 |
| `VECTOR_CLIP` | 排版当前任务工具 / 目录 | 目录 → 矢量裁剪工具 |
| `EFFECTS` | 效果任务 / 目录 | 目录 → 效果工具 |
| `KARAOKE` | 效果当前任务工具 / 目录 | 目录 → Karaoke 工具 |
| `FONTS` | 目录 → 字体管理 | 目录 → 字体管理 |
| `FONT_REQUIREMENTS` | 目录 → 字体依赖 | 目录 → 字体依赖 |
| `QC` | 目录 → 质量检查 | 目录 → 质量检查 |
| `COMPATIBILITY` | 目录 → 兼容性 | 目录 → 兼容性 |
| `DIAGNOSTICS` | 目录 → 运行诊断 | 目录 → 运行诊断 |
| `BATCH` | 目录 / 多选工具栏 → 批量 | 目录 → 批量规则 |
| `PROJECT` | 目录 / 工程菜单 | 目录 → 工程与封装 |
| `CAPABILITIES` | 可见「工具」入口本身 | 可见「空间功能」→「专业工具目录」入口本身 |

### 旧交互承接：必须区分当前路径与缺失消费者

| 历史模式能力 | 当前生产路径 | 尚不能宣布完成 |
| --- | --- | --- |
| CANVAS | SPATIAL 世界节点、相机、鸟瞰、移动、调整大小、锁和召回 | 旧 Surface dock/minimize/tabGroup 的完整当前消费者 |
| PAGER | SPATIAL 聚焦原生编辑器、工具切换、返回画布 | 原完整三页现场/手势未等价实现 |
| TOOL_INSTANCES | SPATIAL 工具列表、绑定、复制、隐藏、关闭、书签 | 所有工具独立草稿、历史状态闭环 |
| PRECISION_LENS | Position 聚焦操作栏与 #147 新增的空间功能菜单路径 | OEM 取样、真实媒体、触觉和精确性设备验收 |
| GLASS_LAYERED | SPATIAL 节点 alpha 与 audio pass-through 的真实菜单 | 三材质、真实背景模糊、临时看穿等旧消费者 |
| SUBTITLE_OBJECT | 预览长按候选与对象定位操作 | 完整对象关系 UI 以及多对象复杂语义 |
| EDGE_BOOKMARK | SPATIAL 左工具列表、右 Bookmark 轨 | 四边独立边层、侧别/排序/分组、旧边层尺寸/吸附 |
| TIMELINE_DOCK | 两 Host 时间工具；SPATIAL 常驻时间轴可收展/拖动 | 多轨高级能力、真实触控与窗口转换验收 |

### 证据边界与后续动作

- #147 仅把**已经存在的**「专业工具目录 / 实例列表 / 鸟瞰 / 时间轴 / Position 精密操纵」回调集中为持久可见的空间功能入口，并添加 Android Compose 菜单回归及目录全 17 个专业工具的可点击/派发回归；**不假装修复表中未承接的旧消费者**。
- `WorkspaceToolDirectoryInstrumentedTest` 的目录回调测试并不等价于各专业工具的完整 authoring/Raw/renderer 验收；二者分开登记。
- 各旧能力真正迁移时，必须指出当前 Host 的生产消费者，而不是只有保存字段/旧源码/旧枚举。状态至少区分 **可达、可执行、回归验证、DEVICE 验收**。
- 原已实现能力不得无声删除；但历史账本的 `Planned` / 无代码证据条目不能冒充已实现迁移范围。
- #147 仍为 Draft；Android CI / Android Emulator / Fontconfig 结果须从对应 exact-head Actions 获得。主 UI R2 视频/参数来源设计仍待独立实施，不由本 PR 宣称完成。
