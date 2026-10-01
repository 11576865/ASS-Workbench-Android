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

- [x] Style / Position can resolve explicit ToolInstance Binding.
- [x] Pinned Position mutation paths accept Event id rather than mutating Focus to reach the target.
- [x] Opening a different pinned target creates a sibling instead of silently retargeting an existing pinned primary.
- [x] Missing pinned Event remains UNRESOLVED instead of falling back to Focus.
- [ ] Extend explicit target plumbing to remaining focus-dependent event tools.

Target invariant:

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


## Preview / Interaction phase — 0.29.x

- [x] Interaction Overlay promoted from experimental container to `ui/interaction`.
- [x] Fixed and Canvas hosts share one InteractionOverlayRegistry.
- [x] Position rod/proxy can be hosted above either presentation.
- [x] Preview long-press discovers active Dialogue candidates by ASS anchor.
- [x] Candidate confidence distinguishes explicit anchor / derived anchor / unresolved semantics.
- [x] Preview target action separates Focus from “open pinned Position target”.
- [ ] Renderer-backed glyph/bbox hit testing. Anchor discovery must not be described as glyph hit testing.
- [ ] Lens as an independent Workspace node.
- [ ] Explicit control-display gain presets and persisted interaction preferences.

The Preview picker is a discovery surface, not a second renderer. libass remains visual authority; approximate anchor discovery is intentionally labeled as such.


## Scope transparency phase — WHO / WHERE / HOW MANY

- [x] Add a canonical UI resolver for mutating ToolInstance scope.
- [x] Event tools expose the bound Event identity independently from global Focus.
- [x] Shared Style editing reports the Style name and number of affected Events.
- [x] Position / Effects / Vector Clip report Event Override as the write target.
- [x] Batch defaults to FollowSelection and reports the selected object count.
- [x] Unresolved pinned targets keep their former identity visible with affected count 0.
- [x] Fixed and Canvas hosts consume the same derived scope explanation.
- [ ] Extend scope metadata to Timeline trim, QC Quick Fix and container write-back actions whose mutation target is action-dependent.
- [ ] Promote individual parameter Read Source / Write Target into the parameter model.

The banner is derived state only. It must never become another owner of target identity, affected counts or editable ASS values.
