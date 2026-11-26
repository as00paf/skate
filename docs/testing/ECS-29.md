# ECS-29: generic inspector ranges, read-only rows, runtime state

1. Open any project, select an object with a **StreamingFocus** component (Add Component if needed).
2. Drag `radiusScale`: it stops at 0.1 and 10 and moves in 0.05 steps (`@Range`).
3. Select a component with a `val` saved field (e.g. `NetworkIdentity`, `MeshCollider3D`): the field shows as a greyed "Fixed value (read-only)" row, never missing. Arrays show "N values (read-only)".
4. Save a scene with a Player/Skater, reopen: `InputStateComponent` fields, the sun's `lightSpaceMatrix` and Animator playback times are no longer in the `.scene` file (runtime state, `@Transient`); the scene loads and plays as before.

Audit notes: asset-path Strings left to their domain tasks (`MeshCollider3D.meshRef` is a GUID `val`: PHY-37; `RenderComponent.materialPath` has an asset picker). `DestructibleComponent.broken` and `Animator.isPlaying` stay saved (authored or replicated); `SkeletonComponent.pose`/`NetState.values` stay hidden (`FieldKind.NOT_EDITABLE`).
