package io.github.assworkbench.app.ui

internal enum class EdgeDockSide {
    LEFT, RIGHT, TOP, BOTTOM
}

internal data class EdgePanelState(
    val open: Boolean = false,
    val resident: Boolean = false,
    val extentFraction: Float = 0.34f,
) {
    fun toggled(): EdgePanelState = copy(open = !open)

    fun withExtent(next: Float): EdgePanelState =
        copy(extentFraction = next.coerceIn(0.18f, 0.62f))

    fun snapped(): EdgePanelState {
        val stops = listOf(0.26f, 0.38f, 0.52f)
        val nearest = stops.minBy { kotlin.math.abs(it - extentFraction) }
        return copy(extentFraction = nearest)
    }
}

internal data class EdgeBookmark(
    val instanceId: String,
    val toolKey: String,
    val side: EdgeDockSide,
    val groupName: String = "",
    val order: Int = 0,
)

internal data class EdgeWorkspaceState(
    val left: EdgePanelState = EdgePanelState(),
    val right: EdgePanelState = EdgePanelState(),
    val top: EdgePanelState = EdgePanelState(extentFraction = 0.30f),
    val bottom: EdgePanelState = EdgePanelState(extentFraction = 0.34f),
    val bookmarks: List<EdgeBookmark> = emptyList(),
    val activeBookmarkId: String? = null,
) {
    fun panel(side: EdgeDockSide): EdgePanelState = when (side) {
        EdgeDockSide.LEFT -> left
        EdgeDockSide.RIGHT -> right
        EdgeDockSide.TOP -> top
        EdgeDockSide.BOTTOM -> bottom
    }

    fun updatePanel(side: EdgeDockSide, panel: EdgePanelState): EdgeWorkspaceState = when (side) {
        EdgeDockSide.LEFT -> copy(left = panel)
        EdgeDockSide.RIGHT -> copy(right = panel)
        EdgeDockSide.TOP -> copy(top = panel)
        EdgeDockSide.BOTTOM -> copy(bottom = panel)
    }

    fun togglePanel(side: EdgeDockSide): EdgeWorkspaceState =
        updatePanel(side, panel(side).toggled())

    fun toggleResident(side: EdgeDockSide): EdgeWorkspaceState {
        val current = panel(side)
        return updatePanel(side, current.copy(resident = !current.resident, open = true))
    }

    fun resizePanel(side: EdgeDockSide, deltaFraction: Float): EdgeWorkspaceState {
        val current = panel(side)
        return updatePanel(side, current.withExtent(current.extentFraction + deltaFraction))
    }

    fun snapPanel(side: EdgeDockSide): EdgeWorkspaceState =
        updatePanel(side, panel(side).snapped())

    fun addOrActivateBookmark(
        instanceId: String,
        toolKey: String,
        side: EdgeDockSide,
    ): EdgeWorkspaceState {
        val existing = bookmarks.firstOrNull { it.instanceId == instanceId }
        if (existing != null) return copy(activeBookmarkId = instanceId)
        val nextOrder = (bookmarks.filter { it.side == side }.maxOfOrNull { it.order } ?: -1) + 1
        return copy(
            bookmarks = bookmarks + EdgeBookmark(
                instanceId = instanceId,
                toolKey = toolKey,
                side = side,
                order = nextOrder,
            ),
            activeBookmarkId = instanceId,
        )
    }

    fun moveBookmark(instanceId: String, side: EdgeDockSide, orderDelta: Int = 0): EdgeWorkspaceState {
        val current = bookmarks.firstOrNull { it.instanceId == instanceId } ?: return this
        val sameSide = bookmarks.filter { it.side == current.side }.sortedBy { it.order }
        val currentIndex = sameSide.indexOfFirst { it.instanceId == instanceId }
        val targetIndex = (currentIndex + orderDelta).coerceIn(0, (sameSide.size - 1).coerceAtLeast(0))
        val moved = bookmarks.map {
            if (it.instanceId == instanceId) it.copy(side = side) else it
        }
        val normalized = EdgeDockSide.entries.fold(moved) { acc, edge ->
            acc.filter { it.side == edge }
                .sortedWith(compareBy<EdgeBookmark> {
                    if (edge == current.side && it.instanceId == instanceId) targetIndex else it.order
                }.thenBy { it.instanceId })
                .mapIndexed { index, item -> item.copy(order = index) }
                .let { normalizedSide ->
                    acc.filterNot { item -> item.side == edge } + normalizedSide
                }
        }
        return copy(bookmarks = normalized, activeBookmarkId = instanceId)
    }

    fun groupBookmark(instanceId: String, groupName: String): EdgeWorkspaceState =
        copy(bookmarks = bookmarks.map {
            if (it.instanceId == instanceId) it.copy(groupName = groupName) else it
        })

    fun removeBookmark(instanceId: String): EdgeWorkspaceState =
        copy(
            bookmarks = bookmarks.filterNot { it.instanceId == instanceId },
            activeBookmarkId = activeBookmarkId.takeUnless { it == instanceId },
        )
}
