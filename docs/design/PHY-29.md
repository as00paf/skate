# PHY-29 — Destruction system (design note)

Status: **revision 1, awaiting the human's approval.** No implementation code has been written. Open questions are in
§16; each has a recommended answer.

Task (GameEngine.md, Physics, human on Discord 2026-11-17): "plan a destruction system (fractured meshes,
pre-fractured vs runtime, debris pooling and lifetime, damage thresholds, effects and audio hooks, network and
save-state concerns with REN-37's remembered props). Design note `docs/design/PHY-29.md` with sliced tasks; ask for
approval before code." Done when the note is approved and its slices are in the backlog.

Reviewers: skate-gameplay (§5 damage, GPL-24), skate-render (§6.3 debris rendering, §9 scatter records), skate-assets
(§6 `.skfracture`), skate-online (§10), skate-audio (§8), skate-editor (§11).

All paths are relative to `src/main/kotlin/com/pafoid/skate/` unless they start with `src/test/` or `docs/`.
**NEW** marks a file or type that does not exist yet.

## 1. What exists today

| Area | Code today | What destruction can use |
|---|---|---|
| Collision events | `engine/events/PhysicsEvents.kt` `Collision(self, other, point, normal, impulse)`, published by `PhysicsSystem` once per pair at contact start, on Bullet, Jolt and `SimplePhysics3D` | Impact damage. **But** `impulse` is not the same quantity on every backend: Bullet and Simple report the impulse the solver applied, Jolt an estimate taken in the contact callback before solving (`physics3d/jolt/JoltContacts.kt` `impulseEstimate`). §5.2 fixes this. |
| Queries | PHY-22: `ScenePhysicsQueries` (raycast, shapeCast, `overlap`) as `PhysicsSystem.queries` | Explosions and area damage (§5.1) |
| Shapes | `ColliderShape.ConvexHull`, `CompoundHull`, `Box`, `Capsule`, `Cylinder`; `CompoundHullCollider3D`, `CustomCollider3D` | One convex hull per debris piece; no convex decomposition or fracture tool exists |
| Bodies | `IPhysics3D.add/remove(go)` for scene objects, `createRigidBody(shape, mass)` for standalone bodies, `applyImpulse`, `isKinematic`, sleeping (`activeBodyCount`) | Spawning pieces, freezing settled debris |
| Cost | PHY-17 benchmark (release natives): a 1000-box pile steps in 0.53 ms (Bullet) / 0.92 ms (Jolt) mean, p95 2.6 / 1.7 ms | A few hundred active debris pieces fit the frame (§12) |
| Damage pipeline | GPL-24 §4 (in progress as GPL-27 slice 24b): `HealthComponent` (data), `HitEvent`, `DamagedEvent`, `DiedEvent`, `HealthSystem` applies damage synchronously | Reused when present (§5.3) |
| Remembered props | REN-28 §15.10 (REN-37 tasks 23–25, not built): `ScatterItemKey`, `ChunkStateDelta` records `DESTROYED` / `CHANGED`, `ScatterItem` destroy hook, save container with named sections. Today `engine/ecs/systems/TerrainScatterEntitySystem.kt` spawns scatter prefabs and keeps removed items gone for the session only | Broken scatter props stay broken (§9) |
| Network | NET-01: server-authoritative, `NetReplicated` opt-in per component, snapshots of Transform and `RigidBody3D` velocity, no deterministic physics across machines | §10 |
| Effects | `AudioSystem` already turns `WaterEntered` into splash sounds (`engine/audio/WaterAudio.kt`); `FollowCameraSystem` reacts to `Collision`; `ParticleEmitter` has `burstCount`; `engine/render/particles/ParticlePool.kt` | §8: break events feed the same kind of hooks |
| Rendering | `RenderComponent` draws a whole model; per-part visibility is REN-32 (2) (GPL-24, not built); no per-object fade or dissolve | §6.3 |

## 2. Goals and non-goals

Goals:
- Props (crates, fences, glass, statues, scatter rocks) take damage from impacts, weapons and explosions, and break.
- Breaking can remove the object, swap it for a broken version, or shatter it into physical pieces.
- Debris is cheap: pooled, budgeted, short-lived, and never a frame spike.
- Effects (sound, dust, camera shake, scripts) hook in through events.
- Broken props stay broken across chunk streaming and save/load (REN-37), and in multiplayer.
- Same behaviour on Bullet, Jolt and `SimplePhysics3D`.

