# ASS Workbench Workspace Kernel Phase 1

## Goal

Replace the implicit floating-panel model with an explicit workspace model.

The application is not split into a beginner UI and a professional UI. The workspace itself is the product model: tools, context, bindings and presentation are visible concepts.

## Core concepts

### ToolInstance

A tool window is an instance, not only a tool type.

Example:

- Position #1 -> fixed to Event 42
- Position #2 -> follows current focus

Both can exist simultaneously.

### Binding

A ToolInstance observes one of:

- FollowFocus
- FollowSelection
- PinnedEvent

A pinned Event that no longer exists becomes unresolved. It must not silently retarget.

### Workspace state

Workspace owns:

- active tool instance
- open instances
- bindings
- presentation state

ASS document data remains separate.

## Migration rules

1. Surface movement never modifies subtitle data.
2. Clear-screen hides surfaces; it does not destroy instances.
3. Preview rendering remains authoritative through the existing renderer.
4. Existing undo/redo and transient preview semantics remain unchanged.
5. Position and Style editing must eventually target explicit Event identity instead of only global focus.

## Phase order

1. Introduce workspace state model.
2. Migrate floating surfaces to instance identity.
3. Add binding UI.
4. Move geometry operations to explicit Event ids.
5. Persist workspace layout.
