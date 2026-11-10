# GPL-24: Game systems and character customization (design note)

Status: **approved** by the human on 2026-11-10 (revision 2). The answers to §11 are recorded in §10.

Revision 2 (2026-11-10) replaces the first draft, which the lead rejected because it was not grounded in this
codebase. Changes:
- Fixed the task references. ECS-14 is the seeded RNG service and PHY-17 is the Jolt backend.
- Every class below has been read in the code. Paths are relative to `src/main/kotlin/com/pafoid/skate/`, and new
  files are marked **(new)**.
- Added the slicing into tasks with owners (§8).
- Kept the human decisions of 2026-11-07 unchanged (§10).

## Goal

Reusable inventory, combat, interaction/pickup and character-customization systems for any 3D game. Customization
covers clothes, tattoos, headwear and eyewear, through attachment points, mesh swaps and material layers. The engine
part goes in `engine/`. Skate-specific content (items, the skater's outfit, the HUD) stays in `game/`, as the domain
rules and `EngineGameLayeringGuardTest` require.

## 1. What the code gives us today

| Need | Existing code | Notes / gap |
|---|---|---|
| Components | `engine/ecs/components/Component.kt`: `@Serializable @Polymorphic abstract class Component`, with `init(gameObject)`, `reset()` and `destroy()` | A new component also needs a `subclass(...)` line in `engine/assets/serialization/Serializer.kt`, enforced by `ComponentRegistrationGuardTest`, plus a `ComponentType` entry and a branch in `Component.type`. GPL-22 added `CameraCycleComponent` this way (commit `13ffcd6c`). |
| Objects | `engine/ecs/GameObject.kt`: `components`, `children`, `parent`, `tag`, `layer` (32 layers), `uId`, plus a stable `guid` on `integration/30g` | Child objects inherit the world transform through `TransformSystem` (`LATE_UPDATE`, `parentTransform.worldMatrix.mul(localMatrix)`). |
| Systems | `engine/ecs/systems/System.kt`: `phase` (`SystemPhase`: PRE_UPDATE, FIXED_UPDATE, UPDATE, LATE_UPDATE, RENDER), `runsAfter`/`runsBefore`, cached `query(vararg KClass)`, `runsWhilePaused`. Ordered by `SystemOrdering.resolve` and registered by `SystemManager.addSystem` | Engine systems are listed in `engine/core/Engine.kt` (`engineSystems`). Game systems are added in `game/player/SkateBehavior.kt` (`SkateGameplay.install`), the same way as `SkateHudSystem`. |
| Events | `engine/core/EventSystem.kt`: synchronous, class-keyed `subscribe<T>`/`publish`, one instance (`engine.eventSystem`). Event types live in `engine/events/*.kt` and extend `Event(eventName)` (e.g. `PhysicsEvents.kt`, `TrickEvents.kt`) | `unsubscribe<T>()` removes **every** listener of `T`, not just one. A system that unsubscribes on `destroy()` would silently unsubscribe the others (§9, NEEDS ECS). |
| Physics queries | `engine/physics3d/IPhysics3D.kt`: `raycast`, `raycastAll`, `shapeCast(ColliderShape, from, to, layerMask, excludeBody)` → `PhysicsHit(gameObject, point, normal, fraction)` (`PhysicsQueries.kt`). Triggers: `RigidBody3D.isTrigger` → `TriggerEntered`/`TriggerExited` events from `PhysicsSystem` | No **overlap** query (all bodies inside a shape). `PhysicsSystem` keeps `physics3d` private and exposes only `sphereCast` (used by `FollowCameraSystem`). |
| Skeleton | `assets/data/models/animations/Skeleton.kt` (`getBoneByName`), `SkeletonPose.globalTransforms` (model space), `ecs/components/SkeletonComponent.kt` (`matrixPalette`, `MAX_BONES = 100`) | There is no skeleton config asset and no socket/attachment concept. `animation/retarget/HumanoidBone.kt` maps rig-specific names to `HEAD`, `NECK`, `LEFT_HAND`, … (`HumanoidBone.fromName`). |
| Rendering of a model | `RenderComponent(model, material, materialPath, …)`. `GeometryPass`, `ShadowRenderer` and `PickingPass` call `go.getComponent<SkeletonComponent>()` **on the drawn object only** | A skinned mesh on a child object (a shirt) has no palette to skin with. `RenderComponent.material` replaces the material of **every** part. `MeshPart` and `Material` have **no names**, so "the `skin` material" cannot be addressed. |
| Input | `engine/gameplay/ActionQuery.kt`, `InputContextLeases.kt`, `ActionPresets.register(id) { ActionMap -> }` (INP-10 API) | An `interact` action is registered like the skate actions are in `SkateGameplay.register`. |
| Skater prefab | `game/prefabs/Skater.kt`: Transform, RenderComponent(james), Animator, SkeletonComponent, RigidBody3D, BoxCollider3D, InputStateComponent, PlayerController, BoneOverride. Spawned by `engine/assets/PrefabsGenerator.spawnSkater` | The new components are added here in the slices that need them. |
| Data configs | `engine/gameplay/CharacterConfigs.kt` loads `*.character.json` (project file first, then the bundled resource; lenient JSON) | We reuse the same loader pattern for item and skeleton configs. |
| RNG | ECS-14: `engine/ecs/random/RandomService.kt` + `System.random` (named stream per system), on `integration/30g` | Combat in this note is deterministic. If loot or crits are added later, they draw from `System.random`. |
| Script bridge | `engine/scripting/api/ScriptApi.kt`: `on(name)`/`emit(name, payload)` | Optional bridge of the new events to scripts (§8, SCR). |

## 2. Shared shape of the systems

- **Data components** in `engine/ecs/components/` (as GPL-22 did). **Logic** that can be tested without a window
  goes in pure classes under **(new)** `engine/gameplay/items/`, `engine/gameplay/combat/`,
  `engine/gameplay/interaction/` and `engine/gameplay/appearance/`. **Systems** in `engine/ecs/systems/` are thin
  wrappers that move data between the components and the pure classes.
- **Events** go in **(new)** `engine/events/GameplayEvents.kt`. They are published on `engine.eventSystem`, which
  each system receives through its constructor (as `PhysicsSystem` and `FollowCameraSystem` do). They follow human
  decision 2: no direct calls between systems.
- **Data assets** are JSON, loaded like `CharacterConfigs`. AST-18 also keeps authoring data in JSON.
- **Units** follow `docs/design/conventions.md`: metres, seconds and degrees in data.
- **Main thread only.** Removing objects (picked-up items) goes through `GameObjectManager.removeGameObject` in
  `update`, never inside a physics callback.

## 3. Inventory (human decision 1: per character, also usable by chests and vehicles)

### 3.1 Item definitions

**(new)** `engine/gameplay/items/ItemDef.kt`, `ItemDatabase.kt`:

```kotlin
@Serializable
data class ItemDef(
    val id: String,                       // stable key, e.g. "cap.red"
    val nameKey: String,                  // string-table key (StringManager), not literal text
    val iconPath: String? = null,         // project-relative texture
    val maxStack: Int = 1,                // 1 = not stackable
    val maxDurability: Int? = null,       // null = never wears out
    val tags: Set<String> = emptySet(),   // free-form categories ("headwear", "consumable", ...) instead of a fixed enum
    val equip: EquipDef? = null,          // null = cannot be equipped (§6)
    val stats: Map<String, Float> = emptyMap(), // e.g. "resist.fire" = 0.3, "damage" = 10
)
```

`ItemDatabase` loads every `*.item.json` from the project items folder and the bundled defaults. It warns about
duplicate ids, and looking up an unknown id returns `null`, never throws. Items have free-form tags instead of the
draft's `ItemCategory` enum, so a game can add categories without changing the engine.

### 3.2 Container and equipment

**(new)** `engine/gameplay/items/Inventory.kt`: a pure, fixed-capacity list of slots.

```kotlin
@Serializable data class ItemStack(val itemId: String, var count: Int, var durability: Int? = null)

class Inventory(capacity: Int) {
    val slots: List<ItemStack?>
    fun add(def: ItemDef, count: Int): Int             // returns the overflow that did not fit; fills partial stacks first
    fun remove(slot: Int, count: Int): ItemStack?
    fun move(from: Int, to: Int)                       // swaps the slots, or merges same-id stacks
    fun wear(slot: Int, amount: Int, db: ItemDatabase): Boolean  // true when the item broke and was removed
}
```

**(new)** `engine/ecs/components/InventoryComponent.kt`: `capacity` plus the serialized `slots`. It works on any
object: a character, a chest or a vehicle (decision 1).

**(new)** `engine/ecs/components/EquipmentComponent.kt`: `equipped: Map<String, ItemStack>`, keyed by **slot id**.
Only characters carry it. Slot ids come from the skeleton config (§6.1), not from an engine enum, so another game
can add a `BACK` or `TAIL` slot (see Q4).

### 3.3 InventorySystem

**(new)** `engine/ecs/systems/InventorySystem.kt`: `UPDATE` phase. Other systems and scripts ask for changes by
publishing request events: `InventoryRequest.Add/Remove/Move/Equip/Unequip(owner: GameObject, …)`. The system
applies them through `Inventory` and publishes the results: `ItemAdded`, `ItemRemoved`, `ItemEquipped`,
`ItemUnequipped` and `InventoryFull`. Because every change is an event, the appearance system (§6) and the UI react
without being called directly.

### 3.4 Tests (pure, no GL)

- Stacking: two adds of a stackable item merge into one slot; overflow goes to the next free slot; a full inventory
  returns the overflow.
- Equip/unequip: equipping moves the stack into `EquipmentComponent` and publishes `ItemEquipped`; an item without an
  equip def is refused. The draft's "visual swap" check moves to §6 tests.
- Durability: `wear` decreases durability and removes the item at zero.
- Database: duplicate ids, unknown id, lenient decode of a partial file.

## 4. Combat (human decision 2: damage goes through ECS events)

### 4.1 Data and pure math

**(new)** `engine/ecs/components/HealthComponent.kt`: `max`, `current`, `dead` and `resistances: Map<String, Float>`.
`HealthComponent` is plain data with no `takeDamage` method; damage goes through the event below.

**(new)** `engine/gameplay/combat/DamageMath.kt` (pure):
`resolve(base, type, resistances, equipmentStats) → amount`. The resistance for a damage type `t` is the sum of the
base resistance and the `resist.<t>` stats of the equipped items, clamped to `[0, 0.9]`. Damage types are strings
(`"physical"`, `"fire"`, …), not an engine enum.

### 4.2 Events

In `engine/events/GameplayEvents.kt` **(new)**:

```kotlin
data class HitEvent(val attacker: GameObject?, val target: GameObject, val amount: Float, val type: String,
                    val point: Vector3f, val normal: Vector3f) : GameplayEvent("gameplay.hit")
data class DamagedEvent(val target: GameObject, val applied: Float, val remaining: Float) : GameplayEvent("gameplay.damaged")
data class DiedEvent(val target: GameObject, val killer: GameObject?) : GameplayEvent("gameplay.died")
```

### 4.3 Systems

- **(new)** `engine/ecs/systems/HealthSystem.kt` subscribes to `HitEvent`. It applies `DamageMath` to the target's
  `HealthComponent` and publishes `DamagedEvent`, then `DiedEvent` once when health reaches 0. Damage is applied
  synchronously in the subscriber, because `EventSystem.publish` is synchronous, so the order is deterministic (Q5).
- **(new)** `engine/ecs/systems/CombatSystem.kt`, `FIXED_UPDATE`, `runsAfter = PhysicsSystem`. Attacks are
  requested through `AttackRequest(attacker, kind, …)` events and resolved with physics queries:
  - **Ranged**: `raycast(from, to, layerMask, excludeBody = attacker's body)`; the first `PhysicsHit.gameObject`
    carrying a `HealthComponent` gets a `HitEvent`.
  - **Melee**: `shapeCast(ColliderShape.Capsule(radius, …), from, to, …)` along the swing, like
    `PhysicsSystem.sphereCast`. `shapeCast` returns only the first hit, so multi-target cleave waits for the overlap
    query below.
  - **Area**: needs the new **overlap** query (NEEDS PHY). Damage falls off linearly with distance.
- `CombatSystem` reaches physics through a narrow query interface that `PhysicsSystem` provides (NEEDS PHY). It is
  never handed `IPhysics3D` or `Physics3DFactory` directly, so tests can pass a fake.

### 4.4 Tests

- `DamageMath`: armour with known fire resistance reduces fire damage and leaves physical damage unchanged; the
  resistance clamp holds.
- `HealthSystem` with a real `EventSystem`: a `HitEvent` lowers health and publishes `DamagedEvent`; reaching zero
  publishes `DiedEvent` exactly once; hits on a dead target are ignored.
- `CombatSystem` with a fake query interface: a ranged hit targets the first damageable object and ignores the
  attacker; an area hit applies the falloff.

## 5. Interaction and pickup

### 5.1 Components

- **(new)** `engine/ecs/components/InteractableComponent.kt`: `actionId: String` ("pickup", "open", "use", or any
  game id), `promptKey: String` (string-table key), `radius` (metres), `requiresItem: String?` and `enabled`.
- **(new)** `engine/ecs/components/PickupComponent.kt`: `itemId`, `count` and `autoPickup` (picked up on proximity,
  with no prompt).

### 5.2 InteractionSystem

**(new)** `engine/ecs/systems/InteractionSystem.kt`, `UPDATE` phase. Interactors are objects with a
`PlayerController` and a `Transform`, the same query as `MountFollowSystem`.

1. **Focus**: the nearest enabled interactable within `radius`. When more than one is in range, the one most in
   front of the interactor (largest view dot product) wins. An interactable whose `requiresItem` is not in the
   interactor's `InventoryComponent` is not offered. Distance checks are enough at the expected object counts; there
   is no physics query here. If profiling ever shows a cost, this can move to trigger volumes (`TriggerEntered`).
   The focus logic is a pure function in **(new)** `engine/gameplay/interaction/InteractionFocus.kt`.
2. A focus change publishes `InteractionFocusChanged(interactor, target?, promptKey?)`. The HUD draws the prompt from
   this event, as `SkateHudSystem` draws trick text, so the engine has no UI code.
3. **Input**: the `interact` action is registered through `ActionPresets` (INP-10). Its default bindings are E and
   gamepad X/Square. It is read with `ActionQuery.wasPressed("interact")`. Rebinding uses the existing INP UI.
4. **Execute**: publishes `InteractEvent(interactor, target, actionId)`. A pickup becomes
   `InventoryRequest.Add`. If the item was added in full, the pickup object is removed through
   `GameObjectManager.removeGameObject`; if the inventory is full, the pickup stays and `InventoryFull` is
   published. Other actions are left to game code or scripts that subscribe to `InteractEvent`, so the engine has no
   `when(actionId)` switch.

### 5.3 Tests

- Focus: entering range gives focus and leaving range clears it; the closest and most frontal target wins.
- Item requirement: the interactable is offered only while the player holds the required item.
- Pickup: the interact press adds the item and removes the object; a full inventory keeps the object;
  `autoPickup` works without input.

## 6. Character customization (human decisions 3 and 4)

### 6.1 Skeleton config with attachment points (decision 3: in the skeleton config, shared by all characters)

There is no skeleton config asset yet. **(new)** `engine/gameplay/appearance/SkeletonConfig.kt` defines one, stored
as `*.skeleton.json`. The bundled default is **(new)** `configs/humanoid.skeleton.json`, next to the existing
`configs/skater.character.json`.

```kotlin
@Serializable data class SkeletonConfig(
    val sockets: List<Socket>,
    val slots: List<SlotDef>,            // equip slots this rig offers: HEAD, EYES, NECK, TORSO, LEGS, FEET, HANDS ...
)
@Serializable data class Socket(
    val name: String,                    // "head", "eyes", "rightHand"
    val bone: String,                    // a HumanoidBone name (HEAD) or a raw bone name
    val offset: @Contextual Vector3f = Vector3f(),        // metres, in bone space
    val rotationDeg: @Contextual Vector3f = Vector3f(),   // Euler XYZ degrees
)
@Serializable data class SlotDef(val id: String, val socket: String?)
```

**Shared by all characters**: the bone is resolved per rig at load. A `HumanoidBone` name maps through
`HumanoidBone.fromName` over the rig's bones, otherwise the raw name goes to `Skeleton.getBoneByName`. So one
`humanoid.skeleton.json` fits the james rig, Mixamo rigs and other humanoids without per-character copies. A socket
that does not resolve is reported once, and its slot is disabled for that character. The character config
(`*.character.json`) gets an optional `skeletonConfig` path, which defaults to the humanoid file.

### 6.2 Rigid attachments (hats, caps, glasses)

**(new)** `engine/ecs/components/BoneAttachment.kt`: `socket: String`. It sits on a **child** object of the
character, next to that child's own `RenderComponent`.

**(new)** `engine/ecs/systems/BoneAttachmentSystem.kt`, `LATE_UPDATE`, `runsBefore = TransformSystem`. Each frame it
sets the child's local transform to `pose.globalTransforms[bone] × socketOffset`. The pose comes from the parent's
`SkeletonComponent` and is model space (`SkeletonMath` builds skin matrices as global × inverse bind), so
`TransformSystem` then composes the result with the character's world matrix. No renderer change is needed. The
math is a pure function in **(new)** `engine/gameplay/appearance/SocketMath.kt`.

### 6.3 Skinned clothes (mesh swap)

A shirt or trousers is a skinned mesh exported on the **same skeleton** as the body. It is drawn from a child
object whose `RenderComponent` holds the clothing model, with no `SkeletonComponent` of its own. Two engine pieces
are missing:

1. **Skin against an ancestor's skeleton** (NEEDS REN, ANI). `GeometryPass`, `ShadowRenderer` and `PickingPass`
   must look up the `SkeletonComponent` on the drawn object, or failing that on its nearest ancestor, and reuse that
   palette. The Vulkan passes need the same change for parity.
2. **Joint remap by bone name** (NEEDS ANI). A clothing file's joint indices may be ordered differently from the
   body's. At equip time, a one-off pass rewrites `MeshPart.joints` from clothing bone names to body bone indices. A
   clothing bone that is not in the body skeleton fails the equip, with a logged error.

"Replace" means: the item's `EquipDef` lists `hidesParts` (mesh part names of the base body, e.g. `torso` under a
hoodie). The appearance system hides those parts while the item is worn and shows them again on unequip, which
restores the original look. This needs mesh parts to keep their names from import (NEEDS AST) and a per-part
visibility flag on `RenderComponent` (NEEDS REN).

