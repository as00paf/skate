package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.BoneAttachment
import com.pafoid.skate.engine.ecs.components.EquipmentComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.ItemEquipped
import com.pafoid.skate.engine.events.ItemUnequipped
import com.pafoid.skate.engine.gameplay.appearance.SkeletonConfigs
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.gameplay.items.ItemStack
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import java.util.logging.Logger

/**
 * Shows what characters wear (GPL-24 §6.5, rigid items for now). On [ItemEquipped] it adds a child `Equip:<slot>`
 * with the item's model and a [BoneAttachment] on the slot's socket; on [ItemUnequipped] it removes that child.
 * After each scene init (on the first update) it rebuilds every character's children from its [EquipmentComponent],
 * replacing `Equip:*` children that a save may have kept. Skinned clothes, hidden body parts and material overrides come with GPL-24f.
 *
 * [loadModel] loads a model by path (the engine's assets; a fake in tests); a model that fails to load shows nothing.
 */
class AppearanceSystem(
    events: EventSystem,
    private val items: ItemDatabase,
    private val loadModel: (String) -> `3dModel`?,
) : System() {
    private val logger = Logger.getLogger("AppearanceSystem")

    init {
        events.subscribe<ItemEquipped> { show(it.owner, it.slotId, it.stack) }
        events.subscribe<ItemUnequipped> { hide(it.owner, it.slotId) }
    }

    private var rebuildPending = false

    override fun init(scene: Scene) {
        super.init(scene)
        SkeletonConfigs.clear()
        // On the first update: the item set may be reloaded by another system's init (InventorySystem)
        rebuildPending = true
    }

    override fun update(dt: Float) {
        if (!rebuildPending) return
        rebuildPending = false
        rebuild(scene)
    }

    /** Recreates the `Equip:*` children of every character under [root] from what it wears. */
    fun rebuild(root: GameObject) {
        val characters = mutableListOf<GameObject>()
        fun walk(go: GameObject) {
            if (go.getComponent<EquipmentComponent>() != null) characters += go
            go.children.toList().forEach(::walk)
        }
        walk(root)
        characters.forEach { character ->
            character.children.filter { it.name.startsWith(PREFIX) }.forEach { remove(character, it) }
            character.getComponent<EquipmentComponent>()?.equipped?.forEach { (slot, stack) -> show(character, slot, stack) }
        }
    }

    private fun show(owner: GameObject, slotId: String, stack: ItemStack) {
        hide(owner, slotId)
        val equip = items[stack.itemId]?.equip ?: return
        if (equip.model.isBlank()) return
        if (equip.skinned) return logger.warning("Skinned item '${stack.itemId}' is not shown yet (GPL-24f)")
        val socket = SkeletonConfigs.resolveFor(owner)?.socketOf(slotId)?.name
            ?: SkeletonConfigs.configOf(owner).slot(slotId)?.socket
            ?: return logger.warning("Slot '$slotId' has no socket for rigid item '${stack.itemId}'")
        val model = runCatching { loadModel(equip.model) }.getOrNull()
            ?: return logger.warning("Item '${stack.itemId}': model '${equip.model}' could not be loaded")
        val child = GameObject(PREFIX + slotId)
            .addComponent(Transform())
            .addComponent(RenderComponent(model))
            .addComponent(BoneAttachment(socket, Vector3f(equip.offset), Vector3f(equip.rotationDeg), Vector3f(equip.scale)))
        owner.addChild(child)
    }

    private fun hide(owner: GameObject, slotId: String) {
        owner.children.filter { it.name == PREFIX + slotId }.forEach { remove(owner, it) }
    }

    private fun remove(owner: GameObject, child: GameObject) {
        owner.removeChild(child)
        child.destroy()
    }

    companion object {
        /** Name prefix of the runtime children that show equipped items. */
        const val PREFIX = "Equip:"
    }
}
