package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.GroundStateComponent
import com.pafoid.skate.engine.ecs.components.PlayerController
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.gameplay.MotionMath
import com.pafoid.skate.engine.gameplay.ground.GroundDetector
import com.pafoid.skate.engine.gameplay.ground.GroundQueries
import com.pafoid.skate.engine.gameplay.ik.FootPlacement
import com.pafoid.skate.engine.getComponent
import org.joml.Vector3f

/**
 * Probes the ground below each [GroundStateComponent] body after every physics step: surface, slope, tag under the
 * feet and a climbable step ahead. Also places the foot IK targets of a [PlayerController] until foot IK has its
 * own component.
 */
class GroundProbeSystem(private val queries: GroundQueries) : System() {
    override val phase = SystemPhase.FIXED_UPDATE
    override val runsAfter = listOf(PhysicsSystem::class)

    private val probed = query(GroundStateComponent::class, RigidBody3D::class)

    override fun fixedUpdate(fixedDt: Float) {
        if (!scene.isRunning) return
        probed.forEach { go ->
            val state = go.getComponent<GroundStateComponent>() ?: return@forEach
            val body = go.getComponent<RigidBody3D>() ?: return@forEach
            probe(state, body, go.getComponent<PlayerController>())
        }
    }

    private fun probe(state: GroundStateComponent, body: RigidBody3D, controller: PlayerController?) {
        val config = state.config
        // Probe only what the character collides with: a ridden board (mount layer) must not count as ground
        val detector = GroundDetector(config) { from, to -> queries.raycast(from, to, body.collisionMask, body)?.fraction }
        val origin = body.getWorldPosition()
        val feet = Vector3f(origin.x, origin.y - config.footOffset, origin.z)

        state.wasGrounded = state.ground.standing
        state.ground = detector.probe(feet)
        // A sphere as wide as the ground samples, so a thin rail between the rays still counts as underfoot.
        state.groundTag = queries.sphereCast(
            maxOf(config.sampleRadius, TAG_MIN_RADIUS),
            Vector3f(feet).add(0f, config.probeStartHeight + TAG_MIN_RADIUS, 0f),
            Vector3f(feet).sub(0f, config.snapDistance, 0f),
            body.collisionMask, body,
        )?.takeIf { it.normal.y > TAG_MIN_NORMAL_Y }?.gameObject?.tag?.takeIf { it.isNotEmpty() }

        val velocity = body.linearVelocity
        state.stepAhead = if (config.stepUp) detector.findStep(feet, velocity.x, velocity.z) else null

        controller ?: return
        val yaw = MotionMath.yawOfRotation(body.getRotation())
        controller.footIk.legs.forEachIndexed { i, leg ->
            val target = controller.footTargets.getOrNull(i) ?: return@forEachIndexed
            FootPlacement.compute(feet, yaw, leg, controller.footIk, target) { from, to ->
                queries.raycast(from, to, body.collisionMask, body)
            }
        }
    }

    private companion object {
        const val TAG_MIN_RADIUS = 0.05f

        /** Only a surface hit from above counts as underfoot, not the side of a ledge brushed in passing. */
        const val TAG_MIN_NORMAL_Y = 0.5f
    }
}