Non-goals (now): runtime fracture of arbitrary meshes (§6.4), structural collapse of buildings (§16 Q7), destructible
terrain, soft bodies.

## 3. Overview

```
 damage sources                         DestructionSystem (NEW, FIXED_UPDATE, after PhysicsSystem)
 ─ Collision impulse ──► impact damage ─┐
 ─ HitEvent (GPL-24)  ──────────────────┼─► integrity / HealthComponent ──► break queue (budget per step)
 ─ Explosion (overlap) ─────────────────┘                                     │
 ─ API: DestructionService.damage()                                           ▼
                                               REMOVE │ SWAP (broken prefab) │ SHATTER (.skfracture pieces)
                                                                              │
                                    events: DestructibleDamaged, Fractured ◄──┤──► DebrisSystem (pool, budget,
                                    (audio, particles, camera, scripts, saves)        lifetime, freeze, despawn)
```

## 4. Components

### 4.1 `DestructibleComponent` (NEW, `engine/ecs/components/DestructibleComponent.kt`, PHY; ECS registers)

| Field | Default | Notes |
|---|---|---|
| `mode` | `SHATTER` | `REMOVE` (just disappears, with effects), `SWAP` (replaced by `brokenPrefab`), `SHATTER` (pieces from `fracture`) |
| `integrity` | 100 | Own hit points, used when the object has no `HealthComponent` (§5.3) |
| `impactThreshold` | 150 N·s | Impacts below this do nothing (a crate dropped from knee height survives) |
| `impactDamagePerNs` | 0.5 | Damage per N·s above the threshold. A 70 kg skater at 6 m/s (≈ 420 N·s) breaks a default crate; a 20 kg crate thrown at 5 m/s (100 N·s) does not |
| `fracture` | "" | `.skfracture` asset for `SHATTER` (§6) |
| `brokenPrefab` | "" | Prefab GUID or path for `SWAP` |
| `breakImpulse` | 2 N·s/kg | Outward push of the pieces from the break point |
| `surface` | `"wood"` | Key for sounds and dust (§8); free string like GPL-24 damage types |
| `debrisCollidesWithCharacters` | false | Small pieces don't trip the skater (§7.3) |
| `broken` | false | Saved and replicated: the prop is already broken (§9, §10) |

Plain data, like `HealthComponent`. All behaviour is in systems.

### 4.2 Runtime state (not serialized)

`DestructionRuntime` holds the queued break and the impact accumulated this step, so several contacts in one step
count once (the largest).

## 5. Damage and breaking

### 5.1 Damage sources

1. **Impacts:** `DestructionSystem` subscribes to `Collision`. For a destructible `self`, damage is
   `max(0, J − impactThreshold) × impactDamagePerNs`, where `J` is the normalised impact impulse (§5.2). Both sides
   of a contact can be destructible.
