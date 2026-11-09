package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.contextPopupId
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
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

    override fun renderFileItem(file: File) {
        val size = 80f
        val padding = 5f

        ImGui.beginGroup()
        // Never loads on the UI thread: a placeholder until the worker has decoded and shrunk the image (AST-23)
        val thumb = thumbnails.get(file)
        ImGui.pushID(file.absolutePath)
        if (thumb != null) {
            ImGui.imageButton("TextureItem", thumb.texId, size, size, 0f, 0f, 1f, 1f)
        } else {
            ImGui.button(if (thumbnails.state(file) is AsyncAssetCache.State.Failed) Icons.WINDOW_CLOSE else Icons.FILE_IMAGE, size, size)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(file.name)
        }
        
        // Context menu on right-click
        if (ImGui.beginPopupContextItem(contextPopupId("texture"))) {
            if (stringManager.tipped(ImGui.menuItem("${Icons.CHECK} ${stringManager.getString("context.asset_browser.apply_to_selected")}"), "context.asset_browser.apply_to_selected")) {
                applyTextureToSelected(file.path)
            }
            ImGui.separator()
            if (stringManager.tipped(ImGui.menuItem("${Icons.EXTERNAL_LINK} ${stringManager.getString("context.asset_browser.open_external")}"), "context.asset_browser.open_external")) {
                Desktop.getDesktop().open(file)
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}"), "context.asset_browser.show_in_folder")) {
                Desktop.getDesktop().open(file.parentFile)
            }
            ImGui.separator()
            if (stringManager.tipped(ImGui.menuItem("${Icons.ARROW_ROTATE} ${stringManager.getString("context.asset_browser.refresh")}"), "context.asset_browser.refresh")) {
                engine.logger.logEditor("Refresh requested for: ${file.name}")
            }
            ImGui.separator()
            if (stringManager.tipped(ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}"), "context.asset_browser.properties")) {
                val dims = thumb?.let { "${it.sourceWidth}x${it.sourceHeight}" } ?: "?"
                engine.logger.logEditor("Texture: ${file.name}, Size: $dims, Path: ${file.absolutePath}")
            }
            ImGui.endPopup()
        }
        
        ImGui.popID()

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("TEXTURE", file.path)
            thumb?.let { ImGui.image(it.texId, size * 1.5f, size * 1.5f, 0f, 0f, 1f, 1f) }
            ImGui.text(file.name)
            thumb?.let { ImGui.textColored(0.7f, 0.7f, 0.7f, 1f, "${it.sourceWidth}x${it.sourceHeight}") }
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

        engine.eventSystem.publish(ViewportAction.DropTexture(texturePath, selectedObject))
        engine.logger.logEditor("Applied texture ${texturePath} to ${selectedObject.name}")
    }

    private val thumbnails = AsyncAssetCache<File, ThumbnailPixels, TextureThumbnail>(
        io = { job -> engine.jobSystem.runIO { job() } },
        decode = { ThumbnailScaler.decode(it) },
        upload = { _, pixels -> ThumbnailScaler.upload(pixels) },
        dispose = { it.texture.close() },
        discard = { it.free() },
        uploadsPerFrame = UPLOADS_PER_FRAME,
    )

    override fun pumpPreviews() {
        thumbnails.pump()
    }

    override fun onProjectChanged() = thumbnails.clear()

    override fun refreshAssets() = scan(BrowserAssetType.TEXTURE) { job -> engine.jobSystem.runIO { job() } }

    private companion object {
        /** Small (≤128 px, no mips) uploads: a handful per frame keeps the frame time flat. */
        const val UPLOADS_PER_FRAME = 4
    }
}
