# ASS Workbench Android — 240 UI 实验主账本

日期：2026-10-02  
状态：Phase 0 traceability baseline（追踪基线），不是完成宣言。  
源清单：`ASS-Workbench-Android_超级激进UI实验清单_2026-10-02.txt`。原清单明确属于拟议要求，不是现有能力说明。

## 事实快照

- current main at Slice E branch point: `73c09f4052af0b7b057794a138a618f7883f4803`
- #61 Presentation invariant gate: merged。
- #62 UI Stability Boundary + Contract Slice A: merged；merge commit `27e23899fe96d535cce087b172b23f2ce6050626`。
- #69 Edge Bookmark presentation gate: merged；Edge Bookmark 现已进入 cross-presentation canonical state / Focus / Undo-Redo invariant gate。
- #72 UI Contract Slice B（current object + Binding）: merged；Binding 解析统一经 presentation-neutral canonical identity 投影，missing pinned Event 保持 unresolved。
- #75 UIGS visual-capture baseline repair: merged；Fixed capture 使用真实入口，并移除了 renderer-disabled fixture 无法真实产生的 Canvas Position rod 视觉证据声明。
- #74 UI Contract Slice C（transient preview ownership）: merged；preview ownership/cancel 已进入稳定 boundary，generic preview commit 仍被明确禁止。
- #77 UI Contract Slice D（resources / container / renderer diagnostics）: merged；只读资源、容器与 renderer diagnostics 摘要已进入 presentation-neutral contract。
- #76 Canvas Production Visual Evidence: merged；Canvas workspace 已有确定性 Android compositor production-rendered 证据。
- #80 UI Contract Slice E（write target / batch intent）: current PR；typed Write Target + selection-derived batch default + batch commit action 正在验收。
- Current main 已登记：Spatial、Tool Instances、Glass Layered、Precision Lens、Subtitle Object、Edge Bookmark 等实验 presentation。
- `TIMELINE_DOCK_EXPERIMENTAL` 仍不在 current main；#98 从 current main 语义重落地 Timeline Dock，#56/#82 保留为历史实现与测试 provenance。

## 状态口径

- **Implemented**：current-main production code 已有直接证据覆盖该项核心行为；仍不等于 Production Visual Evidence 或 DEVICE 证据完成。
- **Partial**：已有相关代码/交互，但原要求仍有明确语义缺口，或只具备部分链路。
- **Planned**：当前没有足够的 current-main 实现证据；不得因为相邻功能存在而推定完成。
- **Blocked**：已知依赖未成立，当前不应继续伪装完成。

初始保守盘点：**Implemented 51 / Partial 108 / Planned 79 / Blocked 2 = 240**。  
这个数字是追踪起点，不是成熟度评分。每项要升级状态，必须补足相应代码、自动测试、视觉或设备证据。

## 240 项逐项账本

### 一、空间工作现场：让手机屏幕成为窗口

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 001 | 建立大于一屏的二维工作区，预览、列表、工具、参考资料都可成为空间节点。 | **Implemented** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped |
| 002 | 允许近似无限扩展：向空白方向移动仍有组织空间；以资源限额保证实际可用。 | **Partial** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped；World is currently bounded (1800×1320), not dynamically near-infinite. |
| 003 | 工作区支持横向与纵向平移，配合明确的空白区域拖动和导航手势。 | **Implemented** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped |
| 004 | 双指缩放整个工作区，缩小后工具变成摘要，再放大恢复可操作内容。 | **Partial** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped；Whole-workspace zoom exists; semantic summary-at-low-zoom is not complete. |
| 005 | 区分工作区缩放、视频预览缩放、时间轴缩放，不让一个手势同时修改三者。 | **Partial** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped |
| 006 | 添加全局鸟瞰图：一眼看到所有工具和预览，点击任一节点飞入其位置。 | **Partial** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped；Overview/focus controls exist; clickable node overview is incomplete. |
| 007 | 添加视口导航历史：回到刚才看的位置，再前进到下一处工作现场。 | **Planned** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped；No viewport back/forward history evidenced. |
| 008 | 工具允许放到屏幕之外；边缘显示方向提示，点击可带回视口。 | **Planned** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped；No off-screen direction indicator/recall evidence. |
| 009 | “适合全部内容”把整个工作现场缩到可见范围，“回到当前字幕”只定位对象相关区域。 | **Partial** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped |
| 010 | 建立排版岛、时间岛、字体岛等可命名区域；用户能自由摆放并在区域间切换。 | **Planned** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped；No named spatial islands evidence. |
| 011 | 允许把视频预览固定在屏幕上，其他工作区节点从它下面移动。 | **Planned** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped；No screen-pinned preview conversion evidence. |
| 012 | 允许屏幕固定节点与世界节点相互转换，转换后保留实例身份和对象绑定。 | **Planned** | main · #61 gate | `SPATIAL_EXPERIMENTAL` · `SpatialWorkspace` | #61 presentation smoke; item-level mapping absent | Not 240-ID mapped | Not item-mapped；No screen/world node conversion evidence. |

