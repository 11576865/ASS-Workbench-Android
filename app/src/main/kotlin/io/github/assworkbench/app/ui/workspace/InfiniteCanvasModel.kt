package io.github.assworkbench.app.ui.workspace

/** Presentation coordinates are world dp; they never carry ASS parameter values. */
internal data class InfiniteCanvasCamera(val x: Float = 0f, val y: Float = 0f, val scale: Float = 1f) {
    fun pan(dx: Float, dy: Float): InfiniteCanvasCamera =
        if (dx.isFinite() && dy.isFinite() && (x + dx).isFinite() && (y + dy).isFinite())
            copy(x = x + dx, y = y + dy) else this
    fun zoomAt(anchorX: Float, anchorY: Float, nextScale: Float): InfiniteCanvasCamera {
        if (!anchorX.isFinite() || !anchorY.isFinite() || !nextScale.isFinite() || scale <= 0f) return this
        val z = nextScale.coerceIn(0.25f, 2f)
        val nx = anchorX - (anchorX - x) * z / scale
        val ny = anchorY - (anchorY - y) * z / scale
        return if (nx.isFinite() && ny.isFinite()) InfiniteCanvasCamera(nx, ny, z) else this
    }
}

internal data class InfiniteCanvasNode(
    val id: String,
    val x: Float = 0f, val y: Float = 0f,
    val width: Float = 400f, val height: Float = 320f,
    val z: Int = 1, val alpha: Float = 1f,
    val passthrough: Boolean = false, val hidden: Boolean = false,
) {
    fun move(dx: Float, dy: Float): InfiniteCanvasNode =
        if (dx.isFinite() && dy.isFinite() && (x + dx).isFinite() && (y + dy).isFinite())
            copy(x = x + dx, y = y + dy) else this
    fun resize(dw: Float, dh: Float): InfiniteCanvasNode =
        if (dw.isFinite() && dh.isFinite() && (width + dw).isFinite() && (height + dh).isFinite())
            copy(width = (width + dw).coerceIn(220f, 2400f), height = (height + dh).coerceIn(160f, 1800f)) else this
}

internal fun showCanvasContent(scale: Float, id: String, detailedId: String?): Boolean =
    scale >= 0.55f || id == detailedId

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
        listOf("infinite-v1", listOf(camera.x, camera.y, camera.scale).joinToString(SEP.toString())) +
            nodes.map { listOf(it.id, it.x, it.y, it.width, it.height, it.z, it.alpha, it.passthrough, it.hidden).joinToString(SEP.toString()) }
    fun decode(rows: List<String>): Pair<InfiniteCanvasCamera, List<InfiniteCanvasNode>> {
        if (rows.firstOrNull() != "infinite-v1" || rows.size < 2) return InfiniteCanvasCamera() to emptyList()
        val c = rows[1].split(SEP).mapNotNull { it.toFloatOrNull()?.takeIf(Float::isFinite) }
        if (c.size != 3 || c[2] !in 0.25f..2f) return InfiniteCanvasCamera() to emptyList()
        val nodes = rows.drop(2).mapNotNull { row ->
            val p = row.split(SEP)
            if (p.size != 9 || p[0].isBlank()) return@mapNotNull null
            val f = p.slice(1..4).map { it.toFloatOrNull()?.takeIf(Float::isFinite) ?: return@mapNotNull null }
            if (f[2] !in 220f..2400f || f[3] !in 160f..1800f) return@mapNotNull null
            val z = p[5].toIntOrNull()?.takeIf { it in 1..1_000_000 } ?: return@mapNotNull null
            val alpha = p[6].toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..1f } ?: return@mapNotNull null
            val pass = p[7].toBooleanStrictOrNull() ?: return@mapNotNull null
            val hidden = p[8].toBooleanStrictOrNull() ?: return@mapNotNull null
            InfiniteCanvasNode(p[0], f[0], f[1], f[2], f[3], z, alpha, pass, hidden)
        }.distinctBy { it.id }
        return InfiniteCanvasCamera(c[0], c[1], c[2]) to nodes
    }
}
