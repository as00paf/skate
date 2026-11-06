package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.render.graph.RenderResourceIds
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.DirectionalLightComponent
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.render.ShadowMap
import com.pafoid.skate.engine.render.lighting.LightData
import com.pafoid.skate.engine.render.lighting.LightType
import com.pafoid.skate.engine.render.lighting.SceneLights
import com.pafoid.skate.engine.render.renderer.ShadowRenderer
import com.pafoid.skate.engine.render.rhi.ClearValues
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.shadow.CascadeFitter
import com.pafoid.skate.engine.render.shadow.CascadeSplits
import com.pafoid.skate.engine.render.shadow.LocalShadows
import com.pafoid.skate.engine.render.shadow.ShadowLayerCache
import com.pafoid.skate.engine.render.shadow.ShadowFrame
import com.pafoid.skate.engine.render.shadow.ShadowQualityLevel
import com.pafoid.skate.engine.render.shadow.ShadowSettings
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Renders every shadow map for the frame and publishes the result in [frame] for the geometry pass:
 *
 * - **Directional light**: cascaded shadow maps (PSSM splits, sphere-fit and texel-snapped), one layer per cascade
 *   in [cascadeMap].
 * - **Spot / point lights**: the closest lights within the quality tier's layer budget get one layer (spot) or six
 *   cube-face layers (point) in [localMap].
 *
 * Resolution, cascade count, PCF radius and local budget come from [quality] (by default [ShadowSettings.quality]).
 */
