package io.github.assworkbench.app.ui.workspace

/**
 * Migration helpers for moving floating UI surfaces from tool-type identity
 * to ToolInstance identity.
 *
 * A surface is a presentation of a tool instance. It must not use the domain
 * tool type as its identity because multiple instances of the same editor tool
 * may coexist.
 */
internal object WorkspaceSurfaceMigration {

    fun surfaceId(instance: WorkspaceToolInstance): String =
        instance.id

    fun duplicateInstance(
        source: WorkspaceToolInstance,
        newId: String,
    ): WorkspaceToolInstance =
        source.copy(id = newId)
}
