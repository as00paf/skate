package com.pafoid.skate.engine.animation.retarget

import com.pafoid.skate.engine.assets.BoneNameMapper
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton

/** Outcome of remapping a skinned mesh's joints onto another skeleton ([JointRemap]). */
sealed interface JointRemapResult {
    /** The joints rewritten to target bone indices, one array per mesh part (same order as the input). */
    data class Remapped(val joints: List<IntArray>) : JointRemapResult

    /**
     * The mesh cannot be worn on the target skeleton. [missingBones] are weighted source bones with no bone of the
     * same name in the target; [problems] are malformed data (joint index out of range, array sizes). Both sorted.
     */
    data class Refused(val missingBones: List<String>, val problems: List<String> = emptyList()) : JointRemapResult {
        /** One line for the log ("Cannot wear …: missing bones …"). */
        val reason: String
            get() = buildList {
                if (missingBones.isNotEmpty()) add("bones not in the target skeleton: ${missingBones.joinToString()}")
                addAll(problems)
            }.joinToString("; ")
    }
}

/**
 * Joint remap of skinned clothing by bone name (ANI-19, `docs/design/GPL-24.md` §6.3). A shirt exported on the same
 * rig as the body can list its bones in another order, so its `MeshPart.joints` (indices into the clothing file's own
 * skeleton) are rewritten to the body skeleton's indices. The clothing then skins with the body's palette.
 *
 * Bones are matched by name: exactly first, else by the [BoneNameMapper] form ignoring case (`mixamorig:Hips` and
 * `Hips`) when that match is unique. Only joint slots that carry weight count: an unweighted slot (index 0 by
 * default) is left as it is. Any weighted bone missing from the target refuses the whole item; this handles
 * reordering only, not a different rig (retargeting skin weights is out of scope). Pure: inputs are never modified.
 */
object JointRemap {
    /** Joint and weight slots per vertex (as `AssimpLoader` writes them). */
    const val SLOTS = 4

    /**
     * For each source bone index, the target bone index with the same name, or -1 when there is none.
     * [sourceNames] and [targetNames] are bone names by index (null for an empty index).
     */
    fun table(sourceNames: List<String?>, targetNames: List<String?>): IntArray {
        val exact = HashMap<String, Int>()
        val loose = HashMap<String, MutableList<Int>>()
        targetNames.forEachIndexed { i, name ->
            if (name == null) return@forEachIndexed
            exact.putIfAbsent(name, i)
            loose.getOrPut(normalize(name)) { ArrayList() } += i
        }
        return IntArray(sourceNames.size) { i ->
            val name = sourceNames[i] ?: return@IntArray -1
            exact[name] ?: loose[normalize(name)]?.singleOrNull() ?: -1
        }
    }

    /** Remaps one joint array ([SLOTS] per vertex) with its matching [weights]. */
    fun remap(joints: IntArray, weights: FloatArray, sourceNames: List<String?>, targetNames: List<String?>): JointRemapResult =
        remapParts(listOf(joints to weights), sourceNames, targetNames)

    /** Remaps every part of a clothing model from the [source] skeleton (its own file's) onto the [target] (the body). */
    fun remap(parts: List<MeshPart>, source: Skeleton, target: Skeleton): JointRemapResult =
        remapParts(parts.map { it.joints to it.weights }, names(source), names(target))

    /** [part] with its joints replaced by the remapped ones (the caller re-uploads the vertex buffer). */
    fun apply(part: MeshPart, joints: IntArray): MeshPart = part.copy(joints = joints)

    /** Bone names of [skeleton] by bone index. */
    fun names(skeleton: Skeleton): List<String?> = List(skeleton.boneCount) { skeleton.bones[it]?.name }

    private fun remapParts(parts: List<Pair<IntArray, FloatArray>>, sourceNames: List<String?>, targetNames: List<String?>): JointRemapResult {
        val table = table(sourceNames, targetNames)
        val missing = sortedSetOf<String>()
        val problems = ArrayList<String>()
        val out = parts.mapIndexed { p, (joints, weights) ->
            if (joints.size != weights.size || joints.size % SLOTS != 0) {
                problems += "part $p: ${joints.size} joints for ${weights.size} weights"
                return@mapIndexed joints
            }
            val remapped = joints.copyOf()
            for (k in joints.indices) {
                if (weights[k] == 0f) continue
                val j = joints[k]
                if (j !in table.indices) {
                    problems += "part $p: joint $j is not a bone of the clothing skeleton (${table.size} bones)"
                    continue
                }
                val target = table[j]
                if (target < 0) missing += sourceNames[j] ?: "#$j" else remapped[k] = target
            }
            remapped
        }
        return if (missing.isEmpty() && problems.isEmpty()) JointRemapResult.Remapped(out)
        else JointRemapResult.Refused(missing.toList(), problems.distinct().sorted())
    }

    private fun normalize(name: String) = BoneNameMapper.map(name).lowercase()
}
