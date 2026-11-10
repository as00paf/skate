package com.pafoid.skate.editor.imgui

import com.pafoid.skate.app.Editor
import com.pafoid.skate.editor.events.ProjectEvent
import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.data.Color
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.EDITOR_MENU_BAR_HEIGHT
import com.pafoid.skate.editor.systems.ProjectManager
import com.pafoid.skate.editor.ui.menus.AboutModal
import com.pafoid.skate.editor.ui.menus.EditMenuBuilder
import com.pafoid.skate.editor.ui.menus.FileMenuBuilder
import com.pafoid.skate.editor.ui.menus.HamburgerMenuModel
import com.pafoid.skate.editor.ui.menus.HelpMenuBuilder
import com.pafoid.skate.editor.ui.menus.PlayMenuBuilder
import com.pafoid.skate.editor.ui.menus.SettingsMenuBuilder
import com.pafoid.skate.editor.ui.menus.ToolsMenuBuilder
import com.pafoid.skate.editor.ui.menus.ViewMenuBuilder
import com.pafoid.skate.editor.ui.menus.WindowControlsRenderer
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.internal.ImGui.image

/**
 * The title bar: app icon, the hamburger menu (EDT-51 §2.7: File, Edit, View, Play, Settings, Tools, Help, drawn from
 * [HamburgerMenuModel]), the project switcher and the window controls.
 */
class EditorMenuBar(
    engine: Engine,
    editor: Editor,
    windowRegistry: WindowRegistry,
    private val projectManager: ProjectManager,
) {
    private val eventSystem = engine.eventSystem
    private val stringManager = engine.stringManager
    private val assetsManager = engine.assetsManager
    private val sceneManager = engine.sceneManager
    private val settingsManager = editor.settingsManager

    private var appIconTexId = -1

    private val aboutModal = AboutModal(stringManager)
    private val fileMenu = FileMenuBuilder(stringManager, eventSystem, projectManager, settingsManager, sceneManager)
    private val editMenu = EditMenuBuilder(stringManager, sceneManager, eventSystem)
    private val viewMenu = ViewMenuBuilder(stringManager, eventSystem, windowRegistry)
    private val playMenu = PlayMenuBuilder(stringManager, eventSystem, editor.playModeHandler.controller)
    private val settingsMenu = SettingsMenuBuilder(stringManager, settingsManager, eventSystem)
    private val toolsMenu = ToolsMenuBuilder(stringManager, eventSystem)
    private val helpMenu = HelpMenuBuilder(stringManager, aboutModal)
    private val windowControls = WindowControlsRenderer(eventSystem, stringManager)

    init {
        eventSystem.subscribe<ProjectEvent.Opened> { event ->
            appIconTexId = assetsManager.getTexture(event.project.iconPath).texId
        }
        eventSystem.subscribe<ProjectEvent.Closed> {
            appIconTexId = assetsManager.getBundledTexture(Assets.Bundled.APP_ICON).texId
        }
        appIconTexId = assetsManager.getBundledTexture(Assets.Bundled.APP_ICON).texId
    }

    fun render() {
        toolsMenu.handleShortcut()
        if (ImGui.beginMenuBar()) {
            renderAppIcon()
            renderHamburgerMenu()
            renderProjectInfo()
            windowControls.render()

            ImGui.endMenuBar()
        }
        aboutModal.render()
    }

    private fun renderAppIcon() {
        if (appIconTexId != -1) {
            val iconSize = 32f

            ImGui.setCursorPosY((EDITOR_MENU_BAR_HEIGHT - iconSize) / 2f)
            image(appIconTexId.toLong(), iconSize, iconSize)
        }
    }

    private fun renderHamburgerMenu() {
        val btnSize = 30f
        val offsetY = (EDITOR_MENU_BAR_HEIGHT - btnSize) / 2f
        ImGui.setCursorPosY(offsetY)

        if (stringManager.tippedWith(ImGui.button(Icons.MENU, btnSize, btnSize), "tooltip.menu_bar.main_menu")) {
            ImGui.openPopup("main_hamburger_menu")
        }

        if (ImGui.beginPopup("main_hamburger_menu")) {
            fileMenu.render()
            editMenu.render()
            viewMenu.render()
            playMenu.render()
            settingsMenu.render()
            toolsMenu.render()
            helpMenu.render()
            ImGui.endPopup()
        }
    }

    private fun renderProjectInfo() {
        val fontSize = ImGui.getFontSize()
        val textY = (EDITOR_MENU_BAR_HEIGHT - fontSize) / 2f * 0.8f
        ImGui.setCursorPosY(textY)

        ImGui.textDisabled("|")
        ImGui.setCursorPosY(textY)
        ImGui.textColored(
            Color.ISLAND_ACCENT_BLUE.x,
            Color.ISLAND_ACCENT_BLUE.y,
            Color.ISLAND_ACCENT_BLUE.z,
            Color.ISLAND_ACCENT_BLUE.w,
            Icons.CUBE
        )
        ImGui.setCursorPosY(textY)

        val currentProjectName = projectManager.currentProject?.name ?: stringManager.getString("lbl.menu_bar.no_project")
        if (ImGui.button(currentProjectName)) {
            eventSystem.publish(WindowAction.Show("window.project_switcher"))
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.switch_projects"))
        }
    }

    fun setMaximized(maximized: Boolean) {
        windowControls.isMaximized = maximized
    }
}

