package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.render.graph.RenderResourceIds
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.render.lighting.EnvironmentProbe
import com.pafoid.skate.engine.ecs.SceneSun
import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
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
import com.pafoid.skate.engine.render.material.FramePrograms
import com.pafoid.skate.engine.render.renderer.LightingUniformsLoader
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f

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
 * @param materialShader The program of a material's custom shader reference, or null to use [defaultShader]
 *   (AST-26 §5.6: custom material shaders replace the colour pass only; shadows and picking keep the engine shaders)
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
    private val environmentProbe: EnvironmentProbe? = null,
    private val materialShader: (String) -> Shader? = { null },
) : BaseRenderPass() {

    override val name: String = "GeometryPass"
    override val inputs: Set<String> = setOf(RenderResourceIds.SHADOW_MAP, RenderResourceIds.LOCAL_SHADOW_MAP)
    override val outputs: Set<String> = setOf(RenderResourceIds.SCENE_COLOR, RenderResourceIds.SCENE_DEPTH)

    /** Without geometry the viewport is just the clear colour, so the pass cannot be switched off (EDT-54). */
    override val canDisable: Boolean = false
    override val description: String = "Renders all 3D geometry and sprites to the framebuffer"

    private val drawQueue = DrawQueue<DrawItem>()

    override fun execute(scene: Scene) {
        val renderables = mutableListOf<Renderable2D>()

        // Clear with sky color from EnvironmentComponent
        val environmentComponent = scene.getComponent<EnvironmentComponent>()?.takeIf { it.enabled }
        val renderSky = environmentComponent?.renderSky == true
        val skyColor = environmentComponent?.skyColor.takeIf { renderSky } ?: DEFAULT_SKY_COLOR
        clear.color!!.let { it[0] = skyColor.x; it[1] = skyColor.y; it[2] = skyColor.z }
        // Image-based lighting: re-bakes when the sky changed; it draws into its own target, so before our pass begins
        environmentProbe?.update(environmentComponent, scene.getComponent<DayNightCycleComponent>())
        val cmd = RhiContext.require().commandList()
        cmd.beginPass(if (useFbo) frameBuffer.target else null, clear = clear, viewport = presentedViewport.of(frameBuffer, useFbo))

        val camera = cameraManager.camera
        val cameraPosition = cameraManager.transform.worldMatrix.getTranslation(Vector3f())

        // Frame data shared by every program this pass uses; uploaded to each on its first draw (REN-31)
        frame.camera = camera
        frame.cameraPosition = cameraPosition
        frame.sun = SceneSun.of(scene)
        frame.environment = environmentComponent
        frame.shadows = shadowsThisFrame(shadowFrame) // consumed once per frame
        SceneLights.collect(scene, sceneLights)
        lightBuffer.upload(sceneLights)
        programs.beginFrame()

        // Collect 3D draws and 2D sprites, then draw 3D sorted: opaque by program → material → mesh, transparent last
        drawQueue.clear()
        RenderTraversal.forEachRendered(scene) { go ->
            val renderComponent = go.getComponent<RenderComponent>()?.takeIf { it.enabled }
            val transform = go.getComponent<Transform>()?.takeIf { it.enabled }
            if (transform != null) {
                if (renderComponent != null) {
                    val reference = renderComponent.material?.shader
                    val program = reference?.let(materialShader)
                    val programId = if (program == null) 0 else programs.idOf(reference)
                    drawQueue.add(sortKeyOf(renderComponent, programId), DrawItem(go, renderComponent, transform, program ?: defaultShader))
                }
                go.getComponent<SpriteRenderer>()
                    ?.let { if (it.enabled) renderables.add(Renderable2D(it, transform.worldMatrix)) }
            }
        }
        drawQueue.sort()

        var current: Shader? = null
        for (i in 0 until drawQueue.size) {
            val (go, renderComponent, transform, program) = drawQueue.itemAt(i)
            if (program !== current) {
                current?.stop()
                program.start()
                if (programs.firstUse(program)) uploadFrame(program)
                current = program
            }
            // Hover & Selected states, inherited from ancestors so a mesh-less root highlights its children
            program.uploadFloat(Uniforms.SELECTED, SelectionHighlight.stateOf(go, scene))

            val model = renderComponent.model
            val lightCount = if (model == null || sceneLights.isEmpty()) 0 else {
                val radius = ModelBounds.of(model).toWorld(transform.worldMatrix, boundsCenter)
                LightCulling.select(sceneLights, boundsCenter, radius, objectLights, lightScores)
            }
            lightBuffer.setObjectLights(program, objectLights, lightCount)

            modelRenderer.render(
                transform = transform,
                renderComponent = renderComponent,
                shader = program,
                cameraPosition = cameraPosition,
                skeletonComponent = go.getComponent<SkeletonComponent>()
            )
        }
        drawQueue.clear()
        current?.stop()
        frame.clear()

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

    fun destroy() {
        lightBuffer.destroy()
        environmentProbe?.destroy()
    }

    private data class DrawItem(val go: GameObject, val renderComponent: RenderComponent, val transform: Transform, val program: Shader)

    /** What every program needs once per frame: camera, sun, lights, shadows, IBL. */
    private class FrameData {
        var camera: CameraComponent? = null
        var cameraPosition: Vector3f = Vector3f()
        var sun: DirectionalLightComponent? = null
        var environment: EnvironmentComponent? = null
        var shadows: ShadowFrame? = null

        fun clear() {
            camera = null; sun = null; environment = null; shadows = null
        }
    }

    private val frame = FrameData()
    private val programs = FramePrograms<Shader>()

    /** Uploads the frame's camera, lighting, shadow and IBL uniforms to [program] (bound). */
    private fun uploadFrame(program: Shader) {
        val camera = frame.camera ?: return
        program.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        program.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        lightingUniformsLoader.loadCameraPosition(program, frame.cameraPosition)
        lightBuffer.bind(program)
        lightingUniformsLoader.loadLightingUniforms(program, frame.sun, frame.environment, frame.shadows)
        environmentProbe?.bind(program) ?: program.uploadInt(Uniforms.ENV_ENABLED, 0)
    }

    private fun sortKeyOf(renderComponent: RenderComponent, programId: Int): Long {
        val parts = renderComponent.model?.mesh
        val first = parts?.firstOrNull() ?: return SortKey.build(false, programId, 0, 0)
        val material = renderComponent.material ?: first.material
        if (material.alphaMode == AlphaMode.BLEND) return SortKey.build(true, 0, 0, 0)
        return SortKey.build(false, programId, material.id, first.vaoId)
    }

    private fun render2D(camera: CameraComponent, renderables: List<Renderable2D>, shader: Shader) {
        renderer2D.bindShader(shader)
        renderer2D.bindCamera(camera)
        renderer2D.addAll(renderables)
        renderer2D.render()
        renderer2D.clear()
    }

    companion object {
        val DEFAULT_SKY_COLOR = Vector3f(0.2f, 0.2f, 0.2f)

        /**
         * The shadow maps to use this frame: only what the shadow pass published this frame. With the shadow pass off
         * (or `NoShadowPass`) nothing is published, so this is null and geometry renders unshadowed: no stale maps,
         * nothing to clear (EDT-54).
         */
        internal fun shadowsThisFrame(frame: ShadowFrame?): ShadowFrame? = frame?.takeIf { it.consume() }
    }
}
