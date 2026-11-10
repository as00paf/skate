package com.pafoid.skate.engine.animation.mocap

import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Synthetic MediaPipe landmarks: poses the [MocapSkeleton] reference rig with known local rotations and hips offset,
 * then reads the 33 points off the posed rig (hip-centred, engine axes), like MediaPipe's world landmarks.
 */
object SyntheticPose {
    val proportions = BodyProportions()
    val skeleton: Skeleton = MocapSkeleton.build(proportions)

    fun deg(d: Float) = Math.toRadians(d.toDouble()).toFloat()

    /** Model-space matrices of the rig posed with [locals] (bone name -> local rotation) and [hipsOffset]. */
    fun globals(locals: Map<String, Quaternionf>, hipsOffset: Vector3f = Vector3f()): Map<String, Matrix4f> {
        val out = HashMap<String, Matrix4f>()
        fun walk(b: Bone, parent: Matrix4f) {
            val t = b.bindLocalTransform.getTranslation(Vector3f())
            if (b.name == MocapSkeleton.HIPS) t.add(hipsOffset)
            val g = Matrix4f(parent).translate(t).rotate(locals[b.name] ?: Quaternionf())
            out[b.name] = g
            b.children.forEach { walk(it, g) }
        }
        walk(skeleton.rootBone, Matrix4f())
        return out
    }

    fun positions(locals: Map<String, Quaternionf>, hipsOffset: Vector3f = Vector3f()) =
        globals(locals, hipsOffset).mapValues { it.value.getTranslation(Vector3f()) }

    /** 33 landmark points (engine axes, hips-centred) for a pose, plus where the hips are in the world. */
    fun landmarks(locals: Map<String, Quaternionf>, hipsOffset: Vector3f = Vector3f()): FloatArray {
        val g = globals(locals, hipsOffset)
        fun pos(bone: String) = g.getValue(bone).getTranslation(Vector3f())
        fun at(bone: String, x: Float, y: Float, z: Float) = g.getValue(bone).transformPosition(Vector3f(x, y, z))
        val p = Array(PoseLandmark.COUNT) { Vector3f() }
        val lm = PoseLandmark
        for (left in listOf(true, false)) {
            fun s(name: String) = MocapSkeleton.side(left, name)
            fun i(l: Int, r: Int) = if (left) l else r
            p[i(lm.LEFT_SHOULDER, lm.RIGHT_SHOULDER)] = pos(s("Arm"))
            p[i(lm.LEFT_ELBOW, lm.RIGHT_ELBOW)] = pos(s("ForeArm"))
            p[i(lm.LEFT_WRIST, lm.RIGHT_WRIST)] = pos(s("Hand"))
            p[i(lm.LEFT_HIP, lm.RIGHT_HIP)] = pos(s("UpLeg"))
            p[i(lm.LEFT_KNEE, lm.RIGHT_KNEE)] = pos(s("Leg"))
            p[i(lm.LEFT_ANKLE, lm.RIGHT_ANKLE)] = pos(s("Foot"))
            p[i(lm.LEFT_FOOT_INDEX, lm.RIGHT_FOOT_INDEX)] = pos(s("ToeBase"))
            p[i(lm.LEFT_HEEL, lm.RIGHT_HEEL)] = at(s("Foot"), 0f, -proportions.ankleHeight, -0.25f * proportions.footLength)
            p[i(lm.LEFT_EAR, lm.RIGHT_EAR)] = at(MocapSkeleton.HEAD, if (left) 0.07f else -0.07f, 0f, 0f)
        }
        p[lm.NOSE] = at(MocapSkeleton.HEAD, 0f, 0.03f, 0.1f)
        val hipC = Vector3f(p[lm.LEFT_HIP]).add(p[lm.RIGHT_HIP]).mul(0.5f)
        return FloatArray(PoseLandmark.COUNT * 3) { val v = p[it / 3]; (when (it % 3) { 0 -> v.x; 1 -> v.y; else -> v.z }) - (when (it % 3) { 0 -> hipC.x; 1 -> hipC.y; else -> hipC.z }) }
    }

    fun frame(time: Float, points: FloatArray) = LandmarkFrame(time, points, FloatArray(PoseLandmark.COUNT) { 1f }, null)

    fun sequence(fps: Float, poses: List<FloatArray>) = LandmarkSequence(fps, 640, 480, poses.mapIndexed { i, p -> frame(i / fps, p) })

    /** The same frames as MediaPipe's JSON (raw axes: x right, y down, z away from the camera). */
    fun json(seq: LandmarkSequence): String {
        val frames = seq.frames.joinToString(",") { f ->
            val world = f.world?.let { w ->
                (0 until PoseLandmark.COUNT).joinToString(",", "[", "]") { i -> "[${w[i * 3]},${-w[i * 3 + 1]},${-w[i * 3 + 2]},0.99]" }
            } ?: "null"
            val image = (0 until PoseLandmark.COUNT).joinToString(",", "[", "]") { "[0.5,0.5]" }
            """{"t":${f.time},"world":$world,"image":$image}"""
        }
        return """{"version":1,"source":"test","fps":${seq.fps},"width":${seq.width},"height":${seq.height},"frames":[$frames]}"""
    }

    /** A pose with turned hips, a lowered left arm, a bent right elbow, a stepping right leg and a bent knee. */
    val samplePose: Map<String, Quaternionf> = mapOf(
        MocapSkeleton.HIPS to Quaternionf().rotationY(deg(25f)),
        "LeftArm" to Quaternionf().rotationZ(deg(-60f)),
        "RightArm" to Quaternionf().rotationZ(deg(50f)),
        "RightForeArm" to Quaternionf().rotationY(deg(70f)),
        "RightUpLeg" to Quaternionf().rotationX(deg(-35f)),
        "RightLeg" to Quaternionf().rotationX(deg(45f)),
        "LeftLeg" to Quaternionf().rotationX(deg(20f)),
    )
}
