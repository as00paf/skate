package com.pafoid.skate.engine.assets.data.models.animations

/**
 * Left/right bone name swapping for mirroring clips (ANI-15). Recognises the side as a word (`LeftUpLeg`,
 * `mixamorig:RightHand`, `upperarm_left`), a one-letter prefix (`L_Foot`, `r.hand`) or a one-letter suffix
 * (`Thigh_L`, `hand.r`). Letters inside words are left alone (`Small_Toe`, `Upper_Arm`). A name without a side
 * comes back unchanged.
 */
object BoneMirrorUtil {
    private val word = Regex("Left|Right|LEFT|RIGHT|left|right")
    private val letterPrefix = Regex("(^|[:|])([LlRr])(?=[_.\\- ])")
    private val letterSuffix = Regex("(?<=[_.\\- ])([LlRr])$")

    fun getMirroredBoneName(boneName: String): String {
        if (word.containsMatchIn(boneName)) return word.replace(boneName) { swapWord(it.value) }
        val prefixed = letterPrefix.replace(boneName) { it.groupValues[1] + swapLetter(it.groupValues[2]) }
        return letterSuffix.replace(prefixed) { swapLetter(it.value) }
    }

    private fun swapWord(w: String) = when (w) {
        "Left" -> "Right"; "Right" -> "Left"
        "LEFT" -> "RIGHT"; "RIGHT" -> "LEFT"
        "left" -> "right"; else -> "left"
    }

    private fun swapLetter(l: String) = when (l) {
        "L" -> "R"; "R" -> "L"; "l" -> "r"; else -> "l"
    }
}
