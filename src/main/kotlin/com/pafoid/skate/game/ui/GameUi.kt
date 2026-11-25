package com.pafoid.skate.game.ui

/**
 * Names of the scene HUD texts the skate game writes ([SkateHudSystem], GPL-21); the HUD itself comes with the scene
 * (the HUD prefab), so editor Play and the exported game show the same one (GUI-20).
 */
object GameUi {
    const val SCORE_NAME = "ScoreText"
    const val TRICK_NAME = "TrickText"
    /** Interaction prompt (GPL-24d), e.g. "Pick up", shown while the player faces something usable. */
    const val PROMPT_NAME = "PromptText"
}
