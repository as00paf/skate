package com.pafoid.skate.engine.animation.mocap

import com.pafoid.skate.engine.animation.edit.KeyEditing
import com.pafoid.skate.engine.animation.edit.SkClip
import com.pafoid.skate.engine.animation.edit.SkKey
import com.pafoid.skate.engine.animation.edit.SkTrack
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.Locale
import kotlin.math.roundToInt

/** A skeleton and its motion, as read from (or written to) a BVH file. */
class BvhMotion(val skeleton: Skeleton, val clip: SkClip)

/**
 * BVH (Biovision hierarchy) reading and writing, pure Kotlin. In the engine's channel space a joint's rest is
 * `bind = translation(OFFSET)` with no rotation, its rotation channels are the channel rotation, and position
 * channels are the channel translation (`local = bind * T(position) * R`).
 */
object Bvh {
    /**
     * Writes [clip] sampled every frame at [SkClip.fps]. Joints: every bone of [skeleton]; the root gets position and
     * rotation channels, the others rotation only, in `Zrotation Xrotation Yrotation` order (degrees).
     */
    fun write(skeleton: Skeleton, clip: SkClip): String {
        val sb = StringBuilder("HIERARCHY\n")
        val order = ArrayList<Bone>()
        fun joint(b: Bone, depth: Int) {
            val pad = "\t".repeat(depth)
            val o = b.bindLocalTransform.getTranslation(Vector3f())
            sb.append(pad).append(if (depth == 0) "ROOT " else "JOINT ").append(b.name).append('\n')
            sb.append(pad).append("{\n")
            sb.append(pad).append("\tOFFSET ").append(num(o.x)).append(' ').append(num(o.y)).append(' ').append(num(o.z)).append('\n')
            sb.append(pad).append(if (depth == 0) "\tCHANNELS 6 Xposition Yposition Zposition Zrotation Xrotation Yrotation\n" else "\tCHANNELS 3 Zrotation Xrotation Yrotation\n")
            order += b
            if (b.children.isEmpty()) {
                sb.append(pad).append("\tEnd Site\n").append(pad).append("\t{\n").append(pad).append("\t\tOFFSET 0 0 0\n").append(pad).append("\t}\n")
            }
            b.children.forEach { joint(it, depth + 1) }
            sb.append(pad).append("}\n")
        }
        joint(skeleton.rootBone, 0)

        val frames = (clip.duration * clip.fps).roundToInt() + 1
        sb.append("MOTION\nFrames: ").append(frames).append("\nFrame Time: ").append(num(1f / clip.fps)).append('\n')
        val tracks = clip.tracks.associateBy { it.bone to it.path }
        val euler = Vector3f()
        for (f in 0 until frames) {
            val t = (f / clip.fps).coerceAtMost(clip.duration)
            val row = ArrayList<String>()
            for ((i, b) in order.withIndex()) {
                if (i == 0) {
                    // BVH root positions are absolute: OFFSET plus the channel translation.
                    val p = tracks[b.name to AnimationPath.TRANSLATION]?.let { KeyEditing.sample(it, t) } ?: FloatArray(3)
                    val o = b.bindLocalTransform.getTranslation(Vector3f())
                    row += num(o.x + p[0]); row += num(o.y + p[1]); row += num(o.z + p[2])
                }
                val q = tracks[b.name to AnimationPath.ROTATION]?.let { KeyEditing.sample(it, t) } ?: floatArrayOf(0f, 0f, 0f, 1f)
                Quaternionf(q[0], q[1], q[2], q[3]).getEulerAnglesZXY(euler)
                row += num(Math.toDegrees(euler.z.toDouble()).toFloat())
                row += num(Math.toDegrees(euler.x.toDouble()).toFloat())
                row += num(Math.toDegrees(euler.y.toDouble()).toFloat())
            }
            sb.append(row.joinToString(" ")).append('\n')
        }
        return sb.toString()
    }

