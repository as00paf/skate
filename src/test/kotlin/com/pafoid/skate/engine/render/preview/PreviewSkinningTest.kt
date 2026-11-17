package com.pafoid.skate.engine.render.preview

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-33: skin matrices for the offscreen preview from bone-local poses. */
class PreviewSkinningTest {
    /** root at y=1, child 1 m above it; inverse binds from the bind pose, like the importer computes them. */
    private val root = Bone(0, "root", Matrix4f().translation(0f, 1f, 0f))
    private val child = Bone(1, "child", Matrix4f().translation(0f, 1f, 0f))
    private val skeleton = Skeleton(root.also { it.addChild(child) }, 2).also {
        root.inverseBindMatrix.set(Matrix4f().translation(0f, 1f, 0f)).invert()
        child.inverseBindMatrix.set(Matrix4f().translation(0f, 2f, 0f)).invert()
    }
    private val parents = intArrayOf(-1, 0)
    private val world = Array(2) { Matrix4f() }
    private val out = Array(3) { Matrix4f().scale(5f) }

    private fun locals(rootLocal: Matrix4f) = arrayOf(rootLocal, Matrix4f(child.bindLocalTransform))

    @Test
    fun bindPose_GivesIdentitySkinning() {
        PreviewSkinning.palette(skeleton, parents, locals(Matrix4f(root.bindLocalTransform)), world, out)
        out.forEach { assertTrue(it.equals(Matrix4f(), 1e-5f), "$it") }
    }

    @Test
    fun rotatingTheRoot_MovesTheChildsVertices() {
        // Root turned 90° about Z at its joint: a vertex at the child joint (0,2,0) swings to (-1,1,0)
        val turned = Matrix4f().translation(0f, 1f, 0f).rotateZ(Math.toRadians(90.0).toFloat())
        PreviewSkinning.palette(skeleton, parents, locals(turned), world, out)
        val v = out[1].transformPosition(Vector3f(0f, 2f, 0f))
        assertEquals(-1f, v.x, 1e-5f)
        assertEquals(1f, v.y, 1e-5f)
        assertTrue(out[2].equals(Matrix4f(), 0f), "unused palette slots are identity")
    }

    @Test
    fun theSharedSkeleton_IsNotWritten() {
        val before = Matrix4f(child.worldTransform)
        PreviewSkinning.palette(skeleton, parents, locals(Matrix4f().rotateY(1f)), world, out)
        assertEquals(before, child.worldTransform)
        assertEquals(Matrix4f().translation(0f, 1f, 0f), root.localTransform)
    }
}
