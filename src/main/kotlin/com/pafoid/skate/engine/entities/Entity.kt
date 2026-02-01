package com.pafoid.skate.engine.entities

import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.utils.MImGui
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
class Entity(
    val model: TexturedModel,
    val transform: Transform = Transform(),
    var shininess: Float = 10f,
    var reflectivity: Float = 1f,
    var textureScale: Float = 1.0f,
    @Transient val onTick: (dt:Float) -> Unit = {}
): Component(), KoinComponent {

    @Transient
    lateinit var resourceManager: ResourceManager

    @Transient var skeleton: Skeleton? = null
        private set

    init {
        skeleton = model.skeleton?.copy()
        // Inject dependencies manually if needed, or rely on external injection
        // Since it's a KoinComponent, we can use get() if not using inject delegate
        if (!this::resourceManager.isInitialized) {
            resourceManager = org.koin.core.context.GlobalContext.get().get()
        }
    }

    override fun update(dt: Float) {
        onTick(dt)
        skeleton?.update()
    }

    fun translate(dx: Float = 0f, dy: Float = 0f, dz: Float = 0f) {
        translate(Vector3f(dx, dy, dz))
    }

    fun translate(translation: Vector3f) {
        transform.translation.x += translation.x
        transform.translation.y += translation.y
        transform.translation.z += translation.z
    }

    fun rotate(rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) {
        rotate(Vector3f(rx, ry, rz))
    }

    fun rotate(rotation: Vector3f) {
        transform.rotation.x += rotation.x
        transform.rotation.y += rotation.y
        transform.rotation.z += rotation.z
    }

    override fun imgui() {
        if (imgui.ImGui.collapsingHeader("Material")) {
            for ((index, part) in model.parts.withIndex()) {
                if (imgui.ImGui.treeNode("Part $index")) {
                    val mat = part.material

                    // Texture Display
                    val tex = mat.baseColorTexture ?: resourceManager.loadTextureSync(Assets.Textures.WHITE)
                    imgui.ImGui.text("Base Texture: ${mat.baseColorPath ?: "Embedded/Generated"}")
                    imgui.ImGui.image(tex.texId.toLong(), 64f, 64f, 0f, 1f, 1f, 0f)

                    if (MImGui.colorPicker4("Base Color", mat.baseColorFactor)) {
                        // Color changed
                    }

                    val roughness = floatArrayOf(mat.roughnessFactor)
                    if (imgui.ImGui.dragFloat("Roughness", roughness, 0.01f, 0f, 1f)) {
                        mat.roughnessFactor = roughness[0]
                    }

                    val metallic = floatArrayOf(mat.metallicFactor)
                    if (imgui.ImGui.dragFloat("Metallic", metallic, 0.01f, 0f, 1f)) {
                        mat.metallicFactor = metallic[0]
                    }

                    imgui.ImGui.treePop()
                }
            }

            val scale = floatArrayOf(textureScale)
            if (imgui.ImGui.dragFloat("UV Scale", scale, 0.1f, 0.01f, 100f)) {
                textureScale = scale[0]
            }
        }
    }
}

    