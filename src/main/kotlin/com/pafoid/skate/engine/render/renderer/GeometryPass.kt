package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.FrameBuffer
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
 * @param frameBuffer The framebuffer for FBO rendering (optional)
 * @param lightingUniformsLoader Helper for uploading lighting uniforms
 * @param sceneManager The scene manager for accessing current scene
 * @param getUseFbo Lambda to get current FBO usage setting at render time
 * @param getWindowWidth Lambda to get current window width at render time
 * @param getWindowHeight Lambda to get current window height at render time
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
    private val getWindowWidth: () -> Int,
    private val getWindowHeight: () -> Int
) : RenderPass {

    override fun execute(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        // Setup framebuffer
        val width = getWindowWidth()
        val height = getWindowHeight()
        val useFbo = getUseFbo()
        if (useFbo) {
            frameBuffer.bind()
            glViewport(0, 0, frameBuffer.width, frameBuffer.height)
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            glViewport(0, 0, width, height)
        }

        // Clear with sky color
        clearColor(scene.sceneData.skyColor)

        val camera = scene.camera

        // 2D Rendering Setup
        renderer2D.bindCamera(camera)

        // 3D Rendering Setup - Upload projection and view matrices
        defaultShader.start()
        defaultShader.uploadMat4f(Attribs.PROJECTION_MATRIX, camera.createProjectionMatrix())
        defaultShader.uploadMat4f(Attribs.VIEW_MATRIX, camera.createViewMatrix())

        // Upload lighting uniforms
        lightingUniformsLoader.loadLightingUniforms(defaultShader, camera, scene.sceneData)

        // Render all 3D game objects
        scene.gameObjectManager.gameObjects.forEach { go ->
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

        scene.gameObjectManager.gameObjects.forEach { go ->
            go.getComponent<com.pafoid.skate.engine.ecs.components.SpriteRenderer>()?.let { sprite ->
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

    fun unbind() {
        if (getUseFbo()) {
            frameBuffer.unbind()
        }
    }

    fun cleanup() {
        // Final state cleanup
        glUseProgram(0)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }
}
