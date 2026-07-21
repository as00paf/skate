package com.pafoid.skate.editor.commands

import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.utils.JobSystem
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

class OpenSceneCommandTest {
    private lateinit var serializer: Serializer
    private lateinit var sceneManager: SceneManager
    private lateinit var eventSystem: EventSystem
    private lateinit var jobSystem: JobSystem
    private lateinit var loadedSceneInstance: Scene

    @BeforeEach
    fun setup() {
        serializer = mockk(relaxed = true)
        sceneManager = mockk(relaxed = true)
        eventSystem = EventSystem()
        jobSystem = JobSystem()
        loadedSceneInstance = mockk(relaxed = true)
        every { loadedSceneInstance.name } returns "Loaded Scene"
        every { loadedSceneInstance.destroyScene() } returns Unit

    }

    @AfterEach
    fun teardown() {
        jobSystem.destroy()
        unmockkAll()
    }

}