### 二、工具实例：从笨重弹窗变成可以组织的器具

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 013 | 点击工具图标直接生成具体工具实例，不跳转到独立设置页面。 | **Implemented** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 014 | 同一种工具可创建多个实例，分别观察当前焦点、选择集或指定事件。 | **Implemented** | main via #53 + #72 UI Contract | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` + `WorkspaceUiBindingContract` | #61 + `WorkspaceUiBindingContractTest`; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 015 | 工具首次打开为临时层，点击外部空白自动收回；打开另一工具时自然交接。 | **Implemented** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 016 | 再次点击入口图标收回当前临时工具，保留草稿和局部滚动状态。 | **Partial** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 017 | 用户可将临时工具转为驻留工具，点击外部不再让它消失。 | **Implemented** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 018 | 驻留工具能收成侧书签，点击书签立即恢复原实例。 | **Implemented** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 019 | 工具具有紧凑、标准、精确三种内容表示；切换的是密度，不是重新创建工具。 | **Implemented** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 020 | 拖动工具标题即可移动，拖到边缘能停靠，拖回工作区恢复自由摆放。 | **Partial** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 021 | 工具支持复制实例，复制时明确选择沿用绑定还是跟随新焦点。 | **Implemented** | main via #53 + #72 UI Contract | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` + `WorkspaceUiBindingContract` | #61 + `WorkspaceUiBindingContractTest`; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 022 | 关闭、收起、最小化、隐藏、解除绑定各有独立含义和可查入口。 | **Implemented** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 023 | 多工具同时编辑同一参数时同步显示正式值，并明确识别尚未提交的草稿冲突。 | **Blocked** | main via #53 · #61 gate | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` | #61 + tool/workspace tests; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped；Depends on a unified Draft/Interaction Transaction conflict model. |
| 024 | 为失效绑定保留工具外壳和重新关联入口，不悄悄换成另一条字幕。 | **Implemented** | main via #53 + #72 UI Contract | `TOOL_INSTANCES_EXPERIMENTAL` · `ToolInstanceWorkspace` + `WorkspaceUiBindingContract` | #61 + `WorkspaceUiBindingContractTest` (missing pin stays unresolved) | Not 240-ID mapped | Not item-mapped |

### 三、透明、模糊、叠加：让视觉层次成为操作语言

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 025 | 工具底板采用可调半透明材质，让用户仍能看见后方画面与空间关系。 | **Implemented** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 026 | 工具底板实施真实背景模糊；不把一张灰色透明底板冒充模糊。 | **Implemented** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 027 | 当前编辑层提高边界与文字清晰度，驻留观察层降低视觉重量。 | **Implemented** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 028 | 正在操纵字幕时，非相关工具淡出，相关读数与控制器保持清楚。 | **Partial** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 029 | 工具展开位置主动避开当前字幕的关键观察区域。 | **Blocked** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior；Needs object screen bounds + placement policy; explicitly left incomplete in #47. |
| 030 | 工具可在透明、磨砂、实底三种材质间切换，分别检验观看与阅读效果。 | **Implemented** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 031 | 允许用户调节工具透明度与模糊程度，但保持控件和状态可辨认。 | **Implemented** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 032 | 多层工具形成明确深度：边界、阴影与遮挡关系一致，而不是杂乱堆卡片。 | **Partial** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 033 | 空白的透明辅助层让触摸穿透，实际控制区才捕获输入。 | **Partial** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 034 | 提供“临时看穿工具”动作，按住时淡化工具，松开立即恢复。 | **Implemented** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 035 | 多层叠加支持层概览：显示每层身份、可见性、驻留状态和活动情况。 | **Implemented** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |
| 036 | 低性能设备可降级模糊与阴影，工具行为、身份与召回能力保持完整。 | **Implemented** | main via #54 · #61 gate | `GLASS_LAYERED_EXPERIMENTAL` · `GlassLayeredWorkspace` | #61 + `GlassRenderPlan`/Android regressions | Not 240-ID mapped | Required for blur/perf/thermal behavior |

### 四、侧书签与四边工具层

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 037 | 左右侧设置工具书签轨，显示工具图标、选中状态与必要的短对象标识。 | **Implemented** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |
| 038 | 同类工具书签可成组叠放，展开后显示各实例，避免一列重复图标。 | **Implemented** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |
| 039 | 书签允许拖动排序、跨侧移动、分组和命名。 | **Partial** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped；Auto grouping exists; complete user rename/group UX remains incomplete. |
| 040 | 点击书签召回，左右滑过书签轨连续浏览工具，确认后停在目标工具。 | **Partial** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped；Click recall exists; true continuous rail scrub remains incomplete. |
| 041 | 长按书签显示工具名称、绑定对象与核心值；继续拖动可把工具拉出来。 | **Partial** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped；Long-press metadata and rail drag exist; pulling a tool out as a free surface is incomplete. |
| 042 | 顶部应用内抓手下拉项目与会话层，包含导入、保存、工程状态和最近现场。 | **Partial** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |
| 043 | 底部抓手上拉时间编辑层，紧凑时间轴仍保留明确关联。 | **Implemented** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |
| 044 | 左侧拉出事件、样式、资源与问题导航；右侧拉出参数工具集合。 | **Implemented** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |
| 045 | 四边层可临时打开，也可驻留；展开方向按实际可用空间改变。 | **Partial** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |
| 046 | 边缘工具层支持预览展开程度，拖动时连续调整，松手吸附到稳定尺寸。 | **Implemented** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |
| 047 | 多边层同时展开时有明确空间分配，必要时覆盖，不靠无限压缩内容解决。 | **Implemented** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |
| 048 | 所有边缘手势都有可见抓手和点击替代入口，起手区域避开系统返回与通知栏。 | **Partial** | main via #67 · #69 gate | `EDGE_BOOKMARK_EXPERIMENTAL` · `EdgeBookmarkWorkspace` / `EdgeWorkspaceModel.kt` | `EdgeWorkspaceModelTest` + Android regression + #69 presentation invariant gate | Not 240-ID mapped | Not item-mapped |

