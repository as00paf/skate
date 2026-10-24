package com.pafoid.skate.engine.animation.retarget

/** Canonical humanoid slots shared by all rigs, used to map animation bone names between skeletons. */
enum class HumanoidBone {
    HIPS, SPINE, CHEST, UPPER_CHEST, NECK, HEAD,
    LEFT_SHOULDER, LEFT_UPPER_ARM, LEFT_LOWER_ARM, LEFT_HAND,
    RIGHT_SHOULDER, RIGHT_UPPER_ARM, RIGHT_LOWER_ARM, RIGHT_HAND,
    LEFT_UPPER_LEG, LEFT_LOWER_LEG, LEFT_FOOT, LEFT_TOES,
    RIGHT_UPPER_LEG, RIGHT_LOWER_LEG, RIGHT_FOOT, RIGHT_TOES;

    companion object {
        private val center = mapOf(
            "hips" to HIPS, "hip" to HIPS, "pelvis" to HIPS,
            "spine" to SPINE, "spine01" to SPINE,
            "spine1" to CHEST, "spine02" to CHEST, "chest" to CHEST,
            "spine2" to UPPER_CHEST, "spine03" to UPPER_CHEST, "upperchest" to UPPER_CHEST,
            "neck" to NECK, "neck1" to NECK, "head" to HEAD,
        )
        private val limb = mapOf(
            "shoulder" to "SHOULDER", "clavicle" to "SHOULDER",
            "arm" to "UPPER_ARM", "upperarm" to "UPPER_ARM",
            "forearm" to "LOWER_ARM", "lowerarm" to "LOWER_ARM",
            "hand" to "HAND",
            "upleg" to "UPPER_LEG", "thigh" to "UPPER_LEG", "upperleg" to "UPPER_LEG",
            "leg" to "LOWER_LEG", "calf" to "LOWER_LEG", "lowerleg" to "LOWER_LEG", "shin" to "LOWER_LEG",
            "foot" to "FOOT",
            "toebase" to "TOES", "toes" to "TOES", "toe" to "TOES", "ball" to "TOES",
        )
        private val noise = Regex("^(mixamorig\\d*|bip0*1|armature|skel)[:_. ]?")
        private val sidePrefixWord = Regex("^(left|right)[_.\\- ]?(.+)$")
        private val sidePrefixLetter = Regex("^([lr])[_.\\- ](.+)$")
        private val sideSuffix = Regex("^(.+?)[_.\\- ](left|right|l|r)$")
        private val nonAlnum = Regex("[^a-z0-9]")

        /** Resolves common rig naming (Mixamo, Biped, UE mannequin, generic) to a slot, or null if not a known humanoid bone. */
        fun fromName(rawName: String): HumanoidBone? {
            var s = rawName.substringAfterLast(':').trim().lowercase()
            s = noise.replace(s, "")
            var side: String? = null
            val pw = sidePrefixWord.matchEntire(s)
            val pl = sidePrefixLetter.matchEntire(s)
            val sf = sideSuffix.matchEntire(s)
            when {
                pw != null -> { side = pw.groupValues[1]; s = pw.groupValues[2] }
                pl != null -> { side = if (pl.groupValues[1] == "l") "left" else "right"; s = pl.groupValues[2] }
                sf != null -> { side = if (sf.groupValues[2].startsWith("l")) "left" else "right"; s = sf.groupValues[1] }
            }
            val key = nonAlnum.replace(s, "")
            if (side == null) return center[key]
            val slot = limb[key] ?: return null
            return valueOf("${side.uppercase()}_$slot")
        }
    }
}
