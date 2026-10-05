package io.github.assworkbench.app.ui.workspace

import io.github.assworkbench.app.EditorViewModel

/**
 * Single mutation boundary for extracted workspace parameters.
 *
 * The router validates semantic intent first, then delegates to the existing
 * canonical EditorViewModel preview/commit API. Canvas controls never mutate
 * ASS documents directly.
 */
internal object WorkspaceParameterIntentRouter {
    fun dispatch(
        intent: WorkspaceParameterIntent,
        resolvedEventId: Long,
        viewModel: EditorViewModel,
    ) {
        val descriptor = WorkspaceParameterIntentContract.requireValid(intent)
        require(resolvedEventId > 0L) { "Resolved Event id must be positive." }

        when (descriptor.key) {
            WorkspaceParameterCatalog.rotationZ.key -> dispatchRotationZ(
                intent = intent,
                eventId = resolvedEventId,
                viewModel = viewModel,
            )

            else -> error("No live parameter router for ${descriptor.key}")
        }
    }

    private fun dispatchRotationZ(
        intent: WorkspaceParameterIntent,
        eventId: Long,
        viewModel: EditorViewModel,
    ) {
        val ownerId = "geometry:$eventId:${intent.address.projectionId}"
        when (intent.phase) {
            WorkspaceParameterIntentPhase.PREVIEW ->
                viewModel.previewEventRotationZ(
                    id = eventId,
                    angle = intent.values.single(),
                    ownerId = ownerId,
                )

            WorkspaceParameterIntentPhase.COMMIT ->
                viewModel.setEventRotationZ(eventId, intent.values.single())

            WorkspaceParameterIntentPhase.CANCEL ->
                viewModel.clearTransientPreview(ownerId)
        }
    }
}
