package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.SystemManager.ExecutionPriority
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.particles.ParticleSimulator

/**
 * Steps every [ParticleEmitter] while the scene is running, after transforms are final for the frame.
 * Emitters with `playOnStart` start on the first running frame. Leaving play mode, or disabling an emitter or
 * its game object, clears its particles.
 */
class ParticleSystem : System(priority = ExecutionPriority.LATE) {

    override val runsAfter = listOf(TransformSystem::class)

    private val emitters = query(ParticleEmitter::class, Transform::class, includeDisabled = true)

    override fun update(dt: Float) {
        val running = scene.isRunning
        emitters.forEach { go ->
            val emitter = go.getComponent<ParticleEmitter>() ?: return@forEach
            val runtime = emitter.runtime
            if (!running || !go.isEnabled || !emitter.enabled) {
                if (runtime.started || runtime.pool.count > 0) runtime.reset()
                return@forEach
            }
            if (!runtime.started && emitter.playOnStart) emitter.play()
            val transform = go.getComponent<Transform>() ?: return@forEach
            ParticleSimulator.step(emitter, runtime, dt, transform.worldMatrix)
        }
    }
}
