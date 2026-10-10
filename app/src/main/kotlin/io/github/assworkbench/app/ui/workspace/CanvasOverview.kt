package io.github.assworkbench.app.ui.workspace

internal data class CanvasOverviewNode(
    val id: String, val left: Float, val top: Float, val right: Float, val bottom: Float,
    val z: Int, val hidden: Boolean,
)

/** Normalize in Double: opposite finite Float coordinates can overflow a Float span. */
internal fun canvasOverview(nodes: List<InfiniteCanvasNode>): List<CanvasOverviewNode> {
    val valid = nodes.filter {
        it.x.isFinite() && it.y.isFinite() && it.width.isFinite() && it.height.isFinite() &&
            it.width > 0f && it.height > 0f
    }
    if (valid.isEmpty()) return emptyList()
    val left = valid.minOf { it.x.toDouble() }
    val top = valid.minOf { it.y.toDouble() }
    val width = valid.maxOf { it.x.toDouble() + it.width } - left
    val height = valid.maxOf { it.y.toDouble() + it.height } - top
    val span = maxOf(width, height).coerceAtLeast(1.0)
    val offsetX = (1.0 - width / span) / 2.0
    val offsetY = (1.0 - height / span) / 2.0
    return valid.map {
        CanvasOverviewNode(it.id,
            (offsetX + (it.x.toDouble() - left) / span).toFloat(),
            (offsetY + (it.y.toDouble() - top) / span).toFloat(),
            (offsetX + (it.x.toDouble() + it.width - left) / span).toFloat(),
            (offsetY + (it.y.toDouble() + it.height - top) / span).toFloat(),
            it.z, it.hidden)
    }
}

internal fun hitCanvasOverview(
    nodes: List<CanvasOverviewNode>, x: Float, y: Float, tolerance: Float = 0.03f,
): String? {
    if (!x.isFinite() || !y.isFinite() || !tolerance.isFinite() || tolerance < 0f) return null
    fun distance(node: CanvasOverviewNode): Float {
        val dx = x - x.coerceIn(node.left, node.right)
        val dy = y - y.coerceIn(node.top, node.bottom)
        return dx * dx + dy * dy
    }
    return nodes.filter { distance(it) <= tolerance * tolerance }
        .minWithOrNull(compareBy<CanvasOverviewNode> { distance(it) }.thenByDescending { it.z })?.id
}


/**
 * Find tools without requiring a precise tap on a tiny map marker. Search is
 * strictly presentational: it cannot alter world geometry, hidden state,
 * ToolInstance identity, or source ordering.
 */
internal fun filterCanvasOverviewEntries(
    entries: List<InfiniteCanvasEntry>,
    nodes: List<InfiniteCanvasNode>,
    query: String,
    hiddenOnly: Boolean = false,
): List<Pair<InfiniteCanvasEntry, InfiniteCanvasNode>> {
    val byId = nodes.associateBy { it.id }
    val term = query.trim()
    return entries.mapNotNull { entry ->
        byId[entry.id]?.let { entry to it }
    }.filter { (entry, node) ->
        (!hiddenOnly || node.hidden) &&
            (term.isEmpty() || listOf(entry.title, entry.subtitle, entry.id)
                .any { it.contains(term, ignoreCase = true) })
    }
}