### 五、字幕对象模式

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 049 | 长按画面字幕进入对象选择与编辑，冻结命中所用时刻，避免播放中候选漂移。 | **Implemented** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 050 | 单一可靠候选直接进入编辑；重叠候选显示文本、事件身份、Layer 与时间。 | **Implemented** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 051 | 没有可靠字形命中时显示候选，不把最近的锚点伪装成精确像素归属。 | **Implemented** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 052 | 选中字幕出现对象标识、操纵杆和紧凑能力图标，工具围绕该对象调用。 | **Implemented** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 053 | 对象能力包括文字、字体、排版、位置、旋转、时间、效果、Raw 与检查。 | **Implemented** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 054 | 长按后滑向能力图标形成连续菜单操作，松手打开相应工具。 | **Partial** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped；Object HUD exists; continuous long-press radial scrub is explicitly incomplete. |
| 055 | 支持选择多条事件并显示选择数量，同时提供分组移动与统一参数编辑。 | **Implemented** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 056 | 可将对象固定为参考，让另一工具继续跟随当前焦点进行比较。 | **Implemented** | main via #55 + #72 UI Contract | `SUBTITLE_OBJECT_EXPERIMENTAL` + `WorkspaceUiBindingContract` | #61 + `WorkspaceUiBindingContractTest`; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |
| 057 | 对象身份在画面、列表、时间轴、工具中一致显示，颜色只作辅助。 | **Partial** | main via #55 + #72 UI Contract | `EditorUiState.objects.currentEvent` + `SUBTITLE_OBJECT_EXPERIMENTAL` | `EditorUiContractTest` + #61; cross-surface visual identity still not item-mapped | Not 240-ID mapped | Not item-mapped |
| 058 | 将样式视为可选择对象，能从字幕追踪到共享样式及其他引用事件。 | **Partial** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped；Shared Style relationships exist; full style-object semantics remain incomplete. |
| 059 | 提供对象关系视图，观察样式继承、覆盖与工具绑定，点击关系可跳转。 | **Partial** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped；Relation view exists; deeper property/override graph remains incomplete. |
| 060 | 无视频时仍能进入字幕对象模式，在明确背景和脚本坐标中编辑。 | **Implemented** | main via #55 · #61 gate | `SUBTITLE_OBJECT_EXPERIMENTAL` · `SubtitleObjectWorkspace` | #61 + Android regression; item-level mapping incomplete | Not 240-ID mapped | Not item-mapped |

### 六、操纵杆与局部聚焦放大

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 061 | 保留现有拖动与旋转操纵杆，以它作为触屏几何操作的主入口。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 062 | 开始操作时放大字幕及其周边，松手后回到原预览观察状态。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 063 | 放大视图放在手指外侧，自动选择遮挡较少的位置，移动过程中避免频繁跳位。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 064 | 放大区域保留周边背景、锚点、旋转中心和参考线，便于判断相对位置。 | **Partial** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Required: compositor/video Surface path；System Magnifier samples the Activity View; hardware-video Surface capture still needs real-device proof. |
| 065 | 操作期间显示位置、角度和变化量，读数跟随操作但不挡住字幕。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 066 | 支持主画面局部聚焦与独立放大窗两种呈现，用户可选择并比较。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 067 | 放大过渡保持操作坐标映射稳定，不能因画面变大而让字幕跳动。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 068 | 操纵杆设置粗调与细调增益，允许在一次手势中显式切换精度。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 069 | 拖动接近吸附目标时显示预告，触觉反馈提示吸附，允许暂时解除吸附。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 070 | 旋转中心可独立调整；移动旋转中心与旋转字幕采用不同抓手。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 071 | 已知不支持直接操控的复杂标签组合显示原因，并保留 Raw 与数值入口。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |
| 072 | 完成或取消时清除放大、临时轨迹和捕获，保留正式修改或正确回滚。 | **Implemented** | main via #51 · #61 gate | `PRECISION_LENS_EXPERIMENTAL` · `PrecisionLensWorkspace` / precision interaction code | #61 + `PrecisionInteractionMathTest` + Android regression | Not 240-ID mapped | Not item-mapped |

