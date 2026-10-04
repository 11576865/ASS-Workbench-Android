package io.github.assworkbench.app.ui.workspace

/**
 * Semantic parameter identity is independent from its visual control.
 *
 * A descriptor names what is being edited; a presentation names only how the
 * same value is projected. Intents carry preview/commit/cancel phases without
 * granting a canvas node direct access to EditorViewModel mutation APIs.
 */
internal enum class WorkspaceParameterShape(val arity: Int) {
    SCALAR(1),
    VECTOR2(2),
}

internal enum class WorkspaceParameterPresentation {
    NUMBER,
    NUMBER_PAIR,
    SLIDER,
    SLIDER_PAIR,
    ANGLE_DIAL,
    XY_PAD,
}

internal enum class WorkspaceParameterContext {
    EVENT,
    STYLE,
    DOCUMENT,
}

internal data class WorkspaceParameterDescriptor(
    val key: String,
    val title: String,
    val sourceToolKey: String,
    val context: WorkspaceParameterContext,
    val shape: WorkspaceParameterShape,
    val presentations: Set<WorkspaceParameterPresentation>,
    val unit: String? = null,
) {
    init {
        require(key.isNotBlank()) { "Parameter descriptor key must not be blank." }
        require(title.isNotBlank()) { "Parameter descriptor title must not be blank." }
        require(sourceToolKey.isNotBlank()) { "Parameter descriptor source tool must not be blank." }
        require(presentations.isNotEmpty()) { "Parameter descriptor needs at least one presentation." }
        require(presentations.all { it.accepts(shape) }) {
            "Parameter presentation is incompatible with descriptor shape: $key"
        }
    }

    fun supports(presentation: WorkspaceParameterPresentation): Boolean =
        presentation in presentations
}

internal data class WorkspaceParameterAddress(
    val projectionId: String,
    val descriptorKey: String,
    val binding: WorkspaceBinding,
) {
    init {
        require(projectionId.isNotBlank()) { "Parameter projection id must not be blank." }
        require(descriptorKey.isNotBlank()) { "Parameter descriptor key must not be blank." }
    }
}

internal data class WorkspaceParameterProjection(
    val id: String,
    val descriptorKey: String,
    val binding: WorkspaceBinding,
    val presentation: WorkspaceParameterPresentation,
) {
    init {
        require(id.isNotBlank()) { "Parameter projection id must not be blank." }
        val descriptor = requireNotNull(WorkspaceParameterCatalog.find(descriptorKey)) {
            "Unknown workspace parameter: $descriptorKey"
        }
        require(descriptor.supports(presentation)) {
            "Presentation $presentation is not supported by $descriptorKey"
        }
    }

    val address: WorkspaceParameterAddress
        get() = WorkspaceParameterAddress(id, descriptorKey, binding)
}

internal enum class WorkspaceParameterIntentPhase {
    PREVIEW,
    COMMIT,
    CANCEL,
}

internal data class WorkspaceParameterIntent(
    val address: WorkspaceParameterAddress,
    val phase: WorkspaceParameterIntentPhase,
    val values: List<Double> = emptyList(),
    val revision: Long,
)

internal object WorkspaceParameterCatalog {
    val positionXY = WorkspaceParameterDescriptor(
        key = "event.position.xy",
        title = "位置 X/Y",
        sourceToolKey = "POSITION",
        context = WorkspaceParameterContext.EVENT,
        shape = WorkspaceParameterShape.VECTOR2,
        presentations = setOf(
            WorkspaceParameterPresentation.NUMBER_PAIR,
            WorkspaceParameterPresentation.XY_PAD,
        ),
        unit = "script-px",
    )

    val rotationZ = WorkspaceParameterDescriptor(
        key = "event.rotation.z",
        title = "Z 旋转",
        sourceToolKey = "POSITION",
        context = WorkspaceParameterContext.EVENT,
        shape = WorkspaceParameterShape.SCALAR,
        presentations = setOf(
            WorkspaceParameterPresentation.NUMBER,
            WorkspaceParameterPresentation.SLIDER,
            WorkspaceParameterPresentation.ANGLE_DIAL,
        ),
        unit = "deg",
    )

    val scaleXY = WorkspaceParameterDescriptor(
        key = "event.scale.xy",
        title = "缩放 X/Y",
        sourceToolKey = "POSITION",
        context = WorkspaceParameterContext.EVENT,
        shape = WorkspaceParameterShape.VECTOR2,
        presentations = setOf(
            WorkspaceParameterPresentation.NUMBER_PAIR,
            WorkspaceParameterPresentation.SLIDER_PAIR,
            WorkspaceParameterPresentation.XY_PAD,
        ),
        unit = "%",
    )

    val shearXY = WorkspaceParameterDescriptor(
        key = "event.shear.xy",
        title = "斜切 X/Y",
        sourceToolKey = "POSITION",
        context = WorkspaceParameterContext.EVENT,
        shape = WorkspaceParameterShape.VECTOR2,
        presentations = setOf(
            WorkspaceParameterPresentation.NUMBER_PAIR,
            WorkspaceParameterPresentation.SLIDER_PAIR,
            WorkspaceParameterPresentation.XY_PAD,
        ),
    )

    val descriptors: List<WorkspaceParameterDescriptor> =
        listOf(positionXY, rotationZ, scaleXY, shearXY)

    private val byKey = descriptors.associateBy { it.key }

    fun find(key: String): WorkspaceParameterDescriptor? = byKey[key]
}

internal object WorkspaceParameterIntentContract {
    fun requireValid(intent: WorkspaceParameterIntent): WorkspaceParameterDescriptor {
        require(intent.revision >= 0L) { "Parameter intent revision must be non-negative." }
        val descriptor = requireNotNull(WorkspaceParameterCatalog.find(intent.address.descriptorKey)) {
            "Unknown workspace parameter: ${intent.address.descriptorKey}"
        }
        when (descriptor.context) {
            WorkspaceParameterContext.EVENT -> require(
                intent.address.binding == WorkspaceBinding.FollowFocus ||
                    intent.address.binding is WorkspaceBinding.PinnedEvent
            ) {
                "Event parameter projections require FollowFocus or PinnedEvent binding."
            }
            WorkspaceParameterContext.STYLE,
            WorkspaceParameterContext.DOCUMENT -> Unit
        }

        if (intent.phase == WorkspaceParameterIntentPhase.CANCEL) {
            require(intent.values.isEmpty()) { "Cancel intent must not carry parameter values." }
        } else {
            require(intent.values.size == descriptor.shape.arity) {
                "Parameter ${descriptor.key} expects ${descriptor.shape.arity} values."
            }
            require(intent.values.all(Double::isFinite)) {
                "Parameter intent values must be finite."
            }
        }
        return descriptor
    }
}

private fun WorkspaceParameterPresentation.accepts(shape: WorkspaceParameterShape): Boolean =
    when (this) {
        WorkspaceParameterPresentation.NUMBER,
        WorkspaceParameterPresentation.SLIDER,
        WorkspaceParameterPresentation.ANGLE_DIAL -> shape == WorkspaceParameterShape.SCALAR

        WorkspaceParameterPresentation.NUMBER_PAIR,
        WorkspaceParameterPresentation.SLIDER_PAIR,
        WorkspaceParameterPresentation.XY_PAD -> shape == WorkspaceParameterShape.VECTOR2
    }
