package com.pafoid.skate.editor.imgui

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.Project
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditorMenuBarLifecycleTest {

    @Test
    fun `appIconLifecycle_ProjectClosed_ReloadsBundledTextureId`() {
        val assetsManager = mockk<AssetsManager>(relaxed = true)
        every { assetsManager.getBundledTexture(Assets.Bundled.APP_ICON) } returnsMany listOf(texture(101), texture(202))

        val eventSystem = EventSystem()
        val engine = mockk<Engine>(relaxed = true)
        every { engine.eventSystem } returns eventSystem
        every { engine.assetsManager } returns assetsManager
        every { engine.stringManager } returns mockk<StringManager>(relaxed = true)
        every { engine.sceneManager } returns mockk(relaxed = true)
        val editor = mockk<Editor>(relaxed = true)
        val menuBar = EditorMenuBar(
            engine = engine,
            editor = editor,
            windowRegistry = mockk(relaxed = true),
            projectManager = mockk<ProjectManager>(relaxed = true),
        )

        assertEquals(101, readAppIconTexId(menuBar))

        eventSystem.publish(ProjectEvent.Closed(Project(name = "AnyProject", projectPath = "AnyProject/AnyProject.skateproj", defaultScene = "Main.scene", scenesPath = emptyList())))
        assertEquals(202, readAppIconTexId(menuBar))
        verify(exactly = 2) { assetsManager.getBundledTexture(Assets.Bundled.APP_ICON) }
    }

    private fun texture(id: Int): Texture = Texture().also { it.texId = id }

    private fun readAppIconTexId(menuBar: EditorMenuBar): Int {
        val field = EditorMenuBar::class.java.getDeclaredField("appIconTexId")
        field.isAccessible = true
        return field.getInt(menuBar)
    }
}
