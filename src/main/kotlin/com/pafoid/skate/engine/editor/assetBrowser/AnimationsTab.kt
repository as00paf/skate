package com.pafoid.skate.engine.editor.assetBrowser

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.editor.ThumbnailCache
import com.pafoid.skate.engine.utils.JobSystem
import com.pafoid.skate.engine.utils.StringManager
import java.io.File

class AnimationsTab(
    resourceManager: ResourceManager,
    thumbnailCache: ThumbnailCache,
    stringManager: StringManager
): AssetBrowserTab(resourceManager, thumbnailCache, stringManager) {

    private val supportedAnimationFormats = listOf("fbx")

    override fun renderFileItem(file: File) {

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