```kotlin
@Serializable data class EquipDef(
    val slot: String,                         // a SlotDef.id from the skeleton config
    val model: String,                        // project-relative model path
    val skinned: Boolean,                     // false = rigid on the slot's socket (§6.2); true = skinned (§6.3)
    val hidesParts: List<String> = emptyList(),
    val materialOverrides: Map<String, MaterialOverride> = emptyMap(), // keyed by material name (§6.4)
)
```

### 6.4 Material overrides and layers

- **Override** (tinted lenses, shirt colour): `MaterialOverride(baseColor?, baseColorTexture?, roughness?,
  metallic?)`, applied by material name to the item model or to the body. Today `RenderComponent.material` replaces
  every part, so this needs per-material overrides keyed by material name (NEEDS REN for the render side, NEEDS AST
  for keeping material names from import).
- **Tattoos (decision 4: applied live at runtime, not baked into the mesh)**:

```kotlin
@Serializable data class TattooDef(
    val id: String,
    val texture: String,          // RGBA, transparent background
    val targetMaterial: String,   // e.g. "skin"
    val uvOffset: @Contextual Vector2f = Vector2f(), val uvScale: @Contextual Vector2f = Vector2f(1f), val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val uvSet: Int = 0,           // MeshPart.texCoords (0) or texCoords1 (1)
)
```

  Applied tattoos are a list on the appearance component. When the list changes, the renderer composites the target
  material's base colour texture with the tattoo layers, in order, into a per-character runtime texture (an
  offscreen pass). That texture becomes the override for the target material. The cost is paid once per change,
  and there is no per-frame decal pass or extra draw call. Tattoos can still be added, moved or removed at runtime,
  as decision 4 requires. Q1 covers the alternative, per-draw shader layers. The runtime composite pass is
  NEEDS REN.

