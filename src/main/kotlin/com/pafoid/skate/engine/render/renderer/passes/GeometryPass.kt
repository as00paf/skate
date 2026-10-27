package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.PointLightComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpotLightComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.getAllChildren
import com.pafoid.skate.engine.render.state.DrawQueue
import com.pafoid.skate.engine.render.state.SortKey
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.getAllComponents
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.data.Renderable2D
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.utils.withBlendState
import com.pafoid.skate.engine.render.utils.GLStateTracker
import com.pafoid.skate.engine.render.utils.withCullFace
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import com.pafoid.skate.engine.render.utils.withDepthMask
import com.pafoid.skate.engine.render.utils.withDepthTest
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
 * @param getUseFbo Lambda to get current FBO usage setting at render time
 * @param shadowMapTextureId Default shadow map texture ID
 * @param shadowMapResolution Shadow map resolution for PCF texel size calculation
 */
class GeometryPass(
    private val defaultShader: Shader,
    private val batchShader: Shader,
    private val modelRenderer: ModelRenderer,
    val renderer2D: Renderer2D,
    private val skyDomeRenderer: SkyDomeRenderer,
    private val frameBuffer: FrameBuffer,
    private val lightingUniformsLoader: LightingUniformsLoader,
    private val useFbo: Boolean,
    private val cameraManager: CameraManager,
    private val shadowMapTextureId: Int = 0,
    private val shadowMapResolution: Float = 2048f
) : BaseRenderPass() {

    override val name: String = "GeometryPass"
    override val description: String = "Renders all 3D geometry and sprites to the framebuffer"
    override val inputs: Set<String> = setOf("ShadowMap")

    private val drawQueue = DrawQueue<DrawItem>()

    override fun execute(scene: Scene) {
        val renderables = mutableListOf<Renderable2D>()

        // Setup framebuffer
        if (useFbo) frameBuffer.bind() else glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glViewport(0, 0, frameBuffer.width, frameBuffer.height)

        // Clear with sky color from EnvironmentComponent
        val environmentComponent = scene.getComponent<EnvironmentComponent>()?.takeIf { it.enabled }
        val renderSky = environmentComponent?.renderSky == true
        val skyColor = environmentComponent?.skyColor.takeIf { renderSky }
        clearColor(skyColor ?: DEFAULT_SKY_COLOR)

        val camera = cameraManager.camera
        val cameraPosition = cameraManager.transform.worldMatrix.getTranslation(Vector3f())

        // 3D Rendering Setup - Upload projection and view matrices
        defaultShader.start()
        defaultShader.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        defaultShader.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        lightingUniformsLoader.loadCameraPosition(defaultShader, cameraPosition)

        // Upload lighting uniforms
        val ambientLightComponent = scene.getComponent<AmbientLightComponent>()?.takeIf { it.enabled }
        val directionalLight = scene.getComponent<DirectionalLightComponent>()?.takeIf { it.enabled }
        val pointLights = scene.getAllComponents<PointLightComponent>()
            .filter { it.enabled && it.gameObject?.isVisible == true }.mapNotNull { it.gameObject }
        val spotLights = scene.getAllComponents<SpotLightComponent>()
            .filter { it.enabled && it.gameObject?.isVisible == true }
            .mapNotNull { it.gameObject }
        lightingUniformsLoader.loadLightingUniforms(
            defaultShader,
            ambientLightComponent,
            directionalLight,
            pointLights,
            spotLights,
            environmentComponent,
            shadowMapTextureId,
            shadowMapResolution
        )

        // Collect 3D draws and 2D sprites, then draw 3D sorted: opaque by material → mesh, transparent last in scene order
        drawQueue.clear()
        scene.getAllChildren().forEach { go ->
            if (!go.isVisible) return@forEach
            val renderComponent = go.getComponent<RenderComponent>()?.takeIf { it.enabled }
            val transform = go.getComponent<Transform>()?.takeIf { it.enabled }
            if (transform != null) {
                if (renderComponent != null) {
                    drawQueue.add(sortKeyOf(renderComponent), DrawItem(go, renderComponent, transform))
                }
                go.getComponent<SpriteRenderer>()
                    ?.let { if (it.enabled) renderables.add(Renderable2D(it, transform.worldMatrix)) }
            }
        }
        drawQueue.sort()

        for (i in 0 until drawQueue.size) {
            val (go, renderComponent, transform) = drawQueue.itemAt(i)
            // Hover & Selected states
            var selectionState = 0.0f
            if (scene.isSelected(go)) selectionState = 1.0f
            else if (go.uId == scene.hoveredGameObject?.uId) selectionState = 2.0f

            defaultShader.uploadFloat(Uniforms.SELECTED, selectionState)

            modelRenderer.render(
                transform = transform,
                renderComponent = renderComponent,
                shader = defaultShader,
                cameraPosition = cameraPosition,
                skeletonComponent = go.getComponent<SkeletonComponent>()
            )
        }
        drawQueue.clear()

        defaultShader.stop()

        // Render 2D sprites
        render2D(camera, renderables, batchShader)

        // Render Sky Dome
        skyDomeRenderer.render(camera, cameraPosition, scene)

        renderUi()
    }

    /** Runtime UI overlay: recorded by the UI systems earlier in the frame, drawn last without depth. */
    private fun renderUi() {
        if (renderer2D.ui.quadCount == 0) {
            renderer2D.renderUi(frameBuffer.width, frameBuffer.height)
            return
        }
        withDepthTest(false) {
            withBlendState(true) {
                withCullFace(false) {
                    GLStateTracker.setBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                    renderer2D.bindShader(batchShader)
                    renderer2D.renderUi(frameBuffer.width, frameBuffer.height)
                }
            }
        }
    }

    private data class DrawItem(val go: GameObject, val renderComponent: RenderComponent, val transform: Transform)

    private fun sortKeyOf(renderComponent: RenderComponent): Long {
        val parts = renderComponent.model?.mesh
        val first = parts?.firstOrNull() ?: return SortKey.build(false, 0, 0, 0)
        val material = renderComponent.material ?: first.material
        if (material.alphaMode == AlphaMode.BLEND) return SortKey.build(true, 0, 0, 0)
        return SortKey.build(false, 0, material.id, first.vaoId)
    }

    private fun render2D(camera: CameraComponent, renderables: List<Renderable2D>, shader: Shader) {
        withDepthTest(true) {
            withBlendState(false) {
                withDepthMask(true) {
                    withCullFace(false) {
                        renderer2D.bindShader(shader)
                        renderer2D.bindCamera(camera)
                        renderer2D.addAll(renderables)
                        renderer2D.render()
                        renderer2D.clear()
                    }
                }
            }
        }
    }

    private fun clearColor(sky: Vector3f) {
        GL30.glEnable(GL30.GL_DEPTH_TEST)
        GL30.glClearColor(sky.x, sky.y, sky.z, 1.0f)
        GL30.glClear(GL30.GL_COLOR_BUFFER_BIT or GL30.GL_DEPTH_BUFFER_BIT)
    }

    override fun cleanup() {
        if (useFbo) {
            frameBuffer.unbind()
        }
        // Final state cleanup
        glUseProgram(0)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    companion object {
        val DEFAULT_SKY_COLOR = Vector3f(0.2f, 0.2f, 0.2f)
    }
}
