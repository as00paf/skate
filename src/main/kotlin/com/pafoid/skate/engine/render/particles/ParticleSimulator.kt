package com.pafoid.skate.engine.render.particles

import com.pafoid.skate.engine.ecs.components.EmissionShape
import com.pafoid.skate.engine.ecs.components.ParticleEmitter
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Per-emitter simulation state; not serialized. */
class ParticleRuntime {
    val pool = ParticlePool()
    var playing = false

    /** Whether the emitter has been started once since the last [reset] (drives `playOnStart`). */
    var started = false

    /** Seconds into the current emission cycle. */
    var time = 0f

    /** Fractional particles carried to the next step so low rates still emit. */
    var emitDebt = 0f
    var burstDone = false
    var random: Random = Random.Default

    fun restart(emitter: ParticleEmitter) {
        pool.clear()
        time = 0f
        emitDebt = 0f
        burstDone = false
        playing = true
        started = true
        random = if (emitter.seed != 0) Random(emitter.seed) else Random.Default
    }

    fun reset() {
        pool.clear()
        playing = false
        started = false
        time = 0f
        emitDebt = 0f
        burstDone = false
    }

    /** True when a non-looping emitter has finished emitting and every particle has died. */
    val isFinished: Boolean get() = !playing && pool.count == 0
}

/** CPU particle simulation step and over-life evaluation. GL-free so it is unit-testable. */
object ParticleSimulator {
    const val MAX_PARTICLES = 100_000

    private val tmpPos = Vector3f()
    private val tmpDir = Vector3f()

    /**
     * Advances [runtime] by [dt] seconds: ages, integrates and kills live particles, then emits new ones from the
     * emitter placed at [emitterWorld]. Particles spawned in this step are not integrated until the next one.
     */
    fun step(emitter: ParticleEmitter, runtime: ParticleRuntime, dt: Float, emitterWorld: Matrix4f) {
        val pool = runtime.pool
        pool.ensureCapacity(emitter.maxParticles.coerceIn(0, MAX_PARTICLES))
        if (dt <= 0f) return

        integrate(emitter, pool, dt)
        if (runtime.playing) emit(emitter, runtime, dt, emitterWorld)
    }

    private fun integrate(emitter: ParticleEmitter, pool: ParticlePool, dt: Float) {
        val g = emitter.gravity
        var i = 0
        while (i < pool.count) {
            pool.age[i] += dt
            if (pool.age[i] >= pool.life[i]) {
                pool.kill(i)
                continue
            }
            pool.accX[i] += g.x * dt
            pool.accY[i] += g.y * dt
            pool.accZ[i] += g.z * dt
            val scale = lerp(1f, emitter.endSpeedScale, pool.normalizedAge(i))
            pool.posX[i] += (pool.velX[i] * scale + pool.accX[i]) * dt
            pool.posY[i] += (pool.velY[i] * scale + pool.accY[i]) * dt
            pool.posZ[i] += (pool.velZ[i] * scale + pool.accZ[i]) * dt
            i++
        }
    }

    private fun emit(emitter: ParticleEmitter, runtime: ParticleRuntime, dt: Float, world: Matrix4f) {
        if (!runtime.burstDone) {
            repeat(emitter.burstCount.coerceAtLeast(0)) { spawn(emitter, runtime, world) }
            runtime.burstDone = true
        }

        val duration = emitter.duration
        val remaining = if (duration > 0f) (duration - runtime.time).coerceIn(0f, dt) else dt
        val emitTime = if (emitter.looping) dt else remaining
        runtime.emitDebt += emitter.emissionRate.coerceAtLeast(0f) * emitTime
        val count = floor(runtime.emitDebt).toInt()
        runtime.emitDebt -= count
        repeat(count) { spawn(emitter, runtime, world) }

        runtime.time += dt
        if (duration > 0f && runtime.time >= duration) {
            if (emitter.looping) {
                runtime.time %= duration
                runtime.burstDone = false
            } else {
                runtime.playing = false
            }
        }
    }

