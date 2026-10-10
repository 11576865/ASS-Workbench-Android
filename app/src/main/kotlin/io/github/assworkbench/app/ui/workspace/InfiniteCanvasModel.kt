package io.github.assworkbench.app.ui.workspace

/** Presentation coordinates are world dp; they never carry ASS parameter values. */
internal data class InfiniteCanvasCamera(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f) {
    fun pan(dx: Float, dy: Float): InfiniteCanvasCamera =
        if (dx.isFinite() && dy.isFinite() && (x + dx).isFinite() && (y + dy).isFinite())
            copy(x = x + dx, y = y + dy) else this
    fun zoomAt(anchorX: Float, anchorY: Float, nextScale: Float): InfiniteCanvasCamera {
        if (!anchorX.isFinite() || !anchorY.isFinite() || !nextScale.isFinite() ||
            !scale.isFinite() || scale <= 0f) return this
        val z = nextScale.coerceIn(0.025f, 2f)
        // Float subtraction can overflow for two finite opposite-end world offsets.
        // Compute the affine anchor projection in Double, then check Float storage.
        val ratio = z.toDouble() / scale.toDouble()
        val nx = anchorX.toDouble() - (anchorX.toDouble() - x.toDouble()) * ratio
        val ny = anchorY.toDouble() - (anchorY.toDouble() - y.toDouble()) * ratio
        val finiteRange = -Float.MAX_VALUE.toDouble()..Float.MAX_VALUE.toDouble()
        return if (nx.isFinite() && ny.isFinite() &&
            nx in finiteRange && ny in finiteRange) {
            InfiniteCanvasCamera(nx.toFloat(), ny.toFloat(), z)
        } else this
    }
}

/**
 * Browser-style history for intentional viewport jumps.
 *
 * Camera history is presentation-only: it never participates in document Undo
 * or writes tool-node geometry. Continuous pan/pinch updates are NOT recorded;
 * the next explicit jump captures the camera reached by those gestures.
 */
internal data class CanvasViewportHistory(
    val past: List<InfiniteCanvasCamera> = emptyList(),
    val future: List<InfiniteCanvasCamera> = emptyList(),
) {
    val canBack: Boolean get() = past.isNotEmpty()
    val canForward: Boolean get() = future.isNotEmpty()

    fun visit(current: InfiniteCanvasCamera, next: InfiniteCanvasCamera): CanvasViewportHistory {
        if (!current.isValidViewport() || !next.isValidViewport() || current == next) return this
        return copy(past = (past + current).takeLast(LIMIT), future = emptyList())
    }

    fun goBack(current: InfiniteCanvasCamera): Pair<CanvasViewportHistory, InfiniteCanvasCamera>? {
        if (!current.isValidViewport() || past.isEmpty()) return null
        return copy(past = past.dropLast(1),
            future = (future + current).takeLast(LIMIT)) to past.last()
    }

    fun goForward(current: InfiniteCanvasCamera): Pair<CanvasViewportHistory, InfiniteCanvasCamera>? {
        if (!current.isValidViewport() || future.isEmpty()) return null
        return copy(past = (past + current).takeLast(LIMIT),
            future = future.dropLast(1)) to future.last()
    }

    companion object { const val LIMIT = 16 }
}

private fun InfiniteCanvasCamera.isValidViewport(): Boolean =
    x.isFinite() && y.isFinite() && scale.isFinite() && scale in 0.025f..2f

/** Safe, bounded Activity-state restoration; never decode malformed camera frames. */
internal object CanvasViewportHistoryPersistence {
    private fun serialize(c: InfiniteCanvasCamera): String =
        listOf(c.x, c.y, c.scale).joinToString(",")

    private fun parse(raw: String): InfiniteCanvasCamera? {
        val values = raw.split(",").map { it.toFloatOrNull() }
        if (values.size != 3 || values.any { it == null }) return null
        val c = InfiniteCanvasCamera(values[0]!!, values[1]!!, values[2]!!)
        return c.takeIf { it.isValidViewport() }
    }

