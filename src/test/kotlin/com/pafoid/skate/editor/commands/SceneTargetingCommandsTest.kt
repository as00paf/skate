package com.pafoid.skate.editor.commands

import com.pafoid.skate.editor.commands.project.CloseOtherScenesCommand
import com.pafoid.skate.editor.commands.project.CloseSceneCommand
import com.pafoid.skate.editor.commands.project.DeleteSceneCommand
import com.pafoid.skate.editor.commands.scene.SwitchSceneCommand
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class SceneTargetingCommandsTest {

    @Test
    fun `switch scene command uses scene reference`() {
        val scene = mockk<Scene>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)

        SwitchSceneCommand(scene, sceneManager).execute()

        verify(exactly = 1) { sceneManager.switchScene(scene) }
    }

    @Test
    fun `close scene command uses scene reference`() {
        val scene = mockk<Scene>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)

        CloseSceneCommand(scene, sceneManager).execute()

        verify(exactly = 1) { sceneManager.closeScene(scene) }
    }

    @Test
    fun `close other scenes command uses keep scene reference`() {
        val keepScene = mockk<Scene>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)

        CloseOtherScenesCommand(keepScene, sceneManager, mockk()).execute()

        verify(exactly = 1) { sceneManager.closeOtherScenes(keepScene) }
    }

    @Test
    fun `delete scene command closes targeted scene reference`() {
        val scene = mockk<Scene>(relaxed = true)
        val sceneManager = mockk<SceneManager>(relaxed = true)
        val logger = mockk<LoggerService>(relaxed = true)
        val projectManager: ProjectManager = mockk()

        DeleteSceneCommand(scene, projectManager, sceneManager, logger).execute()

        verify(exactly = 1) { sceneManager.closeScene(scene) }
    }
}
