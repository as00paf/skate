package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Color
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.ui.imgui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.imgui.menus.WindowControlsRenderer
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.ecs.Scene
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.internal.ImGui.image
import imgui.internal.ImGui.popStyleColor
import imgui.internal.ImGui.popStyleVar
import imgui.internal.ImGui.pushStyleColor
import imgui.internal.ImGui.pushStyleVar

/**
 * Renders the main editor menu bar with File, Edit, Settings, and View menus.
 * Delegates menu building to specialized builder components.
 *
 * @param fileMenu Builder for File menu
 * @param editMenu Builder for Edit menu
 * @param settingsMenu Builder for Settings menu
 * @param viewMenu Builder for View menu
 * @param windowControls Renderer for window control buttons
 * @param stringManager Localization for menu labels
 * @param resourceManager To load app icon texture
 */
class EditorMenuBar(
    private val fileMenu: FileMenuBuilder,
    private val editMenu: EditMenuBuilder,
    private val settingsMenu: SettingsMenuBuilder,
    private val viewMenu: ViewMenuBuilder,
    private val windowControls: WindowControlsRenderer,
    private val stringManager: com.pafoid.skate.editor.systems.StringManager,
    private val resourceManager: ResourceManager
) {
    private var appIconTexId = -1
    private val projectIcon = Icons.CUBE
    private val projectName = "Skate Project"

    init {
        appIconTexId = resourceManager.loadTextureSync(Assets.Textures.APP_ICON).texId
    }

    fun render(currentScene: Scene) {
        if (imgui.internal.ImGui.beginMenuBar()) {
            val barHeight = 48f

            renderAppIcon(barHeight)
            renderHamburgerMenu(currentScene, barHeight)
            renderProjectInfo(barHeight)
            windowControls.render()

            imgui.internal.ImGui.endMenuBar()
        }
    }

    private fun renderAppIcon(barHeight: Float) {
        if (appIconTexId != -1) {
            val iconSize = 32f

            ImGui.setCursorPosY((barHeight - iconSize) / 2f)
            image(appIconTexId.toLong(), iconSize, iconSize)
        }
    }

    private fun renderHamburgerMenu(currentScene: Scene, barHeight: Float) {
        val btnSize = 30f
        val offsetY = (barHeight - btnSize) / 2f
        ImGui.setCursorPosY(offsetY)

        if (ImGui.button(Icons.MENU, btnSize, btnSize)) {
            ImGui.openPopup("main_hamburger_menu")
        }

        if (ImGui.beginPopup("main_hamburger_menu")) {
            fileMenu.render(currentScene)
            editMenu.render()
            settingsMenu.render()
            viewMenu.render()
            ImGui.separator()
            if (ImGui.menuItem(stringManager.getString("menu.file.quit"))) {
                // Window close handled by WindowControlsRenderer
            }
            ImGui.endPopup()
        }
    }

    private fun renderProjectInfo(barHeight: Float) {
        val fontSize = ImGui.getFontSize()
        val textY = (barHeight - fontSize) / 2f * 0.8f
        ImGui.setCursorPosY(textY)

        ImGui.textDisabled("|")
        ImGui.setCursorPosY(textY)
        ImGui.textColored(
            Color.ISLAND_ACCENT_BLUE.x,
            Color.ISLAND_ACCENT_BLUE.y,
            Color.ISLAND_ACCENT_BLUE.z,
            Color.ISLAND_ACCENT_BLUE.w,
            projectIcon
        )
        ImGui.setCursorPosY(textY)
        ImGui.text(projectName)
    }
}
