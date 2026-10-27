package com.pafoid.skate.engine.animation.ik

import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Vector3f

/** Receives world-space debug line segments. Implemented by the renderer's debug drawer. */
fun interface IkDebugSink {
    fun line(from: Vector3f, to: Vector3f, color: Vector3f)
}

/**
 * Emits the IK state as debug lines: solved chains (green), targets (red crosses), the solved end bone's
 * miss distance (yellow), pole hints (blue), and look-at rays (cyan) with their targets (magenta).
 * Pure: it only reads the rig and the post-IK pose.
 */
object IkDebugDraw {
    val CHAIN = Vector3f(0.2f, 1f, 0.2f)
    val TARGET = Vector3f(1f, 0.2f, 0.2f)
    val MISS = Vector3f(1f, 1f, 0f)
    val POLE = Vector3f(0.3f, 0.5f, 1f)
    val LOOK_RAY = Vector3f(0f, 1f, 1f)
    val LOOK_TARGET = Vector3f(1f, 0f, 1f)
    const val CROSS_SIZE = 0.08f
    const val LOOK_RAY_LENGTH = 0.5f

    /** [locals] must be the final (post-IK) local pose. [modelToWorld] null means the skeleton sits at the world origin. */
    fun collect(rig: IkRig, skeleton: Skeleton, locals: Array<Matrix4f>, modelToWorld: Matrix4f?, sink: IkDebugSink) {
        if (!rig.isActive) return
        val parents = IkPass.parentsOf(skeleton)
        val toWorld = modelToWorld ?: Matrix4f()

        fun bonePos(index: Int) =
            toWorld.transformPosition(IkSolvers.globalOf(parents, locals, index).getTranslation(Vector3f()))

        fun targetPos(p: Vector3f, space: IkSpace) =
            if (space == IkSpace.MODEL) toWorld.transformPosition(Vector3f(p)) else Vector3f(p)

        for (c in rig.twoBone) {
            if (c.weight <= 0f) continue
            val root = skeleton.getBoneByName(c.rootBone)?.index ?: continue
            val mid = skeleton.getBoneByName(c.midBone)?.index ?: continue
            val end = skeleton.getBoneByName(c.endBone)?.index ?: continue
            val a = bonePos(root)
            val b = bonePos(mid)
            val e = bonePos(end)
            val t = targetPos(c.target, c.space)
            sink.line(a, b, CHAIN)
            sink.line(b, e, CHAIN)
            cross(t, TARGET, sink)
            sink.line(e, t, MISS)
            c.pole?.let {
                val p = targetPos(it, c.space)
                cross(p, POLE, sink)
                sink.line(b, p, POLE)
            }
        }

        for (c in rig.lookAt) {
            if (c.weight <= 0f) continue
            val bone = skeleton.getBoneByName(c.bone)?.index ?: continue
            val origin = bonePos(bone)
            val forward = toWorld.transformDirection(
                IkSolvers.globalOf(parents, locals, bone).transformDirection(Vector3f(c.axis))
            ).normalize()
            sink.line(origin, Vector3f(forward).mul(LOOK_RAY_LENGTH).add(origin), LOOK_RAY)
            val t = targetPos(c.target, c.space)
            cross(t, LOOK_TARGET, sink)
            sink.line(origin, t, LOOK_TARGET)
        }
    }

    private fun cross(p: Vector3f, color: Vector3f, sink: IkDebugSink) {
        val s = CROSS_SIZE
        sink.line(Vector3f(p).add(-s, 0f, 0f), Vector3f(p).add(s, 0f, 0f), color)
        sink.line(Vector3f(p).add(0f, -s, 0f), Vector3f(p).add(0f, s, 0f), color)
        sink.line(Vector3f(p).add(0f, 0f, -s), Vector3f(p).add(0f, 0f, s), color)
    }
}