### 七、常驻时间轴：整个编辑现场的时间骨架

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 073 | 主界面底部常驻紧凑时间轴，包含播放头、字幕事件条与播放控制。 | **Implemented** | #98 Timeline Dock re-land candidate | `TIMELINE_DOCK_EXPERIMENTAL` + `WorkbenchPreview` transport/event strip + `ModernTimelinePane(compact=true)` | `TimelineDockPolicyTest` + `UiVariantRegistryTest` + `PresentationStateSmokeInstrumentedTest` | Not 240-ID mapped | DEVICE 未验证；#98 合入 main 前保持 candidate authority。 |
| 074 | 上拉展开多轨时间轴，下拉恢复紧凑状态，时间视口和选择不丢失。 | **Partial** | #98 Timeline Dock re-land candidate · main timeline core | 单一 `ModernTimelinePane` 实例在 compact / expanded 之间只改变约束与密度；上拉展开、下拉收拢 | `TimelineDockPolicyTest` + presentation invariant gate；现有 timeline viewport tests | Not 240-ID mapped | 展开/收拢与局部现场连续性已实现；真正多轨模型仍未实现，因此不得标 Implemented。 |
| 075 | 双指缩放时间尺度，水平拖动浏览时间，纵向拖动浏览轨道。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 076 | 拖动事件条调整整条时间，拖动左右端点分别调整开始与结束。 | **Implemented** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 077 | 起止点附近出现局部时间放大区与精确读数，避免短事件难以抓取。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped；No local endpoint time magnifier evidenced. |
| 078 | 提供逐帧步进；视频帧索引与 ASS 时间精度分别显示，不暗示任意毫秒都是一帧。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 079 | 时间轴同时显示事件、选择集、循环区、标记和播放头，视觉职责清楚。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 080 | 多语言字幕可以并列轨道显示；时间相近不等同于已建立翻译配对。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped；No mainline parallel multilingual timeline-track implementation evidenced. |
| 081 | ASS Layer 与 UI 轨道分开建模，整理轨道不擅自改写渲染层级。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 082 | 时间轴点击事件同步画面与工具，播放经过事件时不强制替换编辑对象。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 083 | 提供循环播放当前事件、选择范围和手动区间，便于反复检查。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 084 | 时间轴常驻区域可由用户调节高度，保留明确的召回和展开入口。 | **Implemented** | #98 Timeline Dock re-land candidate | `TimelineDockPolicy` bounded resize + snap；`timeline-dock-handle` vertical drag + `timeline-dock-toggle` explicit entry | `TimelineDockPolicyTest` + `UiVariantRegistryTest` + presentation invariant gate | Not 240-ID mapped | DEVICE 未验证；手势体验与触摸命中仍需真机。 |

### 八、时间轴的激进扩展

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 085 | 语义缩放：远处显示章节和字幕密度，中间显示事件，近处显示时间细节。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 086 | 音频波形进入时间轴，支持与字幕边界对照；波形只是参考，不自动保证语义切分。 | **Implemented** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped；Waveform Lite exists in current product timeline. |
| 087 | 添加视频缩略帧带，观察镜头变化与字幕出现时刻。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 088 | 事件重叠、短闪、长空白以可关闭的诊断带显示。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 089 | 点击诊断带定位问题，随后展开相关事件，而不是另开一张诊断页面。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 090 | 拖动选择形成时间范围，范围内事件可以统一偏移或按明确规则缩放。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 091 | 时间吸附支持事件边界、标记、视频帧及音频参考点，各自有开关。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 092 | 提供双播放头比较两个时刻，主播放时钟仍只有一个明确所有者。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 093 | 将某段时间折叠成摘要，展开恢复完整事件，不改动真实时间。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 094 | Transform 区间与关键时间以可视带呈现，支持标签语义允许的编辑操作。 | **Partial** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 095 | 卡拉 OK 分段进入独立细节轨，文字片段与时间段保持对应。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |
| 096 | 创建时间书签，记录时刻、视口和相关事件，一键恢复检查现场。 | **Planned** | main timeline core · #56 historical only | `ModernTimelinePane` + `core/domain/Timeline*`; dedicated Timeline Dock absent from registry | `TimelineViewportPolicyTest` / `AssTimelineSnapTest` / `AssTimelineRelationsTest`; no dock gate | Not 240-ID mapped | Not item-mapped |

### 九、用图标、滑块与直接操控重写参数工具

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 097 | 替换大面积文字分类框，建立统一图标工具轨与清晰的选中状态。 | **Partial** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 098 | 图标长按显示名称，键盘焦点与无障碍访问也能获得名称。 | **Partial** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 099 | 同一图标已有长按业务动作时，名称提示由其他入口提供，避免抢手势。 | **Planned** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 100 | 字号、描边、阴影、字距、透明度等连续参数优先用滑块与即时预览。 | **Implemented** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 101 | 数值旁显示单位，点击数值进入精确输入，拖动数值可做细调。 | **Partial** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 102 | 大范围参数采用合适的非线性刻度，并明确显示真实值与范围。 | **Planned** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 103 | 对齐改为九宫格，点击即可预览与修改，不要求用户输入编号。 | **Implemented** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 104 | 颜色改为色块、调色盘和通道滑块，保留精确颜色输入。 | **Implemented** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 105 | 位置改为画面操控与二维控制板，X、Y 数值作为精确补充。 | **Partial** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 106 | 旋转改为角度盘、操纵杆和读数，可显式输入角度。 | **Partial** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped |
| 107 | 参数图标周围可显示微型数值或状态环，收起工具时仍能观察关键值。 | **Planned** | main · no dedicated 240 slice | Fixed/Canvas parameter tools in `ModernEditorScreen.kt` | Existing editor tests; no 240-ID test mapping | Not 240-ID mapped | Not item-mapped；No micro value/status ring around collapsed parameter icons evidenced. |
| 108 | 共享样式影响范围、错误原因和参数来源按需展开，关键风险信息仍直接可见。 | **Partial** | main via Scope Transparency + #80 Slice E | Fixed/Canvas parameter tools + typed `WorkspaceWriteTarget` | `WorkspaceEditScopeResolverTest`; item-level visual mapping incomplete | Not 240-ID mapped | Not item-mapped |

