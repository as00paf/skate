package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.ecs.Scene

/**
 * Keeps the scene tab bar on the active scene and tells user tab clicks apart from programmatic switches (EDT-63).
 *
 * ImGui remembers which tab it shows. When the active scene changes without a click (a game script's switch during
 * Play, Stop restoring the scenes, a new scene opening), ImGui still reports the old tab as selected, which used to
 * read as a click on it. After such a change the active tab is selected programmatically and no click is reported
 * until ImGui shows it.
 */
class SceneTabSync {
    private var lastActive: Scene? = null
    private var syncing = false

    /** Call once per frame before the tabs, with the active scene. */
    fun beginFrame(active: Scene?) {
        if (active !== lastActive) {
            lastActive = active
            syncing = active != null
        }
    }

    /** True when [scene]'s tab must be selected programmatically this frame. */
    fun forceSelect(scene: Scene): Boolean = syncing && scene === lastActive

    /**
     * Call for the tab ImGui reports as selected. Returns the scene the user switched to, or null when it is the
     * active scene or the tab bar is still catching up with a programmatic switch.
     */
    fun onTabShown(scene: Scene): Scene? {
        if (syncing) {
            if (scene === lastActive) syncing = false
            return null
        }
        return scene.takeIf { it !== lastActive }
    }
}
