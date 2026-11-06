package com.pafoid.skate.engine.ecs

import com.pafoid.skate.engine.assets.serialization.FileKind
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.systems.SystemManager
import com.pafoid.skate.engine.events.Event
import com.pafoid.skate.engine.events.SceneAction
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** EDT-45 review N1: replacing a scene keeps its tab, loads systems once and publishes one event. */
class SceneManagerReplaceTest {
    private val events = EventSystem()
    private val systems = mockk<SystemManager>(relaxed = true)
    private val manager = SceneManager(mockk(relaxed = true), events, Serializer(), systems, mockk(relaxed = true))
    private val published = mutableListOf<Event>()

    private val a = Scene("A")
    private val b = Scene("B")
    private val c = Scene("C")

    init {
        manager.openScene(a)
        manager.openScene(b)
        manager.openScene(c)
        manager.switchScene(b)
        events.subscribe<SceneAction.Opened> { published += it }
        events.subscribe<SceneAction.Changed> { published += it }
        events.subscribe<SceneAction.Closing> { published += it }
        events.subscribe<SceneAction.Closed> { published += it }
        clearMocks(systems, answers = false)
    }

    @Test
    fun ReplaceScene_ActiveScene_KeepsTabAndLoadsSystemsOnce() {
        val fresh = Scene("B")
        assertTrue(manager.replaceScene(b, fresh))
        assertEquals(listOf(a, fresh, c), manager.openScenes)
        assertSame(fresh, manager.currentScene)
        verify(exactly = 1) { systems.loadScene(any()) }
        verify(exactly = 1) { systems.loadScene(fresh) }
        assertEquals(listOf<Event>(SceneAction.Changed(fresh)), published)
    }

    @Test
    fun ReplaceScene_InactiveScene_LoadsNothingAndKeepsTheActiveOne() {
        val fresh = Scene("C")
        assertTrue(manager.replaceScene(c, fresh))
        assertEquals(listOf(a, b, fresh), manager.openScenes)
        assertSame(b, manager.currentScene)
        verify(exactly = 0) { systems.loadScene(any()) }
        assertTrue(published.isEmpty())
    }

    @Test
    fun ReplaceScene_SceneNotOpen_DoesNothing() {
        assertFalse(manager.replaceScene(Scene("X"), Scene("Y")))
        assertEquals(listOf(a, b, c), manager.openScenes)
    }

    @Test
    fun SceneIsRunning_IsNotSaved() {
        val serializer = Serializer()
        val scene = Scene("Main").apply { isRunning = true }
        val text = serializer.encodeFile(FileKind.SCENE, scene)
        assertFalse(text.contains("isRunning"), text)
        assertFalse(serializer.decodeFile<Scene>(FileKind.SCENE, text).isRunning)
    }
}