    fun encode(value: CanvasViewportHistory): List<String> =
        listOf(value.past.size.toString()) +
            value.past.takeLast(CanvasViewportHistory.LIMIT).map(::serialize) +
            value.future.takeLast(CanvasViewportHistory.LIMIT).map(::serialize)

    fun decode(raw: List<String>): CanvasViewportHistory {
        val boundary = raw.firstOrNull()?.toIntOrNull() ?: return CanvasViewportHistory()
        if (boundary !in 0..CanvasViewportHistory.LIMIT ||
            raw.size - boundary - 1 !in 0..CanvasViewportHistory.LIMIT) return CanvasViewportHistory()
        val past = raw.drop(1).take(boundary).mapNotNull(::parse)
        val future = raw.drop(boundary + 1).mapNotNull(::parse)
        if (past.size != boundary || future.size != raw.size - boundary - 1) return CanvasViewportHistory()
        return CanvasViewportHistory(past, future)
    }
}

/**
 * Fit a set of world-space nodes into the visible board, retaining headroom for
 * its top command bar and bottom tool strip. Double intermediate math prevents
 * opposite finite Float coordinates from overflowing a bounding-box subtraction.
 * The world is not clipped by the viewport; malformed geometry is ignored.
 */
internal fun fitCanvasCamera(
    nodes: List<InfiniteCanvasNode>,
    viewportWidth: Float,
    viewportHeight: Float,
): InfiniteCanvasCamera? {
    if (!viewportWidth.isFinite() || !viewportHeight.isFinite() ||
        viewportWidth <= 64f || viewportHeight <= 176f) return null
    val eligible = nodes.filter {
        it.x.isFinite() && it.y.isFinite() &&
            it.width.isFinite() && it.height.isFinite() &&
            it.width > 0f && it.height > 0f
    }
    if (eligible.isEmpty()) return null
    val left = eligible.minOf { it.x.toDouble() }
    val top = eligible.minOf { it.y.toDouble() }
    val right = eligible.maxOf { it.x.toDouble() + it.width.toDouble() }
    val bottom = eligible.maxOf { it.y.toDouble() + it.height.toDouble() }
    val extentW = right - left
    val extentH = bottom - top
    if (!extentW.isFinite() || !extentH.isFinite() || extentW <= 0.0 || extentH <= 0.0) return null
    val availableW = (viewportWidth - 32f).toDouble()
    val availableH = (viewportHeight - 160f).toDouble()
    val requiredScale = minOf(1.0, availableW / extentW, availableH / extentH)
    // A projection beyond the camera's numerical operating scale is not a real fit.
    // Callers can open the normalized birdseye map instead of reporting false success.
    if (!requiredScale.isFinite() || requiredScale < 0.025) return null
    val scale = requiredScale.coerceAtMost(2.0)
    // Center the fitted world inside its usable viewport, not against the
    // top-left margin. Preserve 16dp horizontal and 72/88dp vertical chrome.
    val screenX = 16.0 + (availableW - extentW * scale) / 2.0 - left * scale
    val screenY = 72.0 + (availableH - extentH * scale) / 2.0 - top * scale
    if (!screenX.isFinite() || !screenY.isFinite() ||
        screenX !in -Float.MAX_VALUE.toDouble()..Float.MAX_VALUE.toDouble() ||
        screenY !in -Float.MAX_VALUE.toDouble()..Float.MAX_VALUE.toDouble()) return null
    return InfiniteCanvasCamera(screenX.toFloat(), screenY.toFloat(), scale.toFloat())
}

/** Approach updates observation, never modifies a node's saved world geometry. */
internal fun canvasCameraForNode(
    node: InfiniteCanvasNode, viewportWidth: Float, viewportHeight: Float,
): InfiniteCanvasCamera =
    fitCanvasCamera(listOf(node), viewportWidth, viewportHeight) ?: InfiniteCanvasCamera()