### 6.5 AppearanceComponent and AppearanceSystem

**(new)** `engine/ecs/components/AppearanceComponent.kt`: `skeletonConfig` path, `tattoos: List<AppliedTattoo>`, and
`bodyOverrides: Map<String, MaterialOverride>` (skin tone, for example).

**(new)** `engine/ecs/systems/AppearanceSystem.kt` subscribes to `ItemEquipped` and `ItemUnequipped`:

- On **equip**, it spawns a child object named `Equip:<slot>` with a `RenderComponent` for the model. A rigid item
  also gets a `BoneAttachment`; a skinned item gets the joint remap. It then applies `materialOverrides` and hides
  `hidesParts`.
- On **unequip**, it removes that child and shows the hidden parts again.
- Equipping into an occupied slot unequips the old item first. Several slots at once (headwear, eyewear, neck) are
  independent children, so they cannot conflict.

The visual state is rebuilt from `EquipmentComponent` and `AppearanceComponent` on scene load. The `Equip:*`
children are runtime-only and are not serialized. Q3 covers a possible serialization flag.

### 6.6 Tests (pure unless noted)

- `SkeletonConfig`: a humanoid config resolves on the james rig and on a Mixamo-named fixture skeleton; an
  unresolved socket disables its slot.
- `SocketMath`: a known bone global matrix and offset give the expected local transform. This covers the draft's
  "hat on the head bone at the right transform".
