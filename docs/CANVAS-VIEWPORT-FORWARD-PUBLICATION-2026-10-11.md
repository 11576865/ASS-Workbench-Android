# Canvas viewport Forward must publish the persisted scene

Date: 2026-10-11
Status: repair-submitted / Pending External Validation
Source: ASS-Workbench-Android active redesign PR #138; emulator run 38061223380 at head `800a662e892be84293b53431ca6b85f0cf108429`.

## Observed

The latest Android Emulator Regression ran 122 instrumented cases, with five failures. `explicitBirdseyeJumpHasIndependentViewportBackAndForward` ended at the assertion that the saved camera must differ from the starting camera following a Forward action. Android CI and Fontconfig were green for this exact revision.

## Source-level root cause

`InfiniteCanvasHost` handles intentional viewport navigation with `navigateToCamera`, `goToPreviousViewport`, and `goToNextViewport`. The first two assign the new camera to `scene` and immediately call `onSaveScene(InfiniteCanvasPersistence.encode(...))`. The Forward handler assigned the camera only to the local Compose `scene` and omitted the corresponding publish call. The callback-backed persisted scene could therefore lag behind the expected destination.

## Bounded correction

- Match Forward's publish semantics to Visit and Back, using the updated camera and unchanged world nodes.
- Strengthen the existing Android integration regression to capture the previously visited camera and require exact restoration after Forward. Assert node geometry unchanged and ASS Undo count remains zero.
- Preserve existing camera-history stacks, ToolInstance identities, document editing state and independent Undo.

## Verification boundary

Product fix and connected regression submitted on top of PR #138; Android CI/Emulator/Fontconfig must run at the integrated head. The other four failures in the same 122-test run—fixed-inspector draft switching, semantics/LOD visibility, board summary expectations and left-side rail visibility—are not claimed fixed here. No device-visual acceptance or 240-item UI completion.
