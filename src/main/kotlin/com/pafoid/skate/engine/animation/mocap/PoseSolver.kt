package com.pafoid.skate.engine.animation.mocap

import com.pafoid.skate.engine.animation.edit.Filters
import com.pafoid.skate.engine.animation.edit.FootLock
import com.pafoid.skate.engine.animation.edit.LegChain
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkKey
import com.pafoid.skate.engine.animation.edit.SkTrack
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.animation.mocap.MocapSkeleton.side
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f

/** How much jitter to remove (One-Euro filter on the landmarks). */
enum class Smoothing(val minCutoffHz: Float, val beta: Float) {
    NONE(0f, 0f), LOW(3f, 0.5f), MEDIUM(1.5f, 0.3f), HIGH(0.7f, 0.1f)
}

data class SolveOptions(
    val smoothing: Smoothing = Smoothing.MEDIUM,
    /** Move the hips through the scene using the stance foot; off keeps the motion in place (hips over the origin). */
    val rootMotion: Boolean = false,
    /** Plant feet while they touch the ground (only with [rootMotion]: in place, a planted foot must slide). */
    val footLock: Boolean = true,
    /** A foot touches the ground when it is within this height of the lowest foot (metres). */
    val contactHeight: Float = 0.04f,
)

/** The solved motion on the reference rig, ready for retargeting or BVH export. */
class SolvedMotion(val skeleton: Skeleton, val clip: SkClip, val proportions: BodyProportions, val contacts: FootContacts)

/** Per frame: is the left / right foot planted. */
class FootContacts(val left: BooleanArray, val right: BooleanArray)

/**
 * Turns landmark positions into joint rotations on the [MocapSkeleton] reference rig (ANI-17, ANI-12 §3.3).
 *
 * - Hips and chest orientations come from the hip and shoulder lines (left minus right = +X) and the spine (+Y); the
 *   three spine bones share the turn between them. The head uses the ear line.
 * - Limbs swing from their parent so each segment points at the next landmark (shoulder→elbow→wrist,
 *   hip→knee→ankle→toe). Twist around a limb's own axis is not measured (MediaPipe gives too little for it).
 * - The lowest foot is put on the ground; optional root motion follows the stance foot.
 *
 * Pure: no files, no GL. Frames without a detection are filled from their neighbours.
 */
object PoseSolver {

    fun solve(seq: LandmarkSequence, name: String, options: SolveOptions = SolveOptions()): SolvedMotion {
        require(seq.detectedFrames > 0) { "No person was detected in the video" }
        val frames = smooth(fillGaps(seq), seq.fps, options.smoothing)
        val proportions = BodyProportions.measure(seq)
        val skeleton = MocapSkeleton.build(proportions)
        val times = FloatArray(seq.frames.size) { seq.frames[it].time }

        val rotations = HashMap<String, MutableList<Quaternionf>>()
        val hipsPositions = ArrayList<Vector3f>()
        val contacts = FootContacts(BooleanArray(frames.size), BooleanArray(frames.size))
        val root = Vector3f()
        var previousStance: Pair<Boolean, Vector3f>? = null
        val restHips = Vector3f(0f, proportions.hipsHeight, 0f)

        for ((f, points) in frames.withIndex()) {
            val globals = solveFrame(points, skeleton)
            locals(skeleton.rootBone, null, globals) { bone, q -> rotations.getOrPut(bone) { ArrayList() }.add(q) }

            val leftFoot = footPoint(points, left = true)
            val rightFoot = footPoint(points, left = false)
            val ground = minOf(leftFoot.y, rightFoot.y, points[PoseLandmark.LEFT_ANKLE].y, points[PoseLandmark.RIGHT_ANKLE].y)
            contacts.left[f] = leftFoot.y - ground <= options.contactHeight
            contacts.right[f] = rightFoot.y - ground <= options.contactHeight

            if (options.rootMotion) {
                val stanceLeft = leftFoot.y <= rightFoot.y
                val stance = if (stanceLeft) leftFoot else rightFoot
                val prev = previousStance
                // While the same foot stays down, the body moves by the opposite of that foot's motion around the hips.
                if (prev != null && prev.first == stanceLeft) root.add(prev.second.x - stance.x, 0f, prev.second.z - stance.z)
                previousStance = stanceLeft to Vector3f(stance)
            }
            hipsPositions += Vector3f(root.x, -ground, root.z)
        }

        val tracks = ArrayList<SkTrack>()
        tracks += SkTrack(MocapSkeleton.HIPS, AnimationPath.TRANSLATION, InterpolationType.LINEAR,
            hipsPositions.mapIndexed { i, p -> SkKey(times[i], listOf(p.x - restHips.x, p.y - restHips.y, p.z - restHips.z)) })
        for (bone in skeleton.getAllBones()) {
            val qs = rotations[bone.name] ?: continue
            for (i in 1 until qs.size) if (qs[i - 1].dot(qs[i]) < 0f) qs[i].set(-qs[i].x, -qs[i].y, -qs[i].z, -qs[i].w)
            tracks += SkTrack(bone.name, AnimationPath.ROTATION, InterpolationType.LINEAR,
                qs.mapIndexed { i, q -> SkKey(times[i], listOf(q.x, q.y, q.z, q.w)) })
        }
        val duration = times.last().coerceAtLeast(1f / seq.fps)
        var clip = SkClip(name = name, fps = seq.fps, duration = duration, loop = false, humanoid = true, tracks = tracks)

        if (options.rootMotion && options.footLock) {
            for (left in listOf(true, false)) {
                val leg = LegChain(side(left, "UpLeg"), side(left, "Leg"), side(left, "Foot"))
                for ((a, b) in ranges(if (left) contacts.left else contacts.right, minLength = 3)) {
                    clip = FootLock.apply(clip, skeleton, leg, times[a], times[b], blendSeconds = 2f / seq.fps)
                }
            }
        }
        return SolvedMotion(skeleton, clip, proportions, contacts)
    }

