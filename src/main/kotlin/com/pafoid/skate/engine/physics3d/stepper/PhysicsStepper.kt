package com.pafoid.skate.engine.physics3d.stepper

import com.jme3.bullet.PhysicsSpace
import com.pafoid.skate.engine.render.EngineStats

/**
 * Responsible for stepping the physics simulation with a fixed timestep.
 * This class handles the accumulation of time and stepping the physics space
 * at consistent intervals regardless of the rendering frame rate.
 */
class PhysicsStepper(private val physicsSpace: PhysicsSpace) {
    private var accumulator = 0f
    private val fixedTimestep = 1.0f / 60.0f

    /**
     * Steps the physics simulation forward by the given delta time.
     * It uses a fixed timestep accumulator to ensure deterministic physics behavior
     * regardless of the rendering frame rate.
     *
     * @param dt The time elapsed since the last frame in seconds.
     */
    fun stepPhysics(dt: Float) {
        accumulator += dt
        while (accumulator >= fixedTimestep) {
            val startTime = System.nanoTime()
            physicsSpace.update(fixedTimestep, 0)
            val endTime = System.nanoTime()
            EngineStats.physicsStepTime.set(endTime - startTime)
            accumulator -= fixedTimestep
        }
    }

    /**
     * Resets the internal accumulator to zero.
     * This is useful when transitioning between scenes or restarting simulations.
     */
    fun resetAccumulator() {
        accumulator = 0f
    }
}