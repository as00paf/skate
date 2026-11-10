package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** GPL-21: a ridden mount's trick spin is applied in its own frame, on top of the rider's heading. */
class MountSpinTest {

    private val system = MountFollowSystem()
    private val offset = Vector3f(0f, -0.1f, 0f)

    private fun axis(t: Transform, local: Vector3f): Vector3f {
        val r = t.rotation
        return Quaternionf().rotationXYZ(Math.toRadians(r.x.toDouble()).toFloat(), Math.toRadians(r.y.toDouble()).toFloat(), Math.toRadians(r.z.toDouble()).toFloat())
            .transform(Vector3f(local))
    }

    private fun nose(t: Transform) = axis(t, Vector3f(0f, 0f, 1f))

    @Test
    fun eulerXYZ_RebuildsTheSameRotation_AlsoAtTheMiddleAngleSingularity() {
        val cases = listOf(
            Quaternionf().rotationY(Math.toRadians(90.0).toFloat()).rotateZ(Math.PI.toFloat()),
            Quaternionf().rotationY(Math.toRadians(-90.0).toFloat()).rotateZ(1f),
            Quaternionf().rotationY(0.7f).rotateZ(2.5f),
            Quaternionf().rotationXYZ(0.3f, -1.2f, 0.8f),
        )
        for (q in cases) {
            val e = com.pafoid.skate.engine.gameplay.MotionMath.eulerXYZ(q)
            val back = Quaternionf().rotationXYZ(e.x, e.y, e.z)
            listOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f)).forEach { v ->
                val want = q.transform(Vector3f(v))
                val got = back.transform(Vector3f(v))
                // Float precision near the singularity is ~1e-4; the old conversion was off by up to 2 (axis flipped).
                assertEquals(0f, want.distance(got), 1e-3f, "$q $v")
            }
        }
    }

    @Test
    fun withoutSpin_TheMountTakesTheRidersHeading() {
        val rider = Transform(Vector3f(1f, 0f, 2f), rotation = Vector3f(0f, 90f, 0f))
        val mount = Transform()

        system.follow(rider, mount, offset)

        val n = nose(mount)
        assertEquals(1f, n.x, 1e-4f)
    }

    @Test
    fun spin_TurnsTheMountInItsOwnFrame() {
        val rider = Transform(rotation = Vector3f(0f, 90f, 0f))
        val shove = Transform()
        val flip = Transform()

        // A quarter shove-it turns the nose a further 90 degrees; a half kickflip rolls around the nose.
        system.follow(rider, shove, offset, Quaternionf().rotationY(Math.toRadians(90.0).toFloat()))
        system.follow(rider, flip, offset, Quaternionf().rotationZ(Math.toRadians(180.0).toFloat()))

        assertEquals(-1f, nose(shove).z, 1e-4f)
        assertEquals(1f, nose(flip).x, 1e-4f)
        assertEquals(-1f, axis(flip, Vector3f(0f, 1f, 0f)).y, 1e-4f, "half way through the flip the board is upside down")
    }
}
