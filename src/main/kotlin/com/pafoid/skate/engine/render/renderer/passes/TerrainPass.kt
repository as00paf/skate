package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.TerrainComponent
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.graph.RenderResourceIds
import com.pafoid.skate.engine.render.renderer.ShadowCasterSource
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.shadow.ShadowSignature
import com.pafoid.skate.engine.render.terrain.TerrainRenderer
import com.pafoid.skate.engine.utils.EntityIdEncoder
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Draws terrain chunks (REN-28 §4) into the scene colour and depth after the geometry pass, lit by the frame's
 * [SceneFrame] (sun, lights, shadows, IBL). Also feeds the picking pass ([drawPicking], LOD 2) and the shadow pass
 * ([shadowCaster], one LOD coarser). No terrain: nothing happens.
 */
class TerrainPass(
    private val shader: () -> Shader?,
    private val pickingShader: () -> Shader?,
    private val depthShader: () -> Shader?,
    private val renderer: TerrainRenderer,
    private val frameBuffer: FrameBuffer,
    private val sceneFrame: SceneFrame,
    private val cameraManager: CameraManager,
    private val useFbo: Boolean = true,
    private val device: () -> RhiDevice = RhiContext::require,
) : BaseRenderPass() {

    override val name: String = "TerrainPass"
    override val inputs: Set<String> = setOf(RenderResourceIds.SHADOW_MAP, RenderResourceIds.LOCAL_SHADOW_MAP, RenderResourceIds.SCENE_COLOR, RenderResourceIds.SCENE_DEPTH)
    override val outputs: Set<String> = setOf(RenderResourceIds.SCENE_COLOR, RenderResourceIds.SCENE_DEPTH)
    override val description: String = "Renders terrain chunks with LOD and skirts"

    private val draws = ArrayList<TerrainRenderer.Draw>()
    private val cameraPosition = Vector3f()
    private val center = Vector3f()
    private val presentedViewport = PresentedViewport()

    override fun execute(scene: Scene) {
        renderer.collect(scene, draws)
        renderer.retain(draws)
        if (draws.isEmpty() || !sceneFrame.began) return
        val program = shader() ?: return
        val camera = cameraManager.camera
        cameraManager.transform.worldMatrix.getTranslation(cameraPosition)
        val cmd = device().commandList()
        cmd.beginPass(if (useFbo) frameBuffer.target else null, viewport = presentedViewport.of(frameBuffer, useFbo))
        program.start()
        sceneFrame.prepare(program)
        renderer.setFrustum(camera.projection, camera.view)
        renderer.resetCounters()
        val scale = renderer.projectionScale(camera.viewportHeight, camera.fov)
        for (draw in draws) {
            val chunks = renderer.sync(draw)
            program.uploadFloat(Uniforms.SELECTED, SelectionHighlight.stateOf(draw.go, scene))
            program.uploadFloat(Uniforms.TERRAIN_BASE, draw.position.y)
            val half = (draw.terrain.runtime.layout?.half ?: 0f)
            sceneFrame.selectObjectLights(program, center.set(draw.position), half * 1.42f)
            renderer.drawChunks(program, draw, chunks, cameraPosition, scale)
        }
        program.stop()
        cmd.endPass()
    }

    /** Terrain ids into the bound picking target, at LOD 2. */
    fun drawPicking(scene: Scene) {
        val program = pickingShader() ?: return
        // Same targets as mesh picking: rendered, unlocked, not NonPickable
        pickable.clear()
        PickingPass.forEachPickable(scene) { go -> if (go.getComponent<TerrainComponent>() != null) pickable += go }
        if (pickable.isEmpty()) return
        renderer.collect(scene, draws)
        draws.retainAll { it.go in pickable }
        if (draws.isEmpty()) return
        val camera = cameraManager.camera
        cameraManager.transform.worldMatrix.getTranslation(cameraPosition)
        program.start()
        program.uploadMat4f(Uniforms.PROJECTION_MATRIX, camera.projection)
        program.uploadMat4f(Uniforms.VIEW_MATRIX, camera.view)
        renderer.setFrustum(camera.projection, camera.view)
        for (draw in draws) {
            program.uploadFloat(Uniforms.ENTITY_ID, EntityIdEncoder.encode(draw.go.uId))
            renderer.drawChunks(program, draw, renderer.sync(draw), cameraPosition, 1f, fixedLod = PICKING_LOD)
        }
        program.stop()
        draws.clear()
    }

    private val pickable = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<GameObject, Boolean>())

    /** Terrain as a shadow caster: chunks overlapping the light's frustum, one LOD coarser, depth only. */
    val shadowCaster = object : ShadowCasterSource {
        private val casters = ArrayList<TerrainRenderer.Draw>()
        private val camera = Vector3f()

        override fun collect(root: GameObject) {
            renderer.collect(root, casters)
            casters.removeAll { !it.terrain.castShadows }
        }

        override fun signature(seed: Long): Long {
            var h = seed
            for (d in casters) {
                h = ShadowSignature.mix(h, System.identityHashCode(d.terrain))
                h = ShadowSignature.mix(h, d.terrain.runtime.version)
                h = ShadowSignature.mix(h, d.position.x); h = ShadowSignature.mix(h, d.position.y); h = ShadowSignature.mix(h, d.position.z)
            }
            return h
        }

        override fun draw(lightMatrix: Matrix4f) {
            if (casters.isEmpty()) return
            val program = depthShader() ?: return
            cameraManager.transform.worldMatrix.getTranslation(camera)
            val cam = cameraManager.camera
            val scale = renderer.projectionScale(cam.viewportHeight, cam.fov)
            program.start()
            program.uploadMat4f(Uniforms.LIGHT_SPACE_MATRIX, lightMatrix)
            renderer.setFrustum(lightMatrix)
            for (d in casters) renderer.drawChunks(program, d, renderer.sync(d), camera, scale, lodBias = 1, shadow = true)
        }
    }

    fun destroy() = renderer.destroy()

    companion object {
        const val PICKING_LOD = 2
    }
}
