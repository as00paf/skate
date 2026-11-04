package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.render.particles.ParticleRuntime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f
import org.joml.Vector4f

/** Where new particles appear and which way they start moving (in the emitter's local space; +Y is "up"). */
@Serializable
enum class EmissionShape {
    /** At the origin, moving in a random direction. */
    POINT,

    /** Inside a sphere of [ParticleEmitter.shapeRadius], moving outward. */
    SPHERE,

    /** Inside a box of [ParticleEmitter.boxSize], moving along +Y. */
    BOX,

    /** On a disc of [ParticleEmitter.shapeRadius], moving within [ParticleEmitter.coneAngle] degrees of +Y. */
    CONE,
}

/**
 * CPU-simulated particle emitter, drawn as camera-facing instanced quads. Particles live in world space, so they
 * stay behind when the emitter moves. Simulation runs only while the scene is running (see `ParticleSystem`).
 * Size and color are linearly interpolated from start to end over each particle's life. With an atlas
 * ([atlasColumns] x [atlasRows] > 1) the frame is either picked by normalized age ([animateFrames]) or stays 0.
 */
@Serializable
class ParticleEmitter(
    var playOnStart: Boolean = true,
    var looping: Boolean = true,
    var duration: Float = 5f,
    var maxParticles: Int = 256,
    var emissionRate: Float = 20f,
    var burstCount: Int = 0,
    var shape: EmissionShape = EmissionShape.CONE,
    var shapeRadius: Float = 0.2f,
    var coneAngle: Float = 20f,
    @Contextual val boxSize: Vector3f = Vector3f(1f, 1f, 1f),
    var lifetimeMin: Float = 1f,
    var lifetimeMax: Float = 2f,
    var speedMin: Float = 1f,
    var speedMax: Float = 2f,
    /** Velocity multiplier reached at the end of life (1 = constant speed, 0 = slows to a stop). */
    var endSpeedScale: Float = 1f,
    /** World-space acceleration applied to every particle. */
    @Contextual val gravity: Vector3f = Vector3f(0f, 0f, 0f),
    var startSize: Float = 0.3f,
    var endSize: Float = 0.1f,
    @Contextual val startColor: Vector4f = Vector4f(1f, 1f, 1f, 1f),
    @Contextual val endColor: Vector4f = Vector4f(1f, 1f, 1f, 0f),
    /** Texture (atlas) path; empty draws a soft round dot. */
    var texturePath: String = "",
    var atlasColumns: Int = 1,
    var atlasRows: Int = 1,
    var animateFrames: Boolean = true,
    /** Additive blending (fire, sparks); otherwise alpha blending (smoke, dust). */
    var additive: Boolean = false,
    /** RNG seed; 0 picks a random one each time the emitter starts. */
    var seed: Int = 0,
) : Component() {

    @Transient
    val runtime: ParticleRuntime = ParticleRuntime()

    /** Starts (or restarts) emitting from a clean state. */
    fun play() = runtime.restart(this)

    /** Stops emitting; live particles finish their lives. */
    fun stop() {
        runtime.playing = false
    }

    /** Stops and removes every live particle. */
    fun clear() = runtime.reset()

    override fun reset() = runtime.reset()

    override fun destroy() = runtime.reset()
}