/**
 * Visibility for expensive production editor content (not for its saved node).
 * Screen-viewport clipping alone does not suspend composition or native media
 * resources. Keep a modest screen-dp prefetch band to avoid rapid churn on pans.
 *
 * Double arithmetic is intentional: finite opposite-end Float world positions
 * must not overflow before the intersection check.
 */
internal fun shouldComposeCanvasTool(
    camera: InfiniteCanvasCamera,
    node: InfiniteCanvasNode,
    viewportWidth: Float,
    viewportHeight: Float,
    prefetchDp: Float = 128f,
): Boolean {
    if (!camera.x.isFinite() || !camera.y.isFinite() || !camera.scale.isFinite() ||
        camera.scale <= 0f || !node.x.isFinite() || !node.y.isFinite() ||
        !node.width.isFinite() || !node.height.isFinite() ||
        node.width <= 0f || node.height <= 0f ||
        !viewportWidth.isFinite() || !viewportHeight.isFinite() ||
        viewportWidth <= 0f || viewportHeight <= 0f ||
        !prefetchDp.isFinite() || prefetchDp < 0f) return false

    val scale = camera.scale.toDouble()
    val left = camera.x.toDouble() + node.x.toDouble() * scale
    val top = camera.y.toDouble() + node.y.toDouble() * scale
    val right = camera.x.toDouble() + (node.x.toDouble() + node.width.toDouble()) * scale
    val bottom = camera.y.toDouble() + (node.y.toDouble() + node.height.toDouble()) * scale
    val padding = prefetchDp.toDouble()
    return right >= -padding && bottom >= -padding &&
        left <= viewportWidth.toDouble() + padding &&
        top <= viewportHeight.toDouble() + padding
}

internal data class InfiniteCanvasNode(
    val id: String,
    val x: Float = 0f, val y: Float = 0f,
    val width: Float = 400f, val height: Float = 320f,
    val z: Int = 1, val alpha: Float = 1f,
    val passthrough: Boolean = false, val hidden: Boolean = false,
    val layoutLocked: Boolean = false,
) {
    fun move(dx: Float, dy: Float): InfiniteCanvasNode =
        if (!layoutLocked && dx.isFinite() && dy.isFinite() && (x + dx).isFinite() && (y + dy).isFinite())
            copy(x = x + dx, y = y + dy) else this
    fun resize(dw: Float, dh: Float): InfiniteCanvasNode =
        if (!layoutLocked && dw.isFinite() && dh.isFinite() && (width + dw).isFinite() && (height + dh).isFinite())
            copy(width = (width + dw).coerceIn(220f, 2400f), height = (height + dh).coerceIn(160f, 1800f)) else this
}

internal fun showCanvasContent(scale: Float, id: String, detailedId: String?): Boolean =
    scale >= 0.55f || id == detailedId

/** Approach/expand must leave space for signal below fixed-size touch controls. */
internal fun expandAudioCanvasForFocus(node: InfiniteCanvasNode, scale: Float, viewportHeight: Float): InfiniteCanvasNode {
    if (node.id != "audio" || !scale.isFinite() || scale <= 0f || !viewportHeight.isFinite()) return node
    val screenHeight = minOf(240f, (viewportHeight - 96f).coerceAtLeast(0f))
    return node.copy(height = maxOf(node.height, screenHeight / scale).coerceAtMost(1800f))
}


/**
 * Arrange only visible, unlocked nodes; never rewrite pinned geometry, hidden
 * cards, domain values or z-order. Start below locked cards so a convenience
 * layout cannot cover deliberately anchored windows.
 */
