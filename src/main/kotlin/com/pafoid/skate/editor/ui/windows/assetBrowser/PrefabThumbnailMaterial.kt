package com.pafoid.skate.editor.ui.windows.assetBrowser

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.game.prefabs.MaterialType
import com.pafoid.skate.game.prefabs.PrefabCatalog
import com.pafoid.skate.game.prefabs.PrefabEntry
import java.io.File

/**
 * Surface material of a prefab thumbnail (AST-13): the same texture `PrefabsGenerator` applies when the prefab is
 * spawned, so obstacle and generic prefab thumbnails are not drawn untextured.
 */
object PrefabThumbnailMaterial {

    /** The surface the thumbnail shows: [requested] when [entry] offers it, else its default; null when it has none. */
    fun surfaceFor(entry: PrefabEntry, requested: MaterialType?): MaterialType? = PrefabCatalog.materialFor(entry.type, requested)

    /**
     * Texture file of [surface]: the project's bundled copy (`Assets/EngineDefaults/Textures/...`) when it exists, else
     * the engine's source texture under [engineRoot]; null when neither exists.
     */
    fun texturePath(
        surface: MaterialType,
        projectRoot: File?,
        engineRoot: File = File(System.getProperty("user.dir")),
        exists: (File) -> Boolean = File::isFile,
    ): String? {
        val bundled = surface.texturePath
        projectRoot?.let { File(File(it, AssetDiscovery.PROJECT_ASSETS_DIR), bundled.replace('\\', File.separatorChar)) }
            ?.takeIf(exists)?.let { return it.path }
        val source = Assets.Bundled.bundledAssets.firstOrNull { it.first == bundled }?.second ?: return null
        return File(engineRoot, source).takeIf(exists)?.path
    }

    /** Thumbnail cache id: one thumbnail per model and surface, so changing the surface re-renders it. */
    fun cacheKey(entry: PrefabEntry, surface: MaterialType?): String = "${entry.modelPath}#${surface?.name ?: "-"}"

    /** [model] with every part using [texture] as base color, or [model] itself when [texture] is null. */
    fun applyTexture(model: `3dModel`, texture: Texture?): `3dModel` {
        if (texture == null) return model
        return `3dModel`(path = model.path, mesh = model.mesh.map { it.copy(material = Material(texture)) }, skeleton = model.skeleton)
    }
}