    /** Model-space rotation of every solved bone for one frame of 33 points. */
    internal fun solveFrame(p: List<Vector3f>, skeleton: Skeleton): Map<String, Quaternionf> {
        val lm = PoseLandmark
        val hipC = mid(p[lm.LEFT_HIP], p[lm.RIGHT_HIP])
        val shC = mid(p[lm.LEFT_SHOULDER], p[lm.RIGHT_SHOULDER])
        val up = Vector3f(shC).sub(hipC)
        val hips = basis(Vector3f(p[lm.LEFT_HIP]).sub(p[lm.RIGHT_HIP]), up)
        val chest = basis(Vector3f(p[lm.LEFT_SHOULDER]).sub(p[lm.RIGHT_SHOULDER]), up)
        val earMid = mid(p[lm.LEFT_EAR], p[lm.RIGHT_EAR])
        val head = basis(Vector3f(p[lm.LEFT_EAR]).sub(p[lm.RIGHT_EAR]), Vector3f(earMid).sub(shC), fallback = chest)

        val g = HashMap<String, Quaternionf>()
        g[MocapSkeleton.HIPS] = hips
        g[MocapSkeleton.SPINE] = Quaternionf(hips).slerp(chest, 1f / 3f)
        g[MocapSkeleton.SPINE1] = Quaternionf(hips).slerp(chest, 2f / 3f)
        g[MocapSkeleton.SPINE2] = Quaternionf(chest)
        g[MocapSkeleton.NECK] = Quaternionf(chest).slerp(head, 0.5f)
        g[MocapSkeleton.HEAD] = head

        val rest = restOffsets(skeleton)
        for (left in listOf(true, false)) {
            fun i(l: Int, r: Int) = if (left) l else r
            g[side(left, "Shoulder")] = Quaternionf(chest)
            val arm = swing(chest, rest.getValue(side(left, "ForeArm")), p[i(lm.LEFT_SHOULDER, lm.RIGHT_SHOULDER)], p[i(lm.LEFT_ELBOW, lm.RIGHT_ELBOW)])
            g[side(left, "Arm")] = arm
            val fore = swing(arm, rest.getValue(side(left, "Hand")), p[i(lm.LEFT_ELBOW, lm.RIGHT_ELBOW)], p[i(lm.LEFT_WRIST, lm.RIGHT_WRIST)])
            g[side(left, "ForeArm")] = fore
            g[side(left, "Hand")] = Quaternionf(fore)
            val thigh = swing(hips, rest.getValue(side(left, "Leg")), p[i(lm.LEFT_HIP, lm.RIGHT_HIP)], p[i(lm.LEFT_KNEE, lm.RIGHT_KNEE)])
            g[side(left, "UpLeg")] = thigh
            val shin = swing(thigh, rest.getValue(side(left, "Foot")), p[i(lm.LEFT_KNEE, lm.RIGHT_KNEE)], p[i(lm.LEFT_ANKLE, lm.RIGHT_ANKLE)])
            g[side(left, "Leg")] = shin
            val foot = swing(shin, rest.getValue(side(left, "ToeBase")), p[i(lm.LEFT_ANKLE, lm.RIGHT_ANKLE)], p[i(lm.LEFT_FOOT_INDEX, lm.RIGHT_FOOT_INDEX)])
            g[side(left, "Foot")] = foot
            g[side(left, "ToeBase")] = Quaternionf(foot)
        }
        return g
    }

