# GPL-24 — Game systems and character customization (design note)

Status: **proposal, awaiting human approval**. No code has been written.

## Goal

Design reusable game systems for inventory, combat, interaction/pickup, and character customization (clothes, tattoos, headwear, eyewear via attachment points, mesh swaps and material layers). These systems are engine-agnostic gameplay abstractions that can be used by any project — not just skate games — while providing skate-specific defaults where applicable.

## Decision summary

| Topic | Decision |
|---|---|
| System architecture | ECS-component-based: each system is a `System` in the engine's ECS (ECS-14). Components hold data; systems handle logic. |
| Inventory | Generic item database + inventory slots with stack counts, durability and equip/unequip actions. |
| Combat | Hit detection via physics queries (PHY-17), damage calculation from configurable stats, health component. |
| Interaction/pickup | Proximity-based trigger zones; raycast for point-and-click interaction; action binding per-item. |
| Character customization | Attachment-point system on the skeleton: each attachment point defines a transform and target bone; mesh swaps replace the child mesh at that point; material layers allow tinting/texture overrides without duplicating meshes. |

## 1. Inventory system

### 1.1 Data model

```kotlin
// Item definition (loaded from JSON or protobuf)
data class ItemDef(
    val id: String,              // unique identifier
    val name: String,            // display name
    val description: String,     // tooltip text
    val iconPath: Path?,         // optional icon texture path
    val stackable: Boolean = false,
    val maxStack: Int = 1,
    val durability: Int? = null, // null = infinite
    val category: ItemCategory   // WEAPON, ARMOR, CONSUMABLE, MATERIAL, etc.
)

// Inventory slot
data class InventorySlot(
    var itemDefId: String?,      // null = empty
    var count: Int = 0,
    var durabilityLeft: Int? = null
)

// Player inventory
class Inventory {
    val slots: Array<InventorySlot>  // fixed-size array (e.g., 36 slots)
    fun addItem(itemDefId: String, count: Int): Boolean
    fun removeItem(slotIndex: Int, count: Int): ItemDef?
    fun equip(slotIndex: Int): Boolean
    fun unequip(slotIndex: Int): Boolean
}
```

### 1.2 ECS component

```kotlin
class InventoryComponent : Component {
    var inventory: Inventory = Inventory(36)
    var equippedSlots: Map<EquipSlot, Int> = emptyMap()
        // EquipSlot: HEAD, TORSO, ARMS, LEGS, FEET, HANDS, NECK, EYES
}

enum class EquipSlot { HEAD, TORSO, ARMS, LEGS, FEETS, HANDS, NECK, EYES }
```

### 1.3 System: InventorySystem

Handles item add/remove/equip logic, stack merging, durability updates and event publishing (e.g., `ItemEquippedEvent`).

```kotlin
class InventorySystem : System() {
    override fun update(dt: Float) {
        // Process queued inventory actions
        for (action in actionQueue) {
            when (action) {
                is AddItemAction -> handleAddItem(action)
                is RemoveItemAction -> handleRemoveItem(action)
                is EquipAction -> handleEquip(action)
                is UnequipAction -> handleUnequip(action)
            }
        }
    }

    private fun handleAddItem(action: AddItemAction) {
        val inv = getInventory(action.entityId)
        // Try to stack first
        if (inv.addItem(action.itemDefId, action.count)) {
            publish(ItemAddedEvent(action.entityId, action.itemDefId, action.count))
        }
    }
}
```

### 1.4 Test plan for inventory

1. **Stacking**: add two stacks of the same stackable item; verify they merge into one slot with combined count.
2. **Equip/unequip**: equip an armor item to HEAD slot; verify `equippedSlots` is updated and a visual swap occurs on the character mesh.
3. **Durability**: use a consumable item repeatedly; verify durability decreases and the item is removed when it reaches zero.

## 2. Combat system

### 2.1 Data model

```kotlin
// Damage type (elemental, physical, etc.)
enum class DamageType { PHYSICAL, FIRE, ICE, ELECTRIC, MAGIC }

// Hit result
data class HitResult(
    val targetId: GameObjectId,
    val damage: Float,
    val damageType: DamageType,
    val hitPoint: Vec3,
    val hitNormal: Vec3,
    val wasBlocked: Boolean = false
)

// Health component
class HealthComponent : Component {
    var currentHealth: Float = 100f
    var maxHealth: Float = 100f
    var isDead: Boolean = false

    fun takeDamage(amount: Float, type: DamageType): HitResult? {
        val actualDamage = applyResistance(type, amount)
        currentHealth -= actualDamage
        if (currentHealth <= 0f) {
            currentHealth = 0f
            isDead = true
        }
        return HitResult(/* ... */)
    }

    private fun applyResistance(type: DamageType, baseDamage: Float): Float {
        // Look up resistance from equipped armor's stats
        val resistance = getArmorResistance(type)
        return baseDamage * (1f - resistance)
    }
}
```

