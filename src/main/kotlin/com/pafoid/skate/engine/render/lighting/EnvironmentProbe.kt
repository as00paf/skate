package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.ecs.components.DayNightCycleComponent
import com.pafoid.skate.engine.ecs.components.EnvironmentComponent
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.render.data.VertexLayouts
import com.pafoid.skate.engine.render.rhi.BufferUpdate
import com.pafoid.skate.engine.render.rhi.BufferUsage
import com.pafoid.skate.engine.render.rhi.Filter
import com.pafoid.skate.engine.render.rhi.IntRect
import com.pafoid.skate.engine.render.rhi.PixelFormat
import com.pafoid.skate.engine.render.rhi.RenderTargetDesc
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiDevice
import com.pafoid.skate.engine.render.rhi.RhiRenderTarget
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.rhi.SamplerDesc
import com.pafoid.skate.engine.render.rhi.TextureDesc
import com.pafoid.skate.engine.render.rhi.TextureType
import com.pafoid.skate.engine.render.rhi.Wrap
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max

/**
 * Image-based lighting from the scene's sky (REN-27): reflections and sky light for every mesh.
 *
 * When the sky changes (texture, tint, exposure or fog colour) the probe draws the sky texture into a small target,
 * reads it back and bakes it on the CPU ([EnvironmentBake]): a GGX-prefiltered radiance mip chain and SH irradiance.
 * [bind] then gives `shader_3d_default.glsl` the prefiltered map, the full-resolution sky (mirror reflections at
 * roughness 0), the split-sum BRDF table and the SH coefficients. Scenes without a sky get no IBL and keep the plain
 * ambient term. The sky's rotation (day/night) is a uniform, so turning the sky never re-bakes.
 *
 * @param captureShader `ibl_capture.glsl`; null disables IBL (tests without shaders).
 */
