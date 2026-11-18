package com.pafoid.skate.engine.gameplay.appearance

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.TransformSystem
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** GPL-24e §6.6: a known bone matrix and offset give the expected attachment transform. */
class SocketMathTest {

    private fun assertVec(expected: Vector3f, actual: Vector3f, eps: Float = 1e-4f) =
        assertTrue(expected.distance(actual) < eps, "expected $expected, got $actual")

    @Test
    fun SocketMath_HatSitsOnTheHeadBoneAtTheSocketOffset() {
        // Head bone 1.6 m up, turned 90° about Y: the socket's +Z offset points along model +X
        val head = Matrix4f().translation(0f, 1.6f, 0f).rotateY(Math.toRadians(90.0).toFloat())
        val socket = Socket("head", "HEAD", Vector3f(0f, 0.17f, 0.02f))
        val m = SocketMath.attachment(head, socket, Vector3f(0f, 0.03f, 0f), Vector3f(), Vector3f(0.1f, 0.1f, 0.1f))
        assertVec(Vector3f(0.02f, 1.8f, 0f), m.getTranslation(Vector3f()))
        assertVec(Vector3f(0.1f, 0.1f, 0.1f), m.getScale(Vector3f()))
    }

    @Test
    fun SocketMath_DecomposedTransformRebuildsTheSameMatrixInTransformSystem() {
        val bone = Matrix4f().translation(0.1f, 1.5f, -0.2f).rotateXYZ(0.3f, -0.8f, 0.2f)
        val socket = Socket("eyes", "HEAD", Vector3f(0f, 0.09f, 0.1f), Vector3f(10f, 0f, 0f))
        val m = SocketMath.attachment(bone, socket, Vector3f(), Vector3f(0f, 0f, 5f), Vector3f(0.08f, 0.02f, 0.01f))

        val transform = Transform()
        SocketMath.decompose(m, transform.translation, transform.rotation, transform.scale)
        GameObject("Glasses").addComponent(transform)
        TransformSystem().updateTransform(transform)

        val probe = Vector3f(1f, 2f, 3f)
        assertVec(m.transformPosition(Vector3f(probe)), transform.worldMatrix.transformPosition(Vector3f(probe)))
    }

    @Test
    fun SocketMath_DecomposeHandlesNonUniformScaleAndGimbalLock() {
        for (yDeg in listOf(-45f, 90f, -90f)) {
            val m = SocketMath.trs(Vector3f(1f, 2f, 3f), Vector3f(20f, yDeg, 30f), Vector3f(0.5f, 2f, 1f), Matrix4f())
            val t = Vector3f()
            val r = Vector3f()
            val sc = Vector3f()
            SocketMath.decompose(m, t, r, sc)
            val back = SocketMath.trs(t, r, sc, Matrix4f())
            val probe = Vector3f(0.3f, -1f, 2f)
            assertVec(m.transformPosition(Vector3f(probe)), back.transformPosition(Vector3f(probe)), 1e-3f)
        }
    }
}
