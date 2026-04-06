package com.pafoid.skate.editor.windows.assetBrowser

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.ThumbnailCache
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.database.AssetDatabase
import com.pafoid.skate.engine.assets.database.AssetType
import com.pafoid.skate.engine.utils.JobSystem
import imgui.ImGui
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class AnimationsTab(
    resourceManager: ResourceManager,
    thumbnailCache: ThumbnailCache,
    stringManager: StringManager,
    assetDatabase: AssetDatabase? = null
): AssetBrowserTab(resourceManager, thumbnailCache, stringManager, assetDatabase), KoinComponent {

    private val logger: LoggerService by inject()

    private val supportedAnimationFormats = listOf("fbx")

    override fun renderFileItem(file: File) {
        val size = 80f
        val padding = 5f

        ImGui.beginGroup()

        ImGui.pushID(file.absolutePath)

        if (ImGui.button("${Icons.PLAY}", size, size)) {
            previewAnimation(file)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(file.name)
        }

        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("${Icons.PLAY} ${stringManager.getString("context.asset_browser.preview_animation")}")) {
                previewAnimation(file)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.CHECK} ${stringManager.getString("context.asset_browser.apply_to_selected")}")) {
                applyAnimationToSelected(file.path)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.FOLDER} ${stringManager.getString("context.asset_browser.show_in_folder")}")) {
                java.awt.Desktop.getDesktop().open(file.parentFile)
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.INFO} ${stringManager.getString("context.asset_browser.properties")}")) {
                logger.logEditor("Animation: ${file.name}, Format: ${file.extension}, Path: ${file.absolutePath}")
            }
            ImGui.endPopup()
        }
        
        ImGui.popID()

        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("ANIMATION", file.path)
            ImGui.text("${Icons.PLAY} ${file.name}")
            ImGui.textColored(0.5f, 0.5f, 0.5f, 1f, stringManager.getString("lbl.asset_browser.drop_animation_hint"))
            ImGui.endDragDropSource()
        }

        ImGui.textWrapped(file.name)
        ImGui.dummy(0f, padding)
        ImGui.endGroup()
    }
    
    private fun previewAnimation(file: File) {
        logger.logEditor("Preview animation: ${file.name} (not yet implemented)")
    }
    
    private fun applyAnimationToSelected(animationPath: String) {
        // Future enhancement: Apply animation to selected GameObject's Animator component
        logger.logEditor("Apply animation to selected not yet implemented: $animationPath")
    }

    override fun refreshAssets() {
        JobSystem.runIO {
            // Animation assets not yet in AssetDatabase — use directory fallback
            refreshFromDirectory(supportedAnimationFormats.toSet())
        }
    }

    override fun refreshFromDirectory(fileExtensions: Set<String>) {
        items.clear()
        val animationsDir = File(Assets.Folders.ANIMATIONS)
        if(animationsDir.exists()) {
            items.addAll(
                animationsDir.walkTopDown().filter {
                    it.isFile && fileExtensions.contains(it.extension)
                })
        }
    }
}