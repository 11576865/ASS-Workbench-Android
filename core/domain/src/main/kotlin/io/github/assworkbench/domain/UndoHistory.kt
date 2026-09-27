package io.github.assworkbench.domain

import java.util.ArrayDeque

class UndoHistory<T>(initial: T, private val limit: Int = 80) {
    init {
        require(limit >= 2)
    }

    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun commit(next: T): T {
        if (next == current) return current
        undo.addLast(current)
        while (undo.size > limit) undo.removeFirst()
        current = next
        redo.clear()
        return current
    }

    fun undo(): T {
        if (undo.isEmpty()) return current
        val previous = undo.removeLast()
        redo.addLast(current)
        current = previous
        return current
    }

    fun redo(): T {
        if (redo.isEmpty()) return current
        val next = redo.removeLast()
        undo.addLast(current)
        current = next
        return current
    }

    fun reset(value: T): T {
        undo.clear()
        redo.clear()
        current = value
        return current
    }
}