### 十、把控件拆出来，组成自己的工具

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 109 | 将任意参数从原工具拖到工作区，生成独立参数工具。 | **Partial** | `feat/workspace-live-parameter-projection` | Rotation Z can be explicitly extracted from Position into a persisted Infinite Canvas parameter node; WorkspaceState schema v4 stores projection identity/binding/presentation | `WorkspaceParameterContractTest` + `WorkspaceParameterProjectionTest` + connected projection regression | Not 240-ID mapped | Explicit extraction exists for Rotation Z；arbitrary parameter drag-to-extract remains pending. |
| 110 | 同一参数可同时以滑块、角度盘、数值或二维板呈现，全部连接同一正式状态。 | **Partial** | `feat/workspace-live-parameter-projection` | Rotation Z live projections can switch NUMBER/SLIDER/ANGLE_DIAL and multiple projections share the same Event parameter/EditorViewModel authority | Unit persistence/identity tests + connected preview→commit→Undo regression | Not 240-ID mapped | Rotation Z Angle Dial is implemented with continuous relative angle and commit/cancel routing; connected validation Pending CI. XY Pad and broader parameter families remain pending. |
| 111 | 把字号、字距和描边拼成一个“排版手柄”，用户自定控件顺序。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 112 | 把 X、Y、角度和旋转中心组合成几何控制台。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 113 | 为多个事件建立比较工具，同屏显示差异与来源。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 114 | 自定义工具可以跟随焦点，也可以固定事件或作用于选择集。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 115 | 允许一个二维板控制两个独立参数，坐标轴名称、范围和单位必须明确。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 116 | 建立用户定义的联动旋钮，通过显式公式控制多个参数，使用前显示映射。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 117 | 联动旋钮保存的是参数映射，操作结果写入 ASS 可表达的实际参数。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 118 | 参数分组可折叠成摘要条，展开后保持每个控件身份与草稿。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 119 | 自定义工具支持模板保存；工程事件 ID 不直接混入通用模板。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped |
| 120 | 提供工具构造预览，用户可以试拖控件而不立即改写字幕。 | **Planned** | main · no dedicated 240 slice | No dedicated custom-tool-construction surface | No 240-ID automated mapping | Not 240-ID mapped | Not item-mapped；No dedicated tool-construction preview surface evidenced. |

### 十一、动画与触觉：界面变化必须看得见来处

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 121 | 工具从入口图标展开，收回到原入口或当前书签。 | **Planned** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 122 | 切换工具以连续过渡交代离场与入场，避免两个工具突兀替换。 | **Planned** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 123 | 停靠时显示停靠轮廓，松手后工具过渡到最终位置。 | **Planned** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 124 | 书签召回工具时形成明确的空间连接，便于记住工具来自哪里。 | **Planned** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 125 | 局部聚焦以缩放、淡化和辅助线渐入协同完成。 | **Partial** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 126 | 吸附、端点对齐与事务提交提供不同的轻触觉反馈，可关闭。 | **Partial** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 127 | 多选时对象标识合并成选择摘要，取消多选时恢复各对象呈现。 | **Partial** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 128 | 共享样式改变时短暂强调受影响事件，帮助理解修改范围。 | **Planned** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 129 | 视口飞行导航采用可打断过渡，用户接触屏幕即可接管。 | **Planned** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped；Spatial focus currently jumps; interruptible flight navigation is not evidenced. |
| 130 | 动画被取消或组件重建时仍能完成正确状态清理，业务提交不等待动画回调。 | **Partial** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 131 | 提供减少动态效果策略，使用淡入淡出或直接切换保留相同功能。 | **Planned** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped；No systematic reduced-motion policy evidenced. |
| 132 | 动画参数可以在实验设置中调节，比较速度、弹性与操作响应。 | **Planned** | main · no dedicated 240 slice | Existing presentation / precision interaction surfaces; no dedicated animation slice | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |

### 十二、手势成为连续操作语言

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 133 | 空白区域拖动工作区；工具内部拖动滚动内容；字幕把手拖动领域参数。 | **Partial** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 134 | 双指手势通过起始区域确定作用于工作区、预览还是时间轴。 | **Partial** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 135 | 长按对象后继续滑动选择操作，形成一次连续调用。 | **Planned** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 136 | 工具标题长按进入布局编辑，工具参数区域保持原编辑职责。 | **Planned** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 137 | 两指轻触可召回快捷操作盘，提供可见按钮作为等效入口。 | **Planned** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 138 | 滑块细调通过明确的附加手势或精度开关完成，显示当前增益。 | **Partial** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 139 | 事件列表支持明确进入的范围选择手势，不与普通滚动争抢。 | **Partial** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 140 | 手势开始后捕获对象和参数，焦点变化不把同一手势重定向到别的字幕。 | **Partial** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 141 | 多指参与或离开有明确转移规则，避免丢指时突然提交错误位置。 | **Planned** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 142 | 系统取消、窗口失焦、工程切换都结束捕获并清理临时预览。 | **Partial** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 143 | 加入手势演练场，显示起手区域、当前捕获者和操作反馈，不修改工程。 | **Planned** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped；No gesture rehearsal/training surface evidenced. |
| 144 | 手势可配置映射，但冲突映射需指出；常用功能仍可通过图标访问。 | **Planned** | main · no dedicated 240 slice | Existing pointer/gesture handlers; no dedicated gesture-language surface | Broad Android regression only; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |

