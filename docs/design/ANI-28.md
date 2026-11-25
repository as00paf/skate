# ANI-28 — IK review for skateboarding and parkour (design note)

Status: **approved by the human (2026-11-18)**, every recommendation accepted (answers in §10). Slice **a** is GPL-38;
slices b–h follow §6.
Task: ANI-28 (`P1`), human request (Discord 2026-11-17): a complete review of inverse kinematics for skateboarding
(feet on the board, hands on the rail or ledge, look-at) and parkour (hand plants, foot placement on ledges and
stairs): what exists, what is missing (3D markers or targets as components, IK chains, weights, blending with
animation), and a super user-friendly editor UI (gizmos for targets, presets for the skater).

---

## 1. Goal

Characters touch the world where the world is: feet on the deck and on stairs, hands on rails, ledges and walls, the
head turned toward what matters. A designer sets this up **without code**: pick a preset, drag targets in the viewport,
tune weights with sliders, and see it live. Gameplay only says *where* and *how much*; animation decides *how* the
pose bends (agent rule: no skate logic in the animation system).

## 2. What exists today (read from the code, 2026-11-17)

| Piece | Where | What it does | Limits |
|---|---|---|---|
| Solvers | `engine/animation/ik/IkSolvers.kt` | Two-bone IK (pole hint, end rotation, weight) and single-bone look-at (axis, max angle). Pure, tested. | No chains longer than 2, no pelvis adjustment, no joint limits (except the look-at cone). |
| Constraints | `ik/IkRig.kt` | `TwoBoneIkConstraint`, `LookAtConstraint`; WORLD or MODEL targets; weight 0 = off. | **Runtime only**: not serialized, no component; code registers them at spawn. |
| Pass | `ik/IkPass.kt`, `AnimationSystem.applyIk` | After pose evaluation (and the mask), every constraint is solved in list order; the pre-IK pose is kept so weights do not compound. | No ordering by kind (pelvis before legs), no per-state or per-clip weights. |
| Debug | `ik/IkDebugDraw.kt`, `Engine` wires the sink | Chain, target, miss, pole and look-at lines. | `AnimationSystem.drawIkDebug` is never turned on: **no editor toggle**. |
| Ground feet | `engine/gameplay/ik/FootIk.kt`, `PhysicsSystem`, `PlayerMotionSystem` | Raycasts under each foot, ramps the weight, on foot only (off while riding). | See F1, F2. |
| Procedural poses | `animation/procedural/HumanoidPose.kt` | Builds skate stances with two-bone IK to board-relative foot goals. | Baked into the pose; not live contacts. |
| Board sockets | `docs/design/ANI-04-ik-wiring.md` | Proposed `Socket_FrontFoot` / `Socket_BackFoot` on the board. | **Never implemented.** |
| `BoneOverride` component | `ecs/components/BoneOverride.kt`, added by `game/prefabs/Skater.kt` | Stores per-bone rotations. | **Nothing reads it** (dead data). |
| Physics queries | `IPhysics3D`: `raycast`, `raycastAll`, `shapeCast`, `overlap` | Enough to find ground, edges and hand-plant points. | No probes built on them besides the foot raycast. |
| Parkour | — | — | **Nothing exists.** |

### Findings

- **F1 (bug, verified): ground foot IK never runs on the bundled skater.** `FootIkConfig` names the legs
  `mixamorig9_LeftUpLeg`..., but the importer strips that prefix (`BoneNameMapper`): the skeleton of `james.glb` has
  `LeftUpLeg`, `LeftLeg`, `LeftFoot`. `IkPass` skips constraints whose bones it cannot find, silently. Fix: resolve
  legs by `HumanoidBone` slot (as `LegChain.detect` does since ANI-15) and warn once when a chain's bones are missing.
- **F2: feet do not follow the slope, and the body does not lower.** `FootIk` sets the target but no `endRotation`,
  so the sole stays at the clip's angle on ramps; and with no pelvis offset, the lower foot on stairs stretches and
  stops short (ANI-04 open question, never done).
- **F3: while riding, nothing ties the feet to the board.** Foot IK is off when mounted; the feet come from the clip
  or the procedural stance, relative to the body. Any board motion relative to the body (flip tricks, grind and slide
  locks, board tilt on ramps) separates feet and deck.
- **F4: nothing is authorable.** Constraints exist only in code; there is no component, no saved data, no editor UI,
  no gizmo, no preset, and the debug view cannot be switched on.
- **F5: missing solver pieces** for the requested moves: n-bone chains (spine lean toward a target), a pelvis/hips
  offset, foot roll to a surface normal, look-at spread over spine, neck and head, and simple joint limits.
- **F6: no blending with animation authoring.** Weights are set only by code. A clip or a controller state cannot
  say "left hand planted from 30 % to 60 %".

## 3. What the requested moves need

