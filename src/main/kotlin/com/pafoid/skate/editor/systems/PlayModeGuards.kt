package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.input.ActionBinding

/** Editor shortcuts that must step aside while the scene is in Play (GUI-09 review). */
object PlayModeGuards {

    /**
     * True when the editor's deselect key must be ignored: in Play, a key that the game's UI cancel action also
     * uses (Esc by default) belongs to the game, where it opens the pause menu. A deselect key rebound to anything
     * else keeps working in Play, and while editing the binding always applies.
     */
    fun deselectClaimedByGame(playing: Boolean, deselectKey: Int, cancelBindings: List<ActionBinding>): Boolean =
        playing && cancelBindings.any { it is ActionBinding.Key && it.key == deselectKey }

    /**
     * Scene saves are refused in Play: the live scene holds runtime state and runtime-only objects (the pause
     * menu), which must not end up in the scene file. Stop first, as in other engines.
     */
    fun canSaveScene(playing: Boolean): Boolean = !playing
}
