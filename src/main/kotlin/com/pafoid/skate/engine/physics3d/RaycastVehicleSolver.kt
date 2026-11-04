package com.pafoid.skate.engine.physics3d

import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.VehicleComponent
import com.pafoid.skate.engine.ecs.components.WheelState
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Applies suspension, drive, brake and tyre impulses of a [VehicleComponent] to its chassis through [IPhysics3D]. */
class RaycastVehicleSolver(private val physics: IPhysics3D) {
    private val pos = Vector3f()
    private val rot = Quaternionf()
    private val linear = Vector3f()
    private val angular = Vector3f()
    private val up = Vector3f()
    private val forward = Vector3f()
    private val right = Vector3f()
    private val origin = Vector3f()
    private val end = Vector3f()
    private val rel = Vector3f()
    private val pointVel = Vector3f()
    private val impulse = Vector3f()
    private val tyreRel = Vector3f()

    fun step(vehicle: VehicleComponent, body: RigidBody3D, dt: Float) {
        val handle = body.rawBody ?: return
        if (dt <= 0f || vehicle.wheels.isEmpty()) return
        if (vehicle.wheelStates.size != vehicle.wheels.size) vehicle.wheelStates = vehicle.wheels.map { WheelState() }

        handle.getLocation(pos)
        handle.getRotation(rot)
        handle.linearVelocity.let { linear.set(it) }
        handle.angularVelocity.let { angular.set(it) }
        val chassisUp = rot.transform(up.set(0f, 1f, 0f))
        val baseForward = Vector3f(0f, 0f, 1f).rotate(rot)

        val driven = max(1, vehicle.wheels.count { it.driven })
        val share = max(body.mass, 0.001f) / vehicle.wheels.size
        val throttle = vehicle.throttle.coerceIn(-1f, 1f)
        val brake = vehicle.brake.coerceIn(0f, 1f)

        for (i in vehicle.wheels.indices) {
            val wheel = vehicle.wheels[i]
            val state = vehicle.wheelStates[i]
            val steer = if (wheel.steerable) vehicle.steering.coerceIn(-1f, 1f) * vehicle.maxSteerAngle else 0f
            state.steerAngle = steer
            state.grounded = false
            state.compression = 0f
            state.load = 0f

            origin.set(wheel.offset).rotate(rot).add(pos)
            val reach = vehicle.suspensionRestLength + wheel.radius
            end.set(origin).fma(-reach, chassisUp)
            val fraction = physics.raycastClosest(origin, end, body) ?: continue

            val distance = fraction * reach
            val compression = reach - distance
            rel.set(origin).fma(-distance, chassisUp).sub(pos)
            pointVel.set(angular).cross(rel).add(linear)
            val upSpeed = pointVel.dot(chassisUp)

            val load = (vehicle.springStiffness * compression - vehicle.springDamping * upSpeed)
                .coerceIn(0f, vehicle.maxSuspensionForce)
            state.grounded = true
            state.compression = compression
            state.load = load
            impulse.set(chassisUp).mul(load * dt)
            handle.applyImpulse(impulse, rel)

            // Tyre forces act in the ground plane of the wheel, applied at hub height to limit body roll
            forward.set(baseForward).rotateAxis(-steer, chassisUp.x, chassisUp.y, chassisUp.z)
            right.set(chassisUp).cross(forward)
            pointVel.fma(-upSpeed, chassisUp)
            val forwardSpeed = pointVel.dot(forward)
            val lateralSpeed = pointVel.dot(right)
            val grip = vehicle.tyreFriction * load * dt

            var longitudinal = if (wheel.driven) throttle * vehicle.maxEngineForce / driven * dt else 0f
            if (brake > 0f) {
                val stop = min(brake * vehicle.maxBrakeForce / vehicle.wheels.size * dt, abs(forwardSpeed) * share)
                longitudinal -= stop * Math.signum(forwardSpeed)
            }
            longitudinal = longitudinal.coerceIn(-grip, grip)
            val lateral = (-lateralSpeed * share).coerceIn(-grip, grip)

            impulse.set(forward).mul(longitudinal).fma(lateral, right)
            tyreRel.set(rel).fma(-rel.dot(chassisUp), chassisUp)
            handle.applyImpulse(impulse, tyreRel)
        }
    }
}