class ShadowPass(
    private val shadowRenderer: ShadowRenderer,
    private val cascadeMap: ShadowMap,
    private val localMap: ShadowMap,
    private val cameraManager: CameraManager,
    val frame: ShadowFrame,
    private val quality: () -> ShadowQualityLevel = { ShadowSettings.quality },
) : BaseRenderPass() {

    override val name: String = "ShadowPass"
    override val outputs: Set<String> = setOf(RenderResourceIds.SHADOW_MAP, RenderResourceIds.LOCAL_SHADOW_MAP)
    override val description: String = "Renders cascaded sun shadows and spot/point light shadow maps"
    override val canDisable: Boolean = true

    private val fitter = CascadeFitter()
    private val cascadeCache = ShadowLayerCache(CascadeSplits.MAX_CASCADES)
    private val localCache = ShadowLayerCache(LocalShadows.MAX_LAYERS)
    private var drawing = false

    /** Layers actually rendered / skipped as unchanged in the last frame (shown in the Render Graph window). */
    var layersRendered = 0
        private set
    var layersSkipped = 0
        private set
    private val sceneLights = ArrayList<LightData>()
    private val cameraPosition = Vector3f()

    override fun execute(scene: Scene) {
        frame.reset()
        val q = quality()
        frame.quality = q
        cameraManager.transform.worldMatrix.getTranslation(cameraPosition)

        val light = scene.getComponent<DirectionalLightComponent>()?.takeIf { it.enabled && it.castShadows }
        SceneLights.collect(scene, sceneLights)
        LocalShadows.allocate(sceneLights, cameraPosition, q.localLayers, frame.localSlots)
        if (light == null && frame.localSlots.isEmpty()) return

        shadowRenderer.collect(scene)
        layersRendered = 0
        layersSkipped = 0
        try {
            if (light != null) renderCascades(light, q)
            if (frame.localSlots.isNotEmpty()) renderLocal(q)
        } finally {
            if (drawing) {
                shadowRenderer.end()
                drawing = false
            }
        }
        frame.publish()
    }

    private fun renderCascades(light: DirectionalLightComponent, q: ShadowQualityLevel) {
        if (cascadeMap.ensure(q.cascadeResolution, q.cascadeCount)) cascadeCache.invalidateAll()
        val res = cascadeMap.resolution
        val camera = cameraManager.camera
        val near = max(camera.nearPlane, MIN_NEAR)
        val far = max(near + 1f, min(light.shadowDistance, camera.farPlane))

        val count: Int
        if (light.autoCalculateBounds) {
            count = q.cascadeCount
            CascadeSplits.compute(near, far, count, CascadeSplits.DEFAULT_LAMBDA, frame.splits)
            val fovY = Math.toRadians(camera.fov.toDouble()).toFloat() * camera.zoom
            for (i in 0 until count) {
                val corners = if (camera.isOrthographic) {
                    fitter.orthographicSliceCorners(
                        camera.inverseView, camera.viewportWidth * camera.zoom / 2f,
                        camera.viewportHeight * camera.zoom / 2f, frame.splits[i], frame.splits[i + 1],
                    )
                } else {
                    fitter.perspectiveSliceCorners(camera.inverseView, fovY, camera.aspectRatio, frame.splits[i], frame.splits[i + 1])
                }
                fitter.fit(corners, light.direction, res, CASTER_EXTENSION, frame.cascades[i])
            }
        } else {
            // Manual bounds: one cascade of the component's ortho size centred on the camera.
            count = 1
            frame.splits[0] = near
            frame.splits[1] = far
            val radius = max(max(abs(light.orthoLeft), abs(light.orthoRight)), max(abs(light.orthoBottom), abs(light.orthoTop)))
            fitter.fitSphere(cameraPosition, max(radius, 1f), light.direction, res, CASTER_EXTENSION, frame.cascades[0])
        }

        for (i in 0 until count) {
            if (!needsRender(cascadeCache, i, shadowRenderer.cascadeSignature(frame.cascades[i]))) continue
            renderLayer(cascadeMap, i) { shadowRenderer.drawCascade(frame.cascades[i]) }
        }
        frame.cascadeCount = count
        frame.cascadeTexture = cascadeMap.target?.depthTexture()
        frame.cascadeResolution = res
        frame.extraDepthBias = light.depthBias
        frame.extraNormalOffset = light.slopeScaledBias
    }

    private fun renderLocal(q: ShadowQualityLevel) {
        if (localMap.ensure(q.localResolution, q.localLayers)) localCache.invalidateAll()
        for (slot in frame.localSlots) {
            val l = sceneLights[slot.lightIndex]
            if (slot.type == LightType.SPOT) {
                val m = LocalShadows.spotMatrix(l.position, l.direction, LocalShadows.spotFov(l.cosOuter), slot.near, slot.far, frame.localMatrices[slot.baseLayer])
                if (!needsRender(localCache, slot.baseLayer, shadowRenderer.localSignature(l.position, l.range, m))) continue
                renderLayer(localMap, slot.baseLayer) { shadowRenderer.drawLocal(l.position, l.range, m) }
            } else {
                for (face in 0 until LocalShadows.POINT_FACES) {
                    val layer = slot.baseLayer + face
                    val m = LocalShadows.pointFaceMatrix(l.position, face, slot.near, slot.far, frame.localMatrices[layer])
                    if (!needsRender(localCache, layer, shadowRenderer.localSignature(l.position, l.range, m))) continue
                    renderLayer(localMap, layer) { shadowRenderer.drawLocal(l.position, l.range, m) }
                }
            }
        }
        frame.localTexture = localMap.target?.depthTexture()
        frame.localResolution = localMap.resolution
    }

    /** Clears [layer] of [map] to far depth and renders it with [draw]. */
    private inline fun renderLayer(map: ShadowMap, layer: Int, draw: () -> Unit) {
        val cmd = RhiContext.require().commandList()
        cmd.beginPass(map.target, layer, ClearValues.DEPTH)
        try {
            draw()
        } finally {
            cmd.endPass()
        }
    }

    /** Records the layer as rendered or skipped and starts the shadow draw state on the first rendered layer. */
    private fun needsRender(cache: ShadowLayerCache, layer: Int, signature: Long): Boolean {
        if (!cache.needsRender(layer, signature)) {
            layersSkipped++
            return false
        }
        layersRendered++
        if (!drawing) {
            shadowRenderer.begin()
            drawing = true
        }
        return true
    }

    companion object {
        /** Extra depth towards the sun so casters outside the view slice still land in the cascade. */
        const val CASTER_EXTENSION = 50f
        private const val MIN_NEAR = 0.05f
    }
}