- Equip/unequip at the ECS level, with no GL: equipping creates the `Equip:HEAD` child with a `BoneAttachment`;
  unequipping removes it and restores hidden parts; equipping headwear, eyewear and neck items together creates
  three children.
- Joint remap: reordered joint names map to body indices; a missing bone fails.
- `MaterialOverride` resolution and the tattoo layer order (a pure composite description). The GPU composite is
  covered by REN's own tests.

## 7. Skater prefab and demo

- `game/prefabs/Skater.kt` gets `InventoryComponent`, `EquipmentComponent` and `AppearanceComponent` in the slices
  that need them. `PrefabsGenerator.spawnSkater` is unchanged.
- **(new)** `game/items/` holds the skate item set: caps, beanies, glasses and a hoodie as `*.item.json` with bundled
  models. Models come from AST-16 store packs with compatible licences, or simple placeholder meshes.
- **(new)** `game/ui/`: the interaction prompt in the skate HUD, from `InteractionFocusChanged`. The inventory
  screen is GUI-owned (§8).
- Combat is **not** part of the skate demo (Q6). It is shown in the Feature Showcase (TLG).

## 8. Slicing into tasks

| Task | Owner | Content | Deps |
|---|---|---|---|
| **GPL-24a** | GPL | Items and inventory: `ItemDef`, `ItemDatabase`, `Inventory`, `InventoryComponent`, `EquipmentComponent`, `InventorySystem`, the inventory events in `GameplayEvents.kt`, registration lines (GPL-22 precedent); §3 tests | ECS-xx (listener handles) |
| **GPL-24b** | GPL | Health and damage: `HealthComponent`, `DamageMath`, `HealthSystem`, hit/damage/death events; §4.4 tests | 24a (equipment stats) |
| **PHY-xx** | PHY | Scene query interface from `PhysicsSystem` (raycast, shapeCast and a new `overlap(shape, position, rotation, layerMask): List<GameObject>` on `IPhysics3D`), implemented for the Bullet, Jolt and simple backends | — |
| **GPL-24c** | GPL | `CombatSystem` (ranged, melee and area attack requests) through the PHY interface, with a fake for tests | 24b, PHY-xx |
| **GPL-24d** | GPL | Interaction and pickup: components, `InteractionFocus`, `InteractionSystem`, the `interact` action preset, the skate HUD prompt; §5.3 tests | 24a |
| **GPL-24e** | GPL (ANI reviews) | `SkeletonConfig`, `humanoid.skeleton.json`, `BoneAttachment`, `SocketMath`, `BoneAttachmentSystem`; rigid hat and glasses on the skater | — |
| **AST-xx** | AST | Keep mesh part names and material names from Assimp import on `MeshPart`/`Material`, including the packed/exported model path | — |
| **REN-xx** | REN (VK for parity) | (1) skin a child mesh with the nearest ancestor's `SkeletonComponent` in Geometry, Shadow and Picking passes; (2) per-part visibility and per-material overrides on `RenderComponent`; (3) runtime texture composite for tattoo layers | AST-xx |
| **ANI-xx** | ANI | Joint remap of a skinned clothing mesh onto a target skeleton by bone name (pure, tested) | — |
| **GPL-24f** | GPL | `AppearanceComponent`, `AppearanceSystem`, skinned clothes and material overrides; skate item set in `game/items/`; §6.6 tests | 24a, 24e, REN-xx (1, 2), ANI-xx |
| **GPL-24g** | GPL | Tattoos: `TattooDef`, applied tattoo list, composite requests | 24f, REN-xx (3) |
| **GUI-xx** | GUI | Inventory and equipment screen (drag/move, equip, durability), gamepad navigable; driven only by the inventory request and result events | 24a |
| **SCR-xx** (optional) | SCR | Bridge the gameplay events to `ScriptApi.on/emit`, and expose inventory requests to scripts | 24a–d |
| **TLG-xx** | TLG | Feature Showcase scene: pickups, a chest, a damage target, the equip screen | 24c, 24d, GUI-xx |
| **EDT-xx** (later) | EDT | Item and skeleton-config editors (until then the JSON files are edited by hand) | 24a, 24e |

