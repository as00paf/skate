package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InteractableComponent
import com.pafoid.skate.engine.ecs.components.InventoryComponent
import com.pafoid.skate.engine.ecs.components.PickupComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.events.InteractEvent
import com.pafoid.skate.engine.events.InteractionFocusChanged
import com.pafoid.skate.engine.events.InventoryFull
import com.pafoid.skate.engine.gameplay.items.ItemDatabase
import com.pafoid.skate.engine.gameplay.items.ItemDef
import com.pafoid.skate.engine.gameplay.items.ItemStack
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24d §5.3: focus events, the item requirement and pickups through the inventory, with a real [EventSystem]. */
class InteractionSystemTest {
    private val events = EventSystem()
    private val items = ItemDatabase().apply {
        register(ItemDef("wax", maxStack = 5))
        register(ItemDef("key"))
    }
    private val inventory = InventorySystem(events, items)
    private var press = false
    private val system = InteractionSystem(events, pressed = { press })
    private val scene = Scene("Park").apply { isRunning = true }
    private val focus = mutableListOf<InteractionFocusChanged>()
    private val used = mutableListOf<InteractEvent>()
    private val full = mutableListOf<InventoryFull>()

    private val player = GameObject("Player")
        .addComponent(Transform())
        .addComponent(PlayerController())
        .addComponent(InventoryComponent(1))
        .also(scene::addChild)

    init {
        events.subscribe<InteractionFocusChanged> { focus += it }
        events.subscribe<InteractEvent> { used += it }
        events.subscribe<InventoryFull> { full += it }
        system.init(scene)
    }

    private fun thing(name: String, at: Vector3f, interactable: InteractableComponent, pickup: PickupComponent? = null) =
        GameObject(name).addComponent(Transform(Vector3f(at)).also { it.worldMatrix.translation(at) })
            .addComponent(interactable)
            .also { go -> pickup?.let { go.addComponent(it) } }
            .also(scene::addChild)

    private fun wax(at: Vector3f = Vector3f(0f, 0f, 1f), count: Int = 2, auto: Boolean = false) =
        thing("Wax", at, InteractableComponent("pickup", "prompt.pickup", 1.5f), PickupComponent("wax", count, auto))

    private fun moveTo(x: Float, z: Float) = player.getComponent<Transform>()!!.worldMatrix.translation(x, 0f, z)

    private fun frame(pressing: Boolean = false) {
        press = pressing
        system.update(0.016f)
    }

    @Test
    fun Interaction_FocusChangesWhenEnteringAndLeavingRange() {
        val wax = wax()
        moveTo(0f, -5f)
        frame()
        assertEquals(listOf(InteractionFocusChanged(player, null, null)), focus)
        moveTo(0f, 0f)
        frame()
        frame()
        assertEquals(InteractionFocusChanged(player, wax, "prompt.pickup"), focus.last())
        assertEquals(2, focus.size, "no event while the focus stays the same")
        moveTo(0f, -5f)
        frame()
        assertNull(focus.last().target)
    }

    @Test
    fun Interaction_PressingPicksUpTheItemAndRemovesTheObject() {
        val wax = wax()
        frame(pressing = true)
        assertEquals(listOf(InteractEvent(player, wax, "pickup")), used)
        assertEquals(ItemStack("wax", 2), player.getComponent<InventoryComponent>()!!.inventory.slots[0])
        assertFalse(wax in scene.children)
        frame()
        assertNull(focus.last().target, "focus moves off the removed pickup")
    }

    @Test
    fun Interaction_AFullInventoryKeepsThePickupWithWhatDidNotFit() {
        val wax = wax(count = 7)
        frame(pressing = true)
        assertEquals(5, player.getComponent<InventoryComponent>()!!.inventory.count("wax"))
        assertTrue(wax in scene.children)
        assertEquals(2, wax.getComponent<PickupComponent>()!!.count)
        assertEquals(listOf(InventoryFull(player, "wax", 2)), full)
    }

    @Test
    fun Interaction_AutoPickupNeedsNoInputAndNoFocus() {
        val wax = wax(auto = true)
        frame()
        assertFalse(wax in scene.children)
        assertEquals(2, player.getComponent<InventoryComponent>()!!.inventory.count("wax"))
        assertTrue(focus.none { it.target === wax })
    }

    @Test
    fun Interaction_ARequiredItemOffersTheTargetOnlyWhileCarried() {
        val door = thing("Door", Vector3f(0f, 0f, 1f), InteractableComponent("open", "prompt.open", 1.5f, requiresItem = "key"))
        frame(pressing = true)
        assertNull(focus.last().target)
        assertTrue(used.isEmpty())

        player.getComponent<InventoryComponent>()!!.inventory.slots[0] = ItemStack("key", 1)
        frame(pressing = true)
        assertSame(door, focus.last().target)
        assertEquals(listOf(InteractEvent(player, door, "open")), used, "other actions are left to game code")
        assertTrue(door in scene.children)
    }

    @Test
    fun Interaction_NothingHappensOutsidePlay() {
        wax(auto = true)
        scene.isRunning = false
        frame(pressing = true)
        assertTrue(focus.isEmpty() && used.isEmpty())
    }
}
