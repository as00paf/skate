package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.AlphaMode
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.CameraComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.SkeletonComponent
import com.pafoid.skate.engine.ecs.components.SpriteRenderer
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.data.Renderable2D
import com.pafoid.skate.engine.render.graph.RenderResourceIds
import com.pafoid.skate.engine.render.renderer.ModelRenderer
import com.pafoid.skate.engine.render.renderer.RenderTraversal
import com.pafoid.skate.engine.render.renderer.Renderer2D
import com.pafoid.skate.engine.render.renderer.SkyDomeRenderer
import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import com.pafoid.skate.engine.render.state.DrawQueue
import com.pafoid.skate.engine.render.state.SortKey
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f

/**
 * Main geometry pass: clears the scene target, draws opaque and alpha-masked meshes with full PBR shading, 2D sprites
 * and the sky dome. Blended meshes are handed to [TransparentPass] through [sceneFrame], which also carries the
 * frame's lights, shadows and IBL for the later lit passes (REN-29 task 1).
 *
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
    private val sceneFrame: SceneFrame,
    private val useFbo: Boolean,
    private val cameraManager: CameraManager,
    private val materialShader: (String) -> Shader? = { null },
) : BaseRenderPass() {

    override val name: String = "GeometryPass"
    override val inputs: Set<String> = setOf(RenderResourceIds.SHADOW_MAP, RenderResourceIds.LOCAL_SHADOW_MAP)
    override val outputs: Set<String> = setOf(RenderResourceIds.SCENE_COLOR, RenderResourceIds.SCENE_DEPTH)

    /** Without geometry the viewport is just the clear colour, so the pass cannot be switched off (EDT-54). */
    override val canDisable: Boolean = false
    override val description: String = "Renders opaque 3D geometry, sprites and the sky to the framebuffer"

    private val drawQueue = DrawQueue<SceneFrame.DrawItem>()
    private val renderables = ArrayList<Renderable2D>()
    private val cameraPosition = Vector3f()

    override fun execute(scene: Scene) {
        renderables.clear()

        // Clear with sky color from EnvironmentComponent
        val environmentComponent = scene.getComponent<EnvironmentComponent>()?.takeIf { it.enabled }
        val renderSky = environmentComponent?.renderSky == true
        val skyColor = environmentComponent?.skyColor.takeIf { renderSky } ?: DEFAULT_SKY_COLOR
        clear.color!!.let { it[0] = skyColor.x; it[1] = skyColor.y; it[2] = skyColor.z }

        val camera = cameraManager.camera
        cameraManager.transform.worldMatrix.getTranslation(cameraPosition)
        // Probe re-bakes draw into their own target, so the frame begins before our pass does
        sceneFrame.begin(scene, camera, cameraPosition, environmentComponent)

        val cmd = RhiContext.require().commandList()
        cmd.beginPass(if (useFbo) frameBuffer.target else null, clear = clear, viewport = presentedViewport.of(frameBuffer, useFbo))

        // Opaque draws sorted by program → material → mesh; blended draws keep scene order for the transparent pass
        drawQueue.clear()
        RenderTraversal.forEachRendered(scene) { go ->
            val renderComponent = go.getComponent<RenderComponent>()?.takeIf { it.enabled }
            val transform = go.getComponent<Transform>()?.takeIf { it.enabled }
            if (transform != null) {
                if (renderComponent != null) {
                    val reference = renderComponent.material?.shader
                    val program = reference?.let(materialShader)
                    val item = SceneFrame.DrawItem(go, renderComponent, transform, program ?: defaultShader)
                    if (isBlended(renderComponent)) {
                        sceneFrame.transparent.add(0L, item)
                    } else {
                        val programId = if (program == null) 0 else sceneFrame.programId(reference)
                        drawQueue.add(sortKeyOf(renderComponent, programId), item)
                    }
                }
                go.getComponent<SpriteRenderer>()
                    ?.let { if (it.enabled) renderables.add(Renderable2D(it, transform.worldMatrix)) }
            }
        }
        drawQueue.sort()
        drawItems(drawQueue, sceneFrame, modelRenderer, scene)
        drawQueue.clear()

        render2D(camera, renderables, batchShader)
        renderables.clear()
        skyDomeRenderer.render(camera, cameraPosition, scene)
        cmd.endPass()
    }

    /** Clear values of the scene target; the color is the frame's sky color. */
    private val clear = ClearValues(floatArrayOf(0f, 0f, 0f, 1f), 1f)
    private val presentedViewport = PresentedViewport()

    fun destroy() = sceneFrame.destroy()

    private fun sortKeyOf(renderComponent: RenderComponent, programId: Int): Long {
        val parts = renderComponent.model?.mesh
        val first = parts?.firstOrNull() ?: return SortKey.build(false, programId, 0, 0)
        val material = renderComponent.material ?: first.material
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

        /** A mesh goes to the transparent pass when its effective material (override or first part) blends. */
        fun isBlended(renderComponent: RenderComponent): Boolean {
            val first = renderComponent.model?.mesh?.firstOrNull() ?: return false
            return (renderComponent.material ?: first.material).alphaMode == AlphaMode.BLEND
        }

        /** Draws [queue] in its order with the frame's lighting; shared by the geometry and transparent passes. */
        internal fun drawItems(queue: DrawQueue<SceneFrame.DrawItem>, frame: SceneFrame, modelRenderer: ModelRenderer, scene: Scene) {
            var current: Shader? = null
            for (i in 0 until queue.size) {
                val (go, renderComponent, transform, program) = queue.itemAt(i)
                if (program !== current) {
                    current?.stop()
                    program.start()
                    frame.prepare(program)
                    current = program
                }
                // Hover & Selected states, inherited from ancestors so a mesh-less root highlights its children
                program.uploadFloat(Uniforms.SELECTED, SelectionHighlight.stateOf(go, scene))
                frame.selectObjectLights(program, renderComponent.model, transform)
                modelRenderer.render(
                    transform = transform,
                    renderComponent = renderComponent,
                    shader = program,
                    cameraPosition = frame.cameraPosition,
                    skeletonComponent = go.getComponent<SkeletonComponent>(),
                )
            }
            current?.stop()
        }

        /**
         * The shadow maps to use this frame: only what the shadow pass published this frame. With the shadow pass off
         * (or `NoShadowPass`) nothing is published, so this is null and geometry renders unshadowed: no stale maps,
         * nothing to clear (EDT-54).
         */
        internal fun shadowsThisFrame(frame: ShadowFrame?): ShadowFrame? = frame?.takeIf { it.consume() }
    }
}
