package com.pafoid.skate.engine.render.renderer.passes

import com.pafoid.skate.engine.assets.data.Shader
import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.ColorTarget
import com.pafoid.skate.engine.render.FrameBuffer
import com.pafoid.skate.engine.render.VAOLoader
import com.pafoid.skate.engine.render.rhi.RhiCommandList
import com.pafoid.skate.engine.render.rhi.RhiContext
import com.pafoid.skate.engine.render.rhi.RhiTexture
import com.pafoid.skate.engine.render.rhi.RhiVertexInput
import com.pafoid.skate.engine.render.state.PipelineCache
import com.pafoid.skate.engine.render.state.PipelineStates
import com.pafoid.skate.engine.utils.ShaderConst.Uniforms
import org.joml.Vector2f

/**
 * Post-processing render pass implementing the post-processing stack.
 *
 * The pass executes the enabled [PostProcessSettings] effects in the canonical
 * [PostEffect.DEFAULT_PIPELINE] order. Effects ping-pong between two scratch
 * colour targets as planned by [PostChain]: no effect samples the texture it
 * draws into (that read/write feedback loop is undefined in GL and produced
 * mirrored, tiled garbage). The final result is copied back into the scene
 * framebuffer's colour attachment, so the viewport keeps showing
 * [FrameBuffer.getTextureId] and the scene depth stays untouched for later
 * passes such as the debug pass.
 *
 * Post-processing needs the scene in a texture, so it is skipped when the scene
 * renders straight to the default framebuffer (`useFbo == false`).
 *
 * The pass can be toggled as a whole (runtime and editor) through the standard
 * render-graph enable/disable mechanism, or effect-by-effect through
 * [toggleEffect] and [updateSettings].
 *
 * @param frameBuffer The framebuffer holding the rendered color/depth buffers.
 * @param useFbo Whether the scene renders into the framebuffer instead of the default framebuffer.
 * @param bloomShader Shader applying the bloom effect.
 * @param tonemapShader Shader applying the ACES tonemapping effect.
 * @param gammaShader Shader applying the gamma correction effect.
 * @param fxaaShader Shader applying the FXAA anti-aliasing effect.
 * @param ssaoShader Shader applying the screen-space ambient occlusion effect.
 * @param vaoLoader Loader used to create the fullscreen quad used by effects.
 * @param settings The post-processing effect state for this pass.
 */