GPL-24a, 24d and 24e can start right after approval. Rigid accessories (24e) give a visible result before the
REN/AST/ANI work lands.

## 9. Risks

- **Skinned clothes must share the skeleton.** Joint remap by name handles reordering, not a different rig.
  Retargeting skin weights is out of scope.
- **Mesh part names in the james model.** If the body is a single mesh part, `hidesParts` has nothing to hide, and
  clothing can poke through. Mitigations: split the body model into parts (an asset task), or fall back to an alpha
  mask on the body (a REN follow-up). Check this in AST-xx.
- **`EventSystem.unsubscribe<T>()` clears every listener of `T`.** New systems never call it. NEEDS ECS: an
  unsubscribe handle returned by `subscribe`, so a system can drop only its own listener on `destroy()`.
- **Bone limit.** `SkeletonComponent.MAX_BONES = 100`. Clothing reuses the body palette, so it adds no bones.
- **Draw calls.** Each attachment is one more `RenderComponent`. Ten or more accessories per character across many
  characters may need instancing later (REN).
- **Item data sources.** JSON only for now. Store packs (AST-16) provide models and textures, not item definitions.
  Protobuf (AST-18) is for runtime packing only.
- **Multiplayer.** Inventory, health and equipment are not replicated yet. A NET task follows once the NET-01
  replication model is applied to gameplay components.