    /** Bind offset of each bone from its parent (the parent's segment direction at rest), by child name. */
    private fun restOffsets(skeleton: Skeleton): Map<String, Vector3f> =
        skeleton.getAllBones().associate { it.name to it.bindLocalTransform.getTranslation(Vector3f()) }

    /** [parent] turned the least so the segment whose rest direction is [restChildOffset] points from [a] to [b]. */
    private fun swing(parent: Quaternionf, restChildOffset: Vector3f, a: Vector3f, b: Vector3f): Quaternionf {
        val target = Vector3f(b).sub(a)
        val current = parent.transform(Vector3f(restChildOffset))
        if (target.lengthSquared() < 1e-10f || current.lengthSquared() < 1e-10f) return Quaternionf(parent)
        return Quaternionf().rotationTo(current.normalize(), target.normalize()).mul(parent)
    }

    /** Rotation whose X axis is [x] and Y axis is [y] (made orthogonal to X); Z = X × Y. */
    private fun basis(x: Vector3f, y: Vector3f, fallback: Quaternionf? = null): Quaternionf {
        if (x.lengthSquared() < 1e-10f || y.lengthSquared() < 1e-10f) return Quaternionf(fallback ?: Quaternionf())
        val xn = Vector3f(x).normalize()
        val yn = Vector3f(y).sub(Vector3f(xn).mul(y.dot(xn)))
        if (yn.lengthSquared() < 1e-10f) return Quaternionf(fallback ?: Quaternionf())
        yn.normalize()
        val zn = Vector3f(xn).cross(yn)
        return Quaternionf().setFromNormalized(Matrix3f(xn, yn, zn))
    }

    /** Converts model-space rotations to local ones (bind rotations are identity on the reference rig). */
    private fun locals(bone: Bone, parent: Quaternionf?, globals: Map<String, Quaternionf>, emit: (String, Quaternionf) -> Unit) {
        val g = globals[bone.name] ?: Quaternionf(parent ?: Quaternionf())
        emit(bone.name, if (parent == null) Quaternionf(g) else Quaternionf(parent).invert().mul(g).normalize())
        bone.children.forEach { locals(it, g, globals, emit) }
    }

    private fun footPoint(p: List<Vector3f>, left: Boolean): Vector3f =
        if (left) mid(p[PoseLandmark.LEFT_HEEL], p[PoseLandmark.LEFT_FOOT_INDEX]) else mid(p[PoseLandmark.RIGHT_HEEL], p[PoseLandmark.RIGHT_FOOT_INDEX])

    private fun mid(a: Vector3f, b: Vector3f) = Vector3f(a).add(b).mul(0.5f)

    /** Frames as 33 points each; frames with no detection copy the nearest detected frame (linear between two). */
    internal fun fillGaps(seq: LandmarkSequence): List<List<Vector3f>> {
        val detected = seq.frames.indices.filter { seq.frames[it].detected }
        return seq.frames.indices.map { i ->
            if (seq.frames[i].detected) return@map points(seq.frames[i])
            val before = detected.lastOrNull { it < i }
            val after = detected.firstOrNull { it > i }
            when {
                before == null -> points(seq.frames[after!!])
                after == null -> points(seq.frames[before])
                else -> {
                    val t = (i - before).toFloat() / (after - before)
                    points(seq.frames[before]).zip(points(seq.frames[after])) { a, b -> Vector3f(a).lerp(b, t) }
                }
            }
        }
    }

    private fun points(frame: LandmarkFrame) = List(PoseLandmark.COUNT) { frame.point(it) }

    /** One-Euro filter per landmark coordinate, reusing the clip filters (ANI-13). */
    internal fun smooth(frames: List<List<Vector3f>>, fps: Float, smoothing: Smoothing): List<List<Vector3f>> {
        if (smoothing == Smoothing.NONE || frames.size < 3) return frames
        val out = frames.map { f -> f.map { Vector3f(it) } }
        for (l in 0 until PoseLandmark.COUNT) {
            val track = SkTrack("lm$l", AnimationPath.TRANSLATION, keys = frames.indices.map { i ->
                val v = frames[i][l]; SkKey(i / fps, listOf(v.x, v.y, v.z))
            })
            val filtered = Filters.oneEuro(track, smoothing.minCutoffHz, smoothing.beta)
            filtered.keys.forEachIndexed { i, k -> out[i][l].set(k.value[0], k.value[1], k.value[2]) }
        }
        return out
    }

    /** Index ranges (inclusive) where [flags] stays true for at least [minLength] frames. */
    internal fun ranges(flags: BooleanArray, minLength: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var start = -1
        for (i in 0..flags.size) {
            val on = i < flags.size && flags[i]
            if (on && start < 0) start = i
            if (!on && start >= 0) { if (i - start >= minLength) out += start to i - 1; start = -1 }
        }
        return out
    }
}
