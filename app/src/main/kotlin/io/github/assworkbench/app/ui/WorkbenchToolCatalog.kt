package io.github.assworkbench.app.ui

import io.github.assworkbench.app.ui.workspace.SurfaceGeometry
import io.github.assworkbench.app.ui.workspace.WorkspaceBinding

/** Task groups from the UI constitution. Tool names remain persistence keys. */
internal enum class WorkbenchToolGroup(val title: String) {
    PROJECT("工程与文件"),
    NAVIGATION("对象与选择"),
    TEXT_EVENT("文本与事件"),
    TIME("时间"),
    TYPESETTING("排版与空间"),
    EFFECTS("时间效果"),
    RESOURCES("字体与资源"),
    VALIDATION("检查与诊断"),
    BATCH("批量与自动化"),
    WORKSPACE("工作区"),
}

internal enum class ToolContextKind {
    EVENT,
    SELECTION,
    DOCUMENT,
    RESOURCE,
    WORKSPACE,
}

internal data class ToolDescriptor(
    val contextKind: ToolContextKind,
    val supportsFollowFocus: Boolean = false,
    val supportsFollowSelection: Boolean = false,
    val supportsPinnedEvent: Boolean = false,
    val canDuplicate: Boolean = false,
    val preferredHost: ToolHostHint = ToolHostHint.SIDE_INSPECTOR,
) {
    val eventBindable: Boolean get() = supportsFollowFocus || supportsPinnedEvent
    val defaultBinding: WorkspaceBinding
        get() = if (supportsFollowSelection && contextKind == ToolContextKind.SELECTION) {
            WorkspaceBinding.FollowSelection
        } else {
            WorkspaceBinding.FollowFocus
        }
}

// Names are persistence keys. Reclassification must not rename them.
internal enum class WorkbenchTool(
    val title: String,
    val group: WorkbenchToolGroup,
    val description: String,
    private val initialWidth: Float = 420f,
    private val initialHeight: Float = 480f,
) {
    SUBTITLES("字幕导航", WorkbenchToolGroup.NAVIGATION, "列表、搜索、Focus 与 Selection"),
    TEXT("正文", WorkbenchToolGroup.TEXT_EVENT, "Event Text、局部 override 与换行"),
    EVENT("事件结构", WorkbenchToolGroup.TEXT_EVENT, "Layer、Actor、Dialogue/Comment 与结构操作", 390f, 340f),
    BATCH("批量规则", WorkbenchToolGroup.BATCH, "Filter → Transform → Preview → Commit", 500f, 580f),

    TIMELINE("时间轴", WorkbenchToolGroup.TIME, "区间、播放头、波形、吸附与重叠/间隙", 620f, 460f),
    FRAMES("帧时间", WorkbenchToolGroup.TIME, "视频 PTS、CFR/VFR 与帧对齐"),

    STYLE("样式", WorkbenchToolGroup.TYPESETTING, "共享 Style 的字体、颜色、描边、字距与布局", 460f, 540f),
    POSITION("位置与几何", WorkbenchToolGroup.TYPESETTING, "Event 位置、运动、原点、变换与裁剪", 460f, 540f),
    VECTOR_CLIP("矢量裁剪", WorkbenchToolGroup.TYPESETTING, "检查并编辑已有矢量裁剪路径", 500f, 540f),

    EFFECTS("效果与动画", WorkbenchToolGroup.EFFECTS, "淡入淡出、Blur 与 Transform", 500f, 580f),
    KARAOKE("卡拉 OK", WorkbenchToolGroup.EFFECTS, "音节时长与高亮标签", 460f, 520f),

    FONTS("字体管理", WorkbenchToolGroup.RESOURCES, "导入资源、字形覆盖、绑定与封装选择"),
    FONT_REQUIREMENTS("字体依赖", WorkbenchToolGroup.RESOURCES, "ASS 实际请求、缺失资源与依赖诊断"),

    QC("质量检查", WorkbenchToolGroup.VALIDATION, "Linter、时间与文本问题"),
    COMPATIBILITY("兼容性", WorkbenchToolGroup.VALIDATION, "不同播放环境的 ASS 支持风险"),
    DIAGNOSTICS("运行诊断", WorkbenchToolGroup.VALIDATION, "有效值、构建身份、renderer 与运行日志"),

    PROJECT("工程与封装", WorkbenchToolGroup.PROJECT, "工程概况、保存对象、MKV 轨道与写回", 420f, 360f),
    CAPABILITIES("工具目录", WorkbenchToolGroup.WORKSPACE, "查找工具、呈现能力与工作区入口"),
    ;

    fun initialGeometry(x: Float, y: Float): SurfaceGeometry =
        SurfaceGeometry(x, y, initialWidth, initialHeight)

    val descriptor: ToolDescriptor
        get() = when (this) {
            STYLE, POSITION -> ToolDescriptor(
                contextKind = ToolContextKind.EVENT,
                supportsFollowFocus = true,
                supportsPinnedEvent = true,
                canDuplicate = true,
            )
            SUBTITLES -> ToolDescriptor(
                contextKind = ToolContextKind.SELECTION,
                supportsFollowFocus = true,
                supportsFollowSelection = true,
                preferredHost = ToolHostHint.INLINE,
            )
            TEXT, EVENT, EFFECTS, KARAOKE, VECTOR_CLIP -> ToolDescriptor(
                contextKind = ToolContextKind.EVENT,
                supportsFollowFocus = true,
            )
            BATCH -> ToolDescriptor(
                contextKind = ToolContextKind.SELECTION,
                supportsFollowSelection = true,
            )
            FONTS, FONT_REQUIREMENTS -> ToolDescriptor(ToolContextKind.RESOURCE)
            TIMELINE, FRAMES, QC, COMPATIBILITY, DIAGNOSTICS, PROJECT ->
                ToolDescriptor(ToolContextKind.DOCUMENT)
            CAPABILITIES -> ToolDescriptor(
                contextKind = ToolContextKind.WORKSPACE,
                preferredHost = ToolHostHint.INLINE,
            )
        }
}

internal enum class PositionSection(val title: String) {
    PLACEMENT("位置与路径"),
    TRANSFORM("变换"),
    CLIP("矩形裁剪"),
    STYLE_LAYOUT("Style 布局"),
}
