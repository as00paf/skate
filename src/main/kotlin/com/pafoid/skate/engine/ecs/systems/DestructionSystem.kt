package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DestructibleComponent
import com.pafoid.skate.engine.ecs.components.DestructionMode
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.events.Collision
import com.pafoid.skate.engine.events.DestructibleDamaged
import com.pafoid.skate.engine.events.Fractured
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.destruction.DestructionService
import com.pafoid.skate.engine.utils.toDegreesF
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.IdentityHashMap

/**
 * Damage and breaking of [DestructibleComponent] props (PHY-29 slice 29a, `docs/design/PHY-29.md` §5). Runs in the
 * fixed step right after [PhysicsSystem]:
 *
 * 1. `Collision` events from that step become impact damage, `max(0, J − impactThreshold) × impactDamagePerNs`, using
 *    only the largest impulse each prop got this step. Both sides of a contact can be destructible.
 * 2. Damage lowers `integrity` and publishes [DestructibleDamaged]; at 0 the prop is queued to break. Calls to
 *    [damage] and [breakNow] (scripts, weapons) do the same at once.
 * 3. At most [MAX_BREAKS_PER_STEP] queued props break per step, oldest first, so a chain reaction never spikes a frame.
 *    A queued prop can't be damaged or queued again.
 *
 * Breaking sets `broken`, publishes [Fractured] while the prop is still in the scene, then [DestructionMode.REMOVE]
 * destroys it and [DestructionMode.SWAP] puts [prefabs]`(brokenPrefab)` in its place (same parent, sibling index and
 * Transform, or the body's current pose for a simulated prop); the broken version gets the original body's velocities once its own body exists. A swap whose prefab is
 * missing, and [DestructionMode.SHATTER] until slice 29c, break like a removal, and [Fractured.mode] says so.
 *
 * `HealthComponent` props (§5.3) are slice 29g; until then every destructible uses its own integrity.
 */