| Move | Chains | Targets come from | Weight comes from |
|---|---|---|---|
| Feet on the board (ride, flips, grind lock) | both legs, feet aligned to the deck | board sockets (child markers of the board) | riding states (1), push (front foot only), bail (0) |
| Push / ground foot | one leg | ground probe | push state |
| Hand on rail or ledge (grabs, handplants, boneless) | arm, hips lean | rail/ledge probe or a socket on the obstacle | trick state, clip window |
| Look at landing / next obstacle | spine → neck → head spread | gameplay goal (landing point) or camera focus | air states |
| Stairs and ledges on foot | both legs + pelvis offset + foot roll | ground probe per foot | locomotion |
| Vault / hand plant (parkour) | both arms, hips | edge finder on the obstacle | vault state, clip window |
| Ledge grab / hang | both arms (feet on the wall when found) | edge finder | hang state |

## 4. Proposed architecture

### 4.1 Data: IK as components (authorable, saved with the scene)

- **`IkTarget`** component on any GameObject: a 3D marker. Its `Transform` is the target (position, and rotation for
  feet and hands). Fields: `label`, `color`, `alignRotation`. A target can sit anywhere: a child of the board
  (`front foot`, `back foot`), of a rail, of the character (`look focus`), or free in the level. Moving it with the
  normal transform gizmo moves the limb.
- **`IkSetup`** component on the character (replaces code-only registration; `Animator.ik` stays as the solver input):
  a list of **chains**, each:
  - `kind`: `LIMB` (two-bone with pole), `CHAIN` (n-bone, FABRIK), `LOOK` (look-at spread over bones), `HIPS`
    (pelvis offset so both feet reach);
  - bones by **humanoid slot** (`LEFT_FOOT`...) resolved on the character's rig, with an optional explicit name
    override for non-humanoid rigs;
  - `target`: an `IkTarget` object reference, or a named **goal** that gameplay sets (`"leftHandRail"`);
  - `pole` (target reference or "auto"), `weight`, `rampSeconds`, `alignRotation`, limits (look cone, chain stiffness).
- **`IkSetupSystem`** (ANI) runs before `AnimationSystem`: builds and updates `Animator.ik` from `IkSetup`, the targets'
  world transforms and the goals. Code-registered constraints keep working (gameplay can still add its own).

### 4.2 Weights and blending with animation

Final weight per chain = `IkSetup` weight × controller state weight × gameplay goal weight, each ramped:
- **per state** in the controller asset (`StateDef.ik = {"leftFoot": 1, "leftHand": 0}`), edited in the Animator
  window's state inspector next to blend trees (ANI-23), so "feet on board in every riding state" is data;
- **per clip window** later: an IK weight curve in `.skanim` (the Animation Editor already has curves, ANI-15);
- **gameplay** multiplies (bail ramps everything to 0; a probe that finds nothing sets its goal weight to 0).

Solve order inside the pass: `HIPS`, then legs, then `CHAIN`/`LOOK` (spine to head), then arms, so hands land on
targets after the spine leans. Everything stays pure (pose in → pose out) and unit-tested without GL.

### 4.3 Solvers to add (ANI, pure)

1. Pelvis offset: lower the hips by the largest leg shortfall (capped), before the legs solve.
2. Foot roll: align the foot (and toes) to the target rotation or surface normal; the ground probe returns the normal.
3. FABRIK n-bone chain with a stiffness falloff (spine, tails) and simple per-joint cone limits.
4. Look spread: distribute a look-at over spine, neck and head with per-bone shares and a cone.

### 4.4 Contacts (where targets come from)

