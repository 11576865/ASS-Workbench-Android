package io.github.assworkbench.app.ui.preview

import io.github.assworkbench.domain.AssDocument
import io.github.assworkbench.domain.AssEvent

/** Read-only parameter display. Draft text and document history keep their existing owners. */
internal object GeometryParameterDisplay {
    fun externalPreview(document: AssDocument, preview: AssDocument?, owner: String?, id: Long, revision: Long, localRevision: Long?): Boolean {
        val committed = document.events.firstOrNull { it.id == id } ?: return false
        return event(document, preview, owner, id) != committed && revision != localRevision
    }
    fun event(document: AssDocument, preview: AssDocument?, owner: String?, id: Long): AssEvent? {
        val committed = document.events.firstOrNull { it.id == id } ?: return null
        if (owner != "geometry:$id") return committed
        return preview?.events?.firstOrNull { it.id == id } ?: committed
    }
}
