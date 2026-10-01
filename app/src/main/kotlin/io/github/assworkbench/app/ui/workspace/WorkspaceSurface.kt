package io.github.assworkbench.app.ui.workspace

/** Editor layout only. Coordinates are dp, never ASS / PlayRes coordinates. */
internal data class SurfaceGeometry(
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 374f,
    val height: Float = 430f,
) {
    fun isValid(): Boolean = listOf(x, y, width, height).all { it.isFinite() } &&
        width >= MIN_WIDTH && height >= MIN_HEIGHT

    /** Presentation projection: viewport changes must not overwrite saved geometry. */
    fun inViewport(viewportWidth: Float, viewportHeight: Float): SurfaceGeometry {
        val availableWidth = viewportWidth.takeIf { it.isFinite() && it > 0f } ?: 1f
        val availableHeight = viewportHeight.takeIf { it.isFinite() && it > 0f } ?: 1f
        // Projected candidates may be smaller than the canonical minimum in split-screen.
        val safe = if (listOf(x, y, width, height).all { it.isFinite() } && width > 0f && height > 0f)
            this else SurfaceGeometry()
        val w = safe.width.coerceAtMost(availableWidth)
        val h = safe.height.coerceAtMost(availableHeight)
        return safe.copy(
            x = safe.x.coerceIn(0f, (availableWidth - w).coerceAtLeast(0f)),
            y = safe.y.coerceIn(0f, (availableHeight - h).coerceAtLeast(0f)),
            width = w,
            height = h,
        )
    }

    companion object {
        const val MIN_WIDTH = 220f
        const val MIN_HEIGHT = 160f
    }
}

internal enum class SurfaceSizeClass(val label: String) {
    COMPACT("紧凑"), STANDARD("标准"), PRECISION("精确");

    fun geometryAt(x: Float, y: Float): SurfaceGeometry = when (this) {
        COMPACT -> SurfaceGeometry(x, y, 286f, 210f)
        STANDARD -> SurfaceGeometry(x, y, 374f, 430f)
        PRECISION -> SurfaceGeometry(x, y, 486f, 590f)
    }

    companion object {
        fun forGeometry(geometry: SurfaceGeometry): SurfaceSizeClass = when {
            geometry.width >= 460f && geometry.height >= 520f -> PRECISION
            geometry.width < 330f || geometry.height < 300f -> COMPACT
            else -> STANDARD
        }
    }
}

internal enum class SurfacePlacement {
    FLOATING, DOCK_LEFT, DOCK_RIGHT, DOCK_BOTTOM, MINIMIZED,
}

internal data class WorkspaceSurfaceState(
    val instanceId: String,
    val geometry: SurfaceGeometry = SurfaceGeometry(),
    val sizeClass: SurfaceSizeClass = SurfaceSizeClass.STANDARD,
    val layoutLocked: Boolean = false,
    val zOrder: Int = 1,
    val placement: SurfacePlacement = SurfacePlacement.FLOATING,
    val stackId: String? = null,
) {
    fun withGeometry(next: SurfaceGeometry): WorkspaceSurfaceState =
        if (layoutLocked || !next.isValid()) this
        else copy(geometry = next, sizeClass = SurfaceSizeClass.forGeometry(next))
}

/** Versioned private editor state; deliberately has no subtitle binding or parameter values. */
internal object WorkspaceSurfacePersistence {
    private const val VERSION = "surface-v2"
    private const val LEGACY_VERSION = "surface-v1"
    private const val SEPARATOR = '\u001f'

    fun encode(surfaces: Collection<WorkspaceSurfaceState>): List<String> =
        listOf(VERSION) + surfaces.map {
            listOf(
                it.instanceId,
                it.geometry.x,
                it.geometry.y,
                it.geometry.width,
                it.geometry.height,
                it.sizeClass.name,
                it.layoutLocked,
                it.zOrder,
                it.placement.name,
                it.stackId.orEmpty(),
            ).joinToString(SEPARATOR.toString())
        }

    fun decode(values: List<String>): List<WorkspaceSurfaceState> {
        val version = values.firstOrNull() ?: return emptyList()
        if (version != VERSION && version != LEGACY_VERSION) return emptyList()
        return values.drop(1).mapNotNull { row ->
            val parts = row.split(SEPARATOR)
            if (parts.size !in setOf(8, 10) || parts[0].isBlank()) return@mapNotNull null
            val numbers = parts.slice(1..4).map { it.toFloatOrNull() ?: return@mapNotNull null }
            val geometry = SurfaceGeometry(numbers[0], numbers[1], numbers[2], numbers[3])
            if (!geometry.isValid()) return@mapNotNull null
            val sizeClass = SurfaceSizeClass.entries.firstOrNull { it.name == parts[5] }
                ?: return@mapNotNull null
            val locked = parts[6].toBooleanStrictOrNull() ?: return@mapNotNull null
            val z = parts[7].toIntOrNull()?.takeIf { it in 1..1_000_000 }
                ?: return@mapNotNull null
            val placement = parts.getOrNull(8)?.let { name ->
                SurfacePlacement.entries.firstOrNull { it.name == name }
            } ?: SurfacePlacement.FLOATING
            val stackId = parts.getOrNull(9)?.takeIf(String::isNotBlank)
            WorkspaceSurfaceState(parts[0], geometry, sizeClass, locked, z, placement, stackId)
        }.distinctBy { it.instanceId }
    }
}
