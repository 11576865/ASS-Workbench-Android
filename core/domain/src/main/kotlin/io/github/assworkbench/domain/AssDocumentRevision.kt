package io.github.assworkbench.domain

/** Session-local revision; equal content after Undo does not restore old authority. */
object AssDocumentRevision {
    fun advance(revision: Long, before: AssDocument, after: AssDocument): Long =
        if (before == after) revision else Math.addExact(revision, 1L)
}