## 10. Decisions

Human decisions recorded on 2026-11-07:

1. **Inventory scope**: Per-character inventory, but generic enough to support vehicles/chests too (not character-only).
2. **Combat damage application**: Use ECS events for decoupling (not direct method calls). Systems subscribe to `HitEvent` and apply damage.
3. **Attachment points location**: Defined in skeleton config — shared across all characters.
4. **Tattoo application**: Applied live at runtime (not baked into the mesh).

Human answers to §11, recorded on 2026-11-10 (revision 2 approved):

1) tattoos baked into a per-character texture when the list changes
2) same skeleton required, joint remap by bone name
3) hide named body parts (hidesParts)
4) slots: a DYNAMIC list with defaults (HEAD, EYES, NECK, TORSO, LEGS, FEET, HANDS), editable per skeleton config (add/rename/remove)
5) damage applied synchronously in the HitEvent subscriber
6) no combat in the skate demo
7) items in Assets/Items/ (new type folder and Asset Browser tab), skeleton configs in Assets/Data/

Backlog tasks: GPL-27 (24a,b,d,e), GPL-28 (24c,f,g), PHY-22, AST-33, REN-32, ANI-19, GUI-18, TLG-15.

## 11. Open questions for the human (answered 2026-11-10, see §10)

1. **How tattoos are drawn at runtime.** **Recommended: composite into a per-character texture whenever the tattoo
   list changes** (§6.4). It is cheap per frame, has no limit on layers and needs no shader change for OpenGL/Vulkan
   parity. The alternative is N decal layers sampled in the material shader every frame: changes show instantly,
   but the shader is more complex and the number of layers is capped.
