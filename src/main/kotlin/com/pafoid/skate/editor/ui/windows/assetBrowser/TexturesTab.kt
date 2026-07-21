package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.commands.objects.ApplyTextureCommand
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import java.awt.Desktop
import java.io.File

class TexturesTab(
    stringManager: StringManager,
    private val engine: Engine,
    private val undoRedoManager: UndoRedoManager,
) : AssetBrowserTab(engine.assetsManager, stringManager) {

    init {
        refreshAssets()
    }

    override fun renderFileItem(file: File) {
        val size = 80f
        val padding = 5f

        ImGui.beginGroup()
        val texId: Int = assetsManager.getTexture(file.path).texId
        ImGui.pushID(file.absolutePath)
        if (ImGui.imageButton("TextureItem", texId.toLong(), size, size, 0f, 0f, 1f, 1f)) {
            // On Click
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(file.name)
        }
        
        // Context menu on right-click
        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("${Icons.CHECK} ${stringManager.getString("context.asset_browser.apply_to_selected")}")) {
                applyTextureToSelected(file.path)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.EXTERNAL_LINK} ${stringManager.getString("context.asset_browser.open_external")}")) {
                Desktop.getDesktop().open(file)
            }
            if (ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}")) {
                Desktop.getDesktop().open(file.parentFile)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.ARROW_ROTATE} ${stringManager.getString("context.asset_browser.refresh")}")) {
                engine.logger.logEditor("Refresh requested for: ${file.name}")
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}")) {
                val tex = assetsManager.getTexture(file.path)
                engine.logger.logEditor("Texture: ${file.name}, Size: ${tex.width}x${tex.height}, ID: ${tex.texId}")
            }
            ImGui.endPopup()
        }
        
        ImGui.popID()

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("TEXTURE", file.path)
            ImGui.image(texId.toLong(), size *1.5f, size *1.5f, 0f, 0f, 1f, 1f)
            ImGui.text(file.name)
            val tex = assetsManager.getTexture(file.path)
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, "${tex.width}x${tex.height}")
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(file.name)
        ImGui.dummy(0f, padding)
        ImGui.endGroup()
    }
    
    private fun applyTextureToSelected(texturePath: String) {
        val scene = engine.sceneManager.currentScene ?: return
        val selectedObject = scene.selectedGameObject ?: run {
            engine.logger.logEditor("No object selected")
            return
        }
        
        val renderComponent = selectedObject.getComponent<RenderComponent>() ?: run {
            engine.logger.logEditor("Selected object has no RenderComponent")
            return
        }

        undoRedoManager.executeCommand(
            ApplyTextureCommand(selectedObject, texturePath, assetsManager, engine.eventSystem)
        )
        engine.logger.logEditor("Applied texture ${texturePath} to ${selectedObject.name}")
    }

    override fun refreshAssets() {
        engine.jobSystem.runIO {
            val fileExtensions = setOf("png", "jpg", "jpeg")
            items.clear()
            val texturesDir = File(Assets.Folders.TEXTURES)
            if (texturesDir.exists()) {
                items.addAll(texturesDir.walkTopDown().filter {
                    it.isFile && fileExtensions.contains(it.extension) && assetsManager.hasTexture(it.absolutePath)
                })
            }
        }
    }
}