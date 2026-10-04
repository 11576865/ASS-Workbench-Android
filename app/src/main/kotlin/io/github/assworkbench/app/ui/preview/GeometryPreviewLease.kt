package io.github.assworkbench.app.ui.preview

/** Provenance captured by one pane publication, never inferred from equal ASS values. */
internal data class GeometryPreviewLease(
    val sessionId: Long,
    val eventId: Long,
    val parameter: String,
    val revision: Long,
) {
    fun owns(
        sessionId: Long,
        eventId: Long,
        parameter: String,
        revision: Long,
        owner: String?,
        hasPreview: Boolean,
    ): Boolean = this.sessionId == sessionId && this.eventId == eventId &&
        this.parameter == parameter && this.revision == revision &&
        owner == "geometry:$eventId" && hasPreview
}