class DestructionSystem(
    private val eventSystem: EventSystem? = null,
    private val prefabs: (String) -> GameObject? = { null },
    private val warn: (String) -> Unit = {},
) : System(priority = ExecutionPriority.EARLY), DestructionService {

    override val phase = SystemPhase.FIXED_UPDATE
    override val runsAfter = listOf(PhysicsSystem::class)

    /** The largest impact a prop got during the current physics step ([DestructionRuntime] in §4.2). */
    private class Impact(var impulse: Float, val point: Vector3f)

    private class PendingBreak(val target: GameObject, val point: Vector3f, val impulse: Float)

    /** Velocities a swapped-in broken prefab gets once [PhysicsSystem] has created its body. */
    private class Inherited(val linear: Vector3f, val angular: Vector3f, var stepsLeft: Int)

    private val impacts = IdentityHashMap<GameObject, Impact>()
    private val queue = ArrayDeque<PendingBreak>()
    private val queued = IdentityHashMap<GameObject, Unit>()
    private val inherited = IdentityHashMap<GameObject, Inherited>()
    private val warnedPrefabs = HashSet<String>()
    private val rotation = Quaternionf()
    private val euler = Vector3f()

    /** Props waiting to break (tests, stats). */
    val queuedBreaks: Int get() = queue.size

    init {
        eventSystem?.subscribe<Collision> { onCollision(it) }
    }

    override fun init(scene: Scene) {
        super.init(scene)
        impacts.clear()
        queue.clear()
        queued.clear()
        inherited.clear()
    }

    private fun onCollision(event: Collision) {
        if (!scene.isRunning || !event.impulse.isFinite() || event.impulse <= 0f) return
        val self = event.self
        if (destructibleOf(self) == null || self in queued) return
        val impact = impacts[self]
        if (impact == null) {
            impacts[self] = Impact(event.impulse, Vector3f(event.contactPoint))
        } else if (event.impulse > impact.impulse) {
            impact.impulse = event.impulse
            impact.point.set(event.contactPoint)
        }
    }

    override fun fixedUpdate(fixedDt: Float) {
        if (!scene.isRunning) {
            impacts.clear()
            return
        }
        applyInheritedVelocities()
        if (impacts.isNotEmpty()) {
            for ((target, impact) in impacts) {
                val destructible = destructibleOf(target) ?: continue
                if (target in queued) continue
                val amount = destructible.impactDamage(impact.impulse)
                if (amount > 0f) applyDamage(target, destructible, amount, impact.point, DestructionService.DAMAGE_TYPE_IMPACT, impact.impulse)
            }
            impacts.clear()
        }
        var breaks = 0
        while (breaks < MAX_BREAKS_PER_STEP && queue.isNotEmpty()) {
            val pending = queue.removeFirst()
            queued.remove(pending.target)
            if (breakProp(pending)) breaks++
        }
    }

    override fun damage(target: GameObject, amount: Float, point: Vector3f, type: String): Boolean {
        if (!amount.isFinite() || amount <= 0f || target in queued) return false
        val destructible = destructibleOf(target) ?: return false
        applyDamage(target, destructible, amount, point, type, impulse = 0f)
        return true
    }

    override fun breakNow(target: GameObject, point: Vector3f): Boolean {
        if (target in queued) return false
        val destructible = destructibleOf(target) ?: return false
        destructible.integrity = 0f
        enqueue(target, point, impulse = 0f)
        return true
    }

    private fun applyDamage(target: GameObject, destructible: DestructibleComponent, amount: Float, point: Vector3f, type: String, impulse: Float) {
        destructible.integrity = (destructible.integrity - amount).coerceAtLeast(0f)
        eventSystem?.publish(DestructibleDamaged(target, amount, destructible.integrity, Vector3f(point), type))
        if (destructible.integrity <= 0f) enqueue(target, point, impulse)
    }

    private fun enqueue(target: GameObject, point: Vector3f, impulse: Float) {
        if (queued.put(target, Unit) == null) queue.addLast(PendingBreak(target, Vector3f(point), impulse))
    }

    /** A live, unbroken, enabled destructible in this scene, or null. */
    private fun destructibleOf(go: GameObject): DestructibleComponent? {
        if (go.isDead) return null
        val destructible = go.getComponent<DestructibleComponent>() ?: return null
        if (!destructible.enabled || destructible.broken || !isInScene(go)) return null
        return destructible
    }

    private fun isInScene(go: GameObject): Boolean {
        var node: GameObject? = go.parent
        while (node != null) {
            if (node === scene) return true
            node = node.parent
        }
        return false
    }

    /** Breaks [pending]'s prop; false when it went away or broke some other way while queued. */
    private fun breakProp(pending: PendingBreak): Boolean {
        val target = pending.target
        val destructible = destructibleOf(target) ?: return false
        val parent = target.parent ?: return false
        destructible.broken = true

        val replacement = if (destructible.mode == DestructionMode.SWAP) instantiateBroken(destructible.brokenPrefab) else null
        val mode = if (replacement != null) DestructionMode.SWAP else DestructionMode.REMOVE
        eventSystem?.publish(
            Fractured(target, pending.point, pending.impulse, destructible.surface, mode, pieces = if (replacement != null) 1 else 0),
        )
        // A listener may already have removed or replaced it
        if (target.isDead || target.parent !== parent) {
            replacement?.destroy()
            return true
        }

        val index = parent.children.indexOf(target)
        val body = target.getComponent<RigidBody3D>()
        val linear = body?.linearVelocity ?: Vector3f()
        val angular = body?.angularVelocity ?: Vector3f()
        if (replacement != null) {
            val from = target.getComponent<Transform>()
            val to = replacement.getComponent<Transform>()
            if (from != null && to != null) {
                to.translation.set(from.translation)
                to.rotation.set(from.rotation)
                to.scale.set(from.scale)
                // A simulated prop's Transform is last frame's interpolated pose; its body has the current one
                body?.rawBody?.takeIf { parent === scene }?.let { handle ->
                    handle.getLocation(to.translation)
                    handle.getRotation(rotation).getEulerAnglesXYZ(euler)
                    to.rotation.set(euler.x.toDegreesF(), euler.y.toDegreesF(), euler.z.toDegreesF())
                }
            }
        }
        parent.removeChild(target)
        target.destroy()
        if (replacement != null) {
            parent.addChild(replacement, index)
            if (replacement.getComponent<RigidBody3D>() != null && (linear.lengthSquared() > 0f || angular.lengthSquared() > 0f)) {
                inherited[replacement] = Inherited(linear, angular, INHERIT_STEPS)
            }
        }
        return true
    }

    private fun instantiateBroken(reference: String): GameObject? {
        if (reference.isBlank()) {
            warnOnce("", "A SWAP destructible has no broken prefab; it was removed instead")
            return null
        }
        val result = runCatching { prefabs(reference) }.getOrNull()
        if (result == null) warnOnce(reference, "Broken prefab '$reference' could not be instantiated; the prop was removed instead")
        return result
    }

    private fun warnOnce(key: String, message: String) {
        if (warnedPrefabs.add(key)) warn(message)
    }

    private fun applyInheritedVelocities() {
        if (inherited.isEmpty()) return
        val done = ArrayList<GameObject>()
        for ((go, velocities) in inherited) {
            val body = go.getComponent<RigidBody3D>()
            when {
                go.isDead || body == null -> done += go
                body.rawBody != null -> {
                    body.linearVelocity = velocities.linear
                    body.angularVelocity = velocities.angular
                    done += go
                }
                --velocities.stepsLeft <= 0 -> done += go
            }
        }
        done.forEach { inherited.remove(it) }
    }

    companion object {
        /** Breaks run per fixed step (§5.4, §12); the rest wait. */
        const val MAX_BREAKS_PER_STEP = 4

        /** Steps a swapped-in prefab may take to get its body before its inherited velocities are dropped. */
        private const val INHERIT_STEPS = 4
    }
}