### 十三、自适应布局与自动让位

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 145 | 根据窗口宽高、键盘、安全边距与工具最小尺寸安排工具，保持同一产品模型。 | **Partial** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 146 | 竖屏叠加、横屏并列、大屏多工具同时显示，切换后工具身份不变。 | **Partial** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 147 | 键盘出现时当前输入工具避让，预览可暂时紧凑化，键盘退出恢复现场。 | **Partial** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 148 | Popup 优先靠近触发对象；空间不足时转到侧面、上方或边缘层。 | **Partial** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 149 | 工具在浮动、停靠和覆盖承载间转换，保留绑定、草稿、滚动和展开状态。 | **Partial** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 150 | 活动工具拥有必要空间，低优先级工具收成书签，用户可锁定保留。 | **Partial** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 151 | 拖动字幕时工具避让路径，操作结束回到原位置，不保存临时让位几何。 | **Planned** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants；Tool auto-avoid during subtitle drag is not evidenced. |
| 152 | 自动排列提供预览和撤回，不能随每次参数变化重排整张工作现场。 | **Planned** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 153 | 用户排列、布局锁定与自动避让分别控制，临时窗口变化不覆盖正式排列。 | **Planned** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 154 | 支持折叠屏遮挡区与分屏窗口，不能把关键抓手放到不可触区域。 | **Planned** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants；No foldable hinge/occlusion-specific implementation evidenced. |
| 155 | 大字号时工具增长或滚动，保留文字和触摸面积，不机械缩小字体。 | **Partial** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |
| 156 | 提供布局冲突提示，解释哪个工具被收起以及从哪里召回。 | **Planned** | main · no dedicated 240 slice | Adaptive Fixed/Canvas workspace | Broad responsive/emulator regression; no item-level mapping | Not 240-ID mapped | Required for keyboard/split/foldable/large-text variants |

### 十四、字幕几何与空间辅助

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 157 | 显示 ASS 脚本坐标、预览映射与安全框，明确每种单位。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 158 | 位置网格、对齐参考线、中心线和边缘吸附分别可控制。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 159 | 显示移动轨迹并可编辑明确的起点、终点与时间区间。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 160 | 旋转时显示角度弧、轴向和旋转中心，辅助判断三维旋转标签。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 161 | 比例操作显示锁定状态，横向和纵向缩放可以独立控制。 | **Implemented** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 162 | 多事件对齐支持中心、边缘和间距分布，写入范围在执行前可见。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 163 | 制作参考快照，与当前字幕半透明对照，快照不被误当成另一个可编辑事件。 | **Planned** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped；No reference snapshot comparison tool evidenced. |
| 164 | 可在局部放大视图中查看基线、描边边缘与阴影偏移参考。 | **Planned** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 165 | Clip 编辑提供可见轮廓和节点，复杂情况保留 Raw 入口与能力边界。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 166 | Drawing 对象可以进入专用路径编辑器，不把它当普通文字框。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 167 | 不可靠的字形边界采用不同辅助线样式，并显示估计性质。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 168 | 空间工具允许观察多个对象的差异，视图操作本身不增加字幕 Undo。 | **Partial** | main · no dedicated 240 slice | Position/geometry tools + interaction overlays | Existing geometry/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |

### 十五、把效果编辑变成可观察的时间变化

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 169 | 淡入淡出采用可拖时间段与透明度曲线，直接观察变化。 | **Partial** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 170 | Transform 以参数轨显示起止值和区间，关联 Raw 标签。 | **Partial** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 171 | 播放头经过效果区间时，工具显示当前有效值与声明值的区别。 | **Partial** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 172 | 图形曲线编辑必须限制在 ASS 能表达的语义中，超出时明确提出采样转换方案。 | **Planned** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped；No curve editor with explicit ASS-semantic boundary/sampling conversion evidenced. |
| 173 | 样式继承值、事件覆盖值和动画有效值在同一控件里可展开追踪。 | **Partial** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 174 | 为运动、旋转、缩放与透明度提供效果预览片段，可拖到明确对象上应用。 | **Planned** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 175 | 效果组合显示写入标签和冲突，不把相互覆盖的效果悄悄叠加。 | **Partial** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 176 | 在工具内拖动小播放头检查效果，明确是否驱动全局播放时刻。 | **Partial** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 177 | 建立效果前后对照视图，比较同一事件的未修改快照与当前版本。 | **Planned** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped；No before/after snapshot compare surface evidenced. |
| 178 | 卡拉 OK 编辑将文字片段、时长与高亮预览连接起来。 | **Partial** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 179 | 效果关闭可以临时仅影响预览，正式移除则作为可撤销文档编辑。 | **Planned** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 180 | 效果参数的滑动、拖动与数值输入共用一次明确的提交逻辑。 | **Partial** | main · no dedicated 240 slice | Effects/Animation tools | Existing effects/editor tests; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |

