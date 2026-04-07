package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetType
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.utils.JobSystem
import imgui.ImGui
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.awt.Desktop
import java.io.File

class TexturesTab(
    resourceManager: ResourceManager,
    thumbnailCache: ThumbnailCache,
    stringManager: StringManager,
    assetDatabase: AssetDatabase? = null
    ): AssetBrowserTab(resourceManager, thumbnailCache, stringManager, assetDatabase), KoinComponent {

    private val logger: LoggerService by inject()
    private val sceneManager: SceneManager by inject()

    private val supportedTextureFormats = listOf("png", "jpg", "jpeg")

    override fun renderFileItem(file: File) {
        val size = 80f
        val padding = 5f

        ImGui.beginGroup()
        val texId: Int = resourceManager.loadTextureSync(file.path).texId
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
                logger.logEditor("Refresh requested for: ${file.name}")
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}")) {
                val tex = resourceManager.loadTextureSync(file.path)
                logger.logEditor("Texture: ${file.name}, Size: ${tex.width}x${tex.height}, ID: ${tex.texId}")
            }
            ImGui.endPopup()
        }
        
        ImGui.popID()

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("TEXTURE", file.path)
            ImGui.image(texId.toLong(), size *1.5f, size *1.5f, 0f, 0f, 1f, 1f)
            ImGui.text(file.name)
            val tex = resourceManager.loadTextureSync(file.path)
            ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, "${tex.width}x${tex.height}")
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(file.name)
        ImGui.dummy(0f, padding)
        ImGui.endGroup()
    }
    
    private fun applyTextureToSelected(texturePath: String) {
        val scene = sceneManager.currentScene ?: return
        val selectedObject = scene.getSelectedGameObject() ?: run {
            logger.logEditor("No object selected")
            return
        }
        
        val renderComponent = selectedObject.getComponent<RenderComponent>() ?: run {
            logger.logEditor("Selected object has no RenderComponent")
            return
        }
        
        val texture = resourceManager.loadTextureSync(texturePath)
        logger.logEditor("Applied texture ${texturePath} to ${selectedObject.name}")
    }

    override fun refreshAssets() {
        JobSystem.runIO {
            refreshFromDatabase(AssetType.TEXTURE, setOf("png", "jpg", "jpeg"))
        }
    }

    override fun refreshFromDirectory(fileExtensions: Set<String>) {
        items.clear()
        val texturesDir = File(Assets.Folders.TEXTURES)
        if (texturesDir.exists()) {
            items.addAll(texturesDir.walkTopDown().filter {
                it.isFile && fileExtensions.contains(it.extension)
            })
        }
    }
}