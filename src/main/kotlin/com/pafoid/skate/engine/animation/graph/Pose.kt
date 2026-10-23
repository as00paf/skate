package com.pafoid.skate.engine.animation.graph

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/** Local-space TRS pose, one entry per bone index. Pure data, no GL or skeleton dependency. */
class Pose(val boneCount: Int) {
    val translations = FloatArray(boneCount * 3)
    val rotations = FloatArray(boneCount * 4).also { for (i in 0 until boneCount) it[i * 4 + 3] = 1f }
    val scales = FloatArray(boneCount * 3).also { it.fill(1f) }

    fun set(other: Pose) {
        require(other.boneCount == boneCount) { "Pose bone count mismatch" }
        other.translations.copyInto(translations)
        other.rotations.copyInto(rotations)
        other.scales.copyInto(scales)
    }

    fun setBone(index: Int, local: Matrix4f) {
        val t = local.getTranslation(Vector3f())
        val r = local.getUnnormalizedRotation(Quaternionf()).normalize()
        val s = local.getScale(Vector3f())
        translations[index * 3] = t.x; translations[index * 3 + 1] = t.y; translations[index * 3 + 2] = t.z
        rotations[index * 4] = r.x; rotations[index * 4 + 1] = r.y; rotations[index * 4 + 2] = r.z; rotations[index * 4 + 3] = r.w
        scales[index * 3] = s.x; scales[index * 3 + 1] = s.y; scales[index * 3 + 2] = s.z
    }

    fun toMatrix(index: Int, out: Matrix4f): Matrix4f = out.translationRotateScale(
        translations[index * 3], translations[index * 3 + 1], translations[index * 3 + 2],
        rotations[index * 4], rotations[index * 4 + 1], rotations[index * 4 + 2], rotations[index * 4 + 3],
        scales[index * 3], scales[index * 3 + 1], scales[index * 3 + 2]
    )

    companion object {
        /** Writes `lerp(a, b, w)` into [out]; rotations use shortest-path slerp. [out] may alias [a] or [b]. */
        fun blend(a: Pose, b: Pose, w: Float, out: Pose) {
            require(a.boneCount == b.boneCount && a.boneCount == out.boneCount) { "Pose bone count mismatch" }
            val weight = w.coerceIn(0f, 1f)
            for (i in a.translations.indices) {
                out.translations[i] = a.translations[i] + (b.translations[i] - a.translations[i]) * weight
                out.scales[i] = a.scales[i] + (b.scales[i] - a.scales[i]) * weight
            }
            val qa = Quaternionf()
            val qb = Quaternionf()
            for (i in 0 until a.boneCount) {
                val o = i * 4
                qa.set(a.rotations[o], a.rotations[o + 1], a.rotations[o + 2], a.rotations[o + 3])
                qb.set(b.rotations[o], b.rotations[o + 1], b.rotations[o + 2], b.rotations[o + 3])
                qa.slerp(qb, weight)
                out.rotations[o] = qa.x; out.rotations[o + 1] = qa.y; out.rotations[o + 2] = qa.z; out.rotations[o + 3] = qa.w
            }
        }
    }
}