internal fun arrangeCanvasNodes(nodes: List<InfiniteCanvasNode>, columns: Int, gap: Float = 40f): List<InfiniteCanvasNode> {
    if (columns !in 1..6 || !gap.isFinite() || gap < 0f) return nodes
    val movable = nodes.filter { !it.hidden && !it.layoutLocked }
    if (movable.isEmpty()) return nodes
    val locked = nodes.filter { !it.hidden && it.layoutLocked }
    val originY = maxOf(0f, locked.maxOfOrNull { it.y + it.height } ?: 0f) +
        if (locked.isNotEmpty()) gap else 0f
    if (!originY.isFinite()) return nodes
    val widths = FloatArray(columns)
    movable.forEachIndexed { i, node ->
        widths[i % columns] = maxOf(widths[i % columns], node.width)
    }
    val starts = FloatArray(columns)
    for (i in 1 until columns) starts[i] = starts[i - 1] + widths[i - 1] + gap
    if (starts.any { !it.isFinite() }) return nodes
    val relocated = mutableMapOf<String, InfiniteCanvasNode>()
    var y = originY
    movable.chunked(columns).forEach { row ->
        row.forEachIndexed { col, node ->
            relocated[node.id] = node.copy(x = starts[col], y = y)
        }
        y += (row.maxOfOrNull { it.height } ?: 0f) + gap
        if (!y.isFinite()) return nodes
    }
    return nodes.map { relocated[it.id] ?: it }
}

/** Opacity cycles are deterministic and layout-only; never touch ASS history. */
internal fun nextCanvasOpacity(alpha: Float): Float = when {
    !alpha.isFinite() || alpha >= 0.95f -> 0.75f
    alpha >= 0.65f -> 0.50f
    alpha >= 0.40f -> 0.25f
    else -> 1f
}

internal fun raiseCanvasNode(nodes: List<InfiniteCanvasNode>, id: String): List<InfiniteCanvasNode> {
    val normalized = if ((nodes.maxOfOrNull { it.z } ?: 0) >= 999_999)
        nodes.sortedBy { it.z }.mapIndexed { i, n -> n.copy(z = i + 1) } else nodes
    val next = (normalized.maxOfOrNull { it.z } ?: 0) + 1
    return normalized.map { if (it.id == id) it.copy(z = next) else it }
}

/** Saveable UI state only; old project snapshots need no document migration. */
internal object InfiniteCanvasPersistence {
    private const val SEP = '\u001f'
    fun encode(camera: InfiniteCanvasCamera, nodes: List<InfiniteCanvasNode>): List<String> =
        listOf("infinite-v2", listOf(camera.x, camera.y, camera.scale).joinToString(SEP.toString())) +
            nodes.map { listOf(it.id, it.x, it.y, it.width, it.height, it.z, it.alpha, it.passthrough, it.hidden, it.layoutLocked).joinToString(SEP.toString()) }
    fun decode(rows: List<String>): Pair<InfiniteCanvasCamera, List<InfiniteCanvasNode>> {
        val version = rows.firstOrNull()
        if (version !in listOf("infinite-v1", "infinite-v2") || rows.size < 2)
            return InfiniteCanvasCamera() to emptyList()
        val c = rows[1].split(SEP).mapNotNull { it.toFloatOrNull()?.takeIf(Float::isFinite) }
        if (c.size != 3 || c[2] !in 0.025f..2f) return InfiniteCanvasCamera() to emptyList()
        val nodes = rows.drop(2).mapNotNull { row ->
            val p = row.split(SEP)
            if (p.size != (if (version == "infinite-v2") 10 else 9) || p[0].isBlank()) return@mapNotNull null
            val f = p.slice(1..4).map { it.toFloatOrNull()?.takeIf(Float::isFinite) ?: return@mapNotNull null }
            if (f[2] !in 220f..2400f || f[3] !in 160f..1800f) return@mapNotNull null
            val z = p[5].toIntOrNull()?.takeIf { it in 1..1_000_000 } ?: return@mapNotNull null
            val alpha = p[6].toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..1f } ?: return@mapNotNull null
            val pass = p[7].toBooleanStrictOrNull() ?: return@mapNotNull null
            val hidden = p[8].toBooleanStrictOrNull() ?: return@mapNotNull null
            val locked = if (version == "infinite-v2")
                p[9].toBooleanStrictOrNull() ?: return@mapNotNull null else false
            InfiniteCanvasNode(p[0], f[0], f[1], f[2], f[3], z, alpha, pass, hidden, locked)
        }.distinctBy { it.id }
        return InfiniteCanvasCamera(c[0], c[1], c[2]) to nodes
    }
}
