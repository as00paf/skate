package com.pafoid.skate.engine.animation.retarget

import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.assets.data.models.animations.AnimationPath
import com.pafoid.skate.engine.assets.data.models.animations.AnimationSampler
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton

/** Name-based humanoid retargeting: rewrites channel targets so a clip authored on one rig drives another. */
object Retargeter {

    /** Maps source bone name -> target bone name; humanoid slots first, exact-name match as fallback. */
    fun buildMap(sourceBoneNames: Collection<String>, target: Skeleton): Map<String, String> {
        val targetNames = target.getAllBones().map { it.name }
        val bySlot = HashMap<HumanoidBone, String>()
        for (n in targetNames) HumanoidBone.fromName(n)?.let { bySlot.putIfAbsent(it, n) }
        val exact = targetNames.toSet()
        val result = LinkedHashMap<String, String>()
        for (src in sourceBoneNames) {
            val slot = HumanoidBone.fromName(src)
            val mapped = (if (slot != null) bySlot[slot] else null) ?: src.takeIf { it in exact }
            if (mapped != null) result[src] = mapped
        }
        return result
    }

    /**
     * Returns a copy of [animation] targeting [target]'s bone names. Channels with no counterpart are dropped.
     * [translationScale] rescales translation tracks for rigs of different proportions.
     */
    fun retarget(animation: Animation, target: Skeleton, translationScale: Float = 1f): Animation {
        val map = buildMap(animation.channels.map { it.targetNodeName }.toSet(), target)
        val channels = animation.channels.mapNotNull { ch ->
            val name = map[ch.targetNodeName] ?: return@mapNotNull null
            if (ch.path != AnimationPath.TRANSLATION || translationScale == 1f) return@mapNotNull ch.copy(targetNodeName = name)
            val s = ch.sampler
            val scaled = FloatArray(s.values.size) { s.values[it] * translationScale }
            ch.copy(sampler = AnimationSampler(s.times, scaled, s.interpolation, s.componentsPerValue), targetNodeName = name)
        }
        return Animation(animation.name, channels, animation.duration, animation.path)
    }

    /** Fraction of the clip's channels that found a target bone; useful to reject bad rig pairs. */
    fun coverage(animation: Animation, target: Skeleton): Float {
        if (animation.channels.isEmpty()) return 0f
        val map = buildMap(animation.channels.map { it.targetNodeName }.toSet(), target)
        return animation.channels.count { it.targetNodeName in map } / animation.channels.size.toFloat()
    }
}
