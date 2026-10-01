package io.github.assworkbench.app.ui

import io.github.assworkbench.app.ui.workspace.SurfaceGeometry

/** A single ownership map for fixed navigation, Canvas discovery and initial density. */
internal enum class WorkbenchToolGroup(val title: String) {
    CONTENT("字幕编辑"), TIMING("时间与同步"), APPEARANCE("排版与动画"),
    RESOURCES("字体资源"), VALIDATION("检查与诊断"), PROJECT("工程与输出"),
}

// Names are persistence keys. Reclassification must not rename them.
internal enum class WorkbenchTool(
    val title: String,
    val group: WorkbenchToolGroup,
    val description: String,
    private val initialWidth: Float = 420f,
    private val initialHeight: Float = 480f,
) {
    SUBTITLES("字幕", WorkbenchToolGroup.CONTENT, "列表、搜索和选择"),
    TEXT("正文", WorkbenchToolGroup.CONTENT, "文本、时间摘要和拆分"),
    EVENT("事件结构", WorkbenchToolGroup.CONTENT, "Layer、Actor、注释和增删合并", 390f, 340f),
    BATCH("批量规则", WorkbenchToolGroup.CONTENT, "筛选、变换、预览和提交", 500f, 580f),
    TIMELINE("时间轴", WorkbenchToolGroup.TIMING, "波形、区间和吸附", 620f, 460f),
    FRAMES("帧时间", WorkbenchToolGroup.TIMING, "视频 PTS、CFR 和 VFR 帧对齐"),
    STYLE("样式", WorkbenchToolGroup.APPEARANCE, "共享 Style 的字体、颜色和外观", 460f, 540f),
    POSITION("位置与几何", WorkbenchToolGroup.APPEARANCE, "Event 位置、变换、裁剪及 Style 布局", 460f, 540f),
    EFFECTS("效果与动画", WorkbenchToolGroup.APPEARANCE, "淡入淡出、模糊和 Transform", 500f, 580f),
    KARAOKE("卡拉OK", WorkbenchToolGroup.APPEARANCE, "音节时长和高亮标签", 460f, 520f),
    VECTOR_CLIP("矢量裁剪", WorkbenchToolGroup.APPEARANCE, "检查与编辑已有矢量裁剪路径", 500f, 540f),
    FONTS("字体管理", WorkbenchToolGroup.RESOURCES, "导入文件、字形覆盖和绑定"),
    FONT_REQUIREMENTS("字体依赖", WorkbenchToolGroup.RESOURCES, "ASS 实际请求的字体与打包需求"),
    QC("质量检查", WorkbenchToolGroup.VALIDATION, "Linter、时间和文本问题", 460f, 540f),
    COMPATIBILITY("兼容性", WorkbenchToolGroup.VALIDATION, "不同播放环境的 ASS 支持风险"),
    DIAGNOSTICS("运行诊断", WorkbenchToolGroup.VALIDATION, "有效值、构建身份和渲染日志"),
    PROJECT("工程与封装", WorkbenchToolGroup.PROJECT, "工程概况、MKV 轨道和写回", 420f, 360f),
    CAPABILITIES("工具目录", WorkbenchToolGroup.PROJECT, "按职责查找全部工具"),
    ;

    fun initialGeometry(x: Float, y: Float): SurfaceGeometry =
        SurfaceGeometry(x, y, initialWidth, initialHeight)
}

internal enum class PositionSection(val title: String) {
    PLACEMENT("位置与路径"), TRANSFORM("变换"), CLIP("矩形裁剪"), STYLE_LAYOUT("Style 布局"),
}
