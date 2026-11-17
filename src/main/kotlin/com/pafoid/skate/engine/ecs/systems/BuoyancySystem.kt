package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Collider3D
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.environment.WaterQueries
import com.pafoid.skate.engine.events.WaterEntered
import com.pafoid.skate.engine.events.WaterExited
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.BodyDescription
import com.pafoid.skate.engine.physics3d.BodyType
import com.pafoid.skate.engine.physics3d.ColliderShape
import com.pafoid.skate.engine.physics3d.ShapePart
import com.pafoid.skate.engine.physics3d.water.BuoyancyMath
import com.pafoid.skate.engine.physics3d.water.BuoyancyMode
import com.pafoid.skate.engine.physics3d.water.BuoyancySample
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Buoyancy and drag in water volumes (PHY-16, `docs/design/REN-29.md` §8.2). Driven by [PhysicsSystem] before each
 * physics step, so it is not a scheduled system itself.
 *
 * Each body is approximated by [BuoyancyMath] sample spheres. Per step, a sample at world `p` under the surface
 * `h = WaterWaves.heightAt(p, t)` pushes up with `ρ V f |g|` at its own position, so off-centre samples right boxes
 * and roll logs. Drag acts per sample too, `−linearDrag · ρ · V_s,i · (v + ω × r_i − flow)`, so its sum is the
 * §8.2 linear drag for a translating body and rocking is damped as well; the angular velocity also decays by
 * `angularDrag · f`. Drag scales with the displaced water rather than the body mass (§8.2 says `m · f`): the two agree
 * for a body at rest, but `m · f` leaves light bodies (a quarter under) bobbing for tens of seconds.
 * Only `applyForce`, `applyCentralForce` and the velocity setters are used, so every backend floats the same way.
 *
 * [WaterEntered]/[WaterExited] are published per body and water for [BuoyancyMode.AUTO] and [BuoyancyMode.CONTROLLER]
 * bodies; controller bodies (and AUTO bodies with a [PlayerController]) get no force.
 */
class BuoyancySystem(private val eventSystem: EventSystem? = null) {
    private class BodySamples(val samples: List<BuoyancySample>, val scale: Vector3f, val colliderCount: Int) {
        val volume = BuoyancyMath.volume(samples)
        val reach = samples.maxOfOrNull { it.local.length() + it.radius } ?: 0f
    }

    private val cache = IdentityHashMap<RigidBody3D, BodySamples>()
    /** Waters each body currently touches. */
    private val inside = IdentityHashMap<GameObject, MutableSet<GameObject>>()

    private val position = Vector3f()
    private val rotation = Quaternionf()
    private val world = Vector3f()
    private val relative = Vector3f()
    private val force = Vector3f()
    private val pointVelocity = Vector3f()
    private var displacedBy = FloatArray(0)
    private var volumeBy = arrayOfNulls<WaterQueries.Volume>(0)
    private val touched: MutableSet<GameObject> = Collections.newSetFromMap(IdentityHashMap())
    private val live: MutableSet<GameObject> = Collections.newSetFromMap(IdentityHashMap())

    /** Forgets cached samples and water membership (scene reload, Play start). No events are published. */
    fun reset() {
        cache.clear()
        inside.clear()
    }

    /**
     * Applies water forces to [bodies] for one fixed step of [dt] at clock time [time]. [waters] must be refreshed for
     * this step; [gravity] is the world gravity.
     */
    fun step(bodies: Iterable<GameObject>, waters: WaterQueries, time: Float, dt: Float, gravity: Vector3f) {
        live.clear()
        if (waters.volumes.isEmpty() && inside.isEmpty()) return
        for (go in bodies) {
            live += go
            val body = go.getComponent<RigidBody3D>() ?: continue
            val mode = modeOf(go, body)
            val handle = body.rawBody
            if (mode == BuoyancyMode.OFF || handle == null || body.isTrigger || !body.enabled ||
                body.bodyType == BodyType.Static || (body.bodyType == BodyType.Dynamic && body.mass <= 0f)
            ) {
                leaveAll(go)
                continue
            }
            // Only simulated bodies are pushed; kinematic controller bodies still get events
            val simulated = body.bodyType == BodyType.Dynamic && !handle.isKinematic
            val samples = samplesOf(go, body)
            handle.getLocation(position)
            touched.clear()
            if (samples.samples.isNotEmpty() && waters.volumes.any { reaches(it, samples.reach) }) {
                handle.getRotation(rotation)
                applyWater(body, samples, waters, time, dt, gravity, applyForces = mode == BuoyancyMode.AUTO && simulated)
            }
            updateMembership(go)
        }
        // Bodies that left the scene while in water
        inside.keys.filter { it !in live }.forEach(::leaveAll)
        val volumesNow = waters.volumes.map { it.source }
        inside.forEach { (body, set) ->
            set.filter { water -> volumesNow.none { it === water } }.forEach { water ->
                set.remove(water)
                eventSystem?.publish(WaterExited(water, body))
            }
        }
        inside.values.removeIf { it.isEmpty() }
    }

