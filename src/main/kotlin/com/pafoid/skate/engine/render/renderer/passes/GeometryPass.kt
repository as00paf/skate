package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.AmbientLightComponent
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.state.DrawQueue
import com.pafoid.skate.engine.render.state.SortKey
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.data.Renderable2D
import com.pafoid.skate.engine.render.lighting.LightBuffer
import com.pafoid.skate.engine.render.lighting.LightCulling
import com.pafoid.skate.engine.render.lighting.LightData
import com.pafoid.skate.engine.render.lighting.ModelBounds
import com.pafoid.skate.engine.render.lighting.SceneLights
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import com.pafoid.skate.engine.render.utils.withBlendState
import com.pafoid.skate.engine.render.rhi.opengl.GLStateTracker
import com.pafoid.skate.engine.render.rhi.opengl.GlPrograms
import com.pafoid.skate.engine.render.utils.withCullFace
import com.pafoid.skate.engine.render.utils.withDepthMask
import com.pafoid.skate.engine.render.utils.withDepthTest
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f
import org.lwjgl.opengl.GL30.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL30.GL_TEXTURE0
import org.lwjgl.opengl.GL30.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30.glActiveTexture
import org.lwjgl.opengl.GL30.glBindBuffer
import org.lwjgl.opengl.GL30.glBindTexture
import org.lwjgl.opengl.GL30.glBindVertexArray

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
 * @param shadowFrame Shadow maps published by the shadow pass this frame (null: no shadows)
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
    private val shadowFrame: ShadowFrame? = null,
) : BaseRenderPass() {

    override val name: String = "GeometryPass"
    override val description: String = "Renders all 3D geometry and sprites to the framebuffer"
    override val inputs: Set<String> = setOf("ShadowMap")

    private val drawQueue = DrawQueue<DrawItem>()

    override fun execute(scene: Scene) {
        val renderables = mutableListOf<Renderable2D>()

        // Clear with sky color from EnvironmentComponent
        val environmentComponent = scene.getComponent<EnvironmentComponent>()?.takeIf { it.enabled }
        val renderSky = environmentComponent?.renderSky == true
        val skyColor = environmentComponent?.skyColor.takeIf { renderSky } ?: DEFAULT_SKY_COLOR
        clear.color!!.let { it[0] = skyColor.x; it[1] = skyColor.y; it[2] = skyColor.z }
        GLStateTracker.setDepthTestEnabled(true)
        GLStateTracker.setDepthMask(true)
        val cmd = RhiContext.require().commandList()
        cmd.beginPass(if (useFbo) frameBuffer.target else null, clear = clear, viewport = presentedViewport.of(frameBuffer, useFbo))

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
        SceneLights.collect(scene, sceneLights)
        lightBuffer.upload(sceneLights)
        lightBuffer.bind(defaultShader)
        lightingUniformsLoader.loadLightingUniforms(
            defaultShader,
            ambientLightComponent,
            directionalLight,
            environmentComponent,
            shadowFrame?.takeIf { it.consume() },
        )

        // Collect 3D draws and 2D sprites, then draw 3D sorted: opaque by material → mesh, transparent last in scene order
        drawQueue.clear()
        RenderTraversal.forEachRendered(scene) { go ->
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
            // Hover & Selected states, inherited from ancestors so a mesh-less root highlights its children
            defaultShader.uploadFloat(Uniforms.SELECTED, SelectionHighlight.stateOf(go, scene))

            val model = renderComponent.model
            val lightCount = if (model == null || sceneLights.isEmpty()) 0 else {
                val radius = ModelBounds.of(model).toWorld(transform.worldMatrix, boundsCenter)
                LightCulling.select(sceneLights, boundsCenter, radius, objectLights, lightScores)
            }
            lightBuffer.setObjectLights(defaultShader, objectLights, lightCount)

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
        cmd.endPass()
    }

    /** Clear values of the scene target; the color is the frame's sky color. */
    private val clear = ClearValues(floatArrayOf(0f, 0f, 0f, 1f), 1f)
    private val presentedViewport = PresentedViewport()

    private val lightBuffer = LightBuffer()
    private val sceneLights = ArrayList<LightData>()
    private val objectLights = IntArray(LightCulling.MAX_LIGHTS_PER_OBJECT)
    private val lightScores = FloatArray(LightCulling.MAX_LIGHTS_PER_OBJECT)
    private val boundsCenter = Vector3f()

    fun destroy() = lightBuffer.destroy()

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

    override fun cleanup() {
        // Final state cleanup
        GlPrograms.use(0)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    companion object {
        val DEFAULT_SKY_COLOR = Vector3f(0.2f, 0.2f, 0.2f)
    }
}