2. **Weapons:** a `HitEvent` (GPL-24) whose target is a destructible (§5.3).
3. **Explosions:** `DestructionService.explode(position, radius, damage, impulse, type)` (NEW, pure apart from the
   query) uses `ScenePhysicsQueries.overlap` (PHY-22) with a sphere, applies damage falling off linearly with distance
   (as GPL-24's area attacks) and pushes every dynamic body hit. GPL-24c's area attack can call it.
4. **Scripts and gameplay:** `DestructionService.damage(target, amount, point, type)` and `breakNow(target, point)`.

### 5.2 One impact measure on every backend

Today `Collision.impulse` differs between backends (§1), so a crate would break on Jolt and survive on Bullet. Slice
29a defines it as **the closing speed along the normal at contact start, times the pair's reduced mass**
(`m₁m₂/(m₁+m₂)`, the lighter-static case gives `m`), computed from the velocities **before** the step. Each backend
already sees those velocities when it detects the contact (Jolt in the callback; Bullet and Simple from the bodies'
velocities saved before `step`). A contract test checks that the same drop gives the same impulse within 10 % on all
three backends. Existing users (`FollowCameraSystem` shake) keep working; their thresholds get a one-time check.

### 5.3 Integrity or health

- **No `HealthComponent`:** damage lowers `DestructibleComponent.integrity`; at 0 the prop breaks.
- **With a `HealthComponent`** (GPL-24b): health is the only source of truth. Impact and explosion damage are
  published as `HitEvent(attacker = other body's object or null, type = "impact" | "explosion")`, `HealthSystem`
  applies resistances, and `DestructionSystem` breaks the prop on its `DiedEvent`. Weapons, armour-like resistances
  (`resist.fire` on a wooden door) and scripts then work the same for characters and props.
- Until GPL-24b lands, slice 29a ships the first path only; the bridge is slice 29g.

### 5.4 Breaking

- Breaks are queued and run at most **4 per fixed step** (the rest wait a step), so a chain reaction never spikes one
  frame. A queued prop can't break twice.
- **REMOVE:** effects, then the object is destroyed (scatter: `DESTROYED` record, §9).
- **SWAP:** the broken prefab is instantiated at the same transform with the body's velocities, the original
  destroyed.
- **SHATTER:** the original's body is removed and its render hidden; `DebrisSystem` spawns the pieces of its
  `.skfracture` at their places in the prop's frame, with the prop's linear and angular velocity plus `breakImpulse`
  pointing away from the break point. Pieces marked `anchored` (a fence post's stump, a statue's base) stay static and
  become the prop's "remains" (§9).
- `broken = true` is set on the original before it goes, for saves and replication.

## 6. Fracture data

### 6.1 `.skfracture` (NEW asset, AST reviews)

A sidecar of a model, JSON, versioned:
- `model`: GUID of the fractured model (one mesh part per piece);
- per piece: `meshPart` name, `hull` (convex hull points, ≤ 64), `center`, `volume`, `anchored` flag;
- `interiorMaterial`: material slot used for the cut faces.

Mass per piece is the prop's mass times its volume share. Hulls are precomputed, so a break never runs a hull build.

### 6.2 Two ways to make one

1. **Import pre-fractured models (first):** a model whose mesh parts are the pieces, e.g. exported from Blender's
   Cell Fracture add-on. The editor builds the `.skfracture` from it (hull and volume per part). Needs AST to keep mesh
   part names on import (GPL-24's AST-xx task already asks for it).
2. **Editor fracture generator (later slice):** Voronoi fracture of a closed model into N pieces (3–32), seeded, with
   capped cut faces; writes the pieces as a new model file plus its `.skfracture`. Runs offline in the editor, never in
   the game.

### 6.3 Rendering the pieces

Each piece is a GameObject with a `RenderComponent` showing **one mesh part** of the fractured model. That is REN-32's
per-part visibility (GPL-24); until it lands, the generator and import can write one small model per piece instead
(more files, same result). Debris fade-out needs a per-object fade or dissolve, which the renderer doesn't have:
debris shrinks to zero over 0.4 s before despawning instead (no REN dependency); a dissolve is an optional REN task.

### 6.4 Runtime fracture (not planned)

Cutting meshes at runtime (Voronoi around the impact point) needs runtime mesh building and upload, hull building on
a worker, and gives different pieces on every client. Pre-fractured pieces look as good for props, cost nothing at
break time and are deterministic. §16 Q1 asks to confirm leaving it out.

## 7. Debris

### 7.1 `DebrisSystem` (NEW, `engine/ecs/systems/DebrisSystem.kt`, PHY)

- **Pool:** piece GameObjects (Transform, RenderComponent, RigidBody3D, ConvexHull collider) are pooled per
  `.skfracture` piece and reused; a pooled piece is disabled and its body removed from the world.
- **Budget:** at most `maxActiveDebris` live pieces (Quality setting: Low 96, Medium 192, High 256). Past it, the
  oldest settled pieces go first, then the smallest.
- **Lifetime:** a piece lives `debrisLifetime` (10 s), then shrinks and is returned to the pool. A piece that rested
  for 1 s becomes kinematic (frozen, no solver cost) until it expires; touching it again doesn't wake it.
- **Distance:** pieces beyond 60 m from the active camera are returned at once.
- **Spawn cost:** at most 32 pieces per break and 64 per fixed step; the rest of a break spawns next step.

### 7.2 Persistent pieces

A `.skfracture` piece can be marked `persistent` (a door that falls off and must stay, a key piece of a puzzle). It
isn't pooled or expired: it becomes a normal scene object, saved and replicated like any prop (§9, §10).

### 7.3 Layers

Debris uses a `Debris` collision layer (NEW constant): it collides with the world and other props, not with other
debris (no pile jitter), and with characters only when `debrisCollidesWithCharacters` is on or for anchored and
persistent pieces.

## 8. Effects and hooks

New events in `engine/events/PhysicsEvents.kt`:
- `DestructibleDamaged(target, amount, remaining, point, type)`;
- `Fractured(target, point, impulse, surface, mode, pieces: Int)`.

Users, each in its own domain:
- **AUD:** break sounds per `surface` (wood, glass, stone, metal), random per break, like `WaterAudio`'s splashes; a
  small crack sound on `DestructibleDamaged` above a threshold.
- **REN/GPL:** a dust or splinter burst per `surface` at the break point (`ParticleEmitter` burst from a small
  per-surface prefab).
- **GPL:** camera shake from `Fractured.impulse`; score or loot drops.
- **SCR:** scripts get both events through the existing event bridge.

## 9. Saves and streaming (with REN-37)

- **Hand-placed props:** `broken` (and `integrity`) are serialized fields, so the scene-state section of the save
  container (REN-37 task 25, AST) keeps them. On load, a broken `SHATTER` prop spawns only its anchored remains,
  a broken `SWAP` prop its broken prefab, a broken `REMOVE` prop nothing.
- **Scatter props:** the break calls the `ScatterItem` destroy hook (REN-37 task 24): `REMOVE` and `SHATTER` without
  remains write `DESTROYED`; `SWAP`, remains, or a damaged but unbroken prop write `CHANGED` with the component
  override (`broken`, `integrity`). Instanced scatter (rocks with colliders, PHY-27) can be destroyed through
  `TerrainScatter.remove(key)` (REN-28 §15.10.2).
- **Debris is never saved**, except persistent pieces (§7.2). After a load, the remains are there and the pieces are
  gone, as if they had expired.
- Until REN-37 exists, hand-placed props already work through the scene state; scatter props stay broken for the
  session only (today's `TerrainScatterEntitySystem` behaviour).

## 10. Network (with skate-online)

- **Authority:** only the server applies damage and decides breaks (NET-01: physics isn't deterministic across
  machines). Clients never break props on their own; a predicted local skater hitting a crate sees it break when the
  server's event arrives (≈ one round trip).
- **Replicated state:** `DestructibleComponent` implements `NetReplicated` with `integrity` (quantized) and `broken`.
  Breaks travel as a reliable `FracturedMessage(netId, point, impulse, seed)`.
- **Debris is cosmetic and local:** each client spawns the pieces from the message with the same seed and initial
  velocities, then simulates them itself. Pieces end up in slightly different places per client, which is fine for
  short-lived debris. Persistent pieces (§7.2) are server-spawned networked objects.
- **Late join:** `broken` in the snapshot spawns the remains, never debris.
- **Lag compensation:** weapon hits on props use the same `rewound` window as characters (NET-03).

## 11. Editor (PHY writes the UI, EDT reviews)

- Destructible inspector: mode, thresholds, fracture and broken-prefab pickers, surface, a "Break now" preview button
  that previews the break in edit mode (undone on release; no scene change), and a debris count readout.
- `.skfracture` file inspector: piece list, hull wireframes, anchored and persistent toggles.
- Fracture generator dialog (slice 29h): piece count, seed, interior material, preview.
- Physics tuner: live counters (active debris, breaks per step).

## 12. Performance budgets

| Item | Budget |
|---|---|
| Breaks per fixed step | 4 (queued beyond) |
| Pieces spawned per fixed step | 64 |
| Active debris (High) | 256, ≈ 0.5–1 ms per step with most frozen (PHY-17 box pile: 1000 bodies ≈ 0.5–0.9 ms mean) |
| Break cost on the main thread | < 0.3 ms for 32 pooled pieces (no hull building, no allocation once pools are warm) |
| Memory | a `.skfracture` of 32 pieces ≈ 30 KB; pools grow to the live peak and shrink after 30 s idle |

## 13. Tests (no GL)

- `DestructionMath`: impact damage below and above the threshold; explosion falloff; piece masses add up to the prop.
- Impact parity: the same drop gives the same normalised impulse within 10 % on Bullet, Jolt and Simple.
- `DestructionSystem` with a real `EventSystem`: integrity reaches 0 → one `Fractured`; a queued prop never breaks
  twice; at most 4 breaks per step; `HealthComponent` path breaks on `DiedEvent` only.
- `SHATTER`: pieces spawn at their places with inherited and outward velocities; anchored pieces are static.
- `DebrisSystem`: budget eviction order, freeze after rest, expiry returns to the pool, distance cull, no allocation
  after warm-up.
- Save round-trip: broken hand-placed props of each mode reload as their remains; scatter records (with REN-37).
- Network: a server break reaches a loopback client as `broken` plus one local debris spawn (with NET).

## 14. Slices

| Slice | Owner | Content | Deps |
|---|---|---|---|
| **29a** | PHY | Normalised `Collision.impulse` (§5.2) with the parity test; `DestructibleComponent`, `DestructionSystem` (impacts, own integrity, break queue), `DestructionService.damage/breakNow`, `REMOVE` and `SWAP`, events | — |
| **29b** | AST + PHY | `.skfracture` asset (AST: format, registry, packing); import from a pre-fractured model (PHY: hulls, volumes) | AST keeps mesh part names (GPL-24's AST-xx) |
| **29c** | PHY | `SHATTER` and `DebrisSystem` (pool, budget, lifetime, freeze, distance, `Debris` layer); shrink-out | 29a, 29b; REN-32 (2) or one model per piece |
| **29d** | PHY | Explosions through `overlap` (`DestructionService.explode`) | 29a, PHY-22 (done) |
| **29e** | AUD, REN | Break and crack sounds per surface (AUD); dust bursts per surface (REN or GPL) | 29a |
| **29f** | PHY (EDT reviews) | Destructible and `.skfracture` inspectors, "Break now" preview | 29a–29c |
| **29g** | PHY + GPL | `HealthComponent` bridge (`HitEvent`/`DiedEvent`) | 29a, GPL-24b |
| **29h** | PHY (EDT reviews) | Editor Voronoi fracture generator | 29b |
| **29i** | PHY + REN + AST | Saves: remains on load; scatter `DESTROYED`/`CHANGED` through the `ScatterItem` hook | 29c, REN-37 tasks 24–25 |
| **29j** | NET + PHY | `NetReplicated` destructible state, `FracturedMessage`, local cosmetic debris | 29c, NET-02 |
| **29k** | TLG | Feature Showcase "Destruction" corner: crates of three strengths, a glass pane, a fence, an explosive barrel | 29a–29e |
| (optional) | REN | Per-object dissolve for debris fade-out | 29c |

29a, 29b and 29d can start right after approval; 29a alone already gives breakable crates (`SWAP`/`REMOVE`).

## 15. Risks

- **Impulse parity (29a)** changes a value other systems read. Mitigation: the parity test, and a check of
  `FollowCameraSystem`'s shake threshold in the same slice.
- **Chain reactions** (a broken crate's pieces breaking the next crate): pieces deal impact damage like any body, but
  the break queue bounds the cost per step; a `.skfracture` piece can't itself be destructible.
- **Jolt keeps islands awake** (PHY-17 benchmark): frozen (kinematic) debris avoids that cost.
- **Multiplayer:** clients see debris in different places; persistent pieces are the escape hatch for gameplay.
- **Content:** pre-fractured models need authoring (Blender) until the generator (29h) exists.

## 16. Open questions for the human

1. **Runtime fracture:** leave it out and use pre-fractured pieces only (imported or generated in the editor)?
   *Recommended:* yes (§6.4).
2. **Fracture source first:** import pre-fractured models (Blender Cell Fracture) first and the editor generator
   later? *Recommended:* yes; the generator is slice 29h.
3. **Debris persistence:** debris is cosmetic, expires and is never saved; after a load only the remains are there.
   *Recommended:* yes, with `persistent` pieces for the rare gameplay piece.
4. **Multiplayer debris:** each client simulates its own debris, so pieces differ slightly between players. OK?
   *Recommended:* yes.
5. **Budgets:** 256 active pieces on High (96 Low), 10 s lifetime, 4 breaks per step. OK?
6. **Characters break props:** the skater riding into a crate at speed breaks it (impact damage from any body).
   *Recommended:* yes, tuned per prop with `impactThreshold`.
7. **Structural destruction** (walls and buildings breaking piece by piece with support): out of scope for now?
   *Recommended:* yes, a later design if a game needs it.
8. **Health:** props with a `HealthComponent` use GPL-24's health and resistances; props without one use their own
   `integrity`. OK? *Recommended:* yes (§5.3).