/** What a hamburger menu item does this frame: [run] on click, greyed out unless [enabled], ticked when [checked]. */
class MenuAction(val enabled: Boolean = true, val checked: Boolean = false, val run: () -> Unit)

/**
 * Draws [HamburgerMenuModel] menus. Every item gets its icon, shortcut and tooltip from the model; the builder only
 * maps item ids to [MenuAction]s (null hides the item). Separators never lead, trail or double up.
 */
object HamburgerMenuRenderer {
    fun menu(
        strings: StringManager,
        menu: HamburgerMenuModel.Menu,
        actions: (String) -> MenuAction?,
        dynamicSubmenu: (HamburgerMenuModel.Submenu) -> Unit = {},
    ) {
        if (!ImGui.beginMenu(strings.label(menu.labelKey))) return
        entries(strings, menu.entries, actions, dynamicSubmenu)
        ImGui.endMenu()
    }

    private fun entries(
        strings: StringManager,
        entries: List<HamburgerMenuModel.Entry>,
        actions: (String) -> MenuAction?,
        dynamicSubmenu: (HamburgerMenuModel.Submenu) -> Unit,
    ) {
        var drawn = false
        var separatorPending = false
        fun beforeDrawing() {
            if (separatorPending && drawn) ImGui.separator()
            separatorPending = false
            drawn = true
        }
        for (entry in entries) {
            when (entry) {
                HamburgerMenuModel.Separator -> separatorPending = true
                is HamburgerMenuModel.Item -> {
                    val action = actions(entry.id) ?: continue
                    beforeDrawing()
                    item(strings, entry, action)
                }
                is HamburgerMenuModel.Submenu -> {
                    beforeDrawing()
                    if (ImGui.beginMenu(strings.label(entry.labelKey))) {
                        if (entry.entries.isEmpty()) dynamicSubmenu(entry) else entries(strings, entry.entries, actions, dynamicSubmenu)
                        ImGui.endMenu()
                    }
                }
            }
        }
    }

    private fun item(strings: StringManager, item: HamburgerMenuModel.Item, action: MenuAction) {
        val label = (item.icon?.let { "$it " } ?: "") + strings.getString(item.labelKey) + "##" + item.id
        val ticked = item.kind != HamburgerMenuModel.Kind.ACTION && action.checked
        val clicked = ImGui.menuItem(label, item.shortcut?.display() ?: "", ticked, action.enabled)
        Tooltips.show(strings, item.tooltipKey, item.shortcut)
        if (clicked) action.run()
    }
}