- **Board sockets** (GPL): `IkTarget` children on the board prefab: `front foot`, `back foot`, `nose grab`, `tail
  grab`. They move with the board in every trick, so feet stay on the deck with no per-trick code (ANI-04's plan).
- **Probes** (pure functions over `IPhysics3D`, PHY or GPL): ground per foot (exists, add the normal), **edge finder**
  (forward shape cast, then a down ray: edge point, edge direction, top normal) for ledges, rails and vault boxes,
  and **hand-plant point** on a surface ahead.
- **Motion warping** (parkour, later): stretch the root motion so a vault clip lands its hands on the found edge.
  Needs its own design note; out of scope here.

## 5. Editor UI (super user-friendly)

Domain agents write their editor UI (rule of 2026-11-17); skate-editor reviews.

- **IK inspector** (on `IkSetup`): a **preset** dropdown at the top (Skater, Humanoid walker, Parkour, Custom); one row
  per chain with an on/off toggle, its name ("Left foot"), a weight slider, the target picker and a **Create target
  here** button that adds a child `IkTarget` at the limb's current end and selects it. In play mode the rows show live
  weights.
- **Viewport gizmos**: `IkTarget`s draw as coloured spheres with an orientation tripod; selecting one shows the usual
  move/rotate gizmo; the chain it drives is highlighted. Poles draw as small cones. A **viewport toolbar toggle**
  shows the IK debug lines (the existing `IkDebugDraw`).
- **Edit-mode preview**: an "IK preview" toggle solves IK in the scene view while not playing, so dragging a target
  bends the character immediately.
- **Animator window**: per-state IK weights (§4.2) with the chain names, next to the state's clip and blend tree.
- Conventions: standard window header, every edit a `Command` (undo), EN/FR strings, tooltips through `Tooltips`,
  headless ImGui render tests.

### Skater preset (what "one click" sets up)

| Chain | Kind | Target | Weight by state |
|---|---|---|---|
| Hips | HIPS | — | on foot 1, riding 0.5 |
| Left / right foot | LIMB, align | board `front foot` / `back foot` while riding; ground probe on foot | riding 1, push: pushing foot 0, bail 0 |
| Left / right hand | LIMB, align | goals `grab`, `rail` | grab and handplant states only |
| Look | LOOK (spine 0.2, neck 0.3, head 0.5) | goal `landing` | air 1, otherwise 0.3 toward travel |

## 6. Slices (proposed tasks; IDs to be assigned by the lead)

| Slice | Owner | Content | Deps |
|---|---|---|---|
| **a** | GPL | **F1 fix**: resolve `FootIkConfig` legs by `HumanoidBone` (warn once when missing); feet align to the ground normal. Bug, can go before approval. | — |
| **b** | ANI | `IkTarget` and `IkSetup` components, `IkSetupSystem`, humanoid-slot resolution, serialization; tests. | approval |
| **c** | ANI | Solvers: pelvis offset, foot roll, FABRIK chain with limits, look spread; solve order; tests. | b |
| **d** | ANI | Per-state IK weights in the controller asset and the Animator window; ramps; tests. | b |
| **e** | ANI (EDT reviews) | IK inspector with presets, target gizmos, create-target, debug toggle, edit-mode preview; render tests. | b, c |
| **f** | GPL | Board sockets as `IkTarget`s on the board prefab; the Skater preset drives feet while riding, hands for grabs and handplants, look at the landing; tests. | b, d |
| **g** | PHY (or GPL) | Probes: ground normal, edge finder, hand-plant point (pure over `IPhysics3D`); tests with a fake. | — |
| **h** | ANI | IK weight curves in `.skanim` clips (Animation Editor) feeding §4.2. | d |
| **i** | GPL + ANI | Parkour: own design note first (vault, stairs, ledge grab, motion warping), using b–h. | b–g |

Order: **a** now; then **b → c, d → e, f**; **g** in parallel; **h** and **i** later.

## 7. Risks

- **Pops** when a target appears or vanishes: every weight ramps; probes keep the last good target for a few frames.
- **Out-of-reach targets** (short legs, tall boards): pelvis offset first, then the limb stretches to full length and
  stops; the debug view shows the miss in yellow.
- **Rigs without humanoid names**: slots fail to resolve; the inspector shows which bones are missing and offers a
  name override.
- **Cost**: a handful of chains per character is cheap; IK is skipped for characters culled by the animation LOD.
- **Ordering**: targets must be updated before `AnimationSystem` in the same frame, or limbs lag one frame
  (`IkSetupSystem` runs after gameplay and physics, before animation).

## 8. Out of scope

Full-body IK solvers, physics-based balance, motion matching, ragdoll blending beyond weight 0, and motion warping
(slice **i**'s own note).

## 9. Questions for the human (with my recommendation)

1. **Targets as scene objects?** `IkTarget` markers as GameObjects (move them with the normal gizmo, parent them to
   the board or a rail) rather than fields inside one IK component. *Recommendation: yes.*
2. **Chains by humanoid slot?** Bones picked by humanoid slot (works on any humanoid rig), with a name override for
   other rigs. *Recommendation: yes.*
3. **Where do per-state weights live?** In the controller asset, edited in the Animator window (gameplay can still
   multiply). *Recommendation: the controller.*
4. **Edit-mode IK preview** in the scene view (drag a target, see the pose without pressing Play)? *Recommendation:
   yes, behind a toggle.*
5. **Parkour scope:** IK and probes now (slices b–h), parkour movement itself later behind its own GPL design note
   (slice i)? *Recommendation: yes.*
6. **Bug F1:** fix it right away as slice **a**, before the rest is approved? *Recommendation: yes; it is a bug.*
7. **`BoneOverride`:** nothing reads it. Remove it, or turn it into the IK inspector's "pose override" (a fixed
   rotation per bone after IK)? *Recommendation: remove it in slice b; the IK setup covers the need.*

## 10. Decisions (human, 2026-11-18)

All recommendations accepted:

1. IK targets are scene objects: `IkTarget` GameObjects.
2. Chains pick bones by humanoid slot, with a bone-name override.
3. Per-state IK weights live in the controller asset, edited in the Animator window.
4. Edit-mode IK preview, behind a toggle.
5. IK and probes now (slices b–h); parkour movement later, under its own GPL note (slice i).
6. Bug F1 is fixed now as slice a, by skate-gameplay (task **GPL-38**).
7. `BoneOverride` is removed in slice b.
