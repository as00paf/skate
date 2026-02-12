package com.pafoid.skate.editor.windows.assetBrowser

import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.utils.StringManager
import imgui.ImGui
import java.io.File

class TexturesTab(
    resourceManager: ResourceManager,
    thumbnailCache: ThumbnailCache,
    stringManager: StringManager,
    ): AssetBrowserTab(resourceManager, thumbnailCache, stringManager) {

    private val supportedTextureFormats = listOf("png", "jpg", "jpeg")

    override fun renderFileItem(file: File) {
        val size = 80f
        val padding = 5f

        ImGui.beginGroup()

        // We can load it since ResourceManager caches it
        // Warning: Loading many large textures might still be heavy on VRAM
        val texId: Int = resourceManager.loadTextureSync(file.path).texId
        ImGui.pushID(file.absolutePath)
        if (ImGui.imageButton("TextureItem", texId.toLong(), size, size, 0f, 0f, 1f, 1f)) {
            // On Click
        }
        ImGui.popID()

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("TEXTURE", file.path)
            ImGui.image(texId.toLong(), size *0.8f, size *0.8f, 0f, 0f, 1f, 1f)
            ImGui.text(file.name)
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(file.name)
        ImGui.dummy(0f, padding)
        ImGui.endGroup()
    }

    override fun refreshAssets() {
        JobSystem.runIO {
            items.clear()
            val texturesDir = File(Assets.Folders.TEXTURES)
            if (texturesDir.exists()) {
                items.addAll(texturesDir.walkTopDown().filter {
                    it.isFile && supportedTextureFormats.contains(it.extension)
                })
            }
        }
    }
}