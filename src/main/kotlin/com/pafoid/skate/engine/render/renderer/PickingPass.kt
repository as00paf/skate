package com.pafoid.skate.engine.render.renderer

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.render.PickingTexture
import com.pafoid.skate.engine.render.utils.GLStateTracker
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.lwjgl.opengl.GL30.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengl.GL30.GL_SCISSOR_TEST
import org.lwjgl.opengl.GL30.glClear
import org.lwjgl.opengl.GL30.glClearColor
import org.lwjgl.opengl.GL30.glDisable
import org.lwjgl.opengl.GL30.glViewport

/**
 * Render pass for object picking (selection/hover detection).
 * 
 * Renders all pickable objects to a special texture where pixel color
 * encodes the entity ID. This allows CPU-side identification of which
 * object the mouse is over.
 * 
 * @param pickingTexture The texture to render picking IDs to
 * @param pickingShader3D The shader used for 3D picking rendering
 * @param pickingRenderer The renderer for executing picking draw calls
 * @param renderer2D The 2D renderer for sprite picking
 * @param pickingShader The shader used for 2D picking rendering
 * @param modelRenderer The 3D model renderer for simple rendering
 */
class PickingPass(
    private val pickingTexture: PickingTexture,
    private val pickingShader3D: Shader,
    private val pickingRenderer: PickingRenderer,
    private val renderer2D: Renderer2D,
    private val pickingShader: Shader,
    private val modelRenderer: ModelRenderer,
    private val getWindowWidth: () -> Int,
    private val getWindowHeight: () -> Int
) : RenderPass {

    fun resize(width: Int, height: Int) {
        pickingTexture.resize(width, height)
    }

    override fun execute(scene: Scene, activeGameObject: GameObject?, hoveredGameObject: GameObject?) {
        // Skip picking pass if an object is already selected
        if (activeGameObject != null) return

        // Setup picking framebuffer
        val width = getWindowWidth()
        val height = getWindowHeight()
        pickingTexture.resize(width, height)
        pickingTexture.enableWriting()
        glViewport(0, 0, width, height)

        // CRITICAL: Reset state that might have been changed by ImGui or previous passes
        glDisable(GL_SCISSOR_TEST)
        GLStateTracker.resetToDefaults()

        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Render 2D and 3D objects for picking
        renderer2D.bindCamera(scene.camera)

        // Update camera viewport dimensions for correct aspect ratio
        scene.camera.viewportWidth = width
        scene.camera.viewportHeight = height

        render2D(scene, pickingShader)
        render3DPicking(scene)
        pickingRenderer.draw()

        pickingTexture.disableWriting()
    }

    private fun render3DPicking(scene: Scene) {
        val camera = scene.camera

        pickingShader3D.start()
        pickingShader3D.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.createProjectionMatrix())
        pickingShader3D.uploadMat4f(Uniforms.VIEW_MATRIX, camera.createViewMatrix())

        scene.gameObjectManager.gameObjects.forEach { go ->
            val renderComponent = go.getComponent<RenderComponent>()
            val transform = go.getComponent<Transform>()
            if (renderComponent != null && transform != null && go.getComponent<NonPickable>() == null) {
                val skeletonComponent = go.getComponent<SkeletonComponent>()

                pickingShader3D.uploadFloat(Uniforms.ENTITY_ID, go.getUid().toFloat() + 1)
                pickingShader3D.uploadBoolean(Uniforms.USE_BATCH, false)

                // Use ModelRenderer's simple render (no textures/PBR)
                modelRenderer.renderSimple(
                    go = go,
                    transform = transform,
                    renderComponent = renderComponent,
                    shader = pickingShader3D,
                    skeletonComponent = skeletonComponent
                )
            }
        }
        pickingShader3D.stop()
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
}