### 十六、文本、双语与 Raw 的新呈现

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 181 | 点击对象文字图标出现就地编辑层，保留字幕与视频上下文。 | **Partial** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 182 | 正文、标签和可见渲染文本可切换观察，明确哪些内容被保留或转换。 | **Partial** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 183 | Raw 编辑支持标签着色、匹配提示、来源定位与错误标识。 | **Partial** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 184 | 点击可视参数能定位对应 Raw 标签；点击标签能召回相应参数工具。 | **Planned** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped；No bidirectional structured-control ↔ Raw-tag navigation evidenced. |
| 185 | 双语字幕支持上下或左右对照编辑，语言与事件配对关系显式建立。 | **Partial** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 186 | 允许只筛选一种语言或一类样式，减少批量操作的重复工作。 | **Partial** | main batch engine + #80 Slice E | `RuleBatchPane` + `EditorUiState.batch` | `EditorUiContractTest` + existing batch engine tests; language-specific filter remains incomplete | Not 240-ID mapped | Not item-mapped |
| 187 | 搜索结果出现在对象轨和时间轴，点击结果定位并召回相关工具。 | **Partial** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 188 | 多事件正文差异可以并列查看，批量替换先显示实际影响范围。 | **Partial** | main batch preview + #80 Slice E | `RuleBatchPane` preview shows affected/changed counts and examples; full side-by-side multi-event diff remains incomplete | `EditorUiContractTest` + batch preview path; no complete 240-ID scenario | Not 240-ID mapped | Not item-mapped |
| 189 | 标签块保留可折叠表示，未知标签仍保留原始内容。 | **Partial** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 190 | 输入时实时预览使用草稿层；点击外部收回工具不悄悄吞掉草稿。 | **Partial** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 191 | 提供换行和排版辅助，建议与正式写入分开，应用后可撤销。 | **Planned** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |
| 192 | 建立文本聚焦现场：预览、当前文本和时间轴同时存在，其他工具以书签保留。 | **Planned** | main · no dedicated 240 slice | Event/Text/Raw/Review surfaces | Round-trip/editor regressions; no 240-ID mapping | Not 240-ID mapped | Not item-mapped |

### 十七、字体与资源也进入工作现场

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 193 | 字体浏览以真实字形样张展示当前字幕，不只列文件名。 | **Partial** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 194 | 拖动字体样张到字幕对象上预览，确认应用后写入明确目标。 | **Planned** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths；No drag-font-sample-to-subtitle preview/apply flow evidenced. |
| 195 | 共享样式更换字体时显示受影响事件集合，避免误认为只改当前事件。 | **Partial** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 196 | 缺失字形直接在样张与预览检查结果中标识，区分字体缺失和字形缺失。 | **Partial** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 197 | 展示实际字体匹配与回退结果，在有证据时指出 renderer 使用的字体。 | **Implemented** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 198 | Font 工具可固定为侧书签，切换字幕时比较不同事件的字体使用。 | **Partial** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 199 | 资源层用缩略卡展示视频、字幕、字体与附件，卡片可展开查看来源。 | **Partial** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 200 | 资源失效时保留原引用与重新关联入口，不自动改成另一份同名文件。 | **Partial** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 201 | UI 字体可以单独选择和预览，不修改字幕 Fontname。 | **Planned** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 202 | 建立字体对照墙，在受控缩略预览中比较多个候选字体。 | **Planned** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths；No font comparison wall evidenced. |
| 203 | MKV 内字幕与附件在工程资源视图中明确呈现，容器修改必须显示实际保存目标。 | **Partial** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |
| 204 | 资源使用关系可以展开，观察哪些样式或事件依赖某字体。 | **Partial** | main · no dedicated 240 slice | Font Manager / Project / Diagnostics | Fontconfig/native + font diagnostics; no 240-ID mapping | Not 240-ID mapped | Required for renderer/font/container device paths |

### 十八、更离谱的实验：把编辑环境当成可操纵对象

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 205 | 参数星座：选中字幕后，相关参数围绕对象形成可展开的空间控件簇。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped；No parameter-constellation surface evidenced. |
| 206 | 工具扇面：在工具组之间横向滑动，当前工具保持清楚，其余显示侧边预览。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 207 | 字幕关系地图：事件、样式、覆盖与诊断成为可导航节点，点击节点返回具体编辑。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 208 | 时间门户：在工作区放置两个时间窗口，分别观察不同片段并保持各自时间视口。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 209 | 预览分身：同一媒体源提供局部放大、全景与对照视图，资源策略避免无界复制解码器。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 210 | 工作现场路线：用户记录排版、字体检查、时间校对的视口路线，一键巡回。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 211 | 工具磁场：用户定义吸附区，相关工具拖近时形成整齐工具组，可临时解除。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 212 | 视觉目的控制板：以明确映射调整“更疏”“更厚”“更靠边”，同时显示实际参数变化。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 213 | 参数触控垫：手指在二维垫上连续调整一组用户指定参数，映射与增益可查看。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 214 | 多对象连杆：显示对象间对齐关系，拖动连杆预览共同变化，确认后提交实际参数。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 215 | 操作回放：展示一次编辑的对象、变化与工具调用轨迹，可定位对应 Undo 节点。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped |
| 216 | 工作现场快照墙：并列比较多个布局和文档快照，恢复前显示恢复对象与差异。 | **Planned** | main · no dedicated 240 slice | No dedicated surface | None mapped | Not 240-ID mapped | Not item-mapped；No workspace snapshot wall evidenced. |

