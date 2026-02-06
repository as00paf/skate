package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.models.MeshPart
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

class ModelRenderer(
    private val resourceManager: ResourceManager
) {
    
    fun render(
        go: GameObject,
        renderComponent: RenderComponent,
        defaultShader: Shader,
        cameraPosition: Vector3f,
        skeletonComponent: SkeletonComponent? = null
    ) {
        val transformComponent = go.getComponent<com.pafoid.skate.engine.scenes.components.Transform>()
        val transformationMatrix = transformComponent?.toWorldMatrix() ?: org.joml.Matrix4f().identity()
        
        val textureScale = renderComponent.textureScale
        
        for (part in renderComponent.model.mesh) {
            renderMeshPart(
                part,
                transformationMatrix,
                textureScale,
                skeletonComponent?.skeleton,
                defaultShader,
                cameraPosition
            )
        }
    }
    
    private fun renderMeshPart(
        part: MeshPart,
        transformationMatrix: org.joml.Matrix4f,
        textureScale: Float,
        skeleton: com.pafoid.skate.engine.animation.Skeleton?,
        shader: Shader,
        cameraPosition: Vector3f
    ) {
        val model = part.rawModel
        val material = part.material

        // Upload transformation matrices
        shader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, transformationMatrix)
        shader.uploadFloat(Uniforms.TEXTURE_SCALE, textureScale)
        shader.uploadVec3f(Uniforms.CAMERA_POSITION, cameraPosition)

        glBindVertexArray(model.vaoId)

        // Enable only available attributes
        model.enabledAttributes.forEach { glEnableVertexAttribArray(it) }

        // Base Color
        glActiveTexture(GL_TEXTURE0)
        material.baseColorTexture?.bind() ?: resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind()
        shader.uploadInt(Uniforms.BASE_COLOR_TEXTURE, 0)
        shader.uploadVec4f(Uniforms.BASE_COLOR_FACTOR, material.baseColorFactor)

        // Normal Map
        glActiveTexture(GL_TEXTURE1)
        val hasNormal = material.normalMap != null
        if (hasNormal) material.normalMap?.bind()
        else resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind() // Bind dummy
        shader.uploadInt(Uniforms.NORMAL_MAP, 1)
        shader.uploadBoolean(Uniforms.HAS_NORMAL_MAP, hasNormal)

        // Metallic Roughness
        glActiveTexture(GL_TEXTURE2)
        val hasMR = material.metallicRoughnessTexture != null
        if (hasMR) material.metallicRoughnessTexture?.bind()
        else resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind() // Bind dummy
        shader.uploadInt(Uniforms.METALLIC_ROUGHNESS_TEXTURE, 2)
        shader.uploadBoolean(Uniforms.HAS_METALLIC_ROUGHNESS_TEXTURE, hasMR)
        shader.uploadFloat(Uniforms.METALLIC_FACTOR, material.metallicFactor)
        shader.uploadFloat(Uniforms.ROUGHNESS_FACTOR, material.roughnessFactor)

        // AO
        glActiveTexture(GL_TEXTURE3)
        val hasAO = material.aoTexture != null
        material.aoTexture?.bind() ?: resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind()
        shader.uploadInt(Uniforms.AO_TEXTURE, 3)
        shader.uploadBoolean(Uniforms.HAS_AO_TEXTURE, hasAO)

        // Emissive
        glActiveTexture(GL_TEXTURE4)
        val hasEmissive = material.emissiveTexture != null
        if (hasEmissive) material.emissiveTexture?.bind()
        else resourceManager.loadTextureSync(Assets.Textures.DEFAULT).bind() // Bind dummy
        shader.uploadInt(Uniforms.EMISSIVE_TEXTURE, 4)
        shader.uploadBoolean(Uniforms.HAS_EMISSIVE_TEXTURE, hasEmissive)
        shader.uploadVec3f(Uniforms.EMISSIVE_FACTOR, material.emissiveFactor)

        // Alpha
        val alphaInt = when(material.alphaMode) {
            "OPAQUE" -> 0
            "MASK" -> 1
            "BLEND" -> 2
            else -> 0
        }
        shader.uploadInt(Uniforms.ALPHA_MODE, alphaInt)
        shader.uploadFloat(Uniforms.ALPHA_CUTOFF, material.alphaCutoff)

        val hasSkin = skeleton != null
        shader.uploadBoolean(Uniforms.HAS_SKIN, hasSkin)
        if (skeleton != null) {
            shader.uploadMat4fArray(Uniforms.JOINT_MATRICES, skeleton.getMatrixPalette())
        }

        if (alphaInt == 2) {
            glEnable(GL_BLEND)
            glDepthMask(false)
        } else {
            glDisable(GL_BLEND)
            glDepthMask(true)
        }

        if (material.doubleSided) glDisable(GL_CULL_FACE)
        else glEnable(GL_CULL_FACE)

        glDrawElements(model.drawMode, model.vertexCount, GL_UNSIGNED_INT, 0)

        if (alphaInt == 2) {
            glDisable(GL_BLEND)
            glDepthMask(true)
        }

        model.enabledAttributes.forEach { glDisableVertexAttribArray(it) }
        
        glBindVertexArray(0)
    }
}