    private fun reaches(volume: WaterQueries.Volume, reach: Float): Boolean =
        volume.containsXZ(position.x, position.z, reach) &&
            position.y - reach <= volume.topY && position.y + reach >= volume.floorY

    private fun applyWater(
        body: RigidBody3D,
        cached: BodySamples,
        waters: WaterQueries,
        time: Float,
        dt: Float,
        gravity: Vector3f,
        applyForces: Boolean,
    ) {
        val handle = body.rawBody ?: return
        val n = cached.samples.size
        if (displacedBy.size < n) {
            displacedBy = FloatArray(n)
            volumeBy = arrayOfNulls(n)
        }
        var submergedVolume = 0f
        var dragMass = 0f
        var angularDrag = 0f
        for (i in 0 until n) {
            val sample = cached.samples[i]
            displacedBy[i] = 0f
            rotation.transform(world.set(sample.local)).add(position)
            val volume = waters.volumeAt(world.x, world.y, world.z, sample.radius) ?: continue
            if (world.y + sample.radius < volume.floorY) continue
            val fraction = BuoyancyMath.sphereCapFraction(volume.surfaceY(world.x, world.z, time) - world.y, sample.radius)
            if (fraction <= 0f) continue
            touched += volume.source
            val displaced = sample.volume * fraction
            displacedBy[i] = displaced
            volumeBy[i] = volume
            submergedVolume += displaced
            dragMass += displaced * volume.water.density * volume.water.linearDrag
            angularDrag += displaced * volume.water.angularDrag
        }
        if (!applyForces || submergedVolume <= 0f || cached.volume <= 0f) return

        // Drag never reverses the relative velocity within one step
        val dragScale = minOf(1f, body.mass / (dragMass * dt))
        val linear = handle.linearVelocity
        val angular = handle.angularVelocity
        for (i in 0 until n) {
            val displaced = displacedBy[i]
            if (displaced <= 0f) continue
            val volume = volumeBy[i] ?: continue
            rotation.transform(relative.set(cached.samples[i].local))
            val density = volume.water.density
            // Archimedes: the weight of the displaced water, against gravity, at the sample
            force.set(gravity).mul(-density * displaced)
            // Drag on the water this sample displaces, at the sample's own velocity, so rocking is damped too
            pointVelocity.set(angular).cross(relative).add(linear).sub(volume.flow)
            force.fma(-volume.water.linearDrag * density * displaced * dragScale, pointVelocity)
            handle.applyForce(force, relative)
        }

        val keep = (1f - angularDrag / submergedVolume * (submergedVolume / cached.volume) * dt).coerceIn(0f, 1f)
        if (keep < 1f) handle.angularVelocity = angular.mul(keep)
    }

    private fun updateMembership(go: GameObject) {
        val set = inside[go]
        if (touched.isEmpty()) {
            if (set != null) leaveAll(go)
            return
        }
        val current = set ?: Collections.newSetFromMap(IdentityHashMap<GameObject, Boolean>()).also { inside[go] = it }
        current.filter { it !in touched }.forEach { water ->
            current.remove(water)
            eventSystem?.publish(WaterExited(water, go))
        }
        touched.forEach { water ->
            if (current.add(water)) eventSystem?.publish(WaterEntered(water, go))
        }
    }

    private fun leaveAll(go: GameObject) {
        val set = inside.remove(go) ?: return
        set.forEach { eventSystem?.publish(WaterExited(it, go)) }
    }

    private fun modeOf(go: GameObject, body: RigidBody3D): BuoyancyMode =
        if (body.buoyancy == BuoyancyMode.AUTO && go.getComponent<PlayerController>() != null) BuoyancyMode.CONTROLLER
        else body.buoyancy

    /** Samples built once per body; rebuilt when its scale or collider count changes. */
    private fun samplesOf(go: GameObject, body: RigidBody3D): BodySamples {
        val scale = go.getComponent<Transform>()?.scale ?: ONE
        val colliders = go.components.filterIsInstance<Collider3D>()
        cache[body]?.let { if (it.scale == scale && it.colliderCount == colliders.size) return it }
        // No collider: the backends give the body a unit box, so it floats as one
        val parts = BodyDescription.parts(colliders).ifEmpty {
            if (colliders.isEmpty()) listOf(ShapePart(ColliderShape.Box(Vector3f(1f)), Vector3f())) else emptyList()
        }
        val samples = BuoyancyMath.samples(parts, scale)
        return BodySamples(samples, Vector3f(scale), colliders.size).also { cache[body] = it }
    }

    /** Number of bodies currently in water, for tests and stats. */
    val bodiesInWater: Int get() = inside.size

    private companion object {
        val ONE = Vector3f(1f)
    }
}
