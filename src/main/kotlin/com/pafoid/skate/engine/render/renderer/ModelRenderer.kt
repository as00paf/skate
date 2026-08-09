package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.assets.data.models.Material
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.data.models.animations.Skeleton
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
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.utils.TextureSlots
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

class ModelRenderer(
    private val debugRenderer: DebugRenderer
) {

    private val boneColor = Vector3f(0f, 1f, 1f) // Cyan for bones

    /**
     * Renders a game object with full PBR shading and optional skeleton visualization.
     */
    fun render(
        transform: Transform,
        renderComponent: RenderComponent,
        shader: Shader,
        cameraPosition: Vector3f? = null,
        skeletonComponent: SkeletonComponent? = null,
        simple: Boolean = false
    ) {
        val model = renderComponent.model ?: return // No model to render
        val transformationMatrix = transform.toWorldMatrix()
        val textureScale = renderComponent.textureScale

        // Upload global uniforms for this object
        shader.uploadMat4f(Uniforms.TRANSFORMATION_MATRIX, transformationMatrix)
        shader.uploadFloat(Uniforms.TEXTURE_SCALE, textureScale)
        if (!simple && cameraPosition != null) shader.uploadVec3f(Uniforms.CAMERA_POSITION, cameraPosition)

        val hasSkin = skeletonComponent != null
        shader.uploadBoolean(Uniforms.HAS_SKIN, hasSkin)

        // Render mesh if requested
        if (renderComponent.renderMode == RenderMode.MESH || renderComponent.renderMode == RenderMode.BOTH) {
            for (part in model.mesh) {
                if (simple) renderMeshPartSimple(part, shader) else renderMeshPart(
                    part,
                    shader,
                    renderComponent.material
                )
            }
        }

        if (hasSkin) {
            // Render skeleton if requested
            if (renderComponent.renderMode == RenderMode.SKELETON || renderComponent.renderMode == RenderMode.BOTH) {
                val skeleton = skeletonComponent.pose.skeleton
                visualizeBoneRecursive(skeleton.rootBone, skeleton, transformationMatrix)
            }
            shader.uploadMat4fArray(Uniforms.JOINT_MATRICES, skeletonComponent.matrixPalette)
        }
    }

    /**
     * Renders a mesh part with full PBR material support.
     */
    private fun renderMeshPart(
        part: MeshPart,
        shader: Shader,
        overrideMaterial: Material? = null,
    ) {
        val material = overrideMaterial ?: part.material
        part.vaoId.bindVAO(part.enabledAttributes)

        // Bind all PBR texture maps and upload uniforms
        material.baseColorTexture?.texId?.let { bindTexture(TextureSlots.BASE_COLOR, it) }
        shader.uploadInt(Uniforms.BASE_COLOR_TEXTURE, TextureSlots.BASE_COLOR)
        shader.uploadVec4f(Uniforms.BASE_COLOR_FACTOR, material.baseColorFactor)

        val hasNormal = material.normalMap != null
        material.normalMap?.texId?.let { bindTexture(TextureSlots.NORMAL, it) }
        shader.uploadInt(Uniforms.NORMAL_MAP, TextureSlots.NORMAL)
        shader.uploadBoolean(Uniforms.HAS_NORMAL_MAP, hasNormal)

        val hasMR = material.metallicRoughnessTexture != null
        material.metallicRoughnessTexture?.texId?.let { bindTexture(TextureSlots.METALLIC_ROUGHNESS, it) }
        shader.uploadInt(Uniforms.METALLIC_ROUGHNESS_TEXTURE, TextureSlots.METALLIC_ROUGHNESS)
        shader.uploadBoolean(Uniforms.HAS_METALLIC_ROUGHNESS_TEXTURE, hasMR)
        shader.uploadFloat(Uniforms.METALLIC_FACTOR, material.metallicFactor)
        shader.uploadFloat(Uniforms.ROUGHNESS_FACTOR, material.roughnessFactor)

        val hasAO = material.aoTexture != null
        material.aoTexture?.texId?.let { bindTexture(TextureSlots.AO, it) }
        shader.uploadInt(Uniforms.AO_TEXTURE, TextureSlots.AO)
        shader.uploadBoolean(Uniforms.HAS_AO_TEXTURE, hasAO)

        val hasEmissive = material.emissiveTexture != null
        material.emissiveTexture?.texId?.let { bindTexture(TextureSlots.EMISSIVE, it) }
        shader.uploadInt(Uniforms.EMISSIVE_TEXTURE, TextureSlots.EMISSIVE)
        shader.uploadBoolean(Uniforms.HAS_EMISSIVE_TEXTURE, hasEmissive)
        shader.uploadVec3f(Uniforms.EMISSIVE_FACTOR, material.emissiveFactor)

        // Alpha mode
        val alphaInt = when (material.alphaMode) {
            AlphaMode.OPAQUE -> 0
            AlphaMode.MASK -> 1
            AlphaMode.BLEND -> 2
        }
        shader.uploadInt(Uniforms.ALPHA_MODE, alphaInt)
        shader.uploadFloat(Uniforms.ALPHA_CUTOFF, material.alphaCutoff)

        // Configure render state
        withBlendState(alphaInt == 2) {
            withDepthMask(alphaInt != 2) {
                withCullFace(!material.doubleSided) {
                    GL11.glDrawElements(GL11.GL_TRIANGLES, part.vertexCount, GL11.GL_UNSIGNED_INT, 0)
                    EngineStats.drawCalls.incrementAndGet()
                }
            }
        }

        part.vaoId.unbindVAO(part.enabledAttributes)
    }

    /**
     * Renders a mesh part with minimal state (no textures, no PBR).
     * Used for simple rendering scenarios like shadow passes or debug rendering.
     */
    private fun renderMeshPartSimple(
        part: MeshPart,
        shader: Shader
    ) {
        val material = part.material

        part.vaoId.bindVAO(part.enabledAttributes)

        material.baseColorTexture?.let {
            bindTexture(
                TextureSlots.BASE_COLOR,
                it.texId
            )
            shader.uploadInt(Uniforms.BASE_COLOR_TEXTURE, TextureSlots.BASE_COLOR)
        }
        shader.uploadBoolean("u_HasBaseColorTexture", material.baseColorTexture != null)

        // Alpha mode
        val alphaInt = when (material.alphaMode) {
            AlphaMode.OPAQUE -> 0
            AlphaMode.MASK -> 1
            AlphaMode.BLEND -> 2
        }
        shader.uploadInt(Uniforms.ALPHA_MODE, alphaInt)
        shader.uploadFloat(Uniforms.ALPHA_CUTOFF, material.alphaCutoff)

        withCullFace(!part.material.doubleSided) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, part.vertexCount, GL11.GL_UNSIGNED_INT, 0)
            EngineStats.drawCalls.incrementAndGet()
        }

        // Just unbind VAO without disabling attributes (disabling breaks skinning)
        GL30.glBindVertexArray(0)
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
