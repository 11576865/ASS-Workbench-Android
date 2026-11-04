# Contextual inspector tab increment

Design reference: Figma 5yNY5dMnmpmaOnYxDlEhY5 node 85:337. This increment covers the Event / Style / Position tab strip and existing editor routing, not full inspector visual parity.

- Render three 48dp native accessible tabs with theme-token labels and active underline.
- Project TEXT/EVENT to Event and VECTOR_CLIP/POSITION to Position. Other tools have no fabricated selected inspector category.
- Dispatch through the existing FIXED selectTool command. Because that route reopens FollowFocus tools, enable cross-tool switching only when the current binding is FollowFocus and the resolved existing Event equals Focus. Pinned/selection targets, missing and stale targets are disabled with visible reason, rather than silently editing another Event.
- Keep the central shared Raw dock mounted for both TEXT and EVENT in the wide subtitle scene. Docked EVENT displays existing timing controls alongside metadata; compact and SPATIAL EVENT retain their existing behavior. This remains separate timing and metadata commit commands, not a new atomic combined review.

Added JVM cases for target eligibility and active-tab projection; Compose cases for real tool dispatch and locked-binding disabled semantics. Local whitespace and Actions artifact-policy checks pass. Android/JVM/Compose execution for this increment is pending; the local runtime has no Gradle.

Prior source head 1985cd2: source/JVM #39908199838 passed. Emulator #39908199829 failed after APK prebuild. Both attempted log reads returned no usable logs/internal error, so its root cause, case counts and dock-runtime outcome are unknown. Do not reuse the previous timeout or 172/5 count as this run's result.

Remaining: full property-row and provenance visual alignment; QC/review layout; bilingual data mapping; actual target-size native captures; failure triage and device/IME acceptance. Draft #149 remains stacked on #148. No main merge.

UIGS Intake: applied existing truthful selection/binding and task-versus-tool concepts, including the existing Raw shortcut target guard. No new reusable rule or Canonical promotion.
