package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.AssetsManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.utils.bindTexture
import com.pafoid.skate.engine.render.utils.bindVAO
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import com.pafoid.skate.engine.utils.TextureSlots
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30.GL_DEPTH_TEST
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDepthMask
import org.lwjgl.opengl.GL30.glEnable

/**
 * Renderer for shadow pass.
 *
 * Renders all objects with castShadow enabled into the shadow map depth texture.
 * Uses a depth-only shader that only outputs depth values (no color).
 *
 * ## Features
 *
 * - Renders all entities with RenderComponent.castShadow == true
 * - Supports skinned meshes with GPU bone transforms
 * - Depth-only rendering (no color buffer writes)
 *
 * @param shadowShader The shadow shader to use
 * @param assetsManager Resource manager for loading textures
 */
class ShadowRenderer(
    private val shadowShader: Shader,
    private val assetsManager: AssetsManager
) {

    /**
     * Renders all shadow-casting entities to the shadow map.
     *
     * @param gameObjects List of all game objects in the scene
     * @param lightSpaceMatrix The light's view-projection matrix
     */
    fun render(
        gameObjects: List<GameObject>,
        lightSpaceMatrix: org.joml.Matrix4f
    ) {
        shadowShader.start()

        // Enable depth testing and writing for shadow map
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)

        // Upload light space matrix for shadow mapping
        shadowShader.uploadMat4f(Uniforms.LIGHT_SPACE_MATRIX, lightSpaceMatrix)

        // Render all objects that cast shadows
        gameObjects.forEach { go ->
            val renderComponent = go.getComponent<RenderComponent>()
            val transform = go.getComponent<Transform>()

            if (renderComponent != null && transform != null && renderComponent.castShadow) {
                val skeleton = go.getComponent<SkeletonComponent>()
                renderShadowCaster(renderComponent, transform, skeleton, shadowShader)
            }
        }

        shadowShader.stop()
    }

    /**
     * Renders a single shadow-casting object.
     *
     * @param renderComponent The render component with mesh and material data
     * @param transform The transform component for world positioning
     * @param skeleton Optional skeleton component for skinned meshes
     * @param shader The shadow shader to use
     */
    private fun renderShadowCaster(
        renderComponent: RenderComponent,
        transform: Transform,
        skeleton: SkeletonComponent?,
        shader: Shader
    ) {
        val model = renderComponent.model ?: return
        val worldMatrix = transform.toWorldMatrix()

        // Render each mesh part
        model.mesh.forEach { part ->
            val rawModel = part.rawModel
            val material = part.material
            val vaoId = rawModel?.vaoId ?: return@forEach
            if (vaoId == 0) return@forEach

            // Bind VAO with proper attribute enabling (critical for skinned meshes)
            vaoId.bindVAO(rawModel.enabledAttributes)

            // Upload alpha masking uniforms
            val alphaModeInt = when (material.alphaMode) {
                AlphaMode.OPAQUE -> 0
                AlphaMode.MASK -> 1
                AlphaMode.BLEND -> 2
            }
            shader.uploadInt(Uniforms.ALPHA_MODE, alphaModeInt)
            shader.uploadFloat(Uniforms.ALPHA_CUTOFF, material.alphaCutoff)
            shader.uploadBoolean(Uniforms.HAS_BASE_COLOR_TEXTURE, material.baseColorTexture != null)

            // Bind base color texture for alpha masking (if available)
            if (material.alphaMode == AlphaMode.MASK && material.baseColorTexture != null) {
                bindTexture(TextureSlots.BASE_COLOR, material.baseColorTexture, assetsManager)
                shader.uploadInt(Uniforms.BASE_COLOR_TEXTURE, TextureSlots.BASE_COLOR)
            }

            if (skeleton != null) {
                // Skinned mesh: upload bone matrices and enable skinning
                val boneMatrices = skeleton.getMatrixPalette()
                shader.uploadMat4f(Uniforms.MODEL_MATRIX, worldMatrix)
                shader.uploadMat4fArray(Uniforms.JOINT_MATRICES, boneMatrices)
                shader.uploadBoolean(Uniforms.HAS_SKIN, true)

                // Render with skinning
                GL11.glDrawElements(rawModel.drawMode, rawModel.vertexCount, GL11.GL_UNSIGNED_INT, 0)
            } else {
                // Static mesh: no skinning
                shader.uploadMat4f(Uniforms.MODEL_MATRIX, worldMatrix)
                shader.uploadBoolean(Uniforms.HAS_SKIN, false)

                // Render without skinning
                GL11.glDrawElements(rawModel.drawMode, rawModel.vertexCount, GL11.GL_UNSIGNED_INT, 0)
            }

            // Just unbind VAO without disabling attributes (preserves attribute state)
            glBindVertexArray(0)
        }
    }

    /**
     * Destroys the shadow renderer and frees resources.
     */
    fun destroy() {
        shadowShader.destroy()
    }
}
