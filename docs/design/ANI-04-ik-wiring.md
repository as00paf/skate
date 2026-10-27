# ANI-04 follow-up: foot/hand IK target wiring (design)

Status: design only. No ANI-* task exists for these follow-ups; they come from the ANI-04 `NEEDS` lines (GAMEPLAY, EDITOR). The engine side (solver, `Animator.ik`, debug draw) is done.

## Division of responsibility

- **Animation (done):** `IkRig` constraints on `Animator.ik`; `AnimationSystem` solves them after pose evaluation. Targets are plain `Vector3f`/`Quaternionf` in `WORLD` or `MODEL` space; weight 0 disables a constraint.
- **Gameplay (to do):** decides *when* a foot is pinned and *where*. Animation never knows about the board.
- **Editor/render (to do):** toggle and display the debug view.

## Foot targets on the board

1. **Board sockets.** The board prefab carries two child GameObjects (`Socket_FrontFoot`, `Socket_BackFoot`) whose `Transform` is the foot placement point, with Y-up and +Z toward the nose. They move with the board during trick animations and physics, so no per-trick math is needed.
2. **Rig setup (on spawn/mount).** `SkateBehavior` registers, once, for each leg: `TwoBoneIkConstraint(rootBone = "UpperLeg_L/R", midBone = "LowerLeg_L/R", endBone = "Foot_L/R", space = WORLD, pole = knee hint)`. Bone names go through `BoneNameMapper`/`HumanoidBone`; the lookup is by the skeleton's own names, so use the mapped names for the character's rig.
3. **Per frame (before `AnimationSystem`).** Copy each socket's `worldMatrix` translation into `constraint.target`, its rotation into `constraint.endRotation`, and the knee hint (character forward + up offset) into `constraint.pole`. This must run before `AnimationSystem` in the same frame, or feet lag one frame; set the system priority accordingly (gameplay behaviours run before `AnimationSystem`).
4. **Weight.** `weight` follows the skate state: 1 in RIDE/AIR/GRIND/MANUAL, ramped to 0 over about 0.15 s on PUSH (the pushing foot is on the ground) and BAIL (ragdoll takes over). Ramp in gameplay with a simple smoothing; the solver only blends.
5. **Hands.** Same constraint type with `Hand_*` bones and a hand-grab socket for grab tricks. Look-at on `Head` targets the next obstacle or camera focus with `maxAngleDegrees` ~ 70.
6. **Ragdoll.** When `RagdollSystem` takes over, set all weights to 0.

## Open questions for the lead

- Who owns the `Socket_*` children: the skate prefab (GPL-01 prefab work) or a skeleton-side bone attach? Proposed: prefab.
- A leg-length mismatch between character and board (the target is out of reach) currently stretches toward the target and stops at full extension. A "keep pelvis height" correction would belong in gameplay (lower the root), not in the solver.

## Debug view (done)

`AnimationSystem.drawIkDebug` (default off) sends lines through `ikDebugSink`, which `Engine` wires to the renderer's `DebugRenderer.addLine3D`. Lines: green chain, red target cross, yellow miss (end bone to target), blue pole, cyan look-at ray, magenta look-at target. An editor toggle should set `drawIkDebug` (a one-field change behind a command or settings flag; NEEDS EDITOR).
