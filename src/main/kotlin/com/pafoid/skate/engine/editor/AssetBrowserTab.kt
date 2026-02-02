package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.utils.Icons
import com.pafoid.skate.engine.utils.JobSystem
import imgui.ImGui
import imgui.flag.ImGuiTableFlags
import imgui.type.ImString
import java.io.File

class AssetBrowserTab(private val resourceManager: ResourceManager, private val thumbnailCache: ThumbnailCache) {
    private val loadingSet = HashSet<String>()

    fun render(label:String, searchText: ImString, type: Type, items:List<File>, refreshAssets:()->Unit) {
        ImGui.inputTextWithHint(label, "${Icons.SEARCH} Search...", searchText)
        ImGui.sameLine()
        if (ImGui.button(Icons.ARROW_ROTATE)) {
            refreshAssets()
        }
        ImGui.separator()

        val files = items.filter { it.name.contains(searchText.get(), ignoreCase = true) }

        if (ImGui.beginTable("$label Table", 4, ImGuiTableFlags.SizingFixedFit)) {
            for (file in files) {
                ImGui.tableNextColumn()
                renderFileItem(file, type)
            }
            ImGui.endTable()
        }
    }

    private fun renderFileItem(file: File, type: Type) {
        val size = 80f
        val padding = 5f

        ImGui.beginGroup()

        val texId: Int = if (type == Type.TEXTURES) {
            // We can load it since ResourceManager caches it
            // Warning: Loading many large textures might still be heavy on VRAM
            resourceManager.loadTextureSync(file.path).texId
        } else {
            // Models
            val model = resourceManager.getModel(file.path)
            if (model != null) {
                // Model is loaded, use/generate thumbnail (ThumbnailCache handles FBO rendering on main thread)
                // Note: We need a unique ID for the thumbnail cache
                thumbnailCache.getThumbnail(file.absolutePath, model)
            } else {
                // Model not loaded yet
                if (!loadingSet.contains(file.path)) {
                    loadingSet.add(file.path)
                    JobSystem.runAsync {
                        try {
                            resourceManager.loadModel(file.path)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            JobSystem.runOnMain {
                                loadingSet.remove(file.path)
                            }
                        }
                    }
                }
                // Return placeholder
                resourceManager.loadTextureSync(Assets.Textures.DEFAULT).texId
            }
        }

        // Flip UVs for direct texture rendering (stb_image loads top-down usually, but OpenGL expects bottom-up)
        // For FBOs (Models), we usually render them correctly for the quad.
        val uv0Y = if (type == Type.TEXTURES) 0f else 1f
        val uv1Y = if (type == Type.TEXTURES) 1f else 0f

        ImGui.pushID(file.absolutePath)
        if (ImGui.imageButton("FileItem", texId.toLong(), size, size, 0f, uv0Y, 1f, uv1Y)) {
            // On Click
        }
        ImGui.popID()

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("ASSET_$type", file.path)
            ImGui.image(texId.toLong(), 64f, 64f, 0f, uv0Y, 1f, uv1Y)
            ImGui.text(file.name)
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(file.name)
        ImGui.dummy(0f, padding)
        ImGui.endGroup()
    }


    enum class Type {
        MODELS,
        TEXTURES,
    }
}
