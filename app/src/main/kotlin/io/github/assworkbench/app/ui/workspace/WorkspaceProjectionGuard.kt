package io.github.assworkbench.app.ui.workspace

/** A gesture/disposal callback retains the authority of its original pane. */
internal data class WorkspaceProjectionGuard(
    val sessionId: Long, val eventId: Long, val ownerId: String,
) {
    fun mayWrite(currentSessionId: Long, resolvedEventId: Long?, previewOwnerId: String?): Boolean =
        currentSessionId == sessionId && resolvedEventId == eventId &&
            (previewOwnerId == null || previewOwnerId == ownerId)

    fun mayFinish(currentSessionId: Long, resolvedEventId: Long?, previewOwnerId: String?,
        currentRevision: Long, ownedRevision: Long?): Boolean =
        ownedRevision != null && currentRevision == ownedRevision &&
            mayWrite(currentSessionId, resolvedEventId, previewOwnerId)

    // Changing focus may leave an old owned preview to clean up. Changing the
    // project must never authorize cleanup of an identically named new owner.
    fun mayClear(currentSessionId: Long): Boolean = currentSessionId == sessionId
}
