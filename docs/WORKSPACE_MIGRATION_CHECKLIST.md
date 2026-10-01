# Workspace Migration Checklist

## Phase 1A — Model layer

- [x] WorkspaceState introduced
- [x] ToolInstance identity introduced
- [x] Binding model introduced
- [x] Pinned target unresolved state defined

## Phase 1B — Surface identity

Goal:

Replace the assumption that one tool type maps to one floating surface.

Old model:

```
STYLE -> one surface
POSITION -> one surface
```

Target model:

```
STYLE:primary
STYLE:2
POSITION:primary
POSITION:2
```

Each surface must have an independent identity.

## Required changes

- Surface controller keys must use ToolInstance.id.
- Duplicate surface creates a new ToolInstance.
- Closing a surface removes presentation, not subtitle data.
- Bringing a surface forward changes only presentation order.

## Phase 1C — Explicit editing target

Current risk:

```
Tool -> focusedEventId -> mutation
```

Target:

```
ToolInstance
   -> Binding
      -> Event id
         -> mutation
```

The focused event remains a convenience context, not the only source of truth.

## Phase 1D — Persistence

Later migration:

Persist:

- tool instances
- bindings
- surface geometry
- active instance

Do not persist:

- transient renderer state
- pointer interaction state
- temporary preview overlays

## Surface phase — 0.27.2

- [x] Separate surface geometry/size/lock/order model
- [x] Saveable Activity restoration, committed geometry only
- [x] Temporary viewport clamp without overwriting canonical layout
- [x] Tool strip and app bar outside floating hit regions
- [x] Independent layout lock vs object pin
- [x] Free resize outline and one release-time remeasure
- [ ] Durable project workspace persistence / user templates
- [ ] Docked / floating host migration
- [ ] Per-tool semantic size-class content

See `WORKSPACE_SURFACES_0.27.2.md` for exact scope and test coverage.
