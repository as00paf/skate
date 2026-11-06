package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.ecs.Scene
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-63: the tab bar follows programmatic scene switches instead of reporting them as clicks. */
class SceneTabSyncTest {
    private val main = Scene("Main")
    private val showcase = Scene("Showcase")
    private val sync = SceneTabSync()

    /** One frame where ImGui shows [shown]; returns the click reported, if any. */
    private fun frame(active: Scene, shown: Scene): Scene? {
        sync.beginFrame(active)
        return sync.onTabShown(shown)
    }

    @Test
    fun SceneTabSync_FirstFrame_SelectsTheActiveTab() {
        sync.beginFrame(main)
        assertTrue(sync.forceSelect(main))
        assertFalse(sync.forceSelect(showcase))
    }

    @Test
    fun SceneTabSync_UserClick_IsReported() {
        frame(main, main)
        assertSame(showcase, frame(main, showcase))
    }

    @Test
    fun SceneTabSync_ScriptSwitch_IsFollowedNotReported() {
        frame(main, main)

        // A script switched to Showcase; ImGui still shows Main for a frame or two
        sync.beginFrame(showcase)
        assertTrue(sync.forceSelect(showcase))
        assertNull(sync.onTabShown(main))
        assertNull(frame(showcase, main))
        assertNull(frame(showcase, showcase))

        // Caught up: a later click on Main is a real click
        assertFalse(sync.forceSelect(showcase))
        assertSame(main, frame(showcase, main))
    }

    @Test
    fun SceneTabSync_RestoredSceneCopy_IsFollowed() {
        frame(main, main)
        val restoredMain = Scene("Main")
        assertNull(frame(restoredMain, main))
        assertNull(frame(restoredMain, restoredMain))
        assertNull(frame(restoredMain, restoredMain))
    }
}
