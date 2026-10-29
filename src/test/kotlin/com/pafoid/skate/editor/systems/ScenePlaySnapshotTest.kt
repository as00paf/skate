package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.getComponent
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScenePlaySnapshotTest {
    private val serializer = Serializer()

    private fun scene(): Scene {
        val s = Scene("Main")
        s.addComponent(DayNightCycleComponent())
        val parent = GameObject("Parent").addComponent(Transform(Vector3f(1f, 2f, 3f)))
        val child = GameObject("Child").addComponent(Transform(Vector3f(4f, 5f, 6f)))
        parent.addChild(child)
        s.addChild(parent)
        return s
    }

    private fun mutate(s: Scene) {
        val parent = s.children[0]
        parent.getComponent<Transform>()!!.translation.set(9f, 9f, 9f)
        parent.children[0].getComponent<Transform>()!!.translation.set(8f, 8f, 8f)
        s.getComponent<DayNightCycleComponent>()!!.timeScale = 0f
        s.addChild(GameObject("Spawned"))
    }

    @Test
    fun ScenePlaySnapshot_Decode_RestoresNestedTransformsAndSceneComponents() {
        val original = scene()
        original.getComponent<DayNightCycleComponent>()!!.dayDuration = 77f
        val snap = ScenePlaySnapshot.capture(original, serializer)
        mutate(original)
        val restored = snap.decode(serializer)
        assertEquals(1, restored.children.size)
        assertEquals(Vector3f(1f, 2f, 3f), restored.children[0].getComponent<Transform>()!!.translation)
        assertEquals(Vector3f(4f, 5f, 6f), restored.children[0].children[0].getComponent<Transform>()!!.translation)
        assertEquals(77f, restored.getComponent<DayNightCycleComponent>()!!.dayDuration)
        assertEquals(1f, restored.getComponent<DayNightCycleComponent>()!!.timeScale)
    }

    @Test
    fun ScenePlaySnapshot_SelectionIsResolvedByPath() {
        val original = scene()
        original.setSelection(listOf(original.children[0].children[0]))
        val snap = ScenePlaySnapshot.capture(original, serializer)
        val restored = snap.decode(serializer)
        assertEquals(listOf("Child"), snap.selectionIn(restored).map { it.name })
        assertTrue(snap.selectionIn(Scene("Empty")).isEmpty())
    }

    @Test
    fun ScenePlaySnapshot_Restore_ReplacesSceneAtSamePosition() {
        val manager = SceneManager(mockk(relaxed = true), EventSystem(), serializer, mockk(relaxed = true), mockk(relaxed = true))
        val other = Scene("Other")
        val playing = scene()
        manager.openScene(other)
        manager.openScene(playing)
        manager.openScene(Scene("Third"))
        manager.switchScene(playing)
        playing.setSelection(listOf(playing.children[0]))
        val snap = ScenePlaySnapshot.capture(playing, serializer)
        mutate(playing)

        val fresh = snap.restore(manager, serializer, playing)

        assertNotSame(playing, fresh)
        assertEquals(listOf("Other", "Main", "Third"), manager.openScenes.map { it.name })
        assertSame(fresh, manager.currentScene)
        assertEquals(1, fresh.children.size)
        assertEquals(Vector3f(1f, 2f, 3f), fresh.children[0].getComponent<Transform>()!!.translation)
        assertEquals(listOf("Parent"), fresh.selectedGameObjects.map { it.name })
    }
}
