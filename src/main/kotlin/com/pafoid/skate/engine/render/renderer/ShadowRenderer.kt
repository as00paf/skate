package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.assets.ResourceManager
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.toWorldMatrix
import com.pafoid.skate.engine.render.ShadowMap
import com.pafoid.skate.engine.utils.ShaderConst
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.lwjgl.opengl.GL30.GL_TRIANGLES
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDrawArrays

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
 * @param resourceManager Resource manager for loading shaders and textures
 */
class ShadowRenderer(
    private val resourceManager: ResourceManager
) {
    private var shadowShader: Shader? = null

    /**
     * Gets or creates the shadow shader.
     * Lazy loading to avoid loading shaders before OpenGL context is ready.
     */
    private fun getShadowShader(): Shader {
        if (shadowShader == null) {
            shadowShader = resourceManager.loadShaderSync(Assets.Shaders.SHADOW)
        }
        return shadowShader!!
    }

    /**
     * Renders all shadow-casting entities to the shadow map.
     *
     * @param gameObjects List of all game objects in the scene
     * @param lightSpaceMatrix The light's view-projection matrix
     * @param shadowMap The shadow map to render into
     */
    fun render(
        gameObjects: List<GameObject>,
        lightSpaceMatrix: org.joml.Matrix4f,
        shadowMap: ShadowMap
    ) {
        val shader = getShadowShader()
        shader.start()

        // Upload light space matrix for shadow mapping
        shader.uploadMat4f(Uniforms.LIGHT_SPACE_MATRIX, lightSpaceMatrix)

        // Render all objects that cast shadows
        gameObjects.forEach { go ->
            val renderComponent = go.getComponent<RenderComponent>()
            val transform = go.getComponent<Transform>()

            if (renderComponent != null && transform != null && renderComponent.castShadow) {
                val skeleton = go.getComponent<SkeletonComponent>()
                renderShadowCaster(renderComponent, transform, skeleton, shader)
            }
        }

        shader.stop()
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
        val model = renderComponent.model
        val worldMatrix = transform.toWorldMatrix()

        // Render each mesh part
        model.mesh.forEach { part ->
            val rawModel = part.rawModel
            val vaoId = rawModel.vaoId
            if (vaoId == 0) return@forEach

            glBindVertexArray(vaoId)

            if (skeleton != null) {
                // Skinned mesh: upload bone matrices and enable skinning
                val boneMatrices = skeleton.getMatrixPalette()
                shader.uploadMat4f(Uniforms.MODEL_MATRIX, worldMatrix)
                shader.uploadMat4fArray(ShaderConst.Uniforms.JOINT_MATRICES, boneMatrices)
                shader.uploadBoolean(ShaderConst.Uniforms.HAS_SKIN, true)

                // Render with skinning
                glDrawArrays(GL_TRIANGLES, 0, rawModel.vertexCount)
            } else {
                // Static mesh: no skinning
                shader.uploadMat4f(Uniforms.MODEL_MATRIX, worldMatrix)
                shader.uploadBoolean(ShaderConst.Uniforms.HAS_SKIN, false)

                // Render without skinning
                glDrawArrays(GL_TRIANGLES, 0, rawModel.vertexCount)
            }

            glBindVertexArray(0)
        }
    }

    /**
     * Destroys the shadow renderer and frees resources.
     */
    fun destroy() {
        shadowShader = null
    }
}