2. **Clothes on a different rig.** **Recommended: require the same skeleton, with joint remap by bone name**, and
   refuse items that do not match. Retargeting skin weights between rigs is a large project.
3. **Hiding the body under clothes.** **Recommended: hide named mesh parts** listed by the item (`hidesParts`). This
   depends on the body model being split into parts; if it is not, add a body alpha mask later.
4. **Equip slots.** **Recommended: string slot ids declared in the skeleton config**, with a default set (HEAD,
   EYES, NECK, TORSO, LEGS, FEET, HANDS). A game can then add slots without an engine change. The alternative is a
   fixed engine enum, as in the first draft.
5. **When damage is applied.** **Recommended: synchronously, inside the `HitEvent` subscriber** (deterministic
   because `EventSystem.publish` is synchronous). The alternative is queuing hits and applying them in
   `HealthSystem.update`, which delays them by up to one frame.
6. **Combat in the skate demo.** **Recommended: no.** Combat ships as engine systems and is shown in the Feature
   Showcase only. The skate demo (GPL-23) gets pickups and cosmetic items.
7. **Where item files live.** **Recommended: a typed `Items/` folder under `Assets/`**, alongside the AST-26 typed
   folders (`Models`, `Textures`, …), with `*.item.json` files. Skeleton configs go in a `Configs/` folder, or in
   whichever folder AST-26 assigns to configs.
