package com.pafoid.skate.game.ui

/**
 * Names of the scene HUD widgets the skate game writes ([SkateHudSystem], GPL-21, GPL-23); the HUD itself comes with
 * the scene (the HUD prefab), so editor Play and the exported game show the same one (GUI-20).
 */
object GameUi {
    const val TRICK_NAME = "TrickText"
    /** Combo chain and trick count (GPL-23), top centre. */
    const val COMBO_NAME = "ComboText"
    /** Control hint for the selected scheme (GPL-23), under the trick name. */
    const val HINT_NAME = "HintText"
    /** Manual balance meter (GPL-23), bottom centre. */
    const val BALANCE_NAME = "BalanceMeter"
    /** Interaction prompt (GPL-24d), e.g. "Pick up", shown while the player faces something usable. */
    const val PROMPT_NAME = "PromptText"
}