open class PostProcessPass(
    private val frameBuffer: FrameBuffer,
    private val useFbo: Boolean,
    private val bloomShader: Shader,
    private val tonemapShader: Shader,
    private val gammaShader: Shader,
    private val fxaaShader: Shader,
    private val ssaoShader: Shader,
    private val vaoLoader: VAOLoader,
    private var settings: PostProcessSettings = PostProcessSettings(),
) : BaseRenderPass() {

    override val name: String = "PostProcessPass"

    override val description: String = "Applies the post-processing effect stack (bloom, tonemapping, gamma, FXAA, SSAO)"

    override val canDisable: Boolean = true

    private var fullscreenQuad: MeshPart? = null
    private val ping = ColorTarget()
    private val pong = ColorTarget()
    private val texelSize = Vector2f()

    override fun execute(scene: Scene) {
        if (!isEnabled || !useFbo) return

        val effects = resolvedEffects()
        if (effects.isEmpty()) return
        val quad = getFullscreenQuad() ?: return

        val width = frameBuffer.width
        val height = frameBuffer.height
        ping.ensure(width, height)
        pong.ensure(width, height)
        val steps = PostChain.plan(effects.size)

        val input = quad.input ?: return
        val cmd = RhiContext.require().commandList()
        for (i in effects.indices) {
            cmd.beginPass(targetOf(steps[i].destination).target)
            apply(cmd, effects[i], textureOf(steps[i].source), input)
            cmd.endPass()
        }

        // Copy the result back into the scene colour buffer (depth is left as the geometry pass wrote it).
        val result = targetOf(PostChain.resultTarget(effects.size))
        cmd.blit(result.target!!, frameBuffer.target)
    }

    private fun targetOf(target: PostChain.Target): ColorTarget = when (target) {
        PostChain.Target.PING -> ping
        PostChain.Target.PONG -> pong
        PostChain.Target.SCENE -> error("post effects never write the scene target")
    }

    private fun textureOf(target: PostChain.Target): RhiTexture? = when (target) {
        PostChain.Target.SCENE -> frameBuffer.target.colorTexture()
        PostChain.Target.PING -> ping.target?.colorTexture()
        PostChain.Target.PONG -> pong.target?.colorTexture()
    }

    /** No depth, no blending: each effect overwrites its target ([PipelineStates.FULLSCREEN]). */
    private val pipelines = PipelineCache(PipelineStates.FULLSCREEN)

    private fun apply(cmd: RhiCommandList, effect: PostEffect, source: RhiTexture?, input: RhiVertexInput) {
        val shader = when (effect) {
            PostEffect.SSAO -> ssaoShader
            PostEffect.BLOOM -> bloomShader
            PostEffect.TONEMAPPING -> tonemapShader
            PostEffect.GAMMA_CORRECTION -> gammaShader
            PostEffect.FXAA -> fxaaShader
        }
        shader.start()
        when (effect) {
            PostEffect.SSAO -> {
                shader.uploadFloat(Uniforms.SSAO_RADIUS, settings.ssaoRadius)
                shader.uploadFloat(Uniforms.SSAO_INTENSITY, settings.ssaoIntensity)
                shader.uploadInt(Uniforms.SSAO_DEPTH_TEXTURE, 1)
                cmd.setTexture(1, frameBuffer.target.depthTexture())
            }
            PostEffect.BLOOM -> {
                shader.uploadFloat(Uniforms.BLOOM_INTENSITY, settings.bloomIntensity)
                shader.uploadFloat(Uniforms.BLOOM_THRESHOLD, settings.bloomThreshold)
            }
            PostEffect.TONEMAPPING -> shader.uploadFloat(Uniforms.ACES_EXPOSURE, settings.acesExposure)
            PostEffect.GAMMA_CORRECTION -> shader.uploadFloat(Uniforms.GAMMA_VALUE, settings.gammaValue)
            PostEffect.FXAA -> {
                shader.uploadFloat(Uniforms.FXAA_STRENGTH, settings.fxaaStrength)
                shader.uploadVec2f(Uniforms.FXAA_TEXEL_SIZE, texelSize.set(1f / frameBuffer.width, 1f / frameBuffer.height))
            }
        }
        shader.uploadTexture(Uniforms.TEXTURE, 0)
        cmd.setTexture(0, source)
        cmd.setPipeline(pipelines.get(shader, input.layout) ?: return)
        cmd.setVertexInput(input)
        cmd.drawIndexed(fullscreenQuad?.vertexCount ?: return)
        shader.stop()
    }

    /**
     * Returns the effects that should run during the current frame, in
     * canonical pipeline order. Returns an empty list when the pass is disabled.
     */
    fun resolvedEffects(): List<PostEffect> = if (isEnabled) settings.enabledEffects() else emptyList()

    /** Returns the post-processing settings currently used by this pass. */
    fun getSettings(): PostProcessSettings = settings

    /** Replaces the post-processing settings used by this pass. */
    fun updateSettings(next: PostProcessSettings) {
        settings = next
    }

    /** Toggles the given effect on/off and returns the new enabled state. */
    fun toggleEffect(effect: PostEffect): Boolean = settings.toggle(effect)

    /** Returns whether the given effect is currently enabled. */
    fun isEffectEnabled(effect: PostEffect): Boolean = settings.isEnabled(effect)

    fun destroy() {
        fullscreenQuad?.let { vaoLoader.deleteVAO(it.vaoId) }
        fullscreenQuad = null
        pipelines.close()
        ping.destroy()
        pong.destroy()
        bloomShader.destroy()
        tonemapShader.destroy()
        gammaShader.destroy()
        fxaaShader.destroy()
        ssaoShader.destroy()
    }

    private fun getFullscreenQuad(): MeshPart? {
        if (fullscreenQuad == null) {
            fullscreenQuad = vaoLoader.loadToVAO(
                positions = floatArrayOf(
                    -1f, -1f, 0f,
                    1f, -1f, 0f,
                    1f, 1f, 0f,
                    -1f, 1f, 0f
                ),
                textureCoords = floatArrayOf(
                    0f, 0f,
                    1f, 0f,
                    1f, 1f,
                    0f, 1f
                ),
                normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
                indices = intArrayOf(0, 1, 2, 2, 3, 0)
            )
        }
        return fullscreenQuad
    }
}
