package com.pafoid.skate.engine.animation

object BoneMirrorUtil {

    private val LEFT_PREFIXES = listOf("Left", "L_")
    private val RIGHT_PREFIXES = listOf("Right", "R_")

    fun getMirroredBoneName(boneName: String): String {
        // Check for common 'Left' prefixes
        for (prefix in LEFT_PREFIXES) {
            if (boneName.contains(prefix, ignoreCase = true)) {
                return boneName.replace(prefix, RIGHT_PREFIXES[LEFT_PREFIXES.indexOf(prefix)], ignoreCase = true)
            }
        }

        // Check for common 'Right' prefixes
        for (prefix in RIGHT_PREFIXES) {
            if (boneName.contains(prefix, ignoreCase = true)) {
                return boneName.replace(prefix, LEFT_PREFIXES[RIGHT_PREFIXES.indexOf(prefix)], ignoreCase = true)
            }
        }

        return boneName
    }
}
