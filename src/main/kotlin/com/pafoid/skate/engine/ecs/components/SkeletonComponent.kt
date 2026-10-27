package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.SkeletonPose
import com.pafoid.skate.engine.utils.SkeletonMath
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import java.nio.FloatBuffer

@Serializable
data class SkeletonComponent(
    val pose: SkeletonPose
) : Component() {
    @Transient
    var selectedBone: Bone? = null

    @Transient
    val matrixPalette = Array(pose.skeleton.boneCount) { Matrix4f() }

    /** Incremented whenever [matrixPalette] changes; the renderer re-uploads only when this differs from what the GPU holds. */
    @Transient
    var paletteVersion = 0
        private set

    @Transient
    private var packedVersion = -1

    // Skins above MAX_BONES are truncated (the shader array is fixed-size).
    @Transient
    private val packed: FloatBuffer = BufferUtils.createFloatBuffer(minOf(pose.skeleton.boneCount, MAX_BONES) * 16)

    /** Model-space bind-pose bounding sphere, used for visibility tests. */
    @Transient
    val boundsCenter = Vector3f()

    @Transient
    var boundsRadius = 0f
        private set

    init {
        // Compute initial pose
        SkeletonMath.buildSkinMatrices(pose, matrixPalette)
        markPaletteDirty()
        computeBounds()
    }

    fun markPaletteDirty() {
        paletteVersion++
    }

    /** Column-major palette ready for a UBO upload. Repacked only when [paletteVersion] changed. */
    fun packedPalette(): FloatBuffer {
        if (packedVersion != paletteVersion) {
            val count = packed.capacity() / 16
            for (i in 0 until count) matrixPalette[i].get(i * 16, packed)
            packed.position(0).limit(count * 16)
            packedVersion = paletteVersion
        }
        return packed
    }

    private fun computeBounds() {
        val positions = pose.skeleton.getAllBones().map { Matrix4f(it.inverseBindMatrix).invert().getTranslation(Vector3f()) }
        if (positions.isEmpty()) return
        val c = Vector3f()
        positions.forEach { c.add(it) }
        c.div(positions.size.toFloat())
        boundsCenter.set(c)
        // Bones are joints, not surface points: pad so the skinned volume around them stays inside.
        boundsRadius = (positions.maxOf { it.distance(c) } + 0.5f) * 1.25f
    }

    override fun reset() {
        super.reset()
        matrixPalette.forEach { it.set(Matrix4f()) }
        pose.reset()
        SkeletonMath.buildSkinMatrices(pose, matrixPalette)
        markPaletteDirty()
    }

    companion object {
        const val MAX_BONES = 100
    }
}