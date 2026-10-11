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
| TIMELINE_DOCK_EXPERIMENTAL 常驻时间轴 | 紧凑/展开的真实 Timeline Dock，与媒体共用时间现场 | 当前仅两档高度；旧连续拉伸高度手势未承接 | InfiniteCanvasHost timelineDockVisible/Expanded、renderTimeline；SpatialWorkspace Timeline pane |

“多 UI 保留”的准确结论：FIXED 和 SPATIAL 两个可选 Host 存在，共享领域工具与一部分旧交互已承接；八种退役模式不是仍可独立使用的 UI，也没有完整能力等价迁移。保留状态字段不等于功能可用。用户原来担心的能力流失确有具体缺口，而不是单纯布局数量的问题。

本单元修正画布聚焦工具栏：返回画布、当前工具、工具目录、鸟瞰及实例管理保持在固定标题行；时间轴、精密操控、文档 Undo/Redo、参考显示/大小使用独立的 48dp 横向滚动操作栏。驻留目录和书签轨从标题64dp + divider1dp + 操作48dp 以下开始，只对生产编辑内容保留侧边占位，避免 320dp 下双轨挤掉核心导航。Undo/Redo 在原生手势归属期间禁用。

新增 Android 回归包含 320dp、左目录驻留、右书签、Position 和真实溢出的参考操作；检查核心控件边界、最终操作滚入视口、手势归属禁用、scene 节点不变。用例使用生产 Host，简单测试内容不冒充生产编辑器视觉证据。本地无 Android SDK，未执行该用例，未获得截图或 IME 实测。独立源代码审查补出了双轨宽度和无溢出测试覆盖缺陷，修正后无 Critical/Important 遗留。

JVM 相机/节点/鸟瞰/布局注册 33/33 通过；编译使用从生产 Host 原样提取的 InfiniteCanvasEntry 声明，不使用伪造替身。首轮命令遗漏该声明，编译失败且零测试，不计为测试通过；补齐生产声明后才得到33项通过。领域与主界面 JVM 230项为上一单元基线结果，未重复计作本单元新增覆盖。

CI 时点：fb3953f2 Android CI 38142464433、Emulator 38142464426、Fontconfig 38142464464 均在运行中。此前 Emulator 38135092606 失败日志不可读取，根因未确认。本提交另触发异步 CI，不合并 PR。

后续顺序：继续既定字幕筛选/结果到编辑/批量作用域；专业工具草稿统一与冲突表达；根据能力清单分单元恢复四边、停靠/标签组、玻璃或连续时间轴尺寸。上述缺口不因旧记录写有 Implemented 就宣布当前完成，也不在本轮工具栏改动中偷偷扩大为新子系统。