### 十九、完整实验必须具备的状态与持久化

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 217 | 文档、工作区排列、临时显示、草稿和动画分别有明确状态所有者。 | **Partial** | main via #62/#72/#74/#77 + #80 Slice E | `WorkspaceState` + `EditorUiState` + Binding/Preview/Resource/Batch contract slices | #61 + `EditorUiContractTest` + `WorkspaceUiBindingContractTest` | Not 240-ID mapped | Batch Filter/Transform draft remains presentation-local by contract; animation ownership still incomplete |
| 218 | 一次连续拖动只生成一次正式编辑与一次字幕 Undo。 | **Partial** | main via #74 Preview boundary + #80 batch commit boundary | UI Contract keeps transient preview separate from canonical commit; batch recipe commits as one transaction | `EditorUiContractTest` + existing interaction/batch tests | Not 240-ID mapped | Continuous interaction coverage remains incomplete across all tools |
| 219 | 工具移动、停靠与书签切换进入布局历史，不混入字幕 Undo。 | **Planned** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped；Layout state is separate from subtitle state, but a dedicated layout-history stack is not evidenced. |
| 220 | 手势取消清除临时预览；完成后保留正式参数；动画中断不改变业务结论。 | **Partial** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped |
| 221 | 点击外部、系统返回、收起工具和退出工程各有明确草稿处理规则。 | **Partial** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped |
| 222 | 保存工程现场包含实例身份、绑定、布局、书签、时间视口与必要局部状态。 | **Partial** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped |
| 223 | 保存 ASS 和保存工作现场分别显示未保存状态及实际保存结果。 | **Partial** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped |
| 224 | 用户模板保存抽象绑定与布局，工程现场保存具体对象绑定。 | **Planned** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped；No user-template abstract-binding persistence model evidenced. |
| 225 | 横竖屏与键盘引起的临时投影不覆盖用户正式布局。 | **Partial** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped |
| 226 | 重启恢复已提交现场，未应用草稿明确标识，不恢复半完成手势捕获。 | **Partial** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped |
| 227 | 对象删除后绑定失效可诊断，重新导入工程不复用错误事件身份。 | **Partial** | main via #62 + #72 UI Contract | `WorkspaceUiBindingContract` / `UnresolvedPinnedEvent` | `WorkspaceUiBindingContractTest` covers missing pin; re-import identity still not fully item-mapped | Not 240-ID mapped | Not item-mapped |
| 228 | 导出后以真实 renderer 检查代表性结果，不把 UI 操控框当作渲染真相。 | **Partial** | main via #62 UI Contract | `WorkspaceState` + UI Contract boundary | #61 + stabilization suites; `EditorUiContractTest` (merged via #62) | Not 240-ID mapped | Not item-mapped |

### 二十、用完整体验验收，而不是数按钮

| ID | 原始要求 | 状态 | Authority | Surface / code | 自动证据 | Visual | DEVICE / 阻塞 |
|---|---|---|---|---|---|---|---|
| 229 | 长按字幕、选择对象、拖杆、聚焦放大、松手提交、撤销，连续走通。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 230 | 打开描边滑块、实时预览、点击外部收回、从侧书签召回，状态一致。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 231 | 同时打开两个位置工具，分别固定不同事件，操作不串对象。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 232 | 时间轴拖动事件边界、循环播放、对象工具观察同一事件，全链路一致。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 233 | 透明与模糊层叠加时，目标字幕可判断、控件可读、触摸目标正确。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 234 | 在大于一屏的工作现场移动、缩放、鸟瞰、回到对象，找得到所有工具。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 235 | 键盘、旋转、分屏、大字号出现后，工具仍可操作，返回后现场可恢复。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 236 | 动画中途再次点击、切换工具或返回，界面不锁死，不留下透明拦截层。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 237 | 复杂 ASS、重叠事件和不可靠命中显示能力边界，仍有可用编辑路径。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 238 | 保存并重启后，书签、绑定、工具状态与时间视口按约定恢复。 | **Partial** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation |
| 239 | 真机连续操作检查遮挡、误触、响应、模糊成本和耗电，记录问题而非只拍静态截图。 | **Planned** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation；Explicit physical-device continuous-use validation remains required. |
| 240 | 最终实验交付必须包含完整视觉与交互链路；占位图标、无效滑块、假的模糊和只展示不运行的动画不计完成。 | **Planned** | acceptance layer | Scenario / acceptance matrix | No complete scenario matrix mapped | Not 240-ID mapped | Required for final scenario validation；Final integrated visual/interaction acceptance has not been reached. |

## 状态升级规则

1. Planned → Partial：必须出现可定位到 current main 或当前权威 PR 的真实实现，不接受仅有设计说明。
2. Partial → Implemented：原要求的核心交互语义必须闭合；已知缺口必须消失，并至少有自动回归或可重复验证入口。
3. Implemented 不自动等于 Verified：Production Visual Evidence 与 DEVICE evidence 单独登记，不能由 CI 绿色替代。
4. Presentation 级 smoke 只能证明 canonical state 不被 presentation 破坏，不能代替 12 个条目的逐项功能测试。
5. 任何新增实现都必须保持 UI Contract / canonical Editor state 边界，不允许 presentation 拥有第二套字幕业务状态。
6. 240 清单继续按依赖图推进，不按编号机械串行；账本编号只负责可追踪性。

## 下一批工程切片

当前顺序保持：Presentation invariant gate（#61 + #69，已合入）→ UI Contract Slice A（#62，已合入）→ Binding/current object Slice B（#72，已合入）→ Preview/Commit → fonts/container/diagnostics → Write Target/batch intent → Timeline Dock（#98 current-main re-land）→ 后续 240 实验。

因此，在 Preview/Commit 等 UI Contract 后续切片尚未稳定前，本账本继续作为追踪基线，不把 073+ 的新 presentation 大块并行塞进高冲突热点文件。