    /**
     * Reads a BVH file. [unitScale] converts its lengths to metres; null guesses: files whose rest skeleton is taller
     * than 10 units are taken to be in centimetres (×0.01), others in metres. That guess is a heuristic.
     */
    fun read(text: String, name: String, unitScale: Float? = null): BvhMotion {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var i = 0
        fun next() = tokens.getOrNull(i++) ?: throw IllegalArgumentException("Unexpected end of BVH file")
        fun expect(s: String) { val t = next(); require(t.equals(s, ignoreCase = true)) { "BVH: expected '$s', found '$t'" } }

        class Joint(val name: String, val offset: Vector3f, val channels: List<String>, val parent: Joint?) {
            val children = ArrayList<Joint>()
        }
        val joints = ArrayList<Joint>()

        fun parseJoint(name: String, parent: Joint?): Joint {
            expect("{")
            expect("OFFSET")
            val offset = Vector3f(next().toFloat(), next().toFloat(), next().toFloat())
            expect("CHANNELS")
            val channels = List(next().toInt()) { next() }
            val joint = Joint(name, offset, channels, parent).also { joints += it; parent?.children?.add(it) }
            while (true) {
                when (next().uppercase(Locale.ROOT)) {
                    "JOINT" -> parseJoint(next(), joint)
                    "END" -> { next(); expect("{"); expect("OFFSET"); next(); next(); next(); expect("}") }
                    "}" -> return joint
                    else -> throw IllegalArgumentException("BVH: unexpected token in joint ${joint.name}")
                }
            }
        }

        expect("HIERARCHY")
        expect("ROOT")
        val root = parseJoint(next(), null)
        expect("MOTION")
        expect("Frames:")
        val frameCount = next().toInt()
        expect("Frame"); expect("Time:")
        val frameTime = next().toFloat()
        require(frameTime > 0f) { "BVH: frame time must be positive" }

        val scale = unitScale ?: run {
            val ys = HashMap<Joint, Float>()
            for (j in joints) ys[j] = (j.parent?.let { ys.getValue(it) } ?: 0f) + j.offset.y
            val height = (ys.values.maxOrNull() ?: 0f) - (ys.values.minOrNull() ?: 0f)
            if (height > 10f) 0.01f else 1f
        }

        val bones = joints.mapIndexed { idx, j -> Bone(idx, j.name, Matrix4f().translation(Vector3f(j.offset).mul(scale))) }
        joints.forEachIndexed { idx, j -> j.children.forEach { c -> bones[idx].addChild(bones[joints.indexOf(c)]) } }
        val skeleton = Skeleton(bones[joints.indexOf(root)], bones.size)

        val rot = joints.associateWith { ArrayList<SkKey>() }
        val pos = joints.filter { j -> j.channels.any { it.endsWith("position", ignoreCase = true) } }.associateWith { ArrayList<SkKey>() }
        for (f in 0 until frameCount) {
            val t = f * frameTime
            for (j in joints) {
                val q = Quaternionf()
                val p = Vector3f()
                for (c in j.channels) {
                    val v = next().toFloat()
                    when (c.lowercase(Locale.ROOT)) {
                        "xposition" -> p.x = v * scale
                        "yposition" -> p.y = v * scale
                        "zposition" -> p.z = v * scale
                        "xrotation" -> q.rotateX(Math.toRadians(v.toDouble()).toFloat())
                        "yrotation" -> q.rotateY(Math.toRadians(v.toDouble()).toFloat())
                        "zrotation" -> q.rotateZ(Math.toRadians(v.toDouble()).toFloat())
                        else -> throw IllegalArgumentException("BVH: unknown channel $c")
                    }
                }
                q.normalize()
                rot.getValue(j) += SkKey(t, listOf(q.x, q.y, q.z, q.w))
                // Position channels are absolute in BVH; the bind already holds OFFSET.
                pos[j]?.add(SkKey(t, listOf(p.x - j.offset.x * scale, p.y - j.offset.y * scale, p.z - j.offset.z * scale)))
            }
        }
        val tracks = ArrayList<SkTrack>()
        for (j in joints) {
            pos[j]?.let { tracks += SkTrack(j.name, AnimationPath.TRANSLATION, InterpolationType.LINEAR, it) }
            tracks += SkTrack(j.name, AnimationPath.ROTATION, InterpolationType.LINEAR, continuous(rot.getValue(j)))
        }
        val duration = ((frameCount - 1).coerceAtLeast(1)) * frameTime
        val clip = SkClip(name = name, fps = 1f / frameTime, duration = duration, loop = false, tracks = tracks)
        return BvhMotion(skeleton, clip)
    }

    private fun continuous(keys: List<SkKey>): List<SkKey> {
        val out = ArrayList<SkKey>(keys.size)
        for (k in keys) {
            val prev = out.lastOrNull()?.value
            val dot = prev?.let { p -> (0 until 4).sumOf { (p[it] * k.value[it]).toDouble() } } ?: 1.0
            out += if (dot < 0) k.copy(value = k.value.map { -it }) else k
        }
        return out
    }

    private fun num(v: Float) = String.format(Locale.ROOT, "%.6f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
}
