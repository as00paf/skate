package com.pafoid.skate.engine.entities

import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.models.TexturedModel
import com.pafoid.skate.engine.scenes.components.Component
import com.pafoid.skate.engine.utils.MImGui
import imgui.ImGui
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.joml.Vector3f

@Serializable
class Entity(
    val model: TexturedModel,
    val transform: Transform = Transform(),
    var shininess: Float = 10f,
    var reflectivity: Float = 1f,
    var textureScale: Float = 1.0f,
    @Transient val onTick: (dt:Float) -> Unit = {}
): Component() {

    @Transient var skeleton: Skeleton? = null
        private set

    init {
        skeleton = model.skeleton?.copy()
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
        if (ImGui.collapsingHeader("Material")) {
            for ((index, part) in model.parts.withIndex()) {
                if (ImGui.treeNode("Part $index")) {
                    val mat = part.material

                    // Texture Display
                    val tex = mat.baseColorTexture
                    ImGui.text("Base Texture: ${mat.baseColorPath ?: "Embedded/Generated"}")
                    tex?.let {
                        ImGui.image(tex.texId.toLong(), 64f, 64f, 0f, 1f, 1f, 0f)
                    }

                    if (MImGui.colorPicker4("Base Color", mat.baseColorFactor)) {
                        // Color changed
                    }

                    val roughness = floatArrayOf(mat.roughnessFactor)
                    if (ImGui.dragFloat("Roughness", roughness, 0.01f, 0f, 1f)) {
                        mat.roughnessFactor = roughness[0]
                    }

                    val metallic = floatArrayOf(mat.metallicFactor)
                    if (ImGui.dragFloat("Metallic", metallic, 0.01f, 0f, 1f)) {
                        mat.metallicFactor = metallic[0]
                    }

                    ImGui.treePop()
                }
            }

            val scale = floatArrayOf(textureScale)
            if (ImGui.dragFloat("UV Scale", scale, 0.1f, 0.01f, 100f)) {
                textureScale = scale[0]
            }
        }
    }
}

    