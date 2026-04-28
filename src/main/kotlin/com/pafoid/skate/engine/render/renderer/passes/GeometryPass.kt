package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.LightingStateComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.graph.RenderContext
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.utils.ShaderConst.Attribs
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_TEXTURE0
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30.glActiveTexture
import org.lwjgl.opengl.GL30.glBindBuffer
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindTexture
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glUseProgram
import org.lwjgl.opengl.GL30.glViewport

/**
 * Main geometry rendering pass.
 *
 * Renders all 3D objects with full PBR shading, 2D sprites, and the sky dome.
 * This is the primary visual rendering pass that produces the final image.
 *
 * @param defaultShader The main 3D shader for PBR rendering
 * @param batchShader The 2D sprite batch shader
 * @param modelRenderer The 3D model renderer
 * @param renderer2D The 2D sprite renderer
 * @param skyDomeRenderer The sky dome renderer
 * @param frameBuffer The framebuffer for FBO rendering (provides width/height)
 * @param lightingUniformsLoader Helper for uploading lighting uniforms
 * @param sceneManager The scene manager for accessing current scene
 * @param getUseFbo Lambda to get current FBO usage setting at render time
 * @param shadowMapTextureId Default shadow map texture ID
 * @param shadowMapResolution Shadow map resolution for PCF texel size calculation
 */
class GeometryPass(
    private val defaultShader: Shader,
    private val batchShader: Shader,
    private val modelRenderer: ModelRenderer,
    private val renderer2D: Renderer2D,
    private val skyDomeRenderer: SkyDomeRenderer,
    private val frameBuffer: FrameBuffer,
    private val lightingUniformsLoader: LightingUniformsLoader,
    private val getUseFbo: () -> Boolean,
    private val sceneManager: SceneManager,
    private val shadowMapTextureId: Int = 0,
    private val shadowMapResolution: Float = 2048f
) : BaseRenderPass() {

    override val name: String = "GeometryPass"
    override val description: String = "Renders all 3D geometry and sprites to the framebuffer"
    override val inputs: Set<String> = setOf("ShadowMap")

    override fun execute(context: RenderContext) {
        val scene = context.scene
        val activeGameObject = context.activeGameObject
        val hoveredGameObject = context.hoveredGameObject
        
        // Use shadow map from context if available, otherwise fallback to default
        val contextShadowMap = context.getTexture("ShadowMap")
        val currentShadowMap = if (contextShadowMap != 0) contextShadowMap else shadowMapTextureId

        // Setup framebuffer
        val useFbo = getUseFbo()
        if (useFbo) {
            frameBuffer.bind()
            glViewport(0, 0, frameBuffer.width, frameBuffer.height)
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glViewport(0, 0, frameBuffer.width, frameBuffer.height)
        }

        // Clear with sky color from EnvironmentComponent
        val environmentComponent = scene.getComponent<EnvironmentComponent>()
        val renderSky = environmentComponent?.renderSky ?: true
        // Use sky color if renderSky is true, otherwise use fallback gray
        val skyColor = if (renderSky) {
            environmentComponent?.skyColor ?: Vector3f(0.6f, 0.7f, 0.9f)
        } else {
            Vector3f(0.2f, 0.2f, 0.2f) // Fallback dark gray when sky is disabled
        }
        clearColor(skyColor)

        val camera = scene.camera

        // 2D Rendering Setup
        renderer2D.bindCamera(camera)

        // 3D Rendering Setup - Upload projection and view matrices
        defaultShader.start()
        defaultShader.uploadMat4f(Attribs.PROJECTION_MATRIX, camera.createProjectionMatrix())
        defaultShader.uploadMat4f(Attribs.VIEW_MATRIX, camera.createViewMatrix())

        // Upload lighting uniforms
        val directionalLight = scene.getComponent<DirectionalLightComponent>()
        val environmentConfig = environmentComponent
        val lightingStateComponent = scene.getComponent<LightingStateComponent>()
        lightingUniformsLoader.loadLightingUniforms(
            defaultShader,
            camera,
            scene.sceneData,
            lightingStateComponent,
            directionalLight,
            environmentConfig,
            currentShadowMap
        )

        // Upload shadow map texel size for PCF
        if (currentShadowMap != 0) {
            defaultShader.uploadFloat(Uniforms.SHADOW_MAP_TEXEL_SIZE, 1.0f / shadowMapResolution)
            // Upload shadow bias uniforms
            if (directionalLight != null) {
                defaultShader.uploadFloat(Uniforms.SHADOW_DEPTH_BIAS, directionalLight.depthBias)
                defaultShader.uploadFloat(Uniforms.SHADOW_SLOPE_SCALED_BIAS, directionalLight.slopeScaledBias)
            } else {
                defaultShader.uploadFloat(Uniforms.SHADOW_DEPTH_BIAS, 0.001f)
                defaultShader.uploadFloat(Uniforms.SHADOW_SLOPE_SCALED_BIAS, 0.002f)
            }

            // Bind shadow map texture to texture unit
            glActiveTexture(GL_TEXTURE0 + Uniforms.SHADOW_TEXTURE_UNIT)
            glBindTexture(GL_TEXTURE_2D, currentShadowMap)
        }

        // Render all 3D game objects
        scene.gameObjects.forEach { go ->
            if (!go.isVisible) return@forEach
            val renderComponent = go.getComponent<RenderComponent>()
            val transformComponent = go.getComponent<Transform>()
            if (renderComponent != null && transformComponent != null) {
                // Hover & Selected states
                var selectionState = 0.0f
                if (go == activeGameObject) selectionState = 1.0f
                else if (go == hoveredGameObject) selectionState = 2.0f

                defaultShader.uploadFloat(Uniforms.SELECTED, selectionState)

                val skeletonComponent = go.getComponent<SkeletonComponent>()
                val cameraPosition = sceneManager.currentScene?.camera?.position ?: Vector3f(0f, 0f, 0f)

                modelRenderer.render(
                    go = go,
                    transform = transformComponent,
                    renderComponent = renderComponent,
                    defaultShader = defaultShader,
                    cameraPosition = cameraPosition,
                    skeletonComponent = skeletonComponent
                )
            }
        }

        defaultShader.stop()

        // Render 2D sprites
        render2D(scene, batchShader)

        // Render Sky Dome
        skyDomeRenderer.render(camera, scene)
    }

    private fun render2D(scene: Scene, shader: Shader) {
        renderer2D.bindShader(shader)
        renderer2D.bindCamera(scene.camera)

        scene.gameObjects.forEach { go ->
            if (!go.isVisible) return@forEach
            go.getComponent<SpriteRenderer>()?.let { sprite ->
                renderer2D.add(go)
            }
        }
        renderer2D.render()
        renderer2D.clear()
    }

    private fun clearColor(sky: Vector3f) {
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glClearColor(sky.x, sky.y, sky.z, 1.0f)
        GL30.glClear(GL30.GL_COLOR_BUFFER_BIT or GL30.GL_DEPTH_BUFFER_BIT)
    }

    override fun cleanup() {
        if (getUseFbo()) {
            frameBuffer.unbind()
        }
        // Final state cleanup
        glUseProgram(0)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }
}
