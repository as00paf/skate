package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.CharacterModel
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.engine.render.EngineStats
import com.pafoid.skate.engine.render.data.RenderMode
import com.pafoid.skate.engine.render.utils.bindTexture
import com.pafoid.skate.engine.render.utils.bindVAO
import com.pafoid.skate.engine.render.utils.unbindVAO
import com.pafoid.skate.engine.render.utils.withBlendState
import com.pafoid.skate.engine.render.utils.withCullFace
import com.pafoid.skate.engine.render.utils.withDepthMask
import com.pafoid.skate.engine.utils.ShaderConst
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13

class ModelRenderer(
    private val resourceManager: ResourceManager
) : KoinComponent {
    private val debugRenderer: DebugRenderer by inject()

    private val boneColor = Vector3f(0f, 1f, 1f) // Cyan for bones

    /**
     * Binds a texture to the specified slot, using the fallback texture if null.
     * Also uploads the texture unit index to the shader.
     */
    private fun bindTexture(
        slot: Int,
        texture: Texture?,
        shader: Shader,
        uniformName: String
    ) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + slot)
        (texture ?: resourceManager.loadTextureSync(Assets.Textures.DEFAULT)).bind()
        shader.uploadInt(uniformName, slot)
    }

    /**
     * Renders a mesh part with full PBR material support.
     */
    private fun renderMeshPart(
        part: MeshPart,
        shader: Shader
    ) {
        val model = part.rawModel
        val material = part.material

        model.vaoId.bindVAO(model.enabledAttributes)

        // Bind all PBR texture maps and upload uniforms
        bindTexture(0, material.baseColorTexture, resourceManager)
        shader.uploadInt(ShaderConst.Uniforms.BASE_COLOR_TEXTURE, 0)
        shader.uploadVec4f(ShaderConst.Uniforms.BASE_COLOR_FACTOR, material.baseColorFactor)

        val hasNormal = material.normalMap != null
        bindTexture(1, material.normalMap, resourceManager)
        shader.uploadInt(ShaderConst.Uniforms.NORMAL_MAP, 1)
        shader.uploadBoolean(ShaderConst.Uniforms.HAS_NORMAL_MAP, hasNormal)

        val hasMR = material.metallicRoughnessTexture != null
        bindTexture(2, material.metallicRoughnessTexture, resourceManager)
        shader.uploadInt(ShaderConst.Uniforms.METALLIC_ROUGHNESS_TEXTURE, 2)
        shader.uploadBoolean(ShaderConst.Uniforms.HAS_METALLIC_ROUGHNESS_TEXTURE, hasMR)
        shader.uploadFloat(ShaderConst.Uniforms.METALLIC_FACTOR, material.metallicFactor)
        shader.uploadFloat(ShaderConst.Uniforms.ROUGHNESS_FACTOR, material.roughnessFactor)

        val hasAO = material.aoTexture != null
        bindTexture(3, material.aoTexture, resourceManager)
        shader.uploadInt(ShaderConst.Uniforms.AO_TEXTURE, 3)
        shader.uploadBoolean(ShaderConst.Uniforms.HAS_AO_TEXTURE, hasAO)

        val hasEmissive = material.emissiveTexture != null
        bindTexture(4, material.emissiveTexture, resourceManager)
        shader.uploadInt(ShaderConst.Uniforms.EMISSIVE_TEXTURE, 4)
        shader.uploadBoolean(ShaderConst.Uniforms.HAS_EMISSIVE_TEXTURE, hasEmissive)
        shader.uploadVec3f(ShaderConst.Uniforms.EMISSIVE_FACTOR, material.emissiveFactor)

        // Alpha mode
        val alphaInt = when (material.alphaMode) {
            AlphaMode.OPAQUE -> 0
            AlphaMode.MASK -> 1
            AlphaMode.BLEND -> 2
        }
        shader.uploadInt(ShaderConst.Uniforms.ALPHA_MODE, alphaInt)
        shader.uploadFloat(ShaderConst.Uniforms.ALPHA_CUTOFF, material.alphaCutoff)

        // Configure render state
        withBlendState(alphaInt == 2) {
            withDepthMask(alphaInt != 2) {
                withCullFace(!material.doubleSided) {
                    GL11.glDrawElements(model.drawMode, model.vertexCount, GL11.GL_UNSIGNED_INT, 0)
                    EngineStats.drawCalls.incrementAndGet()
                }
            }
        }

        model.vaoId.unbindVAO(model.enabledAttributes)
    }

    /**
     * Renders a mesh part with minimal state (no textures, no PBR).
     * Used for simple rendering scenarios like shadow passes or debug rendering.
     */
    private fun renderMeshPartSimple(
        part: MeshPart,
        shader: Shader
    ) {
        val model = part.rawModel

        model.vaoId.bindVAO(model.enabledAttributes)

        withCullFace(!part.material.doubleSided) {
            GL11.glDrawElements(model.drawMode, model.vertexCount, GL11.GL_UNSIGNED_INT, 0)
            EngineStats.drawCalls.incrementAndGet()
        }

        model.vaoId.unbindVAO(model.enabledAttributes)
    }

    /**
     * Renders a game object with full PBR shading and optional skeleton visualization.
     */
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

        // Upload global uniforms for this object
        defaultShader.uploadMat4f(ShaderConst.Uniforms.TRANSFORMATION_MATRIX, transformationMatrix)
        defaultShader.uploadFloat(ShaderConst.Uniforms.TEXTURE_SCALE, textureScale)
        defaultShader.uploadVec3f(ShaderConst.Uniforms.CAMERA_POSITION, cameraPosition)

        val hasSkin = skeletonComponent?.pose != null
        defaultShader.uploadBoolean(ShaderConst.Uniforms.HAS_SKIN, hasSkin)
        if (skeletonComponent != null) {
            defaultShader.uploadMat4fArray(ShaderConst.Uniforms.JOINT_MATRICES, skeletonComponent.getMatrixPalette())
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

    /**
     * Renders a game object with minimal shading (no textures, no PBR).
     * Used for shadow passes, debug rendering, or other simplified scenarios.
     */
    fun renderSimple(
        go: GameObject,
        transform: Transform,
        renderComponent: RenderComponent,
        shader: Shader,
        skeletonComponent: SkeletonComponent? = null
    ) {
        val transformationMatrix = transform.toWorldMatrix()
        shader.uploadMat4f(ShaderConst.Uniforms.TRANSFORMATION_MATRIX, transformationMatrix)

        val hasSkin = skeletonComponent?.pose != null
        shader.uploadBoolean(ShaderConst.Uniforms.HAS_SKIN, hasSkin)
        if (skeletonComponent?.pose != null) {
            shader.uploadMat4fArray(ShaderConst.Uniforms.JOINT_MATRICES, skeletonComponent.getMatrixPalette())
        }

        val skeleton = skeletonComponent?.pose?.skeleton

        // Render mesh if requested
        if (renderComponent.renderMode == RenderMode.MESH || renderComponent.renderMode == RenderMode.BOTH) {
            for (part in renderComponent.model.mesh) {
                renderMeshPartSimple(part, shader)
            }
        }

        // Render skeleton if requested
        if (renderComponent.renderMode == RenderMode.SKELETON || renderComponent.renderMode == RenderMode.BOTH) {
            if (renderComponent.model is CharacterModel && skeleton != null) {
                renderSkeleton(skeleton, transform)
            }
        }
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
            debugRenderer.addLine3D(boneWorldPos, childWorldPos, boneColor)

            // Recursively visualize the child's children
            visualizeBoneRecursive(child, skeleton, modelMatrix)
        }

        // Optionally draw a small box at the bone location for better visibility
        val boneRotation = Quaternionf()
        bone.worldTransform.getUnnormalizedRotation(boneRotation)
        debugRenderer.addBox3D(boneWorldPos, boneRotation, Vector3f(0.01f), boneColor)
    }
}
