package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.objects.AddAudioComponentCommand
import com.pafoid.skate.editor.commands.objects.ApplyTextureCommand
import com.pafoid.skate.editor.commands.scene.CreateLightCommand
import com.pafoid.skate.editor.commands.scene.CreatePrimitiveCommand
import com.pafoid.skate.editor.commands.scene.DuplicateGameObjectCommand
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.render.data.LightType
import io.mockk.mockk
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SyncCommandSemanticsTest {

    @Test
    fun `create primitive command is execute only`() {
        val command = CreatePrimitiveCommand(
            "Cube",
            Vector3f(0.5f),
            mockk<Scene>(relaxed = true),
            mockk<GameObjectManager>(relaxed = true)
        )
        assertEquals(CommandCategory.EXECUTE_ONLY, command.getCategory())
    }

    @Test
    fun `create light command is execute only`() {
        val command = CreateLightCommand(
            "Directional Light",
            LightType.DIRECTIONAL,
            mockk<Scene>(relaxed = true),
            mockk<GameObjectManager>(relaxed = true)
        )
        assertEquals(CommandCategory.EXECUTE_ONLY, command.getCategory())
    }

    @Test
    fun `duplicate game object command is execute only`() {
        val command = DuplicateGameObjectCommand(
            GameObject("Original"),
            mockk<Scene>(relaxed = true),
            mockk<GameObjectManager>(relaxed = true)
        )
        assertEquals(CommandCategory.EXECUTE_ONLY, command.getCategory())
    }

    @Test
    fun `apply texture command is execute only`() {
        val command = ApplyTextureCommand(
            GameObject("Target"),
            "assets/textures/test.png",
            mockk<ResourceManager>(relaxed = true),
            mockk<EventSystem>(relaxed = true)
        )
        assertEquals(CommandCategory.EXECUTE_ONLY, command.getCategory())
    }

    @Test
    fun `add audio component undo removes created component when object had none`() {
        val gameObject = GameObject("AudioTarget")
        val command = AddAudioComponentCommand(gameObject, "assets/audio/test.wav")

        command.execute()
        command.undo()

        assertNull(gameObject.getComponent<AudioComponent>())
    }

    @Test
    fun `add audio component undo restores previous sound path when component existed`() {
        val gameObject = GameObject("AudioTarget")
        gameObject.addComponent(AudioComponent().apply {
            soundFilePath = "assets/audio/original.wav"
        })
        val command = AddAudioComponentCommand(gameObject, "assets/audio/new.wav")

        command.execute()
        command.undo()

        assertEquals("assets/audio/original.wav", gameObject.getComponent<AudioComponent>()?.soundFilePath)
    }
}
