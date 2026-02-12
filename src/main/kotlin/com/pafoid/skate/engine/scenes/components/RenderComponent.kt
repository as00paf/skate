package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.models.BaseModel
import com.pafoid.skate.engine.scenes.GameObject
import kotlinx.serialization.Serializable
import org.koin.core.component.inject

@Serializable
class RenderComponent(
    val model: BaseModel,
    var shininess: Float = 10f,
    var reflectivity: Float = 1f,
    var textureScale: Float = 1.0f,
    var renderMode: RenderMode = RenderMode.MESH
) : Component() {

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        val resourceManager: ResourceManager by inject()
        
        model.mesh.forEach { meshPart ->
            val mat = meshPart.material
            mat.baseColorPath?.let { mat.baseColorTexture = resourceManager.loadTextureSync(it) }
            mat.normalMapPath?.let { mat.normalMap = resourceManager.loadTextureSync(it) }
            mat.metallicRoughnessPath?.let { mat.metallicRoughnessTexture = resourceManager.loadTextureSync(it) }
            mat.aoPath?.let { mat.aoTexture = resourceManager.loadTextureSync(it) }
            mat.emissivePath?.let { mat.emissiveTexture = resourceManager.loadTextureSync(it) }
        }
    }

    override fun update(dt: Float) {
        // Render component doesn't need per-frame updates
    }
}