class EnvironmentProbe(
    private val captureShader: Shader?,
    private val device: () -> RhiDevice? = { RhiContext.device },
) {
    private var prefiltered: RhiTexture? = null
    private var brdfLut: RhiTexture? = null
    private var capture: RhiRenderTarget? = null
    private var quadBuffer: com.pafoid.skate.engine.render.rhi.RhiBuffer? = null
    private var quad: RhiVertexInput? = null
    private val pipelines = PipelineCache(PipelineStates.FULLSCREEN)
    private var sh: FloatArray? = null

    // Cache key of the current bake
    private var bakedSky: RhiTexture? = null
    private val bakedTint = Vector3f(-1f)
    private var bakedExposure = -1f
    private val bakedFog = Vector3f(-1f)

    private var sky: RhiTexture? = null
    private var active = false
    private var rotation = 0f
    private var lightScale = 1f
    private var reflection = 0f
    private var skyLight = 0f
    private val skyScale = Vector3f()
    private val shValue = Vector4f()

    /** Number of bakes so far (tests and stats). */
    var bakeCount = 0
        private set

    /**
     * Re-bakes when the sky changed and records this frame's state. Call outside any render pass (it draws into its
     * own target and reads it back).
     */
    fun update(environment: EnvironmentComponent?, dayNight: DayNightCycleComponent?) {
        val skyTexture = environment?.takeIf { it.enabled && it.renderSky }?.skyTexture?.rhi
        active = skyTexture != null && captureShader != null && device() != null
        if (!active || environment == null || skyTexture == null) return
        if (needsBake(skyTexture, environment)) bake(skyTexture, environment)
        sky = skyTexture
        rotation = skyRotation(environment, dayNight)
        lightScale = environmentLightScale(dayNight)
        reflection = environment.reflectionIntensity * lightScale
        skyLight = environment.skyLightIntensity * lightScale
        skyScale.set(environment.skyTint).mul(environment.skyExposure)
    }

    /** Uploads the IBL uniforms and textures for this frame to [shader] (the mesh shader, already started). */
    fun bind(shader: Shader) {
        val enabled = active && prefiltered != null && sh != null
        shader.uploadInt(Uniforms.ENV_ENABLED, if (enabled) 1 else 0)
        if (!enabled) return
        val coefficients = sh ?: return
        for (k in 0 until 9) {
            shader.uploadVec4f(SH_NAMES[k], shValue.set(coefficients[k * 3], coefficients[k * 3 + 1], coefficients[k * 3 + 2], 0f))
        }
        shader.uploadFloat(Uniforms.ENV_ROTATION, rotation)
        shader.uploadFloat(Uniforms.ENV_MAX_LOD, (EnvironmentBake.LEVELS - 1).toFloat())
        shader.uploadFloat(Uniforms.ENV_REFLECTION, reflection)
        shader.uploadFloat(Uniforms.ENV_SKY_LIGHT, skyLight)
        shader.uploadVec3f(Uniforms.ENV_SKY_SCALE, skyScale)
        shader.uploadInt(Uniforms.ENV_PREFILTERED, Uniforms.ENV_PREFILTERED_UNIT)
        shader.uploadInt(Uniforms.ENV_SKY, Uniforms.ENV_SKY_UNIT)
        shader.uploadInt(Uniforms.ENV_BRDF_LUT, Uniforms.ENV_BRDF_LUT_UNIT)
        val cmd = RhiContext.require().commandList()
        cmd.setTexture(Uniforms.ENV_PREFILTERED_UNIT, prefiltered)
        cmd.setTexture(Uniforms.ENV_SKY_UNIT, sky)
        cmd.setTexture(Uniforms.ENV_BRDF_LUT_UNIT, brdfLut)
    }

    private fun needsBake(skyTexture: RhiTexture, env: EnvironmentComponent): Boolean =
        prefiltered == null || bakedSky !== skyTexture || bakedExposure != env.skyExposure ||
            bakedTint != env.skyTint || bakedFog != env.fogColor

    private fun bake(skyTexture: RhiTexture, env: EnvironmentComponent) {
        val d = device() ?: return
        val display = captureSky(d, skyTexture) ?: return
        val radiance = EnvironmentBake.radiance(display, EnvironmentBake.BASE_WIDTH, EnvironmentBake.BASE_HEIGHT,
            env.skyTint, env.skyExposure, env.fogColor)
        val baked = EnvironmentBake.bake(radiance)
        upload(d, baked)
        sh = baked.shIrradiance
        bakedSky = skyTexture
        bakedTint.set(env.skyTint)
        bakedExposure = env.skyExposure
        bakedFog.set(env.fogColor)
        bakeCount++
    }

    /** Draws [skyTexture] into the capture target and reads its RGBA8 texels back (rows of `v` 0 upward). */
    private fun captureSky(d: RhiDevice, skyTexture: RhiTexture): ByteArray? {
        val shader = captureShader ?: return null
        val w = EnvironmentBake.BASE_WIDTH
        val h = EnvironmentBake.BASE_HEIGHT
        val target = capture ?: d.createRenderTarget(RenderTargetDesc(w, h, depth = null)).also { capture = it }
        val input = quad ?: d.createBuffer(BufferUsage.VERTEX, BufferUpdate.STATIC, QUAD).let { buffer ->
            quadBuffer = buffer
            d.createVertexInput(d.createVertexLayout(VertexLayouts.FULLSCREEN_2D), listOf(buffer)).also { quad = it }
        }
        skyTexture.setSampler(skyTexture.sampler.copy(wrap = Wrap.REPEAT, wrapV = Wrap.CLAMP_TO_EDGE))
        val cmd = d.commandList()
        cmd.beginPass(target)
        shader.start()
        shader.uploadInt(Uniforms.TEXTURE, 0)
        shader.uploadFloat(LOD, captureLod(skyTexture.desc.width, skyTexture.desc.mipLevels))
        cmd.setTexture(0, skyTexture)
        pipelines.get(shader, input.layout)?.let { pipeline ->
            cmd.setPipeline(pipeline)
            cmd.setVertexInput(input)
            cmd.draw(QUAD.size / 2)
        }
        shader.stop()
        cmd.endPass()
        val pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        d.readPixels(target, 0, IntRect(0, 0, w, h), PixelFormat.RGBA8, pixels)
        return ByteArray(w * h * 4).also { pixels.get(it) }
    }

    private fun upload(d: RhiDevice, baked: EnvironmentBake) {
        val texture = prefiltered ?: d.createTexture(TextureDesc(
            TextureType.TEXTURE_2D, PixelFormat.RGBA8, EnvironmentBake.BASE_WIDTH, EnvironmentBake.BASE_HEIGHT,
            mipLevels = EnvironmentBake.LEVELS,
            sampler = SamplerDesc(Filter.LINEAR_MIPMAP_LINEAR, Filter.LINEAR, Wrap.REPEAT, wrapV = Wrap.CLAMP_TO_EDGE),
        )).also { prefiltered = it }
        baked.levels.forEachIndexed { level, bytes -> texture.upload(level, 0, direct(bytes)) }
        if (brdfLut == null) {
            brdfLut = d.createTexture(TextureDesc(
                TextureType.TEXTURE_2D, PixelFormat.RGBA8, EnvironmentBake.LUT_SIZE, EnvironmentBake.LUT_SIZE,
                sampler = SamplerDesc(Filter.LINEAR, Filter.LINEAR, Wrap.CLAMP_TO_EDGE),
            )).also { it.upload(0, 0, direct(EnvironmentBake.brdfLutRgba8)) }
        }
    }

    private fun direct(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).flip()

    fun destroy() {
        prefiltered?.close(); prefiltered = null
        brdfLut?.close(); brdfLut = null
        capture?.close(); capture = null
        quad?.close(); quad = null
        quadBuffer?.close(); quadBuffer = null
        pipelines.close()
        captureShader?.destroy()
        bakedSky = null
        sh = null
    }

    companion object {
        private const val LOD = "uLod"
        private val QUAD = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)
        private val SH_NAMES = Array(9) { "${Uniforms.ENV_SH}[$it]" }

        /** Night floor of the environment light, so a night scene is not pitch black. */
        const val NIGHT_LIGHT = 0.15f

        /** Rotation of the sky dome around Y (radians): follows the time of day, plus the environment's offset. */
        fun skyRotation(environment: EnvironmentComponent, dayNight: DayNightCycleComponent?): Float {
            val timeOfDay = dayNight?.takeIf { it.enabled }?.timeOfDay ?: 12f
            val angle = (timeOfDay / 24f - 0.5f) * 2f * PI.toFloat()
            return -angle + Math.toRadians(environment.skyRotation.toDouble()).toFloat()
        }

        /** Scale of the sky's light: dims with the day/night cycle (the sun and auto ambient do too), 1 without one. */
        fun environmentLightScale(dayNight: DayNightCycleComponent?): Float {
            val cycle = dayNight?.takeIf { it.enabled } ?: return 1f
            return NIGHT_LIGHT + (1f - NIGHT_LIGHT) * DayNightCycleSystem.daylight(cycle.timeOfDay)
        }

        /** Mip level of a [width]-wide sky texture whose texels match the capture width (clamped to its mips). */
        fun captureLod(width: Int, mipLevels: Int): Float =
            (ln(max(1f, width.toFloat() / EnvironmentBake.BASE_WIDTH)) / ln(2f)).coerceIn(0f, (mipLevels - 1).toFloat())
    }
}