### 2.2 Hit detection

Combat uses physics queries (PHY-17) for hit detection:

1. **Melee attack**: sweep a sphere/capsule along the attack arc; query all overlapping bodies; apply damage to each with a `DamageableComponent`.
2. **Ranged attack**: raycast from weapon tip to target direction; first hit body takes damage.
3. **Area of effect**: sphere/box shape query at the attack location; all bodies within range take damage (with falloff based on distance).

```kotlin
class CombatSystem : System() {
    private val physics: IPhysics3D = Physics3DFactory.instance

    fun performMeleeAttack(attackerId: GameObjectId, origin: Vec3, direction: Vec3, radius: Float) {
        // Sweep sphere along attack arc
        val hits = physics.shapeQuery(
            shape = Sphere(origin, radius),
            filter = CollisionFilter(attackerId)  // ignore attacker's own colliders
        )

        for (hit in hits) {
            val target = getEntity(hit.bodyId)
            val health = target.getComponent<HealthComponent>()
            if (health != null && !health.isDead) {
                val result = health.takeDamage(attackDamage, attackType)
                publish(HitEvent(result))
            }
        }
    }
}
```

### 2.3 Test plan for combat

1. **Melee hit detection**: swing a weapon at a target; verify the hit point and damage match expected values.
2. **Damage resistance**: equip armor with known fire resistance; apply fire damage; verify reduced damage output.
3. **Death event**: reduce health to zero; verify `isDead` is true and a death event is published.

## 3. Interaction / pickup system

### 3.1 Data model

```kotlin
// Interactive object component
class InteractiveComponent : Component {
    var actionId: String              // e.g., "pickup", "open", "use"
    var proximityRadius: Float = 2f   // trigger zone radius
    var label: String                 // displayed prompt (e.g., "Press E to pickup")
    var requiresItem: String? = null  // optional item requirement
}

// Pickupable item component
class PickupableComponent : Component {
    var itemDefId: String
    var autoPickup: Boolean = false   // if true, pickup on proximity (no prompt)
}
```

### 3.2 System: InteractionSystem

Handles proximity detection and input binding for interactions:

1. **Proximity check**: each tick, query all entities with `InteractiveComponent`; for each, check if the player is within range using a sphere shape query or distance calculation.
2. **Input binding**: when the player is in range of an interactive object, bind the configured action key (default: E) to call the associated action.
3. **Action execution**: on input, execute the action (pickup, open door, use item). For pickups, add the item to the player's inventory and destroy the pickupable entity.

```kotlin
class InteractionSystem : System() {
    private var currentInteractive: GameObjectId? = null
    private var promptText: String? = null

    override fun update(dt: Float) {
        // Find closest interactive object within range
        val playerPos = getPlayerPosition()
        var closestDist = Float.MAX_VALUE
        var closestEntity: GameObjectId? = null

        for (entity in query<InteractiveComponent>()) {
            val comp = entity.getComponent<InteractiveComponent>()
            val dist = playerPos.distanceTo(entity.position)
            if (dist < comp.proximityRadius && dist < closestDist) {
                closestDist = dist
                closestEntity = entity.id
            }
        }

        // Update prompt
        if (closestEntity != null) {
            currentInteractive = closestEntity
            val comp = closestEntity!!.getComponent<InteractiveComponent>()
            promptText = "${comp.label} (${inputBinding.getActionName("interact")})"
        } else {
            currentInteractive = null
            promptText = null
        }

        // Handle input
        if (currentInteractive != null && input.isActionJustPressed("interact")) {
            executeInteraction(currentInteractive!!)
        }
    }

    private fun executeInteraction(entityId: GameObjectId) {
        val comp = entityId.getComponent<InteractiveComponent>()
        when (comp.actionId) {
            "pickup" -> pickupItem(entityId)
            "open" -> openDoor(entityId)
            "use" -> useItem(entityId)
        }
    }
}
```

### 3.3 Test plan for interaction

1. **Proximity prompt**: place an interactive object within range; verify the prompt appears. Move out of range; verify it disappears.
2. **Pickup action**: press the interact key while in range; verify the item is added to inventory and the pickupable entity is destroyed.
3. **Item requirement**: create an interactive object that requires a specific item; verify the prompt only appears when the player has the required item.

## 4. Character customization system

### 4.1 Attachment points

Each character skeleton defines attachment points in its animation clip or a separate config file:

```kotlin
data class AttachmentPoint(
    val name: String,                // e.g., "head", "leftHand", "eyes"
    val targetBone: String,          // bone index or name to attach to
    val localOffset: Vec3 = Vec3.Zero,
    localRotation: Quat = Quat.Identity,
    var attachedMesh: MeshHandle? = null,  // currently equipped mesh
    var materialOverrides: Map<String, TextureHandle> = emptyMap()  // tint/texture overrides
)
```

