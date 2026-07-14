package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.project.ProjectWizard
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.menus.WindowControlsRenderer
import com.pafoid.skate.editor.ui.windows.ProjectSwitcherDialog
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.WindowController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditorMenuBarLifecycleTest {

    @Test
    fun `appIconLifecycle_ProjectClosed_InvalidatesAndReloadsTextureId`() {
        val assetsManager = mockk<AssetsManager>(relaxed = true)
        every { assetsManager.getTexture(Assets.Textures.APP_ICON) } returnsMany listOf(texture(101), texture(202))

        val eventSystem = EventSystem()
        val menuBar = EditorMenuBar(
            fileMenu = mockk<FileMenuBuilder>(relaxed = true),
            editMenu = mockk<EditMenuBuilder>(relaxed = true),
            settingsMenu = mockk<SettingsMenuBuilder>(relaxed = true),
            viewMenu = mockk<ViewMenuBuilder>(relaxed = true),
            windowControls = mockk<WindowControlsRenderer>(relaxed = true),
            stringManager = mockk<StringManager>(relaxed = true),
            assetsManager = assetsManager,
            projectManager = mockk<ProjectManager>(relaxed = true),
            eventSystem = eventSystem,
            projectSwitcher = mockk<ProjectSwitcherDialog>(relaxed = true),
            windowController = mockk<WindowController>(relaxed = true),
            projectWizard = mockk<ProjectWizard>(relaxed = true),
            imguiLayer = mockk<ImGuiLayer>(relaxed = true)
        )

        assertEquals(101, readAppIconTexId(menuBar))

        eventSystem.publish(ProjectEvent.Closed("AnyProject"))
        assertEquals(-1, readAppIconTexId(menuBar))

        invokeLoadAppIconTexture(menuBar)
        assertEquals(202, readAppIconTexId(menuBar))
        verify(exactly = 2) { assetsManager.getTexture(Assets.Textures.APP_ICON) }
    }

    private fun texture(id: Int): Texture = Texture().also { it.texId = id }

    private fun readAppIconTexId(menuBar: EditorMenuBar): Int {
        val field = EditorMenuBar::class.java.getDeclaredField("appIconTexId")
        field.isAccessible = true
        return field.getInt(menuBar)
    }

    private fun invokeLoadAppIconTexture(menuBar: EditorMenuBar) {
        val method = EditorMenuBar::class.java.getDeclaredMethod("loadAppIconTexture")
        method.isAccessible = true
        method.invoke(menuBar)
    }
}
