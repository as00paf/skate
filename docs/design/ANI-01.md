# ANI-01 — Animation review

Scope: `AnimationSystem`, `Animator`, `SkeletonComponent`, `BoneOverride`, `AnimationLoader`, `BoneNameMapper`, `SkeletonMath`
(plus `Animation`, `AnimationSampler`, `Skeleton`, `SkeletonPose`, which they depend on; those belong to other owners/paths and are flagged where relevant).
Review only: no code changed. Severity: **S1** wrong output / hang, **S2** incorrect or fragile behaviour, **S3** perf / maintainability.

## 1. Defects

### AnimationLoader
| ID | Sev | Defect |
|----|-----|--------|
| L1 | S1 | `getAnimInfo` returns `AiAnimationInfo(0f, firstPosY)`: `scale = 0`, so every non-root translation key is multiplied by 0 and all translation tracks are flattened to zero. Bone offsets only survive because the bind pose is re-applied (see A1). |
| L2 | S1 | `getAnimInfo` loop: `mChannels() ?: continue` never increments `i` (infinite loop if null); it always reads `channels.get(1)` instead of channel `i`, so "find Hips" only ever tests channel 1; throws `"Could not get anim info"` for any clip whose channel 1 isn't Hips. |
| L3 | S1 | Every clip in a file gets the same `name` (derived from the file name); `aiAnim.mName()` is ignored. `Animator.addAnimation` rejects duplicates by name, so multi-clip files lose all but one clip. |
| L4 | S2 | Root handling is hard-coded: Hips X/Z zeroed (no root motion), Y offset by the first key of clip 0 and applied to all clips; non-root Y is also forced to 0 (`else 0f`). Root motion cannot be opted into. |
| L5 | S2 | Name derivation `path.substringBefore(".fbx")` only works for FBX and lowercase; uses deprecated `capitalize()`. |
| L6 | S2 | Rotation keys are not normalised; `mPositionKeys()?.get(k) ?: continue` leaves zero times/values in the arrays; scale keys are ignored by `info.scale` while position keys use it (inconsistent). `ticksPerSecond` falls back to 60 (Assimp's default is 25). |
| L7 | S2 | `AIScene` leaks (`aiReleaseImport`) when `processAnimation` throws. Importing a full scene (mesh + materials) just to read animations is wasteful; flags ignore `aiProcess_PopulateArmatureData`/no-mesh options. |
| L8 | S3 | Always `LINEAR`; no keyframe reduction. Two near-identical overloads. Static, untestable without Assimp: parsing (AI* types) is mixed with clip building. |

### Animation.update (clip evaluation, `engine/assets/data/models/animations`)
| ID | Sev | Defect |
|----|-----|--------|
| A1 | S1 | `bone.localTransform = bindLocalTransform * animatedLocal`. glTF/FBX channel values are *absolute local TRS*, not deltas on top of the bind pose; the bind pose is double-applied. Only masked by L1. |
| A2 | S1 | The base TRS is read back from `bone.localTransform` (previous frame's result). Bones with a missing channel path (e.g. rotation only) accumulate/drift frame to frame; combined with A1 the pose is path dependent. Base must come from the bind pose each time. |
| A3 | S2 | `time % duration` is NaN for `duration == 0`; all clips loop, no one-shot/clamp mode. |
| A4 | S3 | Per call: `groupBy` allocation, `getBoneByName` is a linear scan with string compare per bone, `Matrix4f/Vector3f/Quaternionf` allocations. Channels should be resolved to bone indices once. |
| A5 | S2 | Writes into the shared `Skeleton` bones (`bone.localTransform`), so evaluation is not pure and two animators sharing a skeleton would clobber each other. |

### Animator
| ID | Sev | Defect |
|----|-----|--------|
| N1 | S1 | Layering violation: engine component imports `game.player.PlayerState` and stores `currentState`. Animation must not know skate/player logic (domain rule). |
| N2 | S1 | `play(name)` uses `contains(ignoreCase)` and takes the first match: `"idle"` can match `"Falling Idle"`. Default blend is `1f` vs `0.2f` for `play(Animation)`. |
| N3 | S2 | Interrupting a blend pops: `previousAnimation = currentAnimation` discards the in-flight blended pose. `play(sameAnimation)` returns early even if it is the outgoing clip. |
| N4 | S2 | `update(dt)` is empty (yet called by the system); time/blend state lives in public mutable `var`s mutated by the system. `blendTime` can end negative (alpha > 1, no clamp); no speed, loop flag, pause, or events. `currentTime` grows unbounded (float precision loss over long sessions). `normalizedTime` divides by `duration` (0 → NaN). |
| N5 | S2 | Serialisation: runtime fields (`currentTime`, `blendTime`, `isPlaying`, `previousTime`, `currentState`) are persisted; only `animations[].path` is meaningful. `resolveAnimationsFromPaths` silently skips missing files and resets `currentAnimation` to `first()` without setting `isPlaying`. Absolute `File(path)`. |
| N6 | S2 | `validateSkeletonCompatibility`: critical-bone check uses substring matches (`"hip"`, `"spine"`…), ignores mapper collisions (M1), and returns true when non-critical bones are missing with no diagnostics; commented-out dead code. |
| N7 | S3 | Duplicate-name check is O(n) per add; the Animator owns clip data and playback state and compatibility logic (3 responsibilities). |

### AnimationSystem
| ID | Sev | Defect |
|----|-----|--------|
| S1 | S1 | `BoneOverride` is never applied by the system (only edited by the inspector, created by `Skater`). Overrides have no runtime effect. |
| S2 | S2 | `rebuildCache` only scans `scene.children`; nested game objects (child models, prefab instances) never animate. Cache invalidation relies on `cacheDirty` set in `start()`. |
| S3 | S2 | Blend path snapshots two full pose copies as `List<Matrix4f>` every frame and blends decomposed matrices (shear/scale loss, `getUnnormalizedRotation`) instead of TRS. `alpha` is computed after decrementing blendTime, so frame 1 skips alpha 0 and the last frame overshoots. |
| S4 | S2 | Dual state: results are written to `bone.localTransform` and then copied into `pose.localTransforms`. `computeGlobalTransforms` runs twice per frame (see K1). RagdollSystem writes the same palette; ordering/ownership is implicit. |
| S5 | S3 | Everything is evaluated every frame, even when the pose cannot change (paused, not playing) or the mesh is off-screen (ANI-05). Four `getComponent`-style lookups only at rebuild, fine. |
| S6 | S3 | `ago.animator.update(dt)` is a no-op; per-frame allocations. |

### SkeletonComponent / BoneOverride
| ID | Sev | Defect |
|----|-----|--------|
| C1 | S2 | `SkeletonComponent.init` and `reset` rebuild skin matrices (and `AnimationSystem` does again). `matrixPalette` is `Array<Matrix4f>` so the renderer must pack per draw (`uploadMat4fArray`) – blocks UBO/SSBO upload (ANI-05). |
| C2 | S3 | Editor state (`selectedBone`) lives on the engine component. |
| B1 | S2 | `BoneOverride` stores `Quaternionf` by reference, `getOverrides()` copies the map each call, private serialised map in a data class, no weight / space (local vs model) / enable flag; keyed by bone name string (collisions per M1). |

### BoneNameMapper / SkeletonMath
| ID | Sev | Defect |
|----|-----|--------|
| M1 | S1 | Underscore stripping collapses distinct bones: `Spine_01`/`Spine_02` → `Spine`; `Arm_L_01`… Mapping is not injective, not idempotent, and cannot be reversed. |
| M2 | S2 | Mixamo-only prefix stripping, no canonical humanoid map (needed by ANI-03). A new `Regex` is built per call; commented-out dead code; the `"9:"` prefix is a guess. |
| K1 | S2 | `buildSkinMatrices` recomputes global transforms; the system calls `computeGlobalTransforms` first, then `buildSkinMatrices` (double traversal). |
| K2 | S3 | Recursive traversal with a fresh `Matrix4f()` per call; writes `bone.worldTransform` on the shared skeleton (same impurity as A5). `Skeleton.getBoneByName` is O(n) (no name→index map). |

### Tests
No unit tests exist for any of the reviewed classes (the only existing animation-adjacent tests are architecture guards).

## 2. Target design

Principles: evaluation is **pure** (pose in → pose out, no GL, no Assimp, no `Bone` mutation), index-based, allocation-free in steady state, and game-agnostic (gameplay decides the state, animation decides the blend).

### 2.1 Data
- `AnimationClip` (immutable): `name`, `duration`, `loop` default, `tracks: List<BoneTrack>` where a track targets a **bone index** (resolved once against a `Skeleton` by `ClipBinding`), plus `events: List<AnimEvent(time, name)>`. The existing `Animation` becomes the serialised/asset form and is converted to a clip on load.
- `Pose`: flat arrays `translations: FloatArray(3n)`, `rotations: FloatArray(4n)`, `scales: FloatArray(3n)` (TRS, not matrices), initialised from bind pose.
- `SkeletonMath` outputs the palette into one flat `FloatArray(16 * n)` (column-major), ready for UBO/SSBO upload (ANI-05).

### 2.2 Pure evaluation (`engine/animation/eval`, owned by this domain)
- `ClipSampler.sample(clip, binding, time, out: Pose)`: starts from bind pose, overwrites only tracks that exist, absolute TRS (fixes A1/A2), clamp or wrap by `loop`, safe for `duration == 0`.
- `PoseBlender`: `blend(a, b, w, out)` (lerp T/S, nlerp/slerp R with shortest-path), `additive(base, add, ref, w, out)`, and `masked(...)` with a per-bone weight array (ANI-03).
- All functions take pre-allocated outputs; no `Matrix4f` decomposition.

### 2.3 Animator (runtime state, no gameplay types)
- `Animator` keeps only serialisable config: clip paths, default blend, speed, and (ANI-02) a `controller` asset reference. Runtime state moves to a transient `AnimatorState`: layers, current/next clip + times, active crossfade, parameters (`float/bool/trigger`), pending events.
- Crossfade is a **normalised-weight** transition `t = clamp(elapsed / duration)`. Interrupting a fade snapshots the current blended pose into a frozen source pose (fixes N3). `play(name)` becomes exact-name lookup (case-insensitive) with an explicit `findByPrefix` helper; one default blend constant (N2).
- `PlayerState` is removed from the engine; the game layer maps player state → animator parameters/state name (`PlayerMotionSystem` is the caller; reported to lead as a cross-domain follow-up, see §4).
- Time is stored per layer and wrapped on loop to avoid precision growth (N4).

### 2.4 AnimationSystem pipeline (per animator, per frame)
1. Advance time/transitions (pure `AnimatorState.advance(dt)`), collect events.
2. Evaluate layers → `Pose` (sample + blend + masks).
3. Apply `BoneOverride` (weighted, local space) — fix S1; IK hook point for ANI-04 after overrides.
4. `SkeletonMath.computeGlobals(pose → globals)` once, then `buildPalette` into the flat array. Skip steps 2–4 when the animator is paused/unchanged or flagged culled (ANI-05).
5. Ragdoll, when active, owns step 4's globals (explicit `SkeletonComponent.drivenBy` flag, not implicit ordering).
- Cache includes nested game objects (traverse hierarchy) and is rebuilt on scene add/remove events.

### 2.5 Loader and naming
- Split `AnimationLoader` into a thin Assimp reader → plain `RawClip` DTOs (testable without natives) + `ClipBuilder`. Use `aiAnim.mName()` (unique-ified if blank/duplicate), real `ticksPerSecond` (fallback 25), normalised quaternions, no zero `scale`, no hips guessing: root motion handling becomes a per-clip import option (`rootMotion: NONE | XZ | FULL`, default `NONE` to preserve current behaviour) with the hips Y offset computed per clip. Always release the scene (`try/finally`).
- `BoneNameMapper`: make `map` pure and **injective** (strip only known namespace prefixes/`_$AssimpFbx$` suffixes; keep numbered suffixes), cache the regexes, and add a `HumanoidBone` canonical enum + alias table (Mixamo, UE mannequin, generic) used by ANI-03 retargeting. Provide `Skeleton.boneIndexByName` (hash map).
- `BoneOverride`: `Map<String, Override(rotation, weight, space)>`, copies on write, read-only view, `enabled` flag.
- `SkeletonComponent`: drop `selectedBone` into the editor panel state; construct palette lazily once.

### 2.6 Testing strategy
Pure functions in §2.2/2.3 get JUnit tests without GL: sampler (absolute TRS, clamp/loop, missing channels), blender (endpoints, shortest path, mask, additive), crossfade state machine (interrupt, zero duration), mapper (injective, idempotent), palette (bind pose → identity palette).

## 3. Work breakdown (maps to backlog)

| Step | Backlog | Content |
|------|---------|---------|
| 0 (prereq fixes, S1) | ANI-02 (first PR) | L1–L3, A1–A2, N1–N2, M1: fix scale 0 + channel-1 hang, clip names, absolute TRS evaluation, exact-name play, injective mapper. Each with a regression test. Must ship together (A1 is hidden by L1: fixing one alone breaks animations). |
| 1 | ANI-02 | `Pose`/`ClipSampler`/`PoseBlender`, `AnimatorState` with crossfade, 1D/2D blend trees, state machine asset + parameters, events; `BoneOverride` application. |
| 2 | ANI-03 | `HumanoidBone` map in `BoneNameMapper`, retarget bindings, per-bone masks. |
| 3 | ANI-04 | IK solvers (pure) plugged into the pipeline hook. |
| 4 | ANI-05 | Flat palette + UBO/SSBO and culling flag (needs REN-02). |

Suggested backlog amendment for the lead: add the step-0 fixes explicitly to ANI-02's "Do".

## 4. Cross-domain needs
- **GAMEPLAY**: `PlayerMotionSystem` calls `animator.play("idle")` etc. and `Animator.currentState` uses `PlayerState`; after step 0/1 gameplay should drive named states/parameters instead.
- **RENDER**: palette upload format change (flat `FloatArray`, UBO/SSBO) must be agreed via the lead before ANI-05 (`ModelRenderer.kt:74`, `ShadowRenderer.kt:118` read `matrixPalette`).
- **EDITOR**: `editor/imgui/components/Animator.kt` and `Skeleton.kt` depend on `Animator` fields and `selectedBone`.
- **ASSETS/ECS**: `Animation`, `AnimationSampler`, `Skeleton` (`engine/assets/data/models/animations`) are outside the animation-owned paths; defects A1–A5, K2 live there and need the lead to confirm ownership.

## 5. Assumptions
- Review is by static reading; no runtime profiling or play-testing was done (natives are Windows-only).
- Current visual output is only plausible because L1 and A1 cancel; defects were therefore reported together.
