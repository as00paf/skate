package com.pafoid.skate.engine.render

import com.pafoid.skate.engine.animation.Bone
import com.pafoid.skate.engine.animation.Skeleton
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.Shader
import com.pafoid.skate.engine.assets.ShaderConst.Uniforms
import com.pafoid.skate.engine.models.AlphaMode
import com.pafoid.skate.engine.models.CharacterModel
import com.pafoid.skate.engine.models.MeshPart
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.components.RenderComponent
import com.pafoid.skate.engine.scenes.components.RenderMode
import com.pafoid.skate.engine.scenes.components.SkeletonComponent
import com.pafoid.skate.engine.scenes.components.Transform
import com.pafoid.skate.engine.scenes.components.toWorldMatrix
import com.pafoid.skate.engine.utils.EngineStats
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_UNSIGNED_INT
import org.lwjgl.opengl.GL11.glDepthMask
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glDrawElements
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.GL_TEXTURE1
import org.lwjgl.opengl.GL13.GL_TEXTURE2
import org.lwjgl.opengl.GL13.GL_TEXTURE3
import org.lwjgl.opengl.GL13.GL_TEXTURE4
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL20.glDisableVertexAttribArray
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.glBindVertexArray

class ModelRenderer(
    private val resourceManager: ResourceManager
) : KoinComponent {
    private val debugDraw: DebugDraw by inject()

    private val boneColor = Vector3f(0f, 1f, 1f) // Cyan for bones

    fun render(
        go: GameObject,
        transform: Transform,
        renderComponent: RenderComponent,
        defaultShader: Shader,
        cameraPosition: Vector3f,
        skeletonComponent: SkeletonComponent? = null
    ) {
        val transformationMatrix = transform.toWorldMatrix()
        val textureScale = renderComponent.textureScale

        // Hoist global uniforms for this object
        defaultShader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, transformationMatrix)
        defaultShader.uploadFloat(Uniforms.TEXTURE_SCALE, textureScale)
        defaultShader.uploadVec3f(Uniforms.CAMERA_POSITION, cameraPosition)

        val hasSkin = skeletonComponent?.pose != null
        defaultShader.uploadBoolean(Uniforms.HAS_SKIN, hasSkin)
        if (skeletonComponent != null && skeletonComponent.pose != null) {
            defaultShader.uploadMat4fArray(Uniforms.JOINT_MATRICES, skeletonComponent.getMatrixPalette())
        }

        val skeleton = skeletonComponent?.pose?.skeleton

        // Render mesh if requested
        if (renderComponent.renderMode == RenderMode.MESH || renderComponent.renderMode == RenderMode.BOTH) {
            for (part in renderComponent.model.mesh) {
                renderMeshPart(part, defaultShader)
            }
        }

        // Render skeleton if requested
        if (renderComponent.renderMode == RenderMode.SKELETON || renderComponent.renderMode == RenderMode.BOTH) {
            if (renderComponent.model is CharacterModel && skeleton != null) {
                renderSkeleton(skeleton, transform)
            }
        }
    }

    private fun renderMeshPart(
        part: MeshPart,
        shader: Shader
    ) {
        val model = part.rawModel
        val material = part.material

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
            AlphaMode.OPAQUE -> 0
            AlphaMode.MASK -> 1
            AlphaMode.BLEND -> 2
        }
        shader.uploadInt(Uniforms.ALPHA_MODE, alphaInt)
        shader.uploadFloat(Uniforms.ALPHA_CUTOFF, material.alphaCutoff)

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
        EngineStats.drawCalls.incrementAndGet()

        if (alphaInt == 2) {
            glDisable(GL_BLEND)
            glDepthMask(true)
        }

        model.enabledAttributes.forEach { glDisableVertexAttribArray(it) }

        glBindVertexArray(0)
    }

    private fun renderSkeleton(
        skeleton: Skeleton,
        transform: Transform
    ) {
        val transformationMatrix = transform.toWorldMatrix()

        // Use the same approach as in AnimationSystem for consistent visualization
        visualizeBoneRecursive(skeleton.rootBone, skeleton, transformationMatrix)
    }

    private fun visualizeBoneRecursive(bone: Bone, skeleton: Skeleton, modelMatrix: Matrix4f) {
        // Get the bone's world position from the skeleton's world transforms
        val boneWorldPos = Vector3f()
        bone.worldTransform.getTranslation(boneWorldPos)
        modelMatrix.transformPosition(boneWorldPos)

        for (child in bone.children) {
            // Get the child's world position
            val childWorldPos = Vector3f()
            child.worldTransform.getTranslation(childWorldPos)
            modelMatrix.transformPosition(childWorldPos)

            // Draw line from bone to child
            debugDraw.addLine3D(boneWorldPos, childWorldPos, boneColor)

            // Recursively visualize the child's children
            visualizeBoneRecursive(child, skeleton, modelMatrix)
        }

        // Optionally draw a small box at the bone location for better visibility
        val boneRotation = Quaternionf()
        bone.worldTransform.getUnnormalizedRotation(boneRotation)
        debugDraw.addBox3D(boneWorldPos, boneRotation, Vector3f(0.01f), boneColor)
    }

    fun renderSimple(
        go: GameObject,
        transform: Transform,
        renderComponent: RenderComponent,
        shader: Shader,
        skeletonComponent: SkeletonComponent? = null
    ) {
        val transformationMatrix = transform.toWorldMatrix()
        shader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, transformationMatrix)

        val hasSkin = skeletonComponent?.pose != null
        shader.uploadBoolean(Uniforms.HAS_SKIN, hasSkin)
        if (skeletonComponent?.pose != null) {
            shader.uploadMat4fArray(Uniforms.JOINT_MATRICES, skeletonComponent.getMatrixPalette())
        }

        val skeleton = skeletonComponent?.pose?.skeleton

        // Render mesh if requested
        if (renderComponent.renderMode == RenderMode.MESH || renderComponent.renderMode == RenderMode.BOTH) {
            for (part in renderComponent.model.mesh) {
                val model = part.rawModel
                glBindVertexArray(model.vaoId)
                model.enabledAttributes.forEach { glEnableVertexAttribArray(it) }

                if (part.material.doubleSided) glDisable(GL_CULL_FACE)
                else glEnable(GL_CULL_FACE)

                glDrawElements(model.drawMode, model.vertexCount, GL_UNSIGNED_INT, 0)
                EngineStats.drawCalls.incrementAndGet()

                model.enabledAttributes.forEach { glDisableVertexAttribArray(it) }
                glBindVertexArray(0)
            }
        }

        // Render skeleton if requested
        if (renderComponent.renderMode == RenderMode.SKELETON || renderComponent.renderMode == RenderMode.BOTH) {
            if (renderComponent.model is CharacterModel && skeleton != null) {
                renderSkeleton(skeleton, transform)
            }
        }
    }
}