### 4.2 Mesh swap

When an item is equipped (e.g., a hat), the system:

1. Looks up the attachment point for that slot (e.g., `HEAD` → `"head"`).
2. Loads the mesh from the item's asset path.
3. Replaces the child mesh at the target bone with the new mesh.
4. Applies any material overrides (tint, texture swap) from the item definition.

```kotlin
class CharacterCustomizationSystem : System() {
    fun equipItem(characterId: GameObjectId, slot: EquipSlot, itemDefId: String) {
        val character = getCharacter(characterId)
        val attachment = character.getAttachmentPoint(slot) ?: return
        val meshHandle = assetManager.loadMesh(itemDefId)

        // Swap the mesh at the attachment point's target bone
        character.swapMesh(attachment.targetBone, meshHandle)

        // Apply material overrides
        for ((matName, texHandle) in itemDef.materialOverrides) {
            character.overrideMaterial(meshHandle, matName, texHandle)
        }

        // Store the equipped mesh reference
        attachment.attachedMesh = meshHandle
    }

    fun unequipItem(characterId: GameObjectId, slot: EquipSlot) {
        val character = getCharacter(characterId)
        val attachment = character.getAttachmentPoint(slot) ?: return

        // Restore original mesh (stored in the skeleton config)
        character.restoreMesh(attachment.targetBone, attachment.originalMesh)
        attachment.attachedMesh = null
    }
}
```

### 4.3 Material layers for tattoos and decals

Tattoos and decals are applied as material layers on existing meshes:

1. Each mesh has named material slots (e.g., `skin`, `shirt`, `pants`).
2. A tattoo item defines which material slot to overlay and a decal texture with UV coordinates.
3. The rendering system composites the decal onto the target material using a second pass (decals are rendered after the base mesh, with alpha blending).

```kotlin
// Tattoo definition
data class TattooDef(
    val id: String,
    val texturePath: Path,           // RGBA texture with transparent background
    val targetMaterial: String,      // e.g., "skin"
    val uvOffset: Vec2 = Vec2.Zero,
    val uvScale: Vec2 = Vec2.One,
    val opacity: Float = 0.8f
)

// Applied tattoo on a character
class AppliedTattoo {
    var tattooDef: TattooDef
    var meshHandle: MeshHandle       // the mesh this tattoo is applied to
    var position: Vec3               // world-space position (for body tracking)
}
```

### 4.4 Test plan for customization

1. **Mesh swap**: equip a hat to the HEAD attachment; verify the hat mesh appears on the character's head bone at the correct transform.
2. **Unequip**: unequip the hat; verify the original head mesh is restored and the hat disappears.
3. **Material override**: equip glasses with tinted lenses; verify the lens material uses the tint color from the item definition.
4. **Tattoo application**: apply a tattoo to the skin material; verify it appears on the character's arm at the correct UV position.
5. **Multiple attachments**: equip headwear, eyewear and neck accessory simultaneously; verify all appear correctly without mesh conflicts.

## 5. Cross-domain ownership

| Piece | Owner |
|---|---|
| Inventory system (ECS components + System) | GPL |
| Combat system (hit detection, damage) | GPL + PHY-17 (physics queries) |
| Interaction/pickup system | GPL + INP-10 (input binding) |
| Character customization (attachment points, mesh swaps) | GPL + ANI-12 (skeleton/animation) |
| Tattoo rendering (material layers) | REN-28 (rendering pass) |
| UI for inventory/equipment screen | GUI-xx (in-game UI) |

## 6. Risks

- **Mesh swap complexity**: swapping child meshes at runtime requires careful handling of skeleton skinning data. The mesh must share the same skeleton as the original; otherwise, bones won't align.
- **Tattoo UV mapping**: decals need correct UV coordinates on the target mesh. If the mesh has multiple UV sets, we may need to specify which set to use.
- **Performance with many attachments**: each attachment point adds a draw call (unless instanced). For characters with 10+ accessories, consider batching or GPU instancing.
- **Item database loading**: items can be defined in JSON, protobuf (AST-18) or loaded from the asset store (AST-16). Need a unified item registry that handles all sources.

## 7. Decisions

Human decisions recorded on 2026-11-07:

1. **Inventory scope**: Per-character inventory, but generic enough to support vehicles/chests too (not character-only).
2. **Combat damage application**: Use ECS events for decoupling (not direct method calls). Systems subscribe to `HitEvent` and apply damage.
3. **Attachment points location**: Defined in skeleton config — shared across all characters.
4. **Tattoo application**: Applied live at runtime (not baked into the mesh).

## 8. Open questions

_No remaining open questions._
