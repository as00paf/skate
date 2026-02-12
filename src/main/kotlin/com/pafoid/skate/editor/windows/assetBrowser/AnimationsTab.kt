package com.pafoid.skate.editor.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.utils.JobSystem
import imgui.ImGui
import java.io.File

class AnimationsTab(
    resourceManager: ResourceManager,
    thumbnailCache: ThumbnailCache,
    stringManager: StringManager
): AssetBrowserTab(resourceManager, thumbnailCache, stringManager) {

    private val supportedAnimationFormats = listOf("fbx")

    override fun renderFileItem(file: File) {
        val size = 80f
        val padding = 5f

        ImGui.beginGroup()

        ImGui.pushID(file.absolutePath)
        // Use a generic icon for animations since we don't have thumbnails
        if (ImGui.button("${Icons.PLAY}", size, size)) {
            // Preview?
        }
        ImGui.popID()

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("ANIMATION", file.path)
            ImGui.text("${Icons.PLAY}")
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
            val animationsDir = File(Assets.Folders.ANIMATIONS)
            if(animationsDir.exists()) {
                items.addAll(
                    animationsDir.walkTopDown().filter {
                        it.isFile && supportedAnimationFormats.contains(it.extension)
                    })
            }
        }
    }
}