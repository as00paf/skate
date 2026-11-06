package com.pafoid.skate.editor.systems

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-45: Play/Pause/Stop/Reset state machine. */
class PlayModeControllerTest {

    /** Engine stand-in: a "scene" value that Play mutates and a snapshot restores. */
    private class FakeHost(var hasScene: Boolean = true) : PlayModeController.Host {
        override var isPlaying = false
        override var isPaused = false
        var sceneValue = 0
        var captures = 0
        var restores = 0
        val calls = mutableListOf<String>()

        override fun setRuntimePlaying(playing: Boolean) {
            calls += "playing=$playing"
            isPlaying = playing
            if (!playing) isPaused = false
        }

        override fun setRuntimePaused(paused: Boolean) {
            calls += "paused=$paused"
            isPaused = paused && isPlaying
        }

        override fun capture(): PlayModeController.Snapshot? {
            if (!hasScene) return null
            captures++
            val saved = sceneValue
            return PlayModeController.Snapshot {
                restores++
                calls += "restore"
                sceneValue = saved
                // Loading a scene clears the pause, as SystemManager.loadScene does.
                isPaused = false
            }
        }
    }

    private val host = FakeHost()
    private val controller = PlayModeController(host)

    @Test
    fun PlayMode_StartsInEdit() {
        assertEquals(PlayMode.EDIT, controller.mode)
        assertFalse(controller.canReset)
    }

    @Test
    fun PlayMode_PlayCapturesThenPlays() {
        controller.play()
        assertEquals(PlayMode.PLAYING, controller.mode)
        assertEquals(1, host.captures)
        assertTrue(controller.canReset)
    }

    @Test
    fun PlayMode_PlayWithoutSceneStaysInEdit() {
        host.hasScene = false
        controller.play()
        assertEquals(PlayMode.EDIT, controller.mode)
    }

    @Test
    fun PlayMode_PauseKeepsTheCurrentState() {
        controller.play()
        host.sceneValue = 42
        controller.pause()
        assertEquals(PlayMode.PAUSED, controller.mode)
        assertEquals(42, host.sceneValue)
        assertEquals(0, host.restores)
    }

    @Test
    fun PlayMode_PlayFromPausedResumesWithoutANewSnapshot() {
        controller.play()
        controller.pause()
        host.sceneValue = 7
        controller.play()
        assertEquals(PlayMode.PLAYING, controller.mode)
        assertEquals(1, host.captures)
        assertEquals(7, host.sceneValue)
    }

    @Test
    fun PlayMode_TogglePauseSwitchesBetweenPlayingAndPaused() {
        controller.togglePause()
        assertEquals(PlayMode.EDIT, controller.mode)
        controller.play()
        controller.togglePause()
        assertEquals(PlayMode.PAUSED, controller.mode)
        controller.togglePause()
        assertEquals(PlayMode.PLAYING, controller.mode)
    }

    @Test
    fun PlayMode_StopRestoresTheSavedState() {
        host.sceneValue = 1
        controller.play()
        host.sceneValue = 99
        controller.stop()
        assertEquals(PlayMode.EDIT, controller.mode)
        assertEquals(1, host.sceneValue)
        assertFalse(controller.canReset)
    }

    @Test
    fun PlayMode_StopFromPausedRestoresAndUnpauses() {
        host.sceneValue = 1
        controller.play()
        host.sceneValue = 5
        controller.pause()
        controller.stop()
        assertEquals(PlayMode.EDIT, controller.mode)
        assertFalse(host.isPaused)
        assertEquals(1, host.sceneValue)
    }

    @Test
    fun PlayMode_StopEndsPlayBeforeRestoring() {
        controller.play()
        host.calls.clear()
        controller.stop()
        assertEquals(listOf("paused=false", "playing=false", "restore"), host.calls)
    }

    @Test
    fun PlayMode_StopInEditDoesNothing() {
        controller.stop()
        assertTrue(host.calls.isEmpty())
        assertEquals(0, host.restores)
    }

    @Test
    fun PlayMode_StopWithoutRestoreLeavesTheSceneAlone() {
        host.sceneValue = 1
        controller.play()
        host.sceneValue = 2
        controller.stop(restore = false)
        assertEquals(PlayMode.EDIT, controller.mode)
        assertEquals(2, host.sceneValue)
        // The next Play captures again.
        controller.play()
        assertEquals(2, host.captures)
    }

    @Test
    fun PlayMode_StopsPlayStartedOutsideTheController() {
        host.isPlaying = true
        controller.stop()
        assertEquals(PlayMode.EDIT, controller.mode)
    }

    @Test
    fun PlayMode_NextPlayAfterStopTakesANewSnapshot() {
        controller.play()
        controller.stop()
        host.sceneValue = 3
        controller.play()
        host.sceneValue = 4
        controller.stop()
        assertEquals(2, host.captures)
        assertEquals(3, host.sceneValue)
    }

    @Test
    fun PlayMode_ResetRestoresAndKeepsPlaying() {
        host.sceneValue = 1
        controller.play()
        host.sceneValue = 50
        controller.reset()
        assertEquals(PlayMode.PLAYING, controller.mode)
        assertEquals(1, host.sceneValue)
        // Still the same snapshot: a second Reset goes back to the same state.
        host.sceneValue = 60
        controller.reset()
        assertEquals(1, host.sceneValue)
        assertEquals(1, host.captures)
    }

    @Test
    fun PlayMode_ResetWhilePausedStaysPaused() {
        host.sceneValue = 1
        controller.play()
        host.sceneValue = 8
        controller.pause()
        controller.reset()
        assertEquals(PlayMode.PAUSED, controller.mode)
        assertEquals(1, host.sceneValue)
    }

    @Test
    fun PlayMode_ResetReentersPlayForTheRestoredScene() {
        controller.play()
        host.calls.clear()
        controller.reset()
        assertEquals(listOf("restore", "playing=true", "paused=false"), host.calls)
    }

    @Test
    fun PlayMode_ResetInEditDoesNothing() {
        controller.reset()
        assertEquals(PlayMode.EDIT, controller.mode)
        assertEquals(0, host.restores)
    }

    @Test
    fun PlayMode_TogglePlayStartsAndStops() {
        host.sceneValue = 1
        controller.togglePlay()
        assertEquals(PlayMode.PLAYING, controller.mode)
        host.sceneValue = 2
        controller.togglePlay()
        assertEquals(PlayMode.EDIT, controller.mode)
        assertEquals(1, host.sceneValue)
    }

    @Test
    fun PlayMode_PauseFromTheGameShowsAsPaused() {
        controller.play()
        host.isPaused = true // in-game pause menu
        assertEquals(PlayMode.PAUSED, controller.mode)
    }
}
