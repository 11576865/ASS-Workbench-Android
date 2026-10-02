package io.github.assworkbench.app.ui

/**
 * Registry entry for a complete editor presentation.
 *
 * Persistence intentionally uses enum names because existing .asswb project files
 * already store FIXED / CANVAS_EXPERIMENTAL in workspace_mode.
 */
internal enum class UiVariantStatus(val label: String) {
    STABLE("稳定"),
    EXPERIMENTAL("实验"),
    ARCHIVED("归档"),
}

internal enum class WorkspacePresentationMode(
    val title: String,
    val status: UiVariantStatus,
    val description: String,
) {
    FIXED(
        title = "固定工作台",
        status = UiVariantStatus.STABLE,
        description = "预览、字幕导航与当前工具采用稳定的自适应固定布局。",
    ),
    CANVAS_EXPERIMENTAL(
        title = "自由 Canvas",
        status = UiVariantStatus.EXPERIMENTAL,
        description = "以预览为底层，多个工具作为可移动 Surface 叠加与组织。",
    ),
    PAGER_EXPERIMENTAL(
        title = "聚焦翻页工作台",
        status = UiVariantStatus.EXPERIMENTAL,
        description = "字幕、预览、当前工具三页切换；一次只把一个主要任务放到前台。",
    ),
    SPATIAL_EXPERIMENTAL(
        title = "空间工作现场",
        status = UiVariantStatus.EXPERIMENTAL,
        description = "把预览、字幕与工具放进大于屏幕的二维工作区；支持平移、缩放、鸟瞰与节点召回。",
    ),
    PRECISION_LENS_EXPERIMENTAL(
        title = "操纵杆精密放大工作台",
        status = UiVariantStatus.EXPERIMENTAL,
        description = "围绕字幕几何操纵杆加入局部放大、粗细调、吸附预告、触觉反馈与实时读数。",
    ),
    TOOL_INSTANCES_EXPERIMENTAL(
        title = "工具实例工作台",
        status = UiVariantStatus.EXPERIMENTAL,
        description = "同一工具可多实例；临时、驻留、侧书签和隐藏状态彼此独立，绑定与局部现场随实例保留。",
    ),
    GLASS_LAYERED_EXPERIMENTAL(
        title = "玻璃叠层工作台",
        status = UiVariantStatus.EXPERIMENTAL,
        description = "工具以独立透明/磨砂/实底层叠加在预览上；支持真实背景模糊、层概览与临时看穿。",
    ),
    SUBTITLE_OBJECT_EXPERIMENTAL(
        title = "字幕对象工作台",
        status = UiVariantStatus.EXPERIMENTAL,
        description = "长按画面冻结命中时刻并选择字幕对象；能力围绕对象展开，并保留候选置信与关系信息。",
    ),
}

/**
 * Single registration point for all editor UI variants.
 *
 * New presentations should be added here and rendered by ModernEditorScreen.
 * Keeping discovery metadata centralized lets UI Lab grow without replacing
 * or silently deleting older presentations.
 */
internal object UiVariantRegistry {
    val entries: List<WorkspacePresentationMode> = WorkspacePresentationMode.entries

    val default: WorkspacePresentationMode = WorkspacePresentationMode.FIXED

    fun resolve(persistedName: String?): WorkspacePresentationMode =
        WorkspacePresentationMode.entries.firstOrNull { it.name == persistedName } ?: default
}
