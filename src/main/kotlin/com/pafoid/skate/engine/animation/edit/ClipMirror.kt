package com.pafoid.skate.engine.animation.edit

import com.pafoid.skate.engine.animation.ik.IkSolvers
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.BoneMirrorUtil
import com.pafoid.skate.engine.assets.data.models.animations.InterpolationType
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Mirrors a clip left ↔ right across the rig's YZ plane (model X is the character's side axis, as on Mixamo rigs and
 * the skater). Left and right bones swap motion (names paired by [BoneMirrorUtil]) and every motion is reflected.
 *
 * The mirror is computed in model space, so it does not assume the two sides have mirrored local axes: for bone `b`
 * with mirror `m`, `G'(b) = S·G(m)·S·C(b)` where `S` reflects X and `C(b) = (S·R(m)·S)⁻¹·R(b)` maps the rest pose
 * (`R`, the bind globals) of one side onto the other. The rest pose mirrors onto itself. Locals are then recovered
 * from the mirrored globals and stored in channel space (`bindLocal⁻¹ * local`, see [ClipEvaluator]).
 *
 * Each output track takes its key times, interpolation and tangent modes from the source bone's track (stored
 * handles fall back to AUTO; see [keyTimes] for translations). Scale tracks are swapped as they are; tracks of bones missing from the rig are kept.
 * A side bone without a twin in the rig (Mixamo exports end bones such as `LeftHandThumb4` on one side only) keeps
 * its own local motion, so it stays attached to its mirrored parent.
 */
object ClipMirror {
    fun apply(clip: SkClip, skeleton: Skeleton): SkClip {
        val rig = ClipRig(skeleton)
        val n = rig.boneCount
        val mirroredName = Array(n) { BoneMirrorUtil.getMirroredBoneName(rig.names[it]) }
        val mirror = IntArray(n) { i -> rig.names.indexOf(mirroredName[i]).takeIf { it >= 0 } ?: i }
        val unpaired = BooleanArray(n) { i -> mirroredName[i] != rig.names[i] && mirror[i] == i }
        val s = Matrix4f().scaling(-1f, 1f, 1f)
        val rest = Array(n) { IkSolvers.globalOf(rig.parents, rig.bindLocals, it) }
        val correction = Array(n) { b -> Matrix4f(s).mul(rest[mirror[b]]).mul(s).invert().mul(rest[b]) }
        val bindInverse = Array(n) { Matrix4f(rig.bindLocals[it]).invert() }
        val evaluator = ClipEvaluator(clip, rig)
        val cache = HashMap<Float, Array<Matrix4f>>()

        /** Channel-space transform of every bone in the mirrored clip at [t]. */
        fun mirroredChannels(t: Float): Array<Matrix4f> = cache.getOrPut(t) {
            val locals = evaluator.locals(t)
            val globals = arrayOfNulls<Matrix4f>(n)
            fun global(i: Int): Matrix4f = globals[i] ?: run {
                val p = rig.parents[i]
                (if (p < 0) Matrix4f(locals[i]) else Matrix4f(global(p)).mul(locals[i])).also { globals[i] = it }
            }
            val mirrored = Array(n) { b -> Matrix4f(s).mul(global(mirror[b])).mul(s).mul(correction[b]) }
            Array(n) { b ->
                val p = rig.parents[b]
                val local = if (p < 0) Matrix4f(mirrored[b]) else Matrix4f(mirrored[p]).invert().mul(mirrored[b])
                Matrix4f(bindInverse[b]).mul(local)
            }
        }

        val rigBones = rig.names.toSet()
        val out = ArrayList<SkTrack>()
        for (b in 0 until n) {
            if (unpaired[b]) { out += clip.tracks.filter { it.bone == rig.names[b] }; continue }
            val source = rig.names[mirror[b]]
            val sourceTracks = clip.tracks.filter { it.bone == source }
            val rotationTimes = sourceTracks.firstOrNull { it.path == AnimationPath.ROTATION }?.keys?.map { it.time }.orEmpty()
            for (track in sourceTracks) {
                val keys = keyTimes(track, rotationTimes).map { k ->
                    val value = when (track.path) {
                        AnimationPath.ROTATION -> QuatMath.toRow(mirroredChannels(k.time)[b].getUnnormalizedRotation(Quaternionf()).normalize())
                        AnimationPath.TRANSLATION -> mirroredChannels(k.time)[b].getTranslation(Vector3f()).let { floatArrayOf(it.x, it.y, it.z) }
                        AnimationPath.SCALE -> k.value.toFloatArray()
                    }
                    SkKey(k.time, value.toList(), k.tangentMode.takeIf { !it.stored } ?: TangentMode.AUTO)
                }
                val rows = Array(keys.size) { keys[it].value.toFloatArray() }
                if (track.path == AnimationPath.ROTATION) QuatMath.makeContinuous(rows)
                val mirroredKeys = keys.mapIndexed { i, k -> k.copy(value = rows[i].toList()) }
                // Translations keyed at rotation times only to follow an asymmetric rest pose; a constant one stays one key.
                val constant = track.path == AnimationPath.TRANSLATION && track.keys.size == 1 &&
                    rows.all { r -> (0 until 3).all { kotlin.math.abs(r[it] - rows[0][it]) <= CONSTANT_EPSILON } }
                val interpolation = if (track.keys.size < 2 && mirroredKeys.size > 1) InterpolationType.LINEAR else track.interpolation
                out += SkTrack(rig.names[b], track.path, interpolation, if (constant) mirroredKeys.take(1) else mirroredKeys)
            }
        }
        out += clip.tracks.filter { it.bone !in rigBones }
        return clip.copy(tracks = out)
    }

    /**
     * Keys to write for a mirrored [track]. A translation also gets the source's rotation key times: when the rest pose
     * is not exactly symmetric (a few millimetres on real rigs) the mirrored offset turns with the twin's rotation.
     */
    private fun keyTimes(track: SkTrack, rotationTimes: List<Float>): List<SkKey> {
        if (track.path != AnimationPath.TRANSLATION || rotationTimes.isEmpty()) return track.keys
        val extra = rotationTimes.filter { t -> track.keys.none { kotlin.math.abs(it.time - t) <= KeyEditing.TIME_EPSILON } }
        if (extra.isEmpty()) return track.keys
        val mode = if (track.interpolation == InterpolationType.CUBIC_SPLINE) TangentMode.AUTO else track.keys.first().tangentMode
        return (track.keys + extra.map { SkKey(it, track.keys.first().value, mode) }).sortedBy { it.time }
    }

    /** Translations closer than this (rig units) count as constant. */
    private const val CONSTANT_EPSILON = 1e-6f
}
