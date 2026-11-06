package com.pafoid.skate.editor.systems

import com.pafoid.skate.engine.addComponent
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.RigidBody3D
import com.pafoid.skate.engine.ecs.components.ScriptComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * EDT-45: Stop and Reset restore everything Play can change: transforms, physics bodies, animators, audio,
 * scripts, day/night and UI, and drop objects spawned in Play.
 */
class PlayModeRestoreTest {
    private val serializer = Serializer()

    private fun scene() = Scene("Main").apply {
        addComponent(DayNightCycleComponent(timeOfDay = 8f))
        addChild(GameObject("Box").addComponent(Transform(Vector3f(0f, 5f, 0f))).addComponent(RigidBody3D(mass = 2f)))
        addChild(GameObject("Hero").addComponent(Transform()).addComponent(Animator()))
        addChild(GameObject("Speaker").addComponent(Transform()).addComponent(AudioComponent(soundFilePath = "a.ogg", volume = 0.5f)))
        addChild(GameObject("Logic").addComponent(ScriptComponent(path = "spin.kts", properties = mutableMapOf("speed" to "1"))))
        addChild(GameObject("Hud").addComponent(UICanvasComponent(sortOrder = 1)))
    }

    private fun Scene.child(name: String) = children.single { it.name == name }

    /** What a few seconds of Play does to the scene. */
    private fun play(s: Scene) {
        s.getComponent<DayNightCycleComponent>()!!.timeOfDay = 20f
        s.child("Box").getComponent<Transform>()!!.translation.set(0f, -3f, 0f)
        s.child("Box").getComponent<RigidBody3D>()!!.mass = 9f
        s.child("Hero").getComponent<Animator>()!!.apply { currentTime = 3f; isPlaying = true }
        s.child("Speaker").getComponent<AudioComponent>()!!.apply { isPlaying = true; awoken = true; volume = 1f }
        s.child("Logic").getComponent<ScriptComponent>()!!.properties["speed"] = "9"
        s.child("Hud").getComponent<UICanvasComponent>()!!.visible = false
        s.addChild(GameObject("Spawned"))
    }

    @Test
    fun PlayModeRestore_PutsEveryKindOfStateBack() {
        val original = scene()
        val snapshot = ScenePlaySnapshot.capture(original, serializer)
        play(original)

        val restored = snapshot.decode(serializer)

        assertNotSame(original, restored)
        assertEquals(listOf("Box", "Hero", "Speaker", "Logic", "Hud"), restored.children.map { it.name })
        assertEquals(8f, restored.getComponent<DayNightCycleComponent>()!!.timeOfDay)
        assertEquals(Vector3f(0f, 5f, 0f), restored.child("Box").getComponent<Transform>()!!.translation)
        restored.child("Box").getComponent<RigidBody3D>()!!.let {
            assertEquals(2f, it.mass)
            assertNull(it.rawBody, "the physics body is rebuilt for the restored scene")
        }
        restored.child("Hero").getComponent<Animator>()!!.let {
            assertEquals(0f, it.currentTime)
            assertFalse(it.isPlaying)
        }
        restored.child("Speaker").getComponent<AudioComponent>()!!.let {
            assertEquals(0.5f, it.volume)
            assertFalse(it.isPlaying)
            assertFalse(it.awoken, "playOnAwake fires again on the next Play")
        }
        assertEquals("1", restored.child("Logic").getComponent<ScriptComponent>()!!.properties["speed"])
        assertTrue(restored.child("Hud").getComponent<UICanvasComponent>()!!.visible)
    }

    @Test
    fun PlayModeRestore_ResetCanRestoreTheSameSnapshotTwice() {
        val original = scene()
        val snapshot = ScenePlaySnapshot.capture(original, serializer)
        play(original)
        val first = snapshot.decode(serializer)
        play(first)
        val second = snapshot.decode(serializer)
        assertEquals(Vector3f(0f, 5f, 0f), second.child("Box").getComponent<Transform>()!!.translation)
        assertEquals(5, second.children.size)
    }
}
