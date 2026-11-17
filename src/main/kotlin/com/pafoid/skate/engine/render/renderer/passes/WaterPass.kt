package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.components.WaterComponent
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.graph.RenderResourceIds
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.rhi.BlitMask
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.render.water.WaterRenderer
import com.pafoid.skate.engine.render.wind.WindUniforms
import com.pafoid.skate.engine.utils.EntityIdEncoder
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f

/**
 * Draws [com.pafoid.skate.engine.ecs.components.WaterComponent] surfaces after opaque geometry and before transparent
 * meshes (REN-29 §6.2). It copies the scene colour and depth first ([sceneCopy]), so the water can refract, absorb and
 * reflect (SSR, task 8) what is behind and around it without sampling the target it draws into. Depth is written, so
 * transparent meshes and particles test against the surface. No water: nothing happens, nothing is allocated.
 *
 * @param ssrSteps screen-space reflection steps from the quality preset (0: IBL reflections only)
 */
class WaterPass(
    private val shader: () -> Shader?,
    private val pickingShader: () -> Shader?,
    private val renderer: WaterRenderer,
    private val frameBuffer: FrameBuffer,
    private val sceneFrame: SceneFrame,
    private val cameraManager: CameraManager,
    private val ssrSteps: () -> Int = { 0 },
    private val debugRenderer: DebugRenderer? = null,
    private val device: () -> RhiDevice = RhiContext::require,
) : BaseRenderPass() {

    override val name: String = "WaterPass"
    override val inputs: Set<String> = setOf(RenderResourceIds.SCENE_COLOR, RenderResourceIds.SCENE_DEPTH)
    override val outputs: Set<String> =
        setOf(RenderResourceIds.SCENE_COLOR_COPY, RenderResourceIds.SCENE_DEPTH_COPY, RenderResourceIds.SCENE_COLOR, RenderResourceIds.SCENE_DEPTH)
    override val description: String = "Renders water surfaces with refraction, reflections and foam"

    private val draws = ArrayList<WaterRenderer.Draw>()
    private val cameraPosition = Vector3f()
    private val vec2 = Vector2f()
    private val boxCenter = Vector3f()
    private val boxHalf = Vector3f()
    private val boxRotation = Quaternionf()

    /** Copy of the scene colour and depth, created on the first frame with water. */
    var sceneCopy: RhiRenderTarget? = null
        private set

    override fun execute(scene: Scene) {
        renderer.collect(scene, draws)
        if (draws.isEmpty() || !sceneFrame.began) return
        val program = shader() ?: return
        val camera = cameraManager.camera
        cameraManager.transform.worldMatrix.getTranslation(cameraPosition)
        val target = frameBuffer.target

        // Scene copy: the water samples it instead of the attachments it draws into
        val copy = ensureCopy(target.width, target.height)
        val cmd = device().commandList()
        cmd.blit(target, copy, filter = Filter.NEAREST, mask = BlitMask.COLOR_DEPTH)

        cmd.beginPass(target)
        program.start()
        sceneFrame.prepare(program)
        val env = sceneFrame.environment
        program.uploadVec2f(Uniforms.WATER_SCREEN, vec2.set(target.width.toFloat(), target.height.toFloat()))
        program.uploadVec2f(Uniforms.WATER_NEAR_FAR, vec2.set(camera.nearPlane, camera.farPlane))
        program.uploadInt(Uniforms.WATER_SSR_STEPS, ssrSteps().coerceIn(0, MAX_SSR_STEPS))
        program.uploadInt(Uniforms.WATER_SCENE_COLOR, Uniforms.WATER_SCENE_COLOR_UNIT)
        program.uploadInt(Uniforms.WATER_SCENE_DEPTH, Uniforms.WATER_SCENE_DEPTH_UNIT)
        cmd.setTexture(Uniforms.WATER_SCENE_COLOR_UNIT, copy.colorTexture())
        cmd.setTexture(Uniforms.WATER_SCENE_DEPTH_UNIT, copy.depthTexture())
        WindUniforms.upload(program, env)
        val time = env?.runtime?.renderTime() ?: 0f
        val projectionScale = renderer.projectionScale(camera)

        renderer.beginFrame(camera)
        for (draw in draws) {
            val water = draw.water
            renderer.configureWaves(water, env)
            renderer.uploadSurface(program, water, draw.transform, time)
            renderer.uploadMaterial(program, renderer.material(water.material))
            draw.transform.worldMatrix.getTranslation(boxCenter)
            sceneFrame.selectObjectLights(program, boxCenter, 0.5f * kotlin.math.sqrt(water.sizeX * water.sizeX + water.sizeZ * water.sizeZ))
            renderer.drawSurface(program, water, draw.transform, cameraPosition, projectionScale)
            if (debugRenderer != null && SelectionHighlight.stateOf(draw.go, scene) == SelectionHighlight.SELECTED) drawVolume(draw)
        }
        renderer.endFrame()
        program.stop()
        cmd.endPass()
        draws.clear()
    }

    /** The volume box (surface down to `depth`, plus the wave crest) of a selected water, through the debug pass. */
    private fun drawVolume(draw: WaterRenderer.Draw) {
        val water = draw.water
        val m = draw.transform.worldMatrix
        val crest = renderer.waves.totalAmplitude
        boxCenter.set(m.m30(), m.m31() + (crest - water.depth) * 0.5f, m.m32())
        boxHalf.set(water.sizeX * 0.5f, (water.depth + crest) * 0.5f, water.sizeZ * 0.5f)
        boxRotation.identity().rotateY(WaterRenderer.yawOf(m))
        debugRenderer?.addBox3D(boxCenter, boxRotation, boxHalf, VOLUME_COLOR)
    }

    /** Draws the water surfaces' entity ids into the bound picking target (LOD 2, same waves as the colour pass). */
    fun drawPicking(scene: Scene) {
        val program = pickingShader() ?: return
        // Same targets as the mesh picking: rendered, unlocked, not NonPickable
        draws.clear()
        PickingPass.forEachPickable(scene) { go ->
            val water = go.getComponent<WaterComponent>()?.takeIf { it.enabled } ?: return@forEachPickable
            val transform = go.getComponent<Transform>()?.takeIf { it.enabled } ?: return@forEachPickable
            draws += WaterRenderer.Draw(go, water, transform)
        }
        if (draws.isEmpty()) return
        val camera = cameraManager.camera
        cameraManager.transform.worldMatrix.getTranslation(cameraPosition)
        program.start()
        program.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        program.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        val env = scene.getComponent<EnvironmentComponent>()?.takeIf { it.enabled }
        val time = env?.runtime?.renderTime() ?: 0f
        renderer.beginFrame(camera)
        for (draw in draws) {
            program.uploadFloat(Uniforms.ENTITY_ID, EntityIdEncoder.encode(draw.go.uId))
            renderer.configureWaves(draw.water, env)
            renderer.uploadSurface(program, draw.water, draw.transform, time)
            renderer.drawSurface(program, draw.water, draw.transform, cameraPosition, 1f, fixedLod = PICKING_LOD)
        }
        program.stop()
        draws.clear()
    }

    private fun ensureCopy(width: Int, height: Int): RhiRenderTarget {
        val current = sceneCopy
        if (current == null) return device().createRenderTarget(copyDesc(width, height)).also { sceneCopy = it }
        if (current.width != width || current.height != height) current.resize(width, height)
        return current
    }

    fun destroy() {
        sceneCopy?.close()
        sceneCopy = null
        renderer.destroy()
    }

    companion object {
        const val MAX_SSR_STEPS = 64
        const val PICKING_LOD = 2
        private val VOLUME_COLOR = Vector3f(0.2f, 0.6f, 1f)

        /** Same formats as the scene target, so the colour and depth blit is a straight copy; clamped, unfiltered depth. */
        fun copyDesc(width: Int, height: Int) = RenderTargetDesc(
            width, height,
            colors = listOf(PixelFormat.RGBA8),
            depth = PixelFormat.DEPTH32F,
            colorSampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.CLAMP_TO_EDGE),
            depthSampler = SamplerDesc(Filter.NEAREST, Filter.NEAREST, Wrap.CLAMP_TO_EDGE),
        )
    }
}