    private fun spawn(emitter: ParticleEmitter, runtime: ParticleRuntime, world: Matrix4f) {
        val pool = runtime.pool
        val i = pool.spawn()
        if (i < 0) return
        val rng = runtime.random
        shapeSample(emitter, rng, tmpPos, tmpDir)
        world.transformPosition(tmpPos)
        world.transformDirection(tmpDir)
        if (tmpDir.lengthSquared() > 1e-12f) tmpDir.normalize()
        val speed = range(rng, emitter.speedMin, emitter.speedMax)
        pool.posX[i] = tmpPos.x; pool.posY[i] = tmpPos.y; pool.posZ[i] = tmpPos.z
        pool.velX[i] = tmpDir.x * speed; pool.velY[i] = tmpDir.y * speed; pool.velZ[i] = tmpDir.z * speed
        pool.life[i] = range(rng, emitter.lifetimeMin, emitter.lifetimeMax).coerceAtLeast(MIN_LIFETIME)
    }

    /** Writes a local-space spawn position and unit direction for the emitter's shape. */
    fun shapeSample(emitter: ParticleEmitter, rng: Random, pos: Vector3f, dir: Vector3f) {
        val radius = emitter.shapeRadius.coerceAtLeast(0f)
        when (emitter.shape) {
            EmissionShape.POINT -> {
                pos.zero()
                randomUnit(rng, dir)
            }

            EmissionShape.SPHERE -> {
                randomUnit(rng, dir)
                // cbrt keeps the density uniform through the volume
                pos.set(dir).mul(radius * Math.cbrt(rng.nextDouble()).toFloat())
            }

            EmissionShape.BOX -> {
                val b = emitter.boxSize
                pos.set((rng.nextFloat() - 0.5f) * b.x, (rng.nextFloat() - 0.5f) * b.y, (rng.nextFloat() - 0.5f) * b.z)
                dir.set(0f, 1f, 0f)
            }

            EmissionShape.CONE -> {
                val a = rng.nextFloat() * TWO_PI
                val r = radius * sqrt(rng.nextFloat())
                pos.set(cos(a) * r, 0f, sin(a) * r)
                val maxAngle = Math.toRadians(emitter.coneAngle.coerceIn(0f, 180f).toDouble()).toFloat()
                // uniform over the spherical cap around +Y
                val cosT = 1f - rng.nextFloat() * (1f - cos(maxAngle))
                val sinT = sqrt((1f - cosT * cosT).coerceAtLeast(0f))
                val b = rng.nextFloat() * TWO_PI
                dir.set(cos(b) * sinT, cosT, sin(b) * sinT)
            }
        }
    }

    fun sizeAt(emitter: ParticleEmitter, t: Float): Float = lerp(emitter.startSize, emitter.endSize, t)

    fun colorAt(emitter: ParticleEmitter, t: Float, dest: Vector4f): Vector4f =
        dest.set(emitter.startColor).lerp(emitter.endColor, t)

    fun frameCount(emitter: ParticleEmitter): Int =
        emitter.atlasColumns.coerceAtLeast(1) * emitter.atlasRows.coerceAtLeast(1)

    /** Atlas frame for normalized age [t]; frames run left to right, top to bottom. */
    fun frameAt(emitter: ParticleEmitter, t: Float): Int {
        val frames = frameCount(emitter)
        if (frames <= 1 || !emitter.animateFrames) return 0
        return (t.coerceIn(0f, 1f) * frames).toInt().coerceAtMost(frames - 1)
    }

    /**
     * UV rect of [frame] as (u0, v0, width, height) with v0 at the frame's top edge. Textures are loaded unflipped,
     * so v = 0 is the image's top row.
     */
    fun uvRect(emitter: ParticleEmitter, frame: Int, dest: FloatArray, offset: Int = 0): FloatArray {
        val cols = emitter.atlasColumns.coerceAtLeast(1)
        val rows = emitter.atlasRows.coerceAtLeast(1)
        val f = frame.coerceIn(0, cols * rows - 1)
        val w = 1f / cols
        val h = 1f / rows
        dest[offset] = (f % cols) * w
        dest[offset + 1] = (f / cols) * h
        dest[offset + 2] = w
        dest[offset + 3] = h
        return dest
    }

    private fun randomUnit(rng: Random, dest: Vector3f) {
        val z = rng.nextFloat() * 2f - 1f
        val a = rng.nextFloat() * TWO_PI
        val r = sqrt((1f - z * z).coerceAtLeast(0f))
        dest.set(cos(a) * r, z, sin(a) * r)
    }

    private fun range(rng: Random, a: Float, b: Float): Float {
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        return lo + (hi - lo) * rng.nextFloat()
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private const val TWO_PI = (Math.PI * 2).toFloat()
    private const val MIN_LIFETIME = 0.001f
}
