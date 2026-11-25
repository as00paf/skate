package com.pafoid.skate.engine.ecs.systems

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
import com.pafoid.skate.engine.events.InventoryRequest
import com.pafoid.skate.engine.events.ItemAdded
import com.pafoid.skate.engine.gameplay.interaction.FocusCandidate
import com.pafoid.skate.engine.gameplay.interaction.Interaction
import com.pafoid.skate.engine.gameplay.interaction.InteractionFocus
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f
import java.util.IdentityHashMap

/**
 * Interaction and pickup (GPL-24 §5.2). Interactors are objects with a [PlayerController]; each frame of play:
 * 1. [PickupComponent.autoPickup] items within reach are taken.
 * 2. The focus is chosen by [InteractionFocus]; a change publishes [InteractionFocusChanged] for the HUD prompt.
 * 3. When [pressed] says the interactor pressed `interact`, [InteractEvent] is published; a `pickup` becomes an
 *    [InventoryRequest.Add]. Other action ids are left to game code and scripts.
 *
 * A pickup is removed (through [remove]) only when all of it went into the inventory; the rest stays, and the
 * inventory publishes `InventoryFull`. Positions are last frame's world matrices.
 */
class InteractionSystem(
    private val events: EventSystem,
    private val pressed: (GameObject) -> Boolean = { false },
    private val remove: (GameObject) -> Unit = { go -> go.parent?.removeChild(go); go.destroy() },
) : System() {

    private val interactors = query(PlayerController::class, Transform::class)
    private val interactables = query(InteractableComponent::class, Transform::class)
    private val focus = IdentityHashMap<GameObject, GameObject?>()
    private val removed = mutableListOf<GameObject>()

    // Filled synchronously while an InventoryRequest.Add is being published
    private var pendingOwner: GameObject? = null
    private var added = 0

    init {
        events.subscribe<ItemAdded> { if (it.owner === pendingOwner) added += it.count }
    }

    override fun init(scene: Scene) {
        super.init(scene)
        focus.clear()
    }

    override fun update(dt: Float) {
        if (!scene.isRunning) return
        val targets = interactables.entities.filter { it.getComponent<InteractableComponent>()?.enabled == true }
        interactors.forEach { interactor -> interact(interactor, targets) }
        removed.forEach(remove)
        removed.clear()
    }

    private fun interact(interactor: GameObject, targets: List<GameObject>) {
        val from = positionOf(interactor)
        val live = targets.filter { it !in removed && offered(it, interactor) }

        live.filter { it.getComponent<PickupComponent>()?.autoPickup == true && inReach(from, it) }.forEach { pickUp(interactor, it) }

        val choices = live.filter { it !in removed && it.getComponent<PickupComponent>()?.autoPickup != true }
        val candidates = choices.map { FocusCandidate(positionOf(it), it.getComponent<InteractableComponent>()!!.radius) }
        val target = InteractionFocus.choose(from, forwardOf(interactor), candidates).let { choices.getOrNull(it) }
        if (!focus.containsKey(interactor) || focus[interactor] !== target) {
            focus[interactor] = target
            events.publish(InteractionFocusChanged(interactor, target, target?.getComponent<InteractableComponent>()?.promptKey))
        }

        if (target != null && pressed(interactor)) {
            val actionId = target.getComponent<InteractableComponent>()!!.actionId
            events.publish(InteractEvent(interactor, target, actionId))
            if (actionId == Interaction.PICKUP && target.getComponent<PickupComponent>() != null) pickUp(interactor, target)
        }
    }

    /** An interactable needing an item is offered only while the interactor carries it. */
    private fun offered(target: GameObject, interactor: GameObject): Boolean {
        val required = target.getComponent<InteractableComponent>()?.requiresItem.orEmpty()
        return required.isEmpty() || interactor.getComponent<InventoryComponent>()?.inventory?.contains(required) == true
    }

    private fun inReach(from: Vector3f, target: GameObject) =
        from.distance(positionOf(target)) <= (target.getComponent<InteractableComponent>()?.radius ?: 0f)

    private fun pickUp(interactor: GameObject, target: GameObject) {
        val pickup = target.getComponent<PickupComponent>() ?: return
        if (pickup.count <= 0 || pickup.itemId.isBlank()) return
        pendingOwner = interactor
        added = 0
        try {
            events.publish(InventoryRequest.Add(interactor, pickup.itemId, pickup.count))
        } finally {
            pendingOwner = null
        }
        pickup.count -= added
        if (pickup.count <= 0) removed += target
    }

    private fun positionOf(go: GameObject): Vector3f =
        go.getComponent<Transform>()?.worldMatrix?.getTranslation(Vector3f()) ?: Vector3f()

    /** The object's +Z axis, as characters face +Z at yaw 0. */
    private fun forwardOf(go: GameObject): Vector3f =
        go.getComponent<Transform>()?.worldMatrix?.transformDirection(Vector3f(0f, 0f, 1f)) ?: Vector3f(0f, 0f, 1f)
}
