# 0.27.1 — Adaptive workbench UI

Version: 0.27.1 / versionCode 30.

## Behavior

The 0.25 layout placed preview at the top of an otherwise empty landscape column and expanded editors inside subtitle rows. This release separates navigation from the inspector, assigns the preview column's remaining height to a compact timeline, and makes all feature groups visible in a permanent horizontal tool strip.

Wide windows use preview / navigation / inspector columns. Narrow landscape and portrait stack navigation above the inspector. Preview can be collapsed for text-heavy work. Dark, light and system palettes are persisted locally. Video retains its aspect ratio and the existing coordinate canvas; it is never stretched to fill a panel.

Text, effects and Event metadata share one draft-preserving editor identity. The SaveableStateHolder is owned above orientation-specific layout branches. Undo, recovery, MKV and native renderer logic remain in their existing domain layers.

## Release gate

Run Android CI, Emulator Regression (including actual rotation and tool-switch draft coverage), and the arm64 Fontconfig production build before merging. Physical-device testing follows publication under the established 0.27.x strategy. Automated evidence must be recorded from actual workflow results, not inferred from source.

## Device follow-up

Check landscape timeline height, keyboard resizing, large font scaling, 4:3 / portrait video letterboxing, and OEM rotation behavior. This is a debug-variant production renderer candidate, following the existing release mechanism.
