package io.github.assworkbench.app.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** Layout owner. Gesture candidates are separate from committed/saveable geometry. */
@Stable
internal class WorkbenchSurfaceController(restored: List<WorkspaceSurfaceState> = emptyList()) {
    private val surfaces = mutableStateMapOf<String, WorkspaceSurfaceState>().apply {
        restored.forEach { put(it.instanceId, it) }
    }
    private val resizeGestures = mutableSetOf<String>()
    private val candidates = mutableMapOf<String, MutableState<SurfaceGeometry?>>()

    fun state(id: String, fallback: SurfaceGeometry): WorkspaceSurfaceState =
        surfaces[id] ?: WorkspaceSurfaceState(id, fallback)

    fun candidate(id: String): MutableState<SurfaceGeometry?> =
        candidates.getOrPut(id) { mutableStateOf(null) }

    fun begin(id: String, fallback: SurfaceGeometry, visible: SurfaceGeometry, resize: Boolean = false) {
        if (!state(id, fallback).layoutLocked) {
            if (resize) resizeGestures.add(id) else resizeGestures.remove(id)
            candidate(id).value = visible
        }
    }

    fun moveBy(id: String, dx: Float, dy: Float, viewportWidth: Float, viewportHeight: Float) {
        val value = candidate(id).value ?: return
        candidate(id).value = value.copy(x = value.x + dx, y = value.y + dy)
            .inViewport(viewportWidth, viewportHeight)
    }

    fun resizeBy(id: String, dw: Float, dh: Float, viewportWidth: Float, viewportHeight: Float) {
        val value = candidate(id).value ?: return
        candidate(id).value = value.copy(
            width = (value.width + dw).coerceAtLeast(SurfaceGeometry.MIN_WIDTH),
            height = (value.height + dh).coerceAtLeast(SurfaceGeometry.MIN_HEIGHT),
        ).inViewport(viewportWidth, viewportHeight)
    }

    fun commit(id: String, fallback: SurfaceGeometry) {
        candidate(id).value?.let { next ->
            // Tiny viewports may project below the canonical minimum.
            val original = state(id, fallback).geometry
            val valid = if (id in resizeGestures) next.copy(
                width = next.width.coerceAtLeast(SurfaceGeometry.MIN_WIDTH),
                height = next.height.coerceAtLeast(SurfaceGeometry.MIN_HEIGHT),
            ) else next.copy(width = original.width, height = original.height)
            surfaces[id] = state(id, fallback).withGeometry(valid)
        }
        cancel(id)
    }

    fun cancel(id: String) {
        candidate(id).value = null
        resizeGestures.remove(id)
    }

    fun toggleLayoutLock(id: String, fallback: SurfaceGeometry) {
        cancel(id)
        val old = state(id, fallback)
        surfaces[id] = old.copy(layoutLocked = !old.layoutLocked)
    }

    fun cycleSize(id: String, fallback: SurfaceGeometry) {
        val old = state(id, fallback)
        if (old.layoutLocked || old.presentation != SurfacePresentation.FLOATING) return
        val next = SurfaceSizeClass.entries[(old.sizeClass.ordinal + 1) % SurfaceSizeClass.entries.size]
        cancel(id)
        surfaces[id] = old.copy(geometry = next.geometryAt(old.geometry.x, old.geometry.y), sizeClass = next)
        bringToFront(id)
    }

    fun toggleMinimize(id: String, fallback: SurfaceGeometry) {
        cancel(id)
        val old = state(id, fallback)
        val next = if (old.presentation == SurfacePresentation.MINIMIZED) {
            SurfacePresentation.FLOATING
        } else {
            SurfacePresentation.MINIMIZED
        }
        surfaces[id] = old.copy(presentation = next)
        bringToFront(id)
    }

    fun dock(id: String, side: SurfacePresentation, fallback: SurfaceGeometry) {
        require(side == SurfacePresentation.DOCK_LEFT || side == SurfacePresentation.DOCK_RIGHT)
        cancel(id)
        val old = state(id, fallback)
        surfaces[id] = old.copy(presentation = side, stackId = null)
        bringToFront(id)
    }

    fun undock(id: String, fallback: SurfaceGeometry) {
        cancel(id)
        val old = state(id, fallback)
        surfaces[id] = old.copy(presentation = SurfacePresentation.FLOATING)
        bringToFront(id)
    }

    fun stack(id: String, targetId: String, fallback: SurfaceGeometry, targetFallback: SurfaceGeometry = fallback) {
        if (id == targetId) return
        cancel(id)
        cancel(targetId)
        val source = state(id, fallback)
        val target = state(targetId, targetFallback)
        val stack = target.stackId ?: source.stackId ?: "stack:" + targetId
        val targetGeometry = target.geometry
        surfaces[targetId] = target.copy(
            stackId = stack,
            presentation = SurfacePresentation.FLOATING,
        )
        surfaces[id] = source.copy(
            stackId = stack,
            presentation = SurfacePresentation.FLOATING,
            geometry = targetGeometry,
            sizeClass = target.sizeClass,
        )
        bringToFront(id)
    }

    fun unstack(id: String, fallback: SurfaceGeometry) {
        val old = state(id, fallback)
        if (old.stackId == null) return
        surfaces[id] = old.copy(stackId = null)
        bringToFront(id)
    }

    fun stackMemberIds(id: String): List<String> {
        val stack = surfaces[id]?.stackId ?: return listOf(id)
        return surfaces.values.filter { it.stackId == stack }.sortedBy { it.zOrder }.map { it.instanceId }
    }

    fun isVisibleStackTab(id: String): Boolean {
        val stack = surfaces[id]?.stackId ?: return true
        val members = surfaces.values.filter { it.stackId == stack }
        return members.maxByOrNull { it.zOrder }?.instanceId == id
    }

    fun ensure(id: String, fallback: SurfaceGeometry) {
        if (id !in surfaces) bringToFront(id, fallback)
    }

    fun z(id: String): Float = (surfaces[id]?.zOrder ?: 1).toFloat()

    fun bringToFront(id: String, fallback: SurfaceGeometry? = null) {
        val old = surfaces[id] ?: fallback?.let { WorkspaceSurfaceState(id, it) } ?: return
        // Bounded ordering also keeps long sessions/restored state safe.
        if ((surfaces.values.maxOfOrNull { it.zOrder } ?: 1) >= 999_999) {
            surfaces.values.sortedBy { it.zOrder }.forEachIndexed { index, surface ->
                surfaces[surface.instanceId] = surface.copy(zOrder = index + 1)
            }
        }
        val next = (surfaces.values.maxOfOrNull { it.zOrder } ?: 1) + 1
        surfaces[id] = old.copy(zOrder = next)
    }

    fun restore(serialized: List<String>) {
        resizeGestures.clear()
        candidates.values.forEach { it.value = null }
        surfaces.clear()
        WorkspaceSurfacePersistence.decode(serialized).forEach { surfaces[it.instanceId] = it }
    }

    fun save(): List<String> = WorkspaceSurfacePersistence.encode(surfaces.values)
}

@Composable
internal fun rememberWorkbenchSurfaceController(): WorkbenchSurfaceController = rememberSaveable(
    saver = listSaver(
        save = { it.save() },
        restore = { WorkbenchSurfaceController(WorkspaceSurfacePersistence.decode(it)) },
    ),
) { WorkbenchSurfaceController() }
