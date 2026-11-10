package com.pafoid.skate.engine.animation.mocap

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Vector3f

/** Body measurements in metres, taken from the landmarks (medians over the clip). */
data class BodyProportions(
    val hipHalfWidth: Float = 0.1f,
    val thigh: Float = 0.42f,
    val shin: Float = 0.42f,
    val ankleHeight: Float = 0.07f,
    val footLength: Float = 0.16f,
    val spine: Float = 0.5f,
    val neck: Float = 0.18f,
    val shoulderHalfWidth: Float = 0.18f,
    val upperArm: Float = 0.28f,
    val forearm: Float = 0.26f,
) {
    /** Rest height of the hips above the ground. */
    val hipsHeight: Float get() = thigh + shin + ankleHeight

    companion object {
        /** Medians over detected frames; falls back to the defaults for lengths that cannot be measured. */
        fun measure(seq: LandmarkSequence): BodyProportions {
            val frames = seq.frames.filter { it.detected }
            if (frames.isEmpty()) return BodyProportions()
            fun median(f: (LandmarkFrame) -> Float): Float {
                val v = frames.map(f).filter { it.isFinite() && it > 1e-3f }.sorted()
                return if (v.isEmpty()) Float.NaN else v[v.size / 2]
            }
            fun d(fr: LandmarkFrame, a: Int, b: Int) = fr.point(a).distance(fr.point(b))
            fun mid(fr: LandmarkFrame, a: Int, b: Int) = fr.point(a).add(fr.point(b)).mul(0.5f)
            fun avg(a: Float, b: Float) = (a + b) / 2f
            val p = PoseLandmark
            val defaults = BodyProportions()
            fun or(v: Float, d: Float) = if (v.isNaN()) d else v
            return BodyProportions(
                hipHalfWidth = or(median { d(it, p.LEFT_HIP, p.RIGHT_HIP) } / 2f, defaults.hipHalfWidth),
                thigh = or(median { avg(d(it, p.LEFT_HIP, p.LEFT_KNEE), d(it, p.RIGHT_HIP, p.RIGHT_KNEE)) }, defaults.thigh),
                shin = or(median { avg(d(it, p.LEFT_KNEE, p.LEFT_ANKLE), d(it, p.RIGHT_KNEE, p.RIGHT_ANKLE)) }, defaults.shin),
                ankleHeight = or(median { avg(d(it, p.LEFT_ANKLE, p.LEFT_HEEL), d(it, p.RIGHT_ANKLE, p.RIGHT_HEEL)) }, defaults.ankleHeight),
                footLength = or(median { avg(d(it, p.LEFT_HEEL, p.LEFT_FOOT_INDEX), d(it, p.RIGHT_HEEL, p.RIGHT_FOOT_INDEX)) }, defaults.footLength),
                spine = or(median { mid(it, p.LEFT_HIP, p.RIGHT_HIP).distance(mid(it, p.LEFT_SHOULDER, p.RIGHT_SHOULDER)) }, defaults.spine),
                neck = or(median { mid(it, p.LEFT_SHOULDER, p.RIGHT_SHOULDER).distance(mid(it, p.LEFT_EAR, p.RIGHT_EAR)) }, defaults.neck),
                shoulderHalfWidth = or(median { d(it, p.LEFT_SHOULDER, p.RIGHT_SHOULDER) } / 2f, defaults.shoulderHalfWidth),
                upperArm = or(median { avg(d(it, p.LEFT_SHOULDER, p.LEFT_ELBOW), d(it, p.RIGHT_SHOULDER, p.RIGHT_ELBOW)) }, defaults.upperArm),
                forearm = or(median { avg(d(it, p.LEFT_ELBOW, p.LEFT_WRIST), d(it, p.RIGHT_ELBOW, p.RIGHT_WRIST)) }, defaults.forearm),
            )
        }
    }
}

/**
 * The reference rig mocap is solved onto: a T-pose humanoid with Mixamo bone names (so `HumanoidBone` maps it to any
 * rig) and identity bind rotations, facing +Z with its left on +X, feet on the ground. Retargeting (ANI-16) then
 * carries the motion onto the project's rig.
 */
object MocapSkeleton {
    const val HIPS = "Hips"
    const val SPINE = "Spine"
    const val SPINE1 = "Spine1"
    const val SPINE2 = "Spine2"
    const val NECK = "Neck"
    const val HEAD = "Head"

    fun side(left: Boolean, bone: String) = (if (left) "Left" else "Right") + bone

    fun build(p: BodyProportions = BodyProportions()): Skeleton {
        var next = 0
        fun bone(name: String, x: Float, y: Float, z: Float, vararg children: Bone) =
            Bone(next++, name, Matrix4f().translation(x, y, z)).also { b -> children.forEach { b.addChild(it) } }

        fun arm(left: Boolean): Bone {
            val s = if (left) 1f else -1f
            return bone(side(left, "Shoulder"), 0.4f * p.shoulderHalfWidth * s, p.spine / 4f, 0f,
                bone(side(left, "Arm"), 0.6f * p.shoulderHalfWidth * s, 0f, 0f,
                    bone(side(left, "ForeArm"), p.upperArm * s, 0f, 0f,
                        bone(side(left, "Hand"), p.forearm * s, 0f, 0f))))
        }

        fun leg(left: Boolean): Bone {
            val s = if (left) 1f else -1f
            return bone(side(left, "UpLeg"), p.hipHalfWidth * s, 0f, 0f,
                bone(side(left, "Leg"), 0f, -p.thigh, 0f,
                    bone(side(left, "Foot"), 0f, -p.shin, 0f,
                        bone(side(left, "ToeBase"), 0f, -p.ankleHeight, p.footLength * 0.75f))))
        }

        val q = p.spine / 4f
        val root = bone(HIPS, 0f, p.hipsHeight, 0f,
            bone(SPINE, 0f, q, 0f, bone(SPINE1, 0f, q, 0f,
                bone(SPINE2, 0f, q, 0f, bone(NECK, 0f, q, 0f, bone(HEAD, 0f, p.neck, 0f)), arm(true), arm(false)))),
            leg(true), leg(false))
        return Skeleton(root, next)
    }

    /** Rest-pose model-space position of every bone, by name. */
    fun restPositions(skeleton: Skeleton): Map<String, Vector3f> {
        val out = HashMap<String, Vector3f>()
        fun walk(b: Bone, parent: Matrix4f) {
            val g = Matrix4f(parent).mul(b.bindLocalTransform)
            out[b.name] = g.getTranslation(Vector3f())
            b.children.forEach { walk(it, g) }
        }
        walk(skeleton.rootBone, Matrix4f())
        return out
    }
}
